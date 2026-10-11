package dev.routinebuilder

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

/** Stateless sections emit typed configuration changes; the editor owns validation and saving. */
@Composable
internal fun TaskConfigurationScreen(
    state: TaskConfigurationState,
    editing: Boolean,
    onChange: (TaskConfigurationState) -> Unit,
    onBack: () -> Unit,
    onApply: () -> Unit
) {
    Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 600.dp).fillMaxSize()) {
            TextButton(onClick = onBack, modifier = Modifier.padding(horizontal = 12.dp)) {
                Text(if (editing) "‹  Routine" else "‹  Task library")
            }
            LazyColumn(
                Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item(key = "activity") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = .08f)) {
                            Text(state.source.icon, Modifier.padding(14.dp), style = MaterialTheme.typography.headlineSmall)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(state.source.category, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text(state.source.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(state.source.instructions, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyMedium)
                }
                if (state.capabilities.durationUnit != null) item(key = "target") {
                    TargetConfigurationSection(state, onChange)
                }
                if (state.capabilities.activityTimer) item(key = "timer") { TimerConfigurationSection(state, onChange) }
                if (state.capabilities.checklist) item(key = "checklist") { ChecklistConfigurationSection(state, onChange) }
                item(key = "cadence") { CadenceConfigurationSection(state, onChange) }
            }
            Surface(shadowElevation = 3.dp) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Button(onClick = onApply, enabled = state.canSave, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Text(if (editing) "Save task changes" else "Add to routine", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigurationSection(title: String, description: String, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun ConfigurationNumberField(
    value: String, onChange: (String) -> Unit, label: String, error: String?, hint: String? = null, decimal: Boolean = false
) {
    val supporting = error ?: hint
    OutlinedTextField(
        value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth(),
        label = { Text(label) }, supportingText = if (supporting != null) ({ Text(supporting) }) else null, isError = error != null,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        singleLine = true
    )
}

@Composable
private fun TargetConfigurationSection(state: TaskConfigurationState, onChange: (TaskConfigurationState) -> Unit) {
    val hours = state.capabilities.durationUnit == DurationUnit.HOURS
    ConfigurationSection("Activity goal", "Set the target you want to use for this routine.") {
        ConfigurationNumberField(
            value = state.targetInput,
            onChange = { input -> filterDurationInput(state.source.kind, input)?.let { onChange(state.copy(targetInput = it)) } },
            label = if (hours) "Sleep target (hours)" else "Duration (minutes)",
            hint = if (hours) "Decimals are welcome, such as 7.5 hours." else null,
            error = state.targetError, decimal = hours
        )
    }
}

@Composable
private fun ConfigurationToggle(checked: Boolean, onChange: (Boolean) -> Unit, title: String) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun TimerConfigurationSection(state: TaskConfigurationState, onChange: (TaskConfigurationState) -> Unit) {
    ConfigurationSection("Activity timer", "A countdown you start from the task screen.") {
        ConfigurationToggle(state.timerEnabled, { onChange(state.copy(timerEnabled = it)) }, "Use timer")
        if (state.timerEnabled) {
            ConfigurationNumberField(
                value = state.timerInput,
                onChange = { if (it.length <= 3 && it.all(Char::isDigit)) onChange(state.copy(timerInput = it)) },
                label = "Timer limit (minutes)", error = state.timerError
            )
        }
    }
}

@Composable
private fun CadenceConfigurationSection(state: TaskConfigurationState, onChange: (TaskConfigurationState) -> Unit) {
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val is24Hour = android.text.format.DateFormat.is24HourFormat(context)
    val formattedTime = state.time.format(DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a", Locale.getDefault()))
    ConfigurationSection("Repeat", "Time and Days") {
        OutlinedButton(onClick = { showTimePicker = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("${if (state.source.kind == "sleep") "Bedtime" else "Time"} · $formattedTime")
        }
        CompactDaySelector(state.days) { onChange(state.copy(days = it)) }
        state.daysError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
    if (showTimePicker) ConfigurationTimeDialog(state.time, is24Hour, onDismiss = { showTimePicker = false }) {
        onChange(state.copy(time = it))
        showTimePicker = false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigurationTimeDialog(time: LocalTime, is24Hour: Boolean, onDismiss: () -> Unit, onSelect: (LocalTime) -> Unit) {
    val picker = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute, is24Hour = is24Hour)
    var keyboard by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose a time") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TimeInput(picker)
            }
        },
        confirmButton = { TextButton(onClick = { onSelect(LocalTime.of(picker.hour, picker.minute)) }) { Text("Set time") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun CompactDaySelector(selected: Set<DayOfWeek>, onChange: (Set<DayOfWeek>) -> Unit) {
    val allDays = DayOfWeek.entries.toSet()
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val minimumWidth = 32.dp * fontScale
        val fits = maxWidth >= minimumWidth * 8 + 4.dp * 7
        Row(
            if (fits) Modifier.fillMaxWidth() else Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val labels = listOf("All") + DayOfWeek.entries.map { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(2) }
            labels.forEachIndexed { index, label ->
                val day = DayOfWeek.entries.getOrNull(index - 1)
                val checked = if (day == null) selected == allDays else day in selected
                val description = day?.getDisplayName(TextStyle.FULL, Locale.getDefault()) ?: "Every day"
                Box(
                    (if (fits) Modifier.weight(1f) else Modifier.width(minimumWidth))
                        .heightIn(min = 48.dp)
                        .toggleable(checked, role = Role.Checkbox) {
                            onChange(if (day == null) { if (checked) emptySet() else allDays }
                                else if (checked) selected - day else selected + day)
                        }
                        .semantics { contentDescription = description },
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp),
                        color = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        contentColor = if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        border = if (checked) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Box(Modifier.heightIn(min = 30.dp).padding(horizontal = 2.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
                            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChecklistConfigurationSection(state: TaskConfigurationState, onChange: (TaskConfigurationState) -> Unit) {
    ConfigurationSection("Checklist", "Break this activity into small steps.") {
        ConfigurationToggle(state.checklistEnabled, { onChange(state.copy(checklistEnabled = it)) }, "Use checklist")
        if (state.checklistEnabled) {
            state.checklistItems.forEachIndexed { index, item ->
                key(item.id) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = item.title,
                            onValueChange = { title ->
                                if (title.codePointCount(0, title.length) <= MAX_CHECKLIST_TITLE) {
                                    onChange(state.copy(checklistItems = state.checklistItems.map {
                                        if (it.id == item.id) it.copy(title = title) else it
                                    }))
                                }
                            },
                            label = { Text("Subtask ${index + 1}") },
                            isError = item.title.isBlank(),
                            modifier = Modifier.weight(1f), singleLine = true
                        )
                        IconButton(onClick = { onChange(state.copy(checklistItems = state.checklistItems.filterNot { it.id == item.id })) },
                            modifier = Modifier.semantics { contentDescription = "Remove subtask ${index + 1}" }) {
                            Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_delete_routine), contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
            TextButton(onClick = {
                onChange(state.copy(checklistItems = state.checklistItems + ChecklistItem(UUID.randomUUID().toString(), "")))
            }, enabled = state.checklistItems.size < MAX_CHECKLIST_ITEMS) { Text("+ Add subtask") }
            state.checklistError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
