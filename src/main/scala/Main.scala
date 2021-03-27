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
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

object Main extends JFXApp {

  println("Starting application")

  val agentMoverSystem: ActorSystem[AgentMover.Commands] = ActorSystem(AgentMover(), "AgentMover")
  implicit val ec: ExecutionContextExecutor              = agentMoverSystem.executionContext

  implicit val timeout: Timeout = 3.seconds
  implicit val agentParentSystem: ActorSystem[AgentParentActor.Commands] =
    ActorSystem(AgentParentActor(agentMoverSystem), "AgentParentActor")

  var actorLabelMap: mutable.Map[ActorRef[AgentActor.Commands], Label] = mutable.Map.empty

  agentParentSystem ! AgentParentActor.SpawnAgents(2)

  val canvas: Pane = new Pane()
  stage = new PrimaryStage {
    title = "Agents"
    scene = new Scene(canvas, 800, 600)
  }
  stage.show

  def updateAgentPositions(): Runnable = { () =>
    agentMoverSystem
      .askWithStatus(AgentMover.GetAgentPositions)
      .onComplete {
        case Failure(exception) => println(s"error $exception")
        case Success(value) =>
          println("got value")
          value.foreach {
            case (ref, coordinates) =>
              val label: Label = actorLabelMap.getOrElseUpdate(ref, addNewLabel())
              println(s"relocating $label")
              label.relocate(coordinates.x, coordinates.y)
          }
      }
  }

  def addNewLabel(): Label = {
    println("creating new label")
    val newLabel = new Label("this is a label")
    // Modifying GUI elements needs to be done on the JavaFX Application Thread
    Platform.runLater {
      canvas.getChildren.add(newLabel)
    }
    newLabel
  }

  agentMoverSystem.scheduler.scheduleAtFixedRate(10.seconds, 1.seconds) { updateAgentPositions() }

  override def stopApp(): Unit = {
    println("terminating system")
    agentParentSystem.terminate()
    agentMoverSystem.terminate()
  }
}
