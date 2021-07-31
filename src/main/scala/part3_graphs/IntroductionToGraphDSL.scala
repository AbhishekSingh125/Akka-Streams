package part3_graphs

import akka.actor.ActorSystem
import akka.stream.Materializer

object IntroductionToGraphDSL extends App {

  /** Goal
   * Write complex Akka Streams graphs
   * familiarize with graph DSL
   * non-linear components
   *  - fon out
   *  - fan in*/

  val system = ActorSystem("IntroductionToGraphDSL")
  implicit val materializer = Materializer(system)



}
