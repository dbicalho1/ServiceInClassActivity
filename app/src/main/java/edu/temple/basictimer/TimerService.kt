package edu.temple.basictimer

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.CountDownTimer
import android.os.IBinder
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object TimerInfo {
    private val _currentTime = MutableStateFlow(0)
    val currentTime = _currentTime.asStateFlow()

    fun postNewTime(currentTime: Int) {
        _currentTime.value = currentTime
    }
}

class TimerService : Service() {

    private var timer: CountDownTimer? = null
    private var remainingMillis = 0L
    private var deadlineMillis = 0L
    private var isRunning = false
    private var paused = false
    private val binder = TimerBinder()

    inner class TimerBinder : Binder() {
        val isRunning: Boolean
            get() = this@TimerService.isRunning

        val isPaused: Boolean
            get() = paused

        // Starting a paused timer resumes its remaining time.
        fun start(startValue: Int) {
            if (isRunning) return
            if (!paused) {
                require(startValue > 0) { "Timer duration must be positive" }
                remainingMillis = startValue * 1000L
            }
            startCountdown()
        }

        fun stop() {
            stopCountdown()
        }

        fun pause() {
            if (paused) {
                startCountdown()
            } else if (isRunning) {
                remainingMillis = (deadlineMillis - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
                timer?.cancel()
                timer = null
                this@TimerService.isRunning = false
                paused = remainingMillis > 0L
                publishRemainingTime()
            }
        }
    }

    override fun onBind(intent: Intent): IBinder = binder

    private fun startCountdown() {
        paused = false
        isRunning = true
        deadlineMillis = SystemClock.elapsedRealtime() + remainingMillis
        publishRemainingTime()
        timer = object : CountDownTimer(remainingMillis, 100L) {
            override fun onTick(millisUntilFinished: Long) {
                remainingMillis = millisUntilFinished
                publishRemainingTime()
            }

            override fun onFinish() {
                stopCountdown()
            }
        }.start()
    }

    private fun publishRemainingTime() {
        TimerInfo.postNewTime(((remainingMillis + 999L) / 1000L).toInt())
    }

    private fun stopCountdown() {
        timer?.cancel()
        timer = null
        remainingMillis = 0L
        isRunning = false
        paused = false
        TimerInfo.postNewTime(0)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        stopCountdown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        stopCountdown()
        super.onDestroy()
    }
}
