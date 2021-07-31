package part2_primer

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}

import scala.concurrent.Future
import scala.util.{Failure, Success}

object MaterializingStreams extends App {

  /** Materializing
   * Components are static until they run
   * val result(Materialized value) = graph.run()
   * A graph is a "blueprint" for a stream
   * Running a graph allocates the right resources
   *  - actors, thread pools
   *  - sockets, connections
   *  etc - everything is transparent
   *
   *  Running a graph = materializing
   * */
  /** Materialized values
   * Materializing graph = materializing all components
   *  - each component produces a materialized value when run
   *  - the graph produces a single materialized value
   *  - our job to choose which one to pick
   *
   *  A component can materialize multiple times
   *    - you can reuse the same component in different graphs
   *    - different runs = different materializing
   *
   *  A materialized value can be ANYTHING
   *  */

  val system = ActorSystem("MaterializingStreams")
  implicit val materializer = Materializer(system)

  val simpleGraph = Source(1 to 10).to(Sink.foreach(println))
//  val simpleMaterializedValue = simpleGraph.run()

  val source = Source(1 to 10)
  val sink: Sink[Int, Future[Int]] = Sink.reduce[Int]((a,b) => a + b)
  // Sink[value received, return type of Sink]

//  val sumFuture: Future[Int] = source.runWith(sink) // connects the source to sink and creates a graph and runs it
//
//  sumFuture.onComplete{
//    case Success(value) => println(s"Sum of all element is: $value")
//    case Failure(exception) => println(s"The sum of elements could not be computed: $exception")
//  }(system.dispatcher)

  /** Problem:
   * When you want to materialize a graphs all the components can return a materialized value
   * but result of running the graph as a single materialized value so we need to choose which materialized value we want to return at the end
   *
   * so When we construct a graph with via and to - by default the left most materialized value is kept */

  // Choosing Materialized value
  val simpleSource = Source(1 to 10)
  val simpleFlow = Flow[Int].map(x => x + 1)
  val simpleSink = Sink.foreach[Int](println)
  val runnableGraph = simpleSource.viaMat(simpleFlow)(Keep.right).toMat(simpleSink)(Keep.right)  //((sourceMat, flowMat) => flowMat)

//  runnableGraph.run().onComplete{
//    case Success(value) => println(s"Stream Processing Finished")
//    case Failure(exception) => println(s"Stream processing Failed with $exception")
//  }(system.dispatcher)


  // sugars
//  val sum: Future[Int] = Source(1 to 10).runWith(Sink.reduce[Int](_ + _)) // source.to(sink.reduce)(Keep.right) | runWith(Sink) syntax -> keeps the sink's materialized value
//  Source(1 to 10).runReduce(_ + _) // same

  // backwards
//  Sink.foreach[Int](println).runWith(Source.single(42)) // Source(...).to(sink....).run() | Source.to(Sink) syntax -> keeps source materialized value

  // Run components both ways
//  Flow[Int].map(x => x * 2).runWith(simpleSource, simpleSink)

  /** Exercise
   * Run very different ways as you can
   * - return the last element out of a source (Sink.last)
   * - compute the total word count out of a stream of sentences
   *    - map, fold, reduce */

  val aList = List("wow", "this is amazing","will it stream this")
  val aSource = Source(aList)
  val aSink = Sink.last[String]

  // #1
  val someResult = aSource.runWith(aSink)

  someResult.onComplete{
    case Success(value) => println(s"I found : $value")
    case Failure(exception) => println("I found nothing")
  }(system.dispatcher)

  // #2
//  val newWay = aSource.to(aSink)
//  newWay.run()

  // #3
//  aSink.runWith(aSource)

  //#4
//  aSource.toMat(aSink)(Keep.right)

  val f1 = Source(1 to 10).toMat(Sink.last)(Keep.right).run()
  f1.onComplete{
    case Success(value) => println(value)
    case Failure(exception) => println("Oops something went wrong")
  }(system.dispatcher)

//  val f2 = aSink.runWith(aSource)

  val sentence = Source(List(
    "Hello this is bob",
    "wow if this works",
    "lol"
  ))

  val wordCountSink = Sink.fold[Int, String](0)((currentWords, newSentence) => currentWords + newSentence.split(" ").length)
  sentence.toMat(wordCountSink)(Keep.right).run().onComplete{
    case Success(value) => println(s"I found count of $value")
    case Failure(exception) => println(s"I failed with $exception")
  }(system.dispatcher)

  val g2 = sentence.runWith(wordCountSink) // keeps right
  val g3 = sentence.runFold(0)((currentWords, newSentence) => currentWords + newSentence.split(" ").length)
  val g4 = sentence.toMat(wordCountSink)(Keep.right)

  val wordCountFlow = Flow[String].fold[Int](0)((currentWords, newSentence) => currentWords + newSentence.split(" ").length)

  val simpleSinkWithPatternMatching = Sink.onComplete{
    case Success(value) => println(s"Total word count: $value")
    case Failure(exception) => println("I have failed")
  }

  val g5 = sentence.via(wordCountFlow).toMat(simpleSinkWithPatternMatching)(Keep.right).run()
  val g6 = sentence.viaMat(wordCountFlow)(Keep.left).toMat(Sink.head)(Keep.right).run()
  val g7 = sentence.via(wordCountFlow).runWith(Sink.head)
  val g8 = wordCountFlow.runWith(sentence, simpleSinkWithPatternMatching)._2

  /** RECAP
   * Materializing a graph = materializing all components
   *  - each component produces a materialized value when run
   *  - the graph produces a single materialized value
   *  - our job to choose whihc one to pick
   *
   * A component can materialized multiple times
   *
   * A materialized value can be anything at All
   *  */

}
