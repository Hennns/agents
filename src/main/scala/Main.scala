import akka.actor.typed.ActorSystem

object Main {
  def main(args: Array[String]): Unit = {
    println("Starting application")

    val system = ActorSystem(AgentParentActor(), "AgentParentActor")
    system ! AgentParentActor.SpawnAgents(2)

    system.terminate()
  }

}
