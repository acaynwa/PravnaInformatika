package com.wellnesscookie.pravnainformatika.rule

import com.wellnesscookie.pravnainformatika.db.CbrCase
import com.wellnesscookie.pravnainformatika.db.CbrStore
import com.wellnesscookie.pravnainformatika.model.CaseRecord
import com.wellnesscookie.pravnainformatika.model.CbrQuerySummary
import com.wellnesscookie.pravnainformatika.model.CbrRecommendation
import com.wellnesscookie.pravnainformatika.model.CbrResult
import com.wellnesscookie.pravnainformatika.model.CbrSimilarCase
import kotlin.math.roundToInt

class CbrService(private val cbrStore: CbrStore) {

    private val topK = 5

    fun query(parsedCase: CaseRecord?, fallbackQuery: Map<String, String>? = null): CbrResult {
        val cbrCases = cbrStore.all()
        if (cbrCases.isEmpty()) {
            return CbrResult(recommendation = CbrRecommendation(conditionalExplanation = "CBR baza slucajeva nije ucitana"))
        }

        val queryBroj = parsedCase?.caseNumber
            ?: fallbackQuery?.get("brojPredmeta")
            ?: "NOVI-${System.currentTimeMillis()}"

        val queryCase: CbrCase = parsedCase?.let { p ->
            cbrStore.byBrojPredmeta(p.caseNumber)
                ?: cbrStore.byId(p.caseId.orEmpty())
                ?: CbrStore.buildCbrRecordFromParsed(p)
        } ?: CbrStore.normalizeCbrQuery(fallbackQuery.orEmpty())

        return runQuery(queryBroj, queryCase, cbrCases, includeSelf = parsedCase == null)
    }

    fun queryFromRaw(raw: Map<String, String>): CbrResult {
        val cbrCases = cbrStore.all()
        if (cbrCases.isEmpty()) return CbrResult()
        val q = CbrStore.normalizeCbrQuery(raw)
        return runQuery(q.brojPredmeta, q, cbrCases, includeSelf = true)
    }

    private fun runQuery(
        queryBroj: String,
        queryCase: CbrCase,
        cbrCases: List<CbrCase>,
        includeSelf: Boolean,
    ): CbrResult {
        data class Scored(val case: CbrCase, val similarity: Double)

        // Pre-filter: same crime branch only (hunting compares to hunting,
        // fishing to fishing). Cross-type sentences aren't comparable.
        val queryTip = queryCase.tipKrivicnogDjela.lowercase()
        val sameBranch = cbrCases.filter { c ->
            val t = c.tipKrivicnogDjela.lowercase()
            when {
                "ribolov" in queryTip -> "ribolov" in t
                "lov" in queryTip -> "lov" in t && "ribolov" !in t
                else -> true
            }
        }.ifEmpty { cbrCases }

        val results = sameBranch.asSequence()
            .filter { includeSelf || it.brojPredmeta != queryBroj }
            .map { Scored(it, CbrReasoning.computeSimilarity(queryCase, it)) }
            .sortedByDescending { it.similarity }
            .toList()

        val top = results.take(topK.coerceAtMost(results.size))
        val similarCases = top.map { it.case.toResponse(it.similarity) }

        // Weighted recommended sentence (only cases with positive sentence)
        var weightedSentence = 0.0
        var weightSum = 0.0
        for (s in top) {
            val months = s.case.kaznaZatvoraMjeseci.toDoubleOrNull() ?: 0.0
            val days = s.case.kaznaZatvoraDani.toDoubleOrNull() ?: 0.0
            val total = months + days / 30.0
            if (total > 0.0) {
                weightedSentence += s.similarity * total
                weightSum += s.similarity
            }
        }
        val recommendedMonths: Double? =
            if (weightSum > 0.0) ((weightedSentence / weightSum) * 10).roundToInt() / 10.0 else null

        val verdictCounts = similarCases.groupingBy { it.vrstaPresude.ifEmpty { "nepoznato" } }.eachCount()
        val conditionalCount = similarCases.count { it.uslovnaOsuda == "Da" }
        val topSize = top.size.coerceAtLeast(1)
        val conditionalNames = similarCases.filter { it.uslovnaOsuda == "Da" }.map { it.brojPredmeta }
        val nonConditionalNames = similarCases.filter { it.uslovnaOsuda != "Da" }.map { it.brojPredmeta }

        return CbrResult(
            queryCase = CbrQuerySummary(
                brojPredmeta = queryBroj,
                tipKrivicnogDjela = queryCase.tipKrivicnogDjela,
                clanKZ = queryCase.clanKZ,
                kaznaZatvoraMjeseci = queryCase.kaznaZatvoraMjeseci.toDoubleOrNull() ?: 0.0,
                uslovnaOsuda = queryCase.uslovnaOsuda.ifEmpty { "nepoznat" },
                vrstaPresude = CbrReasoning.displayVerdict(queryCase.vrstaPresude),
                ranijeOsudjivan = queryCase.ranijeOsudjivan.ifEmpty { "nepoznat" },
            ),
            similarCases = similarCases,
            recommendation = CbrRecommendation(
                kaznaZatvoraMjeseci = recommendedMonths,
                verdictDistribution = verdictCounts,
                conditionalSentenceLikelihood = (conditionalCount.toDouble() / topSize * 100).roundToInt(),
                conditionalExplanation = "",
            ),
            totalCasesCompared = results.size,
        )
    }

    private fun CbrCase.toResponse(similarity: Double): CbrSimilarCase = CbrSimilarCase(
        brojPredmeta = brojPredmeta,
        sud = CbrReasoning.formatCourtName(sud),
        tipKrivicnogDjela = tipKrivicnogDjela,
        clanKZ = clanKZ,
        vrstaPresude = CbrReasoning.displayVerdict(vrstaPresude),
        kaznaZatvoraMjeseci = kaznaZatvoraMjeseci.toDoubleOrNull() ?: 0.0,
        kaznaZatvoraDani = kaznaZatvoraDani.toIntOrNull() ?: 0,
        radUJavnomInteresuCasovi = radUJavnomInteresuCasovi.toIntOrNull() ?: 0,
        rokProvjereGodine = rokProvjereGodine.toIntOrNull() ?: 0,
        uslovnaOsuda = uslovnaOsuda,
        troskoviEur = troskoviEur.toDoubleOrNull() ?: 0.0,
        saizvrsilastvo = saizvrsilastvo,
        zabranjenoSredstvo = zabranjenoSredstvo,
        lovostajIliZabranjeneVode = lovostajIliZabranjeneVode,
        velikaKolicina = velikaKolicina,
        elektricnaStruja = elektricnaStruja,
        agregat = agregat,
        sonda = sonda,
        pretvarac = pretvarac,
        prisutanUlov = prisutanUlov,
        kolicinaUlovaKg = kolicinaUlovaKg.toDoubleOrNull() ?: 0.0,
        oduzimanjePredmeta = oduzimanjePredmeta,
        ranijeOsudjivan = ranijeOsudjivan,
        brojOkrivljenih = brojOkrivljenih.toIntOrNull() ?: 1,
        brojSvjedoka = brojSvjedoka.toIntOrNull() ?: 0,
        similarity = (similarity * 1000).roundToInt() / 10.0,
    )
}
