package com.wellnesscookie.pravnainformatika.rule

import com.wellnesscookie.pravnainformatika.model.CaseRecord
import com.wellnesscookie.pravnainformatika.model.PenaltyRange
import com.wellnesscookie.pravnainformatika.model.ReasoningResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Invokes `rule/rez_pravila.py` via ProcessBuilder. Mirrors what the
 * original Express route did (web/src/routes/reasoningRoutes.js).
 *
 * The Python script expects to be run with `cwd` = the resources root, because
 * its `PROJECT_ROOT = Path(__file__).parent.parent` then resolves to that root
 * (so it finds `txt/presude/...` for fact-extraction).
 */
class RuleReasoningRunner(private val resourcesRoot: File) {

    private val scriptPath: File = File(resourcesRoot, "rule/rez_pravila.py")
    private val pythonCommand: String = if (isWindows()) "py" else "python3"
    private val timeoutSeconds: Long = 60
    private val json = Json { ignoreUnknownKeys = true }

    /** Reason about an existing case by id (the case must have an `xmlFile`). */
    fun reasonByCase(case: CaseRecord): ReasoningResult {
        val xml = case.xmlFile ?: return errorResult("Predmet nema povezan XML fajl")
        log("=== request: by caseId=${case.caseNumber} (xml=${File(xml).name}) ===")
        return invoke(listOf("--case", xml, "--json"))
    }

    /** Reason about a free-form fact map (used by the Nova presuda form). */
    fun reasonByFacts(facts: Map<String, String>): ReasoningResult {
        log("=== request: by facts (${facts.size} fields, " +
            "tip='${facts["tipKrivicnogDjela"].orEmpty()}', " +
            "clanKZ='${facts["clanKZ"].orEmpty()}') ===")

        // Promote numeric-looking values to JSON numbers — the Python script does
        // mixed-type comparisons internally and assumes JS-typed input.
        val payload: MutableMap<String, JsonElement> = facts
            .mapValues { (_, v) -> typedPrimitive(v) as JsonElement }
            .toMutableMap()

        // Python expects `articles: ["325.1"]` (list of "<num>.<para>" strings).
        // The form sends `clanKZ: "cl. 325 st. 1"`; translate it so the
        // SentenceCalculator can match hunting/fishing rules.
        facts["clanKZ"]?.let { clan ->
            val art = ARTICLE_RE.find(clan)
            if (art != null) {
                val num = art.groupValues[1]
                val st = art.groupValues.getOrNull(2)?.takeIf { it.isNotEmpty() }
                val label = if (st != null) "$num.$st" else num
                payload["articles"] = JsonArray(listOf(JsonPrimitive(label)))
                log("translation: clanKZ='$clan' → articles=[$label]")
            } else {
                log("translation: clanKZ='$clan' did NOT match ARTICLE_RE → no articles[] added (Python will see empty applicable_articles)")
            }
        } ?: log("translation: clanKZ missing → no articles[] added")

        // Python uses `previously_convicted: bool` instead of `ranijeOsudjivan: "da/ne"`.
        facts["ranijeOsudjivan"]?.let {
            val flag = it.equals("da", ignoreCase = true)
            payload["previously_convicted"] = JsonPrimitive(flag)
            log("translation: ranijeOsudjivan='$it' → previously_convicted=$flag")
        }
        // Infer crime_type from clanKZ if not set explicitly.
        if (payload["crime_type"] == null) {
            val tip = facts["tipKrivicnogDjela"].orEmpty()
            val clan = facts["clanKZ"].orEmpty()
            val inferred = when {
                "ribolov" in tip.lowercase() || "326" in clan -> "nezakonit ribolov"
                "lov" in tip.lowercase() || "325" in clan -> "nezakonit lov"
                else -> null
            }
            if (inferred != null) {
                payload["crime_type"] = JsonPrimitive(inferred)
                log("translation: inferred crime_type='$inferred' from clanKZ='$clan' / tip='$tip'")
            }
        }

        val factsJson = json.encodeToString(JsonObject.serializer(), JsonObject(payload))
        log("payload sent to Python (${factsJson.length} chars): " + factsJson.take(800) +
            (if (factsJson.length > 800) "... [truncated]" else ""))
        return invoke(listOf("--facts", factsJson, "--json"))
    }

    private val ARTICLE_RE = Regex("""(\d{2,3})(?:[.\s]*(?:st\.?|stav)?\s*(\d{1,2}))?""", RegexOption.IGNORE_CASE)

    private fun typedPrimitive(v: String): JsonPrimitive {
        if (v.isEmpty()) return JsonPrimitive(v)
        v.toLongOrNull()?.let { return JsonPrimitive(it) }
        v.toDoubleOrNull()?.let { return JsonPrimitive(it) }
        return JsonPrimitive(v)
    }

