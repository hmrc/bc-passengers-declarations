/*
 * Copyright 2023 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package repositories

import akka.NotUsed
import akka.stream.scaladsl.Source

import java.time.{LocalDateTime, ZoneOffset}
import com.google.inject.{Inject, Singleton}
import com.mongodb.client.model.Accumulators.{first, sum}
import org.mongodb.scala.model.Filters._
import models.declarations.{Declaration, State}
import models.{ChargeReference, DeclarationResponse, DeclarationsStatus, DeclarationsStatusCount, PreviousDeclarationRequest}
import org.bson.BsonValue
import org.mongodb.scala.model.Aggregates.group
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model.{Filters, FindOneAndReplaceOptions, FindOneAndUpdateOptions, IndexModel, IndexOptions, ReturnDocument, Updates}
import play.api.Configuration
import play.api.libs.json.{JsArray, JsObject, Json}
import services.{ChargeReferenceService, ValidationService, Validator}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.{implicitConversions, postfixOps}
import uk.gov.hmrc.mongo.play.json.Codecs

@Singleton
class DefaultDeclarationsRepository @Inject() (
  mongoComponent: MongoComponent,
  chargeReferenceService: ChargeReferenceService,
  validationService: ValidationService,
  config: Configuration
)(implicit val ec: ExecutionContext)
    extends PlayMongoRepository[Declaration](
      collectionName = "declarations",
      mongoComponent = mongoComponent,
      domainFormat = Declaration.format,
      indexes = Seq(
        IndexModel(ascending("lastUpdated"), IndexOptions().name("declarations-last-updated-index")),
        IndexModel(ascending("state"), IndexOptions().name("declarations-state-index")),
        IndexModel(ascending("amendState"), IndexOptions().name("declarations-amendState-index"))
      )
    )
    with DeclarationsRepository {

  private def validator(schemaVersion: String): Validator = {
    val schema = config.get[String](s"declarations.schemas.$schemaVersion")
    validationService.get(schema)
  }

  val started: Future[Unit] = null

  override def insert(
    data: JsObject,
    correlationId: String,
    sentToEtmp: Boolean
  ): Future[Either[List[String], Declaration]] =
    chargeReferenceService.nextChargeReference().flatMap { id: ChargeReference =>
      val json             = data deepMerge id
      val validationErrors = validator("request").validate(json)

      if (validationErrors.isEmpty) {

        val declaration = Declaration(
          chargeReference = id,
          state = State.PendingPayment,
          sentToEtmp = sentToEtmp,
          journeyData = json.apply("journeyData").as[JsObject],
          data = json - "journeyData",
          correlationId = correlationId
        )

        collection.insertOne(declaration).toFuture().map(_ => Right(declaration))
      } else {
        Future.successful(Left(validationErrors))
      }
    }

  override def insertAmendment(
    amendmentData: JsObject,
    correlationId: String,
    id: ChargeReference
  ): Future[Declaration] = {

    val declarations = Await
      .ready(
        awaitable = get(id) map (declaration =>
          Declaration(
            chargeReference = declaration.map(_.chargeReference).get,
            state = declaration.map(_.state).get,
            amendState = Some(State.PendingPayment),
            sentToEtmp = declaration.map(_.sentToEtmp).get,
            amendSentToEtmp = Some(false),
            correlationId = declaration.map(_.correlationId).get,
            amendCorrelationId = Some(correlationId),
            journeyData = amendmentData("journeyData").as[JsObject],
            data = declaration.map(_.data).get,
            amendData = Some((amendmentData - "journeyData").as[JsObject]),
            lastUpdated = LocalDateTime.now(ZoneOffset.UTC)
          )
        ),
        atMost = 2 seconds
      )
      .value
      .get
      .get

    collection
      .findOneAndReplace(
        filter = equal("_id", id.toString),
        replacement = declarations,
        options = FindOneAndReplaceOptions().upsert(false).returnDocument(ReturnDocument.AFTER)
      )
      .toFuture()

  }

  override def get(id: ChargeReference): Future[Option[Declaration]] =
    collection.find(equal("_id", id.toString)).first().toFutureOption()

  override def get(retrieveDeclarationRequest: PreviousDeclarationRequest): Future[Option[DeclarationResponse]] =
    collection
      .find(
        and(
          regex("_id", retrieveDeclarationRequest.referenceNumber, "i"),
          regex(
            "data.simpleDeclarationRequest.requestDetail.personalDetails.lastName",
            retrieveDeclarationRequest.lastName,
            "i"
          ),
          equal("state", "paid"),
          and(
            or(
              exists("amendState", false),
              equal("amendState", "paid"),
              equal("amendState", "pending-payment")
            )
          )
        )
      )
      .first()
      .toFuture()
      .map[Option[DeclarationResponse]] {
        case dec if Option(dec).isDefined =>
          Option(
            DeclarationResponse(
              dec.journeyData.value("euCountryCheck").as[String],
              dec.journeyData.value("arrivingNICheck").as[Boolean],
              dec.journeyData.value("ageOver17").as[Boolean],
              dec.journeyData.\("isUKResident").asOpt[Boolean],
              dec.journeyData.value("privateCraft").as[Boolean],
              dec.journeyData.value("userInformation").as[JsObject],
              dec.journeyData.value("calculatorResponse").as[JsObject].\("calculation").as[JsObject],
              dec.data
                .value("simpleDeclarationRequest")
                .as[JsObject]
                .\("requestDetail")
                .as[JsObject]
                .\("liabilityDetails")
                .as[JsObject],
              dec.journeyData.value("purchasedProductInstances").as[JsArray],
              dec.journeyData.\("amendmentCount").asOpt[Int],
              dec.journeyData.\("deltaCalculation").asOpt[JsObject],
              Some(dec.amendState.getOrElse("None").toString)
            )
          )
        case _                            => Option.empty
      }

  override def remove(id: ChargeReference): Future[Option[Declaration]] =
    collection.findOneAndDelete(equal("_id", Codecs.toBson(id))).headOption()

  override def setState(id: ChargeReference, state: State): Future[Declaration] =
    collection
      .findOneAndUpdate(
        equal("_id", Codecs.toBson(id)),
        Updates.combine(
          Updates.set("state", Codecs.toBson(state)),
          Updates.set("lastUpdated", Codecs.toBson(LocalDateTime.now(ZoneOffset.UTC)))
        ),
        options = FindOneAndUpdateOptions().upsert(false).returnDocument(ReturnDocument.AFTER)
      )
      .toFuture()

  override def setAmendState(id: ChargeReference, state: State): Future[Declaration] =
    collection
      .findOneAndUpdate(
        equal("_id", Codecs.toBson(id)),
        Updates.combine(
          Updates.set("amendState", Codecs.toBson(state)),
          Updates.set("lastUpdated", Codecs.toBson(LocalDateTime.now(ZoneOffset.UTC)))
        )
      )
      .toFuture()

  override def setSentToEtmp(id: ChargeReference, sentToEtmp: Boolean): Future[Declaration] =
    collection
      .findOneAndUpdate(equal("_id", Codecs.toBson(id)), Updates.set("sentToEtmp", Codecs.toBson(sentToEtmp)))
      .toFuture()

  override def setAmendSentToEtmp(id: ChargeReference, amendSentToEtmp: Boolean): Future[Declaration] =
    collection
      .findOneAndUpdate(equal("_id", Codecs.toBson(id)), Updates.set("amendSentToEtmp", Codecs.toBson(amendSentToEtmp)))
      .toFuture()

  override def unpaidDeclarations: Source[Declaration, NotUsed] =
    Source.fromPublisher {
      collection.find(
        or(
          equal("state", "pending-payment"),
          equal("state", "payment-cancelled"),
          equal("state", "payment-failed")
        )
      )
    }

  override def unpaidAmendments: Source[Declaration, NotUsed] =
    Source.fromPublisher {
      collection.find(filter =
        Filters.and(
          equal("state", "paid"),
          Filters.or(
            equal("amendState", "pending-payment"),
            equal("amendState", "payment-cancelled"),
            equal("amendState", "payment-failed")
          )
        )
      )
    }

  override def paidDeclarationsForEtmp: Source[Declaration, NotUsed] =
    Source.fromPublisher {
      collection.find(and(equal("state", "paid"), equal("sentToEtmp", false)))
    }

  override def paidAmendmentsForEtmp: Source[Declaration, NotUsed] =
    Source.fromPublisher {
      collection.find(
        and(
          equal("state", "paid"),
          equal("sentToEtmp", true),
          equal("amendState", "paid"),
          equal("amendSentToEtmp", false)
        )
      )
    }

  override def paidDeclarationsForDeletion: Source[Declaration, NotUsed] = {

    val filters = Filters.and(
      Filters.equal("state", "paid"),
      Filters.equal("sentToEtmp", true),
      Filters.or(
        Filters.and(
          Filters.exists("amendState", false),
          Filters.exists("amendSentToEtmp", false)
        ),
        Filters.and(
          Filters.equal("amendState", "paid"),
          Filters.equal("amendSentToEtmp", true)
        )
      )
    )

    Source.fromPublisher {
      collection.find(filter = filters)
    }
  }

  override def failedDeclarations: Source[Declaration, NotUsed] =
    Source.fromPublisher {
      collection.find(equal("state", "submission-failed"))
    }

  override def failedAmendments: Source[Declaration, NotUsed]             =
    Source.fromPublisher {
      collection.find(equal("amendState", Codecs.toBson("submission-failed")))
    }

  override def metricsCount: Source[DeclarationsStatus, NotUsed] = {

    def declarationsStatusCount: Future[List[DeclarationsStatusCount]] = collection
      .aggregate[BsonValue](List(group("$state", first("messageState", "$state"), sum("count", 1))))
      .toFuture()
      .map(_.toList.map(Codecs.fromBson[DeclarationsStatusCount]))

    Source.unfoldAsync[Boolean, DeclarationsStatus](true) {
      case true  => declarationsStatusCount.map(ds => Some((false, DeclarationsStatusCount.toDeclarationsStatus(ds))))
      case false => Future.successful(None)
    }
  }
  private implicit def toJson(chargeReference: ChargeReference): JsObject =
    Json.obj(
      "simpleDeclarationRequest" -> Json.obj(
        "requestCommon" -> Json.obj(
          "acknowledgementReference" -> (chargeReference.toString + "0")
        ),
        "requestDetail" -> Json.obj(
          "declarationHeader" -> Json.obj(
            "chargeReference" -> chargeReference.toString
          )
        )
      )
    )
}

trait DeclarationsRepository {

  val started: Future[Unit]
  def insert(data: JsObject, correlationId: String, sentToEtmp: Boolean): Future[Either[List[String], Declaration]]
  def insertAmendment(amendData: JsObject, correlationId: String, id: ChargeReference): Future[Declaration]
  def get(id: ChargeReference): Future[Option[Declaration]]
  def get(retrieveDeclarationRequest: PreviousDeclarationRequest): Future[Option[DeclarationResponse]]
  def remove(id: ChargeReference): Future[Option[Declaration]]
  def setState(id: ChargeReference, state: State): Future[Declaration]
  def setAmendState(id: ChargeReference, amendState: State): Future[Declaration]
  def setSentToEtmp(id: ChargeReference, sentToEtmp: Boolean): Future[Declaration]
  def setAmendSentToEtmp(id: ChargeReference, amendSentToEtmp: Boolean): Future[Declaration]
  def unpaidDeclarations: Source[Declaration, NotUsed]
  def unpaidAmendments: Source[Declaration, NotUsed]
  def paidDeclarationsForEtmp: Source[Declaration, NotUsed]
  def paidAmendmentsForEtmp: Source[Declaration, NotUsed]
  def paidDeclarationsForDeletion: Source[Declaration, NotUsed]
  def failedDeclarations: Source[Declaration, NotUsed]
  def failedAmendments: Source[Declaration, NotUsed]
  def metricsCount: Source[DeclarationsStatus, NotUsed]
}
