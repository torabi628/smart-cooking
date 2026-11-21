package ir.aminim.smartcooking.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.io.File

/** ذخیره‌سازی محلی (JSON) */
class Storage(private val context: Context) {
    private fun file(name: String) = File(context.filesDir, name)

    private fun <T> readList(name: String, map: (org.json.JSONObject) -> T): List<T> = runCatching {
        val f = file(name); if (!f.exists()) return emptyList()
        val a = JSONArray(f.readText())
        (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { runCatching { map(it) }.getOrNull() } }
    }.getOrDefault(emptyList())

    private fun writeAtomic(name: String, text: String) {
        val tmp = file("$name.tmp"); tmp.writeText(text); tmp.renameTo(file(name))
    }

    private fun <T> save(name: String, list: List<T>, json: (T) -> org.json.JSONObject) = writeAtomic(name, JSONArray().also { a -> list.forEach { a.put(json(it)) } }.toString())

    fun loadPantry(): List<PantryItem> = readList("pantry.json") { PantryItem.from(it) }
    fun savePantry(list: List<PantryItem>) = save("pantry.json", list) { it.toJson() }

    fun loadShopping(): List<ShopItem> = readList("shopping.json") { ShopItem.from(it) }
    fun saveShopping(list: List<ShopItem>) = save("shopping.json", list) { it.toJson() }

    fun loadFavorites(): List<Recipe> = readList("favorites.json") { Recipe.from(it) }
    fun saveFavorites(list: List<Recipe>) = save("favorites.json", list) { it.toJson() }

    fun loadHistory(): List<Recipe> = readList("history.json") { Recipe.from(it) }
    fun saveHistory(list: List<Recipe>) = save("history.json", list) { it.toJson() }

    fun loadPlan(): MealPlan? = runCatching { file("plan.json").takeIf { it.exists() }?.let { MealPlan.from(org.json.JSONObject(it.readText())) } }.getOrNull()
    fun savePlan(p: MealPlan?) { if (p == null) file("plan.json").delete() else writeAtomic("plan.json", p.toJson().toString()) }
}

/** ابزار تصویر: بارگذاری، چرخش طبق EXIF، کوچک‌سازی و ذخیره */
object Images {
    fun photosDir(c: Context) = File(c.filesDir, "photos").apply { mkdirs() }

    /** فایل موقت برای دوربین */
    fun newCameraFile(c: Context): File = File(c.cacheDir, "cam_${System.currentTimeMillis()}.jpg")

    private fun decodeSampled(open: () -> java.io.InputStream?, maxSide: Int): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open()?.use { BitmapFactory.decodeStream(it, null, o) }
        if (o.outWidth <= 0) return null
        var sample = 1
        while (maxOf(o.outWidth, o.outHeight) / (sample * 2) >= maxSide) sample *= 2
        return open()?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
    }

    private fun rotation(open: () -> java.io.InputStream?): Int = runCatching {
        open()?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90; ExifInterface.ORIENTATION_ROTATE_180 -> 180; ExifInterface.ORIENTATION_ROTATE_270 -> 270; else -> 0
            }
        } ?: 0
    }.getOrDefault(0)

    private fun scale(b: Bitmap, maxSide: Int, rot: Int): Bitmap {
        val r = maxSide.toFloat() / maxOf(b.width, b.height)
        val m = Matrix().apply { if (r < 1f) postScale(r, r); if (rot != 0) postRotate(rot.toFloat()) }
        return if (r >= 1f && rot == 0) b else Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
    }

    /** وارد کردن عکس از Uri (گالری/دوربین) به پوشه برنامه؛ خروجی: مسیر فایل */
    fun import(c: Context, uri: Uri): String? = runCatching {
        val open = { c.contentResolver.openInputStream(uri) }
        val bmp = decodeSampled(open, 1600) ?: return null
        val out = scale(bmp, 1600, rotation(open))
        val f = File(photosDir(c), "p_${System.currentTimeMillis()}_${(100..999).random()}.jpg")
        f.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        f.absolutePath
    }.getOrNull()

    /** base64 برای ارسال به هوش مصنوعی (ضلع بلند ۱۰۲۴ پیکسل) */
    fun base64(path: String, maxSide: Int = 1024): String? = runCatching {
        val f = File(path)
        val bmp = decodeSampled({ f.inputStream() }, maxSide) ?: return null
        val out = scale(bmp, maxSide, 0)
        val bos = ByteArrayOutputStream()
        out.compress(Bitmap.CompressFormat.JPEG, 82, bos)
        Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
    }.getOrNull()

    /** بندانگشتی برای نمایش */
    fun thumb(path: String, maxSide: Int = 600): Bitmap? = runCatching { val f = File(path); decodeSampled({ f.inputStream() }, maxSide) }.getOrNull()
}
