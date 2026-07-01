package com.wellnesscookie.pravnainformatika.rule

import com.wellnesscookie.pravnainformatika.model.CbrResult
import com.wellnesscookie.pravnainformatika.model.JudgmentDecision
import com.wellnesscookie.pravnainformatika.model.JudgmentDecisionRequest
import com.wellnesscookie.pravnainformatika.model.JudgmentInput
import com.wellnesscookie.pravnainformatika.model.JudgmentRuleReasoning
import com.wellnesscookie.pravnainformatika.model.JudgmentSections
import com.wellnesscookie.pravnainformatika.model.MarkovModels
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

object JudiciaryLogic {

    data class CaseIdentity(
        val broj: String,
        val year: Int,
        val fileBase: String,
        val judgmentName: String,
        val fallbackCaseNumber: String,
    )

    fun parseCaseIdentity(raw: String, casesDir: File?, fallbackYear: Int): CaseIdentity {
        val cleaned = raw.trim().replace(Regex("""^K\s*""", RegexOption.IGNORE_CASE), "")
        var broj = ""
        var year = fallbackYear

        Regex("""(\d+)\s*/\s*(\d{2,4})""").find(cleaned)?.let {
            broj = it.groupValues[1]
            year = normalizeYear(it.groupValues[2].toInt(), fallbackYear)
        } ?: Regex("""(\d+)[^\d]+(\d{2,4})""").find(cleaned)?.let {
            broj = it.groupValues[1]
            year = normalizeYear(it.groupValues[2].toInt(), fallbackYear)
        }

        if (broj.isEmpty()) {
            broj = Regex("""\d+""").find(cleaned)?.value.orEmpty()
        }
        if (broj.isEmpty()) {
            broj = nextSequentialCaseNumber(casesDir, year).toString()
        }
        broj = broj.filter { it.isDigit() }
        if (broj.isEmpty()) broj = nextSequentialCaseNumber(casesDir, year).toString()
        if (broj.length > 4) broj = broj.take(4)

        return CaseIdentity(
            broj = broj,
            year = year,
            fileBase = "K ${broj}_$year",
            judgmentName = "K_${broj}_$year",
            fallbackCaseNumber = "$broj/${year.toString().takeLast(2)}",
        )
    }

    private fun normalizeYear(parsed: Int, fallback: Int): Int = when {
        parsed in 100..2099 -> parsed
        parsed <= 30 -> 2000 + parsed
        parsed < 100 -> 1900 + parsed
        else -> fallback
    }

    fun nextSequentialCaseNumber(dir: File?, targetYear: Int): Int {
        if (dir == null || !dir.exists()) return 1
        var max = 0
        // Walk the tree so files under lov/ and ribolov/ subfolders are seen.
        dir.walkTopDown()
            .filter { it.isFile && it.extension.equals("xml", ignoreCase = true) }
            .forEach { f ->
                Regex("""K\s*(\d+)_(\d{4})\.xml""", RegexOption.IGNORE_CASE).matchEntire(f.name)?.let {
                    val num = it.groupValues[1].toIntOrNull() ?: return@forEach
                    val yr = it.groupValues[2].toIntOrNull() ?: return@forEach
                    if (yr == targetYear && num > max) max = num
                }
            }
        return max + 1
    }

    fun normalizeVerdictLabel(vrsta: String): String {
        val v = vrsta.lowercase().trim()
        return when (v) {
            "uslovna", "uslovna osuda", "uslovna presuda" -> "Uslovna presuda"
            "oslobadjajuca", "oslobađajuća", "oslobađajuca" -> "Oslobađajuća"
            "osudjujuca", "osuđujuća", "osuđujuca" -> "Osuđujuća"
            "sudska opomena", "opomena" -> "Sudska opomena"
            "rad u javnom interesu" -> "Rad u javnom interesu"
            else -> "Osuđujuća"
        }
    }

