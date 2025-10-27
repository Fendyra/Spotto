package com.example.spotto

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.spotto.databinding.ActivityHomeBinding
import com.example.spotto.databinding.ItemSpotBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.toObject // <-- IMPORT BARU DAN PENTING
import com.google.firebase.ktx.Firebase
import java.lang.Exception // Pastikan ini ada

data class Spot(
    val id: String = "",
    val uid: String = "",
    val name: String = "",
    val note: String = ""
)

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
    }

    override fun getItemCount() = spots.size
}


class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var spotAdapter: SpotAdapter
    private val spotList = mutableListOf<Spot>()
    private var firestoreListener: ListenerRegistration? = null

    private companion object {
        const val TAG = "HomeActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firebaseAuth = FirebaseAuth.getInstance()
        firestore = Firebase.firestore

        if (firebaseAuth.currentUser == null) {
            goToLogin()
            return
        }

        setSupportActionBar(binding.toolbar)
        setupRecyclerView()

        binding.fabAddSpot.setOnClickListener {
            startActivity(Intent(this, AddSpotActivity::class.java)) // Pastikan ini sudah diubah
        }
        showLoading(true)
    }

    override fun onStart() {
        super.onStart()
        attachFirestoreListener()
    }

    override fun onStop() {
        super.onStop()
        firestoreListener?.remove()
    }

    private fun setupRecyclerView() {
        spotAdapter = SpotAdapter(spotList)
        binding.rvSpots.apply {
            layoutManager = LinearLayoutManager(this@HomeActivity)
            adapter = spotAdapter
        }
    }

    private fun attachFirestoreListener() {
        val currentUser = firebaseAuth.currentUser ?: return
        showLoading(true)

        val query = firestore.collection("spots")
            .whereEqualTo("uid", currentUser.uid)
            .orderBy("name", Query.Direction.ASCENDING)

        firestoreListener = query.addSnapshotListener { snapshots, e ->
            showLoading(false)

            if (e != null) {
                Log.w(TAG, "Listen failed.", e)
                Toast.makeText(this, "Gagal memuat data: ${e.message}", Toast.LENGTH_SHORT).show()
                showEmptyState(true)
                return@addSnapshotListener
            }

            if (snapshots != null) {
                spotList.clear()
                for (doc in snapshots) {
                    try {
                        // --- PERUBAHAN UTAMA DI SINI ---
                        val spot = doc.toObject<Spot>()?.copy(id = doc.id) // Gunakan reified toObject dan ?.copy()
                        if (spot != null) { // Tambahkan null check
                            spotList.add(spot)
                        } else {
                            Log.w(TAG, "Gagal konversi dokumen ${doc.id}, mungkin null.")
                        }
                        // ------------------------------
                    } catch (ex: Exception) {
                        Log.e(TAG, "Error converting document ${doc.id}", ex)
                    }
                }
                spotAdapter.notifyDataSetChanged()
                showEmptyState(spotList.isEmpty())
                Log.d(TAG, "Jumlah spot dimuat: ${spotList.size}")
            } else {
                Log.d(TAG, "Snapshot null")
                showEmptyState(true)
            }
        }
    }


    private fun showLoading(isLoading: Boolean) {
        // Uncomment baris ini jika sudah menambahkan ProgressBar di activity_home.xml
        // binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.rvSpots.visibility = if (isLoading) View.GONE else View.VISIBLE
        binding.tvEmptyState.visibility = if (isLoading) View.GONE else View.VISIBLE
    }

    private fun showEmptyState(show: Boolean) {
        binding.tvEmptyState.visibility = if (show) View.VISIBLE else View.GONE
        binding.rvSpots.visibility = if (show) View.GONE else View.VISIBLE
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
        firestoreListener?.remove()
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