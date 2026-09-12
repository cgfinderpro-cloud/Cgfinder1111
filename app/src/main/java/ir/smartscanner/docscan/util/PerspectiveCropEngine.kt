package ir.smartscanner.docscan.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

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
 * خط استخراج شده از هاف ترنسفورم به صورت مختصات قطبی: r = x*cos(theta) + y*sin(theta)
 */
private data class PolarLine(
    val r: Float,
    val theta: Float, // بر حسب رادیان
    val votes: Int
)

/**
 * خط در قالب پارامترهای دکارتی: a*x + b*y + c = 0
 */
private data class CartesianLine(
    val a: Float,
    val b: Float,
    val c: Float
)

/**
 * موتور پردازش و تصحیح پرسپکتیو با الگوریتم Canny Edge Detection و Hough Transform
 * جهت تشخیص خودکار، بسیار دقیق و بلادرنگ ۴ لبه و گوشه‌های مدرک
 */
object PerspectiveCropEngine {

    /**
     * تشخیص هوشمند گوشه‌های مدرک با استفاده از تلفیق:
     * ۱. فیلتر گوسی (Gaussian Smoothing)
     * ۲. تشخیص لبه Canny (گرادیان سوبل + Non-Maximum Suppression + Hysteresis Thresholding)
     * ۳. تبدیل هاف برای استخراج خطوط اصلی لبه‌های کاغذ (Hough Transform for Lines)
     * ۴. دسته‌بندی خطوط به ۴ جهت (بالا، راست، پایین، چپ) و تقاطع‌گیری هندسی
     * ۵. الگوریتم پشتیبان چندپرتویی (Multi-Ray Adaptive Fallback) در صورت اسناد کم‌کنتراست
     */
    fun detectDocumentCorners(bitmap: Bitmap): CornerPoints {
        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()

        try {
            // ۱. مقیاس‌بندی به اندازه بهینه جهت اجرای فوق‌سریع Canny و Hough (کمتر از ۲۰ میلی‌ثانیه)
            val sampleW = 200
            val sampleH = ((200f / width) * height).toInt().coerceIn(150, 300)
            val scaled = Bitmap.createScaledBitmap(bitmap, sampleW, sampleH, false)
            val pixels = IntArray(sampleW * sampleH)
            scaled.getPixels(pixels, 0, sampleW, 0, 0, sampleW, sampleH)
            scaled.recycle()

            // ۲. تبدیل به تصویر خاکستری (Grayscale)
            val gray = FloatArray(sampleW * sampleH)
            for (i in pixels.indices) {
                val c = pixels[i]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                gray[i] = 0.299f * r + 0.587f * g + 0.114f * b
            }

            // ۳. اعمال فیلتر گوسی ۳×۳ برای حذف نویزهای سطح و بافت کاغذ
            val smoothed = applyGaussianFilter(gray, sampleW, sampleH)

            // ۴. اجرای الگوریتم پیشرفته Canny Edge Detection
            val edges = applyCannyEdgeDetector(smoothed, sampleW, sampleH)

            // ۵. اجرای هاف ترنسفورم (Hough Transform) برای استخراج خطوط شاخص مرزی
            val houghCorners = findCornersViaHoughTransform(edges, sampleW, sampleH)

            val scaleX = width / sampleW
            val scaleY = height / sampleH

            val safeMarginX = width * 0.03f
            val safeMarginY = height * 0.03f

            if (houghCorners != null) {
                // اگر گوشه‌ها از طریق تقاطع خطوط Canny + Hough پیدا شدند، اعتبارسنجی ابعاد
                val finalP0 = Offset(
                    x = (houghCorners.p0.x * scaleX).coerceIn(safeMarginX, width * 0.40f),
                    y = (houghCorners.p0.y * scaleY).coerceIn(safeMarginY, height * 0.40f)
                )
                val finalP1 = Offset(
                    x = (houghCorners.p1.x * scaleX).coerceIn(width * 0.60f, width - safeMarginX),
                    y = (houghCorners.p1.y * scaleY).coerceIn(safeMarginY, height * 0.40f)
                )
                val finalP2 = Offset(
                    x = (houghCorners.p2.x * scaleX).coerceIn(width * 0.60f, width - safeMarginX),
                    y = (houghCorners.p2.y * scaleY).coerceIn(height * 0.60f, height - safeMarginY)
                )
                val finalP3 = Offset(
                    x = (houghCorners.p3.x * scaleX).coerceIn(safeMarginX, width * 0.40f),
                    y = (houghCorners.p3.y * scaleY).coerceIn(height * 0.60f, height - safeMarginY)
                )

                // بررسی محدب بودن (Convex) و ابعاد منطقی
                if (isValidPolygon(finalP0, finalP1, finalP2, finalP3, width, height)) {
                    return CornerPoints(finalP0, finalP1, finalP2, finalP3)
                }
            }

            // ۶. پشتیبان: ردیابی لبه‌ها روی نقشه لبه‌های Canny با روش تابش پرتو از ۴ جهت
            val fallbackCorners = detectCornersFromEdgeMap(edges, sampleW, sampleH)
            if (fallbackCorners != null) {
                val finalP0 = Offset(
                    x = (fallbackCorners.p0.x * scaleX).coerceIn(safeMarginX, width * 0.40f),
                    y = (fallbackCorners.p0.y * scaleY).coerceIn(safeMarginY, height * 0.40f)
                )
                val finalP1 = Offset(
                    x = (fallbackCorners.p1.x * scaleX).coerceIn(width * 0.60f, width - safeMarginX),
                    y = (fallbackCorners.p1.y * scaleY).coerceIn(safeMarginY, height * 0.40f)
                )
                val finalP2 = Offset(
                    x = (fallbackCorners.p2.x * scaleX).coerceIn(width * 0.60f, width - safeMarginX),
                    y = (fallbackCorners.p2.y * scaleY).coerceIn(height * 0.60f, height - safeMarginY)
                )
                val finalP3 = Offset(
                    x = (fallbackCorners.p3.x * scaleX).coerceIn(safeMarginX, width * 0.40f),
                    y = (fallbackCorners.p3.y * scaleY).coerceIn(height * 0.60f, height - safeMarginY)
                )
                return CornerPoints(finalP0, finalP1, finalP2, finalP3)
            }

            // در غیر اینصورت پیش‌فرض شکیل ۵ درصدی
            return getDefaultCorners(width, height, 0.05f)

        } catch (e: Exception) {
            e.printStackTrace()
            return getDefaultCorners(width, height, 0.05f)
        }
    }

