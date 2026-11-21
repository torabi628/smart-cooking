package ir.aminim.smartcooking.ui.screens

import android.graphics.Paint
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.aminim.smartcooking.MainViewModel
import ir.aminim.smartcooking.data.Meals
import ir.aminim.smartcooking.data.PlanMeal
import ir.aminim.smartcooking.data.Recipe
import ir.aminim.smartcooking.engine.Matcher
import ir.aminim.smartcooking.knowledge.Guides
import ir.aminim.smartcooking.knowledge.Ingredients
import ir.aminim.smartcooking.knowledge.RecipeDb
import ir.aminim.smartcooking.ui.components.*
import ir.aminim.smartcooking.ui.theme.Palette
import ir.aminim.smartcooking.util.Jalali
import ir.aminim.smartcooking.util.fa
import kotlinx.coroutines.launch

/* ═════════════════════════  کتاب آشپزی  ═════════════════════════ */
@Composable
fun LibraryScreen(vm: MainViewModel, onBack: () -> Unit, onRecipe: (Recipe) -> Unit, onAsk: (String) -> Unit) {
    var q by rememberSaveable { mutableStateOf("") }
    var meal by rememberSaveable { mutableStateOf("") }
    val pantry = vm.pantry.toList()
    val list = remember(q, meal, vm.pantry.size) {
        RecipeDb.search(q).filter { meal.isBlank() || meal in it.meals }.map { Matcher.evaluate(it, pantry) }.sortedByDescending { if (pantry.isEmpty()) 0 else it.match }
    }
    MeshBackground {
        Column(Modifier.fillMaxSize().imePadding()) {
            TopBar("کتاب آشپزی", onBack, subtitle = "${RecipeDb.all.size.fa()} دستور آفلاین • هر غذای دیگری را از سرآشپز بخواهید")
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
                AppTextField(
                    q, { q = it }, "جستجوی غذا یا ماده", placeholder = "مثلاً قورمه، مرغ، بادمجان…", icon = Icons.Rounded.Search,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { if (q.isNotBlank() && list.isEmpty()) onAsk(q) }),
                    trailing = if (q.isNotBlank()) { { IconButton({ q = "" }) { Icon(Icons.Rounded.Close, null, tint = Palette.TextMuted) } } } else null,
                )
                Spacer(Modifier.height(10.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectChip("همه", meal.isBlank(), { meal = "" }, color = Palette.Spice)
                    Meals.all.forEach { (id, t) -> SelectChip("${Meals.emoji(id)} $t", meal == id, { meal = id }, color = Palette.Spice) }
                }
                Spacer(Modifier.height(12.dp))
                if (q.isNotBlank()) {
                    GlassCard(Modifier.fillMaxWidth(), onClick = { onAsk(q) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(Icons.Rounded.AutoAwesome, Palette.SpiceBrush, size = 42.dp, tint = Palette.OnSpice)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("دستور «$q» از سرآشپز هوشمند", style = MaterialTheme.typography.titleSmall, color = Palette.Text)
                                Text("دستور کامل و اصیل با هوش مصنوعی", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                            }
                            Icon(Icons.Rounded.ChevronLeft, null, tint = Palette.TextMuted)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                list.forEach { r -> RecipeCard(r.copy(why = ""), onShop = null) { onRecipe(r) }; Spacer(Modifier.height(12.dp)) }
                if (list.isEmpty()) EmptyState(Icons.Rounded.SearchOff, "در کتاب آشپزی نیست", "از سرآشپز هوشمند بخواهید تا دستورش را بنویسد.")
            }
        }
    }
}

/* ═════════════════════════  گردونه غذا  ═════════════════════════ */
@Composable
fun WheelScreen(vm: MainViewModel, onBack: () -> Unit, onRecipe: (Recipe) -> Unit) {
    var fromPantry by rememberSaveable { mutableStateOf(vm.pantry.isNotEmpty()) }
    var meal by rememberSaveable { mutableStateOf(Meals.forHour(java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)).let { if (it == "snack") "dinner" else it }) }
    var seed by remember { mutableIntStateOf(0) }
    val options = remember(fromPantry, meal, seed) { vm.wheelOptions(fromPantry, meal) }
    val rot = remember { Animatable(0f) }
    var winner by remember { mutableStateOf<Recipe?>(null) }
    var spinning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val colors = Palette.Series
    fun spin() {
        if (spinning || options.isEmpty()) return
        spinning = true; winner = null
        scope.launch {
            val target = rot.value + 360f * 6 + (0 until 360).random()
            rot.animateTo(target, tween(4200, easing = FastOutSlowInEasing))
            val sweep = 360f / options.size
            val a = ((270f - rot.value) % 360f + 360f) % 360f
            winner = options[(a / sweep).toInt().coerceIn(0, options.lastIndex)]
            spinning = false
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }
    MeshBackground {
        Column(Modifier.fillMaxSize()) {
            TopBar("گردونه غذا", onBack, subtitle = "نمی‌دانی چی بپزی؟ بسپارش به شانس!")
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("breakfast", "lunch", "dinner", "dessert").forEach { id -> SelectChip("${Meals.emoji(id)} ${Meals.title(id)}", meal == id, { meal = id; winner = null }, color = Palette.Spice) }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("فقط غذاهای مناسب مواد یخچال", style = MaterialTheme.typography.titleSmall, color = Palette.Text, modifier = Modifier.weight(1f))
                    Switch(fromPantry, { fromPantry = it; winner = null }, colors = SwitchDefaults.colors(checkedTrackColor = Palette.Spice))
                }
                Spacer(Modifier.height(16.dp))
                Box(Modifier.size(320.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize().padding(8.dp).rotate(rot.value)) {
                        val n = options.size.coerceAtLeast(1)
                        val sweep = 360f / n
                        val r = size.minDimension / 2
                        val paint = Paint().apply { textSize = r * 0.2f; textAlign = Paint.Align.CENTER; isAntiAlias = true }
                        options.forEachIndexed { i, o ->
                            drawArc(colors[i % colors.size], i * sweep, sweep, true)
                            drawArc(Color.White.copy(alpha = 0.35f), i * sweep, sweep, true, style = Stroke(3f))
                            val mid = Math.toRadians((i * sweep + sweep / 2).toDouble())
                            val cx = center.x + (r * 0.66f) * kotlin.math.cos(mid).toFloat()
                            val cy = center.y + (r * 0.66f) * kotlin.math.sin(mid).toFloat()
                            drawIntoCanvas { it.nativeCanvas.drawText(o.emoji, cx, cy + paint.textSize / 3, paint) }
                        }
                        drawCircle(Color.White.copy(alpha = 0.5f), r, style = Stroke(6f))
                    }
                    Box(Modifier.size(78.dp).clip(CircleShape).background(Palette.GoldBrush).border(4.dp, Color.White.copy(alpha = 0.6f), CircleShape).clickable { spin() }, contentAlignment = Alignment.Center) {
                        Text(if (spinning) "…" else "بچرخون", color = Palette.OnGold, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge)
                    }
                    // نشانگر بالا
                    Canvas(Modifier.align(Alignment.TopCenter).size(34.dp)) {
                        val p = Path().apply { moveTo(size.width / 2, size.height); lineTo(0f, 0f); lineTo(size.width, 0f); close() }
                        drawPath(p, Palette.Coral)
                    }
                }
                Spacer(Modifier.height(16.dp))
                winner?.let { w ->
                    GradientText("امروز: ${w.name}!", MaterialTheme.typography.headlineSmall, align = TextAlign.Center)
                    Spacer(Modifier.height(10.dp))
                    RecipeCard(w, onShop = { vm.addShop(w.missing, w.name) }) { onRecipe(w) }
                    Spacer(Modifier.height(10.dp))
                    GoldButton("بریم بپزیم!", { onRecipe(w) }, Modifier.fillMaxWidth(), icon = Icons.Rounded.SoupKitchen, brush = Palette.SpiceBrush, contentColor = Palette.OnSpice)
                } ?: run {
                    GoldButton("بچرخون!", { spin() }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Casino, enabled = !spinning)
                    Spacer(Modifier.height(10.dp))
                    TextButton({ seed++ }) { Text("غذاهای دیگر روی گردونه", color = Palette.Gold) }
                }
                Spacer(Modifier.height(10.dp))
                options.forEachIndexed { i, o ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(colors[i % colors.size]))
                        Spacer(Modifier.width(8.dp))
                        Text("${o.emoji} ${o.name}", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                    }
                }
            }
        }
    }
}

