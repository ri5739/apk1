package com.example.auth

import android.content.Context
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

fun attemptAutoSignIn(
    context: Context,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope
) {
    if (Firebase.auth.currentUser != null) {
        onAuthSuccess()
        return
    }
    scope.launch {
        try {
            Firebase.auth.signInAnonymously().await()
            onAuthSuccess()
        } catch (_: Exception) {
            onUnauthenticated()
        }
    }
}

@Composable
fun ContinueWithoutGoogleButton(
    label: String,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }

    Button(
        onClick = {
            isLoading = true
            scope.launch {
                try {
                    Firebase.auth.signInAnonymously().await()
                    isLoading = false
                    onAuthSuccess()
                } catch (e: Exception) {
                    isLoading = false
                    onAuthError(e.localizedMessage ?: "অ্যাকাউন্ট চালু করা যায়নি। Firebase সেটআপ পরীক্ষা করুন।")
                }
            }
        },
        enabled = !isLoading,
        modifier = modifier.fillMaxWidth().height(52.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp
            )
        } else {
            Text(text = label, style = MaterialTheme.typography.titleMedium)
        }
    }
}

fun signOutUser(
    scope: CoroutineScope,
    onBeforeSignOut: () -> Unit = {},
    onSignOutComplete: () -> Unit = {}
) {
    scope.launch {
        try {
            onBeforeSignOut()
        } finally {
            Firebase.auth.signOut()
            onSignOutComplete()
        }
    }
}
