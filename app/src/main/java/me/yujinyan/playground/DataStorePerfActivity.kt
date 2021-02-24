package me.yujinyan.playground

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.createDataStore
import com.jakewharton.picnic.table
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import me.yujinyan.playground.ui.theme.Playground2Theme
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource
import kotlin.time.measureTime

// region Android code
class DataStorePerfActivity : AppCompatActivity() {
    @ExperimentalAnimationApi
    @ExperimentalTime
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Playground2Theme {
                Surface(color = MaterialTheme.colors.background) {
                    TestRunnerScreen()
                }
            }
        }
    }
}


val dataIterationOptions = listOf(100, 500, 1000, 2000)

@ExperimentalAnimationApi
@Preview
@Composable
@ExperimentalTime
fun TestRunnerScreen() {
    val padding = 16.dp
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    //  This doesn't work. Maybe related to AndroidView usage.
    //  val (selectedFactory, onFactorySelected) = remember {
    //      mutableStateOf(testFactories.first())
    //  }
    val selectedFactory = remember {
        mutableStateOf(testFactories.first())
    }
    val (selectedDataIterations, onDataIterationsSelected) = remember {
        mutableStateOf(dataIterationOptions[0])
    }
    val testResults = remember { mutableStateOf(emptyList<Report>()) }
    val running = remember { mutableStateOf(false) }
    val error = remember { mutableStateOf<Throwable?>(null) }

    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedVisibility(visible = running.value) { LinearProgressIndicator() }
        AndroidView(viewBlock = { context ->
            Spinner(context).apply {
                val mAdapter = ArrayAdapter(
                    context,
                    android.R.layout.simple_spinner_dropdown_item,
                    testFactories.map { it.name }.toTypedArray()
                )
                adapter = mAdapter
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(
                        parent: AdapterView<*>?, view: View?, position: Int, id: Long
                    ) {
                        selectedFactory.value = testFactories[position]
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                }
            }
        })
        Spacer(Modifier.preferredSize(padding))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            dataIterationOptions.forEach {
                Column(
                    Modifier.selectable(
                        selected = (it == selectedDataIterations),
                        onClick = { onDataIterationsSelected(it) }
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    RadioButton(
                        modifier = Modifier.clearAndSetSemantics { },
                        selected = (it == selectedDataIterations),
                        onClick = { onDataIterationsSelected(it) }
                    )
                    Text(text = it.toString())
                }
            }
        }
        Spacer(Modifier.preferredSize(padding))
        Button(onClick = {
            scope.launch {
                running.value = true
                val result = try {
                    runTest(context, selectedFactory.value, selectedDataIterations)
                } catch (e: Throwable) {
                    e.printStackTrace()
                    error.value = e
                    return@launch
                } finally {
                    running.value = false
                }

                testResults.value += result
            }
        }) { Text(text = "Start") }
        Spacer(Modifier.preferredSize(padding))
        if (error.value != null) {
            Text(text = "$error")
        } else {
            ReportDisplay(testResults = testResults.value)
        }
    }
}

@ExperimentalTime
@Composable
fun ReportDisplay(testResults: List<Report>) {
    Text(
        fontFamily = FontFamily.Monospace, fontSize = 14.sp, text = table {
            cellStyle { paddingLeft = 1; paddingRight = 1 }
            row {
                cell("name")
                cell("iter")
                cell("init")
                cell("write")
                cell("read")
            }
            testResults.forEach {
                row {
                    cell(it.name)
                    cell(it.iter)
                    cell(it.initFile)
                    cell(it.writeDataTime)
                    cell(it.readDataTime)
                }
            }
        }.toString()
    )
}

// endregion


// region DataStore tests code
val testFactories = listOf(
    SharedPreferencesTest,
    SharedPreferencesSyncTest,
    DataStoreTest,
    DataStoreReadOptimizedSharedFlowTest,
    DataStoreReadOptimizedStateFlowTest,
    DataStoreReadOptimized2Test
)

sealed class TestValue(val key: String, open val value: Any) {
    class IntValue(key: String, override val value: Int) : TestValue(key, value)
    class StringValue(key: String, override val value: String) : TestValue(key, value)
}

interface PerfTestFactory {
    val name: String
    suspend fun make(context: Context): PerfTest
}


