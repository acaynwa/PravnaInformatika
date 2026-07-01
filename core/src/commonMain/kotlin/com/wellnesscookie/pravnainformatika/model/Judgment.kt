package com.wellnesscookie.pravnainformatika.model

import kotlinx.serialization.Serializable

/** Mirror of the `input` payload the original JS routes receive. */
@Serializable
data class JudgmentInput(
    val brojPredmeta: String = "",
    val sud: String = "Podgorici",
    val sudija: String = "",
    val zapisnicar: String = "",
    val okrivljeni: String = "",
    val datumPresude: String = "",
    val tipKrivicnogDjela: String = "",
    val clanKZ: String = "",
    val opis: String = "",
    val ranijeOsudjivan: String = "ne",
    val saizvrsilastvo: String = "ne",
    val priznanje: String = "ne",
    val zaposlenost: String = "nepoznat",
    val obrazovanje: String = "nepoznat",
    val bracniStatus: String = "nepoznat",
    val zabranjenoSredstvo: String = "ne",
    val lovostajIliZabranjeneVode: String = "ne",
    val velikaKolicina: String = "ne",
    val elektricnaStruja: String = "ne",
    val agregat: String = "ne",
    val sonda: String = "ne",
    val pretvarac: String = "ne",
    val prisutanUlov: String = "ne",
    val kolicinaUlovaKg: Double = 0.0,
    val oduzimanjePredmeta: String = "ne",
    // Hunting-specific (čl. 325). lovostajIliZabranjeneVode is reused for
    // hunting (lovostaj / zabranjeno područje).
    val tudjeLoviste: String = "ne",
    val krupnaDivljac: String = "ne",
    val zasticenaVrsta: String = "ne",
    val bezPosebneDozvole: String = "ne",
    val masovnoUnistavanje: String = "ne",
    val vatrenoOruzje: String = "ne",
    val lovackiPsi: Int = 0,
    val bezOruznogLista: String = "ne",
    val bezDozvoleZaLov: String = "ne",
    val odstreljenaDivljac: String = "ne",
    val vrstaDivljaci: String = "",
    val brojGrlaDivljaci: Int = 0,
    val sticajDrugogDela: String = "ne",
    val brojOkrivljenih: Int = 1,
    val brojSvjedoka: Int = 0,
    val brojDokaza: Int = 0,
    val svjedoci: String = "",
    val dokazi: String = "",
)

@Serializable
data class JudgmentDecisionRequest(
    val vrstaPresude: String? = null,
    val kaznaZatvoraMjeseci: Double? = null,
    val kaznaZatvoraDani: Int? = null,
    val radUJavnomInteresuCasovi: Int? = null,
    val rokProvjereGodine: Int? = null,
    val troskoviEur: Double? = null,
    val uslovnaOsuda: String? = null,
)

@Serializable
data class JudgmentRuleReasoning(
    val verdict: String = "",
    val recommendation: String = "",
    val actual_sentence: String = "",
    val acquittal: Boolean = false,
    val articles: List<String> = emptyList(),
    val mitigating_factors: List<String> = emptyList(),
    val aggravating_factors: List<String> = emptyList(),
)

@Serializable
data class JudgmentRequest(
    val input: JudgmentInput = JudgmentInput(),
    val ruleReasoning: JudgmentRuleReasoning? = null,
    val cbrReasoning: CbrResult? = null,
    val decisionOverride: JudgmentDecisionRequest? = null,
    val previewOnly: Boolean = true,
)

@Serializable
data class JudgmentDecision(
    val vrstaPresude: String = "osudjujuca",
    val kaznaZatvoraMjeseci: Int = 0,
    val kaznaZatvoraDani: Int = 0,
    val radUJavnomInteresuCasovi: Int = 0,
    val uslovnaOsuda: String = "Ne",
)

@Serializable
data class JudgmentSections(
    val introduction: String = "",
    val background: String = "",
    val motivation: String = "",
    val decision: String = "",
    val reasoningSummary: String = "",
    val generatorLabel: String = "template-fallback",
)

@Serializable
data class JudgmentResult(
    val status: String = "success",
    val xmlFile: String? = null,
    val caseId: String = "",
    val decision: JudgmentDecision = JudgmentDecision(),
    val sections: JudgmentSections = JudgmentSections(),
    val xml: String = "",
)

@Serializable
data class GenerateDescriptionRequest(
    val input: JudgmentInput = JudgmentInput(),
)

@Serializable
data class GenerateDescriptionResponse(
    val description: String,
)
