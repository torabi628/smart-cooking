package ir.aminim.smartcooking.ui.screens

import android.content.Context
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.aminim.smartcooking.MainViewModel
import ir.aminim.smartcooking.data.Meals
import ir.aminim.smartcooking.data.Recipe
import ir.aminim.smartcooking.engine.Amounts
import ir.aminim.smartcooking.engine.Matcher
import ir.aminim.smartcooking.ui.components.*
import ir.aminim.smartcooking.ui.theme.Palette
import ir.aminim.smartcooking.util.fa
import ir.aminim.smartcooking.util.formatDuration
import kotlinx.coroutines.delay
import java.util.Locale

/* ═════════════════════════  پیشنهاد غذا  ═════════════════════════ */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SuggestScreen(vm: MainViewModel, onBack: () -> Unit, onRecipe: (Recipe) -> Unit, onPantry: () -> Unit) {
    val p = vm.prefs
    var showFilters by remember { mutableStateOf(vm.suggestions.isEmpty() && vm.offlineSuggestions.isEmpty()) }
    val preview = remember(vm.pantry.size, p) { Matcher.suggest(vm.pantry.toList(), p, 6) }
    val hasResults = vm.suggestions.isNotEmpty() || vm.offlineSuggestions.isNotEmpty()
    MeshBackground {
        Column(Modifier.fillMaxSize()) {
            TopBar("چی بپزم؟", onBack, subtitle = if (vm.suggestEngine.isNotBlank()) "موتور: ${vm.suggestEngine}" else "پیشنهاد هوشمند با مواد خودت") {
                CircleIconButton(if (showFilters) Icons.Rounded.ExpandLess else Icons.Rounded.Tune, { showFilters = !showFilters }, size = 40.dp, tint = Palette.Gold)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
                // خلاصه یخچال
                GlassCard(Modifier.fillMaxWidth(), padding = 14.dp, onClick = onPantry) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Kitchen, null, tint = Palette.Spice)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (vm.pantry.isEmpty()) "یخچال خالی است؛ بزنید و مواد را ثبت کنید" else vm.pantry.joinToString("، ") { it.name },
                            style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                        )
                        Icon(Icons.Rounded.Edit, null, tint = Palette.TextFaint, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (showFilters) {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Text("وعده", style = MaterialTheme.typography.labelLarge, color = Palette.Text)
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SelectChip("فرقی نداره", p.meal.isBlank(), { vm.prefs = p.copy(meal = "") }, color = Palette.Spice)
                            Meals.all.forEach { (id, t) -> SelectChip("${Meals.emoji(id)} $t", p.meal == id, { vm.prefs = p.copy(meal = id) }, color = Palette.Spice) }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("وقت داری؟", style = MaterialTheme.typography.labelLarge, color = Palette.Text)
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0 to "مهم نیست", 20 to "⚡ ۲۰ دقیقه", 45 to "۴۵ دقیقه", 90 to "۱:۳۰ ساعت").forEach { (m, t) -> SelectChip(t, p.maxMinutes == m, { vm.prefs = p.copy(maxMinutes = m) }, color = Palette.Spice) }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("برای چند نفر؟", style = MaterialTheme.typography.labelLarge, color = Palette.Text, modifier = Modifier.weight(1f))
                            Stepper(p.people, { vm.prefs = p.copy(people = it.coerceIn(1, 20)) })
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("سبک و رژیم", style = MaterialTheme.typography.labelLarge, color = Palette.Text)
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Matcher.cuisines.forEach { c -> SelectChip(c, p.cuisine == c, { vm.prefs = p.copy(cuisine = if (p.cuisine == c) "" else c) }, color = Palette.Gold) }
                            Matcher.dietOptions.forEach { d -> SelectChip(d, d in p.diet, { vm.prefs = p.copy(diet = if (d in p.diet) p.diet - d else p.diet + d) }) }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("فقط با همین مواد", style = MaterialTheme.typography.titleSmall, color = Palette.Text)
                                Text("بدون نیاز به خرید", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                            }
                            Switch(p.strict, { vm.prefs = p.copy(strict = it) }, colors = SwitchDefaults.colors(checkedTrackColor = Palette.Spice))
                        }
                        Spacer(Modifier.height(10.dp))
                        VoiceTextField(p.note, { vm.prefs = p.copy(note = it) }, "حال و هوای امروز (اختیاری)", "مثلاً: یه غذای سبک و تند، یا مهمون دارم", vm::newDictation, minLines = 1)
                    }
                    Spacer(Modifier.height(12.dp))
                }
                GoldButton(if (hasResults) "پیشنهادهای تازه" else "پیشنهاد بده ✨", { showFilters = false; vm.suggest() }, Modifier.fillMaxWidth(), icon = Icons.Rounded.AutoAwesome, loading = vm.suggestBusy)
                Spacer(Modifier.height(16.dp))

                if (vm.suggestBusy) CookingLoader(vm.suggestStage)
                vm.suggestError?.let { e ->
                    GlassCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.ErrorOutline, null, tint = Palette.Coral); Spacer(Modifier.width(10.dp))
                            Text(e, style = MaterialTheme.typography.bodyMedium, color = Palette.Text)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                if (vm.suggestIntro.isNotBlank() && vm.suggestions.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text("👨‍🍳", fontSize = 26.sp); Spacer(Modifier.width(8.dp))
                        Text(vm.suggestIntro, style = MaterialTheme.typography.bodyLarge, color = Palette.Text, modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                }
                vm.suggestions.forEachIndexed { i, r ->
                    RecipeCard(r, rank = i + 1, onShop = { vm.addShop(r.missing, r.name) }) { onRecipe(r) }
                    Spacer(Modifier.height(12.dp))
                }
                val offline = if (hasResults) vm.offlineSuggestions.toList() else preview
                if (offline.isNotEmpty() && !vm.suggestBusy) {
                    Spacer(Modifier.height(6.dp))
                    SectionLabel(if (vm.suggestions.isEmpty()) "از کتاب آشپزی (بدون اینترنت)" else "از کتاب آشپزی خودت")
                    Spacer(Modifier.height(8.dp))
                    offline.forEach { r ->
                        RecipeCard(r, onShop = { vm.addShop(r.missing, r.name) }) { onRecipe(r) }
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

/** انیمیشن انتظار */
@Composable
fun CookingLoader(stage: String) {
    val foods = listOf("🍳", "🥘", "🍲", "🍝", "🥗", "🍛", "🍗", "🥙")
    var i by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(650); i = (i + 1) % foods.size } }
    val tips = listOf("در حال بررسی مواد یخچال…", "چشیدن ایده‌ها…", "انتخاب بهترین ترکیب‌ها…", "نوشتن دستور پخت قدم‌به‌قدم…", "محاسبه کالری…")
    var t by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(2200); t = (t + 1) % tips.size } }
    val pulse = rememberInfiniteTransition(label = "l").animateFloat(0.9f, 1.15f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "s")
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(96.dp).clip(CircleShape).background(Palette.SpiceTint), contentAlignment = Alignment.Center) {
                Text(foods[i], fontSize = 48.sp, modifier = Modifier.scale(pulse.value))
            }
            Spacer(Modifier.height(12.dp))
            Text(stage.ifBlank { "سرآشپز در حال فکر کردن…" }, style = MaterialTheme.typography.titleSmall, color = Palette.Text, textAlign = TextAlign.Center)
            Text(tips[t], style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)), color = Palette.Spice, trackColor = Palette.GlassStrong)
        }
    }
    Spacer(Modifier.height(12.dp))
}

