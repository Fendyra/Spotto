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

// SuppressLint diperlukan karena tema Splash biasanya fullscreen tanpa action bar
@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private lateinit var firebaseAuth: FirebaseAuth
    private val splashDelay: Long = 2500 // Durasi total splash screen (ms) - 2 detik animasi + 0.5 detik buffer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firebaseAuth = FirebaseAuth.getInstance()

        // Mulai Animasi
        startAnimations()

        // Jadwalkan Navigasi setelah delay
        Handler(Looper.getMainLooper()).postDelayed({
            checkUserStatus()
        }, splashDelay)
    }

    private fun startAnimations() {
        // Animasi untuk Logo (Fade In + Scale Up)
        binding.ivLogo.apply {
            alpha = 0f // Mulai transparan
            scaleX = 0.5f // Mulai dari setengah ukuran
            scaleY = 0.5f
            animate()
                .alpha(1f) // Menjadi terlihat
                .scaleX(1f) // Kembali ke ukuran normal
                .scaleY(1f)
                .setDuration(2000) // Durasi animasi 2 detik
                .start()
        }

        // Animasi untuk Teks (Fade In) - sedikit delay
        val textViews = listOf(binding.tvAppName, binding.tvTagline)
        textViews.forEach { view ->
            view.alpha = 0f
            view.animate()
                .alpha(1f)
                .setDuration(1500) // Lebih cepat dari logo
                .setStartDelay(500) // Mulai setelah 0.5 detik logo muncul
                .start()
        }
    }

    private fun checkUserStatus() {
        val currentUser = firebaseAuth.currentUser
        if (currentUser != null) {
            // User sudah login, arahkan ke HomeActivity
            navigateTo(HomeActivity::class.java)
        } else {
            // User belum login, arahkan ke LoginActivity
            navigateTo(LoginActivity::class.java)
        }
    }

    private fun navigateTo(activityClass: Class<*>) {
        val intent = Intent(this, activityClass)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        // Tutup SplashActivity agar tidak bisa kembali
        finish()
    }
}