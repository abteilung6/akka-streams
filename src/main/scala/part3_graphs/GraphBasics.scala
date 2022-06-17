package part3_graphs

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ClosedShape}
import akka.stream.scaladsl.{Balance, Broadcast, Flow, GraphDSL, Merge, RunnableGraph, Sink, Source, Zip}

import scala.language.postfixOps

/*
                              -> flow
source -> fan-out (broadcast)             -> fan-in (zip) -> sink
                              -> flow
 */

object GraphBasics extends App {
  implicit val system: ActorSystem = ActorSystem("GraphBasics")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  val input = Source(1 to 1000)
  val incrementer = Flow[Int].map(x => x + 1) // hard computation
  val multiplier = Flow[Int].map(x => x * 10) // hard computation
  val output = Sink.foreach[(Int, Int)](println)

  val graph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] => // builder = mutable/mutate builder
      import GraphDSL.Implicits._ // brings operators into scope

      // step 2 - add the necessary components of this graph
      val broadcast = builder.add(Broadcast[Int](2)) // fan-out operator (two output, 0 and 1)
      val zip = builder.add(Zip[Int, Int]) // fan-in operator

      // step 3 - tying up the components
      input ~> broadcast

      broadcast.out(0) ~> incrementer ~> zip.in0
      broadcast.out(1) ~> multiplier ~> zip.in1

      zip.out ~> output

      // step 4 - must return a closed shape
      ClosedShape // freeze builders shape
    } // graph
  ) // runnable graph
  // graph.run() // run the graph and materialize it

  /**
   * exercise 1: feed a source into 2 sinks at the same time
   */

  val firstSink = Sink.foreach[Int](x => println(s"first sink ${x}"))
  val secondSink = Sink.foreach[Int](x => println(s"second sink ${x}"))

  // step 1
/*  val sourceToTwoSinkGraphs = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      // step 2 - declaring the components
      val broadcast = builder.add(Broadcast[Int](2))

      // step 3 - tying up the components
      // input ~> broadcast ~> firstSink // implicit port numbering
      // input ~> broadcast ~> secondSink
      broadcast.out(0)  ~> firstSink
      broadcast.out(1)  ~> secondSink

      ClosedShape
    }
  )

  /***/
   * exercise 2: balance
   */
  import scala.concurrent.duration._
  val fastSource = input.throttle(5, 1 second)
  val slowSource = input.throttle(2, 1 second)
  val sink1 = Sink.fold[Int, Int](0)((count, element) => {
    println("sink1", count, element)
    count + 1
  })
  val sink2 = Sink.fold[Int, Int](0)((count, element) => {
    println("sink2", count, element)
    count + 1
  })

  // step 1
  val balanceGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._
      // step 2 - declare components
      val merge = builder.add(Merge[Int](2)) // two input ports
      val balance = builder.add(Balance[Int](2))

      // step 3 - tying them up
      fastSource ~> merge ~> balance ~> sink1
      slowSource ~> merge
      balance ~> sink2

      // step 4
      ClosedShape
    }
  )

  balanceGraph.run()
}

