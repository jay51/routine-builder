# Routine import and export

Use **Import routine** in Routines to open a file. Use **Export routine** on a saved routine to choose a save location. Android's document picker handles both operations; no broad storage permission is required.

## Version 3 format

Files are UTF-8 JSON, usually named `<title>.routine.json`. Exports use [routine-v3.schema.json](routine-v3.schema.json), using JSON Schema draft 2020-12. The app validates its required fields, types, ranges, and unknown fields before saving. Unsupported versions are rejected rather than guessed. Files are limited to 1 MiB.

```json
{
  "format": "routine-builder.routine",
  "schemaVersion": 3,
  "routine": {
    "title": "Morning routine",
    "tasks": [
      {
        "templateId": "walking",
        "title": "Walking",
        "icon": "🚶",
        "category": "Exercise",
        "kind": "walk",
        "instructions": "Take an easy-paced walk and notice what's around you.",
        "durationMinutes": 15,
        "timeOfDay": "08:30",
        "daysOfWeek": [1, 2, 3, 4, 5],
        "isDurationTask": true,
        "timerEnabled": true,
        "timerMinutes": 10,
        "checklistEnabled": true,
        "checklistItems": [{"title": "Put on walking shoes"}, {"title": "Fill water bottle"}]
      }
    ]
  }
}
```

- Task array order is preserved. There can be up to 100 tasks, including repeated templates with different settings. An empty array is valid for a saved routine whose tasks were removed.
- Weekdays use ISO numbers: Monday is 1, Sunday is 7. Choose at least one, with no duplicates.
- Times are 24-hour local `HH:mm`, interpreted in the importing device's time zone. No supported version carries a time-zone field.
- Durations range from 1 to 999 minutes. `isDurationTask` controls whether duration inputs are shown; non-duration activities still retain a duration estimate.
- Version 2 requires `timerEnabled` (boolean) and `timerMinutes` (1–999) on every task. The timer limit is independent of the routine duration target. Enabled timers require `isDurationTask: true` and a supported `kind`: run, walk, strength, stretch, yoga, study, read, review, language, meditate, journal, breathe, outside, or tidy. Sleep and non-duration activities must have the timer disabled. Timer settings travel with exports; active countdown sessions do not.
- Sleep tasks use `kind: "sleep"` and show hours in the app. Their `durationMinutes` field remains minutes (for example, 8 hours is 480), and must have `timerEnabled: false`.
- Task definitions are saved snapshots. `templateId` is informational and need not exist in the current library; imported tasks remain editable using their saved settings and instructions.
- Version 3 requires `checklistEnabled` (boolean) and `checklistItems` (ordered array) on every task. Up to 50 items are allowed, each an object with only a nonblank `title` of up to 200 Unicode characters. Enabled checklists need at least one item; disabled checklists can retain saved items. Item identifiers are local and regenerated on import; daily checkbox progress is never exported.
- Only routine configuration is exported. Database IDs, activation state, completions, and private notes are excluded.
- Each import creates a separate **inactive** routine with fresh routine/task IDs. It never overwrites existing routines or switches the active routine. Activate the copy with its toggle when ready.
- Import validates the complete document before a Room transaction saves the routine and all its tasks. Invalid or unreadable files leave existing data unchanged. Success and failure are shown in a snackbar; canceling the file picker changes nothing.
- Export reads a consistent database snapshot and checks it against the version 3 contract before writing. Save failures are reported; a document provider may leave an empty or partial file after a failed write. Files saved outside the app remain available independently of app-private records.

Future format changes must introduce a new schema version and an explicit reader for that version; do not reinterpret version 1 fields.

## Version 1 imports

The original [routine-v1.schema.json](routine-v1.schema.json) remains unchanged. Version 1 imports are explicitly supported: each task gets a disabled timer with its saved duration as the initial timer limit. Re-exporting writes version 3. Unknown fields are rejected for every version.

## Version 2 imports

The [routine-v2.schema.json](routine-v2.schema.json) contract remains unchanged. Version 2 preserves timer settings. Both version 1 and 2 imports initialize an empty, disabled checklist.
