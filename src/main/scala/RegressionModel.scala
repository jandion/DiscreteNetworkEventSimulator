import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}


object RegressionModel{

  def apply(targetEvent:Int, curve_x:Array[Double],curve_y:Array[Long],queueActor:ActorRef[MessagesPatterns.Command]): Behavior[MessagesPatterns.Command] =
    Behaviors.setup(context => new RegressionModel(context, targetEvent, curve_x, curve_y,queueActor))

}

class RegressionModel(context: ActorContext[MessagesPatterns.Command],
                      targetEvent:Int,
                      curve_x:Array[Double], curve_y:Array[Long],queueActor:ActorRef[MessagesPatterns.Command])
  extends AbstractBehavior[MessagesPatterns.Command](context) {

  import MessagesPatterns._

  var activationTime: Long = Long.MinValue

  def processEvent(e: Seq[Event]): Unit = {
    if(e.nonEmpty && e.head.time >= activationTime) {
      //Make regression
      val time2next = curve_y(curve_x.search(scala.math.random()).insertionPoint)
      activationTime = e.head.time + time2next
      queueActor ! AddEvent(Event(targetEvent, activationTime))
    }
  }

  override def onMessage(msg: Command): Behavior[Command] = {
    msg match {
      case ReceiveEvents(events) =>
        processEvent(events)
        queueActor ! ACK()
        this
    }
  }
}
