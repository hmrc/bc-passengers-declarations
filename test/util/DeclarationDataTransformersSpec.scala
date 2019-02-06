package util

import models.ChargeReference
import models.declarations.State.{PendingPayment, SubmissionFailed}
import models.declarations.{Declaration, State}
import org.scalatest.{FreeSpec, MustMatchers}
import org.scalatestplus.play.OneAppPerSuite
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.{JsObject, Json}
import play.api.test.Injecting
import services.{ValidationService, Validator}

class DeclarationDataTransformersSpec extends FreeSpec with MustMatchers with GuiceOneAppPerSuite with Injecting {

  private lazy val validationService: ValidationService = inject[ValidationService]
  private lazy val validator: Validator = validationService.get("schema-v1-1-0.json")

  val declarationDataV115 = Json.parse(
    """
      | {
      |     "simpleDeclarationRequest": {
      |         "requestCommon": {
      |             "receiptDate": "2018-05-31T12:14:08Z",
      |             "acknowledgementReference": "123456789112340",
      |             "requestParameters": [{
      |                 "paramName": "REGIME",
      |                 "paramValue": "PNGR"
      |             }]
      |         },
      |         "requestDetail": {
      |             "customerReference": {
      |                 "passport": "123456789"
      |             },
      |             "personalDetails": {
      |                 "firstName": "Harry",
      |                 "lastName": "Potter"
      |             },
      |             "contactDetails": {},
      |             "declarationHeader": {
      |                 "portOfEntry": "Heathrow",
      |                 "expectedDateOfArrival": "2018-05-31",
      |                 "chargeReference": "12345678911234",
      |                 "timeOfEntry": "12:20"
      |             },
      |             "declarationTobacco": {
      |                 "totalExciseTobacco": "100.54",
      |                 "totalCustomsTobacco": "192.94",
      |                 "totalVATTobacco": "149.92",
      |                 "declarationItemTobacco": [{
      |                     "commodityDescription": "Cigarettes",
      |                     "quantity": "250",
      |                     "goodsValue": "400.00",
      |                     "valueCurrency": "USD",
      |                     "originCountry": "US",
      |                     "exchangeRate": "1.20",
      |                     "exchangeRateDate": "2018-10-29",
      |                     "goodsValueGBP": "304.11",
      |                     "VATRESClaimed": false,
      |                     "exciseGBP": "74.00",
      |                     "customsGBP": "79.06",
      |                     "vatGBP": "91.43"
      |                 }, {
      |                     "commodityDescription": "Rolling Tobacco",
      |                     "weight": "120.00",
      |                     "goodsValue": "200.00",
      |                     "valueCurrency": "USD",
      |                     "originCountry": "US",
      |                     "exchangeRate": "1.20",
      |                     "exchangeRateDate": "2018-10-29",
      |                     "goodsValueGBP": "152.05",
      |                     "VATRESClaimed": false,
      |                     "exciseGBP": "26.54",
      |                     "customsGBP": "113.88",
      |                     "vatGBP": "58.49"
      |                 }]
      |             },
      |             "declarationAlcohol": {
      |                 "totalExciseAlcohol": "2.00",
      |                 "totalCustomsAlcohol": "0.30",
      |                 "totalVATAlcohol": "18.70",
      |                 "declarationItemAlcohol": [{
      |                     "commodityDescription": "Cider",
      |                     "volume": "5",
      |                     "goodsValue": "120.00",
      |                     "valueCurrency": "USD",
      |                     "originCountry": "US",
      |                     "exchangeRate": "1.20",
      |                     "exchangeRateDate": "2018-10-29",
      |                     "goodsValueGBP": "91.23",
      |                     "VATRESClaimed": false,
      |                     "exciseGBP": "2.00",
      |                     "customsGBP": "0.30",
      |                     "vatGBP": "18.70"
      |                 }]
      |             },
      |             "declarationOther": {
      |                 "totalExciseOther": "0.00",
      |                 "totalCustomsOther": "341.65",
      |                 "totalVATOther": "556.41",
      |                 "declarationItemOther": [{
      |                     "commodityDescription": "Televisions",
      |                     "quantity": "1",
      |                     "goodsValue": "1500.00",
      |                     "valueCurrency": "USD",
      |                     "originCountry": "US",
      |                     "exchangeRate": "1.20",
      |                     "exchangeRateDate": "2018-10-29",
      |                     "goodsValueGBP": "1140.42",
      |                     "VATRESClaimed": false,
      |                     "exciseGBP": "0.00",
      |                     "customsGBP": "159.65",
      |                     "vatGBP": "260.01"
      |                 }, {
      |                     "commodityDescription": "Televisions",
      |                     "quantity": "1",
      |                     "goodsValue": "1300.00",
      |                     "valueCurrency": "GBP",
      |                     "originCountry": "GB",
      |                     "exchangeRate": "1.20",
      |                     "exchangeRateDate": "2018-10-29",
      |                     "goodsValueGBP": "1300.00",
      |                     "VATRESClaimed": false,
      |                     "exciseGBP": "0.00",
      |                     "customsGBP": "182.00",
      |                     "vatGBP": "296.40"
      |                 }]
      |             },
      |             "liabilityDetails": {
      |                 "totalExciseGBP": "102.54",
      |                 "totalCustomsGBP": "534.89",
      |                 "totalVATGBP": "725.03",
      |                 "grandTotalGBP": "1362.46"
      |             }
      |         }
      |     }
      | }
    """.stripMargin).as[JsObject]

