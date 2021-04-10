package agents

import agents.Main.{Rectangle, getAgentBorders, getRectangleMatrix}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.pattern.StatusReply

import scala.collection.mutable
import scala.math.abs
import scala.util.Random

case class PositionData(x: Double, y: Double, xVector: Double, yVector: Double, row: Int, column: Int) {

  def move(): PositionData = {
    val (canvasHeight: Double, canvasWidth: Double) = getAgentBorders
    val newX: Double                                = x + xVector
    val newY: Double                                = y + yVector
    val newXVector: Double = newX match {
      case nextX if nextX > canvasWidth - agentRadius => -math.abs(xVector)
      case nextX if nextX < 0                         => math.abs(xVector)
      case _                                          => xVector
    }
    val newYVector: Double = newY match {
      case nextY if nextY > canvasHeight - agentRadius => -math.abs(yVector)
      case nextY if nextY < 0                          => math.abs(yVector)
      case _                                           => yVector
    }

    if (!getRectangleMatrix(row)(column).intersectsPoint(newX, newY)) {

      val t = getRectangleMatrix
      println(t)

      // TODO check surrounding boxes, then use this as fallback
      // TODO cleanup .head etc. !
      val (rowRect, rowIndex) = getRectangleMatrix.zipWithIndex.collectFirst {
        case (rows, index) if (rows.head.y <= newY) && (newY <= rows.head.y + rows.head.heigth) =>
          (rows, index)
      } match {
        case Some(value) => value
        case None        => (Vector.empty, 0)
      }
      val columnIndex = rowRect.zipWithIndex.collectFirst {
        case (rect, index) if (rect.x <= newX) && (newX <= rect.x + rect.width) => index
      } match {
        case Some(value) => value
        case None        => 0
      }
      this.copy(newX, newY, newXVector, newYVector, rowIndex, columnIndex)
    } else {
      this.copy(newX, newY, newXVector, newYVector)
    }
  }

  def intersectOtherAgent(other: PositionData): Boolean = {
    (abs(x - other.x) < agentRadius) &&
    (abs(y - other.y) < agentRadius)
  }

}
object PositionData {
  def apply(): PositionData = {
    // TODO add max and min values
    // TODO place in correct box on apply
    PositionData(0, 0, Random.nextDouble(), Random.nextDouble(), 0, 0)
  }
}

class AgentMover(context: ActorContext[AgentMover.Commands]) extends AbstractBehavior[AgentMover.Commands](context) {
  import AgentMover._

  var actorRefTracker: mutable.Map[ActorRef[AgentActor.Commands], PositionData] =
    mutable.Map.empty[ActorRef[AgentActor.Commands], PositionData]

//  var boxTracker: Array[Array[mutable.Set[ActorRef[AgentActor.Commands]]]] =
//    Array
//      .ofDim[mutable.Set[ActorRef[AgentActor.Commands]]](getRectangleMatrix().length, getRectangleMatrix().head.length)

  // TODO the dimensions should be based on getRectangleMatrix().length and getRectangleMatrix().head.length
  // But it cannot be called during init, since it's not defined yet.
  // Use nested map instead?
  // mutable.Map[Int, [mutable.Map[Int, [mutable.Map[ActorRef[AgentActor.Commands]], PositionData]]]]]
  var boxTracker: Array[Array[mutable.Map[ActorRef[AgentActor.Commands], PositionData]]] =
    Array.tabulate(30)(_ => Array.tabulate(30)(_ => mutable.Map.empty[ActorRef[AgentActor.Commands], PositionData]))

