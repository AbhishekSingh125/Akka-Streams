package part3_graphs

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Keep, Sink, Source}
import akka.stream.{FlowShape, Materializer, SinkShape}

import scala.concurrent.Future
import scala.util.{Failure, Success}

object GraphMaterializedValues extends App {

  /** GOAl:
   * Expose materialized values in components built with GraphDSL
   * */

  val system = ActorSystem("GraphMaterializedValues")
  implicit val materialize: Materializer = Materializer(system)

  val wordSource = Source(List("Akka","is","awesome","rock","the","JVM"))
  val printer = Sink.foreach[String](println)
  val counter = Sink.fold[Int, String](0)((count, _) => count + 1)

  /** Create a composite component (sink)
   *  - prints out all the strings which are lower case
   *  - Counts the string that are short < 5 chars */

  val complexWordSink = Sink.fromGraph(
    GraphDSL.create(counter, printer)((counterMatValue, printerMatValue) => counterMatValue) {
      implicit builder => (counterShape, printerShape) =>

      import GraphDSL.Implicits._

      // SHAPES
      val broadcast = builder.add(Broadcast[String](2))
      val lowercaseFilter = builder.add(Flow[String].filter(word => word == word.toLowerCase()))
      val shortStringFilter = builder.add(Flow[String].filter(word => word.length < 5))

      // CONNECTIONS
      broadcast ~> lowercaseFilter ~> printerShape
      broadcast ~> shortStringFilter ~> counterShape

      // SHAPE
      SinkShape(broadcast.in)
    }
  )

  val shortStringsCountFuture = wordSource.toMat(complexWordSink)(Keep.right).run()

  shortStringsCountFuture.onComplete{
    case Success(value) => println(s"Total short string: $value")
    case Failure(exception) => println(s"The count of short string failed: $exception")
  }(system.dispatcher)

  /** Exercise
   * Future[Int] return value will have number of elements that went through this flow
   *
   * HINT: use a broadcast and Sink.fold
   * */

  def enhanceFlow[A,B](flow: Flow[A, B, _]): Flow[A,B, Future[Int]] = {
    val counterSink = Sink.fold[Int, B](0)((counter, _) => counter + 1)
    Flow.fromGraph(
      GraphDSL.create(counterSink){implicit builder => counterShape =>
        import GraphDSL.Implicits._

        val broadcast = builder.add(Broadcast[B](2))
        val originalFlowShape = builder.add(flow)

        originalFlowShape ~> broadcast ~> counterShape

        FlowShape(originalFlowShape.in, broadcast.out(1))
      }
    )
  }
  val simpleSource = Source(1 to 42)
  val simpleFlow = Flow[Int].map(x => x*2)
  val simpleSink = Sink.ignore

  val enhancedFlowCountFuture = simpleSource.viaMat(enhanceFlow(simpleFlow))(Keep.right).toMat(simpleSink)(Keep.left).run()

  enhancedFlowCountFuture.onComplete{
    case Success(value) => println(s"Current count is: $value")
    case Failure(exception) => println("Oops")
  }(system.dispatcher)
}
