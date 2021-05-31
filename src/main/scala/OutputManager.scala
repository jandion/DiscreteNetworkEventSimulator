import java.io.{BufferedWriter, FileWriter}

import MessagesPatterns.{FlushOutput, ReceiveEvents}
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

object OutputManager {
  def apply(): Behavior[MessagesPatterns.Command] = Behaviors.setup[MessagesPatterns.Command] { context =>
    val bw = new BufferedWriter(new FileWriter("output_r"))
    Behaviors.receiveMessage {
      case ReceiveEvents( events) =>
        events.foreach(ev=>{
          bw.write(f"${ev.time},${ev.eventIndex}\n")
        })
        Behaviors.same
      case FlushOutput() =>
        bw.flush()
        System.exit(0)
        Behaviors.stopped
    }
  }
}
