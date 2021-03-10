package me.yujinyan.playground.atomicity

import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime


@OptIn(ExperimentalTime::class)
suspend fun TestTarget.runTest(key: String, parallelism: Int): TestResult {
    val d = measureTime {
        coroutineScope {
            repeat(parallelism) {
                launch {
                    increment(key)
                }
            }
        }
    }

    return TestResult(this, d, result(key))
}

@OptIn(ExperimentalTime::class)
data class TestResult(val target: TestTarget, val duration: Duration, val reading: Int)

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

    class DSReadByDataFirst(private val ds: DataStore<Preferences>) : TestTarget() {
        override val name: String = "DataStore data.first"
        override suspend fun increment(key: String) {
            ds.edit {
                it[intPreferencesKey(key)] = (ds.data.first()[intPreferencesKey(key)] ?: 0) + 1
            }
        }

        override suspend fun result(key: String): Int =
            ds.data.first()[intPreferencesKey(key)]!!

        override suspend fun clear(key: String) {
            ds.edit { it.clear() }
        }
    }

    class DSCachedValue(private val ds: DataStore<Preferences>) : TestTarget(), CoroutineScope {
        override val name: String = "DataStore with cached value"
        override val coroutineContext: CoroutineContext = Job()

        private val deferredCachedData = async {
            ds.data.stateIn(this + Job())
        }

        private suspend fun cachedData() = deferredCachedData.await()

        override suspend fun increment(key: String) {
            ds.edit {
                it[intPreferencesKey(key)] =
                    (cachedData().value[intPreferencesKey(key)] ?: 0 + 1)
            }
        }

        override suspend fun result(key: String): Int =
            cachedData().value[intPreferencesKey(key)]!!

        override suspend fun clear(key: String) {
            ds.edit { it.clear() }
        }

    }

    abstract val name: String
    abstract suspend fun increment(key: String)
    abstract suspend fun result(key: String): Int
    abstract suspend fun clear(key: String)
}