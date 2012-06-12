
package com.github.bigtoast.akkaBpTalk

import akka.actor._
import akka.dispatch._
import akka.pattern.pipe

trait Stuff

trait StuffService {
  def doStuff :Future[Stuff]
}

case class DoStuff( str :String )

class LeakyLeaksalot( service :StuffService ) extends Actor {
  
  var currentStuff :List[Stuff] = Nil

  def receive = {
    case DoStuff("who will I go to?") => // bad
      service.doStuff.map { sender ! _ }
   
    case DoStuff("extract sender to local stack") => // better
      val replyTo = sender
      service.doStuff.map { replyTo ! _ }
  
    case DoStuff("use pipe. its rad") => // best
      service.doStuff.pipeTo( sender )

   case DoStuff("never do this") => // very bad. local mutable var leaked into future block
     service.doStuff.map { currentStuff :+ _ } 
  }
  
}