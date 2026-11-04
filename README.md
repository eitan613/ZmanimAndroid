# Zmanim

Native Android calendar and zmanim app using Kotlin, Compose, Room, DataStore,
KosherJava, Glance, AlarmManager, WorkManager, and Android Calendar Provider.
Android 8.0 / API 26 or newer; no account, backend, API key, or runtime Python
installation is needed for calculations and personal events.

Open the project in Android Studio and use its compatible JDK/Gradle setup.
The committed wrapper/toolchain configuration is authoritative.

```sh
./gradlew :app:assembleDebug :app:lintDebug
./gradlew :app:assembleRelease
```

The debug APK is generated in `app/build/outputs/apk/debug/`. Release artifacts
are unsigned until a publication signing configuration is supplied. Development
application ID: `com.example.zmanim`.

## Using the app

The calendar opens immediately without a setup dialog. New installations use
English controls, Hebrew calendar/zman labels, a Hebrew month, Sunday first,
and Automatic calendar direction (Hebrew RTL, Gregorian LTR). General display
preferences are independent of profile selection.

The bottom bar opens Gregorian month, Hebrew month, week, day, and zmanim views.
The three-dot menu contains Today, Go to date, search, Settings, app location, and bundled offline Help. In month view, swipe left/right to change months and up/down to change years.
The swap setting exchanges these axes; accessible period actions are also available.
Double-tap an empty hour in week/day view to create an event at that date/time.
Adjacent-month cells mute event text and counts along with their dates and holidays.

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
action. Reviewed city metadata initializes Israel/diaspora separately from civil timezone
and display country. GPS timezones alone never establish a holiday schedule;
confirm an unknown schedule in Calendar settings. Ten reviewed locality
corrections are recorded by stable GeoNames ID against CBS locality records. The established setting stays fixed during travel and can
always be changed manually.

Settings has Display, Calendar, Zmanim, Permissions, and Data tabs. Calendar
colors start with the PHP calendar palette, support category overrides, and can
be reset. Permission status is separate from app feature switches; Android app
permissions can be managed from Settings. Selective JSON backups include the
new settings and profile options. Widgets remain independently configurable.
Every widget follows app location. Separate Hebrew and Gregorian date widgets
have separate 1 × 1 and 2 × 1 launcher entries. The 1 × 1 badges stack weekday,
day, month, and year and offer calendar/zmanim opening choices during setup.
The Hebrew 2 × 1 widget right-aligns full weekday/date labels and ends its
Gregorian date with the year; the Gregorian widget always uses English weekdays.
The daily overview
has selectable, ordered learning rows independent of zman profiles. Hebrew rows
are right-aligned; holiday, Rosh Chodesh, and Omer rows appear only when applicable.
Dates and learning rows scroll together, so a swipe can start on either.
Selecting the events widget's Event tap action opens the event editor inside
Zmanim. Connected calendar edits apply to the selected instance in its original
calendar, preserving the rest of a recurring series. Read-only calendars and
unsupported unsynced recurring events cannot be saved from this editor.
All zmanim
rows scroll together; each upcoming timed row can use a native countdown with
seconds. App countdowns also show seconds beyond an hour. Widget layouts and
colors follow actual allocated size and the selected theme.

Learning tables are generated offline from pinned Hebcal 6.13.1 and cover
2020–2040; Dirshu Halacha's finite third-cycle table ends 31 August 2027.
Separate Oz V’hadar tables reproduce the published cycle 11–12 references from
Kollel Iyun Hadaf, covering 14 November 2022 through 2 May 2034.
Oraysa includes the official Yevamot calendar and reviews, 23 November 2025
through 7 November 2026; later dates explicitly show Unavailable.
Only identified schedules are exposed, with explicit unavailable/no-learning
states. Five unidentified programs were excluded at the user's request. The
learning metadata records source coverage in `app/src/main/assets/learning-metadata.json`.

The Hebrew-date notification uses Hebrew letters in both its status icon and
expanded date, independently of the calendar-label preference.
Use row bells or Active profile reminders to configure reminders without events.
A reminder may deliver a notification or a genuine ringing alarm with selected
sound, vibration, snooze and dismiss. Hidden groups suppress both. Real alarms
require precise access and an enabled alarm channel; approximate notification
delivery requires explicit opt-in. Android full-screen access is optional.

Profiles support independent display-name overrides. Their duplication remaps
custom references and names. Backups use current format version 2 and preserve
the new models; imports never silently activate reminders. Obsolete test backup
formats are not supported.

Events use Gregorian/Hebrew grid pickers and a 12/24-hour time picker. Repeat has
its own calendar; non-repeating Hebrew dates retain their day semantics. Clock
changes resolve local start/end intervals automatically. Provider events open
in-app details at their exact instance; converting an editable provider event to
an app-managed rule adopts its provider record to avoid duplicate exports.

Optional Look up elevation uses cached Open Topo Data SRTM90m terrain height.
Manual entry and calculation remain offline. Explicit adjusted sunrise/sunset use
supported location height independently of the profile toggle. Source and library
notices, full license texts and dataset metadata live under Help → About.

## Project files

`app/src/main/` contains the app code, Android resources, bundled offline datasets,
and source/license notices. The calculation adapters, learning tables, place data,
and Hebrew day icons are required app inputs and are committed directly.
Keep `.gzip` suffixes: Android's asset merger automatically unpacks `.gz` files
and changes their asset names.

Room migrations preserve existing events and profiles. A settings journal
recovers writes interrupted between Room and DataStore commits.

Build configuration and the Gradle wrapper are retained at the project root and
under `gradle/`. Remaining planned work is tracked in [docs/todo.md](docs/todo.md).
