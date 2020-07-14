package xarvalus.reactive.stock.common

import java.util.UUID

import play.api.libs.json.{Format, Json}

case class TokenContent(
  userId: UUID,
  username: String
)

object TokenContent {
  implicit val format: Format[TokenContent] = Json.format
}
