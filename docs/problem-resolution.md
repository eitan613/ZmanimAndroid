# November 2026 problem resolution

This checklist maps `problems.md` to implementation. Defaults apply to new data;
persisted choices and personal records are retained.

| Feedback | Implementation | Verification |
|---|---|---|
| Basic appearance; reference app layout | Compact native headings, restrained surfaces, bordered month grid, responsive row heights; independent visual identity | Appearance screenshots and manual visual review |
| Month/Gregorian/Hebrew/week/day/zmanim controls at bottom | Five persistent destinations; selected date retained | Navigation UI test |
| Settings and date actions in vertical three-dot menu | Shared overflow header; Settings, Today, Go to date, search; no visible previous/next/year controls | Menu/navigation and future-date timeline UI tests |
| Cannot return from zmanim | Calendar destinations remain available; standard back stack | Navigation UI test |
| Week requires horizontal scrolling | Seven weighted columns, shared hour gutter, sticky weekday heading | Seven-column bounds UI test |
| Create event by double-tapping an hour | Tapped date/time passed directly into an event draft | Double-tap, save, and stored-values UI test |
| Indistinguishable dates and broken text | Hebrew-number dates in either month mode; primary at top, secondary at bottom; one-line labels, full selected-day text | Calendar unit tests and visual review |
| Month names need separators | Secondary headers joined with ` / ` | Visual review |
| Unclear day boundaries | Shared month borders and explicit timeline day cards | Visual review |
| Hebrew zmanim aligned left | Hebrew labels explicitly right aligned independent of English controls; times isolated for bidi | Mixed-language appearance review |
| Profiles, selection, location, Back to now occupy screen | Moved into zmanim overflow menu; actionable setup states remain visible | Menu UI coverage |
| Load earlier/later buttons | Automatic bidirectional loading; stable row keys and bounded window; no timed-result prerequisite | Timeline model and scrolling checks |
| Seasonal “Unavailable” rows | `NotApplicable` differs from a failure; applies to chametz, lunar results, candle lighting, and custom dependencies | Seasonal and custom-dependency unit tests |
| Same rules everywhere | Shared calculation context includes Israel/diaspora; widgets, events, and reminders use it | Domain and existing integration tests |
| Candle lighting unclear | Factual help; explicit before-sunset offset and holiday nightfall selection; configuration errors remain actionable | Candle-transition and invalid-dependency unit tests |
| Selection requires Save | Immediate atomic field updates; pending state and retry; survives picker dismissal | Autosave/recreation UI test and concurrent repository update test |
| Custom picker has no creation UI | Create/edit path from Custom group and zmanim menu, with formula preview | Formula unit tests and UI review |
| Back to now misses next zman | Find the actual timeline row; measure viewport and center it once on request | Emulator positioning check |
| Raw booleans, timestamps, method jargon | Short labels plus factual explanations; formatted results; expandable relevant technical details | Method catalog checks and detail review |
| “Latest prayer” terminology | “Latest teffilah” in generator, generated catalog, and group labels | Catalog/source check |
| Confusing location screen | Current-location action, separate following switch, saved-selection indicator, search, progress/retry, advanced manual entry | Offline-place tests, location snapshot test, compact screenshots |
| Location permission requires pressing refresh again | Pending action resumes after permission result | Real Android permission-dialog continuation test with emulator GPS |
| Blocking Israel/diaspora onboarding | Calendar immediately accessible; provisional diaspora, one-time location initialization, manual override | Fresh-launch UI and initialization unit tests |
| Permission access and app toggles | Dedicated Permissions tab with access status and Android settings links | Permissions-tab UI test |
| Hebrew labels, Hebrew primary, Sunday, RTL defaults | Defaults changed independently of interface language | Fresh defaults and appearance checks |
| Sunday/Monday should be a choice | Explicit Week starts on dropdown | Settings review |
| PHP-like configurable colors | PHP category colors and precedence, dark-theme blending, per-category hex controls and reset | Category-color unit test and appearance review |
| Settings too long | Display, Calendar, Zmanim, Permissions, Data tabs | Tab navigation and large-text UI test |
| Preserve data | Additive JSON fields, existing Room schema, atomic latest-profile transforms; backup round trip | Existing migration tests and new backup/concurrency coverage |

## Defaults and calculation boundaries

- Interface defaults to English; labels default to Hebrew. Users can change each.
- Special-day precedence follows the PHP grid: Shabbat/Yom Tov, Chol Hamoed,
  fast, Chanukah/Purim, then the optional candle-lighting accent.
- New candle-lighting configuration does not supply a religiously recommended
  method. Users select their own offset and holiday nightfall method.
- Chametz methods follow the pinned library's Erev Pesach applicability. This
  update does not invent alternate practical rules for exceptional years.
- Existing calculations still report unavailable solar events and missing inputs;
  no fallback method or elevation value is silently substituted.
- Calendar directions are independent of UI direction. Left/right month-grid swipes change months; up/down changes years. The
  “Swap month/year swipe directions” setting exchanges the two axes.

## Validation commands

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:assembleRelease
```

Instrumentation tests use a dedicated development emulator. The appearance test
restores its original font scale. Screenshots are written to the app's `cache/qa`
directory and copied to `app/build/reports/ui-review`; Gradle HTML/XML results are in `app/build/reports` and test-result
folders. Build outputs and screenshots are not repository source artifacts.

## Verified results (2026-11-02)

- 37 JVM unit tests passed, including ten redesign regression cases.
- 24 Android instrumentation tests passed on the dedicated Pixel 7 / Android 16
  emulator. Calendar-provider and alarm-delivery tests ran with their documented
  permissions granted. Location setup was exercised through Android's permission
  dialog using an emulator GPS fix.
- Debug and optimized unsigned release APKs built successfully. Android lint
  completed with zero errors; dependency-update and platform warnings remain.
- Reviewed light/dark month screenshots, compact seven-column week and zmanim
  views, picker/location/settings screens at compact and standard sizes, and the
  Hebrew backup flow at 1.7× font scale. The test restores font scale and screen
  size after capturing screenshots.

## Widget, notification, and gesture follow-up

- Prior redesign committed as `d419a9b` before starting this follow-up.
- Hebrew-date notification icons now contain actual Hebrew letters for days
  1–30, including טו/טז. The expanded date is Hebrew regardless of label language.
- Month view accepts both swipe axes; the setting swaps month/year roles while
  respecting the chosen right-to-left or left-to-right day direction.
- Date badge defaults to 2 × 1 launcher cells; the combined daily overview defaults
  to 3 × 2. Event and zmanim lists remain independently available. Existing launcher
  allocations are preserved; resizing a placed widget applies the smaller bounds.
- All widget families have distinct colored backgrounds and content hierarchy,
  theme-aware cards, and typography that uses actual allocated dimensions.
- The overview displays both dates, labeled upcoming parsha, and Daf Yomi. A
  widget with no explicit place now follows the app location. Explicit saved or
  follow-device locations remain independent. Event widgets omit location metadata.

Follow-up validation: all 37 JVM tests and all 27 Android instrumentation tests
passed, including both swipe axes in both calendar directions, Hebrew notification
text/icons, and 20 widget snapshots across compact/wide/tall sizes in light/dark
themes. Debug and optimized release builds succeeded; lint completed without
errors. Widget previews and the 30-icon sheet are saved in
`app/build/reports/widget-review`. The emulator app data was backed up before the
UI tests, then restored and verified byte-for-byte afterward.
