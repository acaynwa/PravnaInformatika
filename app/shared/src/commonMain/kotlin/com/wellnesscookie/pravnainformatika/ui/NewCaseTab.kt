package com.wellnesscookie.pravnainformatika.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wellnesscookie.pravnainformatika.data.ApiService
import com.wellnesscookie.pravnainformatika.data.buildCaseDeepLinkQuery
import com.wellnesscookie.pravnainformatika.data.openUrlInNewTab
import com.wellnesscookie.pravnainformatika.model.CbrRequest
import com.wellnesscookie.pravnainformatika.model.CbrResult
import com.wellnesscookie.pravnainformatika.model.CbrSimilarCase
import com.wellnesscookie.pravnainformatika.model.GenerateDescriptionRequest
import com.wellnesscookie.pravnainformatika.model.JudgmentDecisionRequest
import com.wellnesscookie.pravnainformatika.model.JudgmentInput
import com.wellnesscookie.pravnainformatika.model.JudgmentRequest
import com.wellnesscookie.pravnainformatika.model.JudgmentResult
import com.wellnesscookie.pravnainformatika.model.JudgmentRuleReasoning
import com.wellnesscookie.pravnainformatika.model.ReasoningRequest
import com.wellnesscookie.pravnainformatika.model.ReasoningResult
import kotlinx.coroutines.launch

private val DaNe = listOf("ne", "da")
private val Zaposlenost = listOf("nepoznat", "zaposlen", "nezaposlen", "student", "penzioner")
private val BracniStatus = listOf("nepoznat", "ozenjen", "neozenjen", "razveden", "udovac")
private val Obrazovanje = listOf("nepoznat", "osnovno", "srednje", "vise", "visoko")
private val CrimeTypes = listOf("nezakonit ribolov", "nezakonit lov")

private fun isHuntingType(tip: String): Boolean {
    val low = tip.lowercase()
    return "lov" in low && "ribolov" !in low
}

private fun defaultClanFor(tip: String): String =
    if (isHuntingType(tip)) "cl. 325 st. 1" else "cl. 326 st. 2"

