package com.example.zmanim.ui

import androidx.compose.runtime.Composable
import com.example.zmanim.domain.*

/** Factual descriptions of the pinned KosherJava calculation, without practical rulings. */
@Composable fun methodExplanation(id: String, profile: Profile?): String {
    profile?.custom?.find { it.id == id }?.let { return formulaText(it, profile.custom) }
    val method = MethodCatalog.byId[id] ?: return tr("This calculation is no longer available.", "חישוב זה אינו קיים עוד.")
    return when (method.group) {
        "dawn" -> tr("The morning time when the sun reaches the selected angle below the horizon, or the selected interval before sunrise.", "זמן הבוקר שבו השמש מגיעה לזווית שנבחרה מתחת לאופק, או למרווח שנבחר לפני הזריחה.")
        "misheyakir" -> tr("The morning solar angle specified by this method. Different angles produce different times.", "זווית השמש בבוקר לפי השיטה שנבחרה. זוויות שונות נותנות זמנים שונים.")
        "sunrise" -> tr("Sunrise at this location. Sea-level methods ignore elevation; other methods follow the elevation setting.", "זריחת השמש במקום זה. שיטות פני הים אינן מתחשבות בגובה; שיטות אחרות משתמשות בהגדרת הגובה.")
        "shema" -> tr("Three temporal hours after the beginning of the day defined by this method.", "שלוש שעות זמניות מתחילת היום לפי גבולות השיטה.")
        "prayer" -> tr("Four temporal hours after the beginning of the day defined by this method.", "ארבע שעות זמניות מתחילת היום לפי גבולות השיטה.")
        "chatzos" -> when {
            id == "sun-transit" -> tr("Solar noon: when the sun crosses the local meridian. This can differ from the midpoint between sunrise and sunset.", "חצות שמש: מעבר השמש במרידיאן המקומי. זמן זה עשוי להיות שונה מאמצע הזמן שבין הזריחה לשקיעה.")
            id.contains("fixed-local") -> tr("A fixed local noon or midnight calculated from longitude and the timezone, independent of seasonal sunrise and sunset.", "חצות יום או לילה מקומי קבוע, המחושב לפי קו האורך ואזור הזמן, ללא תלות בזמני הזריחה והשקיעה העונתיים.")
            else -> if (profile?.options?.astronomicalChatzos != false) tr("Uses solar noon (the sun crossing the local meridian) for midday calculations.", "חישובי חצות משתמשים בחצות שמש, בעת מעבר השמש במרידיאן המקומי.")
                else tr("Uses the midpoint between sunrise and sunset for midday calculations.", "חישובי חצות משתמשים באמצע הזמן בין הזריחה לשקיעה.")
        }
        "mincha-gedolah" -> tr("The method’s early-afternoon boundary, based on its temporal-hour or fixed-minute definition. Check the calculation details for the selected variant.", "גבול תחילת אחר הצהריים לפי השעות הזמניות או הדקות הקבועות של השיטה. פרטי החישוב מציגים את הגרסה שנבחרה.")
        "mincha-ketanah" -> tr("The method’s later-afternoon boundary, calculated from its specified day boundaries.", "גבול אחר הצהריים המאוחר לפי גבולות היום של השיטה.")
        "plag" -> tr("One and a quarter temporal hours before the end of the day defined by this method.", "שעה ורבע זמנית לפני סוף היום לפי גבולות השיטה.")
        "sunset" -> tr("Sunset at this location. Sea-level methods ignore elevation; other methods follow the elevation setting.", "שקיעת השמש במקום זה. שיטות פני הים אינן מתחשבות בגובה; שיטות אחרות משתמשות בהגדרת הגובה.")
        "nightfall", "twilight" -> tr("An evening boundary using the named solar angle or interval relative to sunset. Fixed minutes and temporal minutes are different calculations.", "גבול ערב לפי זווית השמש או המרווח מהשקיעה שבשם השיטה. דקות קבועות ודקות זמניות הן חישובים שונים.")
        "duration" -> tr("The length of one temporal hour: the interval between this method’s day boundaries divided by twelve. This is a duration, not a clock time.", "משך שעה זמנית אחת: הזמן שבין גבולות היום של השיטה חלקי שנים עשר. זהו משך ולא שעה בשעון.")
        "candles" -> tr("On before-sunset evenings, subtracts your chosen minutes from sea-level sunset. Applicable holiday transitions use your explicitly chosen nightfall method instead. No offset or nightfall method is assumed.", "בערבים שלפני השקיעה, מחסיר את מספר הדקות שבחרתם משקיעה בגובה פני הים. במעברי חג המתאימים משתמש בשיטת צאת הכוכבים שבחרתם במפורש. לא נבחרים מרווח או שיטת צאת הכוכבים מראש.")
        "chametz" -> tr("Shown only on Erev Pesach. The eating deadline is four temporal hours and the disposal deadline is five temporal hours after the start of the day defined by this method.", "מוצג רק בערב פסח. סוף זמן אכילה הוא ארבע שעות זמניות, וסוף זמן ביעור הוא חמש שעות זמניות מתחילת היום לפי השיטה.")
        "lunar" -> tr("A molad-based moment or Kiddush Levanah boundary, shown only on the local date on which this calculation occurs. The named variant specifies its interval from the molad.", "רגע המבוסס על המולד או גבול זמן קידוש לבנה, המוצג רק בתאריך המקומי שבו חל החישוב. הגרסה שבשם השיטה קובעת את המרווח מהמולד.")
        else -> method.label
    }
}
