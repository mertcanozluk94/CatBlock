package com.catblock.app.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.catblock.app.R
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.random.Random

/**
 * Full-screen blocking overlay built with a ComposeView hosted in WindowManager.
 *
 * Features:
 *  - Picks a random cat asset from a mixed pool (Lottie JSON + MP4 video).
 *  - Picks a random localized break message.
 *  - Self-ticks every second so the countdown never jumps in 2-second steps.
 *  - Swallows every touch so the underlying app cannot be interacted with.
 *  - Optionally speaks the message via the device's TextToSpeech engine.
 */
class CatOverlayManager(private val context: Context) {

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var hostView: View? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var ttsSpoken = false

    val isShowing: Boolean get() = hostView != null

    /** When the break is scheduled to end, in System.currentTimeMillis() epoch. */
    @Volatile
    private var breakUntilEpoch: Long = 0L

    /**
     * Optional callback wired up by the service. Invoked when the user taps the
     * Skip button (which appears 5 seconds after the cat shows). Should clear
     * the active break in the repository.
     */
    var onSkip: (() -> Unit)? = null

    fun show(remainingMillis: Long, voiceEnabled: Boolean) {
        breakUntilEpoch = System.currentTimeMillis() + remainingMillis.coerceAtLeast(0L)
        if (hostView != null) return  // already up — just letting breakUntilEpoch update

        val pickedAsset = pickRandomAsset()
        val message = pickRandomMessage()
        val waitMessage = pickRandomWaitMessage()

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_SYSTEM_ALERT

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }

        // ComposeView outside an Activity needs a manual Lifecycle + SavedStateRegistry.
        val owner = OverlayLifecycleOwner().also {
            it.performRestore(null)
            it.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            it.handleLifecycleEvent(Lifecycle.Event.ON_START)
            it.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
        lifecycleOwner = owner

        val compose = ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setOnTouchListener { _, _ -> true }
            isClickable = true
            isFocusable = true
            isFocusableInTouchMode = true
            setOnKeyListener { _, _, event -> event.action == MotionEvent.ACTION_DOWN }
            setContent {
                OverlayContent(
                    asset = pickedAsset,
                    message = message,
                    waitMessage = waitMessage
                )
            }
        }

