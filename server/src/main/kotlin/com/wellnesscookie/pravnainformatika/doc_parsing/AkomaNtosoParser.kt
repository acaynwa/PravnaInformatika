package com.wellnesscookie.pravnainformatika.doc_parsing

import com.wellnesscookie.pravnainformatika.model.CaseRecord
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

object AkomaNtosoParser {

    private val factory: DocumentBuilderFactory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
    }

    private val courtMap = mapOf(
        "podgoric" to "Podgorici", "nikši" to "Nikšiću", "niksic" to "Nikšiću",
        "rožaj" to "Rožajama", "rozaj" to "Rožajama", "berana" to "Beranama",
        "bar" to "Baru", "kotor" to "Kotoru", "cetinj" to "Cetinju",
        "herceg" to "Herceg Novom", "bijel" to "Bijelom Polju",
        "pljevlj" to "Pljevljima", "plav" to "Plavu", "ulcinj" to "Ulcinju",
        "danilovgrad" to "Danilovgradu", "kolašin" to "Kolašinu", "kolasin" to "Kolašinu",
    )

    fun parse(file: File): CaseRecord? = runCatching {
        val doc = factory.newDocumentBuilder().parse(file)
        val meta = doc.getElementsByTagNameNS("*", "meta").item(0) as? Element ?: return null
        val body = (doc.getElementsByTagNameNS("*", "body").item(0)
            ?: doc.getElementsByTagNameNS("*", "judgmentBody").item(0)) as? Element ?: return null

        val proprietary = meta.getElementsByTagNameNS("*", "proprietary").item(0) as? Element
        val p = ProprietaryFields.from(proprietary)

        val caseNumber = resolveCaseNumber(meta, file, p.brojPredmeta)
        val court = resolveCourt(meta, p.sud).let { normalizeCourtName(it) }
        val defendants = collectDefendants(meta)
        val defendant = if (defendants.isNotEmpty()) defendants.joinToString("; ") else "Nepoznat"
        val brojOkrivljenih = maxOf(p.brojOkrivljenih, defendants.size).coerceAtLeast(1)
        val sudija = if (p.sudija == "Nepoznat") resolveJudge(meta) else p.sudija

        val decisionElement = (body.getElementsByTagNameNS("*", "decision").item(0)
            ?: body.getElementsByTagNameNS("*", "conclusions").item(0)) as? Element
        val decisionPs = decisionElement?.getElementsByTagNameNS("*", "p")
        val bodyRefs = body.getElementsByTagNameNS("*", "ref")

        // Derive crime type / display type
        val crimeType = p.tipKrivicnogDjela.ifEmpty {
            (meta.getElementsByTagNameNS("*", "FRBRname").item(0) as? Element)?.getAttribute("value").orEmpty()
        }
        var tipKrivicnogDjela = p.tipKrivicnogDjela
        var displayType = (crimeType.ifEmpty { "Nezakonit ribolov" }).let { t ->
            val low = t.lowercase()
            when {
                "lov" in low && "ribolov" !in low -> "Nezakonit lov"
                "ribolov" in low || "fish" in low -> "Nezakonit ribolov"
                else -> t
            }
        }
        if (crimeType.isEmpty()) {
            val bodyText = (body.textContent ?: "").lowercase()
            when {
                "ribolov" in bodyText || "ribu" in bodyText || "ribe" in bodyText -> {
                    displayType = "Nezakonit ribolov"; tipKrivicnogDjela = "nezakonit ribolov"
                }
                "lovio" in bodyText || "divljač" in bodyText -> {
                    displayType = "Nezakonit lov"; tipKrivicnogDjela = "nezakonit lov"
                }
            }
        }

        // Auto-detect clanKZ from body refs if not in proprietary
        var clanKZ = p.clanKZ
        if (clanKZ.isEmpty()) {
            for (i in 0 until bodyRefs.length) {
                val href = (bodyRefs.item(i) as Element).getAttribute("href").orEmpty()
                val m = ART_REF_HREF.find(href) ?: continue
                val artNum = m.groupValues[1].toInt()
                val paraNum = m.groupValues.getOrNull(2)?.toIntOrNull()
                if (artNum == 326 || artNum == 325) {
                    clanKZ = if (paraNum != null) "čl. $artNum st. $paraNum" else "čl. $artNum"
                    break
                }
            }
        }

        // Final fallback: infer tipKrivicnogDjela / displayType from clanKZ if still empty/wrong.
        if (clanKZ.contains("325")) {
            tipKrivicnogDjela = "nezakonit lov"
            displayType = "Nezakonit lov"
        } else if (clanKZ.contains("326")) {
            tipKrivicnogDjela = "nezakonit ribolov"
            displayType = "Nezakonit ribolov"
        }

        val articles = buildAppliedArticles(
            clanKZ = clanKZ,
            tipKrivicnogDjela = tipKrivicnogDjela.ifEmpty { displayType },
            references = meta.getElementsByTagNameNS("*", "TLCReference").asElementSeq().toList(),
            bodyRefs = bodyRefs.asElementSeq().toList(),
            decisionPs = decisionPs?.asElementSeq()?.toList().orEmpty(),
        )

        val caseDescription = p.opisSlucaja.ifEmpty {
            val arguments = body.getElementsByTagNameNS("*", "arguments").item(0) as? Element
            arguments?.let { args ->
                val ps = args.getElementsByTagNameNS("*", "p")
                val parts = mutableListOf<String>()
                for (i in 0 until ps.length) {
                    val text = (ps.item(i).textContent ?: "").trim()
                    if (text.length > 20) parts += text
                }
                // No length cap — return the full obrazloženje. UI can truncate
                // for previews if needed. Old 5-paragraph / 1000-char cap chopped
                // mid-sentence on cases like K 274/23.
                parts.joinToString("\n\n")
            }.orEmpty()
        }

        val (verdictText, verdictUslovna) = resolveVerdict(p.vrstaPresude, decisionPs, body, p.uslovnaOsuda)

        // Heuristic body-text extraction when not generated (i.e. real historic case)
        val flags = if (p.generatedBy.isEmpty()) {
            extractEnvFactsFromBody(body, decisionElement, p)
        } else {
            p.toFlags()
        }

        val year = resolveYear(meta, caseNumber)
        val caseDate = resolveDate(meta) ?: "Nepoznat"

        val sentenceMonths = flags.kaznaZatvoraMjeseci.takeIf { it > 0 }
            ?: SENTENCE_NUMBER.find(p.kazna)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        val sentenceText = p.kazna.ifEmpty {
            if (verdictText in setOf("OSLOBOĐEN", "OSLOBAĐAJUĆA", "ODBIJENA", "NEPOZNAT")) "Nema"
            else buildSentenceText(flags, verdictUslovna)
        }
        val evidenceStr = if (p.dokazi.isNotEmpty()) p.dokazi.joinToString("; ") else "Nije navedeno"

        CaseRecord(
            id = caseNumber,
            caseNumber = caseNumber,
            caseId = file.nameWithoutExtension,
            type = displayType,
            court = court,
            date = caseDate,
            verdict = verdictText,
            articles = articles,
            sentence = sentenceText,
            sentenceMonths = sentenceMonths,
            defendant = defendant,
            keywords = listOf(displayType),
            caseDescription = caseDescription,
            evidence = evidenceStr,
            sudija = sudija,
            zapisnicar = p.zapisnicar,
            uslovnaOsuda = verdictUslovna,
            ranijeOsudjivan = flags.ranijeOsudjivan,
            svjedoci = p.svjedoci,
            dokazi = p.dokazi,
            clanKZ = clanKZ,
            tipKrivicnogDjela = tipKrivicnogDjela.ifEmpty { displayType },
            brojOkrivljenih = brojOkrivljenih,
            brojSvjedoka = if (p.brojSvjedoka > 0) p.brojSvjedoka else p.svjedoci.size,
            brojDokaza = p.brojDokaza,
            generatedBy = p.generatedBy,
            saizvrsilastvo = flags.saizvrsilastvo,
            zabranjenoSredstvo = flags.zabranjenoSredstvo,
            lovostajIliZabranjeneVode = flags.lovostajIliZabranjeneVode,
            velikaKolicina = flags.velikaKolicina,
            elektricnaStruja = flags.elektricnaStruja,
            agregat = flags.agregat,
            sonda = flags.sonda,
            pretvarac = flags.pretvarac,
            prisutanUlov = flags.prisutanUlov,
            kolicinaUlovaKg = flags.kolicinaUlovaKg,
            oduzimanjePredmeta = flags.oduzimanjePredmeta,
            kaznaZatvoraMjeseci = flags.kaznaZatvoraMjeseci,
            kaznaZatvoraDani = flags.kaznaZatvoraDani,
            radUJavnomInteresuCasovi = flags.radUJavnomInteresuCasovi,
            rokProvjereGodine = flags.rokProvjereGodine,
            troskoviEur = flags.troskoviEur,
            tudjeLoviste = flags.tudjeLoviste,
            krupnaDivljac = flags.krupnaDivljac,
            zasticenaVrsta = flags.zasticenaVrsta,
            bezPosebneDozvole = flags.bezPosebneDozvole,
            masovnoUnistavanje = flags.masovnoUnistavanje,
            vatrenoOruzje = flags.vatrenoOruzje,
            lovackiPsi = flags.lovackiPsi,
            bezOruznogLista = flags.bezOruznogLista,
            bezDozvoleZaLov = flags.bezDozvoleZaLov,
            odstreljenaDivljac = flags.odstreljenaDivljac,
            vrstaDivljaci = flags.vrstaDivljaci,
            brojGrlaDivljaci = flags.brojGrlaDivljaci,
            sticajDrugogDela = flags.sticajDrugogDela,
            year = year,
            xmlFile = file.absolutePath,
        )
    }.onFailure {
        System.err.println("Error parsing ${file.path}: ${it.message}")
    }.getOrNull()

    // ----- helpers -----

    private fun resolveCaseNumber(meta: Element, file: File, fromProprietary: String): String {
        if (fromProprietary.isNotEmpty() && fromProprietary != "Nepoznat") return fromProprietary
        val title = (meta.getElementsByTagNameNS("*", "FRBRtitle").item(0) as? Element)?.textContent?.trim()
        if (!title.isNullOrEmpty()) return title
        val number = (meta.getElementsByTagNameNS("*", "FRBRnumber").item(0) as? Element)?.getAttribute("value")
        if (!number.isNullOrEmpty()) return number
        val base = file.nameWithoutExtension
        val m = Regex("""K\s*(\d+)_(\d+)""", RegexOption.IGNORE_CASE).find(base)
        return if (m != null) {
            val yr = m.groupValues[2].let { if (it.length == 4) it.substring(2) else it }
            "K ${m.groupValues[1]}/$yr"
        } else base
    }

    private fun resolveCourt(meta: Element, fromProprietary: String): String {
        if (fromProprietary.isNotEmpty() && fromProprietary != "Nepoznat") return fromProprietary
        val author = (meta.getElementsByTagNameNS("*", "FRBRauthor").item(0) as? Element)?.textContent?.trim()
        if (!author.isNullOrEmpty()) return author
        val orgs = meta.getElementsByTagNameNS("*", "TLCOrganization")
        for (i in 0 until orgs.length) {
            val org = orgs.item(i) as Element
            if (org.getAttribute("eId") == "court") {
                val showAs = org.getAttribute("showAs")
                if (!showAs.isNullOrEmpty()) return showAs
            }
        }
        return "Nepoznat"
    }

    private fun normalizeCourtName(court: String): String {
        if (court.isEmpty() || court == "Nepoznat") return court
        val low = court.lowercase()
        val city = courtMap.entries.firstOrNull { (k, _) -> k in low }?.value
        return when {
            city != null -> "Osnovni sud u $city"
            "osnovni" !in low -> "Osnovni sud u $court"
            else -> court
        }
    }

    private fun collectDefendants(meta: Element): List<String> {
        val persons = meta.getElementsByTagNameNS("*", "TLCPerson")
        val out = mutableListOf<String>()
        for (i in 0 until persons.length) {
            val person = persons.item(i) as Element
            if ("defendant" in person.getAttribute("eId").orEmpty()) {
                val name = person.getAttribute("showAs")
                if (!name.isNullOrEmpty()) out += name
            }
        }
        return out
    }

    private fun resolveJudge(meta: Element): String {
        val persons = meta.getElementsByTagNameNS("*", "TLCPerson")
        for (i in 0 until persons.length) {
            val person = persons.item(i) as Element
            if ("judge" in person.getAttribute("eId").orEmpty()) {
                return person.getAttribute("showAs").orEmpty().ifEmpty { "Nepoznat" }
            }
        }
        return "Nepoznat"
    }

    private fun resolveVerdict(
        vrstaPresude: String,
        decisionPs: org.w3c.dom.NodeList?,
        body: Element,
        proprietaryUslovna: Boolean,
    ): Pair<String, Boolean> {
        if (vrstaPresude.isNotEmpty()) {
            val vp = vrstaPresude.lowercase()
            val name = when {
                vp == "kriv" || vp == "kriva" -> "KRIV"
                "osuđujuć" in vp || "osudjujuc" in vp -> "KRIV"
                "oslob" in vp -> "OSLOBAĐAJUĆA"
                "uslovna" in vp -> "USLOVNA PRESUDA"
                "opomena" in vp -> "SUDSKA OPOMENA"
                else -> vrstaPresude.uppercase()
            }
            return name to (proprietaryUslovna || "uslovna" in vp)
        }
        var verdict = "NEPOZNAT"
        var uslovna = proprietaryUslovna

        val psToScan = if (decisionPs != null && decisionPs.length > 0) decisionPs
            else body.getElementsByTagNameNS("*", "p")

        for (i in 0 until psToScan.length) {
            val text = psToScan.item(i).textContent.orEmpty()
            if ("OSLOBOĐEN" in text || "oslobođen" in text ||
                "Oslobađa" in text || "OSLOBAĐA" in text ||
                "OSLOBAĐAJU" in text
            ) verdict = "OSLOBOĐEN"
            if ("ODBIJA" in text || "odbija" in text) verdict = "ODBIJENA"
            if (verdict == "NEPOZNAT" && (
                "KRIV" in text || "Kriv" in text ||
                "OSUĐUJE" in text || "Osuđuje" in text ||
                "osudjuje" in text
            )) verdict = "KRIV"
            if ("USLOVN" in text || "uslovn" in text || "Uslovn" in text) {
                uslovna = true
            }
        }
        return verdict to uslovna
    }

    private fun buildSentenceText(flags: EnvFlags, uslovna: Boolean): String {
        val isConditional = uslovna || flags.rokProvjereGodine > 0
        val parts = mutableListOf<String>()
        if (flags.kaznaZatvoraMjeseci > 0) parts += "${flags.kaznaZatvoraMjeseci} mjeseci zatvora"
        if (flags.kaznaZatvoraDani > 0) parts += "${flags.kaznaZatvoraDani} dana zatvora"
        if (flags.radUJavnomInteresuCasovi > 0) parts += "rad u javnom interesu ${flags.radUJavnomInteresuCasovi} č."
        if (isConditional && flags.rokProvjereGodine > 0) parts += "uslovno ${flags.rokProvjereGodine} god."
        else if (isConditional) parts += "uslovno"
        return parts.joinToString(", ").ifEmpty { "Nepoznat" }
    }

    private data class EnvFlags(
        val saizvrsilastvo: String = "ne",
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
        val ranijeOsudjivan: String = "",
        val kaznaZatvoraMjeseci: Int = 0,
        val kaznaZatvoraDani: Int = 0,
        val radUJavnomInteresuCasovi: Int = 0,
        val rokProvjereGodine: Int = 0,
        val troskoviEur: Double = 0.0,
        // Hunting-specific (čl. 325)
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
    )

    private fun ProprietaryFields.toFlags() = EnvFlags(
        saizvrsilastvo = saizvrsilastvo,
        zabranjenoSredstvo = zabranjenoSredstvo,
        lovostajIliZabranjeneVode = lovostajIliZabranjeneVode,
        velikaKolicina = velikaKolicina,
        elektricnaStruja = elektricnaStruja,
        agregat = agregat,
        sonda = sonda,
        pretvarac = pretvarac,
        prisutanUlov = prisutanUlov,
        kolicinaUlovaKg = kolicinaUlovaKg,
        oduzimanjePredmeta = oduzimanjePredmeta,
        ranijeOsudjivan = ranijeOsudjivan,
        kaznaZatvoraMjeseci = kaznaZatvoraMjeseci,
        kaznaZatvoraDani = kaznaZatvoraDani,
        radUJavnomInteresuCasovi = radUJavnomInteresuCasovi,
        rokProvjereGodine = rokProvjereGodine,
        troskoviEur = troskoviEur,
        tudjeLoviste = tudjeLoviste,
        krupnaDivljac = krupnaDivljac,
        zasticenaVrsta = zasticenaVrsta,
        bezPosebneDozvole = bezPosebneDozvole,
        masovnoUnistavanje = masovnoUnistavanje,
        vatrenoOruzje = vatrenoOruzje,
        lovackiPsi = lovackiPsi,
        bezOruznogLista = bezOruznogLista,
        bezDozvoleZaLov = bezDozvoleZaLov,
        odstreljenaDivljac = odstreljenaDivljac,
        vrstaDivljaci = vrstaDivljaci,
        brojGrlaDivljaci = brojGrlaDivljaci,
        sticajDrugogDela = sticajDrugogDela,
    )

    private fun extractEnvFactsFromBody(
        body: Element,
        decisionElement: Element?,
        p: ProprietaryFields,
    ): EnvFlags {
        val bodyText = (body.textContent ?: "").lowercase()
        var saizvrsilastvo = p.saizvrsilastvo
        var zabranjenoSredstvo = p.zabranjenoSredstvo
        var lovostajIliZabranjeneVode = p.lovostajIliZabranjeneVode
        val velikaKolicina = p.velikaKolicina
        var elektricnaStruja = p.elektricnaStruja
        var agregat = p.agregat
        var sonda = p.sonda
        var pretvarac = p.pretvarac
        var prisutanUlov = p.prisutanUlov
        var kolicinaUlovaKg = p.kolicinaUlovaKg
        var oduzimanjePredmeta = p.oduzimanjePredmeta
        var ranijeOsudjivan = p.ranijeOsudjivan

        if ("agregat" in bodyText) { agregat = "da"; zabranjenoSredstvo = "da" }
        if ("sond" in bodyText) { sonda = "da"; zabranjenoSredstvo = "da" }
        if ("pretvarač" in bodyText || "pretvarac" in bodyText) pretvarac = "da"
        if ("elektri" in bodyText && ("struj" in bodyText || "izlov" in bodyText)) {
            elektricnaStruja = "da"; zabranjenoSredstvo = "da"
        }
        if ("saizvršio" in bodyText || "saizvršioc" in bodyText || "zajedno" in bodyText ||
            "čl. 23 st. 2" in bodyText || "čl. 23 st.2" in bodyText
        ) saizvrsilastvo = "da"
        if ("ulov" in bodyText || "kg ribe" in bodyText || "kilogram" in bodyText) {
            prisutanUlov = "da"
            WEIGHT_KG.find(bodyText)?.let {
                kolicinaUlovaKg = it.groupValues[1].replace(',', '.').toDoubleOrNull() ?: kolicinaUlovaKg
            }
        }
        if ("oduz" in bodyText &&
            ("predmet" in bodyText || "sredstv" in bodyText || "ulov" in bodyText)
        ) oduzimanjePredmeta = "da"
        if ("ranije osuđivan" in bodyText || "osuđivan" in bodyText) {
            val idx = bodyText.indexOf("osuđivan")
            val ctx = bodyText.substring(maxOf(0, idx - 30), minOf(bodyText.length, idx + 30))
            ranijeOsudjivan = if ("nije" in ctx || "neo" in ctx) "ne" else "da"
        }
        if ("lovostaj" in bodyText || "zabranjenim vodama" in bodyText ||
            ("zabranjen" in bodyText && "period" in bodyText) ||
            "trajnog lovnog zabrana" in bodyText ||
            ("lov je zabranjen" in bodyText) || ("lov zabranjen" in bodyText) ||
            "van lovne sezone" in bodyText || "van lovišta" in bodyText
        ) lovostajIliZabranjeneVode = "da"

        // ---- hunting-specific heuristics (čl. 325) ----
        var tudjeLoviste = p.tudjeLoviste
        var krupnaDivljac = p.krupnaDivljac
        var zasticenaVrsta = p.zasticenaVrsta
        var bezPosebneDozvole = p.bezPosebneDozvole
        var masovnoUnistavanje = p.masovnoUnistavanje
        var vatrenoOruzje = p.vatrenoOruzje
        var lovackiPsi = p.lovackiPsi
        var bezOruznogLista = p.bezOruznogLista
        var bezDozvoleZaLov = p.bezDozvoleZaLov
        var odstreljenaDivljac = p.odstreljenaDivljac
        var vrstaDivljaci = p.vrstaDivljaci
        var brojGrlaDivljaci = p.brojGrlaDivljaci
        var sticajDrugogDela = p.sticajDrugogDela

        if ("tuđem lovištu" in bodyText || "tudjem lovištu" in bodyText ||
            "bez odobrenja korisnika lovišta" in bodyText ||
            "trajnog lovnog zabrana" in bodyText || "nelovnoj površini" in bodyText
        ) tudjeLoviste = "da"
        if ("krupna divljač" in bodyText || "krupnu divljač" in bodyText ||
            "divlja svinja" in bodyText || "divlju svinju" in bodyText ||
            "vepra" in bodyText || "vepar" in bodyText ||
            "medvjed" in bodyText || "srnjak" in bodyText || "srndać" in bodyText ||
            "jelena" in bodyText || "jelen" in bodyText
        ) krupnaDivljac = "da"
        if ("zaštićen" in bodyText || "zasticen" in bodyText) zasticenaVrsta = "da"
        if ("bez posebne dozvole" in bodyText) bezPosebneDozvole = "da"
        if ("masovno uništ" in bodyText || "sredstvima masovnog" in bodyText) masovnoUnistavanje = "da"
        if ("vatreno oružje" in bodyText || "lovačku pušku" in bodyText ||
            "lovačka puška" in bodyText || "lovačke puške" in bodyText ||
            ("puška" in bodyText && "kalibra" in bodyText) ||
            "poluautomatsk" in bodyText
        ) vatrenoOruzje = "da"
        if ("oružn" in bodyText &&
            ("bez oružnog lista" in bodyText || "nije imao oružni" in bodyText ||
                "nisu imali izdatu ispravu o oružju" in bodyText ||
                "bez ispravne isprave" in bodyText)
        ) bezOruznogLista = "da"
        if ("bez dozvole za lov" in bodyText || "bez lovačke" in bodyText) bezDozvoleZaLov = "da"
        if ("odstrijel" in bodyText || "odstrelio" in bodyText || "odstreljen" in bodyText ||
            "ubio životinju" in bodyText || "ubio divlj" in bodyText ||
            "ubio vepra" in bodyText || "ubio i" in bodyText
        ) {
            odstreljenaDivljac = "da"
            prisutanUlov = "da"
        }
        // Vrsta divljači — pick first match
        val species = listOf(
            "divlja svinja" to "divlja svinja",
            "divlju svinju" to "divlja svinja",
            "vepra" to "divlja svinja",
            "zeca" to "zec", "zec" to "zec",
            "lisic" to "lisica",
            "srnjak" to "srnjak", "srndać" to "srnjak",
            "patke" to "ptice", "baljuš" to "ptice", "ptice" to "ptice",
            "medvjed" to "medvjed",
            "jelen" to "jelen",
        )
        if (vrstaDivljaci.isEmpty()) {
            vrstaDivljaci = species.firstOrNull { it.first in bodyText }?.second.orEmpty()
        }
        // Lovački psi: search for "<number> lovačk" or "<broj> ker"/"psa" near "lov"
        if (lovackiPsi == 0) {
            DOGS_NUM.find(bodyText)?.let { lovackiPsi = it.groupValues[1].toIntOrNull() ?: lovackiPsi }
            if (lovackiPsi == 0 && ("lovačk" in bodyText && ("ker" in bodyText || "pas" in bodyText || "psa" in bodyText || "pse" in bodyText))) {
                lovackiPsi = 1
            }
        }
        // Broj grla divljači
        if (brojGrlaDivljaci == 0) {
            GAME_COUNT.find(bodyText)?.let { brojGrlaDivljaci = it.groupValues[1].toIntOrNull() ?: brojGrlaDivljaci }
        }
        // Sticaj drugog dela (čl. 403 oružje, čl. 309 životinje)
        if ("čl. 403" in bodyText || "čl.403" in bodyText || "član 403" in bodyText ||
            "čl. 309" in bodyText || "član 309" in bodyText ||
            ("sticaj" in bodyText && "krivičn" in bodyText)
        ) sticajDrugogDela = "da"

        val decisionText = (decisionElement?.textContent ?: "")
        fun firstIntGroup(re: Regex, text: String): Int? = re.find(text)
            ?.groupValues
            ?.drop(1)
            ?.firstOrNull { it.isNotEmpty() && it.toIntOrNull() != null }
            ?.toIntOrNull()
        val kaznaZatvoraMjeseci = (firstIntGroup(MONTHS, decisionText)?.takeIf { it in 1..36 }) ?: p.kaznaZatvoraMjeseci
        val kaznaZatvoraDani = (firstIntGroup(DAYS, decisionText)?.takeIf { it in 1..365 }) ?: p.kaznaZatvoraDani
        val radUJavnomInteresuCasovi = (firstIntGroup(HOURS, decisionText)?.takeIf { it in 1..720 }) ?: p.radUJavnomInteresuCasovi
        val rokProvjereGodine = (firstIntGroup(PROBATION, decisionText)?.takeIf { it in 1..10 }) ?: p.rokProvjereGodine
        val troskoviEur = COSTS.find(decisionText)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull() ?: p.troskoviEur

        return EnvFlags(
            saizvrsilastvo = saizvrsilastvo,
            zabranjenoSredstvo = zabranjenoSredstvo,
            lovostajIliZabranjeneVode = lovostajIliZabranjeneVode,
            velikaKolicina = velikaKolicina,
            elektricnaStruja = elektricnaStruja,
            agregat = agregat,
            sonda = sonda,
            pretvarac = pretvarac,
            prisutanUlov = prisutanUlov,
            kolicinaUlovaKg = kolicinaUlovaKg,
            oduzimanjePredmeta = oduzimanjePredmeta,
            ranijeOsudjivan = ranijeOsudjivan,
            kaznaZatvoraMjeseci = kaznaZatvoraMjeseci,
            kaznaZatvoraDani = kaznaZatvoraDani,
            radUJavnomInteresuCasovi = radUJavnomInteresuCasovi,
            rokProvjereGodine = rokProvjereGodine,
            troskoviEur = troskoviEur,
            tudjeLoviste = tudjeLoviste,
            krupnaDivljac = krupnaDivljac,
            zasticenaVrsta = zasticenaVrsta,
            bezPosebneDozvole = bezPosebneDozvole,
            masovnoUnistavanje = masovnoUnistavanje,
            vatrenoOruzje = vatrenoOruzje,
            lovackiPsi = lovackiPsi,
            bezOruznogLista = bezOruznogLista,
            bezDozvoleZaLov = bezDozvoleZaLov,
            odstreljenaDivljac = odstreljenaDivljac,
            vrstaDivljaci = vrstaDivljaci,
            brojGrlaDivljaci = brojGrlaDivljaci,
            sticajDrugogDela = sticajDrugogDela,
        )
    }

    private fun resolveYear(meta: Element, caseNumber: String): Int {
        val frbrDate = (meta.getElementsByTagNameNS("*", "FRBRdate").item(0) as? Element)?.getAttribute("date")
        if (!frbrDate.isNullOrEmpty()) {
            val y = frbrDate.take(4).toIntOrNull() ?: 0
            if (y in 1991..2099) return y
        }
        val m = Regex("""/(\d+)""").find(caseNumber)
        if (m != null) {
            val py = m.groupValues[1].toInt()
            return when {
                py in 100..2099 -> py
                py <= 30 -> 2000 + py
                py < 100 -> 1900 + py
                else -> 2024
            }
        }
        return 2024
    }

    private fun resolveDate(meta: Element): String? {
        val attr = (meta.getElementsByTagNameNS("*", "FRBRdate").item(0) as? Element)?.getAttribute("date")
        if (attr.isNullOrEmpty()) return null
        val y = attr.take(4).toIntOrNull() ?: return null
        return if (y in 1991..2099) attr else null
    }

    // ----- article-ref helpers -----

    private val WORD_NORMALIZERS = listOf(
        Regex("""član""", RegexOption.IGNORE_CASE) to "čl.",
        Regex("""clan""", RegexOption.IGNORE_CASE) to "čl.",
        Regex("""cl\.""", RegexOption.IGNORE_CASE) to "čl.",
        Regex("""stav""", RegexOption.IGNORE_CASE) to "st.",
    )

    private fun normalizeLegalArticleText(value: String): String {
        var s = value
        for ((re, repl) in WORD_NORMALIZERS) s = s.replace(re, repl)
        return s.replace(Regex("""\s+"""), " ").trim()
    }

    private fun pickMainArticleByCrimeType(typeText: String): Int? {
        val v = typeText.lowercase()
        return when {
            "ribolov" in v || "fish" in v || "pecanje" in v -> 326
            "lov" in v || "hunt" in v -> 325
            else -> null
        }
    }

    private data class ParsedArticleRef(val article: Int, val stav: Int?, val label: String)

    private fun extractCanonicalArticleRef(rawText: String, forcedMainArticle: Int?): ParsedArticleRef? {
        val text = normalizeLegalArticleText(rawText)
        if (text.isEmpty()) return null
        var article: Int? = null
        var stav: Int? = null
        Regex("""\b(325|326)\.\s*(\d{1,2})\b""").find(text)?.let {
            article = it.groupValues[1].toInt()
            stav = it.groupValues[2].toInt()
        }
        if (article == null) {
            Regex("""(?:čl\.?)\s*(\d{2,3})""", RegexOption.IGNORE_CASE).find(text)?.let {
                article = it.groupValues[1].toInt()
            }
        }
        if (article == null) {
            Regex("""\b(325|326)\b""").find(text)?.let { article = it.groupValues[1].toInt() }
        }
        if (article == null && forcedMainArticle != null) article = forcedMainArticle
        val a = article ?: return null
        if (a != 325 && a != 326 && a != 23) return null
        if (stav == null) {
            Regex("""(?:st\.?)\s*(\d{1,2})""", RegexOption.IGNORE_CASE).find(text)?.let {
                stav = it.groupValues[1].toInt()
            }
        }
        if (stav == null) {
            Regex("""\b(325|326)\b[^\d]{0,12}(?:čl\.?|st\.?)\s*(\d{1,2})""", RegexOption.IGNORE_CASE)
                .find(text)?.let { stav = it.groupValues[2].toInt() }
        }
        if (stav == null) {
            val numbers = Regex("""\d+""").findAll(text).map { it.value.toInt() }.toList()
            if (numbers.size >= 2 && (numbers[0] == 325 || numbers[0] == 326) && numbers[1] in 1..10) {
                stav = numbers[1]
            }
        }
        val label = if (stav != null) "$a.$stav" else "$a"
        return ParsedArticleRef(a, stav, label)
    }

    private fun buildAppliedArticles(
        clanKZ: String,
        tipKrivicnogDjela: String,
        references: List<Element>,
        bodyRefs: List<Element>,
        decisionPs: List<Element>,
    ): List<String> {
        val forced = pickMainArticleByCrimeType(tipKrivicnogDjela)
        val candidates = mutableListOf<String>()
        if (clanKZ.isNotEmpty()) candidates += clanKZ
        for (r in references) r.getAttribute("showAs").takeIf { it.isNotEmpty() }?.let { candidates += it }
        for (r in bodyRefs) {
            val href = r.getAttribute("href").orEmpty()
            val m = ART_REF_HREF.find(href) ?: continue
            val artNum = m.groupValues[1].toInt()
            val sav = m.groupValues.getOrNull(2)?.toIntOrNull()
            if (artNum == 325 || artNum == 326 || artNum == 23) {
                candidates += if (sav != null) "$artNum.$sav" else "$artNum"
            }
        }
        for (pEl in decisionPs) (pEl.textContent ?: "").takeIf { it.isNotEmpty() }?.let { candidates += it }
        val seen = mutableSetOf<String>()
        val result = mutableListOf<String>()
        for (c in candidates) {
            val parsed = extractCanonicalArticleRef(c, forced) ?: continue
            val key = "${parsed.article}-${parsed.stav ?: 0}"
            if (seen.add(key)) result += parsed.label
        }
        if (result.isEmpty() && forced != null) result += "$forced"
        return result
    }

    // ----- inner data class for proprietary fields -----

    private data class ProprietaryFields(
        val sudija: String,
        val zapisnicar: String,
        val sud: String,
        val kazna: String,
        val tipKrivicnogDjela: String,
        val uslovnaOsuda: Boolean,
        val brojPredmeta: String,
        val vrstaPresude: String,
        val opisSlucaja: String,
        val clanKZ: String,
        val brojOkrivljenih: Int,
        val brojSvjedoka: Int,
        val brojDokaza: Int,
        val generatedBy: String,
        val ranijeOsudjivan: String,
        val svjedoci: List<String>,
        val dokazi: List<String>,
        val saizvrsilastvo: String,
        val zabranjenoSredstvo: String,
        val lovostajIliZabranjeneVode: String,
        val velikaKolicina: String,
        val elektricnaStruja: String,
        val agregat: String,
        val sonda: String,
        val pretvarac: String,
        val prisutanUlov: String,
        val kolicinaUlovaKg: Double,
        val oduzimanjePredmeta: String,
        val kaznaZatvoraMjeseci: Int,
        val kaznaZatvoraDani: Int,
        val radUJavnomInteresuCasovi: Int,
        val rokProvjereGodine: Int,
        val troskoviEur: Double,
        val tudjeLoviste: String,
        val krupnaDivljac: String,
        val zasticenaVrsta: String,
        val bezPosebneDozvole: String,
        val masovnoUnistavanje: String,
        val vatrenoOruzje: String,
        val lovackiPsi: Int,
        val bezOruznogLista: String,
        val bezDozvoleZaLov: String,
        val odstreljenaDivljac: String,
        val vrstaDivljaci: String,
        val brojGrlaDivljaci: Int,
        val sticajDrugogDela: String,
    ) {
        companion object {
            fun from(el: Element?): ProprietaryFields {
                fun t(name: String) = el?.let {
                    val list = it.getElementsByTagNameNS("*", name)
                    if (list.length > 0) (list.item(0).textContent ?: "").trim() else ""
                } ?: ""
                fun tList(parent: String, child: String): List<String> {
                    val parentEl = el?.getElementsByTagNameNS("*", parent)?.item(0) as? Element ?: return emptyList()
                    val nodes = parentEl.getElementsByTagNameNS("*", child)
                    val out = mutableListOf<String>()
                    for (i in 0 until nodes.length) out += (nodes.item(i).textContent ?: "").trim()
                    return out
                }
                return ProprietaryFields(
                    sudija = t("sudija").ifEmpty { "Nepoznat" },
                    zapisnicar = t("zapisnicar").ifEmpty { "Nepoznat" },
                    sud = t("sud").ifEmpty { "Nepoznat" },
                    kazna = t("kazna"),
                    tipKrivicnogDjela = t("tipKrivicnogDjela"),
                    uslovnaOsuda = t("uslovnaOsuda") == "Da",
                    brojPredmeta = t("brojPredmeta"),
                    vrstaPresude = t("vrstaPresude"),
                    opisSlucaja = t("opisSlucaja"),
                    clanKZ = t("clanKZ"),
                    brojOkrivljenih = t("brojOkrivljenih").ifEmpty { "1" }.toIntOrNull() ?: 1,
                    brojSvjedoka = t("brojSvjedoka").ifEmpty { "0" }.toIntOrNull() ?: 0,
                    brojDokaza = t("brojDokaza").ifEmpty { "0" }.toIntOrNull() ?: 0,
                    generatedBy = t("generatedBy"),
                    ranijeOsudjivan = t("ranijeOsudjivan"),
                    svjedoci = tList("svjedoci", "svjedok"),
                    dokazi = tList("dokazi", "dokaz"),
                    saizvrsilastvo = t("saizvrsilastvo").ifEmpty { "ne" },
                    zabranjenoSredstvo = t("zabranjenoSredstvo").ifEmpty { "ne" },
                    lovostajIliZabranjeneVode = t("lovostajIliZabranjeneVode").ifEmpty { "ne" },
                    velikaKolicina = t("velikaKolicina").ifEmpty { "ne" },
                    elektricnaStruja = t("elektricnaStruja").ifEmpty { "ne" },
                    agregat = t("agregat").ifEmpty { "ne" },
                    sonda = t("sonda").ifEmpty { "ne" },
                    pretvarac = t("pretvarac").ifEmpty { "ne" },
                    prisutanUlov = t("prisutanUlov").ifEmpty { "ne" },
                    kolicinaUlovaKg = t("kolicinaUlovaKg").ifEmpty { "0" }.toDoubleOrNull() ?: 0.0,
                    oduzimanjePredmeta = t("oduzimanjePredmeta").ifEmpty { "ne" },
                    kaznaZatvoraMjeseci = t("kaznaZatvoraMjeseci").ifEmpty { "0" }.toIntOrNull() ?: 0,
                    kaznaZatvoraDani = t("kaznaZatvoraDani").ifEmpty { "0" }.toIntOrNull() ?: 0,
                    radUJavnomInteresuCasovi = t("radUJavnomInteresuCasovi").ifEmpty { "0" }.toIntOrNull() ?: 0,
                    rokProvjereGodine = t("rokProvjereGodine").ifEmpty { "0" }.toIntOrNull() ?: 0,
                    troskoviEur = t("troskoviEur").ifEmpty { "0" }.toDoubleOrNull() ?: 0.0,
                    tudjeLoviste = t("tudjeLoviste").ifEmpty { "ne" },
                    krupnaDivljac = t("krupnaDivljac").ifEmpty { "ne" },
                    zasticenaVrsta = t("zasticenaVrsta").ifEmpty { "ne" },
                    bezPosebneDozvole = t("bezPosebneDozvole").ifEmpty { "ne" },
                    masovnoUnistavanje = t("masovnoUnistavanje").ifEmpty { "ne" },
                    vatrenoOruzje = t("vatrenoOruzje").ifEmpty { "ne" },
                    lovackiPsi = t("lovackiPsi").ifEmpty { "0" }.toIntOrNull() ?: 0,
                    bezOruznogLista = t("bezOruznogLista").ifEmpty { "ne" },
                    bezDozvoleZaLov = t("bezDozvoleZaLov").ifEmpty { "ne" },
                    odstreljenaDivljac = t("odstreljenaDivljac").ifEmpty { "ne" },
                    vrstaDivljaci = t("vrstaDivljaci"),
                    brojGrlaDivljaci = t("brojGrlaDivljaci").ifEmpty { "0" }.toIntOrNull() ?: 0,
                    sticajDrugogDela = t("sticajDrugogDela").ifEmpty { "ne" },
                )
            }
        }
    }

    // ----- regexes pulled out for readability -----
    private val ART_REF_HREF = Regex("""art_(\d+)(?:_para_(\d+))?""")
    // Stricter sentence regexes: require either a parenthesized verbal numeral
    // or an explicit "trajanju od X" / "po X" context. Avoids capturing dates,
    // article numbers, or random integers nearby words like "dana" or "časa".
    private val MONTHS = Regex(
        """(\d+)\s*\((?:tri|dva|jednog?|četiri|pet|šest|sedam|osam|devet|deset|dvanaest|petnaest|dvadeset)\)\s*mjesec|trajanju\s+od\s+(?:po\s+)?(\d+)\s*\(?[^)]*\)?\s*mjesec""",
        RegexOption.IGNORE_CASE
    )
    private val DAYS = Regex(
        """trajanju\s+od\s+(?:po\s+)?(\d+)\s*\(?[^)]*\)?\s*dan|(\d+)\s*\((?:jedan|dva|tri|četiri|pet|deset|petnaest|dvadeset|trideset|četrdeset|pedeset|šezdeset|sedamdeset|osamdeset|devedeset|stotinu)[^)]*\)\s*dan""",
        RegexOption.IGNORE_CASE
    )
    private val HOURS = Regex(
        """trajanju\s+od\s+(?:po\s+)?(\d+)\s*\(?[^)]*\)?\s*časov|(\d+)\s*\((?:šezdeset|stotinu|dvije?stotine?|tri[a-zšđčćž]*|dvije?st)[^)]*\)\s*časov""",
        RegexOption.IGNORE_CASE
    )
    private val PROBATION = Regex(
        """(?:rok\s+(?:provjere|provere|provjeravanja).*?|u\s+roku\s+od|za\s+vrijeme\s+od|period(?:u)?\s+(?:od|provjere)\s*)\s*(\d+)\s*\(?[^)]*\)?\s*godin""",
        RegexOption.IGNORE_CASE
    )
    private val COSTS = Regex("""(\d+(?:[.,]\d+)?)\s*(?:eura?|€|EUR)""", RegexOption.IGNORE_CASE)
    private val WEIGHT_KG = Regex("""(\d+(?:[.,]\d+)?)\s*kg""")
    private val SENTENCE_NUMBER = Regex("""(\d+)""")
    // "3 lovačka kera", "2 lovačka psa", "tri lovačka kera" — capture leading number
    private val DOGS_NUM = Regex("""(\d+)\s*(?:/[^/]+/\s*)?lovačk[aiou]?\s*(?:ker|psa|pse|pasa|kera|kerova)""", RegexOption.IGNORE_CASE)
    // "ulovili pet baljuški", "ulovio 1 zec", "10 komada karaša", "ubio 2 vepra"
    private val GAME_COUNT = Regex("""(?:ulov(?:io|ili|ljen[ao]?)?|ubio|odstrijel(?:io|ili)?)\s*(\d+)\s*(?:kom(?:ada|adi)?|grla?|zec|vepr|svinj|patke|ptic)""", RegexOption.IGNORE_CASE)
}

private fun org.w3c.dom.NodeList.asElementSeq(): Sequence<Element> = sequence {
    for (i in 0 until length) {
        val n: Node = item(i)
        if (n is Element) yield(n)
    }
}
