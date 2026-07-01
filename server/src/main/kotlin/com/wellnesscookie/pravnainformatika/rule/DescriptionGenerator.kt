package com.wellnesscookie.pravnainformatika.rule

import com.wellnesscookie.pravnainformatika.model.JudgmentInput

/**
 * Builds the human-readable case-description (`opis slucaja`) from the form fields.
 * Port of `web/src/routes/judgmentRoutes.js` `/api/generate-description`.
 */
object DescriptionGenerator {

    fun generate(input: JudgmentInput): String {
        val okrivljeni = input.okrivljeni.ifEmpty { "NN" }
        val sud = input.sud.ifEmpty { "Podgorici" }
        val sudLabel = "u $sud"
        val datumLabel = if (input.datumPresude.isNotEmpty()) "dana ${input.datumPresude}" else ""

        val tip = input.tipKrivicnogDjela
        val low = tip.lowercase()
        val isRibolov = "ribolov" in low
        val isLov = !isRibolov && "lov" in low

        val parts = mutableListOf<String>()

        if (isLov) {
            var action = "izvršio krivično djelo nezakonitog lova iz ${input.clanKZ} Krivičnog zakonika Crne Gore"
            val circumstances = buildList {
                if (input.lovostajIliZabranjeneVode == "da") add("u vrijeme lovostaja ili na području gdje je lov zabranjen")
                if (input.tudjeLoviste == "da") add("u tuđem lovištu bez odobrenja korisnika lovišta")
                if (input.bezDozvoleZaLov == "da") add("bez dozvole za lov")
                if (input.bezPosebneDozvole == "da") add("bez posebne dozvole za lov određene vrste")
            }
            if (circumstances.isNotEmpty()) action += " " + circumstances.joinToString(", ")
            val means = buildList {
                if (input.vatrenoOruzje == "da") add("koristeći vatreno oružje")
                if (input.lovackiPsi > 0) {
                    val psiText = if (input.lovackiPsi == 1) "jednog lovačkog psa" else "${input.lovackiPsi} lovačkih pasa"
                    add("koristeći $psiText")
                }
                if (input.masovnoUnistavanje == "da") add("koristeći sredstva za masovno uništavanje divljači")
                if (input.zabranjenoSredstvo == "da" && isEmpty()) add("zabranjenim sredstvom za lov")
            }
            if (means.isNotEmpty()) action += ", ${means.joinToString(", ")}"
            if (input.odstreljenaDivljac == "da") {
                val vrsta = input.vrstaDivljaci.ifEmpty { "divljač" }
                // Broj grla je opcionalna kvantifikacija; izostavi je ako je 1
                // (gramatičko slaganje sa vrstom je u tom slučaju glomazno).
                val game = if (input.brojGrlaDivljaci > 1) "${input.brojGrlaDivljaci} komada ($vrsta)" else vrsta
                action += ", odstrijelivši $game"
                if (input.kolicinaUlovaKg > 0) action += " težine oko ${input.kolicinaUlovaKg} kg"
            } else if (input.krupnaDivljac == "da") {
                action += ", radi se o krupnoj divljači"
            } else if (input.zasticenaVrsta == "da") {
                action += ", radi se o zaštićenoj vrsti divljači"
            }
            if (input.bezOruznogLista == "da") action += ". Oružje nije imalo izdat oružni list"
            parts += "Optuženi $okrivljeni se tereti da je $sudLabel${if (datumLabel.isNotEmpty()) ", $datumLabel" else ""}, $action."
            if (input.sticajDrugogDela == "da") {
                parts += "Djelo je izvršeno u sticaju sa drugim krivičnim djelom (npr. nedozvoljeno držanje oružja iz čl. 403 ili ubijanje životinja iz čl. 309 KZ)."
            }
        } else {
            var action = "izvršio krivično djelo nezakonitog ribolova iz ${input.clanKZ} Krivičnog zakonika Crne Gore"
            val methods = buildList {
                if (input.elektricnaStruja == "da") add("korišćenjem električne struje")
                if (input.agregat == "da") add("korišćenjem elektroagregata")
                if (input.sonda == "da") add("korišćenjem sonde")
                if (input.pretvarac == "da") add("korišćenjem pretvarača")
                if (input.zabranjenoSredstvo == "da" && isEmpty()) add("zabranjenim sredstvom za ribolov")
            }
            if (methods.isNotEmpty()) action += ", ${methods.joinToString(", ")}"
            if (input.lovostajIliZabranjeneVode == "da") action += ", u vrijeme lovostaja ili u zabranjenim vodama"
            if (input.velikaKolicina == "da") action += ", pri čemu je ulovljena velika količina ribe"
            if (input.kolicinaUlovaKg > 0) action += " (${input.kolicinaUlovaKg} kg)"
            parts += "Optuženi $okrivljeni se tereti da je $sudLabel${if (datumLabel.isNotEmpty()) ", $datumLabel" else ""}, $action."
        }

        // Skip generic ulov sentence for hunting cases where the kill is already in the action sentence.
        val ulovAlreadyMentioned = isLov && (input.odstreljenaDivljac == "da" || input.brojGrlaDivljaci > 0)
        if (input.prisutanUlov == "da" && input.kolicinaUlovaKg > 0 && !ulovAlreadyMentioned) {
            parts += "Na licu mjesta je zatečen ulov u količini od ${input.kolicinaUlovaKg} kg."
        }
        if (input.oduzimanjePredmeta == "da") {
            parts += "Predmeti korišćeni za izvršenje krivičnog djela su oduzeti."
        }
        if (input.saizvrsilastvo == "da") {
            parts += "Djelo je izvršeno u saizvršilaštvu u smislu člana 23 stav 2 Krivičnog zakonika."
        }
        when (input.ranijeOsudjivan.lowercase()) {
            "da" -> parts += "Okrivljeni je ranije osuđivan."
            "ne" -> parts += "Okrivljeni ranije nije osuđivan."
        }
        if (input.zaposlenost.lowercase() != "nepoznat") {
            val label = when (input.zaposlenost.lowercase()) {
                "zaposlen" -> "zaposlen"
                "nezaposlen" -> "nezaposlen"
                "student" -> "student"
                "penzioner" -> "penzioner"
                else -> input.zaposlenost
            }
            parts += "Okrivljeni je $label."
        }
        if (input.priznanje.lowercase() == "da") {
            parts += "Okrivljeni je priznao izvršenje krivičnog djela."
        }

        val witnesses = input.svjedoci.split(Regex("""\r?\n|;""")).map { it.trim() }.filter { it.isNotEmpty() }
        val evidence = input.dokazi.split(Regex("""\r?\n|;""")).map { it.trim() }.filter { it.isNotEmpty() }
        if (witnesses.isNotEmpty()) parts += "Saslušani su svjedoci: ${witnesses.joinToString(", ")}."
        if (evidence.isNotEmpty()) parts += "Materijalni dokazi: ${evidence.joinToString(", ")}."

        return parts.joinToString(" ")
    }
}
