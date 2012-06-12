
package com.github.bigtoast.akkaBpTalk

import akka.actor._
import akka.dispatch._
import akka.pattern.pipe


object LeakyLeaksAlot {
  
	trait Stuff
	/* simple service that returns some stuff in a future */
	trait StuffService {
	  def doStuff :Future[Stuff]
	}
	
	case class DoStuff( str :String )
	
	class LeakyLeaksAlot( service :StuffService ) extends Actor {
	  
	  var currentStuff :List[Stuff] = Nil
	
	  def receive = {
	    /* BAD. the doStuff method returns a future. The future 'map' callback will be executed at a later time.
	     * By the time the callback executes the actor may be processing another message which means the 
	     * sender method would return the sender of the current message being processed so we would be replying
	     * to the wrong sender. */
	    case DoStuff("who will I go to?") => // bad. 
	      service.doStuff.map { sender ! _ }
	   
	    /* Better. This will fix the problem above my extracting the current sender onto the local stack in an 
	     * immutable val. */
	    case DoStuff("extract sender to local stack") => // better
	      val replyTo = sender
	      service.doStuff.map { replyTo ! _ }
	  
	    /* Best. Akka provides a pipe method in the akka.pattern package which takes the result of a future and 
	     * 'pipes' it to an actor. */
	    case DoStuff("use pipe. its rad") => // best
	      service.doStuff.pipeTo( sender )
	
	   /* Very Bad. This actor has a local mutable variable 'currentStuff'. By putting the 'currentSuff' variable in
	    * the 'map' callback, we have leaked a mutable variable onto another thread. 
	    */
	   case DoStuff("never do this") => // very bad. local mutable var leaked into future block
	     service.doStuff.map { stuff => currentStuff = currentStuff :+ stuff } 
	  }
	  
	}

}