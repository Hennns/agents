import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors

class Root(context: ActorContext[String]) extends AbstractBehavior[String](context) {
  override def onMessage(msg: String): Behavior[String] =
    msg match {
      case "start" =>
        println("got start message")
        this
    }
}

object Root {
  def apply(): Behavior[String] =
    Behaviors.setup(context => new Root(context))

}

object Main {
  def main(args: Array[String]): Unit = {
    println("Starting application")

    val testSystem = ActorSystem(Root(), "root")
    testSystem ! "start"

    testSystem.terminate()
  }

}
