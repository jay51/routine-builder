# Routine import and export

Use **Import routine** in Routines to open a file. Use **Export routine** on a saved routine to choose a save location. Android's document picker handles both operations; no broad storage permission is required.

## Version 1 format

Files are UTF-8 JSON, usually named `<title>.routine.json`. The contract is [routine-v1.schema.json](routine-v1.schema.json), using JSON Schema draft 2020-12. The app validates its required fields, types, ranges, and unknown fields before saving. Unsupported versions are rejected rather than guessed. Files are limited to 1 MiB.

```json
{
  "format": "routine-builder.routine",
  "schemaVersion": 1,
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
        "isDurationTask": true
      }
    ]
  }
}
```

- Task array order is preserved. There can be up to 100 tasks, including repeated templates with different settings. An empty array is valid for a saved routine whose tasks were removed.
- Weekdays use ISO numbers: Monday is 1, Sunday is 7. Choose at least one, with no duplicates.
- Times are 24-hour local `HH:mm`, interpreted in the importing device's time zone. Version 1 carries no time-zone field.
- Durations range from 1 to 999 minutes. `isDurationTask` controls whether duration inputs are shown; non-duration activities still retain a duration estimate.
- Sleep tasks use `kind: "sleep"` and show hours in the app. Their `durationMinutes` field remains minutes (for example, 8 hours is 480), so no schema change is required.
- Task definitions are saved snapshots. `templateId` is informational and need not exist in the current library; imported tasks remain editable using their saved settings and instructions.
- Only routine configuration is exported. Database IDs, activation state, completions, and private notes are excluded.
- Each import creates a separate **inactive** routine with fresh routine/task IDs. It never overwrites existing routines or switches the active routine. Activate the copy with its toggle when ready.
- Import validates the complete document before a Room transaction saves the routine and all its tasks. Invalid or unreadable files leave existing data unchanged. Success and failure are shown in a snackbar; canceling the file picker changes nothing.
- Export reads a consistent database snapshot and checks it against the version 1 contract before writing. Save failures are reported; a document provider may leave an empty or partial file after a failed write. Files saved outside the app remain available independently of app-private records.

Future format changes must introduce a new schema version and an explicit reader for that version; do not reinterpret version 1 fields.
