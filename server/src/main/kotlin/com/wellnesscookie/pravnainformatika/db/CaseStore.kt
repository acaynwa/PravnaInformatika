package com.wellnesscookie.pravnainformatika.db

import com.wellnesscookie.pravnainformatika.model.CaseRecord
import com.wellnesscookie.pravnainformatika.model.CaseStatistics
import com.wellnesscookie.pravnainformatika.model.CaseTypeCount
import com.wellnesscookie.pravnainformatika.doc_parsing.AkomaNtosoParser
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

class CaseStore(private val resourcesRoot: File) {

    private val cases: MutableList<CaseRecord> = CopyOnWriteArrayList()

    fun load() {
        val candidates = listOf(
            File(resourcesRoot, "data/cases/akomantoso_new"),
            File(resourcesRoot, "data/cases/akomantoso"),
        )
        val dir = candidates.firstOrNull { it.exists() && it.isDirectory }
        if (dir == null) {
            println("[CaseStore] no cases directory found under ${resourcesRoot.absolutePath}")
            return
        }
        // Walk the tree — files now live under `lov/` and `ribolov/` subfolders,
        // but flat layouts (older drops) still work.
        val files = dir.walkTopDown()
            .filter { it.isFile && it.extension.equals("xml", ignoreCase = true) }
            .toList()
        println("[CaseStore] found ${files.size} XML files under ${dir.absolutePath}")
        val byFolder = files.groupBy { it.parentFile?.name ?: "(root)" }
        for ((sub, list) in byFolder) {
            println("[CaseStore]   $sub/ → ${list.size} files")
        }
        files.forEach { f ->
            AkomaNtosoParser.parse(f)?.let { cases.add(it) }
        }
        println("[CaseStore] loaded ${cases.size} cases")
    }

    fun all(): List<CaseRecord> = cases.toList()

    fun byId(id: String): CaseRecord? = cases.firstOrNull {
        it.id == id || it.caseId == id || it.caseNumber == id
    }

    fun searchByType(type: String): List<CaseRecord> {
        val q = type.lowercase()
        return cases.filter { it.type.lowercase().contains(q) }
    }

    fun caseTypes(): List<CaseTypeCount> {
        val grouped = cases.groupingBy { it.type }.eachCount()
        return grouped.entries.sortedBy { it.key }.map { CaseTypeCount(it.key, it.value) }
    }

    fun statistics(): CaseStatistics {
        val krivCount = cases.count { it.verdict == "KRIV" }
        val uslovnaCount = cases.count { it.verdict == "USLOVNA PRESUDA" || it.verdict == "USLOVNA OSUDA" }
        val oslobodjenCount = cases.count { it.verdict == "OSLOBAĐAJUĆA" || it.verdict == "OSLOBOĐEN" }
        val fishingCount = cases.count { it.type.lowercase().contains("ribolov") }
        val huntingCount = cases.count {
            val l = it.type.lowercase()
            l.contains("lov") && !l.contains("ribolov")
        }
        return CaseStatistics(
            totalCases = cases.size,
            guiltyCount = krivCount,
            conditionalCount = uslovnaCount,
            acquittedCount = oslobodjenCount,
            krivCount = krivCount,
            fishingCount = fishingCount,
            huntingCount = huntingCount,
            courts = cases.map { it.court }.toSet().size,
        )
    }

    fun replaceOrAdd(rec: CaseRecord) {
        val idx = cases.indexOfFirst { it.caseId == rec.caseId || it.id == rec.id }
        if (idx >= 0) cases[idx] = rec else cases += rec
    }

    fun delete(id: String): CaseRecord? {
        val idx = cases.indexOfFirst { it.id == id || it.caseId == id || it.caseNumber == id }
        if (idx < 0) return null
        val rec = cases[idx]
        cases.removeAt(idx)
        rec.xmlFile?.let { path -> runCatching { File(path).delete() } }
        return rec
    }

    fun akomantosoXml(id: String): String? {
        val rec = byId(id) ?: return null
        val xmlFile = rec.xmlFile ?: return null
        val file = File(xmlFile)
        return if (file.exists()) file.readText() else null
    }
}
