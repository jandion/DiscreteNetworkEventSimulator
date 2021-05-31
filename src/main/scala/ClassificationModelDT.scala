import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import smile.classification.DecisionTree
import smile.data.Tuple

import scala.collection.mutable

object ClassificationModelDT{

  def apply(windowSize:Long, leadTime:Long, targetEvent:Int, featureTransformation:Map[Int,Int], model: DecisionTree,queueActor:ActorRef[MessagesPatterns.Command]): Behavior[MessagesPatterns.Command] =
    Behaviors.setup(context => new ClassificationModelDT(context, windowSize, leadTime, targetEvent, featureTransformation, model,queueActor))

}

class ClassificationModelDT(context: ActorContext[MessagesPatterns.Command],
                            windowSize:Long, leadTime:Long, targetEvent:Int,
                            featureTransformation:Map[Int,Int],
                            model: DecisionTree,queueActor:ActorRef[MessagesPatterns.Command])
  extends AbstractBehavior[MessagesPatterns.Command](context) {

  import MessagesPatterns._

  var activationTime: Long = Long.MinValue
  var passByZero:Boolean = true
  val queue: mutable.Queue[Event] = mutable.Queue[Event]()
  val observationWindow: Array[Double] = Array.fill(featureTransformation.size)(0.0)
  //val schema: StructType = DataTypes.struct((0 until featureTransformation.size).map(x=> new StructField(f"_${x}", DataTypes.DoubleType)):_*)

  def processEvent(e: Seq[Event]): Unit = {
    val eventTime = e.head.time
    val indexes = e.map(_.eventIndex).filter(featureTransformation.contains).map(featureTransformation)
    //If event isn't in featureTransformation do nothing
    if (indexes.nonEmpty) {

      //Reindex and enqueue
      indexes.foreach(index => {
        queue.enqueue(Event(index, eventTime))
        observationWindow.update(index, observationWindow(index) + 1)
      })

      //Discard all events outside of observation window
      queue.dequeueWhile(ev => ev.time < eventTime - windowSize).foreach(removed=>
        observationWindow.update(removed.eventIndex, observationWindow(removed.eventIndex) - 1))


      val prediction: Boolean = model.predict(Tuple.of(observationWindow, model.schema())) > 0 //make predicition
      if(!prediction) passByZero = true
      if (eventTime >= activationTime && prediction && passByZero) {
        activationTime = eventTime + leadTime
        passByZero = false
        queueActor ! AddEvent(Event(targetEvent, eventTime + leadTime))
      }
    }
  }

  override def onMessage(msg: Command): Behavior[Command] = {
    msg match {
      case ReceiveEvents( events) =>
        processEvent(events)
        queueActor ! ACK()
        this
    }
  }
}
