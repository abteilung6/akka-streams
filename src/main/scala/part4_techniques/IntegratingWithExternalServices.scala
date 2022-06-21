package part4_techniques

import akka.{Done, NotUsed}
import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.Timeout

import java.util.Date
import scala.concurrent.Future
import scala.language.postfixOps

object IntegratingWithExternalServices extends App {

  implicit val system: ActorSystem = ActorSystem("IntegratingWithExternalServices")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  def genericExternalService[A, B](element: A): Future[B] = ???

  // example: simplified PagerDuty

  case class PagerEvent(application: String, description: String, date: Date)

  val eventSource: Source[PagerEvent, NotUsed] = Source(List(
    PagerEvent("AkkaInfra", "Infrastructure broke", new Date),
    PagerEvent("FastDataPipeline", "Illegal elements in the data pipeline", new Date),
    PagerEvent("AkkaInfra", "A service is not responding", new Date),
    PagerEvent("SuperFrontend", "A button does not work", new Date),
  ))

  import system.dispatcher // not recommended
  // implicit val dispatcher = system.dispatchers.lookup("dedicated-dispatcher")
  object PagerService {
    private val engineers = List("Daniel", "John", "Lady Gaga")
    private val emails = Map(
      "Daniel" -> "daniel@foo.com",
      "John" -> "john@foo.com",
      "Lady Gaga" -> "ladygaga@foo.com"
    )

    def processEvent(pagerEvent: PagerEvent) = Future {
      val engineerIndex = (pagerEvent.date.toInstant.getEpochSecond / (24 * 3600)) % engineers.length
      val engineer = engineers(engineerIndex.toInt)
      val engineerEmail = emails(engineer)

      // page the engineer
      println(s"sending engineer $engineerEmail a high priority notification $pagerEvent")
      Thread.sleep(1000)

      // return the email that was paged
      engineerEmail
    }
  }

  val infraEvents: Source[PagerEvent, NotUsed] = eventSource.filter(_.application == "AkkaInfra")
  val pagedEngineersEmails: Source[String, NotUsed] =
    infraEvents.mapAsync(parallelism = 4)(event => PagerService.processEvent(event))
  // guarantees the relative order of elements
  val pagedEmailsSink: Sink[String, Future[Done]] = Sink.foreach[String](email => println(s"sent notification to $email"))

  pagedEngineersEmails.to(pagedEmailsSink).run()

  class PagerActor extends Actor with ActorLogging {
    private val engineers = List("Daniel", "John", "Lady Gaga")
    private val emails = Map(
      "Daniel" -> "daniel@foo.com",
      "John" -> "john@foo.com",
      "Lady Gaga" -> "ladygaga@foo.com"
    )

    private def processEvent(pagerEvent: PagerEvent) = {
      val engineerIndex = (pagerEvent.date.toInstant.getEpochSecond / (24 * 3600)) % engineers.length
      val engineer = engineers(engineerIndex.toInt)
      val engineerEmail = emails(engineer)

      // page the engineer
      println(s"sending engineer $engineerEmail a high priority notification $pagerEvent")
      Thread.sleep(1000)

      // return the email that was paged
      engineerEmail
    }

    override def receive: Receive = {
      case pagerEvent: PagerEvent =>
        sender() ! processEvent(pagerEvent)
    }
  }

  import akka.pattern.ask
  import scala.concurrent.duration._
  implicit val timeout = Timeout(3 seconds)
  val pagerActor = system.actorOf(Props[PagerActor], "pagerActor")
  val alternativePageEngineerEmails = infraEvents.mapAsync(parallelism = 4)(event => (pagerActor ? event).mapTo[String])
  alternativePageEngineerEmails.to(pagedEmailsSink).run()

  // do not confuse mapAsync with async (async boundary)
}

