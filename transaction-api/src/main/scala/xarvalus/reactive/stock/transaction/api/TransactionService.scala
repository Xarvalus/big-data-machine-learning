package xarvalus.reactive.stock.transaction.api

import java.util.UUID

import akka.Done
import com.lightbend.lagom.scaladsl.api.broker.Topic
import com.lightbend.lagom.scaladsl.api.broker.kafka.{KafkaProperties, PartitionKeyStrategy}
import com.lightbend.lagom.scaladsl.api.{Descriptor, Service, ServiceCall}
import play.api.libs.json.{Format, JsResult, JsSuccess, JsValue, Json, Reads, Writes}
import com.wix.accord.dsl._
import com.wix.accord.transform.ValidationTransform

object TransactionService {
  val TOPIC_NAME = "orders"
}

trait TransactionService extends Service {
  def order(): ServiceCall[Order, Done]

  def ordersTopic(): Topic[PlacedOrder]

  override final def descriptor: Descriptor = {
    import Service._
    // @formatter:off
    named("transaction-service")
      .withCalls(
        pathCall("/api/transaction/order", order _)
      )
      .withTopics(
        topic(TransactionService.TOPIC_NAME, ordersTopic _)
          .addProperty(
            KafkaProperties.partitionKeyStrategy,
            PartitionKeyStrategy[PlacedOrder](_.orderId)
          )
      )
      .withAutoAcl(true)
    // @formatter:on
  }
}

sealed trait OrderType
sealed trait Buy extends OrderType
sealed trait Sell extends OrderType

case object Buy extends Buy {
  val BUY = "BUY"

  implicit val format: Format[Buy] =
    Format(Reads(_ => JsSuccess(Buy)), Writes(_ => Json.obj("type" -> BUY)))
}

case object Sell extends Sell {
  val SELL = "SELL"

  implicit val format: Format[Sell] =
    Format(Reads(_ => JsSuccess(Sell)), Writes(_ => Json.obj("type" -> SELL)))
}

case object OrderType {
  implicit val format: Format[OrderType] = new Format[OrderType] {
    override def writes(o: OrderType): JsValue = o match {
      case buy: Buy   => Json.toJson(buy)
      case sell: Sell => Json.toJson(sell)
    }

    override def reads(json: JsValue): JsResult[OrderType] = {
      // TODO: erroneously returns type with quotes
      val jsonType = (json \ "type").get.toString().tail.init

      if (jsonType == Buy.BUY) {
        Json.fromJson[Buy](json)
      } else if (jsonType == Sell.SELL) {
        Json.fromJson[Sell](json)
      } else {
        throw new Exception("Cannot read JSON of OrderType")
      }
    }
  }
}

case class Order(
  asset: String,
  price: BigDecimal,
  quantity: BigDecimal,
  orderType: OrderType
)

object Order {
  implicit val format: Format[Order] = Json.format

  implicit val orderValidator: ValidationTransform.TransformedValidator[Order] = validator[Order] { order =>
    order.asset is notEmpty
    order.price should be >= BigDecimal(0)
    order.quantity should be >= BigDecimal(0)
    order.orderType is notNull
  }
}

case class PlacedOrder(
  orderId: String,
  asset: String,
  price: BigDecimal,
  quantity: BigDecimal,
  orderType: OrderType,
  user: UUID,
  timestamp: String
)

object PlacedOrder {
  implicit val format: Format[PlacedOrder] = Json.format
}
