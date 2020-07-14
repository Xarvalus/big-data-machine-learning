package xarvalus.reactive.stock.trader.impl

import java.util.UUID

import com.lightbend.lagom.scaladsl.persistence.cassandra.CassandraSession

import scala.concurrent.{ExecutionContext, Future}

class TraderRepository(
  db: CassandraSession
)(implicit ec: ExecutionContext) {

  def findUserByUsername(username: String): Future[Option[UserByUsername]] = {
    val result = db.selectOne(
      "SELECT id, username, hashed_password FROM users_by_username WHERE username = ?", username)
      .map {
        case Some(row) => Option(
          UserByUsername(
            id = row.getUUID("id"),
            username = row.getString("username"),
            hashedPassword = row.getString("hashed_password")
          )
        )

        case None => Option.empty
      }

    result
  }
}

case class UserByUsername(id: UUID, username: String, hashedPassword: String)