  val expectedDeclarationData = Json.parse(
    """
      |{
      |    "simpleDeclarationRequest": {
      |        "requestCommon": {
      |            "receiptDate": "2018-05-31T12:14:08Z",
      |            "acknowledgementReference": "123456789112340",
      |            "requestParameters": [{
      |                "paramName": "REGIME",
      |                "paramValue": "PNGR"
      |            }]
      |        },
      |        "requestDetail": {
      |            "customerReference": {
      |                "passport": "123456789"
      |            },
      |            "personalDetails": {
      |                "firstName": "Harry",
      |                "lastName": "Potter"
      |            },
      |            "contactDetails": {},
      |            "declarationTobacco": {
      |                "totalExciseTobacco": "100.54",
      |                "totalCustomsTobacco": "192.94",
      |                "totalVATTobacco": "149.92",
      |                "declarationItemTobacco": [{
      |                    "commodityDescription": "Cigarettes",
      |                    "quantity": "250",
      |                    "goodsValue": "400.00",
      |                    "valueCurrency": "USD",
      |                    "originCountry": "US",
      |                    "exchangeRate": "1.20",
      |                    "exchangeRateDate": "2018-10-29",
      |                    "customsValueGBP": "304.11",
      |                    "VATRESClaimed": false,
      |                    "exciseGBP": "74.00",
      |                    "customsGBP": "79.06",
      |                    "vatGBP": "91.43"
      |                }, {
      |                    "commodityDescription": "Rolling Tobacco",
      |                    "weight": "120.00",
      |                    "goodsValue": "200.00",
      |                    "valueCurrency": "USD",
      |                    "originCountry": "US",
      |                    "exchangeRate": "1.20",
      |                    "exchangeRateDate": "2018-10-29",
      |                    "customsValueGBP": "152.05",
      |                    "VATRESClaimed": false,
      |                    "exciseGBP": "26.54",
      |                    "customsGBP": "113.88",
      |                    "vatGBP": "58.49"
      |                }]
      |            },
      |            "declarationAlcohol": {
      |                "totalExciseAlcohol": "2.00",
      |                "totalCustomsAlcohol": "0.30",
      |                "totalVATAlcohol": "18.70",
      |                "declarationItemAlcohol": [{
      |                    "commodityDescription": "Cider",
      |                    "volume": "5",
      |                    "goodsValue": "120.00",
      |                    "valueCurrency": "USD",
      |                    "originCountry": "US",
      |                    "exchangeRate": "1.20",
      |                    "exchangeRateDate": "2018-10-29",
      |                    "customsValueGBP": "91.23",
      |                    "VATRESClaimed": false,
      |                    "exciseGBP": "2.00",
      |                    "customsGBP": "0.30",
      |                    "vatGBP": "18.70"
      |                }]
      |            },
      |            "declarationOther": {
      |                "totalExciseOther": "0.00",
      |                "totalCustomsOther": "341.65",
      |                "totalVATOther": "556.41",
      |                "declarationItemOther": [{
      |                    "commodityDescription": "Televisions",
      |                    "quantity": "1",
      |                    "goodsValue": "1500.00",
      |                    "valueCurrency": "USD",
      |                    "originCountry": "US",
      |                    "exchangeRate": "1.20",
      |                    "exchangeRateDate": "2018-10-29",
      |                    "customsValueGBP": "1140.42",
      |                    "VATRESClaimed": false,
      |                    "exciseGBP": "0.00",
      |                    "customsGBP": "159.65",
      |                    "vatGBP": "260.01"
      |                }, {
      |                    "commodityDescription": "Televisions",
      |                    "quantity": "1",
      |                    "goodsValue": "1300.00",
      |                    "valueCurrency": "GBP",
      |                    "originCountry": "GB",
      |                    "exchangeRate": "1.20",
      |                    "exchangeRateDate": "2018-10-29",
      |                    "customsValueGBP": "1300.00",
      |                    "VATRESClaimed": false,
      |                    "exciseGBP": "0.00",
      |                    "customsGBP": "182.00",
      |                    "vatGBP": "296.40"
      |                }]
      |            },
      |            "liabilityDetails": {
      |                "totalExciseGBP": "102.54",
      |                "totalCustomsGBP": "534.89",
      |                "totalVATGBP": "725.03",
      |                "grandTotalGBP": "1362.46"
      |            },
      |            "declarationHeader": {
      |                "chargeReference": "12345678911234",
      |                "portOfEntry": "Heathrow",
      |                "expectedDateOfArrival": "2018-05-31"
      |            }
      |        }
      |    }
      |}
    """.stripMargin).as[JsObject]

  private lazy val declarationTransformer: DeclarationDataTransformers = inject[DeclarationDataTransformers]

  "declarationToV110" - {

    "must return None if the supplied declaration does not match the MDG schema version 1.1.5" in {

      val declaration = Declaration(ChargeReference(1000011), PendingPayment, "X01", Json.obj("foo" -> "bar"))

      declarationTransformer.declarationToV110(declaration) mustNot be(defined)
    }

    "must transform declaration data into the expected format for MDG schema version 1.1.0" in {

      val declaration = Declaration(ChargeReference(1000011), PendingPayment, "X01", declarationDataV115)

      val transformedDeclarationData = declarationTransformer.declarationToV110(declaration).get.data

      transformedDeclarationData mustEqual expectedDeclarationData

      validator.validate(transformedDeclarationData) mustEqual Nil
    }
  }
}
