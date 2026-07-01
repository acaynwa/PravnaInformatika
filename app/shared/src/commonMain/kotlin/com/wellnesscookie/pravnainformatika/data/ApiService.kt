package com.wellnesscookie.pravnainformatika.data

import com.wellnesscookie.pravnainformatika.model.CaseRecord
import com.wellnesscookie.pravnainformatika.model.CaseStatistics
import com.wellnesscookie.pravnainformatika.model.CaseTypeCount
import com.wellnesscookie.pravnainformatika.model.CbrRequest
import com.wellnesscookie.pravnainformatika.model.CbrResult
import com.wellnesscookie.pravnainformatika.model.GenerateDescriptionRequest
import com.wellnesscookie.pravnainformatika.model.GenerateDescriptionResponse
import com.wellnesscookie.pravnainformatika.model.Glava25Result
import com.wellnesscookie.pravnainformatika.model.JudgmentRequest
import com.wellnesscookie.pravnainformatika.model.JudgmentResult
import com.wellnesscookie.pravnainformatika.model.ReasoningRequest
import com.wellnesscookie.pravnainformatika.model.ReasoningResult
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class ApiService(private val baseUrl: String = defaultBaseUrl()) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val client = HttpClient {
        install(ContentNegotiation) { json(json) }
    }

    suspend fun cases(): List<CaseRecord> = client.get("$baseUrl/api/cases").body()

    suspend fun case(id: String): CaseRecord =
        client.get("$baseUrl/api/cases/${encodePath(id)}").body()

    suspend fun deleteCase(id: String): Map<String, String> =
        client.delete("$baseUrl/api/cases/${encodePath(id)}").body()

    suspend fun statistics(): CaseStatistics = client.get("$baseUrl/api/statistics").body()

    suspend fun caseTypes(): List<CaseTypeCount> = client.get("$baseUrl/api/case-types").body()

    suspend fun searchByType(type: String): List<CaseRecord> =
        client.get("$baseUrl/api/search/type/${encodePath(type)}").body()

    suspend fun glava25(): Glava25Result = client.get("$baseUrl/api/glava25").body()

    suspend fun akomantosoXml(id: String): String =
        client.get("$baseUrl/api/akomantoso/${encodePath(id)}").body()

    suspend fun reasoning(req: ReasoningRequest): ReasoningResult = client.post("$baseUrl/api/reasoning") {
        contentType(ContentType.Application.Json)
        setBody(req)
    }.body()

    suspend fun cbrReasoning(req: CbrRequest): CbrResult = client.post("$baseUrl/api/cbr-reasoning") {
        contentType(ContentType.Application.Json)
        setBody(req)
    }.body()

    suspend fun cbrReasoningInput(req: CbrRequest): CbrResult = client.post("$baseUrl/api/cbr-reasoning-input") {
        contentType(ContentType.Application.Json)
        setBody(req)
    }.body()

    suspend fun generateJudgment(req: JudgmentRequest): JudgmentResult =
        client.post("$baseUrl/api/generate-judgment") {
            contentType(ContentType.Application.Json)
            setBody(req)
        }.body()

    suspend fun generateDescription(req: GenerateDescriptionRequest): GenerateDescriptionResponse =
        client.post("$baseUrl/api/generate-description") {
            contentType(ContentType.Application.Json)
            setBody(req)
        }.body()
}

internal expect fun defaultBaseUrl(): String

private fun encodePath(s: String): String =
    s.replace("/", "%2F").replace(" ", "%20")
