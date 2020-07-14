package xarvalus.reactive.stock.trader.impl

import com.lightbend.lagom.scaladsl.api.ServiceLocator
import com.lightbend.lagom.scaladsl.api.ServiceLocator.NoServiceLocator
import com.lightbend.lagom.scaladsl.persistence.cassandra.CassandraPersistenceComponents
import com.lightbend.lagom.scaladsl.server._
import com.lightbend.lagom.scaladsl.devmode.LagomDevModeComponents
import play.api.libs.ws.ahc.AhcWSComponents
import xarvalus.reactive.stock.trader.api.TraderService
import com.lightbend.lagom.scaladsl.broker.kafka.LagomKafkaComponents
import com.lightbend.lagom.scaladsl.playjson.JsonSerializerRegistry
import com.softwaremill.macwire._

class TraderLoader extends LagomApplicationLoader {

  override def load(context: LagomApplicationContext): LagomApplication =
    new TraderApplication(context) {
      override def serviceLocator: ServiceLocator = NoServiceLocator
    }

  override def loadDevMode(context: LagomApplicationContext): LagomApplication =
    new TraderApplication(context) with LagomDevModeComponents

  override def describeService = Some(readDescriptor[TraderService])
}

abstract class TraderApplication(context: LagomApplicationContext)
  extends LagomApplication(context)
    with CassandraPersistenceComponents
    with LagomKafkaComponents
    with AhcWSComponents {

  override lazy val lagomServer: LagomServer = serverFor[TraderService](wire[TraderServiceImpl])
  override lazy val jsonSerializerRegistry: JsonSerializerRegistry = TraderSerializerRegistry

  lazy val traderRepository: TraderRepository = wire[TraderRepository]

  persistentEntityRegistry.register(wire[TraderEntity])
  readSide.register(wire[TraderEventProcessor])
}
