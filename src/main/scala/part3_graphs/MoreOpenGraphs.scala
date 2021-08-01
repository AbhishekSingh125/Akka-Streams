package part3_graphs

import akka.actor.ActorSystem
import akka.stream.{ClosedShape, FanOutShape2, Materializer, UniformFanInShape}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, RunnableGraph, Sink, Source, ZipWith}

import java.util.Date

object MoreOpenGraphs extends App {
  /** Goal
   * More complex shapes with the GraphDSL
   *  - Fan-out
   *  - Fan-in
   *  - uniform/non-uniform */

  val system = ActorSystem("MoreOpenGraphs")
  implicit val materialize = Materializer(system)

  /** Example: Max 3 operator
   *  - 3 inputs of type int
   *  - maximum of the 3
   *  */

  val max3StaticGraph = GraphDSL.create(){implicit builder =>

    import GraphDSL.Implicits._

    // Step 2 - Define Aux Shapes
    val maxZip1 = builder.add(ZipWith[Int, Int, Int]((a,b) => math.max(a,b)))
    val maxZip2 = builder.add(ZipWith[Int, Int, Int]((a,b) => math.max(a,b)))

    // Step 3 - Combine
    maxZip1.out ~> maxZip2.in0

    // Step 4
    UniformFanInShape(maxZip2.out,maxZip1.in0, maxZip1.in1, maxZip2.in1)
  }

  val source1 = Source(1 to 10)
  val source2 = Source((1 to 10).reverse)
  val source3 = Source(1 to 10).map(_ + 3)

  val maxSink = Sink.foreach[Int](x => println(s"Max is $x"))

  val max3RunnableGraph = RunnableGraph.fromGraph(
    GraphDSL.create() {implicit builder =>
      import GraphDSL.Implicits._

      // declare shapes
      val max3Shape = builder.add(max3StaticGraph)

      source1 ~> max3Shape.in(0)
      source2 ~> max3Shape.in(1)
      source3 ~> max3Shape.in(2)

      max3Shape.out ~> maxSink

      ClosedShape
    }
  )
//  max3RunnableGraph.run()
  // Same for fan out shapes - called uniform

  /** non uniform Fan out shape can deal with different types
   * Processing Bank transactions
   *  - txn suspicious if amount > 10000
   *
   *  - output 1:Let it go through
   *  - output 2: suspicious txn ids
   *  */

  case class Transaction(id: String, source: String, recipient: String, amount: Int, date: Date)

  val transactionSource = Source(List(
    Transaction("3251213678","Paul","Jim",100, new Date()),
    Transaction("3847539847","Paula","John",1000, new Date()),
    Transaction("9793847293","Dan","Robert",100000, new Date()),
    Transaction("1757284818","Ray","Josh",100, new Date())
  ))

  val bankProcessor = Sink.foreach[Transaction](println)
  val suspiciousAnalysisService = Sink.foreach[String](txnID => println(s"Suspicious Transaction ID: $txnID"))

  val suspiciousTransactionStaticGraph = GraphDSL.create(){implicit builder =>
    import GraphDSL.Implicits._

    // define shapes
    val broadcast = builder.add(Broadcast[Transaction](2))
    val suspiciousTxnFilter = builder.add(Flow[Transaction].filter(txn => txn.amount > 10000))
    val txnIDExtractor = builder.add(Flow[Transaction].map[String](txn => txn.id))

    // tying shapes together
    broadcast.out(0) ~> suspiciousTxnFilter ~> txnIDExtractor

    //
    new FanOutShape2(broadcast.in,broadcast.out(1),txnIDExtractor.out)
  }
  val suspiciousTxnRunnableGraph = RunnableGraph.fromGraph(
    GraphDSL.create(){implicit builder =>
      import GraphDSL.Implicits._
      //
      val suspiciousTxnShape = builder.add(suspiciousTransactionStaticGraph)

      transactionSource ~> suspiciousTxnShape.in

      suspiciousTxnShape.out0 ~> bankProcessor
      suspiciousTxnShape.out1 ~> suspiciousAnalysisService

      ClosedShape
    }
  )
  suspiciousTxnRunnableGraph.run()
}
/** RECAP
 * More complex shapes with the GraphDSL
 *  - fan-out
 *  - fan-in
 *  - uniform/non-uniform
 *  */
