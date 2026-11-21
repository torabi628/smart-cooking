package ir.aminim.smartcooking.knowledge

/** دسته‌بندی مواد غذایی */
enum class IngCat(val title: String, val emoji: String) {
    PROTEIN("گوشت و پروتئین", "🍗"),
    DAIRY("لبنیات و تخم‌مرغ", "🧀"),
    VEG("سبزیجات", "🥦"),
    HERB("سبزی‌های معطر", "🌿"),
    FRUIT("میوه و خشکبار", "🍋"),
    LEGUME("حبوبات", "🫘"),
    GRAIN("برنج، نان و غلات", "🍚"),
    SPICE("ادویه و چاشنی", "🧂"),
    SAUCE("سس، رب و کنسرو", "🥫"),
    OTHER("سایر", "🛒");
}

/** یک ماده غذایی شناخته‌شده */
data class Ingredient(
    val id: String,
    val fa: String,
    val emoji: String,
    val cat: IngCat,
    val aka: List<String> = emptyList(),
    /** همیشه در خانه فرض می‌شود (نمک، فلفل، زردچوبه، روغن، آب) */
    val staple: Boolean = false,
    /** ماندگاری تقریبی در یخچال (روز)؛ ۰ یعنی ماندگار */
    val shelf: Int = 0,
)

object Ingredients {
    private fun i(id: String, fa: String, emoji: String, cat: IngCat, aka: String = "", staple: Boolean = false, shelf: Int = 0) =
        Ingredient(id, fa, emoji, cat, aka.split('|').map { it.trim() }.filter { it.isNotEmpty() }, staple, shelf)