/** کارت پیشنهاد غذا */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecipeCard(r: Recipe, rank: Int = 0, onShop: (() -> Unit)? = null, onClick: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), padding = 0.dp, onClick = onClick) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(96.dp).fillMaxHeight().heightIn(min = 130.dp).background(Palette.SpiceBrush), contentAlignment = Alignment.Center) {
                if (r.image.isNotBlank()) PhotoThumb(r.image, Modifier.matchParentSize(), 300) else Text(r.emoji, fontSize = 46.sp)
                if (rank > 0) Box(Modifier.align(Alignment.TopStart).padding(6.dp).size(24.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                    Text(rank.fa(), color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
            Column(Modifier.weight(1f).padding(12.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(r.name, style = MaterialTheme.typography.titleMedium, color = Palette.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${r.cuisine} • ${r.difficultyTitle}", style = MaterialTheme.typography.labelSmall, color = Palette.Gold)
                    }
                    if (r.match >= 0) ScoreRing(r.match, matchColor(r.match), size = 52.dp, label = "تطابق")
                }
                if (r.desc.isNotBlank()) Text(r.desc, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (r.minutes > 0) MiniStat(Icons.Rounded.Schedule, "${r.minutes.fa()} دقیقه")
                    if (r.calories > 0) MiniStat(Icons.Rounded.LocalFireDepartment, "${r.calories.fa()} کالری")
                }
                if (r.why.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text("✓ ${r.why}", style = MaterialTheme.typography.labelSmall, color = Palette.Herb, maxLines = 2)
                }
                if (r.missing.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        r.missing.take(4).forEach { m ->
                            Text(m, style = MaterialTheme.typography.labelSmall, color = Palette.Coral,
                                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Palette.Coral.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 3.dp))
                        }
                        onShop?.let {
                            Row(Modifier.clip(RoundedCornerShape(10.dp)).background(Palette.Teal.copy(alpha = 0.15f)).clickable(onClick = it).padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.AddShoppingCart, null, tint = Palette.Teal, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("لیست خرید", style = MaterialTheme.typography.labelSmall, color = Palette.Teal)
                            }
                        }
                    }
                }
            }
        }
    }
}

