package com.wellnesscookie.pravnainformatika

import com.wellnesscookie.pravnainformatika.akomantoso.AkomaNtosoBuilder
import com.wellnesscookie.pravnainformatika.db.CaseStore
import com.wellnesscookie.pravnainformatika.db.CbrStore
import com.wellnesscookie.pravnainformatika.model.CbrRequest
import com.wellnesscookie.pravnainformatika.model.GenerateDescriptionRequest
import com.wellnesscookie.pravnainformatika.model.GenerateDescriptionResponse
import com.wellnesscookie.pravnainformatika.model.Glava25Result
import com.wellnesscookie.pravnainformatika.model.JudgmentRequest
import com.wellnesscookie.pravnainformatika.model.JudgmentResult
import com.wellnesscookie.pravnainformatika.model.MarkovModels
import com.wellnesscookie.pravnainformatika.model.ReasoningRequest
import com.wellnesscookie.pravnainformatika.doc_parsing.Glava25Parser
import com.wellnesscookie.pravnainformatika.rule.CbrService
import com.wellnesscookie.pravnainformatika.rule.DescriptionGenerator
import com.wellnesscookie.pravnainformatika.rule.JudiciaryLogic
import com.wellnesscookie.pravnainformatika.rule.RuleReasoningRunner
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.http.content.staticFiles
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import java.io.File

private val jsonConfig = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

private fun resolveResourcesRoot(): File {
    val override = System.getProperty("pravna.resources")?.let { File(it) }
    if (override != null && override.exists()) return override
    // Walk up from the working dir until we find one containing `resources/data/cases`
    var dir: File? = File(".").absoluteFile.canonicalFile
    repeat(6) {
        val candidate = File(dir, "resources/data/cases")
        if (candidate.exists()) return File(dir, "resources")
        dir = dir?.parentFile ?: return@repeat
    }
    return File("resources").absoluteFile
}

private fun resolveWebDist(projectRoot: File): File? {
    val override = System.getProperty("pravna.webDist")?.let { File(it) }
    if (override != null && override.exists()) return override
    val candidates = listOf(
        File(projectRoot, "app/webApp/build/dist/wasmJs/productionExecutable"),
        File(projectRoot, "app/webApp/build/dist/wasmJs/developmentExecutable"),
    )
    return candidates.firstOrNull { File(it, "index.html").exists() }
}

