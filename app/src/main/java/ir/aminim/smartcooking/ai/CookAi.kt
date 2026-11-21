package ir.aminim.smartcooking.ai

import ir.aminim.smartcooking.data.MealPlan
import ir.aminim.smartcooking.data.Meals
import ir.aminim.smartcooking.data.Recipe
import ir.aminim.smartcooking.engine.Prefs
import ir.aminim.smartcooking.knowledge.Ingredients
import ir.aminim.smartcooking.knowledge.RecipeDb
import ir.aminim.smartcooking.util.fa

/** دستورها و تبدیل‌های هوش مصنوعی آشپز */
object CookAi {

    private const val ROLE = """تو «آشپزی هوشمند» هستی؛ سرآشپز حرفه‌ای و متخصص تغذیه، مسلط به آشپزی اصیل ایرانی (همه استان‌ها) و آشپزی بین‌المللی.
مخاطب تو خانواده ایرانی است: مواد رایج بازار ایران، پیمانه‌ها و واحدهای خانگی (پیمانه، قاشق غذاخوری، قاشق چای‌خوری، عدد، گرم)، و سلیقه ایرانی را در نظر بگیر.
فارسی روان، گرم و دقیق بنویس. دستورها واقعی، قابل اجرا و امتحان‌شده باشند؛ هرگز ماده یا روش غیرواقعی نساز."""

    private const val RECIPE_SCHEMA = """{"name": "نام غذا", "emoji": "یک ایموجی مناسب", "desc": "۱ تا ۲ جمله توصیف اشتهاآور", "cuisine": "ایرانی | فرنگی | فست‌فود | آسیایی", "meals": ["breakfast|lunch|dinner|snack|dessert|side"], "minutes": 45, "difficulty": 1, "servings": 4, "calories": 450, "protein": 25, "carbs": 40, "fat": 18,
 "ingredients": [{"name": "نام ماده", "amount": "مقدار دقیق با واحد خانگی", "optional": false}],
 "steps": [{"text": "توضیح کامل مرحله", "minutes": 10}],
 "tips": ["نکته سرآشپز"], "tags": ["گیاهی | رژیمی | بدون گلوتن | مناسب کودک | اقتصادی"], "match": 90, "missing": ["موادی که کاربر ندارد"], "why": "یک جمله: چرا این غذا برای کاربر مناسب است"}"""

    private fun prefsText(p: Prefs, family: Int, avoid: String): String = buildString {
        if (p.meal.isNotBlank()) append("- وعده: ${Meals.title(p.meal)}\n")
        if (p.maxMinutes > 0) append("- حداکثر زمان پخت: ${p.maxMinutes} دقیقه\n")
        append("- تعداد نفرات: ${if (p.people > 0) p.people else family}\n")
        if (p.diet.isNotEmpty()) append("- رژیم/ویژگی: ${p.diet.joinToString("، ")}\n")
        if (p.cuisine.isNotBlank()) append("- سبک آشپزی: ${p.cuisine}\n")
        append(if (p.strict) "- فقط با مواد موجود (خرید ممنوع؛ جز نمک، فلفل، زردچوبه، روغن و آب)\n" else "- اگر لازم بود حداکثر ۱ تا ۲ قلم خرید ساده مجاز است\n")
        if (avoid.isNotBlank()) append("- حساسیت یا موادی که نمی‌خورد: $avoid (هرگز استفاده نکن)\n")
        if (p.note.isNotBlank()) append("- توضیح کاربر: ${p.note}\n")
    }

    val SUGGEST_SYSTEM = """$ROLE

وظیفه: بر اساس موادی که کاربر در خانه دارد و ترجیحاتش، ۵ غذای متنوع و واقعی پیشنهاد بده و «فقط» یک JSON معتبر برگردان:
{"intro": "یک جمله کوتاه و صمیمی", "recipes": [ $RECIPE_SCHEMA ]}

قواعد:
- بیشترین استفاده را از مواد موجود بکن (به‌خصوص موادی که زودتر خراب می‌شوند). match درصد واقعی پوشش مواد اصلی است.
- تنوع داشته باش: ترکیبی از غذای ایرانی محبوب و چند گزینه خلاقانه؛ تکراری یا خیلی شبیه نباشند.
- نمک، فلفل، زردچوبه، روغن و آب را همیشه موجود فرض کن و در missing نیاور.
- مراحل (steps) کامل، شماره‌نخورده و دقیق باشند؛ برای مراحل زمان‌دار (تفت، جوشیدن، دم کشیدن، فر) minutes را بنویس، وگرنه 0.
- مقادیر برای تعداد نفرات خواسته‌شده باشد. calories/protein/carbs/fat برای هر نفر.
- فقط JSON؛ بدون متن اضافه."""

