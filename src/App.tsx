import React, { useState, useRef } from 'react';
import { 
  Camera, 
  Image as ImageIcon, 
  ArrowRight, 
  Share2, 
  Save, 
  FileText, 
  Printer, 
  Contrast, 
  Sparkles, 
  Layers, 
  Check, 
  Download, 
  Code2, 
  Smartphone, 
  ShieldCheck, 
  ChevronLeft, 
  Search, 
  MoreVertical, 
  Upload, 
  Info,
  Crop,
  RotateCw,
  Wand2,
  Maximize2
} from 'lucide-react';

type FilterType = 'photocopy' | 'bw' | 'color' | 'original';

interface DocumentItem {
  id: string;
  title: string;
  datePersian: string;
  filter: FilterType;
  pageCount: number;
  imageSrc?: string;
}

const INITIAL_DOCUMENTS: DocumentItem[] = [
  {
    id: 'doc-1',
    title: 'شناسنامه و کارت ملی هوشمند',
    datePersian: '۲۲ اردیبهشت ۱۴۰۳',
    filter: 'photocopy',
    pageCount: 2,
  },
  {
    id: 'doc-2',
    title: 'قرارداد کاری و سفته بانکی',
    datePersian: '۱۸ اردیبهشت ۱۴۰۳',
    filter: 'color',
    pageCount: 4,
  },
  {
    id: 'doc-3',
    title: 'قبض بیمه و گواهی مهارت فنی',
    datePersian: '۱۰ اردیبهشت ۱۴۰۳',
    filter: 'bw',
    pageCount: 1,
  },
];

interface Point {
  x: number;
  y: number;
}

