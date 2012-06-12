package com.github.bigtoast.akkaBpTalk


import akka.dispatch._

case class SomeStuff( msg :String ) extends Stuff
case class OtherStuff( msg :String ) extends Stuff

trait ComposableService {
  
  protected implicit def context :ExecutionContext
  
  def doSomeStuffDirect :Future[SomeStuff] = Promise.successful( SomeStuff("don't cross thread boundries if we don't need to") )
  
  def doSomeStuff :Future[SomeStuff] = Future {
    // doing all kinds of crazy stuff 
    SomeStuff("anyc and possibly on another thread")
  }
  
  // futures are composible
  def doWithStuff( stuff :SomeStuff ) :Future[OtherStuff] = doSomeStuff.map { result => OtherStuff( result.msg + stuff.msg ) }
  
  def doMultipleStuff :Future[Stuff] = doSomeStuff flatMap { doWithStuff _ }
  
}