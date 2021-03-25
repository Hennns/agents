import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors

import scala.collection.mutable

case class Coordinates(x: Int, y: Int)

class AgentMover(context: ActorContext[AgentMover.Commands])
    extends AbstractBehavior[AgentMover.Commands](context) {
  import AgentMover._

  var actorRefTracker: mutable.Map[ActorRef[AgentActor], Coordinates] =
    mutable.Map.empty

  override def onMessage(msg: Commands): Behavior[Commands] =
    msg match {
      case AddAgent(agent) =>
        val initialLocation = Coordinates(10, 10)
        actorRefTracker += ((agent, initialLocation))
        this
      case MoveAgent(coordinates, agent) => this
    }
}

object AgentMover {
  def apply(): Behavior[Commands] =
    Behaviors.setup(context => new AgentMover(context))

  sealed trait Commands
  final case class AddAgent(agent: ActorRef[AgentActor]) extends Commands
  final case class MoveAgent(coordinates: Coordinates, agent: ActorRef[AgentActor])
      extends Commands

}
