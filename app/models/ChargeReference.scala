package models

import play.api.libs.json.Reads
import play.api.mvc.PathBindable

final case class ChargeReference(value: String)

object ChargeReference {

  implicit lazy val reads: Reads[ChargeReference] = {

    import play.api.libs.json._

    (__ \ "simpleDeclarationRequest" \ "requestDetail" \ "declarationHeader" \ "chargeReference")
      .read[String].map(ChargeReference.apply)
  }

  implicit lazy val pathBindable: PathBindable[ChargeReference] = {
    PathBindable.bindableString.transform(ChargeReference.apply, _.value)
  }
}
