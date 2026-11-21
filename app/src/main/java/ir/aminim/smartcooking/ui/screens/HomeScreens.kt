package ir.aminim.smartcooking.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import ir.aminim.smartcooking.BuildConfig
import ir.aminim.smartcooking.MainViewModel
import ir.aminim.smartcooking.R
import ir.aminim.smartcooking.Routes
import ir.aminim.smartcooking.data.Images
import ir.aminim.smartcooking.data.Meals
import ir.aminim.smartcooking.data.Recipe
import ir.aminim.smartcooking.engine.Matcher
import ir.aminim.smartcooking.engine.Prefs
import ir.aminim.smartcooking.knowledge.Guides
import ir.aminim.smartcooking.settings.AiMode
import ir.aminim.smartcooking.ui.components.*
import ir.aminim.smartcooking.ui.theme.Palette
import ir.aminim.smartcooking.ui.theme.ThemeState
import ir.aminim.smartcooking.util.Jalali
import ir.aminim.smartcooking.util.fa
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

/** دوربین و گالری؛ خروجی: (گرفتن عکس، انتخاب از گالری) */
@Composable
fun rememberPhotoPickers(onPicked: (Uri) -> Unit): Pair<() -> Unit, () -> Unit> {
    val ctx = LocalContext.current
    var pending by remember { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) pending?.let(onPicked) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(onPicked) }
    val takePhoto: () -> Unit = {
        runCatching {
            val f = Images.newCameraFile(ctx)
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
            pending = uri
            camera.launch(uri)
        }.onFailure { android.widget.Toast.makeText(ctx, "دوربین در دسترس نیست؛ از گالری انتخاب کنید", android.widget.Toast.LENGTH_LONG).show() }
    }
    val pick: () -> Unit = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    return takePhoto to pick
}

/* ═════════════════════════  اسپلش  ═════════════════════════ */
@Composable
fun SplashScreen(onDone: () -> Unit) {
    val scale = remember { Animatable(0.55f) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        alpha.animateTo(1f, tween(450))
        scale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 220f))
        delay(900)
        onDone()
    }
    val glow = rememberInfiniteTransition(label = "g").animateFloat(0.35f, 0.8f, infiniteRepeatable(tween(1300), RepeatMode.Reverse), label = "ga")
    MeshBackground {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.scale(scale.value).alpha(alpha.value)) {
                Box(Modifier.size(220.dp).drawBehind { drawCircle(Brush.radialGradient(listOf(Palette.Spice.copy(alpha = glow.value * 0.5f), Color.Transparent))) })
                AppLogo(size = 136.dp)
            }
            Spacer(Modifier.height(26.dp))
            GradientText("آشپزی هوشمند", MaterialTheme.typography.displaySmall, modifier = Modifier.alpha(alpha.value))
            Spacer(Modifier.height(8.dp))
            Text("بگو چی داری، بگم چی بپزی", style = MaterialTheme.typography.titleSmall, color = Palette.TextMuted, modifier = Modifier.alpha(alpha.value))
        }
        Row(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(24.dp).alpha(alpha.value), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(if (ThemeState.dark) R.drawable.logo_amin_gold else R.drawable.logo_amin_navy), null, Modifier.size(28.dp))
            Spacer(Modifier.width(8.dp))
            Text("گروه پژوهشی مدیریت هوشمند امین", style = MaterialTheme.typography.labelMedium, color = Palette.TextMuted)
        }
    }
}

/** لوگوی برنامه */
@Composable
fun AppLogo(size: Dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.28f))
            .background(Brush.linearGradient(listOf(Color(0xFFC65A26), Color(0xFF8E2F45), Color(0xFF0A1A3A))))
            .border(1.dp, Palette.Gold.copy(alpha = 0.35f), RoundedCornerShape(size * 0.28f)),
        contentAlignment = Alignment.Center,
    ) { Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.fillMaxSize().scale(1.45f)) }
}

