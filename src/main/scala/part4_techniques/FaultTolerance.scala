package part4_techniques

import akka.actor.ActorSystem
import akka.stream.Supervision.{Resume, Stop}
import akka.stream.{ActorAttributes, Materializer, RestartSettings}
import akka.stream.scaladsl.{RestartSource, Sink, Source}

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Random

object FaultTolerance extends App {
  /** Goal
   * How to react to failures in streams */
  val system = ActorSystem("FaultTolerance")
  implicit val materializer = Materializer(system)

  // 1 - Logging - monitoring, completions and failures
  val faultySource = Source(1 to 10).map(e => if (e == 6) throw new RuntimeException else e)
//  faultySource.log("trackingElements").to(Sink.ignore).run()

  // 2 - Gracefully terminating a stream
  faultySource.recover{
    case _: RuntimeException => Int.MinValue
  }.log("gracefulSource").to(Sink.ignore)
//    .run()

  // 3 - Recover with another stream
  faultySource.recoverWithRetries(3, {
    case _: RuntimeException => Source(90 to 99)
  }).log("recoverWithRetries")
    .to(Sink.ignore)
//    .run()

  // 4 - backoff supervision
  val restartSource = RestartSource.onFailuresWithBackoff(RestartSettings(
    minBackoff = 1 second, // Intital delay after first attempt is made
    maxBackoff = 30 seconds, // Maximum delay after it will declared failed
    randomFactor = 0.2 // random factor
  )) (() => {
    val randomNumber = new Random().nextInt(20)
    Source(1 to 10).map(element => if (element == randomNumber) throw new RuntimeException else element)
  })

  restartSource.log("restartBackoff").to(Sink.ignore)
//    .run()

  // 5 - Supervision Strategy
  val numbers = Source(1 to 20).map(n => if (n == 13) throw new RuntimeException("bad luck") else n).log("supervision")
  val supervisedNumbers = numbers.withAttributes(ActorAttributes.supervisionStrategy {
    // Resume = Skips the faulty element
    // Stop = stop the stream
    // Restart = Resume + clear the internal state of the component
    case _: RuntimeException => Resume
    case _ => Stop
  })

  supervisedNumbers.to(Sink.ignore).run()

}

/** RECAP
 * Logging
 * Recovering
 * BackoffSupervision
 * SupervisionStrategies
 */