# Zmanim

Native Android calendar and zmanim app using Kotlin, Compose, Room, DataStore,
KosherJava, Glance, AlarmManager, WorkManager, and Android Calendar Provider.
Android 8.0 / API 26 or newer; no account, backend, API key, or runtime Python
installation is needed for calculations and personal events.

Open the project in Android Studio and use its compatible JDK/Gradle setup.
The committed wrapper/toolchain configuration is authoritative.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:assembleRelease
```

Calendar Provider integration tests require calendar read/write permission on
the test device. They create and remove a dedicated **local** test calendar;
without access, those tests skip. The real alarm-delivery test additionally needs
notification permission and precise-alarm access. On a dedicated emulator:

```sh
adb shell pm grant com.example.zmanim android.permission.READ_CALENDAR
adb shell pm grant com.example.zmanim android.permission.WRITE_CALENDAR
adb shell pm grant com.example.zmanim android.permission.POST_NOTIFICATIONS
adb shell appops set com.example.zmanim SCHEDULE_EXACT_ALARM allow
```

`POST_NOTIFICATIONS` applies on API 33+, and precise-alarm special access on API
31+. UI tests reset app data and change the test
emulator's font scale temporarily, so use a dedicated emulator/test device.

The debug APK is generated in `app/build/outputs/apk/debug/`. Release artifacts
are unsigned until a publication signing configuration is supplied. Development
application ID: `com.example.zmanim`.

## Using the app

The calendar opens immediately without a setup dialog. New installations use
English controls, Hebrew calendar/zman labels, a Hebrew month, Sunday first,
and right-to-left calendar days. Existing saved preferences are preserved.

The bottom bar opens Gregorian month, Hebrew month, week, day, and zmanim views.
The three-dot menu contains Today, Go to date, search, and Settings. In month view, swipe left/right to change months and up/down to change years.
The swap setting exchanges these axes; accessible period actions are also available.
Double-tap an empty hour in week/day view to create an event at that date/time.

Choose zmanim from the zmanim screen's menu; each selection saves immediately.
Custom zmanim can be created directly from the Custom group or menu using minute
offsets, solar angles, or temporal hours. Formulas remain drafts until saved.
Profiles store calculation options and reminder choices; locations and display
preferences stay separate. Method explanations describe calculations rather than
religious rulings.

The timeline loads days automatically in both directions. Back to now centers
the next loaded zman. Seasonal methods are hidden outside applicable dates;
missing calculation inputs or astronomical events still show an explanation.
Candle lighting requires an explicit before-sunset offset and, for applicable
holiday transitions, an explicitly selected nightfall method.

Location offers a current-position snapshot, continuous following, offline city
search, and saved places. Granting location permission continues the requested
action. The first reliable active location initializes Israel/diaspora using its
offline timezone (Asia/Jerusalem implies Israel); unknown location uses diaspora
until initialization. The established setting stays fixed during travel and can
always be changed manually.

Settings has Display, Calendar, Zmanim, Permissions, and Data tabs. Calendar
colors start with the PHP calendar palette, support category overrides, and can
be reset. Permission status is separate from app feature switches; Android app
permissions can be managed from Settings. Selective JSON backups include the
new settings and profile options. Widgets remain independently configurable.
An unselected widget location follows the active app location; an explicitly selected place stays independent. The Date
widget starts at 2 × 1 launcher cells and shows a prominent date badge. The Daily
overview starts at 3 × 2 cells and shows both dates, parsha, and Daf Yomi. Events
and Zmanim widgets show their own scrolling lists; the next zman/countdown stays
visible. Widget layouts and typography adapt to actual allocated space, with
separate light/dark color palettes. Already placed widgets retain the launcher's
allocated size and can be resized to use the smaller minimums.

The Hebrew-date notification uses Hebrew letters in both its status icon and
expanded date, independently of the calendar-label preference.
Precise reminders require Android access; approximate delivery requires opt-in.

See [the problem resolution checklist](docs/problem-resolution.md) for the
mapping from the original feedback to implementation and validation.

## Development helpers and data

The files under `tools/` are optional development-time generators. Generated
Kotlin and offline data are committed, so ordinary Gradle builds do not run them.

- `generate_catalog.py` reads the pinned KosherJava source JAR and generates
  typed Kotlin calculation adapters plus `docs/method-catalog.csv`. Pass
  `--source /path/to/zmanim-3.0.0-sources.jar` if it is not in the Gradle cache.
- `GenerateHebrewDayIcons.java` regenerates the 30 Hebrew-letter status icons
  from DejaVu Sans Bold outlines; its font notice is bundled in the app assets.
- `prepare_places.py` downloads public city/timezone datasets and builds offline
  assets. Dataset provenance and licenses are recorded in
  `app/src/main/assets/place-data.json` and `NOTICE.txt`. Timezone points are
  memory-mapped from a generated cache instead of loaded into the Java heap.

Keep `.gzip` suffixes: Android's asset merger automatically unpacks `.gz` files
and changes their asset names. Room schemas are committed under `app/schemas`;
record migrations must preserve existing events and profiles. A settings journal
recovers writes interrupted between Room and DataStore commits.

## Release review

`docs/app.md` is the product specification. The factual labels in
`docs/method-catalog.csv` require owner terminology review. Religious guidance is
not generated or supplied. Before publishing, validate independently reviewed
calculation fixtures and reminders/widgets on physical devices, including Doze,
reboot, denied access and travel. No store publication or cloud integration is
performed by this project.