/* ═════════════════════════  معرفی اولیه  ═════════════════════════ */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(vm: MainViewModel, onDone: () -> Unit) {
    val pages = listOf(
        Triple(Icons.Rounded.Kitchen, "یخچالت رو بگو", "با صدا، متن یا یک عکس از یخچال بگو چه موادی داری؛ آشپزی هوشمند همه را می‌شناسد و تاریخ انقضا را هم یادت می‌آورد."),
        Triple(Icons.Rounded.AutoAwesome, "غذای مناسب، همین الان", "هوش مصنوعی با همان مواد، غذاهای متنوع ایرانی و فرنگی پیشنهاد می‌دهد؛ با درصد تطابق و موادی که کم داری."),
        Triple(Icons.Rounded.SoupKitchen, "قدم‌به‌قدم بپز", "حالت آشپزی با تایمر خودکار، خواندن مراحل با صدا، گردونه غذا، برنامه هفتگی و لیست خرید هوشمند."),
    )
    val pager = rememberPagerState { pages.size + 1 }
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(vm.settings.userName) }
    var family by remember { mutableIntStateOf(vm.settings.family) }
    var avoid by remember { mutableStateOf(vm.settings.avoid) }
    var diets by remember { mutableStateOf(vm.settings.diets) }
    fun finish() {
        vm.settings.userName = name; vm.settings.family = family; vm.settings.avoid = avoid; vm.settings.diets = diets; vm.settings.onboarded = true
        vm.prefs = vm.prefs.copy(people = family, diet = diets)
        onDone()
    }
    MeshBackground {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End) {
                TextButton({ finish() }) { Text("رد شدن", color = Palette.TextMuted) }
            }
            HorizontalPager(pager, Modifier.weight(1f)) { i ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    if (i < pages.size) {
                        val (icon, t, d) = pages[i]
                        IconBadge(icon, listOf(Palette.SpiceBrush, Palette.CoralBrush, Palette.GoldBrush)[i], size = 120.dp, glow = Palette.Spice, tint = if (i == 2) Palette.OnGold else Color.White)
                        Spacer(Modifier.height(32.dp))
                        GradientText(t, MaterialTheme.typography.headlineMedium, align = TextAlign.Center)
                        Spacer(Modifier.height(14.dp))
                        Text(d, style = MaterialTheme.typography.bodyLarge, color = Palette.TextMuted, textAlign = TextAlign.Center)
                    } else {
                        AppLogo(84.dp)
                        Spacer(Modifier.height(14.dp))
                        GradientText("کمی از خودت بگو", MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(16.dp))
                        AppTextField(name, { name = it }, "نام شما (اختیاری)", icon = Icons.Rounded.Person)
                        Spacer(Modifier.height(14.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Groups, null, tint = Palette.Spice)
                            Spacer(Modifier.width(10.dp))
                            Text("معمولاً برای چند نفر می‌پزید؟", style = MaterialTheme.typography.titleSmall, color = Palette.Text, modifier = Modifier.weight(1f))
                            Stepper(family, { family = it.coerceIn(1, 12) })
                        }
                        Spacer(Modifier.height(14.dp))
                        Text("ترجیحات غذایی", style = MaterialTheme.typography.labelLarge, color = Palette.Text, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Matcher.dietOptions.forEach { d -> SelectChip(d, d in diets, { diets = if (d in diets) diets - d else diets + d }) }
                        }
                        Spacer(Modifier.height(14.dp))
                        AppTextField(avoid, { avoid = it }, "حساسیت غذایی یا چیزی که نمی‌خورید", placeholder = "مثلاً بادمجان، گردو، لاکتوز", icon = Icons.Rounded.NoFood)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f)) {
                    repeat(pages.size + 1) { i ->
                        val w by animateDpAsState(if (pager.currentPage == i) 26.dp else 8.dp, label = "d")
                        Box(Modifier.padding(end = 6.dp).height(8.dp).width(w).clip(CircleShape).background(if (pager.currentPage == i) Palette.Spice else Palette.GlassStrong))
                    }
                }
                GoldButton(if (pager.currentPage == pages.size) "بزن بریم!" else "بعدی", {
                    if (pager.currentPage == pages.size) finish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                }, brush = Palette.SpiceBrush, contentColor = Palette.OnSpice)
            }
        }
    }
}

