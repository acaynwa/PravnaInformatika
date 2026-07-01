package com.wellnesscookie.pravnainformatika.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wellnesscookie.pravnainformatika.data.ApiService
import com.wellnesscookie.pravnainformatika.model.Article
import com.wellnesscookie.pravnainformatika.model.Glava25Result
import kotlinx.coroutines.delay

private const val HIGHLIGHT_DURATION_MS = 10_000L
private val DIGITS_REGEX = Regex("""\d+""")

private fun digitsOnly(s: String): String = DIGITS_REGEX.find(s)?.value.orEmpty()

@Composable
fun Glava25Tab(api: ApiService, highlightArticleNum: String? = null) {
    var data by remember { mutableStateOf<Glava25Result?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        loading = true
        runCatching { api.glava25() }
            .onSuccess { data = it }
            .onFailure { error = it.message }
        loading = false
    }

    // Chip text from CasesTab is like "325" (article only) or "325.1" (article + stav). The
    // criminal-code XML stores Article.num as "Član 325" and paragraph num as "(1)", so we
    // compare on digits-only. A non-null targetStav means highlight just that paragraph.
    val targetArticle = highlightArticleNum
        ?.substringBefore(".")
        ?.let { digitsOnly(it) }
        ?.takeIf { it.isNotEmpty() }
    val targetStav = highlightArticleNum
        ?.takeIf { it.contains('.') }
        ?.substringAfter('.')
        ?.let { digitsOnly(it) }
        ?.takeIf { it.isNotEmpty() }

    // Drive a temporary highlight: true on each new target, false 10s later.
    var highlightActive by remember { mutableStateOf(false) }
    LaunchedEffect(targetArticle, targetStav, data) {
        val articles = data?.articles ?: return@LaunchedEffect
        val target = targetArticle ?: return@LaunchedEffect
        val idx = articles.indexOfFirst { digitsOnly(it.num) == target }
        if (idx >= 0) {
            listState.animateScrollToItem(idx + 1) // +1 for header item
            highlightActive = true
            delay(HIGHLIGHT_DURATION_MS)
            highlightActive = false
        }
    }

    Card(
        modifier = Modifier.fillMaxSize(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        when {
            loading -> Box(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            error != null -> Box(modifier = Modifier.padding(16.dp)) {
                Text("Greska: $error", color = MaterialTheme.colorScheme.error)
            }
            data == null || data!!.articles.isEmpty() -> Box(modifier = Modifier.padding(16.dp)) {
                Text("Nema sadrzaja.", color = Color.Gray)
            }
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(16.dp),
            ) {
                item {
                    Text(
                        "Glava 25 - Krivična dela protiv životne sredine",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                }
                itemsIndexed(data!!.articles, key = { _, a -> a.num.ifEmpty { a.heading } }) { _, article ->
                    val matches = highlightActive && digitsOnly(article.num) == targetArticle
                    ArticleCard(
                        article,
                        highlightArticle = matches && targetStav == null,
                        highlightStav = if (matches) targetStav else null,
                    )
                }
            }
        }
    }
}

@Composable
private fun ArticleCard(
    article: Article,
    highlightArticle: Boolean,
    highlightStav: String?,
) {
    val borderColor by animateColorAsState(
        targetValue = if (highlightArticle) Color(0xFFDC2626) else Color.Transparent,
        label = "article-highlight",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .border(2.dp, borderColor, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            (if (article.num.isNotEmpty()) "${article.num} - " else "") + article.heading,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            color = Color(0xFF0F172A),
        )
        Spacer(Modifier.height(4.dp))
        article.paragraphs.forEach { p ->
            ParagraphRow(p, highlighted = highlightStav != null && digitsOnly(p.num) == highlightStav)
        }
    }
}

@Composable
private fun ParagraphRow(p: com.wellnesscookie.pravnainformatika.model.ArticleParagraph, highlighted: Boolean) {
    val borderColor by animateColorAsState(
        targetValue = if (highlighted) Color(0xFFDC2626) else Color.Transparent,
        label = "stav-highlight",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, top = 4.dp, bottom = 4.dp)
            .border(2.dp, borderColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = (if (p.num.isNotEmpty()) "${p.num} " else "") + p.text,
            fontSize = 13.sp,
        )
    }
}
