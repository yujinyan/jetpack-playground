package me.yujinyan.benchmark

import android.content.Context
import android.util.Log
import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.datastore.core.DataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import me.yujinyan.lib.datastore.DataStoreReadOptimizedStateFlowTest
import me.yujinyan.lib.datastore.DataStoreTest
import me.yujinyan.lib.datastore.SharedPreferencesTest
import me.yujinyan.lib.datastore.TestValue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Benchmark, which will execute on an Android device.
 *
 * The body of [BenchmarkRule.measureRepeated] is measured in a loop, and Studio will
 * output the result. Modify your code to see how it affects performance.
 */
@RunWith(AndroidJUnit4::class)
class ExampleBenchmark {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    val testSeq = sequence {
        repeat(50) { yield(TestValue.IntValue("int key $it", it)) }
        repeat(50) { yield(TestValue.StringValue("string key $it", "value $it")) }
    }

    @Test
    fun dsWrite() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        coroutineScope {
            val test = DataStoreTest.make(context)
            benchmarkRule.measureRepeated {
                runBlocking {
                    testSeq.forEach { test.write(it) }
                }
            }
            test.cleanUp()
            cancel()
        }
    }

    @Test
    fun spWrite() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val test = SharedPreferencesTest.make(context)
        benchmarkRule.measureRepeated {
            runBlocking {
                testSeq.forEach { test.write(it) }
            }
        }
        test.cleanUp()
    }

    @Test
    fun spRead() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val test = SharedPreferencesTest.make(context)
        testSeq.forEach {
            test.write(it)
        }
        benchmarkRule.measureRepeated {
            runBlocking {
                testSeq.forEach { test.read(it) }
            }
        }
        test.cleanUp()
    }

    @Test
    fun dsReadWithStateFlow() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()

        coroutineScope {
            val test = DataStoreReadOptimizedStateFlowTest.make(context)
            testSeq.forEach {
                test.write(it)
            }
            benchmarkRule.measureRepeated {
                runBlocking {
                    testSeq.forEach { test.read(it) }
                }
            }
            test.cleanUp()
            cancel()
        }
    }
}