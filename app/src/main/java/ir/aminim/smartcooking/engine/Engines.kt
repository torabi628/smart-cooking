package ir.aminim.smartcooking.engine

import ir.aminim.smartcooking.data.PantryItem
import ir.aminim.smartcooking.data.Recipe
import ir.aminim.smartcooking.data.RecipeIng
import ir.aminim.smartcooking.knowledge.Ingredients
import ir.aminim.smartcooking.knowledge.RecipeDb
import ir.aminim.smartcooking.util.fa

/** ترجیحات کاربر برای پیشنهاد غذا */
data class Prefs(
    /** وعده (خالی یعنی فرقی ندارد) */
    val meal: String = "",
    /** حداکثر زمان پخت به دقیقه (۰ یعنی مهم نیست) */
    val maxMinutes: Int = 0,
    val people: Int = 4,
    /** گیاهی، رژیمی، بدون گلوتن، مناسب کودک، اقتصادی */
    val diet: Set<String> = emptySet(),
    /** ایرانی، فرنگی، فست‌فود، آسیایی (خالی یعنی همه) */
    val cuisine: String = "",
    /** فقط با همین مواد (بدون خرید) */
    val strict: Boolean = false,
    /** توضیح آزاد کاربر */
    val note: String = "",
)

/** موتور آفلاین: پیدا کردن غذا از روی مواد موجود */
object Matcher {
    val dietOptions = listOf("گیاهی", "رژیمی", "بدون گلوتن", "مناسب کودک", "اقتصادی")
    val cuisines = listOf("ایرانی", "فرنگی", "فست‌فود", "آسیایی")

    /** آیا این ماده در خانه هست؟ */
    fun has(ing: RecipeIng, ids: Set<String>, names: List<String>): Boolean {
        if (ing.ingId.isNotBlank() && (ing.ingId in ids || ing.ingId in Ingredients.staples)) return true
        val n = Ingredients.norm(ing.name)
        if (n.isBlank()) return false
        Ingredients.find(ing.name)?.let { if (it.id in ids || it.staple) return true }
        return names.any { p -> p.length >= 2 && (n == p || n.startsWith("$p ") || n.contains(" $p") || p.startsWith("$n ")) }
    }

    fun pantryIds(pantry: List<PantryItem>): Set<String> = pantry.mapNotNull { it.ingId.takeIf { id -> id.isNotBlank() } }.toSet()
    fun pantryNames(pantry: List<PantryItem>): List<String> = pantry.map { Ingredients.norm(it.name) }

    /** محاسبه تطابق یک دستور با مواد موجود */
    fun evaluate(r: Recipe, pantry: List<PantryItem>): Recipe {
        val ids = pantryIds(pantry); val names = pantryNames(pantry)
        val required = r.ingredients.filter { !it.optional && !(it.ingId.isBlank() && r.local) && it.ingId !in Ingredients.staples }
        if (required.isEmpty()) return r.copy(match = 100, missing = emptyList())
        val have = required.filter { has(it, ids, names) }
        val optHave = r.ingredients.count { it.optional && has(it, ids, names) }
        val missing = required.filter { !has(it, ids, names) }.map { it.name }
        val score = (100.0 * have.size / required.size + optHave * 2).toInt().coerceIn(0, 100)
        return r.copy(match = score, missing = missing)
    }

