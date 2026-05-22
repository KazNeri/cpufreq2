package com.yin.cpufreq2.core.locale

import android.annotation.SuppressLint
import android.content.Context
import android.content.Context.WINDOW_SERVICE
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.ui.res.stringResource
import com.yin.cpufreq2.R
import com.yin.cpufreq2.core.cpu.CpuUsageMonitor
import com.yin.cpufreq2.feature.overlay.CpuBarView
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@SuppressLint("StaticFieldLeak")
object CpuFloatManager {
    var enabled = false
        private set

    private lateinit var appContext: Context
    private lateinit var windowManager: WindowManager
    private var floatingView: CpuBarView? = null
    private val monitor = CpuUsageMonitor()

    // 不再提前初始化，改为在 start 中创建
    private var scheduler: ScheduledExecutorService? = null
    private var scheduledFuture: ScheduledFuture<*>? = null
    private val isUpdating = AtomicBoolean(false)

    fun init(context: Context) {
        this.appContext = context
    }

    @Synchronized
    fun start() {
        if (enabled) return
        ConfigManager.init(appContext)

        if (!checkOverlayPermission(appContext)) {
            requestOverlayPermission(appContext)
            return
        }

        windowManager = appContext.getSystemService(WINDOW_SERVICE) as WindowManager

        // 每次启动都创建新的线程池，避免 shutdown 后复用
        scheduler = Executors.newSingleThreadScheduledExecutor()
        isUpdating.set(false)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }

        floatingView = CpuBarView(appContext, params, windowManager)
        windowManager.addView(floatingView, params)

        floatingView?.viewTreeObserver?.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                floatingView?.viewTreeObserver?.removeOnGlobalLayoutListener(this)
                applyOrientation()
            }
        })

        scheduledFuture = scheduler?.scheduleAtFixedRate(
            {
                if (!isUpdating.compareAndSet(false, true)) return@scheduleAtFixedRate
                try {
                    val usages = monitor.getUsages()
                    floatingView?.post {
                        floatingView?.updateData(usages)
                    }
                } catch (e: Exception) {
                    Log.e("CpuFloatManager", "Update failed", e)
                } finally {
                    isUpdating.set(false)
                }
            },
            0, 500, TimeUnit.MILLISECONDS
        )

        enabled = true
    }

    @Synchronized
    fun stop() {
        if (!enabled) return
        scheduledFuture?.cancel(true)
        scheduledFuture = null
        scheduler?.shutdown()
        scheduler = null
        floatingView?.let { windowManager.removeView(it) }
        floatingView = null
        enabled = false
    }

    private fun applyOrientation() {
        val view = floatingView ?: return
        val (gravity, offset) = ConfigManager.getGravityAndOffset(appContext, view.width, view.height)
        val params = view.layoutParams as WindowManager.LayoutParams
        params.gravity = gravity
        params.x = offset.first
        params.y = offset.second
        windowManager.updateViewLayout(view, params)
    }

    private fun checkOverlayPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    private fun requestOverlayPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        }
        Toast.makeText(context, context.getString(R.string.permission_request), Toast.LENGTH_SHORT).show()
    }

    fun update() {
        floatingView?.updateConfig()
    }
}