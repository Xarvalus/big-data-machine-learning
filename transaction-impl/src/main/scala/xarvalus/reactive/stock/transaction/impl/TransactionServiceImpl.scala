package xarvalus.reactive.stock.transaction.impl

import java.util.UUID

import akka.Done
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, EntityRef}
import akka.util.Timeout
import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.lightbend.lagom.scaladsl.api.broker.Topic
import com.lightbend.lagom.scaladsl.api.transport.BadRequest
import com.lightbend.lagom.scaladsl.broker.TopicProducer
import com.lightbend.lagom.scaladsl.persistence.{EventStreamElement, PersistentEntityRegistry}
import com.lightbend.lagom.scaladsl.server.ServerServiceCall
import xarvalus.reactive.stock.common.AuthenticationServiceComposition._
import xarvalus.reactive.stock.common.ValidationUtil._
import xarvalus.reactive.stock.transaction.api.{PlacedOrder, TransactionService, Order => OrderRequest}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class TransactionServiceImpl(
  clusterSharding: ClusterSharding,
  persistentEntityRegistry: PersistentEntityRegistry
)(implicit ec: ExecutionContext)
  extends TransactionService {
  private def entityRef(id: String): EntityRef[TransactionCommand] =
    clusterSharding.entityRefFor(TransactionState.typeKey, id)

  implicit val timeout: Timeout = Timeout(15.seconds)

  override def order(): ServiceCall[OrderRequest, Done] = authenticated { (token, _) =>
    ServerServiceCall { request =>
      validate(request)

      val ref = entityRef(UUID.randomUUID().toString)

      ref
        .ask[Confirmation](replyTo =>
          PlaceOrder(
            asset = request.asset,
            price = request.price,
            quantity = request.quantity,
            orderType = request.orderType,
            user = token.userId,
            replyTo
          ))
        .map {
          case Accepted => Done
          case _        => throw BadRequest("Cannot place order")
        }
    }
  }

  override def ordersTopic(): Topic[PlacedOrder] =
    TopicProducer.singleStreamWithOffset { fromOffset =>
      persistentEntityRegistry
        .eventStream(TransactionEvent.Tag, fromOffset)
        .map(ev => (convertEvent(ev), ev.offset))
    }

  private def convertEvent(
    transactionEvent: EventStreamElement[TransactionEvent]
  ): PlacedOrder = {
    transactionEvent.event match {
      case OrderPlaced(orderId, asset, price, quantity, orderType, user, timestamp) =>
        PlacedOrder(orderId.toString, asset, price, quantity, orderType, user, timestamp)
    }
  }
}
