package part5_advanced

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Balance, GraphDSL, Merge, RunnableGraph, Sink, Source}
import akka.stream.{ClosedShape, Graph, Inlet, Materializer, Outlet, Shape}

import scala.concurrent.duration._
import scala.language.postfixOps

object CustomGraphShapes extends App {

  /** GOAL
   * Create components with arbitrary inputs and outputs and of arbitrary types
   *
   * GraphDSL could be used to implement a shape that is Merge + normal Balance
   * Problem: there is no predefined shape
   * */

  val system = ActorSystem("CustomGraphShapes")
  implicit val materializer = Materializer(system)

  /** Example
   * A M x N balance components with any number of inputs and outputs
   * This component would feed equal number of elements to each of its n outputs regardless of its rate of production of its inputs */

  // Balance 2 X 3 shape -> 2 input 3 output
  case class Balance2x3 (
                   in0: Inlet[Int],
                   in1: Inlet[Int],
                   out0: Outlet[Int],
                   out1: Outlet[Int],
                   out2: Outlet[Int]
                   ) extends Shape {

    // every Shape need to expose some ports
    // if you dont want the expression to be evaluated every single time use val instead of def

    override val inlets: Seq[Inlet[_]] = List(in0, in1)

    override val outlets: Seq[Outlet[_]] = List(out0, out1, out2)

    override def deepCopy(): Shape = Balance2x3(
      in0.carbonCopy(),
      in1.carbonCopy(),
      out0.carbonCopy(),
      out1.carbonCopy(),
      out2.carbonCopy()
    )
  }

  val balance2x3Impl = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val merge = builder.add(Merge[Int](2))
    val balance = builder.add(Balance[Int](3))

    merge ~> balance

    Balance2x3(merge.in(0),merge.in(1),balance.out(0),balance.out(1),balance.out(2))
  }

  val balance2x3Graph = RunnableGraph.fromGraph(
    GraphDSL.create() {implicit builder =>

      import GraphDSL.Implicits._

      val slowSource = Source(LazyList.from(1)).throttle(1, 1 second)
      val fastSource = Source(LazyList.from(1)).throttle(2, 1 second)

      def createSink(index: Int) = Sink.fold(0)((count: Int, element: Int) => {
      println(s"[$index] Received $element, current count is $count")
      count + 1
    })
      val sink1 = builder.add(createSink(1))
      val sink2 = builder.add(createSink(2))
      val sink3 = builder.add(createSink(3))

      val balance2x3 = builder.add(balance2x3Impl)

      slowSource ~> balance2x3.in0
      fastSource ~> balance2x3.in1

      balance2x3.out0 ~> sink1
      balance2x3.out1 ~> sink2
      balance2x3.out2 ~> sink3

      ClosedShape
    }
  )

//  balance2x3Graph.run()

  /** Exercise
   * Generalize the balance component, make it M x N */

  case class GeneralizedBalanced[T](override val inlets: List[Inlet[T]], override val outlets: List[Outlet[T]]) extends Shape {
    override def deepCopy(): Shape = GeneralizedBalanced(
      inlets.map(_.carbonCopy()), outlets.map(_.carbonCopy())
    )
  }
  object GeneralizedBalanced {
    def apply[T](input: Int, output: Int): Graph[GeneralizedBalanced[T], NotUsed] = GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val merge = builder.add(Merge[T](input))
      val balance = builder.add(Balance[T](output))

      merge ~> balance

      GeneralizedBalanced(merge.inlets.toList, balance.outlets.toList)
    }

  }

  val generalizedBalanceShape = RunnableGraph.fromGraph(
    GraphDSL.create(){ implicit builder =>
      import GraphDSL.Implicits._

      val source = builder.add(Source(LazyList.from(1)).throttle(1, 1 second))

      def createSink(index: Int) = Sink.fold(0)((count: Int, element: Int) => {
        println(s"[$index] Received $element, current count is $count")
        count + 1
      })

      val sink1 = builder.add(createSink(1))
      val sink2 = builder.add(createSink(2))
      val sink3 = builder.add(createSink(3))

      val generalizedBalanced = builder.add(GeneralizedBalanced[Int](1,3))

      source ~> generalizedBalanced.inlets(0)

      generalizedBalanced.outlets(0) ~> sink1
      generalizedBalanced.outlets(1) ~> sink2
      generalizedBalanced.outlets(2) ~> sink3

      ClosedShape
    }
  )

  generalizedBalanceShape.run()


}
