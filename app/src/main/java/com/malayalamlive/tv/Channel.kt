package com.malayalamlive.tv

data class Channel(
    val name: String,
    val url: String,
    val logo: String? = null,
    val groups: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
    val userAgent: String? = null,
    val referrer: String? = null,
) {
    val isMalayalam: Boolean
        get() = languages.any { it.equals("Malayalam", ignoreCase = true) } ||
            groups.any { it.contains("Malayalam", ignoreCase = true) } ||
            name.contains("Malayalam", ignoreCase = true)
}
