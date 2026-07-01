package com.wellnesscookie.pravnainformatika

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform