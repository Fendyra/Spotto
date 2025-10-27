package com.example.spotto

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
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
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.ktx.storage
import com.squareup.picasso.Picasso

class AddSpotActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddSpotBinding
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // Variabel untuk menyimpan data
    private var currentLocation: Location? = null
    private var imageUri: Uri? = null

    private companion object {
        const val TAG = "AddSpotActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddSpotBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inisialisasi Firebase & Location
        firebaseAuth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        storage = Firebase.storage
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setupToolbar()
        setupSpinner()
        setupClickListeners()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun setupSpinner() {
        // Ambil array dari strings.xml
        val categories = resources.getStringArray(R.array.spot_categories)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categories)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerCategory.adapter = adapter
    }

    private fun setupClickListeners() {
        binding.btnChoosePhoto.setOnClickListener {
            requestGalleryPermission()
        }
        binding.btnGetLocation.setOnClickListener {
            requestLocationPermission()
        }
        binding.btnSaveSpot.setOnClickListener {
            saveSpot()
        }
    }

    // --- Bagian Logika Foto ---

    // 1. Launcher untuk Izin Galeri
    private val requestGalleryPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                openGallery()
            } else {
                Toast.makeText(this, "Izin galeri ditolak", Toast.LENGTH_SHORT).show()
            }
        }

    // 2. Launcher untuk Memilih Foto
    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                imageUri = result.data?.data
                // Tampilkan preview menggunakan Picasso
                binding.ivPhotoPreview.visibility = View.VISIBLE
                Picasso.get().load(imageUri).into(binding.ivPhotoPreview)
            }
        }

    // 3. Cek Izin & Buka Galeri
    private fun requestGalleryPermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        when {
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> {
                openGallery()
            }
            shouldShowRequestPermissionRationale(permission) -> {
                Toast.makeText(this, "Izin diperlukan untuk memilih foto", Toast.LENGTH_LONG).show()
                requestGalleryPermissionLauncher.launch(permission)
            }
            else -> {
                requestGalleryPermissionLauncher.launch(permission)
            }
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        pickImageLauncher.launch(intent)
    }

    // --- Bagian Logika Lokasi ---

    // 1. Launcher untuk Izin Lokasi
    private val requestLocationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                getLastLocation()
            } else {
                Toast.makeText(this, "Izin lokasi ditolak", Toast.LENGTH_SHORT).show()
                binding.tvLocationStatus.text = "Izin lokasi diperlukan."
            }
        }

    // 2. Cek Izin & Ambil Lokasi
    private fun requestLocationPermission() {
        val permission = Manifest.permission.ACCESS_FINE_LOCATION
        when {
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> {
                getLastLocation()
            }
            shouldShowRequestPermissionRationale(permission) -> {
                Toast.makeText(this, "Aplikasi ini butuh lokasi untuk menandai spot", Toast.LENGTH_LONG).show()
                requestLocationPermissionLauncher.launch(permission)
            }
            else -> {
                requestLocationPermissionLauncher.launch(permission)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun getLastLocation() {
        setLoading(true, "Mendapatkan lokasi...")
        binding.tvLocationStatus.text = "Mendapatkan lokasi..."

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                setLoading(false)
                if (location != null) {
                    currentLocation = location
                    val lat = String.format("%.6f", location.latitude)
                    val lon = String.format("%.6f", location.longitude)
                    binding.tvLocationStatus.text = "Lokasi didapat: ($lat, $lon)"
                } else {
                    binding.tvLocationStatus.text = "Gagal dapat lokasi. Pastikan GPS aktif."
                }
            }
            .addOnFailureListener { e ->
                setLoading(false)
                binding.tvLocationStatus.text = "Error: ${e.message}"
                Log.e(TAG, "Gagal mendapatkan lokasi", e)
            }
    }

    // --- Bagian Logika Simpan ---

    private fun saveSpot() {
        val name = binding.etName.text.toString().trim()
        val note = binding.etNote.text.toString().trim()
        val category = binding.spinnerCategory.selectedItem.toString()
        val currentUser = firebaseAuth.currentUser

        // Validasi
        if (name.isEmpty()) {
            binding.tilName.error = "Nama tempat tidak boleh kosong"
            return
        }
        binding.tilName.error = null

        if (note.isEmpty()) {
            binding.tilNote.error = "Catatan tidak boleh kosong"
            return
        }
        binding.tilNote.error = null

        if (currentLocation == null) {
            Toast.makeText(this, "Lokasi otomatis belum diambil", Toast.LENGTH_SHORT).show()
            return
        }

        if (currentUser == null) {
            Toast.makeText(this, "User tidak valid, silakan login ulang", Toast.LENGTH_SHORT).show()
            return
        }

        setLoading(true, "Menyimpan spot...")

        // Cek apakah ada foto yang diupload
        if (imageUri != null) {
            uploadImageAndSaveData(currentUser.uid, name, note, category)
        } else {
            // Simpan tanpa foto
            saveDataToFirestore(currentUser.uid, name, note, category, "")
        }
    }

    private fun uploadImageAndSaveData(
        uid: String,
        name: String,
        note: String,
        category: String
    ) {
        val timestamp = System.currentTimeMillis()
        val storageRef = storage.reference.child("spot_images/$uid/$timestamp.jpg")

        imageUri?.let {
            storageRef.putFile(it)
                .addOnSuccessListener {
                    // Ambil URL download
                    storageRef.downloadUrl
                        .addOnSuccessListener { uri ->
                            val photoUrl = uri.toString()
                            saveDataToFirestore(uid, name, note, category, photoUrl)
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Gagal mendapatkan download URL", e)
                            setLoading(false)
                            Toast.makeText(this, "Gagal upload: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Gagal upload foto", e)
                    setLoading(false)
                    Toast.makeText(this, "Gagal upload: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun saveDataToFirestore(
        uid: String,
        name: String,
        note: String,
        category: String,
        photoUrl: String
    ) {
        val newSpot = hashMapOf(
            "uid" to uid,
            "name" to name,
            "note" to note,
            "category" to category,
            "latitude" to currentLocation!!.latitude,
            "longitude" to currentLocation!!.longitude,
            "photoUrl" to photoUrl,
            "timestamp" to Timestamp.now()
        )

        firestore.collection("spots")
            .add(newSpot)
            .addOnSuccessListener {
                setLoading(false)
                Toast.makeText(this, "Spot berhasil ditambahkan", Toast.LENGTH_SHORT).show()
                finish() // Kembali ke HomeActivity
            }
            .addOnFailureListener { e ->
                setLoading(false)
                Toast.makeText(this, "Gagal menyimpan: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "Gagal menyimpan ke Firestore", e)
            }
    }

    private fun setLoading(isLoading: Boolean, message: String = "") {
        if (isLoading) {
            binding.progressBar.visibility = View.VISIBLE
            binding.btnSaveSpot.isEnabled = false
            binding.btnSaveSpot.text = message
        } else {
            binding.progressBar.visibility = View.GONE
            binding.btnSaveSpot.isEnabled = true
            binding.btnSaveSpot.text = "Simpan Spot"
        }
    }
}