    val all: List<Ingredient> = listOf(
        // ───── پروتئین
        i("chicken", "مرغ", "🍗", IngCat.PROTEIN, "سینه مرغ|ران مرغ|فیله مرغ|جوجه|گوشت مرغ|بال مرغ", shelf = 2),
        i("beef", "گوشت قرمز", "🥩", IngCat.PROTEIN, "گوشت گوساله|گوشت گوسفندی|گوشت گوسفند|گوشت خورشتی|گوشت|راسته|فیله گوساله|گوساله", shelf = 3),
        i("mince", "گوشت چرخ‌کرده", "🥩", IngCat.PROTEIN, "گوشت چرخ کرده|چرخ کرده|چرخ‌کرده|قیمه چرخ", shelf = 2),
        i("fish", "ماهی", "🐟", IngCat.PROTEIN, "فیله ماهی|ماهی قزل آلا|قزل‌آلا|قزل آلا|ماهی سفید|سالمون", shelf = 2),
        i("shrimp", "میگو", "🦐", IngCat.PROTEIN, "", shelf = 2),
        i("tuna", "تن ماهی", "🥫", IngCat.PROTEIN, "کنسرو ماهی|کنسرو تن|تن‌ماهی"),
        i("sausage", "سوسیس", "🌭", IngCat.PROTEIN, "هات داگ|هات‌داگ", shelf = 7),
        i("kalbas", "کالباس", "🥓", IngCat.PROTEIN, "ژامبون", shelf = 7),
        i("liver", "جگر", "🫀", IngCat.PROTEIN, "جگر مرغ|دل و جگر", shelf = 1),
        i("soy", "سویا", "🌱", IngCat.PROTEIN, "پروتئین سویا"),
        // ───── لبنیات و تخم‌مرغ
        i("egg", "تخم‌مرغ", "🥚", IngCat.DAIRY, "تخم مرغ|تخمه مرغ|تخم", shelf = 21),
        i("milk", "شیر", "🥛", IngCat.DAIRY, "شیر پرچرب|شیر کم چرب", shelf = 4),
        i("yogurt", "ماست", "🥣", IngCat.DAIRY, "ماست چکیده|ماست یونانی", shelf = 7),
        i("cheese", "پنیر", "🧀", IngCat.DAIRY, "پنیر سفید|پنیر لیقوان|پنیر فتا|پنیر تبریز", shelf = 14),
        i("mozzarella", "پنیر پیتزا", "🧀", IngCat.DAIRY, "موزارلا|پنیر گودا|پنیر چدار|پنیر ورقه‌ای|پنیر ورقه ای", shelf = 14),
        i("cream", "خامه", "🍶", IngCat.DAIRY, "خامه صبحانه|خامه قنادی|خامه پرچرب", shelf = 5),
        i("butter", "کره", "🧈", IngCat.DAIRY, "کره حیوانی", shelf = 30),
        i("kashk", "کشک", "🥣", IngCat.DAIRY, "کشک مایع", shelf = 10),
        i("doogh", "دوغ", "🥛", IngCat.DAIRY, "", shelf = 5),
        // ───── سبزیجات
        i("onion", "پیاز", "🧅", IngCat.VEG, "پیاز زرد|پیاز قرمز|پیاز داغ", staple = false),
        i("garlic", "سیر", "🧄", IngCat.VEG, "حبه سیر"),
        i("tomato", "گوجه‌فرنگی", "🍅", IngCat.VEG, "گوجه فرنگی|گوجه", shelf = 7),
        i("potato", "سیب‌زمینی", "🥔", IngCat.VEG, "سیب زمینی|سیب‌زمینی|سیبزمینی"),
        i("carrot", "هویج", "🥕", IngCat.VEG, "", shelf = 20),
        i("eggplant", "بادمجان", "🍆", IngCat.VEG, "بادنجان", shelf = 6),
        i("zucchini", "کدو سبز", "🥒", IngCat.VEG, "کدو مسمایی|کدوسبز", shelf = 6),
        i("pumpkin", "کدو حلوایی", "🎃", IngCat.VEG, "کدوحلوایی", shelf = 20),
        i("bell_pepper", "فلفل دلمه‌ای", "🫑", IngCat.VEG, "فلفل دلمه ای|فلفل دلمه|فلفل سبز|فلفل رنگی", shelf = 8),
        i("chili", "فلفل تند", "🌶️", IngCat.VEG, "فلفل هالوپینو|فلفل قرمز تازه|فلفل سبز تند", shelf = 10),
        i("cucumber", "خیار", "🥒", IngCat.VEG, "", shelf = 7),
        i("lettuce", "کاهو", "🥬", IngCat.VEG, "کاهو پیچ|کاهو رسمی", shelf = 5),
        i("cabbage", "کلم", "🥬", IngCat.VEG, "کلم سفید|کلم قرمز|کلم پیچ", shelf = 14),
        i("cauliflower", "گل‌کلم", "🥦", IngCat.VEG, "گل کلم", shelf = 7),
        i("broccoli", "کلم بروکلی", "🥦", IngCat.VEG, "بروکلی", shelf = 5),
        i("spinach", "اسفناج", "🥬", IngCat.VEG, "", shelf = 4),
        i("mushroom", "قارچ", "🍄", IngCat.VEG, "قارچ دکمه ای|قارچ دکمه‌ای", shelf = 5),
        i("peas", "نخود فرنگی", "🫛", IngCat.VEG, "نخودفرنگی|نخود سبز", shelf = 4),
        i("green_beans", "لوبیا سبز", "🫛", IngCat.VEG, "لوبیاسبز", shelf = 5),
        i("corn", "ذرت", "🌽", IngCat.VEG, "ذرت شیرین|بلال"),
        i("celery", "کرفس", "🥬", IngCat.VEG, "", shelf = 10),
        i("okra", "بامیه", "🫛", IngCat.VEG, "", shelf = 4),
        i("beet", "لبو", "🟣", IngCat.VEG, "چغندر", shelf = 14),
        i("turnip", "شلغم", "⚪", IngCat.VEG, "", shelf = 14),
        i("fava", "باقالی", "🫛", IngCat.VEG, "باقالی سبز|باقلا|باقالی خشک|باقالی پوست کنده"),
        // ───── سبزی‌های معطر
        i("scallion", "پیازچه", "🌱", IngCat.HERB, "", shelf = 5),
        i("herbs", "سبزی خوردن", "🌿", IngCat.HERB, "سبزی تازه", shelf = 4),
        i("stew_herbs", "سبزی قورمه", "🌿", IngCat.HERB, "سبزی قرمه|سبزی قورمه‌ای|سبزی قورمه ای|سبزی سرخ کرده", shelf = 4),
        i("kuku_herbs", "سبزی کوکو", "🌿", IngCat.HERB, "سبزی کوکویی", shelf = 4),
        i("aash_herbs", "سبزی آش", "🌿", IngCat.HERB, "سبزی آشی", shelf = 4),
        i("parsley", "جعفری", "🌿", IngCat.HERB, "", shelf = 5),
        i("coriander", "گشنیز", "🌿", IngCat.HERB, "", shelf = 5),
        i("dill", "شوید", "🌿", IngCat.HERB, "شوید خشک", shelf = 5),
        i("mint", "نعناع", "🌿", IngCat.HERB, "نعناع تازه|نعنا", shelf = 5),
        i("dried_mint", "نعناع خشک", "🍃", IngCat.HERB, "نعنا خشک|نعناع داغ"),
        // ───── میوه و خشکبار
        i("lemon", "لیمو ترش", "🍋", IngCat.FRUIT, "لیموترش|آبلیمو|آب لیمو|لیمو", shelf = 20),
        i("dried_lime", "لیمو عمانی", "🟤", IngCat.FRUIT, "لیموعمانی"),
        i("apple", "سیب", "🍎", IngCat.FRUIT, "سیب درختی", shelf = 20),
        i("banana", "موز", "🍌", IngCat.FRUIT, "", shelf = 5),
        i("orange", "پرتقال", "🍊", IngCat.FRUIT, "", shelf = 14),
        i("date", "خرما", "🌴", IngCat.FRUIT, ""),
        i("raisin", "کشمش", "🍇", IngCat.FRUIT, "مویز"),
        i("barberry", "زرشک", "🔴", IngCat.FRUIT, ""),
        i("prune", "آلو بخارا", "🟣", IngCat.FRUIT, "آلو|آلوبخارا|آلو خشک"),
        i("pomegranate", "انار", "🔴", IngCat.FRUIT, "", shelf = 30),
        i("strawberry", "توت‌فرنگی", "🍓", IngCat.FRUIT, "توت فرنگی", shelf = 3),
        i("walnut", "گردو", "🌰", IngCat.FRUIT, "مغز گردو"),
        i("almond", "بادام", "🌰", IngCat.FRUIT, "خلال بادام|پودر بادام"),
        i("pistachio", "پسته", "🥜", IngCat.FRUIT, "خلال پسته|پودر پسته"),
        i("peanut", "بادام زمینی", "🥜", IngCat.FRUIT, "بادام‌زمینی"),
        i("sesame", "کنجد", "⚪", IngCat.FRUIT, ""),
        // ───── حبوبات
        i("lentil", "عدس", "🟤", IngCat.LEGUME, ""),
        i("chickpea", "نخود", "🟡", IngCat.LEGUME, "نخود خشک"),
        i("red_beans", "لوبیا قرمز", "🔴", IngCat.LEGUME, "لوبیاقرمز"),
        i("pinto", "لوبیا چیتی", "🟤", IngCat.LEGUME, "لوبیاچیتی"),
        i("white_beans", "لوبیا سفید", "⚪", IngCat.LEGUME, "لوبیا سفید کنسروی|خوراک لوبیا کنسروی"),
        i("split_pea", "لپه", "🟡", IngCat.LEGUME, ""),
        i("mung", "ماش", "🟢", IngCat.LEGUME, ""),
        // ───── برنج، نان و غلات
        i("rice", "برنج", "🍚", IngCat.GRAIN, "برنج ایرانی|برنج هندی|برنج طارم|برنج هاشمی"),
        i("pasta", "ماکارونی", "🍝", IngCat.GRAIN, "پاستا|اسپاگتی|پنه|ماکارونی فرمی"),
        i("reshte", "رشته آش", "🍜", IngCat.GRAIN, "رشته آشی|رشته پلویی"),
        i("vermicelli", "رشته فرنگی", "🍜", IngCat.GRAIN, "ورمیشل"),
        i("bread", "نان", "🫓", IngCat.GRAIN, "نان سنگک|نان بربری|نان تافتون|نان باگت|باگت", shelf = 3),
        i("lavash", "نان لواش", "🫓", IngCat.GRAIN, "لواش", shelf = 5),
        i("toast", "نان تست", "🍞", IngCat.GRAIN, "نان ساندویچی|نان همبرگر", shelf = 5),
        i("flour", "آرد", "🌾", IngCat.GRAIN, "آرد سفید|آرد گندم"),
        i("barley", "جو پرک", "🌾", IngCat.GRAIN, "جو|جو پوست کنده"),
        i("oats", "جو دوسر", "🥣", IngCat.GRAIN, "جو دو سر|اوتمیل|بلغور جو دوسر"),
        i("bulgur", "بلغور گندم", "🌾", IngCat.GRAIN, "بلغور"),
        i("starch", "نشاسته", "⚪", IngCat.GRAIN, "نشاسته ذرت"),
        i("breadcrumbs", "پودر سوخاری", "🟫", IngCat.GRAIN, "پودر سوخاری|سوخاری|پودر نان"),
        i("rice_flour", "آرد برنج", "🌾", IngCat.GRAIN, ""),
        // ───── ادویه و چاشنی
        i("salt", "نمک", "🧂", IngCat.SPICE, "", staple = true),
        i("pepper", "فلفل سیاه", "⚫", IngCat.SPICE, "فلفل", staple = true),
        i("turmeric", "زردچوبه", "🟡", IngCat.SPICE, "زرد چوبه", staple = true),
        i("oil", "روغن", "🫗", IngCat.SPICE, "روغن مایع|روغن سرخ کردنی|روغن جامد|روغن آفتابگردان", staple = true),
        i("olive_oil", "روغن زیتون", "🫒", IngCat.SPICE, ""),
        i("saffron", "زعفران", "🌸", IngCat.SPICE, "زعفران دم کرده"),
        i("cinnamon", "دارچین", "🟤", IngCat.SPICE, ""),
        i("cumin", "زیره", "🟤", IngCat.SPICE, "زیره سبز|زیره سیاه"),
        i("curry", "پودر کاری", "🟡", IngCat.SPICE, "کاری|ادویه کاری"),
        i("paprika", "پاپریکا", "🔴", IngCat.SPICE, "پودر پاپریکا"),
        i("red_pepper", "فلفل قرمز", "🌶️", IngCat.SPICE, "پودر فلفل قرمز|فلفل قرمز آسیاب شده|پول بیبر"),
        i("cardamom", "هل", "🟢", IngCat.SPICE, "پودر هل"),
        i("rosewater", "گلاب", "🌹", IngCat.SPICE, ""),
        i("oregano", "پونه کوهی", "🌿", IngCat.SPICE, "اورگانو|آویشن"),
        i("ginger", "زنجبیل", "🫚", IngCat.SPICE, "پودر زنجبیل"),
        i("advieh", "ادویه پلویی", "🟤", IngCat.SPICE, "ادویه|ادویه خورشتی|ادویه مخصوص"),
        i("sumac", "سماق", "🔴", IngCat.SPICE, ""),
        i("sugar", "شکر", "🍬", IngCat.SPICE, "قند|پودر قند|شکر قهوه‌ای"),
        i("honey", "عسل", "🍯", IngCat.SPICE, ""),
        i("baking_powder", "بکینگ پودر", "🥄", IngCat.SPICE, "بیکینگ پودر|جوش شیرین"),
        i("vanilla", "وانیل", "🍦", IngCat.SPICE, "وانیلی"),
        i("yeast", "خمیرمایه", "🟫", IngCat.SPICE, "مخمر"),
        i("cocoa", "پودر کاکائو", "🍫", IngCat.SPICE, "کاکائو|شکلات"),
        // ───── سس، رب و کنسرو
        i("tomato_paste", "رب گوجه‌فرنگی", "🥫", IngCat.SAUCE, "رب گوجه|رب گوجه فرنگی|رب"),
        i("pom_paste", "رب انار", "🥫", IngCat.SAUCE, "رب‌انار"),
        i("verjuice", "آبغوره", "🍶", IngCat.SAUCE, "آب غوره"),
        i("vinegar", "سرکه", "🍶", IngCat.SAUCE, ""),
        i("mayo", "سس مایونز", "🥫", IngCat.SAUCE, "مایونز|سس مایونز"),
        i("ketchup", "سس گوجه", "🥫", IngCat.SAUCE, "کچاپ|سس کچاپ|سس گوجه‌فرنگی"),
        i("soy_sauce", "سس سویا", "🥫", IngCat.SAUCE, ""),
        i("mustard", "سس خردل", "🥫", IngCat.SAUCE, "خردل"),
        i("pickles", "خیارشور", "🥒", IngCat.SAUCE, "خیار شور"),
        i("olive", "زیتون", "🫒", IngCat.SAUCE, "زیتون سبز|زیتون سیاه"),
        i("canned_beans", "کنسرو لوبیا", "🥫", IngCat.SAUCE, "خوراک لوبیا"),
        i("tahini", "ارده", "🥫", IngCat.SAUCE, "تاهینی"),
    )