fun matchColor(m: Int): Color = when { m >= 85 -> Palette.Herb; m >= 60 -> Palette.Gold; m >= 40 -> Palette.Orange; else -> Palette.Coral }

/* ═════════════════════════  دستور پخت  ═════════════════════════ */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecipeScreen(vm: MainViewModel, onBack: () -> Unit, onCook: () -> Unit, onChat: (Recipe) -> Unit, onShopping: () -> Unit) {
    val r = vm.recipe ?: run { LaunchedEffect(Unit) { onBack() }; return }
    val ctx = LocalContext.current
    val factor = vm.servings.toDouble() / r.servings.coerceAtLeast(1)
    val ids = vm.pantryIds
    val names = remember(vm.pantry.size) { Matcher.pantryNames(vm.pantry.toList()) }
    val checked = remember(r.name) { mutableStateListOf<Int>() }
    MeshBackground {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                // سربرگ
                Box(Modifier.fillMaxWidth().height(230.dp).background(Palette.SpiceBrush)) {
                    if (r.image.isNotBlank()) PhotoThumb(r.image, Modifier.fillMaxSize(), 1000)
                    else Text(r.emoji, fontSize = 110.sp, modifier = Modifier.align(Alignment.Center))
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent, Palette.Bg.copy(alpha = 0.9f)))))
                    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircleIconButton(Icons.AutoMirrored.Rounded.ArrowBack, onBack, tint = Color.White)
                        Spacer(Modifier.weight(1f))
                        if (r.hasDetails) {
                            CircleIconButton(Icons.Rounded.Share, { ShareUtil.shareText(ctx, shareText(r, factor)) }, tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            CircleIconButton(if (vm.isFav(r)) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, { vm.toggleFav(r) }, tint = if (vm.isFav(r)) Palette.Coral else Color.White)
                        }
                    }
                }
                Column(Modifier.padding(horizontal = 20.dp)) {
                    GradientText(r.name, MaterialTheme.typography.headlineMedium)
                    if (r.desc.isNotBlank()) Text(r.desc, style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (r.source.isNotBlank()) Pill(r.source, Icons.Rounded.AutoAwesome, color = Palette.Gold)
                        if (r.cuisine.isNotBlank()) Pill(r.cuisine)
                        r.tags.distinct().forEach { Pill(it, color = Palette.Herb) }
                    }

                    if (vm.recipeBusy) {
                        Spacer(Modifier.height(16.dp))
                        CookingLoader(if (r.image.isNotBlank() && !r.hasDetails) "در حال شناسایی غذا و نوشتن دستور…" else "سرآشپز در حال نوشتن دستور کامل…")
                    }
                    vm.recipeError?.let { e ->
                        Spacer(Modifier.height(16.dp))
                        GlassCard(Modifier.fillMaxWidth()) {
                            Text(e, style = MaterialTheme.typography.bodyMedium, color = Palette.Coral)
                            Spacer(Modifier.height(10.dp))
                            if (!r.name.startsWith("در حال")) GlassButton("دوباره تلاش کن", { vm.retryRecipe() }, Modifier.fillMaxWidth(), Icons.Rounded.Refresh, Palette.Teal)
                        }
                    }

                    if (r.hasDetails) {
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            InfoTile(Icons.Rounded.Schedule, "زمان", if (r.minutes > 0) formatMinutes(r.minutes) else "—", Palette.Spice, Modifier.weight(1f))
                            InfoTile(Icons.Rounded.Speed, "سختی", r.difficultyTitle, Palette.Gold, Modifier.weight(1f))
                            InfoTile(Icons.Rounded.LocalFireDepartment, "کالری هر نفر", if (r.calories > 0) r.calories.fa() else "—", Palette.Coral, Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(12.dp))
                        GlassCard(Modifier.fillMaxWidth(), padding = 14.dp) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Groups, null, tint = Palette.Spice)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("تعداد نفرات", style = MaterialTheme.typography.titleSmall, color = Palette.Text)
                                    Text("مقادیر خودکار تنظیم می‌شوند", style = MaterialTheme.typography.labelSmall, color = Palette.TextMuted)
                                }
                                Stepper(vm.servings, { vm.servings = it.coerceIn(1, 30) })
                            }
                        }

                        val required = r.ingredients.filter { !it.optional }
                        val haveCount = required.count { Matcher.has(it, ids, names) }
                        Spacer(Modifier.height(16.dp))
                        SectionLabel("مواد لازم", trailing = { Text("${haveCount.fa()} از ${required.size.fa()} را دارید", style = MaterialTheme.typography.labelMedium, color = matchColor(if (required.isEmpty()) 100 else haveCount * 100 / required.size)) })
                        Spacer(Modifier.height(6.dp))
                        GlassCard(Modifier.fillMaxWidth(), padding = 8.dp) {
                            r.ingredients.forEachIndexed { i, ing ->
                                val have = Matcher.has(ing, ids, names)
                                val done = i in checked
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { if (done) checked.remove(i) else checked.add(i) }.padding(horizontal = 8.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        when { done -> Icons.Rounded.CheckBox; have -> Icons.Rounded.CheckCircle; else -> Icons.Rounded.ShoppingCart },
                                        null, tint = when { done -> Palette.Teal; have -> Palette.Herb; ing.optional -> Palette.TextFaint; else -> Palette.Coral }, modifier = Modifier.size(20.dp),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(ing.name + if (ing.optional) " (اختیاری)" else "", style = MaterialTheme.typography.bodyMedium, color = if (done) Palette.TextFaint else Palette.Text, modifier = Modifier.weight(1f))
                                    Text(Amounts.scale(ing.amount, factor), style = MaterialTheme.typography.labelMedium, color = Palette.Gold, textAlign = TextAlign.End, modifier = Modifier.widthIn(max = 170.dp))
                                }
                            }
                        }
                        val missing = r.ingredients.filter { !it.optional && !Matcher.has(it, ids, names) }.map { it.name }
                        if (missing.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            GlassButton("افزودن ${missing.size.fa()} کمبود به لیست خرید", { vm.addShop(missing, r.name) }, Modifier.fillMaxWidth(), Icons.Rounded.AddShoppingCart, Palette.Teal)
                        }

                        if (r.protein + r.carbs + r.fat > 0) {
                            Spacer(Modifier.height(16.dp))
                            SectionLabel("ارزش غذایی هر نفر (تقریبی)")
                            Spacer(Modifier.height(6.dp))
                            GlassCard(Modifier.fillMaxWidth(), padding = 14.dp) {
                                val total = (r.protein + r.carbs + r.fat).coerceAtLeast(1)
                                listOf(Triple("پروتئین", r.protein, Palette.Teal), Triple("کربوهیدرات", r.carbs, Palette.Gold), Triple("چربی", r.fat, Palette.Coral)).forEach { (t, v, c) ->
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                                        Text(t, style = MaterialTheme.typography.labelMedium, color = Palette.Text, modifier = Modifier.width(84.dp))
                                        PercentBar(v * 100 / total, c, Modifier.weight(1f))
                                        Text("${v.fa()} گرم", style = MaterialTheme.typography.labelSmall, color = Palette.TextMuted, modifier = Modifier.width(64.dp), textAlign = TextAlign.End)
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        SectionLabel("طرز تهیه (${r.steps.size.fa()} مرحله)")
                        Spacer(Modifier.height(6.dp))
                        r.steps.forEachIndexed { i, s ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                                Box(Modifier.size(30.dp).clip(CircleShape).background(Palette.SpiceBrush), contentAlignment = Alignment.Center) {
                                    Text((i + 1).fa(), color = Palette.OnSpice, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(s.text, style = MaterialTheme.typography.bodyLarge, color = Palette.Text)
                                    if (s.minutes > 0) Pill("⏱ ${formatMinutes(s.minutes)}", color = Palette.Spice, bg = Palette.SpiceTint)
                                }
                            }
                        }

                        if (r.tips.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            GlassCard(Modifier.fillMaxWidth()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("👨‍🍳", fontSize = 22.sp); Spacer(Modifier.width(8.dp))
                                    Text("نکته‌های سرآشپز", style = MaterialTheme.typography.titleMedium, color = Palette.Gold)
                                }
                                Spacer(Modifier.height(8.dp))
                                BulletList(r.tips, dot = Palette.Gold)
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                    }
                }
            }
            if (r.hasDetails) BottomBarContainer {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GoldButton("شروع آشپزی", onCook, Modifier.weight(1f), icon = Icons.Rounded.PlayArrow, brush = Palette.SpiceBrush, contentColor = Palette.OnSpice)
                    Spacer(Modifier.width(10.dp))
                    CircleIconButton(Icons.Rounded.Forum, { onChat(r) }, size = 54.dp, tint = Palette.Teal)
                }
            }
        }
    }
}