/* ═════════════════════════  برنامه غذایی هفتگی  ═════════════════════════ */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlannerScreen(vm: MainViewModel, onBack: () -> Unit, onMeal: (PlanMeal) -> Unit, onShopping: () -> Unit) {
    val ctx = LocalContext.current
    var budget by rememberSaveable { mutableStateOf("متوسط") }
    var note by rememberSaveable { mutableStateOf("") }
    val plan = vm.plan
    MeshBackground {
        Column(Modifier.fillMaxSize()) {
            TopBar("برنامه غذایی هفتگی", onBack, subtitle = plan?.let { "ساخته‌شده ${Jalali.dayMonth(it.created)} • ${it.engine}" } ?: "منوی ۷ روزه متعادل + لیست خرید") {
                if (plan != null) {
                    CircleIconButton(Icons.Rounded.Share, { ShareUtil.shareText(ctx, planText(plan)) }, size = 40.dp)
                    CircleIconButton(Icons.Rounded.Refresh, { vm.clearPlan() }, size = 40.dp, tint = Palette.Gold)
                }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                if (plan == null) {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(Icons.Rounded.CalendarMonth, Palette.SkyBrush, size = 44.dp)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("یک هفته بدون دغدغه «امروز چی بپزم»", style = MaterialTheme.typography.titleSmall, color = Palette.Text)
                                Text("صبحانه، ناهار و شام برای ${vm.settings.family.fa()} نفر، با مواد یخچال شما", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Text("بودجه", style = MaterialTheme.typography.labelLarge, color = Palette.Text)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("اقتصادی", "متوسط", "آزاد").forEach { b -> SelectChip(b, budget == b, { budget = b }, color = Palette.Gold) }
                        }
                        Spacer(Modifier.height(12.dp))
                        VoiceTextField(note, { note = it }, "توضیح (اختیاری)", "مثلاً: دو شب در هفته ماهی، جمعه مهمان داریم، غذای کم‌چرب", vm::newDictation, minLines = 2)
                        Spacer(Modifier.height(14.dp))
                        GoldButton("ساخت برنامه هفتگی", { vm.makePlan(budget, note) }, Modifier.fillMaxWidth(), icon = Icons.Rounded.AutoAwesome, loading = vm.planBusy)
                    }
                    if (vm.planBusy) { Spacer(Modifier.height(12.dp)); CookingLoader("در حال چیدن منوی هفته…") }
                } else {
                    if (plan.note.isNotBlank()) {
                        GlassCard(Modifier.fillMaxWidth(), padding = 14.dp) {
                            Row { Text("💡", fontSize = 18.sp); Spacer(Modifier.width(8.dp)); Text(plan.note, style = MaterialTheme.typography.bodySmall, color = Palette.Text) }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    val todayName = Jalali.weekDay(System.currentTimeMillis())
                    plan.days.forEach { d ->
                        val isToday = d.day.replace("‌", "") == todayName.replace("‌", "")
                        GlassCard(Modifier.fillMaxWidth().then(if (isToday) Modifier.border(2.dp, Palette.Spice, RoundedCornerShape(24.dp)) else Modifier), padding = 14.dp) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(d.day, style = MaterialTheme.typography.titleMedium, color = if (isToday) Palette.Spice else Palette.Gold, modifier = Modifier.weight(1f))
                                if (isToday) Pill("امروز", color = Palette.Spice, bg = Palette.SpiceTint)
                            }
                            Spacer(Modifier.height(6.dp))
                            listOf("breakfast" to d.breakfast, "lunch" to d.lunch, "dinner" to d.dinner).forEach { (id, m) ->
                                if (m.name.isNotBlank()) Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onMeal(m) }.padding(vertical = 7.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(Meals.title(id), style = MaterialTheme.typography.labelMedium, color = Palette.TextMuted, modifier = Modifier.width(56.dp))
                                    Text(m.emoji, fontSize = 18.sp)
                                    Spacer(Modifier.width(8.dp))
                                    Text(m.name, style = MaterialTheme.typography.titleSmall, color = Palette.Text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (m.minutes > 0) Text("${m.minutes.fa()}′", style = MaterialTheme.typography.labelSmall, color = Palette.TextFaint)
                                    Icon(Icons.Rounded.ChevronLeft, null, tint = Palette.TextFaint, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                    if (plan.shopping.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        ExpandCard("خرید هفته (${plan.shopping.size.fa()} قلم)", Icons.Rounded.ShoppingCart, Palette.MintBrush, subtitle = "بزنید تا فهرست را ببینید", initiallyOpen = false) {
                            BulletList(plan.shopping, dot = Palette.Mint)
                            Spacer(Modifier.height(12.dp))
                            GoldButton("افزودن همه به لیست خرید", { vm.addShop(plan.shopping, "برنامه هفتگی"); onShopping() }, Modifier.fillMaxWidth(), icon = Icons.Rounded.AddShoppingCart)
                        }
                    }
                }
            }
        }
    }
}

private fun planText(p: ir.aminim.smartcooking.data.MealPlan) = buildString {
    append("📅 برنامه غذایی هفته\n\n")
    p.days.forEach { d -> append("${d.day}:\n  صبحانه: ${d.breakfast.name}\n  ناهار: ${d.lunch.name}\n  شام: ${d.dinner.name}\n") }
    if (p.shopping.isNotEmpty()) { append("\n🛒 خرید هفته:\n"); p.shopping.forEach { append("• $it\n") } }
    append("\n«آشپزی هوشمند» — گروه پژوهشی مدیریت هوشمند امین")
}

/* ═════════════════════════  لیست خرید  ═════════════════════════ */
@Composable
fun ShoppingScreen(vm: MainViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var text by remember { mutableStateOf("") }
    MeshBackground {
        Column(Modifier.fillMaxSize().imePadding()) {
            TopBar("لیست خرید", onBack, subtitle = if (vm.shopping.isEmpty()) "خالی" else "${vm.shopLeft.fa()} قلم مانده از ${vm.shopping.size.fa()}") {
                if (vm.shopping.isNotEmpty()) CircleIconButton(Icons.Rounded.Share, {
                    ShareUtil.shareText(ctx, "🛒 لیست خرید:\n" + vm.shopping.filter { !it.done }.joinToString("\n") { "☐ ${it.name}" } + "\n\n«آشپزی هوشمند»")
                }, size = 40.dp)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
                VoiceTextField(text, { text = it }, "چه چیزی بخریم؟", "مثلاً: نان، ماست، دو کیلو سیب‌زمینی", vm::newDictation, minLines = 1)
                Spacer(Modifier.height(8.dp))
                GoldButton("افزودن", { vm.addShopText(text); text = "" }, Modifier.fillMaxWidth(), icon = Icons.Rounded.AddShoppingCart, enabled = text.isNotBlank())
                Spacer(Modifier.height(16.dp))
                if (vm.shopping.isEmpty()) EmptyState(Icons.Rounded.ShoppingCart, "لیست خرید خالی است", "از صفحه دستور پخت، کمبودها را با یک ضربه اضافه کنید یا اینجا بنویسید.")
                vm.shopping.sortedBy { it.done }.forEach { s ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(16.dp)).background(if (s.done) Palette.Glass.copy(alpha = 0.4f) else Palette.Glass)
                            .border(1.dp, Palette.GlassBorder, RoundedCornerShape(16.dp)).clickable { vm.toggleShop(s) }.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(s.done, { vm.toggleShop(s) }, colors = CheckboxDefaults.colors(checkedColor = Palette.Mint))
                        Column(Modifier.weight(1f)) {
                            Text(
                                (Ingredients.find(s.name)?.emoji?.let { "$it " } ?: "") + s.name, style = MaterialTheme.typography.titleSmall,
                                color = if (s.done) Palette.TextFaint else Palette.Text, textDecoration = if (s.done) TextDecoration.LineThrough else null,
                            )
                            if (s.note.isNotBlank()) Text("برای: ${s.note}", style = MaterialTheme.typography.labelSmall, color = Palette.TextFaint)
                        }
                        IconButton({ vm.removeShop(s) }) { Icon(Icons.Rounded.Close, "حذف", tint = Palette.TextFaint, modifier = Modifier.size(18.dp)) }
                    }
                }
            }
            if (vm.shopping.any { it.done }) BottomBarContainer {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GoldButton("خریده‌ها ← یخچال", { vm.doneToPantry() }, Modifier.weight(1f), icon = Icons.Rounded.Kitchen, brush = Palette.MintBrush, contentColor = Color(0xFF06301A))
                    GlassButton("پاک کردن", { vm.clearDoneShop() }, icon = Icons.Rounded.DeleteSweep, tint = Palette.Coral)
                }
            }
        }
    }
}

/* ═════════════════════════  علاقه‌مندی‌ها و سابقه  ═════════════════════════ */
@Composable
fun FavoritesScreen(vm: MainViewModel, onBack: () -> Unit, onRecipe: (Recipe) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    MeshBackground {
        Column(Modifier.fillMaxSize()) {
            TopBar("علاقه‌مندی‌ها و سابقه", onBack, subtitle = if (vm.settings.cookedCount > 0) "تا امروز ${vm.settings.cookedCount.fa()} غذا با آشپزی هوشمند پخته‌اید 🎉" else null) {
                if (tab == 1 && vm.history.isNotEmpty()) CircleIconButton(Icons.Rounded.DeleteSweep, { vm.clearHistory() }, size = 40.dp, tint = Palette.Coral)
            }
            Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectChip("❤️ علاقه‌مندی‌ها (${vm.favorites.size.fa()})", tab == 0, { tab = 0 }, color = Palette.Coral)
                SelectChip("🕘 اخیراً دیده‌شده (${vm.history.size.fa()})", tab == 1, { tab = 1 }, color = Palette.Gold)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp)) {
                val list = if (tab == 0) vm.favorites else vm.history
                if (list.isEmpty()) EmptyState(if (tab == 0) Icons.Rounded.FavoriteBorder else Icons.Rounded.History, if (tab == 0) "هنوز غذای محبوبی ندارید" else "سابقه‌ای نیست", "در صفحه هر دستور پخت، قلب را بزنید تا اینجا ذخیره شود.")
                list.toList().forEach { r -> RecipeCard(r.copy(why = "", missing = emptyList(), match = -1)) { onRecipe(r) }; Spacer(Modifier.height(12.dp)) }
            }
        }
    }
}