@Composable
fun NewCaseTab(api: ApiService) {
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf(JudgmentInput(sud = "Podgorici", clanKZ = "cl. 326 st. 2")) }

    var ruleResult by remember { mutableStateOf<ReasoningResult?>(null) }
    var ruleLoading by remember { mutableStateOf(false) }
    var ruleError by remember { mutableStateOf<String?>(null) }

    var cbrResult by remember { mutableStateOf<CbrResult?>(null) }
    var cbrLoading by remember { mutableStateOf(false) }
    var cbrError by remember { mutableStateOf<String?>(null) }

    var judgmentResult by remember { mutableStateOf<JudgmentResult?>(null) }
    var judgmentLoading by remember { mutableStateOf(false) }
    var judgmentError by remember { mutableStateOf<String?>(null) }

    var descriptionLoading by remember { mutableStateOf(false) }

    // Decision-override dialog state
    var showDecisionDialog by remember { mutableStateOf(false) }
    var dialogSavesXml by remember { mutableStateOf(false) }
    var dlgVrstaPresude by remember { mutableStateOf("uslovna") }
    var dlgKaznaMjeseci by remember { mutableStateOf("0") }
    var dlgKaznaDani by remember { mutableStateOf("0") }
    var dlgRadCasovi by remember { mutableStateOf("0") }
    var dlgRokGodine by remember { mutableStateOf("1") }
    var dlgTroskoviEur by remember { mutableStateOf("30") }

    fun openDecisionDialog(saveMode: Boolean) {
        val pre = computeDefaultOverride(ruleResult, cbrResult)
        dlgVrstaPresude = pre.vrstaPresude
        dlgKaznaMjeseci = pre.kaznaZatvoraMjeseci
        dlgKaznaDani = pre.kaznaZatvoraDani
        dlgRadCasovi = pre.radUJavnomInteresuCasovi
        dlgRokGodine = pre.rokProvjereGodine
        dlgTroskoviEur = pre.troskoviEur
        dialogSavesXml = saveMode
        showDecisionDialog = true
    }

    fun runJudgmentWithOverride() {
        val override = JudgmentDecisionRequest(
            vrstaPresude = dlgVrstaPresude,
            kaznaZatvoraMjeseci = dlgKaznaMjeseci.toDoubleOrNull(),
            kaznaZatvoraDani = dlgKaznaDani.toIntOrNull(),
            radUJavnomInteresuCasovi = dlgRadCasovi.toIntOrNull(),
            rokProvjereGodine = dlgRokGodine.toIntOrNull(),
            troskoviEur = dlgTroskoviEur.toDoubleOrNull(),
            uslovnaOsuda = if (dlgVrstaPresude == "uslovna") "Da" else "Ne",
        )
        val previewOnly = !dialogSavesXml
        showDecisionDialog = false
        judgmentLoading = true; judgmentError = null
        scope.launch {
            runCatching {
                api.generateJudgment(
                    JudgmentRequest(
                        input = input,
                        ruleReasoning = ruleResult?.toRuleReasoning(),
                        cbrReasoning = cbrResult,
                        decisionOverride = override,
                        previewOnly = previewOnly,
                    )
                )
            }.onSuccess { judgmentResult = it }.onFailure { judgmentError = it.message }
            judgmentLoading = false
        }
    }

    if (showDecisionDialog) {
        DecisionOverrideDialog(
            saveMode = dialogSavesXml,
            vrstaPresude = dlgVrstaPresude, onVrstaChange = { dlgVrstaPresude = it },
            kaznaMjeseci = dlgKaznaMjeseci, onMjeseciChange = { dlgKaznaMjeseci = it },
            kaznaDani = dlgKaznaDani, onDaniChange = { dlgKaznaDani = it },
            radCasovi = dlgRadCasovi, onCasoviChange = { dlgRadCasovi = it },
            rokGodine = dlgRokGodine, onRokChange = { dlgRokGodine = it },
            troskoviEur = dlgTroskoviEur, onTroskoviChange = { dlgTroskoviEur = it },
            onConfirm = { runJudgmentWithOverride() },
            onDismiss = { showDecisionDialog = false },
        )
    }

    // Right column opens (and the left form shifts left, shrinking to make room) as soon as
    // either reasoning path has something to show — mirrors the list/detail split in CasesTab.
    val rightOpen = ruleResult != null || ruleError != null || cbrResult != null || cbrError != null
    val leftWeight by animateFloatAsState(
        targetValue = if (rightOpen) 0.54f else 1f,
        animationSpec = tween(durationMillis = 380),
        label = "newCaseLeftWeight",
    )
    val rightWeight = (1f - leftWeight).coerceAtLeast(0.0001f)

    Row(modifier = Modifier.fillMaxSize()) {
    Box(modifier = Modifier.weight(leftWeight).fillMaxHeight()) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        contentAlignment = if (rightOpen) Alignment.TopStart else Alignment.TopCenter,
    ) {
        Card(
            modifier = if (rightOpen) {
                Modifier.fillMaxWidth().padding(vertical = 12.dp).padding(end = 4.dp)
            } else {
                Modifier.widthIn(max = 1100.dp).fillMaxWidth().padding(vertical = 12.dp)
            },
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Nova presuda",
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = com.wellnesscookie.pravnainformatika.AppPalette.text,
                    )
                    Text(
                        "Unesi činjenice slučaja i pokreni rasudjivanje.",
                        fontSize = 12.sp,
                        color = com.wellnesscookie.pravnainformatika.AppPalette.textMuted,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DemoFillButton("Demo: Ribolov", com.wellnesscookie.pravnainformatika.AppPalette.primary) {
                        input = demoFishingInput()
                        ruleResult = null; cbrResult = null; judgmentResult = null
                        ruleError = null; cbrError = null; judgmentError = null
                    }
                    DemoFillButton("Demo: Lov", com.wellnesscookie.pravnainformatika.AppPalette.secondary) {
                        input = demoHuntingInput()
                        ruleResult = null; cbrResult = null; judgmentResult = null
                        ruleError = null; cbrError = null; judgmentError = null
                    }
                }
            }

            FormSectionHeader("Osnovni podaci")
            FieldRow {
                LabeledTextField("Broj predmeta", input.brojPredmeta, Modifier.weight(1f)) {
                    input = input.copy(brojPredmeta = it)
                }
                LabeledTextField("Sud (mesto)", input.sud, Modifier.weight(1f)) {
                    input = input.copy(sud = it)
                }
                DatePickerField(
                    label = "Datum presude",
                    value = input.datumPresude,
                    modifier = Modifier.weight(1f),
                ) { input = input.copy(datumPresude = it) }
            }
            FieldRow {
                LabeledTextField("Sudija", input.sudija, Modifier.weight(1f)) {
                    input = input.copy(sudija = it)
                }
                LabeledTextField("Zapisničar", input.zapisnicar, Modifier.weight(1f)) {
                    input = input.copy(zapisnicar = it)
                }
                LabeledTextField("Okrivljeni", input.okrivljeni, Modifier.weight(1f)) {
                    input = input.copy(okrivljeni = it)
                }
            }

            FormSectionHeader("Krivično delo")
            FieldRow {
                LabeledSelect("Tip krivičnog dela", input.tipKrivicnogDjela, CrimeTypes, Modifier.weight(1f)) { newTip ->
                    val newClan = if (input.clanKZ.isEmpty() ||
                        input.clanKZ == defaultClanFor(input.tipKrivicnogDjela)
                    ) defaultClanFor(newTip) else input.clanKZ
                    input = input.copy(tipKrivicnogDjela = newTip, clanKZ = newClan)
                }
            }
            ArticleKZPicker(
                currentClanKZ = input.clanKZ,
                onClanKZChange = { input = input.copy(clanKZ = it) },
            )

            val hunting = isHuntingType(input.tipKrivicnogDjela)

            FormSectionHeader("Opšte okolnosti dela")
            FieldRow {
                LabeledSelect("Saizvrsilastvo (cl. 23 st. 2)", input.saizvrsilastvo, DaNe, Modifier.weight(1f)) {
                    input = input.copy(saizvrsilastvo = it)
                }
                LabeledSelect(
                    if (hunting) "Lovostaj / zabranjena oblast" else "Lovostaj / zab. vode",
                    input.lovostajIliZabranjeneVode, DaNe, Modifier.weight(1f),
                ) { input = input.copy(lovostajIliZabranjeneVode = it) }
                if (!hunting) {
                    LabeledSelect("Zabranjeno sredstvo", input.zabranjenoSredstvo, DaNe, Modifier.weight(1f)) {
                        input = input.copy(zabranjenoSredstvo = it)
                    }
                    LabeledSelect("Velika kolicina", input.velikaKolicina, DaNe, Modifier.weight(1f)) {
                        input = input.copy(velikaKolicina = it)
                    }
                }
            }

            if (hunting) {
                FormSectionHeader("Okolnosti lova (cl. 325)")
                FieldRow {
                    LabeledSelect("Tuđe lovište (st. 2)", input.tudjeLoviste, DaNe, Modifier.weight(1f)) {
                        input = input.copy(tudjeLoviste = it)
                    }
                    LabeledSelect("Krupna divljac (st. 3)", input.krupnaDivljac, DaNe, Modifier.weight(1f)) {
                        input = input.copy(krupnaDivljac = it)
                    }
                    LabeledSelect("Zasticena vrsta (st. 4)", input.zasticenaVrsta, DaNe, Modifier.weight(1f)) {
                        input = input.copy(zasticenaVrsta = it)
                    }
                    LabeledSelect("Masovno unistavanje (st. 4)", input.masovnoUnistavanje, DaNe, Modifier.weight(1f)) {
                        input = input.copy(masovnoUnistavanje = it)
                    }
                }
                FieldRow {
                    LabeledSelect("Bez posebne dozvole", input.bezPosebneDozvole, DaNe, Modifier.weight(1f)) {
                        input = input.copy(bezPosebneDozvole = it)
                    }
                    LabeledSelect("Bez dozvole za lov", input.bezDozvoleZaLov, DaNe, Modifier.weight(1f)) {
                        input = input.copy(bezDozvoleZaLov = it)
                    }
                    LabeledSelect("Vatreno oruzje", input.vatrenoOruzje, DaNe, Modifier.weight(1f)) {
                        input = input.copy(vatrenoOruzje = it)
                    }
                    LabeledSelect("Bez oruznog lista", input.bezOruznogLista, DaNe, Modifier.weight(1f)) {
                        input = input.copy(bezOruznogLista = it)
                    }
                }
                FieldRow {
                    LabeledIntField("Lovacki psi (broj)", input.lovackiPsi, Modifier.weight(1f)) {
                        input = input.copy(lovackiPsi = it.coerceAtLeast(0))
                    }
                    LabeledSelect("Odstrijeljena divljac", input.odstreljenaDivljac, DaNe, Modifier.weight(1f)) {
                        input = input.copy(odstreljenaDivljac = it)
                    }
                    LabeledTextField("Vrsta divljaci", input.vrstaDivljaci, Modifier.weight(1f)) {
                        input = input.copy(vrstaDivljaci = it)
                    }
                    LabeledIntField("Broj grla", input.brojGrlaDivljaci, Modifier.weight(1f)) {
                        input = input.copy(brojGrlaDivljaci = it.coerceAtLeast(0))
                    }
                }
                FieldRow {
                    LabeledSelect(
                        "Sticaj sa drugim delom (čl. 403/309)", input.sticajDrugogDela, DaNe,
                        Modifier.weight(1f),
                    ) { input = input.copy(sticajDrugogDela = it) }
                    Spacer(modifier = Modifier.weight(3f))
                }
            } else {
                FormSectionHeader("Sredstva za ribolov (cl. 326)")
                FieldRow {
                    LabeledSelect("Elektricna struja", input.elektricnaStruja, DaNe, Modifier.weight(1f)) {
                        input = input.copy(elektricnaStruja = it)
                    }
                    LabeledSelect("Agregat", input.agregat, DaNe, Modifier.weight(1f)) {
                        input = input.copy(agregat = it)
                    }
                    LabeledSelect("Sonda", input.sonda, DaNe, Modifier.weight(1f)) {
                        input = input.copy(sonda = it)
                    }
                    LabeledSelect("Pretvarac", input.pretvarac, DaNe, Modifier.weight(1f)) {
                        input = input.copy(pretvarac = it)
                    }
                }
            }

            FormSectionHeader(if (hunting) "Ulov i mere bezbednosti" else "Ulov i posledice")
            FieldRow {
                LabeledSelect(
                    if (hunting) "Prisutan ulov (divljac)" else "Prisutan ulov",
                    input.prisutanUlov, DaNe, Modifier.weight(1f),
                ) { input = input.copy(prisutanUlov = it) }
                LabeledDoubleField(
                    if (hunting) "Tezina ulova (kg)" else "Kolicina ulova (kg)",
                    input.kolicinaUlovaKg, Modifier.weight(1f),
                ) { input = input.copy(kolicinaUlovaKg = it) }
                LabeledSelect("Oduzimanje predmeta", input.oduzimanjePredmeta, DaNe, Modifier.weight(1f)) {
                    input = input.copy(oduzimanjePredmeta = it)
                }
            }

            FormSectionHeader("Licne okolnosti okrivljenog")
            FieldRow {
                LabeledSelect("Ranije osudjivan", input.ranijeOsudjivan, DaNe, Modifier.weight(1f)) {
                    input = input.copy(ranijeOsudjivan = it)
                }
                LabeledSelect("Priznanje", input.priznanje, DaNe, Modifier.weight(1f)) {
                    input = input.copy(priznanje = it)
                }
                LabeledSelect("Zaposlenost", input.zaposlenost, Zaposlenost, Modifier.weight(1f)) {
                    input = input.copy(zaposlenost = it)
                }
            }
            FieldRow {
                LabeledSelect("Bracni status", input.bracniStatus, BracniStatus, Modifier.weight(1f)) {
                    input = input.copy(bracniStatus = it)
                }
                LabeledSelect("Obrazovanje", input.obrazovanje, Obrazovanje, Modifier.weight(1f)) {
                    input = input.copy(obrazovanje = it)
                }
            }

            FormSectionHeader("Brojcani podaci")
            FieldRow {
                LabeledIntField("Broj okrivljenih", input.brojOkrivljenih, Modifier.weight(1f)) {
                    input = input.copy(brojOkrivljenih = it.coerceAtLeast(1))
                }
                LabeledIntField("Broj svedoka", input.brojSvjedoka, Modifier.weight(1f)) {
                    input = input.copy(brojSvjedoka = it.coerceAtLeast(0))
                }
                LabeledIntField("Broj dokaza", input.brojDokaza, Modifier.weight(1f)) {
                    input = input.copy(brojDokaza = it.coerceAtLeast(0))
                }
            }

            FormSectionHeader("Svedoci i dokazi")
            LabeledTextArea("Svedoci", input.svjedoci, "npr. M.N.\nP.Q.") {
                input = input.copy(svjedoci = it)
            }
            LabeledTextArea("Dokazi", input.dokazi, "npr. Zapisnik o pregledu\nElektroagregat") {
                input = input.copy(dokazi = it)
            }

            FormSectionHeader("Činjenično stanje")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Opis slučaja", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Button(
                    enabled = !descriptionLoading,
                    onClick = {
                        descriptionLoading = true
                        scope.launch {
                            runCatching { api.generateDescription(GenerateDescriptionRequest(input = input)) }
                                .onSuccess { input = input.copy(opis = it.description) }
                            descriptionLoading = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = com.wellnesscookie.pravnainformatika.AppPalette.primary,
                        contentColor = Color.White,
                    ),
                ) {
                    Text(if (descriptionLoading) "Generisanje..." else "Generiši opis")
                }
            }
            LabeledTextArea("", input.opis, "Unesite cinjenicno stanje...") {
                input = input.copy(opis = it)
            }

            HorizontalDivider()

            // Action buttons
            FormSectionHeader("Rasuđivanje i generisanje presude")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    enabled = !ruleLoading,
                    onClick = {
                        ruleLoading = true; ruleError = null
                        scope.launch {
                            runCatching {
                                api.reasoning(ReasoningRequest(facts = factsFromInput(input)))
                            }.onSuccess { ruleResult = it }.onFailure { ruleError = it.message }
                            ruleLoading = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = com.wellnesscookie.pravnainformatika.AppPalette.primary,
                        contentColor = Color.White,
                    ),
                ) {
                    Text(if (ruleLoading) "..." else "Rasuđivanje po pravilima")
                }
                Button(
                    enabled = !cbrLoading,
                    onClick = {
                        cbrLoading = true; cbrError = null
                        scope.launch {
                            runCatching {
                                api.cbrReasoningInput(CbrRequest(query = cbrQueryFromInput(input)))
                            }.onSuccess { cbrResult = it }.onFailure { cbrError = it.message }
                            cbrLoading = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = com.wellnesscookie.pravnainformatika.AppPalette.secondary,
                        contentColor = Color.White,
                    ),
                ) {
                    Text(if (cbrLoading) "..." else "Rasuđivanje po slučajevima")
                }
                Button(
                    enabled = !judgmentLoading,
                    onClick = { openDecisionDialog(saveMode = true) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = com.wellnesscookie.pravnainformatika.AppPalette.accent,
                        contentColor = Color.White,
                    ),
                ) {
                    Text(if (judgmentLoading) "..." else "Generiši i sačuvaj presudu")
                }
            }

            judgmentError?.let { ErrorPanel("Generisanje presude", it) }
            judgmentResult?.let { JudgmentPanel(it) }
        }
        }
    }
    }

    // Reasoning outputs live in their own fixed column: page/global scroll doesn't touch
    // them, only the column's own scroll does — and they animate in from the right.
    Box(modifier = Modifier.weight(rightWeight).fillMaxHeight()) {
        ReasoningResultsPanel(
            visible = rightOpen,
            ruleError = ruleError,
            ruleResult = ruleResult,
            cbrError = cbrError,
            cbrResult = cbrResult,
            onOpenCase = { caseId -> openUrlInNewTab(buildCaseDeepLinkQuery(caseId)) },
        )
    }
    }
}

