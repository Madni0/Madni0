package com.aishield.detector.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.aishield.detector.App
import com.aishield.detector.databinding.ActivityHistoryBinding

/** Full local history of every analyzed piece of content (incl. silent logs). */
class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var adapter: HistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(com.aishield.detector.R.string.title_history)

        adapter = HistoryAdapter { id ->
            startActivity(
                Intent(this, DetailActivity::class.java).putExtra(DetailActivity.EXTRA_ID, id)
            )
        }
        binding.recyclerHistory.layoutManager = LinearLayoutManager(this)
        binding.recyclerHistory.adapter = adapter

        val rows = try {
            (application as App).db.all(500)
        } catch (_: Throwable) {
            emptyList()
        }
        adapter.submit(rows)
        binding.tvEmpty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
