package ir.smartscanner.docscan

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ir.smartscanner.docscan.model.DocumentItem
import ir.smartscanner.docscan.model.ScanFilter
import ir.smartscanner.docscan.ui.navigation.Screen
import ir.smartscanner.docscan.ui.screens.HomeScreen
import ir.smartscanner.docscan.ui.screens.PreviewScreen
import ir.smartscanner.docscan.ui.theme.SmartScannerTheme
import ir.smartscanner.docscan.util.DocFilterEngine

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // پشتیبانی کامل از زبان فارسی و راست‌چین (RTL)
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

    // داده‌های اولیه مدارک اخیر (با امکان اضافه شدن اسناد جدید دوربین و گالری)
    var documentList by remember {
        mutableStateOf(
            listOf(
                DocumentItem(
                    id = "doc-1",
                    title = "شناسنامه و کارت ملی هوشمند",
                    datePersian = "۲۲ اردیبهشت ۱۴۰۳",
                    filter = ScanFilter.PHOTOCOPY,
                    pageCount = 2
                ),
                DocumentItem(
                    id = "doc-2",
                    title = "قرارداد کاری و سفته بانکی",
                    datePersian = "۱۸ اردیبهشت ۱۴۰۳",
                    filter = ScanFilter.CLEAR_COLOR,
                    pageCount = 4
                ),
                DocumentItem(
                    id = "doc-3",
                    title = "قبض بیمه و گواهی مهارت فنی",
                    datePersian = "۱۰ اردیبهشت ۱۴۰۳",
                    filter = ScanFilter.BLACK_AND_WHITE,
                    pageCount = 1
                )
            )
        )
    }

    // ۱. اتصال واقعی دوربین با لانچر استاندارد ActivityResultContracts.TakePicturePreview
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            val newDocId = "doc-${System.currentTimeMillis()}"
            val newDoc = DocumentItem(
                id = newDocId,
                title = "اسکن دوربین ${documentList.size + 1}",
                datePersian = "امروز",
                filter = ScanFilter.PHOTOCOPY,
                pageCount = 1,
                bitmap = bitmap
            )
            documentList = listOf(newDoc) + documentList
            navController.navigate(Screen.Preview.createRoute(newDocId))
        }
    }

    // ۲. اتصال واقعی گالری دستگاه با لانچر استاندارد ActivityResultContracts.GetContent
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val loadedBitmap = DocFilterEngine.loadBitmapFromUri(context, uri)
            if (loadedBitmap != null) {
                val newDocId = "doc-${System.currentTimeMillis()}"
                val newDoc = DocumentItem(
                    id = newDocId,
                    title = "مدرک گالری ${documentList.size + 1}",
                    datePersian = "امروز",
                    filter = ScanFilter.PHOTOCOPY,
                    pageCount = 1,
                    bitmap = loadedBitmap,
                    imageUri = uri.toString()
                )
                documentList = listOf(newDoc) + documentList
                navController.navigate(Screen.Preview.createRoute(newDocId))
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        // صفحه اصلی (Home)
        composable(Screen.Home.route) {
            HomeScreen(
                documents = documentList,
                onOpenDocument = { docId ->
                    navController.navigate(Screen.Preview.createRoute(docId))
                },
                onLaunchCamera = {
                    cameraLauncher.launch(null)
                },
                onLaunchGallery = {
                    galleryLauncher.launch("image/*")
                }
            )
        }

        // صفحه پیش‌نمایش و فیلترها (Preview Screen)
        composable(
            route = Screen.Preview.route,
            arguments = listOf(
                navArgument("docId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val docId = backStackEntry.arguments?.getString("docId")
            val document = documentList.find { it.id == docId }

            PreviewScreen(
                document = document,
                onBack = {
                    navController.popBackStack()
                },
                onSave = { updatedFilter, updatedBitmap ->
                    if (document != null) {
                        documentList = documentList.map {
                            if (it.id == document.id) {
                                it.copy(
                                    filter = updatedFilter,
                                    bitmap = updatedBitmap ?: it.bitmap
                                )
                            } else {
                                it
                            }
                        }
                    }
                },
                onShare = { _, _ ->
                    // هندل شده درون PreviewScreen با DocFilterEngine.shareBitmap
                }
            )
        }
    }
}
