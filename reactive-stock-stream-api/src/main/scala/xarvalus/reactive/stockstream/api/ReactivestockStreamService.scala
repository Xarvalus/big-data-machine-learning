package xarvalus.reactive.stockstream.api

import akka.NotUsed
import akka.stream.scaladsl.Source
import com.lightbend.lagom.scaladsl.api.{Descriptor, Service, ServiceCall}

/**
  * The reactive-stock stream interface.
  *
  * This describes everything that Lagom needs to know about how to serve and
  * consume the ReactivestockStream service.
  */
trait ReactivestockStreamService extends Service {

  def stream: ServiceCall[Source[String, NotUsed], Source[String, NotUsed]]

  override final def descriptor: Descriptor = {
    import Service._

    named("reactive-stock-stream")
      .withCalls(
        namedCall("stream", stream)
      ).withAutoAcl(true)
  }
}

