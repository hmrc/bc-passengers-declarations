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
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.play.json.ImplicitBSONHandlers.JsObjectDocumentWriter
import reactivemongo.play.json.collection.JSONCollection
import services.{ChargeReferenceService, ValidationService, Validator}

import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions

@Singleton
class DefaultDeclarationsRepository @Inject() (
  mongo: ReactiveMongoApi,
  chargeReferenceService: ChargeReferenceService,
  validationService: ValidationService,
  config: Configuration
)(implicit ec: ExecutionContext, m: Materializer) extends DeclarationsRepository {

  private val validator: Validator = {
    val schema = config.get[String]("declarations.schema")
    validationService.get(schema)
  }

  private val collectionName: String = "declarations"

  private def collection: Future[JSONCollection] =
    mongo.database.map(_.collection[JSONCollection](collectionName))

  private val paymentTimeout = config.get[Duration]("declarations.payment-no-response-timeout")

  private val lastUpdatedIndex = Index(
    key     = Seq("lastUpdated" -> IndexType.Ascending),
    name    = Some("declarations-last-updated-index")
  )

  private val stateIndex = Index(
    key  = Seq("state" -> IndexType.Ascending),
    name = Some("declarations-state-index")
  )

  val started: Future[Unit] =
    collection.flatMap {
      coll =>

        for {
          _ <- coll.indexesManager.ensure(lastUpdatedIndex)
          _ <- coll.indexesManager.ensure(stateIndex)
        } yield ()
    }

  override def insert(data: JsObject, correlationId: String): Future[Either[List[String], Declaration]] = {

    chargeReferenceService.nextChargeReference().flatMap {
      id: ChargeReference =>

        val json             = data deepMerge id
        val validationErrors = validator.validate(json)

        if (validationErrors.isEmpty) {

          val declaration = Declaration(
            chargeReference = id,
            state           = State.PendingPayment,
            data            = json,
            correlationId   = correlationId
          )

          collection.flatMap(_.insert(declaration))
            .map(_ => Right(declaration))
        } else {

          Future.successful(Left(validationErrors))
        }
    }
  }

  override def get(id: ChargeReference): Future[Option[Declaration]] =
    collection.flatMap(_.find(Json.obj("_id" -> id.toString), None).one[Declaration])

  override def remove(id: ChargeReference): Future[Option[Declaration]] =
    collection.flatMap(_.findAndRemove(Json.obj("_id" -> id.toString)).map(_.result[Declaration]))

  override def setState(id: ChargeReference, state: State): Future[Declaration] = {

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

  override def staleDeclarations: Source[Declaration, Future[NotUsed]] = {

    val timeout = LocalDateTime.now.minus(paymentTimeout.toMillis, ChronoUnit.MILLIS)

    val query = Json.obj(
      "lastUpdated" -> Json.obj("$lt" -> timeout),
      "$or" -> Json.arr(
        Json.obj("state" -> State.PendingPayment),
        Json.obj("state" -> State.PaymentCancelled),
        Json.obj("state" -> State.PaymentFailed)
      )
    )

    Source.fromFutureSource {
      collection.map {
        _.find(query, None)
          .cursor[Declaration]()
          .documentSource()
          .mapMaterializedValue(_ => NotUsed.notUsed)
      }
    }
  }

  override def paidDeclarations: Source[Declaration, Future[NotUsed]] = {

    val query = Json.obj(
      "state" -> State.Paid
    )

    Source.fromFutureSource {
      collection.map {
        _.find(query, None)
          .cursor[Declaration]()
          .documentSource()
          .mapMaterializedValue(_ => NotUsed.notUsed)
      }
    }
  }

  override def failedDeclarations: Source[Declaration, Future[NotUsed]] = {

   val query = Json.obj(
     "state" -> State.SubmissionFailed
   )

    Source.fromFutureSource {
      collection.map {
        _.find(query, None)
          .cursor[Declaration]()
          .documentSource()
          .mapMaterializedValue(_ => NotUsed.notUsed)
      }
    }
  }

  private implicit def toJson(chargeReference: ChargeReference): JsObject = {
    Json.obj(
      "simpleDeclarationRequest" -> Json.obj(
        "requestCommon" -> Json.obj(
          "acknowledgementReference" -> (chargeReference.toString+"0")
        ),
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
  def insert(data: JsObject, correlationId: String): Future[Either[List[String], Declaration]]
  def get(id: ChargeReference): Future[Option[Declaration]]
  def remove(id: ChargeReference): Future[Option[Declaration]]
  def setState(id: ChargeReference, state: State): Future[Declaration]
  def staleDeclarations: Source[Declaration, Future[NotUsed]]
  def paidDeclarations: Source[Declaration, Future[NotUsed]]
  def failedDeclarations: Source[Declaration, Future[NotUsed]]
}