    fun formatSentence(d: JudgmentDecision): String = when {
        d.kaznaZatvoraMjeseci > 0 -> "${d.kaznaZatvoraMjeseci} mjeseci"
        d.kaznaZatvoraDani > 0 -> "${d.kaznaZatvoraDani} dana"
        d.radUJavnomInteresuCasovi > 0 -> "rad u javnom interesu ${d.radUJavnomInteresuCasovi} časova"
        else -> "Nije navedeno"
    }

    fun describeSentence(d: JudgmentDecision): String {
        val parts = buildList {
            if (d.kaznaZatvoraMjeseci > 0) add("${d.kaznaZatvoraMjeseci} mjeseci zatvora")
            if (d.kaznaZatvoraDani > 0) add("${d.kaznaZatvoraDani} dana zatvora")
            if (d.radUJavnomInteresuCasovi > 0) add("${d.radUJavnomInteresuCasovi} casova rada u javnom interesu")
        }
        return if (parts.isEmpty()) "Nije navedeno" else parts.joinToString(", ")
    }

    fun deriveDecisionFromSignals(
        rule: JudgmentRuleReasoning?,
        cbr: CbrResult?,
        override: JudgmentDecisionRequest?,
    ): JudgmentDecision {
        val ruleVerdict = rule?.verdict.orEmpty()
        val ruleRecText = rule?.recommendation.orEmpty()
        val ruleIsAcquittal = rule?.acquittal == true || "oslob" in ruleVerdict.lowercase()
        val conditionalChance = cbr?.recommendation?.conditionalSentenceLikelihood ?: 0

        val overrideVerdict = override?.vrstaPresude
        val verdict = when {
            !overrideVerdict.isNullOrEmpty() -> overrideVerdict
            ruleIsAcquittal -> "oslobadjajuca"
            "uslov" in ruleRecText.lowercase() || conditionalChance >= 60 -> "uslovna"
            else -> "osudjujuca"
        }

        val ruleMonths = extractMonths(ruleRecText) ?: extractMonths(rule?.actual_sentence.orEmpty())
        val cbrMonths = cbr?.recommendation?.kaznaZatvoraMjeseci
        val overrideMonths = override?.kaznaZatvoraMjeseci
        val kazna = when {
            overrideMonths != null -> overrideMonths.roundToInt()
            ruleMonths != null && cbrMonths != null -> ((ruleMonths + cbrMonths) / 2).roundToInt()
            ruleMonths != null -> ruleMonths.roundToInt()
            cbrMonths != null -> cbrMonths.roundToInt()
            else -> 0
        }

        return JudgmentDecision(
            vrstaPresude = verdict,
            kaznaZatvoraMjeseci = kazna,
            kaznaZatvoraDani = override?.kaznaZatvoraDani ?: 0,
            radUJavnomInteresuCasovi = override?.radUJavnomInteresuCasovi ?: 0,
            uslovnaOsuda = when {
                verdict == "uslovna" -> "Da"
                !override?.uslovnaOsuda.isNullOrEmpty() -> override!!.uslovnaOsuda!!
                else -> "Ne"
            },
        )
    }

