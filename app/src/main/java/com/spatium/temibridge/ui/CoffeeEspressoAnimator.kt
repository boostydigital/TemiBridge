package com.spatium.deamon.db.temi.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Animator para café espresso con efectos complejos de vapor, crema y ripples
 */
class CoffeeEspressoAnimator(private val scope: CoroutineScope) {

    /**
     * Anima el icono de café espresso con múltiples efectos coordinados
     */
    fun animateCoffeeEspresso(
        view: ImageView,
        duration: Long = 3000
    ) {
        scope.launch(Dispatchers.Main) {
            // Animación de escala suave (respiración)
            val scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.08f, 1f).apply {
                this.duration = duration
                interpolator = AccelerateDecelerateInterpolator()
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.REVERSE
            }

            val scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.08f, 1f).apply {
                this.duration = duration
                interpolator = AccelerateDecelerateInterpolator()
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.REVERSE
            }

            // Animación de opacidad sutil (vapor evaporándose)
            val alpha = ObjectAnimator.ofFloat(view, "alpha", 1f, 0.98f, 1f).apply {
                this.duration = duration
                interpolator = AccelerateDecelerateInterpolator()
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.REVERSE
            }

            // Animación de rotación muy sutil (crema girando)
            val rotation = ObjectAnimator.ofFloat(view, "rotation", 0f, 2f, -2f, 0f).apply {
                this.duration = duration * 2
                interpolator = LinearInterpolator()
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.RESTART
            }

            // Ejecutar todas las animaciones simultáneamente
            AnimatorSet().apply {
                playTogether(scaleX, scaleY, alpha, rotation)
                start()
            }
        }
    }

    /**
     * Anima el efecto de vapor ascendente
     */
    fun animateSteamRise(
        view: View,
        duration: Long = 2500
    ) {
        scope.launch(Dispatchers.Main) {
            val translateY = ObjectAnimator.ofFloat(view, "translationY", 0f, -80f).apply {
                this.duration = duration
                interpolator = AccelerateDecelerateInterpolator()
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.REVERSE
            }

            val scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 2.5f, 0.5f).apply {
                this.duration = duration
                interpolator = AccelerateDecelerateInterpolator()
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.REVERSE
            }

            val alpha = ObjectAnimator.ofFloat(view, "alpha", 0.8f, 0f).apply {
                this.duration = duration
                interpolator = AccelerateDecelerateInterpolator()
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.REVERSE
            }

            AnimatorSet().apply {
                playTogether(translateY, scaleX, alpha)
                start()
            }
        }
    }

    /**
     * Anima el efecto de ripples en la crema
     */
    fun animateCremaRipples(
        view: View,
        duration: Long = 3500
    ) {
        scope.launch(Dispatchers.Main) {
            val scaleX = ObjectAnimator.ofFloat(view, "scaleX", 0.3f, 1.2f).apply {
                this.duration = duration
                interpolator = AccelerateDecelerateInterpolator()
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.RESTART
            }

            val scaleY = ObjectAnimator.ofFloat(view, "scaleY", 0.3f, 1.2f).apply {
                this.duration = duration
                interpolator = AccelerateDecelerateInterpolator()
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.RESTART
            }

            val alpha = ObjectAnimator.ofFloat(view, "alpha", 0.8f, 0f).apply {
                this.duration = duration
                interpolator = AccelerateDecelerateInterpolator()
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.RESTART
            }

            AnimatorSet().apply {
                playTogether(scaleX, scaleY, alpha)
                start()
            }
        }
    }

    /**
     * Anima el efecto de brillo (glint) en la taza
     */
    fun animateGlintFlash(
        view: View,
        duration: Long = 6000
    ) {
        scope.launch(Dispatchers.Main) {
            val alpha = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f, 0f).apply {
                this.duration = duration
                interpolator = LinearInterpolator()
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.RESTART
            }

            alpha.start()
        }
    }

    /**
     * Anima el efecto de sombra proyectada (cast shadow)
     */
    fun animateCastShadow(
        view: View,
        duration: Long = 5000
    ) {
        scope.launch(Dispatchers.Main) {
            val scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.05f, 1f).apply {
                this.duration = duration
                interpolator = AccelerateDecelerateInterpolator()
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.REVERSE
            }

            val alpha = ObjectAnimator.ofFloat(view, "alpha", 0.3f, 0.55f, 0.3f).apply {
                this.duration = duration
                interpolator = AccelerateDecelerateInterpolator()
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.REVERSE
            }

            AnimatorSet().apply {
                playTogether(scaleX, alpha)
                start()
            }
        }
    }

    /**
     * Anima el efecto de micro-burbujas en el café
     */
    fun animateMicroBubbles(
        view: View,
        duration: Long = 4000,
        delayMs: Long = 0
    ) {
        scope.launch(Dispatchers.Main) {
            delay(delayMs)

            val translateY = ObjectAnimator.ofFloat(view, "translationY", 0f, -35f).apply {
                this.duration = duration
                interpolator = AccelerateDecelerateInterpolator()
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.RESTART
            }

            val scaleX = ObjectAnimator.ofFloat(view, "scaleX", 0.3f, 1f).apply {
                this.duration = duration
                interpolator = AccelerateDecelerateInterpolator()
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.RESTART
            }

            val alpha = ObjectAnimator.ofFloat(view, "alpha", 0f, 0.7f, 0f).apply {
                this.duration = duration
                interpolator = AccelerateDecelerateInterpolator()
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.RESTART
            }

            AnimatorSet().apply {
                playTogether(translateY, scaleX, alpha)
                start()
            }
        }
    }

    /**
     * Anima el efecto de crema girando (swirl)
     */
    fun animateCremaSwirl(
        view: View,
        duration: Long = 14000,
        clockwise: Boolean = true
    ) {
        scope.launch(Dispatchers.Main) {
            val rotation = if (clockwise) {
                ObjectAnimator.ofFloat(view, "rotation", 0f, 360f)
            } else {
                ObjectAnimator.ofFloat(view, "rotation", 0f, -360f)
            }

            rotation.apply {
                this.duration = duration
                interpolator = LinearInterpolator()
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.RESTART
                start()
            }
        }
    }
}
