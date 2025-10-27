package com.example.spotto

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast // Pastikan import ini ada
import android.widget.Toast.* // Dan import wildcard ini
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.spotto.databinding.ActivityAddSpotBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class AddSpotActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddSpotBinding
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // Variabel untuk menyimpan lokasi yang didapat
    private var currentLocation: Location? = null

    private companion object {
        const val TAG = "AddSpotActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddSpotBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inisialisasi Firebase
        firebaseAuth = FirebaseAuth.getInstance()
        firestore = Firebase.firestore

        // Inisialisasi Location Client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Setup Toolbar
        setupToolbar()

        // Minta Izin Lokasi & Ambil Lokasi
        requestLocationPermission()

        // Setup Tombol Simpan
        binding.btnSaveSpot.setOnClickListener {
            saveSpotToFirestore()
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        // Title sudah diatur di XML
    }

    override fun onSupportNavigateUp(): Boolean {
        // Handle tombol kembali di toolbar
        finish()
        return true
    }

    // 1. Launcher untuk meminta izin lokasi
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                // Izin diberikan, ambil lokasi
                getLastLocation()
            } else {
                // Izin ditolak
                makeText(this, "Izin lokasi ditolak", LENGTH_SHORT).show()
                binding.tvLocationStatus.text = "Izin lokasi diperlukan untuk menyimpan spot."
            }
        }

    // 2. Fungsi untuk mengecek dan meminta izin
    private fun requestLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Izin sudah ada, langsung ambil lokasi
                getLastLocation()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                // Beri penjelasan jika user pernah menolak
                makeText(this, "Aplikasi ini butuh lokasi untuk menandai spot.", LENGTH_LONG).show()
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            else -> {
                // Minta izin
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    // 3. Fungsi untuk mengambil lokasi terakhir (Wajib @SuppressLint)
    @SuppressLint("MissingPermission")
    private fun getLastLocation() {
        // Tampilkan progress bar
        binding.progressBar.visibility = View.VISIBLE
        binding.tvLocationStatus.text = "Mendapatkan lokasi..."

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                binding.progressBar.visibility = View.GONE
                if (location != null) {
                    // Lokasi berhasil didapat
                    currentLocation = location
                    val lat = String.format("%.6f", location.latitude)
                    val lon = String.format("%.6f", location.longitude)
                    binding.tvLocationStatus.text = "Lokasi didapat: ($lat, $lon)"
                    Log.d(TAG, "Lokasi didapat: ${location.latitude}, ${location.longitude}")
                } else {
                    // Lokasi null (mungkin GPS mati atau lokasi baru)
                    binding.tvLocationStatus.text = "Gagal mendapatkan lokasi. Pastikan GPS aktif."
                    // --- INI PERBAIKANNYA ---
                    makeText(this, "Tidak bisa mendapatkan lokasi. Coba lagi.", LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                binding.tvLocationStatus.text = "Error: ${e.message}"
                Log.e(TAG, "Gagal mendapatkan lokasi", e)
            }
    }

    // 4. Fungsi untuk menyimpan data ke Firestore
    private fun saveSpotToFirestore() {
        val name = binding.etName.text.toString().trim()
        val note = binding.etNote.text.toString().trim()
        val currentUser = firebaseAuth.currentUser

        // Validasi
        if (name.isEmpty()) {
            binding.tilName.error = "Nama tempat tidak boleh kosong"
            return
        } else {
            binding.tilName.error = null
        }

        if (currentLocation == null) {
            makeText(this, "Lokasi belum siap, silakan tunggu...", LENGTH_SHORT).show()
            return
        }

        if (currentUser == null) {
            makeText(this, "User tidak ditemukan, silakan login ulang", LENGTH_SHORT).show()
            // Arahkan ke login jika user null
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            return
        }

        // Tampilkan loading
        binding.btnSaveSpot.isEnabled = false
        binding.btnSaveSpot.text = "Menyimpan..."

        // Buat objek Spot (pastikan data class Spot sudah diupdate)
        val spot = Spot(
            uid = currentUser.uid,
            name = name,
            note = note,
            latitude = currentLocation!!.latitude,
            longitude = currentLocation!!.longitude,
            timestamp = Timestamp.now()
        )

        // Simpan ke collection 'spots'
        firestore.collection("spots")
            .add(spot)
            .addOnSuccessListener {
                makeText(this, "Spot berhasil disimpan!", LENGTH_SHORT).show()
                // Kembali ke HomeActivity
                finish()
            }
            .addOnFailureListener { e ->
                makeText(this, "Gagal menyimpan: ${e.message}", LENGTH_SHORT).show()
                binding.btnSaveSpot.isEnabled = true
                binding.btnSaveSpot.text = "Simpan Spot"
                Log.e(TAG, "Gagal menyimpan ke Firestore", e)
            }
    }
}