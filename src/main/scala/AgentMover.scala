import Main.agentRadius
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.pattern.StatusReply

import scala.collection.mutable
import scala.math.abs
import scala.util.Random

case class PositionVector(x: Double, y: Double, xVector: Double, yVector: Double) {
  def move(): PositionVector = {
    this.copy(x = x + xVector, y = y + yVector)
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

  override def onMessage(msg: Commands): Behavior[Commands] =
    msg match {
      case MoveAgent(agent) =>
        val newLocation = actorRefTracker.get(agent) match {
          case Some(positionVector) => positionVector.move()
          case None                 => PositionVector()
        }

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