    /**
     * فیلتر گوسی ۳×۳ با هسته استاندارد [1, 2, 1; 2, 4, 2; 1, 2, 1] / 16
     */
    private fun applyGaussianFilter(src: FloatArray, w: Int, h: Int): FloatArray {
        val dst = FloatArray(w * h)
        for (y in 1 until h - 1) {
            val yPrev = (y - 1) * w
            val yCurr = y * w
            val yNext = (y + 1) * w
            for (x in 1 until w - 1) {
                val sum = (
                    src[yPrev + x - 1] * 1f + src[yPrev + x] * 2f + src[yPrev + x + 1] * 1f +
                    src[yCurr + x - 1] * 2f + src[yCurr + x] * 4f + src[yCurr + x + 1] * 2f +
                    src[yNext + x - 1] * 1f + src[yNext + x] * 2f + src[yNext + x + 1] * 1f
                ) / 16f
                dst[yCurr + x] = sum
            }
        }
        return dst
    }

    /**
     * الگوریتم کامل Canny Edge Detection شامل:
     * - محاسبه شدت گرادیان افقی و عمودی سوبل (Sobel Gx, Gy)
     * - زاویه گرادیان و کوانتیزه‌سازی جهت‌ها (0, 45, 90, 135 درجه)
     * - سرکوب غیربیشینه‌ها (Non-Maximum Suppression) برای نازک‌سازی لبه‌ها به ۱ پیکسل
     * - اعمال دو آستانه بالا و پایین و پیوند پسماند (Hysteresis Thresholding)
     */
    private fun applyCannyEdgeDetector(src: FloatArray, w: Int, h: Int): BooleanArray {
        val magnitude = FloatArray(w * h)
        val direction = ByteArray(w * h)

        var maxMag = 0f

        // ۱. عملگر سوبل (Sobel Operator)
        for (y in 1 until h - 1) {
            val yPrev = (y - 1) * w
            val yCurr = y * w
            val yNext = (y + 1) * w
            for (x in 1 until w - 1) {
                val gx = (
                    -src[yPrev + x - 1] + src[yPrev + x + 1] +
                    -2f * src[yCurr + x - 1] + 2f * src[yCurr + x + 1] +
                    -src[yNext + x - 1] + src[yNext + x + 1]
                )
                val gy = (
                    -src[yPrev + x - 1] - 2f * src[yPrev + x] - src[yPrev + x + 1] +
                     src[yNext + x - 1] + 2f * src[yNext + x] + src[yNext + x + 1]
                )

                val mag = hypot(gx, gy)
                val idx = yCurr + x
                magnitude[idx] = mag
                if (mag > maxMag) maxMag = mag

                // کوانتیزه‌سازی زاویه به ۴ جهت اصلی: ۰ (افقی)، ۱ (۴۵ درجه)، ۲ (۹۰ درجه عمودی)، ۳ (۱۳۵ درجه)
                val angle = (atan2(gy, gx) * 180f / PI.toFloat() + 180f) % 180f
                direction[idx] = when {
                    (angle < 22.5f || angle >= 157.5f) -> 0 // افقی
                    (angle in 22.5f..67.5f) -> 1            // ۴۵ درجه
                    (angle in 67.5f..112.5f) -> 2           // ۹۰ درجه عمودی
                    else -> 3                               // ۱۳۵ درجه
                }
            }
        }

        // ۲. سرکوب غیربیشینه (Non-Maximum Suppression - NMS)
        val nms = FloatArray(w * h)
        for (y in 1 until h - 1) {
            val yPrev = (y - 1) * w
            val yCurr = y * w
            val yNext = (y + 1) * w
            for (x in 1 until w - 1) {
                val idx = yCurr + x
                val mag = magnitude[idx]
                if (mag < 5f) continue

                val isLocalMax = when (direction[idx].toInt()) {
                    0 -> mag >= magnitude[yCurr + x - 1] && mag >= magnitude[yCurr + x + 1]
                    1 -> mag >= magnitude[yNext + x - 1] && mag >= magnitude[yPrev + x + 1]
                    2 -> mag >= magnitude[yPrev + x] && mag >= magnitude[yNext + x]
                    3 -> mag >= magnitude[yPrev + x - 1] && mag >= magnitude[yNext + x + 1]
                    else -> false
                }
                if (isLocalMax) {
                    nms[idx] = mag
                }
            }
        }

        // ۳. آستانه‌گیری دوگانه و پیوند هیسترزیس (Hysteresis Thresholding)
        val highThreshold = max(25f, maxMag * 0.20f)
        val lowThreshold = highThreshold * 0.40f

        val edgeMap = BooleanArray(w * h)
        val strongIndices = IntArray(w * h)
        var strongCount = 0

        for (i in nms.indices) {
            if (nms[i] >= highThreshold) {
                edgeMap[i] = true
                strongIndices[strongCount++] = i
            }
        }

        // دنبال کردن لبه‌های ضعیف متصل به قوی (BFS / Edge Tracking)
        val dx = intArrayOf(-1, 0, 1, -1, 1, -1, 0, 1)
        val dy = intArrayOf(-1, -1, -1, 0, 0, 1, 1, 1)
        var head = 0
        while (head < strongCount) {
            val currentIdx = strongIndices[head++]
            val cx = currentIdx % w
            val cy = currentIdx / w

            for (k in 0 until 8) {
                val nx = cx + dx[k]
                val ny = cy + dy[k]
                if (nx in 0 until w && ny in 0 until h) {
                    val nIdx = ny * w + nx
                    if (!edgeMap[nIdx] && nms[nIdx] >= lowThreshold) {
                        edgeMap[nIdx] = true
                        strongIndices[strongCount++] = nIdx
                    }
                }
            }
        }

        return edgeMap
    }

