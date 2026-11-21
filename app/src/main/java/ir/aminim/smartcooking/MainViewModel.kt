package ir.aminim.smartcooking

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ir.aminim.smartcooking.ai.AiClient
import ir.aminim.smartcooking.ai.CookAi
import ir.aminim.smartcooking.ai.Msg
import ir.aminim.smartcooking.data.ChatMsg
import ir.aminim.smartcooking.data.Images
import ir.aminim.smartcooking.data.MealPlan
import ir.aminim.smartcooking.data.Meals
import ir.aminim.smartcooking.data.PantryItem
import ir.aminim.smartcooking.data.Recipe
import ir.aminim.smartcooking.data.ShopItem
import ir.aminim.smartcooking.data.Storage
import ir.aminim.smartcooking.engine.Matcher
import ir.aminim.smartcooking.engine.Prefs
import ir.aminim.smartcooking.knowledge.IngCat
import ir.aminim.smartcooking.knowledge.Ingredient
import ir.aminim.smartcooking.knowledge.Ingredients
import ir.aminim.smartcooking.knowledge.RecipeDb
import ir.aminim.smartcooking.reminders.Reminders
import ir.aminim.smartcooking.settings.AppSettings
import ir.aminim.smartcooking.settings.SpeechEngine
import ir.aminim.smartcooking.speech.Dictation
import ir.aminim.smartcooking.speech.GoogleDictation
import ir.aminim.smartcooking.speech.VoskDictation
import ir.aminim.smartcooking.speech.VoskModels
import ir.aminim.smartcooking.util.fa
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx get() = getApplication<Application>()
    val settings = AppSettings(app)
    private val storage = Storage(app)
    val ai = AiClient(app)

    var toast by mutableStateOf<String?>(null)

    val pantry = mutableStateListOf<PantryItem>()
    val shopping = mutableStateListOf<ShopItem>()
    val favorites = mutableStateListOf<Recipe>()
    val history = mutableStateListOf<Recipe>()
    var plan by mutableStateOf<MealPlan?>(null)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val p = storage.loadPantry(); val s = storage.loadShopping(); val f = storage.loadFavorites(); val h = storage.loadHistory(); val pl = storage.loadPlan()
            withContext(Dispatchers.Main) { pantry.addAll(p); shopping.addAll(s); favorites.addAll(f); history.addAll(h); plan = pl }
        }
        Reminders.schedule(app)
    }

    private fun persist(block: suspend () -> Unit) { viewModelScope.launch(Dispatchers.IO) { block() } }
    private fun savePantry() { val s = pantry.toList(); persist { storage.savePantry(s) } }
    private fun saveShopping() { val s = shopping.toList(); persist { storage.saveShopping(s) } }
    private fun saveFavorites() { val s = favorites.toList(); persist { storage.saveFavorites(s) } }
    private fun saveHistory() { val s = history.toList(); persist { storage.saveHistory(s) } }

    /* ═══════════════  یخچال من  ═══════════════ */
    val pantryIds: Set<String> get() = pantry.mapNotNull { it.ingId.takeIf { id -> id.isNotBlank() } }.toSet()
    fun inPantry(id: String) = pantry.any { it.ingId == id }

    private fun newId() = "${System.currentTimeMillis()}_${(1000..9999).random()}"

    private fun defaultExpiry(i: Ingredient?): Long = if (i != null && i.shelf > 0) System.currentTimeMillis() + i.shelf * 86_400_000L else 0L

    fun addKnown(i: Ingredient, silent: Boolean = false): Boolean {
        if (inPantry(i.id)) return false
        pantry.add(0, PantryItem(newId(), i.fa, i.id, i.cat, expiry = defaultExpiry(i)))
        savePantry()
        if (!silent) toast = "${i.emoji} ${i.fa} به یخچال اضافه شد"
        return true
    }

    fun toggleKnown(i: Ingredient) {
        if (inPantry(i.id)) { pantry.removeAll { it.ingId == i.id }; savePantry() } else addKnown(i, silent = true)
    }

    fun addCustom(name: String): Boolean {
        val n = name.trim()
        if (n.isBlank() || pantry.any { Ingredients.norm(it.name) == Ingredients.norm(n) }) return false
        Ingredients.find(n)?.let { return addKnown(it, silent = true) }
        pantry.add(0, PantryItem(newId(), n, "", IngCat.OTHER))
        savePantry(); return true
    }

    /** افزودن مواد از متن آزاد یا گفتار؛ خروجی: تعداد اقلام جدید */
    fun addFromText(text: String): Int {
        val (known, unknown) = Ingredients.parse(text)
        var n = 0
        known.forEach { if (addKnown(it, silent = true)) n++ }
        unknown.forEach { if (addCustom(it)) n++ }
        toast = if (n > 0) "${n.fa()} قلم به یخچال اضافه شد ✅" else if (known.isNotEmpty()) "این مواد از قبل در یخچال هستند" else "ماده‌ای شناسایی نشد؛ با «،» جدا کنید"
        return n
    }

    fun removePantry(p: PantryItem) { pantry.removeAll { it.id == p.id }; savePantry() }
    fun clearPantry() { pantry.clear(); savePantry() }
    fun setExpiry(p: PantryItem, days: Int) {
        val i = pantry.indexOfFirst { it.id == p.id }
        if (i >= 0) { pantry[i] = p.copy(expiry = if (days <= 0) 0 else System.currentTimeMillis() + days * 86_400_000L - 3_600_000L); savePantry() }
    }

    val expiring: List<PantryItem> get() = pantry.filter { it.daysLeft() <= 2 }.sortedBy { it.daysLeft() }

    /** تشخیص مواد از عکس یخچال */
    var fridgeBusy by mutableStateOf(false)
    var fridgeFound by mutableStateOf<List<String>>(emptyList())
    var fridgeNote by mutableStateOf("")
    var fridgeImage by mutableStateOf<String?>(null)

    fun scanFridge(uri: Uri) {
        if (!ai.available(false)) { toast = if (!ai.online()) "تشخیص از عکس به اینترنت نیاز دارد" else "موتور آفلاین عکس را تحلیل نمی‌کند؛ مواد را بنویسید یا بگویید"; return }
        fridgeBusy = true; fridgeFound = emptyList(); fridgeNote = ""
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val path = Images.import(ctx, uri) ?: throw IllegalStateException("خواندن عکس ممکن نشد")
                    withContext(Dispatchers.Main) { fridgeImage = path }
                    val b64 = Images.base64(path) ?: throw IllegalStateException("خواندن عکس ممکن نشد")
                    CookAi.parseFridge(ai.chat(listOf(Msg("system", CookAi.FRIDGE_SYSTEM), Msg("user", "مواد داخل این عکس را فهرست کن.", listOf(b64))), json = true, maxTokens = 1500, temperature = 0.2))
                }
            }
            fridgeBusy = false
            r.onSuccess { (items, note) -> fridgeFound = items; fridgeNote = note; if (items.isEmpty()) toast = "ماده‌ای در عکس پیدا نشد؛ عکس واضح‌تری بگیرید" }
                .onFailure { toast = "تحلیل عکس ناموفق بود: ${it.message}" }
        }
    }

    fun acceptFridge(items: List<String>) {
        var n = 0
        items.forEach { name -> Ingredients.find(name)?.let { if (addKnown(it, silent = true)) n++ } ?: run { if (addCustom(name)) n++ } }
        fridgeFound = emptyList(); fridgeImage = null
        toast = "${n.fa()} قلم به یخچال اضافه شد ✅"
    }

    /* ═══════════════  پیشنهاد غذا  ═══════════════ */
    var prefs by mutableStateOf(Prefs(meal = Meals.forHour(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)), people = settings.family, diet = settings.diets))
    var suggestBusy by mutableStateOf(false)
    var suggestStage by mutableStateOf("")
    var suggestError by mutableStateOf<String?>(null)
    var suggestIntro by mutableStateOf("")
    var suggestEngine by mutableStateOf("")
    val suggestions = mutableStateListOf<Recipe>()
    val offlineSuggestions = mutableStateListOf<Recipe>()

    val aiEngineTitle: String get() = ai.activeTitle(true)

    private fun pantryNames(): List<String> = pantry.map { it.name }

    fun suggest() {
        val p = prefs
        suggestBusy = true; suggestError = null; suggestions.clear(); suggestIntro = ""
        offlineSuggestions.clear(); offlineSuggestions.addAll(Matcher.suggest(pantry.toList(), p).map { scaleServ(it, p.people) })
        if (!ai.available(true)) {
            suggestBusy = false
            suggestEngine = "کتاب آشپزی (آفلاین)"
            if (offlineSuggestions.isEmpty()) suggestError = if (!ai.online()) "اینترنت در دسترس نیست و در کتاب آشپزی غذایی با این مواد و فیلترها پیدا نشد. فیلترها را کمتر کنید یا مواد بیشتری اضافه کنید." else "غذایی با این فیلترها در کتاب آشپزی پیدا نشد."
            return
        }
        val names = pantryNames(); val exp = expiring.map { it.name }
        viewModelScope.launch {
            suggestStage = "سرآشپز در حال فکر کردن با ${ai.activeTitle(true)}…"
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val raw = ai.chat(
                        listOf(Msg("system", CookAi.SUGGEST_SYSTEM), Msg("user", CookAi.suggestUser(names, exp, p, settings.family, settings.avoid, 0))),
                        json = true, maxTokens = 7000, temperature = 0.7, allowInternal = true,
                    )
                    CookAi.parseSuggestions(raw, ai.lastTitle)
                }
            }
            suggestBusy = false
            r.onSuccess { (intro, list) ->
                suggestIntro = intro; suggestEngine = ai.lastTitle
                suggestions.addAll(list.map { if (it.match < 0) Matcher.evaluate(it, pantry.toList()) else it })
            }.onFailure {
                suggestEngine = "کتاب آشپزی (آفلاین)"
                if (offlineSuggestions.isEmpty()) suggestError = "هوش مصنوعی پاسخ نداد (${it.message}). اتصال را بررسی و دوباره امتحان کنید."
                else toast = "هوش مصنوعی پاسخ نداد؛ پیشنهادهای کتاب آشپزی نمایش داده شد"
            }
        }
    }

    private fun scaleServ(r: Recipe, people: Int) = r.copy(servings = r.servings) // مقیاس در صفحه دستور انجام می‌شود

    /* ═══════════════  دستور پخت  ═══════════════ */
    var recipe by mutableStateOf<Recipe?>(null)
    var recipeBusy by mutableStateOf(false)
    var recipeError by mutableStateOf<String?>(null)
    var servings by mutableStateOf(4)

    fun openRecipe(r: Recipe) {
        recipe = if (r.local && r.match < 0) Matcher.evaluate(r, pantry.toList()) else r
        recipeError = null; recipeBusy = false
        servings = prefs.people.takeIf { it > 0 } ?: r.servings
        if (!r.hasDetails) fetchDetails(r) else addHistory(r)
    }

    private fun fetchDetails(r: Recipe) {
        RecipeDb.matchName(r.name)?.takeIf { !ai.available(false) }?.let { l -> recipe = Matcher.evaluate(l, pantry.toList()); addHistory(l); return }
        recipeBusy = true
        viewModelScope.launch {
            val out = withContext(Dispatchers.IO) {
                runCatching {
                    CookAi.parseRecipe(ai.chat(listOf(Msg("system", CookAi.RECIPE_SYSTEM), Msg("user", CookAi.recipeUser(r.name, servings, pantryNames(), settings.avoid, r.desc))), json = true, maxTokens = 3500, temperature = 0.4), ai.lastTitle)
                }
            }
            recipeBusy = false
            out.onSuccess { full -> val x = Matcher.evaluate(full.copy(id = r.id, emoji = r.emoji.takeIf { it != "🍽️" } ?: full.emoji), pantry.toList()); recipe = x; addHistory(x) }
                .onFailure { recipeError = "دستور کامل دریافت نشد: ${it.message}" }
        }
    }

    fun retryRecipe() { recipe?.let { fetchDetails(it) } }

    /** دستور پخت هر غذایی با نام */
    fun askRecipe(name: String, hint: String = "") {
        val n = name.trim()
        if (n.isBlank()) return
        val local = RecipeDb.matchName(n)
        if (local != null && hint.isBlank() && Ingredients.norm(local.name) == Ingredients.norm(n)) { openRecipe(local); return }
        openRecipe(Recipe(id = "ask_${System.currentTimeMillis()}", name = n, desc = hint, servings = settings.family))
    }

    /** تشخیص غذا از عکس و دستور پخت آن */
    fun dishPhoto(uri: Uri) {
        recipe = Recipe(id = "dish_${System.currentTimeMillis()}", name = "در حال شناسایی غذا…", emoji = "📸")
        recipeError = null
        if (!ai.available(false)) { recipeError = if (!ai.online()) "تشخیص غذا از عکس به اینترنت نیاز دارد." else "موتور آفلاین عکس را تحلیل نمی‌کند؛ در تنظیمات، موتور آنلاین را انتخاب کنید."; return }
        recipeBusy = true
        viewModelScope.launch {
            val out = withContext(Dispatchers.IO) {
                runCatching {
                    val path = Images.import(ctx, uri) ?: throw IllegalStateException("خواندن عکس ممکن نشد")
                    withContext(Dispatchers.Main) { recipe = recipe?.copy(image = path) }
                    val b64 = Images.base64(path) ?: throw IllegalStateException("خواندن عکس ممکن نشد")
                    CookAi.parseRecipe(ai.chat(listOf(Msg("system", CookAi.DISH_PHOTO_SYSTEM), Msg("user", "این چه غذایی است؟ دستور پختش را برای ${settings.family} نفر بنویس.", listOf(b64))), json = true, maxTokens = 3500, temperature = 0.3), ai.lastTitle).copy(image = path)
                }
            }
            recipeBusy = false
            out.onSuccess { x -> val e = Matcher.evaluate(x, pantry.toList()); recipe = e; servings = e.servings; if (x.name != "نامشخص") addHistory(e) }
                .onFailure { recipeError = "شناسایی ناموفق بود: ${it.message}" }
        }
    }

    private fun addHistory(r: Recipe) {
        if (!r.hasDetails) return
        history.removeAll { it.name == r.name }
        history.add(0, r.copy(time = System.currentTimeMillis()))
        while (history.size > 40) history.removeAt(history.lastIndex)
        saveHistory()
    }

    fun isFav(r: Recipe) = favorites.any { it.name == r.name }
    fun toggleFav(r: Recipe) {
        if (isFav(r)) { favorites.removeAll { it.name == r.name }; toast = "از علاقه‌مندی‌ها حذف شد" }
        else { favorites.add(0, r.copy(time = System.currentTimeMillis())); toast = "به علاقه‌مندی‌ها اضافه شد ❤️" }
        saveFavorites()
    }
    fun clearHistory() { history.clear(); saveHistory() }

    /** پس از پخت: شمارش و حذف اختیاری مواد مصرف‌شده */
    fun markCooked(r: Recipe, removeUsed: Boolean) {
        settings.cookedCount = settings.cookedCount + 1
        if (removeUsed) {
            val used = r.ingredients.mapNotNull { it.ingId.takeIf { id -> id.isNotBlank() } }.toSet() - Ingredients.staples
            val perishable = used.filter { (Ingredients.byId(it)?.shelf ?: 0) in 1..10 }.toSet()
            val n = pantry.count { it.ingId in perishable }
            pantry.removeAll { it.ingId in perishable }; savePantry()
            if (n > 0) toast = "${n.fa()} ماده مصرف‌شده از یخچال برداشته شد"
        }
    }

    /* ═══════════════  لیست خرید  ═══════════════ */
    fun addShop(names: List<String>, note: String = ""): Int {
        var n = 0
        names.map { it.trim() }.filter { it.isNotBlank() }.forEach { name ->
            if (shopping.none { Ingredients.norm(it.name) == Ingredients.norm(name) && !it.done }) { shopping.add(0, ShopItem(newId(), name, note = note)); n++ }
        }
        saveShopping()
        toast = if (n > 0) "${n.fa()} قلم به لیست خرید اضافه شد 🛒" else "این اقلام از قبل در لیست خرید هستند"
        return n
    }
    fun addShopText(text: String) {
        val parts = text.split('،', ',', '\n').flatMap { it.split(" و ") }.map { it.trim() }.filter { it.isNotBlank() }
        if (parts.isNotEmpty()) addShop(parts)
    }
    fun toggleShop(s: ShopItem) { val i = shopping.indexOfFirst { it.id == s.id }; if (i >= 0) { shopping[i] = s.copy(done = !s.done); saveShopping() } }
    fun removeShop(s: ShopItem) { shopping.removeAll { it.id == s.id }; saveShopping() }
    fun clearDoneShop() { shopping.removeAll { it.done }; saveShopping() }
    /** خریدها به یخچال منتقل شوند */
    fun doneToPantry() {
        val done = shopping.filter { it.done }
        var n = 0
        done.forEach { s -> val name = s.name.substringBefore('(').trim(); Ingredients.find(name)?.let { if (addKnown(it, true)) n++ } ?: run { if (addCustom(name)) n++ } }
        shopping.removeAll { it.done }; saveShopping()
        toast = "${n.fa()} قلم به یخچال منتقل شد 🧊"
    }
    val shopLeft: Int get() = shopping.count { !it.done }

    /* ═══════════════  سرآشپز (گفتگو)  ═══════════════ */
    val chat = mutableStateListOf<ChatMsg>()
    var chatBusy by mutableStateOf(false)
    var chatAbout by mutableStateOf<Recipe?>(null)
    var chatImage by mutableStateOf<String?>(null)
    var chatDraft by mutableStateOf("")

    fun startChat(about: Recipe?) {
        if (chatAbout?.name != about?.name || about == null) chat.clear()
        chatAbout = about; chatImage = null
    }

    fun attachChatImage(uri: Uri) {
        viewModelScope.launch { withContext(Dispatchers.IO) { Images.import(ctx, uri) }?.let { chatImage = it } ?: run { toast = "خواندن عکس ممکن نشد" } }
    }

    fun send(text: String) {
        val t = text.trim()
        val img = chatImage
        if (t.isBlank() && img == null) return
        chat += ChatMsg("user", t.ifBlank { "این عکس را بررسی کن." }, img)
        chatImage = null; chatBusy = true
        val history = chat.toList()
        val about = chatAbout
        val names = pantryNames()
        viewModelScope.launch {
            val out = withContext(Dispatchers.IO) {
                runCatching {
                    val msgs = mutableListOf(Msg("system", CookAi.CHAT_SYSTEM + "\n\n" + CookAi.chatContext(names, about, settings.avoid)))
                    val last = history.takeLast(12)
                    last.forEachIndexed { i, m ->
                        val imgs = if (i == last.lastIndex && m.image != null) listOfNotNull(Images.base64(m.image)) else emptyList()
                        if (!m.error) msgs += Msg(if (m.role == "user") "user" else "assistant", m.text, imgs)
                    }
                    ai.chat(msgs, json = false, maxTokens = 2000, temperature = 0.5)
                }
            }
            chatBusy = false
            out.onSuccess { chat += ChatMsg("assistant", it.trim()) }
                .onFailure { chat += ChatMsg("assistant", "پاسخ دریافت نشد: ${it.message}\nاتصال اینترنت یا موتور هوش مصنوعی را در تنظیمات بررسی کنید.", error = true) }
        }
    }

    /* ═══════════════  برنامه غذایی هفتگی  ═══════════════ */
    var planBusy by mutableStateOf(false)

    fun makePlan(budget: String, note: String) {
        val p = prefs.copy(note = note)
        planBusy = true
        val names = pantryNames()
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) {
                if (!ai.available(false)) Result.success(CookAi.offlinePlan(System.currentTimeMillis()))
                else runCatching { CookAi.parsePlan(ai.chat(listOf(Msg("system", CookAi.PLAN_SYSTEM), Msg("user", CookAi.planUser(names, p, settings.family, settings.avoid, budget))), json = true, maxTokens = 3500, temperature = 0.7), ai.lastTitle) }
                    .recoverCatching { CookAi.offlinePlan(System.currentTimeMillis()) }
            }
            planBusy = false
            r.onSuccess { plan = it; val x = it; persist { storage.savePlan(x) } }.onFailure { toast = "ساخت برنامه ناموفق بود: ${it.message}" }
        }
    }

    fun clearPlan() { plan = null; persist { storage.savePlan(null) } }

    /* ═══════════════  گردونه غذا  ═══════════════ */
    fun wheelOptions(fromPantry: Boolean, meal: String): List<Recipe> {
        val base = if (fromPantry && pantry.isNotEmpty()) Matcher.suggest(pantry.toList(), Prefs(meal = meal), 8) else emptyList()
        val fill = RecipeDb.all.filter { meal.isBlank() || meal in it.meals }.shuffled()
        return (base + fill).distinctBy { it.id }.take(8)
    }

    /* ═══════════════  گفتار  ═══════════════ */
    fun newDictation(): Dictation =
        if (settings.speechEngine == SpeechEngine.OFFLINE && VoskModels.ready(ctx)) VoskDictation(ctx) else GoogleDictation(ctx)

    val asrProgress = mutableStateMapOf<String, Float>()
    fun installAsr(m: VoskModels.AsrModel) {
        if (asrProgress.containsKey(m.id)) return
        asrProgress[m.id] = 0f
        viewModelScope.launch {
            val r = runCatching { VoskModels.install(ctx, m) { p -> viewModelScope.launch(Dispatchers.Main) { asrProgress[m.id] = p } } }
            asrProgress.remove(m.id)
            r.onSuccess { settings.speechEngine = SpeechEngine.OFFLINE; toast = "مدل گفتار «${m.title}» نصب شد" }
                .onFailure { toast = "دانلود ناموفق: ${it.message}" }
        }
    }

    /* ═══════════════  آزمون اتصال  ═══════════════ */
    var testing by mutableStateOf(false)
    var testResult by mutableStateOf<List<Pair<String, Boolean>>>(emptyList())
    fun testAi() {
        testing = true; testResult = emptyList()
        viewModelScope.launch {
            testResult = withContext(Dispatchers.IO) { runCatching { ai.diagnose() }.getOrDefault(emptyList()) }
            testing = false
            if (testResult.isEmpty()) toast = "هیچ مسیر ابری فعال نیست یا اینترنت قطع است"
        }
    }

    /* ═══════════════  داده نمایشی برای تصاویر فروشگاه  ═══════════════ */
    fun seedDemo() {
        if (pantry.isNotEmpty()) return
        val now = System.currentTimeMillis()
        listOf("chicken", "egg", "tomato", "onion", "potato", "eggplant", "rice", "yogurt", "cheese", "bell_pepper", "mushroom", "garlic", "lemon", "kashk", "pasta", "tomato_paste")
            .mapNotNull { Ingredients.byId(it) }.forEachIndexed { i, ing ->
                pantry += PantryItem("d$i", ing.fa, ing.id, ing.cat, now - i * 3_600_000L, if (ing.shelf > 0) now + (if (i < 3) i else ing.shelf) * 86_400_000L else 0)
            }
        shopping += listOf(ShopItem("s1", "زعفران"), ShopItem("s2", "سبزی قورمه"), ShopItem("s3", "لیمو عمانی"), ShopItem("s4", "نان سنگک", done = true))
        val sug = Matcher.suggest(pantry.toList(), Prefs(meal = "lunch"))
        suggestions.addAll(listOf(
            Recipe("demo1", "جوجه کباب تابه‌ای با گوجه کبابی", "🍗", "سینه مرغ زعفرانی با لیمو و ماست؛ آبدار و خوش‌رنگ، بدون منقل.", "ایرانی", listOf("lunch"), 40, 1, 4, 360,
                RecipeDb.byId("joojeh")!!.ingredients, RecipeDb.byId("joojeh")!!.steps, RecipeDb.byId("joojeh")!!.tips, listOf("بدون گلوتن"), 36, 6, 16, "هوش مصنوعی قوی داخلی", match = 95, missing = emptyList(), why = "مرغ، ماست و لیمو را دارید و مرغ تا فردا باید مصرف شود"),
            Recipe("demo2", "میرزاقاسمی", "🍆", "بادمجان کبابی با سیر و گوجه و تخم‌مرغ؛ اصالت گیلان.", "ایرانی", listOf("lunch"), 50, 1, 4, 280,
                RecipeDb.byId("mirza")!!.ingredients, RecipeDb.byId("mirza")!!.steps, emptyList(), listOf("گیاهی"), 10, 14, 20, "هوش مصنوعی قوی داخلی", match = 100, missing = emptyList(), why = "همه مواد را دارید؛ گیاهی و سبک"),
            Recipe("demo3", "کشک بادمجان", "🥣", "بادمجان له‌شده با کشک، پیاز داغ و نعناع داغ.", "ایرانی", listOf("lunch"), 60, 1, 4, 360, emptyList(), emptyList(), emptyList(), listOf("گیاهی"), 10, 16, 28, "هوش مصنوعی قوی داخلی", match = 85, missing = listOf("نعناع خشک"), why = "بادمجان و کشک دارید"),
            Recipe("demo4", "پاستا مرغ و قارچ خامه‌ای", "🍝", "پاستای خامه‌ای با مرغ و قارچ و پنیر؛ رستورانی در خانه.", "فرنگی", listOf("lunch"), 35, 1, 4, 640, emptyList(), emptyList(), emptyList(), emptyList(), 34, 62, 28, "هوش مصنوعی قوی داخلی", match = 80, missing = listOf("خامه"), why = "مرغ، قارچ، پاستا و پنیر دارید"),
        ))
        offlineSuggestions.addAll(sug)
        suggestIntro = "با مرغ و بادمجان و گوجه‌ای که داری، این‌ها امروز عالی می‌شوند 😋"
        suggestEngine = "هوش مصنوعی قوی داخلی"
        favorites += listOfNotNull(RecipeDb.byId("ghormeh"), RecipeDb.byId("tahchin"), RecipeDb.byId("fesenjan"))
        history += listOfNotNull(RecipeDb.byId("kuku_sabzi"), RecipeDb.byId("omelet"), RecipeDb.byId("adasi"))
        plan = CookAi.offlinePlan(42L)
        chat += ChatMsg("user", "برای ته‌دیگ سیب‌زمینی ترد چکار کنم؟")
        chat += ChatMsg("assistant", "**رمز ته‌دیگ سیب‌زمینی ترد** 🥔\n- سیب‌زمینی را حلقه‌ای **نیم سانتی** و یکدست ببرید.\n- ته قابلمه نچسب ۳ قاشق روغن + ۲ قاشق آب + کمی زردچوبه و زعفران بریزید.\n- حلقه‌ها را بچینید و کمی نمک بپاشید.\n- **۱۰ دقیقه اول حرارت متوسط** تا صدای جلزوولز بیاید، بعد حرارت ملایم و دم‌کنی.\n- برای جدا شدن راحت، قابلمه را ۲ دقیقه روی پارچه خیس بگذارید.")
    }

    companion object { const val MAX_SHOTS = 3 }
}
