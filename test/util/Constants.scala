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

package util

import models.ChargeReference
import models.declarations.{Declaration, State}
import play.api.libs.json.{JsObject, Json}

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import scala.util.Random.nextInt

trait Constants {
  val chargeReferenceNumber: Int               = 1234567890
  val chargeReference: ChargeReference         = ChargeReference(chargeReferenceNumber)
  def randomChargeReference(): ChargeReference = ChargeReference(nextInt(chargeReferenceNumber))
  val correlationId: String                    = "fe28db96-d9db-4220-9e12-f2d267267c29"
  val lastUpdated: LocalDateTime               = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS)

  val emailAddress = "testEmail@digital.hmrc.gov.uk"

  val userInformation: JsObject = Json.obj(
    "firstName"            -> "Harry",
    "lastName"             -> "Potter",
    "identificationType"   -> "passport",
    "identificationNumber" -> "SX12345",
    "emailAddress"         -> emailAddress,
    "selectPlaceOfArrival" -> "LHR",
    "enterPlaceOfArrival"  -> "Heathrow Airport",
    "dateOfArrival"        -> "2018-05-31",
    "timeOfArrival"        -> "13:20:00.000"
  )

  val declarationAlcohol: JsObject = Json.obj(
    "totalExciseAlcohol"     -> "2.00",
    "totalCustomsAlcohol"    -> "0.30",
    "totalVATAlcohol"        -> "18.70",
    "declarationItemAlcohol" -> Json.arr(
      Json.obj(
        "commodityDescription" -> "Cider",
        "volume"               -> "5",
        "goodsValue"           -> "120.00",
        "valueCurrency"        -> "USD",
        "valueCurrencyName"    -> "USA dollars (USD)",
        "originCountry"        -> "US",
        "originCountryName"    -> "United States of America",
        "exchangeRate"         -> "1.20",
        "exchangeRateDate"     -> "2018-10-29",
        "goodsValueGBP"        -> "91.23",
        "VATRESClaimed"        -> false,
        "exciseGBP"            -> "2.00",
        "customsGBP"           -> "0.30",
        "vatGBP"               -> "18.70"
      ),
      Json.obj(
        "commodityDescription" -> "Beer",
        "volume"               -> "110",
        "goodsValue"           -> "500.00",
        "valueCurrency"        -> "GBP",
        "valueCurrencyName"    -> "British pounds (GBP)",
        "originCountry"        -> "FR",
        "originCountryName"    -> "France",
        "exchangeRate"         -> "1.00",
        "exchangeRateDate"     -> "2020-12-29",
        "goodsValueGBP"        -> "500.00",
        "VATRESClaimed"        -> false,
        "exciseGBP"            -> "88.00",
        "customsGBP"           -> "0.00",
        "vatGBP"               -> "117.60"
      )
    )
  )

  val declarationTobacco: JsObject = Json.obj(
    "totalExciseTobacco"     -> "100.54",
    "totalCustomsTobacco"    -> "192.94",
    "totalVATTobacco"        -> "149.92",
    "declarationItemTobacco" -> Json.arr(
      Json.obj(
        "commodityDescription" -> "Cigarettes",
        "quantity"             -> "250",
        "goodsValue"           -> "400.00",
        "valueCurrency"        -> "USD",
        "valueCurrencyName"    -> "USA dollars (USD)",
        "originCountry"        -> "US",
        "originCountryName"    -> "United States of America",
        "exchangeRate"         -> "1.20",
        "exchangeRateDate"     -> "2018-10-29",
        "goodsValueGBP"        -> "304.11",
        "VATRESClaimed"        -> false,
        "exciseGBP"            -> "74.00",
        "customsGBP"           -> "79.06",
        "vatGBP"               -> "91.43"
      )
    )
  )

  val declarationOther: JsObject = Json.obj(
    "totalExciseOther"     -> "0.00",
    "totalCustomsOther"    -> "341.65",
    "totalVATOther"        -> "556.41",
    "declarationItemOther" -> Json.arr(
      Json.obj(
        "commodityDescription" -> "Television",
        "quantity"             -> "1",
        "goodsValue"           -> "1500.00",
        "valueCurrency"        -> "USD",
        "valueCurrencyName"    -> "USA dollars (USD)",
        "originCountry"        -> "US",
        "originCountryName"    -> "United States of America",
        "exchangeRate"         -> "1.20",
        "exchangeRateDate"     -> "2018-10-29",
        "goodsValueGBP"        -> "1140.42",
        "VATRESClaimed"        -> false,
        "exciseGBP"            -> "0.00",
        "customsGBP"           -> "159.65",
        "vatGBP"               -> "260.01"
      )
    )
  )

  val liabilityDetails: JsObject = Json.obj(
    "totalExciseGBP"  -> "102.54",
    "totalCustomsGBP" -> "534.89",
    "totalVATGBP"     -> "725.03",
    "grandTotalGBP"   -> "1362.46"
  )

  val declarationData: JsObject = Json.obj(
    "simpleDeclarationRequest" -> Json.obj(
      "requestCommon" -> Json.obj(
        "receiptDate"              -> "2020-12-29T14:00:08Z",
        "requestParameters"        -> Json.arr(
          Json.obj(
            "paramName"  -> "REGIME",
            "paramValue" -> "PNGR"
          )
        ),
        "acknowledgementReference" -> (chargeReference.toString + "0")
      ),
      "requestDetail" -> Json.obj(
        "declarationAlcohol" -> declarationAlcohol,
        "liabilityDetails"   -> liabilityDetails,
        "customerReference"  -> Json.obj("idType" -> "passport", "idValue" -> "SX12345", "ukResident" -> false),
        "personalDetails"    -> Json
          .obj("firstName" -> userInformation("firstName"), "lastName" -> userInformation("lastName")),
        "declarationTobacco" -> declarationTobacco,
        "declarationHeader"  -> Json.obj(
          "travellingFrom"        -> "NON_EU Only",
          "expectedDateOfArrival" -> "2018-05-31",
          "ukVATPaid"             -> false,
          "uccRelief"             -> false,
          "portOfEntryName"       -> userInformation("enterPlaceOfArrival"),
          "ukExcisePaid"          -> false,
          "chargeReference"       -> chargeReference.toString,
          "portOfEntry"           -> "LHR",
          "timeOfEntry"           -> "13:20",
          "onwardTravelGBNI"      -> "GB",
          "messageTypes"          -> Json.obj("messageType" -> "DeclarationCreate")
        ),
        "contactDetails"     -> Json.obj("emailAddress" -> emailAddress),
        "declarationOther"   -> declarationOther
      )
    )
  )

  val zeroPoundsData: JsObject = declarationData.deepMerge(
    Json.obj(
      "simpleDeclarationRequest" -> Json.obj(
        "requestDetail" -> Json.obj(
          "liabilityDetails" -> Json.obj(
            "totalExciseGBP"  -> "0.00",
            "totalCustomsGBP" -> "0.00",
            "totalVATGBP"     -> "0.00",
            "grandTotalGBP"   -> "0.00"
          )
        )
      )
    )
  )

  val amendmentData: JsObject = declarationData.deepMerge(
    Json.obj(
      "simpleDeclarationRequest" -> Json.obj(
        "requestDetail" -> Json.obj(
          "declarationHeader"         -> Json.obj(
            "messageTypes" -> Json.obj("messageType" -> "DeclarationAmend")
          ),
          "amendmentLiabilityDetails" -> Json.obj(
            "additionalExciseGBP"  -> "102.54",
            "additionalCustomsGBP" -> "534.89",
            "additionalVATGBP"     -> "725.03",
            "additionalTotalGBP"   -> "1362.46"
          )
        )
      )
    )
  )

  val journeyData: JsObject = Json.obj(
    "euCountryCheck"            -> "greatBritain",
    "arrivingNICheck"           -> true,
    "isUKResident"              -> false,
    "bringingOverAllowance"     -> true,
    "privateCraft"              -> true,
    "ageOver17"                 -> true,
    "userInformation"           -> userInformation,
    "purchasedProductInstances" -> Json.arr(
      Json.obj(
        "path"         -> "other-goods/adult/adult-clothing",
        "iid"          -> "UCLFeP",
        "country"      -> Json.obj(
          "code"            -> "IN",
          "countryName"     -> "title.india",
          "alphaTwoCode"    -> "IN",
          "isEu"            -> false,
          "isCountry"       -> true,
          "countrySynonyms" -> Json.arr()
        ),
        "currency"     -> "GBP",
        "cost"         -> 500,
        "isVatPaid"    -> false,
        "isCustomPaid" -> false,
        "isUccRelief"  -> false
      )
    ),
    "calculatorResponse"        -> Json.obj(
      "calculation" -> Json.obj(
        "excise"  -> "0.00",
        "customs" -> "12.50",
        "vat"     -> "102.50",
        "allTax"  -> "115.00"
      )
    )
  )

  val declaration: Declaration = Declaration(
    chargeReference,
    State.PendingPayment,
    None,
    sentToEtmp = false,
    None,
    correlationId,
    None,
    journeyData,
    declarationData
  )

  val amendment: Declaration = Declaration(
    chargeReference,
    State.Paid,
    Some(State.PendingPayment),
    sentToEtmp = false,
    amendSentToEtmp = Some(false),
    correlationId,
    Some(correlationId),
    journeyData,
    declarationData,
    Some(amendmentData)
  )

}
