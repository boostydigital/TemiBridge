package com.spatium.temibridge.ui

import android.content.Intent
import android.os.Bundle
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import coil.load
import com.spatium.temibridge.R

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val logo = findViewById<ImageView>(R.id.splashLogo)
        logo.load("https://cdn.prod.website-files.com/6892254c55b94994927b7f75/68938a95d4da97a6a402f2bd_Spatium-logo-vertical.avif") {
            crossfade(true)
            placeholder(android.R.color.black)
            error(android.R.color.darker_gray)
        }

        // Simple scale + fade animation
        logo.alpha = 0f
        logo.scaleX = 0.85f
        logo.scaleY = 0.85f
        logo.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(900)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                logo.animate()
                    .alpha(1f)
                    .setDuration(300)
                    .withEndAction {
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    }
                    .start()
            }
            .start()
    }
}
