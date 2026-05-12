package com.example.jellyfinplayer.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.jellyfinplayer.R

@Composable
fun WelcomeScreen(onContinue: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(cs.background)
            .padding(horizontal = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                contentDescription = "Fjora",
                modifier = Modifier.size(120.dp)
            )
            Spacer(Modifier.height(18.dp))
            Text(
                "Welcome to Fjora",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = cs.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))
            Text(
                "Fjora is a third-party client for Jellyfin. Fjora by itself does not contain any media.",
                style = MaterialTheme.typography.bodyLarge,
                color = cs.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))
            Text(
                "In order to use this client, you need to host your own Jellyfin server.",
                style = MaterialTheme.typography.bodyLarge,
                color = cs.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(48.dp))
            OutlinedButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://jellyfin.org"))
                    runCatching { context.startActivity(intent) }
                },
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text("Learn more about Jellyfin", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onContinue,
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text("Continue", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
