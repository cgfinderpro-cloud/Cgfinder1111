package ir.smartscanner.docscan.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * مدل ۴ گوشه اصلی سند (به ترتیب ساعت‌گرد: بالا-چپ، بالا-راست، پایین-راست، پایین-چپ)
 */
data class CornerPoints(
    val p0: Offset, // بالا - چپ (Top-Left)
    val p1: Offset, // بالا - راست (Top-Right)
    val p2: Offset, // پایین - راست (Bottom-Right)
    val p3: Offset  // پایین - چپ (Bottom-Left)
) {
    fun toList(): List<Offset> = listOf(p0, p1, p2, p3)

    fun withPoint(index: Int, newOffset: Offset): CornerPoints {
        return when (index) {
            0 -> copy(p0 = newOffset)
            1 -> copy(p1 = newOffset)
            2 -> copy(p2 = newOffset)
            3 -> copy(p3 = newOffset)
            else -> this
        }
    }
}

/**
 * موتور پردازش و تصحیح پرسپکتیو کاملاً نیتیو با استفاده از android.graphics.Matrix.setPolyToPoly
 */
object PerspectiveCropEngine {

    /**
     * تشخیص هوشمند پیشرفته لبه‌ها (Robust Multi-Ray Edge Detection):
     * با تحلیل کنتراست و گرادیان نوری از ۴ گوشه به سمت مرکز جهت شناسایی دقیق گوشه‌های کاغذ یا کارت
     */
    fun detectDocumentCorners(bitmap: Bitmap): CornerPoints {
        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()

        try {
            // نمونه‌برداری سبک با نسبت ابعاد برای پردازش بلادرنگ و سریع
            val sampleW = 160
            val sampleH = ((160f / width) * height).toInt().coerceIn(120, 240)
            val scaled = Bitmap.createScaledBitmap(bitmap, sampleW, sampleH, false)
            val pixels = IntArray(sampleW * sampleH)
            scaled.getPixels(pixels, 0, sampleW, 0, 0, sampleW, sampleH)

            // محاسبه روشنایی پس‌زمینه در حاشیه‌های بسیار بیرونی (میز یا فرش)
            var bgLumSum = 0L
            var bgCount = 0
            val borderMargin = 4
            for (x in 0 until sampleW) {
                for (y in 0 until borderMargin) {
                    bgLumSum += getLuminance(pixels[y * sampleW + x])
                    bgLumSum += getLuminance(pixels[(sampleH - 1 - y) * sampleW + x])
                    bgCount += 2
                }
            }
            val avgBgLum = (bgLumSum / max(1, bgCount)).toInt()

            // تابع کمکی برای ردیابی شعاعی لبه از یک نقطه شروع به سمت نقطه هدف
            fun traceEdge(startX: Float, startY: Float, targetX: Float, targetY: Float): Offset {
                val steps = 30
                var bestX = startX
                var bestY = startY
                var maxGrad = 0
                val minSteps = 2 // حداقل فاصله از لبه کادر
                val maxSteps = 22 // حداکثر پیشروی به داخل (حدود ۳۰ تا ۳۵ درصد)

                for (i in minSteps..maxSteps) {
                    val t = i.toFloat() / steps
                    val cx = (startX + (targetX - startX) * t).toInt().coerceIn(0, sampleW - 1)
                    val cy = (startY + (targetY - startY) * t).toInt().coerceIn(0, sampleH - 1)
                    val currentLum = getLuminance(pixels[cy * sampleW + cx])
                    val grad = Math.abs(currentLum - avgBgLum)

                    if (grad > maxGrad && grad > 22) {
                        maxGrad = grad
                        bestX = cx.toFloat()
                        bestY = cy.toFloat()
                    }
                }
                return Offset(bestX, bestY)
            }

            val centerX = sampleW / 2f
            val centerY = sampleH / 2f

            // ردیابی ۴ گوشه اصلی در جهت مرکز
            val foundP0 = traceEdge(0f, 0f, centerX, centerY)
            val foundP1 = traceEdge(sampleW - 1f, 0f, centerX, centerY)
            val foundP2 = traceEdge(sampleW - 1f, sampleH - 1f, centerX, centerY)
            val foundP3 = traceEdge(0f, sampleH - 1f, centerX, centerY)

            scaled.recycle()

            val scaleX = width / sampleW
            val scaleY = height / sampleH

            val safeMarginX = width * 0.05f
            val safeMarginY = height * 0.05f

            // تبدیل مختصات نمونه‌برداری به ابعاد اصلی تصویر با حاشیه امن
            val finalP0 = Offset(
                x = (foundP0.x * scaleX).coerceIn(safeMarginX, width * 0.35f),
                y = (foundP0.y * scaleY).coerceIn(safeMarginY, height * 0.35f)
            )
            val finalP1 = Offset(
                x = (foundP1.x * scaleX).coerceIn(width * 0.65f, width - safeMarginX),
                y = (foundP1.y * scaleY).coerceIn(safeMarginY, height * 0.35f)
            )
            val finalP2 = Offset(
                x = (foundP2.x * scaleX).coerceIn(width * 0.65f, width - safeMarginX),
                y = (foundP2.y * scaleY).coerceIn(height * 0.65f, height - safeMarginY)
            )
            val finalP3 = Offset(
                x = (foundP3.x * scaleX).coerceIn(safeMarginX, width * 0.35f),
                y = (foundP3.y * scaleY).coerceIn(height * 0.65f, height - safeMarginY)
            )

            return CornerPoints(p0 = finalP0, p1 = finalP1, p2 = finalP2, p3 = finalP3)
        } catch (e: Exception) {
            e.printStackTrace()
            // پیش‌فرض امن و شکیل: حاشیه ۵ درصدی استاندارد
            return getDefaultCorners(width, height, 0.05f)
        }
    }

