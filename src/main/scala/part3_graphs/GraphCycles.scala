package part3_graphs

import akka.actor.ActorSystem
import akka.stream.scaladsl.GraphDSL.Implicits._
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Merge, MergePreferred, RunnableGraph, Sink, Source, Zip}
import akka.stream.{ClosedShape, Materializer, OverflowStrategy, UniformFanInShape}

object GraphCycles extends App {

  val system = ActorSystem("GraphCycles")
  implicit val materialize: Materializer = Materializer(system)

  /** Goal
   * Build cycles in graphs with GraphDSL
   * Understand the dangers of cycles
   * */

  val accelerator = GraphDSL.create() {implicit builder =>


   val sourceShape = builder.add(Source(1 to 100))
   val mergeShape = builder.add(Merge[Int](2))
   val incrementerShape = builder.add(Flow[Int].map{ x =>
     println(s"Accelerating $x")
     x + 1
   })

    sourceShape ~> mergeShape ~> incrementerShape
    mergeShape <~ incrementerShape

    ClosedShape
  }

//  RunnableGraph.fromGraph(accelerator).run()
  // graph cycle deadlock

  /*
    Solution 1: MergePreferred ->
    Special version of Merge which has preferential input
    whenever an element that is available on the input it will take than on and pass it on regardless of what are present on the other outputs

   */

  val actualAccelerator = GraphDSL.create() {implicit builder =>


    val sourceShape = builder.add(Source(1 to 100))
    val mergeShape = builder.add(MergePreferred[Int](1))
    val incrementerShape = builder.add(Flow[Int].map{ x =>
      println(s"Accelerating $x")
      x + 1
    })

    sourceShape ~> mergeShape ~> incrementerShape
    mergeShape.preferred <~ incrementerShape

    ClosedShape
  }

//  RunnableGraph.fromGraph(actualAccelerator).run()

  /*
    Solution 2: Buffers
   */

  val bufferedRepeater = GraphDSL.create() { implicit builder =>


    val sourceShape = builder.add(Source(1 to 100))
    val mergeShape = builder.add(Merge[Int](2))
    val repeaterShape = builder.add(Flow[Int].buffer(10,OverflowStrategy.dropHead).map{ x =>
      println(s"Accelerating $x")
      Thread.sleep(100)
      x
    })

    sourceShape ~> mergeShape ~> repeaterShape
    mergeShape <~ repeaterShape

    ClosedShape
  }
//  RunnableGraph.fromGraph(bufferedRepeater).run()

  /*
   If you add cycle in your graph you risk deadlocking
    - add bounds to the number of elements in the cycle

    Tradeoff is between boundedness vs liveness
   */

  /** Challenge: create a fan-in shape
   *  - two inputs which will be fed with exactly One Number
   *  - output will emit INFINITE FIBONACCI SEQUENCE based off those numbers
   *
   *  HINT: use ZipWith and cycles, MergePreferred
   *  */

  val inputSourceGraph = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val zip = builder.add(Zip[BigInt, BigInt])
    val mergePreferred = builder.add(MergePreferred[(BigInt, BigInt)](1))

    val fiboLogic = builder.add(Flow[(BigInt, BigInt)].map{ pair =>
      val last = pair._1
      val previous = pair._2
      Thread.sleep(100)
      (last + previous, last)
    })

    val broadcast = builder.add(Broadcast[(BigInt, BigInt)](2))
    val extractLast = builder.add(Flow[(BigInt, BigInt)].map(_._1))

    zip.out ~> mergePreferred ~> fiboLogic ~> broadcast ~> extractLast
               mergePreferred.preferred <~ broadcast

    UniformFanInShape(extractLast.out, zip.in0, zip.in1)
  }

  val fibonacciGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val source1 = builder.add(Source.single[BigInt](1))
      val source2 = builder.add(Source.single[BigInt](1))

      val sink = builder.add(Sink.foreach[BigInt](println))

      val fibonacciGenerator = builder.add(inputSourceGraph)

      source1 ~> fibonacciGenerator.in(0)
      source2 ~> fibonacciGenerator.in(1)

      fibonacciGenerator.out ~> sink

      ClosedShape
    }
  )

  fibonacciGraph.run()

}