package ir.aminim.smartcooking.data

import ir.aminim.smartcooking.knowledge.IngCat
import ir.aminim.smartcooking.knowledge.Ingredients
import org.json.JSONArray
import org.json.JSONObject

internal fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it).trim().takeIf { s -> s.isNotBlank() && s != "null" } }
internal fun List<String>.json() = JSONArray().also { a -> forEach { a.put(it) } }
internal fun JSONObject.s(k: String) = optString(k).trim().let { if (it == "null") "" else it }
internal inline fun <T> JSONArray?.objs(map: (JSONObject) -> T?): List<T> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it)?.let(map) }

/** وعده‌های غذایی */
object Meals {
    val all = listOf("breakfast" to "صبحانه", "lunch" to "ناهار", "dinner" to "شام", "snack" to "عصرانه", "dessert" to "دسر و شیرینی", "side" to "پیش‌غذا و سالاد")
    fun title(id: String) = all.firstOrNull { it.first == id }?.second ?: id
    fun emoji(id: String) = when (id) { "breakfast" -> "🍳"; "lunch" -> "🍛"; "dinner" -> "🍲"; "snack" -> "🥪"; "dessert" -> "🍮"; "side" -> "🥗"; else -> "🍽️" }
    /** وعده مناسب ساعت فعلی */
    fun forHour(h: Int) = when (h) { in 5..10 -> "breakfast"; in 11..15 -> "lunch"; in 16..18 -> "snack"; else -> "dinner" }
}

/** یک قلم از مواد لازم دستور پخت */
data class RecipeIng(val name: String, val amount: String, val ingId: String = "", val optional: Boolean = false) {
    fun toJson(): JSONObject = JSONObject().put("name", name).put("amount", amount).put("id", ingId).put("optional", optional)
    companion object {
        fun from(o: JSONObject): RecipeIng {
            val name = o.s("name").ifBlank { o.s("item") }
            return RecipeIng(name, o.s("amount").ifBlank { o.s("qty") }, o.s("id").ifBlank { Ingredients.find(name)?.id.orEmpty() }, o.optBoolean("optional", false))
        }
    }
}

/** یک مرحله پخت (با زمان اختیاری برای تایمر) */
data class Step(val text: String, val minutes: Int = 0) {
    fun toJson(): JSONObject = JSONObject().put("text", text).put("minutes", minutes)
    companion object {
        fun from(o: JSONObject) = Step(o.s("text").ifBlank { o.s("step") }, o.optInt("minutes", o.optInt("timer", 0)).coerceIn(0, 600))
    }
}

/** دستور پخت (از کتاب آشپزی داخلی یا هوش مصنوعی) */
data class Recipe(
    val id: String,
    val name: String,
    val emoji: String = "🍽️",
    val desc: String = "",
    val cuisine: String = "ایرانی",
    val meals: List<String> = emptyList(),
    val minutes: Int = 0,
    /** ۱ آسان، ۲ متوسط، ۳ حرفه‌ای */
    val difficulty: Int = 1,
    val servings: Int = 4,
    /** کالری هر نفر */
    val calories: Int = 0,
    val ingredients: List<RecipeIng> = emptyList(),
    val steps: List<Step> = emptyList(),
    val tips: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val protein: Int = 0,
    val carbs: Int = 0,
    val fat: Int = 0,
    val source: String = "",
    val local: Boolean = false,
    /** درصد تطابق با مواد موجود (-۱ یعنی محاسبه نشده) */
    val match: Int = -1,
    val missing: List<String> = emptyList(),
    val why: String = "",
    val image: String = "",
    val time: Long = 0,
) {
    val hasDetails: Boolean get() = steps.isNotEmpty() && ingredients.isNotEmpty()
    val difficultyTitle: String get() = when (difficulty) { 1 -> "آسان"; 2 -> "متوسط"; else -> "حرفه‌ای" }

    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("emoji", emoji).put("desc", desc).put("cuisine", cuisine)
        .put("meals", meals.json()).put("minutes", minutes).put("difficulty", difficulty).put("servings", servings).put("calories", calories)
        .put("ingredients", JSONArray().also { a -> ingredients.forEach { a.put(it.toJson()) } })
        .put("steps", JSONArray().also { a -> steps.forEach { a.put(it.toJson()) } })
        .put("tips", tips.json()).put("tags", tags.json()).put("protein", protein).put("carbs", carbs).put("fat", fat)
        .put("source", source).put("local", local).put("match", match).put("missing", missing.json()).put("why", why).put("image", image).put("time", time)

    companion object {
        fun from(o: JSONObject, fallbackId: String = System.nanoTime().toString()): Recipe {
            val n = o.optJSONObject("nutrition")
            val ings = o.optJSONArray("ingredients")?.let { a ->
                (0 until a.length()).mapNotNull { i ->
                    a.optJSONObject(i)?.let { RecipeIng.from(it) } ?: a.optString(i).takeIf { it.isNotBlank() }?.let { RecipeIng(it, "", Ingredients.find(it)?.id.orEmpty()) }
                }
            } ?: emptyList()
            val steps = o.optJSONArray("steps")?.let { a ->
                (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { Step.from(it) } ?: a.optString(i).takeIf { it.isNotBlank() }?.let { Step(it) } }
            } ?: emptyList()
            val meals = o.optJSONArray("meals").strings().ifEmpty { listOfNotNull(o.s("meal").takeIf { it.isNotBlank() }) }
            return Recipe(
                id = o.s("id").ifBlank { fallbackId },
                name = o.s("name").ifBlank { o.s("title") },
                emoji = o.s("emoji").ifBlank { "🍽️" },
                desc = o.s("desc").ifBlank { o.s("description") },
                cuisine = o.s("cuisine").ifBlank { "ایرانی" },
                meals = meals.map { m -> Meals.all.firstOrNull { it.second == m }?.first ?: m },
                minutes = o.optInt("minutes", o.optInt("time", 0)).coerceIn(0, 1440),
                difficulty = o.optInt("difficulty", 1).coerceIn(1, 3),
                servings = o.optInt("servings", 4).coerceIn(1, 30),
                calories = o.optInt("calories", n?.optInt("calories", 0) ?: 0).coerceIn(0, 5000),
                ingredients = ings,
                steps = steps,
                tips = o.optJSONArray("tips").strings(),
                tags = o.optJSONArray("tags").strings(),
                protein = o.optInt("protein", n?.optInt("protein", 0) ?: 0),
                carbs = o.optInt("carbs", n?.optInt("carbs", 0) ?: 0),
                fat = o.optInt("fat", n?.optInt("fat", 0) ?: 0),
                source = o.s("source"),
                local = o.optBoolean("local", false),
                match = o.optInt("match", -1).coerceIn(-1, 100),
                missing = o.optJSONArray("missing").strings(),
                why = o.s("why"),
                image = o.s("image"),
                time = o.optLong("time", 0L),
            )
        }
    }
}