    fun generateNarrativeSections(
        input: JudgmentInput,
        decision: JudgmentDecision,
        rule: JudgmentRuleReasoning? = null,
        cbr: CbrResult? = null,
    ): JudgmentSections {
        val summary = buildReasoningSummary(rule, cbr)
        val evidences = listFromText(input.dokazi)
        val witnesses = listFromText(input.svjedoci)
        val sudLabel = "Osnovni sud u ${input.sud.ifEmpty { "Podgorici" }}"
        val mitigating = rule?.mitigating_factors.orEmpty()
        val aggravating = rule?.aggravating_factors.orEmpty()
        val appliedArticles = rule?.articles?.joinToString(", ").orEmpty().ifEmpty { input.clanKZ }

        val cbrRec = cbr?.recommendation
        val similarCases = cbr?.similarCases.orEmpty().take(3)
        val similarCasesText = similarCases.joinToString("; ") {
            "${it.brojPredmeta} (slicnost ${it.similarity}%, kazna ${it.kaznaZatvoraMjeseci} mj.)"
        }

        val isHunting = isHunting(input)
        val markovBranch = if (isHunting) "hunting" else "fishing"
        val seed = if (decision.uslovnaOsuda == "Da") "USLOVNU OSUDU" else "kaznu zatvora"
        val markovSentencing = MarkovModels.generate("sentencing", seed, maxWords = 60, branch = markovBranch)
        val usedMarkov = markovSentencing != null

        val djeloLabel = input.tipKrivicnogDjela.ifEmpty {
            if (isHunting) "nezakonit lov" else "nezakonit ribolov"
        }
        val clanDefault = if (isHunting) "cl. 325 st. 1" else "cl. 326 st. 2"

        val introduction =
            "U IME CRNE GORE, $sudLabel, po sudiji ${input.sudija.ifEmpty { "NN" }}, " +
                "uz ucesce zapisnicara ${input.zapisnicar.ifEmpty { "NN" }}, " +
                "u krivicnom predmetu protiv okrivljenog ${input.okrivljeni.ifEmpty { "NN" }}, " +
                "zbog krivicnog djela $djeloLabel iz " +
                "${input.clanKZ.ifEmpty { clanDefault }} Krivicnog zakonika Crne Gore, " +
                "nakon odrzanog glavnog pretresa, donio je dana ${input.datumPresude.ifEmpty { "NN" }} " +
                "sljedecu PRESUDU:"

        val background = input.opis.ifEmpty { "Nije naveden opis slucaja." }

        val motivation = buildString {
            append("Optuzenom ${input.okrivljeni.ifEmpty { "NN" }} stavljeno je na teret krivicno djelo ")
            append("$djeloLabel iz ")
            append("${appliedArticles.ifEmpty { input.clanKZ.ifEmpty { clanDefault } }} ")
            append("Krivicnog zakonika Crne Gore. ")

            append("Sud je cijenio sve izvedene dokaze")
            if (evidences.isNotEmpty()) append(" (${evidences.joinToString(", ")})")
            append(" pojedinacno i u medjusobnoj vezi, te utvrdio sljedece cinjenicno stanje: ")
            append("${input.opis.ifEmpty { "Nije navedeno." }} ")

            if (witnesses.isNotEmpty()) {
                append("U dokaznom postupku saslusani su svjedoci: ${witnesses.joinToString(", ")}. ")
            }

            val facts = if (isHunting) huntingFacts(input) else fishingFacts(input)
            if (facts.isNotEmpty()) {
                append("Utvrdeno je da je okrivljeni ${facts.joinToString(", ")}. ")
            }

            if (input.saizvrsilastvo == "da") {
                append("Djelo je izvrseno u saizvrsijalastvu u smislu cl. 23 st. 2 Krivicnog zakonika. ")
            }

            append("Cijeneci utvrdjeno cinjenicno stanje, sud je nasao da su se u radnjama okrivljenog ")
            append("stekla sva bitna obiljezja krivicnog djela iz ")
            append("${appliedArticles.ifEmpty { input.clanKZ }} Krivicnog zakonika. ")

            if (mitigating.isNotEmpty() || aggravating.isNotEmpty()) {
                append("Prilikom odluke o krivicnoj sankciji, sud je u smislu cl. 42 st. 1 Krivicnog zakonika ")
                append("cijenio sve okolnosti koje su od uticaja na njen izbor i visinu. ")
                if (mitigating.isNotEmpty()) {
                    append("Na strani okrivljenog, kao olaksavajuce okolnosti sud je cijenio: ")
                    append("${mitigating.joinToString(", ")}. ")
                }
                if (aggravating.isNotEmpty()) {
                    append("Kao otezavajuce okolnosti sud je cijenio: ${aggravating.joinToString(", ")}. ")
                }
            }

            if (similarCasesText.isNotEmpty()) {
                append("Rasudivanje po slicnim slucajevima ($similarCasesText) dalo je preporucenu kaznu od ")
                append(cbrRec?.kaznaZatvoraMjeseci?.let { "$it mjeseci" } ?: "N/A")
                cbrRec?.conditionalSentenceLikelihood?.let {
                    append(", sa vjerovatnocom uslovne presude $it%")
                }
                append(". ")
            }

            if (markovSentencing != null) append("$markovSentencing ")
            append("Imajuci u vidu navedeno, odluceno je kao u dispozitivu ove presude.")
        }

        val decisionLine = buildDecisionLine(input, decision)

        return JudgmentSections(
            introduction = introduction,
            background = background,
            motivation = motivation,
            decision = decisionLine,
            reasoningSummary = summary,
            generatorLabel = if (usedMarkov) "mc" else "tf",
        )
    }

