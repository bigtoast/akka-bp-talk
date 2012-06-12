
package com.github.bigtoast.akkaBpTalk

import akka.actor._
import akka.dispatch._
import akka.util.Timeout
import akka.util.duration._
import akka.pattern.ask

object FSMDeadlock {

  sealed trait TicketState
  case object AVAILABLE extends TicketState
  case object SOLD extends TicketState
  
  case class UserData( creditCardNo :String )
  case class Purchase( data :UserData )
  case class Authorize( creditCardNo :String )
  case class Receipt( orderNo :String )
  
  class Ticket extends Actor with FSM[TicketState,Option[UserData]]  {
    
    lazy val paymentProcessor = context.actorFor("/user/paymentProcessor")
    implicit val to = Timeout( 1 second )
    
    startWith(AVAILABLE, None)
    
    when( AVAILABLE ) {
      case Event( Purchase( data ), None ) => 
        // This may deadlock. You must pipe the message back to yourself and introduce intermediate states. This 
        // however introduces complexity because we are mixing runtime and domain state in the same state machine.
        // A better way is to create nested state machines will we will cover later.
        val receipt = Await.result( ask( paymentProcessor, Authorize( data.creditCardNo ) ).mapTo[Receipt], to.duration ) 
        goto( SOLD ) using( Some( data ) ) replying( receipt )
    }
    
    // leaving out the rest for brevity..
  }
}