fun formatMinutes(m: Int): String = when {
    m < 60 -> "${m.fa()} دقیقه"
    m % 60 == 0 -> "${(m / 60).fa()} ساعت"
    else -> "${(m / 60).fa()} ساعت و ${(m % 60).fa()} دقیقه"
}

private fun shareText(r: Recipe, factor: Double): String = buildString {
    append("${r.emoji} ${r.name}\n")
    if (r.desc.isNotBlank()) append(r.desc + "\n")
    append("\n🛒 مواد لازم:\n")
    r.ingredients.forEach { append("• ${it.name}: ${Amounts.scale(it.amount, factor)}\n") }
    append("\n👩‍🍳 طرز تهیه:\n")
    r.steps.forEachIndexed { i, s -> append("${(i + 1).fa()}. ${s.text}\n") }
    if (r.tips.isNotEmpty()) { append("\n💡 نکته‌ها:\n"); r.tips.forEach { append("• $it\n") } }
    append("\nارسال‌شده از «آشپزی هوشمند» — گروه پژوهشی مدیریت هوشمند امین")
}

/* ═════════════════════════  حالت آشپزی  ═════════════════════════ */
@Composable
fun CookModeScreen(vm: MainViewModel, onBack: () -> Unit) {
    val r = vm.recipe?.takeIf { it.hasDetails } ?: run { LaunchedEffect(Unit) { onBack() }; return }
    val ctx = LocalContext.current
    val view = LocalView.current
    DisposableEffect(Unit) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }
    val factor = vm.servings.toDouble() / r.servings.coerceAtLeast(1)
    val total = r.steps.size + 2 // مواد + مراحل + پایان
    var page by rememberSaveable { mutableIntStateOf(0) }

    // خواندن مراحل با صدا
    var speak by remember { mutableStateOf(vm.settings.speakSteps) }
    var ttsReady by remember { mutableStateOf(false) }
    val tts: TextToSpeech = remember {
        val holder = arrayOfNulls<TextToSpeech>(1)
        val engine = TextToSpeech(ctx.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val res = runCatching { holder[0]?.setLanguage(Locale("fa", "IR")) }.getOrNull() ?: TextToSpeech.LANG_NOT_SUPPORTED
                ttsReady = res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED
            }
        }
        holder[0] = engine
        engine
    }
    DisposableEffect(Unit) { onDispose { runCatching { tts.stop(); tts.shutdown() } } }
    LaunchedEffect(Unit) {
        delay(1500)
        if (!ttsReady) runCatching {
            val res = tts.setLanguage(Locale("fa", "IR"))
            ttsReady = res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED
        }
    }
    LaunchedEffect(page, speak, ttsReady) {
        if (!speak || !ttsReady) { runCatching { tts.stop() }; return@LaunchedEffect }
        val text = when {
            page == 0 -> "مواد لازم برای ${r.name}"
            page <= r.steps.size -> "مرحله ${page}. ${r.steps[page - 1].text}"
            else -> "نوش جان!"
        }
        runCatching { tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "step$page") }
    }

    // تایمر
    var timerTotal by remember { mutableIntStateOf(0) }
    var timerLeft by remember { mutableIntStateOf(0) }
    var timerRunning by remember { mutableStateOf(false) }
    var timerStep by remember { mutableIntStateOf(-1) }
    LaunchedEffect(timerRunning) {
        while (timerRunning && timerLeft > 0) { delay(1000); timerLeft-- }
        if (timerRunning && timerLeft <= 0) { timerRunning = false; alarm(ctx); vm.toast = "⏰ زمان مرحله ${timerStep.fa()} تمام شد!" }
    }
    fun startTimer(step: Int, minutes: Int) { timerStep = step; timerTotal = minutes * 60; timerLeft = minutes * 60; timerRunning = true }

    MeshBackground {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                CircleIconButton(Icons.Rounded.Close, onBack)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.name, style = MaterialTheme.typography.titleMedium, color = Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        when { page == 0 -> "آماده‌سازی مواد"; page <= r.steps.size -> "مرحله ${page.fa()} از ${r.steps.size.fa()}"; else -> "پایان" },
                        style = MaterialTheme.typography.labelSmall, color = Palette.TextMuted,
                    )
                }
                CircleIconButton(if (speak) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeOff, {
                    speak = !speak; vm.settings.speakSteps = speak
                    if (speak && !ttsReady) vm.toast = "موتور خواندن فارسی روی گوشی نصب نیست؛ از تنظیمات گوشی «Google TTS» و زبان فارسی را فعال کنید"
                }, tint = if (speak) Palette.Spice else Palette.TextMuted)
            }
            val prog by animateFloatAsState((page + 1f) / total, label = "p")
            LinearProgressIndicator({ prog }, Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(6.dp).clip(RoundedCornerShape(3.dp)), color = Palette.Spice, trackColor = Palette.GlassStrong)
            if (timerStep > 0 && (timerRunning || timerLeft > 0) && page != timerStep) {
                Row(
                    Modifier.padding(horizontal = 20.dp, vertical = 8.dp).clip(RoundedCornerShape(14.dp)).background(Palette.SpiceTint).clickable { page = timerStep }.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Timer, null, tint = Palette.Spice, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("تایمر مرحله ${timerStep.fa()}: ${formatDuration(timerLeft.toLong())}", style = MaterialTheme.typography.labelMedium, color = Palette.Spice)
                }
            }
            AnimatedContent(page, Modifier.weight(1f), transitionSpec = { (slideInHorizontally { -it / 3 } + fadeIn()) togetherWith fadeOut() }, label = "c") { pg ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    when {
                        pg == 0 -> {
                            Text(r.emoji, fontSize = 72.sp)
                            Spacer(Modifier.height(8.dp))
                            GradientText("اول مواد را آماده کن", MaterialTheme.typography.headlineSmall)
                            Spacer(Modifier.height(4.dp))
                            Text("برای ${vm.servings.fa()} نفر", style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
                            Spacer(Modifier.height(16.dp))
                            val got = remember { mutableStateListOf<Int>() }
                            r.ingredients.forEachIndexed { i, ing ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(14.dp)).background(if (i in got) Palette.SpiceTint else Palette.Glass)
                                        .clickable { if (i in got) got.remove(i) else got.add(i) }.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(if (i in got) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null, tint = if (i in got) Palette.Spice else Palette.TextFaint)
                                    Spacer(Modifier.width(10.dp))
                                    Text(ing.name, style = MaterialTheme.typography.titleSmall, color = Palette.Text, modifier = Modifier.weight(1f))
                                    Text(Amounts.scale(ing.amount, factor), style = MaterialTheme.typography.labelLarge, color = Palette.Gold)
                                }
                            }
                        }
                        pg <= r.steps.size -> {
                            val s = r.steps[pg - 1]
                            Box(Modifier.size(64.dp).clip(CircleShape).background(Palette.SpiceBrush), contentAlignment = Alignment.Center) {
                                Text(pg.fa(), fontSize = 30.sp, color = Palette.OnSpice, fontWeight = FontWeight.Black)
                            }
                            Spacer(Modifier.height(24.dp))
                            Text(s.text, style = MaterialTheme.typography.headlineSmall.copy(lineHeight = 40.sp), color = Palette.Text, textAlign = TextAlign.Center)
                            if (s.minutes > 0) {
                                Spacer(Modifier.height(28.dp))
                                val active = timerStep == pg && (timerRunning || timerLeft > 0)
                                TimerDial(if (active) timerLeft else s.minutes * 60, if (active) timerTotal else s.minutes * 60, timerRunning && timerStep == pg)
                                Spacer(Modifier.height(14.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    when {
                                        !active -> GoldButton("شروع تایمر ${formatMinutes(s.minutes)}", { startTimer(pg, s.minutes) }, icon = Icons.Rounded.Timer, brush = Palette.SpiceBrush, contentColor = Palette.OnSpice)
                                        timerRunning -> GlassButton("توقف", { timerRunning = false }, icon = Icons.Rounded.Pause, tint = Palette.Spice)
                                        else -> GlassButton("ادامه", { timerRunning = true }, icon = Icons.Rounded.PlayArrow, tint = Palette.Spice)
                                    }
                                    if (active) GlassButton("از نو", { timerRunning = false; timerLeft = 0; timerStep = -1 }, icon = Icons.Rounded.Replay, tint = Palette.TextMuted)
                                }
                            }
                        }
                        else -> FinishPage(vm, r, onBack)
                    }
                }
            }
            if (page < total - 1) Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (page > 0) GlassButton("قبلی", { page-- }, Modifier.weight(1f), Icons.AutoMirrored.Rounded.ArrowForward)
                GoldButton(if (page == total - 2) "تمام شد! 🎉" else "مرحله بعد", { page++ }, Modifier.weight(2f), icon = Icons.AutoMirrored.Rounded.ArrowBack, brush = Palette.SpiceBrush, contentColor = Palette.OnSpice)
            }
        }
    }
}

