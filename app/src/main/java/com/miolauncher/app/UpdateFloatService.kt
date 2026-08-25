package com.miolauncher.app

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.TextView

/**
 * 更新下载悬浮窗：最小化下载时显示小悬浮球（进度百分比），点击回到下载界面。
 * 悬浮窗位置贴近屏幕边缘，不遮挡操作。
 */
class UpdateFloatService : Service() {

    companion object {
        private const val TAG = "UpdateFloat"
        private const val ACTION_SHOW = "com.miolauncher.app.FLOAT_SHOW"
        private const val ACTION_HIDE = "com.miolauncher.app.FLOAT_HIDE"

        @Volatile
        private var shown = false

        fun canShow(context: Context): Boolean =
            if (Build.VERSION.SDK_INT >= 23) {
                android.provider.Settings.canDrawOverlays(context)
            } else {
                true
            }

        /** 显示悬浮窗。 */
        fun show(context: Context) {
            if (!canShow(context)) return
            val intent = Intent(context, UpdateFloatService::class.java).setAction(ACTION_SHOW)
            context.startService(intent)
        }

        /** 隐藏悬浮窗。 */
        fun hide(context: Context) {
            try {
                context.stopService(Intent(context, UpdateFloatService::class.java))
            } catch (_: Exception) {
            }
        }

        fun isShown(): Boolean = shown
    }

    private var wm: WindowManager? = null
    private var floatView: android.view.View? = null
    private var params: WindowManager.LayoutParams? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val progressTick = object : Runnable {
        override fun run() {
            updateView()
            mainHandler.postDelayed(this, 1000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HIDE -> {
                removeFloat()
                stopSelf()
            }
            else -> {
                if (!shown) {
                    showFloat()
                }
                mainHandler.removeCallbacks(progressTick)
                mainHandler.post(progressTick)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacks(progressTick)
        removeFloat()
        shown = false
    }

    private fun showFloat() {
        try {
            wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            floatView = inflater.inflate(R.layout.view_update_float, null)
            val type = if (Build.VERSION.SDK_INT >= 26) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            }
            params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                x = 16
                y = 0
            }
            wm?.addView(floatView, params)
            floatView?.setOnClickListener {
                // 点击：回到下载界面
                val intent = Intent(this, PatchDownloadActivity::class.java)
                    .putExtra("mode", "apk")
                    .putExtra("resume", true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                startActivity(intent)
                hide(this)
            }
            floatView?.setOnTouchListener(object : android.view.View.OnTouchListener {
                private var startX = 0
                private var startY = 0
                private var touchX = 0f
                private var touchY = 0f
                private var moved = false
                override fun onTouch(v: android.view.View, e: android.view.MotionEvent): Boolean {
                    when (e.action) {
                        android.view.MotionEvent.ACTION_DOWN -> {
                            startX = params?.x ?: 0
                            startY = params?.y ?: 0
                            touchX = e.rawX
                            touchY = e.rawY
                            moved = false
                        }
                        android.view.MotionEvent.ACTION_MOVE -> {
                            val dx = (e.rawX - touchX).toInt()
                            val dy = (e.rawY - touchY).toInt()
                            if (kotlin.math.abs(dx) > 10 || kotlin.math.abs(dy) > 10) {
                                moved = true
                                params?.x = startX + dx
                                params?.y = startY + dy
                                try {
                                    wm?.updateViewLayout(v, params)
                                } catch (_: Exception) {
                                }
                            }
                        }
                        android.view.MotionEvent.ACTION_UP -> {
                            if (!moved) {
                                v.performClick()
                            }
                        }
                    }
                    return true
                }
            })
            shown = true
        } catch (e: Exception) {
            android.util.Log.e(TAG, "showFloat failed: ${e.message}")
        }
    }

    private fun removeFloat() {
        try {
            floatView?.let { wm?.removeView(it) }
        } catch (_: Exception) {
        }
        floatView = null
    }

    private fun updateView() {
        val v = floatView ?: return
        val s = UpdateDownloadService.state
        val pct = if (s.total > 0) s.percent else 0
        val txt = v.findViewById<TextView>(R.id.float_pct)
        txt.text = "$pct%"
        val sub = v.findViewById<TextView>(R.id.float_state)
        sub.text = when {
            !s.running && s.finished && s.success -> "待安装"
            !s.running && s.finished -> "失败"
            else -> "更新中"
        }
    }
}
