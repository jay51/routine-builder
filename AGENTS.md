# Routine Builder — agent instructions

## Product direction

Routine Builder helps people build repeatable daily and weekly routines. It is a routine-building app, not a calendar or general-purpose planner. The core experience is choosing familiar activities from a predefined, icon-based task library, customizing each activity's days and time to fit the person, and grouping those activities into named routines.

- Platform: native Android, Kotlin, Jetpack Compose, Material 3.
- Audience: students and people balancing everyday responsibilities.
- Use original branding, icons/illustrations, and supportive copy. Duolingo may inspire clarity and playful feedback, not copied assets or branding.
- No sign-in, account management, backend, or cross-device sync in the initial single-user prototype.
- Keep the app useful offline and save routines and activity records locally.
- Keep the scope focused on manageable habits. Do not add leaderboards, social feeds, AI coaching, payments, wearables, nutrition databases, medical guidance, or additional platforms unless requested.

## Core routine-building experience

1. Open **Routines** and create a named routine.
2. Browse a predefined task library grouped into Exercise, Learning, Wellbeing, and Daily Essentials. Present tasks as recognizable icon-led choices, such as Running, Walking, Reading, and Meditation.
3. Select a task and tailor its supported settings. A duration-based task should let the user change its duration; every task should let the user choose weekdays or every day and set a local time. Keep task-specific instructions and sensible starter values with each task definition.
4. Add one or more configured activities to the routine and save it.
5. Show activities due on the current weekday in **Today**, where they can be marked complete and optionally logged.

Saved routines can be activated or deactivated from **Routines**. At most one routine is active at a time; activating one deactivates the previous active routine. The first saved routine becomes active automatically. Inactive routines remain editable, but their activities do not appear in Today or schedule reminders.

Routine cadence is expressed as selected weekdays and a local time for each activity. Use that time to send a local reminder; do not introduce a calendar view or broad time-slot planner. Keep planning lightweight and avoid turning Today into a general schedule. A routine may contain multiple activities; each configured activity has its own days, time, and settings. Initially, one exercise is one task.

Starter task categories are Exercise, Learning, Wellbeing, and Daily Essentials. Good examples include Running, Walking, Strength workout, Stretching, Yoga, Study session, Reading, Flashcards, Language practice, Meditation, Journaling, Breathing break, Getting outside, Making the bed, Preparing a bag, Tidying, and Preparing lunch. Keep targets adjustable and supportive; do not imply medical or learning outcomes.

Sleep is a Wellbeing library task with an adjustable hours target (starter value: 8 hours), selected weekdays, and a bedtime reminder (starter time: 22:00). Show its target in hours, allowing two decimal places such as 7.5 during configuration. Users can describe actual sleep in Notes on the completion screen; omit a separate actual-hours input. Store durations in minutes, rounded to the nearest minute, within the existing 1–999 minute range; retain `kind = "sleep"` in routine snapshots and exports. Sleep completion is manual and logged for the selected Today date; there is no automatic overnight tracking.

## Navigation and screens

The main destinations are **Today**, **Routines**, and **Settings**. There is no Progress tab, progress dashboard, XP, badges, streak tracking, or weekly-goal interface.

