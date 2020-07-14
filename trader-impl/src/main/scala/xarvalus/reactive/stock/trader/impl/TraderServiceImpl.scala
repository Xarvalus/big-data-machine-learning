package xarvalus.reactive.stock.trader.impl

import java.util.UUID

import akka.{Done, NotUsed}
import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.lightbend.lagom.scaladsl.api.transport.Forbidden
import com.lightbend.lagom.scaladsl.persistence.PersistentEntityRegistry
import com.lightbend.lagom.scaladsl.server.ServerServiceCall
import xarvalus.reactive.stock.common.AuthenticationServiceComposition._
import xarvalus.reactive.stock.common.TokenContent
import xarvalus.reactive.stock.common.ValidationUtil._
import xarvalus.reactive.stock.trader.api.{
  AssetDone, AssetsDone, BalanceDone, LoginDone, TraderService, AddAsset => AddAssetRequest}
import xarvalus.reactive.stock.trader.impl.util.{JwtTokenUtil, SecurePasswordHashing}

import scala.concurrent.ExecutionContext

/**
  * JWT support based on:
  * https://github.com/dpalinic/lagom-jwt-authentication
  */
class TraderServiceImpl(
  traderRepository: TraderRepository,
  persistentEntityRegistry: PersistentEntityRegistry
)(implicit ec: ExecutionContext)
  extends TraderService {
  override def register() = ServiceCall { request =>
    validate(request)

    val ref = persistentEntityRegistry.refFor[TraderEntity](UUID.randomUUID().toString)

    ref.ask(
      Register(
        firstName = request.firstName,
        lastName = request.lastName,
        email = request.email,
        username = request.username,
        password = request.password
      )
    )
  }

  override def login() = ServiceCall { request =>
    validate(request)

    for {
      user <- traderRepository.findUserByUsername(request.username)

      token = user.filter(user =>
        SecurePasswordHashing.validatePassword(request.password, user.hashedPassword))
        .map(user =>
          TokenContent(
            userId = user.id,
            username = user.username
          )
        )
        .map(tokenContent => JwtTokenUtil.generateTokens(tokenContent))
        .getOrElse(throw Forbidden("Incorrect username and password"))
    } yield {
      LoginDone(token.authToken)
    }
  }

  override def balance(): ServerServiceCall[NotUsed, BalanceDone] = authenticated { (token, _) =>
    ServerServiceCall { _ =>
      val ref = persistentEntityRegistry.refFor[TraderEntity](token.userId.toString)

      ref.ask(
        Balance()
      )
    }
  }

  override def assets(): ServerServiceCall[NotUsed, AssetsDone] = authenticated { (token, _) =>
    ServerServiceCall { _ =>
      val ref = persistentEntityRegistry.refFor[TraderEntity](token.userId.toString)

      ref.ask(
        Assets()
      )
    }
  }

  override def asset(asset: String): ServerServiceCall[NotUsed, AssetDone] = authenticated { (token, _) =>
    ServerServiceCall { _ =>
      val ref = persistentEntityRegistry.refFor[TraderEntity](token.userId.toString)

      ref.ask(
        Asset(name = asset)
      )
    }
  }

  override def addAsset(asset: String): ServerServiceCall[AddAssetRequest, Done] = authenticated { (token, _) =>
    ServerServiceCall { request =>
      validate(request)

      val ref = persistentEntityRegistry.refFor[TraderEntity](token.userId.toString)

      ref.ask(
        AddAsset(
          name = asset,
          value = request.value
        )
      )
    }
  }
}
