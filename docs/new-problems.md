# Implementation checklist: calendar, zmanim, locations, events, and widgets

This replaces the original problem notes with the agreed behavior and the plan
for implementing and verifying every item. The implementation pass is recorded
below. Checked items have implementation and automated/emulator evidence;
unchecked items identify remaining source validation or physical-device review.
Agreement on a decision alone does not mean the work is complete.

## Implementation pass — 4 November 2026

Implemented shared app location, GPS timezone recovery, reusable current-location
snapshots, normalized/ranked city search, reviewed locality overrides, optional
cached terrain lookup, independent explicit elevation methods, profile display
names, conditional calculation options, per-method definitions, offline Help and
license texts, calendar direction/layout/theme changes, calendar/time pickers,
automatic clock-change intervals, shared occurrence formatting, event basics,
direct profile reminders and genuine ringing alarms, date-widget variants,
scrolling per-row native countdowns, learning rows, and centralized in-app launches.
Minute rounding is hidden while Show seconds is enabled, retaining its saved value.
Current-format backups are version 2; obsolete test backup formats are rejected.

The city review covers all 127 PS-classified records in the packaged snapshot.
Ten explicit stable-ID corrections were matched to CBS 2023 names and coordinates;
nearby Palestinian places remain unchanged. See [locality-audit.csv](locality-audit.csv)
and the bundled `locality-corrections.json` for provenance. An erroneous Betar
Illit alias on Battir is excluded from search. The city snapshot hash is bundled.

Learning uses pinned Hebcal 6.13.1 reference tables for 2020–2040, with civil-day
rollover. Dirshu Halacha is a finite third-cycle table ending 31 August 2027.
Bavli, Vilna Yerushalmi, Mishna, Perek, Nach, Rambam one/three, Dirshu Amud,
Chafetz Chaim, Tanya and Dirshu Halacha are exposed with source/cycle metadata.
Oz V’hadar has its own published pagination: 4,188 daily references from Kollel
Iyun Hadaf’s cycles 11–12, 14 November 2022 through 2 May 2034. Both cycles were
checked for consecutive dates, matching pagination and cycle-boundary continuity.
Sample readings are checked against Sefaria and source-cycle anchors; see
[learning-independent-references.json](learning-independent-references.json).
Oraysa is a finite official Yevamot-calendar table covering 23 November 2025
through 7 November 2026, including daily and weekend review references. All 250
weekday readings were checked against the positioned PDF cells. An unofficial
calendar was rejected because its current amud differed from the publisher's.
Later Oraysa dates show Unavailable until a new publisher table is reviewed.
Likutei Halachos and Dirshu Chevras Shas remain unexposed until
an identified source table/cycle has been validated. They were not substituted
with similarly named schedules. Dirshu’s public Chevras Shas download currently
links to 5783 tables; its rules skip bein hazmanim, so an uninterrupted amud
formula would be incorrect. A current complete table is still needed. Likutei
Halachos requires a validated publisher pagination/cycle table, not only isolated
weekly bulletin entries.

User clarification: skip non-Dirshu Amud Yomi, Halacha Yomi, Maharan,
Yerushalmi Meshulav and Dirshu Mussar. These five are outside this pass.

The 24 px small-month status-icon prototype was inspected. Its 5 px month glyphs
are too small for dependable reading, so the shipped icon keeps a clear day;
the drawer entry shows the full date and expandable date/location hierarchy.
The prototype is reproducible with `tools/PreviewHebrewDateIcon.java`.

Physical-phone location/keyboard/launch reports, locked-device/Doze/reboot alarm
behavior, and actual launcher resizing across vendors still require physical
verification. Optional elevation-service failure/cache cases need further device
coverage. Method wording remains an initial terminology iteration. These are not
closed by the emulator results below.

## Scope and settled decisions

- Keep profiles and make their purpose, active selection, settings, and effects clear.
- Bundle Help inside the app, usable offline, without a website.
- Keep the current city and timezone data sources. Put source acknowledgements and
  library notices under Help → About → Licenses & data sources.
- Existing installations contain test data only. Adopt fresh defaults and model
  changes without legacy-data-preservation or backward-compatibility work. Test
  installations can be reset when necessary. Current event/profile relationships
  and current-format backup/import must still be correct.
- Use HebDate, installed by the user on the local emulator, for direct calendar
  layout comparisons. Cheshvan and six-row Tishrei references were captured and compared
  at the same emulator width/font scale; see `docs/qa`.
- Location belongs to the app, independently of profiles. Widgets use app location.
- Hiding a zman group suppresses its notifications and alarms.
- Add real alarms as an alternative to notifications directly for zmanim.
- Mixed Hebrew/English zman terminology is an initial iteration to review visually.
  Allow profile-specific display names for existing zmanim.
- Remove event-editor daylight-saving policy controls. Handle clock changes
  automatically according to the event's scheduled local interval.
- App name and launcher-icon design are parked. Their single deferred item stays
  in [todo.md](todo.md), outside this implementation scope.

## Implementation order and dependencies

1. Establish profile, shared-label, location, and event model changes required by
   later work; adopt fresh defaults without old-format migrations.
2. Fix timezone resolution, locality data, and elevation calculations. All screens,
   events, reminders, and widgets must use the same effective inputs.
3. Fix navigation and launch handling, then calendar layout, direction, and colors.
4. Rebuild event entry with proper pickers, conditional fields, readable previews,
   and automatic clock-change behavior.
5. Expose profile reminders directly and implement real alarm delivery.
6. Finish date notifications, date-widget variants, scrolling/countdown widgets,
   and the learning overview.
7. Finish bundled Help and notices alongside each feature, then run shared
   verification. Add learning schedules in validated batches without holding up
   unrelated correctness fixes.

