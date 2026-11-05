package edu.temple.basictimer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TimerServiceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var binder: TimerService.TimerBinder
    private var bound = false
    private val connected = CountDownLatch(1)
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            binder = service as TimerService.TimerBinder
            connected.countDown()
        }

        override fun onServiceDisconnected(name: ComponentName?) = Unit
    }

    @Before
    fun bindService() {
        bound = context.bindService(
            Intent(context, TimerService::class.java), connection, Context.BIND_AUTO_CREATE
        )
        assertTrue(bound)
        assertTrue("Service did not connect", connected.await(5, TimeUnit.SECONDS))
    }

    @After
    fun unbindService() {
        if (bound) {
            instrumentation.runOnMainSync { context.unbindService(connection) }
        }
    }

    @Test
    fun countdownFinishesAtZeroAndCanRestart() {
        instrumentation.runOnMainSync {
            binder.start(1)
            assertTrue(binder.isRunning)
            assertEquals(1, TimerInfo.currentTime.value)
            binder.start(20) // A second start must not replace the active timer.
        }
        SystemClock.sleep(1500)
        instrumentation.runOnMainSync {
            assertFalse(binder.isRunning)
            assertFalse(binder.isPaused)
            assertEquals(0, TimerInfo.currentTime.value)
            binder.start(3)
            assertTrue(binder.isRunning)
            assertEquals(3, TimerInfo.currentTime.value)
        }
    }

    @Test
    fun pauseFreezesCountdownAndStartResumesIt() {
        instrumentation.runOnMainSync {
            binder.start(2)
            binder.pause()
            assertTrue(binder.isPaused)
            assertFalse(binder.isRunning)
        }
        SystemClock.sleep(2300)
        instrumentation.runOnMainSync {
            assertEquals(2, TimerInfo.currentTime.value)
            binder.start(20)
            assertTrue(binder.isRunning)
            assertFalse(binder.isPaused)
            assertEquals(2, TimerInfo.currentTime.value)
        }
        SystemClock.sleep(2500)
        instrumentation.runOnMainSync {
            assertEquals(0, TimerInfo.currentTime.value)
            assertFalse(binder.isRunning)
        }
    }

    @Test
    fun stopResetsPausedTimerAndAllowsFreshStart() {
        instrumentation.runOnMainSync {
            binder.stop() // Stopping before starting is safe.
            binder.start(5)
            binder.pause()
            binder.stop()
            assertEquals(0, TimerInfo.currentTime.value)
            assertFalse(binder.isRunning)
            assertFalse(binder.isPaused)
            binder.start(3)
            assertEquals(3, TimerInfo.currentTime.value)
            assertTrue(binder.isRunning)
        }
    }
}
