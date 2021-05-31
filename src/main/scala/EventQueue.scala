
import java.util.concurrent.atomic.AtomicInteger

import akka.Done
import akka.actor.Status.{Failure, Success}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, Scheduler}

import scala.collection.mutable
object EventQueue {
    import MessagesPatterns._
    private var lastTimeGenerated = Long.MinValue
    private var modelList = Map.empty[String, ActorRef[MessagesPatterns.Command]]
    private var simulationTime: Long = Long.MinValue
    private val eventQueue: mutable.PriorityQueue[Event] = mutable.PriorityQueue[Event]()

  def nextEvent(): Seq[Event] ={
    var events = Seq(eventQueue.dequeue)
    while(eventQueue.nonEmpty && events.head.time == eventQueue.head.time)
      events = events :+ eventQueue.dequeue
    lastTimeGenerated = events.head.time
    events
  }
  def nextEvent(simTime: Long): Seq[Event] = {
    if (simTime >= eventQueue.head.time) {
      nextEvent()
    } else
      Seq.empty[Event]
  }

  def enqueueEvent(e: Event): Unit ={
    if(lastTimeGenerated < e.time)
      eventQueue.enqueue(e)
  }

  def apply(batch:Boolean= false,limit:Long=Long.MaxValue,outputManager: ActorRef[MessagesPatterns.Command]): Behavior[MessagesPatterns.Command] = Behaviors.setup[MessagesPatterns.Command]{ context =>

      val ackCounter = new AtomicInteger(0)
      val eventCounter = new AtomicInteger(0)
      var initTime = -1L
      Behaviors.receiveMessage {
        case RegisterModel(modelName, modelRef) =>
          modelList += modelName -> modelRef
          Behaviors.same

        case AddEvent(event) =>
          enqueueEvent(event)
          Behaviors.same

        case GenerateEventRealTime() =>
          val events = nextEvent(simulationTime)
          simulationTime += 1
          context.log.info(f"$events")
          modelList.values.foreach(model=> model ! ReceiveEvents(events))
          outputManager ! ReceiveEvents(events)
          Behaviors.same

        case GenerateEventBatch() =>
          if(initTime == -1L) initTime=System.nanoTime()
          val events = nextEvent()
          eventCounter.addAndGet(events.size)
          if( events.head.time >= limit ){
            context.log.warn("Simulation ended")
            context.log.warn(f"${eventCounter.get()} events generated in ${System.nanoTime()-initTime} ns")
            outputManager ! FlushOutput()
            Behaviors.stopped
          }
          else {
            context.log.info(f"$events")
            modelList.values.foreach(model => model ! ReceiveEvents(events))
            outputManager ! ReceiveEvents(events)
            Behaviors.same
          }

        case ACK() =>
          if(batch & ackCounter.incrementAndGet() == modelList.size) {
            ackCounter.set(0)
            context.self ! GenerateEventBatch()
          }
          Behaviors.same
      }
    }

}

