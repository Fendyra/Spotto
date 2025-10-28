package com.example.spotto

import android.Manifest
import android.annotation.SuppressLint
import android.app.DatePickerDialog
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
import com.google.android.gms.location.Priority
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AddSpotActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddSpotBinding
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var currentLocation: Location? = null
    private var imageUri: Uri? = null
    private var selectedDate: Calendar = Calendar.getInstance()

    companion object {
        private const val TAG = "AddSpotActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddSpotBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
        val categories = resources.getStringArray(R.array.spot_categories)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categories)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerCategory.adapter = adapter
    }

    private fun setupClickListeners() {
        binding.btnGetLocation.setOnClickListener { requestLocationPermission() }
        binding.btnSaveSpot.setOnClickListener { saveSpot() }
        binding.etDate.setOnClickListener { showDatePicker() }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val datePickerDialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                selectedDate.set(Calendar.YEAR, year)
                selectedDate.set(Calendar.MONTH, month)
                selectedDate.set(Calendar.DAY_OF_MONTH, dayOfMonth)

                val sdf = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
                binding.etDate.setText(sdf.format(selectedDate.time))
                binding.tilDate.error = null
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.show()
    }

    private val requestGalleryPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) openGallery()
            else Toast.makeText(this, "Izin galeri ditolak", Toast.LENGTH_SHORT).show()
        }

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                imageUri = result.data?.data
                binding.ivPhotoPreview.visibility = View.VISIBLE
                Picasso.get().load(imageUri).into(binding.ivPhotoPreview)
            }
        }

    private fun requestGalleryPermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_IMAGES
        else Manifest.permission.READ_EXTERNAL_STORAGE

        when {
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> openGallery()
            shouldShowRequestPermissionRationale(permission) -> {
                Toast.makeText(this, "Izin diperlukan untuk memilih foto", Toast.LENGTH_LONG).show()
                requestGalleryPermissionLauncher.launch(permission)
            }
            else -> requestGalleryPermissionLauncher.launch(permission)
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK).apply { type = "image/*" }
        pickImageLauncher.launch(intent)
    }

    private val requestLocationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                fetchCurrentLocation()
            } else {
                Toast.makeText(this, "Izin lokasi ditolak", Toast.LENGTH_SHORT).show()
                binding.tvLocationStatus.text = "Izin lokasi diperlukan."
            }
        }

    private fun requestLocationPermission() {
        val permission = Manifest.permission.ACCESS_FINE_LOCATION
        when {
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> {
                fetchCurrentLocation()
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
    private fun fetchCurrentLocation() {
        setLoading(true, "Mendapatkan lokasi saat ini...")
        binding.tvLocationStatus.text = "Mendapatkan lokasi saat ini..."

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location: Location? ->
                setLoading(false)
                if (location != null) {
                    currentLocation = location
                    val lat = String.format(Locale.ROOT, "%.6f", location.latitude)
                    val lon = String.format(Locale.ROOT, "%.6f", location.longitude)
                    binding.tvLocationStatus.text = "Lokasi didapat: ($lat, $lon)"
                    Log.d(TAG, "Lokasi saat ini didapat: Lat ${location.latitude}, Lon ${location.longitude}")
                } else {
                    binding.tvLocationStatus.text = "Gagal mendapatkan lokasi saat ini. Pastikan GPS & Layanan Lokasi aktif."
                    Log.w(TAG, "getCurrentLocation mengembalikan null")

                }
            }
            .addOnFailureListener { e ->
                setLoading(false)
                binding.tvLocationStatus.text = "Error mendapatkan lokasi: ${e.message}"
                Log.e(TAG, "Gagal mendapatkan lokasi saat ini", e)

            }
    }

    private fun saveSpot() {
        val name = binding.etName.text.toString().trim()
        val note = binding.etNote.text.toString().trim()
        val date = binding.etDate.text.toString().trim() // TAMBAHAN BARU
        val category = binding.spinnerCategory.selectedItem.toString()
        val currentUser = firebaseAuth.currentUser

        if (name.isEmpty()) {
            binding.tilName.error = "Nama tempat tidak boleh kosong"
            return
        } else binding.tilName.error = null

        if (note.isEmpty()) {
            binding.tilNote.error = "Catatan tidak boleh kosong"
            return
        } else binding.tilNote.error = null

        // VALIDASI BARU
        if (date.isEmpty()) {
            binding.tilDate.error = "Tanggal kunjungan tidak boleh kosong"
            return
        } else binding.tilDate.error = null

        if (currentLocation == null) {
            Toast.makeText(this, "Lokasi belum berhasil didapatkan", Toast.LENGTH_SHORT).show()
            return
        }

        if (currentUser == null) {
            Toast.makeText(this, "User tidak valid, silakan login ulang", Toast.LENGTH_SHORT).show()
            return
        }

        setLoading(true, "Menyimpan spot...")

        val visitDateTimestamp = Timestamp(selectedDate.time) // TAMBAHAN BARU

        if (imageUri != null)
            uploadImageAndSaveData(currentUser.uid, name, note, category, visitDateTimestamp)
        else
            saveDataToFirestore(currentUser.uid, name, note, category, "", visitDateTimestamp)
    }

    private fun uploadImageAndSaveData(uid: String, name: String, note: String, category: String, visitDate: Timestamp) {
        val timestamp = System.currentTimeMillis()
        val storageRef = storage.reference.child("spot_images/$uid/$timestamp.jpg")

        imageUri?.let {
            storageRef.putFile(it)
                .addOnSuccessListener {
                    storageRef.downloadUrl
                        .addOnSuccessListener { uri ->
                            saveDataToFirestore(uid, name, note, category, uri.toString(), visitDate)
                        }
                        .addOnFailureListener { e ->
                            setLoading(false)
                            Toast.makeText(this, "Gagal upload: ${e.message}", Toast.LENGTH_SHORT).show()
                            Log.e(TAG, "Gagal mendapatkan download URL", e)
                        }
                }
                .addOnFailureListener { e ->
                    setLoading(false)
                    Toast.makeText(this, "Gagal upload: ${e.message}", Toast.LENGTH_SHORT).show()
                    Log.e(TAG, "Gagal upload foto", e)
                }
        }
    }

    private fun saveDataToFirestore(uid: String, name: String, note: String, category: String, photoUrl: String, visitDate: Timestamp) {
        currentLocation?.let { loc ->
            val newSpot = hashMapOf(
                "uid" to uid,
                "name" to name,
                "note" to note,
                "category" to category,
                "latitude" to loc.latitude,
                "longitude" to loc.longitude,
                "photoUrl" to photoUrl,
                "timestamp" to Timestamp.now(),
                "visitDate" to visitDate
            )

            firestore.collection("spots")
                .add(newSpot)
                .addOnSuccessListener {
                    setLoading(false)
                    Toast.makeText(this, "Spot berhasil ditambahkan", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    setLoading(false)
                    Toast.makeText(this, "Gagal menyimpan: ${e.message}", Toast.LENGTH_SHORT).show()
                    Log.e(TAG, "Gagal menyimpan ke Firestore", e)
                }
        } ?: run {
            setLoading(false)
            Toast.makeText(this, "Lokasi tidak valid saat menyimpan", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "currentLocation null saat mencoba menyimpan ke Firestore")
        }
    }

    private fun setLoading(isLoading: Boolean, message: String = "") {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnSaveSpot.isEnabled = !isLoading
        binding.btnSaveSpot.text = if (isLoading) message else "Simpan Spot"
    }
}