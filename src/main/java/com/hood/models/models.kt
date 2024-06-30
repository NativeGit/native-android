package com.hood.models

import android.os.Parcel
import android.os.Parcelable
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class Order(
    val orderId: String,
    val customerName: String,
    val postcode: String,
    val address: String,
    val pickupTime: String,
    val deliveryTime: String,
    val status: Int,
    val packed: Int,
    val phone: String,
    val total: Double,
    val icon: String,
    var pickupImageUrl: String,
    val deliveryImageUrl: String,
    val allocatedTime: String,
    val pickupETA: String,
    val courierId: String,
    val courierName: String,
    val lifecycle: String,
    val items: List<OrderItem> = emptyList(),
    var lifecycleEvents: List<LifecycleEvent> = emptyList()
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readInt(),
        parcel.readInt(),
        parcel.readString() ?: "",
        parcel.readDouble(),
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.createTypedArrayList(OrderItem) ?: emptyList(),
        emptyList()
    ) {
        lifecycleEvents = parseLifecycleEvents(lifecycle)
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(orderId)
        parcel.writeString(customerName)
        parcel.writeString(postcode)
        parcel.writeString(address)
        parcel.writeString(pickupTime)
        parcel.writeString(deliveryTime)
        parcel.writeInt(status)
        parcel.writeInt(packed)
        parcel.writeString(phone)
        parcel.writeDouble(total)
        parcel.writeString(icon)
        parcel.writeString(pickupImageUrl)
        parcel.writeString(deliveryImageUrl)
        parcel.writeString(allocatedTime)
        parcel.writeString(pickupETA)
        parcel.writeString(courierId)
        parcel.writeString(courierName)
        parcel.writeString(lifecycle)
        parcel.writeTypedList(items)
        parcel.writeTypedList(lifecycleEvents)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<Order> {
        override fun createFromParcel(parcel: Parcel): Order {
            return Order(parcel)
        }

        override fun newArray(size: Int): Array<Order?> {
            return arrayOfNulls(size)
        }

        private fun parseLifecycleEvents(lifecycle: String): List<LifecycleEvent> {
            Log.d("Order", "Parsing lifecycle events from: $lifecycle")
            val events = lifecycle.split(",")
                .mapNotNull { eventString ->
                    val parts = eventString.split("*")
                    if (parts.size == 3) {
                        val event = LifecycleEvent(parts[0], parts[1], parts[2])
                        Log.d("Order", "Parsed event: $event")
                        event
                    } else {
                        Log.e("Order", "Failed to parse event: $eventString")
                        null
                    }
                }
            Log.d("Order", "Parsed lifecycle events: $events")
            return events
        }
    }

    fun getStatusText(): String {
        return when (status) {
            1 -> "Received"
            2 -> "Assigned"
            3 -> "Heading to Pickup"
            4 -> "En route"
            5 -> "Delivered"
            7 -> "Cancelled"
            else -> "Status Unknown"
        }
    }


    fun getLifecycleEvent(action: String): LifecycleEvent? {
        Log.d("Order", "Searching for action: $action")
        val trimmedAction = action.trim().lowercase(Locale.getDefault())
        lifecycleEvents.forEach {
            Log.d("Order", "LifecycleEvent: action=${it.action}, value=${it.value}, timestamp=${it.timestamp}")
        }
        val event = lifecycleEvents.firstOrNull { it.action.trim().lowercase(Locale.getDefault()) == trimmedAction }
        if (event == null) {
            Log.e("Order", "No matching LifecycleEvent found for action: $action")
        } else {
            Log.d("Order", "Found matching LifecycleEvent: action=${event.action}, value=${event.value}, timestamp=${event.timestamp}")
        }
        return event
    }

    fun updateLifecycleEvent(action: String, newValue: String) {
        val timestamp = System.currentTimeMillis().toString()
        val lifecycleEvent = LifecycleEvent(action, newValue, timestamp)
        lifecycleEvents = lifecycleEvents.filterNot { it.action == action } + lifecycleEvent
    }

    fun extractEtaTime(value: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val outputFormatDate = SimpleDateFormat("EEE dd-MM HH:mm", Locale.getDefault())

            val etaDate: Date = inputFormat.parse(value)
            val currentTime = Date()

            val diffInMillis = etaDate.time - currentTime.time
            val diffInMinutes = TimeUnit.MILLISECONDS.toMinutes(diffInMillis)

            if (diffInMinutes in 0..59) {
                "$diffInMinutes min"
            } else {
                outputFormatDate.format(etaDate)
            }
        } catch (e: Exception) {
            // Log the exception and return a default value or error message
            Log.e("formatEtaTime", "Error formatting ETA time: ${e.message}", e)
            value // Return the original value as a fallback
        }
    }

    fun extractTime(timestamp: String): String {
        return try{
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
            val outputFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val date = inputFormat.parse(timestamp)
            date?.let { outputFormat.format(it) } ?: timestamp
        } catch (e: Exception) {
            timestamp
        }
    }
}

data class OrderItem(
    val name: String,
    val quantity: Int,
    val price: Double
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readInt(),
        parcel.readDouble()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(name)
        parcel.writeInt(quantity)
        parcel.writeDouble(price)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<OrderItem> {
        override fun createFromParcel(parcel: Parcel): OrderItem {
            return OrderItem(parcel)
        }

        override fun newArray(size: Int): Array<OrderItem?> {
            return arrayOfNulls(size)
        }
    }
}

data class LifecycleEvent(
    val action: String,
    val value: String,
    val timestamp: String
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: ""
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(action)
        parcel.writeString(value)
        parcel.writeString(timestamp)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<LifecycleEvent> {
        override fun createFromParcel(parcel: Parcel): LifecycleEvent {
            return LifecycleEvent(parcel)
        }

        override fun newArray(size: Int): Array<LifecycleEvent?> {
            return arrayOfNulls(size)
        }
    }
}

data class Product(
    val id: Int,
    val name: String,
    val description: String,
    val price: Double,
    val imageURL: String,
    val status: Int,
    val category: String,
    var options: List<String>,  // Adjust the type as needed
    val amount: Int
) {
    fun parseOptions(optionsString: String): List<String> {
        // Implement your parsing logic here
        return optionsString.split(",").map { it.trim() }
    }
}