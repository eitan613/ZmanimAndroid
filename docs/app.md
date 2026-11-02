> **November 2026 redesign:** [problem-resolution.md](problem-resolution.md)
> records the implemented updates to navigation, defaults, applicability,
> factual help, permissions, and settings. Religious guidance remains outside the app; factual descriptions of the pinned
> calculations are included.

# Calendar and Zmanim

- Light/dark mode

### Calendar

- Option to open in lunar or solar month
- always show all visible months. in lunar mode show just lunar month (with year) and in solar mode show just solar month (with year)
- in lunar mode under the lunar month show all overlapping solar months (with year(s)). make bigger the one the selected day is in. same in solar mode under the solar month show all overlapping lunar months (with year(s)). make bigger the one the selected day is in. A month can overlap three months in the other calendar, so do not assume exactly two.
- Option to choose which day starts week (Sunday or Monday)
- Option to choose which direction the month goes in
- Toggle hebrew or transliterations for parsha/yom tov, etc.
- day mode (broken up by hour: option to show army time or standard time i.e. 12 hour or 24 hour)
- week mode
- menu item to choose what double tapping a day does (show zmanim, create event, go into day view, etc.)
- setting to control notifications about events
- show sefirah in month view toggle
- can add events
  1. one time or recurring
  2. which calendar to reoccur on
  3. how often to reoccur (daily, weekly, monthly, yearly)
  4. can add reminders
  5. integrate with google calendar, etc
  6. what time of day event is
  7. location
  8. guests
  9. description

- Israel/diaspora setting applies to both holidays and parsha schedules; keep it explicit rather than silently changing it when the user travels.
- set default view for opening the app
- set which accounts on the phone to sync
- show modern holidays
- show calendar holidays/events
- show parsha/special parsha
- swipe months or years (select which period a horizontal swipe changes)
- fade out a little days not in that month (but are in the week)
- toggle coloring special days like our pdf
- today, go to, new event, search event controls in menu dropdown
- widget to show **_x_** upcoming events (with a cap to keep size manageable).
  - clicking the widget can open into calendar mode (configured version) or into the event. whichever is configured
- widget showing day of month (solar or lunar). lunar can change at shkiyah.
- bigger widget with a bar showing day of both months with day of week, which month and year. below is a box that shows upcoming parsha and days daf
- option to show hebrew day in notification bar

### Zmanim

