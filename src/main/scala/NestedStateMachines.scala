

package com.github.bigtoast.akkaBpTalk

import akka.actor._
import akka.dispatch._
import akka.util.Timeout
import akka.util.duration._
import akka.pattern.{ ask, pipe }

/**
 * For non trivial state machines combining domain and runtime state in
 * a single actor state machine can lead to complex, buggy and unmaintainable 
 * code. This is generally due to the amount of blocks in the receive 
 * partial function. Although this example is very simple, I hope it shows
 * how to model a domain state machine contained in a runtime state machine. There
 * are many, many ways to model state machines with actors: FSM, become/unbecome and
 * partial function composition.
 */
object NestedStateMachines {

  sealed trait TicketEvent { val ticketId :String }
  case class IllegalStateRequest( ticketId :String ) extends TicketEvent
  case class SoldEvent( ticketId :String, user :User) extends TicketEvent
  case class RefundEvent( ticketId :String, user :User ) extends TicketEvent
  case class User( name :String )
  
  /* domain state machine. simple GOF state pattern */
  trait Ticket {
    val id :String
    /* a sold ticket will have a holder */
    def holder :Option[User] = None
    
    /* state transition methods return the tuple (Ticket,TicketEvent) */
    def sell( user :User ) :(Ticket, TicketEvent) = ( this, IllegalStateRequest( id ) )
    def refund :(Ticket, TicketEvent) = ( this, IllegalStateRequest( id ) )
  }

  class OpenTicket( val id :String ) extends Ticket {
    override def sell( user :User ) = (new SoldTicket(id, user ), SoldEvent( id, user ) )
  }
  
  class SoldTicket( val id :String, user :User ) extends Ticket {
    override def holder = Some(user)
    override def refund = (new OpenTicket( id ), RefundEvent( id, user ) )
  }
  
  object NullTicket extends Ticket { val id = "null" }
  
  sealed trait TicketMessage
  case class Purchase( ccNo :String, user :User ) extends TicketMessage
  
  trait TicketRepo { def get( id :String ) :Future[Ticket] }
  trait PaymentProcessor { def authorize( ccNo :String, amount :BigDecimal) :Future[Receipt] }
  case class Receipt( orderNo :String )
  
  /* runtime state machine modeled as an actor and just using separate receive blocks to handle the
   * different states. There is no error handling in this for brevity */
  class TicketKeeper( ticketId :String, repo :TicketRepo, processor :PaymentProcessor ) extends Actor {
    sealed trait TicketKeeperState
    case object EMPTY extends TicketKeeperState
    case object RUNNING extends TicketKeeperState
    case object AUTHORIZING extends TicketKeeperState
    
    /* internal messages */
    case class StartWith( ticket :Ticket )
    case class TicketReceipt( ticket :Ticket, receipt :Receipt, replyTo :ActorRef )
    
    /* initial state */
    var state :TicketKeeperState = EMPTY
    
    var ticket :Ticket = _
    
    def initialized = ticket != null
    
    override def preStart = repo.get( ticketId ).foreach {
        context.self ! StartWith( _ )
      }
    
    /* handle block for the empty state */
    val handleEmpty :Receive = {
      case StartWith( tkt ) => 
        ticket = tkt 
        state = RUNNING
    }
    
    /* handle block for authorizing state */
    val handleAuthorizing :Receive = {
      case msg :TicketReceipt =>
        ticket = msg.ticket  
        msg.replyTo ! ticket
        state = RUNNING
    }
    
    /* handle block for running state */
    val handleRunning :Receive = {
      case Purchase( ccNo, user ) =>
        val replyTo = context.sender
        ticket.sell( user ) match {
          case ( tkt , event :SoldEvent ) =>
            processor.authorize( ccNo, 100 ) map { receipt => TicketReceipt( tkt, receipt, replyTo ) } pipeTo context.self
            state = AUTHORIZING
        }
    }
    
    /* main receive block */
    def receive :Receive = {
      case msg if handleEmpty.isDefinedAt( msg ) && state == EMPTY => handleEmpty( msg )
      case msg if handleAuthorizing.isDefinedAt( msg ) && state == AUTHORIZING => handleEmpty( msg )
      case msg if handleRunning.isDefinedAt( msg ) && state == RUNNING => handleEmpty( msg )
      case msg => // decide what to do with unhandled.. drop, buffer, reply with error etc..
    }
    
  }
    
}