    /**
     * محاسبه ۴ نقطه پیش‌فرض با حاشیه مشخص
     */
    fun getDefaultCorners(width: Float, height: Float, marginFactor: Float = 0.05f): CornerPoints {
        val mx = width * marginFactor
        val my = height * marginFactor
        return CornerPoints(
            p0 = Offset(mx, my),
            p1 = Offset(width - mx, my),
            p2 = Offset(width - mx, height - my),
            p3 = Offset(mx, height - my)
        )
    }

    /**
     * برش پرسپکتیو با تبدیل هندسی نیتیو Matrix.setPolyToPoly:
     * تصویر زاویه‌دار را به یک مستطیل کاملاً صاف، تراز و با کیفیت تبدیل می‌کند
     */
    fun cropPerspective(source: Bitmap, corners: CornerPoints): Bitmap {
        val p0 = corners.p0
        val p1 = corners.p1
        val p2 = corners.p2
        val p3 = corners.p3

        // ۱. محاسبه ابعاد خروجی بر اساس فاصله اقلیدسی لبه‌ها
        val topWidth = hypot(p1.x - p0.x, p1.y - p0.y)
        val bottomWidth = hypot(p2.x - p3.x, p2.y - p3.y)
        val targetWidth = max(topWidth, bottomWidth).toInt().coerceIn(200, 4096)

        val leftHeight = hypot(p3.x - p0.x, p3.y - p0.y)
        val rightHeight = hypot(p2.x - p1.x, p2.y - p1.y)
        val targetHeight = max(leftHeight, rightHeight).toInt().coerceIn(200, 4096)

        // ۲. آرایه نقاط مبدا (۴ گوشه سند انتخابی)
        val src = floatArrayOf(
            p0.x, p0.y, // بالا چپ
            p1.x, p1.y, // بالا راست
            p2.x, p2.y, // پایین راست
            p3.x, p3.y  // پایین چپ
        )

        // ۳. آرایه نقاط مقصد (مستطیل افقی/عمودی خروجی)
        val dst = floatArrayOf(
            0f, 0f,
            targetWidth.toFloat(), 0f,
            targetWidth.toFloat(), targetHeight.toFloat(),
            0f, targetHeight.toFloat()
        )

        val matrix = Matrix()
        val success = matrix.setPolyToPoly(src, 0, dst, 0, 4)

        return if (success) {
            val output = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(source, matrix, paint)
            output
        } else {
            // در صورت عدم همگرایی ماتریس، برش مستطیلی استاندارد جایگزین می‌شود
            val minX = minOf(p0.x, p1.x, p2.x, p3.x).toInt().coerceIn(0, source.width - 20)
            val minY = minOf(p0.y, p1.y, p2.y, p3.y).toInt().coerceIn(0, source.height - 20)
            val maxX = maxOf(p0.x, p1.x, p2.x, p3.x).toInt().coerceIn(minX + 20, source.width)
            val maxY = maxOf(p0.y, p1.y, p2.y, p3.y).toInt().coerceIn(minY + 20, source.height)
            Bitmap.createBitmap(source, minX, minY, max(10, maxX - minX), max(10, maxY - minY))
        }
    }

    /**
     * استخراج ناحیه ذره‌بین (Loupe/Magnifier) در اطراف گوشه مورد نظر جهت تنظیم دقیق میلی‌متری
     */
    fun extractMagnifierBitmap(source: Bitmap, center: Offset, sizePx: Int = 140): Bitmap {
        val halfSize = sizePx / 2
        val left = (center.x - halfSize).toInt().coerceIn(0, max(0, source.width - sizePx))
        val top = (center.y - halfSize).toInt().coerceIn(0, max(0, source.height - sizePx))
        val w = min(sizePx, source.width - left)
        val h = min(sizePx, source.height - top)
        return Bitmap.createBitmap(source, left, top, max(1, w), max(1, h))
    }

    /**
     * چرخش ۹۰ درجه تصویر جهت تراز سریع اسناد افقی یا عمودی
     */
    fun rotateBitmap(source: Bitmap, degrees: Float): Bitmap {
        if (degrees % 360f == 0f) return source
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun getLuminance(color: Int): Int {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return (0.299 * r + 0.587 * g + 0.114 * b).toInt()
    }
}