Source paths below are relative to app/src/main/java/com/example/zmanim unless
another root is specified.

## Profiles, Help, and notices

- [x] **P01 — Keep profiles and explain how to use them.**
  - Decision: a profile saves selected zmanim, ordering, group visibility, custom
    calculations, display names, calculation options, and reminders. Location and
    general display/calendar preferences are separate.
  - Plan: make the active profile visible; provide direct access to its settings
    and zman selection. Explain creating, switching, duplicating, and deleting.
  - Plan: explain that switching changes the main zmanim view and profile reminders.
    A zman-based event keeps its chosen profile; editing that profile affects its
    dependent events. Zmanim widgets default to the active profile; a deliberate
    advanced profile override can remain.
  - Plan: select a new zman-event's profile automatically, with alternate selection
    under advanced controls. Keep dependency protection for profiles/custom methods
    referenced by events or widgets.
  - Touchpoints: domain/Models.kt, ui/ProfileEditors.kt, ui/ZmanimScreen.kt,
    ui/SettingsScreen.kt, ui/EventEditor.kt, data/AppRepository.kt, widget setup.
  - Verify: two profiles with different methods/options/names/reminders stay
    independent; switching updates the expected surfaces; events retain their
    profile; duplication copies names and remaps custom references correctly.

- [x] **P02 — Add fully in-app, offline Help.**
  - Decision: Help has no dependency on a hosted website.
  - Plan: add a main-menu Help destination with bundled English/Hebrew topics:
    getting started, profiles, locations/timezones/elevation, zmanim/calculation
    options, events/recurrence, reminders/alarms, and widgets.
  - Plan: add contextual explanations and links into relevant topics, including
    concrete profile/reminder examples. Explain hidden-group suppression and the
    difference between permissions, configured reminders, and real alarms.
  - Plan: write Help alongside features so it describes their final behavior.
  - Verify: all topics open in airplane mode; contextual links and Back work;
    both interface directions and large text remain usable.

- [x] **P03 — Move source and library acknowledgements to their proper place.**
  - Answer: “Location credits” means source acknowledgements, not money, tokens,
    quotas, or a paid feature. Retain the current sources' required notices.
  - Decision: keep GeoNames and current timezone boundaries; replacing them solely
    to remove a visible credits control is no longer planned.
  - Plan: remove that control from location management/settings tabs. Put source
    links, license texts, dataset versions, transformations, and library notices
    under Help → About → Licenses & data sources.
  - Plan: audit actual packaged dependencies/assets, including KosherJava,
    GeoNames, timezone boundaries, and Hebrew icon resources. Include notices for
    new elevation/learning sources when added.
  - Verify: complete notices are available offline; regular location/settings
    screens have no “credits” terminology or attribution clutter.

## Location management and calculation correctness

- [x] **L01 — Make app location accessible wherever it matters.**
  - Decision: location serves calendar sunset behavior, zmanim, relevant events,
    widgets, and date notifications. It is not owned by a profile.
  - Plan: expose general location management in settings and shortcuts from calendar
    and zmanim. Clearly show the active saved/follow-device location.
  - Plan: distinguish a saved snapshot from following the device; retain progress,
    permission continuation, recovery, last-fix time, and accuracy. Keep deliberate
    event-specific calculation locations independent.
  - Touchpoints: ui/LocationEditors.kt, ui/SettingsScreen.kt, MainActivity.kt,
    ui/AppViewModel.kt, platform/DeviceLocation.kt.
  - Verify: setup works from either main screen; app-location changes update their
    dependents without overwriting event-specific places.

- [ ] **L02 — Fix ambiguous timezone handling for current location.**
  - Finding: at screenshot coordinates 31.8645075, 35.1447088, bundled polygons
    return Asia/Hebron and Asia/Jerusalem. Permission/coordinate acquisition worked;
    rejecting multiple matches caused the error.
  - Plan: retain acquired coordinates even when timezone choice needs input.
    Auto-select the device's timezone ID if it matches a candidate; matching only
    the current UTC offset is insufficient.
  - Plan: otherwise ask the user among understandable zone names. Offer manual
    selection for no matches. Always allow changing the zone afterward, even
    following automatic selection.
  - Plan: persist the choice for the saved location, without applying it
    indiscriminately after travel to another region.
  - Touchpoints: data/OfflinePlaces.kt, platform/DeviceLocation.kt,
    ui/LocationEditors.kt, ui/AppViewModel.kt, domain/Models.kt.
  - Verify: screenshot coordinate, unique-zone/no-match results, unmatched device
    zone, manual correction, and travel. Recheck on the physical phone.

- [x] **L03 — Prevent duplicate “Current location” saved places.**
  - Finding: each successful snapshot currently gets a fresh ID and is appended.
  - Plan: update/reuse the current snapshot on repeated requests and guard against
    simultaneous acquisition. Use the same policy when turning following off.
  - Plan: preserve independently named saved places and their event bindings.
  - Verify: repeated requests and follow-to-snapshot changes leave one current
    snapshot, with a clear active selection and independent named places.

- [x] **L04 — Make city search tolerate spelling and punctuation variants.**
  - Finding: “Givat Zeev” and “גבעת זאב” exist, but basic substring matching does
    not reliably match “Givat Zev” or apostrophe variants.
  - Plan: retain useful source aliases; normalize case, apostrophes, whitespace,
    diacritics, and Hebrew marks; add reviewed aliases where data is incomplete.
  - Plan: rank exact/prefix matches before broader matches, search across countries,
    and distinguish namesake localities.
  - Touchpoints: tools/prepare_places.py, data/OfflinePlaces.kt, city assets.
  - Verify: Givat Zev, Givat Zeev, Giv'at Ze'ev, גבעת זאב, other transliterations,
    and similarly named cities return useful ranked results.

