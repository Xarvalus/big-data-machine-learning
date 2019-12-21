package xarvalus.reactive.stockstream.impl

import com.lightbend.lagom.scaladsl.api.ServiceCall
import xarvalus.reactive.stockstream.api.ReactivestockStreamService
import xarvalus.reactive.stock.api.ReactivestockService

import scala.concurrent.Future

/**
  * Implementation of the ReactivestockStreamService.
  */
class ReactivestockStreamServiceImpl(reactivestockService: ReactivestockService) extends ReactivestockStreamService {
  def stream = ServiceCall { hellos =>
    Future.successful(hellos.mapAsync(8)(reactivestockService.hello(_).invoke()))
  }
}
