package com.wellnesscookie.pravnainformatika

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wellnesscookie.pravnainformatika.data.ApiService
import com.wellnesscookie.pravnainformatika.data.readDeepLinkCaseId
import com.wellnesscookie.pravnainformatika.ui.BackArrowButton
import com.wellnesscookie.pravnainformatika.ui.CasesTab
import com.wellnesscookie.pravnainformatika.ui.Glava25Tab
import com.wellnesscookie.pravnainformatika.ui.NewCaseTab

// Centralized palette — used across all UI modules. Brown/orange primary,
// emerald secondary, slate surfaces.
internal object AppPalette {
    val primary = Color(0xFFC2410C)        // orange-700 (burnt orange)
    val primaryDark = Color(0xFF7C2D12)    // orange-900 (header bg, deep brown-orange)
    val primarySoft = Color(0xFFFFF3E8)    // warm orange-50
    val secondary = Color(0xFF059669)      // emerald-600
    val secondarySoft = Color(0xFFD1FAE5)  // emerald-100
    val info = Color(0xFF0284C7)           // sky-600 (light blue)
    val infoSoft = Color(0xFFE0F2FE)       // sky-100
    val accent = Color(0xFFF59E0B)         // amber-500
    val danger = Color(0xFFDC2626)         // red-600
    val dangerSoft = Color(0xFFFEE2E2)     // red-100
    val warningSoft = Color(0xFFFEF3C7)    // amber-100
    val warningText = Color(0xFF92400E)    // amber-900
    val background = Color(0xFFF8FAFC)     // slate-50
    val surface = Color.White
    val surfaceVariant = Color(0xFFF1F5F9) // slate-100
    val border = Color(0xFFE2E8F0)         // slate-200
    val text = Color(0xFF0F172A)           // slate-900
    val textMuted = Color(0xFF64748B)      // slate-500
    val textSubtle = Color(0xFF94A3B8)     // slate-400
}

private val AppColors = lightColorScheme(
    primary = AppPalette.primary,
    onPrimary = Color.White,
    secondary = AppPalette.secondary,
    background = AppPalette.background,
    surface = AppPalette.surface,
    onSurface = AppPalette.text,
)

@Composable
@Preview
fun App() {
    val api = remember { ApiService() }
    // Set by opening a "reference" chip from a similar-case list (see CbrPanel in
    // NewCaseTab) in a new browser tab — lands on "Lista presuda" with that case open.
    val deepLinkCaseId = remember { readDeepLinkCaseId() }

    MaterialTheme(colorScheme = AppColors) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            var selected by remember { mutableStateOf(0) }
            // Article overlay: opened by clicking a "Čl. X" chip inside a presuda. It renders on
            // top of the current tab (not a tab switch) and closes via its own back arrow, so the
            // user never loses their place in "Lista presuda". Navigating to "Resursi" directly
            // from the header is a separate, unrelated path (no highlight, no overlay).
            var articleOverlay by remember { mutableStateOf<String?>(null) }
            val tabs = listOf("Lista presuda", "Resursi", "Nova presuda")

            Column(modifier = Modifier.fillMaxSize()) {
                Header(tabs = tabs, selectedTab = selected, onTabSelected = { selected = it })
                Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    when (selected) {
                        0 -> CasesTab(
                            api,
                            onOpenArticle = { article -> articleOverlay = article },
                            initialSelectedCaseId = deepLinkCaseId,
                        )
                        1 -> Glava25Tab(api)
                        2 -> NewCaseTab(api)
                    }
                    ArticleOverlay(
                        api = api,
                        articleNum = articleOverlay,
                        onBack = { articleOverlay = null },
                    )
                }
            }
        }
    }
}

// Slides/fades the relevant Glava 25 article in over whichever tab is currently open, with its
// own back arrow — clicking a "Čl. X" chip shouldn't yank the user over to the "Resursi" tab.
@Composable
private fun ArticleOverlay(api: ApiService, articleNum: String?, onBack: () -> Unit) {
    AnimatedVisibility(
        visible = articleNum != null,
        enter = fadeIn(tween(250)) + slideInHorizontally(
            animationSpec = tween(300),
            initialOffsetX = { it / 6 },
        ),
        exit = fadeOut(tween(150)) + slideOutHorizontally(
            animationSpec = tween(200),
            targetOffsetX = { it / 6 },
        ),
    ) {
        Column(modifier = Modifier.fillMaxSize().background(AppPalette.background)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BackArrowButton(onClick = onBack)
                Spacer(Modifier.width(12.dp))
                Text(
                    "Glava 25 — Krivični zakonik",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = AppPalette.text,
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                Glava25Tab(api, highlightArticleNum = articleNum)
            }
        }
    }
}

@Composable
private fun Header(tabs: List<String>, selectedTab: Int, onTabSelected: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppPalette.primaryDark)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                text = "Pravni sistem",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Krivična dela protiv životne sredine — Glava 25 KZ CG",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            tabs.forEachIndexed { i, title ->
                val isSelected = selectedTab == i
                Text(
                    text = title,
                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f),
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isSelected) Color.White.copy(alpha = 0.15f)
                            else Color.Transparent,
                        )
                        .clickable { onTabSelected(i) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}
