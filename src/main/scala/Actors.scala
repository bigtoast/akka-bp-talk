
package com.github.bigtoast.akkaBpTalk

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.pattern.ask
import akka.util.Timeout
import akka.util.duration._

sealed trait BasicActorMessage

case object SayHello extends BasicActorMessage
case class Say( msg :String ) extends BasicActorMessage
case class SayTo( msg :String, other :ActorRef ) extends BasicActorMessage
case class ForwardTo( msg :String, other :ActorRef ) extends BasicActorMessage

class MrActor( name :String ) extends Actor {
  
  def receive :Receive = {
    
    case SayHello => sender ! name + " says hello"
    
    case Say( msg ) => sender ! name + " says " + msg
    
    case SayTo( msg, target ) => target ! msg 
    
    case ForwardTo( msg, target ) => target forward msg
    
  }
  
}

object ActorSample {
  
  val system = ActorSystem("sampleSystem")
  
  val charley = system.actorOf( Props( new MrActor("charley") ), name = "charley" )
  
  val ashok = system.actorOf( Props( new MrActor("ashok") ), name = "ashok" )
  
  implicit val timeout = Timeout( 1 second )
  
  def sayHello = charley ! SayHello // message sent but nothing done with response
  
  def printHello = ask( charley, SayHello ).mapTo[String].map { println _ } // multiple ways to write this.. check docs. I like ask instead of '?'
  
  def sayHiToAshokThroughCharley = ask( system.actorFor("/user/charley"), ForwardTo("Hi Ashok", ashok) ).mapTo[String].map { println _ }
  
}