# How this Android project works

This guide describes the project as it exists on 4 November 2026. It assumes you can read Java and are comfortable with PHP, but have not built Android apps before. Start with sections 1–6 for the overall picture; section 7 is the file-by-file reference. Section 10 explains sharing the app without Google Play.

The inventory covers every version-controlled file, including binary assets and build scripts, plus the local configuration files currently present. Generated build products and caches are explained by purpose rather than listing thousands of replaceable internal files. The actual code and committed build configuration take precedence if this guide becomes outdated.

## Contents

1. [What is actually installed on the phone?](#1-what-is-actually-installed-on-the-phone)
2. [The Android concepts behind the folders](#2-the-android-concepts-behind-the-folders)
3. [Gradle, Composer, and the build toolchain](#3-gradle-composer-and-the-build-toolchain)
4. [How the code fits together](#4-how-the-code-fits-together)
5. [Storage, calculations, and background work](#5-storage-calculations-and-background-work)
6. [Resources and bundled data](#6-resources-and-bundled-data)
7. [Every project file](#7-every-project-file)
8. [Local files and generated files](#8-local-files-and-generated-files)
9. [Where to make common changes](#9-where-to-make-common-changes)
10. [Sharing the app without Google Play](#10-sharing-the-app-without-google-play)

## 1. What is actually installed on the phone?

This is a native Android application, mostly written in **Kotlin**, with a small Java database layer. `.kt` means Kotlin source code; `.java` means Java source code. Kotlin and Java can call each other, and both ultimately become code Android can execute. The directory named `java/` contains both languages by Android convention.

There is no PHP server, hosted database, login service, or application backend. The phone stores the user's information and calculates zmanim itself. The city search, timezone boundaries, learning references, help text, and licenses are bundled with the application. Optional elevation lookup makes an HTTPS request, and device calendar/location features communicate with Android services.

You develop using the source project, but a friend installs an **APK**: an Android application package. An APK contains the compiled program, resources, bundled assets, manifest, dependency code needed by the app, and a digital signature. It does not contain your phone's personal events, preferences, location grants, or database. Each installation begins with its own data.

Think of an APK as the built, installable product of this repository. The recipient needs an Android phone, not Android Studio, Java, Gradle, or the source code. This app supports Android 8.0/API 26 and newer. An APK cannot be installed as an iPhone app.

## 2. The Android concepts behind the folders

### Project, module, package, and source set

These are different levels of organization:

| Term | Meaning here | Rough PHP comparison |
| --- | --- | --- |
| Project | The entire `Zmanim/` directory opened in Android Studio. | A whole application repository. |
| Module | A separately configured build unit. This project has one: `app`. | A package/subproject with its own build configuration. |
| Package | A namespace such as `com.example.zmanim.domain`. | A PHP namespace. |
| Source set | Files used for a particular build purpose. `app/src/main/` is shared production input. | Shared source/configuration used by all environments. |
| Build variant | A combination of build settings. Here the main choices are `debug` and `release`. | A development or production build, although these produce different binaries. |

The physical structure starts like this:

```text
Zmanim/
├── build/configuration files       How to build the project
├── gradle/                         Tool versions and Gradle bootstrap files
├── docs/                           Developer documentation
└── app/                            The Android application module
    ├── build.gradle.kts            App build settings and dependencies
    └── src/main/
        ├── AndroidManifest.xml     What components and permissions exist
        ├── java/com/example/zmanim/ Kotlin and Java program code
        ├── res/                    Android-managed resources
        └── assets/                 Raw data and notices bundled into the APK
```

`main` is not a function or the app's entry point. It means “include these files in the normal app builds.” Other projects often have `src/debug/`, `src/release/`, `src/test/`, and `src/androidTest/`. Those would add variant-specific files, JVM unit tests, or tests run on Android. This checkout currently has no committed test source sets.

Android Studio's **Android** project view groups and simplifies files. Its **Project** view shows their physical locations, which is more useful when following this guide.

### Android starts components, rather than a main method

There is no `public static void main()` that runs a permanent loop. Android creates registered components when needed:

| Component | Role in this app |
| --- | --- |
| `Application` | `ZmanimApplication` creates shared services when the app process starts. |
| `Activity` | A window Android can open: the main app, widget configuration, or ringing-alarm screen. |
| `BroadcastReceiver` | A short-lived entry point for an alarm, widget update, device restart, or time change. |
| `Service` | `ZmanAlarmService` keeps a ringing alarm active with a foreground notification. |
| `Worker` | `MaintenanceWorker` performs persistent, deferrable maintenance through WorkManager. |
| Content provider | An Android interface to shared data. This app uses the device's Calendar Provider and declares a `FileProvider` for sharing backup files. |

The **manifest** registers the components Android needs to know about. Ordinary domain models, helpers, screen functions, and repositories do not need manifest entries. A file can contain several classes; the manifest names classes, not filenames. For example, several receivers are declared inside `ReminderScheduler.kt`.

An **Intent** is a message requesting an action, often with extra values. Tapping a widget can send `destination`, `date`, or event identifiers to `MainActivity`. A **PendingIntent** lets Android perform a previously specified action later, such as delivering an alarm or opening an event when a notification is tapped.

### The app has a lifecycle

Android can pause or destroy an Activity, recreate it on rotation, and kill a background process to reclaim memory. Being installed does not mean the app is always running. `Application` exists only while its process exists.

This explains several design choices: durable information goes into storage; an Activity's temporary state is saved separately; reminders are registered with Android; WorkManager can run maintenance later. WorkManager is roughly analogous to a persistent job queue, but Android decides when deferred work gets CPU time. It is not a precise cron service.

### Compose supplies the interface

The app uses **Jetpack Compose**, Android's declarative UI toolkit. Functions annotated `@Composable` describe the screen for the current state:

```kotlin
@Composable
fun Example(title: String) {
    Text(title)
}
```

There is no HTML, DOM, browser, or XML layout for the main calendar screens. Components such as `Column`, `Row`, `Text`, and `Button` create native UI. A `Modifier` configures things like padding, size, and click behavior. Material 3 supplies common controls and design conventions.

When observed state changes, Compose calls the relevant UI functions again; this is **recomposition**. The useful comparison is rendering a PHP template from a model, except Compose can update parts of an already visible interactive screen. Recomposition is why a drawing function should not unconditionally save data or launch a network request just because it was called again.

`remember` keeps a value across recompositions of that UI; `rememberSaveable` can also preserve supported values through Activity recreation. `SavedStateHandle` serves a similar restoration role for ViewModel state. None replaces saving durable user records to the database.

## 3. Gradle, Composer, and the build toolchain

### Your Composer comparison is partly right

Gradle downloads dependencies, but it also orchestrates compiling, generating code, processing resources, packaging, signing, linting, and installing. It is closer to **Composer plus a build system and task runner**.

| Familiar concept | Android/Gradle counterpart |
| --- | --- |
| `composer.json` dependency declarations | `dependencies { ... }` in `app/build.gradle.kts`, using names from `gradle/libs.versions.toml`. |
| Packagist or another package repository | Google's Maven repository and Maven Central. |
| Downloaded `vendor/` packages | Gradle's dependency cache, usually under `~/.gradle/`; libraries are incorporated into build outputs as needed. |
| Composer scripts or build commands | Gradle tasks such as `:app:assembleDebug`. |
| PHP runtime/tool version | The JDK running Gradle, Gradle itself, and Android build tools. These are separate versions. |
| A lockfile | There is no committed Composer-style dependency lockfile here. Direct versions are centralized, but a version catalog is not a full resolved dependency lock. |

Dependencies can have **transitive dependencies**: Room or Compose may require additional libraries. You do not normally download and commit their JAR files yourself. Gradle resolves that graph.

### The wrapper chooses Gradle

Run `./gradlew` on Linux/macOS or `gradlew.bat` on Windows. These are the **Gradle Wrapper** launch scripts. They invoke `gradle/wrapper/gradle-wrapper.jar`, which reads `gradle-wrapper.properties` and obtains the specified Gradle distribution if necessary.

This project pins **Gradle 9.6.0**, including its download checksum. The wrapper files belong in Git, including the small wrapper JAR. Downloaded Gradle distributions and caches do not.

### The configuration files have separate jobs

Gradle first reads `settings.gradle.kts` to discover modules and dependency repositories, then configures the root and module build scripts. `.gradle.kts` means a Gradle script written using Kotlin DSL. A `.kts` build script configures the build; it does not become application code installed on the phone.

The root `build.gradle.kts` declares shared plugin aliases with `apply false`: make those plugins available without turning the root directory into an Android application. `app/build.gradle.kts` actually applies the application, Compose, and serialization plugins to `app`.

A **plugin** teaches Gradle new tasks and configuration options. The **Android Gradle Plugin (AGP)** knows how to build Android apps; it is distinct from Gradle. This project declares AGP **9.5.0**. AGP 9 supplies Kotlin support directly, so the absence of a separate `org.jetbrains.kotlin.android` plugin is intentional. See Android's [built-in Kotlin documentation](https://developer.android.com/build/migrate-to-built-in-kotlin).

The version catalog, `gradle/libs.versions.toml`, has three sections: `[versions]`, `[libraries]`, and `[plugins]`. For example, `libs.kosherjava.zmanim` expands to the catalog's Maven coordinates for KosherJava. An alias such as `androidx-room-runtime` becomes the dotted accessor `libs.androidx.room.runtime` in the build script.

`implementation(...)` adds a library used by the app. `annotationProcessor(...)` runs a Java code generator while building; here Room generates database implementations from the Java entities and DAO. `platform(libs.androidx.compose.bom)` imports a **bill of materials**, which coordinates versions of the Compose libraries it manages. It does not add all of Compose automatically.

The dependency names are less mysterious once you separate their jobs:

| Library/family | Why the app uses it |
| --- | --- |
| AndroidX Core / Core KTX | Android compatibility/convenience helpers; KTX means Kotlin extensions. |
| AndroidX annotations | Annotations used by Android library APIs and checks. |
| Activity Compose | Connects an Android Activity to Compose content and activity-result handling. |
| Compose UI / Graphics / Material 3 / Icons | Drawing/layout, graphics, common controls, and icon definitions. |
| Navigation Compose | Screen routes and navigation history inside the main Activity. |
| Lifecycle / ViewModel / runtime Compose | State ownership and observation tied to Android lifecycle. |
| Room runtime / compiler | SQLite data access plus its build-time Java code generator. |
| DataStore Preferences | Asynchronous storage for application preferences. |
| WorkManager | Persistent, deferred background maintenance. |
| Glance AppWidget | Home-screen widget implementation. |
| kotlinx.serialization JSON | Kotlin model serialization and JSON parsing/output. |
| KosherJava Zmanim | Astronomical/zmanim and Jewish calendar calculations. |

These are libraries incorporated into the app, not services your friend must install individually. The Android SDK supplies the platform API/build tools; the JDK supplies the Java tools that run on your development computer. Neither is the same thing as an ordinary app dependency.

### The Java versions are not contradictory

`gradle/gradle-daemon-jvm.properties` requests a **JDK 25** Gradle daemon. A daemon is a reusable background JVM that avoids starting the whole build system from scratch for every command. The Foojay resolver in settings supports Java toolchain provisioning; the file records platform-specific download information.

Meanwhile, `compileOptions` in the app requests **Java 11 source/target compatibility**. That concerns Java source and intermediate compiled output, not the JVM needed to run Gradle. Android eventually runs converted Android bytecode on ART, the Android Runtime. Your friend's phone does not need desktop Java 25 installed.

Use the project's configured toolchain and Android Studio's compatible setup; choosing Java 11 to run Gradle just because you see `VERSION_11` in the app build file would mix up these separate concerns.

### The app's Android settings

These values come directly from `app/build.gradle.kts`:

| Setting | Current value | Meaning |
| --- | --- | --- |
| `namespace` | `com.example.zmanim` | Namespace for generated code such as `R`. |
| `applicationId` | `com.example.zmanim` | Installed app identity on the device. Changing it creates a separate app. |
| `compileSdk` | 37 | Android API definitions the code is compiled against. |
| `minSdk` | 26 | Oldest supported Android version: Android 8.0. |
| `targetSdk` | 37 | Android compatibility/security behavior level the app targets. It does not require every phone to run API 37. |
| `versionCode` | 1 | Integer used to order application versions. Increase it for distributed updates. |
| `versionName` | `1.0` | Human-readable version label. |
| Compose | Enabled | Compiles the Compose UI. |

The build configuration also enables release optimization scoped to `androidx.**`, `kotlin.**`, and `kotlinx.**`. There is no project-specific ProGuard rules file in this checkout and no configured release signing key.

The installed app's **visible name** comes from `res/values/strings.xml`, not `applicationId` or `rootProject.name`. You can keep the existing name/icon for private sharing. Changing the visible label or icon later does not itself create a new app; changing the application ID does.

### From files to APK

Conceptually, the build does this, with many steps cached or run concurrently:

```text
Build scripts + version catalog
    → resolve tools, plugins, and dependency libraries
Kotlin + Java + Room/serialization code generation
    → compiled code → Android DEX bytecode
Manifest + res/ + dependency resources
    → merged manifest and compiled resources
DEX + resources + assets + required library code
    → packaged APK → digital signature → installable file
```

**Gradle Sync** lets Android Studio load this configuration, dependencies, source roots, and task model. It is not the same as producing a shareable APK. **Run** builds and deploys a development installation to your chosen device. **Assemble** produces an artifact without requiring a connected phone.

Common commands, run from the project root:

```sh
./gradlew :app:assembleDebug
./gradlew :app:lintDebug
./gradlew :app:assembleRelease
./gradlew :app:tasks
./gradlew clean
```

`:app:` names the module; `assembleDebug` names the task. Lint performs Android static checks; it does not prove the UI or alarm behavior works on a real device. `clean` deletes generated build outputs, not app source or the data already stored on a phone. The release assembly is currently unsigned until you supply signing details.

## 4. How the code fits together

The main dependency paths are:

```text
AndroidManifest.xml
    ├── ZmanimApplication: constructs shared services
    └── MainActivity: opens the app window and navigation
            ↓
      Compose screens and dialogs (ui/)
            ↕
      AppViewModel: prepares screen state and handles actions
            ├── AppRepository → Room database + DataStore
            ├── domain/ engines → KosherJava
            └── platform/ → Android calendars, location, reminders

Repository change → maintenance request → MaintenanceWorker
    → calendar exports, reminders, date notification, location setup, widgets

Launcher → widget receiver → Glance widget → same repository and engines
```

The UI sometimes calls repository operations through the ViewModel's `act` wrapper, so this is a description of responsibilities, not a claim that every method passes through a rigid sequence of layers.

### Opening the app

Android identifies the launcher Activity from the manifest. If the process is new, it first creates `ZmanimApplication`, which constructs the repository, offline places, calendar gateway, reminder scheduler, and location service. Learning and elevation helpers initialize lazily when first used. The repository starts loading stored information in the background.

`MainActivity` sets up Compose. `ZmanimApp` observes repository and screen state, applies the language, layout direction, and theme, and chooses the navigation destination. A navigation host switches between calendar, zmanim, and settings; most editors appear as dialogs inside the main Activity. A “screen” in this app is usually a composable function, not a separate Activity.

The repository exposes `Loading`, `Ready`, or `Failed`. This lets the UI show a loading indicator or retry message instead of treating missing data as an empty database. `AppViewModel` combines loaded data with the selected day and timeline range to produce `ScreenData`.

### Changing a profile or saving an event

An editor keeps a draft. On save, it calls a repository operation, which validates the model and serializes the changed records into storage. The repository publishes a new `AppData` value through a `StateFlow`. The ViewModel observes that value, recomputes the relevant screen data, and Compose redraws affected content.

There is no HTTP form submission or page reload. A **Flow** is a stream of values over time; a **StateFlow** additionally exposes the latest value. **Coroutines** let work suspend while waiting without blocking the UI thread. `Dispatchers.IO` is used for storage/I/O and `Dispatchers.Default` for preparing calculated screen data. `suspend` does not automatically mean “runs on another thread”; the calling coroutine/context determines that.

The ViewModel uses a queue for profile writes so rapid selection changes are processed in order. A separate one-second ticker updates countdown displays while the app is visible. The expensive calendar and zmanim calculations respond to data/date/range changes, rather than rerunning with every countdown tick.

## 5. Storage, calculations, and background work

### Three kinds of on-device files

| Kind | Contents | Lifetime |
| --- | --- | --- |
| Packaged input | City/learning tables, timezone polygons, XML resources, help and notices. | Installed with the APK; updated by distributing a new build. |
| Durable app data | Profiles, places, events, widget configuration, export links, preferences. | Kept in the app's private storage across normal updates. |
| Cache/temporary data | Expanded timezone geometry and temporary backup exports. | Replaceable; Android may clear cache. |

**Room** is an Android persistence library over **SQLite**, a database stored in a local file. It is roughly comparable to a small ORM/data access layer, but it generates implementations and checks annotated SQL during compilation. `AppDatabase` declares tables, `AppDao` declares queries and writes, and `AppRepository` uses the generated implementation.

The database is named `zmanim.db` and currently has schema version 2. Most rows contain an ID, a useful indexed field such as name/title, and a JSON `payload` containing the full domain model. This is not a database with a SQL column for every Kotlin property. `kotlinx.serialization` converts the `@Serializable` models to and from JSON.

Global settings live in a **Preferences DataStore** named `calendar_settings`, primarily as JSON under `settings_v1`. DataStore is an asynchronous preference store. A separate old `zmanim_preferences` store is read to migrate prototype selections.

Some platform helpers also use Android's older **SharedPreferences**, a private key/value store: `reminder_delivery` tracks reminder delivery/scheduling state, `zman-alarm-state` holds alarm state, and `terrain-elevation` caches elevation responses. These are supporting state rather than the main user-record database, and the selective JSON backup does not copy these stores wholesale.

Room and DataStore are separate storage systems, so they cannot participate in one ordinary SQLite transaction. The repository first records pending settings in the Room `settings_journal` table along with record changes, then writes DataStore, then clears the journal. Startup replays a pending journal entry if the process died between those steps. A mutex serializes repository updates. The database migration from version 1 to 2 creates this journal table without discarding existing records.

Normal updates using the same application ID and compatible signing identity preserve private app data. Uninstalling or clearing storage removes it. Android automatic backup is disabled and its backup/transfer exclusions are configured; the app instead provides selective JSON export/import. Backups can include settings, profiles, places, and events, but do not clone Android permission grants or launcher widget instances. Imports deliberately do not silently activate imported reminders.

### Calculations are separate from presentation

`Models.kt` defines information such as `Profile`, `Place`, `Event`, and `AppSettings`. An event is a rule; an `Occurrence` is one concrete instance of that rule on a date. You do not store every future Tuesday as a separate app event.

`MethodCatalog` lists supported built-in calculations and their stable IDs. `CalculationEngine` evaluates those methods through KosherJava and combines custom formulas. It distinguishes actual times, durations, methods that do not apply on the date, and unavailable results with an explanation. Missing astronomical events or required inputs do not become invented times.

`CalendarEngine` prepares Gregorian/Hebrew month grids, holidays, parsha, and current Hebrew-date rollover. A `LocalDate` is a civil date used as an unambiguous index; sunset rollover is applied explicitly where that feature needs it. Israel/diaspora holiday policy is distinct from a place's civil timezone.

`OccurrenceEngine` expands recurrence rules, handles Hebrew leap-year/Adar and missing-day policies, and resolves event times, including times relative to a zman. `LocalIntervals.kt` handles wall-clock intervals across clock changes. `Instant` represents an absolute moment; `LocalDate` and `LocalTime` need a timezone to identify a moment. Formatting and rounding happen later, in `TimeFormatting`.

### Android integration and maintenance

The platform helpers isolate communication with Android: `CalendarGateway` uses the system Calendar Provider, `DeviceLocation` uses Android location services, and `ReminderScheduler` uses AlarmManager and notifications. Calendar Provider access is not a direct Google Calendar API login: Android exposes calendars available on the device, and their own sync services may synchronize them.

An app setting enables a feature; an Android permission allows the OS operation. These are separate. Declaring a permission in the manifest is not the same as the user granting it at runtime. Notifications, precise alarms, location, background location, calendar access, and full-screen alarm access have different rules and settings screens.

`ZmanimApplication` requests maintenance after data changes, on startup, and through a recurring WorkManager request at six-hour intervals. It also observes device-calendar changes when allowed. Requests are briefly delayed/coalesced to avoid scheduling a new maintenance job for every tiny edit. The periodic work is not guaranteed to run at exact six-hour boundaries.

Maintenance reconciles managed calendar exports, registers upcoming reminders, refreshes the Hebrew-date notification, configures background location, and updates widgets. “Reconcile” means compare desired state with current registrations/links and make them agree. Receivers request the necessary work after restart, time/timezone change, or app replacement.

Precise reminders use **AlarmManager**, not the six-hour worker. When due, a receiver posts a notification or starts the alarm controller/service. A foreground service keeps ringing audio/vibration active with a visible notification; an Activity provides snooze/dismiss controls. The app checks the relevant access and channel state, and approximate notification delivery requires explicit opt-in.

### Widgets are hosted by the launcher

The app uses **Glance**, which has Compose-like Kotlin APIs but produces Android home-screen widgets. A widget is hosted by the phone's launcher, outside the main app window. It cannot simply reuse an arbitrary Compose screen.

The manifest registers widget receivers. Each receiver points to an XML provider description defining size, resizing, configuration Activity, and preview/loading layout. `WidgetConfigurationActivity` saves each widget's options. `ZmanimWidgets.kt` gathers its data; `WidgetPresentation.kt` draws it. All widgets follow the app location, while configuration can select a calculation profile and presentation options.

Widgets are refreshed through Android update requests and application maintenance. A boundary alarm refreshes at relevant midnight, sunset, or zman transitions. Native `Chronometer` views display countdown seconds without waking Kotlin code once a second just to redraw a number.

## 6. Resources and bundled data

### `res/` is an Android resource system

Android compiles resources and generates identifiers in `R`, so Kotlin can use `R.drawable.hebrew_day_1` or `R.layout.widget_countdown`. In XML, `@string/app_name` references a string resource, and `@xml/widget_date` references another XML resource. `@+id/widget_countdown` declares a new view ID.

Resource names generally use lowercase letters and underscores. Subdirectory names identify types and optional qualifiers:

| Directory | What it contains |
| --- | --- |
| `drawable/` | Vector drawings and XML shape/gradient backgrounds. XML here describes an image, not necessarily a UI layout. |
| `layout/` | Traditional Android view hierarchies, used here for widget previews and the countdown view. |
| `mipmap-*/` | Launcher icons in density-specific or density-independent forms. |
| `values/` | Named strings and styles. The filename groups definitions; resource names come from entries inside it. |
| `xml/` | Other Android configuration, such as widget provider and backup rules. |

`mdpi`, `hdpi`, `xhdpi`, `xxhdpi`, and `xxxhdpi` represent increasingly dense screens. Android selects the appropriate resource; these files are not separate installed apps. `anydpi` provides density-independent adaptive icon definitions. **dp** sizes scale for screen density, and **sp** text sizes also account for font scaling.

The XML theme sets the Activity's base window theme. `ui/theme/Theme.kt` configures Compose's colors and typography inside that window. These cooperate; the XML theme is not the sole source of the app's appearance.

The app's English/Hebrew UI translations mostly live in Kotlin calls such as `tr(english, hebrew)` and the message translation helper. There is no `values-he/strings.xml` translation tree currently. Interface language, calendar/zman labels, and calendar layout direction are separate preferences.

### `assets/` contains files opened by filename

Assets retain filenames and directory structure and are read with `context.assets.open(...)`. They do not become `R` entries. This is where large raw tables, JSON metadata, and license texts belong.

`OfflinePlaces` reads the compressed city table, applies reviewed locality corrections, and searches names/aliases. For a coordinate's timezone it uses the polygon index plus binary geometry. Geometry is unpacked into cache and memory-mapped, so the OS can load needed sections without allocating the entire global boundary dataset on the app heap.

`LearningSchedules` reads versioned metadata and the compressed daily-reference table, caching a small number of requested dates. It also obtains some calendar rows from KosherJava. The references have finite coverage: most generated tables cover 2020–2040; individual programs can have shorter ranges. Oraysa's current bundled table ends 7 November 2026. Outside coverage the app shows unavailability rather than extrapolating. The data is already generated and committed; building/running the app does not require installing Python, Node, or Hebcal.

Keep the **`.gzip` filenames**. Android's asset merger treats `.gz` specially and can unpack/rename those inputs. This project deliberately uses `.gzip` so its own `GZIPInputStream` code can read the expected packaged filenames.

## 7. Every project file

Paths in each table are relative to its stated directory. Directory names group related responsibilities; they are not separate processes or Gradle modules.

### Root files and `gradle/`

| File | Purpose |
| --- | --- |
| `.gitignore` | Keeps local IDE state, caches, generated outputs, signing keystores, and diagnostics out of Git. It does not delete files or secure a secret already committed. |
| `README.md` | Short project overview, user-facing behavior, data coverage, build commands, and development identity. |
| `build.gradle.kts` | Root plugin declarations shared with modules; does not build an APK itself. |
| `settings.gradle.kts` | Names the project, includes `:app`, configures plugin/library repositories, and installs the Foojay toolchain resolver. Repository mode rejects per-module repository declarations. |
| `gradle.properties` | Gradle JVM memory/encoding settings, configuration-cache enablement, and Kotlin style preference. |
| `gradlew` | Unix Gradle Wrapper launcher; use this for terminal builds on Linux/macOS. |
| `gradlew.bat` | Windows version of the wrapper launcher. |
| `gradle/libs.versions.toml` | Shared aliases and versions for libraries and build plugins. |
| `gradle/gradle-daemon-jvm.properties` | JDK 25 daemon criteria and platform download URLs. Generated by Gradle's daemon-JVM configuration task and committed to share the toolchain choice. |
| `gradle/wrapper/gradle-wrapper.properties` | Gradle 9.6.0 distribution URL, checksum, timeout, validation, and cache locations. |
| `gradle/wrapper/gradle-wrapper.jar` | Small binary wrapper bootstrap program. It downloads/launches the configured Gradle distribution. |
| `app/.gitignore` | Module-level exclusion for generated app build output. |
| `app/build.gradle.kts` | Application plugins, Android API/identity/version settings, Java compatibility, Compose enablement, release optimization, and dependencies. |
| `docs/todo.md` | Remaining planned work; currently choosing the app name and designing launcher icons. |
| `docs/project-guide.md` | This explanation and complete project file reference. |

### Manifest and application entry points

| File | Purpose |
| --- | --- |
| `app/src/main/AndroidManifest.xml` | Declares permissions, the Application, Activities, alarm service/receivers, widget receivers, FileProvider, icon/name/theme, and backup policy. The launcher intent filter identifies `MainActivity`. `exported` controls whether other apps/system entry points can invoke a component. |
| `app/src/main/java/com/example/zmanim/ZmanimApplication.kt` | Shared service construction, repository-change callbacks, calendar observation, maintenance scheduling, background receiver completion, and `MaintenanceWorker`. |
| `app/src/main/java/com/example/zmanim/MainActivity.kt` | Main Android window plus root `ZmanimApp` composable. Reads launch Intents, restores launch handling, applies theme/language/direction, observes state, and wires bottom navigation, screens, editors, and permissions. |

### Domain files

Directory: `app/src/main/java/com/example/zmanim/domain/`.

| File | Purpose |
| --- | --- |
| `Models.kt` | Shared enums and models for locations, profiles, formulas, reminders, events/recurrence, settings, widgets, export links, calculation results, and occurrences. Includes location/profile lookup and dependency helpers. Most stored models are serializable. |
| `MethodCatalog.kt` | Catalog of built-in KosherJava calculations, stable IDs, labels, groups, source definitions, requirements, and evaluation callbacks. Connects user selections to actual library methods. |
| `CalculationEngine.kt` | Executes built-in/custom zmanim with date, place, profile, elevation policy, and holiday context. Validates formula dependencies and options, detects cycles, and returns times/durations/unavailability plus explanations. Also declares calculation query/method/group types. |
| `CalendarEngine.kt` | Gregorian/Hebrew conversion, month bounds and grids, labels, holidays/parsha/Omer, month/year shifting, Daf Yomi helper, and explicit sunset rollover for current Hebrew dates. |
| `OccurrenceEngine.kt` | Validates event rules, generates recurrence dates, and turns them into concrete occurrences. Handles missing days, Adar choices, exclusions, fixed/all-day/zman-relative times, and clock transitions. |
| `LocalIntervals.kt` | Maps a local wall-clock interval to valid absolute-time segments across timezone offset changes, including skipped/repeated times. |
| `LocationResolution.kt` | Resolves timezone candidates, retains nearby manual timezone choices, preserves applicable height metadata, and normalizes/ranks city names and aliases. |
| `TimeFormatting.kt` | Locale-aware time and duration/countdown presentation, seconds/12–24-hour choices, rounding, and weekday labels. Formatting stays separate from calculation. |
| `ZmanLabels.kt` | Chooses profile overrides, custom names, or built-in labels, and adds Unicode direction isolates so Hebrew labels containing Latin qualifiers/numbers display coherently. |
| `SpecialDays.kt` | Default special-day color palette and category override resolution used by calendar presentation/settings. |
| `EventStart.kt` | Small date plus optional time model passed from calendar gestures to event creation. |

### Older calculation helper

Directory: `app/src/main/java/com/example/zmanim/calculation/`.

| File | Purpose |
| --- | --- |
| `ZmanimCalculator.kt` | Earlier, small sunrise/sunset/date preview adapter with `SavedLocation` and `SolarDay`, including a Jerusalem example location. The current main UI uses `domain/CalculationEngine.kt`; this helper is not the main calculation pipeline. |

### Data files

Directory: `app/src/main/java/com/example/zmanim/data/`.

| File | Purpose |
| --- | --- |
| `AppRepository.kt` | Owns the loaded `AppData` state and persistence operations. Creates Room/DataStore, recovers settings journal entries, migrates prototype choices, validates saves, serializes records, handles profile duplication/event-series edits, and triggers maintenance after changes. Defines `RepositoryState`, shared JSON configuration, database migration, and reviewed holiday-schedule initialization. |
| `BackupService.kt` | Defines version-2 JSON backup format, selective export, import preview/validation, and keep/replace/copy conflict merging with ID remapping. Removes device-specific links and disables imported reminder activation. It prepares data; the dialog supplies file interaction. |
| `OfflinePlaces.kt` | Loads/searches compressed cities, applies locality corrections, reads timezone polygon metadata, prepares the memory-mapped geometry cache, and finds timezone candidates for coordinates. |
| `LearningSchedules.kt` | Loads provider metadata and date/reference rows, caches date results, supplies calendar-based learning rows, and reports explicit no-learning/unavailable states. |
| `ZmanimPreferences.kt` | Old prototype DataStore for sunrise/sunset selection and its `SolarMethod` enum. Retained because `AppRepository` reads it once to migrate older installations. Current profiles are stored through the repository. |

### Java database files

Directory: `app/src/main/java/com/example/zmanim/data/db/`. These are compact Room declarations; their generated implementations go into build output.

| File | Purpose |
| --- | --- |
| `AppDatabase.java` | Declares the six Room entity types, schema version 2, and DAO accessor. Schema export is disabled. |
| `AppDao.java` | Annotated SQL interface for loading/upserting/deleting records and reading/clearing pending settings. Room generates the implementation. |
| `ProfileRow.java` | `profiles` table row: ID, indexed name, full JSON payload. |
| `PlaceRow.java` | `places` table row: ID, indexed name, JSON payload, including stored device-place information. |
| `EventRow.java` | `events` table row: ID, indexed title, JSON event-rule payload. |
| `WidgetRow.java` | `widgets` table row: ID, indexed widget kind, JSON configuration. |
| `ExportRow.java` | `exports` table row: ID, indexed app event ID, JSON link to a managed Calendar Provider record. |
| `SettingsJournalRow.java` | `settings_journal` row: fixed integer ID and pending settings JSON, used for recovery between Room and DataStore writes. |

### UI files

Directory: `app/src/main/java/com/example/zmanim/ui/`.

| File | Purpose |
| --- | --- |
| `AppViewModel.kt` | Bridges repository/platform/domain state to the UI. Builds `ScreenData`, tracks selected date/timeline range, runs the visible countdown ticker, queues profile writes, wraps actions/errors, and handles device-location acquisition and timezone confirmation. |
| `CalendarScreen.kt` | Calendar toolbar, Gregorian/Hebrew month grid, week/day schedule, event cards and overlap layout, navigation gestures, special-day coloring, and event-creation gestures. |
| `ZmanimScreen.kt` | Scrolling zmanim timeline, date navigation, countdowns, selection/profile/reminder actions, and calculation detail dialogs. |
| `SettingsScreen.kt` | Display, Calendar, Zmanim, Permissions, and Data settings tabs; connects controls to settings and relevant Android permission/settings actions. |
| `EventEditor.kt` | App event editor/details, recurrence and reminder options, single/future/series operations, plus device-calendar occurrence details/editing and conversion to app-managed rules. |
| `ProfileEditors.kt` | Profile list/create/duplicate/delete/edit dialogs, method picker, custom formula editor, display-name overrides, reminder offset controls, and JSON-backed `rememberDraft` helper. |
| `ReminderEditors.kt` | Profile reminder collections and alarm options, including sound selection, vibration, and snooze settings. |
| `LocationEditors.kt` | App location selection, snapshot/follow actions, city search, saved-place editing, timezone confirmation, elevation lookup, and reusable location-binding controls. |
| `BackupDialog.kt` | Backup selection, Android file create/open contracts, export sharing, import preview/conflict choices, and applying imported data through the repository. |
| `Onboarding.kt` | Despite the filename, currently contains Gregorian/Hebrew go-to-date and time-selection dialogs. It does not implement a startup onboarding wizard. |
| `ColorSetting.kt` | Special-day category color chooser used by settings. |
| `Components.kt` | Shared UI controls and dialog scaffolding, headings, fields, choices/toggles, notices, confirmation/discard handling, enum labels, translation context, and method labels. |
| `Messages.kt` | Maps validation/runtime messages into Hebrew when requested, including recognizable message patterns. |
| `MethodHelp.kt` | Produces localized method explanations from catalog/source definitions and current profile choices. |
| `HelpScreen.kt` | Bundled English/Hebrew help topics, contextual help links, About/source metadata, and display of asset notices/license texts. |

Directory: `app/src/main/java/com/example/zmanim/ui/theme/`.

| File | Purpose |
| --- | --- |
| `Color.kt` | Named Compose color constants. |
| `Theme.kt` | Light/dark Material 3 color schemes and the `ZmanimTheme` wrapper. |
| `Type.kt` | Compose typography sizes, line heights, weights, and default font choices. |

### Android platform files

Directory: `app/src/main/java/com/example/zmanim/platform/`.

| File | Purpose |
| --- | --- |
| `CalendarGateway.kt` | Reads device calendars/instances, checks read/write access, edits supported selected occurrences, saves ordinary provider events, builds calendar Intents, and reconciles/adopts/restores links for app-managed exports. |
| `DeviceLocation.kt` | Checks location access, obtains device fixes, resolves offline timezone candidates, converts fixes to `Place`, and configures background location delivery. Also defines `LocationUpdateReceiver`. |
| `ElevationLookup.kt` | Explicit HTTPS terrain-height lookup through Open Topo Data/SRTM90m with caching and result/source metadata. This is the optional network lookup, not a dependency of offline zmanim calculation. |
| `ReminderScheduler.kt` | Computes upcoming event/profile reminders, reconciles AlarmManager registrations, checks precise/notification access, creates notification channels, and updates the Hebrew-date notification. Also defines `ReminderReceiver` and `SystemChangeReceiver`. |
| `ZmanAlarmService.kt` | Ringing-alarm controller, persisted active/snoozed alarm state, foreground audio/vibration service, snooze receiver, and lock-screen-capable alarm Activity with snooze/dismiss controls. Several manifest components therefore come from this one file. |

### Widget files

Directory: `app/src/main/java/com/example/zmanim/widgets/`.

| File | Purpose |
| --- | --- |
| `ZmanimWidgets.kt` | Glance widget classes/receivers for events, zmanim, Hebrew/Gregorian date badges, compact badges, and daily overview. Collects rows, creates launch Intents, handles deletion, updates widgets, and schedules boundary refreshes through `WidgetBoundaryReceiver`. |
| `WidgetPresentation.kt` | Widget visuals, responsive sizing, light/dark backgrounds, text fitting/alignment, scrollable rows, date formatting, tap actions, and native countdown embedding. Its `widgetPlace` helper follows the app location. |
| `WidgetConfigurationActivity.kt` | Android Activity opened by the launcher to configure a widget's profile, rows, count, calendar, rollover, opening/tap behavior, and countdown options, then save/update that widget instance. |

### Bundled assets

Directory: `app/src/main/assets/`.

| File | Purpose |
| --- | --- |
| `cities.tsv.gzip` | Compressed tab-separated offline city IDs, names, countries, coordinates, zones, optional heights, and aliases. |
| `place-data.json` | Provenance/version/count metadata for place and timezone datasets, also used to version the expanded timezone cache. |
| `locality-corrections.json` | Reviewed per-locality corrections keyed by stable GeoNames IDs, with source coordinates/references. Does not replace the global timezone boundary dataset. |
| `timezone-index.json` | Timezone polygon bounding boxes and ring offsets/counts into the binary coordinate data. |
| `timezone-points.bin.gzip` | Compressed binary polygon points paired with the timezone index; expanded into cache and memory-mapped on demand. |
| `learning.tsv.gzip` | Compressed date/provider/reference rows used by the daily overview learning choices. Contains references rather than full book texts. |
| `learning-metadata.json` | Learning provider IDs, English/Hebrew names, cycle/edition, coverage, source/version information, and source checks where recorded. |
| `NOTICE.txt` | Attribution/source overview for places, boundaries, calculations, optional elevation, learning, and packaged libraries. |
| `NOTICE-learning.txt` | Learning-source attribution, finite reference coverage, and the Hebcal BSD two-clause license. |
| `NOTICE-hebrew-icons.txt` | Attribution and license notice for Hebrew day icon glyph outlines derived from DejaVu Sans Bold. |
| `LICENSE-Apache-2.0.txt` | Bundled Apache license text for relevant libraries. |
| `LICENSE-CC-BY-4.0.txt` | Bundled Creative Commons Attribution license text associated with city data. |
| `LICENSE-LGPL-2.1.txt` | Bundled LGPL license text associated with KosherJava. |
| `LICENSE-ODbL-1.0.txt` | Bundled Open Database License text associated with timezone boundary data. |

### Drawables: icons and widget backgrounds

Directory: `app/src/main/res/drawable/`. The Hebrew day files are vector glyph drawings, each used for that Hebrew day number in the ongoing date notification's status icon.

| File | Purpose |
| --- | --- |
| `hebrew_day_1.xml` | Hebrew day 1 icon: א. |
| `hebrew_day_2.xml` | Hebrew day 2 icon: ב. |
| `hebrew_day_3.xml` | Hebrew day 3 icon: ג. |
| `hebrew_day_4.xml` | Hebrew day 4 icon: ד. |
| `hebrew_day_5.xml` | Hebrew day 5 icon: ה. |
| `hebrew_day_6.xml` | Hebrew day 6 icon: ו. |
| `hebrew_day_7.xml` | Hebrew day 7 icon: ז. |
| `hebrew_day_8.xml` | Hebrew day 8 icon: ח. |
| `hebrew_day_9.xml` | Hebrew day 9 icon: ט. |
| `hebrew_day_10.xml` | Hebrew day 10 icon: י. |
| `hebrew_day_11.xml` | Hebrew day 11 icon: יא. |
| `hebrew_day_12.xml` | Hebrew day 12 icon: יב. |
| `hebrew_day_13.xml` | Hebrew day 13 icon: יג. |
| `hebrew_day_14.xml` | Hebrew day 14 icon: יד. |
| `hebrew_day_15.xml` | Hebrew day 15 icon: טו. |
| `hebrew_day_16.xml` | Hebrew day 16 icon: טז. |
| `hebrew_day_17.xml` | Hebrew day 17 icon: יז. |
| `hebrew_day_18.xml` | Hebrew day 18 icon: יח. |
| `hebrew_day_19.xml` | Hebrew day 19 icon: יט. |
| `hebrew_day_20.xml` | Hebrew day 20 icon: כ. |
| `hebrew_day_21.xml` | Hebrew day 21 icon: כא. |
| `hebrew_day_22.xml` | Hebrew day 22 icon: כב. |
| `hebrew_day_23.xml` | Hebrew day 23 icon: כג. |
| `hebrew_day_24.xml` | Hebrew day 24 icon: כד. |
| `hebrew_day_25.xml` | Hebrew day 25 icon: כה. |
| `hebrew_day_26.xml` | Hebrew day 26 icon: כו. |
| `hebrew_day_27.xml` | Hebrew day 27 icon: כז. |
| `hebrew_day_28.xml` | Hebrew day 28 icon: כח. |
| `hebrew_day_29.xml` | Hebrew day 29 icon: כט. |
| `hebrew_day_30.xml` | Hebrew day 30 icon: ל. |
| `ic_launcher_background.xml` | Current launcher icon background vector. |
| `ic_launcher_foreground.xml` | Current Android-template foreground vector, also referenced as the adaptive icon's monochrome layer. |
| `widget_date_background.xml` | Light-theme date badge gradient/rounded background. |
| `widget_date_background_dark.xml` | Dark-theme date badge background. |
| `widget_event_card.xml` | Light-theme individual event row/card background. |
| `widget_event_card_dark.xml` | Dark-theme event card background. |
| `widget_events_background.xml` | Light-theme upcoming-events widget background. |
| `widget_events_background_dark.xml` | Dark-theme upcoming-events background. |
| `widget_overview_background.xml` | Light-theme daily overview background. |
| `widget_overview_background_dark.xml` | Dark-theme daily overview background. |
| `widget_zman_card.xml` | Light-theme individual zman row/card background. |
| `widget_zman_card_dark.xml` | Dark-theme zman card background. |
| `widget_zmanim_background.xml` | Light-theme upcoming-zmanim widget background. |
| `widget_zmanim_background_dark.xml` | Dark-theme upcoming-zmanim background. |

### Widget layouts

Directory: `app/src/main/res/layout/`.

| File | Purpose |
| --- | --- |
| `widget_countdown.xml` | Native countdown `Chronometer` view embedded through `RemoteViews` into Glance widgets. |
| `widget_date_preview.xml` | Hebrew 2 × 1 date widget preview for the launcher picker. |
| `widget_date_compact_preview.xml` | Hebrew 1 × 1 date widget preview. |
| `widget_gregorian_date_preview.xml` | Gregorian 2 × 1 date widget preview. |
| `widget_gregorian_date_compact_preview.xml` | Gregorian 1 × 1 date widget preview. |

Preview text is a static illustration, not the current date. The actual widget presentation comes from Kotlin.

### Launcher icon files

Paths below are relative to `app/src/main/res/`.

| File | Purpose |
| --- | --- |
| `mipmap-anydpi/ic_launcher.xml` | Adaptive launcher icon combining foreground, background, and monochrome references. |
| `mipmap-anydpi/ic_launcher_round.xml` | Round launcher icon's adaptive definition using the same layers. |
| `mipmap-mdpi/ic_launcher.webp` | Standard bitmap icon at medium density. |
| `mipmap-mdpi/ic_launcher_round.webp` | Round bitmap icon at medium density. |
| `mipmap-hdpi/ic_launcher.webp` | Standard bitmap icon at high density. |
| `mipmap-hdpi/ic_launcher_round.webp` | Round bitmap icon at high density. |
| `mipmap-xhdpi/ic_launcher.webp` | Standard bitmap icon at extra-high density. |
| `mipmap-xhdpi/ic_launcher_round.webp` | Round bitmap icon at extra-high density. |
| `mipmap-xxhdpi/ic_launcher.webp` | Standard bitmap icon at double-extra-high density. |
| `mipmap-xxhdpi/ic_launcher_round.webp` | Round bitmap icon at double-extra-high density. |
| `mipmap-xxxhdpi/ic_launcher.webp` | Standard bitmap icon at triple-extra-high density. |
| `mipmap-xxxhdpi/ic_launcher_round.webp` | Round bitmap icon at triple-extra-high density. |

### Values and Android XML configuration

Paths below are relative to `app/src/main/res/`.

| File | Purpose |
| --- | --- |
| `values/strings.xml` | Installed app label (`Zmanim`) and static date/weekday/month/year strings for widget previews. |
| `values/themes.xml` | Base Activity/window style `Theme.Zmanim`, using a platform Material light theme without a platform action bar. Compose draws the app's own UI. |
| `xml/backup_rules.xml` | Legacy Android backup exclusion rules for private database/preferences/files/root data. |
| `xml/data_extraction_rules.xml` | Newer Android cloud-backup/device-transfer exclusion rules. Complements the manifest's disabled automatic backup. |
| `xml/share_paths.xml` | Restricts FileProvider file sharing to the app cache's `exports/` directory. Used for temporary backup attachments. |
| `xml/widget_events.xml` | Provider sizing, resize behavior, loading layout, configuration Activity, and update metadata for upcoming events. |
| `xml/widget_zmanim.xml` | Equivalent provider metadata for upcoming zmanim. |
| `xml/widget_combined.xml` | Equivalent metadata for the daily overview/calendar and learning widget. |
| `xml/widget_date.xml` | Hebrew 2 × 1 date widget metadata, with its preview and optional initial configuration. |
| `xml/widget_date_compact.xml` | Hebrew 1 × 1 date widget metadata and compact preview. |
| `xml/widget_gregorian_date.xml` | Gregorian 2 × 1 date widget metadata and preview, with optional initial configuration. |
| `xml/widget_gregorian_date_compact.xml` | Gregorian 1 × 1 date widget metadata and compact preview. |

`@layout/glance_default_loading_layout` in provider XML comes from the Glance dependency. Android merges library resources with app resources, so a referenced resource need not have a source file in this repository.

## 8. Local files and generated files

### Machine-specific configuration

`local.properties` is present locally but ignored by Git. It tells Android tooling where this computer's SDK is installed, usually with `sdk.dir=...`. A different computer needs its own path, which Android Studio can create. This is build-machine configuration, not app settings.

The local `.idea/` directory belongs to Android Studio/IntelliJ. These files are currently present and ignored; their exact contents and membership can change as you use the IDE:

| File | Purpose |
| --- | --- |
| `.idea/.gitignore` | IDE-generated ignore rules for local IDE files. |
| `.idea/AndroidProjectSystem.xml` | Android project-system IDE metadata. |
| `.idea/compiler.xml` | IDE compiler configuration metadata. |
| `.idea/gradle.xml` | IDE Gradle project linkage/settings. |
| `.idea/misc.xml` | General project/JDK IDE metadata. |
| `.idea/vcs.xml` | Version-control directory mappings. |
| `.idea/workspace.xml` | Per-user workspace/session state, such as open views and local task state. |
| `.idea/deploymentTargetSelector.xml` | Local run/deployment target selection state. |
| `.idea/deviceManager.xml` | Local device-manager IDE settings. |
| `.idea/runConfigurations.xml` | IDE run-configuration metadata. |
| `.idea/caches/deviceStreaming.xml` | Cached IDE device-streaming metadata. |
| `.idea/codeStyles/Project.xml` | IDE project code-formatting settings. |
| `.idea/codeStyles/codeStyleConfig.xml` | Which IDE code-style settings to use. |
| `.idea/inspectionProfiles/Project_Default.xml` | IDE static-inspection profile. |

`.git/` is Git's internal repository database: commits, branches, index, and local configuration. Its files are not application inputs. The local environment also exposes `.agents/`, `.codex/`, and `.aws/`; they contain no project application files in this checkout and are not part of the APK or the version-controlled project inventory.

### Recreated build outputs

These directories/files may appear after a sync/build and are ignored by Git:

| Path/category | Contents and treatment |
| --- | --- |
| `.gradle/` | This project's Gradle state and configuration cache. Recreated as needed. |
| `.kotlin/` | Kotlin compiler/tooling caches when created. |
| `build/` | Root build reports/output when generated. |
| `app/build/` | Compiled classes, generated Room/serialization-related output, merged resources/manifest, intermediates, reports, and APKs. Edit original sources, not these files. |
| `app/build/outputs/apk/debug/app-debug.apk` | Normally the installable result of `:app:assembleDebug`, signed with this development environment's debug key. |
| `app/build/outputs/apk/release/` | Release APK outputs; plain release assembly is unsigned with the current configuration. |
| `app/build/reports/` | Build/static-check reports, including lint reports when generated. |
| `~/.gradle/` | Shared downloaded dependencies, Gradle distributions, and other build caches outside the repository. |
| `~/.android/debug.keystore` | Conventional local debug signing key outside the repository; automatically created by Android tooling. |

`R` resource identifiers, Room's DAO/database implementation, and other generated code are deliberately not handwritten project files. If an error points into generated code, fix the model/resource/build input responsible and rebuild.

## 9. Where to make common changes

| Desired change | Start here |
| --- | --- |
| Understand startup/navigation | `MainActivity.kt`, then `ZmanimApplication.kt`. |
| Understand a visible value | Its screen, `AppViewModel.kt`, then the relevant domain engine/model. |
| Add/change a stored domain field | `Models.kt`, repository serialization/validation, and backup behavior. A database table/column change also needs a Room schema migration. |
| Add a built-in calculation | `MethodCatalog.kt`, then calculation requirements/details, labels/help, and relevant UI grouping. |
| Change custom formula behavior | `CalculationEngine.kt` and `ProfileEditors.kt`. |
| Change recurrence/date behavior | `OccurrenceEngine.kt`, `CalendarEngine.kt`, and clock-interval handling as relevant. |
| Change UI colors/typography | `ui/theme/`; special-day colors also use `SpecialDays.kt`; widgets additionally use their drawables/presentation file. |
| Change the installed label | `res/values/strings.xml` → `app_name`. |
| Replace launcher artwork | Adaptive definitions/layers and density-specific icon files under `res/`. |
| Add a library | Its alias/version in `gradle/libs.versions.toml`, then `implementation(...)` in the app build file, then Gradle Sync. |
| Update a packaged dataset | The relevant asset and its metadata/notices, preserving file format and matching index/geometry versions. |
| Debug notifications/alarms | `ReminderScheduler.kt`, `ZmanAlarmService.kt`, manifest permissions, then device permission/channel settings. |
| Debug a widget | Provider XML and manifest registration, then `ZmanimWidgets.kt`, configuration, and presentation. |
| Share a new version | Build an APK; keep the application ID/signing identity and increase `versionCode`. |

## 10. Sharing the app without Google Play

You can share an APK directly. A final product name, new icon, public store listing, and source-code access are not prerequisites. The recipient needs Android 8.0 or later and permission to install from the source they use to open the APK. Android officially supports [distribution outside Google Play](https://developer.android.com/distribute/marketing-tools/alternative-distribution).

### Quick option: build a debug APK for a friend

From Android Studio's terminal in the project root:

```sh
./gradlew :app:assembleDebug
```

After a successful build, share:

```text
app/build/outputs/apk/debug/app-debug.apk
```

You can rename a copy to something recognizable such as `Zmanim-1.0.apk`; renaming the file does not change the installed app's identity or label. Upload it to a file-sharing location and send a download link, or transfer the file directly. Some messaging/email services block executable attachments, in which case a download link is easier.

Build the APK explicitly instead of copying whatever Android Studio's Run action most recently deployed. Run can produce an APK marked `testOnly`, which expects installation through development tools rather than tapping a file. See Android's [build-for-distribution instructions](https://developer.android.com/build/build-for-release).

A debug APK is automatically signed and is adequate for a small, informal test. Its signing identity belongs to your local debug key, so builds from a different machine or a regenerated debug key may not update the same installation. If friends will keep using this app, it is worth starting with a signed release APK instead.

### Better for ongoing use: create a signed release APK

Signing is Android's way to verify that an update comes from the same author/key as the installed app. It is independent of being published on Google Play; a locally created key can sign a privately distributed APK. Android's [signing guide](https://developer.android.com/studio/publish/app-signing) documents the wizard and key management.

1. In Android Studio, choose **Build → Generate Signed Bundle / APK**. Exact menu wording can vary by Studio version.
2. Select **APK**, then the `app` module if prompted. An **AAB** is a publishing bundle, not the file your friend can tap to install.
3. Create a new keystore if you do not have one. Store the `.jks` file somewhere private outside the repository; choose a key alias and password, and keep a secure backup of the file and credentials.
4. Select that key, choose the **release** variant and destination, and generate the APK.
5. Use the wizard's output/Locate link to find the signed APK. Test that exact release build on a phone before distributing it, since release optimization differs from debug.

This project currently has no `signingConfigs` block, so `./gradlew :app:assembleRelease` by itself does **not** produce the signed installer you need. The Studio wizard can supply signing for the generated build. A persistent command-line signing configuration can be added later without committing passwords or the private key.

### Instructions to send the recipient

> This is my Android calendar/zmanim app. Download the `.apk` on your phone and open it, either from the download notification or your Files/Downloads app. If Android asks, allow “Install unknown apps” or “Allow from this source” for the app opening the download, then return and tap Install. Open Zmanim, choose your location and preferred zmanim, and grant calendar/location/notification permissions for the features you want. You can turn off the install-from-this-source permission afterward.

The install permission is per source, such as the browser or file manager; it is distinct from the permissions Zmanim requests for its own features. Android/Play Protect may scan or warn about a privately distributed app. A blocked or restricted device may need additional steps specific to its policy or verification status; do not assume every installation warning means the APK is broken. The recipient does not need to enable USB debugging for ordinary APK installation.

### Sending updates

For a straightforward update over an existing installation, keep the same `applicationId` and signing key, increase `versionCode` (`1` → `2` → `3`, etc.), and generate/share the new APK. Set `versionName` to whatever version label makes sense. The recipient opens the new APK and accepts the update; this app has no automatic APK update service.

The debug and release variants currently share `com.example.zmanim` but normally use different signing keys. Therefore a signed release will not install as an update over the Android Studio debug build already on your phone. Before switching, export the data you want to keep using the app's backup screen, uninstall the debug app, install the release APK, and import the backup. Include places/events in the export if you need them; reconfigure permissions/widgets and re-enable desired reminders afterward.

For direct distribution, losing the release key generally means you cannot deliver an ordinary compatible update to existing users. If you later publish through Google Play and want those private installations to update to the Play version, retain the application ID and plan Play App Signing around compatible signing identity. A visible rename/icon change can happen independently.

### Current Android verification rules

This part is time-sensitive. As of this guide's date, Google's published timeline starts verification protections in selected regions/distribution paths in September 2026 and expands them globally in 2027. It also offers a limited-distribution route for hobbyists. Check the current [Android developer verification guidance](https://developer.android.com/developer-verification) for the recipient's region/device and distribution method if verification blocks installation. Developer registration/verification and publishing a public Google Play listing are separate decisions.

For a few friends, directly sharing a signed APK is the practical starting point. You can make naming, artwork, and store-publication decisions separately.
