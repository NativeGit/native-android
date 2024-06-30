package com.hood.activities

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.icu.util.Calendar
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.hood.R
import com.hood.fragments.CollectionSheetFragment
import com.hood.ui.MessageBubble
import com.hood.models.Order
import com.hood.models.OrderItem
import com.squareup.picasso.Picasso
import okhttp3.*
import java.io.File
import java.io.FileOutputStream
import jp.wasabeef.picasso.transformations.RoundedCornersTransformation
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.io.ByteArrayOutputStream

import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

class OrderActivity : AppCompatActivity(),
    CollectionSheetFragment.OnCollectionSheetInteractionListener {


    private lateinit var order: Order
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_order_view)

        order = intent.getParcelableExtra<Order>("order") ?: return
        supportActionBar?.title = "#${order.orderId} - ${order.customerName}"
        setupUI()
        loadOrderItems(order.orderId, "your_id_here") // Replace "your_id_here" with the actual ID
        displayLifecycleEvents()


    }

    private fun setupUI() {
        findViewById<TextView>(R.id.order_id).text = "#${order.orderId}"
        findViewById<TextView>(R.id.customer_name).text = order.customerName
        findViewById<TextView>(R.id.order_status).text = order.getStatusText()
        findViewById<TextView>(R.id.order_address).text = "${order.postcode}, ${order.address}"
        val phoneTextView = findViewById<TextView>(R.id.order_phone)
        phoneTextView.text = order.phone
        phoneTextView.setOnClickListener {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${order.phone}"))
            startActivity(intent)
        }


    }

    private fun loadOrderItems(orderId: String, id: String) {
        val urlString = "https://hoodapp.co.uk/get.aspx?type=6&orderid=$orderId&id=1600312"
        val request = Request.Builder().url(urlString).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("OrderViewActivity", "Error fetching data: ${e.message}", e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    Log.e("OrderViewActivity", "Error: HTTP status code ${response.code}")
                    return
                }

                val responseString = response.body?.string() ?: return
                val orderArray = responseString.split("$")
                val parsedOrderItems = parseOrderItems(orderArray)

                runOnUiThread {
                    updateUIWithItems(parsedOrderItems)
                }
            }
        })
    }

    private fun parseOrderItems(dataArray: List<String>): List<OrderItem> {
        return dataArray.mapNotNull { dataEntry ->
            val dataComponents = dataEntry.split("|")
            if (dataComponents.size == 7) {
                val productName = dataComponents[0]
                val price = dataComponents[1].toDoubleOrNull() ?: return@mapNotNull null
                val quantity = dataComponents[4].toIntOrNull() ?: return@mapNotNull null
                OrderItem(name = productName, quantity = quantity, price = price)
            } else {
                null
            }
        }
    }

    private fun updateUIWithItems(items: List<OrderItem>) {
        val itemsContainer = findViewById<LinearLayout>(R.id.items_container)
        itemsContainer.removeAllViews()

        for (item in items) {
            val itemView = layoutInflater.inflate(R.layout.order_item_view, itemsContainer, false)
            val itemText = itemView.findViewById<TextView>(R.id.item_text)

            // Format the text with bold quantity and normal product name
            val formattedText = "${item.quantity} x ${item.name}"
            val spannableString = SpannableString(formattedText)
            spannableString.setSpan(StyleSpan(android.graphics.Typeface.BOLD), 0, "${item.quantity} x".length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

            itemText.text = spannableString
            itemsContainer.addView(itemView)
        }
    }

    private fun displayLifecycleEvents() {
        val activitiesContainer = findViewById<LinearLayout>(R.id.activities_container)

        // Handle received event
        val receivedEvent = order.getLifecycleEvent("received")
        receivedEvent?.let {
            val formattedTime = order.extractEtaTime(it.value)
            val messageBubble = MessageBubble(this).apply {
                setMessage("Order received", "ETA $formattedTime", formatTimestampToTime(it.timestamp))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(10, 10, 10, 10)
                }
            }
            activitiesContainer.addView(messageBubble)
        }

        val button = findViewById<Button>(R.id.update_pickup_button)

        // Handle pickupEta event
        val pickupEtaEvent = order.getLifecycleEvent("pickupEta")
        if (pickupEtaEvent != null) {
            button.text = "Mark as collected"
            button.setOnClickListener {
                showImagePickerDialog()
            }
            displayPickupEtaEvent()
        } else {
            button.setOnClickListener {
                showCollectionSheet()
            }
        }

        // Handle pickedup event
        val pickedupEvent = order.getLifecycleEvent("pickedup")
        pickedupEvent?.let {
            Log.d("OrderViewActivity", "Pickup image ${order.pickupImageUrl}")
            val formattedTime = formatTimestampToTime(it.timestamp)
            displayPickupImage(order.pickupImageUrl, formattedTime)
            button.text = "Mark as delivered"
            button.setOnClickListener {
                showImagePickerDialog()
            }
        }

        // Handle delivered event
        val deliveredEvent = order.getLifecycleEvent("delivered")
        deliveredEvent?.let {
            Log.d("OrderViewActivity", "Delivery image ${order.deliveryImageUrl}")
            val formattedTime = formatTimestampToTime(it.timestamp)
            displayDeliveryImage(order.deliveryImageUrl, formattedTime)
            button.visibility = View.GONE // Hide the button as the order is delivered
        }
    }



    private fun displayPickupEtaEvent() {
        val activitiesContainer = findViewById<LinearLayout>(R.id.activities_container)
        val pickupEtaEvent = order.getLifecycleEvent("pickupEta")
        pickupEtaEvent?.let {
            val formattedTime = order.extractEtaTime(it.value)
            val messageBubble = MessageBubble(this).apply {
                setMessage(order.courierName, "Pickup eta $formattedTime", formatTimestampToTime(it.timestamp))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(10, 10, 10, 10)
                }
            }
            activitiesContainer.addView(messageBubble)
        }


    }


    private fun showCollectionSheet() {
        val fragment =
            CollectionSheetFragment.newInstance(order, CollectionSheetFragment.SheetMode.COLLECTION)
        fragment.show(supportFragmentManager, "CollectionSheetFragment")
    }

    override fun onCollectionUpdated(updatedOrder: Order) {
        this.order = updatedOrder
        displayPickupEtaEvent()

        val button = findViewById<Button>(R.id.update_pickup_button)
        button.text = "Mark as collected2"

        button.setOnClickListener {
            showImagePickerDialog()
        }

    }
    private fun showImagePickerDialog() {
        val options = arrayOf("Camera", "Photo Gallery")
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Choose an option")
        builder.setItems(options) { dialog, which ->
            when (which) {
                0 -> openCamera()
                1 -> openGallery()
            }
        }
        builder.show()
    }

    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (intent.resolveActivity(packageManager) != null) {
            startActivityForResult(intent, REQUEST_CAMERA)
        } else {
            Toast.makeText(this, "Camera not available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, REQUEST_GALLERY)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            when (requestCode) {
                REQUEST_CAMERA -> {
                    val photo = data?.extras?.get("data") as Bitmap
                    val fileUri = saveBitmapToFile(photo)
                    order.pickupImageUrl = fileUri.toString()
                    val currentTime = System.currentTimeMillis()
                    val formattedTime = formatTimestampToTime(currentTime.toString())
                    displayPickupImage(order.pickupImageUrl, formattedTime)
                    val uniqueImageName = generateUniqueImageName()
                    uploadImage(photo, uniqueImageName) { result ->
                        result.onSuccess {
                            val pickupImageValue = if (order.status < 4) "1" else "0"
                            updateOrderWithImage(order.orderId, pickupImageValue, uniqueImageName) { updateResult ->
                                updateResult.onSuccess {
                                    // Handle successful order update
                                }.onFailure {
                                    // Handle failed order update
                                }
                            }
                            val button = findViewById<Button>(R.id.update_pickup_button)
                            button.text = "Mark as delivered"

                            button.setOnClickListener {
                                showImagePickerDialog()
                            }
                            // Handle successful upload
                        }.onFailure {
                            // Handle failed upload
                        }
                    }
                }
                REQUEST_GALLERY -> {
                    val selectedImageUri = data?.data
                    order.pickupImageUrl = selectedImageUri.toString()
                    val currentTime = System.currentTimeMillis()
                    val formattedTime = formatTimestampToTime(currentTime.toString())
                    displayPickupImage(order.pickupImageUrl, formattedTime)
                    selectedImageUri?.let {
                        val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, it)
                        val uniqueImageName = generateUniqueImageName()
                        uploadImage(bitmap, uniqueImageName) { result ->
                            result.onSuccess {
                                val pickupImageValue = if (order.status < 4) "1" else "0"
                                updateOrderWithImage(order.orderId, pickupImageValue, uniqueImageName) { updateResult ->
                                    updateResult.onSuccess {
                                        // Handle successful order update
                                    }.onFailure {
                                        // Handle failed order update
                                    }
                                }
                                val button = findViewById<Button>(R.id.update_pickup_button)
                                button.text = "Mark as delivered"
                                // Handle successful upload
                            }.onFailure {
                                // Handle failed upload
                            }
                        }
                    }
                }
            }
        }
    }

    fun updateOrderWithImage(orderId: String, pickupImageValue: String, imageName: String, completion: (Result<String>) -> Unit) {
        val updateOrderURL = "https://minitel.co.uk/app/models/ordersGate?command=updateOrderPod&orderId=$orderId&pickupImage=$pickupImageValue&generatedName=$imageName"
        val client = OkHttpClient()

        val request = Request.Builder()
            .url(updateOrderURL)
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                completion(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    completion(Result.failure(IOException("Unexpected code $response")))
                } else {
                    completion(Result.success(response.body?.string() ?: ""))
                }
            }
        })
    }


    private fun saveBitmapToFile(bitmap: Bitmap): Uri {
        val file = File(getExternalFilesDir(null), "pickup_image.jpg")
        val outputStream = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        outputStream.flush()
        outputStream.close()
        return Uri.fromFile(file)
    }

    private fun displayPickupImage(imageUrl: String, formattedTime: String) {
        val pickupContainer = findViewById<RelativeLayout>(R.id.pickup_container)
        val imageView = findViewById<ImageView>(R.id.pickup_image)
        val pickupTimeTextView = findViewById<TextView>(R.id.pickup_time)

        if (imageUrl.isNotEmpty()) {
            pickupContainer.visibility = View.VISIBLE
            Picasso.get()
                .load(imageUrl)
                .transform(RoundedCornersTransformation(8, 0)) // 8dp corner radius
                .into(imageView)

            // Set the pickup time
            pickupTimeTextView.text = formattedTime
        } else {
            Log.e("OrderViewActivity", "Image URL is empty")
        }
    }


    private fun displayDeliveryImage(imageUrl: String, formattedTime: String) {
        val deliveryContainer = findViewById<RelativeLayout>(R.id.delivery_container)
        val imageView = findViewById<ImageView>(R.id.delivery_image)
        val deliveryTimeTextView = findViewById<TextView>(R.id.delivery_time)

        if (imageUrl.isNotEmpty()) {
            deliveryContainer.visibility = View.VISIBLE
            Picasso.get()
                .load(imageUrl)
                .transform(RoundedCornersTransformation(8, 0)) // 8dp corner radius
                .into(imageView)

            // Set the delivery time
            deliveryTimeTextView.text = formattedTime
        } else {
            Log.e("OrderViewActivity", "Image URL is empty")
        }
    }

    fun generateUniqueImageName(): String {
        val uuid = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis() / 1000 // Current timestamp in seconds
        return "$uuid$timestamp"
    }

    fun uploadImage(bitmap: Bitmap, imageName: String, completion: (Result<String>) -> Unit) {
        // Convert the bitmap to a byte array without compression
        val imageData = ByteArrayOutputStream().apply {
            bitmap.compress(Bitmap.CompressFormat.PNG, 50, this) // PNG format to preserve quality
        }.toByteArray()

        val imageString = Base64.encodeToString(imageData, Base64.NO_WRAP)

        // Prepare the request body in the format expected by the server
        val requestBodyString = "$imageName.pngimage=$imageString"
        val requestBody = RequestBody.create("application/x-www-form-urlencoded".toMediaTypeOrNull(), requestBodyString)

        val uploadURL = "https://hoodapp.co.uk/utlities/saveimage.aspx"
        val client = OkHttpClient()

        val request = Request.Builder()
            .url(uploadURL)
            .post(requestBody)
            .addHeader("Content-Type", "application/x-www-form-urlencoded")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                completion(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    completion(Result.failure(IOException("Unexpected code $response")))
                } else {
                    completion(Result.success(response.body?.string() ?: ""))
                }
            }
        })
    }

    companion object {
        private const val REQUEST_CAMERA = 1
        private const val REQUEST_GALLERY = 2

        fun start(context: Context, order: Order) {
            val intent = Intent(context, OrderActivity::class.java)
            intent.putExtra("order", order)
            context.startActivity(intent)
        }
    }

    fun formatTimestampToTime(timestamp: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val outputFormatDate = SimpleDateFormat("EEE dd-MM HH:mm", Locale.getDefault())
            val outputFormatToday = SimpleDateFormat("HH:mm", Locale.getDefault())
            val etaDate: Date

            // Check if the timestamp is a Unix timestamp
            if (timestamp.length > 10 && timestamp.all { it.isDigit() }) {
                etaDate = Date(timestamp.toLong())
            } else {
                etaDate = inputFormat.parse(timestamp)
            }

            val currentTime = Date()
            val diffInMillis = etaDate.time - currentTime.time
            val diffInMinutes = TimeUnit.MILLISECONDS.toMinutes(diffInMillis)

                val calendar = Calendar.getInstance()
                calendar.time = currentTime

                val etaCalendar = Calendar.getInstance()
                etaCalendar.time = etaDate

                val isToday = calendar.get(Calendar.YEAR) == etaCalendar.get(Calendar.YEAR) &&
                        calendar.get(Calendar.DAY_OF_YEAR) == etaCalendar.get(Calendar.DAY_OF_YEAR)

                if (isToday) {
                    outputFormatToday.format(etaDate)
                } else {
                    outputFormatDate.format(etaDate)
                }

        } catch (e: Exception) {
            // Log the exception and return a default value or error message
            Log.e("formatEtaTime", "Error formatting ETA time: ${e.message}", e)
            timestamp // Return the original value as a fallback
        }
    }

}