/** شمارنده + و − */
@Composable
fun Stepper(value: Int, onChange: (Int) -> Unit, suffix: String = "نفر") {
    Row(
        Modifier.clip(RoundedCornerShape(16.dp)).background(Palette.Glass).border(1.dp, Palette.GlassBorder, RoundedCornerShape(16.dp)).padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(Palette.SpiceTint).clickable { onChange(value + 1) }, contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Add, "بیشتر", tint = Palette.Spice, modifier = Modifier.size(20.dp))
        }
        Text("${value.fa()} $suffix", style = MaterialTheme.typography.labelLarge, color = Palette.Text, modifier = Modifier.padding(horizontal = 10.dp))
        Box(Modifier.size(34.dp).clip(CircleShape).background(Palette.GlassStrong).clickable { onChange(value - 1) }, contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Remove, "کمتر", tint = Palette.TextMuted, modifier = Modifier.size(20.dp))
        }
    }
}

/* ═════════════════════════  منوی کناری  ═════════════════════════ */
@Composable
fun AppDrawer(vm: MainViewModel, current: String?, onNavigate: (String) -> Unit, onTheme: (String) -> Unit, onSuggest: () -> Unit) {
    val ctx = LocalContext.current
    ModalDrawerSheet(drawerContainerColor = Palette.Drawer, drawerShape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp), modifier = Modifier.width(310.dp)) {
        Column(Modifier.fillMaxHeight().verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().background(Palette.HeroBrush).statusBarsPadding().padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppLogo(54.dp); Spacer(Modifier.width(12.dp))
                    Column {
                        Text("آشپزی هوشمند", style = MaterialTheme.typography.titleLarge, color = Color.White)
                        Text(
                            vm.settings.userName.ifBlank { "نسخه ${BuildConfig.VERSION_NAME.fa()}" } + if (vm.settings.cookedCount > 0) " • ${vm.settings.cookedCount.fa()} غذا پخته" else "",
                            style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f),
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.padding(horizontal = 16.dp)) { GoldButton("چی بپزم؟", onSuggest, Modifier.fillMaxWidth(), icon = Icons.Rounded.AutoAwesome) }
            Spacer(Modifier.height(10.dp))
            val items = listOf(
                Triple(Routes.HOME, Icons.Rounded.Home, "خانه"),
                Triple(Routes.PANTRY, Icons.Rounded.Kitchen, "یخچال من"),
                Triple(Routes.SUGGEST, Icons.Rounded.AutoAwesome, "پیشنهاد غذا"),
                Triple(Routes.CHAT, Icons.Rounded.Forum, "سرآشپز هوشمند"),
                Triple(Routes.LIBRARY, Icons.Rounded.MenuBook, "کتاب آشپزی"),
                Triple(Routes.WHEEL, Icons.Rounded.Casino, "گردونه غذا"),
                Triple(Routes.PLANNER, Icons.Rounded.CalendarMonth, "برنامه غذایی هفتگی"),
                Triple(Routes.SHOPPING, Icons.Rounded.ShoppingCart, "لیست خرید"),
                Triple(Routes.FAVORITES, Icons.Rounded.Favorite, "علاقه‌مندی‌ها و سابقه"),
                Triple(Routes.TOOLS, Icons.Rounded.Calculate, "پیمانه و جایگزین مواد"),
                Triple(Routes.GUIDES, Icons.Rounded.School, "آموزش آشپزی"),
                Triple(Routes.SETTINGS, Icons.Rounded.Tune, "تنظیمات و هوش مصنوعی"),
                Triple(Routes.ABOUT, Icons.Rounded.Info, "درباره ما"),
            )
            items.forEach { (r, icon, label) ->
                val sel = current == r
                val badge = when (r) {
                    Routes.PANTRY -> vm.pantry.size.takeIf { it > 0 }?.fa()
                    Routes.SHOPPING -> vm.shopLeft.takeIf { it > 0 }?.fa()
                    Routes.FAVORITES -> vm.favorites.size.takeIf { it > 0 }?.fa()
                    else -> null
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp).clip(RoundedCornerShape(16.dp))
                        .background(if (sel) Palette.SpiceTint else Color.Transparent).clickable { onNavigate(r) }.padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(icon, null, tint = if (sel) Palette.Spice else Palette.TextMuted, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(14.dp))
                    Text(label, style = MaterialTheme.typography.titleSmall, color = if (sel) Palette.Spice else Palette.Text, modifier = Modifier.weight(1f), fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium)
                    badge?.let { Pill(it, color = Palette.Teal) }
                }
            }
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = Palette.GlassBorder, modifier = Modifier.padding(horizontal = 20.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (ThemeState.dark) Icons.Rounded.DarkMode else Icons.Rounded.LightMode, null, tint = Palette.Gold)
                Spacer(Modifier.width(14.dp))
                Text(if (ThemeState.dark) "حالت تیره" else "حالت روشن", style = MaterialTheme.typography.titleSmall, color = Palette.Text, modifier = Modifier.weight(1f))
                Switch(ThemeState.dark, { onTheme(if (it) "dark" else "light") }, colors = SwitchDefaults.colors(checkedThumbColor = Palette.OnGold, checkedTrackColor = Palette.Gold))
            }
            Row(Modifier.fillMaxWidth().clickable { ShareUtil.openUrl(ctx, "bazaar://details?id=ir.aminim.smartcooking") }.padding(horizontal = 26.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.StarRate, null, tint = Palette.Gold); Spacer(Modifier.width(14.dp))
                Text("امتیاز در کافه‌بازار", style = MaterialTheme.typography.titleSmall, color = Palette.Text)
            }
            Row(Modifier.fillMaxWidth().clickable { ShareUtil.shareApp(ctx) }.padding(horizontal = 26.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Share, null, tint = Palette.Teal); Spacer(Modifier.width(14.dp))
                Text("معرفی به دوستان", style = MaterialTheme.typography.titleSmall, color = Palette.Text)
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(if (ThemeState.dark) R.drawable.logo_amin_gold else R.drawable.logo_amin_navy), null, Modifier.size(30.dp))
                Spacer(Modifier.width(10.dp))
                Text("گروه پژوهشی مدیریت هوشمند امین", style = MaterialTheme.typography.labelSmall, color = Palette.TextMuted)
            }
        }
    }
}

