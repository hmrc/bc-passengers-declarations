package models.declarations

import java.time.LocalDateTime

import models.ChargeReference
import play.api.libs.json._

final case class Declaration (
                               chargeReference: ChargeReference,
                               state: State,
                               data: JsObject,
                               lastUpdated: LocalDateTime = LocalDateTime.now
                             )

object Declaration {

  implicit lazy val reads: Reads[Declaration] = {

    import play.api.libs.functional.syntax._

    (
      (__ \ "_id").read[ChargeReference] and
      (__ \ "state").read[State] and
      (__ \ "data").read[JsObject] and
      (__ \ "lastUpdated").read[LocalDateTime]
    )(Declaration.apply _)
  }

  implicit lazy val writes: OWrites[Declaration] = {

    import play.api.libs.functional.syntax._

    (
      (__ \ "_id").write[ChargeReference] and
      (__ \ "state").write[State] and
      (__ \ "data").write[JsObject] and
      (__ \ "lastUpdated").write[LocalDateTime]
    )(unlift(Declaration.unapply))
  }
}