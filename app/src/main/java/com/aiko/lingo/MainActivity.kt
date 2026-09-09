package com.aiko.lingo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.aiko.lingo.ui.learn.LearnScreen
import com.aiko.lingo.ui.learn.LearnViewModel
import com.aiko.lingo.ui.courses.CoursesScreen
import com.aiko.lingo.ui.courses.CoursesViewModel
import com.aiko.lingo.ui.theme.*
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
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
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
                onNavigateToLearn = { navController.navigate("learn") },
                onNavigateToCourses = { navController.navigate("courses") },
                onNavigateToGrammar = { navController.navigate("grammar") },
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
                onNavigateToReview = { mode -> navController.navigate("review/$mode") }
            )
        }
        composable(
            route = "review/{mode}",
            arguments = listOf(navArgument("mode") { type = NavType.StringType })
        ) { backStackEntry ->
            val modeStr = backStackEntry.arguments?.getString("mode") ?: "SRS"
            val mode = when (modeStr) {
                "PRACTICE" -> ReviewMode.PRACTICE
                else -> ReviewMode.SRS
            }
            val vm: ReviewViewModel = viewModel(factory = factory)
            LaunchedEffect(mode) { vm.setMode(mode) }
            ReviewScreen(
                vm,
                onBack = { navController.popBackStack() },
                onNavigateToLearn = { navController.navigate("learn") },
                onNavigateToPractice = {
                    if (modeStr != "PRACTICE") navController.navigate("review/PRACTICE")
                }
            )
        }
        composable("learn") {
            val vm: LearnViewModel = viewModel(factory = factory)
            LearnScreen(vm, onBack = { navController.popBackStack() })
        }
        composable("courses") {
            val vm: CoursesViewModel = viewModel(factory = ViewModelFactory(apiService, "courses"))
            CoursesScreen(vm, title = "Courses 📖", onBack = { navController.popBackStack() })
        }
        composable("grammar") {
            val vm: CoursesViewModel = viewModel(factory = ViewModelFactory(apiService, "grammar"))
            CoursesScreen(vm, title = "Grammar ✏️", onBack = { navController.popBackStack() })
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
    onNavigateToLearn: () -> Unit,
    onNavigateToCourses: () -> Unit,
    onNavigateToGrammar: () -> Unit,
    onNavigateToDashboard: () -> Unit,
    onNavigateToLeaderboard: () -> Unit,
    onToggleTheme: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        Card(
            shape = RoundedCornerShape(40.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
            modifier = Modifier.padding(bottom = 24.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.aiko_square),
                contentDescription = "Aiko",
                modifier = Modifier.size(120.dp),
                contentScale = ContentScale.Crop
            )
        }

        Text(
            "Aiko Lingo ♡",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "Nihongo · JLPT track",
            modifier = Modifier.padding(top = 4.dp, bottom = 32.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = Color.Gray
        )

        MenuRow {
            CuteMenuCard(text = "Learn", icon = "🌱", color = PastelGreen, modifier = Modifier.weight(1f), onClick = onNavigateToLearn)
            CuteMenuCard(text = "Practice", icon = "🎯", color = PastelBlue, modifier = Modifier.weight(1f), onClick = onNavigateToReview)
        }
        Spacer(modifier = Modifier.height(16.dp))
        MenuRow {
            CuteMenuCard(text = "Courses", icon = "📖", color = PastelPurple, modifier = Modifier.weight(1f), onClick = onNavigateToCourses)
            CuteMenuCard(text = "Grammar", icon = "✏️", color = PastelYellow, modifier = Modifier.weight(1f), onClick = onNavigateToGrammar)
        }
        Spacer(modifier = Modifier.height(16.dp))
        MenuRow {
            CuteMenuCard(text = "Chat", icon = "💬", color = ShoujoPink, modifier = Modifier.weight(1f), onClick = onNavigateToConversation)
            CuteMenuCard(text = "Translate", icon = "🔤", color = PastelOrange, modifier = Modifier.weight(1f), onClick = onNavigateToTranslate)
        }
        Spacer(modifier = Modifier.height(16.dp))
        MenuRow {
            CuteMenuCard(text = "Stats", icon = "📊", color = PastelPurple, modifier = Modifier.weight(1f), onClick = onNavigateToDashboard)
            CuteMenuCard(text = "Ranks", icon = "🏆", color = PastelYellow, modifier = Modifier.weight(1f), onClick = onNavigateToLeaderboard)
        }

        Spacer(modifier = Modifier.height(48.dp))
        TextButton(onClick = onToggleTheme, modifier = Modifier.padding(bottom = 32.dp)) {
            Text("✨ Switch Aesthetic ✨", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun MenuRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        content = content
    )
}

@Composable
fun CuteMenuCard(
    text: String,
    icon: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(92.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = color),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(icon, fontSize = 26.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = ShoujoText)
        }
    }
}
