package xarvalus.reactive.stock.trader.impl

import java.util.UUID

import akka.Done
import com.lightbend.lagom.scaladsl.persistence.{
  AggregateEvent, AggregateEventShards, AggregateEventTag, PersistentEntity}
import com.lightbend.lagom.scaladsl.playjson.{JsonSerializer, JsonSerializerRegistry}
import play.api.libs.json.{Format, Json}
import xarvalus.reactive.stock.trader.api.{AssetDone, AssetsDone, BalanceDone}
import xarvalus.reactive.stock.trader.impl.util.SecurePasswordHashing

import scala.collection.immutable.Seq


class TraderEntity extends PersistentEntity {
  override type Command = TraderCommand
  override type Event = TraderEvent
  override type State = TraderState

  override def initialState = TraderState(None)

  override def behavior: Behavior = {
    Actions()
      .onCommand[Register, Done] {
        case (Register(firstName, lastName, email, username, password), ctx, state) =>
          state.user match {
            case None =>
              val hashedPassword = SecurePasswordHashing.hashPassword(password)

              ctx.thenPersist(
                UserCreated(
                  userId = UUID.fromString(entityId),
                  firstName = firstName,
                  lastName = lastName,
                  email = email,
                  username = username,
                  hashedPassword = hashedPassword
                )
              ) { _ =>
                ctx.reply(Done)
              }
            case Some(_) =>
              ctx.invalidCommand(s"User with id $entityId is already registered")
              ctx.done
          }
      }
      .onReadOnlyCommand[Balance, BalanceDone] {
        case (Balance(), ctx, state) =>
          state.user match {
            case Some(user) =>
              ctx.reply(
                BalanceDone(
                  value = user.balance
                )
              )
            case None =>
              ctx.invalidCommand(s"User with ID $entityId can't be found")
          }
      }
      .onReadOnlyCommand[Assets, AssetsDone] {
        case (Assets(), ctx, state) =>
          state.user match {
            case Some(user) =>
              ctx.reply(
                AssetsDone(
                  assets = user.assets
                )
              )
            case None =>
              ctx.invalidCommand(s"User with ID $entityId can't be found")
          }
      }
      .onReadOnlyCommand[Asset, AssetDone] {
        case (Asset(name), ctx, state) =>
          state.user match {
            case Some(user) =>
              if (user.assets.isDefinedAt(name)) {
                ctx.reply(
                  AssetDone(value = user.assets(name))
                )
              } else {
                ctx.invalidCommand(s"Asset $name not found on user with ID $entityId")
              }
            case None =>
              ctx.invalidCommand(s"User with ID $entityId can't be found")
          }
      }
      .onCommand[AddAsset, Done] {
        case (AddAsset(name, value), ctx, state) =>
          state.user match {
            case Some(_) =>
              ctx.thenPersist(
                AssetAdded(
                  name,
                  value
                )
              ) { _ =>
                ctx.reply(Done)
              }
            case None =>
              ctx.invalidCommand(s"User with ID $entityId can't be found")
              ctx.done
          }
      }
      .onEvent {
        case (UserCreated(userId, firstName, lastName, email, username, hashedPassword), _) =>
          TraderState(
            Some(User(
              id = userId,
              firstName = firstName,
              lastName = lastName,
              email = email,
              username = username,
              password = hashedPassword
            ))
          )
        case (AssetAdded(name, value), state) =>
          state.addAsset(name, value)
      }
  }
}


sealed trait TraderCommand

case class Register(
  firstName: String,
  lastName: String,
  email: String,
  username: String,
  password: String
) extends PersistentEntity.ReplyType[Done] with TraderCommand

object Register {
  implicit val format: Format[Register] = Json.format
}

case class Balance() extends PersistentEntity.ReplyType[BalanceDone] with TraderCommand
case class Assets() extends PersistentEntity.ReplyType[AssetsDone] with TraderCommand

case class Asset(name: String) extends PersistentEntity.ReplyType[AssetDone] with TraderCommand

object Asset {
  implicit val format: Format[Asset] = Json.format
}

case class AddAsset(name: String, value: BigDecimal) extends PersistentEntity.ReplyType[Done] with TraderCommand

object AddAsset {
  implicit val format: Format[AddAsset] = Json.format
}


sealed trait TraderEvent extends AggregateEvent[TraderEvent] {
  override def aggregateTag(): AggregateEventShards[TraderEvent] = TraderEvent.Tag
}

object TraderEvent {
  val NumShards = 5
  val Tag = AggregateEventTag.sharded[TraderEvent]("TraderEvent", NumShards)
}

case class UserCreated(
  userId: UUID,
  firstName: String,
  lastName: String,
  email: String,
  username: String,
  hashedPassword: String
) extends TraderEvent

object UserCreated {
  implicit val format: Format[UserCreated] = Json.format
}

case class AssetAdded(
  name: String,
  value: BigDecimal
) extends TraderEvent

object AssetAdded {
  implicit val format: Format[AssetAdded] = Json.format
}


case class User(
  id: UUID,
  firstName: String,
  lastName: String,
  email: String,
  username: String,
  password: String,
  balance: BigDecimal = 0,
  assets: Map[String, BigDecimal] = Map.empty
)

object User {
  implicit val format: Format[User] = Json.format
}

case class TraderState(user: Option[User]) {
  def addAsset(asset: String, value: BigDecimal): TraderState = user match {
    case None => throw new IllegalStateException("User does not exists")
    case Some(existing_user) =>
      val assetsWithNewAdded = existing_user.assets + (asset -> value)

      TraderState(
        Some(existing_user.copy(assets = assetsWithNewAdded)))
  }
}

object TraderState {
  implicit val format: Format[TraderState] = Json.format
}


object TraderSerializerRegistry extends JsonSerializerRegistry {
  override def serializers: Seq[JsonSerializer[_]] = Seq(
    JsonSerializer[Register],
    JsonSerializer[Asset],
    JsonSerializer[AddAsset],
    JsonSerializer[UserCreated],
    JsonSerializer[AssetAdded],
    JsonSerializer[User],
    JsonSerializer[TraderState]
  )
}