/* ═════════════════════════  خانه  ═════════════════════════ */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    vm: MainViewModel,
    onMenu: () -> Unit,
    onTheme: (String) -> Unit,
    onNavigate: (String) -> Unit,
    onRecipe: (Recipe) -> Unit,
    onDishPhoto: (Uri) -> Unit,
) {
    val (dishCam, dishGallery) = rememberPhotoPickers(onDishPhoto)
    var dishMenu by remember { mutableStateOf(false) }
    val now = remember { System.currentTimeMillis() }
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val meal = remember { Meals.forHour(hour) }
    val month = remember { Jalali.of(now).month }
    val (season, seasonTips) = remember(month) { Guides.season(month) }
    val today = remember(meal) { Matcher.ofTheDay(if (meal == "snack") "dinner" else meal, now) }
    val greet = when (hour) { in 5..11 -> "صبح بخیر"; in 12..16 -> "ظهر بخیر"; in 17..19 -> "عصر بخیر"; else -> "شب بخیر" }
    val question = when (meal) { "breakfast" -> "صبحانه چی بخوریم؟"; "lunch" -> "ناهار امروز چی بپزیم؟"; "snack" -> "عصرانه یا شام چی داریم؟"; else -> "شام امشب چی بپزیم؟" }
    MeshBackground {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding()
                .padding(horizontal = 20.dp).padding(bottom = 28.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                CircleIconButton(Icons.Rounded.Menu, onMenu, size = 42.dp)
                Spacer(Modifier.width(10.dp))
                AppLogo(40.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("آشپزی هوشمند", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                    Text(Jalali.long(now), style = MaterialTheme.typography.labelSmall, color = Palette.TextMuted)
                }
                if (vm.shopLeft > 0) {
                    BadgedBox(badge = { Badge(containerColor = Palette.Spice, contentColor = Palette.OnSpice) { Text(vm.shopLeft.fa()) } }) {
                        CircleIconButton(Icons.Rounded.ShoppingCart, { onNavigate(Routes.SHOPPING) }, size = 42.dp, tint = Palette.Teal)
                    }
                    Spacer(Modifier.width(8.dp))
                }
                val rot by animateFloatAsState(if (ThemeState.dark) 0f else 180f, tween(500), label = "r")
                CircleIconButton(if (ThemeState.dark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode, { onTheme(if (ThemeState.dark) "light" else "dark") },
                    size = 42.dp, tint = Palette.Gold, modifier = Modifier.rotate(rot))
            }
            Spacer(Modifier.height(20.dp))
            Text((if (vm.settings.userName.isNotBlank()) "${vm.settings.userName} عزیز، " else "") + greet + " 👋", style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
            GradientText(question, MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))

            HeroFridgeCard(vm, onNavigate)

            vm.expiring.takeIf { it.isNotEmpty() }?.let { exp ->
                Spacer(Modifier.height(14.dp))
                GlassCard(Modifier.fillMaxWidth(), onClick = { vm.prefs = vm.prefs.copy(note = "اول از «${exp.take(3).joinToString("، ") { it.name }}» استفاده شود"); onNavigate(Routes.SUGGEST) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.HourglassBottom, Palette.CoralBrush, size = 44.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${exp.size.fa()} ماده رو به خراب شدن است", style = MaterialTheme.typography.titleSmall, color = Palette.Text)
                            Text(exp.joinToString("، ") { it.name } + " • بزنید تا با همین‌ها غذا پیشنهاد بدهیم", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Icon(Icons.Rounded.ChevronLeft, null, tint = Palette.TextMuted)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionLabel("پیشنهاد امروز سرآشپز")
            Spacer(Modifier.height(8.dp))
            TodayCard(today) { onRecipe(today) }

            Spacer(Modifier.height(20.dp))
            SectionLabel("ابزارها")
            Spacer(Modifier.height(8.dp))
            val tools = listOf(
                Tool(Icons.Rounded.Forum, "سرآشپز هوشمند", "هر سؤالی داری بپرس", Palette.TealBrush, Routes.CHAT),
                Tool(Icons.Rounded.PhotoCamera, "عکس غذا ← دستور", "غذا را بشناس و بپز", Palette.PinkBrush, "dish"),
                Tool(Icons.Rounded.Casino, "گردونه غذا", "نمی‌دانی چی بپزی؟ بچرخون!", Palette.VioletBrush, Routes.WHEEL),
                Tool(Icons.Rounded.CalendarMonth, "برنامه هفتگی", "منوی ۷ روزه + خرید", Palette.SkyBrush, Routes.PLANNER),
                Tool(Icons.Rounded.MenuBook, "کتاب آشپزی", "${ir.aminim.smartcooking.knowledge.RecipeDb.all.size.fa()} دستور آفلاین", Palette.SpiceBrush, Routes.LIBRARY),
                Tool(Icons.Rounded.ShoppingCart, "لیست خرید", if (vm.shopLeft > 0) "${vm.shopLeft.fa()} قلم مانده" else "خرید هوشمند", Palette.MintBrush, Routes.SHOPPING),
                Tool(Icons.Rounded.Calculate, "پیمانه و جایگزین", "تبدیل واحد و جایگزین مواد", Palette.OrangeBrush, Routes.TOOLS),
                Tool(Icons.Rounded.School, "آموزش آشپزی", "کته، ته‌دیگ، پیاز داغ…", Palette.GoldBrush, Routes.GUIDES),
            )
            tools.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { t ->
                        Box(Modifier.weight(1f)) {
                            ToolCard(t, Modifier.fillMaxWidth()) { if (t.route == "dish") dishMenu = true else onNavigate(t.route) }
                            if (t.route == "dish") DropdownMenu(dishMenu, { dishMenu = false }, containerColor = Palette.Surface) {
                                DropdownMenuItem({ Text("عکس گرفتن از غذا") }, { dishMenu = false; dishCam() }, leadingIcon = { Icon(Icons.Rounded.CameraAlt, null) })
                                DropdownMenuItem({ Text("انتخاب از گالری") }, { dishMenu = false; dishGallery() }, leadingIcon = { Icon(Icons.Rounded.PhotoLibrary, null) })
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.height(8.dp))
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Rounded.Eco, Palette.HerbBrush, size = 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("در فصل $season تازه‌اند", style = MaterialTheme.typography.labelMedium, color = Palette.TextMuted)
                        Text(seasonTips[0], style = MaterialTheme.typography.titleSmall, color = Palette.Text)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(seasonTips[1], style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
            }

            if (vm.favorites.isNotEmpty() || vm.history.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                SectionLabel(if (vm.favorites.isNotEmpty()) "غذاهای محبوب شما" else "اخیراً دیده‌اید", trailing = { TextButton({ onNavigate(Routes.FAVORITES) }) { Text("همه", color = Palette.Gold) } })
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    (vm.favorites + vm.history).distinctBy { it.name }.take(10).forEach { r -> MiniRecipeCard(r) { onRecipe(r) } }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroFridgeCard(vm: MainViewModel, onNavigate: (String) -> Unit) {
    val glow = rememberInfiniteTransition(label = "h").animateFloat(0f, 1f, infiniteRepeatable(tween(2600), RepeatMode.Reverse), label = "hg")
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Palette.HeroBrush)
            .drawBehind {
                drawCircle(Color.White.copy(alpha = 0.07f + glow.value * 0.05f), radius = size.width * 0.45f, center = androidx.compose.ui.geometry.Offset(size.width * 0.05f, size.height * 0.1f))
                drawCircle(Color(0x33EDB850), radius = size.width * 0.3f, center = androidx.compose.ui.geometry.Offset(size.width * 0.95f, size.height))
            }
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("یخچال من", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.85f))
                Text(if (vm.pantry.isEmpty()) "چه موادی در خانه داری؟" else "${vm.pantry.size.fa()} ماده در آشپزخانه", style = MaterialTheme.typography.titleLarge, color = Color.White)
            }
            Box(Modifier.size(58.dp).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.16f)).clickable { onNavigate(Routes.PANTRY) }, contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Kitchen, null, tint = Palette.GoldLight, modifier = Modifier.size(32.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        if (vm.pantry.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                vm.pantry.take(10).forEach { p ->
                    Text("${p.emoji} ${p.name}", color = Color.White, style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 5.dp))
                }
                if (vm.pantry.size > 10) Text("+${(vm.pantry.size - 10).fa()}", color = Palette.GoldLight, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(6.dp))
            }
            Spacer(Modifier.height(14.dp))
        } else {
            Text("مواد یخچال و کابینت را با صدا، متن یا عکس بگو تا بهترین غذا را با همان‌ها پیشنهاد بدهم.", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
            Spacer(Modifier.height(14.dp))
        }
        GoldButton(if (vm.pantry.isEmpty()) "ثبت مواد یخچال" else "چی بپزم؟ پیشنهاد بده", { onNavigate(if (vm.pantry.isEmpty()) Routes.PANTRY else Routes.SUGGEST) }, Modifier.fillMaxWidth(),
            icon = if (vm.pantry.isEmpty()) Icons.Rounded.Kitchen else Icons.Rounded.AutoAwesome)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HeroSmall(Icons.Rounded.Mic, if (vm.pantry.isEmpty()) "بگو یا بنویس" else "ویرایش یخچال", Modifier.weight(1f)) { onNavigate(Routes.PANTRY) }
            HeroSmall(Icons.Rounded.Casino, "گردونه شانس", Modifier.weight(1f)) { onNavigate(Routes.WHEEL) }
        }
        Spacer(Modifier.height(12.dp))
        val mode = vm.settings.aiMode
        val engine = when {
            mode == AiMode.INTERNAL && vm.settings.internalLeft > 0 -> "موتور: هوش مصنوعی قوی داخلی • ${vm.settings.internalLeft.fa()} پیشنهاد امروز باقی‌مانده"
            mode == AiMode.INTERNAL -> "سهمیه امروز داخلی تمام شد؛ ادامه با هوش مصنوعی قوی خارجی"
            else -> "موتور: ${mode.title}"
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = Palette.GoldLight, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(engine, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f))
        }
    }
}

@Composable
private fun HeroSmall(icon: ImageVector, text: String, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.14f)).border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
        Text(text, color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun TodayCard(r: Recipe, onClick: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), padding = 0.dp, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 110.dp, height = 120.dp).background(Palette.SpiceBrush), contentAlignment = Alignment.Center) {
                Text(r.emoji, fontSize = 54.sp)
            }
            Column(Modifier.weight(1f).padding(14.dp)) {
                Text(r.name, style = MaterialTheme.typography.titleMedium, color = Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(r.desc, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MiniStat(Icons.Rounded.Schedule, "${r.minutes.fa()} دقیقه")
                    MiniStat(Icons.Rounded.LocalFireDepartment, "${r.calories.fa()} کالری")
                }
            }
        }
    }
}