- show in 12 or 24 hour format
- toggle show seconds or round (with a +/- to show which direction rounded)
- toggle to control using elevation for sunrise/sunset
- very much like our project here:
  - users can choose which zmanim to show. which groups and which zmanim in those groups
  - so a user can choose to only show alos and to show 72 min. and 18 degrees and other options, etc
  - groups are alos, misheyakir, sunrise, shema, tiffilah, chatzos, mincha gedolah, mincha ketanah, plag, shkiyah, tzeis and sha'a zmanis
  - they can choose which groups to show and which zmanim in those groups. picking from any zmanim methods available
  - the picker must use user-friendly Hebrew/English/transliterated labels, never library names such as `get_tzais_gra`. For example, within Alos show "72 minutes before sunrise" and "Sun 18° below the horizon"; within Shema show the named opinion and the day boundaries that distinguish the calculation. Keep stable method IDs internal. Owner reviews labels for accuracy.
  - choose if its hebrew or english
  - no defaults
  - maybe some explanation and guidance: NOT AI GENERATED. placeholders can be. I will fill in the text if we decide to provide this
  - location getting zmanim for (option of user's location)
  - users can define their own zmanim (i.e. tzais is 20 minutes after shkiyah) with either fixed minutes offset or degrees or sha'os zmanios offsets
    - users can even create new ones like mincha: 15 minutes before shkiyah and mariv 20 minutes after
  - optional notification about zmanim
  - zmanim page is a scrollable page with zmanim that passed above you and upcoming zmanim below
    - kind of like [this](https://play-lh.googleusercontent.com/8TIvsxwU8FbaI8RDjzopghRgoDbIqI8FT0gB9juOdAkH0c48FrbdIO4UQHtkB-LmeLd-3yLgvppnV801ZHEr=w2560-h1440) just a much different look
    - can scroll as far as you want
    - widget to show **_x_** upcoming zmanim (with a cap to keep size manageable).
      - clicking the widget opens into zmanim mode no matter what the configured "open into setting is"

### Additional features

- Events relative to zmanim: for example, "Every weekday, 15 minutes before sunset." Recalculate each occurrence for its date and chosen location; allow reminders on these events.
- Calculation details on tap: method, coordinates, timezone, elevation policy, and custom formula. These factual details are separate from owner-authored explanations and guidance.
- Export/import settings, locations, custom zmanim, and personal events. Allow sharing calculation settings without including private events or locations unless explicitly selected.
- Explicit location modes: "Follow my location" or "Use this saved location." Always show the active location. Widgets can select a location independently.
- A now marker, highlighting of the next selected zman, optional time remaining, and a "Back to now" action. Preserve the user's scroll position while browsing another day.
- Core calculations and personal events work offline, without an account or backend. Network features, such as place search, elevation lookup, or external calendar synchronization, are optional.

### Saved zmanim profiles

Status: approved.

A profile is a user-named saved selection of zmanim and calculation methods, with their display order, custom definitions, and reminder preferences. For example, "Daily" might show one chosen alos calculation and one chosen tzeis calculation; "Compare methods" might show several alternatives. Users can create, rename, duplicate, and switch profiles instead of reconfiguring every checkbox. These are user-created selections, not supplied religious recommendations. A location can be selected separately.

### Accepted specification principles

These principles are agreed. Questions below concern the remaining policy choices and user settings, not whether to implement these principles.

- Hebrew recurrence keeps the original Hebrew date and rule as the source of truth. Calculate occurrences from that rule. Handle Adar I/II, missing day 30, and event-type differences explicitly; do not silently impose an observance policy.
- Keep the current Hebrew date separate from a civil appointment date. A sunset rollover in the Hebrew-date widget does not move tomorrow's civil appointments into today.
- Custom zmanim have explicit units and dependencies. Fixed minutes, solar depression angles, and sha'os zmanios are distinct calculation types. A degree-based calculation finds when the sun reaches the specified angle, not a fixed offset from sunset. Temporal-hour formulas specify their day boundaries.
- Distinguish time instants from durations. Sha'ah zmanis is a duration and does not appear as a timed occurrence in the upcoming-zmanim timeline.
- If a required astronomical event cannot be calculated, show "Unavailable" with a factual reason. Do not substitute another method silently. Dependent custom zmanim and reminders must also handle unavailability.
- Display all overlapping months in the secondary calendar, including cases with three months.
- Israel/diaspora selection controls holiday and parsha schedules.
- Users choose their calculation methods explicitly. First launch opens the calendar immediately; the zmanim screen offers selection and location setup when needed. No calculation methods are silently preselected.
- Retain unrounded calculated instants internally. Rounding and the +/- indication are display choices; explicitly define how alerts relate to the actual instant.
- Share one calculation engine between app screens, widgets, recurrence, and reminder scheduling.

### Implementation decisions (2026-11-01)

The implementation uses the decisions agreed during planning:

- Continue in this native Android repository, retaining `Zmanim`, `com.example.zmanim`, API 26 minimum, and the pinned toolchain for development. Publication branding is separate.
- Explicit Hebrew recurrence choices: ordinary Adar can map to Adar I, Adar II, both, or skip; leap-year Adar can map to ordinary Adar or skip; absent days can use previous day, next day, or skip. No event-type observance policy is inferred.
- One app-wide Hebrew all-day setting, explicitly selected before the first such event: civil day or previous-sunset-to-sunset. Changes preview affected events. Sunset spans export as timed occurrences.
- Current Hebrew date rollover uses sea-level sunset independently of selected methods. Missing sunset/location is unavailable, with no midnight substitution.
- Profiles include calculation-affecting settings; location and presentation settings are separate. Switching profiles switches profile reminders, while events retain their profile references.
- Bundle offline city and timezone data; permit manual coordinates, timezone, and elevation. Optional background location supports travel. Device fixes are flagged after 24 hours or a clock discontinuity, without silently switching location.
- Month cells show both dates, short enabled calendar labels, and event indicators; selected-day details appear below. Grid direction is independent of language. Horizontal swipes change months or years according to the selected setting; accessible period actions and the menu date picker provide alternatives.
- Display-only rounding offers down/nearest/up; `+` means later than the exact instant and `−` earlier. Reminders use exact instants plus their explicit offsets.
- Approximate alarms require user acceptance and show a may-be-late status. Denied notifications are inactive.
- Device Calendar Provider handles connected account calendars. Ordinary one-time events can be edited directly; app-owned recurrence exports individual occurrences for a rolling year. External changes pause managed updates and expose restore, detach/suppress, or stop-exporting choices. Guest invitations finish in the device calendar editor.
- Widgets support up to 10 events/zmanim with independent configuration and native scrolling when needed. The Hebrew-date notification contains the full date and a monochrome day-number icon, subject to Android visibility controls.
- Versioned JSON backups support selective export and keep/replace/copy import previews. Private locations/events are excluded from calculation sharing unless selected. Imported reminders remain configured but inactive until explicitly enabled; permissions and device bindings are not restored.
- Factual catalog labels await owner review in `docs/method-catalog.csv`. Factual calculation help is included; no narrative religious guidance is supplied. Optional online place/elevation providers and direct cloud integrations remain outside this implementation.

### Specification questions and decision ownership

**Owner (you)** means an app/product/content decision to settle during development. **App user** means a preference or event/formula choice the app must expose. If both are involved, separate rows identify the owner's policy decision and the user's selection. Proposed answers are not accepted decisions.

#### Calendar, dates, and recurrence

| ID | Decider     | Question / required choice                                                                                                                                                                         | Status or proposed direction                                                                                        |
|----|-------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------|
| C1 | Owner (you) | Which Hebrew recurrence policies are supported for ordinary Adar in leap years, Adar I/II in ordinary years, and a missing day 30? Which policies depend on birthday, anniversary, or yahrtzeit?   | Resolved: explicit choices; see implementation decisions. |
| C2 | App user    | Which supported recurrence policy should this event use, including behavior in years where its month/day is absent?                                                                                | Required where relevant; save with the event and preview future occurrences.                                        |
| C3 | Owner (you) | For a Hebrew-date event, does the date label cover the evening before the corresponding civil day, the daytime, or an explicit time window? How are all-day Hebrew events presented/exported?      | Resolved: required app-wide civil-day or sunset-span setting. |
| C4 | App user    | Is this event one-time or recurring; Hebrew or Gregorian; daily, weekly, monthly, or yearly; and at a fixed time, all day, or relative to a zman?                                                  | Required event-editor choices, plus interval/end conditions, reminders, and location where applicable.              |
| C5 | Owner (you) | Which sunset definition and elevation policy drive the current Hebrew-date rollover? What happens when location or sunset is unavailable?                                                          | Resolved: sea-level sunset; explicit unavailability. |
| C6 | App user    | Should the Hebrew-date widget advance at sunset or midnight, and which location should it use?                                                                                                     | Existing requested setting; civil appointments retain their dates.                                                  |
| C7 | App user    | Israel or diaspora holiday/parsha schedule?                                                                                                                                                        | One-time location-derived initial default, diaspora fallback, manual override; travel never changes the established choice.                                                             |
| C8 | App user    | Opening view; primary calendar; Sunday/Monday week start; month direction; scroll directions; double-tap action; special-day colors; holiday/parsha/Omer visibility; Hebrew/transliterated labels? | Existing requested preferences.                                                                                     |
| C9 | Owner (you) | What exactly do month direction and year/month scroll-direction controls change? Are label language and layout direction independent?                                                              | Resolved: independent grid/language; horizontal swipes change months or years; accessible period actions and menu date navigation. |

#### Zmanim and locations

| ID  | Decider     | Question / required choice                                                                                                                                        | Status or proposed direction                                                                                    |
|-----|-------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------|
| Z1 | Owner (you) | Beyond selected methods, display order, custom definitions, and reminder preferences, which settings belong to a profile versus the whole app? | Resolved: calculation settings in profiles; location/presentation app-wide. |
| Z2  | App user    | Which groups and individual methods are visible, and in what order? What are the names and contents of their saved profiles?                                | No preselected calculation methods.                                                                             |
| Z3  | Owner (you) | What are the final user-friendly labels, transliterations, group assignments, and distinguishing details for every supported method?                              | Owner review required. Library identifiers stay internal.                                                       |
| Z4  | Owner (you) | Include explanatory guidance? What reviewed text and sources will be supplied?                                                                                    | Factual calculation help is included; owner-authored religious guidance remains outside this implementation. |
| Z5  | App user    | Which base zman, fixed-minute offset, morning/evening solar angle, or temporal-hour formula defines a custom zman? Which day boundaries define its temporal hour? | Explicit formula-editor choices; show a readable formula and example result.                                    |
| Z6  | Owner (you) | Can custom zmanim reference other custom zmanim? What happens when a dependency is edited or deleted?                                                             | Yes. Reject dependency cycles and explain affected formulas before deletion.                                    |
| Z7  | App user    | Follow device location or use a saved place? What location applies to each widget and zman-relative event?                                                        | Explicit choices; show the active place and when device location was obtained.                                  |
| Z8  | Owner (you) | How are place timezone and elevation obtained? How stale can device location be before prompting or falling back to a saved place?                                | Resolved: offline cities/boundaries, manual elevation, 24-hour stale flag, optional background access. |
| Z9  | App user    | Use elevation for supported sunrise/sunset-based calculations? Show seconds or rounded minutes, round which way, and use 12/24-hour time?                         | Existing requested settings; calculation details show the effective elevation policy.                           |
| Z10 | Owner (you) | Which rounding options and +/- convention are offered? Are reminders anchored to the exact instant or the displayed rounded time?                                 | Resolved: down/nearest/up display rounding; alerts use exact instants. |

#### Reminders, widgets, integration, and delivery

| ID  | Decider     | Question / required choice                                                                                                                                                  | Status or proposed direction                                                                                    |
|-----|-------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------|
| I1  | App user    | Which events/zmanim have reminders, at what offsets, with which notification preferences?                                                                                   | Explicit opt-in; show whether required Android notification/alarm access is available.                          |
| I2  | Owner (you) | What fallback and explanation are shown when exact-alarm or notification access is denied? How are unavailable zmanim communicated?                                         | Resolved: explicit approximate fallback; blocked notifications inactive. |
| I3  | Owner (you) | What maximum item counts, sizes, and update behavior do widgets support? What form can the Hebrew date take in Android's notification/status area?                          | Resolved: Glance widgets, up to 10 items; host countdown; day icon and full-date notification. |
| I4  | App user    | Widget item count, location, date format, optional countdown, and event-widget tap action? Show the Hebrew-date notification?                                               | Respect each widget's configuration. Zmanim-widget taps always open zmanim.                                     |
| I5  | Owner (you) | How are Hebrew or zman-relative event occurrences exported to device calendars: date horizon, updates, duplicate prevention, external edits/deletions, and event ownership? | Resolved: app-owned rolling-year exports with stable mappings and conflict review. |
| I6  | App user    | Which available device calendars are visible, and which writable calendar receives an exported or ordinary new event?                                                       | Select calendars, not just accounts; support read-only calendars appropriately.                                 |
| I7  | Owner (you) | Which event-editing and guest-invitation workflows are supported initially, and how are duplicate notifications avoided?                                                    | Resolved: Provider editing and device-editor guest invitation handoff; one reminder owner. |
| I8  | App user    | Which data should be exported/shared, and how should import handle conflicts?                                                                                               | Explicit selection; exclude private events and locations from shared calculation settings unless selected.      |
| I9  | Owner (you) | What is the backup format/version and import-conflict policy? Can settings from the current PHP project be migrated?                                                        | Resolved: versioned JSON, keep/replace/copy preview; no PHP migration. |
| I10 | Owner (you) | Final app name, application ID, separate project location, minimum Android version, and first-release scope?                                                                | Resolved for development: existing repository/name/ID; API 26; full phased scope. |
| I11 | App user    | Light/dark/system appearance and language preferences?                                                                                                                      | Light/dark already requested; offer system appearance as an additional choice.                                  |

### Android implementation plan

- Preferred approach: native Kotlin + Jetpack Compose, calling KosherJava directly through a small Kotlin interface. No Kotlin port is necessary.
- Use Room for structured app data and DataStore for preferences. One shared calculation engine serves all app surfaces.
- Use Jetpack Glance for home-screen widgets. Glance widgets have their own UI components; ordinary Compose screen components cannot simply be reused unchanged.
- Use AlarmManager for time-sensitive reminders with appropriate access checks and a clearly communicated fallback. Use WorkManager for deferred maintenance, not exact-time alerts.
- Recalculate affected occurrences and reminders after reboot, clock/timezone changes, relevant location changes, and edits to calculation settings. Invalidate widget results when their inputs change.
- Use Android Calendar Provider for calendars already connected to the phone; the existing account sync provider handles remote synchronization. Store the app's Hebrew and zman-relative recurrence definitions independently.
- Build incrementally: calculation interface and readable method catalog; selectable zmanim and locations; dual-calendar month view; one widget and reminder prototype; then custom formulas, recurrence, external-calendar integration, and full day/week views.
- Validate DST transitions, Hebrew leap years, sunset rollover, Israel/diaspora differences, elevation, missing solar events, rounding, and dependent custom formulas. Use reviewed expected results; agreement between ports alone is insufficient validation. Reuse this project's representative settings/outputs as migration references.

### Getting started in Android Studio

Android Studio is the preferred editor for this project: it includes Android project setup, Kotlin support, Compose previews, the SDK/device managers, emulator, debugger, and Logcat. VS Code can edit the same files and invoke Gradle, but Android Studio should own the initial setup and Android debugging workflow. Create the app in a separate project/repository, outside this PHP project.

1. Install the current stable [Android Studio](https://developer.android.com/studio) on the development computer. Complete its setup wizard and install the recommended SDK, platform tools, and emulator. Keep the generated project's compatible Gradle/JDK configuration; no separate Kotlin compiler installation is needed.
2. Choose **New Project > Phone and Tablet > Empty Activity** (the Compose template). Choose Kotlin and Kotlin DSL (`build.gradle.kts`) when offered. The template already includes Compose; a separate Compose installation is unnecessary.
3. Enter the app name, a deliberate application ID such as `com.example.zmanim` for a temporary prototype, and a save location outside this repository, for example `~/AndroidStudioProjects/ZmanimAndroid`. Replace the example application ID before publication. Proposed minimum SDK: API 26, pending decision I10. Leave compile/target SDK and build-tool versions at compatible current stable template settings.
4. Finish and let Gradle sync. Run the generated app before adding libraries. In **View > Tool Windows > Device Manager**, create a phone emulator, download its recommended stable system image, select the device, and press **Run**. Alternatively, enable developer options and USB debugging on a physical phone, connect it, authorize the development computer, select it, and run.
5. Initialize Git in the new project and commit the working generated app. Copy this specification into the new project's `docs/app.md`.
6. Add KosherJava to the **app module's** `app/build.gradle.kts`, inside its existing `dependencies` block, then sync. A published Maven Central version verified when this guide was written is shown below. This is a reproducible starting version, not a claim that it is the newest release. Check available releases and matching API docs before choosing a different version; the repository's development README can differ from the published artifact.

   ```kotlin
   dependencies {
       implementation("com.kosherjava:zmanim:3.0.0")
   }
   ```

   Keep the other generated dependencies. Ensure `mavenCentral()` is present in dependency repositories in `settings.gradle.kts`; the standard template normally includes it. See the [published artifact](https://central.sonatype.com/artifact/com.kosherjava/zmanim) and [upstream project](https://github.com/KosherJava/zmanim).

7. First milestone: one screen calculates a Hebrew date and sunrise/sunset for a fixed date and saved place, with an explicit timezone and readable labels. These fixed prototype methods are development checks, not production defaults. Add the method picker and persist explicit choices before treating it as the user-facing zmanim screen.
8. Keep code in a few clear packages initially: calculation/domain, data, UI, widgets, and reminders. Avoid a large multi-module setup until needed. Add Room, DataStore, Glance, and scheduling features as their corresponding milestones begin.
9. Use Compose Preview for quick layout checks, **Run/Debug** for actual behavior, and **Logcat** for crashes. Test Hebrew/English mixed text and different screen/font sizes. Test widgets, denied permissions, reboot, and reminders on a real phone as well as the emulator.

Official setup references: [create a project](https://developer.android.com/studio/projects/create-project), [manage emulators](https://developer.android.com/studio/run/managing-avds), [run on a phone](https://developer.android.com/studio/run/device), and [Java/Gradle JDK selection](https://developer.android.com/build/jdks).
