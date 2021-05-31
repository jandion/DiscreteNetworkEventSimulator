import akka.actor.typed.ActorSystem

object Main {

  def main(args: Array[String]): Unit = {

    ActorSystem[Nothing](SimulatorSupervisor(), "simulator")

  }

}