@Composable
private fun TimerDial(left: Int, total: Int, running: Boolean) {
    val frac = if (total > 0) left.toFloat() / total else 0f
    val anim by animateFloatAsState(frac, tween(900, easing = LinearEasing), label = "t")
    val track = Palette.GlassStrong; val c = Palette.Spice
    val pulse = rememberInfiniteTransition(label = "tp").animateFloat(1f, 1.04f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "tps")
    Box(Modifier.size(190.dp).scale(if (running) pulse.value else 1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = size.minDimension * 0.08f
            val arc = Size(size.width - sw, size.height - sw)
            drawArc(track, 0f, 360f, false, Offset(sw / 2, sw / 2), arc, style = Stroke(sw, cap = StrokeCap.Round))
            drawArc(c, -90f, 360f * anim, false, Offset(sw / 2, sw / 2), arc, style = Stroke(sw, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(formatDuration(left.toLong()), style = MaterialTheme.typography.displaySmall, color = Palette.Text, fontWeight = FontWeight.Black)
            Text(if (running) "در حال پخت…" else "تایمر", style = MaterialTheme.typography.labelMedium, color = Palette.TextMuted)
        }
    }
}

@Suppress("DEPRECATION")
private fun alarm(ctx: Context) {
    runCatching {
        val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 250, 500, 250, 800), -1)) else v.vibrate(longArrayOf(0, 500, 250, 500), -1)
    }
    runCatching { RingtoneManager.getRingtone(ctx, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))?.play() }
}

