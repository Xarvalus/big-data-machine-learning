package xarvalus.reactive.stock.common

import com.lightbend.lagom.scaladsl.api.transport.{Forbidden, RequestHeader}
import com.lightbend.lagom.scaladsl.server.ServerServiceCall
import pdi.jwt.{Jwt, JwtAlgorithm, JwtJson}
import play.api.libs.json._

import scala.util.{Failure, Success}

object AuthenticationServiceComposition {
  // TODO: resolve config file
  // val secret: String = ConfigFactory.load().getString("jwt.secret")
  val secret: String = "4jkdgf4JHF38/385kjghs#$#(-.gdgk4498Q(gjgh3/3jhgdf,.,24#%8953+'8GJA3gsjjd3598#%(/$.,-Kjg#%$#64jhgskghja"
  val algorithm: JwtAlgorithm.HS512.type = JwtAlgorithm.HS512

  def authenticated[Request, Response](serviceCall: (TokenContent, String) =>
    ServerServiceCall[Request, Response]): ServerServiceCall[Request, Response] =
      ServerServiceCall.compose { requestHeader =>
        val tokenContent = extractTokenContent(requestHeader)
        val authToken = extractTokenHeader(requestHeader)

        tokenContent match {
          case Some(token) => serviceCall(token, authToken.getOrElse(""))
          case _ => throw Forbidden("Authorization token is invalid")
        }
      }

  private def extractTokenHeader(requestHeader: RequestHeader) = {
    requestHeader.getHeader("Authorization")
      .map(header => sanitizeToken(header))
  }

  private def extractTokenContent[Response, Request](requestHeader: RequestHeader) = {
    extractTokenHeader(requestHeader)
      .filter(rawToken => validateToken(rawToken))
      .map(rawToken => decodeToken(rawToken))
  }

  private def sanitizeToken(header: String) = header.replaceFirst("Bearer ", "")
  private def validateToken(token: String) = Jwt.isValid(token, secret, Seq(algorithm))

  private def decodeToken(token: String) = {
    val jsonTokenContent = JwtJson.decode(token, secret, Seq(algorithm))

    jsonTokenContent match {
      case Success(json) => Json.parse(json.content).as[TokenContent]
      case Failure(_) => throw Forbidden(s"Unable to decode token")
    }
  }
}
