package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.AppLanguage
import com.example.data.model.BusinessProfile
import com.example.data.model.GreetingStyle
import com.example.data.model.Product
import org.json.JSONArray
import org.json.JSONObject

/**
 * Lightweight SharedPreferences cache bridging Firestore UI state with the
 * background [com.example.service.AutoReplyNotificationService] so incoming
 * notifications can be answered immediately 24/7 with zero server needed.
 */
class LocalBridgeCache(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveProfile(profile: BusinessProfile) {
        val obj = JSONObject().apply {
            put("userId", profile.userId)
            put("businessName", profile.businessName)
            put("ownerName", profile.ownerName)
            put("greetingStyle", profile.greetingStyle)
            put("supportPolicy", profile.supportPolicy)
            put("casualChatEnabled", profile.casualChatEnabled)
            put("autoReplyEnabled", profile.autoReplyEnabled)
            put("replyToGroups", profile.replyToGroups)
            put("languagePreference", profile.languagePreference)
            put("enabledApps", JSONArray(profile.enabledApps))
        }
        prefs.edit().putString(KEY_PROFILE, obj.toString()).apply()
    }

    fun getProfile(): BusinessProfile {
        val raw = prefs.getString(KEY_PROFILE, null) ?: return BusinessProfile()
        return try {
            val obj = JSONObject(raw)
            val appsArray = obj.optJSONArray("enabledApps")
            val apps = if (appsArray != null) {
                (0 until appsArray.length()).mapNotNull { idx ->
                    appsArray.optString(idx).takeIf { it.isNotBlank() }
                }
            } else {
                BusinessProfile().enabledApps
            }
            BusinessProfile(
                userId = obj.optString("userId", ""),
                businessName = obj.optString("businessName", "My Online Shop"),
                ownerName = obj.optString("ownerName", ""),
                greetingStyle = GreetingStyle.fromRaw(obj.optString("greetingStyle")).raw,
                supportPolicy = obj.optString("supportPolicy", BusinessProfile().supportPolicy),
                casualChatEnabled = obj.optBoolean("casualChatEnabled", true),
                autoReplyEnabled = obj.optBoolean("autoReplyEnabled", true),
                replyToGroups = obj.optBoolean("replyToGroups", false),
                enabledApps = apps,
                languagePreference = AppLanguage.fromRaw(obj.optString("languagePreference")).raw
            )
        } catch (_: Exception) {
            BusinessProfile()
        }
    }

    fun saveProducts(products: List<Product>) {
        val array = JSONArray()
        products.forEach { product ->
            val obj = JSONObject().apply {
                put("id", product.id)
                put("userId", product.userId)
                put("name", product.name)
                put("price", product.price)
                put("currency", product.currency)
                put("category", product.category)
                put("description", product.description)
                put("inStock", product.inStock)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_PRODUCTS, array.toString()).apply()
    }

    fun getProducts(): List<Product> {
        val raw = prefs.getString(KEY_PRODUCTS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { idx ->
                val obj = array.optJSONObject(idx) ?: return@mapNotNull null
                Product(
                    id = obj.optString("id", ""),
                    userId = obj.optString("userId", ""),
                    name = obj.optString("name", ""),
                    price = obj.optDouble("price", 0.0),
                    currency = obj.optString("currency", "৳"),
                    category = obj.optString("category", ""),
                    description = obj.optString("description", ""),
                    inStock = obj.optBoolean("inStock", true)
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun clearOnSignOut() {
        prefs.edit().remove(KEY_PROFILE).remove(KEY_PRODUCTS).commit()
    }

    companion object {
        private const val PREFS_NAME = "shopreply_bridge_cache"
        private const val KEY_PROFILE = "cached_profile"
        private const val KEY_PRODUCTS = "cached_products"
    }
}
