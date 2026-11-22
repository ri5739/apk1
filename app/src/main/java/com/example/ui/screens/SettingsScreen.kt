package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AppLanguage
import com.example.data.model.BusinessProfile
import com.example.data.model.DEFAULT_SUPPORTED_CHANNELS
import com.example.data.model.GreetingStyle
import com.example.service.AutoReplyNotificationService
import com.example.ui.AppStrings
import com.example.ui.BengaliStrings

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    strings: AppStrings,
    profile: BusinessProfile,
    onSaveProfile: (BusinessProfile) -> Unit,
    onSignOut: () -> Unit
) {
    var businessName by remember(profile.businessName) { mutableStateOf(profile.businessName) }
    var ownerName by remember(profile.ownerName) { mutableStateOf(profile.ownerName) }
    var supportPolicy by remember(profile.supportPolicy) { mutableStateOf(profile.supportPolicy) }
    var greetingStyle by remember(profile.greetingStyle) {
        mutableStateOf(GreetingStyle.fromRaw(profile.greetingStyle))
    }
    var casualChatEnabled by remember(profile.casualChatEnabled) {
        mutableStateOf(profile.casualChatEnabled)
    }
    var replyToGroups by remember(profile.replyToGroups) {
        mutableStateOf(profile.replyToGroups)
    }
    var enabledApps by remember(profile.enabledApps) {
        mutableStateOf(profile.enabledApps.toSet())
    }
    var language by remember(profile.languagePreference) {
        mutableStateOf(AppLanguage.fromRaw(profile.languagePreference))
    }
    val isBn = strings == BengaliStrings

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    text = strings.settingsTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                val context = LocalContext.current
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (isBn) "WhatsApp / Messenger সংযোগ" else "WhatsApp / Messenger Connection",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isBn)
                                "অটো রিপ্লাই চালাতে ShopReply AI-কে Notification Access দিতে হবে। নিচের বাটনে চাপ দিয়ে সেটিংস খুলুন, ShopReply AI চালু করুন, তারপর অ্যাপে ফিরে আসুন।"
                            else
                                "Auto-reply needs Notification Access. Open settings below, enable ShopReply AI, then return to the app.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Button(
                            onClick = { AutoReplyNotificationService.openNotificationListenerSettings(context) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (isBn) "Notification Access চালু / পরীক্ষা করুন" else "Enable / Check Notification Access")
                        }
                        Text(
                            text = if (isBn)
                                "WhatsApp ও Messenger-এর Notification এবং Reply অপশন চালু রাখুন। শুধু Notification-এ Reply action থাকলে স্বয়ংক্রিয় উত্তর পাঠানো সম্ভব।"
                            else
                                "Keep notifications and reply actions enabled in WhatsApp/Messenger. Automatic replies only work when a notification exposes a reply action.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = businessName,
                            onValueChange = { businessName = it.take(100) },
                            label = { Text(strings.businessNameLabel) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_business_name_input")
                        )
                        OutlinedTextField(
                            value = ownerName,
                            onValueChange = { ownerName = it.take(100) },
                            label = { Text(strings.ownerNameLabel) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = supportPolicy,
                            onValueChange = { supportPolicy = it.take(1500) },
                            label = { Text(strings.supportPolicyLabel) },
                            minLines = 3,
                            maxLines = 6,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_policy_input")
                        )

                        Text(
                            text = strings.aiToneLabel,
                            style = MaterialTheme.typography.labelLarge
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            GreetingStyle.entries.forEach { style ->
                                FilterChip(
                                    selected = greetingStyle == style,
                                    onClick = { greetingStyle = style },
                                    label = { Text(strings.greetingLabel(style)) }
                                )
                            }
                        }

                        Text(
                            text = strings.uiLanguageLabel,
                            style = MaterialTheme.typography.labelLarge
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = language == AppLanguage.BN,
                                onClick = { language = AppLanguage.BN },
                                label = { Text("বাংলা") }
                            )
                            FilterChip(
                                selected = language == AppLanguage.EN,
                                onClick = { language = AppLanguage.EN },
                                label = { Text("English") }
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = strings.casualChatToggleTitle,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = strings.casualChatToggleDesc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = casualChatEnabled,
                                onCheckedChange = { casualChatEnabled = it }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = strings.groupReplyToggleTitle,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = strings.groupReplyToggleDesc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = replyToGroups,
                                onCheckedChange = { replyToGroups = it }
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = strings.enabledAppsTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        DEFAULT_SUPPORTED_CHANNELS.forEach { channel ->
                            val checked = enabledApps.contains(channel.packageName)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { isChecked ->
                                        enabledApps = if (isChecked) {
                                            enabledApps + channel.packageName
                                        } else {
                                            enabledApps - channel.packageName
                                        }
                                    }
                                )
                                Text(
                                    text = if (isBn) channel.labelBn else channel.labelEn,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        onSaveProfile(
                            profile.copy(
                                businessName = businessName,
                                ownerName = ownerName,
                                supportPolicy = supportPolicy,
                                greetingStyle = greetingStyle.raw,
                                casualChatEnabled = casualChatEnabled,
                                replyToGroups = replyToGroups,
                                enabledApps = enabledApps.toList(),
                                languagePreference = language.raw
                            )
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("save_settings_button")
                ) {
                    Text(strings.saveLabel)
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.BatteryChargingFull,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Column {
                            Text(
                                text = strings.batteryTipTitle,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = strings.batteryTipDesc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = onSignOut,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("sign_out_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.signOutLabel)
                }
            }
        }
    }
}
