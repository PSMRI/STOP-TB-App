package org.piramalswasthya.stoptb.ui.splash

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.appcompat.app.AppCompatActivity
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.ui.login_activity.LoginActivity

class SplashActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        playSplashAnimation()

        handler.postDelayed({
            startActivity(Intent(this, LoginActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }, SPLASH_DURATION_MS)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun playSplashAnimation() {
        val logoContainer = findViewById<View>(R.id.splashLogoContainer)
        val logo = findViewById<View>(R.id.splashLogo)
        val title = findViewById<View>(R.id.tvSplashTitle)
        val subtitle = findViewById<View>(R.id.tvSplashSubtitle)
        val dotOne = findViewById<View>(R.id.splashDotOne)
        val dotTwo = findViewById<View>(R.id.splashDotTwo)
        val dotThree = findViewById<View>(R.id.splashDotThree)

        listOf(logoContainer, title, dotOne, dotTwo, dotThree).forEach {
            it.alpha = 0f
            it.translationY = 26f
        }
        subtitle.alpha = 0f
        logoContainer.scaleX = 0.72f
        logoContainer.scaleY = 0.72f

        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(logoContainer, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(logoContainer, View.SCALE_X, 0.72f, 1f),
                ObjectAnimator.ofFloat(logoContainer, View.SCALE_Y, 0.72f, 1f),
                ObjectAnimator.ofFloat(logoContainer, View.TRANSLATION_Y, 26f, 0f),
                ObjectAnimator.ofFloat(title, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(title, View.TRANSLATION_Y, 26f, 0f),
                ObjectAnimator.ofFloat(dotOne, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(dotTwo, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(dotThree, View.ALPHA, 0f, 1f)
            )
            duration = 720L
            interpolator = OvershootInterpolator(1.05f)
            start()
        }

        ObjectAnimator.ofFloat(logoContainer, View.SCALE_X, 1f, 1.12f, 1f).apply {
            startDelay = 620L
            duration = 820L
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }

        ObjectAnimator.ofFloat(logoContainer, View.SCALE_Y, 1f, 1.12f, 1f).apply {
            startDelay = 620L
            duration = 820L
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }

        ObjectAnimator.ofFloat(logo, View.ROTATION, -10f, 10f, -10f).apply {
            startDelay = 260L
            duration = 900L
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }

        listOf(dotOne, dotTwo, dotThree).forEachIndexed { index, dot ->
            ObjectAnimator.ofFloat(dot, View.SCALE_X, 0.7f, 1.65f, 0.7f).apply {
                startDelay = 520L + (index * 130L)
                duration = 720L
                repeatCount = ObjectAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
                start()
            }
            ObjectAnimator.ofFloat(dot, View.SCALE_Y, 0.7f, 1.65f, 0.7f).apply {
                startDelay = 520L + (index * 130L)
                duration = 720L
                repeatCount = ObjectAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
                start()
            }
            ObjectAnimator.ofFloat(dot, View.TRANSLATION_Y, 0f, -14f, 0f).apply {
                startDelay = 520L + (index * 130L)
                duration = 720L
                repeatCount = ObjectAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
                start()
            }
        }
    }

    companion object {
        private const val SPLASH_DURATION_MS = 2600L
    }
}
