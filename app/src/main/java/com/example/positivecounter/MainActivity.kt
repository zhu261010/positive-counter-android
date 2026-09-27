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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.UUID

val Context.timerStore by preferencesDataStore("timer_preferences")
val TIMERS_JSON = stringPreferencesKey("timers_json")
val LEGACY_NAME = stringPreferencesKey("timer_name")
val LEGACY_START = longPreferencesKey("start_time_epoch_millis")

@Serializable data class StoredTimer(val id: String, val name: String, val startEpochMillis: Long)
data class TimerData(val id: String, val name: String, val start: Instant)
data class Elapsed(val days: Long, val hours: Long, val minutes: Long, val seconds: Long)
data class UiState(val timers: List<TimerData> = emptyList(), val now: Instant = Instant.now(), val loading: Boolean = true, val error: String? = null)
@Serializable data class ExportElapsed(val days: Long, val hours: Long, val minutes: Long, val seconds: Long)
@Serializable data class TimerExport(val name: String, val startTime: String, val currentTime: String, val elapsedSeconds: Long, val elapsed: ExportElapsed)

class TimerRepository(private val context: Context) {
    val data: Flow<List<TimerData>> = context.timerStore.data.map { prefs ->
        val raw = prefs[TIMERS_JSON]
        val stored = if (!raw.isNullOrBlank()) runCatching { Json.decodeFromString<List<StoredTimer>>(raw) }.getOrDefault(emptyList()) else {
            val n = prefs[LEGACY_NAME]; val s = prefs[LEGACY_START]
            if (n != null && s != null) listOf(StoredTimer("legacy", n, s)) else emptyList()
        }
        stored.map { TimerData(it.id, it.name, Instant.ofEpochMilli(it.startEpochMillis)) }
    }
    suspend fun saveAll(timers: List<TimerData>) { context.timerStore.edit { p -> p[TIMERS_JSON] = Json.encodeToString(timers.map { StoredTimer(it.id,it.name,it.start.toEpochMilli()) }); p.remove(LEGACY_NAME); p.remove(LEGACY_START) } }
}

class TimerViewModel(private val repo: TimerRepository) : ViewModel() {
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    init { viewModelScope.launch { repo.data.collectLatest { list -> _state.update { it.copy(timers=list, loading=false) } } }; viewModelScope.launch { while(true) { _state.update { it.copy(now=Instant.now()) }; kotlinx.coroutines.delay(1000) } } }
    fun save(id: String?, name: String, date: LocalDate, time: LocalTime): Boolean {
        val clean=name.trim(); val start=LocalDateTime.of(date,time).atZone(ZoneId.systemDefault()).toInstant()
        val error=when { clean.isEmpty()->"请输入主题名称"; clean.length>50->"主题名称不能超过 50 个字符"; start.isAfter(Instant.now())->"开始时间不能晚于当前时间"; else->null }
        if(error!=null){_state.update{it.copy(error=error)};return false}
        val item=TimerData(id ?: UUID.randomUUID().toString(),clean,start)
        val list=if(id==null) _state.value.timers+item else _state.value.timers.map{if(it.id==id)item else it}
        viewModelScope.launch { repo.saveAll(list); _state.update{it.copy(error=null)} }
        return true
    }
    fun delete(id:String){viewModelScope.launch{repo.saveAll(_state.value.timers.filterNot{it.id==id})}}
    fun export(timer:TimerData):String { val now=Instant.now(); val total=maxOf(0,Duration.between(timer.start,now).seconds); val e=elapsed(total); return Json{prettyPrint=true}.encodeToString(TimerExport(timer.name,timer.start.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),now.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),total,ExportElapsed(e.days,e.hours,e.minutes,e.seconds))) }
    companion object { fun elapsed(total:Long)=Elapsed(total/86400,(total%86400)/3600,(total%3600)/60,total%60) }
}
class VmFactory(private val repo:TimerRepository):ViewModelProvider.Factory{override fun <T:ViewModel> create(c:Class<T>):T=TimerViewModel(repo) as T}

