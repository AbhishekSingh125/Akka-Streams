package part4_techniques

import akka.actor.ActorSystem
import akka.pattern.pipe
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import akka.testkit.{TestKit, TestProbe}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Success, Failure}

class TestingAkkaStreams extends TestKit(ActorSystem("TestingAkkaStreams"))
  with AnyWordSpecLike
  with BeforeAndAfterAll {

  /** GOAL
   * Unit testing Akka Streams
   *  - final results
   *  - integrating with test actors
   *  - Streams Testkit
   *  */

  implicit val meterializer = Materializer(system)

  override def afterAll(): Unit = TestKit.shutdownActorSystem(system)

  "A simple stream" should {
    "satisfy basic assertions" in {
      // describe our tests
      val simpleSource = Source(1 to 10)
      val simpleSink = Sink.fold[Int, Int](0)(_ + _)

      val sumFuture = simpleSource.toMat(simpleSink)(Keep.right).run()
      val sum = Await.result(sumFuture, 2 seconds)
      assert(sum == 55)
    }

    "integrate with test actors via materialized value" in {
      import system.dispatcher
      val simpleSource = Source(1 to 10)
      val simpleSink = Sink.fold[Int, Int](0)(_ + _)

      val probe = TestProbe()
      simpleSource.toMat(simpleSink)(Keep.right).run().pipeTo(probe.ref)

      probe.expectMsg(55)
    }

    "integrate with a test actor based sink" in {
      val simpleSource = Source(1 to 5)
      val flow = Flow[Int].scan[Int](0)(_ + _) // 0, 1, 3, 6, 10, 15
      val streamUnderTest = simpleSource.via(flow)

      val probe = TestProbe()
      val probeSink = Sink.actorRef(probe.ref, "completionMessage", onFailureMessage = akka.actor.Status.Failure)

      streamUnderTest.to(probeSink).run()
      probe.expectMsgAllOf(0, 1, 3, 6, 10, 15)
    }

    "integrate with streams TestKit sink" in {
      val sourceUnderTest = Source(1 to 5).map(_ * 2)
      val testSink = TestSink.probe[Int]
      val materializedTestValue = sourceUnderTest.runWith(testSink)
      materializedTestValue.request(5).expectNext(2,4,6, 8, 10).expectComplete()
    }

    "integrate with Streams TestKit Source" in {
      import system.dispatcher
      val sinkUnderTest = Sink.foreach[Int] {
        case 13 => throw new RuntimeException("bad luck")
        case _ =>
      }

      val testSource = TestSource.probe[Int]
      val materialized = testSource.toMat(sinkUnderTest)(Keep.both).run()
      val (testPublisher, resultFuture) = materialized

      testPublisher
        .sendNext(1)
        .sendNext(5)
        .sendNext(13)
        .sendComplete()

      resultFuture.onComplete{
        case Success(_) => fail(" The sink under test should have thrown an expection on 13")
        case Failure(_) => // ok
      }
    }
    "test flows with a test source And a test sink" in {
      val flowUnderTest = Flow[Int].map(_ * 2)
      val testSource = TestSource.probe[Int]
      val testSink = TestSink.probe[Int]

      val materializedValue = testSource.via(flowUnderTest).toMat(testSink)(Keep.both).run()
      val (publisher, subscriber) = materializedValue

      publisher
        .sendNext(1)
        .sendNext(4)
        .sendNext(6)
        .sendNext(23)
        .sendNext(66)
        .sendComplete()

      subscriber.request(5)
        .expectNext(2, 8, 12, 46, 132)
        .expectComplete()
    }
  }
}
