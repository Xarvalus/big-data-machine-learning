package xarvalus.reactive.stock.table.impl

import java.util.UUID

import akka.Done
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, EntityRef}
import akka.stream.scaladsl.Flow
import com.lightbend.lagom.scaladsl.api.broker.Topic
import com.lightbend.lagom.scaladsl.broker.TopicProducer
import com.lightbend.lagom.scaladsl.persistence.EventStreamElement
import com.lightbend.lagom.scaladsl.persistence.PersistentEntityRegistry

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import akka.util.Timeout
import com.lightbend.lagom.scaladsl.api.transport.BadRequest
import xarvalus.reactive.stock.table.api.{ResolvedTransaction, TableService}
import xarvalus.reactive.stock.transaction.api.{PlacedOrder, TransactionService}

class TableServiceImpl(
  transactionService: TransactionService,
  clusterSharding: ClusterSharding,
  persistentEntityRegistry: PersistentEntityRegistry
)(implicit ec: ExecutionContext)
  extends TableService {
  private def entityRef(id: String): EntityRef[TableCommand] =
    clusterSharding.entityRefFor(TableState.typeKey, id)

  implicit val timeout: Timeout = Timeout(15.seconds)

  transactionService
    .ordersTopic()
    .subscribe
    .atLeastOnce(
      Flow.fromFunction(
        placedOrder => processOrder(placedOrder))
    )

  def processOrder(placedOrder: PlacedOrder): Done = {
    val ref = entityRef(placedOrder.asset)

    ref
      .ask[Confirmation](replyTo =>
        PlaceTransaction(
          orderId = UUID.fromString(placedOrder.orderId),
          asset = placedOrder.asset,
          price = placedOrder.price,
          quantity = placedOrder.quantity,
          orderType = placedOrder.orderType,
          user = placedOrder.user,
          timestamp = placedOrder.timestamp,
          replyTo,
        ))
      .map {
        case Accepted =>
          ref
            .ask[Confirmation](replyTo =>
              ReevaluateTableTransactions(replyTo)
            )
            .map {
              case Accepted => Done
              case _        => throw BadRequest("Cannot re-evaluate table")
            }
        case _        => throw BadRequest("Cannot process order")
      }

    Done
  }

  override def resolvedTransactionsTopic(): Topic[ResolvedTransaction] =
    TopicProducer.singleStreamWithOffset { fromOffset =>
      persistentEntityRegistry
        .eventStream(TableEvent.Tag, fromOffset)
        .mapConcat(filterEvents)
    }

  private def filterEvents(event: EventStreamElement[TableEvent])= event match {
    case event @ EventStreamElement(
      _, TransactionResolved(_, _, _, _, _, _, _, _, _), offset) => Seq((convertEvent(event), offset))
    case _ => Nil
  }

  private def convertEvent(
    tableEvent: EventStreamElement[TableEvent]
  ): ResolvedTransaction = {
    tableEvent.event match {
      case TransactionResolved(transactionId, asset, price, quantity, timestamp, buyer, seller, _, _) =>
        ResolvedTransaction(transactionId, asset, price, quantity, timestamp, buyer, seller)
      case _ => throw new Exception("Should never happen")
    }
  }
}