@Composable
private fun FinishPage(vm: MainViewModel, r: Recipe, onBack: () -> Unit) {
    var remove by remember { mutableStateOf(true) }
    Box(Modifier.fillMaxWidth().height(200.dp)) {
        Confetti(Modifier.fillMaxSize())
        Text("🎉", fontSize = 90.sp, modifier = Modifier.align(Alignment.Center))
    }
    GradientText("نوش جان!", MaterialTheme.typography.displaySmall)
    Spacer(Modifier.height(8.dp))
    Text("«${r.name}» آماده است. سرآشپز به شما افتخار می‌کند 👨‍🍳", style = MaterialTheme.typography.bodyLarge, color = Palette.TextMuted, textAlign = TextAlign.Center)
    Spacer(Modifier.height(20.dp))
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Palette.Glass).clickable { remove = !remove }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(remove, { remove = it }, colors = CheckboxDefaults.colors(checkedColor = Palette.Spice))
        Text("مواد تازه مصرف‌شده از «یخچال من» برداشته شوند", style = MaterialTheme.typography.bodyMedium, color = Palette.Text)
    }
    Spacer(Modifier.height(12.dp))
    if (!vm.isFav(r)) {
        GlassButton("افزودن به علاقه‌مندی‌ها", { vm.toggleFav(r) }, Modifier.fillMaxWidth(), Icons.Rounded.FavoriteBorder, Palette.Coral)
        Spacer(Modifier.height(10.dp))
    }
    GoldButton("تمام", { vm.markCooked(r, remove); onBack() }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Done)
}

/** کاغذ رنگی جشن */
@Composable
fun Confetti(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "cf").animateFloat(0f, 1f, infiniteRepeatable(tween(3200, easing = LinearEasing)), label = "cft")
    val colors = Palette.Series
    val parts = remember { List(70) { floatArrayOf(Math.random().toFloat(), Math.random().toFloat(), 0.4f + Math.random().toFloat() * 0.8f, (Math.random() * 360).toFloat()) } }
    Canvas(modifier) {
        parts.forEachIndexed { i, p ->
            val y = ((p[1] + t.value * p[2]) % 1f) * size.height
            val x = p[0] * size.width + kotlin.math.sin((t.value * 6.28f * p[2]) + i) * 18f
            rotate(p[3] + t.value * 360f * p[2], Offset(x, y)) {
                drawRect(colors[i % colors.size], Offset(x - 5f, y - 3f), Size(10f, 6f))
            }
        }
    }
}
