package com.wellnesscookie.pravnainformatika.doc_parsing

import com.wellnesscookie.pravnainformatika.model.Article
import com.wellnesscookie.pravnainformatika.model.ArticleParagraph
import com.wellnesscookie.pravnainformatika.model.Glava25Result
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

object Glava25Parser {

    private val factory: DocumentBuilderFactory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
    }

    fun parse(file: File): Glava25Result? {
        if (!file.exists()) return null
        val doc = factory.newDocumentBuilder().parse(file)
        val articleEls = doc.getElementsByTagNameNS("*", "article")
        val articles = buildList {
            for (i in 0 until articleEls.length) {
                val article = articleEls.item(i) as Element
                val eId = article.getAttribute("eId").orEmpty()
                val articleNum = article.firstChildText("num")
                val heading = article.firstChildText("heading").ifEmpty { "Nepoznat" }

                val paragraphItems = mutableListOf<ArticleParagraph>()
                val content = mutableListOf<String>()

                val paragraphs = article.getElementsByTagNameNS("*", "paragraph")
                for (j in 0 until paragraphs.length) {
                    val paragraph = paragraphs.item(j) as Element
                    val paraNum = paragraph.firstChildText("num").trim()
                    val paraId = paragraph.getAttribute("eId").ifEmpty {
                        if (eId.isNotEmpty()) "${eId}__para_${j + 1}" else "para_${j + 1}"
                    }
                    val pNodes = paragraph.getElementsByTagNameNS("*", "p")
                    if (pNodes.length > 0) {
                        val parts = mutableListOf<String>()
                        for (k in 0 until pNodes.length) {
                            val text = (pNodes.item(k).textContent ?: "").trim()
                            if (text.isNotEmpty()) {
                                parts += text
                                content += if (paraNum.isNotEmpty()) "$paraNum $text" else text
                            }
                        }
                        val paraText = parts.joinToString(" ").trim()
                        if (paraText.isNotEmpty()) {
                            paragraphItems += ArticleParagraph(paraId, paraNum, paraText)
                        }
                    } else {
                        val raw = (paragraph.textContent ?: "").trim()
                        if (raw.isNotEmpty()) {
                            content += raw
                            paragraphItems += ArticleParagraph(paraId, paraNum, raw)
                        }
                    }
                }

                add(
                    Article(
                        eId = eId,
                        num = articleNum,
                        heading = heading,
                        paragraphs = paragraphItems,
                        content = content.joinToString("\n\n"),
                    )
                )
            }
        }
        return Glava25Result(articles)
    }

    private fun Element.firstChildText(localName: String): String {
        val list = getElementsByTagNameNS("*", localName)
        return if (list.length > 0) (list.item(0).textContent ?: "").trim() else ""
    }
}