    fun suggestUser(pantry: List<String>, expiring: List<String>, p: Prefs, family: Int, avoid: String, imageCount: Int): String = buildString {
        if (imageCount > 0) append("$imageCount عکس از یخچال/مواد پیوست است؛ مواد داخل عکس را هم شناسایی و استفاده کن.\n")
        append("مواد موجود: ")
        append(pantry.joinToString("، ").ifBlank { "کاربر چیزی ثبت نکرده؛ غذاهای ساده با مواد رایج خانه پیشنهاد بده." })
        append("\n")
        if (expiring.isNotEmpty()) append("مواد رو به خراب شدن (اولویت مصرف): ${expiring.joinToString("، ")}\n")
        append("ترجیحات:\n")
        append(prefsText(p, family, avoid))
    }

    val RECIPE_SYSTEM = """$ROLE

برای غذایی که کاربر می‌خواهد یک دستور پخت کامل، دقیق و اصیل بنویس و «فقط» این JSON را برگردان:
$RECIPE_SCHEMA
- مراحل کامل و دقیق؛ برای مراحل زمان‌دار minutes را بنویس.
- اگر کاربر مواد موجودش را گفته، match و missing را بر اساس آن محاسبه کن؛ وگرنه match=-1 و missing خالی.
- اگر نام غذا نامعتبر است، نزدیک‌ترین غذای واقعی را بنویس.
- فقط JSON."""

    fun recipeUser(name: String, servings: Int, pantry: List<String>, avoid: String, hint: String = ""): String = buildString {
        append("غذا: $name\nتعداد نفرات: $servings\n")
        if (hint.isNotBlank()) append("توضیح: $hint\n")
        if (pantry.isNotEmpty()) append("مواد موجود کاربر: ${pantry.joinToString("، ")}\n")
        if (avoid.isNotBlank()) append("حساسیت/پرهیز: $avoid\n")
    }

    val DISH_PHOTO_SYSTEM = """$ROLE

کاربر عکس یک غذا را فرستاده است. غذا را با دقت شناسایی کن و دستور پخت کامل آن را بنویس؛ «فقط» این JSON:
$RECIPE_SCHEMA
در desc بنویس از روی چه نشانه‌هایی غذا را شناختی. اگر در عکس غذا نیست، name را "نامشخص" بگذار و در desc توضیح بده."""

    val FRIDGE_SYSTEM = """$ROLE

کاربر عکس یخچال، کابینت یا مواد غذایی‌اش را فرستاده است. همه مواد خوراکی قابل تشخیص را با دقت فهرست کن (نام رایج فارسی) و «فقط» این JSON را برگردان:
{"items": [{"name": "نام فارسی ماده", "confidence": 0-100}], "note": "اگر چیزی مبهم بود یک جمله"}
- بسته‌بندی‌ها را از روی شکل و برچسب حدس بزن؛ موارد نامطمئن را با confidence پایین بیاور.
- ظروف غذای پخته را با عنوان کلی (مثلاً «غذای پخته در ظرف») بیاور.
- فقط JSON."""

    val CHAT_SYSTEM = """$ROLE

تو «سرآشپز هوشمند» در یک گفتگو هستی. به پرسش‌ها کوتاه، دقیق و کاربردی پاسخ بده.
- از تیترهای کوتاه با ** و فهرست‌های گلوله‌ای (- ) یا شماره‌دار استفاده کن.
- اگر عکس فرستاده شد، اول بگو چه می‌بینی (غذا یا مواد)، بعد پاسخ بده.
- برای دستور پخت، مواد را با مقدار و مراحل را شماره‌دار بنویس.
- درباره ایمنی غذایی (مرغ خام، نگهداری غذا، حساسیت‌ها) دقیق باش.
- خارج از آشپزی، تغذیه، خرید و نگهداری مواد غذایی پاسخ نده و مؤدبانه به موضوع برگرد."""

