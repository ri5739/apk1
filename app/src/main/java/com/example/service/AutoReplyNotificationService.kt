package com.example.service

import android.app.Notification
import android.app.RemoteInput
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.ai.AiReplyEngine
import com.example.data.firebase.ShopRepository
import com.example.data.local.LocalBridgeCache
import com.example.data.model.MessagingPlatform
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class AutoReplyNotificationService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var localCache: LocalBridgeCache
    private val aiEngine by lazy { AiReplyEngine() }
    private val recentReplyTimestamps = ConcurrentHashMap<String, Long>()

    override fun onCreate() {
        super.onCreate()
        localCache = LocalBridgeCache(applicationContext)
        if (FirebaseApp.getApps(applicationContext).isEmpty()) {
            FirebaseApp.initializeApp(applicationContext)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val statusBarNotification = sbn ?: return
        val pkg = statusBarNotification.packageName ?: return

        // Never respond to our own app or ongoing system notifications
        if (pkg == packageName) return
        val notification = statusBarNotification.notification ?: return
        if ((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0) return
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return

        val profile = localCache.getProfile()
        if (!profile.autoReplyEnabled) return
        if (!profile.enabledApps.contains(pkg)) return

        // Find an action that supports inline RemoteInput reply
        val replyAction = notification.actions?.firstOrNull { action ->
            action.remoteInputs != null && action.remoteInputs.isNotEmpty() && action.actionIntent != null
        } ?: return

        val extras = notification.extras ?: return
        val isGroupConversation = extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)
        if (isGroupConversation && !profile.replyToGroups) return

        val (senderName, messageText) = extractSenderAndMessage(extras)
        if (senderName.isEmpty() || messageText.isEmpty()) return
        if (senderName.equals("You", ignoreCase = true)) return

        // Cooldown deduplication to prevent infinite reply loops or duplicate triggers
        val dedupKey = "$pkg|$senderName|$messageText"
        val now = System.currentTimeMillis()
        val lastSent = recentReplyTimestamps[dedupKey]
        if (lastSent != null && (now - lastSent) < COOLDOWN_MS) return
        recentReplyTimestamps[dedupKey] = now

        val products = localCache.getProducts()
        val platform = MessagingPlatform.fromPackageName(pkg)

        serviceScope.launch {
            try {
                val aiResult = aiEngine.generateReply(
                    senderName = senderName,
                    incomingMessage = messageText,
                    profile = profile,
                    products = products
                )

                val sent = sendRemoteInputReply(replyAction, aiResult.reply)
                if (sent) {
                    Log.d(TAG, "Auto-replied to $senderName via $pkg [${aiResult.intent}]")
                    val currentUid = Firebase.auth.currentUser?.uid
                    if (!currentUid.isNullOrBlank()) {
                        val repo = ShopRepository(applicationContext)
                        repo.addReplyLog(
                            userId = currentUid,
                            senderName = senderName,
                            platform = platform,
                            incomingMessage = messageText,
                            aiReply = aiResult.reply,
                            intent = aiResult.intent,
                            repliedAutomatically = true
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to auto-reply to $pkg notification", e)
            }
        }
    }

    private fun extractSenderAndMessage(extras: Bundle): Pair<String, String> {
        val sender = extras.getCharSequence(Notification.EXTRA_TITLE)
            ?.toString()?.trim().orEmpty()

        val message = sequenceOf(
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        ).mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
            .firstOrNull().orEmpty()

        val resolvedMessage = if (message.isNotBlank()) {
            message
        } else {
            extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
                ?.mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotBlank) }
                ?.lastOrNull().orEmpty()
        }

        return sender to resolvedMessage
    }

    private fun sendRemoteInputReply(action: Notification.Action, replyText: String): Boolean {
        val remoteInputs = action.remoteInputs ?: return false
        val pendingIntent = action.actionIntent ?: return false

        return try {
            val intent = Intent()
            val bundle = Bundle()
            for (remoteInput in remoteInputs) {
                bundle.putCharSequence(remoteInput.resultKey, replyText)
            }
            RemoteInput.addResultsToIntent(remoteInputs, intent, bundle)
            pendingIntent.send(applicationContext, 0, intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "RemoteInput send failed", e)
            false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        private const val TAG = "AutoReplyService"
        private const val COOLDOWN_MS = 15_000L

        fun isNotificationListenerEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return false
            val componentName = ComponentName(context, AutoReplyNotificationService::class.java)
            return flat.split(":").any {
                ComponentName.unflattenFromString(it) == componentName
            }
        }

        fun openNotificationListenerSettings(context: Context) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
