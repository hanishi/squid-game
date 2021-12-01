package example

import akka.Done
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.pattern.StatusReply
import org.scalablytyped.runtime.StringDictionary
import typings.cheerio.cheerioMod.Cheerio
import typings.cheerio.mod.Node
import typings.devtoolsProtocol.mod.Protocol.Network.ResourceType
import typings.node.bufferMod.global.{Buffer, BufferEncoding}
import typings.node.nodeStrings.undefined
import typings.node.nodeUrlMod.URL
import typings.puppeteer.anon.WaitForOptionsrefererstriTimeout
import typings.puppeteer.mod.*
import typings.puppeteer.mod.global.{Document, Element, NodeListOf}
import typings.puppeteer.puppeteerStrings.{request, response}
import wvlet.airframe.log
import typings.cheerio.{loadMod, mod as cheerio}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{DurationDouble, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.classTag
import scala.scalajs.js
import scala.scalajs.js.Object.keys
import scala.scalajs.js.Promise
import scala.scalajs.js.Thenable.Implicits.*
import scala.util.chaining.scalaUtilChainingOps
import scala.util.{Failure, Success, Try}

object PuppeteerPage {

  trait Command

  case class CapturePageContent(url: String, replyTo: ActorRef[StatusReply[Buffer]]) extends Command

  case class PageContentCaptured(buffer: Buffer) extends Command

  case class CommandFailed(throwable: Throwable) extends Command

  private case class Created(page: typings.puppeteer.mod.Page) extends Command

  private case class InitializationFailed(throwable: Throwable) extends Command

  private class Actor(context: ActorContext[Command],
                      buffer: StashBuffer[Command],
                      timers: TimerScheduler[Command]) {
    private val resources = scala.collection.mutable.Set[String]()
    val handler: js.Function1[HTTPResponse, Unit] = res => {
      res.headers().get("content-type").fold(Future.successful(resources)) { value =>
        value match {
          case "image/jpeg" | "image/png" | "image/gif" =>
            res.buffer().map(buffer => resources += s"data:$value;charset=utf-8;base64,"
              .concat(buffer.toString(BufferEncoding.base64)))
          case other => resources
        }
      }
    }
    def initialize(): Behavior[Command] =
      Behaviors.receiveMessage {
        case Created(page) =>
          page.on_response(response, handler)
          idle(page)
        case InitializationFailed(throwable) =>
          context.log.error("Initialization failed")
          Behaviors.stopped
        case other =>
          buffer.stash(other)
          Behaviors.same
      }

    def idle(page: Page): Behavior[Command] = {
      if (timers.isTimerActive(TimeoutKey)) timers.cancel(TimeoutKey)
      timers.startSingleTimer(TimeoutKey, ClosePage, 5.minutes)
      buffer.unstashAll(active(page))
    }

    def terminating: Behavior[Command] =
      Behaviors.receiveMessagePartial {
        case CapturePageContent(url, replyTo) =>
          replyTo ! StatusReply.Error("Actor is terminating")
          Behaviors.same
        case PageClosed =>
          context.log.info("Page closed")
          Behaviors.stopped
        case CommandFailed(throwable) =>
          context.log.error(s"failed to close Page ${throwable.getCause.getLocalizedMessage}", throwable.getCause)
          Behaviors.same
      }

    def capturingPageContent(page: Page, replyTo: ActorRef[StatusReply[Buffer]]): Behavior[Command] =
      Behaviors.receiveMessage {
        case PageContentCaptured(value) =>
          replyTo ! StatusReply.Success(value)
          idle(page)
        case CommandFailed(throwable) =>
          replyTo ! StatusReply.Error(throwable)
          idle(page)
        case other =>
          buffer.stash(other)
          Behaviors.same
      }

    def active(page: Page): Behavior[Command] = Behaviors.receiveMessage {
      case CapturePageContent(url, replyTo) =>
        resources.clear()
        context.pipeToSelf(capturePageContent(page, url)) {
          case Success(value) => PageContentCaptured(value)
          case Failure(throwable) => CommandFailed(throwable)
        }
        capturingPageContent(page, replyTo)
      case ClosePage =>
        context.pipeToSelf(page.close()) {
          case Success(_) => PageClosed
          case Failure(throwable) => CommandFailed(throwable)
        }
        terminating
    }

    def capturePageContent(page: Page, url: String): Future[Buffer] = {
      val option = ScreenshotOptions()
      option.fullPage = true
      option.captureBeyondViewport = true
      for {_ <- page.setViewport(Viewport(1024, 768))
           _ <- page.goto(url, WaitForOptionsrefererstriTimeout().setWaitUntil(PuppeteerLifeCycleEvent.domcontentloaded))
           pageContent <- page.content().map(content => render(content, url))
           } yield Buffer.from(pageContent)
    }

    def render(content: String, url: String): String = {
      val $ = cheerio.load(s"<table id=\"images\"><thead>$url</thead><tbody></tbody></table>")
      resources.foldLeft($("#images > tbody")) {(images, value) => images.append(js.Array(s"<tr><td><image src=\"$value\"></tr>"))}
      $.html()
    }
  }

  case object ClosePage extends Command

  object Actor {
    def apply(browser: Browser)(implicit ec: ExecutionContext): Behavior[Command] =
      Behaviors.supervise[Command](
        Behaviors.withStash(100) { buffer =>
          Behaviors.setup {
            context =>
              Behaviors.withTimers { timers =>
                context.pipeToSelf(browser.newPage()) {
                  case Success(page) => Created(page)
                  case Failure(exception) =>
                    InitializationFailed(exception)
                }
                new Actor(context, buffer, timers).initialize()
              }
          }
        }).onFailure(SupervisorStrategy.stop)(classTag[Throwable])
  }

  private case object PageClosed extends Command

  private case object TimeoutKey

}