/* ═════════════════════════  پیمانه و جایگزین مواد  ═════════════════════════ */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ToolsScreen(onBack: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    MeshBackground {
        Column(Modifier.fillMaxSize().imePadding()) {
            TopBar("ابزار آشپزخانه", onBack, subtitle = "تبدیل پیمانه و جایگزین مواد")
            Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectChip("⚖️ تبدیل پیمانه", tab == 0, { tab = 0 }, color = Palette.Spice)
                SelectChip("🔄 جایگزین مواد", tab == 1, { tab = 1 }, color = Palette.Spice)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp)) {
                if (tab == 0) Converter() else Substitutes()
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Converter() {
    var amount by rememberSaveable { mutableStateOf("1") }
    var unit by rememberSaveable { mutableIntStateOf(0) }
    var dens by rememberSaveable { mutableIntStateOf(1) }
    val v = amount.map { c -> if (c in '۰'..'۹') '0' + (c - '۰') else if (c == '/' || c == '٫') '.' else c }.joinToString("").toDoubleOrNull() ?: 0.0
    val ml = v * Guides.units[unit].ml
    fun num(x: Double): String = (if (x >= 10 || x == kotlin.math.floor(x)) "%.0f" else "%.1f").format(x).fa()
    AppTextField(amount, { amount = it }, "مقدار", icon = Icons.Rounded.Numbers, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
    Spacer(Modifier.height(10.dp))
    Text("واحد", style = MaterialTheme.typography.labelLarge, color = Palette.Text)
    Spacer(Modifier.height(6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Guides.units.forEachIndexed { i, u -> SelectChip(u.title, unit == i, { unit = i }, color = Palette.Gold) }
    }
    Spacer(Modifier.height(16.dp))
    SectionLabel("معادل‌ها")
    GlassCard(Modifier.fillMaxWidth(), padding = 12.dp) {
        Guides.units.filterIndexed { i, _ -> i != unit }.forEach { u ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                Text(u.title, style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted, modifier = Modifier.weight(1f))
                Text(num(ml / u.ml), style = MaterialTheme.typography.titleSmall, color = Palette.Text)
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    SectionLabel("وزن (گرم) برای ماده")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Guides.densities.forEachIndexed { i, (t, _) -> SelectChip(t, dens == i, { dens = i }) }
    }
    Spacer(Modifier.height(10.dp))
    GlassCard(Modifier.fillMaxWidth()) {
        val g = ml / 240.0 * Guides.densities[dens].second
        Text("${num(v)} ${Guides.units[unit].title} ${Guides.densities[dens].first}", style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
        Text("≈ ${num(g)} گرم", style = MaterialTheme.typography.headlineMedium, color = Palette.Spice, fontWeight = FontWeight.Black)
        Text("پیمانه استاندارد = ۲۴۰ میلی‌لیتر؛ مقادیر تقریبی است.", style = MaterialTheme.typography.labelSmall, color = Palette.TextFaint)
    }
}

@Composable
private fun Substitutes() {
    var q by rememberSaveable { mutableStateOf("") }
    AppTextField(q, { q = it }, "چه چیزی ندارید؟", placeholder = "مثلاً خامه، تخم‌مرغ، بکینگ پودر", icon = Icons.Rounded.Search)
    Spacer(Modifier.height(12.dp))
    val n = Ingredients.norm(q)
    Guides.substitutes.filter { n.isBlank() || Ingredients.norm(it.first).contains(n) || Ingredients.norm(it.second).contains(n) }.forEach { (a, b) ->
        GlassCard(Modifier.fillMaxWidth().padding(vertical = 4.dp), padding = 14.dp) {
            Text("به‌جای $a", style = MaterialTheme.typography.titleSmall, color = Palette.Spice)
            Spacer(Modifier.height(4.dp))
            Text(b, style = MaterialTheme.typography.bodyMedium, color = Palette.Text)
        }
    }
}

/* ═════════════════════════  آموزش آشپزی  ═════════════════════════ */
@Composable
fun GuidesScreen(onBack: () -> Unit, onGuide: (String) -> Unit) {
    MeshBackground {
        Column(Modifier.fillMaxSize()) {
            TopBar("آموزش آشپزی", onBack, subtitle = "فوت‌وفن‌های پایه آشپزی ایرانی")
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
                Guides.all.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { g ->
                            GlassCard(Modifier.weight(1f).height(150.dp), padding = 14.dp, onClick = { onGuide(g.id) }) {
                                Text(g.emoji, fontSize = 34.sp)
                                Spacer(Modifier.weight(1f))
                                Text(g.title, style = MaterialTheme.typography.titleSmall, color = Palette.Text, fontWeight = FontWeight.Bold)
                                Text(g.summary, style = MaterialTheme.typography.labelSmall, color = Palette.TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
fun GuideScreen(id: String, onBack: () -> Unit) {
    val g = Guides.byId(id) ?: run { LaunchedEffect(Unit) { onBack() }; return }
    MeshBackground {
        Column(Modifier.fillMaxSize()) {
            TopBar(g.title, onBack)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
                Box(Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(24.dp)).background(Palette.SpiceBrush), contentAlignment = Alignment.Center) { Text(g.emoji, fontSize = 72.sp) }
                Spacer(Modifier.height(14.dp))
                Text(g.summary, style = MaterialTheme.typography.bodyLarge, color = Palette.TextMuted)
                Spacer(Modifier.height(14.dp))
                GlassCard(Modifier.fillMaxWidth()) { BulletList(g.steps, numbered = true, dot = Palette.Spice) }
                if (g.tips.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    GlassCard(Modifier.fillMaxWidth()) {
                        Text("👨‍🍳 نکته", style = MaterialTheme.typography.titleSmall, color = Palette.Gold)
                        Spacer(Modifier.height(6.dp))
                        BulletList(g.tips, dot = Palette.Gold)
                    }
                }
            }
        }
    }
}
