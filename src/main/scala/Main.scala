import Main.system
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout
import scalafx.Includes.{at, _}
import scalafx.animation.Timeline
import scalafx.animation.Timeline.Indefinite
import scalafx.application.JFXApp
import scalafx.application.JFXApp.PrimaryStage
import scalafx.scene.effect.BoxBlur
import scalafx.scene.paint.Color.{Black, White}
import scalafx.scene.shape.StrokeType.Outside
import scalafx.scene.shape.{Circle, Rectangle}
import scalafx.scene.{Group, Scene}

import java.lang.Runnable
import scala.collection.immutable.VectorBuilder
import scala.concurrent.duration.DurationInt
import scala.math.random
import scala.util.{Failure, Success}

object Main extends JFXApp {

  println("Starting application")

  implicit val timeout: Timeout = 3.seconds

  val m: ActorSystem[AgentMover.Commands] =
    ActorSystem(AgentMover(), "AgentMover")

  implicit val system: ActorSystem[AgentParentActor.Commands] =
    ActorSystem(AgentParentActor(m), "AgentParentActor")

  system ! AgentParentActor.SpawnAgents(2)

  def FetchAgentPositions(): Runnable = { () =>
    {
      {
        m.askWithStatus(AgentMover.GetAgentPositions)
          .onComplete {
            case Failure(exception) => println(s"error $exception")
            case Success(value)     => updateNumAgents(value.keys.size)
          }(m.executionContext)
      }
    }
  }

  var numAgents        = 1
  val circlesToAnimate = new VectorBuilder[Circle]()
  def updateNumAgents(num: Int): Unit = {
    numAgents = num
  }

  m.scheduler.scheduleAtFixedRate(10.seconds, 1.seconds) {
    FetchAgentPositions()
  }(m.executionContext)

  def printNumAgents(): Runnable = { () =>
    {
      println(s"num agents is $numAgents")
    }
  }
  m.scheduler.scheduleAtFixedRate(10.seconds, 1.seconds) {
    printNumAgents()
  }(m.executionContext)

  stage = new PrimaryStage {
    width = 800
    height = 600
    scene = new Scene {
      _scene =>
      fill = Black
      content = Seq(
        new Group {
          children = Seq(
            new Rectangle {
              width <== _scene.width
              height <== _scene.height
              fill = Black
            },
            new Group {
              val circles: Seq[Circle] =
                for (i <- 0 until numAgents) yield new Circle {
                  radius = 70
                  fill = White opacity 0.05
                  stroke = White opacity 0.1
                  strokeWidth = 2
                  strokeType = Outside
                }
              children = circles
              circlesToAnimate ++= circles
              effect = new BoxBlur(2, 2, 2)
            }
          )
        }
      )
    }
  }
  new Timeline {
    cycleCount = Indefinite
    autoReverse = true
    keyFrames = (for (circle <- circlesToAnimate.result())
      yield Seq(
        at(0.s) {
          Set(circle.centerX -> random * 800, circle.centerY -> random * 600)
        },
        at(40.s) {
          Set(circle.centerX -> random * 800, circle.centerY -> random * 600)
        }
      )).flatten
  }.play()

  // When the application terminates, output the mean frame rate.
  override def stopApp(): Unit = {
    system.terminate()
  }
}
