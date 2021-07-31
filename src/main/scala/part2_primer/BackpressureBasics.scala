package part2_primer

import akka.actor.ActorSystem
import akka.stream.{Materializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Sink, Source}

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

object BackpressureBasics extends App {

  /** BACKPRESSURE
   * One of the fundamental features of Reactive Streams
   * - Elements flow as response to demand from consumers
   *
   * Fast consumers: all is well
   * Slow consumers: problem
   * - consumer will send a signal to producer to slow down
   *
   * Backpressure protocol is transparent
   */

  val system = ActorSystem("Backpressure")
  implicit val materializer = Materializer(system)

  val fastSource = Source(1 to 1000)
  val slowSink = Sink.foreach[Int] { x =>
    // simulate a long processing
    Thread.sleep(1000)
    println(s"Sink: $x")
  }

//  fastSource.to(slowSink).run() // fusing?!
  // not backpressure

//  fastSource.async.to(slowSink).run()
  // backpressure in place

  val simpleFlow = Flow[Int].map{ x =>
    println(s"Incoming: $x")
    x + 1
  }

//  fastSource.async.via(simpleFlow).async.to(slowSink).run()

  /** A component will react to backpressure in the following way
   * - try to slow down if possible
   * - Buffer elements until their is more demand
   * - drop down elements from the buffer if it overflows -> we can control upto this point
   * - tear down / kill the whole stream (failure)
   */

  // Drophead drops the oldest element in the buffer
  val bufferedFlow = simpleFlow.buffer(1, OverflowStrategy.dropHead)

//  fastSource.async
//    .via(bufferedFlow).async
//    .to(slowSink)
//    .run()

  /*
  *   1- 16: nobody is backpressured
  *   17-26: flow will buffer -> but source is so fast that in the blink of an eye sink doesnt even have chance to print so flow starts dropping at next element
  *   26-100: flow will always drop the oldest element
  *   => 991-1000 => 992 - 1001 => sink */

  // Overflow strategies
  /*
    - drophead => oldest
    - drophead => newest
    - drop new => exact element to be added = keeps buffer
    - drop entire buffer
    - backpressure signal
    - fail
   */

  val bufferedFlowStrategies = simpleFlow.buffer(10, OverflowStrategy.dropBuffer)

//  fastSource.async
//    .via(bufferedFlow).async
//    .to(slowSink)
//    .run()

  // Throttling
  fastSource.throttle(2, 1 second).runWith(Sink.foreach(println))

  /** RECAP
   * Data flows through streams in response to demand
   * Akka Streams can slow down fast producers
   * Backpressure protocol is transparent
   * */
}