- [ ] **L05 — Show search results while the keyboard is open.**
  - Plan: use a focused search screen/sheet or dedicated layout with the query
    above a scrollable results list. Keep results out of the bottom of a long
    saved-place form; expose loading, empty/error states, and manual entry.
  - Touchpoints: ui/LocationEditors.kt, ui/Components.kt.
  - Verify: search/selection work without dismissing the keyboard on the emulator
    and physical phone, including compact viewports and large text.

- [x] **L06 — Correct affected localities systematically.**
  - Finding: bundled Givat Zeev and Beit El have PS/Asia/Hebron; Efrata has
    PS/Asia/Jerusalem; Ariel already has IL/Asia/Jerusalem. Display classification,
    civil timezone, and holiday schedule therefore need separate checks.
  - Decision: for app display/calculation defaults, apply reviewed Israeli locality
    classification, Asia/Jerusalem civil-time rules where applicable, and Israel
    holiday/parsha defaults for affected Israeli localities.
  - Plan: compare relevant source records against an authoritative Israeli locality
    list using coordinates and aliases. Build a reviewed correction table keyed
    by stable locality IDs and record provenance.
  - Plan: separate display country, timezone, and holiday-schedule defaults in the
    model. Apply corrections to search/selection and retain them across dataset
    refreshes. Allow manual correction. Do not blanket-convert all PS records.
  - Plan: replace initializeSchedule's timezone-only inference with reviewed
    locality/default resolution. Keep Israel/diaspora editable and make uncertain
    identification actionable. GPS ambiguity follows L02, not a nearest-city guess.
  - Touchpoints: tools/prepare_places.py, data/OfflinePlaces.kt,
    data/AppRepository.kt, domain/Models.kt, ui/LocationEditors.kt.
  - Verify: audit the full affected list; cover corrected/unaffected cities,
    namesakes, upstream refreshes, manual choices, DST-period civil times, and
    Israel/diaspora holiday/parsha differences.

- [ ] **L07 — Add optional free elevation lookup and clear height information.**
  - Decision: the location owns height; profiles own the policy for using it.
  - Plan: add “Look up elevation” through an evaluated free service such as Open
    Topo Data. Confirm coverage, rate limits, datum, and notices before selecting
    the service; add internet permission for the optional lookup.
  - Plan: cache coordinates/result/source/dataset/units, invalidate on coordinate
    changes, and retain manual entry and offline calculation. Do not use ordinary
    GPS ellipsoid altitude as mean-sea-level terrain height without conversion.
  - Plan: distinguish missing height, zero, and unsupported negative height; never
    silently clamp unsupported inputs and label the result adjusted.
  - Verify: successful/offline/error/rate-limit/no-data lookup, cache reuse,
    coordinate edits, manual overrides, sea-level and below-sea-level locations.

- [x] **C01 — Separate explicit adjusted sunrise/sunset from the profile toggle.**
  - Finding: height is currently zeroed when useElevation is off, making explicit
    elevation sunrise/sunset behave as sea-level methods too.
  - Decision: pass supported location height, when available, to explicit adjusted
    sunrise/sunset independently of the toggle. Explicit sea-level methods remain
    at sea level. Other supported sunrise/sunset-derived zmanim follow the profile
    toggle, which defaults to off.
  - Plan: classify actual pinned-library method behavior: sea level, explicitly
    adjusted, follows profile policy, or unaffected. Follow custom/mixed-boundary
    dependencies rather than relying on the current broad sensitivity flag.
  - Plan: clearly label both sunrise/sunset variants; rename/explain the toggle as
    “Use elevation for other supported zmanim.” Explain effective height and the
    distinction from terrain/horizon obstructions. Missing height for an explicit
    adjusted method must have an actionable state.
  - Plan: inspect solar-angle, mixed-boundary, custom, and candle-lighting methods
    individually. Keep sea-level candle-lighting behavior explicit and the
    date-rollover sunset policy independent of profile selection.
  - Touchpoints: domain/CalculationEngine.kt, domain/MethodCatalog.kt,
    tools/generate_catalog.py, docs/method-catalog.csv, ui/ProfileEditors.kt,
    ui/MethodHelp.kt, all calculation consumers.
  - Verify: compare against pinned-library calls for toggle on/off and zero,
    positive, missing, negative height, direct/derived/custom methods. Equivalent
    queries agree in app, events, reminders, and widgets.

- [x] **C02 — Explain Ateret Torah and hide its offset when unused.**
  - Answer: this minutes-after-sunset boundary affects the Ateret Torah family,
    including its temporal hour and derived Shema, tefillah, Mincha, and plag.
    It is separate from candle lighting.
  - Plan: show its field only when used by selected methods or dependencies,
    including custom formulas, events, holiday-nightfall choices, or reminders.
    Checking only visible names is insufficient.
  - Plan: retain its configured value when hidden; explain scope and units when
    shown. Keep explicit configuration rather than silently choosing an offset.
  - Verify: direct/indirect usage, events using unselected methods, removing the
    final dependency, and missing-offset recovery.

## Labels, custom display names, and explanations

