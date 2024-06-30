package com.hood.fragments

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.example.hood.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.hood.models.Order
import okhttp3.*
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class CollectionSheetFragment : BottomSheetDialogFragment() {
    interface OnCollectionSheetInteractionListener {
        fun onCollectionUpdated(order: Order)
    }

    private var listener: OnCollectionSheetInteractionListener? = null

    companion object {
        const val ARG_ORDER = "order"
        const val ARG_MODE = "mode"

        fun newInstance(order: Order, mode: SheetMode): CollectionSheetFragment {
            val fragment = CollectionSheetFragment()
            val args = Bundle()
            args.putParcelable(ARG_ORDER, order)
            args.putSerializable(ARG_MODE, mode)
            fragment.arguments = args
            return fragment
        }
    }

    enum class SheetMode {
        COLLECTION,
        DELIVERY
    }

    private lateinit var order: Order
    private lateinit var mode: SheetMode

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            order = it.getParcelable(ARG_ORDER)!!
            mode = it.getSerializable(ARG_MODE) as SheetMode
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_collection_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val title = view.findViewById<TextView>(R.id.title)
        val minutesInput = view.findViewById<EditText>(R.id.minutes_input)
        val allocateButton = view.findViewById<Button>(R.id.allocate_button)

        title.text = if (mode == SheetMode.COLLECTION) "Collection in" else "Delivery in"
        allocateButton.text = "Update"

        allocateButton.setOnClickListener {
            val minutes = minutesInput.text.toString()
            if (minutes.isEmpty()) {
                Toast.makeText(context, "Please enter minutes", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            handleAction(minutes)
            dismiss()
        }
    }

    private fun handleAction(minutes: String) {
        val endpoint = if (mode == SheetMode.COLLECTION) "updatePickup" else "updateDelivery"
        updateOrderStatusWithCourier(minutes, endpoint)
    }

    private fun updateOrderStatusWithCourier(minutes: String, endpoint: String) {
        val minutesToAdd = minutes.toIntOrNull() ?: run {
            Toast.makeText(context, "Invalid minutes input", Toast.LENGTH_SHORT).show()
            return
        }

        val futureDate = Calendar.getInstance().apply {
            add(Calendar.MINUTE, minutesToAdd)
        }.time

        val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val formattedDate = dateFormatter.format(futureDate)
        val urlString = "https://minitel.co.uk/app/models/ordersGate?command=$endpoint&id=${order.orderId}&${if (endpoint == "updatePickup") "pickupTime" else "delivery"}=$formattedDate"

        sendHTTPRequest(urlString, formattedDate)

        order.updateLifecycleEvent(if (mode == SheetMode.COLLECTION) "pickupEta" else "deliveryEta", formattedDate)
        listener?.onCollectionUpdated(order)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnCollectionSheetInteractionListener) {
            listener = context
        } else {
            throw RuntimeException("$context must implement OnCollectionSheetInteractionListener")
        }
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    private fun sendHTTPRequest(urlString: String, formattedDate: String) {
        val request = Request.Builder().url(urlString).build()
        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    println("Unexpected response status code: ${response.code}")
                    return
                }

                activity?.runOnUiThread {
                    // Manually update the order object
                    order.updateLifecycleEvent(if (mode == SheetMode.COLLECTION) "pickupEta" else "deliveryEta", formattedDate)
                    listener?.onCollectionUpdated(order)
                }
            }
        })
    }
}
