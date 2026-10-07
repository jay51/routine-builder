package dev.routinebuilder

import java.math.BigDecimal
import java.math.RoundingMode

// Storage and routine files use minutes; Sleep presents its target and results in hours.
internal fun durationInput(kind: String, minutes: Int): String = if (kind == "sleep") {
    BigDecimal(minutes).divide(BigDecimal(60), 2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
} else minutes.toString()

internal fun durationLabel(kind: String, minutes: Int): String = if (kind == "sleep") {
    "${durationInput(kind, minutes)} ${if (minutes == 60) "hour" else "hours"}"
} else "$minutes min"

internal fun filterDurationInput(kind: String, input: String): String? {
    if (kind != "sleep") return input.filter(Char::isDigit).take(3)
    val normalized = input.replace(',', '.')
    if (!Regex("[0-9]{0,2}(\\.[0-9]{0,2})?").matches(normalized)) return null
    return normalized
}

internal fun parseDurationInput(kind: String, input: String): Int? {
    if (kind != "sleep") return input.toIntOrNull()?.takeIf { it in 1..999 }
    val hours = input.replace(',', '.').toBigDecimalOrNull() ?: return null
    if (hours <= BigDecimal.ZERO || hours > BigDecimal("16.65")) return null
    return hours.multiply(BigDecimal(60)).setScale(0, RoundingMode.HALF_UP).toInt().takeIf { it in 1..999 }
}
