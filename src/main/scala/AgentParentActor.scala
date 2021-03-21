import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors

class AgentParentActor(context: ActorContext[String])
    extends AbstractBehavior[String](context) {
  override def onMessage(msg: String): Behavior[String] =
    msg match {
      case "start" =>
        println("got start message")
        val agent1 = context.spawn(AgentActor(), "agent1")
        val agent2 = context.spawn(AgentActor(), "agent2")

        agent1 ! AgentActor.TradeApple(0, 0, agent2)

        this
    }
}

object AgentParentActor {
  def apply(): Behavior[String] =
    Behaviors.setup(context => new AgentParentActor(context))

}