    fun chatContext(pantry: List<String>, r: Recipe?, avoid: String): String = buildString {
        if (pantry.isNotEmpty()) append("مواد موجود در خانه کاربر: ${pantry.joinToString("، ")}\n")
        if (avoid.isNotBlank()) append("حساسیت/پرهیز کاربر: $avoid\n")
        if (r != null) {
            append("کاربر درباره این دستور می‌پرسد: ${r.name}\nمواد: ${r.ingredients.joinToString("، ") { "${it.name} ${it.amount}" }}\n")
            append("مراحل: ${r.steps.mapIndexed { i, s -> "${i + 1}) ${s.text}" }.joinToString(" ")}\n")
        }
    }

    val PLAN_SYSTEM = """$ROLE

یک برنامه غذایی ۷ روزه متنوع، متعادل و اقتصادی برای خانواده ایرانی بنویس (از شنبه تا جمعه) و «فقط» این JSON را برگردان:
{"days": [{"day": "شنبه", "breakfast": {"name": "", "emoji": "", "minutes": 10, "calories": 300}, "lunch": {"name": "", "emoji": "", "minutes": 60, "calories": 600}, "dinner": {"name": "", "emoji": "", "minutes": 30, "calories": 450}}], "shopping": ["فهرست خرید هفته با مقدار تقریبی، فقط چیزهایی که کاربر ندارد"], "note": "نکته کوتاه تغذیه‌ای یا مدیریت زمان"}
- غذای تکراری نباشد؛ تعادل پروتئین (مرغ، گوشت، ماهی، حبوبات، تخم‌مرغ) و سبزیجات رعایت شود.
- از مواد موجود کاربر در روزهای اول استفاده کن.
- روزهای شلوغ (وسط هفته) غذای سریع‌تر و جمعه غذای مجلسی‌تر.
- فقط JSON."""

    fun planUser(pantry: List<String>, p: Prefs, family: Int, avoid: String, budget: String): String = buildString {
        append("مواد موجود: ${pantry.joinToString("، ").ifBlank { "ثبت نشده" }}\n")
        append("تعداد اعضای خانواده: $family\n")
        if (p.diet.isNotEmpty()) append("ترجیحات: ${p.diet.joinToString("، ")}\n")
        if (budget.isNotBlank()) append("بودجه: $budget\n")
        if (avoid.isNotBlank()) append("حساسیت/پرهیز: $avoid\n")
        if (p.note.isNotBlank()) append("توضیح: ${p.note}\n")
    }

    /* ───────────── تبدیل پاسخ‌ها ───────────── */

    /** کامل کردن پاسخ هوش مصنوعی با داده داخلی (اگر غذا در کتاب آشپزی هست) */
    private fun enrich(r: Recipe, engine: String): Recipe {
        var x = r.copy(source = engine, time = System.currentTimeMillis())
        if (!x.hasDetails) RecipeDb.matchName(x.name)?.let { l -> x = x.copy(ingredients = x.ingredients.ifEmpty { l.ingredients }, steps = x.steps.ifEmpty { l.steps }, tips = x.tips.ifEmpty { l.tips }) }
        if (x.calories == 0) RecipeDb.matchName(x.name)?.let { l -> x = x.copy(calories = l.calories, protein = l.protein, carbs = l.carbs, fat = l.fat) }
        return x
    }

    fun parseSuggestions(raw: String, engine: String): Pair<String, List<Recipe>> {
        val o = extractJson(raw)
        val arr = o.optJSONArray("recipes") ?: o.optJSONArray("suggestions") ?: o.optJSONArray("foods")
        val list = mutableListOf<Recipe>()
        if (arr != null) for (i in 0 until arr.length()) {
            arr.optJSONObject(i)?.let { j -> Recipe.from(j, "ai_${System.currentTimeMillis()}_$i").takeIf { it.name.isNotBlank() }?.let { list += enrich(it, engine) } }
        }
        if (list.isEmpty()) throw AiException("پیشنهادی دریافت نشد")
        return o.optString("intro").trim() to list
    }

    fun parseRecipe(raw: String, engine: String): Recipe {
        val o = extractJson(raw)
        val j = o.optJSONObject("recipe") ?: o
        val r = Recipe.from(j, "ai_${System.currentTimeMillis()}")
        if (r.name.isBlank()) throw AiException("دستور پخت دریافت نشد")
        return enrich(r, engine)
    }

    fun parseFridge(raw: String): Pair<List<String>, String> {
        val o = extractJson(raw)
        val a = o.optJSONArray("items")
        val out = mutableListOf<String>()
        if (a != null) for (i in 0 until a.length()) {
            val jo = a.optJSONObject(i)
            val n = (jo?.optString("name") ?: a.optString(i)).trim()
            val c = jo?.optInt("confidence", 80) ?: 80
            if (n.isNotBlank() && c >= 35) out += n
        }
        return out.distinct() to o.optString("note").trim()
    }

    fun parsePlan(raw: String, engine: String): MealPlan {
        val p = MealPlan.from(extractJson(raw), engine = engine)
        if (p.days.isEmpty()) throw AiException("برنامه دریافت نشد")
        return p
    }

    /** برنامه هفتگی آفلاین از کتاب آشپزی */
    fun offlinePlan(seed: Long): MealPlan {
        val rnd = java.util.Random(seed)
        fun pick(meal: String, used: MutableSet<String>): ir.aminim.smartcooking.data.PlanMeal {
            val opts = RecipeDb.all.filter { meal in it.meals && it.id !in used }.ifEmpty { RecipeDb.all.filter { meal in it.meals } }
            val r = opts[rnd.nextInt(opts.size)]
            used += r.id
            return ir.aminim.smartcooking.data.PlanMeal(r.name, r.emoji, r.minutes, r.calories, r.id)
        }
        val used = mutableSetOf<String>()
        val days = listOf("شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه").map { d ->
            ir.aminim.smartcooking.data.PlanDay(d, pick("breakfast", mutableSetOf()), pick("lunch", used), pick("dinner", used))
        }
        val shopping = days.flatMap { listOf(it.lunch.note, it.dinner.note) }.mapNotNull { RecipeDb.byId(it) }
            .flatMap { r -> r.ingredients.filter { !it.optional && it.ingId.isNotBlank() && it.ingId !in Ingredients.staples }.map { it.name } }
            .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { (n, c) -> if (c > 1) "$n (${c.fa()} وعده)" else n }
        return ir.aminim.smartcooking.data.MealPlan(System.currentTimeMillis(), days, shopping, "برنامه آفلاین از کتاب آشپزی؛ با اینترنت برنامه هوشمند و شخصی‌سازی‌شده بسازید.", "کتاب آشپزی (آفلاین)")
    }
}
