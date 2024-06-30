package com.hood.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.hood.R
import com.hood.models.Order
import com.hood.adapters.SectionedOrderAdapter
import okhttp3.*
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SectionedOrderAdapter
    private val client = OkHttpClient()

    private val nowOrders = mutableListOf<Order>()
    private val laterOrders = mutableListOf<Order>()
    private val sentOrders = mutableListOf<Order>()
    private lateinit var handler: Handler
    private lateinit var runnable: Runnable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        supportActionBar?.title = "Orders"

        // Check if 'cid' exists in SharedPreferences
        val sharedPreferences = getSharedPreferences("my_preferences", Context.MODE_PRIVATE)
        val cid = sharedPreferences.getString("cid", null)
        Log.d("MainActivity", "Retrieved cid: $cid") // Debug information

        if (cid == null) {
            // Navigate to LoginActivity if 'cid' does not exist
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish() // Finish MainActivity to prevent returning to it
            return
        }

        recyclerView = findViewById(R.id.recyclerView)
        adapter = SectionedOrderAdapter(nowOrders, laterOrders, sentOrders)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        handler = Handler(mainLooper)
        runnable = Runnable {
            getOrders()
            handler.postDelayed(runnable, 30000) // Re-run the runnable every 30 seconds
        }

        getOrders() // Initial call to get orders
    }

    override fun onResume() {
        super.onResume()
        handler.post(runnable) // Start the periodic task
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(runnable) // Stop the periodic task
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_profile -> {
                showLogoutOption()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showLogoutOption() {
        val sharedPreferences = getSharedPreferences("my_preferences", Context.MODE_PRIVATE)
        sharedPreferences.edit().clear().apply()
        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()

        // Navigate to LoginActivity
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish() // Finish MainActivity to prevent returning to it
    }

    private fun getOrders() {
        var urlString = "https://minitel.co.uk/app/models/ordersGate?command=getOrders&shop=1"
        val selectedAdminID = getSharedPreferences("my_preferences", MODE_PRIVATE).getString("selectedAdminID", "")
        if (selectedAdminID == "c") {
            urlString = "https://hoodapp.co.uk/app/models/ordersGate?command=getOrders&shop=1&courierid=11&date="
        }

        val request = Request.Builder().url(urlString).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("MainActivity", "Error fetching orders: ${e.message}", e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    Log.e("MainActivity", "Unexpected code $response")
                    return
                }

                val responseString = response.body?.string() ?: return
                val orderStrings = responseString.split("::")
                val fetchedOrders = parseOrders(orderStrings)

                runOnUiThread {
                    nowOrders.clear()
                    laterOrders.clear()
                    sentOrders.clear()

                    for (order in fetchedOrders) {
                        when (order.status) {
                            4, 3 -> nowOrders.add(order)
                            1, 2 -> laterOrders.add(order)
                            5 -> sentOrders.add(order)
                        }
                    }

                    adapter.updateOrders(nowOrders, laterOrders, sentOrders)
                    Log.d("MainActivity", "Fetched orders count: ${fetchedOrders.size}")
                }
            }
        })
    }

    private fun parseOrders(array: List<String>): List<Order> {
        return array.mapNotNull { entry ->
            val data = entry.split("|")
            if (data.size >= 40) {
                try {
                    Order(
                        orderId = data[3],
                        customerName = decodeHTMLEntities(data[0]),
                        postcode = data[2],
                        address = decodeHTMLEntities(data[1]),
                        pickupTime = formatTime(data[9]),
                        deliveryTime = data[10],
                        status = data[6].toIntOrNull() ?: 0,
                        packed = data[29].toIntOrNull() ?: 0,
                        phone = data[28],
                        total = data[4].toDoubleOrNull() ?: 0.0,
                        icon = data[30],
                        pickupImageUrl = data[32],
                        deliveryImageUrl = data[33],
                        allocatedTime = formatTime(data[36]),
                        pickupETA = formatTime(data[37]),
                        courierId = "",
                        courierName = data[38],
                        lifecycle = data[40]
                    )
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error parsing order: ${e.message}", e)
                    null
                }
            } else {
                Log.w("MainActivity", "Incomplete order data: $data")
                null
            }
        }
    }

    private fun decodeHTMLEntities(input: String): String {
        return android.text.Html.fromHtml(input, android.text.Html.FROM_HTML_MODE_LEGACY).toString()
    }

    private fun formatTime(input: String): String {
        // Implement your time formatting logic here
        return input
    }
}
