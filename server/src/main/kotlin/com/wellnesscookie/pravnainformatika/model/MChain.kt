package com.wellnesscookie.pravnainformatika.model

import java.io.File
import kotlin.random.Random

/**
 * Order-N (default 2 → trigram) word-level Markov chain.
 * Port of `web/src/models/markovChain.js`.
 */
class MarkovChain(private val order: Int = 2) {

    private val transitions = HashMap<String, HashMap<String, Int>>()
    private val starters = mutableListOf<String>()

    fun train(texts: Iterable<String>) {
        for (text in texts) {
            val words = text.replace(Regex("""\s+"""), " ").trim().split(" ").filter { it.isNotEmpty() }
            if (words.size < order + 1) continue

            starters += words.subList(0, order).joinToString(" ")
            var i = 0
            val limit = words.size - order - 1
            while (i <= limit) {
                val key = words.subList(i, i + order).joinToString(" ")
                val next = words[i + order]
                val table = transitions.getOrPut(key) { HashMap() }
                table[next] = (table[next] ?: 0) + 1
                i++
            }
        }
    }

    fun generate(maxWords: Int = 80, seed: String? = null): String {
        if (starters.isEmpty()) return ""
        val current: String = if (!seed.isNullOrBlank()) {
            val seedWords = seed.lowercase().split(Regex("""\s+"""))
            starters.firstOrNull { s -> seedWords.any { sw -> sw in s.lowercase() } }
                ?: starters[Random.nextInt(starters.size)]
        } else {
            starters[Random.nextInt(starters.size)]
        }

        val words = current.split(" ").toMutableList()
        var produced = 0
        while (produced < maxWords) {
            val key = words.takeLast(order).joinToString(" ")
            val options = transitions[key] ?: break
            val next = pickWeighted(options) ?: break
            words += next
            produced++
            if (words.size > 20 && next.endsWith(".")) break
        }
        return words.joinToString(" ")
    }

    val isTrained: Boolean get() = transitions.isNotEmpty()

    private fun pickWeighted(options: Map<String, Int>): String? {
        if (options.isEmpty()) return null
        val total = options.values.sum()
        var r = Random.nextDouble() * total
        var last: String? = null
        for ((word, count) in options) {
            last = word
            r -= count
            if (r <= 0) return word
        }
        return last
    }
}

/** One Markov branch (set of 3 chains per section). */
class MarkovBranch {
    val intro = MarkovChain(2)
    val reasoning = MarkovChain(2)
    val sentencing = MarkovChain(2)
    var fileCount: Int = 0
        private set

    fun trainFrom(files: Collection<File>) {
        val introTexts = mutableListOf<String>()
        val reasoningTexts = mutableListOf<String>()
        val sentencingTexts = mutableListOf<String>()
        for (f in files) {
            runCatching {
                val s = parseTxtSections(f.readText())
                if (s.intro.length > 50) introTexts += s.intro
                if (s.reasoning.length > 100) reasoningTexts += s.reasoning
                if (s.sentencing.length > 50) sentencingTexts += s.sentencing
                fileCount++
            }
        }
        intro.train(introTexts)
        reasoning.train(reasoningTexts)
        sentencing.train(sentencingTexts)
    }

    fun chainFor(section: String): MarkovChain? = when (section) {
        "intro" -> intro
        "reasoning" -> reasoning
        "sentencing" -> sentencing
        else -> null
    }
}

/**
 * Per-branch Markov registry. fishing/hunting each get their own set of 3 chains
 * trained from their respective subfolder under txt/presude/.
 *
 * Layout under txtDir:
 *   nezakonit ribolov/   →  fishing branch
 *   nezakonit lov/       →  hunting branch
 *   (any other .txt at root or in unknown subfolder is treated as fishing —
 *    the legacy flat layout was fishing-only.)
 */
object MarkovModels {

    val fishing = MarkovBranch()
    val hunting = MarkovBranch()

    private var trained = false