// Pulled out of NewCaseTab's body so this AnimatedVisibility resolves to the plain top-level
// overload — called lexically inside a Row, it would otherwise pick up RowScope's variant.
@Composable
private fun ReasoningResultsPanel(
    visible: Boolean,
    ruleError: String?,
    ruleResult: ReasoningResult?,
    cbrError: String?,
    cbrResult: CbrResult?,
    onOpenCase: (String) -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(300, delayMillis = 100)) + slideInHorizontally(
            animationSpec = tween(350, delayMillis = 50),
            initialOffsetX = { it / 4 },
        ),
        exit = fadeOut(tween(150)) + slideOutHorizontally(
            animationSpec = tween(200),
            targetOffsetX = { it / 4 },
        ),
    ) {
        Card(
            modifier = Modifier.fillMaxSize().padding(vertical = 12.dp).padding(start = 4.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Rezultati rasuđivanja",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = com.wellnesscookie.pravnainformatika.AppPalette.text,
                )
                ruleError?.let { ErrorPanel("Pravila", it) }
                ruleResult?.let { RulePanel(it) }

                cbrError?.let { ErrorPanel("Slični slučajevi", it) }
                cbrResult?.let { CbrPanel(it, onOpenCase) }
            }
        }
    }
}

// ----- Form helpers --------------------------------------------------------

