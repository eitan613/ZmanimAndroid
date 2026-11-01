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

First launch asks explicitly for Israel/diaspora. Choose zmanim individually or
open the calendar without selecting any methods. Save a location or enable
follow-location access to calculate zmanim. Method choices, custom formulas,
elevation settings and reminder preferences belong to named profiles; locations
and display preferences stay separate.

Settings contains Hebrew/Gregorian month preferences, day/week views, global
Hebrew all-day boundaries, formatting, optional notifications, calendar selection,
background-location access, and selective JSON export/import/sharing. Widgets are
configured independently from the launcher. Precise reminders require Android
access; an approximate fallback requires explicit acceptance.

## Development helpers and data

The files under `tools/` are optional development-time generators. Generated
Kotlin and offline data are committed, so ordinary Gradle builds do not run them.

- `generate_catalog.py` reads the pinned KosherJava source JAR and generates
  typed Kotlin calculation adapters plus `docs/method-catalog.csv`. Pass
  `--source /path/to/zmanim-3.0.0-sources.jar` if it is not in the Gradle cache.
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
