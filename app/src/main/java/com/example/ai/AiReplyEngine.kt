package com.example.ai

import android.util.Log
import com.example.data.model.BusinessProfile
import com.example.data.model.GreetingStyle
import com.example.data.model.MessageIntent
import com.example.data.model.Product
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class AiReplyResult(
    val intent: MessageIntent,
    val reply: String,
    val matchedProductName: String?,
    val summaryNote: String
)

class AiReplyEngine {

    private val replySchema = Schema.obj(
        properties = mapOf(
            "intent" to Schema.enumeration(
                values = listOf("PRODUCT_INQUIRY", "CASUAL_CHAT", "CUSTOMER_SUPPORT"),
                description = "Classify client message: PRODUCT_INQUIRY if asking about items/prices/availability/ordering, CASUAL_CHAT if greeting/small talk/friendly chat, CUSTOMER_SUPPORT if delivery/complaint/refund/help."
            ),
            "reply" to Schema.string(
                description = "Natural, warm, concise messaging reply ready to send directly to the customer on WhatsApp or Facebook Messenger in the same language (Bengali, Banglish, or English) the customer used."
            ),
            "matchedProductName" to Schema.string(
                description = "Name of the catalog product mentioned if applicable, otherwise empty string.",
                nullable = true
            ),
            "summaryNote" to Schema.string(
                description = "Brief 1-sentence note for the shop owner explaining why this reply was chosen."
            )
        ),
        optionalProperties = listOf("matchedProductName")
    )

    private val structuredModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = replySchema
                temperature = 0.65f
            }
        )
    }

    suspend fun generateReply(
        senderName: String,
        incomingMessage: String,
        profile: BusinessProfile,
        products: List<Product>
    ): AiReplyResult {
        val prompt = buildPrompt(senderName, incomingMessage, profile, products)
        return try {
            val response = withTimeout(TIMEOUT_MS) {
                structuredModel.generateContent(prompt)
            }
            val jsonString = response.text
                ?: throw IllegalStateException("AI model returned an empty response.")
            val json = JSONObject(jsonString)
            val intentRaw = json.optString("intent", MessageIntent.CUSTOMER_SUPPORT.raw)
            val replyText = json.optString("reply", "").trim()
            if (replyText.isEmpty()) {
                throw IllegalStateException("AI model returned a blank reply.")
            }
            val matchedProduct = json.optString("matchedProductName", "")
                .takeIf { it.isNotBlank() && it != "null" }
            val summaryNote = json.optString("summaryNote", "").trim()

            AiReplyResult(
                intent = MessageIntent.fromRaw(intentRaw),
                reply = replyText,
                matchedProductName = matchedProduct,
                summaryNote = summaryNote
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI reply timed out after ${TIMEOUT_MS / 1000}s", e)
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI reply generation failed", e)
            throw e
        }
    }

    private fun buildPrompt(
        senderName: String,
        incomingMessage: String,
        profile: BusinessProfile,
        products: List<Product>
    ): String {
        val toneGuide = when (GreetingStyle.fromRaw(profile.greetingStyle)) {
            GreetingStyle.FRIENDLY -> "Warm, polite, friendly, human-like shopkeeper tone (use natural greetings like আসসালামু আলাইকুম / ভাইয়া / আপু when chatting in Bengali or Banglish)."
            GreetingStyle.PROFESSIONAL -> "Polite, clear, respectful customer care tone with crisp details."
            GreetingStyle.CASUAL -> "Relaxed, conversational, buddy-like tone so normal chat feels fun and genuine."
        }

        val catalogText = if (products.isEmpty()) {
            "No products are listed in the catalog yet. If the customer asks about a specific product, politely ask them which item they are looking for or mention that the shop owner will confirm shortly."
        } else {
            products.joinToString("\n") { product ->
                val stockText = if (product.inStock) "In Stock" else "Currently Out of Stock"
                val priceFormatted = if (product.price % 1.0 == 0.0) {
                    product.price.toLong().toString()
                } else {
                    product.price.toString()
                }
                "- ${product.name} | Price: ${product.currency}$priceFormatted | Category: ${product.category.ifBlank { "General" }} | Status: $stockText | Details: ${product.description.ifBlank { "Standard quality item" }}"
            }
        }

        val casualRule = if (profile.casualChatEnabled) {
            "If the customer is just greeting, joking, or making normal casual conversation (e.g. কেমন আছেন, কি অবস্থা, গল্প করা), chat naturally and warmly like a real friendly human without forcing a sales pitch."
        } else {
            "If the customer sends a casual greeting, greet them politely and gently ask how you can help them with products or support today."
        }

        return """
You are the 24/7 AI Messaging Assistant for "${profile.businessName}"${if (profile.ownerName.isNotBlank()) " (Owner: ${profile.ownerName})" else ""}.
Your job is to reply directly to incoming messages from WhatsApp Business, Facebook Page, or Messenger clients.

CRITICAL RULES:
1. Language Mirroring: Reply in the EXACT language and script style of the client's message. If they write in Bengali (বাংলা), reply in natural Bengali. If they write in Banglish (e.g. "dam koto vai"), reply in warm Banglish or polite Bengali. If they write in English, reply in English.
2. Intent Behavior:
   - PRODUCT_INQUIRY: Answer accurately using ONLY the Product Catalog below (quote exact prices, stock status, and details). Never invent fake products or fake prices.
   - CASUAL_CHAT: $casualRule
   - CUSTOMER_SUPPORT: Act like a helpful, empathetic customer service representative using the Support & Delivery Policy below. Help solve delivery, return, payment, or order issues calmly.
3. Tone: $toneGuide
4. Format: Write a concise, natural chat message (1-4 short sentences, no markdown formatting or asterisks, suitable for WhatsApp/Messenger).

SUPPORT & DELIVERY POLICY:
${profile.supportPolicy.ifBlank { "Standard friendly support: ask for order details so the team can assist." }}

PRODUCT CATALOG:
$catalogText

INCOMING CLIENT MESSAGE:
Sender: $senderName
Message: "$incomingMessage"
        """.trimIndent()
    }

    companion object {
        private const val TAG = "AiReplyEngine"
        private const val MODEL_NAME = "gemini-3.8-flash"
        private const val TIMEOUT_MS = 60_000L
    }
}
