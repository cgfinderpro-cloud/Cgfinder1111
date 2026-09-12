package ir.smartscanner.docscan

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ir.smartscanner.docscan.model.DocumentItem
import ir.smartscanner.docscan.model.ScanFilter
import ir.smartscanner.docscan.ui.components.PerspectiveCropView
import ir.smartscanner.docscan.ui.navigation.Screen
import ir.smartscanner.docscan.ui.screens.HomeScreen
import ir.smartscanner.docscan.ui.screens.PreviewScreen
import ir.smartscanner.docscan.ui.theme.SmartScannerTheme
import ir.smartscanner.docscan.util.DocFilterEngine
import ir.smartscanner.docscan.util.DocStorageManager
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // پشتیبانی کامل از زبان فارسی و چینش راست به چپ (RTL)
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                SmartScannerTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        SmartScannerApp()
                    }
                }
            }
        }
    }
}

@Composable
fun SmartScannerApp() {
    val navController = rememberNavController()
    val context = LocalContext.current

    // ۱. لیست اسناد واقعی خوانده‌شده از دیتابیس/حافظه محلی
    var documentList by remember { mutableStateOf<List<DocumentItem>>(emptyList()) }

    // تصویر خام دریافت‌شده از دوربین یا گالری جهت ورود به مرحله ۲ (برش و پرسپکتیو)
    var rawCapturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var rawCapturedTitle by remember { mutableStateOf("") }

    // سند آماده‌شده پس از برش جهت ورود به مرحله ۳ (پیش‌نمایش و فیلترها)
    var pendingDocument by remember { mutableStateOf<DocumentItem?>(null) }

    // متغیر کمکی برای آدرس موقت تصویر دوربین
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    // بارگذاری اسناد واقعی ذخیره‌شده
    fun refreshDocuments() {
        documentList = DocStorageManager.getAllDocuments(context)
    }

    LaunchedEffect(Unit) {
        refreshDocuments()
    }

    // هدایت به مرحله ۲: برش و تنظیم پرسپکتیو لبه‌ها بلافاصله پس از عکس‌برداری/گالری
    fun startCropStage(bitmap: Bitmap, title: String) {
        rawCapturedBitmap = bitmap
        rawCapturedTitle = title
        navController.navigate(Screen.Crop.route)
    }

    // ۲. لانچر عکس‌برداری با کیفیت بالا
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraUri != null) {
            val bitmap = DocFilterEngine.loadBitmapFromUri(context, tempCameraUri!!)
            if (bitmap != null) {
                startCropStage(bitmap, "سند دوربین - ${DocStorageManager.getPersianDateNow()}")
            } else {
                Toast.makeText(context, "خطا در بارگذاری تصویر دوربین", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // لانچر کمکی پیش‌نمایش در صورت محدودیت FileProvider
    val previewCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            startCropStage(bitmap, "سند دوربین - ${DocStorageManager.getPersianDateNow()}")
        }
    }

    // تابع راه‌اندازی ایمن دوربین
    val launchCameraDirectly = {
        try {
            val cameraDir = File(context.cacheDir, "camera").apply { if (!exists()) mkdirs() }
            val photoFile = File(cameraDir, "camera_doc_${System.currentTimeMillis()}.jpg")
            val photoUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                photoFile
            )
            tempCameraUri = photoUri
            takePictureLauncher.launch(photoUri)
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                previewCameraLauncher.launch(null)
            } catch (ex: Exception) {
                Toast.makeText(context, "خطا در باز کردن دوربین: ${ex.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ۳. لانچر درخواست مجوز دسترسی به دوربین
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchCameraDirectly()
        } else {
            Toast.makeText(
                context,
                "جهت عکاسی از اسناد، دسترسی به دوربین الزامی است",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val onLaunchCamera = {
        val permission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        if (permission == PackageManager.PERMISSION_GRANTED) {
            launchCameraDirectly()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // ۴. لانچر انتخاب تصویر از گالری
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val loadedBitmap = DocFilterEngine.loadBitmapFromUri(context, uri)
            if (loadedBitmap != null) {
                startCropStage(loadedBitmap, "سند گالری - ${DocStorageManager.getPersianDateNow()}")
            } else {
                Toast.makeText(context, "خطا در خواندن تصویر از گالری", Toast.LENGTH_SHORT).show()
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        // مرحله ۱: صفحه اصلی (Home)
        composable(Screen.Home.route) {
            HomeScreen(
                documents = documentList,
                onOpenDocument = { docId ->
                    navController.navigate(Screen.Preview.createRoute(docId))
                },
                onDeleteDocument = { docId ->
                    DocStorageManager.deleteDocument(context, docId)
                    refreshDocuments()
                    Toast.makeText(context, "مدرک با موفقیت حذف شد", Toast.LENGTH_SHORT).show()
                },
                onLaunchCamera = onLaunchCamera,
                onLaunchGallery = {
                    galleryLauncher.launch("image/*")
                }
            )
        }

        // مرحله ۲: صفحه اختصاصی برش و تنظیم پرسپکتیو لبه‌ها (Crop Screen)
        composable(Screen.Crop.route) {
            val rawBmp = rawCapturedBitmap
            if (rawBmp != null) {
                PerspectiveCropView(
                    initialBitmap = rawBmp,
                    onConfirmCrop = { croppedBitmap ->
                        // ساخت سند موقت با تصویر تراز شده و ورود به مرحله ۳
                        val newDocId = "new_${System.currentTimeMillis()}"
                        val newDoc = DocumentItem(
                            id = newDocId,
                            title = rawCapturedTitle.ifEmpty { "سند جدید - ${DocStorageManager.getPersianDateNow()}" },
                            datePersian = DocStorageManager.getPersianDateNow(),
                            filter = ScanFilter.PHOTOCOPY,
                            pageCount = 1,
                            bitmap = croppedBitmap
                        )
                        pendingDocument = newDoc
                        rawCapturedBitmap = null

                        navController.navigate(Screen.Preview.createRoute(newDocId)) {
                            popUpTo(Screen.Crop.route) { inclusive = true }
                        }
                    },
                    onCancel = {
                        rawCapturedBitmap = null
                        navController.popBackStack()
                    }
                )
            } else {
                LaunchedEffect(Unit) {
                    navController.popBackStack()
                }
            }
        }

        // مرحله ۳: صفحه پیش‌نمایش، اعمال فیلترها و ذخیره سند (Preview Screen)
        composable(
            route = Screen.Preview.route,
            arguments = listOf(
                navArgument("docId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val docId = backStackEntry.arguments?.getString("docId")
            val document = if (docId == pendingDocument?.id) {
                pendingDocument
            } else {
                documentList.find { it.id == docId }
            }

            PreviewScreen(
                document = document,
                onBack = {
                    navController.popBackStack()
                },
                onSaveSuccess = {
                    refreshDocuments()
                    pendingDocument = null
                    navController.popBackStack(Screen.Home.route, inclusive = false)
                }
            )
        }
    }
}
