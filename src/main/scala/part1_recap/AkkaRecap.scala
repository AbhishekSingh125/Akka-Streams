package part1_recap

import akka.actor.SupervisorStrategy.{Restart, Stop}
import akka.actor.{Actor, ActorLogging, ActorSystem, OneForOneStrategy, PoisonPill, Props, Stash, SupervisorStrategy}
import akka.pattern.{ask, pipe}
import akka.util.Timeout

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

object AkkaRecap extends App {

  case class Greet(name: String)

  class SimpleActor extends Actor with ActorLogging with Stash {
    override def receive: Receive = {
      case "stash this" => stash()
      case "change handler" =>
        unstashAll()
        context.become(anotherHandler)
      case "create child" =>
        val child = context.actorOf(Props[SimpleActor],"myChild")
        child ! "Hello"
      case Greet(name) =>
        log.info(s"I have received a greeting from $name")
        context.become(anotherHandler)
      case message =>
        log.info(s"Hi $message")
    }
    def anotherHandler: Receive = {
      case message => println(s"Another handler is handling $message")

    }

    override def preStart(): Unit = {
      println("I am starting")
      super.preStart()
    }

    override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
      case _: RuntimeException => Restart
      case _ => Stop
    }
  }

  val system = ActorSystem("AkkaRecap")
  // #1 - you can only instantiate an actor through the actor system
  val actor = system.actorOf(Props[SimpleActor],"simpleActor")
  // #2 - sending messages
  actor ! Greet("Bob")

  /*
    - messages are sent asynchronously
    - many actors (in the millions) can share a few dozen threads
    - each message is handled automatically
    - need for locking disappears
    */

  // Changing Actor Behavior + stashing
  // actors can spawn another actors
  // guardians: /system, /user, / (root)

  // actors have a defined lifecycle: they can be started, stopped, suspended, resumed, restarted

  // stopping actors - context.stop or PoisonPill
  actor ! PoisonPill

  // logging
  // Supervision

  // configure Akka infrastructure: dispatchers, routers, schedulers, mailboxes

  // schedulers
  import system.dispatcher
  system.scheduler.scheduleOnce(2 seconds){
    actor ! "delayed by 2 second"
  }

  // Akka patterns using FSM + ask pattern
  implicit val timeout: Timeout = Timeout(3 seconds)
  actor ? "question"

  val future = actor ? "question"

  // the pipe pattern
  val anotherActor = system.actorOf(Props[SimpleActor],"anotherSimpleActor")
  future.mapTo[String].pipeTo(anotherActor)

}
