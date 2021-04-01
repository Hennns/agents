package agents

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}

import scala.util.{Failure, Success, Try}

class AgentActor(
    context: ActorContext[AgentActor.Commands],
    val prefApples: Float,
    val prefOranges: Float
) extends AbstractBehavior[AgentActor.Commands](context) {
  import AgentActor._

  var myApples: Int  = 100
  var myOranges: Int = 100

  override def onMessage(msg: Commands): Behavior[Commands] =
    msg match {
      case TradeApple(apples, oranges, replyTo) =>
        context.log.info("Mrs Apples is {}", AgentActor.mrsApples(prefApples, prefOranges, myApples, myOranges))
        // Check if trade is acceptable
        val tradePossible = true
        if (tradePossible) {
          replyTo ! TradeAccepted
        } else {
          replyTo ! TradeRejected
        }
        this

      case TradeAccepted =>
        context.log.info("Trade completed")
        context.log.info("Mrs Apples is {}", AgentActor.mrsApples(prefApples, prefOranges, myApples, myOranges))
        this

      case TradeRejected =>
        context.log.info("Trade was rejected")
        this
    }

}

object AgentActor {
  def apply(): Behavior[Commands] = {
    val prefApples: Float  = scala.util.Random.nextFloat()
    val prefOranges: Float = 1 - prefApples

    Behaviors.setup(context => new AgentActor(context, prefApples, prefOranges))
  }

  def mrsApples(
      prefApples: Float,
      prefOranges: Float,
      numApples: Int,
      numOranges: Int
  ): Float = {
    Try {
      prefApples * numOranges / prefOranges * numApples
    } match {
      case Success(value)     => value
      case Failure(exception) => 0

    }

  }

  sealed trait Commands

  sealed trait Request extends Commands

  sealed trait RequireResponse extends Request {
    val replyTo: ActorRef[Response]
  }
  final case class TradeApple(Apples: Int, Oranges: Int, replyTo: ActorRef[Response]) extends RequireResponse

  sealed trait Response           extends Commands
  final case object TradeAccepted extends Response
  final case object TradeRejected extends Response

}
