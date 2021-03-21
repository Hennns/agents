import AgentActor.Commands
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors

class AgentActor(context: ActorContext[Commands])
    extends AbstractBehavior[Commands](context) {
  import AgentActor._

  var myApples: Int  = 0
  var myOranges: Int = 0

  override def onMessage(msg: Commands): Behavior[Commands] =
    msg match {
      case TradeApple(apples, oranges, replyTo) =>
        // Check if trade is acceptable
        val tradePossible = true
        if (tradePossible) {
          replyTo ! TradeAccepted
        } else {
          replyTo ! TradeRejected
        }
        this

      case TradeAccepted =>
        println("Trade completed")
        this

      case TradeRejected =>
        println("Trade was rejected")
        this

    }

}

object AgentActor {
  def apply(): Behavior[Commands] =
    Behaviors.setup(context => new AgentActor(context))

  sealed trait Commands

  sealed trait Request extends Commands

  sealed trait RequireResponse extends Request {
    val replyTo: ActorRef[Response]
  }
  final case class TradeApple(Apples: Int, Oranges: Int, replyTo: ActorRef[Response])
      extends RequireResponse

  sealed trait Response           extends Commands
  final case object TradeAccepted extends Response
  final case object TradeRejected extends Response

}
