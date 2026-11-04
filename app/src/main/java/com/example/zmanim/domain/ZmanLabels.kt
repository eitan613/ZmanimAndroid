package com.example.zmanim.domain

/** One effective label for app, widget, event, notification and alarm presentation. */
fun methodDisplayName(id: String, profile: Profile?, hebrew: Boolean): String {
    profile?.displayNames?.get(id)?.let { return it }
    profile?.custom?.find { it.id == id }?.let { return if (hebrew && it.hebrewName.isNotBlank()) it.hebrewName else it.name }
    val method = MethodCatalog.byId[id] ?: return if (hebrew) "חישוב חסר" else "Missing calculation"
    return if (hebrew) method.hebrewLabel else method.label.substringBefore(" · Day:").replace(" (profile elevation policy)", "")
}

/** Unicode isolates keep Latin qualifiers, degree values and signed offsets intact in RTL labels. */
fun bidiZmanLabel(text: String): String {
    if (text.none { it in '\u0590'..'\u05ff' }) return text
    return Regex("[A-Za-z0-9+−-][A-Za-z0-9 .°()+−/-]*").replace(text) { "\u2066${it.value.trimEnd()}\u2069" + if (it.value.endsWith(' ')) " " else "" }
}