- **Today:** replace the date/title header with a Monday–Sunday consistency tracker. Show a check for completing every planned task, an X for a past day with any unfinished task, and a neutral marker for today in progress, future days, rest days, or dates without tracking records. Keep the prominent daily milestone bar below it. List only unfinished activities from the active routine that fall on today's weekday, including their times. Let the user open an activity, record optional notes, including actual duration if desired, and mark it complete; remove it from the Today list when completed.
- The consistency tracker header shows today's date rather than “This week.” Omit the bottom check/X legend while keeping accessible day/status descriptions. Keep the weekly card compact with 8 dp vertical padding, an 8 dp gap under the date, and 4 dp spacing between day indicators. Separate the weekly and milestone cards by 8 dp. Both cards use 12 dp corners to keep compact content clear of rounded-edge clipping. Weekly status markers have a 34 dp minimum size and grow with their text.
- Separate Today's tracker cards from the unfinished task list with a subtle horizontal divider; omit the “Still to do” heading. Show the divider only when unfinished tasks remain.
- Today task cards support a physical right-to-left swipe with a labeled completion background. All current tasks complete immediately through the ViewModel/repository using their saved routine settings; duration/hour tasks log the configured duration as today's duration, and flexible tasks log no duration. Leave notes empty. Optional inputs do not require opening details; tap to open details when the user wants to use task tools or add notes. Keep an equivalent accessibility action. Only completed records remove cards and update the trackers. If future tasks introduce genuinely required inputs without a saved value, route those tasks to details.
- Today's milestone is a compact card with a 56 dp mountain scene, 8 dp top padding for heading clearance, no bottom padding, and a small heading. Place the mountain ground line at the bottom with only 2 dp of drawing clearance, removing the empty strip below the scene. Keep the finished message equally compact, allowing text to grow with system font scaling. Each completed task animates a visibly walking person over one mountain; it shrinks during the walk as the larger upcoming mountain moves forward. Retain animation state in the app shell across task-screen navigation so returning after completion animates from the previous count. Draw only nearby mountains and place the finish flag beyond the last mountain. Keep the numeric count and accessible progress semantics. Loading saved progress or switching task sets sets the scene immediately; honor Android's animation duration scale.
- The finish flag is part of the moving landscape beyond the last mountain. Reveal it naturally before the final task is completed; the final animation walks the person past the finish marker and leaves him beyond it. Do not make the flag appear only at completion.
- Draw the milestone walker as a compact illustrated hiker with a face, hair, green shirt, gold backpack, trousers, and shoes. Use short strides with separate hip anchors and knees that follow their feet, opposing arm swings, and a subtle body bounce. While Today is visible and tasks remain, have the waiting hiker briefly jump and wave every five seconds. Pause this gesture during task completion animations and while the app is in the background; disable it when system animations are disabled. Keep the figure inside the compact scene.
- After the final finish crossing, burst confetti once and fade the milestone card into a soft green “You're done for today” message with the completed task count. Keep this celebration in the app-shell journey state across navigation. Loading an already completed day shows the finished message immediately without replaying confetti; days with no tasks keep the empty milestone scene. Honor Android's animation duration scale for the celebration and transition too.
- **Routines:** create and edit routines, browse and configure library tasks, and see each saved routine with its activities, days, targets, activation status, and an active-routine toggle. Keep the routine builder and task library central to this screen.
- Keep the Routines header concise: title and a compact rounded Import button at the top right, without an introductory subtitle or activation-rule explanation.
- Keep the green create-routine card compact, with its routine-focused text on the left and the Create routine button on the right. Use routine-focused wording rather than calling the product a habit app.
- Saved routine cards preview as many task icons as fit in one horizontal row without wrapping. Calculate the count from the available card width using 48 dp icons and 10 dp gaps; adapt when the window resizes. Show the total activity count in the header; view and change task details through routine editing.
- Place a small rounded Export button to the left of the larger edit control at the top right of each saved routine card. Preserve accessible touch targets even when reducing the visible button size.
- Make Edit prominent with a 28 dp vector pencil inside a 56 dp soft green rounded control; keep the trash icon visually smaller.
- Keep saved routines in alphabetical order with a stable ID tie-breaker; toggling activation must not reorder the cards.
- Show only the activity count below each routine name. The activation toggle indicates status; omit duplicate Active/Inactive text from the card header.
- Group saved routine card actions at the top right in this order: compact Export, a small trash icon for Delete, and the larger Edit control. The Delete icon has a 48 dp touch target and a routine-specific accessibility label. Keep the activation label and toggle on their own row. Confirm deletion in a dialog naming the routine. Delete the routine and its configured tasks atomically through the ViewModel/repository, stop its reminders through schedule reconciliation, and preserve completion snapshots and daily consistency plans. Deleting an active routine leaves no active routine; do not automatically activate another one.
- **Routine files:** Routines offers import and per-routine export through Android's document picker. Export version 3 using `docs/routine-v3.schema.json` and `docs/routine-files.md`; continue reading versions 1 and 2 using their unchanged schema contracts. Validate the entire file before an atomic import, create fresh IDs and an inactive copy, and exclude activation state, logs, and private notes from exports. Do not silently accept unknown fields or schema versions.
- **Settings:** provide useful app preferences, notification permission/status with a route to Android settings, and clear local-data information.
- **Activity timers:** eligible duration tasks (run, walk, strength, stretch, yoga, study, read, review, language, meditate, journal, breathe, outside, tidy) have an optional Use activity timer switch during routine configuration, off by default. When enabled, save an independent 1–999 minute limit using a numeric input without preset buttons. Sleep, making the bed, preparing a bag, and preparing lunch do not show the option. The task screen offers manual Start/Pause/Resume/Reset, a countdown, and a finished message; never auto-start at the reminder time or auto-complete at zero. Keep quick swipe completion available. Sessions live in ViewModel state through navigation and rotation and use monotonic elapsed time including screen-off time; they are cleared on completion, schedule removal, or a new date. Process termination resets sessions; there is no timer-expiry background notification or service. Persist timer configuration in Room and version 3 routine exports (also read version 2 timer settings). The development database is version 5 and may reset via destructive fallback; no migration is required during development.
- **Optional checklists:** every task has an optional Use checklist switch during configuration, off by default. Allow adding, renaming, and removing up to 50 subtasks with nonblank titles up to 200 Unicode characters; an enabled checklist must have at least one item. Keep saved items when disabled. Show accessible checkboxes on the task screen, saving progress locally per task/date through the ViewModel/repository. New days start unchecked. Checking subtasks does not automatically complete the activity, and unchecked subtasks do not block completion. Quick swipe completion preserves existing checks without marking untouched steps done. Logs preserve checklist snapshots and checked item IDs. Version 3 exports include the enabled flag and ordered titles, excluding local item IDs and checked progress; older file versions initialize an empty disabled checklist.
- **Task details:** use the selected task's settings. Below the task title, show only its time and applicable duration target in a compact full-width row without labels or a surrounding information card. Keep instructions in task configuration rather than repeating them on the completion screen. Omit separate actual-duration inputs, including sleep hours; users can record actual duration in Notes. Completing a task uses its configured duration as the log default, matching swipe completion; updating an existing entry preserves its stored duration. Do not parse duration from Notes. Let the row's text grow with font scaling. Reuse simple input components, but do not force every task into the same category-wide form.
- **Task configuration:** use the dedicated section-based screen for activity goal, optional tools, and weekly cadence, in that order. Keep a visible save footer, a scrolling option list, compact single-row day controls (All plus abbreviated weekdays), with horizontal scrolling on narrow windows or large font sizes, and device-aware time entry. `RoutineBuilderViewModel` owns editing state; `TaskConfigurationState` owns typed inputs, applicability, validation, and conversion to a draft. Preserve saved task snapshots and IDs on edits. Omit duration/hour preset buttons and numeric-range helper copy from the goal and timer cards; retain input validation. Add new options through concrete capability/state/section changes described in `docs/task-configuration.md`; avoid a generic JSON form engine or ad hoc fields in the app shell.

