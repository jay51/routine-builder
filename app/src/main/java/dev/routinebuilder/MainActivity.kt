package dev.routinebuilder

import android.content.Intent
import android.content.ContentResolver
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.time.format.DateTimeFormatter
import java.util.Locale

private val forest = Color(0xFF395746)
private val ink = Color(0xFF202821)
private val paper = Color(0xFFF7F6F0)
private val mint = Color(0xFFE5EEE6)
private val gold = Color(0xFFF2C56B)
private val weekdays = listOf(
    DayOfWeek.MONDAY to "Mo", DayOfWeek.TUESDAY to "Tu", DayOfWeek.WEDNESDAY to "We",
    DayOfWeek.THURSDAY to "Th", DayOfWeek.FRIDAY to "Fr", DayOfWeek.SATURDAY to "Sa", DayOfWeek.SUNDAY to "Su"
)

class MainActivity : ComponentActivity() {
    private var pendingReminder by mutableStateOf<ReminderRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingReminder = RoutineNotifications.request(intent)
        val repository = RoutineRepository(RoutineDatabase.get(this).routineDao())
        setContent {
            val vm: RoutineViewModel = viewModel(factory = RoutineViewModel.factory(repository))
            MaterialTheme(colorScheme = lightColorScheme(primary = forest, background = paper, surface = Color.White, onSurface = ink)) {
                RoutineApp(vm, pendingReminder) {
                    pendingReminder = null
                    intent.removeExtra(RoutineNotifications.EXTRA_OPEN_TASK_ID)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingReminder = RoutineNotifications.request(intent)
    }
}

class RoutineViewModel(private val repository: RoutineRepository) : ViewModel() {
    internal val taskTimers = TaskTimers(viewModelScope)
    val routines = repository.routines.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val routineTasks = repository.routineTasks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val todayDate = MutableStateFlow(LocalDate.now())
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val today = todayDate.flatMapLatest { repository.today(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val consistencyWeek = todayDate.flatMapLatest { repository.consistencyWeek(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoutineConsistency.week(LocalDate.now(), emptyList(), emptyList()))
    private val _fileBusy = MutableStateFlow(false)
    val fileBusy = _fileBusy.asStateFlow()
    private val _fileMessage = MutableStateFlow<String?>(null)
    val fileMessage = _fileMessage.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureSingleActiveRoutine()
            while (true) {
                val now = ZonedDateTime.now()
                val midnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
                delay(Duration.between(now, midnight).toMillis().coerceAtLeast(1_000))
                refreshToday()
            }
        }
    }

    fun addRoutine(name: String, tasks: List<RoutineTaskDraft>) {
        viewModelScope.launch { repository.addRoutine(name, tasks) }
    }

    fun updateRoutine(routineId: String, name: String, tasks: List<RoutineTaskDraft>) {
        viewModelScope.launch { repository.updateRoutine(routineId, name, tasks) }
    }

    fun removeRoutineTask(taskId: String) {
        viewModelScope.launch { repository.removeTask(taskId) }
    }

    fun removeRoutine(routineId: String) {
        viewModelScope.launch { repository.removeRoutine(routineId) }
    }

    fun setRoutineActive(routineId: String, active: Boolean) {
        viewModelScope.launch { repository.setRoutineActive(routineId, active) }
    }

    fun saveTaskLog(task: RoutineTaskEntity, actualDuration: Int?, notes: String, checkedItemIds: Set<String>? = null, date: LocalDate = LocalDate.now()) {
        viewModelScope.launch {
            repository.saveTaskLog(task, actualDuration, notes, date, checkedItemIds)
            taskTimers.reset(task.id)
        }
    }

    fun setChecklistItem(taskId: String, date: LocalDate, itemId: String, checked: Boolean) {
        viewModelScope.launch { repository.setChecklistItem(taskId, date, itemId, checked) }
    }

    internal suspend fun resolveReminder(request: ReminderRequest): TodayRoutineTask? {
        refreshToday()
        if (request.date != LocalDate.now()) return null
        return repository.today(request.date).first().firstOrNull {
            it.task.id == request.taskId && it.task.timeOfDay == request.time && it.log == null
        }
    }

    fun refreshToday() {
        viewModelScope.launch {
            repository.syncConsistency()
            todayDate.value = LocalDate.now()
        }
    }

    fun importRoutine(resolver: ContentResolver, uri: Uri) = transfer("Couldn't import this routine. Try opening the file again.") {
        val imported = RoutineFiles.read(resolver, uri)
        repository.importRoutine(imported.title, imported.tasks)
        "Routine imported. Turn on its toggle when you're ready to use it."
    }

    fun exportRoutine(resolver: ContentResolver, uri: Uri, routineId: String) = transfer("Couldn't export this routine. Try another save location.") {
        val contents = repository.exportRoutine(routineId)
        RoutineFiles.write(resolver, uri, contents)
        "Routine exported."
    }

    private fun transfer(failureMessage: String, action: suspend () -> String) {
        if (_fileBusy.value) return
        _fileBusy.value = true
        viewModelScope.launch {
            try {
                _fileMessage.value = withContext(Dispatchers.IO) { action() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: RoutineFileException) {
                _fileMessage.value = e.message
            } catch (_: Exception) {
                _fileMessage.value = failureMessage
            } finally {
                _fileBusy.value = false
            }
        }
    }

    fun showFileMessage(message: String) { _fileMessage.value = message }

    fun clearFileMessage(message: String) {
        if (_fileMessage.value == message) _fileMessage.value = null
    }

    companion object {
        fun factory(repository: RoutineRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = RoutineViewModel(repository) as T
        }
    }
}

@Composable
private fun RoutineApp(vm: RoutineViewModel, notificationRequest: ReminderRequest?, onNotificationHandled: () -> Unit) {
    val context = LocalContext.current
    DisposableEffect(context, vm) {
        val lifecycle = (context as? LifecycleOwner)?.lifecycle
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) vm.refreshToday() }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }
    val routines = vm.routines.collectAsState().value
    val routineTasks = vm.routineTasks.collectAsState().value
    val todayTasks = vm.today.collectAsState().value
    val timerSessions = vm.taskTimers.state.collectAsState().value
    val builderEditor: RoutineBuilderViewModel = viewModel()
    val mountainJourney = rememberMountainJourney(todayTasks)
    val consistencyWeek = vm.consistencyWeek.collectAsState().value
    val fileBusy = vm.fileBusy.collectAsState().value
    val fileMessage = vm.fileMessage.collectAsState().value
    val snackbar = remember { SnackbarHostState() }
    var exportRoutineId by rememberSaveable { mutableStateOf<String?>(null) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importRoutine(context.applicationContext.contentResolver, uri)
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val id = exportRoutineId
        exportRoutineId = null
        if (uri != null && id != null) vm.exportRoutine(context.applicationContext.contentResolver, uri, id)
    }
    LaunchedEffect(fileMessage) {
        fileMessage?.let { message ->
            snackbar.showSnackbar(message)
            vm.clearFileMessage(message)
        }
    }
    var pendingSave by remember { mutableStateOf<(() -> Unit)?>(null) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pendingSave?.invoke()
        pendingSave = null
    }
    LaunchedEffect(routines, routineTasks) {
        val activeRoutineIds = routines.filter { it.active }.map { it.id }.toSet()
        RoutineNotifications.reconcile(context, routineTasks.filter { it.routineId in activeRoutineIds })
    }
    LaunchedEffect(todayTasks) {
        vm.taskTimers.retain(todayTasks)
        todayTasks.filter { it.log != null }.forEach { RoutineNotifications.dismissTask(context, it.task.id) }
    }

    var tab by remember { mutableStateOf("Today") }
    var selected by remember { mutableStateOf<TodayRoutineTask?>(null) }
    var builder by rememberSaveable { mutableStateOf(false) }
    BackHandler(selected != null) { selected = null }
    LaunchedEffect(notificationRequest) {
        val request = notificationRequest ?: return@LaunchedEffect
        tab = "Today"
        builder = false
        try {
            selected = vm.resolveReminder(request)
            RoutineNotifications.dismiss(context, request)
            if (selected == null) vm.showFileMessage("This reminder is no longer due or the task is already complete.")
            onNotificationHandled()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            vm.showFileMessage("Couldn't open this task. Try opening it from Today.")
            onNotificationHandled()
        }
    }

    Scaffold(
        containerColor = paper,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (selected == null && !builder) {
                NavigationBar(containerColor = Color.White) {
                    listOf("Today" to "☀", "Routines" to "▦", "Settings" to "⚙").forEach { (name, icon) ->
                        NavigationBarItem(
                            selected = tab == name,
                            onClick = { tab = name; selected = null },
                            icon = { Text(icon, fontSize = 19.sp) },
                            label = { Text(name) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(if (builder || selected != null) PaddingValues(0.dp) else padding)) {
            when {
                selected != null -> TaskScreen(
                    item = todayTasks.firstOrNull { it.task.id == selected!!.task.id && it.date == selected!!.date } ?: selected!!,
                    timerSession = timerSessions[selected!!.task.id],
                    onStartTimer = { vm.taskTimers.start(selected!!.task) },
                    onPauseTimer = { vm.taskTimers.pause(selected!!.task.id) },
                    onResetTimer = { vm.taskTimers.reset(selected!!.task.id) },
                    onBack = { selected = null },
                    onChecklistChange = { itemId, checked -> vm.setChecklistItem(selected!!.task.id, selected!!.date, itemId, checked) },
                    onSave = { duration, notes, checked ->
                        vm.saveTaskLog(selected!!.task, duration, notes, checked, selected!!.date); selected = null
                    }
                )
                builder -> RoutineBuilderScreen(
                    editor = builderEditor,
                    onCancel = { builder = false },
                    onSave = { routineId, name, tasks ->
                        val save = {
                            if (routineId == null) vm.addRoutine(name, tasks) else vm.updateRoutine(routineId, name, tasks)
                            builder = false
                            tab = "Routines"
                        }
                        val willBeActive = if (routineId == null) routines.none { it.active }
                            else routines.any { it.id == routineId && it.active }
                        if (willBeActive && Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            pendingSave = save
                            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        } else save()
                    }
                )
                tab == "Today" -> TodayScreen(todayTasks, routines.any { it.active }, consistencyWeek, mountainJourney,
                    onOpen = { selected = it }, onComplete = {
                        vm.saveTaskLog(it.task, if (it.task.isDurationTask) it.task.durationMinutes else null, "", date = it.date)
                    })
                tab == "Routines" -> RoutinesScreen(
                    routines = routines,
                    tasks = routineTasks,
                    fileBusy = fileBusy,
                    onImport = {
                        try {
                            importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                        } catch (_: android.content.ActivityNotFoundException) {
                            vm.showFileMessage("No file picker is available on this device.")
                        }
                    },
                    onExport = { routine ->
                        exportRoutineId = routine.id
                        val filename = routine.name.replace(Regex("[^\\p{L}\\p{N} _-]"), "").trim().take(80).ifBlank { "routine" }
                        try {
                            exportLauncher.launch("$filename.routine.json")
                        } catch (_: android.content.ActivityNotFoundException) {
                            exportRoutineId = null
                            vm.showFileMessage("No file picker is available on this device.")
                        }
                    },
                    onAdd = {
                        builderEditor.open(null, emptyList())
                        builder = true
                    },
                    onEdit = { routine, routineTasks ->
                        builderEditor.open(routine, routineTasks)
                        builder = true
                    },
                    onDelete = { vm.removeRoutine(it.id) },
                    onSetActive = { routineId, active ->
                        val change = { vm.setRoutineActive(routineId, active) }
                        if (active && Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            pendingSave = change
                            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        } else change()
                    }
                )
                else -> SettingsScreen()
            }
        }
    }
}

@Composable
private fun TodayScreen(
    tasks: List<TodayRoutineTask>,
    hasActiveRoutine: Boolean,
    week: List<ConsistencyDay>,
    mountainJourney: Animatable<Float, AnimationVector1D>,
    onOpen: (TodayRoutineTask) -> Unit,
    onComplete: (TodayRoutineTask) -> Unit
) {
    val remainingTasks = tasks.filter { it.log == null }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 14.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WeekConsistency(week)
                TodayMilestone(tasks, mountainJourney)
            }
        }
        if (remainingTasks.isNotEmpty()) item {
            HorizontalDivider(color = forest.copy(alpha = .16f))
        }
        if (tasks.isEmpty()) {
            if (hasActiveRoutine) {
                item { EmptyCard("No routine activities today", "Choose the days that work for you when you add activities to a routine.") }
            } else {
                item { EmptyCard("No active routine", "Open Routines and activate the routine you want to follow.") }
            }
        } else if (remainingTasks.isEmpty()) {
            item { EmptyCard("All done for today", "You've completed every activity in today's routine. Enjoy the rest of your day!") }
        }
        items(remainingTasks, key = { it.task.id }) { item ->
            SwipeableTodayTask(item, onOpen = { onOpen(item) }, onComplete = { onComplete(item) })
        }
    }
}

@Composable
private fun WeekConsistency(days: List<ConsistencyDay>) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault())), color = ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth()) {
                days.forEach { day ->
                    val isToday = day.date == LocalDate.now()
                    val description = when (day.status) {
                        ConsistencyStatus.COMPLETE -> "All tasks completed"
                        ConsistencyStatus.INCOMPLETE -> "Incomplete day"
                        ConsistencyStatus.IN_PROGRESS -> "In progress"
                        ConsistencyStatus.UPCOMING -> "Upcoming"
                        ConsistencyStatus.REST -> "No tasks planned"
                        ConsistencyStatus.UNTRACKED -> "Not tracked"
                    }
                    val symbol = when (day.status) {
                        ConsistencyStatus.COMPLETE -> "✓"
                        ConsistencyStatus.INCOMPLETE -> "×"
                        ConsistencyStatus.IN_PROGRESS -> "·"
                        else -> "–"
                    }
                    val background = when (day.status) {
                        ConsistencyStatus.COMPLETE -> forest
                        ConsistencyStatus.INCOMPLETE -> Color(0xFFF6E8E2)
                        else -> paper
                    }
                    val foreground = when (day.status) {
                        ConsistencyStatus.COMPLETE -> Color.White
                        ConsistencyStatus.INCOMPLETE -> Color(0xFF934C38)
                        else -> Color(0xFF68736A)
                    }
                    Column(
                        Modifier.weight(1f).clearAndSetSemantics {
                            contentDescription = "${if (isToday) "Today, " else ""}${day.date.format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault()))}. $description${if (day.total > 0) ", ${day.completed} of ${day.total} tasks completed" else ""}"
                        },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()), color = if (isToday) forest else Color(0xFF68736A), fontSize = 11.sp, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium)
                        Box(
                            Modifier.sizeIn(minWidth = 34.dp, minHeight = 34.dp).background(background, CircleShape)
                                .then(if (isToday) Modifier.border(2.dp, if (day.status == ConsistencyStatus.COMPLETE) gold else forest, CircleShape) else Modifier)
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) { Text(symbol, color = foreground, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                        Text(if (day.total > 0) "${day.completed}/${day.total}" else "", color = Color(0xFF68736A), fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayMilestone(tasks: List<TodayRoutineTask>, mountainJourney: Animatable<Float, AnimationVector1D>) {
    val completed = tasks.count { it.log != null }
    val total = tasks.size
    val celebration = if (total > 0 && completed == total) (mountainJourney.value - total).coerceIn(0f, 1f) else 0f
    val reveal = androidx.compose.animation.core.FastOutSlowInEasing.transform(((celebration - .55f) / .45f).coerceIn(0f, 1f))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = lerp(Color.White, mint, reveal)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 8.dp), contentAlignment = Alignment.Center) {
            Column(Modifier.fillMaxWidth().graphicsLayer { alpha = 1f - reveal }
                .then(if (reveal >= .5f) Modifier.clearAndSetSemantics {} else Modifier), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Today's milestone", modifier = Modifier.weight(1f).padding(end = 8.dp), color = ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("$completed / $total", color = forest, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                MountainMilestone(completed, total, mountainJourney)
            }
            if (reveal > 0f) {
                Column(Modifier.fillMaxWidth().graphicsLayer { alpha = reveal }
                    .then(if (reveal < .5f) Modifier.clearAndSetSemantics {} else Modifier),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("✓  You're done for today", color = forest, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Text("Take a breath. You've earned it.", color = forest, fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Text("$completed / $total tasks completed", color = forest, fontSize = 12.sp)
                }
            }
            FinishConfetti(celebration, Modifier.matchParentSize())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableTodayTask(item: TodayRoutineTask, onOpen: () -> Unit, onComplete: () -> Unit) {
    // Quick completion uses the saved routine settings. Tap to customize today's entry.
    val currentAction by rememberUpdatedState(onComplete)
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Ltr)
        SwipeToDismissBoxValue.EndToStart else SwipeToDismissBoxValue.StartToEnd
    key(item.task.id, LocalDate.now()) {
        val swipeState = rememberSwipeToDismissBoxState(positionalThreshold = { width -> width * .4f })
        LaunchedEffect(swipeState.currentValue) {
            if (swipeState.currentValue == direction) {
                currentAction()
            }
        }
        SwipeToDismissBox(
            state = swipeState,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).semantics {
                customActions = listOf(CustomAccessibilityAction("Mark complete using routine settings") { currentAction(); true })
            },
            enableDismissFromStartToEnd = direction == SwipeToDismissBoxValue.StartToEnd,
            enableDismissFromEndToStart = direction == SwipeToDismissBoxValue.EndToStart,
            backgroundContent = {
                Box(Modifier.fillMaxSize().background(forest).padding(horizontal = 20.dp),
                    contentAlignment = if (LocalLayoutDirection.current == LayoutDirection.Ltr) Alignment.CenterEnd else Alignment.CenterStart) {
                    Text("✓  Complete",
                        color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }
        ) {
            RoutineTaskCard(item, onClick = onOpen)
        }
    }
}

@Composable
private fun RoutineTaskCard(item: TodayRoutineTask, onClick: () -> Unit) {
    val task = item.task
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            Box(Modifier.size(50.dp).background(Color(0xFFF2EEE2), CircleShape), contentAlignment = Alignment.Center) {
                Text(task.icon, fontSize = 23.sp, color = forest)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(task.title, color = ink, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text("${task.category}  ·  ${item.routineName}", color = Color(0xFF737B74), fontSize = 13.sp)
                Text("${formatTaskTime(task.timeOfDay)}${if (task.isDurationTask) " · ${durationLabel(task.kind, task.durationMinutes)}" else ""}", color = forest, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
            Text("Open  ›", color = forest, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun RoutinesScreen(
    routines: List<RoutineEntity>,
    tasks: List<RoutineTaskEntity>,
    fileBusy: Boolean,
    onImport: () -> Unit,
    onExport: (RoutineEntity) -> Unit,
    onAdd: () -> Unit,
    onEdit: (RoutineEntity, List<RoutineTaskEntity>) -> Unit,
    onDelete: (RoutineEntity) -> Unit,
    onSetActive: (String, Boolean) -> Unit
) {
    var pendingDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    val pendingDelete = routines.firstOrNull { it.id == pendingDeleteId }
    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("Delete routine?") },
            text = {
                Text("\"${pendingDelete.name}\" and its activities will be removed, and its reminders will stop. Past completion records will stay on this device. This cannot be undone.")
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingDeleteId = null
                    onDelete(pendingDelete)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) { Text("Cancel") }
            }
        )
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Routines", modifier = Modifier.weight(1f), fontSize = 29.sp, fontWeight = FontWeight.Bold, color = ink)
                OutlinedButton(
                    onClick = onImport,
                    enabled = !fileBusy,
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Import routine" }
                ) { Text("Import") }
            }
        }
        if (fileBusy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = forest), shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Pick your routine", modifier = Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Button(onClick = onAdd, colors = ButtonDefaults.buttonColors(containerColor = gold, contentColor = ink), modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("＋  Create routine", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item { SectionHeading("Your routines", "") }
        if (routines.isEmpty()) item { EmptyCard("No routines yet", "Create one, then add activities from the task library.") }
        items(routines, key = { it.id }) { routine ->
            val routineTasks = tasks.filter { it.routineId == routine.id }.sortedBy { it.orderIndex }
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(routine.name, fontSize = 18.sp, color = ink, fontWeight = FontWeight.SemiBold)
                            Text("${routineTasks.size} ${if (routineTasks.size == 1) "activity" else "activities"}", color = Color(0xFF68736A), fontSize = 13.sp)
                        }
                        OutlinedButton(
                            onClick = { onExport(routine) },
                            enabled = !fileBusy,
                            shape = RoundedCornerShape(50),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 32.dp)
                                .semantics { contentDescription = "Export ${routine.name}" }
                        ) { Text("Export", fontSize = 12.sp) }
                        Spacer(Modifier.width(4.dp))
                        IconButton(
                            onClick = { pendingDeleteId = routine.id },
                            enabled = !fileBusy,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_delete_routine),
                                contentDescription = "Delete ${routine.name}",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = { onEdit(routine, routineTasks) },
                            modifier = Modifier.size(56.dp).background(mint, RoundedCornerShape(16.dp))
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_edit_routine),
                                contentDescription = "Edit ${routine.name}",
                                tint = forest,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Active routine", modifier = Modifier.weight(1f), color = ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Switch(
                            checked = routine.active,
                            onCheckedChange = { active -> onSetActive(routine.id, active) },
                            modifier = Modifier.semantics { contentDescription = "Active routine: ${routine.name}" }
                        )
                    }
                    if (routineTasks.isNotEmpty()) {
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val iconSize = 48.dp
                            val iconSpacing = 10.dp
                            val visibleCount = ((maxWidth + iconSpacing) / (iconSize + iconSpacing)).toInt().coerceAtLeast(0)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(iconSpacing)) {
                                routineTasks.take(visibleCount).forEach { task ->
                                    Box(
                                        Modifier.size(iconSize).background(mint, CircleShape)
                                            .clearAndSetSemantics { contentDescription = task.title },
                                        contentAlignment = Alignment.Center
                                    ) { Text(task.icon, fontSize = 22.sp) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoutineBuilderScreen(
    editor: RoutineBuilderViewModel,
    onCancel: () -> Unit,
    onSave: (String?, String, List<RoutineTaskDraft>) -> Unit
) {
    val ui by editor.state.collectAsState()
    val step = ui.step
    val routineName = ui.routineName
    val drafts = ui.drafts
    val category = ui.category
    val categories = listOf("All", "Exercise", "Learning", "Wellbeing", "Daily Essentials")

    BackHandler {
        when (step) {
            BuilderStep.DETAILS -> onCancel()
            BuilderStep.LIBRARY -> editor.showDetails()
            BuilderStep.CONFIGURE -> editor.leaveConfiguration()
        }
    }

    when (step) {
        BuilderStep.DETAILS -> Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 15.dp)) {
            TextButton(onClick = onCancel, contentPadding = PaddingValues(0.dp)) { Text("‹  Routines", color = forest, fontWeight = FontWeight.SemiBold) }
            Text(if (ui.routineId == null) "Create a routine" else "Edit routine", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = ink)
            OutlinedTextField(
                value = routineName,
                onValueChange = editor::setName,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                label = { Text("Title") },
                singleLine = true
            )
            Row(Modifier.fillMaxWidth().padding(top = 22.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Activities", fontWeight = FontWeight.Bold, color = ink, fontSize = 19.sp)
                Text("${drafts.size} added", color = Color(0xFF737B74), fontSize = 13.sp)
            }
            if (drafts.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    AddActivityButton {
                        editor.showLibrary()
                    }
                }
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(top = 10.dp, bottom = 8.dp)) {
                    items(drafts.indices.toList(), key = { it }) { index ->
                        val draft = drafts[index]
                        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(draft.icon, fontSize = 22.sp)
                                Column(Modifier.weight(1f)) {
                                    Text(draft.title, color = ink, fontWeight = FontWeight.SemiBold)
                                    Text("${daysSummary(draft.daysOfWeek)} · ${formatTaskTime(draft.timeOfDay)}${if (draft.isDurationTask) " · ${durationLabel(draft.kind, draft.durationMinutes)}" else ""}", color = Color(0xFF737B74), fontSize = 12.sp)
                                }
                                IconButton(
                                    onClick = {
                                        editor.editTask(index)
                                    },
                                    modifier = Modifier.semantics { contentDescription = "Edit ${draft.title}" }
                                ) { Text("✎", color = forest, fontSize = 20.sp) }
                                IconButton(
                                    onClick = { editor.removeTask(index) },
                                    modifier = Modifier.semantics { contentDescription = "Remove ${draft.title}" }
                                ) { Text("×", color = Color(0xFF79524A), fontSize = 24.sp) }
                            }
                        }
                    }
                    item {
                        AddActivityButton {
                            editor.showLibrary()
                        }
                    }
                }
            }
            Button(onClick = { onSave(ui.routineId, routineName, drafts) }, enabled = routineName.isNotBlank() && drafts.isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) {
                Text(if (ui.routineId == null) "Save routine" else "Save changes", fontWeight = FontWeight.Bold)
            }
        }

        BuilderStep.LIBRARY -> Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
            TextButton(onClick = editor::showDetails, contentPadding = PaddingValues(0.dp)) { Text("‹  Routine", color = forest, fontWeight = FontWeight.SemiBold) }
            Text("Choose an activity", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = ink)
            Text("Select a task to adjust it for your routine.", color = Color(0xFF68736A), fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                categories.forEach { item ->
                    FilterChip(selected = category == item, onClick = { editor.setCategory(item) }, label = { Text(item) })
                }
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(top = 8.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                val visible = if (category == "All") taskLibrary else taskLibrary.filter { it.category == category }
                items(visible, key = { it.id }) { template ->
                    LibraryTaskCard(template, onClick = { editor.configure(template) })
                }
            }
        }

        BuilderStep.CONFIGURE -> ui.configuration?.let { configuration ->
            TaskConfigurationScreen(
                state = configuration,
                editing = ui.editingIndex != null,
                onChange = editor::changeConfiguration,
                onBack = editor::leaveConfiguration,
                onApply = editor::applyConfiguration
            )
        }
    }
}

@Composable
private fun AddActivityButton(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick, role = Role.Button)
            .semantics(mergeDescendants = true) { contentDescription = "Add an activity" }
            .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.size(112.dp).background(forest, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("+", color = Color.White, fontSize = 58.sp, fontWeight = FontWeight.Light)
        }
        Text("Add an activity", color = forest, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun LibraryTaskCard(task: PredefinedTask, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(47.dp).background(mint, CircleShape), contentAlignment = Alignment.Center) { Text(task.icon, fontSize = 23.sp) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(task.title, color = ink, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(task.category, color = Color(0xFF737B74), fontSize = 13.sp)
                Text(if (task.isDurationTask) "${durationLabel(task.kind, task.defaultDuration)} starter duration" else "Flexible activity", color = forest, fontSize = 12.sp)
            }
            Text("›", color = forest, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TaskScreen(item: TodayRoutineTask, timerSession: TaskTimerSession?, onStartTimer: () -> Unit,
    onPauseTimer: () -> Unit, onResetTimer: () -> Unit, onBack: () -> Unit, onChecklistChange: (String, Boolean) -> Unit,
    onSave: (Int?, String, Set<String>) -> Unit) {
    val task = item.task
    val checklist = item.log?.checklistItems ?: if (task.checklistEnabled) task.checklistItems else emptyList()
    var checkedIds by remember(task.id, item.date, checklist, item.log?.id) { mutableStateOf(item.checkedItemIds) }
    var checklistTouched by remember(task.id, item.date, checklist, item.log?.id) { mutableStateOf(false) }
    LaunchedEffect(item.checkedItemIds) {
        if (!checklistTouched) checkedIds = item.checkedItemIds
    }
    var notes by remember(task.id, item.log?.id) { mutableStateOf(item.log?.notes.orEmpty()) }
    val completionDuration = if (item.log != null) item.log.actualDurationMinutes
        else if (task.isDurationTask) task.durationMinutes else null
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = 22.dp, vertical = 14.dp)) {
        TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) { Text("‹  Today", color = forest, fontWeight = FontWeight.SemiBold) }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Box(Modifier.fillMaxWidth().height(145.dp).background(mint, RoundedCornerShape(26.dp)), contentAlignment = Alignment.Center) {
                    Text(task.icon, fontSize = 57.sp)
                }
            }
            item { Text("${item.routineName.uppercase(Locale.getDefault())}  ·  ${task.category.uppercase(Locale.getDefault())}", color = forest, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = .7.sp) }
            item { Text(task.title, color = ink, fontSize = 27.sp, fontWeight = FontWeight.Bold) }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(formatTaskTime(task.timeOfDay), Modifier.weight(1f), color = forest, style = MaterialTheme.typography.bodyMedium)
                    if (task.isDurationTask) Text(
                        durationLabel(task.kind, task.durationMinutes), Modifier.weight(1f), color = forest,
                        style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.End
                    )
                }
            }
            if (item.log == null && task.timerEnabled && supportsTaskTimer(task.kind, task.isDurationTask)) item {
                TaskTimerCard(task, timerSession, onStartTimer, onPauseTimer, onResetTimer)
            }
            if (checklist.isNotEmpty()) item(key = "checklist") {
                TaskChecklistCard(checklist, checkedIds) { id, checked ->
                    checklistTouched = true
                    checkedIds = if (checked) checkedIds + id else checkedIds - id
                    onChecklistChange(id, checked)
                }
            }
            item {
                OutlinedTextField(notes, { notes = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Notes (optional)") }, placeholder = { Text("Actual duration or anything you'd like to remember") }, minLines = 2)
            }
        }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = { onSave(completionDuration, notes, checkedIds) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
            shape = RoundedCornerShape(18.dp)
        ) {
            Text(if (item.log != null) "Update today's entry" else "Mark activity complete", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        if (item.log != null) Text("This activity is marked complete for today.", Modifier.align(Alignment.CenterHorizontally).padding(top = 7.dp), color = forest, fontSize = 13.sp)
    }
}

@Composable
private fun SettingsScreen() {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    DisposableEffect(context) {
        val lifecycle = (context as? LifecycleOwner)?.lifecycle
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh++ }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }
    val notificationsEnabled = remember(refresh) {
        (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            RoutineNotifications.channelImportance(context) != android.app.NotificationManager.IMPORTANCE_NONE
    }
    val fullScreenAllowed = remember(refresh) { RoutineNotifications.fullScreenAllowed(context) }
    val prominentChannel = remember(refresh) { RoutineNotifications.channelImportance(context) >= android.app.NotificationManager.IMPORTANCE_HIGH }
    val reminderSound = remember(refresh) { RoutineNotifications.soundLabel(context) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
        item { Text("Settings", fontSize = 29.sp, fontWeight = FontWeight.Bold, color = ink) }
        item { Text("Make the app work for you.", color = Color(0xFF68736A), fontSize = 15.sp) }
        item {
            SettingRow(
                "◷",
                "Task notifications",
                if (notificationsEnabled) "Enabled" else "Permission needed",
                if (notificationsEnabled) "Routine activities can notify you at their scheduled times." else "Tap to allow notifications for scheduled activities.",
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                }
            )
        }
        item {
            SettingRow(
                "▣", "Full-screen reminders",
                when {
                    !notificationsEnabled -> "Notifications needed"
                    !fullScreenAllowed -> "Special access needed"
                    !prominentChannel -> "Reminder channel needs attention"
                    else -> "Available when device is locked"
                },
                "Android controls full-screen delivery. While you're using your device, reminders use a normal notification.",
                onClick = {
                    val intent = if (!fullScreenAllowed) RoutineNotifications.fullScreenSettingsIntent(context)
                        else RoutineNotifications.channelSettingsIntent(context)
                    if (intent.resolveActivity(context.packageManager) != null) context.startActivity(intent)
                    else context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                }
            )
        }
        item {
            SettingRow(
                "♫", "Reminder ringtone", reminderSound,
                "Tap to choose the reminder sound in Android settings. Ringtone volume follows your device settings.",
                onClick = {
                    val intent = RoutineNotifications.channelSettingsIntent(context)
                    if (intent.resolveActivity(context.packageManager) != null) context.startActivity(intent)
                    else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                }
            )
        }
        item { SettingRow("A", "Text & accessibility", "Follows your device", "Text size and motion settings are respected.") }
        item { SettingRow("▤", "Your data", "Saved on this device", "Routines and task notes stay private to this device.") }
        item { Text("No account needed. Uninstalling the app or clearing its data removes local records.", color = Color(0xFF737B74), fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)) }
    }
}

