package xarvalus.reactive.stock.table.stream.impl

import akka.NotUsed
import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.lightbend.lagom.scaladsl.playjson.{JsonSerializer, JsonSerializerRegistry}
import xarvalus.reactive.stock.table.api.TableService
import xarvalus.reactive.stock.table.stream.api.TableStreamService

import scala.concurrent.{ExecutionContext, Future}

class TableStreamServiceImpl(
  tableService: TableService
)(implicit ec: ExecutionContext)
  extends TableStreamService {
  override def resolvedTransactionsStream = ServiceCall { _ =>
    Future.successful(
      tableService
        .resolvedTransactionsTopic()
        .subscribe
        .atMostOnceSource
        .mapMaterializedValue(_ => NotUsed))
  }
}

object TableStreamSerializerRegistry extends JsonSerializerRegistry {
  override def serializers: Seq[JsonSerializer[_]] = Seq()
}
