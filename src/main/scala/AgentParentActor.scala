import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors

import scala.collection.mutable

class AgentParentActor(
    context: ActorContext[AgentParentActor.Commands],
    mover: ActorSystem[AgentMover.Commands]
) extends AbstractBehavior[AgentParentActor.Commands](context) {
  import AgentParentActor._

  var currentNumAgents: Int = 0
  var actorRefByAgentName: mutable.Map[String, ActorRef[AgentActor.Commands]] =
    mutable.Map.empty

  override def onMessage(msg: Commands): Behavior[Commands] =
    msg match {
      case SpawnAgents(num) =>
        println("got start message")
        (currentNumAgents to currentNumAgents + num).foreach { agentNum =>
          val name: String                       = s"agent$agentNum"
          val ref: ActorRef[AgentActor.Commands] = context.spawn(AgentActor(), name)
          mover ! AgentMover.AddAgent(ref)
          actorRefByAgentName += (name -> ref)
        }

        val agent1 = actorRefByAgentName.get("agent1")
        val agent2 = actorRefByAgentName.get("agent2")

        (agent1, agent2) match {
          case (Some(ref1), Some(ref2)) => ref1 ! AgentActor.TradeApple(0, 0, ref2)
          case _                        => println("could not find ref for both agents")
        }
        this
    }
}

object AgentParentActor {
  def apply(mover: ActorSystem[AgentMover.Commands]): Behavior[Commands] =
    Behaviors.setup(context => new AgentParentActor(context, mover))

  sealed trait Commands
  final case class SpawnAgents(num: Int) extends Commands

}
