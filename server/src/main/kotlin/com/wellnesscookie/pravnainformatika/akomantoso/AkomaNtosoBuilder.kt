package com.wellnesscookie.pravnainformatika.akomantoso

import com.wellnesscookie.pravnainformatika.model.JudgmentDecision
import com.wellnesscookie.pravnainformatika.model.JudgmentInput
import com.wellnesscookie.pravnainformatika.model.JudgmentSections
import com.wellnesscookie.pravnainformatika.rule.JudiciaryLogic

object AkomaNtosoBuilder {

    fun build(
        input: JudgmentInput,
        decision: JudgmentDecision,
        identity: JudiciaryLogic.CaseIdentity,
        sections: JudgmentSections,
        isoDate: String,
    ): String {
        val caseNumber = input.brojPredmeta.ifEmpty { identity.fallbackCaseNumber }
        val verdict = JudiciaryLogic.normalizeVerdictLabel(decision.vrstaPresude)
        val sentence = JudiciaryLogic.formatSentence(decision)
        val uslovna = decision.uslovnaOsuda.ifEmpty { "Ne" }
        val sudMjesto = input.sud.ifEmpty { "Podgorici" }
        val sudLabel = "Osnovni Sud u $sudMjesto"
        val sudija = input.sudija.ifEmpty { "Korisnički unos" }
        val zapisnicar = input.zapisnicar.ifEmpty { "Korisnički unos" }
        val okrivljeni = input.okrivljeni.ifEmpty { "Korisnički unos" }

        val witnessList = parseListInput(input.svjedoci)
        val evidenceList = parseListInput(input.dokazi)
        val computedWitnesses = witnessList.ifEmpty {
            (1..input.brojSvjedoka.coerceAtLeast(0)).map { "Svjedok $it" }
        }
        val computedEvidence = evidenceList.ifEmpty {
            (1..input.brojDokaza.coerceAtLeast(0)).map { "Dokaz $it" }
        }
        val witnessXml = if (computedWitnesses.isNotEmpty()) {
            computedWitnesses.joinToString("\n") { "          <svjedok>${escapeXml(it)}</svjedok>" }
        } else "          <svjedok>Nije navedeno</svjedok>"
        val evidenceXml = if (computedEvidence.isNotEmpty()) {
            computedEvidence.joinToString("\n") { "          <dokaz>${escapeXml(it)}</dokaz>" }
        } else "          <dokaz>Nije navedeno</dokaz>"
        val motivationEvidenceXml = if (computedEvidence.isNotEmpty()) {
            computedEvidence.joinToString("\n") { "            <p>• ${escapeXml(it)}</p>" }
        } else "            <p>• Nije navedeno</p>"

        val genIntro = sections.introduction.ifEmpty {
            "U IME CRNE GORE — $sudLabel, predmet $caseNumber, sudija $sudija."
        }
        val genBackground = sections.background.ifEmpty {
            "Opis činjeničnog stanja: ${input.opis}"
        }
        val genMotivation = sections.motivation.ifEmpty {
            "Obrazloženje je sastavljeno na osnovu unetog opisa, primenjenih članova i dostupnih dokaza."
        }
        val genDecision = sections.decision.ifEmpty { "$verdict: kazna $sentence." }
        val generatorLabel = sections.generatorLabel.ifEmpty { "markov-chain" }
        val orgHref = sudMjesto.lowercase().replace(Regex("""\s+"""), "_")

        return buildString {
            append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
            append("<akomaNtoso xmlns=\"http://docs.oasis-open.org/legaldocml/ns/akn/3.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n")
            append("  <judgment name=\"${escapeXml(identity.judgmentName)}\">\n")
            append("    <meta>\n")
            append("      <identification source=\"#court\">\n")
            append("        <FRBRWork>\n")
            append("          <FRBRthis value=\"/akn/me/judgment/${escapeXml(identity.judgmentName)}\"/>\n")
            append("          <FRBRuri value=\"/akn/me/judgment/${escapeXml(identity.judgmentName)}\"/>\n")
            append("          <FRBRdate date=\"${escapeXml(isoDate)}\" name=\"judgment\"/>\n")
            append("          <FRBRauthor href=\"#court\"/>\n")
            append("          <FRBRcountry value=\"me\"/>\n")
            append("        </FRBRWork>\n")
            append("        <FRBRExpression>\n")
            append("          <FRBRthis value=\"/akn/me/judgment/${escapeXml(identity.judgmentName)}/srp@${escapeXml(isoDate)}\"/>\n")
            append("          <FRBRuri value=\"/akn/me/judgment/${escapeXml(identity.judgmentName)}/srp@${escapeXml(isoDate)}\"/>\n")
            append("          <FRBRdate date=\"${escapeXml(isoDate)}\" name=\"judgment\"/>\n")
            append("          <FRBRauthor href=\"#court\"/>\n")
            append("          <FRBRlanguage language=\"srp\"/>\n")
            append("        </FRBRExpression>\n")
            append("        <FRBRManifestation>\n")
            append("          <FRBRthis value=\"/akn/me/judgment/${escapeXml(identity.judgmentName)}/srp@${escapeXml(isoDate)}.xml\"/>\n")
            append("          <FRBRuri value=\"/akn/me/judgment/${escapeXml(identity.judgmentName)}/srp@${escapeXml(isoDate)}.xml\"/>\n")
            append("          <FRBRdate date=\"${escapeXml(isoDate)}\" name=\"generation\"/>\n")
            append("          <FRBRauthor href=\"#system\"/>\n")
            append("        </FRBRManifestation>\n")
            append("      </identification>\n")
            append("      <references source=\"#court\">\n")
            append("        <TLCOrganization eId=\"court\" href=\"/ontology/organization/me/${escapeXml(orgHref)}\" showAs=\"${escapeXml(sudLabel)}\"/>\n")
            append("        <TLCPerson eId=\"sudija\" href=\"/ontology/person/sudija_korisnicki_unos\" showAs=\"${escapeXml(sudija)}\"/>\n")
            append("        <TLCPerson eId=\"zapisnicar\" href=\"/ontology/person/zapisnicar_korisnicki_unos\" showAs=\"${escapeXml(zapisnicar)}\"/>\n")
            append("        <TLCPerson eId=\"defendant\" href=\"/ontology/person/okrivljeni_korisnicki_unos\" showAs=\"${escapeXml(okrivljeni)}\"/>\n")
            append("      </references>\n")
            append("      <proprietary source=\"#court\">\n")
            append("        <sud>${escapeXml(sudMjesto)}</sud>\n")
            append("        <brojPredmeta>${escapeXml(caseNumber)}</brojPredmeta>\n")
            append("        <datum>${escapeXml(isoDate)}</datum>\n")
            append("        <datumNormalizovan>${escapeXml(isoDate)}</datumNormalizovan>\n")
            append("        <godina>${identity.year}</godina>\n")
            append("        <sudija>${escapeXml(sudija)}</sudija>\n")
            append("        <zapisnicar>${escapeXml(zapisnicar)}</zapisnicar>\n")
            append("        <okrivljeni>${escapeXml(okrivljeni)}</okrivljeni>\n")
            append("        <zaposlenost>${escapeXml(input.zaposlenost)}</zaposlenost>\n")
            append("        <obrazovanje>${escapeXml(input.obrazovanje)}</obrazovanje>\n")
            append("        <ranijeOsudjivan>${escapeXml(input.ranijeOsudjivan)}</ranijeOsudjivan>\n")
            append("        <tipKrivicnogDjela>${escapeXml(input.tipKrivicnogDjela)}</tipKrivicnogDjela>\n")
            append("        <clanKZ>${escapeXml(input.clanKZ)}</clanKZ>\n")
            append("        <saizvrsilastvo>${escapeXml(input.saizvrsilastvo)}</saizvrsilastvo>\n")
            append("        <zabranjenoSredstvo>${escapeXml(input.zabranjenoSredstvo)}</zabranjenoSredstvo>\n")
            append("        <lovostajIliZabranjeneVode>${escapeXml(input.lovostajIliZabranjeneVode)}</lovostajIliZabranjeneVode>\n")
            append("        <velikaKolicina>${escapeXml(input.velikaKolicina)}</velikaKolicina>\n")
            append("        <elektricnaStruja>${escapeXml(input.elektricnaStruja)}</elektricnaStruja>\n")
            append("        <agregat>${escapeXml(input.agregat)}</agregat>\n")
            append("        <sonda>${escapeXml(input.sonda)}</sonda>\n")
            append("        <pretvarac>${escapeXml(input.pretvarac)}</pretvarac>\n")
            append("        <prisutanUlov>${escapeXml(input.prisutanUlov)}</prisutanUlov>\n")
            append("        <kolicinaUlovaKg>${escapeXml(input.kolicinaUlovaKg.toString())}</kolicinaUlovaKg>\n")
            append("        <oduzimanjePredmeta>${escapeXml(input.oduzimanjePredmeta)}</oduzimanjePredmeta>\n")
            append("        <brojOkrivljenih>${input.brojOkrivljenih}</brojOkrivljenih>\n")
            append("        <brojSvjedoka>${input.brojSvjedoka}</brojSvjedoka>\n")
            append("        <brojDokaza>${input.brojDokaza}</brojDokaza>\n")
            append("        <kazna>${escapeXml(sentence)}</kazna>\n")
            append("        <kaznaZatvoraMjeseci>${decision.kaznaZatvoraMjeseci}</kaznaZatvoraMjeseci>\n")
            append("        <kaznaZatvoraDani>${decision.kaznaZatvoraDani}</kaznaZatvoraDani>\n")
            append("        <radUJavnomInteresuCasovi>${decision.radUJavnomInteresuCasovi}</radUJavnomInteresuCasovi>\n")
            append("        <rokProvjereGodine>0</rokProvjereGodine>\n")
            append("        <troskoviEur>0</troskoviEur>\n")
            append("        <uslovnaOsuda>${escapeXml(uslovna)}</uslovnaOsuda>\n")
            append("        <vrstaPresude>${escapeXml(verdict)}</vrstaPresude>\n")
            append("        <opisSlucaja>${escapeXml(input.opis)}</opisSlucaja>\n")
            append("        <generatedBy>${escapeXml(generatorLabel)}</generatedBy>\n")
            append("        <generationNote>${escapeXml(sections.reasoningSummary.ifEmpty { "Generisano kombinovanjem opisa, pravila i sličnih presuda." })}</generationNote>\n")
            append("        <svjedoci>\n")
            append(witnessXml).append("\n")
            append("        </svjedoci>\n")
            append("        <dokazi>\n")
            append(evidenceXml).append("\n")
            append("        </dokazi>\n")
            append("        <bracniStatus>${escapeXml(input.bracniStatus)}</bracniStatus>\n")
            append("      </proprietary>\n")
            append("    </meta>\n")
            append("    <judgmentBody>\n")
            append("      <introduction>\n        <p>${escapeXml(genIntro)}</p>\n      </introduction>\n")
            append("      <background>\n")
            append("        <p>${escapeXml(genBackground)}</p>\n")
            append("        <p>Okrivljeni: ${escapeXml(okrivljeni)}</p>\n")
            append("        <p>Zapisničar: ${escapeXml(zapisnicar)}</p>\n")
            append("        <p>Datum odluke: ${escapeXml(isoDate)}</p>\n")
            append("      </background>\n")
            append("      <arguments>\n")
            append("        <block name=\"opisSlucaja\">\n          <p>${escapeXml(input.opis)}</p>\n        </block>\n")
            append("      </arguments>\n")
            append("      <motivation>\n")
            append("        <block name=\"dokazi\">\n          <tblock>\n")
            append(motivationEvidenceXml).append("\n          </tblock>\n        </block>\n")
            append("        <block name=\"obrazlozenje\">\n          <p>${escapeXml(genMotivation)}</p>\n        </block>\n")
            append("      </motivation>\n")
            append("      <decision>\n")
            append("        <block name=\"odluka\">\n          <p>${escapeXml(genDecision)}</p>\n          <p>Kazna: ${escapeXml(sentence)}</p>\n        </block>\n")
            append("      </decision>\n")
            append("    </judgmentBody>\n")
            append("    <conclusions>\n")
            append("      <block name=\"pravniOsnov\">\n        <p>Krivično djelo: ${escapeXml(input.tipKrivicnogDjela)} (${escapeXml(input.clanKZ)} Krivičnog zakonika Crne Gore)</p>\n      </block>\n")
            append("      <block name=\"generatorMeta\">\n        <p>${escapeXml(sections.reasoningSummary.ifEmpty { "Generisano na osnovu opisa slučaja i rezultata rasuđivanja." })}</p>\n      </block>\n")
            append("    </conclusions>\n")
            append("  </judgment>\n")
            append("</akomaNtoso>")
        }
    }

    private fun parseListInput(value: String): List<String> =
        value.split(Regex("""\r?\n|;""")).map { it.trim() }.filter { it.isNotEmpty() }

    private fun escapeXml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
