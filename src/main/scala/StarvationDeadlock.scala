
package com.github.bigtoast.akkaBpTalk

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import akka.util.duration._
import akka.dispatch._

object StarvationDeadlock {

  class Sender( responders :Seq[ActorRef] ) extends Actor {
    
    implicit val ecxt = ExecutionContext.defaultExecutionContext( context.system )
    
    def receive :Receive = {
      case msg :String =>
        val futures = responders.map { responder => ask( responder, msg ).mapTo[String] }
        val result = Await.result( Future.sequence( futures ), to.duration ) // never do this. deadlock waiting to happen
        sender ! result.mkString(",")      
    }
  }
  
  class Responder extends Actor {
    def receive :Receive = {
      case msg :String => sender ! msg.reverse
    }
  }
  
  val system = ActorSystem("DeadlockMe")
  
  implicit val context = ExecutionContext.defaultExecutionContext( system )
  
  implicit val to = Timeout( 1 second )
  
  lazy val responders = for ( i <- 1 to 20 ) yield { system.actorOf(Props[Responder]) }
  
  lazy val senders = for ( i <- 1 to 10 ) yield { system.actorOf( Props( new Sender( responders ) ) ) } 

  def main(args :Array[String]) = {
    
    val results = "this will deadlock unpredictably".split(" ").toSeq.flatMap { str =>
      senders.map { sender  => ask(sender, str ).mapTo[String] }
    }
    
    Future.sequence( results ) map { results =>
      results foreach { println _ }
    }
    
  }

}