package part4_techniques

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.pattern.ask
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.Timeout

import java.util.Date
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

object InteractingWithExternalServices extends App {

  val system = ActorSystem("InteractingWithExternalServices")
  implicit val materializer = Materializer(system)
  implicit val dispatcher = system.dispatchers.lookup("dedicated-dispatcher")

  /** GOAL
   * Akka Streams & Futures
   * Important for integrating with external services
   * */

  def genericExtService[A, B](element: A): Future[B] = ???

  /** Example: simplified PagerDuty
   * Service to manage on class software engineers -> based on event on call engineer is notified.
   */

  case class PagerEvent(application: String, description: String, date: Date)

  val eventSource = Source(List(
    PagerEvent("AkkaInfra","Infrastructure Broke",new Date),
    PagerEvent("FastDataPipeline","Illegal events in the pipeline",new Date),
    PagerEvent("Caching","Caching failed",new Date),
    PagerEvent("AkkaRecovery","Invalid recovery of persistent states",new Date),
    PagerEvent("AkkaInfra","Infrastructure failure",new Date),
    PagerEvent("AkkaInfra","Service unfulfilled",new Date)
  ))

  object PagerService {
    private val engineers = List("Daniel","John","Josh")
    private val emails = Map(
      "Daniel" -> "daniel@rtjvm.com",
      "John" -> "john@rtjvm.com",
      "Josh" -> "josh@rtjvm.com"
    )

    def processEvent(pagerEvent: PagerEvent)= Future {
      // Engineers get cycled
      val engineerIndex = pagerEvent.date.toInstant.getEpochSecond / (24 * 3600) % engineers.length
      val engineer = engineers(engineerIndex.toInt)
      val engineerEmail = emails(engineer)

      // Page the engineer
      println(s"Sending engineer $engineerEmail a high priority notification: $pagerEvent")
      Thread.sleep(1000)

      // Return engineer email
      engineerEmail
    }// not recommended in practice in MapAsync
  }

  val infraEvents = eventSource.filter(_.application == "AkkaInfra")
  // MapAsync -> guarantees relative order of elements
  val pagedEngineerEmail = infraEvents.mapAsync(parallelism = 1)(event => PagerService.processEvent(event))

  val pagedEmailSink = Sink.foreach[String](email => println(s"Successfully sent notification to $email"))

//  pagedEngineerEmail.to(pagedEmailSink).run()

  /** Service as Actors */
  class PagerActor extends Actor with ActorLogging{
    private val engineers = List("Daniel","John","Josh")
    private val emails = Map(
      "Daniel" -> "daniel@rtjvm.com",
      "John" -> "john@rtjvm.com",
      "Josh" -> "josh@rtjvm.com"
    )

    private def processEvent(pagerEvent: PagerEvent)= {
      val engineerIndex = pagerEvent.date.toInstant.getEpochSecond / (24 * 3600) % engineers.length
      val engineer = engineers(engineerIndex.toInt)
      val engineerEmail = emails(engineer)

      // Page the engineer
      log.info(s"Sending engineer $engineerEmail a high priority notification: $pagerEvent")
      Thread.sleep(1000)

      // Return engineer email
      engineerEmail
    }// not recommended in practice in MapAsync

    override def receive: Receive = {
      case pagerEvent: PagerEvent =>
        sender() ! processEvent(pagerEvent)
    }
  }

  val pagerActor = system.actorOf(Props[PagerActor],"pagerActor")
  implicit val timeout = Timeout(4 seconds)
  val alternativePagedEngineerEmail = infraEvents.mapAsync(parallelism = 4)(event => (pagerActor ? event).mapTo[String] )
  alternativePagedEngineerEmail.to(pagedEmailSink).run()

  // do not confuse mapAsync with async (ASYNC boundary) -> makes a chain run on a separate actor
}
/** RECAP
 * Flows that call external services
 * Useful when services are asynchronous
 *  - The futures are evaluated in parallel
 *  - The relative order of elements is maintained
 *  - A lagging future will stall the entire system
 *
 * If events order is not important use mapAsyncUnordered
 *  - Faster
 *  - Does not guarantee preservation of the order of elements
 *
 * Evaluate the futures on their own ExecutionContext
 * MapAsync works great with asking actors*/
