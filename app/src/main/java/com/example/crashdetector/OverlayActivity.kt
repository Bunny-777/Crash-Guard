package com.example.crashdetector

import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.view.WindowManager
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class OverlayActivity : AppCompatActivity() {

    private var countDownTimer: CountDownTimer? = null
    private lateinit var countdownText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnSendNow: Button
    private lateinit var btnCancel: Button
    private var ringtone: Ringtone? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Make sure window floats above other apps
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        } else {
            @Suppress("DEPRECATION")
            window.setType(WindowManager.LayoutParams.TYPE_PHONE)
        }

        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        setContentView(R.layout.overlay_layout)

        countdownText = findViewById(R.id.tv_countdown)
        progressBar = findViewById(R.id.progressBar)
        btnSendNow = findViewById(R.id.btn_send_now)
        btnCancel = findViewById(R.id.btn_cancel)

        // Play alarm sound to alert the user immediately
        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ringtone = RingtoneManager.getRingtone(applicationContext, alarmUri)
            ringtone?.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Start the 10-second countdown
        startCountdown()

        btnSendNow.setOnClickListener {
            stopAlertSound()
            countDownTimer?.cancel()
            SmsUtils.sendEmergency(this, false)
            finishAffinity()
        }

        btnCancel.setOnClickListener {
            stopAlertSound()
            countDownTimer?.cancel()
            finish()
        }
    }

    private fun startCountdown() {
        countDownTimer = object : CountDownTimer(10_000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = (millisUntilFinished / 1000).toInt()
                countdownText.text = "${seconds}s"
                progressBar.progress = seconds
            }

            override fun onFinish() {
                stopAlertSound()
                SmsUtils.sendEmergency(this@OverlayActivity, false)
                finishAffinity()
            }
        }
        countDownTimer?.start()
    }

    private fun stopAlertSound() {
        try {
            ringtone?.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlertSound()
        countDownTimer?.cancel()
    }
}