- [ ] **T01 — Make labels consistent and handle mixed text directions.**
  - Finding: case-sensitive replacement misses “Temporal Hour”; names such as
    Geonim are partly untranslated.
  - Decision: Hebrew label preference changes Jewish terminology/names; descriptors
    such as “below the horizon” remain English within labels. Review this first
    iteration visually before treating its wording as final.
  - Plan: replace ad hoc string substitutions with method-ID terminology for
    names, opinions, numbers, units, and qualifiers. Examples:
    שעה זמנית · הגר״א; צאת הכוכבים · גאונים · 8.5° below the horizon.
  - Plan: deliberately isolate/layout Hebrew, English, degrees, punctuation,
    offsets, and clock times; never manually reverse strings. Interface language
    remains independent of label language.
  - Plan: share formatting across timeline, picker, formula references, event
    calculation choices, widgets, notifications, and alarms.
  - Touchpoints: ui/Components.kt, domain/MethodCatalog.kt, catalog generator,
    domain/TimeFormatting.kt, widget/reminder presentation.
  - Verify: both interface languages crossed with both label preferences; decimal
    angles, signed offsets, parentheses, mixed names, and narrow rows.

- [x] **T02 — Rename existing zmanim within each profile.**
  - Decision: a per-profile display name overrides the normal label wherever that
    profile supplies the zman, without changing its calculation.
  - Plan: store overrides by stable method ID; add Rename/Reset name actions.
    Retain the original name/definition in details and search both names.
  - Plan: use effective names in widgets, reminders/alarms, and event calculation
    controls, while preserving each event's own title. Include overrides in profile
    duplication and current-format backup/import.
  - Verify: two profiles can name the same method differently without affecting
    times, formulas, selection, or reminder bindings; reset and mixed names work.

- [x] **T03 — Explain every calculation method usefully.**
  - Decision: use pinned KosherJava docblocks as source material, then write reviewed
    in-app prose rather than displaying raw Java documentation.
  - Plan: extend metadata with source method, definition, boundaries, units,
    opinion/source, applicability, dependencies, and relevant options. Review
    method-specific exceptions rather than using group boilerplate/name parsing.
  - Plan: show a short explanation, followed by expandable effective boundaries,
    offsets, fixed/temporal minutes, elevation, half-day policy, timezone, and
    unavailable reasons. Explain custom formulas too.
  - Plan: reuse help in selection, zman details, and event calculation controls;
    bundle it offline and tie it to the pinned library version.
  - Touchpoints: tools/generate_catalog.py, domain/MethodCatalog.kt,
    domain/CalculationEngine.kt, ui/MethodHelp.kt, ui/ProfileEditors.kt,
    ui/ZmanimScreen.kt, Help resources.
  - Verify: all exposed methods have reviewed explanations; special variants and
    profile-option changes are reflected accurately.

## Calendar layout, navigation, and display

- [x] **U01 — Fix month-cell text through direct HebDate comparison.**
  - History: unrestricted wrapping split words; forcing one line truncated them.
    Simply reverting either change is insufficient.
  - Plan: compare the same month/long labels in both apps on the same emulator
    at equivalent width/font scale. Measure cell width, padding, typography,
    spacing, and placement.
  - Plan: allocate separate space for dates, built-in holiday/parsha labels, and
    events. Use measured word-boundary wrapping and reviewed abbreviations or
    deliberate line breaks for built-in labels; keep Hebrew words intact.
    Retain primary/secondary dates and avoid globally shrinking all text.
  - Plan: allow compact event-title truncation/overflow as in the reference.
    Keep full selected-day details in addition to fixing cells themselves.
  - Touchpoints: ui/CalendarScreen.kt, domain/CalendarEngine.kt, typography.
  - Verify: long labels, five/six-row months, Gregorian/Hebrew, light/dark,
    compact/standard widths, and larger fonts. Compare rendered grids with
    HebDate; an off-cell detail view alone does not satisfy this item.

- [x] **U02 — Make bottom navigation selection obvious in dark mode.**
  - Finding: selection logic exists but the dark-mode indicator is hard to see.
  - Plan: use one authoritative view state for rendering and selection; strengthen
    indicator, icon, and label contrast.
  - Touchpoints: MainActivity.kt, ui/theme/Theme.kt, ui/theme/Color.kt.
  - Verify: all five destinations after switching, settings, recreation, and
    widget launches, in both themes.

- [x] **U03 — Give Settings its own navigation context.**
  - Plan: show Settings as a standalone destination with Back and no calendar
    bottom bar. Back returns to the invoking screen and state.
  - Plan: prevent restored calendar navigation from restoring Settings as calendar.
    Share launch handling with W07.
  - Touchpoints: MainActivity.kt and navigation state.
  - Verify: calendar/zmanim → settings → back, subsequent tab switches,
    recreation, and widget launches with Settings last open.

- [x] **U04 — Automatically choose calendar direction by calendar type.**
  - Decision: exactly three choices: Automatic (Gregorian LTR, Hebrew RTL),
    Always left to right, Always right to left. Default Automatic; no need to
    preserve old booleans.
  - Plan: use the same resolved direction for grid order and swipes, independent
    of interface/label language. Retain separate week-start and swipe-axis choices.
  - Touchpoints: domain/Models.kt, ui/SettingsScreen.kt, ui/CalendarScreen.kt.
  - Verify: both calendars, all three choices, both interface languages,
    Sunday/Monday starts, and both swipe-axis configurations.

- [x] **U05 — Spell out שבת in the Saturday header.**
  - Decision: initial wording is שבת rather than narrow ש׳; ש״ק was also acceptable.
  - Plan: use a controlled Hebrew weekday formatter and reuse where appropriate.
  - Verify: no splitting/cropping in narrow columns, both directions, and widgets.

- [x] **U06 — Show special-day color controls only in effective light mode.**
  - Decision: a dedicated dark special-day palette is outside this pass.
  - Plan: hide toggle/category controls in effective dark mode, including System
    mode. Apply special-day tints only in light mode; retain the chosen preference
    during temporary theme changes.
  - Touchpoints: ui/SettingsScreen.kt, ui/CalendarScreen.kt, theme resolution.
  - Verify: Light/Dark/System transitions, matching controls/rendering, and
    readable selected/today indicators.

