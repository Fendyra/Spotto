package com.example.spotto

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.spotto.databinding.ActivitySplashBinding
import com.google.firebase.auth.FirebaseAuth

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private lateinit var firebaseAuth: FirebaseAuth
    private val splashDelay: Long = 2500

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firebaseAuth = FirebaseAuth.getInstance()

        startAnimations()

        Handler(Looper.getMainLooper()).postDelayed({
            checkUserStatus()
        }, splashDelay)
    }

    private fun startAnimations() {
        binding.ivLogo.apply {
            alpha = 0f
            scaleX = 0.5f
            scaleY = 0.5f
            animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(2000)
                .start()
        }

        val textViews = listOf(binding.tvAppName, binding.tvTagline)
        textViews.forEach { view ->
            view.alpha = 0f
            view.animate()
                .alpha(1f)
                .setDuration(1500)
                .setStartDelay(500)
                .start()
        }
    }

    private fun checkUserStatus() {
        val currentUser = firebaseAuth.currentUser
        if (currentUser != null) {
            // User sudah login, Direct ke HomeActivity
            navigateTo(HomeActivity::class.java)
        } else {
            // User belum login, Direct Login Page
            navigateTo(LoginActivity::class.java)
        }
    }

    private fun navigateTo(activityClass: Class<*>) {
        val intent = Intent(this, activityClass)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}