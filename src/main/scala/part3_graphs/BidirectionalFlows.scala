package part3_graphs

import akka.actor.ActorSystem
import akka.stream.{BidiShape, ClosedShape, Materializer}
import akka.stream.scaladsl.{Flow, GraphDSL, RunnableGraph, Sink, Source}

object BidirectionalFlows extends App {

  /** Goal
   * Flows that go both ways
   * */

  val system = ActorSystem("BiDirectionalFlows")
  implicit val materialize: Materializer = Materializer(system)

  /** Example: Cryptography */

  def encrypt(n: Int)(string: String) = string.map(c => (c + n).toChar)
  def decrypt(n: Int)(string: String) = string.map(c => (c - n).toChar)

  println(encrypt(3)("Akka"))
  println(decrypt(3)("Dnnd"))

  /** Often stay in same place i.e. same component known as bidiFLow */

  val bidiCryptoStaticGraph = GraphDSL.create() {implicit builder =>

   val encryptionFlowShape = builder.add(Flow[String].map(encrypt(3)))
   val decryptionFlowShape = builder.add(Flow[String].map(decrypt(3)))

//   BidiShape(encryptionFlowShape.in,encryptionFlowShape.out,decryptionFlowShape.in,decryptionFlowShape.out)
    BidiShape.fromFlows(encryptionFlowShape, decryptionFlowShape)
  }

  val unencryptedStrings = List("Akka","is","awesome","testing","bidirectional","flows")
  val unencryptedSource = Source(unencryptedStrings)

  val encryptedSource = Source(unencryptedStrings.map(encrypt(3)))

   val cryptoBiDiGraph = RunnableGraph.fromGraph(
     GraphDSL.create(){implicit builder =>
       import GraphDSL.Implicits._

       val unencryptedSourceShape = builder.add(unencryptedSource)
       val encryptedSourceShape = builder.add(encryptedSource)
       val bidi = builder.add(bidiCryptoStaticGraph)
       val encryptedSinkShape = builder.add(Sink.foreach[String](string => println(s"encrypted: $string")))
       val decryptedSinkShape = builder.add(Sink.foreach[String](string => println(s"decrypted: $string")))

       unencryptedSourceShape ~> bidi.in1
       bidi.out1 ~> encryptedSinkShape

       bidi.in2 <~ encryptedSourceShape
       decryptedSinkShape <~ bidi.out2

       ClosedShape
     }
   )

  cryptoBiDiGraph.run()
  /* Encrypting / Decrypting
     Encoding / decoding
     serializing / deserializing
   */
}