- [x] **U07 — Distinguish the next zman from timeline day headings.**
  - Finding: their dark-theme surfaces currently use the same color.
  - Plan: neutral heading surface/divider; distinct next-zman accent, border/marker,
    and text label. Preserve past-row dimming.
  - Touchpoints: ui/ZmanimScreen.kt, theme colors.
  - Verify: both themes and large text, including a distinction beyond color.

- [x] **U08 — Count down to all upcoming zmanim, including seconds.**
  - Answer: current “Show time remaining” affects only the next zman and omits
    seconds for targets over an hour away.
  - Plan: rename to “Show countdowns”; show it on all visible future timed rows.
    Include seconds at every distance and days when needed; use a clear passed
    state. Duration results remain durations.
  - Plan: reuse the one-second UI ticker without recalculating astronomy each
    second. Keep countdown seconds separate from clock-time seconds preference.
  - Touchpoints: ui/ZmanimScreen.kt, ui/AppViewModel.kt, domain/TimeFormatting.kt.
  - Verify: under/over an hour, later days, target crossing, past/duration rows,
    countdown-off, and smooth scrolling/ticking.

## Event creation, recurrence, and display

- [x] **E01 — Simplify the event editor.**
  - Plan: organize title, date, time, repeat, optional details/reminders. Reveal
    advanced timezone, calculation place/profile, guests/export only when useful.
  - Plan: use a proper time picker respecting 12/24-hour preference with optional
    direct input. Preserve dates/times supplied by tapped calendar slots.
  - Plan: local events work without calendar permission; explain invitations
    when guests/device-calendar integration are actually involved.
  - Touchpoints: ui/EventEditor.kt, ui/Components.kt, MainActivity.kt.
  - Verify: timed/all-day/zman/Hebrew/recurring create/edit flows have relevant
    controls and preserve drafts.

- [x] **E02 — Only show recurrence calendar for repeating events.**
  - Plan: put Hebrew/Gregorian repeat-calendar choice in the enabled Repeat
    section; show interval/end/missing-day/Adar controls only when applicable.
  - Plan: separate input calendar mode and Hebrew event-day semantics from
    recurrence so hiding this choice preserves non-recurring Hebrew date entry,
    previous-evening behavior, and Hebrew all-day boundaries.
  - Touchpoints: domain/Models.kt, domain/OccurrenceEngine.kt, ui/EventEditor.kt.
  - Verify: repeat on/off, monthly/yearly missing days, ordinary/leap Adar,
    and non-recurring Hebrew timed/all-day events.

- [x] **E03 — Provide real Gregorian and Hebrew date pickers.**
  - Decision: open the chosen calendar's picker, not the old Go to date dropdown/
    text-field form.
  - Plan: build reusable Gregorian/Hebrew month pickers with year/month navigation,
    correct lengths, leap years, and Adar identity. Switching modes preserves
    the represented date; initialize from the calling flow.
  - Plan: reuse for event dates, repeat end dates, and Go to date.
  - Touchpoints: ui/Onboarding.kt (GoToDialog), ui/EventEditor.kt,
    domain/CalendarEngine.kt.
  - Verify: conversion both ways, leap years/short months, initial selection,
    cancellation, and edit/save round trips.

- [x] **E04 — Handle clock changes automatically, without policy controls.**
  - Decision: events occupy their scheduled local start/end interval. A jump over
    the whole interval skips it; a jump over the start leaves any remaining
    interval active; a backward jump can enter the interval again.
  - Plan: remove gap/overlap controls. Resolve fixed-time local intervals against
    timezone transitions rather than shifting nonexistent starts or always adding
    a fixed elapsed duration.
  - Plan: keep one logical rule, identify actual interval segments where needed
    for display/previews/reminders/export, and resolve repeated start instants
    consistently. Prevent accidental delivery/reconciliation duplicates while
    retaining the defined repeated-clock behavior. Zman events use exact instants.
  - Touchpoints: domain/Models.kt, domain/EventStart.kt,
    domain/OccurrenceEngine.kt, platform/CalendarGateway.kt,
    platform/ReminderScheduler.kt, schedule rendering.
  - Verify: fully/partly skipped and repeated intervals, events spanning a
    transition/midnight, recurrence, reminders, and device-calendar export.
    This adds no clock-change configuration burden to event creation.

- [x] **E05 — Use human-friendly occurrence previews and details.**
  - Plan: replace raw ISO/zoned timestamps with shared localized date/time/range
    formatting, 12/24-hour preference, Hebrew dates where useful, “All day,” and
    relevant timezone context.
  - Plan: distinguish incomplete drafts, unavailable calculations, and valid rules
    without an occurrence in the preview window. Preview current draft values.
    Reuse formatting in event details and affected-event settings previews.
  - Touchpoints: ui/EventEditor.kt, ui/SettingsScreen.kt,
    domain/TimeFormatting.kt, ui/CalendarScreen.kt.
  - Verify: repeating/one-off/all-day/sunset/other-zone/clock-change previews
    and incomplete drafts show useful text, not debug timestamps.

- [x] **E06 — Explain “Calendar destination” and remove one-item dropdowns.**
  - Answer: this selects app storage or a writable Android calendar. Only app
    storage appears when no writable calendars are available.
  - Plan: rename to “Save to.” Show “Saved in this app” and optional calendar
    connection when that is the only destination; otherwise list names/accounts
    with clear permission/no-calendar states.
  - Plan: explain direct provider storage versus app-owned rules exported as
    occurrences; reveal reminder ownership only when relevant and prevent
    app/calendar duplicate alerts.
  - Touchpoints: ui/EventEditor.kt, platform/CalendarGateway.kt,
    ui/SettingsScreen.kt.
  - Verify: absent access, no/one/multiple writable calendars, read-only calendars,
    local saves, and managed recurring exports.

