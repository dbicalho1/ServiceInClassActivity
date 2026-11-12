package edu.temple.basictimer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.Menu
import android.view.MenuItem
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

        startButton.setOnClickListener { toggleTimer() }
        stopButton.setOnClickListener { stopTimer() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                TimerInfo.currentTime.collect { seconds ->
                    timerTextView.text = getString(R.string.timer_value, seconds)
                    updateButtons()
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.timer_actions, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_start).apply {
            title = startButton.text
            isEnabled = startButton.isEnabled
        }
        menu.findItem(R.id.action_stop).isEnabled = stopButton.isEnabled
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_start -> toggleTimer()
            R.id.action_stop -> stopTimer()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun toggleTimer() {
        timerBinder?.let { binder ->
            if (binder.isRunning) binder.pause() else binder.start(DEFAULT_DURATION_SECONDS)
        }
        updateButtons()
    }

    private fun stopTimer() {
        timerBinder?.stop()
        updateButtons()
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
        invalidateOptionsMenu()
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
