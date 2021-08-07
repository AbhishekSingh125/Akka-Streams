package part5_advanced

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{BroadcastHub, Keep, MergeHub, Sink, Source}
import akka.stream.{KillSwitches, Materializer}

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

object DynamicStreamHandling extends App {
  /** GOAL
   * Stop or abort a stream at runtime
   * Dynamically add fan-in/fan-out branches
   */
  val system = ActorSystem("DynamicStreamHandling")
  implicit val materializer = Materializer(system)

  /*
    How to dynamically stop a component in the system
    1 - Kill switch - is a special kind of flow that emits the same elements that go through it but it materializes to a special value that has some additional methods
   */

  val killSwitchFlow = KillSwitches.single[Int] // Flow that receives elements of type Int and It will push to the output whatever the input it and materializes to UniqueKillSwitch

  val counter = Source(LazyList.from(1)).throttle(1, 1 second).log("counter")
  val sink = Sink.ignore

  val killSwitch = counter.viaMat(killSwitchFlow)(Keep.right).toMat(sink)(Keep.left).run()

  // Single kill Switch
  system.scheduler.scheduleOnce(3 seconds) {
    killSwitch.shutdown()
  }(system.dispatcher)

  val anotherCounter = Source(LazyList.from(1)).throttle(2, 1 second).log("anotherCounter")
  val sharedKillSwitch = KillSwitches.shared("OneButtonToRuleThemAll")
//
//  counter.via(sharedKillSwitch.flow).runWith(Sink.ignore)
//  anotherCounter.via(sharedKillSwitch.flow).runWith(Sink.ignore)
//
//  system.scheduler.scheduleOnce(3 seconds){
//    sharedKillSwitch.shutdown()
//  }

  // Dynamically add Fan-in and Fan-out operators
  // MergeHub
  val dynamicMerge: Source[Int, Sink[Int, NotUsed]] = MergeHub.source[Int]
  val materializedSink = dynamicMerge.to(Sink.foreach[Int](println)).run()

  // we can use this sink any time we like
//  Source(1 to 10).runWith(materializedSink)
//  counter.runWith(materializedSink)

  // BroadcastHub
  val dynamicBroadcast = BroadcastHub.sink[Int]
  val materializedSource = Source(1 to 100).runWith(dynamicBroadcast)
//
//  materializedSource.runWith(Sink.ignore)
//  materializedSource.runWith(Sink.foreach[Int](println))

  /** Exercise
   * Combine mergeHub and a broadcastHub
   *
   * Can be used for distributed system where data is copied to multiple streams and consumed by multiple users
   *
   * A publisher - subscriber component*/

  val merge = MergeHub.source[String]
  val bCast = BroadcastHub.sink[String]
  val (publisherPort, subscriberPort) = merge.toMat(bCast)(Keep.both).run

  subscriberPort.runWith(Sink.foreach(element => println(s"I have received the $element")))
  subscriberPort.map(string => string.length).runWith(Sink.foreach(n => println(s"I got a number ${n}")))

  Source(List("Akka", "is","amazing")).runWith(publisherPort)
  Source(List("I","Love","Scala")).runWith(publisherPort)
  Source.single("STREAEMAS").runWith(publisherPort)
}
