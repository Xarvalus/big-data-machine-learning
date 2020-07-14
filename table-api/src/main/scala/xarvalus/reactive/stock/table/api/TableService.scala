package xarvalus.reactive.stock.table.api

import java.util.UUID

import com.lightbend.lagom.scaladsl.api.broker.Topic
import com.lightbend.lagom.scaladsl.api.broker.kafka.{KafkaProperties, PartitionKeyStrategy}
import com.lightbend.lagom.scaladsl.api.{Descriptor, Service}
import play.api.libs.json.{Format, Json}

object TableService  {
  val TOPIC_NAME = "resolved_transactions"
}

trait TableService extends Service {
  def resolvedTransactionsTopic(): Topic[ResolvedTransaction]

  override final def descriptor: Descriptor = {
    import Service._
    // @formatter:off
    named("table-service")
      .withTopics(
        topic(TableService.TOPIC_NAME, resolvedTransactionsTopic _)
          .addProperty(
            KafkaProperties.partitionKeyStrategy,
            PartitionKeyStrategy[ResolvedTransaction](_.transactionId)
          )
      )
      .withAutoAcl(true)
    // @formatter:on
  }
}

case class ResolvedTransaction(
  transactionId: String,
  asset: String,
  price: BigDecimal,
  quantity: BigDecimal,
  timestamp: String,
  buyer: UUID,
  seller: UUID
)

object ResolvedTransaction {
  implicit val format: Format[ResolvedTransaction] = Json.format
}
