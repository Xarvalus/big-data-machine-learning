package xarvalus.reactive.stock.trader.api

import akka.{Done, NotUsed}
import com.lightbend.lagom.scaladsl.api.transport.Method
import com.lightbend.lagom.scaladsl.api.{Descriptor, Service, ServiceCall}
import play.api.libs.json.{Format, Json}
import com.wix.accord.dsl._
import com.wix.accord.transform.ValidationTransform

trait TraderService extends Service {
  def register(): ServiceCall[Register, Done]
  def login(): ServiceCall[Login, LoginDone]
  def balance(): ServiceCall[NotUsed, BalanceDone]
  def assets(): ServiceCall[NotUsed, AssetsDone]
  def asset(asset: String): ServiceCall[NotUsed, AssetDone]
  def addAsset(asset: String): ServiceCall[AddAsset, Done]

  override final def descriptor: Descriptor = {
    import Service._
    // @formatter:off
    named("trader-service")
      .withCalls(
        restCall(Method.POST, "/api/trader/register", register _),
        restCall(Method.POST, "/api/trader/login", login _),
        restCall(Method.GET, "/api/trader/balance", balance _),
        restCall(Method.GET, "/api/trader/assets", assets _),
        restCall(Method.GET, "/api/trader/asset/:asset", asset _),
        restCall(Method.PUT, "/api/trader/asset/:asset", addAsset _)
      )
      .withAutoAcl(true)
    // @formatter:on
  }
}


case class Register(
  username: String,
  password: String,
  email: String,
  firstName: String,
  lastName: String
)

object Register {
  implicit val format: Format[Register] = Json.format

  val emailRegex: String =
    """^[a-zA-Z0-9\.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}""" +
      """[a-zA-Z0-9])?(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$"""

  implicit val registrationValidator: ValidationTransform.TransformedValidator[Register] =
    validator[Register] { user =>
      user.username is notEmpty
      user.password is notEmpty
      user.email should matchRegexFully(emailRegex)
      user.firstName is notEmpty
      user.lastName is notEmpty
    }
}


case class Login(
  username: String,
  password: String
)

object Login {
  implicit val format: Format[Login] = Json.format

  implicit val loginValidator: ValidationTransform.TransformedValidator[Login] =
    validator[Login] { user =>
      user.username is notEmpty
      user.password is notEmpty
    }
}

case class LoginDone(authToken: String)

object LoginDone {
  implicit val format: Format[LoginDone] = Json.format
}


case class BalanceDone(value: BigDecimal)

object BalanceDone {
  implicit val format: Format[BalanceDone] = Json.format
}


case class AssetsDone(assets: Map[String, BigDecimal])

object AssetsDone {
  implicit val format: Format[AssetsDone] = Json.format
}


case class AssetDone(value: BigDecimal)

object AssetDone {
  implicit val format: Format[AssetDone] = Json.format
}


case class AddAsset(value: BigDecimal)

object AddAsset {
  implicit val format: Format[AddAsset] = Json.format

  implicit val assetValidator: ValidationTransform.TransformedValidator[AddAsset] =
    validator[AddAsset] { asset =>
      asset.value should be >= BigDecimal(0)
    }
}