- [x] **E07 — Show optional event basics inside month cells.**
  - Decision: add an event-basics setting instead of always showing only a count.
  - Plan: show compact title/time rows with “+N more” overflow when enabled and
    compact count/dot when disabled. Coordinate with U01's space budget.
  - Plan: include local/enabled device occurrences, count multi-day overlaps
    correctly, and avoid duplicating managed exports.
  - Touchpoints: domain/Models.kt, ui/SettingsScreen.kt, ui/CalendarScreen.kt.
  - Implementation: day overlap uses actual intervals, including multi-day provider all-day events.
  - Verify: empty/busy days, long titles, all-day/multi-day events, exports,
    both calendars, compact sizes, and the setting on/off.

## Notifications, real alarms, and the status-bar date

- [x] **R01 — Configure zman reminders directly, without events.**
  - Answer: existing setup is in Profile editor → Display order and reminders;
    0 means at the zman. Exact-alarm permission does not create a reminder.
  - Plan: add bell actions on rows/details and a direct active-profile reminder
    screen. Replace comma-separated entry with add/edit/remove controls and
    understandable at-time/before-time choices.
  - Plan: show next delivery, active profile, enabled state, and notification/
    precise-alarm access separately; offer permission recovery in this flow.
  - Decision: hidden groups suppress notifications and alarms. Explain this
    beside group visibility and affected reminders. Profile/global switches,
    selection, and method applicability still control delivery.
  - Touchpoints: ui/ProfileEditors.kt, ui/ZmanimScreen.kt, ui/SettingsScreen.kt,
    platform/ReminderScheduler.kt, domain/Models.kt.
  - Verify: multiple/zero offsets, hidden groups, deselection/profile switching,
    unavailable/non-applicable methods, blocked permissions, exact delivery,
    and explicitly accepted approximate notification delivery.

- [ ] **R02 — Add genuine alarms for zman reminders.**
  - Decision: offer Notification or Alarm per reminder; alarms ring with
    sound/vibration, snooze, and dismiss.
  - Plan: add mode/sound/vibration/snooze fields, exact scheduling, ringing-service
    lifecycle, alarm channel/notification, and lock-screen/alarm UI. Respect
    full-screen access and keep stop/snooze accessible when it is unavailable.
  - Plan: use alarm-appropriate audio and reconcile pending/snoozed alarms after
    profile, group, location, method, permission, time/timezone, and reboot changes.
    Stop disabled/deleted active alarms cleanly.
  - Plan: surface missing precise access rather than silently substituting
    an approximate notification for a configured real alarm.
  - Touchpoints: domain/Models.kt, platform/ReminderScheduler.kt, new alarm
    service/UI, ZmanimApplication.kt, AndroidManifest.xml, reminder editor.
  - Verify: actual ringing, selected sound/vibration, snooze/dismiss, locked/
    unlocked states, process death/reboot, Doze, revoked access, simultaneous
    alarms, suppression/cancellation, and the physical phone.

- [ ] **R03 — Offer an attractive optional Hebrew status-bar date.**
  - Reference: the user's second pasted image shows Hebrew day beside the clock.
  - Decision: Android links the icon to a drawer entry; users choose whether to
    enable both.
  - Plan: improve day glyphs and the compact silent drawer notification, showing
    full Hebrew date with clear day/month/year hierarchy. Open the intended
    calendar destination; disable removes icon and notification.
  - Plan: prototype a small month below the day in the monochrome icon and inspect
    actual-size legibility. Full readable month text cannot be guaranteed in that
    space; widgets/notification content can show it.
  - Plan: use app location and chosen sunset/midnight rollover; handle missing
    location/sunset, boot/time changes, and disabled channels.
  - Touchpoints: platform/ReminderScheduler.kt, widget boundary scheduling,
    ui/SettingsScreen.kt, res/drawable/hebrew_day_*.xml, icon tooling.
  - Verify: all 1–30 glyphs including טו/טז, month/year transitions, themes,
    phone/emulator status bars, permissions/channels, and the off state.

## Widgets

- [ ] **W01 — Separate Hebrew/Gregorian date widgets, including 1×1.**
  - Reference: first pasted image contains weekday, large Hebrew day, month, and
    Hebrew year. Match that information using our own styling.
  - Decision: separate calendar widget entries replace required calendar-type
    selection. Support actual 1×1 and 2×1 sizes through resizing or separate
    providers as launcher behavior requires.
  - Plan: define minimum/target cells and useful launcher previews; work from app
    defaults without mandatory setup. Optional reconfiguration can select the
    calendar/view or zmanim opening destination.
  - Touchpoints: widgets/WidgetPresentation.kt, widgets/ZmanimWidgets.kt,
    widgets/WidgetConfigurationActivity.kt, AndroidManifest.xml, res/xml/widget_*.
  - Verify: actual 1×1/2×1 placement/resizing/previews, long months, large text,
    both themes, and all opening destinations.

- [x] **W02 — Make 2×1 date widgets consistent and complementary.**
  - Decision: both show weekday. Hebrew-primary shows Hebrew day/month/year,
    Gregorian year after weekday, and Gregorian dd/mm. Gregorian-primary shows
    its main date and a smaller Hebrew date rather than repeating Gregorian
    dd/mm at the bottom.
  - Plan: share primary/weekday/secondary hierarchy and bidi handling with
    meaningful compact variants.
  - Verify: exact information above, narrow/tall/wide sizes, Hebrew leap months,
    label languages/themes, and midnight/sunset transitions.

