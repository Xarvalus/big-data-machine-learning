package xarvalus.reactive.stock.asset.api

import java.time.LocalDateTime

import akka.NotUsed
import com.lightbend.lagom.scaladsl.api.transport.Method
import com.lightbend.lagom.scaladsl.api.{Descriptor, Service, ServiceCall}
import play.api.libs.json.{Format, Json}

trait AssetService extends Service {
  def assets(): ServiceCall[NotUsed, AssetsDone]
  def asset(asset: String): ServiceCall[NotUsed, AssetDone]

  override final def descriptor: Descriptor = {
    import Service._
    // @formatter:off
    named("asset-service")
      .withCalls(
        restCall(Method.GET, "/api/asset/assets", assets _),
        restCall(Method.GET, "/api/asset/:asset", asset _)
      )
      .withAutoAcl(true)
    // @formatter:on
  }
}

case class AssetsDone(assets: Seq[String])

object AssetsDone {
  implicit val format: Format[AssetsDone] = Json.format
}

case class AssetDone(price: BigDecimal, lastTimeUpdatedAt: LocalDateTime)

object AssetDone {
  implicit val format: Format[AssetDone] = Json.format
}
