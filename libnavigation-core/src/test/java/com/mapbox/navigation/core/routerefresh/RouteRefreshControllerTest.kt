package com.mapbox.navigation.core.routerefresh

import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.navigation.core.trip.session.TripSession
import com.mapbox.navigation.testing.MainCoroutineRule
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import junit.framework.Assert.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class RouteRefreshControllerTest {

    @get:Rule
    val coroutineRule = MainCoroutineRule()

    private val tripSession: TripSession = mockk()
    private val routeRefreshApi: RouteRefreshApi = mockk()
    private val routeRefreshController = RouteRefreshController(tripSession, routeRefreshApi)

    @Before
    fun setup() {
        routeRefreshController.accessToken = "test_access_token"
        routeRefreshController.intervalSeconds = 5
        every { tripSession.getRouteProgress() } returns null
    }

    @Test
    fun `route refresh requires an access token`() = coroutineRule.runBlockingTest {
        routeRefreshController.accessToken = ""
        coroutineRule.testDispatcher.advanceTimeBy(10000)
        verify { routeRefreshApi wasNot Called }
    }

    @Test
    fun `should refresh api over interval`() = coroutineRule.runBlockingTest {
        val mockRoute: DirectionsRoute = mockk(relaxed = true) {
            every { routeOptions() } returns mockk {
                every { requestUuid() } returns "test_id"
            }
        }
        every { tripSession.route } returns mockRoute
        coEvery { routeRefreshApi.refresh(any()) } returns mockRoute

        val job = routeRefreshController.refreshRoute { }
        coroutineRule.testDispatcher.advanceTimeBy(10000)
        job.cancelAndJoin()

        coVerify(exactly = 2) { routeRefreshApi.refresh(any()) }
    }

    @Test
    fun `should refresh after interval completes`() = coroutineRule.runBlockingTest {
        val mockRoute: DirectionsRoute = mockk(relaxed = true) {
            every { routeOptions() } returns mockk {
                every { requestUuid() } returns "test_id"
            }
        }
        every { tripSession.route } returns mockRoute
        coEvery { routeRefreshApi.refresh(any()) } returns mockRoute

        val job = routeRefreshController.refreshRoute { }
        coroutineRule.testDispatcher.advanceTimeBy(10000)
        job.cancelAndJoin()

        coVerify(exactly = 2) { routeRefreshApi.refresh(any()) }
    }

    @Test
    fun `should update interval while running`() = coroutineRule.runBlockingTest {
        val mockRoute: DirectionsRoute = mockk(relaxed = true) {
            every { routeOptions() } returns mockk {
                every { requestUuid() } returns "test_id"
            }
        }
        every { tripSession.route } returns mockRoute
        coEvery { routeRefreshApi.refresh(any()) } returns mockRoute

        // Set the interval to 1 and let 1 second pass by.
        // Set the interval to 10 and let 25 seconds pass by
        // Expect 3 total calls (1 at 1 second, 2 at 10 seconds)
        routeRefreshController.intervalSeconds = 1
        val job = routeRefreshController.refreshRoute { }
        coroutineRule.testDispatcher.advanceTimeBy(1100)
        routeRefreshController.intervalSeconds = 10
        coroutineRule.testDispatcher.advanceTimeBy(20100)
        job.cancelAndJoin()

        coVerify(exactly = 3) { routeRefreshApi.refresh(any()) }
    }

    @Test
    fun `should call api with parameters`() = coroutineRule.runBlockingTest {
        val mockRoute: DirectionsRoute = mockk(relaxed = true) {
            every { routeOptions() } returns mockk {
                every { requestUuid() } returns "test_id"
            }
        }
        every { tripSession.route } returns mockRoute
        val requests = mutableListOf<RouteRefreshRequest>()
        coEvery { routeRefreshApi.refresh(capture(requests)) } returns mockRoute

        val job = routeRefreshController.refreshRoute { }
        coroutineRule.testDispatcher.advanceTimeBy(10000)
        job.cancelAndJoin()

        assertEquals(requests[0].accessToken, "test_access_token")
        assertEquals(requests[0].requestUuid, "test_id")
        assertEquals(requests[0].legIndex, 0)
    }

    @Test
    fun `should handle api failure`() = coroutineRule.runBlockingTest {
        val mockRoute: DirectionsRoute = mockk(relaxed = true) {
            every { routeOptions() } returns mockk {
                every { requestUuid() } returns "test_id"
            }
        }
        every { tripSession.route } returns mockRoute
        coEvery { routeRefreshApi.refresh(any()) } throws RuntimeException("Test exception")

        val job = routeRefreshController.refreshRoute { }
        coroutineRule.testDispatcher.advanceTimeBy(10000)
        job.cancelAndJoin()
    }
}
