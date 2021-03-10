package me.yujinyan.playground

import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.RadioButton
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.jakewharton.picnic.table
import kotlinx.coroutines.launch
import me.yujinyan.playground.atomicity.TestResult
import me.yujinyan.playground.atomicity.TestTarget
import me.yujinyan.playground.atomicity.runTest
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

@ExperimentalTime
val Context.ds: DataStore<Preferences> by preferencesDataStore(DataAtomicityActivity.FILE_NAME)

@ExperimentalTime
class DataAtomicityActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sp = getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
        val options = listOf(
            TestTarget.SP(sp),
            TestTarget.DS(ds),
            TestTarget.DSCachedValue(ds),
            TestTarget.DSReadByDataFirst(ds),
        )

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
                mutableStateOf<List<TestResult>>(emptyList())
            }

            val (duration, setDuration) = remember {
                mutableStateOf<Duration?>(null)
            }

            val (running, setRunning) = remember {
                mutableStateOf(false)
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                options.forEach {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = (it == selectedOption),
                                onClick = { setSelectedOption(it) }
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            modifier = Modifier
                                .clearAndSetSemantics { }
                                .padding(end = padding),
                            selected = (it == selectedOption),
                            onClick = { setSelectedOption(it) })
                        Text(text = it.name)
                    }
                    Spacer(Modifier.size(padding))
                }

                TextField(
                    value = count.toString(),
                    onValueChange = { setCount(it.toIntOrNull() ?: 0) },
                    label = { Text(text = "increment times") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(Modifier.size(padding))
                Button(enabled = !running, onClick = {
                    scope.launch {
                        selectedOption.clear(KEY_NAME)
                        setResult(null)
                        setRunning(true)
                        val testResult: TestResult = selectedOption.runTest(KEY_NAME, count)
                        setRunning(false)
                        setDuration(testResult.duration)
                        setResult(testResult.reading)
                        setResultList(resultList + testResult)
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text(text = "Start") }
                Spacer(Modifier.size(padding))
                if (result != null) Text(text = "Result is $result, took $duration.")
                Spacer(Modifier.size(padding))

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
                                    is TestTarget.DSCachedValue -> "DS-Cached"
                                    is TestTarget.DSReadByDataFirst -> "DS-data.first"
                                }
                                cell(target)
                                cell(it.duration)
                                cell(it.reading)
                            }
                        }
                    }.toString(),
                    fontFamily = FontFamily.Monospace
                )
            }

        }
    }

    companion object {
        const val FILE_NAME: String = "atomicity-test"
        const val KEY_NAME: String = "counter"
    }
}

