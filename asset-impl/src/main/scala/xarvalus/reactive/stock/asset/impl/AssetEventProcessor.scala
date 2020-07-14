package xarvalus.reactive.stock.asset.impl

import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.UUID

import akka.Done
import com.datastax.driver.core.PreparedStatement
import com.lightbend.lagom.scaladsl.persistence.ReadSideProcessor.ReadSideHandler
import com.lightbend.lagom.scaladsl.persistence.cassandra.{CassandraReadSide, CassandraSession}
import com.lightbend.lagom.scaladsl.persistence.{AggregateEventTag, EventStreamElement, ReadSideProcessor}

import scala.concurrent.{ExecutionContext, Future}

class AssetEventProcessor(
  session: CassandraSession,
  readSide: CassandraReadSide
)(implicit ec: ExecutionContext)
  extends ReadSideProcessor[AssetEvent] {
  private var insertAssetStatement: PreparedStatement = _

  override def buildHandler(): ReadSideHandler[AssetEvent] = {
    readSide.builder[AssetEvent]("AssetEventOffset")
      .setGlobalPrepare(createTable)
      .setPrepare { _ =>
        prepareStatements()
      }.setEventHandler[PriceUpdated](insertAsset)
      .build()
  }

  override def aggregateTags: Set[AggregateEventTag[AssetEvent]] = {
    AssetEvent.Tag.allTags
  }

  private def createTable(): Future[Done] = {
    session.executeCreateTable(
      "CREATE TABLE IF NOT EXISTS assets(" +
        "id         uuid," +
        "asset      varchar," +
        "price      decimal," +
        "updated_at timestamp, PRIMARY KEY (id)" +
      ");"
    )
  }

  private def prepareStatements(): Future[Done] = {
      session
        .prepare("INSERT INTO assets(id, asset, price, updated_at) VALUES (?, ?, ?, ?)")
        .map(insertAsset => {
          insertAssetStatement = insertAsset
          Done
        })
  }

  private def insertAsset(asset: EventStreamElement[PriceUpdated]) = {
    Future.successful(
      List(
        insertAssetStatement.bind(
          // TODO: rewrite to
          // asset.entityId,
          UUID.randomUUID(),
          asset.event.asset,
          asset.event.price.bigDecimal,
          Timestamp.valueOf(LocalDateTime.now())
        )
      )
    )
  }
}