class MainActivity:ComponentActivity(){override fun onCreate(b:Bundle?){super.onCreate(b);setContent{val vm:TimerViewModel=viewModel(factory=VmFactory(TimerRepository(applicationContext)));PositiveCounterApp(vm)}}}

@Composable fun PositiveCounterApp(vm:TimerViewModel){val s by vm.state.collectAsStateWithLifecycle();var editing by remember{mutableStateOf<TimerData?>(null)};var creating by remember{mutableStateOf(false)};MaterialTheme{Surface(Modifier.fillMaxSize()){when{ s.loading->Box(Modifier.fillMaxSize(),Alignment.Center){CircularProgressIndicator()};creating->TimerForm("创建正计时",null,vm){creating=false};editing!=null->TimerForm("编辑正计时",editing,vm){editing=null};else->TimerListScreen(s,vm,{creating=true},{editing=it})}}}}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun TimerListScreen(s:UiState,vm:TimerViewModel,onCreate:()->Unit,onEdit:(TimerData)->Unit){val ctx=LocalContext.current;var message by remember{mutableStateOf<String?>(null)};Scaffold(topBar={TopAppBar(title={Text("正计时")})},floatingActionButton={FloatingActionButton(onClick=onCreate){Text("+")}}){pad->LazyColumn(Modifier.padding(pad).padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(vertical=16.dp)){items(s.timers,key={it.id}){t->val e=TimerViewModel.elapsed(maxOf(0,Duration.between(t.start,s.now).seconds));Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){Text(t.name,fontSize=21.sp);ElapsedDisplay(e);Text("开始：${format(t.start)}",style=MaterialTheme.typography.bodySmall);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){TextButton(onClick={onEdit(t)}){Text("编辑")};TextButton(onClick={val json=vm.export(t);(ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("timer.json",json));message="JSON 已复制"}){Text("复制 JSON")};TextButton(onClick={vm.delete(t.id)}){Text("删除")}}}}};message?.let{item{Text(it,color=MaterialTheme.colorScheme.primary)}}}}}

@Composable private fun ElapsedDisplay(e:Elapsed){Row(verticalAlignment=Alignment.Bottom){Text("${e.days}",fontSize=42.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary);Text("天 ",fontSize=18.sp,color=MaterialTheme.colorScheme.primary);Text("%02d小时".format(e.hours),fontSize=24.sp,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(8.dp));Text("%02d分 %02d秒".format(e.minutes,e.seconds),fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}}

@Composable private fun TimerForm(title:String,timer:TimerData?,vm:TimerViewModel,onDone:()->Unit){val ctx=LocalContext.current;var name by remember(timer){mutableStateOf(timer?.name?:"")};var date by remember(timer){mutableStateOf((timer?.start?:Instant.now()).atZone(ZoneId.systemDefault()).toLocalDate())};var time by remember(timer){mutableStateOf((timer?.start?:Instant.now()).atZone(ZoneId.systemDefault()).toLocalTime().withSecond(0).withNano(0))};val error by vm.state.map{it.error}.collectAsStateWithLifecycle(null);Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)){Text(title,style=MaterialTheme.typography.headlineMedium);OutlinedTextField(name,{name=it},Modifier.fillMaxWidth(),label={Text("主题名称")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Text));OutlinedButton(onClick={DatePickerDialog(ctx,{_,y,m,d->date=LocalDate.of(y,m+1,d)},date.year,date.monthValue-1,date.dayOfMonth).show()},Modifier.fillMaxWidth()){Text("日期：$date")};OutlinedButton(onClick={TimePickerDialog(ctx,{_,h,m->time=LocalTime.of(h,m)},time.hour,time.minute,true).show()},Modifier.fillMaxWidth()){Text("时间：%02d:%02d".format(time.hour,time.minute))};error?.let{Text(it,color=MaterialTheme.colorScheme.error)};Button(onClick={if(vm.save(timer?.id,name,date,time))onDone()},Modifier.fillMaxWidth()){Text("保存")};if(timer!=null)TextButton(onClick=onDone,Modifier.align(Alignment.CenterHorizontally)){Text("取消")}}}
private fun format(i:Instant)=i.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm:ss"))
