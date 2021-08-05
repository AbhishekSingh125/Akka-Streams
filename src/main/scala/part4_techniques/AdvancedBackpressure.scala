package part4_techniques

import akka.actor.ActorSystem
import akka.stream.Materializer

object AdvancedBackpressure extends App {

  /** Goal
   * Rates and Buffers
   * Coping with an un-backpressureable source (Fast source that cannot be back pressured)
   * Extrapolating / expanding (How to deal with a fast sink)
   * */

  val system = ActorSystem("AdvanceBackpressure")
  implicit val materializer = Materializer(system)

  

}
