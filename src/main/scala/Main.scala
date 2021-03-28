import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout
import scalafx.application.{JFXApp, Platform}
import scalafx.application.JFXApp.PrimaryStage
import scalafx.scene.Scene
import scalafx.scene.control.Label
import scalafx.scene.layout.Pane

import scala.collection.mutable
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.{DurationDouble, DurationInt}
import scala.util.{Failure, Success}

object Main extends JFXApp {

  println("Starting application")

  implicit val agentMoverSystem: ActorSystem[AgentMover.Commands] = ActorSystem(AgentMover(), "AgentMover")
  implicit val ec: ExecutionContextExecutor                       = agentMoverSystem.executionContext
  implicit val timeout: Timeout                                   = 3.seconds

  var actorLabelMap: mutable.Map[ActorRef[AgentActor.Commands], Label] = mutable.Map.empty

  val agentParentSystem: ActorSystem[AgentParentActor.Commands] =
    ActorSystem(AgentParentActor(agentMoverSystem), "AgentParentActor")

  val canvas: Pane = new Pane()
  stage = new PrimaryStage {
    title = "Agents"
    scene = new Scene(canvas, 800, 600)
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
              val label: Label = actorLabelMap.getOrElseUpdate(ref, addNewLabel())
              label.relocate(coordinates.x, coordinates.y)
          }
      }
    moveAgents()
  }

  def addNewLabel(): Label = {
    println("creating new label")
    val newLabel = new Label("agent")
    // Modifying GUI elements needs to be done on the JavaFX Application Thread
    Platform.runLater { canvas.getChildren.add(newLabel) }
    newLabel
  }

  agentParentSystem ! AgentParentActor.SpawnAgents(1000)
  agentMoverSystem.scheduler.scheduleAtFixedRate(5.seconds, 0.2.seconds) { updateAgentPositions() }

  override def stopApp(): Unit = {
    println("terminating system")
    agentParentSystem.terminate()
    agentMoverSystem.terminate()
  }
}
