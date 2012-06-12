
package com.github.bigtoast.akkaBpTalk

import akka.actor._
import akka.dispatch._
import akka.util.Timeout
import akka.util.duration._
import akka.pattern.ask

object KeeperRegistry {

    /* simple keeper registry api for use in low level services */
	trait KeeperRegistry[M] {
	  
	  def extractId( msg :M ) :String
	  
	  def keeperFor( keeperId :String ) :Future[ActorRef]
	  
	  def timeout :Timeout 
	  
	  def dispatch[R]( msg :M )( implicit m :Manifest[R] ) :Future[R] = {
	    implicit val t = timeout
	    keeperFor( extractId( msg ) ).flatMap { keeper =>
	      keeper ask msg   
	    }.mapTo[R]
	  }	    
	}
  

  case class User( name :String )
  sealed trait TicketMessage { def id :String }
  case class Purchase( id :String, ccNo :String, user :User ) extends TicketMessage

  trait TicketKeeper extends KeeperRegistry[TicketMessage] {
    
    def extractId(msg :TicketMessage) = msg.id
    
  }
  
  trait Ticket // assuming this is defined somewhere else
  
  trait TicketService {
    def ticketKeeper :TicketKeeper  
  
    def purchase( ticketId :String, ccNo :String, user :User ) :Future[Ticket] = ticketKeeper.dispatch[Ticket]( Purchase( ticketId, ccNo, user) )
  }
 
}