    private val byIdMap = all.associateBy { it.id }
    fun byId(id: String): Ingredient? = byIdMap[id]

    val staples: Set<String> = all.filter { it.staple }.map { it.id }.toSet()

    /** پرکاربردترین‌ها برای انتخاب سریع */
    val popular = listOf(
        "egg", "chicken", "mince", "beef", "onion", "potato", "tomato", "rice", "pasta", "cheese", "yogurt", "milk",
        "eggplant", "carrot", "bell_pepper", "mushroom", "lentil", "tomato_paste", "garlic", "lemon", "bread", "sausage",
        "zucchini", "kashk", "stew_herbs", "red_beans", "split_pea", "flour", "cream", "tuna", "spinach", "butter",
    )

    fun norm(s: String): String = s.replace('ي', 'ی').replace('ك', 'ک').replace('ۀ', 'ه').replace('أ', 'ا')
        .replace("‌", " ").replace("‌", " ").replace(Regex("\\s+"), " ").trim().lowercase()

    /** همه نام‌های قابل جستجو، از بلند به کوتاه (تا «گوشت چرخ‌کرده» پیش از «گوشت» شناخته شود) */
    private val names: List<Pair<String, Ingredient>> by lazy {
        all.flatMap { ing -> (listOf(ing.fa) + ing.aka).map { norm(it) to ing } }.filter { it.first.length >= 2 }.sortedByDescending { it.first.length }
    }

