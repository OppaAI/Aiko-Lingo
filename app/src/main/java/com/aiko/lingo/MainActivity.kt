package com.aiko.lingo

import android.app.Activity
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiko.lingo.data.ServerConfig
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.enableEdgeToEdge
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aiko.lingo.data.remote.AikoApiService
import com.aiko.lingo.data.local.OfflineCache
import com.aiko.lingo.data.StudyReminderWorker
import com.aiko.lingo.data.StudyTracker
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
import com.aiko.lingo.ui.test.LessonTestScreen
import com.aiko.lingo.ui.test.LessonTestViewModel
import com.aiko.lingo.ui.theme.*
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private val notificationPermission = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { /* best-effort: reminders simply stay silent if denied */ }

    private val json = Json { ignoreUnknownKeys = true }

    private fun buildApi(baseUrl: String, connectS: Long, readS: Long, writeS: Long): AikoApiService {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(connectS, TimeUnit.SECONDS)
            .readTimeout(readS, TimeUnit.SECONDS)
            .writeTimeout(writeS, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(AikoApiService::class.java)
    }

    // Server address is user-configurable (menu → Server ⚙️), never hardcoded.
    // Short timeouts for JSON calls so failures show Retry fast instead of
    // spinning; long timeouts only for LLM streaming (conversation).
    private val apiService by lazy { buildApi(ServerConfig.get(this), 20, 25, 25) }
    private val streamingApi by lazy { buildApi(ServerConfig.get(this), 120, 120, 120) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Study reminders: track study days + hourly nudge while idle.
        StudyTracker.init(this)
        OfflineCache.init(this)
        StudyReminderWorker.schedule(this)
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
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

private fun refreshVocab(navController: NavHostController, factory: ViewModelFactory) {
    // Plain ViewModelProvider (not the @Composable helper) so this can run
    // from a click callback. The VM already exists; the factory is only a
    // fallback and is not invoked.
    try {
        val entry = navController.getBackStackEntry("vocab")
        ViewModelProvider(entry, factory)[VocabViewModel::class.java].onTestPassed()
    } catch (e: Exception) {
        android.util.Log.w("Lingo", "Vocab refresh after test failed", e)
    }
}

private fun refreshGrammar(navController: NavHostController, apiService: AikoApiService) {
    try {
        val entry = navController.getBackStackEntry("grammar")
        val grammarFactory = ViewModelFactory(apiService, coursesMode = "grammar")
        ViewModelProvider(entry, grammarFactory)[CoursesViewModel::class.java].onTestPassed()
    } catch (e: Exception) {
        android.util.Log.w("Lingo", "Grammar refresh after test failed", e)
    }
}

@Composable
private fun AikoLingoApp(apiService: AikoApiService, streamingApi: AikoApiService, onToggleTheme: () -> Unit) {
    val navController = rememberNavController()
    // Remember so theme toggles (recomposition) don't rebuild it.
    val factory = remember(apiService, streamingApi) { ViewModelFactory(apiService, streamingApi) }
    val context = LocalContext.current

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
                onToggleTheme = onToggleTheme,
                serverUrl = ServerConfig.get(context),
                onSaveServerUrl = { raw ->
                    ServerConfig.set(context, raw)
                    // Rebuild Retrofit clients against the new address.
                    (context as? Activity)?.recreate()
                }
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
            VocabScreen(
                vm,
                onBack = { navController.popBackStack() },
                onNavigateToTest = { deckId -> navController.navigate("courseTest/$deckId") },
                onNavigateToFinal = { navController.navigate("courseFinalTest") }
            )
        }
        composable("courseTest/{deckId}") { backStackEntry ->
            val deckId = backStackEntry.arguments?.getString("deckId").orEmpty()
            val vm: LessonTestViewModel = viewModel(
                factory = ViewModelFactory(apiService, testTrack = "vocab")
            )
            LaunchedEffect(deckId) { vm.loadLessonTest(deckId) }
            LessonTestScreen(
                vm,
                onBack = { navController.popBackStack() },
                onPassed = {
                    refreshVocab(navController, factory)
                    navController.popBackStack()
                }
            )
        }
        composable("courseFinalTest") {
            val vm: LessonTestViewModel = viewModel(
                factory = ViewModelFactory(apiService, testTrack = "vocab")
            )
            LaunchedEffect(Unit) { vm.loadFinalTest() }
            LessonTestScreen(
                vm,
                onBack = { navController.popBackStack() },
                onPassed = {
                    refreshVocab(navController, factory)
                    navController.popBackStack()
                }
            )
        }
        composable("grammar") {
            val vm: CoursesViewModel = viewModel(factory = ViewModelFactory(apiService, coursesMode = "grammar"))
            CoursesScreen(
                vm,
                title = "Grammar ✏️",
                onBack = { navController.popBackStack() },
                onNavigateToTest = { deckId -> navController.navigate("grammarTest/$deckId") },
                onNavigateToFinal = { navController.navigate("grammarFinalTest") }
            )
        }
        composable("grammarTest/{deckId}") { backStackEntry ->
            val deckId = backStackEntry.arguments?.getString("deckId").orEmpty()
            val vm: LessonTestViewModel = viewModel(
                factory = ViewModelFactory(apiService, testTrack = "grammar")
            )
            LaunchedEffect(deckId) { vm.loadLessonTest(deckId) }
            LessonTestScreen(
                vm,
                onBack = { navController.popBackStack() },
                onPassed = {
                    refreshGrammar(navController, apiService)
                    navController.popBackStack()
                }
            )
        }
        composable("grammarFinalTest") {
            val vm: LessonTestViewModel = viewModel(
                factory = ViewModelFactory(apiService, testTrack = "grammar")
            )
            LaunchedEffect(Unit) { vm.loadFinalTest() }
            LessonTestScreen(
                vm,
                onBack = { navController.popBackStack() },
                onPassed = {
                    refreshGrammar(navController, apiService)
                    navController.popBackStack()
                }
            )
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
    onToggleTheme: () -> Unit,
    serverUrl: String = ServerConfig.DEFAULT_URL,
    onSaveServerUrl: (String) -> Unit = {}
) {
    var showServerDialog by remember { mutableStateOf(false) }
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
        TextButton(onClick = { showServerDialog = true }) {
            Text(
                "🔗 ${ServerConfig.displayHost(serverUrl)} ⚙️",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
        TextButton(onClick = onToggleTheme, modifier = Modifier.padding(bottom = 32.dp)) {
            Text("✨ Switch Aesthetic ✨", fontWeight = FontWeight.Bold)
        }
    }

    if (showServerDialog) {
        ServerUrlDialog(
            currentUrl = serverUrl,
            onDismiss = { showServerDialog = false },
            onSave = {
                showServerDialog = false
                onSaveServerUrl(it)
            }
        )
    }
}

@Composable
fun ServerUrlDialog(
    currentUrl: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember(currentUrl) { mutableStateOf(currentUrl) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Aiko server 🔗", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column {
                Text(
                    "Tailscale hostname or IP of your Aiko-chan (https preferred).",
                    fontSize = 13.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("https://aiko…ts.net/") },
                    singleLine = true,
                    isError = error != null,
                    shape = RoundedCornerShape(12.dp)
                )
                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (ServerConfig.isValid(text)) {
                    onSave(text)
                } else {
                    error = "Enter a valid http(s) address."
                }
            }) { Text("Save & reconnect") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    text = ServerConfig.DEFAULT_URL
                    error = null
                }) { Text("Reset") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@Composable
fun MenuRow(content: @Composable RowScope.() -> Unit) {    Row(
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
