package example

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer}
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.pattern.StatusReply
import akka.util.Timeout
import typings.node.bufferMod.global.Buffer
import typings.puppeteer.mod.Browser

import java.util.UUID.randomUUID
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{DurationDouble, FiniteDuration}
import scala.reflect.classTag
import scala.scalajs.js.Thenable.Implicits.*
import scala.util.{Failure, Success}

object PuppeteerBrowser {

  sealed trait Command

  case class CapturePageContent(id: String, url: String, replyTo: ActorRef[StatusReply[Buffer]]) extends Command

  case class CommandFailed(throwable: Throwable, replyTo: ActorRef[StatusReply[String]]) extends Command

  class Actor(context: ActorContext[Command], buffer: StashBuffer[Command])(implicit ex: ExecutionContext) {
    implicit val timeout: Timeout = 30.seconds

    private def initializing(pages: Map[String, ActorRef[PuppeteerPage.Command]] = Map.empty): Behavior[Command] = Behaviors.receiveMessage {
      case BrowserCreated(browser) =>
        context.log.info("Browser Created")
        idle(browser, pages)
      case InitializationFailed(throwable) =>
        context.log.error("Failed to initialize Browser")
        throw throwable
      case other =>
        buffer.stash(other)
        Behaviors.same
    }

    private def idle(browser: Browser, pages: Map[String, ActorRef[PuppeteerPage.Command]]): Behavior[Command] =
      buffer.unstashAll(active(browser, pages))

    private def active(browser: Browser, pages: Map[String, ActorRef[PuppeteerPage.Command]]): Behavior[Command] =
      Behaviors.receiveMessagePartial {
        case command@CapturePageContent(id, url, replyTo) =>
          pages.get(id).fold(create(browser, pages, id, command)) {
            page =>
              context.log.info(s"capturing page content from url for $id")
              page ! PuppeteerPage.CapturePageContent(url, replyTo)
              Behaviors.same
          }
        case PageTerminated(id) =>
          context.log.info(s"page for $id terminated, removing page from $pages")
          active(browser, pages - id)
      }

    private def create(browser: Browser,
                       pages: Map[String, ActorRef[PuppeteerPage.Command]],
                       id: String, request: Command): Behavior[Command] = {
      context.self ! request
      active(browser, pages + (id -> newPage(browser, id)))
    }

    private def newPage(browser: Browser, id: String): ActorRef[PuppeteerPage.Command] = {
      context.log.info(s"creating Page for $id")
      val page = context.spawnAnonymous(PuppeteerPage.Actor(browser))
      context.watchWith(page, PageTerminated(id))
      page
    }
  }

  private case class BrowserCreated(browser: Browser) extends Command

  private case class PageTerminated(id: String) extends Command

  private case class InitializationFailed(throwable: Throwable) extends Command

  object Actor {
    def apply()(implicit ec: ExecutionContext): Behavior[Command] =
      Behaviors.supervise[Command](
        Behaviors.withStash(100) { buffer =>
          Behaviors.setup {
            context =>
              context.pipeToSelf(typings.puppeteer.mod.launch()) {
                case Success(browser) => BrowserCreated(browser)
                case Failure(exception) =>
                  InitializationFailed(exception)
              }
              new Actor(context, buffer).initializing()
          }
        }).onFailure(SupervisorStrategy.stop)(classTag[Throwable])
  }
}
