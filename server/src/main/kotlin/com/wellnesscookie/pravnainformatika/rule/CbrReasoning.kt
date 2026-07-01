package com.wellnesscookie.pravnainformatika.rule

import com.wellnesscookie.pravnainformatika.db.CbrCase
import kotlin.math.abs
import kotlin.math.max

object CbrReasoning {

    private val weights = mapOf(
        "tipKrivicnogDjela" to 5.0,
        "clanKZ" to 4.0,
        "saizvrsilastvo" to 3.0,
        // Fishing-specific
        "zabranjenoSredstvo" to 5.0,
        "elektricnaStruja" to 3.5,
        "agregat" to 2.0,
        "sonda" to 2.0,
        "pretvarac" to 1.5,
        // Shared
        "lovostajIliZabranjeneVode" to 4.0,
        "velikaKolicina" to 4.0,
        "prisutanUlov" to 3.0,
        "kolicinaUlovaKg" to 6.0,
        "oduzimanjePredmeta" to 2.5,
        "ranijeOsudjivan" to 4.0,
        "brojOkrivljenih" to 1.5,
        "brojSvjedoka" to 1.0,
        // Hunting-specific (čl. 325)
        "tudjeLoviste" to 4.5,
        "krupnaDivljac" to 4.0,
        "zasticenaVrsta" to 4.0,
        "bezPosebneDozvole" to 3.5,
        "masovnoUnistavanje" to 4.0,
        "vatrenoOruzje" to 3.0,
        "lovackiPsi" to 1.5,
        "bezOruznogLista" to 2.5,
        "bezDozvoleZaLov" to 3.5,
        "odstreljenaDivljac" to 4.0,
        "brojGrlaDivljaci" to 3.0,
        "sticajDrugogDela" to 2.5,
    )

    private val fishingOnly = setOf(
        "zabranjenoSredstvo", "elektricnaStruja", "agregat", "sonda", "pretvarac",
    )
    private val huntingOnly = setOf(
        "tudjeLoviste", "krupnaDivljac", "zasticenaVrsta", "bezPosebneDozvole",
        "masovnoUnistavanje", "vatrenoOruzje", "lovackiPsi",
        "bezOruznogLista", "bezDozvoleZaLov", "odstreljenaDivljac",
        "brojGrlaDivljaci", "sticajDrugogDela",
    )

    private val categorical = listOf("tipKrivicnogDjela", "clanKZ")
    private val booleans = listOf(
        "saizvrsilastvo", "zabranjenoSredstvo", "lovostajIliZabranjeneVode",
        "velikaKolicina", "elektricnaStruja", "agregat", "sonda", "pretvarac",
        "prisutanUlov", "oduzimanjePredmeta", "ranijeOsudjivan",
        "tudjeLoviste", "krupnaDivljac", "zasticenaVrsta", "bezPosebneDozvole",
        "masovnoUnistavanje", "vatrenoOruzje", "bezOruznogLista",
        "bezDozvoleZaLov", "odstreljenaDivljac", "sticajDrugogDela",
    )
    private val numeric = listOf(
        "kolicinaUlovaKg" to 50.0,
        "brojOkrivljenih" to 5.0,
        "brojSvjedoka" to 10.0,
        "lovackiPsi" to 5.0,
        "brojGrlaDivljaci" to 10.0,
    )

    /** Returns the weight to use for `attr` given the query's crime type.
     * Hunting-only attrs are nulled for fishing queries and vice-versa, so
     * similarity isn't distorted by irrelevant zero-vs-zero matches. */
    private fun weightFor(attr: String, queryTip: String): Double {
        val baseWeight = weights[attr] ?: 1.0
        val tip = queryTip.lowercase()
        return when {
            attr in fishingOnly && "lov" in tip && "ribolov" !in tip -> 0.0
            attr in huntingOnly && "ribolov" in tip -> 0.0
            else -> baseWeight
        }
    }

    fun computeSimilarity(query: CbrCase, target: CbrCase): Double {
        var total = 0.0
        var weighted = 0.0
        val tip = query.tipKrivicnogDjela

        for (attr in categorical) {
            val w = weightFor(attr, tip)
            if (w <= 0.0) continue
            total += w
            weighted += w * exactMatch(field(query, attr), field(target, attr))
        }
        for (attr in booleans) {
            val w = weightFor(attr, tip)
            if (w <= 0.0) continue
            total += w
            weighted += w * booleanSimilarity(field(query, attr), field(target, attr))
        }
        for ((attr, maxDiff) in numeric) {
            val w = weightFor(attr, tip)
            if (w <= 0.0) continue
            total += w
            weighted += w * numericSimilarity(field(query, attr), field(target, attr), maxDiff)
        }
        return if (total > 0.0) weighted / total else 0.0
    }