/** یک قلم در یخچال/آشپزخانه کاربر */
data class PantryItem(
    val id: String,
    val name: String,
    val ingId: String = "",
    val cat: IngCat = IngCat.OTHER,
    val added: Long = System.currentTimeMillis(),
    /** تاریخ انقضا (۰ یعنی ثبت نشده) */
    val expiry: Long = 0,
) {
    val emoji: String get() = Ingredients.byId(ingId)?.emoji ?: cat.emoji
    /** چند روز تا انقضا (Int.MAX یعنی نامشخص) */
    fun daysLeft(now: Long = System.currentTimeMillis()): Int =
        if (expiry <= 0) Int.MAX_VALUE else Math.floorDiv(expiry - now, 86_400_000L).toInt()

    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("ing", ingId).put("cat", cat.name).put("added", added).put("expiry", expiry)
    companion object {
        fun from(o: JSONObject) = PantryItem(
            o.s("id"), o.s("name"), o.s("ing"), IngCat.entries.firstOrNull { it.name == o.s("cat") } ?: IngCat.OTHER,
            o.optLong("added", 0L), o.optLong("expiry", 0L),
        )
    }
}

/** لیست خرید */
data class ShopItem(val id: String, val name: String, val done: Boolean = false, val note: String = "") {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("done", done).put("note", note)
    companion object { fun from(o: JSONObject) = ShopItem(o.s("id"), o.s("name"), o.optBoolean("done"), o.s("note")) }
}

/** پیام گفتگو با سرآشپز */
data class ChatMsg(val role: String, val text: String, val image: String? = null, val error: Boolean = false)

/** یک وعده در برنامه هفتگی */
data class PlanMeal(val name: String, val emoji: String = "🍽️", val minutes: Int = 0, val calories: Int = 0, val note: String = "") {
    fun toJson(): JSONObject = JSONObject().put("name", name).put("emoji", emoji).put("minutes", minutes).put("calories", calories).put("note", note)
    companion object {
        fun from(o: JSONObject?) = if (o == null) PlanMeal("") else PlanMeal(o.s("name"), o.s("emoji").ifBlank { "🍽️" }, o.optInt("minutes"), o.optInt("calories"), o.s("note"))
    }
}

data class PlanDay(val day: String, val breakfast: PlanMeal, val lunch: PlanMeal, val dinner: PlanMeal) {
    fun toJson(): JSONObject = JSONObject().put("day", day).put("breakfast", breakfast.toJson()).put("lunch", lunch.toJson()).put("dinner", dinner.toJson())
    companion object {
        fun from(o: JSONObject) = PlanDay(o.s("day"), PlanMeal.from(o.optJSONObject("breakfast")), PlanMeal.from(o.optJSONObject("lunch")), PlanMeal.from(o.optJSONObject("dinner")))
    }
}

data class MealPlan(val created: Long, val days: List<PlanDay>, val shopping: List<String>, val note: String, val engine: String) {
    fun toJson(): JSONObject = JSONObject().put("created", created).put("days", JSONArray().also { a -> days.forEach { a.put(it.toJson()) } })
        .put("shopping", shopping.json()).put("note", note).put("engine", engine)
    companion object {
        fun from(o: JSONObject, created: Long = System.currentTimeMillis(), engine: String = "") = MealPlan(
            o.optLong("created", created), o.optJSONArray("days").objs { PlanDay.from(it) }, o.optJSONArray("shopping").strings(), o.s("note"), o.s("engine").ifBlank { engine },
        )
    }
}
