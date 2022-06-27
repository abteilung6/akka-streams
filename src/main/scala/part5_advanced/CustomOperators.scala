package part5_advanced

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{ActorMaterializer, Attributes, Inlet, Outlet, SinkShape, SourceShape}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}

import scala.collection.mutable
import scala.util.Random

object CustomOperators extends App {

  implicit val system: ActorSystem = ActorSystem("CustomOperators")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  // 1 - a custom source which emits random numbers until canceled

  class RandomNumberGenerator(max: Int) extends GraphStage[SourceShape[Int]] {
    val outPort: Outlet[Int] = Outlet[Int]("randomGenerator")
    val random = new Random()

    override def shape: SourceShape[Int] = SourceShape(outPort)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
      // implement logic here
      setHandler(outPort, new OutHandler {
        // called when there is demand from downstream
        override def onPull(): Unit = {
          // emit a new element
          val nextNumber = random.nextInt(max)
          // push it out of the outPort
          push(outPort, nextNumber)
        }
      })
    }
  }

  val randomGeneratorSource = Source.fromGraph(new RandomNumberGenerator(100))
  // randomGeneratorSource.runWith(Sink.foreach(println))

  // 2 - a custom sink that prints elements in batches of a given size
  class Batcher(batchSize: Int) extends GraphStage[SinkShape[Int]]  {

    val inPort: Inlet[Int] = Inlet[Int]("Batcher")

    override def shape: SinkShape[Int] = SinkShape[Int](inPort)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {

      override def preStart(): Unit = {
        pull(inPort)
      }

      // mutable state only in createLogic not ouside
      val batch = new mutable.Queue[Int]

      setHandler(inPort, new InHandler {
        // when the upstream wants to send me an element
        override def onPush(): Unit = {
          val nextElement = grab(inPort)
          batch.enqueue(nextElement)

          // assume some complex computation
          Thread.sleep(100)
          if (batch.size >= batchSize) {
            println("New batch: " + batch.dequeueAll(_ => true).mkString("[", ",", "]"))
          }

          pull(inPort) // send demand upstream
        }

        override def onUpstreamFinish(): Unit = {
          if (batch.nonEmpty) {
            println("New batch: " + batch.dequeueAll(_ => true).mkString("[", ",", "]"))
            println("Stream finished")
          }
        }
      })
    }
  }

  val batcherSink: Sink[Int, NotUsed] = Sink.fromGraph(new Batcher(10))
  randomGeneratorSource.to(batcherSink).run()

}
