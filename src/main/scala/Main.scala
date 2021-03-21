import akka.actor.typed.{ActorRef, ActorSystem}

object Main {
  def main(args: Array[String]): Unit = {
    println("Starting application")

    val system = ActorSystem(AgentParentActor(), "root")

    system ! "start"

    system.terminate()
  }

}
