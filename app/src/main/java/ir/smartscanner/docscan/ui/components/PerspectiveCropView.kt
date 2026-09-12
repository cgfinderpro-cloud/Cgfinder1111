package ir.smartscanner.docscan.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.smartscanner.docscan.ui.theme.PrimaryBlue
import ir.smartscanner.docscan.ui.theme.PrimaryBlueDark
import ir.smartscanner.docscan.util.CornerPoints
import ir.smartscanner.docscan.util.PerspectiveCropEngine
import kotlin.math.hypot
import kotlin.math.roundToInt

@Composable
fun PerspectiveCropView(
    initialBitmap: Bitmap,
    onConfirmCrop: (Bitmap) -> Unit,
    onCancel: () -> Unit
) {
    var workingBitmap by remember(initialBitmap) { mutableStateOf(initialBitmap) }

    // محاسبه اولیه هوشمند گوشه‌ها
    var corners by remember(workingBitmap) {
        mutableStateOf(PerspectiveCropEngine.detectDocumentCorners(workingBitmap))
    }

    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var activeHandleIndex by remember { mutableStateOf<Int?>(null) }
    val density = LocalDensity.current.density

    // نگهداری حالت‌های به‌روزشده برای جلوگیری از لغو جسچر هنگام درگ
    val currentCornersState = rememberUpdatedState(corners)
    val currentActiveIndexState = rememberUpdatedState(activeHandleIndex)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A)) // زمینه تیره جهت کنتراست حداکثری با سند
    ) {
        // نوار بالای صفحه برش
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF1E293B),
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onCancel) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "انصراف",
                        tint = Color.White
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "تنظیم و برش لبه‌های مدرک",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "گوشه‌های آبی را جهت تراز دقیق جابجا کنید",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 11.sp
                    )
                }

                IconButton(
                    onClick = {
                        val rotated = PerspectiveCropEngine.rotateBitmap(workingBitmap, 90f)
                        workingBitmap = rotated
                        corners = PerspectiveCropEngine.detectDocumentCorners(rotated)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.RotateRight,
                        contentDescription = "چرخش ۹۰ درجه",
                        tint = Color(0xFF38BDF8)
                    )
                }
            }
        }

        // محفظه تعاملی تصویر + کادر پرسپکتیو + رهگیری لمس روان
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(12.dp)
                .onSizeChanged { containerSize = it },
            contentAlignment = Alignment.Center
        ) {
            val cW = containerSize.width.toFloat()
            val cH = containerSize.height.toFloat()

            if (cW > 0 && cH > 0) {
                val bW = workingBitmap.width.toFloat()
                val bH = workingBitmap.height.toFloat()

                val imageAspect = bW / bH
                val containerAspect = cW / cH

                val displayedW: Float
                val displayedH: Float
                val offsetX: Float
                val offsetY: Float

                if (imageAspect > containerAspect) {
                    displayedW = cW
                    displayedH = cW / imageAspect
                    offsetX = 0f
                    offsetY = (cH - displayedH) / 2f
                } else {
                    displayedH = cH
                    displayedW = cH * imageAspect
                    offsetX = (cW - displayedW) / 2f
                    offsetY = 0f
                }

                val scaleX = displayedW / bW
                val scaleY = displayedH / bH

                fun toScreen(bmpOffset: Offset): Offset {
                    return Offset(
                        x = bmpOffset.x * scaleX + offsetX,
                        y = bmpOffset.y * scaleY + offsetY
                    )
                }

                fun toBitmap(screenOffset: Offset): Offset {
                    val bx = ((screenOffset.x - offsetX) / scaleX).coerceIn(0f, bW)
                    val by = ((screenOffset.y - offsetY) / scaleY).coerceIn(0f, bH)
                    return Offset(bx, by)
                }

                val s0 = toScreen(corners.p0)
                val s1 = toScreen(corners.p1)
                val s2 = toScreen(corners.p2)
                val s3 = toScreen(corners.p3)
                val screenPoints = listOf(s0, s1, s2, s3)

                // ۱. تصویر پیش‌نمایش سند
                Image(
                    bitmap = workingBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Fit
                )

                // ۲. لایه ترسیم چندضلعی پرسپکتیو و خطوط راهنما روی Canvas
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val polygonPath = Path().apply {
                        moveTo(s0.x, s0.y)
                        lineTo(s1.x, s1.y)
                        lineTo(s2.x, s2.y)
                        lineTo(s3.x, s3.y)
                        close()
                    }

                    // لایه رنگی نیمه‌شفاف درون سند
                    drawPath(
                        path = polygonPath,
                        color = Color(0x332563EB)
                    )

                    // خطوط متقاطع شطرنجی ۳×۳ کم‌رنگ جهت تراز متون سند
                    val pTopMid1 = Offset(s0.x + (s1.x - s0.x) / 3f, s0.y + (s1.y - s0.y) / 3f)
                    val pBotMid1 = Offset(s3.x + (s2.x - s3.x) / 3f, s3.y + (s2.y - s3.y) / 3f)
                    drawLine(
                        color = Color.White.copy(alpha = 0.25f),
                        start = pTopMid1,
                        end = pBotMid1,
                        strokeWidth = 1.dp.toPx()
                    )

                    val pTopMid2 = Offset(s0.x + (s1.x - s0.x) * 2f / 3f, s0.y + (s1.y - s0.y) * 2f / 3f)
                    val pBotMid2 = Offset(s3.x + (s2.x - s3.x) * 2f / 3f, s3.y + (s2.y - s3.y) * 2f / 3f)
                    drawLine(
                        color = Color.White.copy(alpha = 0.25f),
                        start = pTopMid2,
                        end = pBotMid2,
                        strokeWidth = 1.dp.toPx()
                    )

                    val pLeftMid1 = Offset(s0.x + (s3.x - s0.x) / 3f, s0.y + (s3.y - s0.y) / 3f)
                    val pRightMid1 = Offset(s1.x + (s2.x - s1.x) / 3f, s1.y + (s2.y - s1.y) / 3f)
                    drawLine(
                        color = Color.White.copy(alpha = 0.25f),
                        start = pLeftMid1,
                        end = pRightMid1,
                        strokeWidth = 1.dp.toPx()
                    )

                    val pLeftMid2 = Offset(s0.x + (s3.x - s0.x) * 2f / 3f, s0.y + (s3.y - s0.y) * 2f / 3f)
                    val pRightMid2 = Offset(s1.x + (s2.x - s1.x) * 2f / 3f, s1.y + (s2.y - s1.y) * 2f / 3f)
                    drawLine(
                        color = Color.White.copy(alpha = 0.25f),
                        start = pLeftMid2,
                        end = pRightMid2,
                        strokeWidth = 1.dp.toPx()
                    )

                    // خطوط پررنگ مرزی با رنگ فیروزه‌ای/آبی نئونی
                    drawPath(
                        path = polygonPath,
                        color = Color(0xFF38BDF8),
                        style = Stroke(width = 3.dp.toPx())
                    )
                }

                // ۳. لایه تعاملی بدون ری‌استارت شدن با pointerInput پایدار (حل مشکل ثابت ماندن کادر)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(workingBitmap, displayedW, displayedH) {
                            detectDragGestures(
                                onDragStart = { startPos ->
                                    val touchThreshold = 60.dp.toPx()
                                    var closestIndex: Int? = null
                                    var minDistance = Float.MAX_VALUE

                                    val curPts = currentCornersState.value.toList().map { toScreen(it) }
                                    curPts.forEachIndexed { index, pt ->
                                        val dist = hypot(pt.x - startPos.x, pt.y - startPos.y)
                                        if (dist < touchThreshold && dist < minDistance) {
                                            minDistance = dist
                                            closestIndex = index
                                        }
                                    }
                                    activeHandleIndex = closestIndex
                                },
                                onDragEnd = {
                                    activeHandleIndex = null
                                },
                                onDragCancel = {
                                    activeHandleIndex = null
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val index = currentActiveIndexState.value ?: return@detectDragGestures
                                    val curCorners = currentCornersState.value
                                    val currentScreenPt = toScreen(curCorners.toList()[index])
                                    val newScreenPt = currentScreenPt + dragAmount
                                    val newBmpPt = toBitmap(newScreenPt)
                                    corners = curCorners.withPoint(index, newBmpPt)
                                }
                            )
                        }
                ) {
                    // ۴. المان‌های بصری دستگیره‌های لمسی ۴ گوشه
                    screenPoints.forEachIndexed { index, pt ->
                        val isActive = activeHandleIndex == index
                        val handleRadius = if (isActive) 20.dp else 15.dp
                        val animatedRadius by animateFloatAsState(
                            targetValue = if (isActive) 20f else 15f,
                            label = "handleRadius"
                        )

                        Box(
                            modifier = Modifier
                                .offset {
                                    IntOffset(
                                        x = (pt.x - (animatedRadius * density)).roundToInt(),
                                        y = (pt.y - (animatedRadius * density)).roundToInt()
                                    )
                                }
                                .size((animatedRadius * 2).dp)
                                .shadow(8.dp, CircleShape)
                                .clip(CircleShape)
                                .background(Color.White)
                                .padding(3.dp)
                                .clip(CircleShape)
                                .background(if (isActive) Color(0xFF0284C7) else Color(0xFF0EA5E9)),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                            )
                        }
                    }

                    // ۵. ذره‌بین شناور (Magnifier) هنگام درگ انگشت برای تنظیم دقیق میلی‌متری
                    activeHandleIndex?.let { index ->
                        val activePt = screenPoints[index]
                        val bmpPt = corners.toList()[index]

                        // موقعیت ذره‌بین: بالای انگشت کاربر تا زیر دست پنهان نشود
                        val loupeSizeDp = 96.dp
                        val loupeOffsetYDp = 85.dp

                        val loupeBitmap = remember(bmpPt, workingBitmap) {
                            try {
                                PerspectiveCropEngine.extractMagnifierBitmap(
                                    source = workingBitmap,
                                    center = bmpPt,
                                    sizePx = 140
                                )
                            } catch (e: Exception) {
                                null
                            }
                        }

                        Box(
                            modifier = Modifier
                                .offset {
                                    IntOffset(
                                        x = (activePt.x - (loupeSizeDp.toPx() / 2f)).roundToInt()
                                            .coerceIn(10, (cW - loupeSizeDp.toPx() - 10).roundToInt()),
                                        y = (activePt.y - loupeOffsetYDp.toPx() - (loupeSizeDp.toPx() / 2f)).roundToInt()
                                            .coerceIn(10, (cH - loupeSizeDp.toPx() - 10).roundToInt())
                                    )
                                }
                                .size(loupeSizeDp)
                                .shadow(12.dp, CircleShape)
                                .clip(CircleShape)
                                .background(Color.Black)
                                .border(3.dp, Color(0xFF38BDF8), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (loupeBitmap != null) {
                                Image(
                                    bitmap = loupeBitmap.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            // نشانه‌گیر مرکزی ذره‌بین (Crosshair Reticle)
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val c = center
                                drawLine(
                                    color = Color(0xFF38BDF8),
                                    start = Offset(c.x - 12.dp.toPx(), c.y),
                                    end = Offset(c.x + 12.dp.toPx(), c.y),
                                    strokeWidth = 2.dp.toPx()
                                )
                                drawLine(
                                    color = Color(0xFF38BDF8),
                                    start = Offset(c.x, c.y - 12.dp.toPx()),
                                    end = Offset(c.x, c.y + 12.dp.toPx()),
                                    strokeWidth = 2.dp.toPx()
                                )
                                drawCircle(
                                    color = Color.White,
                                    radius = 3.dp.toPx(),
                                    center = c
                                )
                            }
                        }
                    }
                }
            }
        }

        // نوار ابزارهای پایینی و دکمه تایید مرحله ۲
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF1E293B),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // دکمه‌های تشخیص خودکار و تمام‌صفحه
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            corners = PerspectiveCropEngine.detectDocumentCorners(workingBitmap)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoFixHigh,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "تشخیص هوشمند",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            corners = PerspectiveCropEngine.getDefaultCorners(
                                workingBitmap.width.toFloat(),
                                workingBitmap.height.toFloat(),
                                marginFactor = 0.02f
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.CropFree,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "کادر کامل",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // دکمه اصلی تأیید کادر و هدایت به مرحله سوم (پیش‌نمایش و فیلترها)
                Button(
                    onClick = {
                        val cropped = PerspectiveCropEngine.cropPerspective(workingBitmap, corners)
                        onConfirmCrop(cropped)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryBlue,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "تأیید کادر و ادامه به پیش‌نمایش",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}