@Composable
private fun FormSectionHeader(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(18.dp)
                .background(com.wellnesscookie.pravnainformatika.AppPalette.primary, RoundedCornerShape(2.dp)),
        )
        Text(
            text,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = com.wellnesscookie.pravnainformatika.AppPalette.text,
        )
    }
}

@Composable
private fun FieldRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
        content = { content() },
    )
}

@Composable
private fun LabeledTextField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun LabeledTextArea(
    label: String,
    value: String,
    placeholder: String,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = if (label.isNotEmpty()) ({ Text(label) }) else null,
        placeholder = { Text(placeholder) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
    )
}

@Composable
private fun LabeledIntField(
    label: String,
    value: Int,
    modifier: Modifier = Modifier,
    onChange: (Int) -> Unit,
) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { it.toIntOrNull()?.let(onChange) ?: run { if (it.isEmpty()) onChange(0) } },
        label = { Text(label) },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun LabeledDoubleField(
    label: String,
    value: Double,
    modifier: Modifier = Modifier,
    onChange: (Double) -> Unit,
) {
    OutlinedTextField(
        value = if (value == 0.0) "" else value.toString(),
        onValueChange = { it.replace(',', '.').toDoubleOrNull()?.let(onChange) ?: run { if (it.isEmpty()) onChange(0.0) } },
        label = { Text(label) },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LabeledSelect(
    label: String,
    value: String,
    options: List<String>,
    modifier: Modifier = Modifier,
    onChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { o ->
                DropdownMenuItem(
                    text = { Text(o) },
                    onClick = { onChange(o); expanded = false },
                )
            }
        }
    }
}

// ----- Result panels -------------------------------------------------------

