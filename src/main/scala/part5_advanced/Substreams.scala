package part5_advanced

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, Sink, Source}

import scala.util.{Failure, Success}

object Substreams extends App {

  implicit val system: ActorSystem = ActorSystem("Substreams")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  import system.dispatcher

  // 1 - grouping a stream by a certain function
  val wordsSource = Source(List("akka", "is", "amazing", "learning", "substreams"))
  val groups =  wordsSource.groupBy(30, word => if(word.isEmpty) '\0' else word.toLowerCase().charAt(0))

  groups.to(Sink.fold(0)((count, word) => {
    val newCount = count + 1
    println(s"received $word, count is $newCount")
    newCount
  }))
    //.run()

  // 2 - merge substreams back
  val textSource = Source(List(
    "i love akka streams",
    "this is amazing",
    "learning from Rock the JVM"
  ))
  val totalCharCountFuture = textSource.groupBy(2, string => string.length % 2)
    .map(_.length) // do your expensive computation
    .mergeSubstreamsWithParallelism(2)
    .toMat(Sink.reduce[Int](_ + _))(Keep.right)
    .run()

  totalCharCountFuture.onComplete {
    case Success(value) => println(s"total char: $value")
    case Failure(exception) => println(s"failed $exception")
  }

  // 3 - splitting a stream into substreams, when a condition is met
  val text = "i love akka streams\n" +
    "this is amazing\n" +
    "learning from Rock the JVM\n"

  val anotherCharCountFuture = Source(text.toList)
    .splitWhen(c => c  == '\n')
    .filter(_ != '\n')
    .map(_ => 1)
    .mergeSubstreams
    .toMat(Sink.reduce[Int](_ + _))(Keep.right)
    .run()

  anotherCharCountFuture.onComplete {
    case Success(value) => println(s"total char alternative: $value")
    case Failure(exception) => println(s"failed $exception")
  }

  // 4 - flattening
  val simpleSource = Source(1 to 5)
  simpleSource.flatMapConcat(x => Source(x to (3 * x))).runWith(Sink.foreach(println))
  simpleSource.flatMapMerge(2, x => Source(x to (3 * x))).runWith(Sink.foreach(println))
}
