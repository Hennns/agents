package agents

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.util.Timeout
import scalafx.Includes._
import scalafx.application.JFXApp.PrimaryStage
import scalafx.application.{JFXApp, Platform}
import scalafx.beans.binding.{Bindings, ObjectBinding}
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

  val actorLabelMap: ObservableMap[ActorRef[AgentActor.Commands], Circle] = ObservableMap.empty

  val agentParentSystem: ActorSystem[AgentParentActor.Commands] =
    ActorSystem(AgentParentActor(agentMoverSystem), "AgentParentActor")

  val stroke: BorderStroke =
    new BorderStroke(Color.Black, BorderStrokeStyle.Solid, new CornerRadii(agentCircleRadius), BorderWidths.Default)
  val canvasBorder: Border = new Border(stroke)
  val agentCanvas: Pane    = new Pane()

  val spawnAgentsButton: Button = new Button("Spawn Agents") {
    minHeight = buttonsMinHeight
    maxHeight = buttonsMaxHeight
    maxWidth = 100
    minWidth = 100
    onAction = (e: ActionEvent) => {
      agentParentSystem.log.info("button clicked")
      agentParentSystem ! AgentParentActor.SpawnAgents(10)
    }
  }
  val buttonCanvas: Pane    = new Pane()
  val stageCanvas: GridPane = new GridPane()

  buttonCanvas.getChildren.add(spawnAgentsButton)
  buttonCanvas.minHeight(buttonsMinHeight)
  buttonCanvas.maxHeight(buttonsMaxHeight)

  agentCanvas.setBorder(canvasBorder)
  agentCanvas.prefHeight.bind(sceneHeight - buttonsMaxHeight)
  agentCanvas.prefWidth.bind(sceneWidth)

  stageCanvas.addRow(0, buttonCanvas)
  stageCanvas.addRow(1, agentCanvas)

  stage = new PrimaryStage {
    title = "Agents"
    scene = new Scene(stageCanvas, sceneWidth, sceneHeight)
  }
  stage.show

  private def moveAgents(): Unit = {
//    agentMoverSystem.log.debug("Moving {} agents", actorLabelMap.size)
    actorLabelMap.foreach {
      case (ref, _) =>
        agentMoverSystem ! AgentMover.MoveAgent(ref)
    }
  }

  private def updateAgentPositions(): Runnable = { () =>
    agentMoverSystem
      .askWithStatus(AgentMover.GetAgentPositions)
      .onComplete {
        case Failure(exception) => agentMoverSystem.log.error("Failed to get agent positions {}", exception)
        case Success(value)     =>
//          agentMoverSystem.log.debug("got {} values", value.size)
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
  private def addCircle(circle: Circle): Unit = Platform.runLater { agentCanvas.getChildren.add(circle) }

  private def makeNewCircle(): Circle = {
    agentMoverSystem.log.debug("creating a new circle")
    val newCircle = Circle(agentCircleRadius)
    newCircle.setStroke(Color.Black)
    newCircle.setStrokeWidth(agentStrokeWidth)
    newCircle.setStrokeType(StrokeType.Inside)
    newCircle.setFill(Color.DeepSkyBlue)
    newCircle
  }

  agentMoverSystem.scheduler.scheduleAtFixedRate(5.seconds, 0.1.seconds) { updateAgentPositions() }

  def getAgentBorders: (Double, Double) = (agentCanvas.height.value, agentCanvas.width.value)

  def getRectangleMatrix: Vector[Vector[Rectangle]] = rectangleMatrix.value

  case class Rectangle(x: Int, y: Int, width: Int, heigth: Int) {
    def intersectsPoint(pointX: Double, pointY: Double): Boolean = {
      x < pointX && (pointX < x + width) && y < pointY && (pointY < y + heigth)
    }
  }

  // Rows, the first dimension, increases y values
  // Columns, the second dimension, increases x values
  private lazy val rectangleMatrix: ObjectBinding[Vector[Vector[Rectangle]]] = Bindings.createObjectBinding(
    () => {
      val rows: Vector[Int]    = Vector.range(0, ((agentCanvas.height.value / agentRadius) / 2).toInt)
      val columns: Vector[Int] = Vector.range(0, ((agentCanvas.width.value / agentRadius) / 2).toInt)
      val height: Int          = math.ceil(agentCanvas.height.value / rows.length).toInt
      val width: Int           = math.ceil(agentCanvas.width.value / columns.length).toInt

      val rowsPlusOne    = rows :+ rows.lastOption.getOrElse(0) + 1
      val columnsPlusOne = columns :+ columns.lastOption.getOrElse(0) + 1

      val res =
        rowsPlusOne.map(row => columnsPlusOne.map(column => Rectangle(column * width, row * height, width, height)))
      res
    },
    agentCanvas.width,
    agentCanvas.height
  )

//  rectangleMatrix.addListener {
//    (
//        value: javafx.beans.value.ObservableValue[_ <: Vector[Vector[Rectangle]]],
//        oldVal: Vector[Vector[Rectangle]],
//        newVal: Vector[Vector[Rectangle]]
//    ) =>
//      agentMoverSystem.log.debug("rectangleMatrix has changed")
//  }

  override def stopApp(): Unit = {
    agentParentSystem.log.info("terminating agent parent system")
    agentParentSystem.terminate()

    agentMoverSystem.log.info("terminating agent mover system")
    agentMoverSystem.terminate()
    Platform.exit()
  }
}
