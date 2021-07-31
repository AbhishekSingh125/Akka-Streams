package part2_primer

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink, Source}

import scala.concurrent.Future

object FirstPrincipals extends App {

  /** Goal
   * Understand, Read, and Design asynchronous, backpressured, incremental, potentially infinite data processing systems known as REACTIVE STREAMS
   *
   * REACTIVE STREAMS
   * started by many companies: specifies behavior of async, backpressured streams
   * Concepts:
   *  - publisher: emits elements (asynchronously)
   *  - subscriber: receives elements
   *  - processor: transforms elements along the way
   *  - async -> evaluated or executed at no defined time without blocking any running code
   *  - backpressure
   *
   * Reactive streams is an SPI(service provider interface), not an API
   * our focus: the Akka Streams API
   */

  //  val materializer = ActorMaterializer()(system) // Deprecated -> Akka has default materializer

  /** Components in Akka Streams
   * Source = "publisher"
   *  - emits elements asynchronously
   *  - may or may not terminate
   *
   * Sink = "subscriber"
   *  - receives elements
   *  - terminates only when publisher terminates
   *
   * Flow = "processor"
   *  - transforms elements
   *
   * We build reactive streams by connecting these components
   *
   * Directions
   *  - Upstream -> to the source
   *  - downstream -> to the sink
   *  */

  val system = ActorSystem("FirstPrincipals")
  implicit val materializer = Materializer(system)

  // Source
  val source = Source(1 to 10)

  // Sink
  val sink = Sink.foreach[Int](println)

  val graph = source.to(sink)
//  graph.run()(Materializer(system))

  // Flow
  val flow = Flow[Int].map(x => x + 1)

  val newSource = source.via(flow)

//  newSource.to(sink).run()
  // or
//  source.via(flow).to(sink).run()

  // nulls are NOT allowed
//  val illegalSource = Source.single[String](null)
//  illegalSource.to(Sink.foreach(println)).run()

  // overcoming null by using options instead
  val finiteSource = Source.single(1)
  val anotherFiniteSource = Source(List(1,2,3))
  val emptySource = Source.empty[Int]
//  val infiniteSource = Source(Stream.from(1)) // do not confuse an Akka Stream with a "collection" stream
  val futureSource = Source.future(Future.successful(42))

  // Sinks
  val theMostBoringSink = Sink.ignore
  val foreachSink = Sink.foreach[String](println)
  val headSink = Sink.head[Int] // retrieves head and then closes the stream
  val foldSink = Sink.fold[Int, Int](0)((a, b) => a + b)

  // Flows - usually mapped to collection operators
  val mapFLow = Flow[Int].map(x => 2 * x)
  val takeFlow = Flow[Int].take(5) // turns a stream into a finite stream as it takes only 5 and then close the stream
  // DROP & FILTER
  // NOT HAVE flatMap

  // Source -> Flow -> FLow -> Flow -> .... -> Sink
//  val doubleFlowGraph = source.via(mapFLow).via(mapFLow).to(sink).run()

  // Syntactic Sugars
  val mapSource = Source(1 to 10).map(x => x * 2) // equivalent to Source(1 to 10) via (Flow[Int].map(x => x * 2))
  // run stream directly
//  mapSource.runForeach(println) // equivalent to mapSource.to(Sink(foreach[Int](println)).run()


  // OPERATORS = components

  /** Exercise:
   * Create a stream that takes a names of persons, then you will keep first two names with length greater than 5 Characters */

  val names = List("Bob", "Kalbad", "Daniel" ,"Alexander", "Clauss")
  Source(names).filter(_.length > 5).take(2).runForeach(println)

}