@Composable
fun MiniStat(icon: ImageVector, text: String, tint: Color = Palette.Spice) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = Palette.TextMuted)
    }
}

private data class Tool(val icon: ImageVector, val title: String, val sub: String, val brush: Brush, val route: String)

@Composable
private fun ToolCard(t: Tool, modifier: Modifier, onClick: () -> Unit) {
    GlassCard(modifier.height(118.dp), padding = 14.dp, onClick = onClick) {
        IconBadge(t.icon, t.brush, size = 42.dp, tint = if (t.brush == Palette.GoldBrush) Palette.OnGold else Color.White)
        Spacer(Modifier.weight(1f))
        Text(t.title, style = MaterialTheme.typography.titleSmall, color = Palette.Text, fontWeight = FontWeight.Bold)
        Text(t.sub, style = MaterialTheme.typography.labelSmall, color = Palette.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun MiniRecipeCard(r: Recipe, onClick: () -> Unit) {
    GlassCard(Modifier.width(160.dp), padding = 0.dp, onClick = onClick) {
        Box(Modifier.fillMaxWidth().height(96.dp).background(Palette.SpiceBrush), contentAlignment = Alignment.Center) {
            if (r.image.isNotBlank()) PhotoThumb(r.image, Modifier.fillMaxSize(), 400) else Text(r.emoji, fontSize = 44.sp)
        }
        Column(Modifier.padding(12.dp)) {
            Text(r.name, style = MaterialTheme.typography.titleSmall, color = Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${r.minutes.fa()} دقیقه • ${r.difficultyTitle}", style = MaterialTheme.typography.labelSmall, color = Palette.TextMuted)
        }
    }
}

/* ═════════════════════════  درباره ما  ═════════════════════════ */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    MeshBackground {
        Column(Modifier.fillMaxSize()) {
            TopBar("درباره ما", onBack)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                AppLogo(110.dp)
                Spacer(Modifier.height(14.dp))
                GradientText("آشپزی هوشمند", MaterialTheme.typography.headlineLarge)
                Text("نسخه ${BuildConfig.VERSION_NAME.fa()} • رایگان", style = MaterialTheme.typography.labelMedium, color = Palette.TextMuted)
                Spacer(Modifier.height(20.dp))
                GlassCard(Modifier.fillMaxWidth()) {
                    Text(
                        "آشپزی هوشمند دستیار هوش مصنوعی آشپزخانه شماست. کافی است بگویید چه موادی در یخچال و خانه دارید (با صدا، متن یا عکس)؛ " +
                            "برنامه غذاهای مناسب را با دستور پخت قدم‌به‌قدم، کالری، موادی که کم دارید و نکات سرآشپز پیشنهاد می‌دهد. " +
                            "حالت آشپزی با تایمر و خواندن مراحل با صدا، گردونه غذا، برنامه غذایی هفتگی، لیست خرید، کتاب آشپزی آفلاین و تشخیص غذا از روی عکس، آشپزی را ساده و لذت‌بخش می‌کند.",
                        style = MaterialTheme.typography.bodyMedium, color = Palette.Text,
                    )
                }
                Spacer(Modifier.height(14.dp))
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(painterResource(if (ThemeState.dark) R.drawable.logo_amin_gold else R.drawable.logo_amin_navy), null, Modifier.size(56.dp))
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("گروه پژوهشی مدیریت هوشمند امین", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                            Text("سازنده نامه‌نگار، صورتجلسه، وام و گیاه‌شناس هوشمند", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("ایمنی غذایی", style = MaterialTheme.typography.titleSmall, color = Palette.Coral)
                    Spacer(Modifier.height(6.dp))
                    Text("پیشنهادهای هوش مصنوعی را با دقت بخوانید؛ در صورت حساسیت غذایی، بارداری یا بیماری خاص، مواد را بررسی و با پزشک یا متخصص تغذیه مشورت کنید. گوشت و مرغ را کاملاً بپزید.",
                        style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                }
                Spacer(Modifier.height(16.dp))
                GlassButton("معرفی به دوستان", { ShareUtil.shareApp(ctx) }, Modifier.fillMaxWidth(), Icons.Rounded.Share, Palette.Teal)
            }
        }
    }
}
