package com.wellnesscookie.pravnainformatika.db

import com.wellnesscookie.pravnainformatika.model.CaseRecord
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

class CbrStore(private val resourcesRoot: File) {

    private val cases: MutableList<CbrCase> = CopyOnWriteArrayList()

    fun all(): List<CbrCase> = cases.toList()

    fun load() {
        val csv = File(
            resourcesRoot,
            "case_reasoning/presude-cbr/src/main/resources/presude.csv",
        )
        if (!csv.exists()) {
            println("[CbrStore] presude.csv not found at ${csv.absolutePath}")
            return
        }
        csv.useLines { lines ->
            val iter = lines.iterator()
            if (!iter.hasNext()) return@useLines
            val header = iter.next().removePrefix("#").trim().split(";").map { it.trim() }
            for (line in iter) {
                if (line.isBlank()) continue
                val values = line.split(";").map { it.trim() }
                if (values.size < header.size) continue
                cases += rowFrom(header, values)
            }
        }
        println("[CbrStore] loaded ${cases.size} CBR cases")
    }

    fun loadGeneratedFrom(caseDatabase: List<CaseRecord>) {
        var count = 0
        for (c in caseDatabase) {
            if (c.generatedBy.isNotEmpty()) {
                cases += buildCbrRecordFromParsed(c)
                count++
            }
        }
        if (count > 0) println("[CbrStore] loaded $count generated cases from XML")
    }

    fun byBrojPredmeta(broj: String): CbrCase? = cases.firstOrNull { it.brojPredmeta == broj }
    fun byId(id: String): CbrCase? = cases.firstOrNull { it.id == id }

    fun replaceOrAdd(rec: CbrCase) {
        val idx = cases.indexOfFirst { it.brojPredmeta == rec.brojPredmeta }
        if (idx >= 0) cases[idx] = rec else cases += rec
    }

    fun remove(broj: String): Boolean {
        val idx = cases.indexOfFirst { it.brojPredmeta == broj }
        if (idx < 0) return false
        cases.removeAt(idx); return true
    }

    private fun rowFrom(header: List<String>, values: List<String>): CbrCase {
        val map = header.mapIndexed { i, h -> h to (values.getOrNull(i).orEmpty()) }.toMap()
        return CbrCase(
            id = map["id"].orEmpty(),
            sud = map["sud"]?.takeIf { it.isNotEmpty() } ?: "Nepoznat",
            brojPredmeta = map["brojPredmeta"].orEmpty(),
            tipKrivicnogDjela = map["tipKrivicnogDjela"]?.takeIf { it.isNotEmpty() } ?: "nezakonit ribolov",
            clanKZ = map["clanKZ"]?.takeIf { it.isNotEmpty() } ?: "326.2",
            saizvrsilastvo = map["saizvrsilastvo"]?.takeIf { it.isNotEmpty() } ?: "ne",
            zabranjenoSredstvo = map["zabranjenoSredstvo"]?.takeIf { it.isNotEmpty() } ?: "ne",
            lovostajIliZabranjeneVode = map["lovostajIliZabranjeneVode"]?.takeIf { it.isNotEmpty() } ?: "ne",
            velikaKolicina = map["velikaKolicina"]?.takeIf { it.isNotEmpty() } ?: "ne",
            elektricnaStruja = map["elektricnaStruja"]?.takeIf { it.isNotEmpty() } ?: "ne",
            agregat = map["agregat"]?.takeIf { it.isNotEmpty() } ?: "ne",
            sonda = map["sonda"]?.takeIf { it.isNotEmpty() } ?: "ne",
            pretvarac = map["pretvarac"]?.takeIf { it.isNotEmpty() } ?: "ne",
            prisutanUlov = map["prisutanUlov"]?.takeIf { it.isNotEmpty() } ?: "ne",
            kolicinaUlovaKg = map["kolicinaUlovaKg"]?.takeIf { it.isNotEmpty() } ?: "0",
            oduzimanjePredmeta = map["oduzimanjePredmeta"]?.takeIf { it.isNotEmpty() } ?: "ne",
            ranijeOsudjivan = map["ranijeOsudjivan"]?.takeIf { it.isNotEmpty() } ?: "ne",
            kaznaZatvoraMjeseci = map["kaznaZatvoraMjeseci"]?.takeIf { it.isNotEmpty() } ?: "0",
            kaznaZatvoraDani = map["kaznaZatvoraDani"]?.takeIf { it.isNotEmpty() } ?: "0",
            radUJavnomInteresuCasovi = map["radUJavnomInteresuCasovi"]?.takeIf { it.isNotEmpty() } ?: "0",
            rokProvjereGodine = map["rokProvjereGodine"]?.takeIf { it.isNotEmpty() } ?: "0",
            troskoviEur = map["troskoviEur"]?.takeIf { it.isNotEmpty() } ?: "0",
            vrstaPresude = map["vrstaPresude"]?.takeIf { it.isNotEmpty() } ?: "nepoznat",
            uslovnaOsuda = map["uslovnaOsuda"]?.takeIf { it.isNotEmpty() } ?: "Ne",
            brojOkrivljenih = map["brojOkrivljenih"]?.takeIf { it.isNotEmpty() } ?: "1",
            brojSvjedoka = map["brojSvjedoka"]?.takeIf { it.isNotEmpty() } ?: "0",
            tudjeLoviste = map["tudjeLoviste"]?.takeIf { it.isNotEmpty() } ?: "ne",
            krupnaDivljac = map["krupnaDivljac"]?.takeIf { it.isNotEmpty() } ?: "ne",
            zasticenaVrsta = map["zasticenaVrsta"]?.takeIf { it.isNotEmpty() } ?: "ne",
            bezPosebneDozvole = map["bezPosebneDozvole"]?.takeIf { it.isNotEmpty() } ?: "ne",
            masovnoUnistavanje = map["masovnoUnistavanje"]?.takeIf { it.isNotEmpty() } ?: "ne",
            vatrenoOruzje = map["vatrenoOruzje"]?.takeIf { it.isNotEmpty() } ?: "ne",
            lovackiPsi = map["lovackiPsi"]?.takeIf { it.isNotEmpty() } ?: "0",
            bezOruznogLista = map["bezOruznogLista"]?.takeIf { it.isNotEmpty() } ?: "ne",
            bezDozvoleZaLov = map["bezDozvoleZaLov"]?.takeIf { it.isNotEmpty() } ?: "ne",
            odstreljenaDivljac = map["odstreljenaDivljac"]?.takeIf { it.isNotEmpty() } ?: "ne",
            vrstaDivljaci = map["vrstaDivljaci"].orEmpty(),
            brojGrlaDivljaci = map["brojGrlaDivljaci"]?.takeIf { it.isNotEmpty() } ?: "0",
            sticajDrugogDela = map["sticajDrugogDela"]?.takeIf { it.isNotEmpty() } ?: "ne",
        )
    }