@Composable
private fun ErrorPanel(label: String, msg: String) {
    Text("Greska ($label): $msg", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
}

@Composable
private fun RulePanel(r: ReasoningResult) {
    val noRulesFired = r.appliedArticles.isEmpty() && r.violatedRules.isEmpty()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(com.wellnesscookie.pravnainformatika.AppPalette.primarySoft, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "Rasuđivanje po pravilima",
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = com.wellnesscookie.pravnainformatika.AppPalette.primary,
        )

        // Highlighted recommendation — the most important field for the user.
        if (r.recommendation.isNotEmpty() && r.recommendation != "N/A") {
            Text("Preporucena sankcija:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            Text(r.recommendation, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF065F46))
        }

        // Penalty range from the rulebase (e.g. 30 dana .. 180 dana).
        val penMin = r.penaltyRange.min
        val penMax = r.penaltyRange.max
        if (penMin != "N/A" || penMax != "N/A") {
            Text("Zakonski raspon kazne: $penMin – $penMax", fontSize = 12.sp)
        }

        // Verdict from the actual court decision (only present for caseId mode).
        r.verdict?.takeIf { it.isNotEmpty() && it != "Nepoznato" }?.let {
            Text("Stvarni verdikt: $it", fontSize = 12.sp)
        }
        r.actualSentence.takeIf { it.isNotEmpty() && it != "N/A" }?.let {
            Text("Stvarna kazna: $it", fontSize = 12.sp)
        }
        if (r.acquittal) {
            Text("(Oslobadjajuca presuda)", fontSize = 12.sp, color = Color(0xFF991B1B))
        }

        if (r.appliedArticles.isNotEmpty()) {
            Text("Clanovi:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            Text(r.appliedArticles.joinToString(", "), fontSize = 12.sp)
        }
        if (r.violatedRules.isNotEmpty()) {
            Text("Prekrsena pravila:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            r.violatedRules.forEach { Text("• $it", fontSize = 12.sp) }
        }
        if (r.mitigatingFactors.isNotEmpty()) {
            Text("Olakšavajuće okolnosti:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color(0xFF065F46))
            r.mitigatingFactors.forEach { Text("+ $it", fontSize = 12.sp, color = Color(0xFF065F46)) }
        }
        if (r.aggravatingFactors.isNotEmpty()) {
            Text("Otežavajuće okolnosti:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color(0xFF991B1B))
            r.aggravatingFactors.forEach { Text("− $it", fontSize = 12.sp, color = Color(0xFF991B1B)) }
        }
        if (r.rationale.isNotEmpty()) {
            Text("Obrazlozenje:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            r.rationale.forEach { Text("- $it", fontSize = 12.sp) }
        }

        if (noRulesFired) {
            Text(
                "Nije identifikovan nijedan clan KZ. Najcesce je razlog prazno ili neprepoznato " +
                    "polje 'Clan KZ' (npr. 'cl. 325 st. 1' ili '325.1'). Proveri formu i pokusaj ponovo.",
                fontSize = 11.sp,
                color = Color(0xFF92400E),
            )
        }
    }
}

@Composable
private fun CbrPanel(r: CbrResult, onOpenCase: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(com.wellnesscookie.pravnainformatika.AppPalette.infoSoft, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Rasuđivanje po slučajevima (${r.totalCasesCompared} predmeta)",
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = com.wellnesscookie.pravnainformatika.AppPalette.info,
        )
        val rec = r.recommendation
        rec.kaznaZatvoraMjeseci?.let { Text("Preporučena kazna: $it meseci", fontSize = 12.sp) }
        Text("Uslovna [%]: ${rec.conditionalSentenceLikelihood}%", fontSize = 12.sp)
        if (r.similarCases.isNotEmpty()) {
            Text("Slične presude - klikni za otvaranje u novom tabu:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                r.similarCases.forEach { sc -> SimilarCaseRow(sc, onOpenCase) }
            }
        }
    }
}

@Composable
private fun SimilarCaseRow(sc: CbrSimilarCase, onOpenCase: (String) -> Unit) {
    val (badgeBg, badgeFg) = similarityColors(sc.similarity)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .clickable(enabled = sc.brojPredmeta.isNotEmpty()) { onOpenCase(sc.brojPredmeta) }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                sc.brojPredmeta.ifEmpty { "-" },
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = com.wellnesscookie.pravnainformatika.AppPalette.primaryDark,
            )
            Text(sc.vrstaPresude.ifEmpty { "-" }, fontSize = 11.sp, color = com.wellnesscookie.pravnainformatika.AppPalette.textMuted)
        }
        Box(
            modifier = Modifier
                .background(badgeBg, RoundedCornerShape(20.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Text("${sc.similarity}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = badgeFg)
        }
        Text("↗", fontSize = 14.sp, color = com.wellnesscookie.pravnainformatika.AppPalette.textMuted)
    }
}

private fun similarityColors(pct: Double): Pair<Color, Color> = when {
    pct >= 80 -> com.wellnesscookie.pravnainformatika.AppPalette.secondarySoft to com.wellnesscookie.pravnainformatika.AppPalette.secondary
    pct >= 50 -> com.wellnesscookie.pravnainformatika.AppPalette.warningSoft to com.wellnesscookie.pravnainformatika.AppPalette.warningText
    else -> com.wellnesscookie.pravnainformatika.AppPalette.surfaceVariant to com.wellnesscookie.pravnainformatika.AppPalette.textMuted
}

@Composable
private fun JudgmentPanel(r: JudgmentResult) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(com.wellnesscookie.pravnainformatika.AppPalette.warningSoft, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "Generisana presuda",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = com.wellnesscookie.pravnainformatika.AppPalette.warningText,
            )
            Text(
                "Generator: ${r.sections.generatorLabel}",
                fontSize = 11.sp,
                color = com.wellnesscookie.pravnainformatika.AppPalette.textMuted,
            )
        }
        Text("Slucaj: ${r.caseId}", fontSize = 12.sp)
        r.xmlFile?.let {
            Text("Sačuvano u: $it", fontSize = 12.sp, color = Color(0xFF065F46))
            Text(
                "✓ Presuda je odmah ukljucena u CBR bazu — sledece rasudjivanje po slicnosti " +
                    "ce je videti.",
                fontSize = 11.sp,
                color = Color(0xFF065F46),
            )
        }
        Text("Odluka:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
        val d = r.decision
        Text(
            "Vrsta: ${d.vrstaPresude} | Kazna: ${d.kaznaZatvoraMjeseci} mes.",
            fontSize = 12.sp,
        )
        if (r.sections.decision.isNotEmpty()) {
            Text("Dispozitiv:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            Text(r.sections.decision, fontSize = 12.sp)
        }
        if (r.sections.motivation.isNotEmpty()) {
            Text("Obrazlozenje:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            Text(r.sections.motivation, fontSize = 12.sp, color = Color.DarkGray)
        }
    }
}

// ----- Date picker --------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onChange: (String) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    // Read-only field; click anywhere → open dialog.
    Box(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            placeholder = { Text("YYYY-MM-DD") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        // Transparent click-catcher above the read-only field.
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { showPicker = true },
        )
    }
    if (showPicker) {
        val initialMillis = parseIsoToMillis(value)
        val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(millisToIso(it)) }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showPicker = false }) { Text("Otkaži") }
            },
        ) {
            DatePicker(state = state)
        }
    }
}

