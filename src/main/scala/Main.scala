import akka.actor.typed.ActorSystem
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

import scala.collection.immutable.VectorBuilder
import scala.math.random

object Main extends JFXApp {

  println("Starting application")

  val system: ActorSystem[AgentParentActor.Commands] =
    ActorSystem(AgentParentActor(), "AgentParentActor")
  system ! AgentParentActor.SpawnAgents(2)

  // TODO actually draw the agents
  val circlesToAnimate = new VectorBuilder[Circle]()
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
              val circles: Seq[Circle] = for (i <- 0 until 20) yield new Circle {
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
