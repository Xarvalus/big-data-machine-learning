package xarvalus.reactive.stock.table.stream.impl

import com.lightbend.lagom.scaladsl.api.ServiceLocator
import com.lightbend.lagom.scaladsl.api.ServiceLocator.NoServiceLocator
import com.lightbend.lagom.scaladsl.broker.kafka.LagomKafkaComponents
import com.lightbend.lagom.scaladsl.server._
import com.lightbend.lagom.scaladsl.devmode.LagomDevModeComponents
import com.lightbend.lagom.scaladsl.persistence.cassandra.CassandraPersistenceComponents
import com.lightbend.lagom.scaladsl.playjson.JsonSerializerRegistry
import play.api.libs.ws.ahc.AhcWSComponents
import com.softwaremill.macwire._
import xarvalus.reactive.stock.table.api.TableService
import xarvalus.reactive.stock.table.stream.api.TableStreamService

class TableStreamLoader extends LagomApplicationLoader {

  override def load(context: LagomApplicationContext): LagomApplication =
    new TableStreamApplication(context) {
      override def serviceLocator: ServiceLocator = NoServiceLocator
    }

  override def loadDevMode(context: LagomApplicationContext): LagomApplication =
    new TableStreamApplication(context) with LagomDevModeComponents

  override def describeService = Some(readDescriptor[TableStreamService])
}

abstract class TableStreamApplication(context: LagomApplicationContext)
  extends LagomApplication(context)
    with CassandraPersistenceComponents
    with LagomKafkaComponents
    with AhcWSComponents {

  override lazy val lagomServer: LagomServer = serverFor[TableStreamService](wire[TableStreamServiceImpl])
  override lazy val jsonSerializerRegistry: JsonSerializerRegistry = TableStreamSerializerRegistry

  lazy val tableService: TableService = serviceClient.implement[TableService]
}
