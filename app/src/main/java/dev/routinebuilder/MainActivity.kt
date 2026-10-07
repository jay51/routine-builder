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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
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
import kotlin.math.min

private val forest = Color(0xFF395746)
private val ink = Color(0xFF202821)
private val paper = Color(0xFFF7F6F0)
private val mint = Color(0xFFE5EEE6)
private val gold = Color(0xFFF2C56B)
private val allDays = DayOfWeek.entries.toSet()
private val weekdays = listOf(
    DayOfWeek.MONDAY to "Mo", DayOfWeek.TUESDAY to "Tu", DayOfWeek.WEDNESDAY to "We",
    DayOfWeek.THURSDAY to "Th", DayOfWeek.FRIDAY to "Fr", DayOfWeek.SATURDAY to "Sa", DayOfWeek.SUNDAY to "Su"
)

private data class PredefinedTask(
    val id: String,
    val title: String,
    val icon: String,
    val category: String,
    val kind: String,
    val instructions: String,
    val defaultDuration: Int,
    val defaultDays: Set<DayOfWeek>,
    val isDurationTask: Boolean = true
)

private val taskLibrary = listOf(
    PredefinedTask("running", "Running", "🏃", "Exercise", "run", "Choose a comfortable pace and route. Walk breaks are welcome.", 25, setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY)),
    PredefinedTask("walking", "Walking", "🚶", "Exercise", "walk", "Take an easy-paced walk and notice what's around you.", 15, allDays),
    PredefinedTask("strength", "Strength workout", "💪", "Exercise", "strength", "Do a short, manageable strength session. Rest whenever you need to.", 20, setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)),
    PredefinedTask("stretching", "Stretching", "🧘", "Exercise", "stretch", "Move gently through a few stretches that feel comfortable.", 10, allDays),
    PredefinedTask("yoga", "Yoga", "🌿", "Exercise", "yoga", "Follow a short sequence at a pace that feels right for you.", 20, setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SUNDAY)),
    PredefinedTask("study", "Study session", "📚", "Learning", "study", "Pick one topic and focus on it. Take a short break afterward.", 25, setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)),
    PredefinedTask("reading", "Read a book", "📖", "Learning", "read", "Read something you enjoy or want to learn about.", 20, allDays),
    PredefinedTask("flashcards", "Review flashcards", "🗂️", "Learning", "review", "Review a small set of cards and pause when your focus fades.", 15, setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)),
    PredefinedTask("language", "Practice a language", "💬", "Learning", "language", "Practice a few words, listen to a short clip, or have a conversation.", 15, allDays),
    PredefinedTask("meditation", "Meditation", "🪷", "Wellbeing", "meditate", "Sit comfortably and return your attention to your breathing when it wanders.", 10, allDays),
    PredefinedTask("sleep", "Sleep", "😴", "Wellbeing", "sleep", "Set the hours you want to sleep. Choose a bedtime for your reminder and record your sleep when you're ready.", 480, allDays),
    PredefinedTask("journal", "Write in a journal", "✍️", "Wellbeing", "journal", "Write a few thoughts about your day, plans, or anything on your mind.", 5, allDays),
    PredefinedTask("breathing", "Breathing break", "🌬️", "Wellbeing", "breathe", "Take a few slow, comfortable breaths. There is no need to force them.", 5, allDays),
    PredefinedTask("sunlight", "Get outside", "🌤️", "Wellbeing", "outside", "Step outside for a little fresh air when it works for your day.", 10, allDays),
    PredefinedTask("bed", "Make your bed", "🛏️", "Daily Essentials", "bed", "Straighten your bedding in whatever way feels easy.", 5, allDays, false),
    PredefinedTask("bag", "Prepare tomorrow's bag", "🎒", "Daily Essentials", "prepare", "Gather the things you'll want to take with you tomorrow.", 5, setOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY), false),
    PredefinedTask("tidy", "Tidy your space", "🧺", "Daily Essentials", "tidy", "Put a few things back where they belong. Stop when your time is up.", 10, allDays),
    PredefinedTask("lunch", "Prepare lunch", "🥪", "Daily Essentials", "lunch", "Put together a simple lunch that fits your day.", 15, setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY), false),
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

    fun setRoutineActive(routineId: String, active: Boolean) {
        viewModelScope.launch { repository.setRoutineActive(routineId, active) }
    }

    fun saveTaskLog(task: RoutineTaskEntity, actualDuration: Int?, notes: String) {
        viewModelScope.launch { repository.saveTaskLog(task, actualDuration, notes) }
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
        todayTasks.filter { it.log != null }.forEach { RoutineNotifications.dismissTask(context, it.task.id) }
    }

    var tab by remember { mutableStateOf("Today") }
    var selected by remember { mutableStateOf<TodayRoutineTask?>(null) }
    var builder by remember { mutableStateOf(false) }
    var routineBeingEdited by remember { mutableStateOf<RoutineEntity?>(null) }
    var tasksBeingEdited by remember { mutableStateOf(emptyList<RoutineTaskEntity>()) }
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
                    item = selected!!,
                    onBack = { selected = null },
                    onSave = { duration, notes -> vm.saveTaskLog(selected!!.task, duration, notes); selected = null }
                )
                builder -> RoutineBuilderScreen(
                    existingRoutine = routineBeingEdited,
                    existingTasks = tasksBeingEdited,
                    onCancel = { builder = false },
                    onSave = { routineId, name, tasks ->
                        val save = {
                            if (routineId == null) vm.addRoutine(name, tasks) else vm.updateRoutine(routineId, name, tasks)
                            routineBeingEdited = null
                            tasksBeingEdited = emptyList()
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
                tab == "Today" -> TodayScreen(todayTasks, routines.any { it.active }, consistencyWeek, onOpen = { selected = it })
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
                        routineBeingEdited = null
                        tasksBeingEdited = emptyList()
                        builder = true
                    },
                    onEdit = { routine, routineTasks ->
                        routineBeingEdited = routine
                        tasksBeingEdited = routineTasks
                        builder = true
                    },
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
private fun TodayScreen(tasks: List<TodayRoutineTask>, hasActiveRoutine: Boolean, week: List<ConsistencyDay>, onOpen: (TodayRoutineTask) -> Unit) {
    val remainingTasks = tasks.filter { it.log == null }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        item { WeekConsistency(week) }
        item { TodayMilestone(tasks) }
        if (remainingTasks.isNotEmpty()) item { SectionHeading("Still to do", "") }
        if (tasks.isEmpty()) {
            if (hasActiveRoutine) {
                item { EmptyCard("No routine activities today", "Choose the days that work for you when you add activities to a routine.") }
            } else {
                item { EmptyCard("No active routine", "Open Routines and activate the routine you want to follow.") }
            }
        } else if (remainingTasks.isEmpty()) {
            item { EmptyCard("All done for today", "You've completed every activity in today's routine. Enjoy the rest of your day!") }
        }
        items(remainingTasks, key = { it.task.id }) { item -> RoutineTaskCard(item, onClick = { onOpen(item) }) }
    }
}

@Composable
private fun WeekConsistency(days: List<ConsistencyDay>) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Text(day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()), color = if (isToday) forest else Color(0xFF68736A), fontSize = 11.sp, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium)
                        Box(
                            Modifier.size(34.dp).background(background, CircleShape)
                                .then(if (isToday) Modifier.border(2.dp, if (day.status == ConsistencyStatus.COMPLETE) gold else forest, CircleShape) else Modifier),
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
private fun TodayMilestone(tasks: List<TodayRoutineTask>) {
    val completed = tasks.count { it.log != null }
    val total = tasks.size
    val progress = if (total == 0) 0f else completed.toFloat() / total.toFloat()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Today's milestone", color = ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("$completed / $total", color = forest, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
            Canvas(
                modifier = Modifier.fillMaxWidth().height(32.dp).semantics {
                    progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
                    contentDescription = "$completed of $total tasks completed"
                }
            ) {
                val pointRadius = if (total == 0) 0f else min(9.dp.toPx(), size.width / (total * 3f))
                val trackHeight = 14.dp.toPx()
                val trackTop = (size.height - trackHeight) / 2f
                val trackStart = pointRadius
                val trackWidth = size.width - pointRadius * 2f
                drawRoundRect(mint, Offset(trackStart, trackTop), Size(trackWidth, trackHeight), CornerRadius(trackHeight / 2f))
                if (completed > 0) {
                    drawRoundRect(forest, Offset(trackStart, trackTop), Size(trackWidth * progress, trackHeight), CornerRadius(trackHeight / 2f))
                }
                repeat(total) { index ->
                    val center = Offset(trackStart + trackWidth * (index + 1) / total, size.height / 2f)
                    val reached = index < completed
                    drawCircle(if (reached) gold else Color.White, pointRadius, center)
                    drawCircle(forest, pointRadius, center, style = Stroke(width = 2.dp.toPx()))
                }
            }
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
    onSetActive: (String, Boolean) -> Unit
) {
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
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Pick your routine", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Button(onClick = onAdd, colors = ButtonDefaults.buttonColors(containerColor = gold, contentColor = ink), modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("＋  Create routine", fontWeight = FontWeight.Bold)
                        }
                    }
                    Text("✿", color = gold, fontSize = 32.sp)
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
                            Text("${if (routine.active) "Active" else "Inactive"} · ${routineTasks.size} ${if (routineTasks.size == 1) "activity" else "activities"}", color = if (routine.active) forest else Color(0xFF68736A), fontSize = 13.sp)
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
                            onClick = { onEdit(routine, routineTasks) },
                            modifier = Modifier.size(52.dp).semantics { contentDescription = "Edit ${routine.name}" }
                        ) { Text("✎", color = forest, fontSize = 26.sp) }
                    }
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Active routine", color = ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
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

