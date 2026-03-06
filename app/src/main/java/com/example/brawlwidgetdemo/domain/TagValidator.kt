package com.example.brawlwidgetdemo.domain

private val TAG_REGEX = Regex("^[0289PYLQGRJCUV]{3,}$")

fun normalizeTag(raw: String): String {
    return raw.trim().uppercase().removePrefix("#")
}

fun isTagValid(normalizedTag: String): Boolean = TAG_REGEX.matches(normalizedTag)