    fun validateUserInput(input: JudgmentInput): String? {
        if (input.sud.isBlank()) return "sud je obavezno polje"
        if (input.sudija.isBlank()) return "sudija je obavezno polje"
        if (input.zapisnicar.isBlank()) return "zapisnicar je obavezno polje"
        if (input.okrivljeni.isBlank()) return "okrivljeni je obavezno polje"
        if (input.datumPresude.isBlank()) return "datumPresude je obavezno polje"
        if (input.clanKZ.isBlank()) return "clanKZ je obavezno polje"
        if (input.opis.isBlank()) return "opis je obavezno polje"
        if (input.kolicinaUlovaKg < 0.0) return "kolicinaUlovaKg mora biti broj >= 0"
        if (input.brojOkrivljenih < 1) return "brojOkrivljenih mora biti broj >= 1"
        if (input.brojSvjedoka < 0) return "brojSvjedoka mora biti broj >= 0"
        return null
    }

    private fun buildReasoningSummary(rule: JudgmentRuleReasoning?, cbr: CbrResult?): String {
        val applied = rule?.articles?.joinToString(", ").orEmpty().ifEmpty { "n/a" }
        val rec = rule?.recommendation.orEmpty().ifEmpty { "n/a" }
        val cbrRec = cbr?.recommendation
        val cbrText = cbrRec?.kaznaZatvoraMjeseci?.let {
            "CBR kazna $it mj, uslovna ${cbrRec.conditionalSentenceLikelihood}%"
        } ?: "CBR n/a"
        val similar = cbr?.similarCases.orEmpty().take(3).joinToString(", ") { it.brojPredmeta }
            .ifEmpty { "nema" }
        return "Primijenjeni clanovi: $applied. Rule preporuka: $rec. $cbrText. Slicni: $similar."
    }

    private fun buildDecisionLine(input: JudgmentInput, decision: JudgmentDecision): String {
        val okr = input.okrivljeni.ifEmpty { "NN" }
        val hunting = isHunting(input)
        val djelo = input.tipKrivicnogDjela.ifEmpty {
            if (hunting) "nezakonit lov" else "nezakonit ribolov"
        }
        val article = input.clanKZ.ifEmpty { if (hunting) "cl. 325 st. 1" else "cl. 326 st. 2" }
        var line = when {
            decision.vrstaPresude == "oslobadjajuca" ->
                "Na osnovu clana 363 st. 1 tac. 3 ZKP-a, okrivljeni $okr se OSLOBADJA od optuzbe da je " +
                    "izvrsio krivicno djelo $djelo iz $article Krivicnog zakonika Crne Gore, jer nije " +
                    "dokazano da je izvrsio djelo za koje je optuzen."

            decision.uslovnaOsuda == "Da" -> {
                val provjeraMj = max(12, decision.kaznaZatvoraMjeseci * 4)
                "Okrivljeni $okr proglasava se KRIVIM za krivicno djelo $djelo iz $article " +
                    "Krivicnog zakonika Crne Gore, pa mu sud primjenom cl. 42, cl. 52, cl. 53 i cl. 54 " +
                    "Krivicnog zakonika izrice USLOVNU OSUDU kojom mu utvrdjuje kaznu zatvora u trajanju od " +
                    "${decision.kaznaZatvoraMjeseci} mjeseci i istovremeno odredjuje da se ona nece izvrsiti " +
                    "ako okrivljeni u roku od $provjeraMj mjeseci od pravosnaznosti presude ne ucini novo " +
                    "krivicno djelo."
            }

            else ->
                "Okrivljeni $okr proglasava se KRIVIM za krivicno djelo $djelo iz $article Krivicnog " +
                    "zakonika Crne Gore i OSUDJUJE SE na kaznu zatvora u trajanju od " +
                    "${decision.kaznaZatvoraMjeseci} mjeseci."
        }
        if (input.oduzimanjePredmeta == "da") {
            line += if (hunting) {
                " Na osnovu cl. 75 i cl. 325 st. 5 Krivicnog zakonika Crne Gore, " +
                    "izrice se mjera bezbjednosti oduzimanja ulovljene divljaci i sredstava za lov."
            } else {
                " Na osnovu cl. 75 i cl. 326 st. 4 Krivicnog zakonika Crne Gore, " +
                    "izrice se mjera bezbjednosti oduzimanja ulova i sredstava za ribolov."
            }
        }
        return line
    }