@Composable
private fun SettingRow(icon: String, title: String, value: String, detail: String, onClick: (() -> Unit)? = null) {
    Card(
        modifier = if (onClick == null) Modifier else Modifier.clickable(onClick = onClick, role = Role.Button),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(43.dp).background(mint, CircleShape), contentAlignment = Alignment.Center) { Text(icon, color = forest, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, color = ink, fontWeight = FontWeight.SemiBold)
                Text(value, color = forest, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(detail, color = Color(0xFF707A72), fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun SectionHeading(title: String, trailing: String) {
    Row(Modifier.fillMaxWidth().padding(top = 3.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = ink, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        if (trailing.isNotBlank()) Text(trailing, color = Color(0xFF737B74), fontSize = 13.sp)
    }
}

@Composable
private fun EmptyCard(title: String, detail: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("✿", fontSize = 26.sp, color = forest)
            Text(title, color = ink, fontWeight = FontWeight.SemiBold)
            Text(detail, color = Color(0xFF707A72), fontSize = 13.sp)
        }
    }
}

private fun daysSummary(csv: String): String = daysSummary(csv.split(',').mapNotNull(String::toIntOrNull).mapNotNull(DayOfWeek::of).toSet())

private fun formatTaskTime(time: String): String = runCatching {
    formatTaskTime(LocalTime.parse(time))
}.getOrDefault(time)

private fun formatTaskTime(time: LocalTime): String = time.format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))

private fun daysSummary(days: Set<DayOfWeek>): String = when {
    days.size == 7 -> "Every day"
    days.isEmpty() -> "No days selected"
    else -> weekdays.filter { it.first in days }.joinToString(", ") { it.second }
}
