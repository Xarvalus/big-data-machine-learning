package xarvalus.reactive.stock.asset.impl

import java.time.LocalDateTime

import akka.Done
import com.lightbend.lagom.scaladsl.persistence.{
  AggregateEvent, AggregateEventShards, AggregateEventTag, PersistentEntity}
import com.lightbend.lagom.scaladsl.playjson.{JsonSerializer, JsonSerializerRegistry}
import play.api.libs.json.{Format, Json}
import xarvalus.reactive.stock.asset.api.AssetDone

import scala.collection.immutable.Seq


class AssetEntity extends PersistentEntity {
  override type Command = AssetCommand
  override type Event = AssetEvent
  override type State = AssetState

  /* Price of $10.000 chosen arbitrary */
  override def initialState = AssetState(price = 10000, updatedAt = LocalDateTime.now())

  override def behavior: Behavior = {
    Actions()
      .onCommand[UpdatePrice, Done] {
      case (UpdatePrice(asset, price), ctx, _) =>
        ctx.thenPersist(
          PriceUpdated(asset, price)
        ) { _ =>
          ctx.reply(Done)
        }
    }
      .onReadOnlyCommand[AssetPrice, AssetDone] {
      case (AssetPrice(), ctx, state) =>
        ctx.reply(
          AssetDone(
            price = state.price,
            lastTimeUpdatedAt = state.updatedAt
          )
        )
    }
      .onEvent {
        case (PriceUpdated(_, price), _) =>
          AssetState(price, updatedAt = LocalDateTime.now())
      }
  }
}


sealed trait AssetCommand

case class UpdatePrice(asset: String, price: BigDecimal)
  extends PersistentEntity.ReplyType[Done] with AssetCommand

object UpdatePrice {
  implicit val format: Format[UpdatePrice] = Json.format
}

case class AssetPrice() extends PersistentEntity.ReplyType[AssetDone] with AssetCommand


sealed trait AssetEvent extends AggregateEvent[AssetEvent] {
  override def aggregateTag(): AggregateEventShards[AssetEvent] = AssetEvent.Tag
}

object AssetEvent {
  val NumShards = 5
  val Tag = AggregateEventTag.sharded[AssetEvent]("AssetEvent", NumShards)
}

case class PriceUpdated(
  asset: String,
  price: BigDecimal
) extends AssetEvent

object PriceUpdated {
  implicit val format: Format[PriceUpdated] = Json.format
}


case class AssetState(
  price: BigDecimal,
  updatedAt: LocalDateTime
)

object AssetState {
  implicit val format: Format[AssetState] = Json.format
}


object AssetSerializerRegistry extends JsonSerializerRegistry {
  override def serializers: Seq[JsonSerializer[_]] = Seq(
    JsonSerializer[UpdatePrice],
    JsonSerializer[PriceUpdated],
    JsonSerializer[AssetState],
  )
}
