package com.wellnesscookie.pravnainformatika.db

/**
 * Internal CBR case record. Field names match the CSV columns in
 * `case_reasoning/presude-cbr/src/main/resources/presude.csv`.
 * String-typed throughout — the original JS treated CSV cells as strings and
 * the similarity functions parse on demand.
 */
data class CbrCase(
    val id: String = "",
    val sud: String = "Nepoznat",
    val brojPredmeta: String = "",
    val tipKrivicnogDjela: String = "nezakonit ribolov",
    val clanKZ: String = "326.2",
    val saizvrsilastvo: String = "ne",
    val zabranjenoSredstvo: String = "ne",
    val lovostajIliZabranjeneVode: String = "ne",
    val velikaKolicina: String = "ne",
    val elektricnaStruja: String = "ne",
    val agregat: String = "ne",
    val sonda: String = "ne",
    val pretvarac: String = "ne",
    val prisutanUlov: String = "ne",
    val kolicinaUlovaKg: String = "0",
    val oduzimanjePredmeta: String = "ne",
    val ranijeOsudjivan: String = "ne",
    val kaznaZatvoraMjeseci: String = "0",
    val kaznaZatvoraDani: String = "0",
    val radUJavnomInteresuCasovi: String = "0",
    val rokProvjereGodine: String = "0",
    val troskoviEur: String = "0",
    val vrstaPresude: String = "nepoznat",
    val uslovnaOsuda: String = "Ne",
    val brojOkrivljenih: String = "1",
    val brojSvjedoka: String = "0",
    // Hunting-specific. lovostajIliZabranjeneVode above is reused.
    val tudjeLoviste: String = "ne",
    val krupnaDivljac: String = "ne",
    val zasticenaVrsta: String = "ne",
    val bezPosebneDozvole: String = "ne",
    val masovnoUnistavanje: String = "ne",
    val vatrenoOruzje: String = "ne",
    val lovackiPsi: String = "0",
    val bezOruznogLista: String = "ne",
    val bezDozvoleZaLov: String = "ne",
    val odstreljenaDivljac: String = "ne",
    val vrstaDivljaci: String = "",
    val brojGrlaDivljaci: String = "0",
    val sticajDrugogDela: String = "ne",
)
