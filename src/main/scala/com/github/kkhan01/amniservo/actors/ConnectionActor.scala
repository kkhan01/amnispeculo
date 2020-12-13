package com.github.kkhan01.amniservo.actors

import java.net.Socket
import java.io.PrintStream

import scala.io.BufferedSource

import akka.actor.{Actor, PoisonPill}

import com.github.kkhan01.amniservo.utils.Helpers

class ConnectionActor(methods: Map[String, (Map[String, String]) => String]) extends Actor {
  var connection: java.net.Socket = _
  var in: Iterator[String] = _
  var out: java.io.PrintStream = _

  override def preStart(): Unit = {
    println("Started connection.")
  }

  override def postStop(): Unit = {
    println("Killed connection.")
  }

  // TODO: refactor out to rest vs stream
  def process(f: (Map[String, String]) => String, qP: Map[String, String]) = {
    try {
      val res = """HTTP/1.0 200 OK

"""
      out.println(res + f(qP))
      close()
    } catch {
      case err: java.util.NoSuchElementException => {
        close()
      }
      case err: Throwable =>{
        println("Debug: unknown error:")
        println(err)
        invalid(err)
      }
    }
  }

  // TODO: refactor out to an actor
  def validate(): (Boolean, (Map[String, String]) => String, Map[String, String]) = {
    val input = in.next()

    val(url, queryParams) = Helpers.parseHeader(input)
    if (methods.contains(url)){
      return (true, methods(url), queryParams)
    } else if (methods.contains("_")){
      return (true, methods("_"), queryParams)
    }

    return (false, (_: Map[String, String]) => "", queryParams)
  }

  def invalid(err: String): Unit = {
    // TODO: implement real 500 http error
    val res = """HTTP/1.0 200 OK

"""
    out.println(res + err)
    close()
  }

  def close(): Unit = {
    connection.close()
    self ! PoisonPill
  }

  def receive = {
    case socket: java.net.Socket => {
      connection = socket
      in = new BufferedSource(connection.getInputStream()).getLines()
      out = new PrintStream(connection.getOutputStream())

      val (valid, fn, qP) = validate()
      if (valid) {
        process(fn, qP)
      } else {
        invalid("Invalid route.")
      }
    }
    case _ => println("Debug: something went wrong, socket wasn't passed in.")
  }
}
