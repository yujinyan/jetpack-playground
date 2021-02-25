package me.yujinyan.playground

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.jakewharton.picnic.table
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import me.yujinyan.lib.datastore.Report
import me.yujinyan.lib.datastore.runTest
import me.yujinyan.lib.datastore.testFactories
import me.yujinyan.playground.ui.theme.Playground2Theme
import kotlin.time.ExperimentalTime

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
        AndroidView(
            factory = { context ->
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
        Spacer(Modifier.size(padding))
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
        Spacer(Modifier.size(padding))
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
        Spacer(Modifier.size(padding))
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
