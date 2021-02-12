package suite

import com.typesafe.config.ConfigFactory
import org.scalatest._
import play.api.{Application, Configuration}
import reactivemongo.api._
import repositories.{DeclarationsRepository, LockRepository}
import services.ChargeReferenceService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

object MongoSuite {

  private lazy val config = Configuration(ConfigFactory.load(System.getProperty("config.resource")))

  private lazy val parsedUri = Future.fromTry {
    MongoConnection.parseURI(config.get[String]("mongodb.uri"))
  }

  lazy val connection: Future[Try[MongoConnection]] =
    parsedUri.map(MongoDriver().connection(_,strictUri = false))
}

trait MongoSuite {
  self: TestSuite =>

  def started(app: Application): Future[_] = {

    val declarationsRepository = app.injector.instanceOf[DeclarationsRepository]
    val chargeReferenceService = app.injector.instanceOf[ChargeReferenceService]
    val lockRepository = app.injector.instanceOf[LockRepository]

    val services = Seq(declarationsRepository.started, chargeReferenceService.started, lockRepository.started)

    Future.sequence(services)
  }

  def database: Future[DefaultDB] = {
    for {
      uri        <- MongoSuite.parsedUri
      connection <- MongoSuite.connection
      database   <- connection.get.database(uri.db.get)
    } yield database
  }
}
