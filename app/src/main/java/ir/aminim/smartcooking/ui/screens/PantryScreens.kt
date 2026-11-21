package ir.aminim.smartcooking.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.aminim.smartcooking.MainViewModel
import ir.aminim.smartcooking.data.PantryItem
import ir.aminim.smartcooking.knowledge.IngCat
import ir.aminim.smartcooking.knowledge.Ingredients
import ir.aminim.smartcooking.ui.components.*
import ir.aminim.smartcooking.ui.theme.Palette
import ir.aminim.smartcooking.util.fa

/** متن و رنگ وضعیت انقضا */
fun expiryLabel(p: PantryItem): Pair<String, Color>? {
    val d = p.daysLeft()
    return when {
        d == Int.MAX_VALUE -> null
        d < 0 -> "تاریخ گذشته" to Palette.Coral
        d == 0 -> "امروز مصرف شود" to Palette.Coral
        d == 1 -> "تا فردا" to Palette.Orange
        d <= 3 -> "${d.fa()} روز مانده" to Palette.Gold
        d < 30 -> "${d.fa()} روز" to Palette.Herb
        else -> "بیش از یک ماه" to Palette.Herb
    }
}

/* ═════════════════════════  یخچال من  ═════════════════════════ */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PantryScreen(vm: MainViewModel, onBack: () -> Unit, onSuggest: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var tab by remember { mutableIntStateOf(0) }
    var confirmClear by remember { mutableStateOf(false) }
    val (camera, gallery) = rememberPhotoPickers { vm.scanFridge(it) }
    val tabs = listOf<Pair<String, IngCat?>>("⭐ پرکاربرد" to null) + IngCat.entries.filter { c -> Ingredients.all.any { it.cat == c } }.map { "${it.emoji} ${it.title}" to it }
    MeshBackground {
        Column(Modifier.fillMaxSize().imePadding()) {
            TopBar("یخچال من", onBack, subtitle = if (vm.pantry.isEmpty()) "مواد آشپزخانه را ثبت کنید" else "${vm.pantry.size.fa()} ماده • نمک، فلفل، زردچوبه و روغن همیشه موجود فرض می‌شوند") {
                if (vm.pantry.isNotEmpty()) CircleIconButton(Icons.Rounded.DeleteSweep, { confirmClear = true }, size = 40.dp, tint = Palette.Coral)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
                // ورودی صوتی/متنی
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.RecordVoiceOver, Palette.SpiceBrush, size = 38.dp, tint = Palette.OnSpice)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("چی تو یخچال و خونه داری؟", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                            Text("بگو یا بنویس؛ با «،» یا «و» جدا کن", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    VoiceTextField(text, { text = it }, "مواد موجود", "مثلاً: دو تا گوجه، مرغ، برنج، کمی پنیر و سیب‌زمینی", vm::newDictation, minLines = 2)
                    Spacer(Modifier.height(10.dp))
                    GoldButton("افزودن به یخچال", { if (vm.addFromText(text) >= 0) text = "" }, Modifier.fillMaxWidth(), icon = Icons.Rounded.AddCircle, enabled = text.isNotBlank(),
                        brush = Palette.SpiceBrush, contentColor = Palette.OnSpice)
                }
                Spacer(Modifier.height(12.dp))
                // عکس از یخچال
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.CameraAlt, Palette.TealBrush, size = 38.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("عکس از یخچال یا کابینت", style = MaterialTheme.typography.titleSmall, color = Palette.Text)
                            Text("هوش مصنوعی مواد را خودش تشخیص می‌دهد", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassButton("دوربین", camera, Modifier.weight(1f), Icons.Rounded.PhotoCamera, Palette.Teal)
                        GlassButton("گالری", gallery, Modifier.weight(1f), Icons.Rounded.PhotoLibrary, Palette.Teal)
                    }
                    if (vm.fridgeBusy) {
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(22.dp), color = Palette.Teal, strokeWidth = 2.5.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("در حال شناسایی مواد داخل عکس…", style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
                        }
                    }
                    if (vm.fridgeFound.isNotEmpty()) FridgeResult(vm)
                }

                Spacer(Modifier.height(18.dp))
                SectionLabel("انتخاب سریع")
                Spacer(Modifier.height(6.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    tabs.forEachIndexed { i, (t, _) -> SelectChip(t, tab == i, { tab = i }, color = Palette.Gold) }
                }
                Spacer(Modifier.height(10.dp))
                val cat = tabs[tab].second
                val list = if (cat == null) Ingredients.popular.mapNotNull { Ingredients.byId(it) } else Ingredients.all.filter { it.cat == cat && !it.staple }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    list.forEach { ing ->
                        val on = vm.inPantry(ing.id)
                        IngChip("${ing.emoji} ${ing.fa}", on) { vm.toggleKnown(ing) }
                    }
                }

                Spacer(Modifier.height(20.dp))
                SectionLabel("موجودی آشپزخانه (${vm.pantry.size.fa()})")
                Spacer(Modifier.height(8.dp))
                if (vm.pantry.isEmpty()) {
                    EmptyState(Icons.Rounded.Kitchen, "یخچال خالی است!", "مواد را با صدا یا متن بگویید، عکس بگیرید یا از انتخاب سریع بزنید.")
                } else {
                    vm.pantry.groupBy { it.cat }.toSortedMap(compareBy { it.ordinal }).forEach { (c, items) ->
                        Text("${c.emoji} ${c.title}", style = MaterialTheme.typography.labelLarge, color = Palette.TextMuted, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
                        items.sortedBy { it.daysLeft() }.forEach { p -> PantryRow(vm, p) }
                    }
                }
            }
            BottomBarContainer {
                GoldButton(if (vm.pantry.isEmpty()) "بدون ثبت مواد، پیشنهاد بده" else "چی بپزم؟ با ${vm.pantry.size.fa()} ماده", onSuggest, Modifier.fillMaxWidth(), icon = Icons.Rounded.AutoAwesome)
            }
        }
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("خالی کردن یخچال؟") },
        text = { Text("همه مواد ثبت‌شده حذف می‌شوند.") },
        confirmButton = { TextButton({ vm.clearPantry(); confirmClear = false }) { Text("حذف همه", color = Palette.Coral) } },
        dismissButton = { TextButton({ confirmClear = false }) { Text("انصراف") } },
        containerColor = Palette.Surface,
    )
}

