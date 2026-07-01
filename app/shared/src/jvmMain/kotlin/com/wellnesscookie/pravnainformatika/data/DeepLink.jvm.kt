package com.wellnesscookie.pravnainformatika.data

// Desktop app has no browser tabs to open a case reference into.
actual fun openUrlInNewTab(url: String) {}

internal actual fun currentLocationSearch(): String? = null
