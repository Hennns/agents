import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors
import akka.pattern.StatusReply

import scala.collection.mutable

case class Coordinates(x: Int, y: Int)

class AgentMover(context: ActorContext[AgentMover.Commands]) extends AbstractBehavior[AgentMover.Commands](context) {
  import AgentMover._

  var actorRefTracker: mutable.Map[ActorRef[AgentActor.Commands], Coordinates] =
    mutable.Map.empty

  override def onMessage(msg: Commands): Behavior[Commands] =
    msg match {
      case AddAgent(agent) =>
        val initialLocation = Coordinates(10, 10)
        actorRefTracker += ((agent, initialLocation))
        this
      case MoveAgent(coordinates, agent) =>
        val currentLocation = actorRefTracker.getOrElse(agent, Coordinates(0, 0))
        val newLocation     = Coordinates(currentLocation.x + coordinates.x, currentLocation.y + coordinates.y)
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
  final case class AddAgent(agent: ActorRef[AgentActor.Commands])                            extends Commands
  final case class MoveAgent(coordinates: Coordinates, agent: ActorRef[AgentActor.Commands]) extends Commands
  final case class GetAgentPositions(
      replyTo: ActorRef[StatusReply[Map[ActorRef[AgentActor.Commands], Coordinates]]]
  ) extends Commands

}
