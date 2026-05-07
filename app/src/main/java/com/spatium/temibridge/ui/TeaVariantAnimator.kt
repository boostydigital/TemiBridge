package com.spatium.deamon.db.temi.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Animator para las variantes del Té con animaciones complejas y coordinadas
 */
class TeaVariantAnimator(private val scope: CoroutineScope) {

    /**
     * Anima un icono de variante del Té con escala, rotación y opacidad
     */
    fun animateTeaVariant(
        view: ImageView,
        duration: Long = 2000,
        delayMs: Long = 0,
    ) {
        scope.launch(Dispatchers.Main) {
            delay(delayMs)

            // Animación de escala
            val scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.1f, 1f).apply {
                this.duration = duration
                interpolator = AccelerateDecelerateInterpolator()
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.REVERSE
            }

            val scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.1f, 1f).apply {
                this.duration = duration
                interpolator = AccelerateDecelerateInterpolator()
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.REVERSE
            }

            // Animación de rotación sutil
            val rotation = ObjectAnimator.ofFloat(view, "rotation", 0f, 3f, -3f, 0f).apply {
                this.duration = duration
                interpolator = AccelerateDecelerateInterpolator()
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.RESTART
            }

            // Animación de opacidad sutil
            val alpha = ObjectAnimator.ofFloat(view, "alpha", 1f, 0.95f, 1f).apply {
                this.duration = duration
                interpolator = AccelerateDecelerateInterpolator()
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.REVERSE
            }

            // Ejecutar todas las animaciones simultáneamente
            AnimatorSet().apply {
                playTogether(scaleX, scaleY, rotation, alpha)
                start()
            }
        }
    }

    /**
     * Anima múltiples variantes del Té con efecto de cascada
     */
    fun animateTeaVariantsCascade(
        variantViews: List<ImageView>,
        duration: Long = 2000,
        cascadeDelayMs: Long = 150,
    ) {
        variantViews.forEachIndexed { index, view ->
            animateTeaVariant(
                view,
                duration = duration,
                delayMs = index * cascadeDelayMs,
            )
        }
    }

    /**
     * Anima un icono con efecto de "pulso" para indicar selección
     */
    fun animatePulseEffect(
        view: View,
        duration: Long = 600,
        pulseCount: Int = 3,
    ) {
        scope.launch(Dispatchers.Main) {
            repeat(pulseCount) {
                val scaleUp = ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.15f).apply {
                    this.duration = duration / 2
                    interpolator = DecelerateInterpolator()
                }

                val scaleDown = ObjectAnimator.ofFloat(view, "scaleX", 1.15f, 1f).apply {
                    this.duration = duration / 2
                    interpolator = DecelerateInterpolator()
                }

                val scaleUpY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.15f).apply {
                    this.duration = duration / 2
                    interpolator = DecelerateInterpolator()
                }

                val scaleDownY = ObjectAnimator.ofFloat(view, "scaleY", 1.15f, 1f).apply {
                    this.duration = duration / 2
                    interpolator = DecelerateInterpolator()
                }

                AnimatorSet().apply {
                    playSequentially(scaleUp, scaleDown)
                    start()
                }

                AnimatorSet().apply {
                    playSequentially(scaleUpY, scaleDownY)
                    start()
                }

                delay(duration)
            }
        }
    }

    /**
     * Anima un icono con efecto de "brillo" (glow)
     */
    fun animateGlowEffect(
        view: View,
        duration: Long = 1500,
    ) {
        scope.launch(Dispatchers.Main) {
            val alphaGlow = ObjectAnimator.ofFloat(view, "alpha", 0.8f, 1f, 0.8f).apply {
                this.duration = duration
                interpolator = AccelerateDecelerateInterpolator()
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.REVERSE
            }

            alphaGlow.start()
        }
    }

    /**
     * Anima un icono con efecto de "flotación" (floating)
     */
    fun animateFloatingEffect(
        view: View,
        duration: Long = 2000,
        floatDistance: Float = 10f,
    ) {
        scope.launch(Dispatchers.Main) {
            val floatUp = ObjectAnimator.ofFloat(view, "translationY", 0f, -floatDistance).apply {
                this.duration = duration / 2
                interpolator = AccelerateDecelerateInterpolator()
            }

            val floatDown = ObjectAnimator.ofFloat(view, "translationY", -floatDistance, 0f).apply {
                this.duration = duration / 2
                interpolator = AccelerateDecelerateInterpolator()
            }

            AnimatorSet().apply {
                playSequentially(floatUp, floatDown)
                start()
            }
        }
    }
}
