package part3_graphs

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ClosedShape, OverflowStrategy}
import akka.stream.scaladsl.{Flow, GraphDSL, Merge, MergePreferred, RunnableGraph, Source}

object GraphCycles extends App {
  implicit val system: ActorSystem = ActorSystem("GraphCycles")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  val accelerator = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val sourceShape = builder.add(Source(1 to 100))
    val mergeShape = builder.add(Merge[Int](2))
    val incrementerShape = builder.add(Flow[Int].map { x =>
      println(s"accelerating $x")
      x + 1
    })

    sourceShape ~>  mergeShape ~> incrementerShape
                    mergeShape <~ incrementerShape

    ClosedShape
  }

  //RunnableGraph.fromGraph(accelerator).run()
  // graph cycle deadlock!

  /**
   * Solution 1: MergePreferred
   */
  val actualAccelerator = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val sourceShape = builder.add(Source(1 to 100))
    val mergeShape = builder.add(MergePreferred[Int](1))
    val incrementerShape = builder.add(Flow[Int].map { x =>
      println(s"accelerating $x")
      x + 1
    })

    sourceShape ~>  mergeShape ~> incrementerShape
    mergeShape.preferred <~ incrementerShape

    ClosedShape
  }
  // RunnableGraph.fromGraph(actualAccelerator).run()

  /**
   * Solution 2: buffers
   */
  val bufferRepeater = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val sourceShape = builder.add(Source(1 to 100))
    val mergeShape = builder.add(Merge[Int](2))
    val repeaterShape = builder.add(Flow[Int].buffer(10, OverflowStrategy.dropHead).map { x =>
      println(s"accelerating $x")
      Thread.sleep(100)
      x
    })

    sourceShape ~>  mergeShape ~> repeaterShape
    mergeShape <~ repeaterShape

    ClosedShape
  }

  // RunnableGraph.fromGraph(bufferRepeater).run()

  /**
   * cycles risk deadlocking
   * - add bounds to the number of elements in the cycle
   */

}
