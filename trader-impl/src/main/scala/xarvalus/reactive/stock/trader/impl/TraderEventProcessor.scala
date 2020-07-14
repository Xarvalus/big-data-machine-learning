package xarvalus.reactive.stock.trader.impl

import akka.Done
import com.datastax.driver.core.PreparedStatement
import com.lightbend.lagom.scaladsl.persistence.ReadSideProcessor.ReadSideHandler
import com.lightbend.lagom.scaladsl.persistence.cassandra.{CassandraReadSide, CassandraSession}
import com.lightbend.lagom.scaladsl.persistence.{AggregateEventTag, EventStreamElement, ReadSideProcessor}

import scala.concurrent.{ExecutionContext, Future}

class TraderEventProcessor(
  session: CassandraSession,
  readSide: CassandraReadSide
)(implicit ec: ExecutionContext)
  extends ReadSideProcessor[TraderEvent] {
  private var insertUserStatement: PreparedStatement = _

  override def buildHandler(): ReadSideHandler[TraderEvent] = {
    readSide.builder[TraderEvent]("TraderEventOffset")
      .setGlobalPrepare(createTable)
      .setPrepare { _ =>
        prepareStatements()
      }.setEventHandler[UserCreated](insertUser)
      .build()
  }

  override def aggregateTags: Set[AggregateEventTag[TraderEvent]] = {
    TraderEvent.Tag.allTags
  }

  private def createTable(): Future[Done] = {
    for {
      _ <- session.executeCreateTable(
        "CREATE TABLE IF NOT EXISTS users(" +
          "id              uuid," +
          "username        varchar," +
          "email           varchar," +
          "first_name      varchar," +
          "last_name       varchar," +
          "hashed_password varchar, PRIMARY KEY (id)" +
        ");"
      )

      _ <- session.executeCreateTable(
        "CREATE MATERIALIZED VIEW IF NOT EXISTS users_by_username AS " +
          "SELECT * FROM users " +
          "WHERE username IS NOT NULL " +
          "PRIMARY KEY(username, id);"
      )
    } yield {
      Done
    }
  }

  private def prepareStatements(): Future[Done] = {
    session
      .prepare("INSERT INTO users(id, username, email, first_name, last_name, hashed_password) VALUES (?, ?, ?, ?, ?, ?)")
      .map { insertUser =>
        insertUserStatement = insertUser
        Done
      }
  }

  private def insertUser(user: EventStreamElement[UserCreated]) = {
    Future.successful(
      List(
        insertUserStatement.bind(
          user.event.userId,
          user.event.username,
          user.event.email,
          user.event.firstName,
          user.event.lastName,
          user.event.hashedPassword
        )
      )
    )
  }
}
