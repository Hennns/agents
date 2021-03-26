import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout
import scalafx.application.JFXApp
import scalafx.application.JFXApp.PrimaryStage
import scalafx.scene.Scene
import scalafx.scene.control.Label
import scalafx.scene.layout.Pane

import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

object Main extends JFXApp {

  println("Starting application")

  implicit val timeout: Timeout = 3.seconds

  val m: ActorSystem[AgentMover.Commands] =
    ActorSystem(AgentMover(), "AgentMover")

  implicit val system: ActorSystem[AgentParentActor.Commands] =
    ActorSystem(AgentParentActor(m), "AgentParentActor")

  system ! AgentParentActor.SpawnAgents(2)

  def updateNumAgents(): Runnable = { () =>
    {
      {
        m.askWithStatus(AgentMover.GetAgentPositions)
          .onComplete {
            case Failure(exception) => println(s"error $exception")
            case Success(value)     => numAgents = value.keys.size
          }(m.executionContext)
      }
    }
  }

  var numAgents = 1

  stage = new PrimaryStage {
    title = "Agents"
  }

  val canvas = new Pane()
  val scene  = new Scene(canvas, 800, 600)
  stage.setScene(scene)
  stage.show
  val label = new Label("This is an agent")
  canvas.getChildren.add(label)

  m.scheduler.scheduleAtFixedRate(10.seconds, 1.seconds) {
    updateNumAgents()
  }(m.executionContext)

  def UpdateAgents(): Runnable = { () =>
    {
      label.relocate(label.getLayoutX + 10, 100)
      println(s"num agents is $numAgents")
    }
  }
  m.scheduler.scheduleAtFixedRate(10.seconds, 1.seconds) {
    UpdateAgents()
  }(m.executionContext)

  override def stopApp(): Unit = {
    system.terminate()
    m.terminate()
  }
}
