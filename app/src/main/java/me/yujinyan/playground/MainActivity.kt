package me.yujinyan.playground

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.yujinyan.playground.ui.theme.Playground2Theme
import java.util.*
import kotlin.time.ExperimentalTime

@ExperimentalTime
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Playground2Theme {
                // A surface container using the 'background' color from the theme
                Surface(color = MaterialTheme.colors.background) {
                    MainScreen()
                }
            }
        }
    }
}

data class Destination(val title: String, val activity: Class<*>)

@ExperimentalTime
val destinations = listOf(
    Destination("DataStore Perf Demo", DataStorePerfActivity::class.java),
    Destination("DataStore Atomicity Demo", DataAtomicityActivity::class.java),
)


@Composable
@Preview(showBackground = true)
@ExperimentalTime
fun MainScreen() {
    val context = LocalContext.current
    val padding = 16.dp
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.size(padding))
        destinations.forEach {
            TextButton(onClick = {
                context.startActivity(Intent(context, it.activity))
            }) { Text(text = it.title.toUpperCase(Locale.ROOT)) }
            Spacer(Modifier.size(padding))
        }
    }
}
