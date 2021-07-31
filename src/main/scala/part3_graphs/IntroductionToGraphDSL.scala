package part3_graphs

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Balance, Broadcast, Flow, GraphDSL, Merge, RunnableGraph, Sink, Source, Zip}
import akka.stream.{ClosedShape, Materializer}

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

object IntroductionToGraphDSL extends App {

  /** Goal
   * Write complex Akka Streams graphs
   * familiarize with graph DSL
   * non-linear components
   *  - fon out
   *  - fan in*/

  val system = ActorSystem("GraphBasics")
  implicit val materializer = Materializer(system)

  val input  = Source(1 to 1000)
  // assume two hard computations
  val incrementer = Flow[Int].map(x => x + 1)
  val multiplier = Flow[Int].map(x => x * 10)
  // We want to create a tuple with one as incrementer and other as multiplier but we cant do that right now from what we know
  // what we need is a Fan in operator / component that inputs the value from source in parallel to incrementer and multiplier and fan outs to output / Sink
  val output = Sink.foreach[(Int, Int)](println)


  // runnable graph
  // step - 1 Setting up the fundamentals for the graph
  val graph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] => // builder = MUTABLE data structure
      // must return a shape
      import GraphDSL.Implicits._ // Brings some nice operators into scope

      // Step - 2 add the necessary components to this graph
      val broadcast = builder.add(Broadcast[Int](2)) // fan out operators -> has 1 input and two outputs
      val zip = builder.add(Zip[Int, Int]) // fan in operator -> 2 input and one output -> (First input: Int, Second Input: Int)

      // Step - 3 tying them together
      input ~> broadcast // input part of graph -> input feeds into broadcast

      broadcast.out(0) ~> incrementer ~> zip.in0
      broadcast.out(1) ~> multiplier ~> zip.in1

      zip.out ~> output
      // Step 4 - Return a closed shape
      ClosedShape // FREEZE the builder's shape -> becomes immutable and used to constructing the graph
    } // graph
  ) // runnable graph
//  graph.run() // run the graph and materialize it

  /** Exercise 1:  feed a source into 2 sinks at the same time (hint use a broadcast)*/

  val firstSink = Sink.foreach[Int](x => println(s"First Sink: $x"))
  val secondSink = Sink.foreach[Int](x => println(s"Second Sink: $x"))

  val sourcetoTwoSinksGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
      import GraphDSL.Implicits._

      val broadcast = builder.add(Broadcast[Int](2))

      input ~> broadcast ~> firstSink // implict port numbering
               broadcast ~> secondSink


      ClosedShape
    }
  )

//  sourcetoTwoSinksGraph.run()

  /** Exercise 2: Given a graph with two sources one with fast source other with slow sources
   * Feed into a fan in shape called meerge
   * Merge will take any one element out of one of its inputs and push from its output
   * feeds into fan out shape as a balance will distribute components equally in its output
   * 2 out puts and feeds into two sinks */

  val fastSource = input.throttle(5, 1 second)
  val slowSource = input.throttle(2, 1 second)

  val sink1 = Sink.fold[Int, Int](0)((count, _) => {
    println(s"Sink 1 number of elements: $count")
    count + 1
  })

  val sink2 = Sink.fold[Int, Int](0)((count, _) => {
    println(s"Sink 2 number of elements: $count")
    count + 1
  })
  val BalanceGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
      import GraphDSL.Implicits._

      //
      val merge = builder.add(Merge[Int](2))
      val balance = builder.add(Balance[Int](2))

      slowSource ~> merge
      fastSource ~> merge

      merge ~> balance

      balance ~> sink1
      balance ~> sink2

      ClosedShape
    }
  )

  BalanceGraph.run()
}

/** RECAP
 * Complex Akka Streams Graphs
 * The Graph DSL
 * Non-Linear Components:
 *  - fan-out
 *  - fan-in
 *
 * Fan-out Components:
 *  - Broadcast
 *  - Balance
 *
 * Fan-In components:
 *  - Zip/ZipWith
 *  - Merge
 *  - Concat
 */