package repositories

import com.google.inject.{Inject, Singleton}
import models.ChargeReference
import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.json.{Format, JsValue, Json}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.commands.WriteResult
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.collection.JSONBatchCommands.FindAndModifyCommand
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.{ExecutionContext, Future}

object DeclarationsRepository {

  final case class Data(id: String, data: JsValue, timestamp: DateTime = DateTime.now(DateTimeZone.UTC))

  object Data {

    implicit val formats: Format[Data] = ReactiveMongoFormats.mongoEntity {
      implicit val dateTimeFormat: Format[DateTime] = ReactiveMongoFormats.dateTimeFormats
      Json.format[Data]
    }
  }
}

@Singleton
class DeclarationsRepository @Inject() (
                                          component: ReactiveMongoComponent
                                       )(implicit ec: ExecutionContext) extends ReactiveRepository[DeclarationsRepository.Data, BSONObjectID](
                                          collectionName = "",
                                          mongo = component.mongoConnector.db,
                                          domainFormat = DeclarationsRepository.Data.formats
                                        ) {

  import DeclarationsRepository.Data
  import reactivemongo.play.json.ImplicitBSONHandlers.JsObjectDocumentWriter

  private val index = Index(
    key     = Seq("lastUpdated" -> IndexType.Ascending),
    name    = Some("declarations-index")
//    options = BSONDocument("expireAfterSeconds" -> cacheTtl)
  )

  collection.indexesManager.ensure(index)

  def insert(id: ChargeReference, data: JsValue): Future[WriteResult] =
    insert(Data(id.value, data))

  def get(id: ChargeReference): Future[Option[JsValue]] =
    collection.find(Json.obj("id" -> id.value), None)
      .one[Data]
      .map(_.map(_.data))

  def remove(id: ChargeReference): Future[FindAndModifyCommand.FindAndModifyResult] =
    collection.findAndRemove(Json.obj("id" -> id.value))
}
