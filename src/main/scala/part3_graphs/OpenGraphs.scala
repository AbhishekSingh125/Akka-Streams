package part3_graphs

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Broadcast, Concat, Flow, GraphDSL, Sink, Source}
import akka.stream.{FlowShape, Materializer, SinkShape, SourceShape}

object OpenGraphs extends App {

  val system = ActorSystem("OpenGraphs")
  implicit val materialize = Materializer(system)
  /** Goal
   * Complex Open Components */

  /** Complex Open Graphs
   * A composite source that concatenates to sources
   *  - emits all the elements from first source
   *  - then all elements from the second source
   *  */

  val firstSource = Source(1 to 10)
  val secondSource = Source(42 to 1000)

  val sourceGraph = Source.fromGraph(
    GraphDSL.create() { implicit builder =>

      import GraphDSL.Implicits._

      // declare components
      val concat = builder.add(Concat[Int](2))

      // tying up
      firstSource ~> concat
      secondSource ~> concat

      SourceShape(concat.out)
    }
  )

//  sourceGraph.to(Sink.foreach(println)).run()


  /** Complex Sink
   *  */

  val sink1 = Sink.foreach[Int](x => println(s"Meaningful thing 1: $x"))
  val sink2 = Sink.foreach[Int](x => println(s"Meaningful thing 2: $x"))

  val sinkGraph = Sink.fromGraph(
    GraphDSL.create() {implicit builder =>
      import GraphDSL.Implicits._

      // Create Components - add a broadcast
      val broadcast = builder.add(Broadcast[Int](2))

      // typing components together
      broadcast ~> sink1
      broadcast ~> sink2

      // return a shape
      SinkShape(broadcast.in)
    }
  )
//  firstSource.to(sinkGraph).run()

  /** Exercise - complex Flow
   * Write a complex flow that's composed of two other flows */

  val incrementer = Flow[Int].map(x => x + 1)
  val multiplier = Flow[Int].map(x => x * 10)

  val flowGraph = Flow.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      // Operate on shapes not components

      val incrementerShape = builder.add(incrementer)
      val multiplierShape = builder.add(multiplier)

      incrementerShape ~> multiplierShape

      FlowShape(incrementerShape.in, multiplierShape.out)
    } // static graph
  ) // components

  firstSource.via(flowGraph).to(Sink.foreach(println)).run()

  /** Exercise: possible to create a flow from a sink and a source?
   *
   * Akka stream does not forbid such a flow*/

  def fromSinkFromSource[A, B](sink: Sink[A, _], source: Source[B, _]): Flow[A, B, _] = {
    Flow.fromGraph(
      GraphDSL.create() { implicit builder =>

        val sourceShape = builder.add(source)
        val sinkShape = builder.add(sink)

        // return shape

        FlowShape(sinkShape.in, sourceShape.out)
      }
    )
  }

  val f  = Flow.fromSinkAndSourceCoupled(Sink.foreach[String](println), Source(1 to 10))
}
