package ir.aminim.smartcooking

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ir.aminim.smartcooking.ui.screens.*
import ir.aminim.smartcooking.ui.theme.SmartCookingTheme
import ir.aminim.smartcooking.ui.theme.ThemeState
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()
    private var incoming by mutableStateOf<Incoming?>(null)

    /** عکس اشتراک‌گذاری‌شده (غذا یا یخچال) یا باز شدن از اعلان */
    data class Incoming(val image: Uri? = null, val open: String? = null)

    fun applyTheme(mode: String) {
        vm.settings.themeMode = mode
        val sysDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        ThemeState.dark = when (mode) { "light" -> false; "system" -> sysDark; else -> true }
        val t = android.graphics.Color.TRANSPARENT
        val style = if (ThemeState.dark) SystemBarStyle.dark(t) else SystemBarStyle.light(t, t)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (vm.settings.themeMode == "system") applyTheme("system")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyTheme(vm.settings.themeMode)
        incoming = intent.incoming()
        // حالت نمایشی برای تصاویر فروشگاه: adb shell am start ... --es demo suggest
        val demo = intent.getStringExtra("demo")
        if (demo != null && BuildConfig.DEBUG) vm.seedDemo()
        if (demo != null && BuildConfig.DEBUG) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            SmartCookingTheme {
                AppRoot(vm, incoming, demo.takeIf { BuildConfig.DEBUG }, ::applyTheme) { incoming = null }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.incoming()?.let { incoming = it }
    }

    @Suppress("DEPRECATION")
    private fun Intent.streamUri(): Uri? =
        if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) else getParcelableExtra<Uri>(Intent.EXTRA_STREAM)

    private fun Intent.incoming(): Incoming? {
        if (action == Intent.ACTION_SEND && type?.startsWith("image/") == true) {
            return streamUri()?.let { Incoming(image = it) }
        }
        return getStringExtra("open")?.let { Incoming(open = it) }
    }
}

object Routes {
    const val SPLASH = "splash"; const val ONBOARD = "onboard"; const val HOME = "home"; const val PANTRY = "pantry"
    const val SUGGEST = "suggest"; const val RECIPE = "recipe"; const val COOK = "cook"; const val CHAT = "chat"
    const val LIBRARY = "library"; const val WHEEL = "wheel"; const val PLANNER = "planner"; const val SHOPPING = "shopping"
    const val FAVORITES = "favorites"; const val TOOLS = "tools"; const val GUIDES = "guides"; const val GUIDE = "guide"
    const val SETTINGS = "settings"; const val ABOUT = "about"
}

