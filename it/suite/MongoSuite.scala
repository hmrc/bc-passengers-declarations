package suite

import com.typesafe.config.ConfigFactory
import org.scalatest.Suite
import play.api.Configuration
import reactivemongo.api.{DefaultDB, MongoConnection, MongoDriver}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.reflectiveCalls

object MongoSuite {

  private lazy val config = Configuration(ConfigFactory.load(System.getProperty("config.resource")))

  private lazy val parsedUri = Future.fromTry {
    MongoConnection.parseURI(config.get[String]("mongodb.uri"))
  }

  private lazy val connection =
    parsedUri.map(MongoDriver().connection)
}

trait MongoSuite {
  self: Suite =>


  def database: Future[DefaultDB] = {
    for {
      uri        <- MongoSuite.parsedUri
      connection <- MongoSuite.connection
      database   <- connection.database(uri.db.get)
    } yield database
  }
}
