
import breeze.linalg.{DenseVector, SparseVector}

import scala.collection.mutable
import scala.concurrent.duration.Duration
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.scaladsl.ActorContext
import smile.classification.{Classifier, DataFrameClassifier, FLD, OnlineClassifier, RBFNetwork, RandomForest, SVM, SoftClassifier}
import smile.data.Tuple
import smile.data.`type`.{DataTypes, StructField, StructType}

object ClassificationModel{

  def apply(windowSize:Long, leadTime:Long, targetEvent:Int, featureTransformation:Map[Int,Int], model: Classifier[Array[Double]],queueActor:ActorRef[MessagesPatterns.Command]): Behavior[MessagesPatterns.Command] =
    Behaviors.setup(context => new ClassificationModel(context, windowSize, leadTime, targetEvent, featureTransformation, model,queueActor))

}

  class ClassificationModel(context: ActorContext[MessagesPatterns.Command],
                            windowSize:Long, leadTime:Long, targetEvent:Int,
                            featureTransformation:Map[Int,Int],
                            model: Classifier[Array[Double]],queueActor:ActorRef[MessagesPatterns.Command])
    extends AbstractBehavior[MessagesPatterns.Command](context) {

    import MessagesPatterns._

    var activationTime: Long = _
    val queue: mutable.Queue[Event] = mutable.Queue[Event]()
    val observationWindow: Array[Double] = Array.fill(featureTransformation.size)(0.0)

    def processEvent(e: Seq[Event]): Option[Event] = {
      val eventTime = e.head.time
      val indexes = e.map(_.eventIndex).filter(featureTransformation.contains).map(featureTransformation)
      //If event isn't in featureTransformation do nothing
      if (indexes.isEmpty) None

      //Reindex and enqueue
      indexes.foreach(index => {
        queue.enqueue(Event(index, eventTime))
        observationWindow.update(index, observationWindow(index) + 1)
      })

      //Discard all events outside of observation window
      //In scala 2.13 change by dequeue
      while (queue.front.time < eventTime - windowSize) {
        val removed = queue.dequeue()
        observationWindow.update(removed.eventIndex, observationWindow(removed.eventIndex) - 1)
      }

val prediction = true
      if (eventTime >= activationTime && prediction) {
        activationTime = eventTime + leadTime
        Some(Event(targetEvent, eventTime + leadTime))
      } else {
        None
      }
    }

    override def onMessage(msg: Command): Behavior[Command] = {
      msg match {
        case ReceiveEvents( events) =>
          processEvent(events)
          this
      }
    }
  }