    private fun invoke(scriptArgs: List<String>): ReasoningResult {
        if (!scriptPath.exists()) {
            log("ERROR: script missing at ${scriptPath.path}")
            return errorResult("Reasoning skripta ne postoji: ${scriptPath.path}")
        }
        val cmd = listOf(pythonCommand, scriptPath.absolutePath) + scriptArgs
        // Trim long --facts args in the log to one line.
        val shortCmd = cmd.joinToString(" ") {
            if (it.length > 120) it.take(120) + "...[+${it.length - 120}]" else it
        }
        log("exec: $shortCmd  (cwd=${resourcesRoot.absolutePath})")

        val started = System.currentTimeMillis()
        val process = ProcessBuilder(cmd)
            .directory(resourcesRoot)
            .redirectErrorStream(false)
            .start()

        val stdout = process.inputStream.bufferedReader().use { it.readText() }
        val stderr = process.errorStream.bufferedReader().use { it.readText() }
        val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        val elapsed = System.currentTimeMillis() - started

        if (!finished) {
            process.destroyForcibly()
            log("ERROR: timeout after ${elapsed}ms (limit ${timeoutSeconds}s)")
            return errorResult("Reasoning timeout (>$timeoutSeconds s)")
        }
        val exit = process.exitValue()
        log("python finished: exit=$exit  elapsed=${elapsed}ms  stdout=${stdout.length}B  stderr=${stderr.length}B")
        if (stderr.isNotBlank()) {
            // Always surface stderr — Python script writes errors and warnings here.
            stderr.trim().lineSequence().take(20).forEach { log("[python stderr] $it") }
        }
        if (exit != 0) {
            return errorResult("Reasoning skripta failed (exit $exit): ${stderr.trim().take(400)}")
        }
        return parseResult(stdout)
    }

    private fun parseResult(stdout: String): ReasoningResult {
        // The Python script may emit log lines before the JSON object — find the
        // first balanced {...} block in stdout.
        val start = stdout.indexOf('{')
        val end = stdout.lastIndexOf('}')
        if (start < 0 || end <= start) {
            log("WARN: no JSON object in stdout (first 200B: ${stdout.take(200)})")
            return ReasoningResult(rawOutput = stdout.trim())
        }
        val jsonText = stdout.substring(start, end + 1)
        val obj = runCatching { json.parseToJsonElement(jsonText) as JsonObject }.getOrNull()
        if (obj == null) {
            log("WARN: JSON parse failed; returning raw stdout (${jsonText.length}B)")
            return ReasoningResult(rawOutput = stdout.trim())
        }

        val verdict = (obj["verdict"] as? JsonPrimitive)?.contentOrNull
        val rationale = stringList(obj["rationale"])
        val recommendation = (obj["recommendation"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        val applied = stringList(obj["applicable_articles"])
        val violated = stringList(obj["violated_rules"])
        val mitig = stringList(obj["mitigating_factors"])
        val aggrav = stringList(obj["aggravating_factors"])
        val acquittal = (obj["acquittal"] as? JsonPrimitive)?.contentOrNull?.equals("true", ignoreCase = true) == true
        val actualSentence = (obj["actual_sentence"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        val penalty = (obj["penalty_range"] as? JsonObject)?.let {
            PenaltyRange(
                min = (it["min"] as? JsonPrimitive)?.contentOrNull ?: "N/A",
                max = (it["max"] as? JsonPrimitive)?.contentOrNull ?: "N/A",
            )
        } ?: PenaltyRange()

        log("result: verdict='${verdict ?: "(none)"}'  articles=${applied.size}  rules=${violated.size}  " +
            "mitig=${mitig.size}  aggrav=${aggrav.size}  penalty=${penalty.min}..${penalty.max}  " +
            "rec='${recommendation.take(80)}'")
        if (applied.isEmpty() && violated.isEmpty()) {
            log("HINT: applicable_articles=0 — Python could not match any rule. " +
                "Most common cause: missing/unparseable clanKZ in form input.")
        }

        return ReasoningResult(
            rawOutput = jsonText,
            verdict = verdict,
            rationale = rationale,
            recommendation = recommendation,
            appliedArticles = applied,
            violatedRules = violated,
            mitigatingFactors = mitig,
            aggravatingFactors = aggrav,
            penaltyRange = penalty,
            acquittal = acquittal,
            actualSentence = actualSentence,
        )
    }

    private fun stringList(el: JsonElement?): List<String> = when (el) {
        is JsonArray -> el.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        is JsonPrimitive -> listOf(el.contentOrNull.orEmpty()).filter { it.isNotEmpty() }
        else -> emptyList()
    }

    private fun errorResult(msg: String): ReasoningResult =
        ReasoningResult(rawOutput = msg, verdict = null, rationale = emptyList())

    private fun log(msg: String) {
        println("[RuleReasoning] $msg")
    }

    private fun isWindows(): Boolean =
        System.getProperty("os.name").orEmpty().lowercase().contains("windows")
}
