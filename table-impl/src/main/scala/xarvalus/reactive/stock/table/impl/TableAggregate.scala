package xarvalus.reactive.stock.table.impl

import java.time.LocalDateTime
import java.util.UUID

import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl._
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}
import com.lightbend.lagom.scaladsl.persistence.{AggregateEvent, AggregateEventTag, AkkaTaggerAdapter}
import com.lightbend.lagom.scaladsl.playjson.{JsonSerializer, JsonSerializerRegistry}
import play.api.libs.json.{Format, Json, _}
import xarvalus.reactive.stock.transaction.api.{Buy, OrderType, Sell}

import scala.collection.immutable.Seq

object TableBehavior {
  def create(entityContext: EntityContext[TableCommand]): Behavior[TableCommand] = {
    val persistenceId: PersistenceId =
      PersistenceId(entityContext.entityTypeKey.name, entityContext.entityId)

    create(persistenceId, entityContext.entityId)
      .withTagger(
        AkkaTaggerAdapter.fromLagom(entityContext, TableEvent.Tag)
      )
  }

  private[impl] def create(persistenceId: PersistenceId, entityId: String) = EventSourcedBehavior
      .withEnforcedReplies[TableCommand, TableEvent, TableState](
        persistenceId = persistenceId,
        emptyState = TableState.initial,
        commandHandler = (state, cmd) => state.applyCommand(cmd, state, entityId),
        eventHandler = (state, evt) => state.applyEvent(evt, state)
      )
}


case class Transaction(
  orderId: UUID,
  price: BigDecimal,
  quantity: BigDecimal,
  user: UUID,
  timestamp: String
) extends Ordered[Transaction] {
  override def compare(that: Transaction): Int = {
    this.price.compareTo(that.price)
  }
}

object Transaction {
  implicit val format: Format[Transaction] = Json.format
}

case class TransactionTable(
  buys: Seq[Transaction],
  sells: Seq[Transaction],
)

object TransactionTable {
  implicit val format: Format[TransactionTable] = Json.format
}

case class TableState(table: TransactionTable) {
  def applyCommand(cmd: TableCommand, state: TableState, entityId: String): ReplyEffect[TableEvent, TableState] =
    cmd match {
      case PlaceTransaction(orderId, asset, price, quantity, orderType, user, timestamp, replyTo) =>
        Effect
          .persist(
            TransactionPlaced(
              orderId,
              asset,
              price,
              quantity,
              orderType,
              user,
              timestamp
            )
          )
          .thenReply(replyTo) { _ =>
            Accepted
          }
      case ReevaluateTableTransactions(replyTo) =>
        case class MatchedTransactions(buy: Transaction, sell: Transaction, quantity: BigDecimal)

        // TODO: document this logic
        def matchTransactions(): Option[MatchedTransactions] = {
          var resolvedBuyTransaction: Option[Transaction] = None
          var resolvedSellTransaction: Option[Transaction] = None

          state.table.buys.sorted
            .find(buyTransaction => {
              val resolvedTransaction = state.table.sells.sorted
                .find(sellTransaction =>
                  buyTransaction.price >= sellTransaction.price)

              resolvedTransaction match {
                case Some(_) =>
                  resolvedBuyTransaction = Some(buyTransaction)
                  resolvedSellTransaction = resolvedTransaction
                  true
                case None => false
              }
            })

          if (resolvedBuyTransaction.isEmpty || resolvedSellTransaction.isEmpty) {
            return None
          }

          // TODO: and this
          val minQuantityTransaction = List(resolvedBuyTransaction, resolvedSellTransaction).flatten match {
            case transaction => transaction.min
          }

          Option(MatchedTransactions(
            resolvedBuyTransaction.get, resolvedSellTransaction.get, minQuantityTransaction.quantity))
        }

        val matchedTransactions = matchTransactions().getOrElse(
          return Effect.none.thenReply(replyTo) { _ => Rejected(reason = "No matched transactions") })

        Effect
          .persist(TransactionResolved(
            transactionId = UUID.randomUUID().toString,
            asset = entityId,
            price = matchedTransactions.buy.price,
            quantity = matchedTransactions.quantity,
            timestamp = LocalDateTime.now().toString,
            buyer = matchedTransactions.buy.user,
            seller = matchedTransactions.sell.user,
            buyOrderId = matchedTransactions.buy.orderId,
            sellOrderId = matchedTransactions.sell.orderId
          ))
          .thenReply(replyTo) { _ =>
            Accepted
          }
    }

  def applyEvent(evt: TableEvent, state: TableState): TableState =
    evt match {
      case TransactionPlaced(orderId, _, price, quantity, orderType, user, timestamp) =>
        orderType match {
          case Buy => TableState(
            TransactionTable(
              buys = state.table.buys :+ Transaction(orderId, price, quantity, user, timestamp),
              sells = state.table.sells
            ))

          case Sell => TableState(
            TransactionTable(
              buys = state.table.buys,
              sells = state.table.sells :+ Transaction(orderId, price, quantity, user, timestamp)
            ))
        }
      case TransactionResolved(_, _, _, _, _, _, _, buyOrderId, sellOrderId) =>
        TableState(
          TransactionTable(
            buys = state.table.buys
              .filter(transaction => transaction.orderId != buyOrderId),
            sells = state.table.sells
              .filter(transaction => transaction.orderId != sellOrderId)
          )
        )
    }
}

object TableState {
  def initial: TableState = TableState(
    TransactionTable(buys = Seq.empty, sells = Seq.empty))

  val typeKey: EntityTypeKey[TableCommand] =
    EntityTypeKey[TableCommand]("TableAggregate")

  implicit val format: Format[TableState] = Json.format
}


sealed trait TableEvent extends AggregateEvent[TableEvent] {
  def aggregateTag: AggregateEventTag[TableEvent] = TableEvent.Tag
}

object TableEvent {
  val Tag: AggregateEventTag[TableEvent] = AggregateEventTag[TableEvent]
}

case class TransactionPlaced(
  orderId: UUID,
  asset: String,
  price: BigDecimal,
  quantity: BigDecimal,
  orderType: OrderType,
  user: UUID,
  timestamp: String
) extends TableEvent

object TransactionPlaced {
  implicit val format: Format[TransactionPlaced] = Json.format
}

case class TransactionResolved(
  transactionId: String,
  asset: String,
  price: BigDecimal,
  quantity: BigDecimal,
  timestamp: String,
  buyer: UUID,
  seller: UUID,
  buyOrderId: UUID,
  sellOrderId: UUID
) extends TableEvent

object TransactionResolved {
  implicit val format: Format[TransactionResolved] = Json.format
}


trait TableCommandSerializable

sealed trait TableCommand
    extends TableCommandSerializable


case class PlaceTransaction(
  orderId: UUID,
  asset: String,
  price: BigDecimal,
  quantity: BigDecimal,
  orderType: OrderType,
  user: UUID,
  timestamp: String,
  replyTo: ActorRef[Confirmation]
) extends TableCommand

case class ReevaluateTableTransactions(replyTo: ActorRef[Confirmation]) extends TableCommand


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


object TableSerializerRegistry extends JsonSerializerRegistry {
  override def serializers: Seq[JsonSerializer[_]] = Seq(
    JsonSerializer[Transaction],
    JsonSerializer[TransactionTable],
    JsonSerializer[TableState],
    JsonSerializer[TransactionPlaced],
    JsonSerializer[TransactionResolved],
    JsonSerializer[Confirmation],
    JsonSerializer[Accepted],
    JsonSerializer[Rejected]
  )
}
