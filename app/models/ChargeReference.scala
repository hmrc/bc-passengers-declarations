package models

import play.api.libs.json.{JsString, Reads, Writes}
import play.api.mvc.PathBindable

final case class ChargeReference(value: String)

object ChargeReference {

  implicit lazy val reads: Reads[ChargeReference] = {

    import play.api.libs.json._

    __.read[String].map(ChargeReference.apply)
  }

  implicit lazy val writes: Writes[ChargeReference] =
    Writes {
      chargeReference =>
        JsString(chargeReference.value)
    }

  implicit lazy val pathBindable: PathBindable[ChargeReference] = {
    PathBindable.bindableString.transform(ChargeReference.apply, _.value)
  }
}
