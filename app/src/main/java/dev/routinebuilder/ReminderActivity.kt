package dev.routinebuilder

import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.time.LocalDate

internal data class ReminderState(val loading: Boolean = true, val task: TodayRoutineTask? = null, val closed: Boolean = false, val error: String? = null)

internal class ReminderViewModel(repository: RoutineRepository, request: ReminderRequest) : ViewModel() {
    private val _state = MutableStateFlow(ReminderState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                repository.today(request.date).collect { tasks ->
                    val task = tasks.firstOrNull {
                        it.task.id == request.taskId && it.task.timeOfDay == request.time && it.log == null
                    }.takeIf { request.date == LocalDate.now() }
                    _state.value = ReminderState(loading = false, task = task, closed = task == null)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.value = ReminderState(loading = false, error = "Couldn't load this reminder. Open the app to view your tasks.")
            }
        }
    }
}

class ReminderActivity : ComponentActivity() {
    private var deviceLocked by mutableStateOf(true)
    private var unlockMessage by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val request = RoutineNotifications.request(intent)
        if (request == null || request.date != LocalDate.now()) { finish(); return }
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        deviceLocked = getSystemService(KeyguardManager::class.java).isDeviceLocked
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK), navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK))
        val repository = RoutineRepository(RoutineDatabase.get(this).routineDao())
        setContent {
            val vm: ReminderViewModel = viewModel(factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = ReminderViewModel(repository, request) as T
            })
            val state by vm.state.collectAsState()
            LaunchedEffect(state.closed) {
                if (state.closed) { RoutineNotifications.dismiss(this@ReminderActivity, request); finish() }
            }
            MaterialTheme(colorScheme = darkColorScheme(background = Color.Black, surface = Color.Black, primary = Color(0xFFF2C56B))) {
                Surface(Modifier.fillMaxSize(), color = Color.Black) {
                    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically)) {
                            if (state.loading) CircularProgressIndicator()
                            else {
                                Text(if (deviceLocked) "◷" else state.task?.task?.icon ?: "◷", fontSize = 64.sp)
                                Text("Routine reminder", color = Color.LightGray, fontSize = 16.sp)
                                Text(if (deviceLocked) "Your activity is ready" else state.task?.task?.title ?: "Your activity is ready", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                                if (!deviceLocked) state.task?.let { item ->
                                    Text(item.routineName, color = Color.LightGray)
                                    if (item.task.isDurationTask) Text(durationLabel(item.task.kind, item.task.durationMinutes), color = Color.LightGray)
                                }
                                Text(state.error ?: unlockMessage ?: "Open the task to record your results and complete it.", color = Color.LightGray)
                            }
                        }
                        Button(onClick = { openTask(request) }, enabled = !state.loading && !state.closed, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                            Text("Complete task", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = { finish() }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Not now") }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        deviceLocked = getSystemService(KeyguardManager::class.java).isDeviceLocked
    }

    private fun openTask(request: ReminderRequest) {
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard.isDeviceLocked) {
            keyguard.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() { launchTask(request) }
                override fun onDismissCancelled() { unlockMessage = "Unlock your device to open the task." }
                override fun onDismissError() { unlockMessage = "Unlock your device to open the task." }
            })
        } else launchTask(request)
    }

    private fun launchTask(request: ReminderRequest) {
        RoutineNotifications.dismiss(this, request)
        startActivity(RoutineNotifications.openTaskIntent(this, request))
        finish()
    }
}
