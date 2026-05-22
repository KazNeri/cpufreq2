package com.yin.cpufreq2.core.locale

import android.content.Context
import android.content.SharedPreferences
import android.view.Gravity


object ConfigManager {

    private const val PREFS_NAME = "cpu_float_config"
    private const val KEY_SCALE = "scale_factor"
    private const val KEY_MAX_HEIGHT = "max_height"
    private const val KEY_ORIENTATION = "orientation"
    private const val KEY_ROTATION = "rotation"
    private const val KEY_REVERSE_ORDER = "reverse_order"
    private const val KEY_FRESH_TIME = "fresh_time"

    const val DEFAULT_SCALE = 1.0f
    const val DEFAULT_MAX_HEIGHT = 160f
    const val DEFAULT_ORIENTATION = 0
    const val DEFAULT_ROTATION = 0
    const val DEFAULT_REVERSE_ORDER = false

    const val MIN_SCALE = 0.33f
    const val MAX_SCALE = 3.0f

    const val ORIENT_TOP_LEFT = 0
    const val ORIENT_TOP_RIGHT = 1
    const val ORIENT_BOTTOM_LEFT = 2
    const val ORIENT_BOTTOM_RIGHT = 3

    const val ROTATION_0 = 0
    const val ROTATION_90 = 90
    const val ROTATION_180 = 180
    const val ROTATION_270 = 270

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getScale(): Float = prefs.getFloat(KEY_SCALE, DEFAULT_SCALE).coerceIn(MIN_SCALE, MAX_SCALE)
    fun getMaxHeight(): Float = prefs.getFloat(KEY_MAX_HEIGHT, DEFAULT_MAX_HEIGHT)
    fun getOrientation(): Int = prefs.getInt(KEY_ORIENTATION, DEFAULT_ORIENTATION)
    fun getRotation(): Int = prefs.getInt(KEY_ROTATION, DEFAULT_ROTATION)
    fun isReverseOrder(): Boolean = prefs.getBoolean(KEY_REVERSE_ORDER, DEFAULT_REVERSE_ORDER)

    fun saveScale(scale: Float) = prefs.edit().putFloat(KEY_SCALE, scale.coerceIn(MIN_SCALE, MAX_SCALE)).apply()
    fun saveMaxHeight(height: Float) = prefs.edit().putFloat(KEY_MAX_HEIGHT, height).apply()
    fun saveOrientation(orientation: Int) = prefs.edit().putInt(KEY_ORIENTATION, orientation).apply()
    fun saveRotation(rotation: Int) = prefs.edit().putInt(KEY_ROTATION, rotation).apply()
    fun saveReverseOrder(reverse: Boolean) = prefs.edit().putBoolean(KEY_REVERSE_ORDER, reverse).apply()

    fun getGravityAndOffset(context: Context, viewWidth: Int, viewHeight: Int): Pair<Int, Pair<Int, Int>> {
        val screenWidth = context.resources.displayMetrics.widthPixels
        val screenHeight = context.resources.displayMetrics.heightPixels
        val margin = dpToPx(context, 20f)
        return when (getOrientation()) {
            ORIENT_TOP_LEFT -> Gravity.TOP or Gravity.START to Pair(margin, margin)
            ORIENT_TOP_RIGHT -> Gravity.TOP or Gravity.END to Pair(-margin, margin)
            ORIENT_BOTTOM_LEFT -> Gravity.BOTTOM or Gravity.START to Pair(margin, -margin)
            ORIENT_BOTTOM_RIGHT -> Gravity.BOTTOM or Gravity.END to Pair(-margin, -margin)
            else -> Gravity.TOP or Gravity.START to Pair(margin, margin)
        }
    }

    private fun dpToPx(context: Context, dp: Float): Int = (dp * context.resources.displayMetrics.density).toInt()

}