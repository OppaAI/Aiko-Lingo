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
import androidx.activity.enableEdgeToEdge
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aiko.lingo.data.remote.AikoApiService
import com.aiko.lingo.ui.conversation.ConversationScreen
import com.aiko.lingo.ui.conversation.ConversationViewModel
import com.aiko.lingo.ui.translate.TranslateScreen
import com.aiko.lingo.ui.translate.TranslateViewModel
import com.aiko.lingo.ui.dashboard.DashboardScreen
import com.aiko.lingo.ui.dashboard.DashboardViewModel
import com.aiko.lingo.ui.review.ReviewScreen
import com.aiko.lingo.ui.review.ReviewViewModel
import com.aiko.lingo.ui.review.ReviewMode
import com.aiko.lingo.ui.leaderboard.LeaderboardScreen
import com.aiko.lingo.ui.leaderboard.LeaderboardViewModel
import com.aiko.lingo.ui.theme.AikoLingoTheme
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    
    private val json = Json { ignoreUnknownKeys = true }
    private val apiService by lazy {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl("https://aiko.ide-chroma.ts.net/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(AikoApiService::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
                onNavigateToReview = { navController.navigate("review/SRS") },
                onNavigateToDashboard = { navController.navigate("dashboard") },
                onNavigateToLeaderboard = { navController.navigate("leaderboard") },
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
        composable("dashboard") {
            val vm: DashboardViewModel = viewModel(factory = factory)
            DashboardScreen(
                vm,
                onBack = { navController.popBackStack() },
                onNavigateToReview = { mode -> 
                    navController.navigate("review/$mode")
                }
            )
        }
        composable(
            route = "review/{mode}",
            arguments = listOf(navArgument("mode") { type = NavType.StringType })
        ) { backStackEntry ->
            val modeStr = backStackEntry.arguments?.getString("mode") ?: "SRS"
            val mode = if (modeStr == "PRACTICE") ReviewMode.PRACTICE else ReviewMode.SRS
            val vm: ReviewViewModel = viewModel(factory = factory)
            
            // Re-initialize VM with the correct mode if it's new
            LaunchedEffect(mode) {
                vm.setMode(mode)
            }
            
            ReviewScreen(vm, onBack = { navController.popBackStack() })
        }
        composable("leaderboard") {
            val vm: LeaderboardViewModel = viewModel(factory = factory)
            LeaderboardScreen(vm, onBack = { navController.popBackStack() })
        }
    }
}

@Composable
fun MainMenu(
    onNavigateToTranslate: () -> Unit,
    onNavigateToConversation: () -> Unit,
    onNavigateToReview: () -> Unit,
    onNavigateToDashboard: () -> Unit,
    onNavigateToLeaderboard: () -> Unit,
    onToggleTheme: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
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
        Text("Learn Nihongo with Aiko", modifier = Modifier.padding(top = 8.dp, bottom = 32.dp))
        
        Button(onClick = onNavigateToTranslate, modifier = Modifier.fillMaxWidth(0.7f)) {
            Text("✦  Translate")
        }
        Button(onClick = onNavigateToConversation, modifier = Modifier.padding(top = 12.dp).fillMaxWidth(0.7f)) {
            Text("♡  Conversation")
        }
        Button(onClick = onNavigateToReview, modifier = Modifier.padding(top = 12.dp).fillMaxWidth(0.7f)) {
            Text("📚  Practice")
        }
        Button(onClick = onNavigateToDashboard, modifier = Modifier.padding(top = 12.dp).fillMaxWidth(0.7f)) {
            Text("📊  Dashboard")
        }
        Button(onClick = onNavigateToLeaderboard, modifier = Modifier.padding(top = 12.dp).fillMaxWidth(0.7f)) {
            Text("🏆  Leaderboard")
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        TextButton(onClick = onToggleTheme) {
            Text("Switch Theme ✨")
        }
    }
}
