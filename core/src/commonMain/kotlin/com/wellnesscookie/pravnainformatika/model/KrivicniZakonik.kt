package com.wellnesscookie.pravnainformatika.model

import kotlinx.serialization.Serializable

@Serializable
data class ArticleParagraph(
    val eId: String = "",
    val num: String = "",
    val text: String = "",
)

@Serializable
data class Article(
    val eId: String = "",
    val num: String = "",
    val heading: String = "",
    val paragraphs: List<ArticleParagraph> = emptyList(),
    val content: String = "",
)

@Serializable
data class Glava25Result(
    val articles: List<Article> = emptyList(),
)