    companion object {
        fun normalizeCbrQuery(raw: Map<String, String>): CbrCase = CbrCase(
            brojPredmeta = raw["brojPredmeta"]?.ifEmpty { null } ?: "NOVI-${System.currentTimeMillis()}",
            tipKrivicnogDjela = raw["tipKrivicnogDjela"]?.ifEmpty { null } ?: "nezakonit ribolov",
            clanKZ = raw["clanKZ"]?.ifEmpty { null } ?: "326.2",
            saizvrsilastvo = raw["saizvrsilastvo"]?.ifEmpty { null } ?: "ne",
            zabranjenoSredstvo = raw["zabranjenoSredstvo"]?.ifEmpty { null } ?: "ne",
            lovostajIliZabranjeneVode = raw["lovostajIliZabranjeneVode"]?.ifEmpty { null } ?: "ne",
            velikaKolicina = raw["velikaKolicina"]?.ifEmpty { null } ?: "ne",
            elektricnaStruja = raw["elektricnaStruja"]?.ifEmpty { null } ?: "ne",
            agregat = raw["agregat"]?.ifEmpty { null } ?: "ne",
            sonda = raw["sonda"]?.ifEmpty { null } ?: "ne",
            pretvarac = raw["pretvarac"]?.ifEmpty { null } ?: "ne",
            prisutanUlov = raw["prisutanUlov"]?.ifEmpty { null } ?: "ne",
            kolicinaUlovaKg = raw["kolicinaUlovaKg"]?.ifEmpty { null } ?: "0",
            oduzimanjePredmeta = raw["oduzimanjePredmeta"]?.ifEmpty { null } ?: "ne",
            ranijeOsudjivan = raw["ranijeOsudjivan"]?.ifEmpty { null } ?: "ne",
            kaznaZatvoraMjeseci = raw["kaznaZatvoraMjeseci"]?.ifEmpty { null } ?: "0",
            kaznaZatvoraDani = raw["kaznaZatvoraDani"]?.ifEmpty { null } ?: "0",
            radUJavnomInteresuCasovi = raw["radUJavnomInteresuCasovi"]?.ifEmpty { null } ?: "0",
            rokProvjereGodine = raw["rokProvjereGodine"]?.ifEmpty { null } ?: "0",
            troskoviEur = raw["troskoviEur"]?.ifEmpty { null } ?: "0",
            vrstaPresude = raw["vrstaPresude"]?.ifEmpty { null } ?: "nepoznat",
            uslovnaOsuda = raw["uslovnaOsuda"]?.ifEmpty { null } ?: "Ne",
            brojOkrivljenih = raw["brojOkrivljenih"]?.ifEmpty { null } ?: "1",
            brojSvjedoka = raw["brojSvjedoka"]?.ifEmpty { null } ?: "0",
            tudjeLoviste = raw["tudjeLoviste"]?.ifEmpty { null } ?: "ne",
            krupnaDivljac = raw["krupnaDivljac"]?.ifEmpty { null } ?: "ne",
            zasticenaVrsta = raw["zasticenaVrsta"]?.ifEmpty { null } ?: "ne",
            bezPosebneDozvole = raw["bezPosebneDozvole"]?.ifEmpty { null } ?: "ne",
            masovnoUnistavanje = raw["masovnoUnistavanje"]?.ifEmpty { null } ?: "ne",
            vatrenoOruzje = raw["vatrenoOruzje"]?.ifEmpty { null } ?: "ne",
            lovackiPsi = raw["lovackiPsi"]?.ifEmpty { null } ?: "0",
            bezOruznogLista = raw["bezOruznogLista"]?.ifEmpty { null } ?: "ne",
            bezDozvoleZaLov = raw["bezDozvoleZaLov"]?.ifEmpty { null } ?: "ne",
            odstreljenaDivljac = raw["odstreljenaDivljac"]?.ifEmpty { null } ?: "ne",
            vrstaDivljaci = raw["vrstaDivljaci"].orEmpty(),
            brojGrlaDivljaci = raw["brojGrlaDivljaci"]?.ifEmpty { null } ?: "0",
            sticajDrugogDela = raw["sticajDrugogDela"]?.ifEmpty { null } ?: "ne",
        )

        fun buildCbrRecordFromParsed(c: CaseRecord): CbrCase = CbrCase(
            id = c.caseNumber,
            sud = c.court.ifEmpty { "Nepoznat" },
            brojPredmeta = c.caseNumber,
            tipKrivicnogDjela = c.tipKrivicnogDjela.ifEmpty { c.type }.ifEmpty { "nezakonit ribolov" },
            clanKZ = c.clanKZ.ifEmpty { "326.2" },
            saizvrsilastvo = c.saizvrsilastvo.ifEmpty { "ne" },
            zabranjenoSredstvo = c.zabranjenoSredstvo.ifEmpty { "ne" },
            lovostajIliZabranjeneVode = c.lovostajIliZabranjeneVode.ifEmpty { "ne" },
            velikaKolicina = c.velikaKolicina.ifEmpty { "ne" },
            elektricnaStruja = c.elektricnaStruja.ifEmpty { "ne" },
            agregat = c.agregat.ifEmpty { "ne" },
            sonda = c.sonda.ifEmpty { "ne" },
            pretvarac = c.pretvarac.ifEmpty { "ne" },
            prisutanUlov = c.prisutanUlov.ifEmpty { "ne" },
            kolicinaUlovaKg = c.kolicinaUlovaKg.toString(),
            oduzimanjePredmeta = c.oduzimanjePredmeta.ifEmpty { "ne" },
            ranijeOsudjivan = c.ranijeOsudjivan.ifEmpty { "ne" },
            kaznaZatvoraMjeseci = (if (c.kaznaZatvoraMjeseci > 0) c.kaznaZatvoraMjeseci else c.sentenceMonths).toString(),
            kaznaZatvoraDani = c.kaznaZatvoraDani.toString(),
            radUJavnomInteresuCasovi = c.radUJavnomInteresuCasovi.toString(),
            rokProvjereGodine = c.rokProvjereGodine.toString(),
            troskoviEur = c.troskoviEur.toString(),
            vrstaPresude = c.verdict.ifEmpty { "nepoznat" },
            uslovnaOsuda = if (c.uslovnaOsuda) "Da" else "Ne",
            brojOkrivljenih = c.brojOkrivljenih.toString(),
            brojSvjedoka = c.brojSvjedoka.toString(),
            tudjeLoviste = c.tudjeLoviste.ifEmpty { "ne" },
            krupnaDivljac = c.krupnaDivljac.ifEmpty { "ne" },
            zasticenaVrsta = c.zasticenaVrsta.ifEmpty { "ne" },
            bezPosebneDozvole = c.bezPosebneDozvole.ifEmpty { "ne" },
            masovnoUnistavanje = c.masovnoUnistavanje.ifEmpty { "ne" },
            vatrenoOruzje = c.vatrenoOruzje.ifEmpty { "ne" },
            lovackiPsi = c.lovackiPsi.toString(),
            bezOruznogLista = c.bezOruznogLista.ifEmpty { "ne" },
            bezDozvoleZaLov = c.bezDozvoleZaLov.ifEmpty { "ne" },
            odstreljenaDivljac = c.odstreljenaDivljac.ifEmpty { "ne" },
            vrstaDivljaci = c.vrstaDivljaci,
            brojGrlaDivljaci = c.brojGrlaDivljaci.toString(),
            sticajDrugogDela = c.sticajDrugogDela.ifEmpty { "ne" },
        )
    }
}
