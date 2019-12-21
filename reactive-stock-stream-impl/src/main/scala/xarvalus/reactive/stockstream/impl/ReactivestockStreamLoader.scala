package xarvalus.reactive.stockstream.impl

import com.lightbend.lagom.scaladsl.api.ServiceLocator.NoServiceLocator
import com.lightbend.lagom.scaladsl.server._
import com.lightbend.lagom.scaladsl.devmode.LagomDevModeComponents
import play.api.libs.ws.ahc.AhcWSComponents
import xarvalus.reactive.stockstream.api.ReactivestockStreamService
import xarvalus.reactive.stock.api.ReactivestockService
import com.softwaremill.macwire._

class ReactivestockStreamLoader extends LagomApplicationLoader {

  override def load(context: LagomApplicationContext): LagomApplication =
    new ReactivestockStreamApplication(context) {
      override def serviceLocator: NoServiceLocator.type = NoServiceLocator
    }

  override def loadDevMode(context: LagomApplicationContext): LagomApplication =
    new ReactivestockStreamApplication(context) with LagomDevModeComponents

  override def describeService = Some(readDescriptor[ReactivestockStreamService])
}

abstract class ReactivestockStreamApplication(context: LagomApplicationContext)
  extends LagomApplication(context)
    with AhcWSComponents {

  // Bind the service that this server provides
  override lazy val lagomServer: LagomServer = serverFor[ReactivestockStreamService](wire[ReactivestockStreamServiceImpl])

  // Bind the ReactivestockService client
  lazy val reactivestockService: ReactivestockService = serviceClient.implement[ReactivestockService]
}
