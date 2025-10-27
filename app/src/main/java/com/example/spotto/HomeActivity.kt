package com.example.spotto

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.spotto.databinding.ActivityHomeBinding
import com.google.firebase.auth.FirebaseAuth

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var firebaseAuth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firebaseAuth = FirebaseAuth.getInstance()

        val currentUser = firebaseAuth.currentUser

        if(currentUser == null) {
            // Jika entah bagaimana user bisa ke sini tanpa login, tendang ke Login
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        binding.tvWelcome.text = "Selamat Datang, ${currentUser.email}"

        binding.btnLogout.setOnClickListener {
            firebaseAuth.signOut()
            Toast.makeText(this, "Logout Berhasil", Toast.LENGTH_SHORT).show()

            // Kirim ke LoginActivity dan hapus stack
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
    }
}