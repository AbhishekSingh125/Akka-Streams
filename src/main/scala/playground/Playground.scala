package playground

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Keep, RunnableGraph, Sink, Source}

import scala.concurrent.Future

object Playground extends App {

  implicit val system = ActorSystem("AkkaStreamsDemo")
//  implicit val materializer: ActorMaterializer = ActorMaterializer()
  import system.dispatcher

  val source = Source(1 to 10)
  val flow = Flow[Int].map(x => println(x))
//  val sink = Sink.fold[Int, Int](0)(_ + _)
  val sink = Sink.fold[Int, Int](0)(_ + _)
  // Connect the source to the Sink, obtaining a RunnableGraph
  val runnable: RunnableGraph[Future[Int]] = source.toMat(sink)(Keep.right)

//  Source(1 to 10).via(flow).to(Sink.fold[Int, Int](0)(_ + _))(Keep.right)


  // materialize the flow and get the value of FoldSink
  val sum: Future[Int] = runnable.run()
  sum.onComplete(x => println(s"Sum: $x"))
}