  override def onMessage(msg: Commands): Behavior[Commands] =
    msg match {
      case MoveAgent(agent) =>
        val oldLocation: PositionData = actorRefTracker.get(agent) match {
          case Some(positionVector) => positionVector
          case None                 => PositionData()
        }

        val newLocation: PositionData = oldLocation.move()

        if (newLocation.row != oldLocation.row || newLocation.column != newLocation.column) {
          // Remove from the old box
          context.log.debug("Removing {} from row: {}, column: {}", agent, oldLocation.row, oldLocation.column)
          boxTracker(oldLocation.row).update(oldLocation.row, boxTracker(oldLocation.row)(oldLocation.column) -= agent)

          // Add to the new box
          // TODO add to the box when a new agent is added, currently it won't be added until first time it switches
          context.log.debug("Adding {} to row: {}, column: {}", agent, newLocation.row, newLocation.column)
          boxTracker(newLocation.row)
            .update(newLocation.row, boxTracker(newLocation.row)(newLocation.column) += agent -> newLocation)
        }

        // Return a Boolean
        def checkCollision(): Option[ActorRef[AgentActor.Commands]] = {

          def collectFirstCollidingAgent(
              agentMap: Map[ActorRef[AgentActor.Commands], PositionData]
          ): Option[ActorRef[AgentActor.Commands]] = {
            agentMap.collectFirst {
              case (ref, posData) if newLocation.intersectOtherAgent(posData) && (ref != agent) =>
                ref
            }
          }

          // Check agents in the same box first
          val sameBoxAgents: Map[ActorRef[AgentActor.Commands], PositionData] =
            boxTracker(newLocation.row)(newLocation.column).toMap

          // Then check left/right/above/below
          lazy val leftBoxAgents  = Map.empty[ActorRef[AgentActor.Commands], PositionData]
          lazy val rightBoxAgents = Map.empty[ActorRef[AgentActor.Commands], PositionData]
          lazy val aboveBoxAgents = Map.empty[ActorRef[AgentActor.Commands], PositionData]
          lazy val belowBoxAgents = Map.empty[ActorRef[AgentActor.Commands], PositionData]

          // Then check diagonally
          lazy val aboveLeftBoxAgents  = Map.empty[ActorRef[AgentActor.Commands], PositionData]
          lazy val aboveRightBoxAgents = Map.empty[ActorRef[AgentActor.Commands], PositionData]
          lazy val belowLeftBoxAgents  = Map.empty[ActorRef[AgentActor.Commands], PositionData]
          lazy val belowRightBoxAgents = Map.empty[ActorRef[AgentActor.Commands], PositionData]

          val temp1      = collectFirstCollidingAgent(sameBoxAgents)
          lazy val temp2 = collectFirstCollidingAgent(leftBoxAgents)
          lazy val temp3 = collectFirstCollidingAgent(rightBoxAgents)
          lazy val temp4 = collectFirstCollidingAgent(aboveBoxAgents)
          lazy val temp5 = collectFirstCollidingAgent(belowBoxAgents)
          lazy val temp6 = collectFirstCollidingAgent(aboveLeftBoxAgents)
          lazy val temp7 = collectFirstCollidingAgent(aboveRightBoxAgents)
          lazy val temp8 = collectFirstCollidingAgent(belowLeftBoxAgents)
          lazy val temp9 = collectFirstCollidingAgent(belowRightBoxAgents)

          if (temp1.isDefined) temp1
          else if (temp2.isDefined) temp2
          else None

        }

        // TODO
        def triggerTrade(ref: ActorRef[AgentActor.Commands]): Unit = {
          context.log.info("Trigger trade!")
        }

        checkCollision() match {
          case Some(ref) => triggerTrade(ref)
          case None      => ()
        }

        actorRefTracker.update(agent, newLocation)
        this

      case AgentMover.GetAgentPositions(replyTo) =>
        replyTo ! StatusReply.success(actorRefTracker.toMap)
        this
    }
}

object AgentMover {
  def apply(): Behavior[Commands] =
    Behaviors.setup(context => new AgentMover(context))

  sealed trait Commands
  final case class MoveAgent(agent: ActorRef[AgentActor.Commands]) extends Commands
  final case class GetAgentPositions(
      replyTo: ActorRef[StatusReply[Map[ActorRef[AgentActor.Commands], PositionData]]]
  ) extends Commands

}
