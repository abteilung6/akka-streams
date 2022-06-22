package part4_techniques

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Sink, Source}

import java.util.Date
import scala.language.postfixOps

object AdvancedBackpressure extends App {

  implicit val system: ActorSystem = ActorSystem("OpenGraphs")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  // control backpressure
  val controlledFlow = Flow[Int].map(_ * 2).buffer(10, OverflowStrategy.dropHead)

  case class PagerEvent(description: String, date: Date, nInstances: Int = 1)
  case class Notification(email: String, pagerEvent: PagerEvent)

  val events = List(
    PagerEvent("service discovery failed", new Date),
    PagerEvent("illegal elements int the date pipeline", new Date),
    PagerEvent("number of http 500 spiked", new Date),
    PagerEvent("a service stopped responding", new Date),
  )
  val eventSource = Source(events)

  val onCallEngineer = "daniel@foo.com"

  def sendMail(notification: Notification): Unit = {
    println(s"Dear ${notification.email}, event ${notification.pagerEvent}")
  }

  val notificationSink = Flow[PagerEvent]
    .map(event => Notification(onCallEngineer, event))
    .to(Sink.foreach[Notification](sendMail))

  // standard
  // eventSource.to(notificationSink).run()


  /**
   * un-backpressurable source
   */
  def sendEmailSlow(notification: Notification): Unit = {
    Thread.sleep(1000)
    println(s"Dear ${notification.email}, event ${notification.pagerEvent}")
  }

  val aggregateNotificationFlow = Flow[PagerEvent]
    .conflate((event1, event2) => {
      val nInstances = event1.nInstances + event2.nInstances
      PagerEvent(s"You have $nInstances events that require your attention", new Date, nInstances)
    })
    .map(resultingEvent => Notification(onCallEngineer, resultingEvent))

  // eventSource.via(aggregateNotificationFlow).async.to(Sink.foreach[Notification](sendEmailSlow)).run()
  // alternative to backpressure

  /*
  Slow producers: extrapolate/expand
   */
  import scala.concurrent.duration._
  val slowCounter = Source(Stream.from(1)).throttle(1, 1 second)
  val hungrySink = Sink.foreach[Int](println)

  val extrapolator = Flow[Int].extrapolate(element => Iterator.from(element))
  val repeater = Flow[Int].extrapolate(element => Iterator.continually(element))

  slowCounter.via(extrapolator).to(hungrySink).run()

  val expander = Flow[Int].expand(element => Iterator.from(element))

}
