package dev.routinebuilder

import androidx.room.TypeConverter
import kotlinx.serialization.json.*

/** Stable item identity keeps daily checks attached to the right subtask after editing. */
data class ChecklistItem(val id: String, val title: String)

internal const val MAX_CHECKLIST_ITEMS = 50
internal const val MAX_CHECKLIST_TITLE = 200

internal fun checklistError(enabled: Boolean, items: List<ChecklistItem>): String? = when {
    items.size > MAX_CHECKLIST_ITEMS -> "Use at most $MAX_CHECKLIST_ITEMS subtasks."
    items.map { it.id }.distinct().size != items.size || items.any { it.id.isBlank() || it.id.length > 100 } -> "Each subtask needs a unique identifier."
    enabled && items.isEmpty() -> "Add at least one subtask."
    items.any { it.title.isBlank() || it.title.codePointCount(0, it.title.length) > MAX_CHECKLIST_TITLE } -> "Give each subtask a title of up to $MAX_CHECKLIST_TITLE characters."
    else -> null
}

/** Room representation only; UI and repository use concrete Kotlin values. */
class ChecklistConverters {
    @TypeConverter
    fun encodeItems(items: List<ChecklistItem>): String = buildJsonArray {
        items.forEach { item -> add(buildJsonObject { put("id", item.id); put("title", item.title) }) }
    }.toString()

    @TypeConverter
    fun decodeItems(value: String): List<ChecklistItem> = Json.parseToJsonElement(value).jsonArray.map {
        ChecklistItem(it.jsonObject.getValue("id").jsonPrimitive.content, it.jsonObject.getValue("title").jsonPrimitive.content)
    }

    @TypeConverter
    fun encodeChecks(ids: Set<String>): String = buildJsonArray { ids.sorted().forEach { add(JsonPrimitive(it)) } }.toString()

    @TypeConverter
    fun decodeChecks(value: String): Set<String> = Json.parseToJsonElement(value).jsonArray.map { it.jsonPrimitive.content }.toSet()
}
