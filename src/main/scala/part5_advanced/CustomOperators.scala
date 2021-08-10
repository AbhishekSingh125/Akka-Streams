package part5_advanced

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.stage.{GraphStage, GraphStageLogic, GraphStageWithMaterializedValue, InHandler, OutHandler}
import akka.stream.{Attributes, FlowShape, Inlet, Materializer, Outlet, SinkShape, SourceShape}

import scala.collection.mutable
import scala.concurrent.{Future, Promise}
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Random, Success}

object CustomOperators extends App {

  /** GOAL
   * Define your operators with your own internal logic
   * Master the GraphStage API
   * */

  val system = ActorSystem("CustomOperators")
  implicit val materializer = Materializer(system)

  // 1 - a custom source which emits random numbers until canceled
  class RandomNumberGenerator(max: Int) extends GraphStage[ /** Step-0 Define the shape **/ SourceShape[Int]] {

    // Step 1: define the ports and the component-specific memebers
    val outPort = Outlet[Int]("randomGenerator")
    val random = new Random()

    // step 2: construct a new shape
    override def shape: SourceShape[Int] = SourceShape(outPort)

    // step 3: create the logic
    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
      // implement my logic here

      // step 4 - define mutable state and implement logic by setting handlers through our ports
      setHandler(outPort, new OutHandler {
        // called when there is demand from downstream
        override def onPull(): Unit = {
          // emit a new element
          val nextNumber = random.nextInt(max)
          // push it out of the outPort
          push(outPort, nextNumber)
        }
      })
    }
  }

  // create an actual source based on the definition above
  val randomGeneratorSource = Source.fromGraph(new RandomNumberGenerator(100)).throttle(10, 1 second)

//  randomGeneratorSource.runWith(Sink.foreach(println))
  // 2 - custom sink that print elements in batches of a given size
  class Batcher(batchSize: Int) extends GraphStage[SinkShape[Int]] {
  val inPort = Inlet[Int]("batcher")

  override def shape: SinkShape[Int] = SinkShape[Int](inPort)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {

    override def preStart(): Unit = {
      pull(inPort) // signaling demand upstream for new element
    }

    // mutable state - sits in the graph stage logic never inside the batcher
    val batch = new mutable.Queue[Int]
    setHandler(inPort, new InHandler {
      // When the upstream wants to send an element
      override def onPush(): Unit = {
        // if i grab the in port I would be able to extract the value from the inPort
        val nextElement = grab(inPort)
        batch.enqueue(nextElement)
        if (batch.length >= batchSize) {
          println("New batch: " + batch.dequeueAll(_ => true).mkString("[", ",", "]"))
        }
        pull(inPort) // send demand upstream
      }

      override def onUpstreamFinish(): Unit = {
        if (batch.nonEmpty) {
          println("New batch: " + batch.dequeueAll(_ => true).mkString("[", ",", "]"))
          println("Stream finished.")
        }

      }
    })
  }
}

  val batcherSink = Sink.fromGraph(new Batcher(10))

//  randomGeneratorSource.runWith(batcherSink)

  /** Exercise
   * Create Custom Flow - create simple filter flow */

  class FilterFlow[T](filterPredicate: T => Boolean) extends GraphStage[FlowShape[T, T]] {

    val inPort = Inlet[T]("filterIn")

    val outPort = Outlet[T]("filterOut")


    override def shape: FlowShape[T, T] = FlowShape(inPort, outPort)
    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {


      // onPull is called when the downstream of component connected to the outPort asks for an element
      setHandler(outPort, new OutHandler {
        override def onPull(): Unit = {
          pull(inPort)
        }
      })

      setHandler(inPort, new InHandler {
          override def onPush(): Unit = {
        try {
            val element = grab(inPort)
            if (filterPredicate(element)) push(outPort, element)
            else pull(inPort) // ask for another element
          } catch {
          case e: Throwable => failStage(e)
        }
        }
      })
    }
  }

  val filterFlow = Flow.fromGraph(new FilterFlow[Int](x => x % 2 == 0))

//  randomGeneratorSource.via(filterFlow).runWith(batcherSink)

  /** Materialized values in graph stages
   * */

  // 3 - flow that counts the number of elements that go through it
  class CounterFlow[T] extends GraphStageWithMaterializedValue[FlowShape[T, T], Future[Int]] {

    val inPort = Inlet[T]("counterIn")
    val outPort = Outlet[T]("counterOut")

    override def shape: FlowShape[T, T] = FlowShape(inPort, outPort)

    override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, Future[Int]) = {
      val promise = Promise[Int]
      val logic: GraphStageLogic = new GraphStageLogic(shape) {
        // setting mutable state
        var currentCount = 0

        setHandler(outPort, new OutHandler {
          // when I receive demand from downstream that I signal demand upstream
          override def onPull(): Unit = pull(inPort)

          override def onDownstreamFinish(cause: Throwable): Unit = {
            promise.success(currentCount)
            super.onDownstreamFinish(cause)
          }
        })

        setHandler(inPort, new InHandler {
          // when I have demand I grab the incoming element
          override def onPush(): Unit = {
            val element = grab(inPort)
            currentCount += + 1
            push(outPort, element)
          }

          override def onUpstreamFinish(): Unit = {
            promise.success(currentCount)
            super.onUpstreamFinish()
          }

          override def onUpstreamFailure(ex: Throwable): Unit = {
            promise.failure(ex)
            super.onUpstreamFailure(ex)
          }
        })
      }
      (logic, promise.future)
    }
  }

  val counterFlow = Flow.fromGraph(new CounterFlow[Int])
  val counterFuture = Source(1 to 10).viaMat(counterFlow)(Keep.right).toMat(Sink.foreach[Int](println))(Keep.left).run()

  import system.dispatcher
  counterFuture.onComplete{
    case Success(value) => println(s"Current count is $value")
    case Failure(exception) => println(s"I have failed with $exception")
  }

}
/** Input port methods
 * InHandlers interact with the upstream
 *  - onPush -> mandatory which will be called when receive an element on that inputPort
 *  - onUpstreamFinish -> when upstream terminates the stream
 *  - onUpstreamFailure -> when an upstream throws an error
 *
 * Input ports can check and retrieve elements
 *  - pull: signal demand
 *  - grab: takes the element and will fail if there is no element
 *  - cancel: tell upstream to stop
 *  - isAvailable: checking whether the input port is available
 *  - hasBeenPulled: checks whether demand has already been signaled on that port
 *  - isClosed
 */

/** Output port methods
 * OutHandlers interact with downstream
 *  - onPull -> mandatory which will be called when demand has been signaled
 *  - onDownstreamFinish (no onDownstreamFailure as I'll receive a cancel signal)
 *
 * Output ports can send elements
 *  - push: send an element
 *  - complete: finish the stream
 *  - fail - terminates the stream with an exception
 *  - isAvailable - check if the element has already been pushed
 *  - isClosed
 */
