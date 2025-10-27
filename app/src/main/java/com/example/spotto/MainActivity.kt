package com.example.spotto

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    private lateinit var firebaseAuth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // HAPUS SEMUA KODE UI (enableEdgeToEdge, setContentView, dll)
        // KITA HANYA JADIKAN INI ROUTER

        firebaseAuth = FirebaseAuth.getInstance()
    }

    override fun onStart() {
        super.onStart()

        // Cek apakah user sudah login
        if (firebaseAuth.currentUser == null) {
            // Belum login, arahkan ke LoginActivity
            startActivity(Intent(this, LoginActivity::class.java))
        } else {
            // Sudah login, arahkan ke HomeActivity
            startActivity(Intent(this, HomeActivity::class.java))
        }

        // Tutup MainActivity agar tidak bisa kembali ke sini
        finish()
    }
}