interface PerfTest {
    suspend fun write(value: TestValue)
    suspend fun read(value: TestValue) {
        val readValue = doRead(value)
        check(readValue == value.value) {
            "expected $value, but read $readValue"
        }
    }

    suspend fun cleanUp()

    suspend fun doRead(value: TestValue): Any?
}

interface BeforeRead {
    fun beforeRead(values: Iterable<TestValue>)
}

/**
 * Baseline [SharedPreferencesTest] test
 */
@SuppressLint("ApplySharedPref")
open class SharedPreferencesTest(private val sp: SharedPreferences) : PerfTest {

    companion object : PerfTestFactory {
        override val name: String = "SP"

        override suspend fun make(context: Context): SharedPreferencesTest {
            val sp = context.getSharedPreferences("test", Context.MODE_PRIVATE)
            return SharedPreferencesTest(sp)
        }
    }

    override suspend fun write(value: TestValue) = withContext(Dispatchers.IO) {
        val editor = sp.edit()
        when (value) {
            is TestValue.IntValue -> editor.putInt(value.key, value.value)
            is TestValue.StringValue -> editor.putString(value.key, value.value)
        }
        check(editor.commit()) { "sp failed to write $value" }
    }

    /**
     * [SharedPreferences] only reads from memory on first iteration.
     * So no need to use [Dispatchers.IO]
     */
    override suspend fun doRead(value: TestValue): Any? {
        return when (value) {
            is TestValue.IntValue -> sp.getInt(value.key, 0)
            is TestValue.StringValue -> sp.getString(value.key, "")
        }
    }

    override suspend fun cleanUp() = withContext(Dispatchers.IO) {
        sp.edit().clear().commit()
        Unit
    }
}

/**
 * Add [Synchronized] to read / write [SharedPreferences].
 *
 * [DataStore] provides atomic read-write cycle. Check if extra synchronization on [SharedPreferences]
 * makes it perform like [DataStore].
 *
 * Test result: not significant.
 */
class SharedPreferencesSyncTest(sp: SharedPreferences) : SharedPreferencesTest(sp) {
    companion object : PerfTestFactory {
        override val name: String = "SP-Sync"

        override suspend fun make(context: Context): SharedPreferencesSyncTest {
            val sp = context.getSharedPreferences("test", Context.MODE_PRIVATE)
            return SharedPreferencesSyncTest(sp)
        }
    }

    @Synchronized
    override suspend fun doRead(value: TestValue): Any? = super.doRead(value)

    @Synchronized
    override suspend fun write(value: TestValue) = super.write(value)
}


/**
 * Baseline [DataStore] test.
 *
 * Performs about an order of magnitude slower than [SharedPreferencesTest].
 *
 * Probably due to repeated [Flow] collection overhead.
 */
open class DataStoreTest(private val dataStore: DataStore<Preferences>) : PerfTest {
    companion object : PerfTestFactory {
        override val name: String = "DataStore"
        override suspend fun make(context: Context): DataStoreTest {
            return DataStoreTest(context.createDataStore("test"))
        }
    }

    override suspend fun write(value: TestValue) {
        dataStore.edit {
            when (value) {
                is TestValue.IntValue -> it[intPreferencesKey(value.key)] = value.value
                is TestValue.StringValue -> it[stringPreferencesKey(value.key)] = value.value
            }
        }
    }

    /**
     * Uses [Flow.first] to read pref.
     *
     * This should be the recommended general approach to reading current data from DataStore
     * despite being the slowest among these tests.
     */
    override suspend fun doRead(value: TestValue): Any? {
        val p = dataStore.data.first()

        return when (value) {
            is TestValue.IntValue -> p[intPreferencesKey(value.key)]
            is TestValue.StringValue -> p[stringPreferencesKey(value.key)]
        }
    }

    override suspend fun cleanUp() {
        dataStore.edit { it.clear() }
    }
}

/**
 * Uses [SharedFlow], perf similar to [SharedPreferencesTest]
 *
 * In practice, we would recommend using the [DataStoreReadOptimizedStateFlowTest] approach.
 */
