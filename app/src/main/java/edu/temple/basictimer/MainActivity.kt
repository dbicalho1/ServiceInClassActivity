package edu.temple.basictimer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private var timerBinder: TimerService.TimerBinder? = null
    private var bindingRequested = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            timerBinder = service as? TimerService.TimerBinder
            updateButtons()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            timerBinder = null
            updateButtons()
        }
    }

    private val startButton: Button by lazy { findViewById(R.id.startButton) }
    private val stopButton: Button by lazy { findViewById(R.id.stopButton) }
    private val timerTextView: TextView by lazy { findViewById(R.id.textView) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        updateButtons()

        bindingRequested = bindService(
            Intent(this, TimerService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )

        startButton.setOnClickListener {
            timerBinder?.let { binder ->
                if (binder.isRunning) binder.pause() else binder.start(DEFAULT_DURATION_SECONDS)
            }
            updateButtons()
        }

        stopButton.setOnClickListener {
            timerBinder?.stop()
            updateButtons()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                TimerInfo.currentTime.collect { seconds ->
                    timerTextView.text = getString(R.string.timer_value, seconds)
                    updateButtons()
                }
            }
        }
    }

    private fun updateButtons() {
        val binder = timerBinder
        startButton.isEnabled = binder != null
        startButton.setText(
            when {
                binder?.isPaused == true -> R.string.resume
                binder?.isRunning == true -> R.string.pause
                else -> R.string.start
            }
        )
        stopButton.isEnabled = binder != null && (binder.isRunning || binder.isPaused)
    }

    override fun onDestroy() {
        if (bindingRequested) {
            unbindService(serviceConnection)
            bindingRequested = false
        }
        timerBinder = null
        super.onDestroy()
    }

    private companion object {
        const val DEFAULT_DURATION_SECONDS = 10
    }
}
