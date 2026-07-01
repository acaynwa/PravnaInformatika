package com.wellnesscookie.pravnainformatika.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wellnesscookie.pravnainformatika.data.ApiService
import com.wellnesscookie.pravnainformatika.model.CaseRecord
import kotlinx.coroutines.launch

private const val FILTER_RIBOLOV = "Nezakonit ribolov"
private const val FILTER_LOV = "Nezakonit lov"

@Composable
fun CasesTab(api: ApiService, onOpenArticle: (String) -> Unit, initialSelectedCaseId: String? = null) {
    val scope = rememberCoroutineScope()
    var cases by remember { mutableStateOf<List<CaseRecord>>(emptyList()) }
    var selected by remember { mutableStateOf<CaseRecord?>(null) }
    var typeFilter by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var pendingDelete by remember { mutableStateOf<CaseRecord?>(null) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    var deepLinkApplied by remember { mutableStateOf(false) }

    LaunchedEffect(typeFilter) {
        loading = true
        error = null
        runCatching {
            cases = if (typeFilter.isEmpty()) api.cases() else api.searchByType(typeFilter)
        }.onFailure {
            error = it.message
        }
        loading = false
    }

    // Deep-link entry point: a case opened via CBR "reference" in a new browser tab lands
    // here with `initialSelectedCaseId` set — select it as soon as the case list is loaded.
    LaunchedEffect(cases) {
        if (!deepLinkApplied && initialSelectedCaseId != null && cases.isNotEmpty()) {
            cases.firstOrNull { it.id == initialSelectedCaseId }?.let { selected = it }
            deepLinkApplied = true
        }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Brisanje presude") },
            text = {
                Text(
                    "Da li si siguran da želiš da obrišeš presudu ${target.caseNumber}? " +
                        "XML fajl ce biti obrisan sa diska i predmet ce biti uklonjen iz CBR baze."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val toDelete = target
                    pendingDelete = null
                    scope.launch {
                        runCatching { api.deleteCase(toDelete.id) }
                            .onSuccess {
                                cases = cases.filter { it.id != toDelete.id }
                                if (selected?.id == toDelete.id) selected = null
                            }
                            .onFailure { deleteError = it.message }
                    }
                }) { Text("Obriši", color = Color(0xFFB91C1C)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Otkaži") }
            },
        )
    }

    val previewOpen = selected != null
    val listWeight by animateFloatAsState(
        targetValue = if (previewOpen) 0.32f else 1f,
        animationSpec = tween(durationMillis = 380),
        label = "listWeight",
    )
    // Modifier.weight() requires a strictly positive value, so floor it above zero
    // (the detail panel is invisible anyway while its weight is at the floor).
    val detailWeight = (1f - listWeight).coerceAtLeast(0.0001f)

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar: back arrow (preview mode) or centered Lov/Ribolov filter (list mode)
        CasesTopBar(
            previewOpen = previewOpen,
            typeFilter = typeFilter,
            onFilterChange = { typeFilter = it },
            onBack = { selected = null },
        )
        Spacer(Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxSize()) {
            // List panel — full centered cards when no selection, minimized rail once a case is open.
            Box(modifier = Modifier.weight(listWeight).fillMaxHeight()) {
                when {
                    loading -> CenteredSpinner()
                    error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        Text("Greska: $error", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                    }
                    cases.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        Text("Nema predmeta.", color = Color.Gray, modifier = Modifier.padding(16.dp))
                    }
                    previewOpen -> LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
                        items(
                            items = cases,
                            key = { "${it.id}::${it.caseId.orEmpty()}::${it.xmlFile.orEmpty()}" },
                        ) { c ->
                            CaseRowMinimized(c, isSelected = c.id == selected?.id) { selected = c }
                        }
                    }
                    else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        LazyColumn(
                            modifier = Modifier.widthIn(max = 920.dp).fillMaxWidth().padding(horizontal = 16.dp),
                        ) {
                            items(
                                items = cases,
                                key = { "${it.id}::${it.caseId.orEmpty()}::${it.xmlFile.orEmpty()}" },
                            ) { c ->
                                CaseRowFull(c) { selected = c }
                            }
                        }
                    }
                }
            }

            // Detail panel — slides/expands in from the right once a case is selected.
            Box(modifier = Modifier.weight(detailWeight).fillMaxHeight()) {
                CasesDetailPanel(
                    previewOpen = previewOpen,
                    selected = selected,
                    deleteError = deleteError,
                    onOpenArticle = onOpenArticle,
                    onDelete = { pendingDelete = it },
                )
            }
        }
    }
}

@Composable
private fun CasesTopBar(
    previewOpen: Boolean,
    typeFilter: String,
    onFilterChange: (String) -> Unit,
    onBack: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth().height(56.dp)) {
        AnimatedVisibility(
            visible = previewOpen,
            modifier = Modifier.align(Alignment.CenterStart),
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(150)),
        ) {
            BackArrowButton(onClick = onBack)
        }
        AnimatedVisibility(
            visible = !previewOpen,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(tween(250)),
            exit = fadeOut(tween(150)),
        ) {
            TypeFilterButtons(typeFilter, onFilterChange)
        }
    }
}