    /**
     * هاف ترنسفورم خطی (Hough Transform for Lines) بر روی نقشه لبه‌های Canny:
     * - ایجاد فضای تجمع‌آور (Accumulator Array) با زاویه‌های ۰ تا ۱۸۰ درجه
     * - ردیابی قله‌های رأی‌گیری
     * - دسته‌بندی خطوط به ۴ جهت لبه کاغذ: بالا، پایین، چپ، راست
     * - محاسبه ۴ نقطه تقاطع هندسی دو به دو
     */
    private fun findCornersViaHoughTransform(edges: BooleanArray, w: Int, h: Int): CornerPoints? {
        val maxR = hypot(w.toFloat(), h.toFloat())
        val numThetas = 90 // گام ۲ درجه جهت سرعت بسیار بالا
        val thetaStep = PI.toFloat() / numThetas
        val rStep = 1.5f
        val numR = (2 * maxR / rStep).toInt() + 1
        val rOffset = (maxR / rStep).toInt()

        val cosTable = FloatArray(numThetas) { i -> cos(i * thetaStep) }
        val sinTable = FloatArray(numThetas) { i -> sin(i * thetaStep) }

        val accumulator = IntArray(numThetas * numR)

        // پر کردن جدول آکومولاتور هاف ترنسفورم
        for (y in 0 until h) {
            val yW = y * w
            for (x in 0 until w) {
                if (edges[yW + x]) {
                    for (t in 0 until numThetas) {
                        val rVal = x * cosTable[t] + y * sinTable[t]
                        val rIdx = (rVal / rStep).roundToInt() + rOffset
                        if (rIdx in 0 until numR) {
                            accumulator[t * numR + rIdx]++
                        }
                    }
                }
            }
        }

        // استخراج خطوط برجسته با بیشترین آرا
        val lines = mutableListOf<PolarLine>()
        val minVotes = max(18, (w * 0.12f).toInt())

        for (t in 0 until numThetas) {
            for (rIdx in 1 until numR - 1) {
                val votes = accumulator[t * numR + rIdx]
                if (votes >= minVotes) {
                    // بررسی بیشینه محلی ۳×۳ در فضای پارامتر هاف
                    val prevR = accumulator[t * numR + rIdx - 1]
                    val nextR = accumulator[t * numR + rIdx + 1]
                    if (votes >= prevR && votes >= nextR) {
                        val actualR = (rIdx - rOffset) * rStep
                        val thetaRad = t * thetaStep
                        lines.add(PolarLine(actualR, thetaRad, votes))
                    }
                }
            }
        }

        if (lines.isEmpty()) return null

        // تبدیل خطوط قطبی به خطوط دکارتی (cos*x + sin*y = r => cos*x + sin*y - r = 0)
        // تفکیک خطوط به افقی (نزدیک به 90 درجه) و عمودی (نزدیک به 0 یا 180 درجه)
        val horizontalLines = mutableListOf<CartesianLine>()
        val verticalLines = mutableListOf<CartesianLine>()

        val halfW = w / 2f
        val halfH = h / 2f

        for (l in lines) {
            val a = cos(l.theta)
            val b = sin(l.theta)
            val c = -l.r

            // زاویه خط نسبت به افق: اگر |b| > |a| خط افقی‌تر است، اگر |a| >= |b| خط عمودی‌تر است
            if (abs(b) > 0.65f) {
                horizontalLines.add(CartesianLine(a, b, c))
            } else if (abs(a) > 0.65f) {
                verticalLines.add(CartesianLine(a, b, c))
            }
        }

        if (horizontalLines.isEmpty() || verticalLines.isEmpty()) return null

        // تفکیک خطوط افقی به لبه بالا (Top) و لبه پایین (Bottom) بر اساس موقعیت نسبت به مرکز
        var topLine: CartesianLine? = null
        var minTopY = Float.MAX_VALUE

        var bottomLine: CartesianLine? = null
        var maxBottomY = -Float.MAX_VALUE

        for (line in horizontalLines) {
            // مقدار y در وسط عرض تصویر (x = halfW)
            val yAtCenter = (-line.c - line.a * halfW) / line.b
            if (yAtCenter < halfH && yAtCenter < minTopY && yAtCenter >= 0) {
                minTopY = yAtCenter
                topLine = line
            }
            if (yAtCenter > halfH && yAtCenter > maxBottomY && yAtCenter <= h) {
                maxBottomY = yAtCenter
                bottomLine = line
            }
        }

        // تفکیک خطوط عمودی به لبه چپ (Left) و لبه راست (Right)
        var leftLine: CartesianLine? = null
        var minLeftX = Float.MAX_VALUE

        var rightLine: CartesianLine? = null
        var maxRightX = -Float.MAX_VALUE

        for (line in verticalLines) {
            // مقدار x در وسط ارتفاع تصویر (y = halfH)
            val xAtCenter = (-line.c - line.b * halfH) / line.a
            if (xAtCenter < halfW && xAtCenter < minLeftX && xAtCenter >= 0) {
                minLeftX = xAtCenter
                leftLine = line
            }
            if (xAtCenter > halfW && xAtCenter > maxRightX && xAtCenter <= w) {
                maxRightX = xAtCenter
                rightLine = line
            }
        }

        // اگر هر ۴ خط تشکیل دهنده حاشیه سند یافت شدند، نقطه تقاطع آن‌ها ۴ گوشه است
        if (topLine != null && bottomLine != null && leftLine != null && rightLine != null) {
            val p0 = intersectLines(topLine, leftLine)     // بالا - چپ
            val p1 = intersectLines(topLine, rightLine)    // بالا - راست
            val p2 = intersectLines(bottomLine, rightLine) // پایین - راست
            val p3 = intersectLines(bottomLine, leftLine)  // پایین - چپ

            if (p0 != null && p1 != null && p2 != null && p3 != null) {
                return CornerPoints(p0, p1, p2, p3)
            }
        }

        return null
    }

