package dev.routinebuilder

import android.content.ContentResolver
import android.net.Uri
import kotlinx.serialization.json.*
import java.time.DayOfWeek
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.io.ByteArrayOutputStream

internal class RoutineFileException(message: String) : Exception(message)

internal data class ImportedRoutine(val title: String, val tasks: List<RoutineTaskDraft>)

/** Exports version 3; reads versions 1, 2 and 3. No database IDs or completion logs. */
internal object RoutineFiles {
    private const val FORMAT = "routine-builder.routine"
    private const val MAX_BYTES = 1_048_576
    private val json = Json { prettyPrint = true }
    private val categories = setOf("Exercise", "Learning", "Wellbeing", "Daily Essentials")
    private val taskKeys = setOf("templateId", "title", "icon", "category", "kind", "instructions", "durationMinutes", "timeOfDay", "daysOfWeek", "isDurationTask")

    fun read(resolver: ContentResolver, uri: Uri): ImportedRoutine {
        val bytes = resolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (output.size() <= MAX_BYTES) {
                val count = input.read(buffer, 0, minOf(buffer.size, MAX_BYTES + 1 - output.size()))
                if (count == -1) break
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
            ?: throw RoutineFileException("Couldn't open the selected file.")
        if (bytes.size > MAX_BYTES) throw RoutineFileException("Choose a routine file smaller than 1 MB.")
        val text = try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: java.nio.charset.CharacterCodingException) {
            throw RoutineFileException("The routine file must use UTF-8 text.")
        }
        return decode(text.removePrefix("\uFEFF"))
    }

    fun write(resolver: ContentResolver, uri: Uri, contents: String) {
        resolver.openOutputStream(uri, "wt")?.use { it.write(contents.toByteArray(Charsets.UTF_8)) }
            ?: throw RoutineFileException("Couldn't save to the selected location.")
    }

    fun encode(routine: RoutineEntity, tasks: List<RoutineTaskEntity>): String {
        val document = buildJsonObject {
            put("format", FORMAT)
            put("schemaVersion", 3)
            putJsonObject("routine") {
                put("title", routine.name)
                putJsonArray("tasks") {
                    tasks.sortedBy { it.orderIndex }.forEach { task ->
                        add(buildJsonObject {
                            put("templateId", task.templateId)
                            put("title", task.title)
                            put("icon", task.icon)
                            put("category", task.category)
                            put("kind", task.kind)
                            put("instructions", task.instructions)
                            put("durationMinutes", task.durationMinutes)
                            put("timeOfDay", task.timeOfDay)
                            put("isDurationTask", task.isDurationTask)
                            put("timerEnabled", task.timerEnabled)
                            put("timerMinutes", task.timerMinutes)
                            put("checklistEnabled", task.checklistEnabled)
                            putJsonArray("checklistItems") {
                                task.checklistItems.forEach { item ->
                                    add(buildJsonObject { put("title", item.title) })
                                }
                            }
                            putJsonArray("daysOfWeek") {
                                task.daysOfWeek.split(',').map(String::toInt).sorted().forEach { add(JsonPrimitive(it)) }
                            }
                        })
                    }
                }
            }
        }
        val contents = json.encodeToString(JsonElement.serializer(), document)
        decode(contents) // Export must satisfy the same contract accepted by import.
        if (contents.toByteArray(Charsets.UTF_8).size > MAX_BYTES) throw RoutineFileException("This routine is too large to export.")
        return contents
    }

