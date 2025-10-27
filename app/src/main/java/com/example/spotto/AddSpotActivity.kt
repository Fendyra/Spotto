package com.example.spotto

import android.Manifest
import android.annotation.SuppressLint // <-- IMPORT BARU
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.spotto.databinding.ActivityAddSpotBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore // Pastikan KTX di-import
import com.google.firebase.ktx.Firebase
import java.util.Date
import java.lang.Exception // Pastikan ini ada

class AddSpotActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddSpotBinding
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var currentLatitude: Double = 0.0
    private var currentLongitude: Double = 0.0
    private var isLocationFetched: Boolean = false

    private companion object {
        const val TAG = "AddSpotActivity"
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Log.d(TAG, "Izin lokasi diberikan.")
                fetchLastLocation()
            } else {
                Log.w(TAG, "Izin lokasi ditolak.")
                binding.tvLocationStatus.text = "Izin lokasi ditolak. Tidak bisa mendapatkan GPS."
                Toast.makeText(this, "Izin lokasi diperlukan untuk menyimpan spot.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddSpotBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firebaseAuth = FirebaseAuth.getInstance()
        firestore = Firebase.firestore
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        requestLocationPermission()

        binding.btnSaveSpot.setOnClickListener {
            saveSpotToFirestore()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }


    private fun requestLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                Log.d(TAG, "Izin lokasi sudah ada.")
                fetchLastLocation()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                Log.d(TAG, "Menampilkan penjelasan izin lokasi.")
                Toast.makeText(this, "Aplikasi ini membutuhkan izin lokasi untuk menyimpan koordinat spot.", Toast.LENGTH_LONG).show()
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            else -> {
                Log.d(TAG, "Meminta izin lokasi.")
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    @SuppressLint("MissingPermission") // <-- ANOTASI DITAMBAHKAN
    private fun fetchLastLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "fetchLastLocation dipanggil tanpa izin.")
            binding.tvLocationStatus.text = "Izin lokasi belum diberikan."
            return
        }

        binding.tvLocationStatus.text = "Mencari lokasi terakhir..."
        fusedLocationClient.lastKnownLocation // <- Sekarang seharusnya resolved
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    currentLatitude = location.latitude
                    currentLongitude = location.longitude
                    isLocationFetched = true
                    binding.tvLocationStatus.text = "Lokasi didapat: Lat=%.6f, Lng=%.6f".format(currentLatitude, currentLongitude)
                    Log.d(TAG, "Lokasi berhasil didapat: $currentLatitude, $currentLongitude")
                } else {
                    binding.tvLocationStatus.text = "Lokasi tidak ditemukan. Pastikan GPS aktif."
                    Log.w(TAG, "lastKnownLocation null.")
                    Toast.makeText(this, "Lokasi terakhir tidak ditemukan. Coba lagi atau pastikan GPS aktif.", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener { e: Exception ->
                binding.tvLocationStatus.text = "Gagal mendapatkan lokasi: ${e.message}" // <- Sekarang seharusnya resolved
                Log.e(TAG, "Gagal mendapatkan lokasi", e)
                Toast.makeText(this, "Terjadi kesalahan saat mengambil lokasi.", Toast.LENGTH_SHORT).show()
            }
    }


    private fun saveSpotToFirestore() {
        val name = binding.etName.text.toString().trim()
        val note = binding.etNote.text.toString().trim()
        val currentUser = firebaseAuth.currentUser

        if (name.isEmpty()) {
            binding.tilName.error = "Nama tempat tidak boleh kosong"
            return
        } else {
            binding.tilName.error = null
        }

        if (!isLocationFetched || currentLatitude == 0.0 || currentLongitude == 0.0) {
            Toast.makeText(this, "Lokasi belum didapatkan. Harap tunggu atau pastikan GPS aktif.", Toast.LENGTH_LONG).show()
            if (!isLocationFetched) requestLocationPermission()
            return
        }

        if (currentUser == null) {
            Toast.makeText(this, "User tidak ditemukan, silakan login ulang.", Toast.LENGTH_SHORT).show()
            goToLogin()
            return
        }

        showLoading(true)

        val spotData = hashMapOf(
            "uid" to currentUser.uid,
            "name" to name,
            "note" to note,
            "latitude" to currentLatitude,
            "longitude" to currentLongitude,
            "timestamp" to Timestamp(Date())
        )

        firestore.collection("spots")
            .add(spotData)
            .addOnSuccessListener { documentReference ->
                showLoading(false)
                Log.d(TAG, "Dokumen berhasil ditambahkan dengan ID: ${documentReference.id}")
                Toast.makeText(this, "Spot berhasil disimpan!", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e: Exception ->
                showLoading(false)
                Log.w(TAG, "Error adding document", e)
                Toast.makeText(this, "Gagal menyimpan spot: ${e.message}", Toast.LENGTH_LONG).show() // <- Sekarang seharusnya resolved
            }
    }

    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnSaveSpot.isEnabled = !isLoading
        binding.etName.isEnabled = !isLoading
        binding.etNote.isEnabled = !isLoading
    }

    private fun goToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}