package xarvalus.reactive.stock.transaction.impl

import java.time.LocalDateTime
import java.util.UUID

import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl._
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}
import com.lightbend.lagom.scaladsl.persistence.{AggregateEvent, AggregateEventTag, AkkaTaggerAdapter}
import com.lightbend.lagom.scaladsl.playjson.{JsonSerializer, JsonSerializerRegistry}
import play.api.libs.json.{Format, Json, _}
import xarvalus.reactive.stock.transaction.api.OrderType

import scala.collection.immutable.Seq

object TransactionBehavior {
  def create(entityContext: EntityContext[TransactionCommand]): Behavior[TransactionCommand] = {
    val persistenceId: PersistenceId =
      PersistenceId(entityContext.entityTypeKey.name, entityContext.entityId)

    create(persistenceId, entityContext.entityId)
      .withTagger(
        AkkaTaggerAdapter.fromLagom(entityContext, TransactionEvent.Tag)
      )
  }

  private[impl] def create(persistenceId: PersistenceId, entityId: String) = EventSourcedBehavior
      .withEnforcedReplies[TransactionCommand, TransactionEvent, TransactionState](
        persistenceId = persistenceId,
        emptyState = TransactionState.initial,
        commandHandler = (state, cmd) => state.applyCommand(cmd, entityId),
        eventHandler = (state, evt) => state.applyEvent(evt)
      )
}


case class Order(
  asset: String,
  price: BigDecimal,
  quantity: BigDecimal,
  orderType: OrderType,
  user: UUID
)

object Order {
  implicit val format: Format[Order] = Json.format
}

case class TransactionState(order: Option[Order]) {
  def applyCommand(cmd: TransactionCommand, entityId: String): ReplyEffect[TransactionEvent, TransactionState] =
    cmd match {
      case PlaceOrder(asset, price, quantity, orderType, user, replyTo) =>
        Effect
          .persist(
            OrderPlaced(
              orderId = UUID.fromString(entityId),
              asset,
              price,
              quantity,
              orderType,
              user,
              timestamp = LocalDateTime.now().toString
            )
          )
          .thenReply(replyTo) { _ =>
            Accepted
          }
    }

  def applyEvent(evt: TransactionEvent): TransactionState =
    evt match {
      case OrderPlaced(_, asset, price, quantity, orderType, user, _) =>
        TransactionState(
          Some(Order(
            asset,
            price,
            quantity,
            orderType,
            user
          ))
        )
    }
}

object TransactionState {
  def initial: TransactionState = TransactionState(None)

  val typeKey: EntityTypeKey[TransactionCommand] =
    EntityTypeKey[TransactionCommand]("TransactionAggregate")

  implicit val format: Format[TransactionState] = Json.format
}


sealed trait TransactionEvent extends AggregateEvent[TransactionEvent] {
  def aggregateTag: AggregateEventTag[TransactionEvent] = TransactionEvent.Tag
}

object TransactionEvent {
  val Tag: AggregateEventTag[TransactionEvent] = AggregateEventTag[TransactionEvent]
}

case class OrderPlaced(
  orderId: UUID,
  asset: String,
  price: BigDecimal,
  quantity: BigDecimal,
  orderType: OrderType,
  user: UUID,
  timestamp: String
) extends TransactionEvent

object OrderPlaced {
  implicit val format: Format[OrderPlaced] = Json.format
}


trait TransactionCommandSerializable

sealed trait TransactionCommand
    extends TransactionCommandSerializable

case class PlaceOrder(
  asset: String,
  price: BigDecimal,
  quantity: BigDecimal,
  orderType: OrderType,
  user: UUID,
  replyTo: ActorRef[Confirmation]
) extends TransactionCommand


sealed trait Confirmation

case object Confirmation {
  implicit val format: Format[Confirmation] = new Format[Confirmation] {
    override def reads(json: JsValue): JsResult[Confirmation] = {
      if ((json \ "reason").isDefined)
        Json.fromJson[Rejected](json)
      else
        Json.fromJson[Accepted](json)
    }

    override def writes(o: Confirmation): JsValue = {
      o match {
        case acc: Accepted => Json.toJson(acc)
        case rej: Rejected => Json.toJson(rej)
      }
    }
  }
}

sealed trait Accepted extends Confirmation

case object Accepted extends Accepted {
  implicit val format: Format[Accepted] =
    Format(Reads(_ => JsSuccess(Accepted)), Writes(_ => Json.obj()))
}

case class Rejected(reason: String) extends Confirmation

object Rejected {
  implicit val format: Format[Rejected] = Json.format
}


object TransactionSerializerRegistry extends JsonSerializerRegistry {
  override def serializers: Seq[JsonSerializer[_]] = Seq(
    JsonSerializer[Order],
    JsonSerializer[TransactionState],
    JsonSerializer[OrderPlaced],
    JsonSerializer[Confirmation],
    JsonSerializer[Accepted],
    JsonSerializer[Rejected]
  )
}
