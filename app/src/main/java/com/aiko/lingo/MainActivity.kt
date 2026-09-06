package com.aiko.lingo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aiko.lingo.data.remote.AikoApiService
import com.aiko.lingo.ui.conversation.ConversationScreen
import com.aiko.lingo.ui.conversation.ConversationViewModel
import com.aiko.lingo.ui.theme.AikoLingoTheme
import com.aiko.lingo.ui.translate.TranslateScreen
import com.aiko.lingo.ui.translate.TranslateViewModel
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit

class MainActivity : ComponentActivity() {
    
    private val json = Json { ignoreUnknownKeys = true }
    private val apiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://aiko.ide-chroma.ts.net/")
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(AikoApiService::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var darkTheme by remember { mutableStateOf(false) }
            
            AikoLingoTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AikoLingoApp(apiService, onToggleTheme = { darkTheme = !darkTheme })
                }
            }
        }
    }
}

@Composable
private fun AikoLingoApp(apiService: AikoApiService, onToggleTheme: () -> Unit) {
    val navController = rememberNavController()
    val factory = ViewModelFactory(apiService)

    NavHost(navController = navController, startDestination = "menu") {
        composable("menu") {
            MainMenu(
                onNavigateToTranslate = { navController.navigate("translate") },
                onNavigateToConversation = { navController.navigate("conversation") },
                onToggleTheme = onToggleTheme
            )
        }
        composable("translate") {
            val vm: TranslateViewModel = viewModel(factory = factory)
            TranslateScreen(vm, onBack = { navController.popBackStack() })
        }
        composable("conversation") {
            val vm: ConversationViewModel = viewModel(factory = factory)
            ConversationScreen(vm, onBack = { navController.popBackStack() })
        }
    }
}

@Composable
fun MainMenu(
    onNavigateToTranslate: () -> Unit,
    onNavigateToConversation: () -> Unit,
    onToggleTheme: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(R.drawable.aiko_square),
            contentDescription = "Aiko",
            modifier = Modifier.size(230.dp),
            contentScale = ContentScale.Crop
        )
        Text("Aiko Lingo ♡", style = MaterialTheme.typography.headlineLarge)
        Text("Learn English with Aiko", modifier = Modifier.padding(top = 8.dp, bottom = 32.dp))
        
        Button(onClick = onNavigateToTranslate, modifier = Modifier.fillMaxWidth(0.7f)) {
            Text("✦  Translate")
        }
        Button(onClick = onNavigateToConversation, modifier = Modifier.padding(top = 12.dp).fillMaxWidth(0.7f)) {
            Text("♡  Conversation")
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        TextButton(onClick = onToggleTheme) {
            Text("Switch Theme ✨")
        }
    }
}
