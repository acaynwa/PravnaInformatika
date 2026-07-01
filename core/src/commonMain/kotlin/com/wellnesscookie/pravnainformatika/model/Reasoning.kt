package com.wellnesscookie.pravnainformatika.model

import kotlinx.serialization.Serializable

@Serializable
data class ReasoningRequest(
    val caseId: String? = null,
    val facts: Map<String, String> = emptyMap(),
)

@Serializable
data class PenaltyRange(
    val min: String = "N/A",
    val max: String = "N/A",
)

@Serializable
data class ReasoningResult(
    val rawOutput: String = "",
    val verdict: String? = null,
    val rationale: List<String> = emptyList(),
    val recommendation: String = "",
    val appliedArticles: List<String> = emptyList(),
    val violatedRules: List<String> = emptyList(),
    val mitigatingFactors: List<String> = emptyList(),
    val aggravatingFactors: List<String> = emptyList(),
    val penaltyRange: PenaltyRange = PenaltyRange(),
    val acquittal: Boolean = false,
    val actualSentence: String = "",
)

@Serializable
data class CbrRequest(
    val caseId: String? = null,
    val query: Map<String, String> = emptyMap(),
)

@Serializable
data class CbrSimilarCase(
    val brojPredmeta: String = "",
    val sud: String = "",
    val tipKrivicnogDjela: String = "",
    val clanKZ: String = "",
    val vrstaPresude: String = "",
    val kaznaZatvoraMjeseci: Double = 0.0,
    val kaznaZatvoraDani: Int = 0,
    val radUJavnomInteresuCasovi: Int = 0,
    val rokProvjereGodine: Int = 0,
    val uslovnaOsuda: String = "",
    val troskoviEur: Double = 0.0,
    val saizvrsilastvo: String = "",
    val zabranjenoSredstvo: String = "",
    val lovostajIliZabranjeneVode: String = "",
    val velikaKolicina: String = "",
    val elektricnaStruja: String = "",
    val agregat: String = "",
    val sonda: String = "",
    val pretvarac: String = "",
    val prisutanUlov: String = "",
    val kolicinaUlovaKg: Double = 0.0,
    val oduzimanjePredmeta: String = "",
    val ranijeOsudjivan: String = "",
    val brojOkrivljenih: Int = 0,
    val brojSvjedoka: Int = 0,
    val similarity: Double = 0.0,
)

@Serializable
data class CbrRecommendation(
    val kaznaZatvoraMjeseci: Double? = null,
    val verdictDistribution: Map<String, Int> = emptyMap(),
    val conditionalSentenceLikelihood: Int = 0,
    val conditionalExplanation: String = "",
)

@Serializable
data class CbrQuerySummary(
    val brojPredmeta: String = "",
    val tipKrivicnogDjela: String = "",
    val clanKZ: String = "",
    val kaznaZatvoraMjeseci: Double = 0.0,
    val uslovnaOsuda: String = "",
    val vrstaPresude: String = "",
    val ranijeOsudjivan: String = "",
)

@Serializable
data class CbrResult(
    val queryCase: CbrQuerySummary = CbrQuerySummary(),
    val similarCases: List<CbrSimilarCase> = emptyList(),
    val recommendation: CbrRecommendation = CbrRecommendation(),
    val totalCasesCompared: Int = 0,
)
