package com.wellnesscookie.pravnainformatika.model

import kotlinx.serialization.Serializable

@Serializable
data class CaseStatistics(
    val totalCases: Int = 0,
    val guiltyCount: Int = 0,
    val conditionalCount: Int = 0,
    val acquittedCount: Int = 0,
    val krivCount: Int = 0,
    val fishingCount: Int = 0,
    val huntingCount: Int = 0,
    val courts: Int = 0,
)

@Serializable
data class CaseTypeCount(
    val type: String,
    val count: Int,
)
