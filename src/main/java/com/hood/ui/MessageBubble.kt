package com.hood.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.hood.R

class MessageBubble @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    init {
        LayoutInflater.from(context).inflate(R.layout.view_message_bubble, this, true)
        orientation = VERTICAL
        background = ContextCompat.getDrawable(context, R.drawable.bubble_background)
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
    }

    fun setMessage(senderName: String, messageText: String, messageTime: String) {
        findViewById<TextView>(R.id.sender_name).text = senderName
        findViewById<TextView>(R.id.message_text).text = messageText
        findViewById<TextView>(R.id.message_time).text = messageTime
    }
}
