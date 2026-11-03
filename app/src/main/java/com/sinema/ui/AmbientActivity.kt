package com.sinema.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.sinema.SinemaApp
import com.sinema.model.Scene
import com.sinema.util.GlideAuth
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Ambient gallery: after home-screen idle, a slow Ken Burns slideshow of
 * top-rated scenes with a clock. Any key or touch exits. Respects the
 * screenshot block (FLAG_SECURE is applied app-wide by SinemaApp).
 */
class AmbientActivity : SinemaActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private val slideMs = 12_000L
    private val clockFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    private lateinit var front: ImageView
    private lateinit var back: ImageView
    private lateinit var clock: TextView

    private var scenes: List<Scene> = emptyList()
    private var index = 0
    private var running = false
    private var loaded = false
    private var showRunning = false

    private val slideRunnable = object : Runnable {
        override fun run() {
            showNext()
            handler.postDelayed(this, slideMs)
        }
    }
    private val clockRunnable = object : Runnable {
        override fun run() {
            clock.text = clockFormat.format(Date())
            handler.postDelayed(this, 30_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })
        val density = resources.displayMetrics.density
        val root = FrameLayout(this).apply { setBackgroundColor(0xFF000000.toInt()) }
        front = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        back = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        clock = TextView(this).apply {
            setTextColor(0xCCFFFFFF.toInt())
            textSize = 28f
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END
            ).apply {
                marginEnd = (48 * density).toInt()
                topMargin = (32 * density).toInt()
            }
        }
        root.addView(back)
        root.addView(front)
        root.addView(clock)
        setContentView(root)
        // The gallery must outlast the normal screen timeout.
        root.keepScreenOn = true

        launchSafely(
            "Ambient gallery load",
            // A failed load leaves scenes empty; the old inline catch fell
            // through to the same finish() the empty check performs.
            onError = { finish() }
        ) {
            scenes = SinemaApp.instance.api.findFavoriteScenesPaged(
                page = 1, perPage = 50, sort = "rating", direction = "DESC", unwatchedOnly = false
            ).second.filter { it.duration > 0.0 }
            if (scenes.isEmpty() || isFinishing) {
                finish()
                return@launchSafely
            }
            loaded = true
            if (running) startShow()
        }
    }

    override fun onStart() {
        super.onStart()
        running = true
        if (loaded) startShow()
    }

    override fun onStop() {
        // Stop timers, loads, and animators while invisible; no background
        // image churn.
        running = false
        showRunning = false
        handler.removeCallbacks(slideRunnable)
        handler.removeCallbacks(clockRunnable)
        front.animate().cancel()
        back.animate().cancel()
        super.onStop()
    }

    private fun startShow() {
        // Boolean guard: Handler.hasCallbacks needs API 29 (minSdk is 24).
        if (showRunning) return
        showRunning = true
        showNext()
        handler.postDelayed(slideRunnable, slideMs)
        clock.text = clockFormat.format(Date())
        handler.postDelayed(clockRunnable, 30_000L)
    }

    /** Crossfade and Ken Burns unless reduced motion is enabled. */
    private fun showNext() {
        if (scenes.isEmpty()) return
        val scene = scenes[index % scenes.size]
        index++
        // Swap roles: the old front becomes the fading-out back.
        val oldFront = front
        val oldBack = back
        front = oldBack
        back = oldFront
        front.animate().cancel()
        back.animate().cancel()
        front.alpha = 0f
        Glide.with(this)
            .load(GlideAuth.url(SinemaApp.instance.api, SinemaApp.instance.api.getScreenshotUrl(scene.id)))
            .centerInside()
            .into(front)
        if (SinemaApp.instance.prefs.reduceMotion) {
            front.alpha = 1f
            back.alpha = 0f
            front.scaleX = 1f
            front.scaleY = 1f
            front.translationX = 0f
            front.translationY = 0f
            return
        }
        front.animate().alpha(1f).setDuration(2_000L).setInterpolator(LinearInterpolator()).start()
        back.animate().alpha(0f).setDuration(2_000L).setInterpolator(LinearInterpolator())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    back.animate().setListener(null)
                }
            })
            .start()
        // Slow zoom on the incoming image across the whole slide.
        front.scaleX = 1f
        front.scaleY = 1f
        front.translationX = 0f
        front.translationY = 0f
        front.animate()
            .scaleX(1.08f).scaleY(1.08f)
            .translationXBy(12f * resources.displayMetrics.density)
            .translationYBy(-8f * resources.displayMetrics.density)
            .setDuration(slideMs)
            .setInterpolator(LinearInterpolator())
            .start()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacks(slideRunnable)
        handler.removeCallbacks(clockRunnable)
        super.onDestroy()
    }

}
