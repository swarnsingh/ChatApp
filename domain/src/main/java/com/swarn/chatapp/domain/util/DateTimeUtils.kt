package com.swarn.chatapp.domain.util

import java.text.SimpleDateFormat
import java.util.*

object DateTimeUtils {
    fun formatMessageTime(timestamp: Long): String {
        val date = Date(timestamp)
        val calendar = Calendar.getInstance()
        calendar.time = date
        
        val hour = calendar.get(Calendar.HOUR)
        val minute = calendar.get(Calendar.MINUTE)
        val amPm = if (calendar.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
        
        return String.format("%d:%02d %s", if (hour == 0) 12 else hour, minute, amPm)
    }

    fun formatConversationTime(timestamp: Long): String {
        val date = Date(timestamp)
        val now = Calendar.getInstance()
        val messageDate = Calendar.getInstance().apply { time = date }

        return when {
            now.get(Calendar.DATE) == messageDate.get(Calendar.DATE) -> formatMessageTime(timestamp)
            now.get(Calendar.WEEK_OF_YEAR) == messageDate.get(Calendar.WEEK_OF_YEAR) -> SimpleDateFormat("EEE", Locale.getDefault()).format(date)
            else -> SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(date)
        }
    }
} 