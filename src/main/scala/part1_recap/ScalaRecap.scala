package part1_recap

import scala.concurrent.Future
import scala.language.postfixOps
import scala.util.{Failure, Success}

object ScalaRecap extends App {

  val aCondition: Boolean = false

  def aFunction(x: Int) = x + 1

  // instruction vs expressions
  // types and type inference

  // OO features of scala
  class Animal
  trait Carnivore {
    def eat (a: Animal): Unit
  }
  object Carnivore

  // generics
  abstract class MyList[+A]

  // method notations
  1 + 3
  1.+(2)

  // Functional Programming
  val anIncrementor:Int => Int = (x: Int) => x + 1

  // HOFs: flatmap, filter, map
  List(1,2,3,4).map(anIncrementor)

  // for-comprehensions
  // Monads: Option, try

  // Pattern Matching
  val unknown: Any = 2

  val order = unknown match {
    case 1 => "first"
    case 2 => "second"
    case _ => "unknown"
  }

  try {
    // code that throws an expection
    throw new RuntimeException
  } catch {
    case e: Exception => println("I caught one!")
  }

  /** Scala Advanced
   *  */

  // Multithreading fundamentals

  import scala.concurrent.ExecutionContext.Implicits.global
  val future = Future {
    // long computation
    // executed on SOME other thread
    42
  }

  // map, flatmap, filter, other niceties e.g. recover/recover with
  future.onComplete{
    case Success(value) => println(s"Success: $value")
    case Failure(exception) => println(s"I failed with $exception")
  } // on some thread

  val partialFunction: PartialFunction[Int, Int] = {
    case 1 => 42
    case 2 => 89
    case _ => 666
  } // based on pattern matching

  // type aliases
  type AkkaReceive = PartialFunction[Any, Unit]
  def receive: AkkaReceive = {
    case 2 => 90
    case _ => 666
  }

  // Implicits
  implicit val timeout = 3000

  def setTimeout(f: () => Unit)(implicit timeout: Int) = f()

  setTimeout(() => println("Helloooo"))

  // conversion
  // 1) implicits methods
  case class Person(name: String) {
    def greet: String = s"Hi, my name is $name"
  }
  implicit def fromStringToPerson(name: String) = Person(name)

  "Hello".greet

  // Implicit classes
  implicit class Dog(name: String) {
    def bark = println("bark")
  }
  "Lassie".bark

  // Implicit organizations
  implicit val numberOrdering: Ordering[Int] = Ordering.fromLessThan(_ > _)
  List(1,2,3).sorted

  // imported scope

  // companion objects of types involved in the call
  object Person {
    implicit val personOrdering: Ordering[Person] = Ordering.fromLessThan((a, b) => a.name.compareTo(b.name) < 0)
  }

  List(Person("bob"),Person("Alice")).sorted // Person.personOrdering
  // List -> Person (alice) and Person(bob)
}