Respect Android system Back, system bars, edge-to-edge insets, keyboard behavior, rotation, and window resizing. Design phone screens first and adapt to larger windows. Use at least 48 dp touch targets, scalable text, clear labels, consistent spacing, sufficient contrast, and Compose semantics for TalkBack. Pair category icons with text and never communicate state through color alone. Honor system font scaling and reduced-motion settings.

## Task library and data model

Keep the following concepts clear in code:

- **Task template:** a predefined activity with a stable ID, title, icon, category, instructions, starter duration where applicable, and default days.
- **Routine:** the user's named collection of configured activities.
- **Routine task:** a copy of a task template configured for one routine, including chosen days, local time, and customized values. Future edits to the bundled template must not overwrite a saved copy.
- **Task log:** a local completion record for a routine task on a date, including actual duration and optional private notes where relevant.

Categories organize the library; the task defines its instructions, target, and applicable inputs. Reusable fields such as duration and notes are fine, with task-specific presentation where useful. Keep intended targets separate from actual results. Preserve task-definition snapshots in historical logs when bundled task templates evolve.

Consistency uses durable daily expected task IDs and completion logs, not the current routine retroactively applied to previous dates. Capture active schedules when tracking starts and before/after routine edits or activation changes; backfill unattended days from the last persisted schedule when the app resumes. Preserve past daily plans. Tasks already planned today remain in today's expectations after edits or switches; new tasks due today are added. No planned tasks means a rest day rather than a failure. Tracking starts when this feature first runs; older dates stay untracked. Today becomes incomplete only after the local day ends. Refresh Today and the tracker at local midnight and on resume. The database 2→3 migration retains existing routines/logs while adding tracking tables.

Local data is stored in app-private device storage. This app is currently in development with no active users, so schema changes may start from a clean database; migrations are not required during this phase. Do not imply cloud backup. Be clear that uninstalling or clearing app data removes local records. Keep personal notes out of analytics and diagnostic logs. Authentication and shared remote persistence remain out of scope.

## Android implementation defaults

Inspect the repository before assuming dependencies or project structure. Prefer the existing conventions and keep the implementation simple. Current defaults:

| Layer | Choice |
|---|---|
| Language and UI | Kotlin, Jetpack Compose, Material 3 |
| App structure | Single activity, screen-level ViewModels, unidirectional state |
| Navigation | AndroidX Navigation Compose if/when multiple routes justify it |
| Async and state | Coroutines, Flow/StateFlow, lifecycle-aware collection |
| Structured storage | Room (SQLite) and KSP |
| Preferences | DataStore only when persistent preferences are needed |
| Dependency injection | Add Hilt only if project complexity warrants it |
| Task definitions | Kotlin models; kotlinx.serialization if versioned bundled content needs it |
| Dates and time | java.time for local dates, weekday cadence, and local task times |
| Reminders | AlarmManager, BroadcastReceiver, NotificationCompat, notification channels |
| Visual assets | Bundled vector/drawable assets or appropriate Unicode symbols for the prototype |
| Build tooling | Gradle Wrapper, Kotlin DSL, version catalog where present, Android Studio |

Use stable, mutually compatible dependency versions and do not use dynamic versions. Recommended minimum SDK is API 26 unless repository requirements establish otherwise. Use the installed toolchain documented below; do not download replacement SDK/JDK installations unnecessarily.

### Local Android toolchain

- Android SDK: `/home/jay/android-sdk`
- JDK 17: `/home/jay/jdk17`
- For Gradle, set `ANDROID_HOME=/home/jay/android-sdk` and `JAVA_HOME=/home/jay/jdk17`. Set `ANDROID_SDK_ROOT` to the same SDK path if required.
- Keep `local.properties` and machine-specific paths out of shared Gradle configuration and version control.

## Engineering approach

- Keep Room as the durable local source of truth behind a repository. Composables should not perform database side effects directly; use ViewModels/repositories.
- Validate routine names, selected days, and task-specific values at input boundaries.
- During development, it is acceptable to replace the local database when the schema changes. Before distributing to users, preserve local data and historical records with explicit Room migrations.
- Use local notifications for task reminders; no backend or internet is needed. Request `POST_NOTIFICATIONS` on Android 13+ when the user saves a routine with scheduled activities. Show permission status and a route to Android notification settings.
- Use one-shot `AlarmManager.setAndAllowWhileIdle` alarms computed from each activity's weekday and local-time settings. Do not request exact-alarm access; Android may defer delivery because of Doze and battery management, and force-stopping the app can prevent delivery until it is opened again.
- Reconcile persisted schedules after app data changes, reboot, device time changes, and time-zone changes. Receivers must recheck the stored routine task and its current schedule before posting, and avoid reminders for activities already completed that day.
- Keep task details private on the lock screen by default. Planning and task logging remain usable if notifications are disabled.
- Reminders use a high-importance notification channel with a full-screen intent when the device is locked or its display is off, full-screen access is available, and the channel allows it. While the device is in use or access is unavailable, use the standard notification. Do not start reminder activities directly from the receiver or use overlay permission. Android controls actual presentation.
- The black full-screen reminder and standard notification offer **Complete task**, which opens the exact task's logging screen in the app; the reminder action never completes the task itself. Validate the task's active routine, scheduled date/time, and completion state before displaying/opening it. Hide personal task details while securely locked, and require unlock to enter the app. Dismissing the screen leaves the notification available.
- Settings shows notification/channel and full-screen access status, refreshed after returning from Android settings, with a route to the special access settings on Android 14+. Existing channel migration preserves user-disabled or explicitly lowered importance. Full-screen permission does not grant exact alarm timing; device delivery and Play Store eligibility still require verification before distribution.
- Reminder notifications use the device's default alarm ringtone with alarm audio attributes. Settings includes a Reminder ringtone shortcut to Android's channel sound settings. The system plays the notification sound once, subject to volume and Do Not Disturb; there is no looping playback service. Channel upgrades preserve explicit user sound/silence choices and lowered importance.
- Do not add automated test frameworks or run automated tests; this is deferred by user request. Compilation, Android Lint, APK assembly, and manual device inspection are appropriate implementation checks.

## Verification and delivery

- Discover actual scripts and available tooling before running commands.
- Use the Gradle Wrapper and local Android toolchain for Kotlin compilation, lint, and debug APK assembly when appropriate.
- The repository's `scripts/run-on-device.sh` builds, installs, and launches the app on one connected Android target or configured emulator. It accepts an ADB serial when multiple devices are connected. Keep this convenience script working as the app evolves; do not run it after every small change unless asked, since the user can build and report issues.
- For UI changes, manually inspect the affected flow on an available emulator or connected device, including routine creation, task configuration, weekday/time selection, notification permission, Today completion, and system Back as applicable. Report checks that could not be performed and why.
- Never claim a check or feature is complete when it was not performed.
- Keep these instructions current when the user changes product direction. Do not restore roadmap tracking unless asked.
