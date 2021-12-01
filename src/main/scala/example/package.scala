import typings.express.mod as express
import typings.express.mod.{RequestHandler, request_=}
import typings.expressServeStaticCore.mod.*
import typings.node.bufferMod.global.Buffer
import typings.node.processMod as process
import wvlet.airframe.log

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import scala.scalajs.js.Object.{entries, keys}
import scala.scalajs.js.Thenable.Implicits.*
import scala.scalajs.js.UndefOr

package object example {
  trait ReqBody extends js.Object {
    val payload: js.UndefOr[js.Any]
  }

  object asyncHandler {
    type Req = Request[ParamsDictionary, Buffer, ReqBody, Query, typings.std.Record[String, js.Any]]
    type Res = Response[Buffer, typings.std.Record[String, js.Any], Double]
    type Handler = RequestHandler[ParamsDictionary, Buffer, ReqBody, Query, typings.std.Record[String, js.Any]]

    def apply(fn: js.Function3[Req, Res, NextFunction, Future[Any]]): Handler =
      typings.expressAsyncHandler.mod(
        (param) => handleRequest(param.asInstanceOf[Req], fn))

    private def handleRequest(request: Req,
                              fn: js.Function3[Req, Res, NextFunction, Future[Any]]): UndefOr[js.Promise[Unit]] =
      for {res <- request.res
           next <- request.next} yield fn(request, res, next).map(_ => ()).toJSPromise

  }
}
