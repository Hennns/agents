import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.util.Timeout
import scalafx.Includes._
import scalafx.application.JFXApp.PrimaryStage
import scalafx.application.{JFXApp, Platform}
import scalafx.collections.ObservableMap
import scalafx.event.ActionEvent
import scalafx.scene.Scene
import scalafx.scene.control.Button
import scalafx.scene.layout._
import scalafx.scene.paint.Color
import scalafx.scene.shape.{Circle, StrokeType}

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.{DurationDouble, DurationInt}
import scala.util.{Failure, Success}

object Main extends JFXApp {

  implicit val agentMoverSystem: ActorSystem[AgentMover.Commands] = ActorSystem(AgentMover(), "AgentMover")
  implicit val ec: ExecutionContextExecutor                       = agentMoverSystem.executionContext
  implicit val timeout: Timeout                                   = 3.seconds

  val sceneWidth: Int          = 800
  val sceneHeight: Int         = 600
  val agentRadius: Double      = 10
  val agentStrokeWidth: Double = 5

  val actorLabelMap: ObservableMap[ActorRef[AgentActor.Commands], Circle] = ObservableMap.empty

  val agentParentSystem: ActorSystem[AgentParentActor.Commands] =
    ActorSystem(AgentParentActor(agentMoverSystem), "AgentParentActor")

  val stroke: BorderStroke =
    new BorderStroke(Color.Black, BorderStrokeStyle.Solid, new CornerRadii(10), BorderWidths.Default)
  val canvasBorder: Border = new Border(stroke)
  val canvas: Pane         = new Pane()

  val spawnAgentsButton: Button = new Button("Spawn Agents") {
    layoutX = sceneWidth - 100
    layoutY = 5
    onAction = (e: ActionEvent) => {
      agentParentSystem.log.info("button clicked")
      agentParentSystem ! AgentParentActor.SpawnAgents(10)
    }
  }

  canvas.setBorder(canvasBorder)
  canvas.getChildren.add(spawnAgentsButton)
  stage = new PrimaryStage {
    title = "Agents"
    scene = new Scene(canvas, sceneWidth, sceneHeight)
  }
  stage.show

  def moveAgents(): Unit = {
    agentMoverSystem.log.debug("Moving {} agents", actorLabelMap.size)
    actorLabelMap.foreach {
      case (ref, _) =>
        agentMoverSystem ! AgentMover.MoveAgent(ref)
    }
  }

  def updateAgentPositions(): Runnable = { () =>
    agentMoverSystem
      .askWithStatus(AgentMover.GetAgentPositions)
      .onComplete {
        case Failure(exception) => agentMoverSystem.log.error("Failed to get agent positions {}", exception)
        case Success(value) =>
          agentMoverSystem.log.debug("got {} values", value.size)
          value.foreach {
            case (ref, coordinates) =>
              val circle: Circle = actorLabelMap.getOrElseUpdate(ref, makeNewCircle())
              Platform.runLater(circle.relocate(coordinates.x, coordinates.y))
          }
      }
    moveAgents()
  }

  actorLabelMap.onChange { (_, change) =>
    change match {
      case ObservableMap.Add(key, added)              => addCircle(added)
      case ObservableMap.Remove(key, removed)         => ()
      case ObservableMap.Replace(key, added, removed) => ()
      case _                                          => agentMoverSystem.log.error("Unexpected change event")
    }
  }

  // Modifying GUI elements needs to be done on the JavaFX Application Thread
  def addCircle(circle: Circle): Unit = {
    Platform.runLater { canvas.getChildren.add(circle) }
  }

  def makeNewCircle(): Circle = {
    agentMoverSystem.log.debug("creating a new circle")
    val newCircle = Circle(10.0, 10.0, agentRadius)
    newCircle.setStroke(Color.Black)
    newCircle.setStrokeWidth(agentStrokeWidth)
    newCircle.setStrokeType(StrokeType.Inside)
    newCircle.setFill(Color.DeepSkyBlue)
    newCircle
  }

  agentMoverSystem.scheduler.scheduleAtFixedRate(5.seconds, 0.1.seconds) { updateAgentPositions() }

  override def stopApp(): Unit = {
    agentParentSystem.log.info("terminating agent parent system")
    agentParentSystem.terminate()

    agentMoverSystem.log.info("terminating agent mover system")
    agentMoverSystem.terminate()
    Platform.exit()
  }
}
