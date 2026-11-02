package com.example.zmanim.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable fun ColorSetting(category: String, selected: Long, onChange: (Long) -> Unit) {
    var text by rememberSaveable(selected) { mutableStateOf("%06X".format(selected and 0xFFFFFF)) }
    val name = when(category) {
        "shabbat" -> tr("Shabbat", "שבת"); "holiday" -> tr("Yom Tov", "יום טוב"); "chol" -> tr("Chol Hamoed", "חול המועד")
        "fast" -> tr("Fast days", "תעניות"); "festive" -> tr("Chanukah / Purim", "חנוכה / פורים"); else -> tr("Candle-lighting accent", "הדגשת הדלקת נרות")
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).background(Color(selected.toInt()), MaterialTheme.shapes.small))
        Text(name, style = MaterialTheme.typography.titleSmall)
    }
    Field(tr("Color (#RRGGBB)", "צבע (#RRGGBB)"), text, { text = it }, hint = tr("Six hexadecimal digits", "שש ספרות הקסדצימליות"))
    val parsed = text.removePrefix("#").takeIf { it.length == 6 }?.toLongOrNull(16)
    TextButton(enabled = parsed != null && (parsed or 0xFF000000L) != selected, onClick = { parsed?.let { onChange(it or 0xFF000000L) } }) { Text(tr("Apply color", "החלת צבע")) }
}
