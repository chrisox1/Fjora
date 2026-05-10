package com.example.jellyfinplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.jellyfinplayer.AppViewModel
import com.example.jellyfinplayer.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    vm: AppViewModel,
    onLoginComplete: () -> Unit = {}
) {
    var scheme by remember { mutableStateOf("http") }
    var server by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var passVisible by remember { mutableStateOf(false) }
    val state = vm.uiState.collectAsState().value
    val cs = MaterialTheme.colorScheme

    val userFocus = remember { FocusRequester() }
    val passFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    val canSubmit = state !is UiState.Loading && server.isNotBlank() && user.isNotBlank()
    val submit: () -> Unit = {
        if (canSubmit) {
            keyboard?.hide()
            val fullUrl = "$scheme://${server.trim()}"
            vm.login(fullUrl, user.trim(), pass)
        }
    }

    var wasLoading by remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        when (state) {
            is UiState.Loading -> wasLoading = true
            is UiState.Idle -> if (wasLoading) {
                wasLoading = false
                onLoginComplete()
            }
            is UiState.Error -> wasLoading = false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(cs.background)
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .formContentWidth()
                .align(Alignment.TopCenter)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 80.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                "Sign in",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = cs.onBackground
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Connect to your Jellyfin server",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant
            )

            Spacer(Modifier.height(40.dp))

            Text(
                "Server",
                style = MaterialTheme.typography.labelLarge,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                FilterChip(
                    selected = scheme == "http",
                    onClick = { scheme = "http" },
                    label = { Text("http://") },
                    shape = RoundedCornerShape(8.dp)
                )
                FilterChip(
                    selected = scheme == "https",
                    onClick = { scheme = "https" },
                    label = { Text("https://") },
                    shape = RoundedCornerShape(8.dp)
                )
            }
            OutlinedTextField(
                value = server,
                onValueChange = { input ->
                    when {
                        input.startsWith("https://", ignoreCase = true) -> {
                            scheme = "https"
                            server = input.removePrefix("https://").removePrefix("HTTPS://")
                        }
                        input.startsWith("http://", ignoreCase = true) -> {
                            scheme = "http"
                            server = input.removePrefix("http://").removePrefix("HTTP://")
                        }
                        else -> server = input
                    }
                },
                placeholder = { Text("192.168.1.10:8096") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(onNext = { userFocus.requestFocus() }),
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(Modifier.height(20.dp))

            Text(
                "Username",
                style = MaterialTheme.typography.labelLarge,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            OutlinedTextField(
                value = user,
                onValueChange = { user = it },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(userFocus),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { passFocus.requestFocus() }),
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(Modifier.height(20.dp))

            Text(
                "Password",
                style = MaterialTheme.typography.labelLarge,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            OutlinedTextField(
                value = pass,
                onValueChange = { pass = it },
                trailingIcon = {
                    IconButton(onClick = { passVisible = !passVisible }) {
                        Icon(
                            if (passVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (passVisible) "Hide password" else "Show password"
                        )
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(passFocus),
                visualTransformation = if (passVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Go
                ),
                keyboardActions = KeyboardActions(onGo = { submit() }),
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(Modifier.height(28.dp))

            Button(
                onClick = submit,
                enabled = canSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (state is UiState.Loading) {
                    CircularProgressIndicator(
                        Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = cs.onPrimary
                    )
                } else {
                    Text("Sign in", fontWeight = FontWeight.SemiBold)
                }
            }

            if (state is UiState.Error) {
                Spacer(Modifier.height(16.dp))
                Text(
                    state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.error
                )
            }
        }
    }
}
