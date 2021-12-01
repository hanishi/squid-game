package example

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.AskPattern.{Askable, schedulerFromActorSystem}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import example.asyncHandler.{Req, Res}
import org.scalablytyped.runtime.StringDictionary
import typings.express.mod as express
import typings.node.bufferMod.global.Buffer
import wvlet.airframe.log

import java.net.URLDecoder
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{DurationDouble, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js
import scala.scalajs.js.{isUndefined, undefined}
import scala.util.{Failure, Success}

object App {
  implicit val system: ActorSystem[PuppeteerBrowser.Command] = ActorSystem(PuppeteerBrowser.Actor(), "actorSystem")
  implicit val timeout: Timeout = 30.seconds
  val Handler = asyncHandler((req, res, next) => handleRequest(req, res))
  val app = express()

  def handleRequest(req: Req, res: Res): Future[Any] = (for {
    id <- req.params.get("id")
    url <- req.query.asInstanceOf[StringDictionary[String]].get("url")
  } yield system.askWithStatus[Buffer](PuppeteerBrowser.CapturePageContent(id, url, _)) map { buffer =>
    res.set("Content-Type", "text/html")
    res.send(buffer)
  }).getOrElse(Future.successful {
    res.set("Content-Type", "text/html")
    res.send(Buffer.from("requires both id and url"))
  })
  app.get("/:id", Handler)

  log.initNoColor

  def main(args: Array[String]): Unit = app.listen(3000, () => {
    println("Server started!")
  })
}
