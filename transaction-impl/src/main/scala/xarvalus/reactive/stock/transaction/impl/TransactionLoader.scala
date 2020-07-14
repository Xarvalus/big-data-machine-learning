package xarvalus.reactive.stock.transaction.impl

import akka.cluster.sharding.typed.scaladsl.Entity
import com.lightbend.lagom.scaladsl.api.ServiceLocator
import com.lightbend.lagom.scaladsl.api.ServiceLocator.NoServiceLocator
import com.lightbend.lagom.scaladsl.broker.kafka.LagomKafkaComponents
import com.lightbend.lagom.scaladsl.devmode.LagomDevModeComponents
import com.lightbend.lagom.scaladsl.persistence.cassandra.CassandraPersistenceComponents
import com.lightbend.lagom.scaladsl.playjson.JsonSerializerRegistry
import com.lightbend.lagom.scaladsl.server._
import com.softwaremill.macwire._
import play.api.libs.ws.ahc.AhcWSComponents
import xarvalus.reactive.stock.transaction.api.TransactionService

class TransactionLoader extends LagomApplicationLoader {
  override def load(context: LagomApplicationContext): LagomApplication =
    new TransactionApplication(context) {
      override def serviceLocator: ServiceLocator = NoServiceLocator
    }

  override def loadDevMode(context: LagomApplicationContext): LagomApplication =
    new TransactionApplication(context) with LagomDevModeComponents

  override def describeService = Some(readDescriptor[TransactionService])
}

abstract class TransactionApplication(context: LagomApplicationContext)
  extends LagomApplication(context)
    with CassandraPersistenceComponents
    with LagomKafkaComponents
    with AhcWSComponents {

  override lazy val lagomServer: LagomServer = serverFor[TransactionService](wire[TransactionServiceImpl])
  override lazy val jsonSerializerRegistry: JsonSerializerRegistry = TransactionSerializerRegistry

  clusterSharding.init(
    Entity(TransactionState.typeKey)(
      entityContext => TransactionBehavior.create(entityContext)
    )
  )
}