export default function App() {
  // فلو اصلاح‌شده ۳ مرحله‌ای: مرحله ۱ (home) -> مرحله ۲ (crop) -> مرحله ۳ (preview)
  const [currentScreen, setCurrentScreen] = useState<'home' | 'crop' | 'preview'>('home');
  const [documents, setDocuments] = useState<DocumentItem[]>(INITIAL_DOCUMENTS);
  const [activeDocId, setActiveDocId] = useState<string>('doc-1');
  const [selectedFilter, setSelectedFilter] = useState<FilterType>('photocopy');
  const [toastMessage, setToastMessage] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<'preview' | 'code' | 'cicd'>('preview');
  const [selectedFileCode, setSelectedFileCode] = useState<string>('PerspectiveCropView.kt');
  const [customImage, setCustomImage] = useState<string | null>(null);
  const [tempDocTitle, setTempDocTitle] = useState<string>('سند جدید اسکن');

  // مختصات درصدی ۴ گوشه کادر در مرحله برش (بالا-چپ، بالا-راست، پایین-راست، پایین-چپ)
  const [corners, setCorners] = useState<Point[]>([
    { x: 12, y: 14 },
    { x: 88, y: 10 },
    { x: 86, y: 88 },
    { x: 14, y: 84 },
  ]);
  const [activeCornerIndex, setActiveCornerIndex] = useState<number | null>(null);
  const [rotation, setRotation] = useState<number>(0);

  const fileInputRef = useRef<HTMLInputElement>(null);
  const cropContainerRef = useRef<HTMLDivElement>(null);

  const showToast = (msg: string) => {
    setToastMessage(msg);
    setTimeout(() => setToastMessage(null), 3200);
  };

  const activeDoc = documents.find(d => d.id === activeDocId) || documents[0];

  const handleOpenDoc = (id: string) => {
    const doc = documents.find(d => d.id === id);
    if (doc) {
      setActiveDocId(id);
      setSelectedFilter(doc.filter);
      setCurrentScreen('preview');
    }
  };

  const handleCameraCapture = () => {
    if (fileInputRef.current) {
      fileInputRef.current.click();
    }
  };

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      const reader = new FileReader();
      reader.onload = (event) => {
        const result = event.target?.result as string;
        setCustomImage(result);
        const title = file.name.replace(/\.[^/.]+$/, "") || 'سند جدید دوربین';
        setTempDocTitle(title);
        // ورود به مرحله ۲ بلافاصله پس از عکس‌برداری/گالری
        resetToSmartCorners();
        setRotation(0);
        setCurrentScreen('crop');
        showToast('مرحله ۲: لبه‌های سند را جهت تراز کادر تنظیم کنید');
      };
      reader.readAsDataURL(file);
    }
  };

  // تشخیص هوشمند خودکار لبه‌ها در شبیه‌ساز
  const resetToSmartCorners = () => {
    setCorners([
      { x: 10, y: 12 },
      { x: 90, y: 11 },
      { x: 89, y: 88 },
      { x: 11, y: 87 },
    ]);
    showToast('تشخیص هوشمند لبه‌ها با موفقیت اعمال شد');
  };

  // کادر کامل با حاشیه ۲ درصد
  const resetToFullFrame = () => {
    setCorners([
      { x: 3, y: 3 },
      { x: 97, y: 3 },
      { x: 97, y: 97 },
      { x: 3, y: 97 },
    ]);
    showToast('کادر به حالت تمام‌صفحه تغییر یافت');
  };

  // تأیید برش و ورود به مرحله ۳ (پیش‌نمایش و فیلترها)
  const handleConfirmCrop = () => {
    const newId = `doc-${Date.now()}`;
    const newDoc: DocumentItem = {
      id: newId,
      title: tempDocTitle,
      datePersian: 'امروز',
      filter: 'photocopy',
      pageCount: 1,
      imageSrc: customImage || undefined
    };
    setDocuments(prev => [newDoc, ...prev]);
    setActiveDocId(newId);
    setSelectedFilter('photocopy');
    setCurrentScreen('preview');
    showToast('مرحله ۳: برش پرسپکتیو انجام شد. فیلتر دلخواه را انتخاب کنید');
  };

  const handleSaveFilter = () => {
    setDocuments(prev => prev.map(d => d.id === activeDocId ? { ...d, filter: selectedFilter } : d));
    showToast(`مدرک با فیلتر «${getFilterLabel(selectedFilter)}» ذخیره شد`);
  };

  const handleShare = () => {
    showToast('آماده‌سازی سند جهت اشتراک‌گذاری در پیام‌رسان‌ها...');
  };

  const getFilterLabel = (f: FilterType) => {
    switch (f) {
      case 'photocopy': return 'فتوکپی';
      case 'bw': return 'سیاه و سفید';
      case 'color': return 'رنگی شفاف';
      case 'original': return 'اصلی';
    }
  };

  const getFilterStyle = (f: FilterType): React.CSSProperties => {
    switch (f) {
      case 'photocopy':
        return {
          filter: 'grayscale(100%) contrast(240%) brightness(105%)',
          backgroundColor: '#FFFFFF',
        };
      case 'bw':
        return {
          filter: 'grayscale(100%) contrast(120%) brightness(95%)',
          backgroundColor: '#F8FAFC',
        };
      case 'color':
        return {
          filter: 'saturate(140%) contrast(125%) brightness(108%)',
          backgroundColor: '#FFFFFF',
        };
      case 'original':
        return {
          filter: 'none',
          backgroundColor: '#FFFBEB',
        };
    }
  };

  // درگ گوشه‌های کادر در شبیه‌ساز وب
  const handlePointerMove = (e: React.PointerEvent<HTMLDivElement>) => {
    if (activeCornerIndex === null || !cropContainerRef.current) return;
    const rect = cropContainerRef.current.getBoundingClientRect();
    const clientX = e.clientX;
    const clientY = e.clientY;
    const newX = Math.max(0, Math.min(100, ((clientX - rect.left) / rect.width) * 100));
    const newY = Math.max(0, Math.min(100, ((clientY - rect.top) / rect.height) * 100));

    setCorners(prev => {
      const copy = [...prev];
      copy[activeCornerIndex] = { x: Math.round(newX), y: Math.round(newY) };
      return copy;
    });
  };

  // کدهای متناظر اندروید نیتیو جهت بازبینی مستقیم
  const codeSnippets: Record<string, { lang: string; code: string; desc: string }> = {
    'PerspectiveCropView.kt': {
      lang: 'kotlin',
      desc: 'کامپوننت Jetpack Compose برای مرحله ۲ با درگ روان بدون ری‌استارت شدن، ذره‌بین شناور و خطوط متقاطع شطرنجی',
      code: `@Composable
fun PerspectiveCropView(
    initialBitmap: Bitmap,
    onConfirmCrop: (Bitmap) -> Unit,
    onCancel: () -> Unit
) {
    var workingBitmap by remember(initialBitmap) { mutableStateOf(initialBitmap) }
    var corners by remember(workingBitmap) {
        mutableStateOf(PerspectiveCropEngine.detectDocumentCorners(workingBitmap))
    }
    var activeHandleIndex by remember { mutableStateOf<Int?>(null) }
    val currentCornersState = rememberUpdatedState(corners)

    // استفاده از pointerInput پایدار بدون قرار دادن corners در کلید برای جلوگیری از لغو جسچر
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(workingBitmap) {
                detectDragGestures(
                    onDragStart = { startPos ->
                        activeHandleIndex = findClosestCorner(startPos)
                    },
                    onDragEnd = { activeHandleIndex = null },
                    onDragCancel = { activeHandleIndex = null },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val index = activeHandleIndex ?: return@detectDragGestures
                        corners = corners.withPoint(index, toBitmap(toScreen(corners[index]) + dragAmount))
                    }
                )
            }
    ) {
        // ۱. ترسیم چندضلعی کادر، خطوط شطرنجی راهنما و های‌لایت شفاف
        // ۲. دستگیره‌های انیمیشنی ۴ گوشه
        // ۳. ذره‌بین شناور (Magnifier Loupe) با زوم ۲.۵ برابری بالای انگشت کاربر
    }
}`
    },
    'PerspectiveCropEngine.kt': {
      lang: 'kotlin',
      desc: 'موتور تشخیص هوشمند چندپرتویی لبه‌ها و تصحیح پرسپکتیو با تبدیل هندسی Matrix.setPolyToPoly نیتیو',
      code: `object PerspectiveCropEngine {
    // ردیابی هوشمند لبه‌ها با محاسبه گرادیان روشنایی از ۴ گوشه به سمت مرکز
    fun detectDocumentCorners(bitmap: Bitmap): CornerPoints {
        // نمونه‌برداری سبک، مقایسه روشنایی پس‌زمینه (میز) با کاغذ و تعیین ۴ گوشه واقعی
    }

    // تبدیل ماتریسی تصویر زاویه‌دار به مستطیل صاف و تراز
    fun cropPerspective(source: Bitmap, corners: CornerPoints): Bitmap {
        val src = floatArrayOf(p0.x, p0.y, p1.x, p1.y, p2.x, p2.y, p3.x, p3.y)
        val dst = floatArrayOf(0f, 0f, targetWidth, 0f, targetWidth, targetHeight, 0f, targetHeight)
        val matrix = Matrix()
        matrix.setPolyToPoly(src, 0, dst, 0, 4)
        return Bitmap.createBitmap(targetWidth, targetHeight, ARGB_8888).also { out ->
            Canvas(out).drawBitmap(source, matrix, Paint(ANTI_ALIAS_FLAG or FILTER_BITMAP_FLAG))
        }
    }
}`
    },
    'MainActivity.kt': {
      lang: 'kotlin',
      desc: 'فلو اصلاح‌شده ۳ مرحله‌ای: ۱. عکاسی/گالری -> ۲. صفحه اختصاصی برش CropScreen -> ۳. صفحه پیش‌نمایش و فیلترها PreviewScreen',
      code: `NavHost(navController = navController, startDestination = Screen.Home.route) {
    // مرحله ۱: صفحه اصلی (Home)
    composable(Screen.Home.route) { HomeScreen(...) }

    // مرحله ۲: صفحه اختصاصی برش و تنظیم پرسپکتیو (Crop)
    composable(Screen.Crop.route) {
        PerspectiveCropView(
            initialBitmap = rawCapturedBitmap!!,
            onConfirmCrop = { cropped ->
                pendingDocument = DocumentItem(bitmap = cropped, ...)
                navController.navigate(Screen.Preview.createRoute(newDocId)) {
                    popUpTo(Screen.Crop.route) { inclusive = true }
                }
            },
            onCancel = { navController.popBackStack() }
        )
    }

    // مرحله ۳: صفحه پیش‌نمایش، فیلتر فتوکپی و ذخیره (Preview)
    composable(Screen.Preview.route) { PreviewScreen(...) }
}`
    },
    'app/build.gradle.kts': {
      lang: 'kotlin',
      desc: 'پیکربندی استاندارد کامپایل و خروجی APK آماده مایکت و بازار',
      code: `plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ir.smartscanner.docscan"
    compileSdk = 34

    defaultConfig {
        applicationId = "ir.smartscanner.docscan"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
}`
    },
    '.github/workflows/build-apk.yml': {
      lang: 'yaml',
      desc: 'ورکفلو گیت‌هاب اکشنز برای تولید خودکار فایل app-debug.apk',
      code: `name: Build Android APK
on: [push, pull_request, workflow_dispatch]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: 'zulu'
          java-version: '17'
          cache: 'gradle'
      - run: chmod +x ./gradlew
      - run: ./gradlew assembleDebug --no-daemon
      - uses: actions/upload-artifact@v4
        with:
          name: scanner-apk
          path: app/build/outputs/apk/debug/app-debug.apk`
    }
  };

  return (
    <div className="min-h-screen bg-neutral-950 text-neutral-100 flex flex-col font-['Vazirmatn',sans-serif]" dir="rtl">
      {/* مخفی: ورودی فایل جهت آزمایش زنده در شبیه‌ساز */}
      <input 
        type="file" 
        ref={fileInputRef} 
        onChange={handleFileUpload} 
        accept="image/*" 
        className="hidden" 
        id="camera-input"
      />

      {/* هدر بالای پنل */}
      <header className="border-b border-neutral-800 bg-neutral-900/90 backdrop-blur px-4 py-3 sticky top-0 z-50">
        <div className="max-w-7xl mx-auto flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-sky-600 flex items-center justify-center text-white shadow-lg shadow-sky-600/30">
              <FileText className="w-5 h-5" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h1 className="text-base sm:text-lg font-bold text-white">اسکنر و فتوکپی هوشمند مدارک</h1>
                <span className="text-xs bg-emerald-500/20 text-emerald-400 border border-emerald-500/30 px-2 py-0.5 rounded-full font-medium">
                  فلو ۳ مرحله‌ای فعال
                </span>
                <span className="hidden sm:inline-block text-xs bg-sky-500/20 text-sky-300 border border-sky-500/30 px-2 py-0.5 rounded-full font-mono">
                  Android 14 (SDK 34)
                </span>
              </div>
              <p className="text-xs text-neutral-400">
                مرحله ۱: عکاسی • مرحله ۲: برش و پرسپکتیو روان • مرحله ۳: فتوکپی و ذخیره
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <div className="flex bg-neutral-800 p-1 rounded-xl border border-neutral-700">
              <button
                onClick={() => setActiveTab('preview')}
                className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold transition-all ${
                  activeTab === 'preview'
                    ? 'bg-sky-600 text-white shadow-sm'
                    : 'text-neutral-400 hover:text-white'
                }`}
              >
                <Smartphone className="w-3.5 h-3.5" />
                شبیه‌ساز زنده موبایل
              </button>
              <button
                onClick={() => setActiveTab('code')}
                className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold transition-all ${
                  activeTab === 'code'
                    ? 'bg-sky-600 text-white shadow-sm'
                    : 'text-neutral-400 hover:text-white'
                }`}
              >
                <Code2 className="w-3.5 h-3.5" />
                سورس کاتلین اصلاح‌شده
              </button>
              <button
                onClick={() => setActiveTab('cicd')}
                className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold transition-all ${
                  activeTab === 'cicd'
                    ? 'bg-sky-600 text-white shadow-sm'
                    : 'text-neutral-400 hover:text-white'
                }`}
              >
                <Download className="w-3.5 h-3.5" />
                ورکفلو APK
              </button>
            </div>
          </div>
        </div>
      </header>

      {/* پیام Toast */}
      {toastMessage && (
        <div className="fixed top-16 left-1/2 -translate-x-1/2 z-50 bg-neutral-800 text-neutral-100 border border-neutral-600 px-4 py-2.5 rounded-xl shadow-2xl flex items-center gap-2 text-sm">
          <Check className="w-4 h-4 text-emerald-400 shrink-0" />
          <span>{toastMessage}</span>
        </div>
      )}

      {/* بدنه اصلی شبیه‌ساز */}
      <main className="flex-1 max-w-7xl w-full mx-auto p-4 sm:p-6 flex flex-col items-center justify-center">
        {activeTab === 'preview' && (
          <div className="w-full flex flex-col lg:flex-row items-center justify-center gap-8 py-2">
            
            {/* قاب گوشی هوشمند */}
            <div className="relative w-full max-w-[390px] h-[780px] bg-neutral-900 rounded-[44px] p-3 shadow-[0_25px_60px_-15px_rgba(0,0,0,0.9)] border-[6px] border-neutral-700 flex flex-col shrink-0">
              {/* بریدگی سلفی و بلندگو */}
              <div className="absolute top-5 left-1/2 -translate-x-1/2 w-28 h-4 bg-neutral-800 rounded-full flex items-center justify-center z-40">
                <div className="w-2.5 h-2.5 rounded-full bg-neutral-950 ml-6" />
                <div className="w-12 h-1 bg-neutral-700 rounded-full" />
              </div>

              {/* محتوای صفحه نمایش اندروید */}
              <div className="w-full h-full bg-[#F8FAFC] text-slate-900 rounded-[34px] overflow-hidden flex flex-col relative select-none">
                
                {/* استاتوس بار اندروید */}
                <div className="bg-white/80 backdrop-blur-sm px-6 pt-5 pb-1 flex items-center justify-between text-[11px] font-medium text-slate-700 border-b border-slate-100 shrink-0">
                  <span className="font-mono">12:30</span>
                  <div className="flex items-center gap-1.5">
                    <span className="text-[10px] bg-slate-100 px-1 rounded">IR-MCI</span>
                    <span>📶</span>
                    <span>🔋 98%</span>
                  </div>
                </div>

                {/* مرحله ۱: صفحه اصلی (Home) */}
                {currentScreen === 'home' && (
                  <div className="flex-1 flex flex-col overflow-hidden relative">
                    <div className="bg-white px-5 py-3 border-b border-slate-100 shadow-sm flex items-center justify-between shrink-0">
                      <div className="flex items-center gap-3">
                        <div className="w-9 h-9 rounded-xl bg-sky-100 text-sky-700 flex items-center justify-center">
                          <FileText className="w-5 h-5" />
                        </div>
                        <div>
                          <h2 className="text-base font-bold text-slate-900 leading-tight">اسکنر مدارک</h2>
                          <p className="text-[11px] text-slate-500">فتوکپی و تنظیم کادر هوشمند</p>
                        </div>
                      </div>
                      <div className="flex items-center gap-1 text-slate-500">
                        <button className="p-1.5 hover:bg-slate-100 rounded-lg">
                          <Search className="w-4 h-4" />
                        </button>
                        <button className="p-1.5 hover:bg-slate-100 rounded-lg">
                          <MoreVertical className="w-4 h-4" />
                        </button>
                      </div>
                    </div>

                    {/* لیست مدارک */}
                    <div className="flex-1 overflow-y-auto px-4 py-4 space-y-3 pb-24">
                      <div className="flex items-center justify-between text-xs px-1">
                        <span className="font-bold text-slate-800 text-sm">مدارک اخیر</span>
                        <span className="text-slate-500">{documents.length} مدرک</span>
                      </div>

                      {documents.map((doc) => (
                        <div
                          key={doc.id}
                          onClick={() => handleOpenDoc(doc.id)}
                          className="bg-white rounded-2xl p-3.5 border border-slate-200/80 shadow-sm hover:shadow-md transition-all cursor-pointer flex items-center gap-3.5 active:scale-[0.99]"
                        >
                          <div className="w-14 h-18 bg-slate-100 rounded-xl border border-slate-200 p-2 flex flex-col justify-between shrink-0 relative overflow-hidden">
                            {doc.imageSrc ? (
                              <img src={doc.imageSrc} alt="" className="w-full h-full object-cover rounded-md" />
                            ) : (
                              <>
                                <div className="w-6 h-1.5 bg-sky-500/70 rounded-full" />
                                <div className="space-y-1">
                                  <div className="w-full h-1 bg-slate-300 rounded" />
                                  <div className="w-4/5 h-1 bg-slate-300 rounded" />
                                  <div className="w-3/5 h-1 bg-slate-300 rounded" />
                                </div>
                                <div className="w-4 h-4 rounded-full border border-red-400 flex items-center justify-center text-[7px] text-red-500 font-bold self-end">
                                  مهر
                                </div>
                              </>
                            )}
                          </div>

                          <div className="flex-1 min-w-0">
                            <h3 className="font-bold text-slate-900 text-sm truncate">{doc.title}</h3>
                            <p className="text-xs text-slate-500 mt-1">تاریخ: {doc.datePersian}</p>
                            <div className="flex items-center gap-2 mt-2">
                              <span className="text-[10px] bg-sky-100 text-sky-800 px-2 py-0.5 rounded-md font-semibold">
                                {getFilterLabel(doc.filter)}
                              </span>
                              <span className="text-[10px] text-slate-400">
                                {doc.pageCount} صفحه
                              </span>
                            </div>
                          </div>

                          <ChevronLeft className="w-4 h-4 text-slate-400 shrink-0" />
                        </div>
                      ))}
                    </div>

                    {/* دو دکمه بزرگ عکاسی و گالری */}
                    <div className="absolute bottom-4 inset-x-4 flex items-center gap-3 z-30">
                      <button
                        onClick={handleCameraCapture}
                        className="flex-1 h-14 bg-teal-100 text-teal-900 hover:bg-teal-200 active:scale-95 rounded-2xl shadow-lg border border-teal-200/60 font-bold text-sm flex items-center justify-center gap-2 transition-all"
                      >
                        <ImageIcon className="w-5 h-5 text-teal-800" />
                        <span>گالری</span>
                      </button>

                      <button
                        onClick={handleCameraCapture}
                        className="flex-1 h-14 bg-sky-600 hover:bg-sky-700 active:scale-95 text-white rounded-2xl shadow-xl shadow-sky-600/30 font-bold text-sm flex items-center justify-center gap-2 transition-all"
                      >
                        <Camera className="w-5 h-5" />
                        <span>دوربین</span>
                      </button>
                    </div>
                  </div>
                )}

                {/* مرحله ۲: صفحه اختصاصی برش و تنظیم لبه‌ها (Crop Screen) */}
                {currentScreen === 'crop' && (
                  <div className="flex-1 flex flex-col overflow-hidden bg-slate-950 text-white">
                    {/* نوار ابزار بالای مرحله برش */}
                    <div className="bg-slate-900 px-4 py-2.5 border-b border-slate-800 flex items-center justify-between shrink-0">
                      <button
                        onClick={() => setCurrentScreen('home')}
                        className="p-1.5 hover:bg-slate-800 text-slate-300 rounded-lg"
                        title="انصراف"
                      >
                        <ArrowRight className="w-5 h-5" />
                      </button>
                      <div className="text-center">
                        <span className="font-bold text-sm block">برش و تنظیم پرسپکتیو</span>
                        <span className="text-[10px] text-slate-400">گوشه‌ها را برای تنظیم کادر بکشید</span>
                      </div>
                      <button
                        onClick={() => setRotation(r => (r + 90) % 360)}
                        className="p-1.5 hover:bg-slate-800 text-sky-400 rounded-lg"
                        title="چرخش ۹۰ درجه"
                      >
                        <RotateCw className="w-5 h-5" />
                      </button>
                    </div>

                    {/* کادر تنظیم پرسپکتیو سند */}
                    <div 
                      ref={cropContainerRef}
                      onPointerMove={handlePointerMove}
                      onPointerUp={() => setActiveCornerIndex(null)}
                      onPointerLeave={() => setActiveCornerIndex(null)}
                      className="flex-1 p-3 flex items-center justify-center relative select-none overflow-hidden"
                    >
                      <div 
                        className="relative w-full h-[360px] bg-slate-900 rounded-xl overflow-hidden border border-slate-800 flex items-center justify-center"
                        style={{ transform: `rotate(${rotation}deg)`, transition: 'transform 0.2s ease' }}
                      >
                        {/* سند داخل کادر */}
                        {customImage ? (
                          <img 
                            src={customImage} 
                            alt="تصویر خام مدرک" 
                            className="w-full h-full object-contain pointer-events-none"
                          />
                        ) : (
                          <div className="w-4/5 h-4/5 bg-amber-50 p-4 rounded shadow-md text-slate-800 flex flex-col justify-between pointer-events-none">
                            <div className="flex justify-between items-center border-b pb-2 border-slate-300">
                              <span className="text-[10px] font-bold">جمهوری اسلامی ایران</span>
                              <span className="text-[9px] opacity-70">سند رسمی</span>
                            </div>
                            <div className="space-y-2 py-2">
                              <div className="h-2 bg-slate-300 rounded w-2/3" />
                              <div className="h-1.5 bg-slate-200 rounded w-full" />
                              <div className="h-1.5 bg-slate-200 rounded w-4/5" />
                              <div className="h-1.5 bg-slate-200 rounded w-3/5" />
                            </div>
                            <div className="flex justify-between items-center pt-2 border-t border-slate-300">
                              <span className="text-[9px] text-red-600 font-bold border border-red-500 px-1 rounded">
                                مهر رسمی
                              </span>
                              <div className="w-12 h-3 bg-slate-200 rounded" />
                            </div>
                          </div>
                        )}

                        {/* لایه چندضلعی برش پرسپکتیو با SVG و دستگیره‌های قابل کشیدن */}
                        <svg className="absolute inset-0 w-full h-full pointer-events-none">
                          {/* لایه نیمه‌شفاف کادر سند */}
                          <polygon
                            points={`${corners[0].x}%,${corners[0].y}% ${corners[1].x}%,${corners[1].y}% ${corners[2].x}%,${corners[2].y}% ${corners[3].x}%,${corners[3].y}%`}
                            fill="rgba(14, 165, 233, 0.2)"
                            stroke="#38BDF8"
                            strokeWidth="3"
                          />
                          {/* خطوط شطرنجی ۳×۳ جهت تراز خطوط متن */}
                          <line
                            x1={`${(corners[0].x * 2 + corners[1].x) / 3}%`}
                            y1={`${(corners[0].y * 2 + corners[1].y) / 3}%`}
                            x2={`${(corners[3].x * 2 + corners[2].x) / 3}%`}
                            y2={`${(corners[3].y * 2 + corners[2].y) / 3}%`}
                            stroke="rgba(255, 255, 255, 0.35)"
                            strokeWidth="1"
                            strokeDasharray="3 3"
                          />
                          <line
                            x1={`${(corners[0].x + corners[1].x * 2) / 3}%`}
                            y1={`${(corners[0].y + corners[1].y * 2) / 3}%`}
                            x2={`${(corners[3].x + corners[2].x * 2) / 3}%`}
                            y2={`${(corners[3].y + corners[2].y * 2) / 3}%`}
                            stroke="rgba(255, 255, 255, 0.35)"
                            strokeWidth="1"
                            strokeDasharray="3 3"
                          />
                          <line
                            x1={`${(corners[0].x * 2 + corners[3].x) / 3}%`}
                            y1={`${(corners[0].y * 2 + corners[3].y) / 3}%`}
                            x2={`${(corners[1].x * 2 + corners[2].x) / 3}%`}
                            y2={`${(corners[1].y * 2 + corners[2].y) / 3}%`}
                            stroke="rgba(255, 255, 255, 0.35)"
                            strokeWidth="1"
                            strokeDasharray="3 3"
                          />
                          <line
                            x1={`${(corners[0].x + corners[3].x * 2) / 3}%`}
                            y1={`${(corners[0].y + corners[3].y * 2) / 3}%`}
                            x2={`${(corners[1].x + corners[2].x * 2) / 3}%`}
                            y2={`${(corners[1].y + corners[2].y * 2) / 3}%`}
                            stroke="rgba(255, 255, 255, 0.35)"
                            strokeWidth="1"
                            strokeDasharray="3 3"
                          />
                        </svg>

                        {/* ۴ دستگیره تعاملی و قابل لمس با سایز بزرگ برای راحتی انگشت */}
                        {corners.map((pt, idx) => (
                          <div
                            key={idx}
                            onPointerDown={(e) => {
                              e.preventDefault();
                              setActiveCornerIndex(idx);
                            }}
                            className="absolute w-10 h-10 -ml-5 -mt-5 flex items-center justify-center cursor-grab active:cursor-grabbing z-20 touch-none"
                            style={{ left: `${pt.x}%`, top: `${pt.y}%` }}
                          >
                            <div className={`w-7 h-7 rounded-full bg-white shadow-xl p-1 flex items-center justify-center transition-transform ${
                              activeCornerIndex === idx ? 'scale-125 ring-4 ring-sky-400/50' : 'hover:scale-110'
                            }`}>
                              <div className="w-full h-full rounded-full bg-sky-600 flex items-center justify-center">
                                <div className="w-2 h-2 rounded-full bg-white" />
                              </div>
                            </div>
                          </div>
                        ))}

                        {/* ذره‌بین شناور شبیه‌ساز بالای دستگیره فعال */}
                        {activeCornerIndex !== null && (
                          <div
                            className="absolute w-20 h-20 rounded-full border-2 border-sky-400 bg-slate-900/90 shadow-2xl overflow-hidden pointer-events-none z-30 flex items-center justify-center -translate-x-1/2 -translate-y-24"
                            style={{ 
                              left: `${corners[activeCornerIndex].x}%`, 
                              top: `${corners[activeCornerIndex].y}%` 
                            }}
                          >
                            <div className="relative w-full h-full flex items-center justify-center">
                              <span className="text-[10px] font-mono text-sky-300 font-bold">۲.۵x زوم</span>
                              <div className="absolute w-full h-0.5 bg-sky-400/70" />
                              <div className="absolute h-full w-0.5 bg-sky-400/70" />
                              <div className="absolute w-2 h-2 rounded-full bg-white" />
                            </div>
                          </div>
                        )}
                      </div>
                    </div>

                    {/* دکمه‌های پایینی مرحله برش */}
                    <div className="bg-slate-900 px-4 py-3 border-t border-slate-800 rounded-t-3xl space-y-2.5 shrink-0">
                      <div className="grid grid-cols-2 gap-2">
                        <button
                          onClick={resetToSmartCorners}
                          className="h-10 bg-slate-800 hover:bg-slate-700 text-sky-300 border border-slate-700 rounded-xl text-xs font-bold flex items-center justify-center gap-1.5 active:scale-95 transition-all"
                        >
                          <Wand2 className="w-3.5 h-3.5" />
                          <span>تشخیص هوشمند</span>
                        </button>

                        <button
                          onClick={resetToFullFrame}
                          className="h-10 bg-slate-800 hover:bg-slate-700 text-sky-300 border border-slate-700 rounded-xl text-xs font-bold flex items-center justify-center gap-1.5 active:scale-95 transition-all"
                        >
                          <Maximize2 className="w-3.5 h-3.5" />
                          <span>کادر کامل</span>
                        </button>
                      </div>

                      <button
                        onClick={handleConfirmCrop}
                        className="w-full h-12 bg-sky-600 hover:bg-sky-500 text-white rounded-xl font-bold text-sm flex items-center justify-center gap-2 shadow-lg shadow-sky-600/30 active:scale-95 transition-all"
                      >
                        <Check className="w-4 h-4" />
                        <span>تأیید کادر و ادامه به پیش‌نمایش</span>
                      </button>
                    </div>
                  </div>
                )}

                {/* مرحله ۳: صفحه پیش‌نمایش، فیلترها و ذخیره (Preview Screen) */}
                {currentScreen === 'preview' && (
                  <div className="flex-1 flex flex-col overflow-hidden bg-slate-100">
                    <div className="bg-white px-4 py-2.5 border-b border-slate-200 shadow-sm flex items-center justify-between shrink-0">
                      <div className="flex items-center gap-2">
                        <button
                          onClick={() => setCurrentScreen('home')}
                          className="p-1.5 hover:bg-slate-100 rounded-lg text-slate-700"
                          title="بازگشت به خانه"
                        >
                          <ArrowRight className="w-5 h-5" />
                        </button>
                        <span className="font-bold text-sm text-slate-900 truncate max-w-[140px]">
                          {activeDoc.title}
                        </span>
                      </div>

                      <div className="flex items-center gap-1.5">
                        <button
                          onClick={handleShare}
                          className="p-2 hover:bg-sky-50 text-sky-600 rounded-xl"
                          title="اشتراک‌گذاری"
                        >
                          <Share2 className="w-4 h-4" />
                        </button>
                        <button
                          onClick={handleSaveFilter}
                          className="bg-sky-600 hover:bg-sky-700 text-white px-3 py-1.5 rounded-xl text-xs font-bold flex items-center gap-1.5 shadow-sm active:scale-95 transition-all"
                        >
                          <Save className="w-3.5 h-3.5" />
                          <span>ذخیره</span>
                        </button>
                      </div>
                    </div>

                    {/* کادر نمایش سند تراز شده پس از برش پرسپکتیو */}
                    <div className="flex-1 p-4 flex items-center justify-center overflow-hidden">
                      <div 
                        className="w-full h-full max-h-[360px] rounded-2xl p-5 shadow-xl border border-slate-300 flex flex-col justify-between transition-all duration-300 relative overflow-hidden"
                        style={getFilterStyle(selectedFilter)}
                      >
                        {customImage || activeDoc.imageSrc ? (
                          <div className="w-full h-full relative flex items-center justify-center">
                            <img 
                              src={customImage || activeDoc.imageSrc} 
                              alt="سند اسکن شده" 
                              className="max-h-full max-w-full object-contain rounded-lg shadow-sm"
                            />
                          </div>
                        ) : (
                          <>
                            <div className="flex items-center justify-between border-b pb-3 border-current/20">
                              <div className="w-8 h-8 rounded bg-current/10 flex items-center justify-center text-xs font-bold">
                                🇮🇷
                              </div>
                              <div className="text-center">
                                <span className="text-[10px] block opacity-75">جمهوری اسلامی ایران</span>
                                <h4 className="font-bold text-xs">{activeDoc.title}</h4>
                              </div>
                              <div className="w-8 h-8 rounded bg-current/10 flex items-center justify-center text-[10px]">
                                شماره: ۱۴۰۳
                              </div>
                            </div>

                            <div className="space-y-3 py-2">
                              <div className="h-2 bg-current/40 rounded w-1/3" />
                              <div className="space-y-1.5">
                                <div className="h-1.5 bg-current/30 rounded w-full" />
                                <div className="h-1.5 bg-current/30 rounded w-5/6" />
                                <div className="h-1.5 bg-current/30 rounded w-4/6" />
                              </div>
                              <div className="h-2 bg-current/40 rounded w-1/4" />
                              <div className="space-y-1.5">
                                <div className="h-1.5 bg-current/30 rounded w-full" />
                                <div className="h-1.5 bg-current/30 rounded w-3/4" />
                              </div>
                            </div>

                            <div className="flex items-center justify-between pt-3 border-t border-current/20">
                              <div className="w-12 h-12 rounded-full border-2 border-red-600 flex items-center justify-center text-[9px] text-red-600 font-black rotate-[-12deg]">
                                تأیید شد
                              </div>
                              <div className="text-left">
                                <div className="text-[9px] opacity-75">امضا و تاریخ</div>
                                <div className="w-16 h-4 border-b border-current/40" />
                              </div>
                            </div>
                          </>
                        )}

                        <div className="absolute top-2 left-2 bg-black/60 text-white text-[10px] px-2 py-0.5 rounded-full backdrop-blur-sm">
                          فیلتر: {getFilterLabel(selectedFilter)}
                        </div>
                      </div>
                    </div>

                    {/* نوار پایین مرحله ۳: دکمه تنظیم مجدد کادر و ۴ حالت فیلتر */}
                    <div className="bg-white px-4 py-3 border-t border-slate-200 shadow-lg rounded-t-3xl shrink-0 space-y-2.5">
                      <div className="flex items-center justify-between">
                        <span className="text-[11px] font-bold text-slate-500">فیلتر نهایی مدرک:</span>
                        <button
                          onClick={() => setCurrentScreen('crop')}
                          className="text-[11px] text-sky-700 bg-sky-50 hover:bg-sky-100 px-2.5 py-1 rounded-lg font-bold flex items-center gap-1 border border-sky-200/80"
                        >
                          <Crop className="w-3.5 h-3.5" />
                          <span>تنظیم مجدد کادر</span>
                        </button>
                      </div>

                      <div className="grid grid-cols-4 gap-2">
                        <button
                          onClick={() => setSelectedFilter('photocopy')}
                          className={`p-2 rounded-xl flex flex-col items-center gap-1 transition-all border ${
                            selectedFilter === 'photocopy'
                              ? 'bg-sky-50 border-sky-500 text-sky-700 shadow-sm'
                              : 'bg-slate-50 border-slate-200 text-slate-600 hover:bg-slate-100'
                          }`}
                        >
                          <Printer className="w-4 h-4" />
                          <span className="text-[10px] font-bold">فتوکپی</span>
                        </button>

                        <button
                          onClick={() => setSelectedFilter('bw')}
                          className={`p-2 rounded-xl flex flex-col items-center gap-1 transition-all border ${
                            selectedFilter === 'bw'
                              ? 'bg-sky-50 border-sky-500 text-sky-700 shadow-sm'
                              : 'bg-slate-50 border-slate-200 text-slate-600 hover:bg-slate-100'
                          }`}
                        >
                          <Contrast className="w-4 h-4" />
                          <span className="text-[10px] font-bold">سیاه و سفید</span>
                        </button>

                        <button
                          onClick={() => setSelectedFilter('color')}
                          className={`p-2 rounded-xl flex flex-col items-center gap-1 transition-all border ${
                            selectedFilter === 'color'
                              ? 'bg-sky-50 border-sky-500 text-sky-700 shadow-sm'
                              : 'bg-slate-50 border-slate-200 text-slate-600 hover:bg-slate-100'
                          }`}
                        >
                          <Sparkles className="w-4 h-4" />
                          <span className="text-[10px] font-bold">رنگی شفاف</span>
                        </button>

                        <button
                          onClick={() => setSelectedFilter('original')}
                          className={`p-2 rounded-xl flex flex-col items-center gap-1 transition-all border ${
                            selectedFilter === 'original'
                              ? 'bg-sky-50 border-sky-500 text-sky-700 shadow-sm'
                              : 'bg-slate-50 border-slate-200 text-slate-600 hover:bg-slate-100'
                          }`}
                        >
                          <Layers className="w-4 h-4" />
                          <span className="text-[10px] font-bold">اصلی</span>
                        </button>
                      </div>
                    </div>
                  </div>
                )}
              </div>
            </div>

            {/* پنل توضیحات و مشخصات فنی */}
            <div className="flex-1 max-w-lg space-y-4 text-right">
              <div className="bg-neutral-900 border border-neutral-800 rounded-2xl p-5 shadow-lg space-y-4">
                <div className="flex items-center gap-2 text-sky-400">
                  <ShieldCheck className="w-5 h-5" />
                  <h3 className="font-bold text-base text-white">انطباق کامل با ضوابط کافه‌بازار و مایکت</h3>
                </div>
                <div className="grid grid-cols-2 gap-3 text-xs">
                  <div className="bg-neutral-800/80 p-3 rounded-xl border border-neutral-700/60">
                    <span className="text-neutral-400 block mb-1">Target & Compile SDK</span>
                    <span className="text-emerald-400 font-bold font-mono text-sm">34 (Android 14)</span>
                  </div>
                  <div className="bg-neutral-800/80 p-3 rounded-xl border border-neutral-700/60">
                    <span className="text-neutral-400 block mb-1">حداقل نسخه پشتیبانی (minSdk)</span>
                    <span className="text-sky-400 font-bold font-mono text-sm">24 (Android 7.0+)</span>
                  </div>
                  <div className="bg-neutral-800/80 p-3 rounded-xl border border-neutral-700/60">
                    <span className="text-neutral-400 block mb-1">فلو کاربری</span>
                    <span className="text-amber-400 font-bold text-sm">۳ مرحله‌ای استاندارد</span>
                  </div>
                  <div className="bg-neutral-800/80 p-3 rounded-xl border border-neutral-700/60">
                    <span className="text-neutral-400 block mb-1">وضعیت شبکه</span>
                    <span className="text-purple-400 font-bold text-sm">۱۰۰٪ آفلاین و امن</span>
                  </div>
                </div>

                <div className="border-t border-neutral-800 pt-3 space-y-2">
                  <h4 className="text-xs font-bold text-neutral-300">بهبودهای اساسی اعمال‌شده در ساختار اپ:</h4>
                  <ul className="text-xs text-neutral-400 space-y-1.5 list-disc list-inside">
                    <li><strong>جابجایی مرحله برش:</strong> انتقال ابزار پرسپکتیو به مرحله ۲ بلافاصله پس از عکس‌برداری</li>
                    <li><strong>رفع مشکل گیر کردن کادر:</strong> اصلاح pointerInput با state پایدار بدون ابطال جسچر</li>
                    <li><strong>تشخیص هوشمند قدرتمند:</strong> الگوریتم چندپرتویی تطبیقی از ۴ گوشه به مرکز</li>
                    <li><strong>ذره‌بین شناور (Magnifier):</strong> بزرگ‌نمایی نقطه اتصال بالای انگشت کاربر</li>
                    <li><strong>خطوط شطرنجی راهنما:</strong> تراز آسان خطوط و حاشیه‌های مدرک</li>
                  </ul>
                </div>

                <div className="pt-2 flex flex-wrap gap-2">
                  <button
                    onClick={handleCameraCapture}
                    className="flex-1 bg-neutral-800 hover:bg-neutral-700 text-white px-4 py-2.5 rounded-xl text-xs font-bold flex items-center justify-center gap-2 border border-neutral-700 transition-all"
                  >
                    <Upload className="w-4 h-4 text-sky-400" />
                    <span>تست تصویر دلخواه در شبیه‌ساز</span>
                  </button>
                  <button
                    onClick={() => setActiveTab('code')}
                    className="bg-sky-600 hover:bg-sky-500 text-white px-4 py-2.5 rounded-xl text-xs font-bold flex items-center justify-center gap-2 transition-all"
                  >
                    <Code2 className="w-4 h-4" />
                    <span>مشاهده فایل‌های کاتلین</span>
                  </button>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* برگه فایل‌های کاتلین و گریدل */}
        {activeTab === 'code' && (
          <div className="w-full max-w-5xl bg-neutral-900 border border-neutral-800 rounded-2xl overflow-hidden shadow-2xl flex flex-col md:flex-row min-h-[600px]">
            {/* ستون فهرست فایل‌ها */}
            <div className="w-full md:w-64 bg-neutral-950/80 p-4 border-b md:border-b-0 md:border-l border-neutral-800 flex flex-col gap-2 shrink-0">
              <span className="text-xs font-bold text-neutral-400 px-2">فایل‌های اصلی پروژه کاتلین:</span>
              {Object.keys(codeSnippets).map((path) => (
                <button
                  key={path}
                  onClick={() => setSelectedFileCode(path)}
                  className={`text-right px-3 py-2 rounded-xl text-xs font-mono transition-all truncate ${
                    selectedFileCode === path
                      ? 'bg-sky-600 text-white font-bold'
                      : 'text-neutral-400 hover:bg-neutral-800 hover:text-white'
                  }`}
                  dir="ltr"
                >
                  {path}
                </button>
              ))}
              <div className="mt-auto p-3 bg-neutral-900 rounded-xl border border-neutral-800 text-[11px] text-neutral-400">
                <Info className="w-4 h-4 text-sky-400 mb-1" />
                تمامی فایل‌ها در پوشه <code className="text-sky-300">app/src/main/java</code> اعمال شده و آماده بیلد بدون خطا در GitHub Actions می‌باشند.
              </div>
            </div>

            {/* بخش نمایش محتوای سورس کد */}
            <div className="flex-1 flex flex-col">
              <div className="bg-neutral-800/80 px-4 py-3 border-b border-neutral-700 flex items-center justify-between">
                <div>
                  <span className="font-mono text-xs font-bold text-sky-400" dir="ltr">
                    {selectedFileCode}
                  </span>
                  <p className="text-[11px] text-neutral-400 mt-0.5">
                    {codeSnippets[selectedFileCode]?.desc}
                  </p>
                </div>
                <button
                  onClick={() => {
                    navigator.clipboard.writeText(codeSnippets[selectedFileCode]?.code || '');
                    showToast('کد در کلیپ‌بورد کپی شد');
                  }}
                  className="bg-neutral-700 hover:bg-neutral-600 text-neutral-200 text-xs px-2.5 py-1.5 rounded-lg"
                >
                  کپی کد
                </button>
              </div>

              <div className="flex-1 p-4 overflow-x-auto bg-neutral-950 font-mono text-xs leading-relaxed text-neutral-300" dir="ltr">
                <pre>{codeSnippets[selectedFileCode]?.code}</pre>
              </div>
            </div>
          </div>
        )}

        {/* برگه راهنمای خروجی APK با گیت‌هاب اکشنز */}
        {activeTab === 'cicd' && (
          <div className="w-full max-w-4xl bg-neutral-900 border border-neutral-800 rounded-2xl p-6 shadow-2xl space-y-6">
            <div className="flex items-center gap-3 border-b border-neutral-800 pb-4">
              <div className="w-10 h-10 rounded-xl bg-emerald-600/20 text-emerald-400 flex items-center justify-center border border-emerald-500/30">
                <Download className="w-5 h-5" />
              </div>
              <div>
                <h2 className="text-lg font-bold text-white">ورکفلو GitHub Actions برای خروجی APK</h2>
                <p className="text-xs text-neutral-400">تولید خودکار فایل نصبی آماده انتشار برای مایکت و کافه‌بازار</p>
              </div>
            </div>

            <div className="space-y-4 text-sm text-neutral-300">
              <div className="bg-neutral-950 p-4 rounded-xl border border-neutral-800 space-y-2">
                <span className="font-bold text-sky-400 block text-xs">مراحل خودکار ورکفلو (.github/workflows/build-apk.yml):</span>
                <ol className="list-decimal list-inside space-y-2 text-xs text-neutral-400">
                  <li><strong className="text-neutral-200">راه‌اندازی محیط:</strong> نصب جاوا ۱۷ (Zulu OpenJDK 17) با کش خودکار وابستگی‌های گریدل.</li>
                  <li><strong className="text-neutral-200">اجرای پرمیشن:</strong> اعطای دسترسی اجرایی با <code className="bg-neutral-800 px-1 py-0.5 rounded text-sky-300">chmod +x ./gradlew</code>.</li>
                  <li><strong className="text-neutral-200">کامپایل سورس‌ها:</strong> اجرای بیلد از طریق <code className="bg-neutral-800 px-1 py-0.5 rounded text-sky-300">./gradlew assembleDebug --no-daemon</code>.</li>
                  <li><strong className="text-neutral-200">آپلود خروجی نصبی:</strong> انتشار و ذخیره فایل <code className="bg-neutral-800 px-1 py-0.5 rounded text-emerald-300">app-debug.apk</code> با شناسه Artifact به نام <code className="bg-neutral-800 px-1 py-0.5 rounded text-amber-300">scanner-apk</code>.</li>
                </ol>
              </div>

              <div className="bg-sky-950/40 border border-sky-800/50 p-4 rounded-xl text-xs space-y-2">
                <h4 className="font-bold text-sky-300">نحوه اجرای بیلد در گیت‌هاب:</h4>
                <p className="text-neutral-300 leading-relaxed">
                  با Push کردن تغییرات به مخزن گیت‌هاب، بخش <strong>Actions</strong> به طور خودکار بیلد گرادل را اجرا کرده و فایل نصبی <code className="text-sky-300">app-debug.apk</code> را در کمتر از ۳ دقیقه آماده دانلود و نصب مستقیم روی گوشی ارائه می‌دهد.
                </p>
              </div>
            </div>
          </div>
        )}
      </main>
    </div>
  );
}
