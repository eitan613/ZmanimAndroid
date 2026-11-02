package com.example.zmanim.domain

// Defaults and precedence match the PHP calendar. Overrides are stored by category.
val specialDayDefaults = linkedMapOf(
    "shabbat" to 0xFFF7E7CCL, "holiday" to 0xFFF7E7CCL,
    "chol" to 0xFFE4F1DBL, "fast" to 0xFFF6C7C7L, "festive" to 0xFFE1E7F8L,
    "candles" to 0xFFFAF2ECL,
)
fun AppSettings.specialDayColor(category: String): Long = specialDayColors[category] ?: when (category) {
    "shabbat" -> shabbatTint
    "holiday" -> holidayTint
    else -> specialDayDefaults.getValue(category)
}