- [x] **W03 — Follow app location in every widget.**
  - Decision: remove per-widget saved/follow-device location selection.
  - Plan: resolve timezone/sunset/calculation inputs from app location. If required
    setup is missing, link directly to “Set app location” and return/update
    afterward. Gregorian-only dates may use device timezone meanwhile.
  - Plan: refresh/reschedule on app location/timezone/height changes. Zmanim
    widgets default to active profile; profile choice is separate from location.
  - Touchpoints: domain/Models.kt, widgets/WidgetConfigurationActivity.kt,
    widgets/WidgetPresentation.kt, widgets/ZmanimWidgets.kt, launch handling.
  - Verify: all widgets follow saved/follow-device changes; setup return works;
    missing/stale location and unavailable sunset are understandable.

- [x] **W04 — Scroll the next zman with the remaining widget rows.**
  - Finding: the first row is deliberately outside the scrolling list.
  - Plan: put all zman rows in one list, preserving a next-zman marker without
    pinning its card and keeping compact-height layouts usable.
  - Touchpoints: widgets/WidgetPresentation.kt.
  - Verify: first row scrolls offscreen, later rows are reachable, and the next
    marker updates as targets pass.

- [x] **W05 — Count down to every widget zman, including seconds.**
  - Plan: rename the option for all upcoming rows; use a native RemoteViews
    chronometer per eligible row. Adapt compact layouts/visible row count without
    per-second background workers or full-widget updates.
  - Plan: refresh at passed targets and rebuild bases on time/timezone changes
    or reboot; retain actual clock time.
  - Touchpoints: widgets/WidgetPresentation.kt, widgets/ZmanimWidgets.kt,
    res/layout/widget_countdown.xml, domain/TimeFormatting.kt.
  - Verify: all rows tick with seconds beyond an hour; scrolling/resizing,
    target removal, and countdown-off behave correctly.

- [ ] **W06 — Expand the parsha/daf overview with daily learning.**
  - Decision: retain parsha/Daf Yomi but replace oversized sparse cards with
    selectable/reorderable learning rows that use larger widget sizes well.
  - Plan: add optional holiday, Rosh Chodesh, and Omer rows. Learning selection
    stays independent of zman profiles; parsha follows Israel/diaspora settings.
  - Plan: define providers with source/version, edition/cycle identity, valid
    dates, exceptions/review days, labels, rollover, and no-learning states.
    Do not blindly apply date-widget sunset rollover to every learning cycle.
  - Plan: prefer verified offline algorithms or bundled versioned tables; cache
    fetched data if needed and expose unavailable/stale results. Validate each
    source/cycle before exposing its option.
  - First batch: retain Bavli and verify pinned-library Yerushalmi support; add
    Mishna Yomi, Perek Yomi, Nach Yomi, Rambam one/three chapters, Dirshu Amud,
    Chafetz Chaim, and Tanya from verified cycle sources.
  - Remaining requested programs stay in the list below until sourced/validated.
    Similar edition/program names must not be treated as interchangeable.
  - Touchpoints: domain/CalendarEngine.kt, new learning providers/data,
    widgets/WidgetPresentation.kt, widgets/WidgetConfigurationActivity.kt,
    Help/notices and widget metadata.
  - Verify: reference dates, skip/review days, cycle restarts, relevant Hebrew
    leap dates, offline/source failure, selection/order, and readable compact/
    large layouts with useful content density.

  Each requested schedule remains unchecked until source, cycle, implementation,
  and widget output are verified:

  - [x] Daf Yomi (Bavli; retain and verify).
  - [x] Yerushalmi Vilna.
  - [x] Yerushalmi Oz V'hadar (published cycles 11–12; finite coverage above).
  - [ ] ~~Yerushalmi Meshulav.~~ — skipped at the user’s request.
  - [ ] ~~Amud Yomi (identify the specific non-Dirshu cycle).~~ — skipped at the user’s request.
  - [x] Amud Yomi Dirshu.
  - [x] Oraysa (finite official Yevamot calendar; expires 7 November 2026).
  - [x] Nach Yomi.
  - [x] Mishna Yomi.
  - [x] Mishna Perek Yomi.
  - [ ] ~~Halacha Yomi (identify the specific program).~~ — skipped at the user’s request.
  - [x] Rambam One.
  - [x] Rambam Three.
  - [x] Tanya.
  - [ ] Likutei Halachos.
  - [ ] ~~Maharan (confirm the exact program/text meant by this screenshot label).~~ — skipped at the user’s request.
  - [x] Chafetz Chaim.
  - [ ] Dirshu Chevras Shas.
  - [x] Dirshu Halacha.
  - [ ] ~~Dirshu Mussar.~~ — skipped at the user’s request.

- [ ] **W07 — Fix events-widget launches and opening the wrong app.**
  - Finding: provider-event rows deliberately use external ACTION_VIEW. The exact
    flashing cause still needs on-device reproduction.
  - Decision: Calendar taps open this app at the event date; Event taps open
    details inside this app for local and device-calendar events.
  - Plan: centralize launch requests with destination, date, event/instance, and
    widget identity; choose destination before rendering and consume once.
    Avoid duplicate activities or competing navigation effects.
  - Plan: audit PendingIntent/Glance action identity and task/transitions without
    assuming collisions are the proven flashing cause.
  - Plan: support in-app read-only details for unsupported/read-only provider
    events, with an explicit external action separately. Preserve the exact
    recurring instance and load current provider data.
  - Touchpoints: widgets/ZmanimWidgets.kt, MainActivity.kt,
    platform/CalendarGateway.kt, ui/EventEditor.kt, AndroidManifest.xml.
  - Verify: both tap modes, local/provider/recurring/read-only/deleted events,
    cold/warm app, Settings last open, alternate opening preference, repeated
    taps, and physical-phone flashing.