    fun train(txtDir: File) {
        if (trained) return
        if (!txtDir.exists()) {
            println("[Markov] txt dir not found at ${txtDir.absolutePath} — models stay untrained")
            return
        }
        val huntingDir = File(txtDir, "nezakonit lov")
        val fishingDir = File(txtDir, "nezakonit ribolov")

        val huntingFiles = if (huntingDir.exists()) collectTxts(huntingDir) else emptyList()
        // Files NOT in nezakonit lov/ are treated as fishing (covers both the
        // explicit nezakonit ribolov/ subfolder and any legacy flat-txt-dir drop).
        val fishingFiles = txtDir.walkTopDown()
            .filter { it.isFile && it.extension.equals("txt", ignoreCase = true) }
            .filter { !it.absolutePath.contains("${File.separator}nezakonit lov${File.separator}") }
            .toList()

        if (huntingFiles.isNotEmpty()) hunting.trainFrom(huntingFiles)
        if (fishingFiles.isNotEmpty()) fishing.trainFrom(fishingFiles)

        trained = true
        println(
            "[Markov] trained — fishing=${fishing.fileCount} files, hunting=${hunting.fileCount} files"
        )
    }

    private fun collectTxts(dir: File): List<File> = dir.walkTopDown()
        .filter { it.isFile && it.extension.equals("txt", ignoreCase = true) }
        .toList()

    /** Generates a section using the branch's chain. `branch="hunting"` → hunting chain,
     * anything else (including "fishing", "ribolov", null) → fishing chain. */
    fun generate(section: String, seed: String?, maxWords: Int = 80, branch: String = "fishing"): String? {
        val br = if (branch.equals("hunting", ignoreCase = true) || "lov" == branch.lowercase()) hunting else fishing
        val chain = br.chainFor(section) ?: return null
        if (!chain.isTrained) return null
        val text = chain.generate(maxWords, seed)
        if (text.isBlank() || text.split(Regex("""\s+""")).size < 8) return null
        return text
    }
}

internal data class JudgmentTxtSections(
    val intro: String,
    val facts: String,
    val reasoning: String,
    val sentencing: String,
)

internal fun parseTxtSections(text: String): JudgmentTxtSections {
    val fullText = text.replace(Regex("""\r?\n"""), " ").replace(Regex("""\s+"""), " ")
    val presuduIdx = Regex("""P\s*R\s*E\s*S\s*U\s*D\s*U""", RegexOption.IGNORE_CASE).find(fullText)?.range?.first ?: -1
    val obrazlozenjeIdx = Regex("""O\s*b\s*r\s*a\s*z\s*l\s*o\s*ž\s*e\s*nj?\s*e""", RegexOption.IGNORE_CASE).find(fullText)?.range?.first ?: -1
    val uImeIdx = Regex("""U\s+IME\s+(CRNE\s+GORE|NARODA)""", RegexOption.IGNORE_CASE).find(fullText)?.range?.first ?: -1

    val intro = if (uImeIdx >= 0 && presuduIdx > uImeIdx) {
        fullText.substring(uImeIdx, presuduIdx).trim()
    } else ""

    val sentencing = if (presuduIdx >= 0) {
        val end = if (obrazlozenjeIdx > presuduIdx) obrazlozenjeIdx else fullText.length
        fullText.substring(presuduIdx, end).trim()
    } else ""

    val reasoning = if (obrazlozenjeIdx >= 0) {
        val courtEndIdx = Regex("""OSNOVNI\s+SUD\s+U\s+\w+,?\s*dana""", RegexOption.IGNORE_CASE)
            .find(fullText)?.range?.first ?: -1
        val end = if (courtEndIdx > obrazlozenjeIdx) courtEndIdx else fullText.length
        fullText.substring(obrazlozenjeIdx, end).trim()
    } else ""

    return JudgmentTxtSections(intro = intro, facts = "", reasoning = reasoning, sentencing = sentencing)
}
