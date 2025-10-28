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
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.Timestamp
import android.view.LayoutInflater
import android.view.ViewGroup
import java.text.SimpleDateFormat // IMPORT BARU
import java.util.Locale // IMPORT BARU

data class Spot(
    val id: String = "",
    val uid: String = "",
    val name: String = "",
    val note: String = "",
    val category: String = "",
    val photoUrl: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val timestamp: Timestamp? = null,
    val visitDate: Timestamp? = null // FIELD BARU DITAMBAHKAN
)

class SpotAdapter(
    private val spots: List<Spot>,
    private val onItemClicked: (Spot) -> Unit
) : RecyclerView.Adapter<SpotAdapter.SpotViewHolder>() {

    class SpotViewHolder(val binding: ItemSpotBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SpotViewHolder {
        val binding = ItemSpotBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SpotViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SpotViewHolder, position: Int) {
        val spot = spots[position]
        holder.binding.tvSpotName.text = spot.name
        holder.binding.tvSpotNote.text = spot.note

        // LOGIKA BARU UNTUK MENAMPILKAN TANGGAL
        if (spot.visitDate != null) {
            val sdf = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
            holder.binding.tvSpotDate.text = "Dikunjungi pada: ${sdf.format(spot.visitDate.toDate())}"
            holder.binding.tvSpotDate.visibility = View.VISIBLE
        } else {
            holder.binding.tvSpotDate.visibility = View.GONE
        }

        holder.itemView.setOnClickListener {
            onItemClicked(spot)
        }
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
        const val TAG = "HomeActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firebaseAuth = FirebaseAuth.getInstance()
        firestore = Firebase.firestore

        val currentUser = firebaseAuth.currentUser
        if (currentUser == null) {
            goToLogin()
            return
        }

        setSupportActionBar(binding.toolbar)

        setupRecyclerView()

        binding.fabAddSpot.setOnClickListener {
            startActivity(Intent(this, AddSpotActivity::class.java))
        }

        loadSpotsFromFirestore()
    }

    private fun setupRecyclerView() {
        spotAdapter = SpotAdapter(spotList) { spot ->
            val intent = Intent(this, DetailActivity::class.java)
            intent.putExtra("SPOT_ID", spot.id)
            startActivity(intent)
        }
        binding.rvSpots.apply {
            layoutManager = LinearLayoutManager(this@HomeActivity)
            adapter = spotAdapter
        }
    }

    private fun loadSpotsFromFirestore() {
        val currentUser = firebaseAuth.currentUser ?: return

        firestore.collection("spots")
            .whereEqualTo("uid", currentUser.uid)
            .orderBy("visitDate", Query.Direction.DESCENDING) // DIUBAH: Urutkan berdasarkan tanggal kunjungan
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w(TAG, "Listen failed.", e)
                    Toast.makeText(this, "Gagal memuat data: ${e.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    spotList.clear()
                    for (doc in snapshots) {
                        val spot = doc.toObject(Spot::class.java).copy(id = doc.id)
                        spotList.add(spot)
                    }
                    spotAdapter.notifyDataSetChanged()

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
        finish()
    }
}