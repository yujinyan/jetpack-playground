package me.yujinyan.lib

import android.content.Context
import androidx.datastore.core.DataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.yujinyan.lib.datastore.DataStoreTest
import me.yujinyan.lib.datastore.TestValue

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun ds1() = runBlocking {
        coroutineScope {
            launch {
                val test = DataStoreTest.make(context)
                test.write(TestValue.IntValue("t", 100))
            }
        }
        Unit
    }

    @Test
    fun ds2() = runBlocking {
        coroutineScope {
            launch {
                val test = DataStoreTest.make(context)
                test.write(TestValue.IntValue("t", 100))
            }
        }
        Unit
    }
}