class DataStoreReadOptimizedSharedFlowTest(dataStore: DataStore<Preferences>) :
    DataStoreTest(dataStore), CoroutineScope {

    companion object : PerfTestFactory {
        override val name: String = "DS-SharedFlow"
        override suspend fun make(context: Context): DataStoreTest {
            return DataStoreReadOptimizedSharedFlowTest(context.createDataStore("test"))
        }
    }

    override val coroutineContext = Dispatchers.IO + Job()

    private val state = dataStore.data.shareIn(this, SharingStarted.Eagerly, 1)

    override suspend fun doRead(value: TestValue): Any? {
        if (state.replayCache.isEmpty()) state.first()
        val p = state.replayCache.first()

        return when (value) {
            is TestValue.IntValue -> p[intPreferencesKey(value.key)]
            is TestValue.StringValue -> p[stringPreferencesKey(value.key)]
        }
    }

}


/**
 * Uses [StateFlow], perf similar to [SharedPreferencesTest]
 *
 * A coroutine is started in the background and caches latest state of [Preferences].
 *
 * This also seems to be a good approach, besides using [Flow.first] directly.
 *
 */
class DataStoreReadOptimizedStateFlowTest(dataStore: DataStore<Preferences>) :
    DataStoreTest(dataStore), CoroutineScope {
    companion object : PerfTestFactory {
        override val name: String = "DS-StateFlow"
        override suspend fun make(context: Context): DataStoreTest {
            return DataStoreReadOptimizedStateFlowTest(context.createDataStore("test"))
        }
    }

    private val scopeJob = Job()

    override val coroutineContext: CoroutineContext = Dispatchers.IO + scopeJob

    private val deferredPref = async {
        // note: must `stateIn` a separate `Job`
        dataStore.data.stateIn(this + Job(parent = scopeJob))
    }

    private suspend fun pref(): StateFlow<Preferences> = deferredPref.await()

    override suspend fun doRead(value: TestValue): Any? {
        val p = pref().value

        return when (value) {
            is TestValue.IntValue -> p[intPreferencesKey(value.key)]
            is TestValue.StringValue -> p[stringPreferencesKey(value.key)]
        }
    }
}

/**
 * We observed no significant improvement over [DataStoreReadOptimizedStateFlowTest].
 */
class DataStoreReadOptimized2Test(dataStore: DataStore<Preferences>) :
    DataStoreTest(dataStore), BeforeRead {
    companion object : PerfTestFactory {
        override val name: String = "DS-RO-2"
        override suspend fun make(context: Context): DataStoreTest {
            return DataStoreReadOptimized2Test(context.createDataStore("test"))
        }
    }

    private val state = dataStore.data.shareIn(GlobalScope, SharingStarted.Eagerly, 1)

    var keys = mutableMapOf<String, Preferences.Key<out Any>>()

    /**
     * Pre-allocate keys.
     */
    override fun beforeRead(values: Iterable<TestValue>) {
        values.associateByTo(keys, { it.key }, {
            when (it) {
                is TestValue.IntValue -> intPreferencesKey(it.key)
                is TestValue.StringValue -> stringPreferencesKey(it.key)
            }
        })
    }

    override suspend fun doRead(value: TestValue): Any? {
        if (state.replayCache.isEmpty()) state.first()
        val p = state.replayCache.first()
        val key = keys[value.key]
        return p[key as Preferences.Key<*>]
    }

}
//class RoomSqlTest(v)

@ExperimentalTime
data class Report(
    val name: String,
    val iter: Int,
    val initFile: Duration,
    val writeDataTime: Duration,
    val readDataTime: Duration,
)


@ExperimentalTime
suspend fun runTest(
    context: Context,
    factory: PerfTestFactory,
    dataIterations: Int = 500
): Report {
    val now = TimeSource.Monotonic.markNow()
    val test = factory.make(context)
    val initTook = now.elapsedNow()

    val testDataSeq = sequence {
        repeat(dataIterations) {
            yield(TestValue.IntValue("int value $it", it))
            yield(TestValue.StringValue("string key $it", "string value $it"))
        }
    }

    val writeTook = measureTime {
        testDataSeq.forEach {
            test.write(it)
        }
    }

    if (test is BeforeRead) {
        test.beforeRead(testDataSeq.asIterable())
    }

    val readTook = measureTime {
        testDataSeq.forEach {
            test.read(it)
        }
    }
    test.cleanUp()

    if (test is CoroutineScope) {
        test.cancel()
    }
    return Report(factory.name, dataIterations, initTook, writeTook, readTook)
}

// endregion
