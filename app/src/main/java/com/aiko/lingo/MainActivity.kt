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
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aiko.lingo.data.remote.AikoApiService
import com.aiko.lingo.ui.conversation.ConversationScreen
import com.aiko.lingo.ui.conversation.ConversationViewModel
import com.aiko.lingo.ui.translate.TranslateScreen
import com.aiko.lingo.ui.translate.TranslateViewModel
import com.aiko.lingo.ui.dashboard.DashboardScreen
import com.aiko.lingo.ui.dashboard.DashboardViewModel
import com.aiko.lingo.ui.review.ReviewScreen
import com.aiko.lingo.ui.review.ReviewViewModel
import com.aiko.lingo.ui.leaderboard.LeaderboardScreen
import com.aiko.lingo.ui.leaderboard.LeaderboardViewModel
import com.aiko.lingo.ui.vocab.VocabScreen
import com.aiko.lingo.ui.vocab.VocabViewModel
import com.aiko.lingo.ui.practice.PracticeScreen
import com.aiko.lingo.ui.practice.PracticeViewModel
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

    private fun buildApi(connectS: Long, readS: Long, writeS: Long): AikoApiService {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(connectS, TimeUnit.SECONDS)
            .readTimeout(readS, TimeUnit.SECONDS)
            .writeTimeout(writeS, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl("https://aiko.ide-chroma.ts.net/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(AikoApiService::class.java)
    }

    // Short timeouts for JSON calls so failures show Retry fast instead of
    // spinning; long timeouts only for LLM streaming (conversation).
    private val apiService by lazy { buildApi(20, 25, 25) }
    private val streamingApi by lazy { buildApi(120, 120, 120) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var darkTheme by rememberSaveable { mutableStateOf(false) }
            AikoLingoTheme(darkTheme = darkTheme) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AikoLingoApp(apiService, streamingApi, onToggleTheme = { darkTheme = !darkTheme })
                }
            }
        }
    }
}

@Composable
private fun AikoLingoApp(apiService: AikoApiService, streamingApi: AikoApiService, onToggleTheme: () -> Unit) {
    val navController = rememberNavController()
    val factory = ViewModelFactory(apiService, streamingApi)

    NavHost(navController = navController, startDestination = "menu") {
        composable("menu") {
            MainMenu(
                onNavigateToTranslate = { navController.navigate("translate") },
                onNavigateToConversation = { navController.navigate("conversation") },
                onNavigateToReview = { navController.navigate("review") },
                onNavigateToPractice = { navController.navigate("practice") },
                onNavigateToVocab = { navController.navigate("vocab") },
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
                onNavigateToReview = { navController.navigate("review") }
            )
        }
        composable("review") {
            val vm: ReviewViewModel = viewModel(factory = factory)
            ReviewScreen(
                vm,
                onBack = { navController.popBackStack() },
                onNavigateToLearn = { navController.navigate("vocab") },
                onNavigateToPractice = { navController.navigate("practice") }
            )
        }
        composable("practice") {
            val vm: PracticeViewModel = viewModel(factory = factory)
            PracticeScreen(vm, onBack = { navController.popBackStack() })
        }
        composable("vocab") {
            val vm: VocabViewModel = viewModel(factory = factory)
            VocabScreen(vm, onBack = { navController.popBackStack() })
        }
        composable("grammar") {
            val vm: CoursesViewModel = viewModel(factory = ViewModelFactory(apiService, coursesMode = "grammar"))
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
    onNavigateToPractice: () -> Unit,
    onNavigateToVocab: () -> Unit,
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
            CuteMenuCard(text = "Vocab", icon = "🌱", color = PastelGreen, modifier = Modifier.weight(1f), onClick = onNavigateToVocab)
            CuteMenuCard(text = "Grammar", icon = "✏️", color = PastelYellow, modifier = Modifier.weight(1f), onClick = onNavigateToGrammar)
        }
        Spacer(modifier = Modifier.height(16.dp))
        MenuRow {
            CuteMenuCard(text = "Review", icon = "🔁", color = MenuPracticeBlue, modifier = Modifier.weight(1f), onClick = onNavigateToReview)
            CuteMenuCard(text = "Practice", icon = "✍️", color = MenuPracticeCoral, modifier = Modifier.weight(1f), onClick = onNavigateToPractice)
        }
        Spacer(modifier = Modifier.height(16.dp))
        MenuRow {
            CuteMenuCard(text = "Chat", icon = "💬", color = ShoujoPink, modifier = Modifier.weight(1f), onClick = onNavigateToConversation)
            CuteMenuCard(text = "Translate", icon = "🔤", color = PastelOrange, modifier = Modifier.weight(1f), onClick = onNavigateToTranslate)
        }
        Spacer(modifier = Modifier.height(16.dp))
        MenuRow {
            CuteMenuCard(text = "Stats", icon = "📊", color = MenuStatsTeal, modifier = Modifier.weight(1f), onClick = onNavigateToDashboard)
            CuteMenuCard(text = "Ranks", icon = "🏆", color = MenuRanksGold, modifier = Modifier.weight(1f), onClick = onNavigateToLeaderboard)
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
