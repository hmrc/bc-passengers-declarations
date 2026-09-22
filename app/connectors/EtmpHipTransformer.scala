/*
 * Copyright 2026 HM Revenue & Customs
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

package connectors

import models.declarations.*

object EtmpHipTransformer {

  def transform(etmp: Etmp): EtmpHip = {
    val detail = etmp.simpleDeclarationRequest.requestDetail

    EtmpHip(
      customerReference = HipCustomerReference(
        idType = detail.customerReference.idType,
        idValue = detail.customerReference.idValue,
        ukResident = detail.customerReference.ukResident
      ),
      personalDetails = detail.personalDetails.map(p => HipPersonalDetails(p.firstName, p.lastName)),
      contactDetails = Some(HipContactDetails(detail.contactDetails.emailAddress)),
      declarationHeader = transformHeader(detail.declarationHeader),
      declarationTobacco = detail.declarationTobacco.map(transformTobacco),
      declarationAlcohol = detail.declarationAlcohol.map(transformAlcohol),
      declarationVaping = detail.declarationVaping.map(transformVaping),
      declarationOther = detail.declarationOther.map(transformOther),
      liabilityDetails = transformLiability(detail.liabilityDetails),
      amendmentLiabilityDetails = detail.amendmentLiabilityDetails.map(transformAmendmentLiability)
    )
  }

  private def transformHeader(header: DeclarationHeader): HipDeclarationHeader =
    HipDeclarationHeader(
      chargeReference = header.chargeReference,
      portOfEntry = header.portOfEntry,
      expectedDateOfTravel = header.expectedDateOfArrival,
      timeOfEntry = header.timeOfEntry,
      travellingFrom = header.travellingFrom,
      onwardTravel = header.onwardTravelGBNI
    )

  private def num(value: Option[String]): Option[BigDecimal] = value.map(BigDecimal(_))

  private def transformTobacco(tobacco: DeclarationTobacco): HipDeclarationTobacco =
    HipDeclarationTobacco(
      declItemTobacco = tobacco.declarationItemTobacco.map(_.map(transformTobaccoItem)),
      totalCustomsGbp = num(tobacco.totalCustomsTobacco),
      totalExciseGbp = num(tobacco.totalExciseTobacco),
      totalVatGbp = num(tobacco.totalVATTobacco)
    )

  private def transformTobaccoItem(item: DeclarationItemTobacco): HipDeclarationItemTobacco =
    HipDeclarationItemTobacco(
      commodityDesc = item.commodityDescription,
      quantity = item.quantity,
      weight = item.weight,
      goodsValue = num(item.goodsValue),
      valueCurrency = item.valueCurrency,
      originCountry = item.originCountry,
      exchangeRate = num(item.exchangeRate),
      exchangeRateDate = item.exchangeRateDate,
      goodsValueGbp = num(item.goodsValueGBP),
      vatResClaimed = item.VATRESClaimed,
      exciseGbp = num(item.exciseGBP),
      customsGbp = num(item.customsGBP),
      vatGbp = num(item.vatGBP),
      ukVatPaid = item.ukVATPaid,
      ukExcisePaid = item.ukExcisePaid,
      euCustomsRelief = item.euCustomsRelief,
      madeIn = item.madeIn
    )

  private def transformAlcohol(alcohol: DeclarationAlcohol): HipDeclarationAlcohol =
    HipDeclarationAlcohol(
      declItemAlcohol = alcohol.declarationItemAlcohol.map(_.map(transformAlcoholItem)),
      totalExciseGbp = num(alcohol.totalExciseAlcohol),
      totalCustomsGbp = num(alcohol.totalCustomsAlcohol),
      totalVatGbp = num(alcohol.totalVATAlcohol)
    )

  private def transformAlcoholItem(item: DeclarationItemAlcohol): HipDeclarationItemAlcohol =
    HipDeclarationItemAlcohol(
      commodityDesc = item.commodityDescription,
      volume = item.volume,
      goodsValue = num(item.goodsValue),
      valueCurrency = item.valueCurrency,
      originCountry = item.originCountry,
      exchangeRate = num(item.exchangeRate),
      exchangeRateDate = item.exchangeRateDate,
      goodsValueGbp = num(item.goodsValueGBP),
      vatResClaimed = item.VATRESClaimed,
      exciseGbp = num(item.exciseGBP),
      customsGbp = num(item.customsGBP),
      vatGbp = num(item.vatGBP),
      ukVatPaid = item.ukVATPaid,
      ukExcisePaid = item.ukExcisePaid,
      euCustomsRelief = item.euCustomsRelief,
      madeIn = item.madeIn
    )

  private def transformVaping(vaping: DeclarationVaping): HipDeclarationVaping =
    HipDeclarationVaping(
      declItemVaping = vaping.declarationItemVaping.map(_.map(transformVapingItem)),
      totalExciseGbp = num(vaping.totalExciseVaping),
      totalCustomsGbp = num(vaping.totalCustomsVaping),
      totalVatGbp = num(vaping.totalVATVaping)
    )

  private def transformVapingItem(item: DeclarationItemVaping): HipDeclarationItemVaping =
    HipDeclarationItemVaping(
      commodityDesc = item.commodityDescription,
      volume = item.volume,
      goodsValue = num(item.goodsValue),
      valueCurrency = item.valueCurrency,
      originCountry = item.originCountry,
      exchangeRate = num(item.exchangeRate),
      exchangeRateDate = item.exchangeRateDate,
      goodsValueGbp = num(item.goodsValueGBP),
      vatResClaimed = item.VATRESClaimed,
      exciseGbp = num(item.exciseGBP),
      customsGbp = num(item.customsGBP),
      vatGbp = num(item.vatGBP),
      ukVatPaid = item.ukVATPaid,
      ukExcisePaid = item.ukExcisePaid,
      euCustomsRelief = item.euCustomsRelief,
      madeIn = item.madeIn
    )

  private def transformOther(other: DeclarationOther): HipDeclarationOther =
    HipDeclarationOther(
      declItemOther = other.declarationItemOther.map(_.map(transformOtherItem)),
      totalCustomsGbp = num(other.totalCustomsOther),
      totalExciseGbp = num(other.totalExciseOther),
      totalVatGbp = num(other.totalVATOther)
    )

  private def transformOtherItem(item: DeclarationItemOther): HipDeclarationItemOther =
    HipDeclarationItemOther(
      commodityDesc = item.commodityDescription,
      quantity = item.quantity,
      goodsValue = num(item.goodsValue),
      valueCurrency = item.valueCurrency,
      originCountry = item.originCountry,
      exchangeRate = num(item.exchangeRate),
      exchangeRateDate = item.exchangeRateDate,
      goodsValueGbp = num(item.goodsValueGBP),
      vatResClaimed = item.VATRESClaimed,
      exciseGbp = num(item.exciseGBP),
      customsGbp = num(item.customsGBP),
      vatGbp = num(item.vatGBP),
      uccRelief = item.uccRelief,
      ukVatPaid = item.ukVATPaid,
      euCustomsRelief = item.euCustomsRelief,
      madeIn = item.madeIn
    )

  private def transformLiability(liability: LiabilityDetails): HipLiabilityDetails =
    HipLiabilityDetails(
      totalExciseGbp = num(liability.totalExciseGBP),
      totalCustomsGbp = num(liability.totalCustomsGBP),
      totalVatGbp = num(liability.totalVATGBP),
      grandTotalGbp = BigDecimal(liability.grandTotalGBP)
    )

  private def transformAmendmentLiability(liability: AmendmentLiabilityDetails): HipAmendmentLiabilityDetails =
    HipAmendmentLiabilityDetails(
      additionalExciseGbp = num(liability.additionalExciseGBP),
      additionalCustomsGbp = num(liability.additionalCustomsGBP),
      additionalVatGbp = num(liability.additionalVATGBP),
      additionalTotalGbp = num(liability.additionalTotalGBP)
    )
}