@Composable
private fun CasesDetailPanel(
    previewOpen: Boolean,
    selected: CaseRecord?,
    deleteError: String?,
    onOpenArticle: (String) -> Unit,
    onDelete: (CaseRecord) -> Unit,
) {
    AnimatedVisibility(
        visible = previewOpen,
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
            modifier = Modifier.fillMaxSize().padding(start = 4.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
        ) {
            Column(modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
                selected?.let { c ->
                    CaseDetail(
                        c = c,
                        onOpenArticle = onOpenArticle,
                        onDelete = onDelete,
                    )
                    deleteError?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Brisanje nije uspjelo: $it",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CaseRowFull(c: CaseRecord, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(c.caseNumber.ifEmpty { c.id }, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(c.type, fontSize = 12.sp, color = Color(0xFF065F46))
                }
                Text(c.date.ifEmpty { "-" }, fontSize = 13.sp, color = Color.Gray)
            }
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    MiniInfo("Sud", c.court)
                    MiniInfo("Sudija", c.sudija)
                }
                Column(modifier = Modifier.weight(1f)) {
                    MiniInfo("Presuda", c.verdict)
                }
            }
        }
    }
}

@Composable
private fun MiniInfo(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text("$label: ", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF64748B))
        Text(value.ifEmpty { "-" }, fontSize = 12.sp, color = Color(0xFF1E293B))
    }
}

@Composable
private fun CaseRowMinimized(c: CaseRecord, isSelected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(
                if (isSelected) com.wellnesscookie.pravnainformatika.AppPalette.primarySoft else Color.Transparent,
                RoundedCornerShape(6.dp),
            )
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        Text(c.caseNumber.ifEmpty { c.id }, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Text(c.date.ifEmpty { "-" }, fontSize = 11.sp, color = Color.Gray)
    }
}

@Composable
private fun TypeFilterButtons(selected: String, onChange: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterButton("Ribolov", active = selected == FILTER_RIBOLOV) {
            onChange(if (selected == FILTER_RIBOLOV) "" else FILTER_RIBOLOV)
        }
        FilterButton("Lov", active = selected == FILTER_LOV) {
            onChange(if (selected == FILTER_LOV) "" else FILTER_LOV)
        }
    }
}

@Composable
private fun FilterButton(label: String, active: Boolean, onClick: () -> Unit) {
    if (active) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = com.wellnesscookie.pravnainformatika.AppPalette.primary,
                contentColor = Color.White,
            ),
        ) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick) { Text(label) }
    }
}

@Composable
internal fun BackArrowButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFFF1F5F9))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BackArrowIcon(Modifier.size(18.dp), Color(0xFF334155))
    }
}

@Composable
private fun CaseDetail(
    c: CaseRecord,
    onOpenArticle: (String) -> Unit,
    onDelete: (CaseRecord) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(c.caseNumber.ifEmpty { c.id }, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    if (c.generatedBy.isNotEmpty()) {
                        Button(
                            onClick = { onDelete(c) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFEE2E2),
                                contentColor = Color(0xFFB91C1C),
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text("Obriši", fontSize = 12.sp)
                        }
                    }
                }
                Text(c.type, color = Color(0xFF065F46), fontSize = 13.sp)
            }
            if (c.articles.isNotEmpty()) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("Reference", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color(0xFF64748B))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        c.articles.forEach { a ->
                            AssistChip(
                                onClick = { onOpenArticle(a) },
                                label = { Text("Čl. $a", fontSize = 12.sp) },
                                leadingIcon = { NotesIcon(Modifier.size(16.dp), com.wellnesscookie.pravnainformatika.AppPalette.primaryDark) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = com.wellnesscookie.pravnainformatika.AppPalette.primarySoft,
                                    labelColor = com.wellnesscookie.pravnainformatika.AppPalette.primaryDark,
                                ),
                            )
                        }
                    }
                }
            }
        }
        DetailRow("Sud", c.court)
        DetailRow("Datum", c.date)
        DetailRow("Sudija", c.sudija)
        DetailRow("Zapisničar", c.zapisnicar)
        DetailRow("Okrivljeni", c.defendant)
        DetailRow("Presuda", c.verdict)
        DetailRow("Kazna", c.sentence)
        if (c.caseDescription.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text("Činjenično stanje", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(c.caseDescription, fontSize = 12.sp)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("$label:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.width(120.dp))
        Text(value.ifEmpty { "-" }, fontSize = 13.sp)
    }
}

@Composable
private fun CenteredSpinner() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun NotesIcon(modifier: Modifier = Modifier, tint: Color) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = w * 0.1f, cap = StrokeCap.Round)
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.1f, w * 0.05f),
            size = Size(w * 0.8f, h * 0.9f),
            cornerRadius = CornerRadius(w * 0.1f),
            style = stroke,
        )
        val lineY1 = h * 0.35f
        val lineY2 = h * 0.55f
        val lineY3 = h * 0.75f
        val lx = w * 0.3f
        val rx = w * 0.7f
        drawLine(tint, Offset(lx, lineY1), Offset(rx, lineY1), strokeWidth = w * 0.08f, cap = StrokeCap.Round)
        drawLine(tint, Offset(lx, lineY2), Offset(rx, lineY2), strokeWidth = w * 0.08f, cap = StrokeCap.Round)
        drawLine(tint, Offset(lx, lineY3), Offset(w * 0.55f, lineY3), strokeWidth = w * 0.08f, cap = StrokeCap.Round)
    }
}

@Composable
private fun BackArrowIcon(modifier: Modifier = Modifier, tint: Color) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = w * 0.14f, cap = StrokeCap.Round)
        val midY = h * 0.5f
        drawLine(tint, Offset(w * 0.85f, midY), Offset(w * 0.15f, midY), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.15f, midY), Offset(w * 0.48f, h * 0.15f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.15f, midY), Offset(w * 0.48f, h * 0.85f), strokeWidth = stroke.width, cap = StrokeCap.Round)
    }
}
