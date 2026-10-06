package com.example.alarm

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AlarmOverlayManager {

    private var overlayView: View? = null
    private var windowManager: WindowManager? = null

    @SuppressLint("InflateParams")
    fun showOverlay(
        context: Context,
        alarmId: Long,
        label: String,
        sound: String,
        vibrate: Boolean,
        math: Boolean,
        snoozeMinutes: Int,
        volume: Float
    ) {
        if (!Settings.canDrawOverlays(context)) {
            return
        }

        dismissOverlay()

        Handler(Looper.getMainLooper()).post {
            try {
                windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

                val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }

                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    layoutType,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    y = 50
                }

                val themedContext = android.view.ContextThemeWrapper(
                    context,
                    android.R.style.Theme_DeviceDefault_Dialog
                )

                // Create programmatically styled floating card
                val card = android.widget.LinearLayout(themedContext).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    setPadding(48, 40, 48, 40)
                    val bg = android.graphics.drawable.GradientDrawable().apply {
                        cornerRadius = 48f
                        setColor(0xFF1E1B2E.toInt())
                        setStroke(3, 0xFF818CF8.toInt())
                    }
                    background = bg
                    elevation = 24f
                }

                val titleRow = android.widget.LinearLayout(themedContext).apply {
                    orientation = android.widget.LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }

                val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                val timeTv = TextView(themedContext).apply {
                    text = timeStr
                    textSize = 28f
                    setTextColor(0xFFFFFFFF.toInt())
                    setTypeface(null, android.graphics.Typeface.BOLD)
                }

                val labelTv = TextView(themedContext).apply {
                    text = "  ⏰ $label"
                    textSize = 18f
                    setTextColor(0xFF818CF8.toInt())
                    setTypeface(null, android.graphics.Typeface.BOLD)
                }

                titleRow.addView(timeTv)
                titleRow.addView(labelTv)
                card.addView(titleRow)

                val subTv = TextView(themedContext).apply {
                    text = if (math) "Desafio de matemática ativo! Toque para resolver." else "Alarme tocando"
                    textSize = 14f
                    setTextColor(0xFFCCCCCC.toInt())
                    setPadding(0, 12, 0, 24)
                }
                card.addView(subTv)

                // Buttons row
                val btnRow = android.widget.LinearLayout(themedContext).apply {
                    orientation = android.widget.LinearLayout.HORIZONTAL
                    gravity = Gravity.END
                }

                val snoozeBtn = Button(themedContext).apply {
                    text = "Soneca (${snoozeMinutes}m)"
                    setTextColor(0xFF818CF8.toInt())
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    setOnClickListener {
                        dismissOverlay()
                        AlarmSoundPlayer.stop(context)
                        AlarmService.stopAlarm(context)
                        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                        nm?.cancel(AlarmReceiver.NOTIFICATION_ID)
                        AlarmScheduler.scheduleSnooze(context, alarmId, label, sound, vibrate, math, snoozeMinutes, false, volume)
                    }
                }

                val actionBtn = Button(themedContext).apply {
                    text = if (math) "Abrir Missão" else "Desligar"
                    setTextColor(0xFFFFFFFF.toInt())
                    val btnBg = android.graphics.drawable.GradientDrawable().apply {
                        cornerRadius = 32f
                        setColor(if (math) 0xFF6366F1.toInt() else 0xFFEF4444.toInt())
                    }
                    background = btnBg
                    setOnClickListener {
                        dismissOverlay()
                        if (math) {
                            val openIntent = Intent(context, AlarmRingingActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                                putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                                putExtra(AlarmScheduler.EXTRA_ALARM_LABEL, label)
                                putExtra(AlarmScheduler.EXTRA_ALARM_SOUND, sound)
                                putExtra(AlarmScheduler.EXTRA_ALARM_VIBRATE, vibrate)
                                putExtra(AlarmScheduler.EXTRA_ALARM_MATH, math)
                                putExtra(AlarmScheduler.EXTRA_ALARM_SNOOZE_MINS, snoozeMinutes)
                                putExtra(AlarmScheduler.EXTRA_ALARM_VOLUME, volume)
                            }
                            context.startActivity(openIntent)
                        } else {
                            AlarmSoundPlayer.stop(context)
                            AlarmService.stopAlarm(context)
                            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                            nm?.cancel(AlarmReceiver.NOTIFICATION_ID)
                        }
                    }
                }

                btnRow.addView(snoozeBtn)
                btnRow.addView(actionBtn)
                card.addView(btnRow)

                // Clicking anywhere on card opens full screen
                card.setOnClickListener {
                    dismissOverlay()
                    val openIntent = Intent(context, AlarmRingingActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                        putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                        putExtra(AlarmScheduler.EXTRA_ALARM_LABEL, label)
                        putExtra(AlarmScheduler.EXTRA_ALARM_SOUND, sound)
                        putExtra(AlarmScheduler.EXTRA_ALARM_VIBRATE, vibrate)
                        putExtra(AlarmScheduler.EXTRA_ALARM_MATH, math)
                        putExtra(AlarmScheduler.EXTRA_ALARM_SNOOZE_MINS, snoozeMinutes)
                        putExtra(AlarmScheduler.EXTRA_ALARM_VOLUME, volume)
                    }
                    context.startActivity(openIntent)
                }

                overlayView = card
                windowManager?.addView(card, params)
            } catch (_: Exception) {
            }
        }
    }

    fun dismissOverlay() {
        Handler(Looper.getMainLooper()).post {
            try {
                if (overlayView != null && windowManager != null) {
                    windowManager?.removeView(overlayView)
                    overlayView = null
                }
            } catch (_: Exception) {
            }
        }
    }
}
