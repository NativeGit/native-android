package com.hood.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.hood.R
import com.hood.models.Order
import com.hood.activities.OrderActivity

class SectionedOrderAdapter(
    private val nowOrders: List<Order>,
    private val laterOrders: List<Order>,
    private val sentOrders: List<Order>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
    }

    private enum class Section(val title: String) {
        NOW("Now"),
        LATER("Later"),
        SENT("Sent")
    }

    private data class SectionedOrder(val section: Section, val order: Order?)

    private val sectionedOrders = mutableListOf<SectionedOrder>()

    fun updateOrders(nowOrders: List<Order>, laterOrders: List<Order>, sentOrders: List<Order>) {
        sectionedOrders.clear()

        sectionedOrders.add(SectionedOrder(Section.NOW, null))
        sectionedOrders.addAll(nowOrders.map { SectionedOrder(Section.NOW, it) })

        sectionedOrders.add(SectionedOrder(Section.LATER, null))
        sectionedOrders.addAll(laterOrders.map { SectionedOrder(Section.LATER, it) })

        sectionedOrders.add(SectionedOrder(Section.SENT, null))
        sectionedOrders.addAll(sentOrders.map { SectionedOrder(Section.SENT, it) })

        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return if (sectionedOrders[position].order == null) TYPE_HEADER else TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_HEADER) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.section_header, parent, false)
            HeaderViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.order_item, parent, false)
            OrderViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val sectionedOrder = sectionedOrders[position]
        if (holder is HeaderViewHolder) {
            holder.headerTitle.text = sectionedOrder.section.title
        } else if (holder is OrderViewHolder) {
            val order = sectionedOrder.order!!
            holder.customerName.text = "#${order.orderId} ${order.customerName}"
            holder.postcodeAddress.text = "${order.postcode}  ${order.address}"
            holder.status.text = order.getStatusText()

            holder.itemView.setOnClickListener {
                OrderActivity.start(holder.itemView.context, order)
            }
        }
    }

    override fun getItemCount(): Int {
        return sectionedOrders.size
    }

    class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val headerTitle: TextView = itemView.findViewById(R.id.header_title)
    }

    class OrderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val customerName: TextView = itemView.findViewById(R.id.customer_name)
        val postcodeAddress: TextView = itemView.findViewById(R.id.postcode_address)
        val status: TextView = itemView.findViewById(R.id.status)
    }
}
