package xarvalus.reactive.stock.asset.impl

import akka.{Done, NotUsed}
import akka.stream.scaladsl.Flow
import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.lightbend.lagom.scaladsl.persistence.PersistentEntityRegistry
import com.lightbend.lagom.scaladsl.server.ServerServiceCall
import xarvalus.reactive.stock.common.AuthenticationServiceComposition._
import xarvalus.reactive.stock.asset.api.{AssetDone, AssetService, AssetsDone}
import xarvalus.reactive.stock.table.api.{ResolvedTransaction, TableService}

import scala.concurrent.ExecutionContext

class AssetServiceImpl(
  tableService: TableService,
  assetRepository: AssetRepository,
  persistentEntityRegistry: PersistentEntityRegistry
)(implicit ec: ExecutionContext)
  extends AssetService {

  tableService
    .resolvedTransactionsTopic()
    .subscribe
    .atLeastOnce(
      Flow.fromFunction(
        resolvedTransaction => processTransaction(resolvedTransaction))
    )

  def processTransaction(resolvedTransaction: ResolvedTransaction): Done = {
    val ref = persistentEntityRegistry.refFor[AssetEntity](resolvedTransaction.asset)

    ref.ask(
      UpdatePrice(
        asset = resolvedTransaction.asset,
        price = resolvedTransaction.price
      )
    )

    Done
  }

  override def assets(): ServiceCall[NotUsed, AssetsDone] = authenticated { (_, _) =>
    ServerServiceCall { _ =>
      assetRepository
        .findAllAssets()
        .map {
          case Some(assets) => AssetsDone(assets.names)
          case None => throw new Exception("Cannot find assets")
        }
    }
  }

  override def asset(asset: String): ServiceCall[NotUsed, AssetDone] = authenticated { (_, _) =>
    ServerServiceCall { _ =>
      val ref = persistentEntityRegistry.refFor[AssetEntity](asset)

      ref.ask(
        AssetPrice()
      )
    }
  }
}