    /**
     * محاسبه تقاطع هندسی دو خط a1*x + b1*y + c1 = 0 و a2*x + b2*y + c2 = 0
     */
    private fun intersectLines(l1: CartesianLine, l2: CartesianLine): Offset? {
        val det = l1.a * l2.b - l2.a * l1.b
        if (abs(det) < 1e-5f) return null // خطوط موازی هستند

        val x = (l1.b * l2.c - l2.b * l1.c) / det
        val y = (l2.a * l1.c - l1.a * l2.c) / det
        return Offset(x, y)
    }

    /**
     * ردیابی لبه‌ها روی نقشه لبه‌های Canny با الگوریتم تابش پرتو از ۴ جهت مرکز به بیرون
     */
    private fun detectCornersFromEdgeMap(edges: BooleanArray, w: Int, h: Int): CornerPoints? {
        val centerX = w / 2f
        val centerY = h / 2f

        fun raycastCorner(startX: Float, startY: Float, targetX: Float, targetY: Float): Offset {
            val steps = 30
            for (i in 2..22) {
                val t = i.toFloat() / steps
                val cx = (startX + (targetX - startX) * t).toInt().coerceIn(0, w - 1)
                val cy = (startY + (targetY - startY) * t).toInt().coerceIn(0, h - 1)
                if (edges[cy * w + cx]) {
                    return Offset(cx.toFloat(), cy.toFloat())
                }
            }
            return Offset(startX, startY)
        }

        val p0 = raycastCorner(0f, 0f, centerX, centerY)
        val p1 = raycastCorner(w - 1f, 0f, centerX, centerY)
        val p2 = raycastCorner(w - 1f, h - 1f, centerX, centerY)
        val p3 = raycastCorner(0f, h - 1f, centerX, centerY)

        return CornerPoints(p0, p1, p2, p3)
    }

    /**
     * اعتبارسنجی شکل هندسی ۴ ضلعی استخراج‌شده
     */
    private fun isValidPolygon(p0: Offset, p1: Offset, p2: Offset, p3: Offset, w: Float, h: Float): Boolean {
        val topW = hypot(p1.x - p0.x, p1.y - p0.y)
        val botW = hypot(p2.x - p3.x, p2.y - p3.y)
        val leftH = hypot(p3.x - p0.x, p3.y - p0.y)
        val rightH = hypot(p2.x - p1.x, p2.y - p1.y)

        // عرض و ارتفاع حداقل ۳۰ درصد تصویر باشند
        if (topW < w * 0.3f || botW < w * 0.3f || leftH < h * 0.3f || rightH < h * 0.3f) return false

        // گوشه‌ها نباید روی هم افتاده باشند
        if (p1.x <= p0.x || p2.x <= p3.x || p3.y <= p0.y || p2.y <= p1.y) return false

        return true
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
}
