package agents

import agents.Main.{Rectangle, getAgentBorders, getRectangleMatrix}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.pattern.StatusReply

import scala.collection.mutable
import scala.math.abs
import scala.util.Random

case class PositionVector(x: Double, y: Double, xVector: Double, yVector: Double) {
  def move(): PositionVector = {
    val (canvasHeight: Double, canvasWidth: Double) = getAgentBorders
    val newX: Double                                = x + xVector
    val newY: Double                                = y + yVector
    val newXVector: Double = newX match {
      case nextX if nextX > canvasWidth - agentRadius => -math.abs(xVector)
      case nextX if nextX < 0 + agentRadius           => math.abs(xVector)
      case _                                          => xVector
    }
    val newYVector: Double = newY match {
      case nextY if nextY > canvasHeight - agentRadius => -math.abs(yVector)
      case nextY if nextY < 0 + agentRadius            => math.abs(yVector)
      case _                                           => yVector
    }
    this.copy(newX, newY, newXVector, newYVector)
  }
}
object PositionVector {
  def apply(): PositionVector = {
    // TODO add max and min values
    PositionVector(10, 10, Random.nextDouble(), Random.nextDouble())
  }
}

class AgentMover(context: ActorContext[AgentMover.Commands]) extends AbstractBehavior[AgentMover.Commands](context) {
  import AgentMover._

  var actorRefTracker: mutable.Map[ActorRef[AgentActor.Commands], PositionVector] =
    mutable.Map.empty

//  var boxTracker: Array[Array[mutable.Set[ActorRef[AgentActor.Commands]]]] =
//    Array
//      .ofDim[mutable.Set[ActorRef[AgentActor.Commands]]](getRectangleMatrix().length, getRectangleMatrix().head.length)

  // TODO the dimensions should be based on getRectangleMatrix().length and getRectangleMatrix().head.length
  // But it cannot be called during init, since it's not defined yet.
  var boxTracker: Array[Array[mutable.Set[ActorRef[AgentActor.Commands]]]] =
    Array.tabulate(30)(_ => Array.tabulate(30)(_ => mutable.Set.empty[ActorRef[AgentActor.Commands]]))

  override def onMessage(msg: Commands): Behavior[Commands] =
    msg match {
      case MoveAgent(agent) =>
        val newLocation = actorRefTracker.get(agent) match {
          case Some(positionVector) => positionVector.move()
          case None                 => PositionVector()
        }

        // TODO this is very ugly and proof of concept
        // Need to actually remove the agent from the box when it switches
        // Use the boxes to speed up collision detection algorithm
        // Remove unsafe .get and .head!
        val (rowRect, xIndex) = getRectangleMatrix.zipWithIndex.collectFirst {
          case (rows, index) if (rows.head.x <= newLocation.x) && (newLocation.x <= rows.head.x + rows.head.width) =>
            (rows, index)
        }.get

        val yIndex = rowRect.zipWithIndex.collectFirst {
          case (rect, index) if (rect.y <= newLocation.y) && (newLocation.y <= rect.y + rect.heigth) => index
        }.get

        boxTracker(xIndex).update(xIndex, boxTracker(xIndex)(yIndex) += agent)

        context.log.debug("Logging elements in the boxTracker")
        boxTracker.foreach { row =>
          row.foreach { list =>
            context.log.info("There is {} elements in this box", list.size)
          }
        }
        context.log.debug("Done Logging elements in the boxTracker")

        def checkCollision(): Unit = {
          actorRefTracker
            .collectFirst {
              case (ref, vector)
                  if (abs(newLocation.x - vector.x) < agentRadius) &&
                    (abs(newLocation.y - vector.y) < agentRadius) &&
                    (ref != agent) =>
                ref
            }
            .foreach { ref =>
              context.log.debug(s"agent = {}, {}", newLocation.x, newLocation.y)
              val x = actorRefTracker(ref)
              context.log.debug(s"ref = {}, {}", x.x, x.y)
              context.log.info(s"{} collided with {}", ref, agent)
            }
        }

        checkCollision()

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
      replyTo: ActorRef[StatusReply[Map[ActorRef[AgentActor.Commands], PositionVector]]]
  ) extends Commands

}