@Composable
fun IngChip(text: String, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(14.dp))
            .background(if (on) Palette.SpiceTint else Palette.Glass)
            .border(1.dp, if (on) Palette.Spice else Palette.GlassBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(horizontal = 11.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = if (on) Palette.Spice else Palette.Text, fontSize = 13.sp)
        if (on) { Spacer(Modifier.width(4.dp)); Icon(Icons.Rounded.Check, null, tint = Palette.Spice, modifier = Modifier.size(15.dp)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FridgeResult(vm: MainViewModel) {
    var sel by remember(vm.fridgeFound) { mutableStateOf(vm.fridgeFound.toSet()) }
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        vm.fridgeImage?.let { PhotoThumb(it, Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)), 300); Spacer(Modifier.width(10.dp)) }
        Column(Modifier.weight(1f)) {
            Text("${vm.fridgeFound.size.fa()} ماده پیدا شد 🎉", style = MaterialTheme.typography.titleSmall, color = Palette.Text)
            Text("موارد نادرست را از انتخاب خارج کنید", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
        }
    }
    if (vm.fridgeNote.isNotBlank()) Text(vm.fridgeNote, style = MaterialTheme.typography.bodySmall, color = Palette.Gold, modifier = Modifier.padding(top = 6.dp))
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        vm.fridgeFound.forEach { n ->
            val e = Ingredients.find(n)?.emoji ?: "🛒"
            IngChip("$e $n", n in sel) { sel = if (n in sel) sel - n else sel + n }
        }
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GoldButton("افزودن ${sel.size.fa()} قلم", { vm.acceptFridge(vm.fridgeFound.filter { it in sel }) }, Modifier.weight(1f), enabled = sel.isNotEmpty(), icon = Icons.Rounded.Done)
        GlassButton("لغو", { vm.fridgeFound = emptyList(); vm.fridgeImage = null }, tint = Palette.TextMuted)
    }
}

@Composable
private fun PantryRow(vm: MainViewModel, p: PantryItem) {
    var menu by remember { mutableStateOf(false) }
    val exp = expiryLabel(p)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(16.dp)).background(Palette.Glass)
            .border(1.dp, if (exp != null && p.daysLeft() <= 1) exp.second.copy(alpha = 0.6f) else Palette.GlassBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(p.emoji, fontSize = 22.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, style = MaterialTheme.typography.titleSmall, color = Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            exp?.let { (t, c) -> Text(t, style = MaterialTheme.typography.labelSmall, color = c) }
        }
        Box {
            Row(
                Modifier.clip(RoundedCornerShape(12.dp)).background(Palette.GlassStrong).clickable { menu = true }.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.EventAvailable, null, tint = Palette.TextMuted, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("انقضا", style = MaterialTheme.typography.labelSmall, color = Palette.TextMuted)
            }
            DropdownMenu(menu, { menu = false }, containerColor = Palette.Surface) {
                listOf(1 to "۱ روز دیگر", 2 to "۲ روز دیگر", 3 to "۳ روز دیگر", 7 to "یک هفته", 14 to "دو هفته", 30 to "یک ماه", 0 to "بدون تاریخ").forEach { (d, t) ->
                    DropdownMenuItem({ Text(t) }, { menu = false; vm.setExpiry(p, d) })
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        Box(Modifier.size(32.dp).clip(CircleShape).clickable { vm.removePantry(p) }, contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Close, "حذف", tint = Palette.TextFaint, modifier = Modifier.size(18.dp))
        }
    }
}
