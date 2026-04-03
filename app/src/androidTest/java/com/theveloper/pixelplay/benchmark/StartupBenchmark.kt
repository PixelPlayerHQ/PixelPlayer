package com.theveloper.pixelplay.benchmark

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Placeholder kept in app androidTest so benchmark code does not block unrelated verification.
 * Real macrobenchmark coverage should live in the dedicated benchmark/baseline profile setup.
 */
@Ignore("Macrobenchmark scenarios should run from the dedicated benchmark setup, not app androidTest.")
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @Test
    fun placeholder() = Unit
}

@Ignore("Macrobenchmark profile generation should run from the dedicated benchmark setup, not app androidTest.")
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @Test
    fun placeholder() = Unit
}