    fun decode(contents: String): ImportedRoutine {
        val root = try {
            // Bound nesting before parsing untrusted input; supported versions need at most 6 levels.
            var depth = 0
            var inString = false
            var escaped = false
            contents.forEach { char ->
                if (inString) {
                    if (escaped) escaped = false
                    else if (char == '\\') escaped = true
                    else if (char == '"') inString = false
                } else when (char) {
                    '"' -> inString = true
                    '{', '[' -> { depth++; if (depth > 16) throw RoutineFileException("The routine file is too deeply nested.") }
                    '}', ']' -> depth--
                }
            }
            json.parseToJsonElement(contents) as? JsonObject
                ?: throw RoutineFileException("Choose a Routine Builder JSON file.")
        } catch (e: RoutineFileException) {
            throw e
        } catch (_: IllegalArgumentException) {
            throw RoutineFileException("The selected file isn't valid JSON.")
        }
        root.exactKeys(setOf("format", "schemaVersion", "routine"))
        if (root.string("format", 100) != FORMAT) throw RoutineFileException("This isn't a Routine Builder routine file.")
        val version = root.integer("schemaVersion")
        if (version !in 1..3) throw RoutineFileException("This routine file uses an unsupported version.")
        val routine = root["routine"] as? JsonObject ?: invalid("routine")
        routine.exactKeys(setOf("title", "tasks"))
        val title = routine.string("title", 200)
        val tasks = routine["tasks"] as? JsonArray ?: invalid("tasks")
        if (tasks.size > 100) throw RoutineFileException("A routine file can contain at most 100 activities.")
        val drafts = tasks.mapIndexed { index, element ->
            val task = element as? JsonObject ?: invalid("activity ${index + 1}")
            task.exactKeys(taskKeys +
                (if (version >= 2) setOf("timerEnabled", "timerMinutes") else emptySet()) +
                (if (version >= 3) setOf("checklistEnabled", "checklistItems") else emptySet()))
            val category = task.string("category", 100)
            if (category !in categories) invalid("category")
            val duration = task.integer("durationMinutes")
            if (duration !in 1..999) invalid("durationMinutes")
            val time = task.string("timeOfDay", 5)
            if (!Regex("([01][0-9]|2[0-3]):[0-5][0-9]").matches(time)) invalid("timeOfDay")
            val days = task["daysOfWeek"] as? JsonArray ?: invalid("daysOfWeek")
            val dayNumbers = days.map { integer(it, "daysOfWeek") }
            if (dayNumbers.isEmpty() || dayNumbers.size > 7 || dayNumbers.any { it !in 1..7 } || dayNumbers.distinct().size != dayNumbers.size) invalid("daysOfWeek")
            val durationTask = task["isDurationTask"] as? JsonPrimitive ?: invalid("isDurationTask")
            if (durationTask.isString || durationTask.booleanOrNull == null) invalid("isDurationTask")
            val kind = task.string("kind", 100)
            val timerEnabled = if (version == 1) false else {
                val enabled = task["timerEnabled"] as? JsonPrimitive ?: invalid("timerEnabled")
                if (enabled.isString || enabled.booleanOrNull == null) invalid("timerEnabled")
                enabled.boolean
            }
            val timerMinutes = if (version == 1) duration else task.integer("timerMinutes")
            if (timerMinutes !in 1..999) invalid("timerMinutes")
            if (timerEnabled && !supportsTaskTimer(kind, durationTask.boolean)) invalid("timerEnabled")
            val checklistEnabled = if (version < 3) false else {
                val enabled = task["checklistEnabled"] as? JsonPrimitive ?: invalid("checklistEnabled")
                if (enabled.isString || enabled.booleanOrNull == null) invalid("checklistEnabled")
                enabled.boolean
            }
            val checklistItems = if (version < 3) emptyList() else {
                val items = task["checklistItems"] as? JsonArray ?: invalid("checklistItems")
                if (items.size > MAX_CHECKLIST_ITEMS) invalid("checklistItems")
                items.map { element ->
                    val item = element as? JsonObject ?: invalid("checklistItems")
                    item.exactKeys(setOf("title"))
                    ChecklistItem(java.util.UUID.randomUUID().toString(), item.string("title", MAX_CHECKLIST_TITLE).trim())
                }
            }
            if (checklistError(checklistEnabled, checklistItems) != null) invalid("checklistItems")
            RoutineTaskDraft(
                templateId = task.string("templateId", 100), title = task.string("title", 200),
                icon = task.string("icon", 32), category = category, kind = kind,
                instructions = task.string("instructions", 4000), durationMinutes = duration,
                timeOfDay = time, daysOfWeek = dayNumbers.map(DayOfWeek::of).toSet(),
                isDurationTask = durationTask.boolean, timerEnabled = timerEnabled, timerMinutes = timerMinutes,
                checklistEnabled = checklistEnabled, checklistItems = checklistItems
            )
        }
        return ImportedRoutine(title, drafts)
    }

    private fun JsonObject.exactKeys(expected: Set<String>) {
        if (keys != expected) throw RoutineFileException("The routine file has missing or unsupported fields.")
    }

    private fun JsonObject.string(key: String, max: Int): String {
        val value = this[key] as? JsonPrimitive ?: invalid(key)
        if (!value.isString || value.content.isBlank() || value.content.codePointCount(0, value.content.length) > max) invalid(key)
        return value.content
    }

    private fun JsonObject.integer(key: String): Int = integer(this[key], key)

    private fun integer(element: JsonElement?, key: String): Int {
        val value = element as? JsonPrimitive ?: invalid(key)
        if (value.isString) invalid(key)
        val number = value.content.toBigDecimalOrNull() ?: invalid(key)
        return try { number.intValueExact() } catch (_: ArithmeticException) { invalid(key) }
    }

    private fun invalid(field: String): Nothing = throw RoutineFileException("The routine file has an invalid $field field.")
}