## Shared verification and completion

- [x] Update model validation, current-format backup/import, profile duplication,
  dependency checks, and all consumers. Avoid obsolete-test-format compatibility.
- [x] Add meaningful domain coverage for zone/locality resolution, elevation,
  aliases, Hebrew recurrence/date conversion, clock-change intervals, learning
  cycles, and reminder/alarm suppression/reconciliation.
- [x] Run focused integration checks for calendar storage/export, actual alerts,
  widget actions, navigation, and permission recovery.
- [x] Compare live calendar layout with HebDate on the emulator; review themes,
  calendar modes, interface/label language combinations, widths, large text,
  and keyboard-open states.
- [ ] Verify original physical-phone location, keyboard, and widget-launch reports;
  prior emulator passes do not close these reports.
- [ ] Check actual launcher sizes 1×1, 2×1, and larger; verify rollover, active
  profile/app location changes, and boundary refreshes.
- [x] Verify Help/notices in airplane mode and reconcile wording with final behavior.
- [x] Run debug/unit/lint, connected-device checks, and release compilation after
  implementation. Record actual results and remaining limitations.

Verification commands (run during implementation; final results recorded below):

~~~sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:assembleRelease
~~~

## Verification results — 4 November 2026

- `assembleDebug`, `assembleDebugAndroidTest`, `testDebugUnitTest`, `lintDebug`
  and `assembleRelease`: passed offline with the Android Studio JDK. 49 unit
  tests passed, zero failures; lint has zero errors. Release output is unsigned.
- Final API 36 Pixel 7 emulator run via `am instrument -w -r`: **37 tests passed**
  in 109 seconds. This includes provider record adoption/reminder preservation,
  real alarm audio playback and snooze/suppression, GPS snapshot reuse, city
  search with the keyboard open, date navigation/recreation, learning source
  fixtures/finite coverage, and a real widget host with per-row chronometers.
- Calendar review includes five-row Cheshvan and six-row Tishrei, both calendars,
  light/dark, 900 × 1600 and 1080 × 2400, and font scale 1.7. At larger text sizes
  date numbers stack when needed. Compact grids can scroll to retain readable
  labels. Reference and final representative screenshots are in `docs/qa`.
- Both Hebrew and Gregorian date widgets were rendered at 110 × 56, 110 × 110,
  180 × 72 and 120 × 180 dp in both themes. Checks cover actual line height and
  primary-date ellipsis, in addition to view bounds. Short widgets use measured
  day text beside the supporting dates.
- Airplane mode with Wi-Fi and mobile data disabled: all three `NewProblemsUiTest`
  checks passed, including bundled Help/About and keyboard-open offline city search.
  Original connectivity was restored afterward.
- Manual Settings check: Minute rounding and its explanation are visible with
  Show seconds off and absent with it on; the saved rounding value is retained.
- Generated learning data: **81,841 reference rows across 13 programs**, sorted
  by date, without duplicate date/provider keys; every provider's first/last row
  matches its published coverage metadata. Oz V’hadar has 4,188 source rows;
  Oraysa has 350 days with all 250 weekday readings checked against its PDF.
- The emulator's low free-space reserve blocked one Gradle APK install despite
  Gradle reporting success. Final APKs were installed explicitly with a temporary
  reserve adjustment, restored immediately; the final result above is from the
  live instrumentation output, not stale Gradle reports. No other app was removed.

Physical-device checks and the two unvalidated learning programs remain open as
listed above. Emulator results do not close the original physical-phone reports.

## Reference material

- [Month-layout reference](../../../Downloads/WhatsApp%20Image%202026-11-03%20at%204.56.08%20PM.jpeg).
- [Physical-phone location error](../../../Downloads/WhatsApp%20Image%202026-11-03%20at%207.04.46%20PM.jpeg).
- [Learning choices, first screenshot](../../../Downloads/WhatsApp%20Image%202026-11-03%20at%2010.20.55%20PM.jpeg).
- [Learning choices, second screenshot](../../../Downloads/WhatsApp%20Image%202026-11-03%20at%2010.21.38%20PM.jpeg).
- Additional chat images show the 1×1 weekday/day/month/year badge and Hebrew-day
  status icon. W01/R03 describe their content independently of temporary files.
- [Original online widget image](https://play-lh.googleusercontent.com/tbZtIluFi973Bm0m6yTYubv12DSN-pElWqinliv2qMaPsZdZ7wutQS0d2AR9oflY_WFFW5I22TY_kIgVyzyN=w526-h296).
- [GeoNames source/license](https://www.geonames.org/about.html).
- [Timezone Boundary Builder](https://github.com/evansiroky/timezone-boundary-builder).
- [Open Topo Data coverage/API limits](https://www.opentopodata.org/).
- [Android alarm scheduling](https://developer.android.com/develop/background-work/services/alarms).
- [Android full-screen alarm access](https://developer.android.com/about/versions/14/behavior-changes-14).
- [Android notification appearances](https://developer.android.com/develop/ui/compose/notifications).
- [Glance activity-action identity](https://developer.android.com/reference/kotlin/androidx/glance/appwidget/action/package-summary).
- [Hebcal learning schedules](https://hebcal.github.io/api/learning/): evaluate
  individually; this does not establish equivalence to every screenshot program.
- Check calculations/explanations against the pinned KosherJava 3.0.0 source
  artifact; online API documentation may describe newer versions.