    fun formatCourtName(city: String?): String {
        if (city.isNullOrBlank()) return "Nepoznat"
        val map = mapOf(
            "rožaje" to "Rožajama", "rozaje" to "Rožajama",
            "podgorica" to "Podgorici", "podgoric" to "Podgorici",
            "nikšić" to "Nikšiću", "niksic" to "Nikšiću",
            "bar" to "Baru", "kotor" to "Kotoru",
            "cetinje" to "Cetinju", "cetinj" to "Cetinju",
            "herceg novi" to "Herceg Novom", "herceg" to "Herceg Novom",
            "bijelo polje" to "Bijelom Polju", "bijel" to "Bijelom Polju",
            "pljevlja" to "Pljevljima", "pljevlj" to "Pljevljima",
            "plav" to "Plavu", "ulcinj" to "Ulcinju",
            "danilovgrad" to "Danilovgradu",
            "kolašin" to "Kolašinu", "kolasin" to "Kolašinu",
            "berane" to "Beranama", "berana" to "Beranama",
        )
        val low = city.lowercase().trim()
        val locative = map.entries.firstOrNull { (k, _) -> k in low }?.value
        return if (locative != null) "Osnovni sud u $locative" else "Osnovni sud u $city"
    }

    fun displayVerdict(v: String?): String {
        if (v.isNullOrBlank()) return "nepoznat"
        val low = v.lowercase().trim()
        return when {
            low in setOf("osudjujuca", "osuđujuća", "osuđujuca", "zatvorska kazna") -> "zatvorska kazna"
            low in setOf("uslovna", "uslovna osuda", "uslovna presuda") -> "uslovna presuda"
            low in setOf("oslobadjajuca", "oslobađajuća", "oslobađajuca", "oslobođen", "oslobođena") -> "oslobadjajuca"
            else -> low
        }
    }

    private fun exactMatch(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty() || a == "nepoznat" || b == "nepoznat") return 0.5
        return if (a.equals(b, ignoreCase = true)) 1.0 else 0.0
    }

    private fun booleanSimilarity(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty() || a == "nepoznat" || b == "nepoznat") return 0.5
        return if (a.equals(b, ignoreCase = true)) 1.0 else 0.0
    }

    private fun numericSimilarity(a: String, b: String, maxDiff: Double): Double {
        val na = a.toDoubleOrNull()
        val nb = b.toDoubleOrNull()
        if (na == null || nb == null) return 0.5
        return max(0.0, 1.0 - abs(na - nb) / maxDiff)
    }

    private fun field(c: CbrCase, name: String): String = when (name) {
        "tipKrivicnogDjela" -> c.tipKrivicnogDjela
        "clanKZ" -> c.clanKZ
        "saizvrsilastvo" -> c.saizvrsilastvo
        "zabranjenoSredstvo" -> c.zabranjenoSredstvo
        "lovostajIliZabranjeneVode" -> c.lovostajIliZabranjeneVode
        "velikaKolicina" -> c.velikaKolicina
        "elektricnaStruja" -> c.elektricnaStruja
        "agregat" -> c.agregat
        "sonda" -> c.sonda
        "pretvarac" -> c.pretvarac
        "prisutanUlov" -> c.prisutanUlov
        "kolicinaUlovaKg" -> c.kolicinaUlovaKg
        "oduzimanjePredmeta" -> c.oduzimanjePredmeta
        "ranijeOsudjivan" -> c.ranijeOsudjivan
        "brojOkrivljenih" -> c.brojOkrivljenih
        "brojSvjedoka" -> c.brojSvjedoka
        "tudjeLoviste" -> c.tudjeLoviste
        "krupnaDivljac" -> c.krupnaDivljac
        "zasticenaVrsta" -> c.zasticenaVrsta
        "bezPosebneDozvole" -> c.bezPosebneDozvole
        "masovnoUnistavanje" -> c.masovnoUnistavanje
        "vatrenoOruzje" -> c.vatrenoOruzje
        "lovackiPsi" -> c.lovackiPsi
        "bezOruznogLista" -> c.bezOruznogLista
        "bezDozvoleZaLov" -> c.bezDozvoleZaLov
        "odstreljenaDivljac" -> c.odstreljenaDivljac
        "brojGrlaDivljaci" -> c.brojGrlaDivljaci
        "sticajDrugogDela" -> c.sticajDrugogDela
        else -> ""
    }
}
