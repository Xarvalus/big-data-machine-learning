package xarvalus.reactive.stock.asset.impl

import com.lightbend.lagom.scaladsl.persistence.cassandra.CassandraSession

import scala.concurrent.{ExecutionContext, Future}

class AssetRepository(
  db: CassandraSession
)(implicit ec: ExecutionContext) {
  def findAllAssets(): Future[Option[Assets]] = {
    val assets = db.selectAll("SELECT id, asset, price, updated_at FROM assets")
      .map { rows =>
        val names = rows.map(row => row.getString("asset"))

        if (names.isEmpty) {
          None
        } else {
          Some(
            Assets(names))
        }
      }

    assets
  }
}

case class Assets(names: Seq[String])
