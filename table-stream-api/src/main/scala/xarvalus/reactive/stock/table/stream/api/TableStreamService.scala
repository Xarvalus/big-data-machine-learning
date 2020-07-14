package xarvalus.reactive.stock.table.stream.api

import akka.NotUsed
import akka.stream.scaladsl.Source
import com.lightbend.lagom.scaladsl.api.{Descriptor, Service, ServiceCall}
import xarvalus.reactive.stock.table.api.ResolvedTransaction

trait TableStreamService extends Service {
  def resolvedTransactionsStream: ServiceCall[NotUsed, Source[ResolvedTransaction, NotUsed]]

  override final def descriptor: Descriptor = {
    import Service._

    named("table-stream")
      .withCalls(
        namedCall("resolvedTransactionsStream", resolvedTransactionsStream)
      ).withAutoAcl(true)
  }
}
