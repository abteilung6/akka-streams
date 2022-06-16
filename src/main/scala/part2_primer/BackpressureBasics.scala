package part2_primer

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Sink, Source}

import scala.language.postfixOps

object BackpressureBasics extends App {
  implicit val system: ActorSystem = ActorSystem("BackpressureBasics")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  val fastSource = Source(1 to 1000)
  val slowSink = Sink.foreach[Int] { x =>
    // simulate long processing
    Thread.sleep(1000)
    println(s"sink ${x}")
  }

  // fastSource.to(slowSink).run() // fusing? single actor instance
  // not backpressure

  // fastSource.async.to(slowSink).run() // backpressure

  val simpleFlow = Flow[Int].map { x=>
    println(s"incoming ${x}")
    x + 1
  }
//  fastSource.async
//    .via(simpleFlow).async
//    .to(slowSink)
//    .run()

  /**
   * reactions to backpressure (in order):
   * - try to slow down if possible
   * - buffer elements until there is more demand
   * - drop elements from the buffer if it overflows
   * - tear down/kill the whole stream (failure)
   */

  val bufferedFlow = simpleFlow.buffer(10, overflowStrategy = OverflowStrategy.dropHead)
  fastSource.async
    .via(bufferedFlow).async
    .to(slowSink)
    // .run()
  /**
   * 1 - 16: nobody is backpressured
   * 17 - 26: flow will buffer, flow will start dropping at the next element
   * 26 - 1000: flow will always drop the oldest element
   *  => 991-1000 => 992 - 1001 => sink
   */

  /**
   * overflow strategies:
   * - drop head = oldest
   * - drop tail = newest
   * - drop new = element to be added = keep bufer
   * - drop the entire buffer
   * - backpressure signal
   * - fail
   */

  // throttling
  import scala.concurrent.duration._
  fastSource.throttle(10, 1 second).runWith(Sink.foreach(println))

}
