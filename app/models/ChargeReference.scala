package models

import play.api.libs.json.{Reads, Writes}
import play.api.mvc.PathBindable

final case class ChargeReference(value: String)

object ChargeReference {

  implicit lazy val reads: Reads[ChargeReference] = {

    import play.api.libs.json._

    (__ \ "chargeReference")
      .read[String]
      .map(ChargeReference.apply)
  }

  implicit lazy val writes: Writes[ChargeReference] = {

    import play.api.libs.functional.syntax._
    import play.api.libs.json._

    (__ \ "chargeReference")
      .write[String]
      .contramap[ChargeReference](_.value)
  }

  implicit lazy val pathBindable: PathBindable[ChargeReference] = {
    PathBindable.bindableString.transform(ChargeReference.apply, _.value)
  }
}