    private fun isHunting(input: JudgmentInput): Boolean {
        val tip = input.tipKrivicnogDjela.lowercase()
        val art = input.clanKZ
        return when {
            "ribolov" in tip -> false
            "lov" in tip -> true
            "325" in art -> true
            "326" in art -> false
            else -> false
        }
    }

    private fun fishingFacts(input: JudgmentInput): List<String> = buildList {
        if (input.zabranjenoSredstvo == "da") add("koristio zabranjeno sredstvo za ribolov")
        if (input.elektricnaStruja == "da") add("lovio ribu elektricnom strujom")
        if (input.lovostajIliZabranjeneVode == "da") add("lovio u periodu lovostaja ili u zabranjenim vodama")
        if (input.prisutanUlov == "da" && input.kolicinaUlovaKg > 0) add("ulovio ${input.kolicinaUlovaKg} kg ribe")
    }

    private fun huntingFacts(input: JudgmentInput): List<String> = buildList {
        if (input.lovostajIliZabranjeneVode == "da") add("lovio divljac u vrijeme lovostaja ili na podrucju gdje je lov zabranjen")
        if (input.tudjeLoviste == "da") add("lovio u tudjem lovistu bez odobrenja korisnika lovista")
        if (input.krupnaDivljac == "da") add("lovio krupnu divljac")
        if (input.zasticenaVrsta == "da") add("lovio zasticenu vrstu divljaci")
        if (input.bezPosebneDozvole == "da") add("lovio bez posebne dozvole")
        if (input.masovnoUnistavanje == "da") add("koristio sredstva za masovno unistavanje divljaci")
        if (input.vatrenoOruzje == "da") add("nosio i drzao vatreno oruzje")
        if (input.bezOruznogLista == "da") add("posjedovao oruzje bez oruznog lista")
        if (input.bezDozvoleZaLov == "da") add("lovio bez dozvole za lov")
        if (input.lovackiPsi > 0) {
            val psiText = if (input.lovackiPsi == 1) "jednog lovackog psa" else "${input.lovackiPsi} lovackih pasa"
            add("koristio $psiText")
        }
        if (input.odstreljenaDivljac == "da") {
            val vrsta = input.vrstaDivljaci.ifEmpty { "divljac" }
            val game = if (input.brojGrlaDivljaci > 1) "${input.brojGrlaDivljaci} komada ($vrsta)" else vrsta
            add("odstrijelio $game")
        } else if (input.prisutanUlov == "da" && input.vrstaDivljaci.isNotEmpty()) {
            add("ulovio ${input.vrstaDivljaci}")
        }
        if (input.kolicinaUlovaKg > 0 && input.odstreljenaDivljac == "da") {
            add("ulov tezine oko ${input.kolicinaUlovaKg} kg")
        }
    }

    private fun listFromText(value: String): List<String> =
        value.split(Regex("""\r?\n|;""")).map { it.trim() }.filter { it.isNotEmpty() }

    private fun extractMonths(text: String): Double? {
        val m = Regex("""(\d+(?:[.,]\d+)?)\s*(mesec|mjesec|month)""", RegexOption.IGNORE_CASE).find(text)
        return m?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
    }
}
