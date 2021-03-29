import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.util.Timeout
import scalafx.Includes._
import scalafx.application.JFXApp.PrimaryStage
import scalafx.application.{JFXApp, Platform}
import scalafx.event.ActionEvent
import scalafx.scene.Scene
import scalafx.scene.control.Button
import scalafx.scene.layout._
import scalafx.scene.paint.Color
import scalafx.scene.shape.{Circle, StrokeType}

import scala.collection.mutable
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.{DurationDouble, DurationInt}
import scala.util.{Failure, Success}

object Main extends JFXApp {

  implicit val agentMoverSystem: ActorSystem[AgentMover.Commands] = ActorSystem(AgentMover(), "AgentMover")
  implicit val ec: ExecutionContextExecutor                       = agentMoverSystem.executionContext
  implicit val timeout: Timeout                                   = 3.seconds

  val sceneWidth: Int  = 800
  val sceneHeight: Int = 600

  var actorLabelMap: mutable.Map[ActorRef[AgentActor.Commands], Circle] = mutable.Map.empty

  val agentParentSystem: ActorSystem[AgentParentActor.Commands] =
    ActorSystem(AgentParentActor(agentMoverSystem), "AgentParentActor")

  val stroke: BorderStroke =
    new BorderStroke(Color.Black, BorderStrokeStyle.Solid, new CornerRadii(10), BorderWidths.Default)
  val canvasBorder: Border = new Border(stroke)
  val canvas: Pane         = new Pane()

  val button1 = new Button("Spawn Agents") {
    layoutX = sceneWidth - 100
    layoutY = 5
    onAction = (e: ActionEvent) => {
      println("button clicked")
      agentParentSystem ! AgentParentActor.SpawnAgents(10)
    }
  }

  canvas.setBorder(canvasBorder)
  canvas.getChildren.add(button1)

  stage = new PrimaryStage {
    title = "Agents"
    scene = new Scene(canvas, sceneWidth, sceneHeight)
  }
  stage.show

  def moveAgents(): Unit = {
    println(s"Moving ${actorLabelMap.size} agents")
    actorLabelMap.foreach {
      case (ref, _) =>
        agentMoverSystem ! AgentMover.MoveAgent(ref)
    }
  }

  def updateAgentPositions(): Runnable = { () =>
    agentMoverSystem
      .askWithStatus(AgentMover.GetAgentPositions)
      .onComplete {
        case Failure(exception) => println(s"error $exception")
        case Success(value) =>
          println(s"got ${value.size} values")
          value.foreach {
            case (ref, coordinates) =>
              val circle: Circle = actorLabelMap.getOrElseUpdate(ref, addNewCircle())
              circle.relocate(coordinates.x, coordinates.y)
          }
      }
    moveAgents()
  }

  def addNewCircle(): Circle = {
    println("creating new circle")
    val newCircle = Circle(10.0, 10.0, 5.0)
    newCircle.setStroke(Color.Azure)
    newCircle.setStrokeWidth(5)
    newCircle.setStrokeType(StrokeType.Inside)
    newCircle.setFill(Color.DeepSkyBlue)

    // Modifying GUI elements needs to be done on the JavaFX Application Thread
    Platform.runLater { canvas.getChildren.add(newCircle) }
    newCircle
  }

  agentMoverSystem.scheduler.scheduleAtFixedRate(5.seconds, 0.1.seconds) { updateAgentPositions() }

  override def stopApp(): Unit = {
    println("terminating system")
    agentParentSystem.terminate()
    agentMoverSystem.terminate()
    Platform.exit()
  }
}
