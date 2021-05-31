
import akka.actor.typed.ActorRef
object MessagesPatterns{

  sealed trait Command
  final case class ReceiveEvents(events: Seq[Event]) extends Command
  final case class FlushOutput() extends Command

  final case class RegisterModel(modelName: String, model: ActorRef[MessagesPatterns.Command]) extends Command
  final case class AddEvent(event: Event) extends Command


  final case class GenerateEventBatch() extends Command
  final case class GenerateEventRealTime() extends Command
  final case class ACK() extends Command
}
