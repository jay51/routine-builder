package dev.routinebuilder

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class BuilderStep { DETAILS, LIBRARY, CONFIGURE }

internal data class RoutineBuilderState(
    val routineId: String? = null,
    val routineName: String = "",
    val drafts: List<RoutineTaskDraft> = emptyList(),
    val step: BuilderStep = BuilderStep.DETAILS,
    val category: String = "All",
    val configuration: TaskConfigurationState? = null,
    val editingIndex: Int? = null
)

/** Owns the whole editing session so configuration survives navigation and rotation. */
internal class RoutineBuilderViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(RoutineBuilderState())
    val state = mutableState.asStateFlow()

    fun open(routine: RoutineEntity?, tasks: List<RoutineTaskEntity>) {
        mutableState.value = RoutineBuilderState(
            routineId = routine?.id, routineName = routine?.name.orEmpty(),
            drafts = tasks.sortedBy { it.orderIndex }.map { it.editableDraft() }
        )
    }

    fun setName(name: String) { mutableState.value = mutableState.value.copy(routineName = name) }
    fun setCategory(category: String) { mutableState.value = mutableState.value.copy(category = category) }
    fun showLibrary() { mutableState.value = mutableState.value.copy(step = BuilderStep.LIBRARY, category = "All") }
    fun showDetails() { mutableState.value = mutableState.value.copy(step = BuilderStep.DETAILS) }

    fun configure(template: PredefinedTask) {
        mutableState.value = mutableState.value.copy(
            configuration = TaskConfigurationState.from(template.newRoutineDraft()),
            editingIndex = null, step = BuilderStep.CONFIGURE
        )
    }

    fun editTask(index: Int) {
        val draft = mutableState.value.drafts.getOrNull(index) ?: return
        mutableState.value = mutableState.value.copy(
            configuration = TaskConfigurationState.from(draft), editingIndex = index, step = BuilderStep.CONFIGURE
        )
    }

    fun removeTask(index: Int) {
        mutableState.value = mutableState.value.copy(drafts = mutableState.value.drafts.filterIndexed { i, _ -> i != index })
    }

    fun changeConfiguration(configuration: TaskConfigurationState) {
        // The task snapshot and IDs belong to this session, not to editable controls.
        val current = mutableState.value.configuration ?: return
        mutableState.value = mutableState.value.copy(configuration = configuration.copy(source = current.source))
    }

    fun applyConfiguration() {
        val current = mutableState.value
        val draft = current.configuration?.toDraft() ?: return
        val index = current.editingIndex
        val drafts = if (index == null) current.drafts + draft
            else current.drafts.mapIndexed { i, old -> if (i == index) draft else old }
        mutableState.value = current.copy(drafts = drafts, configuration = null, editingIndex = null, step = BuilderStep.DETAILS)
    }

    fun leaveConfiguration() {
        val current = mutableState.value
        mutableState.value = current.copy(
            step = if (current.editingIndex == null) BuilderStep.LIBRARY else BuilderStep.DETAILS,
            configuration = null, editingIndex = null
        )
    }
}
