/*
 * Copyright 2021 HM Revenue & Customs
 *
 */

package repositories

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import com.google.inject.{Inject, Singleton}
import models.declarations.{Declaration, State}
import models.{ChargeReference, DeclarationsStatus}
import play.api.Configuration
import play.api.libs.json.{JsNumber, JsObject, JsString, Json}
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.akkastream.cursorProducer
import reactivemongo.api.Cursor
import reactivemongo.api.Cursor.ContOnError
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.play.json.ImplicitBSONHandlers.JsObjectDocumentWriter
import reactivemongo.play.json.collection.JSONCollection
import services.{ChargeReferenceService, ValidationService, Validator}

import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions

@Singleton
class DefaultDeclarationsRepository @Inject() (
  mongo: ReactiveMongoApi,
  chargeReferenceService: ChargeReferenceService,
  validationService: ValidationService,
  config: Configuration
)(implicit ec: ExecutionContext, m: Materializer) extends DeclarationsRepository {

  private def validator(schemaVersion: String): Validator = {
    val schema = config.get[String](s"declarations.schemas.$schemaVersion")
    validationService.get(schema)
  }

  private val collectionName: String = "declarations"

  private def collection: Future[JSONCollection] =
    mongo.database.map(_.collection[JSONCollection](collectionName))

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
        val validationErrors = validator("request").validate(json)

        if (validationErrors.isEmpty) {

          val declaration = Declaration(
            chargeReference = id,
            state           = State.PendingPayment,
            journeyData     = json.apply("journeyData").as[JsObject],
            data            = json - "journeyData",
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

  override def unpaidDeclarations: Source[Declaration, Future[NotUsed]] = {

    val query = Json.obj(
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
          .documentSource(err = ContOnError())
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
          .documentSource(err = ContOnError())
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
          .documentSource(err = ContOnError())
          .mapMaterializedValue(_ => NotUsed.notUsed)
      }
    }
  }

  override def metricsCount: Source[DeclarationsStatus, NotUsed] = {

    def buildDeclarationStatus: Future[DeclarationsStatus] = collection.flatMap { col =>
      import col.BatchCommands.AggregationFramework.{Group, SumAll}
      col.aggregatorContext[JsObject](Group(JsString("$state"))("count" -> SumAll))
        .prepared
        .cursor
        .collect[List](-1, Cursor.FailOnError[List[JsObject]]()) map { jsObjects =>
        val ds = jsObjects.foldLeft(DeclarationsStatus(0, 0, 0, 0, 0)) { (status, jsObject) =>
          (jsObject \ "_id").as[JsString].value match {
            case "pending-payment" => status.copy(pendingPaymentCount = (jsObject \ "count").as[JsNumber].value.toInt)
            case "paid" => status.copy(paymentCompleteCount = (jsObject \ "count").as[JsNumber].value.toInt)
            case "payment-failed" => status.copy(paymentFailedCount = (jsObject \ "count").as[JsNumber].value.toInt)
            case "payment-cancelled" => status.copy(paymentCancelledCount = (jsObject \ "count").as[JsNumber].value.toInt)
            case "submission-failed" => status.copy(failedSubmissionCount = (jsObject \ "count").as[JsNumber].value.toInt)
          }
        }

        ds
      }
    }

    Source.unfoldAsync[Boolean, DeclarationsStatus](true) {
      case true => buildDeclarationStatus.map(ds => Some((false, ds)))
      case false => Future.successful(None)
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
  def unpaidDeclarations: Source[Declaration, Future[NotUsed]]
  def paidDeclarations: Source[Declaration, Future[NotUsed]]
  def failedDeclarations: Source[Declaration, Future[NotUsed]]
  def metricsCount: Source[DeclarationsStatus, NotUsed]
}
