package com.wellnesscookie.pravnainformatika.data

// No browser-tab concept on Android; opening a case reference here is a no-op.
actual fun openUrlInNewTab(url: String) {}

internal actual fun currentLocationSearch(): String? = null
