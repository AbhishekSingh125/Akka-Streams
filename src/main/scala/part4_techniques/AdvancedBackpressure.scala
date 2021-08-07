package part4_techniques

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{Materializer, OverflowStrategy}

import java.util.Date
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

object AdvancedBackpressure extends App {

  /** Goal
   * Rates and Buffers
   * Coping with an un-backpressureable source (Fast source that cannot be back pressured)
   * Extrapolating / expanding (How to deal with a fast sink)
   * */

  val system = ActorSystem("AdvanceBackpressure")
  implicit val materialize = Materializer(system)

  // Control Backpressure
  val controlledFlow = Flow[Int].map(_ * 2).buffer(10, OverflowStrategy.dropHead)

  case class PagerEvent(description: String, date: Date, nInstances: Int = 1)
  case class Notification(email: String, pagerEvent: PagerEvent)

  val events = List(
    PagerEvent("Service Discovery Failed", new Date),
    PagerEvent("Illegal elements in Data pipeline", new Date),
    PagerEvent("Number of Http 500 spiked", new Date),
    PagerEvent("Service stopped responding", new Date)
  )

  val eventSource = Source(events)

  val onCallEngineer = "daniel@rtjvm.com" // a fast service for fetching on call emails

  def sendEmail(notification: Notification) = {
    println(s"Dear ${notification.email} you have an event: ${notification.pagerEvent}") // actually send an email
  }

  val notificationSink = Flow[PagerEvent].map(event => Notification(onCallEngineer, event)).to(Sink.foreach[Notification](sendEmail))

  // Standard
//  eventSource.to(notificationSink).run()
  // NOTE: Timer based sources do not respond to backpressure

  /** Un-backpressureable source
   * So if the source cannot be back pressured for some reason solution to that to somehow aggregate the incoming events and create one single notification when we receive deamnd from the sink
   * so instead of buffering the flow -> we can create 1 notification for multiple events
   * USE CONFLATE -> acts like fold in that it combines elements but emits the result only when the downstream sends DEMAND */

  def sendEmailSlow(notification: Notification) = {
    Thread.sleep(1000)
    println(s"Dear ${notification.email} you have an event: ${notification.pagerEvent}") // actually send an email
  }

  // aggregate flow
  val aggregateNotificationFlow = Flow[PagerEvent]
    .conflate((event1, event2) => {
      val nInstances = event1.nInstances + event2.nInstances
      PagerEvent(s"You have $nInstances events that require your attention",new Date, nInstances)
    })
    .map(resultingEvent => Notification(onCallEngineer, resultingEvent))

  // we will put the async boundary before the Sink because we want that part to run on a separate thread otherwise whole stream would run on a same actor and we wont see any effect
//  eventSource.via(aggregateNotificationFlow).async.to(Sink.foreach[Notification](sendEmailSlow)).run()
  /*
    Because conflate never backpressure -> we are decoupling upstream rate with the downstream rate
    so this is an alternative to back pressure
   */

  /** another technique which is good for Slow Producers: extrapolate / expand
   * Extrapolate: Run a function emitted from the last element emitted from upstream to compute further elements to be emitted downstream
   * Expand method -> creates it all times irrespective of demand
   */

  val slowCounter = Source(LazyList.from(1)).throttle(1, 1 second)
  val hungrySink = Sink.foreach[Int](println)

  val extrapolator = Flow[Int].extrapolate(element => Iterator.from(element))
  val repeater = Flow[Int].extrapolate(element => Iterator.continually(element))

//  slowCounter.via(extrapolator).to(hungrySink).run()
  slowCounter.via(repeater).to(hungrySink).run()

  //
  val expander = Flow[Int].expand(element => Iterator.continually(element))
}