fun main() {
    val port = System.getProperty("pravna.port")?.toIntOrNull()
        ?: System.getenv("PORT")?.toIntOrNull()
        ?: 8080
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) { json(jsonConfig) }
    install(CORS) {
        anyHost()
        allowHeader("Content-Type")
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
        allowNonSimpleContentTypes = true
    }
    install(CallLogging)
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (cause.message ?: "Unknown")))
        }
    }

    val resourcesRoot = resolveResourcesRoot()
    println("[server] resources root: ${resourcesRoot.absolutePath}")
    val projectRoot = resourcesRoot.parentFile
    val webDist = resolveWebDist(projectRoot)
    if (webDist != null) {
        println("[server] serving web dist from: ${webDist.absolutePath}")
    } else {
        println("[server] no web dist found — run :app:webApp:wasmJsBrowserDistribution to enable / single-origin serving")
    }

    val cases = CaseStore(resourcesRoot).also { it.load() }
    val cbrStore = CbrStore(resourcesRoot).also {
        it.load()
        it.loadGeneratedFrom(cases.all())
    }
    val cbr = CbrService(cbrStore)
    val ruleRunner = RuleReasoningRunner(resourcesRoot)
    MarkovModels.train(File(resourcesRoot, "txt/presude"))

    // Cache Glava25 parse result; it doesn't change at runtime.
    val glava25: Glava25Result? = Glava25Parser.parse(File(resourcesRoot, "data/glava25/criminal_code.xml"))

    val casesXmlDir = File(resourcesRoot, "data/cases/akomantoso").apply { mkdirs() }

    routing {
        get("/health") { call.respondText("ok") }

        route("/api") {

            get("/cases") { call.respond(cases.all()) }

            get("/statistics") { call.respond(cases.statistics()) }

            get("/case-types") { call.respond(cases.caseTypes()) }

            get("/search/type/{type}") {
                val type = call.parameters["type"].orEmpty()
                call.respond(cases.searchByType(type))
            }

            get("/cases/{id...}") {
                val id = call.parameters.getAll("id")?.joinToString("/").orEmpty()
                val rec = cases.byId(id)
                if (rec != null) call.respond(rec)
                else call.respond(
                    HttpStatusCode.NotFound,
                    mapOf("error" to "Case not found", "requestedId" to id)
                )
            }

            delete("/cases/{id...}") {
                val id = call.parameters.getAll("id")?.joinToString("/").orEmpty()
                val rec = cases.delete(id)
                if (rec != null) {
                    // Also remove from the in-memory CBR store so it stops
                    // showing up in /api/cbr-reasoning results.
                    cbrStore.remove(rec.caseNumber)
                    call.respond(mapOf("status" to "success", "deleted" to id))
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Predmet nije pronađen"))
                }
            }

            get("/glava25") {
                if (glava25 != null) call.respond(glava25)
                else call.respond(HttpStatusCode.NotFound, mapOf("error" to "Glava 25 file not found"))
            }

            get("/akomantoso/{id}") {
                val id = call.parameters["id"].orEmpty()
                val xml = cases.akomantosoXml(id)
                if (xml != null) call.respondText(xml, ContentType.Application.Xml)
                else call.respond(HttpStatusCode.NotFound, mapOf("error" to "Akoma Ntoso XML not found"))
            }

            post("/reasoning") {
                val req = call.receive<ReasoningRequest>()
                val result = when {
                    !req.caseId.isNullOrBlank() -> {
                        val rec = cases.byId(req.caseId!!)
                        if (rec == null) {
                            call.respond(HttpStatusCode.NotFound, mapOf("error" to "Predmet nije pronadjen"))
                            return@post
                        }
                        ruleRunner.reasonByCase(rec)
                    }
                    req.facts.isNotEmpty() -> ruleRunner.reasonByFacts(req.facts)
                    else -> {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Either caseId or facts required"))
                        return@post
                    }
                }
                call.respond(result)
            }

            post("/cbr-reasoning") {
                val req = call.receive<CbrRequest>()
                val caseId = req.caseId
                if (caseId.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "caseId je obavezan"))
                    return@post
                }
                val parsed = cases.byId(caseId)
                if (parsed == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Predmet nije pronadjen"))
                    return@post
                }
                call.respond(cbr.query(parsedCase = parsed))
            }
            post("/cbr-reasoning-input") {
                val req = call.receive<CbrRequest>()
                if (req.query.isEmpty()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "query je obavezan"))
                    return@post
                }
                call.respond(cbr.queryFromRaw(req.query))
            }

            post("/generate-description") {
                val req = call.receive<GenerateDescriptionRequest>()
                call.respond(GenerateDescriptionResponse(DescriptionGenerator.generate(req.input)))
            }

            post("/generate-judgment") {
                val req = call.receive<JudgmentRequest>()
                val validationError = JudiciaryLogic.validateUserInput(req.input)
                if (validationError != null) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to validationError))
                    return@post
                }

                val fallbackYear = LocalDate.now().year
                val identity = JudiciaryLogic.parseCaseIdentity(
                    req.input.brojPredmeta,
                    casesXmlDir,
                    fallbackYear,
                )

                val decision = JudiciaryLogic.deriveDecisionFromSignals(
                    req.ruleReasoning,
                    req.cbrReasoning,
                    req.decisionOverride,
                )
                val sections = JudiciaryLogic.generateNarrativeSections(
                    req.input,
                    decision,
                    req.ruleReasoning,
                    req.cbrReasoning,
                )

                val isoDate = req.input.datumPresude
                    .takeIf { it.matches(Regex("""\d{4}-\d{2}-\d{2}""")) }
                    ?: LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

                // Resolve target directory + final identity before building XML.
                // Otherwise, a collision rename would only bump the file name
                // while the XML body keeps the OLD brojPredmeta — producing two
                // cases with identical FRBRtitle / proprietary id.
                val tip = req.input.tipKrivicnogDjela.lowercase()
                val subfolder = when {
                    "ribolov" in tip -> "ribolov"
                    "lov" in tip -> "lov"
                    "326" in req.input.clanKZ -> "ribolov"
                    "325" in req.input.clanKZ -> "lov"
                    else -> ""
                }
                val targetDir = if (!req.previewOnly && subfolder.isNotEmpty()) {
                    File(casesXmlDir, subfolder).apply { mkdirs() }
                } else if (!req.previewOnly) casesXmlDir else casesXmlDir

                val (finalIdentity, finalInput) = if (
                    !req.previewOnly && File(targetDir, "${identity.fileBase}.xml").exists()
                ) {
                    val nextNum = JudiciaryLogic.nextSequentialCaseNumber(casesXmlDir, identity.year)
                    val newBroj = nextNum.toString()
                    val yr = identity.year
                    val bumped = identity.copy(
                        broj = newBroj,
                        fileBase = "K ${newBroj}_$yr",
                        judgmentName = "K_${newBroj}_$yr",
                        fallbackCaseNumber = "$newBroj/${yr.toString().takeLast(2)}",
                    )
                    bumped to req.input.copy(brojPredmeta = bumped.fallbackCaseNumber)
                } else {
                    identity to req.input
                }

                val xml = AkomaNtosoBuilder.build(finalInput, decision, finalIdentity, sections, isoDate)

                val xmlFileName = if (!req.previewOnly) {
                    val xmlFile = File(targetDir, "${finalIdentity.fileBase}.xml")
                    xmlFile.writeText(xml)
                    // Hot-reload into the in-memory stores so the new case shows up in /api/cases.
                    com.wellnesscookie.pravnainformatika.doc_parsing.AkomaNtosoParser.parse(xmlFile)?.let { parsed ->
                        cases.replaceOrAdd(parsed)
                        cbrStore.replaceOrAdd(CbrStore.buildCbrRecordFromParsed(parsed))
                    }
                    xmlFile.name
                } else null

                call.respond(
                    JudgmentResult(
                        xmlFile = xmlFileName,
                        caseId = finalIdentity.fileBase,
                        decision = decision,
                        sections = sections,
                        xml = xml,
                    )
                )
            }

        }

        if (webDist != null) {
            // Single-origin: serve the wasm bundle at /. Anything not under /api/ falls
            // through to here. SPA-style fallback to index.html for unknown paths.
            staticFiles("/", webDist) {
                default("index.html")
            }
        } else {
            get("/") {
                call.respondText(
                    """
                    Pravna Informatika KMP server is running.
                    Web bundle isn't built yet — run:
                      ./gradlew :app:webApp:wasmJsBrowserDistribution
                    or use the dev workflow:
                      ./gradlew :app:webApp:wasmJsBrowserDevelopmentRun
                    See /api/cases, /api/statistics, /api/glava25 etc.
                    """.trimIndent()
                )
            }
        }
    }
}
