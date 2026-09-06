package com.aiko.lingo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AikoLingoApp() }
    }
}

@Composable
private fun AikoLingoApp() {
    MaterialTheme {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Aiko Lingo", style = MaterialTheme.typography.headlineLarge)
            Text("Learn English with Aiko", modifier = Modifier.padding(top = 8.dp, bottom = 32.dp))
            Button(onClick = { }) { Text("Translate") }
            Button(onClick = { }, modifier = Modifier.padding(top = 12.dp)) { Text("Conversation") }
        }
    }
}