        try {
            wm.addView(compose, params)
            hostView = compose
            if (voiceEnabled) {
                ttsSpoken = false
                speakWhenReady(message)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    fun updateCountdown(remainingMillis: Long) {
        breakUntilEpoch = System.currentTimeMillis() + remainingMillis.coerceAtLeast(0L)
    }

    fun hide() {
        hostView?.let {
            try { wm.removeView(it) } catch (_: Throwable) {}
        }
        hostView = null
        lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        lifecycleOwner = null
        tts?.stop()
    }

    fun destroy() {
        hide()
        try { tts?.shutdown() } catch (_: Throwable) {}
        tts = null
        ttsReady = false
    }

    @Composable
    private fun OverlayContent(asset: CatAsset, message: String, waitMessage: String) {
        // 1-second self-ticking countdown
        var nowTick by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(Unit) {
            while (true) {
                nowTick = System.currentTimeMillis()
                delay(1000L)
            }
        }

        // Skip button appears after 5 seconds.
        var skipVisible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            delay(5000L)
            skipVisible = true
        }

        val remainingMs = (breakUntilEpoch - nowTick).coerceAtLeast(0L)
        val totalSec = (remainingMs / 1000L).toInt()
        val mm = totalSec / 60
        val ss = totalSec % 60

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0A0E15),
                            Color(0xFF1A2332),
                            Color(0xFF0A0E15)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                Text(
                    text = message,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                )

                Spacer(Modifier.height(20.dp))

                // Cat — Lottie or MP4 depending on the asset type.
                when (asset) {
                    is CatAsset.Lottie -> CatLottie(asset.rawRes)
                    is CatAsset.Video -> CatVideo(asset.rawRes)
                }

                Spacer(Modifier.height(20.dp))

                Text(
                    text = String.format(Locale.US, "%02d:%02d", mm, ss),
                    color = Color(0xFFF4A261),
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = waitMessage,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(20.dp))

                // Skip button — appears after 5 seconds, faded in.
                androidx.compose.animation.AnimatedVisibility(
                    visible = skipVisible,
                    enter = androidx.compose.animation.fadeIn(
                        animationSpec = androidx.compose.animation.core.tween(400)
                    ) + androidx.compose.animation.scaleIn(
                        animationSpec = androidx.compose.animation.core.tween(400),
                        initialScale = 0.8f
                    )
                ) {
                    val ctx = LocalContext.current
                    androidx.compose.material3.OutlinedButton(
                        onClick = {
                            onSkip?.invoke()
                            hide()
                        },
                        shape = RoundedCornerShape(24.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            Color.White.copy(alpha = 0.35f)
                        ),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White.copy(alpha = 0.85f)
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = ctx.getString(R.string.overlay_skip),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun CatLottie(rawRes: Int) {
        val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(rawRes))
        val progress by animateLottieCompositionAsState(
            composition = composition,
            iterations = LottieConstants.IterateForever,
            isPlaying = true,
            speed = 1f
        )
        LottieAnimation(
            composition = composition,
            progress = { progress },
            modifier = Modifier.size(300.dp)
        )
    }

    @Composable
    private fun CatVideo(rawRes: Int) {
        val ctx = LocalContext.current
        val player = remember {
            ExoPlayer.Builder(ctx).build().apply {
                val uri = Uri.parse("android.resource://${ctx.packageName}/$rawRes")
                setMediaItem(MediaItem.fromUri(uri))
                repeatMode = Player.REPEAT_MODE_ALL
                volume = 0f
                playWhenReady = true
                prepare()
            }
        }
        DisposableEffect(Unit) {
            onDispose { player.release() }
        }

        AndroidView(
            modifier = Modifier.size(300.dp).clip(RoundedCornerShape(24.dp)),
            factory = { c ->
                PlayerView(c).apply {
                    this.player = player
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            }
        )
    }

    // ----------------- helpers -----------------

    private sealed class CatAsset {
        data class Lottie(val rawRes: Int) : CatAsset()
        data class Video(val rawRes: Int) : CatAsset()
    }

    /**
     * Builds the pool of available assets at call time using reflection over R.raw.
     * Files prefixed `cat_lottie_` are Lotties, `cat_video_` and `cat_gif_` are MP4s.
     */
    private fun pickRandomAsset(): CatAsset {
        val pool = mutableListOf<CatAsset>()
        for (field in R.raw::class.java.fields) {
            val name = field.name
            val resId = try { field.getInt(null) } catch (_: Throwable) { continue }
            when {
                name.startsWith("cat_lottie_") -> pool.add(CatAsset.Lottie(resId))
                name.startsWith("cat_video_") || name.startsWith("cat_gif_") ->
                    pool.add(CatAsset.Video(resId))
            }
        }
        if (pool.isEmpty()) {
            // Defensive fallback — should never happen with a normal build.
            return CatAsset.Lottie(0)
        }
        return pool[Random.nextInt(pool.size)]
    }

    private fun pickRandomMessage(): String {
        val arr = context.resources.getStringArray(R.array.break_messages)
        return arr[Random.nextInt(arr.size)]
    }

    private fun pickRandomWaitMessage(): String {
        val arr = context.resources.getStringArray(R.array.overlay_wait_messages)
        return arr[Random.nextInt(arr.size)]
    }

    private fun speakWhenReady(message: String) {
        if (tts == null) {
            tts = TextToSpeech(context.applicationContext) { status ->
                ttsReady = status == TextToSpeech.SUCCESS
                if (ttsReady) {
                    val locale = Locale.getDefault()
                    val res = tts?.setLanguage(locale)
                    if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                        tts?.setLanguage(Locale.ENGLISH)
                    }
                    tts?.setPitch(1.4f)
                    tts?.setSpeechRate(0.95f)
                    if (!ttsSpoken) {
                        tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "catblock-msg")
                        ttsSpoken = true
                    }
                }
            }
        } else if (ttsReady && !ttsSpoken) {
            tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "catblock-msg")
            ttsSpoken = true
        }
    }
}

/**
 * Lifecycle/SavedStateRegistry owner for a ComposeView attached via WindowManager
 * (no Activity in scope). Required by Compose >= 1.x.
 */
private class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    fun performRestore(state: android.os.Bundle?) = savedStateController.performRestore(state)
    fun handleLifecycleEvent(event: Lifecycle.Event) = lifecycleRegistry.handleLifecycleEvent(event)
}
