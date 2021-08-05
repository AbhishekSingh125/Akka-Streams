package part4_techniques

import akka.Done
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{CompletionStrategy, Materializer, OverflowStrategy}
import akka.util.Timeout

import scala.concurrent.duration._
import scala.language.postfixOps

object InteractingWithActors extends App {

  val system = ActorSystem("InteractingWithActors")
  implicit val materializer: Materializer = Materializer(system)

  /** Goal
   * Make Actors interact with Akka Streams
   *  - process elements in streams
   *  - act as a source
   *  - act as a destination
   *  */

  class SimpleActor extends Actor with ActorLogging {
    override def receive: Receive = {
      case s: String =>
        log.info(s"I got a string ${s}")
        sender() ! s"$s$s"
      case num: Int =>
        log.info(s"Just received a number: $num")
        sender() ! 2*num
    }
  }

  val simpleActor = system.actorOf(Props[SimpleActor],"simpleActor")

  val numberSource = Source(1 to 10)

  // actor as a flow
  implicit val timeout = Timeout(2 seconds)
  val actorFlow = Flow[Int].ask[Int](parallelism = 4)(simpleActor)


//  numberSource.via(actorFlow).to(Sink.ignore).run()
//  numberSource.ask[Int](parallelism = 4)(simpleActor).to(Sink.ignore).run()

  /** Actor as a source
   * - In this case you want an Actor ref exposed to you so that we can send messages to it
   * - When you send a message to these actor refs that means you are injecting messages to the stream */

  val actorPoweredSource: Source[Int, ActorRef] = Source.actorRef(
    completionMatcher = {
      case Done =>
        CompletionStrategy.immediately
    },
    failureMatcher = {
      PartialFunction.empty
    },
    bufferSize = 10,
    overflowStrategy = OverflowStrategy.dropHead)

  val materializedActorRef = actorPoweredSource.to(Sink.foreach[Int](number => println(s"Actor powered flow got number: $number"))).run()

//  materializedActorRef ! 5
//  materializedActorRef ! 6
//  materializedActorRef ! akka.actor.Status.Success("complete")
//  materializedActorRef ! Done

  /** Actor as a destination / Sink
   *  - an init message
   *  - an acknowledge message to confirm the reception
   *  - a complete message
   *  - a function to generate a message in case the stream throws an exception
   *  */

  case object StreamInit
  case object StreamAck
  case object StreamComplete
  case class StreamFail(ex: Throwable)

  class DestinationActor extends Actor with ActorLogging {
    override def receive: Receive = {
      case StreamInit =>
        log.info("Stream Initialized")
        sender() ! StreamAck
      case StreamComplete =>
        log.info("Stream Complete")
        context.stop(self)
      case StreamFail(ex) =>
        log.warning(s"Stream failed: $ex")
      case message =>
        log.info(s"Message $message received")
        sender() ! StreamAck
    }
  }
  val destinationActor = system.actorOf(Props[DestinationActor],"destinationActor")

  val actorPoweredSink = Sink.actorRefWithBackpressure[Int](
    destinationActor,
    onInitMessage = StreamInit,
    ackMessage = StreamAck,
    onCompleteMessage = StreamComplete,
    onFailureMessage = StreamFail
  )

  Source(1 to 10).to(actorPoweredSink).run()

  // Sink.actorRef() not recommended
}
/** RECAP
 * Actors as a flow -> using ask pattern which is equivalent to "via an ask-based flow"
 * Actors as a source -> materializes to an ActorRef
 * Actors as a sink -> need lifecycle messages and supports messages
 * */