/** Parses "YYYY-MM-DD" → epoch millis at UTC midnight, or null if invalid. */
private fun parseIsoToMillis(iso: String): Long? {
    val m = Regex("""^(\d{4})-(\d{2})-(\d{2})$""").matchEntire(iso) ?: return null
    val (y, mo, d) = m.destructured
    // Days from 1970-01-01 to (y, mo, d) using a simple Gregorian calc — multiplatform-safe.
    val year = y.toInt(); val month = mo.toInt(); val day = d.toInt()
    if (month !in 1..12 || day !in 1..31) return null
    val daysPerMonth = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    fun isLeap(yr: Int) = (yr % 4 == 0 && yr % 100 != 0) || (yr % 400 == 0)
    var days = 0L
    for (yr in 1970 until year) days += if (isLeap(yr)) 366 else 365
    for (mi in 1 until month) days += daysPerMonth[mi - 1] + if (mi == 2 && isLeap(year)) 1 else 0
    days += (day - 1)
    return days * 86_400_000L
}

private fun millisToIso(millis: Long): String {
    var d = millis / 86_400_000L
    var year = 1970
    fun isLeap(yr: Int) = (yr % 4 == 0 && yr % 100 != 0) || (yr % 400 == 0)
    while (true) {
        val ly = if (isLeap(year)) 366 else 365
        if (d < ly) break
        d -= ly; year++
    }
    val daysPerMonth = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    var month = 1
    while (true) {
        val md = daysPerMonth[month - 1] + if (month == 2 && isLeap(year)) 1 else 0
        if (d < md) break
        d -= md; month++
    }
    val day = (d + 1).toInt()
    return "$year-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
}

// ----- Član KZ picker (checkbox lista + custom polje) ----------------------

