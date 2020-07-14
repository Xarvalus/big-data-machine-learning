package xarvalus.reactive.stock.trader.impl.util

import java.time.Clock

import com.typesafe.config.ConfigFactory
import pdi.jwt.{JwtAlgorithm, JwtClaim, JwtJson}
import play.api.libs.json.{Format, Json}
import xarvalus.reactive.stock.common.TokenContent

object JwtTokenUtil {
  val secret: String = ConfigFactory.load().getString("jwt.secret")
  val authExpiration: Int = ConfigFactory.load().getInt("jwt.token.auth.expirationInSeconds")
  val algorithm: JwtAlgorithm.HS512.type = JwtAlgorithm.HS512

  implicit val clock: Clock = Clock.systemUTC

  def generateTokens(content: TokenContent)(implicit format: Format[TokenContent]): Token = {
    val authClaim = JwtClaim(Json.toJson(content).toString())
      .expiresIn(authExpiration)
      .issuedNow

    val authToken = JwtJson.encode(authClaim, secret, algorithm)

    Token(
      authToken = authToken,
      refreshToken = None
    )
  }

  def generateAuthTokenOnly(content: TokenContent)(implicit format: Format[TokenContent]): Token = {
    val authClaim = JwtClaim(Json.toJson(content.copy()).toString())
      .expiresIn(authExpiration)
      .issuedNow

    val authToken = JwtJson.encode(authClaim, secret, algorithm)

    Token(
      authToken = authToken,
      None
    )
  }
}

case class Token(authToken: String, refreshToken: Option[String])

object Token {
  implicit val format: Format[Token] = Json.format
}