@Composable
private fun AppRoot(vm: MainViewModel, incoming: MainActivity.Incoming?, demo: String?, onTheme: (String) -> Unit, onConsumed: () -> Unit) {
    val nav = rememberNavController()
    val drawer = rememberDrawerState(if (demo == "drawer") DrawerValue.Open else DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route

    LaunchedEffect(vm.toast) {
        vm.toast?.let { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show(); vm.toast = null }
    }

    fun NavHostController.go(r: String) {
        scope.launch { drawer.close() }
        if (currentDestination?.route == r) return
        navigate(r) { launchSingleTop = true; if (r == Routes.HOME) popUpTo(Routes.HOME) { inclusive = true } }
    }

    fun openRecipe(r: ir.aminim.smartcooking.data.Recipe) { vm.openRecipe(r); nav.navigate(Routes.RECIPE) }
    fun askRecipe(name: String) { vm.askRecipe(name); nav.navigate(Routes.RECIPE) }

    var pendingGuide by rememberSaveable { mutableStateOf("") }

    fun consume(i: MainActivity.Incoming) {
        i.image?.let { vm.dishPhoto(it); nav.go(Routes.RECIPE) }
        if (i.open == "pantry") nav.go(Routes.PANTRY)
        onConsumed()
    }

    LaunchedEffect(incoming) {
        val i = incoming ?: return@LaunchedEffect
        if (nav.currentDestination?.route == Routes.SPLASH) return@LaunchedEffect
        consume(i)
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = route == Routes.HOME,
        drawerContent = { AppDrawer(vm, current = route, onNavigate = { nav.go(it) }, onTheme = onTheme, onSuggest = { nav.go(Routes.SUGGEST) }) },
    ) {
        NavHost(
            navController = nav,
            startDestination = if (demo != null) Routes.HOME else Routes.SPLASH,
            enterTransition = { slideInHorizontally(tween(360)) { -it / 4 } + fadeIn(tween(280)) },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(280)) },
            popExitTransition = { slideOutHorizontally(tween(300)) { -it / 4 } + fadeOut(tween(240)) },
        ) {
            composable(Routes.SPLASH, enterTransition = { fadeIn() }) {
                SplashScreen {
                    nav.navigate(if (vm.settings.onboarded) Routes.HOME else Routes.ONBOARD) { popUpTo(Routes.SPLASH) { inclusive = true } }
                    incoming?.let { consume(it) }
                }
            }
            composable(Routes.ONBOARD) { OnboardingScreen(vm) { nav.navigate(Routes.HOME) { popUpTo(Routes.ONBOARD) { inclusive = true } } } }
            composable(Routes.HOME) {
                var demoDone by rememberSaveable { mutableStateOf(false) }
                LaunchedEffect(demo) {
                    if (demoDone || demo == null) return@LaunchedEffect
                    demoDone = true
                    when (demo) {
                        "pantry" -> nav.navigate(Routes.PANTRY)
                        "suggest" -> nav.navigate(Routes.SUGGEST)
                        "suggest_light" -> { onTheme("light"); nav.navigate(Routes.SUGGEST) }
                        "home_light" -> onTheme("light")
                        "recipe" -> ir.aminim.smartcooking.knowledge.RecipeDb.byId("ghormeh")?.let { openRecipe(it) }
                        "recipe2" -> ir.aminim.smartcooking.knowledge.RecipeDb.byId("mirza")?.let { openRecipe(it) }
                        "cook" -> { ir.aminim.smartcooking.knowledge.RecipeDb.byId("kotlet")?.let { vm.openRecipe(it) }; nav.navigate(Routes.COOK) }
                        "chat" -> nav.navigate(Routes.CHAT)
                        "library" -> nav.navigate(Routes.LIBRARY)
                        "wheel" -> nav.navigate(Routes.WHEEL)
                        "planner" -> nav.navigate(Routes.PLANNER)
                        "shopping" -> nav.navigate(Routes.SHOPPING)
                        "favorites" -> nav.navigate(Routes.FAVORITES)
                        "tools" -> nav.navigate(Routes.TOOLS)
                        "guides" -> nav.navigate(Routes.GUIDES)
                        "settings" -> nav.navigate(Routes.SETTINGS)
                        "about" -> nav.navigate(Routes.ABOUT)
                        "onboard" -> nav.navigate(Routes.ONBOARD)
                    }
                }
                HomeScreen(
                    vm,
                    onMenu = { scope.launch { drawer.open() } },
                    onTheme = onTheme,
                    onNavigate = { r -> if (r == Routes.CHAT) vm.startChat(null); nav.go(r) },
                    onRecipe = { openRecipe(it) },
                    onDishPhoto = { vm.dishPhoto(it); nav.navigate(Routes.RECIPE) },
                )
            }
            composable(Routes.PANTRY) { PantryScreen(vm, onBack = { nav.popBackStack() }, onSuggest = { nav.navigate(Routes.SUGGEST) }) }
            composable(Routes.SUGGEST) {
                SuggestScreen(vm, onBack = { nav.popBackStack() }, onRecipe = { openRecipe(it) }, onPantry = { nav.navigate(Routes.PANTRY) })
            }
            composable(Routes.RECIPE) {
                RecipeScreen(
                    vm, onBack = { nav.popBackStack() },
                    onCook = { nav.navigate(Routes.COOK) },
                    onChat = { vm.startChat(it); nav.navigate(Routes.CHAT) },
                    onShopping = { nav.navigate(Routes.SHOPPING) },
                )
            }
            composable(Routes.COOK) { CookModeScreen(vm, onBack = { nav.popBackStack() }) }
            composable(Routes.CHAT) { ChatScreen(vm, onBack = { nav.popBackStack() }, onRecipe = { askRecipe(it) }) }
            composable(Routes.LIBRARY) { LibraryScreen(vm, onBack = { nav.popBackStack() }, onRecipe = { openRecipe(it) }, onAsk = { askRecipe(it) }) }
            composable(Routes.WHEEL) { WheelScreen(vm, onBack = { nav.popBackStack() }, onRecipe = { openRecipe(it) }) }
            composable(Routes.PLANNER) {
                PlannerScreen(vm, onBack = { nav.popBackStack() }, onMeal = { m ->
                    val local = ir.aminim.smartcooking.knowledge.RecipeDb.byId(m.note) ?: ir.aminim.smartcooking.knowledge.RecipeDb.matchName(m.name)
                    if (local != null) openRecipe(local) else askRecipe(m.name)
                }, onShopping = { nav.navigate(Routes.SHOPPING) })
            }
            composable(Routes.SHOPPING) { ShoppingScreen(vm, onBack = { nav.popBackStack() }) }
            composable(Routes.FAVORITES) { FavoritesScreen(vm, onBack = { nav.popBackStack() }, onRecipe = { openRecipe(it) }) }
            composable(Routes.TOOLS) { ToolsScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.GUIDES) { GuidesScreen(onBack = { nav.popBackStack() }, onGuide = { pendingGuide = it; nav.navigate(Routes.GUIDE) }) }
            composable(Routes.GUIDE) { GuideScreen(pendingGuide, onBack = { nav.popBackStack() }) }
            composable(Routes.SETTINGS) { SettingsScreen(vm, onBack = { nav.popBackStack() }, onTheme = onTheme, onAbout = { nav.navigate(Routes.ABOUT) }) }
            composable(Routes.ABOUT) { AboutScreen(onBack = { nav.popBackStack() }) }
        }
    }
}
