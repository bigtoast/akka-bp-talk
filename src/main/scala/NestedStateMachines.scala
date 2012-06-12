

package com.github.bigtoast.akkaBpTalk

import akka.actor._
import akka.dispatch._
import akka.util.Timeout
import akka.util.duration._
import akka.pattern.{ ask, pipe }

object NestedStateMachines {

  sealed trait TicketEvent { val id :String }
  case class IllegalStateRequest( id :String ) extends TicketEvent
  case class SoldEvent( id :String, user :User) extends TicketEvent
  case class RefundEvent( id :String, user :User ) extends TicketEvent
  case class User( name :String )
  
  /* domain state machine. simple GOF state pattern */
  trait Ticket {
    val id :String
    def holder :Option[User] = None
    
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
  
  case class TicketReceipt( ticket :Ticket, receipt :Receipt, replyTo :ActorRef )
  
  class TicketKeeper( ticketId :String, repo :TicketRepo, processor :PaymentProcessor ) extends Actor {
    sealed trait TicketKeeperState
    case object EMPTY extends TicketKeeperState
    case object RUNNING extends TicketKeeperState
    case object AUTHORIZING extends TicketKeeperState
    
    case class StartWith( ticket :Ticket )
    
    var state :TicketKeeperState = EMPTY
    
    var ticket :Ticket = _
    
    def initialized = ticket != null
    
    override def preStart = repo.get( ticketId ).foreach {
        context.self ! StartWith( _ )
      }
    
    val handleEmpty :Receive = {
      case StartWith( tkt ) => 
        ticket = tkt 
        state = RUNNING
    }
    
    val handleProcessing :Receive = {
      case msg :TicketReceipt =>
        ticket = msg.ticket  
        msg.replyTo ! ticket
        state = RUNNING
    }
    
    def handleRunning :Receive = {
      case Purchase( ccNo, user ) =>
        val replyTo = context.sender
        ticket.sell( user ) match {
          case ( tkt , event :SoldEvent ) =>
            processor.authorize( ccNo, 100 ) map { receipt => TicketReceipt( tkt, receipt, replyTo ) } pipeTo context.self
            state = AUTHORIZING
        }
    }
    
    def receive :Receive = {
      case msg if handleEmpty.isDefinedAt( msg ) && state == EMPTY => handleEmpty( msg )
      case msg if handleProcessing.isDefinedAt( msg ) && state == AUTHORIZING => handleEmpty( msg )
      case msg if handleRunning.isDefinedAt( msg ) && state == RUNNING => handleEmpty( msg )
      case msg => // decide what to do with unhandled.. drop or buffer
    }
    
  }
    
}