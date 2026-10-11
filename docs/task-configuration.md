# Task configuration

## Responsibilities

- `TaskLibrary.kt`: bundled activity definitions and starter values.
- `TaskConfiguration.kt`: task capabilities, typed editable configuration, validation, and conversion to a `RoutineTaskDraft`.
- `RoutineBuilderViewModel.kt`: the routine editing session, navigation between builder steps, and applying or canceling task edits. A `StateFlow` is the UI's source of truth. The ViewModel survives activity rotation; unsaved editing sessions do not survive process termination.
- `TaskConfigurationScreen.kt`: stateless sections for the activity goal, optional timer, optional checklist, and weekly cadence. Shared section, number-input, day-selector, and toggle components keep presentation consistent. Only transient presentation state, such as an open time picker, belongs to composables.
- `LocalData.kt` and `RoutineFiles.kt`: durable routine storage and the explicit versioned import/export contract. The configuration screen performs no database operations.

## Data flow

Choose a template → create a draft → initialize editable configuration → emit changes → validate → apply to the builder's draft collection → save the routine through its repository.

Editing a saved activity initializes from its snapshot, including its existing task ID. It does not reconstruct the activity from an updated library definition. Applying changes copies only configurable fields onto that snapshot. Back cancels the current task edits; existing-task editing returns to the routine, while adding a task returns to the library.

## Adding an option

1. Define its applicability in `TaskConfigurationCapabilities` using task-specific attributes. Category membership alone should not decide whether an input is useful.
2. Add typed editable values and validation to `TaskConfigurationState`. Keep raw text while editing so blank or temporarily invalid input can be corrected. Include applicable required validation in `canSave`, and map validated values in `toDraft()`.
3. Add the saved configuration to `RoutineTaskDraft` and the durable entity when needed. Keep configured targets separate from actual completion results. Update the snapshot-to-draft mapping to preserve saved values during editing.
4. Render the option in its relevant section, or add a focused section with a stable lazy-list key. Reuse shared controls where appropriate. Optional switches reveal their settings only when enabled; irrelevant options are absent.
5. If the option is exported, introduce an explicit schema version and update its reader/writer and format documentation. Keep existing schema contracts intact. Timer settings use version 2; checklist configuration uses version 3.
6. Put runtime behavior in a ViewModel or a controller owned by one. Share applicability rules with the form so an unavailable option cannot run through another entry point.

Use concrete Kotlin types and explicit sections. A generic JSON form renderer or plugin registry is unnecessary for the current task library. Checklist values use concrete `ChecklistItem` objects, with Room converters providing the storage representation.

## Layout and interaction

- The form is centered with a maximum width of 600 dp and observes system and keyboard insets.
- The option list scrolls independently of the footer's add/save button.
- Day controls use one compact row: All (every day) and abbreviated weekdays, with full accessible names and checked-state semantics. The row scrolls horizontally on narrow windows or with larger fonts. Goal and timer fields have no preset buttons or numeric-range hints.
- Time formatting honors the device's 12/24-hour preference. The picker offers clock and keyboard entry.
- Goal, timer, and selected-day errors are shown next to their controls. The ViewModel checks the same validation before applying.
- The timer limit remains independent of the activity duration target, and disabling the timer retains its valid saved limit.

Compilation and manual device inspection have not been performed for this refactor, following the user's preference to build and report issues.

## Optional checklists

- Every task can enable a checklist, off by default. The section follows the activity timer and precedes Repeat.
- The typed form supports adding, renaming, and removing up to 50 subtasks. Each needs a nonblank title of up to 200 Unicode characters. Enabling an empty checklist prevents saving. Disabling preserves nonblank items; unused blank rows are discarded when applying.
- Local stable item IDs preserve checked steps when an existing routine task is edited. Imports create fresh IDs and contain only ordered titles and the enabled flag.
- `TaskChecklistCard` renders accessible, whole-row checkboxes on the task screen. Checks save through the ViewModel/repository to a Room table keyed by task and local date. Reopening or restarting restores them; a new day starts unchecked. Checking every item does not automatically complete the main activity.
- Completing the activity stores the checklist snapshot and checked IDs in its completion log. Unchecked items do not block completion. Quick swipe completion preserves existing checked progress without marking untouched subtasks checked.
- Daily checklist progress does not affect reminder scheduling or consistency until the main activity is completed. Removed tasks' working checklist progress is cleaned up; completion snapshots remain.
- The development database is version 5 and resets older development data through the existing destructive fallback. Build, automated tests, and device inspection are left to the user for this change.