private enum class BuilderStep { DETAILS, LIBRARY, CONFIGURE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutineBuilderScreen(
    existingRoutine: RoutineEntity?,
    existingTasks: List<RoutineTaskEntity>,
    onCancel: () -> Unit,
    onSave: (String?, String, List<RoutineTaskDraft>) -> Unit
) {
    var step by remember { mutableStateOf(BuilderStep.DETAILS) }
    var routineName by remember(existingRoutine?.id) { mutableStateOf(existingRoutine?.name.orEmpty()) }
    var drafts by remember(existingRoutine?.id) {
        mutableStateOf(
            existingTasks.sortedBy { it.orderIndex }.map { task ->
                RoutineTaskDraft(
                    templateId = task.templateId,
                    title = task.title,
                    icon = task.icon,
                    category = task.category,
                    kind = task.kind,
                    instructions = task.instructions,
                    durationMinutes = task.durationMinutes,
                    timeOfDay = task.timeOfDay,
                    daysOfWeek = task.daysOfWeek.split(',').mapNotNull(String::toIntOrNull)
                        .mapNotNull { runCatching { DayOfWeek.of(it) }.getOrNull() }.toSet(),
                    isDurationTask = task.isDurationTask,
                    existingTaskId = task.id
                )
            }
        )
    }
    var activeTemplate by remember { mutableStateOf<PredefinedTask?>(null) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var category by remember { mutableStateOf("All") }
    var durationText by remember { mutableStateOf("") }
    var selectedTime by remember { mutableStateOf(LocalTime.of(9, 0)) }
    var showTimePicker by remember { mutableStateOf(false) }
    var selectedDays by remember { mutableStateOf(emptySet<DayOfWeek>()) }
    val categories = listOf("All", "Exercise", "Learning", "Wellbeing", "Daily Essentials")

    fun configure(template: PredefinedTask, existing: RoutineTaskDraft? = null, index: Int? = null) {
        activeTemplate = template
        editingIndex = index
        durationText = durationInput(template.kind, existing?.durationMinutes ?: template.defaultDuration)
        selectedTime = existing?.timeOfDay?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
            ?: if (template.kind == "sleep") LocalTime.of(22, 0) else LocalTime.of(9, 0)
        selectedDays = existing?.daysOfWeek ?: template.defaultDays
        step = BuilderStep.CONFIGURE
    }

    BackHandler {
        when (step) {
            BuilderStep.DETAILS -> onCancel()
            BuilderStep.LIBRARY -> step = BuilderStep.DETAILS
            BuilderStep.CONFIGURE -> step = BuilderStep.LIBRARY
        }
    }

    when (step) {
        BuilderStep.DETAILS -> Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 15.dp)) {
            TextButton(onClick = onCancel, contentPadding = PaddingValues(0.dp)) { Text("‹  Routines", color = forest, fontWeight = FontWeight.SemiBold) }
            Text(if (existingRoutine == null) "Create a routine" else "Edit routine", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = ink)
            OutlinedTextField(
                value = routineName,
                onValueChange = { routineName = it },
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
                        category = "All"
                        step = BuilderStep.LIBRARY
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
                                        val template = PredefinedTask(
                                            draft.templateId, draft.title, draft.icon, draft.category, draft.kind,
                                            draft.instructions, draft.durationMinutes, draft.daysOfWeek, draft.isDurationTask
                                        )
                                        configure(template, draft, index)
                                    },
                                    modifier = Modifier.semantics { contentDescription = "Edit ${draft.title}" }
                                ) { Text("✎", color = forest, fontSize = 20.sp) }
                                IconButton(
                                    onClick = { drafts = drafts.filterIndexed { i, _ -> i != index } },
                                    modifier = Modifier.semantics { contentDescription = "Remove ${draft.title}" }
                                ) { Text("×", color = Color(0xFF79524A), fontSize = 24.sp) }
                            }
                        }
                    }
                    item {
                        AddActivityButton {
                            category = "All"
                            step = BuilderStep.LIBRARY
                        }
                    }
                }
            }
            Button(onClick = { onSave(existingRoutine?.id, routineName, drafts) }, enabled = routineName.isNotBlank() && drafts.isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) {
                Text(if (existingRoutine == null) "Save routine" else "Save changes", fontWeight = FontWeight.Bold)
            }
        }

        BuilderStep.LIBRARY -> Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
            TextButton(onClick = { step = BuilderStep.DETAILS }, contentPadding = PaddingValues(0.dp)) { Text("‹  Routine", color = forest, fontWeight = FontWeight.SemiBold) }
            Text("Choose an activity", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = ink)
            Text("Select a task to adjust it for your routine.", color = Color(0xFF68736A), fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                categories.forEach { item ->
                    FilterChip(selected = category == item, onClick = { category = item }, label = { Text(item) })
                }
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(top = 8.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                val visible = if (category == "All") taskLibrary else taskLibrary.filter { it.category == category }
                items(visible, key = { it.id }) { template ->
                    LibraryTaskCard(template, onClick = { configure(template) })
                }
            }
        }

        BuilderStep.CONFIGURE -> {
            val template = activeTemplate
            if (template != null) Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 12.dp)) {
                TextButton(onClick = { step = BuilderStep.LIBRARY }, contentPadding = PaddingValues(0.dp)) { Text("‹  Task library", color = forest, fontWeight = FontWeight.SemiBold) }
                Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                    Box(Modifier.size(58.dp).background(mint, CircleShape), contentAlignment = Alignment.Center) { Text(template.icon, fontSize = 29.sp) }
                    Column {
                        Text(template.category, color = forest, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(template.title, color = ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                    Text(template.instructions, Modifier.padding(16.dp), color = Color(0xFF626D64), fontSize = 14.sp)
                }
                if (template.isDurationTask) {
                    OutlinedTextField(
                        value = durationText,
                        onValueChange = { input -> filterDurationInput(template.kind, input)?.let { durationText = it } },
                        modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                        label = { Text(if (template.kind == "sleep") "Sleep target (hours)" else "Duration (minutes)") },
                        supportingText = if (template.kind == "sleep") ({ Text("For example, 8 or 7.5 hours.") }) else null,
                        isError = durationText.isNotEmpty() && parseDurationInput(template.kind, durationText) == null,
                        keyboardOptions = KeyboardOptions(keyboardType = if (template.kind == "sleep") KeyboardType.Decimal else KeyboardType.Number),
                        singleLine = true
                    )
                }
                val context = LocalContext.current
                OutlinedButton(
                    onClick = { showTimePicker = true },
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp).heightIn(min = 52.dp)
                ) {
                    Text("${if (template.kind == "sleep") "Bedtime" else "Time"}  ·  ${formatTaskTime(selectedTime)}")
                }
                if (showTimePicker) {
                    val pickerState = rememberTimePickerState(
                        initialHour = selectedTime.hour,
                        initialMinute = selectedTime.minute,
                        is24Hour = android.text.format.DateFormat.is24HourFormat(context)
                    )
                    AlertDialog(
                        onDismissRequest = { showTimePicker = false },
                        title = { Text("Choose a time") },
                        text = { TimePicker(state = pickerState) },
                        confirmButton = {
                            TextButton(onClick = {
                                selectedTime = LocalTime.of(pickerState.hour, pickerState.minute)
                                showTimePicker = false
                            }) { Text("Set time") }
                        },
                        dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Cancel") } }
                    )
                }
                Text("Which days?", color = ink, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 22.dp, bottom = 8.dp))
                FilterChip(selected = selectedDays == allDays, onClick = { selectedDays = if (selectedDays == allDays) emptySet() else allDays }, label = { Text("Every day") })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    weekdays.forEach { (day, short) ->
                        FilterChip(
                            selected = day in selectedDays,
                            onClick = { selectedDays = if (day in selectedDays) selectedDays - day else selectedDays + day },
                            label = { Text(short.take(1), maxLines = 1) },
                            modifier = Modifier.weight(1f).semantics { contentDescription = day.name.lowercase() }
                        )
                    }
                }
                Text("You can choose every day or select specific days of the week.", color = Color(0xFF737B74), fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
                        val draft = RoutineTaskDraft(
                            templateId = template.id,
                            title = template.title,
                            icon = template.icon,
                            category = template.category,
                            kind = template.kind,
                            instructions = template.instructions,
                            durationMinutes = parseDurationInput(template.kind, durationText) ?: template.defaultDuration,
                            timeOfDay = selectedTime.toString(),
                            daysOfWeek = selectedDays,
                            isDurationTask = template.isDurationTask,
                            existingTaskId = editingIndex?.let { drafts.getOrNull(it)?.existingTaskId }
                        )
                        val index = editingIndex
                        drafts = if (index == null) drafts + draft else drafts.mapIndexed { i, old -> if (i == index) draft else old }
                        step = BuilderStep.DETAILS
                    },
                    enabled = selectedDays.isNotEmpty() && (!template.isDurationTask || parseDurationInput(template.kind, durationText) != null),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)
                ) { Text(if (editingIndex == null) "Add to routine" else "Save changes", fontWeight = FontWeight.Bold) }
            }
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
private fun TaskScreen(item: TodayRoutineTask, onBack: () -> Unit, onSave: (Int?, String) -> Unit) {
    val task = item.task
    var durationText by remember(task.id, item.log?.id) { mutableStateOf(durationInput(task.kind, item.log?.actualDurationMinutes ?: task.durationMinutes)) }
    var notes by remember(task.id, item.log?.id) { mutableStateOf(item.log?.notes.orEmpty()) }
    val actualDuration = if (task.kind == "sleep") parseDurationInput(task.kind, durationText) else durationText.toIntOrNull()
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 14.dp)) {
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
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("Your activity", fontWeight = FontWeight.SemiBold, color = ink)
                        Text("Scheduled for ${formatTaskTime(task.timeOfDay)}", color = forest, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Text(task.instructions, color = Color(0xFF626D64), fontSize = 14.sp)
                        if (task.isDurationTask) Text("Your routine target: ${durationLabel(task.kind, task.durationMinutes)}", color = forest, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                }
            }
            if (task.isDurationTask) item {
                OutlinedTextField(
                    value = durationText,
                    onValueChange = { input -> filterDurationInput(task.kind, input)?.let { durationText = it } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (task.kind == "sleep") "Hours slept (optional)" else "Minutes (optional)") },
                    isError = task.kind == "sleep" && durationText.isNotEmpty() && actualDuration == null,
                    keyboardOptions = KeyboardOptions(keyboardType = if (task.kind == "sleep") KeyboardType.Decimal else KeyboardType.Number),
                    singleLine = true
                )
            }
            item {
                OutlinedTextField(notes, { notes = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Note (optional)") }, placeholder = { Text("Anything you'd like to remember?") }, minLines = 2)
            }
        }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = { onSave(if (task.isDurationTask) actualDuration else null, notes) },
            enabled = !task.isDurationTask || durationText.isBlank() || actualDuration != null,
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
