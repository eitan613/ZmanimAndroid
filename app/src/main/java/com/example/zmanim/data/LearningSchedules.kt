package com.example.zmanim.data

import android.content.Context
import android.util.LruCache
import com.example.zmanim.domain.*
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.util.zip.GZIPInputStream

@Serializable data class LearningProvider(val id: String, val en: String, val he: String,
    val cycle: String, val schedule: String = "", val validFrom: String? = null, val validThrough: String? = null,
    val source: String = "", val version: String = "")
@Serializable private data class LearningMetadata(val version: String, val source: String, val providers: List<LearningProvider>)
data class LearningRow(val label: String, val value: String)

/** Versioned references only. No internet, book text downloads or per-second work. */
class LearningSchedules(private val context: Context) {
    private val metadata by lazy { appJson.decodeFromString<LearningMetadata>(context.assets.open("learning-metadata.json").bufferedReader().use { it.readText() }) }
    val version get() = metadata.version
    val providers get() = listOf(
        LearningProvider("parsha", "Upcoming parsha", "הפרשה הקרובה", "KosherJava 3.0.0; Israel/Diaspora preference"),
        LearningProvider("holiday", "Holiday", "חג", "KosherJava 3.0.0; local civil day"),
        LearningProvider("rosh-chodesh", "Rosh Chodesh", "ראש חודש", "KosherJava 3.0.0; local civil day"),
        LearningProvider("omer", "Omer", "עומר", "KosherJava 3.0.0; local civil day")
    ) + metadata.providers
    private val cache = LruCache<String, Map<String, Pair<String, String>>>(12)
    @Synchronized private fun references(date: LocalDate): Map<String, Pair<String, String>> {
        val key = date.toString()
        cache.get(key)?.let { return it }
        val result = GZIPInputStream(context.assets.open("learning.tsv.gzip")).bufferedReader().useLines { lines ->
            lines.dropWhile { it.substringBefore('\t') < key }.takeWhile { it.substringBefore('\t') == key }
                .map { it.split('\t') }.filter { it.size == 4 }.associate { it[1] to (it[2] to it[3]) }
        }
        cache.put(key, result)
        return result
    }
    fun rows(date: LocalDate, ids: List<String>, settings: AppSettings): List<LearningRow> {
        val he = settings.labels == LabelLanguage.HEBREW
        val formatter = CalendarEngine().formatter(he)
        val jc = JewishCalendar(date).apply { inIsrael = settings.inIsrael ?: false; isUseModernHolidays = settings.modernHolidays }
        val refs by lazy { references(date) }
        return ids.distinct().mapNotNull { id ->
            val provider = providers.find { it.id == id } ?: return@mapNotNull null
            val value = when (id) {
                "parsha" -> CalendarEngine().upcomingParsha(date, settings).ifBlank { if (he) "אין פרשה" else "No parsha" }
                "holiday" -> formatter.formatYomTov(jc).ifBlank { if (he) "אין חג היום" else "No holiday today" }
                "rosh-chodesh" -> formatter.formatRoshChodesh(jc).ifBlank { if (he) "לא ראש חודש" else "Not Rosh Chodesh" }
                "omer" -> if (jc.dayOfOmer > 0) formatter.formatOmer(jc) else if (he) "מחוץ לספירת העומר" else "Outside Omer"
                else -> refs[id]?.let { if (he) it.second.ifBlank { it.first } else it.first } ?: when {
                    id == "yerushalmi-vilna" && date.toString() >= (provider.validFrom ?: "") && date.toString() <= (provider.validThrough ?: "") -> if (he) "אין לימוד היום" else "No learning today"
                    else -> if (he) "לא זמין לתאריך זה · ${provider.validFrom}–${provider.validThrough}" else "Unavailable for this date · ${provider.validFrom}–${provider.validThrough}"
                }
            }
            LearningRow(if (he) provider.he else provider.en, value)
        }
    }
}