private val ChlanKZOptions = listOf(
    "čl. 325 st. 1", "čl. 325 st. 2", "čl. 325 st. 3", "čl. 325 st. 4", "čl. 325 st. 5",
    "čl. 326 st. 1", "čl. 326 st. 2", "čl. 326 st. 3", "čl. 326 st. 4",
)

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ArticleKZPicker(
    currentClanKZ: String,
    onClanKZChange: (String) -> Unit,
) {
    // Selected = set of canonical labels; custom = extras typed by the user
    val selected = remember(currentClanKZ) {
        ChlanKZOptions.filter { canon ->
            val canonNorm = canonicalize(canon)
            canonicalize(currentClanKZ).contains(canonNorm)
        }.toMutableStateList()
    }
    var custom by remember(currentClanKZ) {
        mutableStateOf(extractCustomFromClanKZ(currentClanKZ, ChlanKZOptions))
    }

    fun rebuild() {
        val parts = selected.toList() + custom.split(Regex("""[,;\n]""")).map { it.trim() }.filter { it.isNotEmpty() }
        onClanKZChange(parts.joinToString(", "))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(com.wellnesscookie.pravnainformatika.AppPalette.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Prekršeni članovi KZ — označi sve koji se primenjuju",
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            color = com.wellnesscookie.pravnainformatika.AppPalette.textMuted,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ChlanKZOptions.forEach { opt ->
                val checked = opt in selected
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(
                            if (checked) com.wellnesscookie.pravnainformatika.AppPalette.primarySoft else Color.Transparent,
                            RoundedCornerShape(6.dp),
                        )
                        .clickable {
                            if (checked) selected.remove(opt) else selected.add(opt)
                            rebuild()
                        }
                        .padding(end = 8.dp),
                ) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = {
                            if (it) { if (opt !in selected) selected.add(opt) } else selected.remove(opt)
                            rebuild()
                        },
                    )
                    Text(opt, fontSize = 12.sp)
                }
            }
        }
        OutlinedTextField(
            value = custom,
            onValueChange = { custom = it; rebuild() },
            label = { Text("Other (ex. čl. 403 st. 1)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun canonicalize(s: String): String =
    s.lowercase()
        .replace("č", "c").replace("š", "s").replace("ž", "z").replace("đ", "dj").replace("ć", "c")
        .replace(Regex("""\s+"""), " ").trim()

private fun extractCustomFromClanKZ(clanKZ: String, known: List<String>): String {
    if (clanKZ.isEmpty()) return ""
    val knownCanon = known.map { canonicalize(it) }
    return clanKZ.split(Regex("""[,;]"""))
        .map { it.trim() }
        .filter { it.isNotEmpty() && canonicalize(it) !in knownCanon }
        .joinToString(", ")
}

// ----- Decision-override dialog ---------------------------------------------

private data class OverrideDefaults(
    val vrstaPresude: String,
    val kaznaZatvoraMjeseci: String,
    val kaznaZatvoraDani: String,
    val radUJavnomInteresuCasovi: String,
    val rokProvjereGodine: String,
    val troskoviEur: String,
)

private fun computeDefaultOverride(rule: ReasoningResult?, cbr: CbrResult?): OverrideDefaults {
    // Verdict: lean on rule output first, then CBR conditional likelihood.
    val ruleVerdict = rule?.verdict.orEmpty().lowercase()
    val ruleRecText = rule?.recommendation.orEmpty().lowercase()
    val cbrCondPct = cbr?.recommendation?.conditionalSentenceLikelihood ?: 0
    val acquittal = rule?.acquittal == true || "oslob" in ruleVerdict
    val vrsta = when {
        acquittal -> "oslobadjajuca"
        "uslovna" in ruleRecText || "uslov" in ruleVerdict || cbrCondPct >= 60 -> "uslovna"
        else -> "osudjujuca"
    }
    // Sentence months: CBR-recommended first; otherwise parse rule recommendation
    // text like "Uslovna presuda: 38 dana" or "68 dana".
    val cbrMonths = cbr?.recommendation?.kaznaZatvoraMjeseci
    val (mj, dani) = when {
        cbrMonths != null && cbrMonths > 0 -> cbrMonths.toInt().toString() to "0"
        else -> {
            val daniMatch = Regex("""(\d+)\s*dan""", RegexOption.IGNORE_CASE).find(rule?.recommendation.orEmpty())
            val mjMatch = Regex("""(\d+)\s*(?:mjes|mes)""", RegexOption.IGNORE_CASE).find(rule?.recommendation.orEmpty())
            when {
                mjMatch != null -> mjMatch.groupValues[1] to "0"
                daniMatch != null -> "0" to daniMatch.groupValues[1]
                else -> "0" to "0"
            }
        }
    }

    // Probation default: 1 year for uslovna; otherwise 0.
    val rok = if (vrsta == "uslovna") "1" else "0"

    return OverrideDefaults(
        vrstaPresude = vrsta,
        kaznaZatvoraMjeseci = mj,
        kaznaZatvoraDani = dani,
        radUJavnomInteresuCasovi = "0",
        rokProvjereGodine = rok,
        troskoviEur = "30",
    )
}

private val VrstePresude = listOf("uslovna", "osudjujuca", "oslobadjajuca", "odbijena")

@Composable
private fun DecisionOverrideDialog(
    saveMode: Boolean,
    vrstaPresude: String, onVrstaChange: (String) -> Unit,
    kaznaMjeseci: String, onMjeseciChange: (String) -> Unit,
    kaznaDani: String, onDaniChange: (String) -> Unit,
    radCasovi: String, onCasoviChange: (String) -> Unit,
    rokGodine: String, onRokChange: (String) -> Unit,
    troskoviEur: String, onTroskoviChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (saveMode) "Sačuvaj presudu — odluka suda" else "Generiši presudu — odluka suda")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldRow {
                    LabeledSelect("Vrsta presude", vrstaPresude, VrstePresude, Modifier.weight(1f)) {
                        onVrstaChange(it)
                    }
                }
                FieldRow {
                    LabeledTextField("Kazna zatvora (meseci)", kaznaMjeseci, Modifier.weight(1f), onChange = onMjeseciChange)
                    LabeledTextField("Kazna zatvora (dani)", kaznaDani, Modifier.weight(1f), onChange = onDaniChange)
                }
                FieldRow {
                    LabeledTextField("Rad u javnom interesu (sati)", radCasovi, Modifier.weight(1f), onChange = onCasoviChange)
                    LabeledTextField("Rok provere (godine)", rokGodine, Modifier.weight(1f), onChange = onRokChange)
                    LabeledTextField("Troškovi (€)", troskoviEur, Modifier.weight(1f), onChange = onTroskoviChange)
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onConfirm) {
                Text(if (saveMode) "Sačuvaj XML" else "Generiši preview", color = Color(0xFF065F46))
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Otkaži") }
        },
    )
}

// ----- Demo / autofill -----------------------------------------------------

@Composable
private fun DemoFillButton(label: String, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        modifier = Modifier.heightIn(min = 30.dp),
    ) {
        Text(label, fontSize = 11.sp)
    }
}

private fun demoFishingInput(): JudgmentInput = JudgmentInput(
    brojPredmeta = "K 500/26",
    sud = "Podgorici",
    sudija = "Ana Boskovic",
    zapisnicar = "M. M.",
    okrivljeni = "A. B.",
    datumPresude = "2026-05-10",
    tipKrivicnogDjela = "nezakonit ribolov",
    clanKZ = "cl. 326 st. 2",
    opis = "Okrivljeni je dana 10. maja 2026. godine na Skadarskom jezeru lovio ribu " +
        "koristeci agregat i elektricnu strugu, kojom prilikom je ulovio 8,5 kg ribe.",
    saizvrsilastvo = "ne",
    lovostajIliZabranjeneVode = "da",
    zabranjenoSredstvo = "da",
    velikaKolicina = "ne",
    elektricnaStruja = "da",
    agregat = "da",
    sonda = "da",
    pretvarac = "ne",
    prisutanUlov = "da",
    kolicinaUlovaKg = 8.5,
    oduzimanjePredmeta = "da",
    ranijeOsudjivan = "ne",
    priznanje = "da",
    zaposlenost = "nezaposlen",
    obrazovanje = "srednje",
    bracniStatus = "ozenjen",
    brojOkrivljenih = 1,
    brojSvjedoka = 2,
    brojDokaza = 3,
    svjedoci = "D. I.; G. K.",
    dokazi = "Zapisnik o kontroli NP Skadarsko jezero; Potvrda o oduzetom oruzju; Foto-dokumentacija",
)

