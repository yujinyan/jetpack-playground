package me.yujinyan.playground

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.yujinyan.playground.ui.theme.Playground2Theme


class AnimationDemoActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Playground2Theme {
                Column(Modifier.padding(32.dp)) {
                    Spacer(Modifier.size(32.dp))
                    AnimatedButtonDemo()
                    Spacer(Modifier.size(32.dp))
                }
            }
        }
    }
}

@Composable
fun AnimatedButtonDemo() {
    val rotateDegrees = remember { Animatable(0f) }
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    Button(
        modifier = Modifier
            .graphicsLayer(
                scaleX = scale.value,
                scaleY = scale.value,
                rotationZ = rotateDegrees.value,
                transformOrigin = TransformOrigin(0f, 1f)
            ),
        onClick = {
            scope.launch {
                scale.animateTo(1f, animationSpec = keyframes {
                    durationMillis = 1000
                    0.9f at 200
                    1f at 560
                    0.8f at 850 with FastOutLinearInEasing
                })
            }
            scope.launch {
                rotateDegrees.animateTo(0f, animationSpec = keyframes {
                    durationMillis = 1000
                    3f at 200
                    -8f at 560
                    4f at 840 with FastOutSlowInEasing
                })
            }
        }
    ) { Text(text = "Hello") }
}