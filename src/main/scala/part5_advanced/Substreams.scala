package part5_advanced

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Keep, Sink, Source}

import scala.util.{Failure, Success}

object Substreams extends App {

  /** Goal
   * Create streams dynamically
   * Process sub streams uniformly */

  val system = ActorSystem("SubStreams")
  implicit val materializer = Materializer(system)
  import system.dispatcher

  // 1 - grouping a stream by a certain function
  val wordsSource = Source(List("Akka","is","amazing","learning","substreams"))
  // this expression will create streams of streams
  val groups = wordsSource.groupBy(30,word => if (word.isEmpty) '\u0000' else word.toLowerCase().charAt(0))
  // will act something like a source
  groups.to(Sink.fold(0)((count, word) => {
    val newCount = count + 1
    println(s"I have just received $word, count is $newCount")
    newCount
  }))
//    .run()

  // 2 - How to merge substreams back into a master stream
  val textSource = Source(List(
    "I love akka Streams",
    "this is amazing",
    "learning from rock the jvm"
  ))

  val totalCharacterCountFuture = textSource.groupBy(2, word => word.length % 2)
    .map(_.length) // Do your expensive computation here
    .mergeSubstreamsWithParallelism(2) // this number is cap that means the number of streams that can merge at any given time - risks deadlocking if substreams are infinite
    .toMat(Sink.reduce[Int](_ + _))(Keep.right)
    .run()

  totalCharacterCountFuture.onComplete {
    case Success(value) => println(s"total char count: $value")
    case Failure(exception) => println(s"Char computation failed: $exception")
  }
  // 3 - splitting a stream into substreams, when a condition is met
  val text = "I love akka Streams\n" + "this is amazing\n" + "learning from rock the jvm\n"

  val anotherCharCountFuture = Source(text.toList)
    .splitWhen(c => c == '\n')
    .filter(_ != '\n')
    .map(_ => 1)
    .mergeSubstreams
    .toMat(Sink.reduce[Int](_ + _))(Keep.right)
    .run()

  anotherCharCountFuture.onComplete{
    case Success(value) => println(s"total value is $value")
    case Failure(ex) => println(s"Oops I have failed $ex")
  }

  // 4 - flattening
  /** Flattening
   * Each element can spin up its own stream
   * Choose how to flatten
   *  - concatenate
   *  - merge
   */

  val simpleSource = Source(1 to 5)
  simpleSource.flatMapConcat(x => Source(x to 3 * x)).runWith(Sink.foreach(println))
  simpleSource.flatMapMerge(2,x => Source(x to 3 * x)).runWith(Sink.foreach(println))

}