    fun suggest(pantry: List<PantryItem>, p: Prefs, limit: Int = 12): List<Recipe> {
        val ids = pantryIds(pantry); val names = pantryNames(pantry)
        val empty = pantry.isEmpty()
        return RecipeDb.all.asSequence()
            .filter { p.meal.isBlank() || p.meal in it.meals || (p.meal == "dinner" && "lunch" in it.meals) || (p.meal == "lunch" && "dinner" in it.meals) }
            .filter { p.maxMinutes <= 0 || it.minutes <= p.maxMinutes }
            .filter { r -> p.diet.all { d -> d in r.tags } }
            .filter { p.cuisine.isBlank() || it.cuisine == p.cuisine }
            .map { evaluate(it, pantry) }
            .filter { r ->
                if (empty) true
                else {
                    val hits = r.ingredients.count { !it.optional && it.ingId !in Ingredients.staples && has(it, ids, names) }
                    hits > 0 && (if (p.strict) r.missing.isEmpty() else r.missing.size <= 3 || r.match >= 50)
                }
            }
            .sortedWith(compareByDescending<Recipe> { it.match }.thenBy { it.missing.size }.thenBy { it.minutes })
            .take(limit)
            .map { it.copy(why = whyText(it)) }
            .toList()
    }

    private fun whyText(r: Recipe): String = when {
        r.missing.isEmpty() -> "همه مواد اصلی را دارید"
        r.missing.size == 1 -> "فقط «${r.missing.first()}» را کم دارید"
        else -> "${r.missing.size.fa()} قلم کم دارید"
    }

    /** پیشنهاد امروز بر اساس ساعت و روز (ثابت در طول روز) */
    fun ofTheDay(meal: String, seed: Long): Recipe {
        val list = RecipeDb.all.filter { meal in it.meals }.ifEmpty { RecipeDb.all }
        return list[((seed / 86_400_000L) % list.size).toInt().let { if (it < 0) -it else it }]
    }
}

/** مقیاس کردن مقادیر دستور برای تعداد نفرات */
object Amounts {
    private fun latin(s: String) = buildString { s.forEach { c -> append(if (c in '۰'..'۹') '0' + (c - '۰') else if (c in '٠'..'٩') '0' + (c - '٠') else c) } }

    private val mixed = Regex("(\\d+)\\s*و\\s*(\\d+)\\s*/\\s*(\\d+)")
    private val frac = Regex("(\\d+)\\s*/\\s*(\\d+)")
    private val num = Regex("\\d+(?:\\.\\d+)?")
    private val words = listOf("یک‌چهارم" to 0.25, "یک چهارم" to 0.25, "سه‌چهارم" to 0.75, "سه چهارم" to 0.75, "نصف" to 0.5, "نیم" to 0.5)

    fun pretty(v: Double): String {
        if (v <= 0) return "0"
        val whole = kotlin.math.floor(v).toInt()
        val rest = v - whole
        val f = when {
            rest < 0.12 -> ""
            rest < 0.29 -> "۱/۴"
            rest < 0.41 -> "۱/۳"
            rest < 0.59 -> "۱/۲"
            rest < 0.71 -> "۲/۳"
            rest < 0.88 -> "۳/۴"
            else -> "+"
        }
        return when {
            f == "+" -> (whole + 1).fa()
            whole == 0 && f.isNotEmpty() -> f
            f.isEmpty() -> whole.fa()
            else -> "${whole.fa()} و $f"
        }
    }

    /** «۲ پیمانه» × ۱.۵ = «۳ پیمانه»؛ مقادیر بدون عدد (کمی، به مقدار لازم) تغییر نمی‌کنند */
    fun scale(amount: String, factor: Double): String {
        if (factor == 1.0 || amount.isBlank()) return amount
        val t = latin(amount)
        mixed.find(t)?.let { m ->
            val v = m.groupValues[1].toDouble() + m.groupValues[2].toDouble() / m.groupValues[3].toDouble().coerceAtLeast(1.0)
            return t.replaceRange(m.range, pretty(v * factor)).fa()
        }
        frac.find(t)?.let { m ->
            val v = m.groupValues[1].toDouble() / m.groupValues[2].toDouble().coerceAtLeast(1.0)
            return t.replaceRange(m.range, pretty(v * factor)).fa()
        }
        num.find(t)?.let { m -> return t.replaceRange(m.range, pretty(m.value.toDouble() * factor)).fa() }
        words.forEach { (w, v) -> if (t.contains(w)) return t.replaceFirst(w, pretty(v * factor)) }
        return amount
    }
}