private fun demoHuntingInput(): JudgmentInput = JudgmentInput(
    brojPredmeta = "K 501/26",
    sud = "Herceg Novom",
    sudija = "Ivan Perovic",
    zapisnicar = "T. M.",
    okrivljeni = "Z. R.",
    datumPresude = "2026-05-10",
    tipKrivicnogDjela = "nezakonit lov",
    clanKZ = "cl. 325 st. 1",
    opis = "Okrivljeni je dana 10. maja 2026. godine u reonu lovista Bjelotina, " +
        "za vrijeme lovostaja, koristeci lovackog psa i lovacku pusku, odstrijelio " +
        "divlju svinju tezine oko 90 kg.",
    saizvrsilastvo = "ne",
    lovostajIliZabranjeneVode = "da",
    tudjeLoviste = "ne",
    krupnaDivljac = "da",
    zasticenaVrsta = "ne",
    bezPosebneDozvole = "ne",
    masovnoUnistavanje = "ne",
    vatrenoOruzje = "da",
    lovackiPsi = 1,
    bezOruznogLista = "ne",
    bezDozvoleZaLov = "ne",
    odstreljenaDivljac = "da",
    vrstaDivljaci = "divlju svinju",
    brojGrlaDivljaci = 1,
    sticajDrugogDela = "da",
    prisutanUlov = "da",
    kolicinaUlovaKg = 90.0,
    oduzimanjePredmeta = "da",
    ranijeOsudjivan = "ne",
    priznanje = "da",
    zaposlenost = "nezaposlen",
    obrazovanje = "srednje",
    bracniStatus = "neozenjen",
    brojOkrivljenih = 1,
    brojSvjedoka = 2,
    brojDokaza = 3,
    svjedoci = "LJ. M.; J. R.",
    dokazi = "Potvrda o privremenom oduzimanju ulova i sredstava za lov; Zapisnik LD Orjen; Fotografije ubijene divljaci",
)

// ----- Helpers to build request bodies from form state ---------------------

private fun ReasoningResult.toRuleReasoning(): JudgmentRuleReasoning =
    JudgmentRuleReasoning(
        verdict = verdict.orEmpty(),
        recommendation = recommendation.ifEmpty { rawOutput },
        actual_sentence = actualSentence,
        acquittal = acquittal,
        articles = appliedArticles,
        mitigating_factors = mitigatingFactors,
        aggravating_factors = aggravatingFactors,
    )

private fun factsFromInput(i: JudgmentInput): Map<String, String> = buildMap {
    put("brojPredmeta", i.brojPredmeta)
    put("sud", i.sud)
    put("okrivljeni", i.okrivljeni)
    put("tipKrivicnogDjela", i.tipKrivicnogDjela)
    put("clanKZ", i.clanKZ)
    put("saizvrsilastvo", i.saizvrsilastvo)
    put("zabranjenoSredstvo", i.zabranjenoSredstvo)
    put("lovostajIliZabranjeneVode", i.lovostajIliZabranjeneVode)
    put("velikaKolicina", i.velikaKolicina)
    put("elektricnaStruja", i.elektricnaStruja)
    put("agregat", i.agregat)
    put("sonda", i.sonda)
    put("pretvarac", i.pretvarac)
    put("prisutanUlov", i.prisutanUlov)
    put("kolicinaUlovaKg", i.kolicinaUlovaKg.toString())
    put("oduzimanjePredmeta", i.oduzimanjePredmeta)
    put("ranijeOsudjivan", i.ranijeOsudjivan)
    put("priznanje", i.priznanje)
    put("zaposlenost", i.zaposlenost)
    // Hunting-specific
    put("tudjeLoviste", i.tudjeLoviste)
    put("krupnaDivljac", i.krupnaDivljac)
    put("zasticenaVrsta", i.zasticenaVrsta)
    put("bezPosebneDozvole", i.bezPosebneDozvole)
    put("masovnoUnistavanje", i.masovnoUnistavanje)
    put("vatrenoOruzje", i.vatrenoOruzje)
    put("lovackiPsi", i.lovackiPsi.toString())
    put("bezOruznogLista", i.bezOruznogLista)
    put("bezDozvoleZaLov", i.bezDozvoleZaLov)
    put("odstreljenaDivljac", i.odstreljenaDivljac)
    put("vrstaDivljaci", i.vrstaDivljaci)
    put("brojGrlaDivljaci", i.brojGrlaDivljaci.toString())
    put("sticajDrugogDela", i.sticajDrugogDela)
}

private fun cbrQueryFromInput(i: JudgmentInput): Map<String, String> = buildMap {
    val tip = i.tipKrivicnogDjela.ifEmpty { "nezakonit ribolov" }
    val isHunting = isHuntingType(tip)
    put("brojPredmeta", i.brojPredmeta.ifEmpty { "NOVI" })
    put("tipKrivicnogDjela", tip)
    put("clanKZ", i.clanKZ.ifEmpty { if (isHunting) "325.1" else "326.2" })
    put("saizvrsilastvo", i.saizvrsilastvo)
    put("zabranjenoSredstvo", i.zabranjenoSredstvo)
    put("lovostajIliZabranjeneVode", i.lovostajIliZabranjeneVode)
    put("velikaKolicina", i.velikaKolicina)
    put("elektricnaStruja", i.elektricnaStruja)
    put("agregat", i.agregat)
    put("sonda", i.sonda)
    put("pretvarac", i.pretvarac)
    put("prisutanUlov", i.prisutanUlov)
    put("kolicinaUlovaKg", i.kolicinaUlovaKg.toString())
    put("oduzimanjePredmeta", i.oduzimanjePredmeta)
    put("ranijeOsudjivan", i.ranijeOsudjivan)
    put("brojOkrivljenih", i.brojOkrivljenih.toString())
    put("brojSvjedoka", i.brojSvjedoka.toString())
    // Hunting-specific (no-ops on fishing queries since weights are masked)
    put("tudjeLoviste", i.tudjeLoviste)
    put("krupnaDivljac", i.krupnaDivljac)
    put("zasticenaVrsta", i.zasticenaVrsta)
    put("bezPosebneDozvole", i.bezPosebneDozvole)
    put("masovnoUnistavanje", i.masovnoUnistavanje)
    put("vatrenoOruzje", i.vatrenoOruzje)
    put("lovackiPsi", i.lovackiPsi.toString())
    put("bezOruznogLista", i.bezOruznogLista)
    put("bezDozvoleZaLov", i.bezDozvoleZaLov)
    put("odstreljenaDivljac", i.odstreljenaDivljac)
    put("vrstaDivljaci", i.vrstaDivljaci)
    put("brojGrlaDivljaci", i.brojGrlaDivljaci.toString())
    put("sticajDrugogDela", i.sticajDrugogDela)
}
