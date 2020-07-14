package xarvalus.reactive.stock.asset.impl

import com.lightbend.lagom.scaladsl.api.ServiceLocator
import com.lightbend.lagom.scaladsl.api.ServiceLocator.NoServiceLocator
import com.lightbend.lagom.scaladsl.persistence.cassandra.CassandraPersistenceComponents
import com.lightbend.lagom.scaladsl.server._
import com.lightbend.lagom.scaladsl.devmode.LagomDevModeComponents
import play.api.libs.ws.ahc.AhcWSComponents
import com.lightbend.lagom.scaladsl.broker.kafka.LagomKafkaComponents
import com.lightbend.lagom.scaladsl.playjson.JsonSerializerRegistry
import com.softwaremill.macwire._
import xarvalus.reactive.stock.asset.api.AssetService
import xarvalus.reactive.stock.table.api.TableService

class AssetLoader extends LagomApplicationLoader {

  override def load(context: LagomApplicationContext): LagomApplication =
    new AssetApplication(context) {
      override def serviceLocator: ServiceLocator = NoServiceLocator
    }

  override def loadDevMode(context: LagomApplicationContext): LagomApplication =
    new AssetApplication(context) with LagomDevModeComponents

  override def describeService = Some(readDescriptor[AssetService])
}

abstract class AssetApplication(context: LagomApplicationContext)
  extends LagomApplication(context)
    with CassandraPersistenceComponents
    with LagomKafkaComponents
    with AhcWSComponents {

  override lazy val lagomServer: LagomServer = serverFor[AssetService](wire[AssetServiceImpl])
  override lazy val jsonSerializerRegistry: JsonSerializerRegistry = AssetSerializerRegistry

  lazy val tableService: TableService = serviceClient.implement[TableService]
  lazy val assetRepository: AssetRepository = wire[AssetRepository]

  persistentEntityRegistry.register(wire[AssetEntity])
  readSide.register(wire[AssetEventProcessor])
}
