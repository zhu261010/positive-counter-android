package com.example.positivecounter

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.*

val Context.timerStore by preferencesDataStore("timer_preferences")
val NAME = stringPreferencesKey("timer_name")
val START = longPreferencesKey("start_time_epoch_millis")

data class TimerData(val name: String, val start: Instant)
data class Elapsed(val days: Long, val hours: Long, val minutes: Long, val seconds: Long)
data class UiState(val timer: TimerData? = null, val now: Instant = Instant.now(), val loading: Boolean = true, val error: String? = null)
@Serializable data class ExportElapsed(val days: Long, val hours: Long, val minutes: Long, val seconds: Long)
@Serializable data class TimerExport(val name: String, val startTime: String, val currentTime: String, val elapsedSeconds: Long, val elapsed: ExportElapsed)

class TimerRepository(private val context: Context) {
    val data: Flow<TimerData?> = context.timerStore.data.map { p ->
        val n = p[NAME]; val s = p[START]; if (n != null && s != null) TimerData(n, Instant.ofEpochMilli(s)) else null
    }
    suspend fun save(name: String, start: Instant) { context.timerStore.edit { it[NAME] = name; it[START] = start.toEpochMilli() } }
    suspend fun clear() { context.timerStore.edit { it.clear() } }
}

class TimerViewModel(private val repo: TimerRepository) : ViewModel() {
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    init {
        viewModelScope.launch { repo.data.collectLatest { timer -> _state.update { it.copy(timer = timer, loading = false) } } }
        viewModelScope.launch { while (true) { _state.update { it.copy(now = Instant.now()) }; kotlinx.coroutines.delay(1000) } }
    }
    fun save(name: String, date: LocalDate, time: LocalTime): Boolean = persist(name, date, time)
    private fun persist(name: String, date: LocalDate, time: LocalTime): Boolean {
        val clean = name.trim(); val start = LocalDateTime.of(date, time).atZone(ZoneId.systemDefault()).toInstant()
        val error = when { clean.isEmpty() -> "请输入主题名称"; clean.length > 50 -> "主题名称不能超过 50 个字符"; start.isAfter(Instant.now()) -> "开始时间不能晚于当前时间"; else -> null }
        if (error != null) { _state.update { it.copy(error = error) }; return false }
        viewModelScope.launch { repo.save(clean, start); _state.update { it.copy(error = null) } }
        return true
    }
    fun clearError() { _state.update { it.copy(error = null) } }
    fun export(): String? { val t = _state.value.timer ?: return null; val now = Instant.now(); val total = maxOf(0, Duration.between(t.start, now).seconds); val e = elapsed(total); return Json { prettyPrint = true }.encodeToString(TimerExport(t.name, t.start.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME), now.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME), total, ExportElapsed(e.days,e.hours,e.minutes,e.seconds))) }
    companion object { fun elapsed(total: Long) = Elapsed(total/86400, (total%86400)/3600, (total%3600)/60, total%60) }
}

class VmFactory(private val repo: TimerRepository) : ViewModelProvider.Factory { override fun <T : ViewModel> create(modelClass: Class<T>): T = TimerViewModel(repo) as T }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { val vm: TimerViewModel = viewModel(factory = VmFactory(TimerRepository(applicationContext))); PositiveCounterApp(vm) } }
}

@Composable fun PositiveCounterApp(vm: TimerViewModel) {
    val state by vm.state.collectAsStateWithLifecycle(); var editing by remember { mutableStateOf(false) }
    MaterialTheme { Surface(Modifier.fillMaxSize()) { when { state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }; state.timer == null -> TimerForm("创建正计时", null, vm, onDone = {}); editing -> TimerForm("编辑正计时", state.timer, vm) { editing = false }; else -> TimerHome(state, vm) { editing = true } } } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun TimerHome(state: UiState, vm: TimerViewModel, onEdit: () -> Unit) {
    val t = state.timer!!; val e = TimerViewModel.elapsed(maxOf(0, Duration.between(t.start, state.now).seconds)); val context = LocalContext.current; var snack by remember { mutableStateOf<String?>(null) }
    Scaffold(topBar = { TopAppBar(title = { Text("正计时") }) }, snackbarHost = { SnackbarHost(remember { SnackbarHostState() }) }) { pad ->
        Column(Modifier.padding(pad).padding(24.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(t.name, fontSize = 28.sp, style = MaterialTheme.typography.headlineMedium); Spacer(Modifier.height(28.dp)); Text("${e.days}", fontSize = 64.sp, color = MaterialTheme.colorScheme.primary); Text("天", fontSize = 18.sp); Text("%02d:%02d:%02d".format(e.hours,e.minutes,e.seconds), fontSize = 28.sp)
            Spacer(Modifier.height(28.dp)); Info("开始时间", format(t.start)); Info("当前时间", format(state.now)); Spacer(Modifier.height(24.dp)); Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { OutlinedButton(onClick = onEdit) { Text("编辑") }; Button(onClick = { vm.export()?.let { json -> (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("timer.json", json)); snack = "JSON 已复制到剪贴板" } }) { Text("复制 JSON") } }
            snack?.let { Spacer(Modifier.height(16.dp)); Text(it, color = MaterialTheme.colorScheme.primary) }
        }
    }
}
@Composable private fun Info(label: String, value: String) { Card(Modifier.fillMaxWidth().padding(vertical = 5.dp)) { Column(Modifier.padding(14.dp)) { Text(label, style = MaterialTheme.typography.labelMedium); Text(value, style = MaterialTheme.typography.bodyLarge) } } }
private fun format(i: Instant) = i.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm:ss"))

@Composable private fun TimerForm(title: String, timer: TimerData?, vm: TimerViewModel, onDone: () -> Unit) {
    val context = LocalContext.current; var name by remember(timer) { mutableStateOf(timer?.name ?: "") }; var date by remember(timer) { mutableStateOf((timer?.start ?: Instant.now()).atZone(ZoneId.systemDefault()).toLocalDate()) }; var time by remember(timer) { mutableStateOf((timer?.start ?: Instant.now()).atZone(ZoneId.systemDefault()).toLocalTime().withSecond(0).withNano(0)) }; val state by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) { Text(title, style = MaterialTheme.typography.headlineMedium); OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("主题名称") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)); OutlinedButton(onClick = { DatePickerDialog(context, { _, y,m,d -> date = LocalDate.of(y,m+1,d) }, date.year,date.monthValue-1,date.dayOfMonth).show() }, Modifier.fillMaxWidth()) { Text("日期：$date") }; OutlinedButton(onClick = { TimePickerDialog(context, { _,h,m -> time = LocalTime.of(h,m) }, time.hour,time.minute,true).show() }, Modifier.fillMaxWidth()) { Text("时间：${"%02d:%02d".format(time.hour,time.minute)}") }; state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }; Button(onClick = { if (vm.save(name,date,time)) onDone() }, Modifier.fillMaxWidth()) { Text("保存") }; if (timer != null) TextButton(onClick = onDone, Modifier.align(Alignment.CenterHorizontally)) { Text("取消") } }
}
