package part3_graphs

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ClosedShape, FlowShape, SinkShape, SourceShape}
import akka.stream.scaladsl.{Broadcast, Concat, Flow, GraphDSL, RunnableGraph, Sink, Source}

object OpenGraphs extends App {
  implicit val system: ActorSystem = ActorSystem("OpenGraphs")
  implicit val materializer: ActorMaterializer = ActorMaterializer()


  /*
  Composite source that concatenates 2 sources
  - emits all the elements from the first source
  - then all  th elements from the second
   */

  val firstSource = Source(1 to 10)
  val secondSource = Source(42 to 1000)

  // step 1
  val sourceGraph = Source.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._
      // step 2: declaring components
      val concat = builder.add(Concat[Int](2))

      // strep 3: tying them together
      firstSource ~> concat
      secondSource ~> concat


      // step 4
      SourceShape(concat.out)
    }
  )
  // sourceGraph.to(Sink.foreach(println)).run()

  /**
   * Complex sink
   */
  val sink1 = Sink.foreach[Int](x => println(s"sink1: ${x}"))
  val sink2 = Sink.foreach[Int](x => println(s"sink2: ${x}"))

  val sinkGraph = Sink.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      // step 2 - add a broadcast
      val broadcast = builder.add(Broadcast[Int](2))

      // step 3 - tie components together
      broadcast ~> sink1
      broadcast ~> sink2

      SinkShape(broadcast.in)
    }
  )
  // firstSource.to(sinkGraph).run()

  /**
   * write your own flow that is composed of two other flows
   * - one that adds 1 to a number
   * - one that does number * 10
   */
  val incrementer = Flow[Int].map(_ + 1)
  val multiplier = Flow[Int].map(_ * 10)

  val flowGraph = Flow.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._
      // everything operates on shapes

      // step 2 - define auxiliary shapes
      val incrementerShape = builder.add(incrementer)
      val multiplierShape = builder.add(multiplier)

      // step 3 - connect the shapes
      incrementerShape ~> multiplierShape

      FlowShape(incrementerShape.in, multiplierShape.out)
    }
  ) // component
  // firstSource.via(flowGraph).to(Sink.foreach(println)).run()

  /**
   * Exercise: flow from a sink and a source
   */
  def fromSinkAndSource[A, B](sink: Sink[A, _], source: Source[B, _]): Flow[A, B, _] = {
    Flow.fromGraph(
      GraphDSL.create() { implicit builder =>
        // step 2: declare shapes
        val sourceShape = builder.add(source)
        val sinkShape = builder.add(sink)

        // step 3 - tie

        // step 4 - return the shape
        FlowShape(sinkShape.in, sourceShape.out)
      }
    )
  }

  val f = Flow.fromSinkAndSource(Sink.foreach[String](println), Source(1 to 10))
}
