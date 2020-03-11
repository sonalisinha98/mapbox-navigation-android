package com.mapbox.navigation.utils.timer

import com.mapbox.navigation.testing.MainCoroutineRule
import io.mockk.Called
import io.mockk.coVerify
import io.mockk.mockk
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class MapboxTimerTest {

    @get:Rule
    val coroutineRule = MainCoroutineRule()

    private val mapboxTimer = MapboxTimer()
    private val mockLambda: () -> Unit = mockk(relaxed = true)

    @Test
    fun `should not call before interval`() = coroutineRule.runBlockingTest {
        mapboxTimer.routeRefreshInterval = TimeUnit.MINUTES.toMillis(5)
        val job = mapboxTimer.startRouteRefresh(mockLambda)
        coroutineRule.testDispatcher.advanceTimeBy(TimeUnit.MINUTES.toMillis(4))
        job.cancelAndJoin()

        coVerify { mockLambda wasNot Called }
    }

    @Test
    fun `should call after interval`() = coroutineRule.runBlockingTest {
        mapboxTimer.routeRefreshInterval = TimeUnit.MINUTES.toMillis(5)
        val job = mapboxTimer.startRouteRefresh(mockLambda)
        coroutineRule.testDispatcher.advanceTimeBy(TimeUnit.MINUTES.toMillis(6))
        job.cancelAndJoin()

        coVerify(exactly = 1) { mockLambda.invoke() }
    }

    @Test
    fun `should call after multiple times`() = coroutineRule.runBlockingTest {
        mapboxTimer.routeRefreshInterval = TimeUnit.MINUTES.toMillis(5)
        val job = mapboxTimer.startRouteRefresh(mockLambda)
        coroutineRule.testDispatcher.advanceTimeBy(TimeUnit.MINUTES.toMillis(26))
        job.cancelAndJoin()

        coVerify(exactly = 5) { mockLambda.invoke() }
    }

    @Test
    fun `should update interval dynamically`() = coroutineRule.runBlockingTest {
        mapboxTimer.routeRefreshInterval = TimeUnit.MINUTES.toMillis(1)
        val job = mapboxTimer.startRouteRefresh(mockLambda)
        coroutineRule.testDispatcher.advanceTimeBy(TimeUnit.MINUTES.toMillis(1))
        mapboxTimer.routeRefreshInterval = TimeUnit.MINUTES.toMillis(10)
        coroutineRule.testDispatcher.advanceTimeBy(TimeUnit.MINUTES.toMillis(10))
        job.cancelAndJoin()

        coVerify(exactly = 2) { mockLambda.invoke() }
    }

    @Test
    fun `should stop when canceled`() = coroutineRule.runBlockingTest {
        mapboxTimer.routeRefreshInterval = TimeUnit.MINUTES.toMillis(5)
        val job = mapboxTimer.startRouteRefresh(mockLambda)
        coroutineRule.testDispatcher.advanceTimeBy(TimeUnit.MINUTES.toMillis(6))
        mapboxTimer.stopJobs()
        coroutineRule.testDispatcher.advanceTimeBy(TimeUnit.MINUTES.toMillis(30))
        job.cancelAndJoin()

        coVerify(exactly = 1) { mockLambda.invoke() }
    }

    @Test
    fun `should subscribe to jobs separately`() = coroutineRule.runBlockingTest {
        val mockLambda1: () -> Unit = mockk(relaxed = true)
        val mockLambda2: () -> Unit = mockk(relaxed = true)

        mapboxTimer.routeRefreshInterval = 5000
        val job1 = mapboxTimer.startRouteRefresh(mockLambda1)
        coroutineRule.testDispatcher.advanceTimeBy(5001)
        val job2 = mapboxTimer.startRouteRefresh(mockLambda2)
        coroutineRule.testDispatcher.advanceTimeBy(5001)
        job1.cancelAndJoin()
        job2.cancelAndJoin()

        coVerify(exactly = 2) { mockLambda1.invoke() }
        coVerify(exactly = 1) { mockLambda2.invoke() }
    }

    @Test
    fun `should stop and restart normally`() = coroutineRule.runBlockingTest {
        mapboxTimer.routeRefreshInterval = TimeUnit.MINUTES.toMillis(5)

        // Should emit 1
        val job1 = mapboxTimer.startRouteRefresh(mockLambda)
        coroutineRule.testDispatcher.advanceTimeBy(TimeUnit.MINUTES.toMillis(6))

        // Should emit 0 because the jobs are stopped
        mapboxTimer.stopJobs()
        coroutineRule.testDispatcher.advanceTimeBy(TimeUnit.MINUTES.toMillis(30))

        // Should emit 1 after restart
        val job2 = mapboxTimer.startRouteRefresh(mockLambda)
        coroutineRule.testDispatcher.advanceTimeBy(TimeUnit.MINUTES.toMillis(6))

        job1.cancelAndJoin()
        job2.cancelAndJoin()

        coVerify(exactly = 2) { mockLambda.invoke() }
    }
}
