package part5_advanced

import akka.actor.ActorSystem
import akka.stream.scaladsl.{BroadcastHub, Keep, MergeHub, Sink, Source}
import akka.stream.{ActorMaterializer, KillSwitches}

import scala.concurrent.duration._
import scala.language.postfixOps

object DynamicStreamHandling extends App {

  implicit val system: ActorSystem = ActorSystem("DynamicStreamHandling")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  import system.dispatcher

  // #1 - Kill Switch
  val killSwitchFlow = KillSwitches.single[Int]
  val counter = Source(Stream.from(1)).throttle(1, 1 second).log("counter")
  val sink = Sink.ignore

/*  val killSwitch = counter
    .viaMat(killSwitchFlow)(Keep.right)
    .to(sink)
    .run()

  system.scheduler.scheduleOnce(3 seconds) {
    killSwitch.shutdown()
  }*/

  val anotherCounter = Source(Stream.from(1)).throttle(2, 1 second).log("another counter")
  val sharedKillSwitch = KillSwitches.shared("onButtonToRuleThemAll")
  counter.via(sharedKillSwitch.flow).runWith(Sink.ignore)
  anotherCounter.via(sharedKillSwitch.flow).runWith(Sink.ignore)

  system.scheduler.scheduleOnce(3 seconds) {
    sharedKillSwitch.shutdown()
  }

  // MergeHub
  val dynamicMerge = MergeHub.source[Int]
  val materializedSink = dynamicMerge.to(Sink.foreach[Int](println)).run()

  // use this sink any time we like
  Source(1 to 10).runWith(materializedSink)
  counter.runWith(materializedSink)

  // BroadcastHub
  val dynamicBroadcast = BroadcastHub.sink[Int]
  val materializedSource = Source(1 to 1000).runWith(dynamicBroadcast)

  materializedSource.runWith(Sink.ignore)
  materializedSource.runWith(Sink.foreach[Int](println))

}