    /** یافتن ماده از روی نام دقیق */
    fun find(name: String): Ingredient? {
        val n = norm(name)
        if (n.isBlank()) return null
        names.firstOrNull { it.first == n }?.let { return it.second }
        return names.firstOrNull { n.length >= 3 && (n.startsWith(it.first + " ") || n.endsWith(" " + it.first)) }?.second
    }

    private val fillers = setOf(
        "دارم", "داریم", "هست", "هستش", "یه", "یک", "کم", "کمی", "مقداری", "مقدار", "تا", "دو", "سه", "چهار", "پنج", "عدد", "بسته", "کیلو", "گرم", "نیم",
        "تو", "توی", "یخچال", "خونه", "خانه", "داره", "و", "با", "هم", "همچنین", "ما", "من", "یکم", "چندتا", "چند", "تعدادی", "فقط", "اینا", "اینها", "ام",
        "۱", "۲", "۳", "۴", "۵", "1", "2", "3", "4", "5", "دونه", "دانه", "عددی", "تکه", "تیکه", "بسته‌ای", "قوطی", "کنسرو",
    )

    /**
     * استخراج مواد از متن آزاد (تایپ یا گفتار).
     * خروجی: مواد شناخته‌شده + نام‌های ناشناخته (برای افزودن به‌صورت دلخواه)
     */
    fun parse(text: String): Pair<List<Ingredient>, List<String>> {
        val known = linkedSetOf<Ingredient>()
        val unknown = mutableListOf<String>()
        val parts = norm(text).split('،', ',', '\n', ';', '؛', '.', '-').flatMap { it.split(" و ") }.map { it.trim() }.filter { it.isNotBlank() }
        parts.forEach { part ->
            var t = " $part "
            var hit = false
            names.forEach { (n, ing) ->
                val key = " $n "
                if (t.contains(key)) { known += ing; hit = true; t = t.replace(key, "  ") }
            }
            val rest = t.split(' ').map { it.trim() }.filter { it.isNotBlank() && it !in fillers && !it.all { c -> c.isDigit() } }
            if (rest.isNotEmpty() && rest.size <= 3 && (!hit || rest.size >= 2)) {
                val name = rest.joinToString(" ")
                if (name.length in 2..24) unknown += name
            }
        }
        return known.toList() to unknown.distinct()
    }
}
