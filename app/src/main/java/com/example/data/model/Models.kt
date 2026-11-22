package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class GreetingStyle(val raw: String) {
    FRIENDLY("FRIENDLY"),
    PROFESSIONAL("PROFESSIONAL"),
    CASUAL("CASUAL");

    companion object {
        fun fromRaw(raw: String?): GreetingStyle =
            entries.firstOrNull { it.raw == raw } ?: FRIENDLY
    }
}

enum class MessageIntent(val raw: String) {
    PRODUCT_INQUIRY("PRODUCT_INQUIRY"),
    CASUAL_CHAT("CASUAL_CHAT"),
    CUSTOMER_SUPPORT("CUSTOMER_SUPPORT");

    companion object {
        fun fromRaw(raw: String?): MessageIntent =
            entries.firstOrNull { it.raw == raw } ?: CUSTOMER_SUPPORT
    }
}

enum class MessagingPlatform(val raw: String, val packageName: String?) {
    WHATSAPP("WHATSAPP", "com.whatsapp"),
    WHATSAPP_BUSINESS("WHATSAPP_BUSINESS", "com.whatsapp.w4b"),
    MESSENGER("MESSENGER", "com.facebook.orca"),
    INSTAGRAM("INSTAGRAM", "com.instagram.android"),
    SIMULATOR("SIMULATOR", null),
    OTHER("OTHER", null);

    companion object {
        fun fromRaw(raw: String?): MessagingPlatform =
            entries.firstOrNull { it.raw == raw } ?: OTHER

        fun fromPackageName(pkg: String): MessagingPlatform = when (pkg) {
            "com.whatsapp" -> WHATSAPP
            "com.whatsapp.w4b" -> WHATSAPP_BUSINESS
            "com.facebook.orca", "com.facebook.pages.app", "com.facebook.katana" -> MESSENGER
            "com.instagram.android" -> INSTAGRAM
            else -> OTHER
        }
    }
}

enum class AppLanguage(val raw: String) {
    BN("BN"),
    EN("EN");

    companion object {
        fun fromRaw(raw: String?): AppLanguage =
            entries.firstOrNull { it.raw == raw } ?: BN
    }
}

data class SupportedAppChannel(
    val packageName: String,
    val labelBn: String,
    val labelEn: String
)

val DEFAULT_SUPPORTED_CHANNELS = listOf(
    SupportedAppChannel("com.whatsapp.w4b", "WhatsApp Business", "WhatsApp Business"),
    SupportedAppChannel("com.whatsapp", "WhatsApp", "WhatsApp"),
    SupportedAppChannel("com.facebook.orca", "Facebook Messenger", "Facebook Messenger"),
    SupportedAppChannel("com.facebook.pages.app", "Meta Business Suite / Page", "Meta Business Suite / Page"),
    SupportedAppChannel("com.instagram.android", "Instagram Direct", "Instagram Direct")
)

@Keep
@IgnoreExtraProperties
data class BusinessProfile(
    val userId: String = "",
    val businessName: String = "My Online Shop",
    val ownerName: String = "",
    val greetingStyle: String = GreetingStyle.FRIENDLY.raw,
    val supportPolicy: String = "ঢাকার ভেতরে ডেলিভারি চার্জ ৬০ টাকা, ঢাকার বাইরে ১২০ টাকা। পণ্য হাতে পেয়ে ডেলিভারি ম্যানের সামনে চেক করে নেয়ার সুবিধা রয়েছে।",
    val casualChatEnabled: Boolean = true,
    val autoReplyEnabled: Boolean = true,
    val replyToGroups: Boolean = false,
    val enabledApps: List<String> = listOf(
        "com.whatsapp.w4b",
        "com.whatsapp",
        "com.facebook.orca",
        "com.facebook.pages.app"
    ),
    val languagePreference: String = AppLanguage.BN.raw,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Product(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val price: Double = 0.0,
    val currency: String = "৳",
    val category: String = "",
    val description: String = "",
    val inStock: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ReplyLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val senderName: String = "",
    val platform: String = MessagingPlatform.SIMULATOR.raw,
    val incomingMessage: String = "",
    val aiReply: String = "",
    val intent: String = MessageIntent.PRODUCT_INQUIRY.raw,
    val repliedAutomatically: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
