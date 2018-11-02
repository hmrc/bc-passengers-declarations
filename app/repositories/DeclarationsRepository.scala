package repositories

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import com.google.inject.{Inject, Singleton}
import models.ChargeReference
import models.declarations.{Declaration, State}
import play.api.Configuration
import play.api.libs.json.{JsObject, Json}
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.akkastream.cursorProducer
import reactivemongo.api.Cursor
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.play.json.ImplicitBSONHandlers.JsObjectDocumentWriter
import reactivemongo.play.json.collection.JSONCollection
import services.ChargeReferenceService

import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions

@Singleton
class DefaultDeclarationsRepository @Inject() (
                                                mongo: ReactiveMongoApi,
                                                chargeReferenceService: ChargeReferenceService,
                                                config: Configuration
                                              )(implicit ec: ExecutionContext, m: Materializer) extends DeclarationsRepository {

  private val collectionName: String = "declarations"

  private def collection: Future[JSONCollection] =
    mongo.database.map(_.collection[JSONCollection](collectionName))

  private val paymentTimeout = config.get[Duration]("declarations.payment-no-response-timeout")

  private val index = Index(
    key     = Seq("lastUpdated" -> IndexType.Ascending),
    name    = Some("declarations-index")
  )

  val started: Future[Unit] = {
    collection.flatMap {
      _.indexesManager.ensure(index)
    }.map(_ => ())
  }

  override def insert(data: JsObject): Future[ChargeReference] = {

    for {
      id <- chargeReferenceService.nextChargeReference()
      _  <- collection.flatMap(_.insert(Declaration(
        chargeReference = id,
        state           = State.PendingPayment,
        data            = data ++ id
      )))
    } yield id
  }

  override def get(id: ChargeReference): Future[Option[Declaration]] =
    collection.flatMap(_.find(Json.obj("_id" -> id.toString), None).one[Declaration])

  override def remove(id: ChargeReference): Future[Option[Declaration]] =
    collection.flatMap(_.findAndRemove(Json.obj("_id" -> id.toString)).map(_.result[Declaration]))

  def setState(id: ChargeReference, state: State): Future[Declaration] = {

    val selector = Json.obj(
      "_id" -> id
    )

    val update = Json.obj(
      "$set" -> Json.obj(
        "state" -> state
      )
    )

    collection.flatMap {
      _.findAndUpdate(selector, update, fetchNewObject = true)
        .map {
          _.result[Declaration]
            .getOrElse(throw new Exception(s"unable to update declaration $id"))
        }
    }
  }

  def staleDeclarations: Source[Declaration, Future[NotUsed]] = {

    val timeout = LocalDateTime.now.minus(paymentTimeout.toMillis, ChronoUnit.MILLIS)

    val query = Json.obj(
      "lastUpdated" -> Json.obj("$lt" -> timeout),
      "state"       -> State.PendingPayment
    )

    Source.fromFutureSource {
      collection.map {
        _.find(query, None)
          .cursor[Declaration]()
          .documentSource(err = Cursor.ContOnError())
          .mapMaterializedValue(_ => NotUsed.notUsed)
      }
    }
  }

  private implicit def toJson(chargeReference: ChargeReference): JsObject = {
    Json.obj(
      "simpleDeclarationRequest" -> Json.obj(
        "requestDetail" -> Json.obj(
          "declarationHeader" -> Json.obj(
            "chargeReference" -> chargeReference.toString
          )
        )
      )
    )
  }
}

trait DeclarationsRepository {

  val started: Future[Unit]
  def insert(data: JsObject): Future[ChargeReference]
  def get(id: ChargeReference): Future[Option[Declaration]]
  def remove(id: ChargeReference): Future[Option[Declaration]]
  def staleDeclarations: Source[Declaration, Future[NotUsed]]
  def setState(id: ChargeReference, state: State): Future[Declaration]
}
