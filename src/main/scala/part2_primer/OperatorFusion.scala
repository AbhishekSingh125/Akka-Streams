package part2_primer

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}

object OperatorFusion extends App {

  /** Goal
   * Stream components running on the same actor
   * async boundaries between stream components
   * */

  val system = ActorSystem("OperatorFusion")
  implicit val materializer = Materializer(system)

  val simpleSource = Source(1 to 1000)
  val simpleFlow = Flow[Int].map(_ + 1)
  val simpleFlow2 = Flow[Int].map(_ * 10)
  val simpleSink = Sink.foreach[Int](println)

  // This runs on the same actor
  val simpleGraph = simpleSource.viaMat(simpleFlow)(Keep.right).viaMat(simpleFlow2)(Keep.right).toMat(simpleSink)(Keep.right)
//  simpleGraph.run()
  // called Operator or Component fusion by default behind the scenes to improve throughput

  // Operator fusion causes more harm than good if the operations are time expensive

  // Complex Flows:
  val complexFlow = Flow[Int].map{ x =>
    // simulating a long computation
    Thread.sleep(1000)
    x + 1
  }

  val complexFlow2 = Flow[Int].map{ x =>
    // simulating a long computation
    Thread.sleep(1000)
    x * 10
  }

  // between results 2 seconds pass that is because two Thread sleeps are operating on the same actor
//  simpleSource.viaMat(complexFlow)(Keep.right).viaMat(complexFlow2)(Keep.right).to(simpleSink).run()

  /*
   Whole point of akka stream is to process asynchronously in between all these components - we would like to increase the throughput of these components

   When operators are expensive it is worth making them run separately in parallel on different actors
   */

  // Async Boundary

//  simpleSource.viaMat(complexFlow)(Keep.right).async // runs on one actor
//    .viaMat(complexFlow2)(Keep.right).async // runs on another actor
//    .toMat(simpleSink)(Keep.right) // runs on third actor
//    .run()

  /** These async calls are called async boundaries is the Akka Streams Api to break Operator Fusion that Akka Streams does by default
   * as many async boundaries can be applied */

  /** ASYNC BOUNDARIES
   * An async boundary contains
   *  - everything from the previous boundary (if any)
   *  - everything between the previous boundary and this boundary
   *
   * Communication based on asynchronously actor messages
   *  */

  /* Ordering guarantees
   */

  Source(1 to 3)
    .map(element => {
      println(s"Flow A: $element")
      element
    })
    .map(element => {
      println(s"Flow B: $element")
      element
    })
    .map(element => {
      println(s"Flow C: $element")
      element
    })
    .runWith(Sink.ignore)

  Source(1 to 3)
    .map(element => {
      println(s"Flow A: $element")
      element
    }).async
    .map(element => {
      println(s"Flow B: $element")
      element
    }).async
    .map(element => {
      println(s"Flow C: $element")
      element
    }).async
    .runWith(Sink.ignore)

  /** RECAP
   * Akka Streams components are fused = run on the same actor
   * Async Boundaries
   *  - components run on different actors
   *  - better throughput
   *
   * Best When: Individual operations are expensive
   * Avoid When: Operations are comparable with a message pass
   *
   * Ordering Guarantees
   * */
}
