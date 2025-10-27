package com.example.spotto

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.spotto.databinding.ActivityHomeBinding
import com.example.spotto.databinding.ItemSpotBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query // Import Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.Timestamp
import android.view.LayoutInflater // <-- TAMBAHKAN INI
import android.view.ViewGroup // <-- TAMBAHKAN INI

// Data class untuk menampung data Spot
data class Spot(
    val id: String = "",
    val uid: String = "",
    val name: String = "",
    val note: String = "",
    val category: String = "", // <-- TAMBAHKAN INI
    val photoUrl: String = "", // <-- TAMBAHKAN INI
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val timestamp: Timestamp? = null
)

// Skeleton Adapter untuk RecyclerView
class SpotAdapter(private val spots: List<Spot>) : RecyclerView.Adapter<SpotAdapter.SpotViewHolder>() {

    class SpotViewHolder(val binding: ItemSpotBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SpotViewHolder {
        val binding = ItemSpotBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SpotViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SpotViewHolder, position: Int) {
        val spot = spots[position]
        holder.binding.tvSpotName.text = spot.name
        holder.binding.tvSpotNote.text = spot.note
        // Tambahkan onClickListener di sini jika ingin ada aksi saat item diklik
        // holder.itemView.setOnClickListener { /* Aksi detail spot */ }
    }

    override fun getItemCount() = spots.size
}


class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var spotAdapter: SpotAdapter
    private val spotList = mutableListOf<Spot>()

    private companion object {
        const val TAG = "HomeActivity" // Untuk logging
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firebaseAuth = FirebaseAuth.getInstance()
        firestore = Firebase.firestore // Inisialisasi Firestore

        // Cek user saat ini, jika null kembali ke Login
        val currentUser = firebaseAuth.currentUser
        if (currentUser == null) {
            goToLogin()
            return
        }

        // Setup Toolbar
        setSupportActionBar(binding.toolbar)

        // Setup RecyclerView
        setupRecyclerView()

        // Setup FAB Click Listener
        binding.fabAddSpot.setOnClickListener {
            startActivity(Intent(this, AddSpotActivity::class.java))
        }

        // Muat data dari Firestore
        loadSpotsFromFirestore()
    }

    private fun setupRecyclerView() {
        spotAdapter = SpotAdapter(spotList)
        binding.rvSpots.apply {
            layoutManager = LinearLayoutManager(this@HomeActivity)
            adapter = spotAdapter
        }
    }

    private fun loadSpotsFromFirestore() {
        val currentUser = firebaseAuth.currentUser ?: return // Pastikan user tidak null

        // Query ke koleksi 'spots' dan filter berdasarkan UID pengguna saat ini
        firestore.collection("spots")
            .whereEqualTo("uid", currentUser.uid)
            .orderBy("name", Query.Direction.ASCENDING) // Urutkan berdasarkan nama, bisa diganti timestamp jika ada
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w(TAG, "Listen failed.", e)
                    Toast.makeText(this, "Gagal memuat data: ${e.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    spotList.clear() // Kosongkan list sebelum diisi ulang
                    for (doc in snapshots) {
                        val spot = doc.toObject(Spot::class.java).copy(id = doc.id) // Ambil data dan ID dokumen
                        spotList.add(spot)
                    }
                    spotAdapter.notifyDataSetChanged() // Beri tahu adapter ada data baru

                    // Tampilkan atau sembunyikan empty state
                    binding.tvEmptyState.visibility = if (spotList.isEmpty()) View.VISIBLE else View.GONE
                    binding.rvSpots.visibility = if (spotList.isEmpty()) View.GONE else View.VISIBLE

                    Log.d(TAG, "Jumlah spot dimuat: ${spotList.size}")
                } else {
                    Log.d(TAG, "Current data: null")
                    binding.tvEmptyState.visibility = View.VISIBLE
                    binding.rvSpots.visibility = View.GONE
                }
            }
    }

    // --- Menu Toolbar ---
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.home_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_logout -> {
                performLogout()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun performLogout() {
        firebaseAuth.signOut()
        Toast.makeText(this, "Logout Berhasil", Toast.LENGTH_SHORT).show()
        goToLogin()
    }

    private fun goToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish() // Tutup HomeActivity
    }
}