package com.wellnesscookie.pravnainformatika.data

/** Opens [url] in a new browser tab. No-op on platforms without a browser (desktop/Android). */
expect fun openUrlInNewTab(url: String)

/** Current page `?search` string (e.g. "?case=K%20381%2F12"), or null off-web. */
internal expect fun currentLocationSearch(): String?

/** Same-page query string that, when opened, re-selects [caseId] in "Lista presuda". */
fun buildCaseDeepLinkQuery(caseId: String): String = "?case=" + percentEncode(caseId)

/** Reads the `case` deep-link param from the current URL, if opened via [buildCaseDeepLinkQuery]. */
fun readDeepLinkCaseId(): String? =
    currentLocationSearch()?.let { extractQueryParam(it, "case") }?.let { percentDecode(it) }

private fun extractQueryParam(search: String, key: String): String? =
    search.removePrefix("?")
        .split("&")
        .map { it.split("=", limit = 2) }
        .firstOrNull { it.getOrNull(0) == key }
        ?.getOrNull(1)

private fun percentEncode(value: String): String = buildString {
    for (byte in value.encodeToByteArray()) {
        val code = byte.toInt() and 0xFF
        val ch = code.toChar()
        if ((code < 128 && ch.isLetterOrDigit()) || ch in "-_.~") {
            append(ch)
        } else {
            append('%')
            append(code.toString(16).uppercase().padStart(2, '0'))
        }
    }
}

private fun percentDecode(value: String): String {
    val bytes = mutableListOf<Byte>()
    var i = 0
    while (i < value.length) {
        val c = value[i]
        if (c == '%' && i + 2 < value.length) {
            val hex = value.substring(i + 1, i + 3).toIntOrNull(16)
            if (hex != null) {
                bytes.add(hex.toByte())
                i += 3
                continue
            }
        }
        bytes.addAll(c.toString().encodeToByteArray().toList())
        i++
    }
    return bytes.toByteArray().decodeToString()
}
