package com.wellnesscookie.pravnainformatika.data

// Empty base URL → fetches are relative to the wasm dev server origin (and to the
// production origin too, once Ktor serves the wasm bundle as static content). The dev
// server proxies /api/* to the Ktor backend; see app/webApp/build.gradle.kts.
internal actual fun defaultBaseUrl(): String = ""
