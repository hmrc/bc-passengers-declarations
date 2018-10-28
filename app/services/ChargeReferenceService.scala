package services

import com.google.inject.{Inject, Singleton}
import models.ChargeReference
import play.api.Configuration
import play.api.libs.json.{Json, Reads}
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.commands.LastError
import reactivemongo.play.json.ImplicitBSONHandlers.JsObjectDocumentWriter
import reactivemongo.play.json.collection.JSONCollection

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SequentialChargeReferenceService @Inject() (
                                                   config: Configuration,
                                                   mongo: ReactiveMongoApi
                                                 )(implicit ec: ExecutionContext) extends ChargeReferenceService {

  private implicit lazy val chargeReferenceReads: Reads[ChargeReference] = {

    import play.api.libs.json._

    (__ \ "chargeReference").read[Int].map {
      int =>
        ChargeReference(int.toString)
    }
  }

  private val collectionName: String = config.get[String]("mongodb.collections.charge-reference")
  private val id: String = "counter"

  private def collection: Future[JSONCollection] =
    mongo.database.map(_.collection[JSONCollection](collectionName))

  val started: Future[_] = {

    lazy val documentExistsErrorCode = Some(11000)

    val document = Json.obj(
      "_id"   -> id,
      "chargeReference" -> 0
    )

    collection.flatMap {
      _.insert(document)
        .map(_ => ())
    } recover {
      case e: LastError if e.code == documentExistsErrorCode =>
        Future.successful(())
    }
  }

  override def nextChargeReference(): Future[ChargeReference] = {

    val selector = Json.obj(
      "_id" -> id
    )

    val update = Json.obj(
      "$inc" -> Json.obj(
        "chargeReference" -> 1
      )
    )

    collection.flatMap {
      _.findAndUpdate(selector, update)
        .map {
          _.result[ChargeReference]
            .getOrElse(throw new Exception("unable to generate charge reference"))
        }
    }
  }
}

trait ChargeReferenceService {

  val started: Future[_]
  def nextChargeReference(): Future[ChargeReference]
}
