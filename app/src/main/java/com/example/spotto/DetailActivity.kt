package com.example.spotto

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.spotto.databinding.ActivityDetailSpotBinding
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.ktx.storage
import com.squareup.picasso.Picasso
import java.text.SimpleDateFormat
import java.util.Locale

class DetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailSpotBinding
    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: FirebaseStorage

    private var currentSpotId: String? = null
    private var currentSpot: Spot? = null

    companion object {
        private const val TAG = "DetailActivity"
        const val SPOT_ID_EXTRA = "SPOT_ID"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailSpotBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()

        firestore = FirebaseFirestore.getInstance()
        storage = Firebase.storage

        currentSpotId = intent.getStringExtra(SPOT_ID_EXTRA)
        if (currentSpotId == null) {
            Log.e(TAG, "Spot ID tidak ditemukan.")
            Toast.makeText(this, "Gagal memuat spot", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        loadSpotData(currentSpotId!!)

        binding.btnOpenMaps.setOnClickListener { openInGoogleMaps() }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbarDetail)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(false)
    }


    // Ambil & Tampilkan Data Spot

    private fun loadSpotData(spotId: String) {
        firestore.collection("spots").document(spotId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    currentSpot = document.toObject(Spot::class.java)?.copy(id = document.id)
                    currentSpot?.let { populateUi(it) }
                } else {
                    Toast.makeText(this, "Spot tidak ditemukan", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Gagal mengambil data", e)
                Toast.makeText(this, "Gagal memuat: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun populateUi(spot: Spot) {
        binding.collapsingToolbar.title = spot.name
        binding.tvDetailSpotName.text = spot.name
        binding.tvDetailSpotNote.text = spot.note.ifEmpty { "Tidak ada catatan." }


        binding.chipDetailCategory.text = spot.category
        binding.chipDetailCategory.visibility =
            if (spot.category.isNotEmpty()) View.VISIBLE else View.GONE

        val timestampFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        val dateString = spot.timestamp?.toDate()?.let { timestampFormat.format(it) } ?: "N/A"
        val latLonString = "Lat: %.6f, Lon: %.6f".format(spot.latitude, spot.longitude)
        binding.tvDetailMetadata.text = "$latLonString\nDitambahkan pada $dateString"

        if (spot.photoUrl.isNotEmpty()) {
            binding.ivSpotPhoto.visibility = View.VISIBLE
            Picasso.get().load(spot.photoUrl).into(binding.ivSpotPhoto)
        } else {
            binding.ivSpotPhoto.visibility = View.VISIBLE
            binding.ivSpotPhoto.setImageResource(R.color.mint_green)
        }
    }

    // Hapus Spot

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.detail_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_delete -> {
                showDeleteConfirmation()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showDeleteConfirmation() {
        val spotName = currentSpot?.name ?: "spot ini"
        AlertDialog.Builder(this)
            .setTitle("Hapus Spot?")
            .setMessage("Anda yakin ingin menghapus '$spotName' secara permanen?")
            .setPositiveButton("Hapus") { _, _ -> deleteSpot() }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun deleteSpot() {
        val spotId = currentSpotId ?: return
        val photoUrl = currentSpot?.photoUrl

        // hapus foto
        if (!photoUrl.isNullOrEmpty()) {
            val photoRef = storage.getReferenceFromUrl(photoUrl)
            photoRef.delete()
                .addOnSuccessListener {
                    deleteFirestoreDocument(spotId)
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Gagal hapus foto, lanjut hapus Firestore", e)
                    deleteFirestoreDocument(spotId)
                }
        } else {
            deleteFirestoreDocument(spotId)
        }
    }

    private fun deleteFirestoreDocument(spotId: String) {
        firestore.collection("spots").document(spotId)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Spot berhasil dihapus", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Gagal menghapus: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "Gagal hapus Firestore", e)
            }
    }


    //  Buka Lokasi Gmaps

    private fun openInGoogleMaps() {
        currentSpot?.let { spot ->
            val gmmIntentUri =
                Uri.parse("geo:${spot.latitude},${spot.longitude}?q=${Uri.encode(spot.name)}")
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                setPackage("com.google.android.apps.maps")
            }

            if (mapIntent.resolveActivity(packageManager) != null) {
                startActivity(mapIntent)
            } else {
                Toast.makeText(this, "Google Maps tidak terinstall", Toast.LENGTH_SHORT).show()
                val webIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/maps/search/?api=1&query=${spot.latitude},${spot.longitude}")
                )
                startActivity(webIntent)
            }
        }
    }
}
