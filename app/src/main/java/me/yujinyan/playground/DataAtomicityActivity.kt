package me.yujinyan.playground

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.createDataStore
import com.jakewharton.picnic.table
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

@ExperimentalTime
class DataAtomicityActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sp = getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
        val ds = createDataStore(FILE_NAME)
        val options = listOf(TestTarget.SP(sp), TestTarget.DS(ds))

        setContent {
            val padding = 16.dp
            val scope = rememberCoroutineScope()
            val (selectedOption, setSelectedOption) = remember {
                mutableStateOf(options.first())
            }
            val (count, setCount) = remember {
                mutableStateOf(100)
            }
            val (result, setResult) = remember {
                mutableStateOf<Int?>(null)
            }

            val (resultList, setResultList) = remember {
                mutableStateOf<List<Result>>(emptyList())
            }

            val (duration, setDuration) = remember {
                mutableStateOf<Duration?>(null)
            }

            val (running, setRunning) = remember {
                mutableStateOf(false)
            }

            Column(
                Modifier.fillMaxWidth().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                options.forEach {
                    Row(
                        Modifier.fillMaxWidth().selectable(
                            selected = (it == selectedOption),
                            onClick = { setSelectedOption(it) }
                        ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            modifier = Modifier.clearAndSetSemantics { }.padding(end = padding),
                            selected = (it == selectedOption),
                            onClick = { setSelectedOption(it) })
                        Text(text = it.name)
                    }
                    Spacer(Modifier.preferredSize(padding))
                }

                TextField(
                    value = count.toString(),
                    onValueChange = { setCount(it.toIntOrNull() ?: 0) },
                    label = { Text(text = "increment times") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(Modifier.preferredSize(padding))
                Button(enabled = !running, onClick = {
                    scope.launch {
                        selectedOption.clear(KEY_NAME)
                        setResult(null)
                        setRunning(true)
                        val d = measureTime {
                            coroutineScope {
                                repeat(count) {
                                    launch {
                                        selectedOption.increment(KEY_NAME)
                                    }
                                }
                            }
                        }
                        setRunning(false)
                        val r = selectedOption.result(KEY_NAME)
                        setDuration(d); setResult(r)
                        setResultList(resultList + Result(selectedOption, d, r))
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text(text = "Start") }
                Spacer(Modifier.preferredSize(padding))
                if (result != null) Text(text = "Result is $result, took $duration.")
                Spacer(Modifier.preferredSize(padding))

                Text(
                    table {
                        cellStyle { paddingLeft = 1; paddingRight = 1 }
                        row {
                            cell("target")
                            cell("duration")
                            cell("result")
                        }
                        resultList.forEach {
                            row {
                                val target = when (it.target) {
                                    is TestTarget.DS -> "DS"
                                    is TestTarget.SP -> "SP"
                                }
                                cell(target)
                                cell(it.duration)
                                cell(it.result)
                            }
                        }
                    }.toString(),
                    fontFamily = FontFamily.Monospace
                )
            }

        }
    }

    data class Result(val target: TestTarget, val duration: Duration, val result: Int)

    sealed class TestTarget {
        class SP(private val sp: SharedPreferences) : TestTarget() {
            override val name: String = "SharedPreferences"
            override suspend fun increment(key: String) = withContext(Dispatchers.IO) {
                sp.edit(commit = true) {
                    putInt(key, sp.getInt(key, 0) + 1)
                }
            }

            override suspend fun result(key: String): Int = sp.getInt(key, 0)
            override suspend fun clear(key: String) {
                sp.edit(commit = true) { clear() }
            }
        }

        class DS(private val ds: DataStore<Preferences>) : TestTarget() {
            override val name: String = "DataStore"
            override suspend fun increment(key: String) {
                ds.edit {
                    it[intPreferencesKey(key)] = (it[intPreferencesKey(key)] ?: 0) + 1
                }
            }

            override suspend fun result(key: String): Int =
                ds.data.first()[intPreferencesKey(key)]!!

            override suspend fun clear(key: String) {
                ds.edit { it.clear() }
            }
        }

        abstract val name: String
        abstract suspend fun increment(key: String)
        abstract suspend fun result(key: String): Int
        abstract suspend fun clear(key: String)
    }

    companion object {
        const val FILE_NAME: String = "atomicity-test"
        const val KEY_NAME: String = "counter"
    }
}

