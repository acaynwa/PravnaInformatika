package com.wellnesscookie.pravnainformatika.data

import kotlinx.browser.window

actual fun openUrlInNewTab(url: String) {
    window.open(url, "_blank")
}

internal actual fun currentLocationSearch(): String? = window.location.search
