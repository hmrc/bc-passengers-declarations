package repositories

import com.google.inject.{Inject, Singleton}
import models.{ChargeReference, Declaration}
import play.api.libs.json.{JsObject, JsValue, Json}
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.play.json.ImplicitBSONHandlers.JsObjectDocumentWriter
import reactivemongo.play.json.collection.JSONCollection
import services.ChargeReferenceService

import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions

@Singleton
class DefaultDeclarationsRepository @Inject() (
                                                mongo: ReactiveMongoApi,
                                                chargeReferenceService: ChargeReferenceService
                                              )(implicit ec: ExecutionContext) extends DeclarationsRepository {

  private val collectionName: String = "declarations"

  private def collection: Future[JSONCollection] =
    mongo.database.map(_.collection[JSONCollection](collectionName))

  private val index = Index(
    key     = Seq("lastUpdated" -> IndexType.Ascending),
    name    = Some("declarations-index")
  )

  val started: Future[_] = {
    collection.flatMap {
      _.indexesManager.ensure(index)
    }
  }

  override def insert(data: JsObject): Future[ChargeReference] = {

    for {
      id <- chargeReferenceService.nextChargeReference()
      _  <- collection.flatMap(_.insert(Declaration(id, data ++ id)))
    } yield id
  }

  override def get(id: ChargeReference): Future[Option[Declaration]] =
    collection.flatMap(_.find(Json.obj("_id" -> id.value), None).one[Declaration])

  override def remove(id: ChargeReference): Future[Option[Declaration]] =
    collection.flatMap(_.findAndRemove(Json.obj("_id" -> id.value)).map(_.result[Declaration]))

  private implicit def toJson(chargeReference: ChargeReference): JsObject = {
    Json.obj(
      "simpleDeclarationRequest" -> Json.obj(
        "requestDetail" -> Json.obj(
          "declarationHeader" -> Json.obj(
            "chargeReference" -> chargeReference.value
          )
        )
      )
    )
  }
}

trait DeclarationsRepository {

  val started: Future[_]
  def insert(data: JsObject): Future[ChargeReference]
  def get(id: ChargeReference): Future[Option[Declaration]]
  def remove(id: ChargeReference): Future[Option[Declaration]]
}
