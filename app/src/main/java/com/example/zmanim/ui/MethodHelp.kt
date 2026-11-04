package com.example.zmanim.ui

import androidx.compose.runtime.Composable
import com.example.zmanim.domain.*

/** Reviewed factual definitions generated from the pinned source, never raw Java documentation. */
@Composable fun methodExplanation(id: String, profile: Profile?): String {
    profile?.custom?.find { it.id == id }?.let { return formulaText(it, profile.custom) }
    val method = MethodCatalog.byId[id] ?: return tr("This calculation is no longer available.", "חישוב זה אינו קיים עוד.")
    val definition = method.definition
    val policy = if (method.supportsHalfDays) tr(
        if (profile?.options?.astronomicalHalfDays == true) "Current profile: separate morning/afternoon temporal hours." else "Current profile: full-day temporal hours.",
        if (profile?.options?.astronomicalHalfDays == true) "בפרופיל זה: שעות זמניות נפרדות לבוקר ולאחר הצהריים." else "בפרופיל זה: שעות זמניות לפי יום שלם.") else ""
    return listOf(definition, policy, tr("Definition: KosherJava 3.0.0. Calculation information, not a religious ruling.", "הגדרה: KosherJava 3.0.0. מידע על החישוב, לא פסיקה.")).filter { it.isNotBlank() }.joinToString("\n\n")
}
