package com.kastamonushop.app.viewmodel

import android.content.ContentResolver
import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Typeface
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import java.nio.ByteBuffer
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kastamonushop.app.bridge.NativeSurfaceBridge
import com.kastamonushop.app.data.SavedGalleryStore
import com.kastamonushop.app.data.SavedPhotoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uniffi.kastamonushop_ffi.AutoLevelsResult
import uniffi.kastamonushop_ffi.CafSamplingStrokeInput
import uniffi.kastamonushop_ffi.DocumentInfo
import uniffi.kastamonushop_ffi.HistogramData
import uniffi.kastamonushop_ffi.SampledColorInfo
import uniffi.kastamonushop_ffi.KastamonuEngine
import uniffi.kastamonushop_ffi.LayerSummary
import uniffi.kastamonushop_ffi.SelectionBounds
import uniffi.kastamonushop_ffi.StrokeSample
import uniffi.kastamonushop_ffi.TextLayerStyle
import uniffi.kastamonushop_ffi.AbrBrushSummary
import uniffi.kastamonushop_ffi.PuppetPinPoint
import uniffi.kastamonushop_ffi.createKastamonuEngine
import uniffi.kastamonushop_ffi.engineVersionInfo
import uniffi.kastamonushop_ffi.getLastGpuError
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

enum class ActiveTool {
    NONE,
    BRUSH,
    ERASER,
    EYEDROPPER,
    SPOT_HEAL,
    SMUDGE,
    DODGE,
    BURN,
    CLONE_STAMP,
    SELECT_RECT,
    SELECT_WAND,
    SELECT_QUICK,
    SELECT_BRUSH_ADD,
    SELECT_BRUSH_SUB,
    BUCKET,
    MIXER_BRUSH,
    COLOR_REPLACE,
    SPONGE_SAT,
    SPONGE_DESAT,
    BLUR_BRUSH,
    SHARPEN_BRUSH,
    RED_EYE,
    PATCH,
    REMOVE_TOOL,
    SELECT_LASSO,
    SELECT_MAGNETIC_LASSO,
    PENCIL,
    MAGIC_ERASER,
    BG_ERASER,
    ADJUST_BRUSH,
    HISTORY_BRUSH,
    ART_HISTORY_BRUSH,
    HEALING_BRUSH
}

data class EditorUiState(
    val engineVersion: String = "",
    val docInfo: DocumentInfo? = null,
    val layers: List<LayerSummary> = emptyList(),
    val savedPhotos: List<SavedPhotoItem> = emptyList(),
    val activeTool: ActiveTool = ActiveTool.NONE,
    val rulerEnabled: Boolean = false,
    val symmetryMode: String = "off",
    val brushSize: Float = 28f,
    val brushHardness: Float = 0.85f,
    val brushOpacity: Float = 1.0f,
    val brushFlow: Float = 1.0f,
    val retouchStrength: Float = 55f,
    val colorHex: String = "#FF4D4F",
    val bwLayerId: ULong? = null,
    val invertLayerId: ULong? = null,
    val selectionBounds: SelectionBounds? = null,
    val selectEllipse: Boolean = false,
    val wandTolerance: Float = 32f,
    val wandSmartMode: Boolean = true,
    val wandContiguous: Boolean = true,
    val selectAddMode: Boolean = false,
    val selectSubtractMode: Boolean = false,
    val cloneSourceDoc: Pair<Float, Float>? = null,
    val pickingCloneSource: Boolean = false,
    val compareOriginal: Boolean = false,
    val liquifyCanUndo: Boolean = false,
    val liquifyCanRedo: Boolean = false,
    val maskPaintMode: Boolean = false,
    val statusMessage: String? = null,
    val isBusy: Boolean = false,
    val fitCameraSignal: Int = 0,
    val brushSpacing: Float = 0.15f,
    val brushScatter: Float = 0f,
    val brushSizeJitter: Float = 0f,
    val brushAngleJitter: Float = 0f,
    val brushOpacityJitter: Float = 0f,
    val brushPresets: List<AbrBrushSummary> = emptyList(),
    val activeBrushPresetName: String? = null,
    val mixerWet: Float = 55f,
    val mixerLoad: Float = 50f,
    val mixerMix: Float = 50f,
    val mixerFlow: Float = 100f,
    val mixerSampleAll: Boolean = false,
    val colorReplaceMode: String = "color",
    val colorReplaceTolerance: Float = 32f,
    val patchMode: String = "source",
    val redEyePupilSize: Float = 50f,
    val redEyeDarken: Float = 55f,
    val hasCopiedStyle: Boolean = false,
    val removeCloseLoops: Boolean = true,
    val removeSampleAllLayers: Boolean = false,
    val removeNewLayer: Boolean = false,
    val quickMaskMode: Boolean = false,
    val magicEraserTolerance: Float = 50f,
    val magicEraserContiguous: Boolean = true,
    val magicEraserSampleAll: Boolean = false,
    val bgEraserTolerance: Float = 50f,
    val bgEraserLimits: String = "contiguous",
    val bgEraserProtectFg: Boolean = false,
    val adjBrushKind: String = "exposure",
    val adjBrushSubtract: Boolean = false,
    val artHistoryStyle: String = "tightMedium",
    val artHistoryArea: Float = 65f,
    val artHistoryTolerance: Float = 0f,
    val healingSampleAll: Boolean = false,
    val histogram: HistogramData? = null,
    // v2.4.0 Parça 3: 5-Slot Clone Source, Healing/SpotHeal/Patch/Retouch Pro
    val cloneSlot: Int = 0,
    val cloneScale: Float = 100f,
    val cloneAngle: Float = 0f,
    val cloneFlipH: Boolean = false,
    val cloneFlipV: Boolean = false,
    val cloneOverlay: Boolean = true,
    val cloneAligned: Boolean = true,
    val cloneSampleLayer: String = "current",
    val cloneBlendMode: String = "normal",
    val healingAligned: Boolean = true,
    val healingSampleLayer: String = "current",
    val healingMode: String = "normal",
    val spotHealType: String = "contentAware",
    val spotHealSampleAll: Boolean = false,
    val patchContentAware: Boolean = false,
    val patchStructure: Int = 4,
    val patchColor: Int = 0,
    val patchSampleAll: Boolean = false,
    val retouchRange: String = "midtones",
    val retouchProtectTones: Boolean = true,
    val smudgeFingerPainting: Boolean = false,
    val sharpenProtectDetail: Boolean = true,
    val retouchSampleAll: Boolean = false
)

class EditorViewModel : ViewModel() {
    private val engine: KastamonuEngine = createKastamonuEngine()
    private var statusAutoHideJob: Job? = null

    private val paintExecutor = Executors.newSingleThreadExecutor()
    private var lastLiveRenderMs = 0L
    private val pendingLock = Any()
    private val pendingSamples = ArrayList<StrokeSample>(64)
    @Volatile
    private var lastDispatchedSample: StrokeSample? = null
    private val drainScheduled = AtomicBoolean(false)

    // Coalesced live adjustment preview state
    @Volatile
    private var liveAdjustmentLayerId: ULong? = null
    private val pendingAdjParams = AtomicReference<String?>(null)
    private val adjDrainScheduled = AtomicBoolean(false)

    // Coalesced live pixel filter preview state
    private val pendingFilterUpdate = AtomicReference<Pair<String, String>?>(null)
    private val filterDrainScheduled = AtomicBoolean(false)

    // Coalesced layer opacity slider state
    private val pendingOpacityUpdate = AtomicReference<Pair<ULong, Float>?>(null)
    private val opacityDrainScheduled = AtomicBoolean(false)

    // Coalesced eyedropper sample state
    private val pendingSamplePt = AtomicReference<Pair<Float, Float>?>(null)
    private val sampleDrainScheduled = AtomicBoolean(false)

    // Prevents rapid Spot Healing swipes from piling up on paintExecutor
    private val spotHealInFlight = AtomicBoolean(false)

    // Coalesced 60 FPS Exact Proxy Preview queue (100% WYSIWYG parity for all adjustments & filters)
    private sealed class ProxyJob {
        data class Adjust(val kind: String, val paramsJson: String) : ProxyJob()
        data class Blur(val radius: Float) : ProxyJob()
        data class Sharpen(val amount: Float) : ProxyJob()
        data class LayerFx(
            val kind: String,
            val colorHex: String,
            val size: Float,
            val opacity: Float,
            val distance: Float
        ) : ProxyJob()
        data class CameraLight(
            val exposure: Float,
            val shadows: Float,
            val highlights: Float,
            val vibrance: Float,
            val temperature: Float,
            val tint: Float,
            val clarity: Float,
            val dehaze: Float
        ) : ProxyJob()
        data class Gradient(
            val fromX: Float,
            val fromY: Float,
            val toX: Float,
            val toY: Float,
            val style: String,
            val startHex: String,
            val endHex: String,
            val opacity: Float
        ) : ProxyJob()
        data class Shape(
            val kind: String,
            val x0: Float,
            val y0: Float,
            val x1: Float,
            val y1: Float,
            val fillHex: String,
            val strokeHex: String,
            val strokeWidth: Float,
            val cornerRadius: Float,
            val sides: UInt
        ) : ProxyJob()
        data class CinemaLook(
            val preset: String,
            val intensity: Float,
            val vignette: Float,
            val grain: Float
        ) : ProxyJob()
        data class MotionBlur(val angle: Float, val distance: Float) : ProxyJob()
        data class RadialBlur(val amount: Float, val method: String) : ProxyJob()
        data class HslMixer(val h: List<Float>, val s: List<Float>, val l: List<Float>) : ProxyJob()
        data class GalleryFilter(val kind: String, val amount: Float, val detail: Float) : ProxyJob()
        data class LightDistort(
            val kind: String,
            val amount: Float,
            val detail: Float,
            val paramX: Float,
            val paramY: Float,
            val variant: String
        ) : ProxyJob()
        data class TextPreview(
            val editLayerId: ULong?,
            val text: String,
            val x: Float,
            val y: Float,
            val sizePx: Float,
            val colorHex: String,
            val align: String,
            val fontFamily: String,
            val bold: Boolean,
            val italic: Boolean,
            val underline: Boolean,
            val strikethrough: Boolean,
            val caps: String,
            val hScale: Float,
            val vScale: Float,
            val tracking: Float,
            val lineHeight: Float,
            val vertical: Boolean,
            val warpStyle: String,
            val warpBend: Float,
            val warpHDist: Float,
            val warpVDist: Float
        ) : ProxyJob()
        data class PuppetWarp(val pins: List<PuppetPinPoint>, val mode: String, val density: String, val expansion: Float) : ProxyJob()
        data class BlendIf(val thisBlack: Int, val thisWhite: Int, val underBlack: Int, val underWhite: Int, val feather: Int) : ProxyJob()
        data class CameraRawJson(val paramsJson: String) : ProxyJob()
        data class LensCorrection(
            val distortion: Float,
            val vignetteAmount: Float,
            val redCyan: Float,
            val blueYellow: Float,
            val vertical: Float,
            val horizontal: Float
        ) : ProxyJob()
        data class ColorLookup(val fileName: String, val lutText: String, val opacity: Float) : ProxyJob()
        data class LayerStyleStack(val effectsJson: String) : ProxyJob()
        data class ContentAwareFill(
            val sampling: String,
            val colorAdaptation: String,
            val rotationAdaptation: String,
            val scale: Boolean,
            val mirror: Boolean,
            val sampleAllLayers: Boolean,
            val output: String
        ) : ProxyJob()
        data class ContentAwareScale(
            val scaleX: Float,
            val scaleY: Float,
            val amount: Float,
            val protectSkinTones: Boolean
        ) : ProxyJob()
        data class ContentAwareScalePro(
            val scaleX: Float,
            val scaleY: Float,
            val amount: Float,
            val protectSkinTones: Boolean,
            val protectStrokes: List<CafSamplingStrokeInput>
        ) : ProxyJob()
        data class ContentAwareFillPro(
            val sampling: String,
            val colorAdaptation: String,
            val rotationAdaptation: String,
            val scale: Boolean,
            val mirror: Boolean,
            val sampleAllLayers: Boolean,
            val strokes: List<CafSamplingStrokeInput>
        ) : ProxyJob()
        data class SelectSky(val threshold: Float, val softness: Float, val mode: String, val sampleAllLayers: Boolean) : ProxyJob()
        data class ColorRange(val preset: String, val colorHex: String, val fuzziness: Float, val invert: Boolean, val mode: String, val sampleAllLayers: Boolean) : ProxyJob()
        data class FocusArea(val rangePct: Float, val noisePct: Float, val mode: String, val sampleAllLayers: Boolean) : ProxyJob()
        data class ImageResize(val width: Int, val height: Int, val resample: String) : ProxyJob()
        data class ModifySelection(val op: String, val radius: Float) : ProxyJob()
        data class TextProPreview(
            val editLayerId: ULong?,
            val text: String,
            val x: Float,
            val y: Float,
            val sizePx: Float,
            val colorHex: String,
            val align: String,
            val fontFamily: String,
            val bold: Boolean,
            val italic: Boolean,
            val underline: Boolean,
            val strikethrough: Boolean,
            val caps: String,
            val hScale: Float,
            val vScale: Float,
            val tracking: Float,
            val lineHeight: Float,
            val vertical: Boolean,
            val warpStyle: String,
            val warpBend: Float,
            val warpHDist: Float,
            val warpVDist: Float,
            val extras: uniffi.kastamonushop_ffi.TextProExtras
        ) : ProxyJob()
        data class ContentAwareMove(
            val dx: Float,
            val dy: Float,
            val mode: String,
            val structure: Int,
            val color: Int,
            val scalePct: Float,
            val rotateDeg: Float,
            val sampleAllLayers: Boolean
        ) : ProxyJob()
    }
    private data class StampedProxyJob(val gen: Long, val job: ProxyJob)
    private val proxyGeneration = java.util.concurrent.atomic.AtomicLong(0L)
    private val pendingProxyJob = AtomicReference<StampedProxyJob?>(null)
    @Volatile
    private var lastEnqueuedProxyJob: ProxyJob? = null
    private val proxyDrainScheduled = AtomicBoolean(false)

    private fun invalidateProxyQueue(): Long {
        val g = proxyGeneration.incrementAndGet()
        pendingProxyJob.set(null)
        lastEnqueuedProxyJob = null
        return g
    }

    private fun enqueueProxyJob(job: ProxyJob) {
        if (job == lastEnqueuedProxyJob) return
        lastEnqueuedProxyJob = job
        val gen = proxyGeneration.get()
        pendingProxyJob.set(StampedProxyJob(gen, job))
        if (proxyDrainScheduled.compareAndSet(false, true)) {
            paintExecutor.execute { drainPendingProxyJobs() }
        }
    }

    @Volatile
    private var lastWandTapDoc: Pair<Float, Float>? = null
    private val pendingWandTol = AtomicReference<Float?>(null)
    private val wandTolDrainScheduled = AtomicBoolean(false)

    private val _activeLayerPreview = MutableStateFlow<Pair<SelectionBounds, ImageBitmap?>?>(null)
    val activeLayerPreview: StateFlow<Pair<SelectionBounds, ImageBitmap?>?> = _activeLayerPreview.asStateFlow()

    @Volatile
    private var currentSavedPhotoId: String? = null
    @Volatile
    private var currentSavedDisplayName: String? = null

    private val _layerThumbnails = MutableStateFlow<Map<ULong, ImageBitmap>>(emptyMap())
    val layerThumbnails: StateFlow<Map<ULong, ImageBitmap>> = _layerThumbnails.asStateFlow()

    private val _loupeBitmap = MutableStateFlow<ImageBitmap?>(null)
    val loupeBitmap: StateFlow<ImageBitmap?> = _loupeBitmap.asStateFlow()

    private val _cinemaThumbs = MutableStateFlow<List<ImageBitmap>>(emptyList())
    val cinemaThumbs: StateFlow<List<ImageBitmap>> = _cinemaThumbs.asStateFlow()

    private val _galleryThumbs = MutableStateFlow<List<ImageBitmap>>(emptyList())
    val galleryThumbs: StateFlow<List<ImageBitmap>> = _galleryThumbs.asStateFlow()

    private val _lightDistortThumbs = MutableStateFlow<List<ImageBitmap>>(emptyList())
    val lightDistortThumbs: StateFlow<List<ImageBitmap>> = _lightDistortThumbs.asStateFlow()

    data class StudioFontOption(
        val family: String,
        val displayName: String,
        val category: String,
        val composeFont: FontFamily
    )

    private val _studioFonts = MutableStateFlow<List<StudioFontOption>>(emptyList())
    val studioFonts: StateFlow<List<StudioFontOption>> = _studioFonts.asStateFlow()

    @Volatile
    private var fontsInitialized = false

    fun ensureStudioFontsLoaded(context: Context) {
        if (fontsInitialized && _studioFonts.value.isNotEmpty()) return
        paintExecutor.execute {
            runCatching {
                val options = ArrayList<StudioFontOption>()
                val seen = HashSet<String>()

                // 1. Load previously imported user custom fonts from filesDir/user_fonts
                val userDir = java.io.File(context.filesDir, "user_fonts")
                if (userDir.exists()) {
                    userDir.listFiles()?.sortedByDescending { it.lastModified() }?.forEach { f ->
                        if (f.isFile && (f.name.endsWith(".ttf", true) || f.name.endsWith(".otf", true))) {
                            runCatching {
                                val bytes = f.readBytes()
                                val reg = engine.registerFontBytes(bytes)
                                val fam = reg.firstOrNull() ?: f.nameWithoutExtension
                                val tf = Typeface.createFromFile(f)
                                if (seen.add(fam.lowercase())) {
                                    options.add(StudioFontOption(fam, fam, "Özel", FontFamily(tf)))
                                }
                            }
                        }
                    }
                }

                // 2. Load 10 curated bundled Google Fonts from APK assets/fonts
                val curatedMeta = listOf(
                    Triple("Montserrat.ttf", "Montserrat", "Modern Sans"),
                    Triple("PlayfairDisplay.ttf", "Playfair Display", "Dergi Serif"),
                    Triple("Oswald.ttf", "Oswald", "Poster"),
                    Triple("BebasNeue.ttf", "Bebas Neue", "Başlık"),
                    Triple("Caveat.ttf", "Caveat", "El Yazısı"),
                    Triple("DancingScript.ttf", "Dancing Script", "Kaligrafi"),
                    Triple("Lora.ttf", "Lora", "Klasik Serif"),
                    Triple("Poppins-Regular.ttf", "Poppins", "Yumuşak Sans"),
                    Triple("Pacifico.ttf", "Pacifico", "Retro Fırça"),
                    Triple("SpaceMono-Regular.ttf", "Space Mono", "Daktilo")
                )
                val extraAssets = listOf("Poppins-Bold.ttf", "SpaceMono-Bold.ttf")
                val assets = context.assets

                for ((fileName, fallbackFamily, cat) in curatedMeta) {
                    runCatching {
                        val bytes = assets.open("fonts/$fileName").use { it.readBytes() }
                        val registered = engine.registerFontBytes(bytes)
                        val realFamily = registered.firstOrNull() ?: fallbackFamily
                        val tf = Typeface.createFromAsset(assets, "fonts/$fileName")
                        if (seen.add(realFamily.lowercase())) {
                            options.add(StudioFontOption(realFamily, fallbackFamily, cat, FontFamily(tf)))
                        }
                    }
                }
                for (extra in extraAssets) {
                    runCatching {
                        val bytes = assets.open("fonts/$extra").use { it.readBytes() }
                        engine.registerFontBytes(bytes)
                    }
                }

                // 3. Append Android system fonts discovered by PhotoCraft
                val sysFamilies = runCatching { engine.listAvailableFonts() }.getOrDefault(emptyList())
                val preferredSystem = listOf("Roboto", "Noto Serif", "Droid Sans Mono", "Cutive Mono", "Carrois Gothic SC")
                for (sf in preferredSystem) {
                    val match = sysFamilies.firstOrNull { it.equals(sf, ignoreCase = true) }
                    if (match != null && seen.add(match.lowercase())) {
                        val cf = when {
                            match.contains("Serif", true) -> FontFamily.Serif
                            match.contains("Mono", true) -> FontFamily.Monospace
                            else -> FontFamily.SansSerif
                        }
                        options.add(StudioFontOption(match, match, "Sistem", cf))
                    }
                }
                for (sf in sysFamilies) {
                    if (options.size >= 28) break
                    if (seen.add(sf.lowercase()) && !sf.startsWith(".") && !sf.contains("Emoji", true) && !sf.contains("Symbol", true)) {
                        val cf = when {
                            sf.contains("Serif", true) -> FontFamily.Serif
                            sf.contains("Mono", true) -> FontFamily.Monospace
                            else -> FontFamily.SansSerif
                        }
                        options.add(StudioFontOption(sf, sf, "Sistem", cf))
                    }
                }
                if (options.isEmpty()) {
                    options.add(StudioFontOption("Roboto", "Roboto", "Sistem", FontFamily.SansSerif))
                }
                _studioFonts.value = options
                fontsInitialized = true
            }
        }
    }

    fun importCustomFont(context: Context, uri: Uri, onImported: (String) -> Unit) {
        paintExecutor.execute {
            runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("Font dosyası okunamadı")
                if (bytes.size < 16) error("Geçersiz font dosyası")
                val b0 = bytes[0].toInt() and 0xFF
                val b1 = bytes[1].toInt() and 0xFF
                val b2 = bytes[2].toInt() and 0xFF
                val b3 = bytes[3].toInt() and 0xFF
                val magicStr = String(bytes, 0, 4, Charsets.ISO_8859_1)
                val isValid = (b0 == 0 && b1 == 1 && b2 == 0 && b3 == 0) ||
                    magicStr == "OTTO" || magicStr == "true" || magicStr == "ttcf"
                if (!isValid) error("Yalnızca .ttf veya .otf font dosyaları desteklenir")

                val registered = engine.registerFontBytes(bytes)
                val family = registered.firstOrNull()?.takeIf { it.isNotBlank() }
                    ?: "OzelFont_${System.currentTimeMillis() % 10000}"
                val ext = if (magicStr == "OTTO") "otf" else "ttf"
                val safeName = family.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".$ext"
                val userDir = java.io.File(context.filesDir, "user_fonts").apply { mkdirs() }
                val dest = java.io.File(userDir, safeName)
                dest.writeBytes(bytes)

                val tf = Typeface.createFromFile(dest)
                val opt = StudioFontOption(family, family, "Özel", FontFamily(tf))
                val updated = listOf(opt) + _studioFonts.value.filterNot { it.family.equals(family, true) }
                _studioFonts.value = updated
                android.os.Handler(android.os.Looper.getMainLooper()).post { onImported(family) }
                showStatus("Font eklendi: $family", 2000L)
            }.onFailure { err ->
                showStatus("Font eklenemedi: ${err.message}", 2600L)
            }
        }
    }

    fun inspectActiveTextLayer(onResult: (TextLayerStyle?) -> Unit) {
        paintExecutor.execute {
            val st = runCatching { engine.getActiveTextLayerStyle() }.getOrNull()
            android.os.Handler(android.os.Looper.getMainLooper()).post { onResult(st) }
        }
    }

    fun addOrEditTextLayerPro(
        editLayerId: ULong?,
        text: String,
        x: Float,
        y: Float,
        sizePx: Float,
        colorHex: String,
        align: String,
        fontFamily: String,
        bold: Boolean,
        italic: Boolean,
        underline: Boolean,
        strikethrough: Boolean,
        caps: String,
        hScale: Float,
        vScale: Float,
        tracking: Float,
        lineHeight: Float,
        vertical: Boolean,
        warpStyle: String,
        warpBend: Float,
        warpHDist: Float,
        warpVDist: Float,
        extras: uniffi.kastamonushop_ffi.TextProExtras
    ) {
        invalidateProxyQueue()
        _uiState.update { it.copy(isBusy = true) }
        paintExecutor.execute {
            runCatching {
                engine.addOrEditTextLayer(
                    editLayerId, text, x, y - extras.baselineShift, sizePx, colorHex, align, fontFamily,
                    bold, italic, underline, strikethrough, caps, hScale, vScale,
                    tracking, lineHeight, vertical, warpStyle, warpBend, warpHDist, warpVDist
                )
                val info = engine.applyTextProExtras(colorHex, extras)
                val layers = engine.getLayers()
                _uiState.update { it.copy(docInfo = info, layers = layers, isBusy = false) }
                NativeSurfaceBridge.nativeRenderFrame()
                showStatus("Metin uygulandı")
            }.onFailure { err ->
                _uiState.update { it.copy(isBusy = false) }
                showStatus("Metin hatası: ${err.message}")
            }
        }
    }

    fun addOrEditTextLayer(
        editLayerId: ULong?,
        text: String,
        x: Float,
        y: Float,
        sizePx: Float,
        colorHex: String,
        align: String,
        fontFamily: String,
        bold: Boolean,
        italic: Boolean,
        underline: Boolean,
        strikethrough: Boolean,
        caps: String,
        hScale: Float,
        vScale: Float,
        tracking: Float,
        lineHeight: Float,
        vertical: Boolean,
        warpStyle: String,
        warpBend: Float,
        warpHDist: Float,
        warpVDist: Float
    ) {
        dismissCompareOriginal()
        paintExecutor.execute {
            runCatching {
                val info = engine.addOrEditTextLayer(
                    editLayerId = editLayerId,
                    text = text,
                    x = x,
                    y = y,
                    sizePx = sizePx,
                    colorHex = colorHex,
                    align = align,
                    fontFamily = fontFamily,
                    bold = bold,
                    italic = italic,
                    underline = underline,
                    strikethrough = strikethrough,
                    caps = caps,
                    hScale = hScale,
                    vScale = vScale,
                    tracking = tracking,
                    lineHeight = lineHeight,
                    vertical = vertical,
                    warpStyle = warpStyle,
                    warpBend = warpBend,
                    warpHDist = warpHDist,
                    warpVDist = warpVDist
                )
                val layers = engine.getLayers()
                _uiState.update { it.copy(docInfo = info, layers = layers) }
                NativeSurfaceBridge.nativeRenderFrame()
                refreshLayerThumbnails()
                showStatus(if (editLayerId != null) "Metin güncellendi" else "Metin katmanı eklendi", 1800L)
            }.onFailure { err ->
                showStatus("Metin hatası: ${err.message}", 2600L)
            }
        }
    }

    fun updateProxyTextProPreview(
        editLayerId: ULong?,
        text: String,
        x: Float,
        y: Float,
        sizePx: Float,
        colorHex: String,
        align: String,
        fontFamily: String,
        bold: Boolean,
        italic: Boolean,
        underline: Boolean,
        strikethrough: Boolean,
        caps: String,
        hScale: Float,
        vScale: Float,
        tracking: Float,
        lineHeight: Float,
        vertical: Boolean,
        warpStyle: String,
        warpBend: Float,
        warpHDist: Float,
        warpVDist: Float,
        extras: uniffi.kastamonushop_ffi.TextProExtras
    ) {
        enqueueProxyJob(
            ProxyJob.TextProPreview(
                editLayerId, text, x, y, sizePx, colorHex, align, fontFamily,
                bold, italic, underline, strikethrough, caps, hScale, vScale,
                tracking, lineHeight, vertical, warpStyle, warpBend, warpHDist, warpVDist,
                extras
            )
        )
    }

    fun updateProxyTextPreview(
        editLayerId: ULong?,
        text: String,
        x: Float,
        y: Float,
        sizePx: Float,
        colorHex: String,
        align: String,
        fontFamily: String,
        bold: Boolean,
        italic: Boolean,
        underline: Boolean,
        strikethrough: Boolean,
        caps: String,
        hScale: Float,
        vScale: Float,
        tracking: Float,
        lineHeight: Float,
        vertical: Boolean,
        warpStyle: String,
        warpBend: Float,
        warpHDist: Float,
        warpVDist: Float
    ) {
        enqueueProxyJob(
            ProxyJob.TextPreview(
                editLayerId, text, x, y, sizePx, colorHex, align, fontFamily,
                bold, italic, underline, strikethrough, caps, hScale, vScale,
                tracking, lineHeight, vertical, warpStyle, warpBend, warpHDist, warpVDist
            )
        )
    }

    fun createTextSelectionMask(
        text: String,
        x: Float,
        y: Float,
        sizePx: Float,
        align: String,
        fontFamily: String,
        bold: Boolean,
        italic: Boolean,
        caps: String,
        hScale: Float,
        vScale: Float,
        tracking: Float,
        lineHeight: Float,
        vertical: Boolean,
        warpStyle: String,
        warpBend: Float,
        warpHDist: Float,
        warpVDist: Float
    ) {
        invalidateProxyQueue()
        dismissCompareOriginal()
        paintExecutor.execute {
            runCatching {
                val info = engine.createTextSelectionMask(
                    text = text,
                    x = x,
                    y = y,
                    sizePx = sizePx,
                    align = align,
                    fontFamily = fontFamily,
                    bold = bold,
                    italic = italic,
                    caps = caps,
                    hScale = hScale,
                    vScale = vScale,
                    tracking = tracking,
                    lineHeight = lineHeight,
                    vertical = vertical,
                    warpStyle = warpStyle,
                    warpBend = warpBend,
                    warpHDist = warpHDist,
                    warpVDist = warpVDist
                )
                val layers = engine.getLayers()
                _uiState.update { it.copy(docInfo = info, layers = layers) }
                NativeSurfaceBridge.nativeRenderFrame()
                showStatus("Metin seçim maskesi oluşturuldu", 2000L)
            }.onFailure { err ->
                showStatus("Maske hatası: ${err.message}", 2600L)
            }
        }
    }

    fun updateProxyPuppetWarp(
        pins: List<PuppetPinPoint>,
        mode: String,
        density: String = "normal",
        expansion: Float = 2f
    ) {
        enqueueProxyJob(ProxyJob.PuppetWarp(pins, mode, density, expansion))
    }

    fun fetchPuppetMeshSegments(
        pins: List<PuppetPinPoint>,
        mode: String,
        density: String,
        expansion: Float,
        onResult: (List<Float>) -> Unit
    ) {
        paintExecutor.execute {
            val segs = runCatching {
                engine.getPuppetMeshSegments(pins, mode, density, expansion)
            }.getOrDefault(emptyList())
            viewModelScope.launch(Dispatchers.Main) {
                onResult(segs)
            }
        }
    }

    fun applyPuppetWarp(
        pins: List<PuppetPinPoint>,
        mode: String,
        density: String = "normal",
        expansion: Float = 2f
    ) {
        invalidateProxyQueue()
        dismissCompareOriginal()
        _uiState.update { it.copy(isBusy = true) }
        paintExecutor.execute {
            runCatching {
                val info = engine.applyPuppetWarp(pins, mode, density, expansion)
                val layers = engine.getLayers()
                _uiState.update { it.copy(docInfo = info, layers = layers, isBusy = false) }
                NativeSurfaceBridge.nativeRenderFrame()
                refreshLayerThumbnails()
                showStatus("Kukla bükme uygulandı", 1800L)
            }.onFailure { err ->
                _uiState.update { it.copy(isBusy = false) }
                showStatus("Kukla bükme hatası: ${err.message}", 2600L)
            }
        }
    }

    fun resizeImage(width: Int, height: Int, resample: String) {
        invalidateProxyQueue()
        dismissCompareOriginal()
        _uiState.update { it.copy(isBusy = true) }
        paintExecutor.execute {
            runCatching {
                val info = engine.resizeImage(width.coerceIn(16, 8192).toUInt(), height.coerceIn(16, 8192).toUInt(), resample)
                val layers = engine.getLayers()
                _uiState.update {
                    it.copy(
                        docInfo = info,
                        layers = layers,
                        isBusy = false,
                        fitCameraSignal = it.fitCameraSignal + 1
                    )
                }
                NativeSurfaceBridge.nativeRenderFrame()
                refreshLayerThumbnails()
                showStatus("Boyutlandırıldı: ${info.width}×${info.height} px", 2000L)
            }.onFailure { err ->
                _uiState.update { it.copy(isBusy = false) }
                showStatus("Boyutlandırma hatası: ${err.message}", 2600L)
            }
        }
    }

    fun rasterizeActiveTextLayer() {
        dismissCompareOriginal()
        paintExecutor.execute {
            runCatching {
                val info = engine.rasterizeActiveTextLayer()
                val layers = engine.getLayers()
                _uiState.update { it.copy(docInfo = info, layers = layers) }
                NativeSurfaceBridge.nativeRenderFrame()
                refreshLayerThumbnails()
                showStatus("Metin piksel katmanına çevrildi", 1800L)
            }.onFailure { err ->
                showStatus("Rasterleştirme hatası: ${err.message}", 2400L)
            }
        }
    }

    fun loadLightDistortThumbs() {
        paintExecutor.execute {
            runCatching {
                val raw = engine.renderLightDistortThumbs(80u)
                val out = ArrayList<ImageBitmap>(raw.size)
                for (t in raw) {
                    val w = t.width.toInt()
                    val h = t.height.toInt()
                    if (w > 0 && h > 0 && t.rgba.size >= w * h * 4) {
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        bmp.copyPixelsFromBuffer(ByteBuffer.wrap(t.rgba))
                        out.add(bmp.asImageBitmap())
                    }
                }
                _lightDistortThumbs.value = out
            }
        }
    }

    fun updateProxyLightDistort(
        kind: String,
        amount: Float,
        detail: Float,
        paramX: Float,
        paramY: Float,
        variant: String
    ) {
        enqueueProxyJob(ProxyJob.LightDistort(kind, amount, detail, paramX, paramY, variant))
    }

    fun applyLightDistort(
        kind: String,
        amount: Float,
        detail: Float,
        paramX: Float,
        paramY: Float,
        variant: String,
        label: String
    ) {
        invalidateProxyQueue()
        dismissCompareOriginal()
        _uiState.update { it.copy(isBusy = true) }
        paintExecutor.execute {
            runCatching {
                val info = engine.applyLightDistort(kind, amount, detail, paramX, paramY, variant)
                val layers = engine.getLayers()
                _uiState.update { it.copy(docInfo = info, layers = layers, isBusy = false) }
                NativeSurfaceBridge.nativeRenderFrame()
                refreshLayerThumbnails()
                showStatus("$label uygulandı", 1800L)
            }.onFailure { err ->
                _uiState.update { it.copy(isBusy = false) }
                showStatus("Efekt hatası: ${err.message}", 2600L)
            }
        }
    }

    fun loadGalleryThumbs() {
        paintExecutor.execute {
            runCatching {
                val raw = engine.renderFilterGalleryThumbs(80u)
                val out = ArrayList<ImageBitmap>(raw.size)
                for (t in raw) {
                    val w = t.width.toInt()
                    val h = t.height.toInt()
                    if (w > 0 && h > 0 && t.rgba.size >= w * h * 4) {
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        bmp.copyPixelsFromBuffer(ByteBuffer.wrap(t.rgba))
                        out.add(bmp.asImageBitmap())
                    }
                }
                _galleryThumbs.value = out
            }
        }
    }

    fun updateProxyGalleryFilter(kind: String, amount: Float, detail: Float) {
        enqueueProxyJob(ProxyJob.GalleryFilter(kind, amount, detail))
    }

    fun applyGalleryFilter(kind: String, amount: Float, detail: Float, label: String) {
        invalidateProxyQueue()
        dismissCompareOriginal()
        _uiState.update { it.copy(isBusy = true) }
        paintExecutor.execute {
            runCatching {
                val info = engine.applyGalleryFilter(kind, amount, detail)
                val layers = engine.getLayers()
                _uiState.update { it.copy(docInfo = info, layers = layers, isBusy = false) }
                NativeSurfaceBridge.nativeRenderFrame()
                refreshLayerThumbnails()
                showStatus("$label uygulandı", 1800L)
            }.onFailure { err ->
                _uiState.update { it.copy(isBusy = false) }
                showStatus("Filtre hatası: ${err.message}", 2600L)
            }
        }
    }

    fun loadCinemaThumbs() {
        paintExecutor.execute {
            runCatching {
                val raw = engine.renderCinemaLookThumbs(88u)
                val out = ArrayList<ImageBitmap>(raw.size)
                for (t in raw) {
                    val w = t.width.toInt()
                    val h = t.height.toInt()
                    if (w > 0 && h > 0 && t.rgba.size >= w * h * 4) {
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        bmp.copyPixelsFromBuffer(ByteBuffer.wrap(t.rgba))
                        out.add(bmp.asImageBitmap())
                    }
                }
                _cinemaThumbs.value = out
            }
        }
    }
    private val pendingLoupeReq = AtomicReference<Triple<Float, Float, Float>?>(null)
    private val loupeDrainScheduled = AtomicBoolean(false)

    fun updateLoupe(docX: Float, docY: Float, halfExtentPx: Float) {
        pendingLoupeReq.set(Triple(docX, docY, halfExtentPx))
        if (loupeDrainScheduled.compareAndSet(false, true)) {
            paintExecutor.execute {
                while (true) {
                    val req = pendingLoupeReq.getAndSet(null)
                    if (req == null) {
                        loupeDrainScheduled.set(false)
                        if (pendingLoupeReq.get() == null) return@execute
                        if (!loupeDrainScheduled.compareAndSet(false, true)) return@execute
                        continue
                    }
                    runCatching {
                        val patch = engine.sampleLoupePatch(req.first, req.second, req.third, 64u)
                        if (patch != null) {
                            val w = patch.width.toInt()
                            val h = patch.height.toInt()
                            if (w > 0 && h > 0 && patch.rgba.size >= w * h * 4) {
                                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                bmp.copyPixelsFromBuffer(ByteBuffer.wrap(patch.rgba))
                                _loupeBitmap.value = bmp.asImageBitmap()
                            }
                        }
                    }
                }
            }
        }
    }

    fun hideLoupe() {
        pendingLoupeReq.set(null)
        _loupeBitmap.value = null
    }

    private val _uiState = MutableStateFlow(
        EditorUiState(engineVersion = engineVersionInfo())
    )
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    init {
        refreshState()
        syncBrushToEngine()
    }

    override fun onCleared() {
        super.onCleared()
        paintExecutor.shutdown()
    }

    fun loadSavedGallery(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val items = SavedGalleryStore.list(context)
            _uiState.update { it.copy(savedPhotos = items) }
        }
    }

    fun deleteSavedPhoto(context: Context, id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val remaining = SavedGalleryStore.delete(context, id)
            _uiState.update { it.copy(savedPhotos = remaining) }
        }
    }

    fun showStatus(msg: String, autoHideMs: Long = 3000L) {
        statusAutoHideJob?.cancel()
        _uiState.update { it.copy(statusMessage = msg) }
        if (autoHideMs > 0) {
            statusAutoHideJob = viewModelScope.launch {
                delay(autoHideMs)
                _uiState.update { it.copy(statusMessage = null) }
            }
        }
    }

    fun refreshState() {
        paintExecutor.execute {
            runCatching {
                val info = engine.getDocumentInfo()
                val layers = engine.getLayers()
                val sel = engine.getSelectionBounds()
                _uiState.update { it.copy(docInfo = info, layers = layers, selectionBounds = sel) }
                NativeSurfaceBridge.nativeRenderFrame()
                getLastGpuError()?.let { gpuErr -> showStatus(gpuErr, 6000L) }
            }.onFailure { err ->
                showStatus(err.message ?: "Bilinmeyen hata")
            }
        }
    }

    private val thumbRefreshScheduled = java.util.concurrent.atomic.AtomicBoolean(false)

    @Volatile
    private var layersDrawerOpen: Boolean = false

    fun setLayersDrawerOpen(open: Boolean) {
        layersDrawerOpen = open
        if (open) refreshLayerThumbnails(force = true)
    }

    fun refreshLayerThumbnails(force: Boolean = false) {
        if (!force && !layersDrawerOpen) return
        if (!thumbRefreshScheduled.compareAndSet(false, true)) return
        paintExecutor.execute {
            try {
                runCatching {
                    val layers = engine.getLayers()
                    val out = LinkedHashMap<ULong, ImageBitmap>(layers.size)
                    for (layer in layers) {
                        val thumb = runCatching { engine.getLayerThumbnail(layer.id, 88u) }.getOrNull() ?: continue
                        val w = thumb.width.toInt()
                        val h = thumb.height.toInt()
                        if (w > 0 && h > 0 && thumb.rgba.size >= w * h * 4) {
                            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            bmp.copyPixelsFromBuffer(ByteBuffer.wrap(thumb.rgba))
                            out[layer.id] = bmp.asImageBitmap()
                        }
                    }
                    _layerThumbnails.value = out
                }
            } finally {
                thumbRefreshScheduled.set(false)
            }
        }
    }

    fun dismissCompareOriginal() {
        if (!_uiState.value.compareOriginal) return
        _uiState.update { it.copy(compareOriginal = false) }
        paintExecutor.execute {
            runCatching {
                engine.setCompareOriginal(false)
                NativeSurfaceBridge.nativeRenderFrame()
            }
        }
    }

    fun setTool(tool: ActiveTool) {
        dismissCompareOriginal()
        _uiState.update {
            it.copy(
                activeTool = tool,
                pickingCloneSource = ((tool == ActiveTool.CLONE_STAMP || tool == ActiveTool.HEALING_BRUSH) && it.cloneSourceDoc == null)
            )
        }
    }

    fun requestPickCloneSource() {
        _uiState.update { it.copy(activeTool = ActiveTool.CLONE_STAMP, pickingCloneSource = true) }
        showStatus("Klonlanacak kaynak noktasına dokun", 2400L)
    }

    fun setCloneSource(docX: Float, docY: Float) {
        _uiState.update { it.copy(cloneSourceDoc = docX to docY, pickingCloneSource = false) }
        showStatus(if (_uiState.value.activeTool == ActiveTool.HEALING_BRUSH) "Düzeltme kaynağı seçildi — şimdi onaracağın yere sürükle" else "Klon kaynağı seçildi — şimdi boyamak istediğin yere sürükle", 2200L)
    }

    fun toggleSelectEllipse() {
        _uiState.update { it.copy(selectEllipse = !it.selectEllipse) }
    }

    fun toggleWandSmartMode() {
        val next = !_uiState.value.wandSmartMode
        _uiState.update { it.copy(wandSmartMode = next) }
        showStatus(
            if (next) "Akıllı Nesne Değneği açık: dokunduğun bütün nesneyi kenarlarıyla seçer"
            else "Renk Değneği açık: toleransa göre benzer renkleri seçer",
            2200L
        )
    }

    fun updateWandTolerance(tol: Float) {
        val clamped = tol.coerceIn(0f, 150f)
        _uiState.update { it.copy(wandTolerance = clamped) }
        if (!_uiState.value.wandSmartMode && lastWandTapDoc != null) {
            pendingWandTol.set(clamped)
            if (wandTolDrainScheduled.compareAndSet(false, true)) {
                paintExecutor.execute { drainPendingWandTolerance() }
            }
        }
    }

    private fun drainPendingWandTolerance() {
        while (true) {
            val nextTol = pendingWandTol.getAndSet(null)
            if (nextTol == null) {
                wandTolDrainScheduled.set(false)
                if (pendingWandTol.get() == null) return
                if (!wandTolDrainScheduled.compareAndSet(false, true)) return
                continue
            }
            val pt = lastWandTapDoc ?: continue
            val s = _uiState.value
            runCatching {
                val b = engine.selectMagicWand(pt.first.toInt(), pt.second.toInt(), nextTol.toInt().toUInt(), s.wandContiguous, "replace")
                val info = engine.getDocumentInfo()
                _uiState.update { it.copy(docInfo = info, selectionBounds = b) }
                NativeSurfaceBridge.nativeRenderFrame()
            }
        }
    }

    fun toggleWandContiguous() {
        val next = !_uiState.value.wandContiguous
        _uiState.update { it.copy(wandContiguous = next) }
        showStatus(
            if (next) "Bitişik açık: sadece dokunduğun bağlantılı bölge seçilir"
            else "Bitişik kapalı: fotoğraftaki tüm benzer renkler seçilir",
            2200L
        )
    }

    fun toggleSelectAddMode() {
        val next = !_uiState.value.selectAddMode
        _uiState.update { it.copy(selectAddMode = next, selectSubtractMode = false) }
        showStatus(
            if (next) "Ekle (+) açık: yeni dokunuşlar mevcut seçime eklenir"
            else "Yeni seçim modu: her dokunuş yeni seçim başlatır",
            2200L
        )
    }

    fun toggleSelectSubtractMode() {
        val next = !_uiState.value.selectSubtractMode
        _uiState.update { it.copy(selectSubtractMode = next, selectAddMode = false) }
        showStatus(
            if (next) "Çıkar (-) açık: dokunduğun yerler seçimden çıkarılır"
            else "Normal ekleme moduna dönüldü",
            2200L
        )
    }

    fun selectSubject() {
        _uiState.update { it.copy(isBusy = true) }
        paintExecutor.execute {
            runCatching {
                val b = engine.selectSubject()
                val info = engine.getDocumentInfo()
                _uiState.update { it.copy(docInfo = info, selectionBounds = b, isBusy = false) }
                NativeSurfaceBridge.nativeRenderFrame()
                showStatus(if (b != null) "Ana konu otomatik seçildi" else "Konu bulunamadı — Akıllı Fırça veya Elle Ekle kullanabilirsin", 2400L)
            }.onFailure { err ->
                _uiState.update { it.copy(isBusy = false) }
                showStatus("Konu seçim hatası: ${err.message}", 3000L)
            }
        }
    }

    fun applySelectionStroke(samples: List<StrokeSample>) {
        if (samples.isEmpty()) return
        val s = _uiState.value
        val tool = s.activeTool
        val size = s.brushSize
        val sub = (tool == ActiveTool.SELECT_BRUSH_SUB) || (tool == ActiveTool.SELECT_QUICK && s.selectSubtractMode)
        if (tool == ActiveTool.SELECT_QUICK) {
            _uiState.update { it.copy(isBusy = true) }
        }
        paintExecutor.execute {
            runCatching {
                val b = when (tool) {
                    ActiveTool.SELECT_QUICK -> engine.selectQuickStroke(samples, size, sub)
                    ActiveTool.SELECT_BRUSH_ADD -> engine.paintSelectionStroke(samples, size, false)
                    ActiveTool.SELECT_BRUSH_SUB -> engine.paintSelectionStroke(samples, size, true)
                    else -> return@runCatching
                }
                val info = engine.getDocumentInfo()
                _uiState.update { it.copy(docInfo = info, selectionBounds = b, isBusy = false) }
                NativeSurfaceBridge.nativeRenderFrame()
            }.onFailure { err ->
                _uiState.update { it.copy(isBusy = false) }
                showStatus("Seçim fırçası hatası: ${err.message}", 3000L)
            }
        }
    }

    fun selectRect(x: Int, y: Int, width: Int, height: Int) {
        val s = _uiState.value
        val mode = when { s.selectSubtractMode -> "subtract"; s.selectAddMode -> "add"; else -> "replace" }
        paintExecutor.execute {
            runCatching {
                val b = engine.selectRect(x, y, width.coerceAtLeast(1).toUInt(), height.coerceAtLeast(1).toUInt(), s.selectEllipse, mode)
                val infoSel = runCatching { engine.getDocumentInfo() }.getOrNull()
                _uiState.update { it.copy(docInfo = infoSel ?: it.docInfo, selectionBounds = b) }
                NativeSurfaceBridge.nativeRenderFrame()
                showStatus(if (s.selectEllipse) "Elips alan seçildi" else "Dikdörtgen alan seçildi", 1600L)
            }.onFailure { err ->
                showStatus("Seçim hatası: ${err.message}", 3000L)
            }
        }
    }

    fun selectMagicWand(docX: Float, docY: Float) {
        lastWandTapDoc = docX to docY
        val s = _uiState.value
        val tol = s.wandTolerance.toInt().coerceIn(0, 255).toUInt()
        val mode = when { s.selectSubtractMode -> "subtract"; s.selectAddMode -> "add"; else -> "replace" }
        _uiState.update { it.copy(isBusy = true) }
        paintExecutor.execute {
            runCatching {
                val b = if (s.wandSmartMode) {
                    engine.selectObjectAtPoint(docX.toInt(), docY.toInt(), mode)
                } else {
                    engine.selectMagicWand(docX.toInt(), docY.toInt(), tol, s.wandContiguous, mode)
                }
                _uiState.update { it.copy(selectionBounds = b, isBusy = false) }
                NativeSurfaceBridge.nativeRenderFrame()
                showStatus(
                    if (b != null) {
                        if (s.wandSmartMode) "Nesne seçildi" else "Renk alanı seçildi"
                    } else "Seçilecek alan bulunamadı",
                    1600L
                )
            }.onFailure { err ->
                _uiState.update { it.copy(isBusy = false) }
                showStatus("Değnek hatası: ${err.message}", 3000L)
            }
        }
    }

    fun selectInverse() {
        paintExecutor.execute {
            runCatching {
                val b = engine.selectInverse()
                val infoSel = runCatching { engine.getDocumentInfo() }.getOrNull()
                _uiState.update { it.copy(docInfo = infoSel ?: it.docInfo, selectionBounds = b) }
                NativeSurfaceBridge.nativeRenderFrame()
                showStatus("Seçim tersine çevrildi", 1800L)
            }.onFailure { err ->
                showStatus("Ters seçim hatası: ${err.message}", 3000L)
            }
        }
    }

    fun deselect() {
        paintExecutor.execute {
            runCatching {
                val info = engine.selectDeselect()
                _uiState.update { it.copy(docInfo = info, selectionBounds = null) }
                NativeSurfaceBridge.nativeRenderFrame()
                showStatus("Seçim kaldırıldı", 1500L)
            }
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(isBusy = true) }
        paintExecutor.execute {
            runCatching {
                val info = engine.clearSelectionPixels()
                val layers = engine.getLayers()
                _uiState.update { it.copy(docInfo = info, layers = layers, isBusy = false) }
                NativeSurfaceBridge.nativeRenderFrame()
                showStatus("Seçili alan silindi", 1800L)
            }.onFailure { err ->
                _uiState.update { it.copy(isBusy = false) }
                showStatus("Silme hatası: ${err.message}", 3000L)
            }
        }
    }

    fun fillSelection() {
        val hex = _uiState.value.colorHex
        _uiState.update { it.copy(isBusy = true) }
        paintExecutor.execute {
            runCatching {
                val info = engine.fillSelectionPixels(hex)
                val layers = engine.getLayers()
                _uiState.update { it.copy(docInfo = info, layers = layers, isBusy = false) }
                NativeSurfaceBridge.nativeRenderFrame()
                showStatus("Seçili alan dolduruldu", 1800L)
            }.onFailure { err ->
                _uiState.update { it.copy(isBusy = false) }
                showStatus("Doldurma hatası: ${err.message}", 3000L)
            }
        }
    }

    fun revealSelectionMask() {
        _uiState.update { it.copy(isBusy = true) }
        paintExecutor.execute {
            runCatching {
                val info = engine.revealSelectionMask()
                val layers = engine.getLayers()
                val sel = engine.getSelectionBounds()
                _uiState.update { it.copy(docInfo = info, layers = layers, selectionBounds = sel, isBusy = false) }
                NativeSurfaceBridge.nativeRenderFrame()
                showStatus("Seçim katman maskesine dönüştürüldü", 2200L)
            }.onFailure { err ->
                _uiState.update { it.copy(isBusy = false) }
                showStatus("Maske hatası: ${err.message}", 3000L)
            }
        }
    }

    fun contentAwareRemove() {
        _uiState.update { it.copy(isBusy = true) }
        paintExecutor.execute {
            runCatching {
                val info = engine.contentAwareRemove()
                val layers = engine.getLayers()
                _uiState.update { it.copy(docInfo = info, layers = layers, selectionBounds = null, isBusy = false) }
                NativeSurfaceBridge.nativeRenderFrame()
                showStatus("Nesne içeriğe duyarlı olarak silindi", 2200L)
            }.onFailure { err ->
                _uiState.update { it.copy(isBusy = false) }
                showStatus("Nesne silme hatası: ${err.message}", 3800L)
            }
        }
    }

    fun perspectiveCrop(
        tlX: Float, tlY: Float,
        trX: Float, trY: Float,
        brX: Float, brY: Float,
        blX: Float, blY: Float
    ) {
        _uiState.update { it.copy(isBusy = true) }
        paintExecutor.execute {
            runCatching {
                val info = engine.perspectiveCrop(tlX, tlY, trX, trY, brX, brY, blX, blY)
                val layers = engine.getLayers()
                _uiState.update {
                    it.copy(
                        docInfo = info,
                        layers = layers,
                        selectionBounds = null,
                        isBusy = false,
                        fitCameraSignal = it.fitCameraSignal + 1
                    )
                }
                showStatus("Perspektif düzeltildi (${info.width}×${info.height})", 2200L)
            }.onFailure { err ->
                _uiState.update { it.copy(isBusy = false) }
                showStatus("Perspektif hatası: ${err.message}", 4200L)
            }
        }
    }

    // --- 60 FPS Exact Proxy Preview Engine ("Genel Güç") ---
    fun beginLayerProxyPreview() {
        dismissCompareOriginal()
        val gen = invalidateProxyQueue()
        paintExecutor.execute {
            if (gen != proxyGeneration.get()) return@execute
            runCatching {
                engine.beginLayerProxyPreview()
                if (gen == proxyGeneration.get()) NativeSurfaceBridge.nativeRenderFrame()
            }
        }
    }

    fun updateProxyLayerEffect(kind: String, colorHex: String, size: Float, opacity: Float, distance: Float) {
        enqueueProxyJob(ProxyJob.LayerFx(kind, colorHex, size, opacity, distance))
    }

    fun beginProxyPreview() {
        dismissCompareOriginal()
        val gen = invalidateProxyQueue()
        paintExecutor.execute {
            if (gen != proxyGeneration.get()) return@execute
            runCatching {
                engine.beginProxyPreview()
                if (gen == proxyGeneration.get()) NativeSurfaceBridge.nativeRenderFrame()
            }
        }
    }

    fun updateProxyAdjustment(kind: String, paramsJson: String) {
        enqueueProxyJob(ProxyJob.Adjust(kind, paramsJson))
    }

    fun updateProxyBlur(radius: Float) {
        enqueueProxyJob(ProxyJob.Blur(radius))
    }

    fun updateProxySharpen(amount: Float) {
        enqueueProxyJob(ProxyJob.Sharpen(amount))
    }

    private fun drainPendingProxyJobs() {
        if (_uiState.value.compareOriginal) {
            _uiState.update { it.copy(compareOriginal = false) }
        }
        while (true) {
            val stamped = pendingProxyJob.getAndSet(null)
            if (stamped == null) {
                proxyDrainScheduled.set(false)
                if (pendingProxyJob.get() == null) return
                if (!proxyDrainScheduled.compareAndSet(false, true)) return
                continue
            }
            // Drop stale preview jobs immediately if user closed/switched panel or committed Apply
            if (stamped.gen != proxyGeneration.get()) continue
            val job = stamped.job
            runCatching {
                when (job) {
                    is ProxyJob.Adjust -> engine.updateProxyAdjustment(job.kind, job.paramsJson)
                    is ProxyJob.Blur -> engine.updateProxyBlur(job.radius)
                    is ProxyJob.Sharpen -> engine.updateProxySharpen(job.amount)
                    is ProxyJob.LayerFx -> engine.updateProxyLayerEffect(job.kind, job.colorHex, job.size, job.opacity, job.distance)
                    is ProxyJob.CameraLight -> engine.updateProxyCameraLight(
                        job.exposure, job.shadows, job.highlights, job.vibrance,
                        job.temperature, job.tint, job.clarity, job.dehaze
                    )
                    is ProxyJob.Gradient -> engine.updateProxyGradient(
                        job.fromX, job.fromY, job.toX, job.toY,
                        job.style, job.startHex, job.endHex, job.opacity
                    )
                    is ProxyJob.Shape -> engine.updateProxyShape(
                        job.kind, job.x0, job.y0, job.x1, job.y1,
                        job.fillHex, job.strokeHex, job.strokeWidth, job.cornerRadius, job.sides
                    )
                    is ProxyJob.CinemaLook -> engine.updateProxyCinemaLook(job.preset, job.intensity, job.vignette, job.grain)
                    is ProxyJob.MotionBlur -> engine.updateProxyMotionBlur(job.angle, job.distance)
                    is ProxyJob.RadialBlur -> engine.updateProxyRadialBlur(job.amount, job.method)
                    is ProxyJob.HslMixer -> engine.updateProxyHslMixer(job.h, job.s, job.l)
                    is ProxyJob.GalleryFilter -> engine.updateProxyGalleryFilter(job.kind, job.amount, job.detail)
                    is ProxyJob.LightDistort -> engine.updateProxyLightDistort(job.kind, job.amount, job.detail, job.paramX, job.paramY, job.variant)
                    is ProxyJob.TextPreview -> engine.updateProxyTextPreview(
                        job.editLayerId, job.text, job.x, job.y, job.sizePx, job.colorHex, job.align,
                        job.fontFamily, job.bold, job.italic, job.underline, job.strikethrough,
                        job.caps, job.hScale, job.vScale, job.tracking, job.lineHeight, job.vertical,
                        job.warpStyle, job.warpBend, job.warpHDist, job.warpVDist
                    )
                    is ProxyJob.PuppetWarp -> engine.updateProxyPuppetWarp(job.pins, job.mode, job.density, job.expansion)
                    is ProxyJob.BlendIf -> engine.updateProxyBlendIf(
                        job.thisBlack.coerceIn(0, 255).toUByte(),
                        job.thisWhite.coerceIn(0, 255).toUByte(),
                        job.underBlack.coerceIn(0, 255).toUByte(),
                        job.underWhite.coerceIn(0, 255).toUByte(),
                        job.feather.coerceIn(0, 80).toUByte()
                    )
                    is ProxyJob.CameraRawJson -> engine.updateProxyCameraRawJson(job.paramsJson)
                    is ProxyJob.LensCorrection -> engine.updateProxyLensCorrection(
                        job.distortion, job.vignetteAmount, job.redCyan,
                        job.blueYellow, job.vertical, job.horizontal
                    )
                    is ProxyJob.ColorLookup -> engine.updateProxyColorLookup(job.fileName, job.lutText, job.opacity)
                    is ProxyJob.LayerStyleStack -> engine.updateProxyLayerStyleStack(job.effectsJson)
                    is ProxyJob.ContentAwareFill -> engine.updateProxyContentAwareFill(
                        job.sampling, job.colorAdaptation, job.rotationAdaptation,
                        job.scale, job.mirror, job.sampleAllLayers, job.output
                    )
                    is ProxyJob.ContentAwareScale -> engine.updateProxyContentAwareScale(
                        job.scaleX, job.scaleY, job.amount, job.protectSkinTones
                    )
                    is ProxyJob.ContentAwareScalePro -> engine.updateProxyContentAwareScalePro(
                        job.scaleX, job.scaleY, job.amount, job.protectSkinTones, job.protectStrokes
                    )
                    is ProxyJob.ContentAwareFillPro -> engine.updateProxyContentAwareFillPro(
                        job.sampling, job.colorAdaptation, job.rotationAdaptation,
                        job.scale, job.mirror, job.sampleAllLayers, job.strokes
                    )
                    is ProxyJob.SelectSky -> engine.updateProxySelectSky(
                        job.threshold, job.softness, job.mode, job.sampleAllLayers
                    )
                    is ProxyJob.ColorRange -> engine.updateProxyColorRange(
                        job.preset, job.colorHex, job.fuzziness, job.invert, job.mode, job.sampleAllLayers
                    )
                    is ProxyJob.FocusArea -> engine.updateProxyFocusArea(
                        job.rangePct, job.noisePct, job.mode, job.sampleAllLayers
                    )
                    is ProxyJob.ImageResize -> engine.updateProxyImageResize(
                        job.width.coerceIn(16, 8192).toUInt(),
                        job.height.coerceIn(16, 8192).toUInt(),
                        job.resample
                    )
                    is ProxyJob.ModifySelection -> engine.updateProxyModifySelection(
                        job.op, job.radius
                    )
                    is ProxyJob.TextProPreview -> engine.updateProxyTextProPreview(
                        job.editLayerId, job.text, job.x, job.y, job.sizePx, job.colorHex, job.align, job.fontFamily,
                        job.bold, job.italic, job.underline, job.strikethrough, job.caps, job.hScale, job.vScale,
                        job.tracking, job.lineHeight, job.vertical, job.warpStyle, job.warpBend, job.warpHDist, job.warpVDist,
                        job.extras
                    )
                    is ProxyJob.ContentAwareMove -> engine.updateProxyContentAwareMove(
                        job.dx, job.dy, job.mode,
                        job.structure.coerceIn(1, 7).toUByte(),
                        job.color.coerceIn(0, 10).toUByte(),
                        job.scalePct, job.scalePct, job.rotateDeg, job.sampleAllLayers
                    )
                }
                if (stamped.gen == proxyGeneration.get()) {
                    NativeSurfaceBridge.nativeRenderFrame()
                }
            }
        }
    }


    fun updateProxyCameraLight(
        exposure: Float,
        shadows: Float,
        highlights: Float,
        vibrance: Float,
        temperature: Float,
        tint: Float,
        clarity: Float,
        dehaze: Float
    ) {
        enqueueProxyJob(ProxyJob.CameraLight(exposure, shadows, highlights, vibrance, temperature, tint, clarity, dehaze))
    }

    fun applyCameraLight(
        exposure: Float,
        shadows: Float,
        highlights: Float,
        vibrance: Float,
        temperature: Float,
        tint: Float,
        clarity: Float,
        dehaze: Float
    ) {
        invalidateProxyQueue()
        dismissCompareOriginal()
        _uiState.update { it.copy(isBusy = true) }
        paintExecutor.execute {
            runCatching {
                val info = engine.applyCameraLight(
                    exposure, shadows, highlights, vibrance, temperature, tint, clarity, dehaze
                )
                val layers = engine.getLayers()
                _uiState.update { it.copy(docInfo = info, layers = layers, isBusy = false) }
                NativeSurfaceBridge.nativeRenderFrame()
                refreshLayerThumbnails()
                showStatus("Kamera Işık uygulandı", 1800L)
            }.onFailure { err ->
                _uiState.update { it.copy(isBusy = false) }
                showStatus("Kamera Işık hatası: ${err.message}", 2800L)
            }
        }
    }

    fun updateProxyGradient(
        fromX: Float,
        fromY: Float,
        toX: Float,
        toY: Float,
        style: String,
        startHex: String,
        endHex: String,
        opacity: Float
    ) {
        enqueueProxyJob(ProxyJob.Gradient(fromX, fromY, toX, toY, style, startHex, endHex, opacity))
    }

    fun applyGradientFill(
        fromX: Float,
        fromY: Float,
        toX: Float,
        toY: Float,
        style: String,
        startHex: String,
        endHex: String,
        opacity: Float
    ) {
        invalidateProxyQueue()
        dismissCompareOriginal()
        _uiState.update { it.copy(isBusy = true) }
        paintExecutor.execute {
            runCatching {
                val info = engine.applyGradientFill(fromX, fromY, toX, toY, style, startHex, endHex, opacity)
                val layers = engine.getLayers()
                val sel = engine.getSelectionBounds()
                _uiState.update { it.copy(docInfo = info, layers = layers, selectionBounds = sel, isBusy = false) }
                NativeSurfaceBridge.nativeRenderFrame()
                refreshLayerThumbnails()
                showStatus("Degrade katmanı eklendi", 1800L)
            }.onFailure { err ->
                _uiState.update { it.copy(isBusy = false) }
                showStatus("Degrade hatası: ${err.message}", 2800L)
            }
        }
    }

    fun updateProxyShape(
        kind: String,
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        fillHex: String,
        strokeHex: String,
        strokeWidth: Float,
        cornerRadius: Float,
        sides: Int
    ) {
        enqueueProxyJob(
            ProxyJob.Shape(kind, x0, y0, x1, y1, fillHex, strokeHex, strokeWidth, cornerRadius, sides.coerceIn(3, 12).toUInt())
        )
    }

    fun createShapeLayer(
        kind: String,
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        fillHex: String,
        strokeHex: String,
        strokeWidth: Float,
        cornerRadius: Float,
        sides: Int
    ) {
        invalidateProxyQueue()
        dismissCompareOriginal()
        _uiState.update { it.copy(isBusy = true) }
        paintExecutor.execute {
            runCatching {
                val info = engine.createShapeLayer(
                    kind, x0, y0, x1, y1, fillHex, strokeHex, strokeWidth, cornerRadius, sides.coerceIn(3, 12).toUInt()
                )
                val layers = engine.getLayers()
                _uiState.update { it.copy(docInfo = info, layers = layers, isBusy = false) }
                NativeSurfaceBridge.nativeRenderFrame()
                refreshLayerThumbnails()
                showStatus("Şekil katmanı eklendi", 1800L)
            }.onFailure { err ->
                _uiState.update { it.copy(isBusy = false) }
                showStatus("Şekil hatası: ${err.message}", 2800L)
            }
        }
    }


    // Coalesced 60 FPS Liquify live drag state
    private val pendingLiquifyPt = AtomicReference<Pair<Float, Float>?>(null)
    private val liquifyDrainScheduled = AtomicBoolean(false)

    fun beginLiquify() {
        dismissCompareOriginal()
        invalidateProxyQueue()
        pendingLiquifyPt.set(null)
        paintExecutor.execute {
            runCatching {
                engine.beginLiquifySession()
                _uiState.update { it.copy(liquifyCanUndo = false, liquifyCanRedo = false) }
                NativeSurfaceBridge.nativeRenderFrame()
            }.onFailure { err ->
                showStatus("Sıvılaştırma başlatılamadı: ${err.message}", 2600L)
            }
        }
    }

    fun onLiquifyStart(tool: String, size: Float, pressure: Float, density: Float, docX: Float, docY: Float) {
        pendingLiquifyPt.set(null)
        paintExecutor.execute {
            runCatching {
                engine.liquifyStrokeBegin(tool, size, pressure, density, docX, docY)
                val hs = engine.getLiquifyHistoryState()
                _uiState.update { it.copy(liquifyCanUndo = hs.canUndo, liquifyCanRedo = hs.canRedo) }
                NativeSurfaceBridge.nativeRenderFrame()
            }
        }
    }

    fun onLiquifyMove(docX: Float, docY: Float) {
        pendingLiquifyPt.set(docX to docY)
        if (liquifyDrainScheduled.compareAndSet(false, true)) {
            paintExecutor.execute {
                while (true) {
                    val pt = pendingLiquifyPt.getAndSet(null)
                    if (pt == null) {
                        liquifyDrainScheduled.set(false)
                        if (pendingLiquifyPt.get() == null) return@execute
                        if (!liquifyDrainScheduled.compareAndSet(false, true)) return@execute
                        continue
                    }
                    runCatching {
                        engine.liquifyStrokeMove(pt.first, pt.second)
                        NativeSurfaceBridge.nativeRenderFrame()
                    }
                }
            }
        }
    }

    fun onLiquifyEnd() {
        paintExecutor.execute {
            runCatching { engine.liquifyStrokeEnd() }
        }
    }

    fun resetLiquify() {
        pendingLiquifyPt.set(null)
        paintExecutor.execute {
            runCatching {
                engine.resetLiquifySession()
                val hs = engine.getLiquifyHistoryState()
                _uiState.update { it.copy(liquifyCanUndo = hs.canUndo, liquifyCanRedo = hs.canRedo) }
                NativeSurfaceBridge.nativeRenderFrame()
                showStatus("Sıvılaştırma sıfırlandı", 1400L)
            }
        }
    }

    fun commitLiquify() {
        pendingLiquifyPt.set(null)
        _uiState.update { it.copy(isBusy = true) }
        paintExecutor.execute {
            runCatching {
                val info = engine.commitLiquifySession()
                val layers = engine.getLayers()
                _uiState.update { it.copy(docInfo = info, layers = layers, liquifyCanUndo = false, liquifyCanRedo = false, isBusy = false) }
                NativeSurfaceBridge.nativeRenderFrame()
                refreshLayerThumbnails()
                showStatus("Sıvılaştırma uygulandı", 1800L)
            }.onFailure { err ->
                _uiState.update { it.copy(isBusy = false) }
                showStatus("Sıvılaştırma hatası: ${err.message}", 2800L)
            }
        }
    }

    fun liquifyUndo() {
        pendingLiquifyPt.set(null)
        paintExecutor.execute {
            runCatching {
                val hs = engine.liquifyUndo()
                _uiState.update { it.copy(liquifyCanUndo = hs.canUndo, liquifyCanRedo = hs.canRedo) }
                NativeSurfaceBridge.nativeRenderFrame()
            }
        }
    }

    fun liquifyRedo() {
        pendingLiquifyPt.set(null)
        paintExecutor.execute {
            runCatching {
                val hs = engine.liquifyRedo()
                _uiState.update { it.copy(liquifyCanUndo = hs.canUndo, liquifyCanRedo = hs.canRedo) }
                NativeSurfaceBridge.nativeRenderFrame()
            }
        }
    }

    fun cancelLiquify() {
        pendingLiquifyPt.set(null)
        _uiState.update { it.copy(liquifyCanUndo = false, liquifyCanRedo = false) }
        paintExecutor.execute {
            runCatching {
                engine.cancelLiquifySession()
                NativeSurfaceBridge.nativeRenderFrame()
            }
        }
    }


    fun applyAutoEnhance(mode: String, label: String) {
        dismissCompareOriginal()
        invalidateProxyQueue()
        _uiState.update { it.copy(isBusy = true) }
        paintExecutor.execute {
            runCatching {
                val info = engine.applyAutoEnhance(mode)
                val layers = engine.getLayers()
                _uiState.update { it.copy(docInfo = info, layers = layers, isBusy = false) }
                NativeSurfaceBridge.nativeRenderFrame()
                refreshLayerThumbnails()
                showStatus("$label uygulandı", 1800L)
            }.onFailure { err ->
                _uiState.update { it.copy(isBusy = false) }
                showStatus("Oto İyileştirme hatası: ${err.message}", 2600L)
            }
        }
    }

    fun updateProxyCinemaLook(preset: String, intensity: Float, vignette: Float, grain: Float) {
        enqueueProxyJob(ProxyJob.CinemaLook(preset, intensity, vignette, grain))
    }

    fun applyCinemaLook(preset: String, intensity: Float, vignette: Float, grain: Float) {
        invalidateProxyQueue()
        dismissCompareOriginal()
        _uiState.update { it.copy(isBusy = true) }
        paintExecutor.execute {
            runCatching {
                val info = engine.applyCinemaLook(preset, intensity, vignette, grain)
                val layers = engine.getLayers()
                _uiState.update { it.copy(docInfo = info, layers = layers, isBusy = false) }
                NativeSurfaceBridge.nativeRenderFrame()
                refreshLayerThumbnails()
                showStatus("Sinema stili uygulandı", 1800L)
            }.onFailure { err ->
                _uiState.update { it.copy(isBusy = false) }
                showStatus("Sinema stili hatası: ${err.message}", 2600L)
            }
        }
    }

    fun updateProxyMotionBlur(angle: Float, distance: Float) {
        enqueueProxyJob(ProxyJob.MotionBlur(angle, distance))
    }

    fun applyMotionBlur(angle: Float, distance: Float) {
        invalidateProxyQueue()
        dismissCompareOriginal()
        _uiState.update { it.copy(isBusy = true) }
        paintExecutor.execute {
            runCatching {
                val info = engine.applyMotionBlur(angle, distance)
                val layers = engine.getLayers()
                _uiState.update { it.copy(docInfo = info, layers = layers, isBusy = false) }
                NativeSurfaceBridge.nativeRenderFrame()
                refreshLayerThumbnails()
                showStatus("Hareket bulanıklığı uygulandı", 1800L)
            }.onFailure { err ->
                _uiState.update { it.copy(isBusy = false) }
                showStatus("Hareket bulanıklığı hatası: ${err.message}", 2600L)
            }
        }
    }

    fun updateProxyRadialBlur(amount: Float, method: String) {
        enqueueProxyJob(ProxyJob.RadialBlur(amount, method))
    }

    fun applyRadialBlur(amount: Float, method: String) {
        invalidateProxyQueue()
        dismissCompareOriginal()
        _uiState.update { it.copy(isBusy = true) }
        paintExecutor.execute {
            runCatching {
                val info = engine.applyRadialBlur(amount, method)
                val layers = engine.getLayers()
                _uiState.update { it.copy(docInfo = info, layers = layers, isBusy = false) }
                NativeSurfaceBridge.nativeRenderFrame()
                refreshLayerThumbnails()
                showStatus("Radyal bulanıklık uygulandı", 1800L)
            }.onFailure { err ->
                _uiState.update { it.copy(isBusy = false) }
                showStatus("Radyal bulanıklık hatası: ${err.message}", 2600L)
            }
        }
    }


    fun smartRemoveBackground() {
        dismissCompareOriginal()
        invalidateProxyQueue()
        _uiState.update { it.copy(isBusy = true) }
        paintExecutor.execute {
            runCatching {
                val info = engine.smartRemoveBackground()
                val layers = engine.getLayers()
                _uiState.update { it.copy(docInfo = info, layers = layers, selectionBounds = null, isBusy = false) }
                NativeSurfaceBridge.nativeRenderFrame()
                refreshLayerThumbnails()
                showStatus("Arka plan tek dokunuşla silindi", 2200L)
            }.onFailure { err ->
                _uiState.update { it.copy(isBusy = false) }
                showStatus(err.message ?: "Arka plan silinemedi", 3000L)
            }
        }
    }

    fun smartPortraitBlur(radius: Float = 18f) {
        dismissCompareOriginal()
        invalidateProxyQueue()
        _uiState.update { it.copy(isBusy = true) }
        paintExecutor.execute {
            runCatching {
                val info = engine.smartPortraitBlur(radius)
                val layers = engine.getLayers()
                _uiState.update { it.copy(docInfo = info, layers = layers, selectionBounds = null, isBusy = false) }
                NativeSurfaceBridge.nativeRenderFrame()
                refreshLayerThumbnails()
                showStatus("Portre arka plan bulanıklığı uygulandı", 2200L)
            }.onFailure { err ->
                _uiState.update { it.copy(isBusy = false) }
                showStatus(err.message ?: "Portre bulanıklığı uygulanamadı", 3000L)
            }
        }
    }

    fun updateProxyHslMixer(h: List<Float>, s: List<Float>, l: List<Float>) {
        enqueueProxyJob(ProxyJob.HslMixer(h, s, l))
    }

    fun applyHslMixer(h: List<Float>, s: List<Float>, l: List<Float>) {
        invalidateProxyQueue()
        dismissCompareOriginal()
        _uiState.update { it.copy(isBusy = true) }
        paintExecutor.execute {
            runCatching {
                val info = engine.applyHslMixer(h, s, l)
                val layers = engine.getLayers()
                _uiState.update { it.copy(docInfo = info, layers = layers, isBusy = false) }
                NativeSurfaceBridge.nativeRenderFrame()
                refreshLayerThumbnails()
                showStatus("HSL Renk Mikseri uygulandı", 1800L)
            }.onFailure { err ->
                _uiState.update { it.copy(isBusy = false) }
                showStatus("HSL hatası: ${err.message}", 2600L)
            }
        }
    }

    fun applyPaintBucket(docX: Float, docY: Float, tolerance: Int, contiguous: Boolean) {
        dismissCompareOriginal()
        val hex = _uiState.value.colorHex
        _uiState.update { it.copy(isBusy = true) }
        paintExecutor.execute {
            runCatching {
                val info = engine.applyPaintBucket(docX.toInt(), docY.toInt(), hex, tolerance.coerceIn(0, 255).toUInt(), contiguous)
                val layers = engine.getLayers()
                _uiState.update { it.copy(docInfo = info, layers = layers, isBusy = false) }
                NativeSurfaceBridge.nativeRenderFrame()
                refreshLayerThumbnails()
                showStatus("Boya kovası uygulandı", 1400L)
            }.onFailure { err ->
                _uiState.update { it.copy(isBusy = false) }
                showStatus("Boya kovası hatası: ${err.message}", 2400L)
            }
        }
    }

    fun resetProxyPreview() {
        dismissCompareOriginal()
        val gen = invalidateProxyQueue()
        paintExecutor.execute {
            if (gen != proxyGeneration.get()) return@execute
            runCatching {
                engine.resetProxyPreview()
                if (gen == proxyGeneration.get()) NativeSurfaceBridge.nativeRenderFrame()
            }
        }
    }

    fun endProxyPreview() {
        invalidateProxyQueue()
        histogramRefreshPending.set(false)
        paintExecutor.execute {
            runCatching {
                engine.endProxyPreview()
                NativeSurfaceBridge.nativeRenderFrame()
            }
        }
    }

    // ==================== v2.3.0 PARÇA 2: LIVE HISTOGRAM, TAT SAMPLER & AUTO LEVELS ====================
    private val histogramRefreshPending = java.util.concurrent.atomic.AtomicBoolean(false)
    @Volatile private var lastHistogramMs: Long = 0L

    fun refreshHistogram(force: Boolean = false) {
        val now = android.os.SystemClock.uptimeMillis()
        if (!force && now - lastHistogramMs < 110L) return
        if (!histogramRefreshPending.compareAndSet(false, true)) return
        lastHistogramMs = now
        paintExecutor.execute {
            try {
                val h = runCatching { engine.getActiveHistogram() }.getOrNull()
                if (h != null) {
                    _uiState.update { it.copy(histogram = h) }
                }
            } finally {
                histogramRefreshPending.set(false)
            }
        }
    }

    fun clearHistogram() {
        histogramRefreshPending.set(false)
        _uiState.update { it.copy(histogram = null) }
    }

    fun sampleColorAtNorm(nx: Float, ny: Float, onResult: (SampledColorInfo) -> Unit) {
        paintExecutor.execute {
            val info = runCatching { engine.sampleColorAtNorm(nx, ny) }.getOrNull()
            if (info != null) {
                viewModelScope.launch(Dispatchers.Main) { onResult(info) }
            }
        }
    }

    fun computeAutoLevels(onResult: (AutoLevelsResult) -> Unit) {
        paintExecutor.execute {
            val res = runCatching { engine.computeAutoLevels() }.getOrNull()
            if (res != null) {
                viewModelScope.launch(Dispatchers.Main) { onResult(res) }
            }
        }
    }

    // --- Independent Active Layer Move / Scale / Rotate ---
    fun loadActiveLayerPreview() {
        paintExecutor.execute {
            runCatching {
                val bounds = engine.getActiveLayerBounds()
                val active = engine.getLayers().firstOrNull { it.isActive }
                var bmpImg: ImageBitmap? = null
                if (active != null) {
                    val thumb = runCatching { engine.getLayerBoundsPreview(active.id, 768u) }.getOrNull()
                    if (thumb != null) {
                        val w = thumb.width.toInt()
                        val h = thumb.height.toInt()
                        if (w > 0 && h > 0 && thumb.rgba.size >= w * h * 4) {
                            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            bmp.copyPixelsFromBuffer(ByteBuffer.wrap(thumb.rgba))
                            bmpImg = bmp.asImageBitmap()
                        }
                    }
                    // Hide stationary original layer while dragging its crisp floating copy (zero ghost duplicate!)
                    engine.setTransformHiddenLayer(active.id)
                    NativeSurfaceBridge.nativeRenderFrame()
                }
                if (bounds != null) {
                    _activeLayerPreview.value = bounds to bmpImg
                } else {
                    _activeLayerPreview.value = null
                }
            }
        }
    }

    fun clearActiveLayerPreview() {
        _activeLayerPreview.value = null
        paintExecutor.execute {
            runCatching {
                engine.setTransformHiddenLayer(null)
                NativeSurfaceBridge.nativeRenderFrame()
            }
        }
    }

    fun transformActiveLayer(dx: Float, dy: Float, scalePct: Float, rotateDeg: Float) {
        transformActiveLayerXY(dx, dy, scalePct, scalePct, rotateDeg)
    }

    fun transformActiveLayerXY(dx: Float, dy: Float, scaleXPct: Float, scaleYPct: Float, rotateDeg: Float) {
        _uiState.update { it.copy(isBusy = true) }
        paintExecutor.execute {
            runCatching {
                val info = engine.transformActiveLayerXy(dx, dy, scaleXPct, scaleYPct, rotateDeg)
                val layers = engine.getLayers()
                _activeLayerPreview.value = null
                _uiState.update { it.copy(docInfo = info, layers = layers, isBusy = false) }
                NativeSurfaceBridge.nativeRenderFrame()
                showStatus("Katman taşındı / boyutlandırıldı", 1800L)
            }.onFailure { err ->
                _activeLayerPreview.value = null
                _uiState.update { it.copy(isBusy = false) }
                showStatus("Katman dönüştürme hatası: ${err.message}", 3800L)
            }
        }
    }

    fun toggleRuler() {
        val next = !_uiState.value.rulerEnabled
        _uiState.update { it.copy(rulerEnabled = next) }
        showStatus(
            if (next) "Cetvel açık — düz çizgi çek (0°/45°/90° otomatik hizalanır)"
            else "Cetvel kapatıldı — serbest çizim",
            2200L
        )
    }

    fun cycleSymmetry() {
        val order = listOf("off", "vertical", "horizontal", "dual")
        val curIdx = order.indexOf(_uiState.value.symmetryMode).coerceAtLeast(0)
        val nextMode = order[(curIdx + 1) % order.size]
        setSymmetry(nextMode)
    }

    fun setSymmetry(mode: String) {
        _uiState.update { it.copy(symmetryMode = mode) }
        paintExecutor.execute {
            runCatching {
                engine.setSymmetry(mode)
                val label = when (mode) {
                    "vertical" -> "Ayna: Dikey simetri açık"
                    "horizontal" -> "Ayna: Yatay simetri açık"
                    "dual" -> "Ayna: Dörtlü (Mandala) simetri açık"
                    else -> "Ayna simetrisi kapatıldı"
                }
                showStatus(label, 2000L)
            }.onFailure { err ->
                showStatus("Simetri hatası: ${err.message}", 3000L)
            }
        }
    }

    fun sampleColorAt(docX: Float, docY: Float) {
        pendingSamplePt.set(docX to docY)
        if (sampleDrainScheduled.compareAndSet(false, true)) {
            paintExecutor.execute { drainPendingColorSamples() }
        }
    }

    private fun drainPendingColorSamples() {
        while (true) {
            val pt = pendingSamplePt.getAndSet(null)
            if (pt == null) {
                sampleDrainScheduled.set(false)
                if (pendingSamplePt.get() == null) return
                if (!sampleDrainScheduled.compareAndSet(false, true)) return
                continue
            }
            runCatching {
                val hex = engine.sampleColorHex(pt.first, pt.second)
                _uiState.update { it.copy(colorHex = hex) }
                showStatus("Renk seçildi: $hex", 1400L)
            }
        }
    }

    fun updateBrush(
        size: Float = _uiState.value.brushSize,
        hardness: Float = _uiState.value.brushHardness,
        opacity: Float = _uiState.value.brushOpacity,
        flow: Float = _uiState.value.brushFlow,
        colorHex: String = _uiState.value.colorHex
    ) {
        _uiState.update {
            it.copy(
                brushSize = size.coerceIn(1f, 500f),
                brushHardness = hardness.coerceIn(0f, 1f),
                brushOpacity = opacity.coerceIn(0.01f, 1f),
                brushFlow = flow.coerceIn(0.01f, 1f),
                colorHex = colorHex
            )
        }
        syncBrushToEngine()
    }

    private fun syncBrushToEngine() {
        val s = _uiState.value
        paintExecutor.execute {
            runCatching {
                engine.setBrush(s.brushSize, s.brushHardness, s.brushOpacity, s.brushFlow, s.colorHex)
            }
        }
    }

    private fun isLiveDabTool(tool: ActiveTool): Boolean =
        tool == ActiveTool.SMUDGE ||
            tool == ActiveTool.DODGE ||
            tool == ActiveTool.BURN ||
            tool == ActiveTool.SPONGE_SAT ||
            tool == ActiveTool.SPONGE_DESAT ||
            tool == ActiveTool.BLUR_BRUSH ||
            tool == ActiveTool.SHARPEN_BRUSH ||
            tool == ActiveTool.COLOR_REPLACE ||
            tool == ActiveTool.BG_ERASER

    private fun dabToolKey(tool: ActiveTool): String = when (tool) {
        ActiveTool.SMUDGE -> "smudge"
        ActiveTool.DODGE -> "dodge"
        ActiveTool.BURN -> "burn"
        ActiveTool.SPONGE_SAT -> "sponge_saturate"
        ActiveTool.SPONGE_DESAT -> "sponge_desaturate"
        ActiveTool.BLUR_BRUSH -> "blur_brush"
        ActiveTool.SHARPEN_BRUSH -> "sharpen_brush"
        else -> ""
    }

    fun beginLiveStroke(sample: StrokeSample) {
        val s = _uiState.value
        val isBrushOrEraser = s.activeTool == ActiveTool.BRUSH || s.activeTool == ActiveTool.PENCIL || s.activeTool == ActiveTool.ERASER
        val isMixer = s.activeTool == ActiveTool.MIXER_BRUSH
        val isColorReplace = s.activeTool == ActiveTool.COLOR_REPLACE
        val isDab = isLiveDabTool(s.activeTool)
        val isClone = s.activeTool == ActiveTool.CLONE_STAMP && !s.pickingCloneSource
        if (!isBrushOrEraser && !isMixer && !isDab && !isClone) return

        if (isBrushOrEraser) {
            val activeLayer = s.layers.firstOrNull { it.isActive }
            if (activeLayer?.locked == true) {
                showStatus("Orijinal fotoğraf kilitli — düzenlemek için Katman panelinden kilidi açabilirsin", 3200L)
                return
            }
        }

        synchronized(pendingLock) { pendingSamples.clear() }
        lastDispatchedSample = sample
        paintExecutor.execute {
            runCatching {
                when {
                    isClone -> {
                        val src = s.cloneSourceDoc ?: (sample.x to sample.y)
                        engine.beginLiveClone(sample, src.first, src.second, s.brushSize, s.brushHardness, s.brushOpacity)
                    }
                    isMixer -> {
                        engine.beginLiveMixer(
                            sample, s.brushSize, s.brushHardness,
                            s.mixerWet, s.mixerLoad, s.mixerMix, s.mixerFlow,
                            s.colorHex, s.mixerSampleAll
                        )
                    }
                    isColorReplace -> {
                        engine.beginLiveColorReplacement(
                            sample, s.brushSize, s.brushHardness,
                            s.colorHex, s.colorReplaceMode, s.colorReplaceTolerance
                        )
                    }
                    s.activeTool == ActiveTool.BG_ERASER -> {
                        engine.beginLiveBackgroundEraser(
                            sample, s.brushSize, s.brushHardness, s.bgEraserTolerance, s.bgEraserLimits, s.bgEraserProtectFg
                        )
                    }
                    isDab -> {
                        engine.beginLiveDab(dabToolKey(s.activeTool), sample, s.brushSize, s.brushHardness, s.retouchStrength)
                    }
                    s.activeTool == ActiveTool.PENCIL -> {
                        engine.beginLivePencil(sample, s.colorHex)
                    }
                    else -> {
                        engine.beginLiveStroke(sample, s.colorHex, s.activeTool == ActiveTool.ERASER)
                    }
                }
                NativeSurfaceBridge.nativeRenderFrame()
            }.onFailure { err ->
                showStatus("Araç hatası: ${err.message}")
            }
        }
    }

    fun pushLiveStroke(samples: List<StrokeSample>) {
        val tool = _uiState.value.activeTool
        val ok = tool == ActiveTool.BRUSH || tool == ActiveTool.PENCIL || tool == ActiveTool.ERASER || tool == ActiveTool.MIXER_BRUSH ||
            isLiveDabTool(tool) || (tool == ActiveTool.CLONE_STAMP && !_uiState.value.pickingCloneSource)
        if (!ok || samples.isEmpty()) return
        synchronized(pendingLock) {
            pendingSamples.addAll(samples)
        }
        if (drainScheduled.compareAndSet(false, true)) {
            paintExecutor.execute { drainPendingSamples() }
        }
    }

    private fun drainPendingSamples() {
        while (true) {
            val batch: List<StrokeSample>
            synchronized(pendingLock) {
                if (pendingSamples.isEmpty()) {
                    drainScheduled.set(false)
                    return
                }
                batch = ArrayList(pendingSamples)
                pendingSamples.clear()
            }
            val st = _uiState.value
            val curTool = st.activeTool
            val isDab = isLiveDabTool(curTool)
            val isClone = curTool == ActiveTool.CLONE_STAMP
            val isMixer = curTool == ActiveTool.MIXER_BRUSH
            val isHeavy = isMixer || isDab || isClone
            val minStep = when {
                isHeavy -> (st.brushSize * 0.22f).coerceIn(4f, 52f)
                st.brushSize >= 16f -> (st.brushSize * 0.14f).coerceIn(2.0f, 26f)
                else -> 1.5f
            }
            val minStepSq = minStep * minStep
            val filtered = ArrayList<StrokeSample>(batch.size)
            var prev = lastDispatchedSample
            for (pt in batch) {
                if (prev == null) {
                    filtered.add(pt)
                    prev = pt
                } else {
                    val dx = pt.x - prev.x
                    val dy = pt.y - prev.y
                    if (dx * dx + dy * dy >= minStepSq) {
                        filtered.add(pt)
                        prev = pt
                    }
                }
            }
            // Always preserve exact finger tip endpoint if batch moved
            val batchLast = batch.lastOrNull()
            if (batchLast != null && (filtered.isEmpty() || filtered.last() !== batchLast)) {
                val p = prev
                if (p == null || ((batchLast.x - p.x) * (batchLast.x - p.x) + (batchLast.y - p.y) * (batchLast.y - p.y) >= 1f)) {
                    filtered.add(batchLast)
                }
            }
            if (filtered.isNotEmpty()) {
                lastDispatchedSample = filtered.last()
            }
            if (filtered.isEmpty()) continue
            runCatching {
                when {
                    isClone -> engine.pushLiveClone(filtered)
                    isMixer -> engine.pushLiveMixer(filtered)
                    isDab -> engine.pushLiveDab(filtered)
                    else -> engine.pushLiveStroke(filtered)
                }
                val now = android.os.SystemClock.uptimeMillis()
                val queueEmpty = synchronized(pendingLock) { pendingSamples.isEmpty() }
                if (queueEmpty || now - lastLiveRenderMs >= 16L) {
                    lastLiveRenderMs = now
                    NativeSurfaceBridge.nativeRenderFrame()
                }
            }
        }
    }

    fun finishLiveStroke() {
        val tool = _uiState.value.activeTool
        val isDab = isLiveDabTool(tool)
        val isClone = tool == ActiveTool.CLONE_STAMP && !_uiState.value.pickingCloneSource
        val isMixer = tool == ActiveTool.MIXER_BRUSH
        if (tool != ActiveTool.BRUSH && tool != ActiveTool.PENCIL && tool != ActiveTool.ERASER && !isDab && !isClone && !isMixer) return
        paintExecutor.execute {
            drainPendingSamples()
            runCatching {
                val info = when {
                    isClone -> engine.finishLiveClone()
                    isMixer -> engine.finishLiveMixer()
                    isDab -> engine.finishLiveDab()
                    else -> engine.finishLiveStroke()
                }
                _uiState.update { it.copy(docInfo = info) }
                NativeSurfaceBridge.nativeRenderFrame()
            }.onFailure { err ->
                showStatus("Kayıt hatası: ${err.message}")
            }
        }
    }

    fun commitRulerStroke(samples: List<StrokeSample>) {
        val s = _uiState.value
        if ((s.activeTool != ActiveTool.BRUSH && s.activeTool != ActiveTool.ERASER) || samples.isEmpty()) return
        val activeLayer = s.layers.firstOrNull { it.isActive }
        if (activeLayer?.locked == true) {
            showStatus("Orijinal fotoğraf kilitli — düzenlemek için Katman panelinden kilidi açabilirsin", 3200L)
            return
        }
        val isEraser = s.activeTool == ActiveTool.ERASER
        paintExecutor.execute {
            runCatching {
                engine.beginLiveStroke(samples.first(), s.colorHex, isEraser)
                if (samples.size > 1) {
                    engine.pushLiveStroke(samples.drop(1))
                }
                val info = engine.finishLiveStroke()
                _uiState.update { it.copy(docInfo = info) }
                NativeSurfaceBridge.nativeRenderFrame()
            }.onFailure { err ->
                showStatus("Cetvel çizim hatası: ${err.message}")
            }
        }
    }

    fun cancelLiveStroke() {
        synchronized(pendingLock) { pendingSamples.clear() }
        paintExecutor.execute {
            runCatching {
                engine.cancelLiveStroke()
                engine.cancelLiveDab()
                engine.cancelLiveClone()
                NativeSurfaceBridge.nativeRenderFrame()
            }
        }
    }

    // One-shot CPU bake after 60 FPS GPU Mirror preview
    fun updateRetouchStrength(strength: Float) {
        _uiState.update { it.copy(retouchStrength = strength.coerceIn(5f, 100f)) }
    }

    fun addTextLayer(text: String, x: Float, y: Float, sizePx: Float, colorHex: String, align: String) {
        if (text.trim().isEmpty()) {
            showStatus("Lütfen eklenecek metni yaz", 2000L)
            return
        }
        _uiState.update { it.copy(isBusy = true) }
        paintExecutor.execute {
            runCatching {
                val info = engine.addTextLayer(text, x, y, sizePx, colorHex, align)
                val layers = engine.getLayers()
                _uiState.update { it.copy(docInfo = info, layers = layers, isBusy = false) }
                NativeSurfaceBridge.nativeRenderFrame()
                showStatus("Metin katmanı eklendi", 2000L)
            }.onFailure { err ->
                _uiState.update { it.copy(isBusy = false) }
                showStatus("Metin hatası: ${err.message}", 4000L)
            }
        }
    }

    fun applyRetouchStroke(samples: List<StrokeSample>) {
        val s = _uiState.value
        val toolKey = when (s.activeTool) {
            ActiveTool.SPOT_HEAL -> "spot_heal"
            ActiveTool.SMUDGE -> "smudge"
            ActiveTool.DODGE -> "dodge"
            ActiveTool.BURN -> "burn"
            else -> return
        }
        if (samples.isEmpty()) return
        if (toolKey == "spot_heal" && !spotHealInFlight.compareAndSet(false, true)) {
            return
        }
        paintExecutor.execute {
            try {
                runCatching {
                    val info = if (toolKey == "spot_heal") {
                        engine.applySpotHealingPro(samples, s.brushSize, s.brushHardness, s.spotHealType, s.spotHealSampleAll)
                    } else {
                        engine.applyRetouchPro(
                            toolKey, samples, s.brushSize, s.brushHardness, s.retouchStrength,
                            s.retouchRange, s.retouchProtectTones, s.smudgeFingerPainting,
                            s.sharpenProtectDetail, s.retouchSampleAll
                        )
                    }
                    val layers = engine.getLayers()
                    _uiState.update { it.copy(docInfo = info, layers = layers) }
                    NativeSurfaceBridge.nativeRenderFrame()
                }.onFailure { err ->
                    showStatus("Rötuş hatası: ${err.message}", 3500L)
                }
            } finally {
                if (toolKey == "spot_heal") {
                    spotHealInFlight.set(false)
                }
            }
        }
    }

    /** Quick-saves the current document as a full multi-layer `.pcraft` project + JPG preview in Home gallery. */
    fun quickSaveToGallery(context: Context, onSaved: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isBusy = true) }
            runCatching {
                val pcraftBytes = runCatching { engine.exportDocumentBytes("pcraft") }.getOrNull()
                val previewBytes = runCatching { engine.exportDocumentBytes("jpg") }.getOrNull()
                val usePcraft = pcraftBytes != null && pcraftBytes.isNotEmpty()
                val bytes = if (usePcraft) pcraftBytes!! else engine.exportDocumentBytes("png")
                val ext = if (usePcraft) "pcraft" else "png"
                val doc = _uiState.value.docInfo
                val (savedId, updated) = SavedGalleryStore.recordSavedPhoto(
                    context = context,
                    displayName = currentSavedDisplayName ?: doc?.name ?: "StonebridgeCraft",
                    formatExt = ext,
                    exportedBytes = bytes,
                    previewPngBytes = if (usePcraft) previewBytes else null,
                    width = doc?.width?.toInt() ?: 0,
                    height = doc?.height?.toInt() ?: 0,
                    existingId = currentSavedPhotoId
                )
                currentSavedPhotoId = savedId
                _uiState.update { it.copy(isBusy = false, savedPhotos = updated) }
            }.onFailure { err ->
                _uiState.update { it.copy(isBusy = false) }
                showStatus("Kaydetme hatası: ${err.message}", 3500L)
            }
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                onSaved()
            }
        }
    }

    fun applyPhotoMirror(mode: String, axisRatio: Float) {
        _uiState.update { it.copy(isBusy = true) }
        paintExecutor.execute {
            runCatching {
                val info = engine.applyPhotoMirror(mode, axisRatio)
                val layers = engine.getLayers()
                NativeSurfaceBridge.nativeSetPreview(0, 0.5f, false, false)
                _uiState.update { it.copy(docInfo = info, layers = layers, isBusy = false) }
                NativeSurfaceBridge.nativeRenderFrame()
                showStatus("Ayna efekti uygulandı", 2000L)
            }.onFailure { err ->
                NativeSurfaceBridge.nativeSetPreview(0, 0.5f, false, false)
                _uiState.update { it.copy(isBusy = false) }
                showStatus("Ayna hatası: ${err.message}", 3500L)
            }
        }
    }

    // One-shot CPU bake after 60 FPS GPU Rotate/Flip preview
    fun applyTransformBatch(quarterTurnsCw: Int, flipH: Boolean, flipV: Boolean) {
        val turns = ((quarterTurnsCw % 4) + 4) % 4
        if (turns == 0 && !flipH && !flipV) {
            NativeSurfaceBridge.nativeSetPreview(0, 0.5f, false, false)
            _uiState.update { it.copy(fitCameraSignal = it.fitCameraSignal + 1) }
            return
        }
        _uiState.update { it.copy(isBusy = true) }
        paintExecutor.execute {
            runCatching {
                val info = engine.applyCanvasTransformBatch(turns, flipH, flipV)
                val layers = engine.getLayers()
                NativeSurfaceBridge.nativeSetPreview(0, 0.5f, false, false)
                _uiState.update {
                    it.copy(
                        docInfo = info,
                        layers = layers,
                        isBusy = false,
                        fitCameraSignal = it.fitCameraSignal + 1
                    )
                }
                showStatus("Dönüştürme uygulandı", 1800L)
            }.onFailure { err ->
                NativeSurfaceBridge.nativeSetPreview(0, 0.5f, false, false)
                _uiState.update { it.copy(isBusy = false) }
                showStatus("Döndürme hatası: ${err.message}", 3500L)
            }
        }
    }

    fun cropCanvas(x: Int, y: Int, width: Int, height: Int) {
        _uiState.update { it.copy(isBusy = true) }
        paintExecutor.execute {
            runCatching {
                val info = engine.cropCanvas(x, y, width, height)
                val layers = engine.getLayers()
                _uiState.update {
                    it.copy(
                        docInfo = info,
                        layers = layers,
                        isBusy = false,
                        fitCameraSignal = it.fitCameraSignal + 1
                    )
                }
                showStatus("Kırpıldı (${info.width}×${info.height})", 2200L)
            }.onFailure { err ->
                _uiState.update { it.copy(isBusy = false) }
                showStatus("Kırpma hatası: ${err.message}", 3500L)
            }
        }
    }

    // One-shot native adjustment bake after 60 FPS GPU preview
    fun applyAdjustmentOnce(kind: String, paramsJson: String, labelTr: String) {
        _uiState.update { it.copy(isBusy = true) }
        paintExecutor.execute {
            runCatching {
                val info = engine.applyAdjustmentOnce(kind, paramsJson)
                val layers = engine.getLayers()
                NativeSurfaceBridge.nativeSetAdjustPreview(0f, 0f, 0f, 0f, 0f, 0f)
                NativeSurfaceBridge.nativeSetTonePreview(0f, 1f, 1f, 0f, 0f, 0f)
                NativeSurfaceBridge.nativeSetCurvePreview(0.0f, 0.25f, 0.50f, 0.75f, 1.0f, false)
                _uiState.update { it.copy(docInfo = info, layers = layers, isBusy = false) }
                NativeSurfaceBridge.nativeRenderFrame()
                showStatus("$labelTr uygulandı", 2000L)
            }.onFailure { err ->
                NativeSurfaceBridge.nativeSetAdjustPreview(0f, 0f, 0f, 0f, 0f, 0f)
                NativeSurfaceBridge.nativeSetTonePreview(0f, 1f, 1f, 0f, 0f, 0f)
                NativeSurfaceBridge.nativeSetCurvePreview(0.0f, 0.25f, 0.50f, 0.75f, 1.0f, false)
                _uiState.update { it.copy(isBusy = false) }
                showStatus("$labelTr hatası: ${err.message}", 3500L)
            }
        }
    }

    // One-shot low-memory Blur bake after 60 FPS GPU preview
    fun applyBlurFast(radius: Float) {
        if (radius < 0.5f) {
            NativeSurfaceBridge.nativeSetAdjustPreview(0f, 0f, 0f, 0f, 0f, 0f)
            NativeSurfaceBridge.nativeRenderFrame()
            return
        }
        _uiState.update { it.copy(isBusy = true) }
        paintExecutor.execute {
            runCatching {
                val info = engine.applyBlurFast(radius)
                val layers = engine.getLayers()
                NativeSurfaceBridge.nativeSetAdjustPreview(0f, 0f, 0f, 0f, 0f, 0f)
                _uiState.update { it.copy(docInfo = info, layers = layers, isBusy = false) }
                NativeSurfaceBridge.nativeRenderFrame()
                showStatus("Bulanıklaştırma uygulandı", 2000L)
            }.onFailure { err ->
                NativeSurfaceBridge.nativeSetAdjustPreview(0f, 0f, 0f, 0f, 0f, 0f)
                _uiState.update { it.copy(isBusy = false) }
                showStatus("Bulanıklaştırma hatası: ${err.message}", 3500L)
            }
        }
    }

    // One-shot low-memory Sharpen bake after 60 FPS GPU preview
    fun applySharpenFast(amount: Float) {
        if (amount < 1f) {
            NativeSurfaceBridge.nativeSetAdjustPreview(0f, 0f, 0f, 0f, 0f, 0f)
            NativeSurfaceBridge.nativeRenderFrame()
            return
        }
        _uiState.update { it.copy(isBusy = true) }
        paintExecutor.execute {
            runCatching {
                val info = engine.applySharpenFast(amount)
                val layers = engine.getLayers()
                NativeSurfaceBridge.nativeSetAdjustPreview(0f, 0f, 0f, 0f, 0f, 0f)
                _uiState.update { it.copy(docInfo = info, layers = layers, isBusy = false) }
                NativeSurfaceBridge.nativeRenderFrame()
                showStatus("Keskinleştirme uygulandı", 2000L)
            }.onFailure { err ->
                NativeSurfaceBridge.nativeSetAdjustPreview(0f, 0f, 0f, 0f, 0f, 0f)
                _uiState.update { it.copy(isBusy = false) }
                showStatus("Keskinleştirme hatası: ${err.message}", 3500L)
            }
        }
    }

    // Coalesced, undo-clean real-time live adjustment preview
    fun beginLiveAdjustment(kind: String, initialParamsJson: String) {
        pendingAdjParams.set(null)
        paintExecutor.execute {
            runCatching {
                val id = engine.beginLiveAdjustment(kind, initialParamsJson)
                liveAdjustmentLayerId = id
                NativeSurfaceBridge.nativeRenderFrame()
            }.onFailure { err ->
                showStatus("Önizleme hatası: ${err.message}")
            }
        }
    }

    fun updateLiveAdjustment(paramsJson: String) {
        pendingAdjParams.set(paramsJson)
        if (adjDrainScheduled.compareAndSet(false, true)) {
            paintExecutor.execute { drainPendingAdjustments() }
        }
    }

    private fun drainPendingAdjustments() {
        while (true) {
            val params = pendingAdjParams.getAndSet(null)
            if (params == null) {
                adjDrainScheduled.set(false)
                if (pendingAdjParams.get() == null) return
                if (!adjDrainScheduled.compareAndSet(false, true)) return
                continue
            }
            val id = liveAdjustmentLayerId ?: continue
            runCatching {
                engine.updateLiveAdjustment(id, params)
                NativeSurfaceBridge.nativeRenderFrame()
            }
        }
    }

    fun commitLiveAdjustment(labelTr: String) {
        paintExecutor.execute {
            drainPendingAdjustments()
            liveAdjustmentLayerId = null
            runCatching {
                engine.commitLiveAdjustment()
                val info = engine.getDocumentInfo()
                val layers = engine.getLayers()
                _uiState.update { it.copy(docInfo = info, layers = layers) }
                NativeSurfaceBridge.nativeRenderFrame()
                showStatus("$labelTr uygulandı", 2000L)
            }
        }
    }

    fun cancelLiveAdjustment() {
        pendingAdjParams.set(null)
        paintExecutor.execute {
            liveAdjustmentLayerId = null
            runCatching {
                engine.cancelLiveAdjustment()
                val info = engine.getDocumentInfo()
                val layers = engine.getLayers()
                _uiState.update { it.copy(docInfo = info, layers = layers) }
                NativeSurfaceBridge.nativeRenderFrame()
            }
        }
    }

    // Coalesced real-time live pixel filter preview
    fun beginLiveFilter() {
        pendingFilterUpdate.set(null)
        paintExecutor.execute {
            runCatching {
                engine.beginLiveFilter()
            }.onFailure { err ->
                showStatus("Filtre başlatılamadı: ${err.message}", 3500L)
            }
        }
    }

    fun updateLiveFilter(commandId: String, paramsJson: String) {
        pendingFilterUpdate.set(commandId to paramsJson)
        if (filterDrainScheduled.compareAndSet(false, true)) {
            paintExecutor.execute { drainPendingFilterUpdates() }
        }
    }

    private fun drainPendingFilterUpdates() {
        while (true) {
            val next = pendingFilterUpdate.getAndSet(null)
            if (next == null) {
                filterDrainScheduled.set(false)
                if (pendingFilterUpdate.get() == null) return
                if (!filterDrainScheduled.compareAndSet(false, true)) return
                continue
            }
            runCatching {
                engine.updateLiveFilter(next.first, next.second)
                NativeSurfaceBridge.nativeRenderFrame()
            }.onFailure { err ->
                showStatus("Filtre önizleme hatası: ${err.message}", 3000L)
            }
        }
    }

    fun commitLiveFilter(labelTr: String) {
        paintExecutor.execute {
            drainPendingFilterUpdates()
            runCatching {
                engine.commitLiveFilter()
                val info = engine.getDocumentInfo()
                val layers = engine.getLayers()
                _uiState.update { it.copy(docInfo = info, layers = layers) }
                NativeSurfaceBridge.nativeRenderFrame()
                showStatus("$labelTr uygulandı", 2000L)
            }.onFailure { err ->
                showStatus("$labelTr hatası: ${err.message}", 3500L)
            }
        }
    }

    fun cancelLiveFilter() {
        pendingFilterUpdate.set(null)
        paintExecutor.execute {
            runCatching {
                engine.cancelLiveFilter()
                val info = engine.getDocumentInfo()
                val layers = engine.getLayers()
                _uiState.update { it.copy(docInfo = info, layers = layers) }
                NativeSurfaceBridge.nativeRenderFrame()
            }
        }
    }

    fun toggleBlackAndWhite() {
        val existingId = _uiState.value.bwLayerId
        paintExecutor.execute {
            runCatching {
                if (existingId != null) {
                    runCatching { engine.deleteLayerById(existingId) }
                    val info = engine.getDocumentInfo()
                    val layers = engine.getLayers()
                    _uiState.update { it.copy(docInfo = info, layers = layers, bwLayerId = null) }
                    NativeSurfaceBridge.nativeRenderFrame()
                    showStatus("Siyah-Beyaz kapatıldı", 1800L)
                } else {
                    val newId = engine.beginLiveAdjustment("blackWhite", "{}")
                    val info = engine.getDocumentInfo()
                    val layers = engine.getLayers()
                    _uiState.update { it.copy(docInfo = info, layers = layers, bwLayerId = newId) }
                    NativeSurfaceBridge.nativeRenderFrame()
                    showStatus("Siyah-Beyaz açıldı", 1800L)
                }
            }.onFailure { err ->
                showStatus("Siyah-Beyaz hatası: ${err.message}")
            }
        }
    }

    fun toggleInvert() {
        val existingId = _uiState.value.invertLayerId
        paintExecutor.execute {
            runCatching {
                if (existingId != null) {
                    runCatching { engine.deleteLayerById(existingId) }
                    val info = engine.getDocumentInfo()
                    val layers = engine.getLayers()
                    _uiState.update { it.copy(docInfo = info, layers = layers, invertLayerId = null) }
                    NativeSurfaceBridge.nativeRenderFrame()
                    showStatus("Negatif kapatıldı", 1800L)
                } else {
                    val newId = engine.beginLiveAdjustment("invert", "{}")
                    val info = engine.getDocumentInfo()
                    val layers = engine.getLayers()
                    _uiState.update { it.copy(docInfo = info, layers = layers, invertLayerId = newId) }
                    NativeSurfaceBridge.nativeRenderFrame()
                    showStatus("Negatif açıldı", 1800L)
                }
            }.onFailure { err ->
                showStatus("Negatif hatası: ${err.message}")
            }
        }
    }

    fun undo() {
        dismissCompareOriginal()
        invalidateProxyQueue()
        paintExecutor.execute {
            runCatching {
                val info = engine.undo()
                val layers = engine.getLayers()
                val bounds = runCatching { engine.getSelectionBounds() }.getOrNull()
                val qm = runCatching { engine.isQuickMaskActive() }.getOrDefault(false)
                val ids = layers.map { it.id }.toSet()
                _uiState.update {
                    it.copy(
                        docInfo = info,
                        layers = layers,
                        selectionBounds = bounds,
                        quickMaskMode = qm,
                        bwLayerId = it.bwLayerId?.takeIf { id -> id in ids },
                        invertLayerId = it.invertLayerId?.takeIf { id -> id in ids }
                    )
                }
                NativeSurfaceBridge.nativeRenderFrame()
                refreshLayerThumbnails()
            }
        }
    }

    fun redo() {
        dismissCompareOriginal()
        invalidateProxyQueue()
        paintExecutor.execute {
            runCatching {
                val info = engine.redo()
                val layers = engine.getLayers()
                val bounds = runCatching { engine.getSelectionBounds() }.getOrNull()
                val qm = runCatching { engine.isQuickMaskActive() }.getOrDefault(false)
                val ids = layers.map { it.id }.toSet()
                _uiState.update {
                    it.copy(
                        docInfo = info,
                        layers = layers,
                        selectionBounds = bounds,
                        quickMaskMode = qm,
                        bwLayerId = it.bwLayerId?.takeIf { id -> id in ids },
                        invertLayerId = it.invertLayerId?.takeIf { id -> id in ids }
                    )
                }
                NativeSurfaceBridge.nativeRenderFrame()
                refreshLayerThumbnails()
            }
        }
    }

    fun addLayer() {
        paintExecutor.execute {
            runCatching {
                val nextIndex = _uiState.value.layers.size + 1
                val layers = engine.addLayer("Katman $nextIndex")
                val info = engine.getDocumentInfo()
                _uiState.update { it.copy(docInfo = info, layers = layers) }
                NativeSurfaceBridge.nativeRenderFrame()
                showStatus("Katman $nextIndex eklendi", 2000L)
            }
        }
    }

    fun deleteActiveLayer() {
        paintExecutor.execute {
            runCatching {
                val layers = engine.deleteActiveLayer()
                val info = engine.getDocumentInfo()
                val ids = layers.map { it.id }.toSet()
                _uiState.update {
                    it.copy(
                        docInfo = info,
                        layers = layers,
                        bwLayerId = it.bwLayerId?.takeIf { id -> id in ids },
                        invertLayerId = it.invertLayerId?.takeIf { id -> id in ids }
                    )
                }
                NativeSurfaceBridge.nativeRenderFrame()
            }.onFailure { err ->
                showStatus("Katman silinemedi: ${err.message}")
            }
        }
    }

    fun toggleCompareOriginal() {
        val next = !_uiState.value.compareOriginal
        _uiState.update { it.copy(compareOriginal = next) }
        paintExecutor.execute {
            runCatching {
                engine.setCompareOriginal(next)
                NativeSurfaceBridge.nativeRenderFrame()
                showStatus(if (next) "Önce (Orijinal Fotoğraf)" else "Sonra (Düzenlenmiş)", 1400L)
            }
        }
    }

    fun moveLayerUp() {
        paintExecutor.execute {
            runCatching {
                val layers = engine.moveLayerUp()
                val info = engine.getDocumentInfo()
                _uiState.update { it.copy(docInfo = info, layers = layers) }
                NativeSurfaceBridge.nativeRenderFrame()
                showStatus("Katman yukarı taşındı", 1400L)
            }.onFailure { err -> showStatus("Yukarı taşınamadı: ${err.message}", 2400L) }
        }
    }

    fun moveLayerDown() {
        paintExecutor.execute {
            runCatching {
                val layers = engine.moveLayerDown()
                val info = engine.getDocumentInfo()
                _uiState.update { it.copy(docInfo = info, layers = layers) }
                NativeSurfaceBridge.nativeRenderFrame()
                showStatus("Katman aşağı taşındı", 1400L)
            }.onFailure { err -> showStatus("Aşağı taşınamadı: ${err.message}", 2400L) }
        }
    }

    fun duplicateActiveLayer() {
        paintExecutor.execute {
            runCatching {
                val layers = engine.duplicateActiveLayer()
                val info = engine.getDocumentInfo()
                _uiState.update { it.copy(docInfo = info, layers = layers) }
                NativeSurfaceBridge.nativeRenderFrame()
                showStatus("Katman çoğaltıldı", 1600L)
            }.onFailure { err -> showStatus("Çoğaltılamadı: ${err.message}", 2400L) }
        }
    }

    fun mergeLayerDown() {
        paintExecutor.execute {
            runCatching {
                val layers = engine.mergeLayerDown()
                val info = engine.getDocumentInfo()
                _uiState.update { it.copy(docInfo = info, layers = layers) }
                NativeSurfaceBridge.nativeRenderFrame()
                showStatus("Alt katmanla birleştirildi", 1600L)
            }.onFailure { err -> showStatus("Birleştirilemedi: ${err.message}", 2600L) }
        }
    }

    fun layerViaCopy() {
        _uiState.update { it.copy(isBusy = true) }
        paintExecutor.execute {
            runCatching {
                val info = engine.layerViaCopy()
                val layers = engine.getLayers()
                _uiState.update { it.copy(docInfo = info, layers = layers, selectionBounds = null, isBusy = false) }
                NativeSurfaceBridge.nativeRenderFrame()
                showStatus("Seçim yeni katmana kopyalandı — artık bağımsız taşıyabilirsin", 2600L)
            }.onFailure { err ->
                _uiState.update { it.copy(isBusy = false) }
                showStatus("Katmana kopyalanamadı: ${err.message}", 3000L)
            }
        }
    }

    fun featherSelection(radius: Float = 6f) {
        paintExecutor.execute {
            runCatching {
                val b = engine.featherSelection(radius)
                val infoSel = runCatching { engine.getDocumentInfo() }.getOrNull()
                _uiState.update { it.copy(docInfo = infoSel ?: it.docInfo, selectionBounds = b) }
                NativeSurfaceBridge.nativeRenderFrame()
                showStatus("Seçim kenarı yumuşatıldı (${radius.toInt()}px)", 1600L)
            }.onFailure { err ->
                showStatus("Yumuşatma hatası: ${err.message}", 2600L)
            }
        }
    }

    fun applyLayerEffect(kind: String, colorHex: String, size: Float, opacity: Float, distance: Float) {
        _uiState.update { it.copy(isBusy = true) }
        paintExecutor.execute {
            runCatching {
                val info = engine.applyLayerEffect(kind, colorHex, size, opacity, distance)
                val layers = engine.getLayers()
                _uiState.update { it.copy(docInfo = info, layers = layers, isBusy = false) }
                NativeSurfaceBridge.nativeRenderFrame()
                showStatus("Katman efekti uygulandı", 1800L)
            }.onFailure { err ->
                _uiState.update { it.copy(isBusy = false) }
                showStatus("Efekt hatası: ${err.message}", 3200L)
            }
        }
    }

    fun clearLayerEffects() {
        paintExecutor.execute {
            runCatching {
                val info = engine.clearLayerEffects()
                val layers = engine.getLayers()
                _uiState.update { it.copy(docInfo = info, layers = layers) }
                NativeSurfaceBridge.nativeRenderFrame()
                showStatus("Katman efektleri temizlendi", 1800L)
            }.onFailure { err ->
                showStatus("Efekt temizlenemedi: ${err.message}", 2600L)
            }
        }
    }

    /** Saves a crisp PNG straight into Android's Photos/Gallery (`Pictures/StonebridgeCraft`) with zero permission prompts (API 29+). */
    fun saveToDeviceGallery(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isBusy = true) }
            showStatus("Galeriye kaydediliyor...", 0L)
            runCatching {
                val bytes = engine.exportDocumentBytes("png")
                val fileName = "StonebridgeCraft_${System.currentTimeMillis()}.png"
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/StonebridgeCraft")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: error("Galeri kaydı oluşturulamadı")
                resolver.openOutputStream(uri)?.use { it.write(bytes) }
                    ?: error("Galeri dosyasına yazılamadı")
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                _uiState.update { it.copy(isBusy = false) }
                showStatus("Cihaz galerisine kaydedildi (Pictures/StonebridgeCraft)", 3000L)
            }.onFailure { err ->
                _uiState.update { it.copy(isBusy = false) }
                showStatus("Galeri kayıt hatası: ${err.message}", 4500L)
            }
        }
    }

    fun selectLayer(layerId: ULong) {
        paintExecutor.execute {
            runCatching {
                engine.selectLayer(layerId)
                val info = engine.getDocumentInfo()
                val layers = engine.getLayers()
                _uiState.update { it.copy(docInfo = info, layers = layers) }
            }
        }
    }

    fun toggleLayerVisibility(layerId: ULong, visible: Boolean) {
        paintExecutor.execute {
            runCatching {
                engine.setLayerVisibility(layerId, visible)
                val layers = engine.getLayers()
                _uiState.update { it.copy(layers = layers) }
                NativeSurfaceBridge.nativeRenderFrame()
            }
        }
    }

    fun setLayerOpacity(layerId: ULong, opacity: Float) {
        _uiState.update { st ->
            st.copy(layers = st.layers.map { if (it.id == layerId) it.copy(opacity = opacity) else it })
        }
        pendingOpacityUpdate.set(layerId to opacity)
        if (opacityDrainScheduled.compareAndSet(false, true)) {
            paintExecutor.execute { drainPendingOpacityUpdates() }
        }
    }

    private fun drainPendingOpacityUpdates() {
        while (true) {
            val next = pendingOpacityUpdate.getAndSet(null)
            if (next == null) {
                opacityDrainScheduled.set(false)
                if (pendingOpacityUpdate.get() == null) return
                if (!opacityDrainScheduled.compareAndSet(false, true)) return
                continue
            }
            runCatching {
                engine.setLayerOpacity(next.first, next.second)
                NativeSurfaceBridge.nativeRenderFrame()
            }
        }
    }

    fun setLayerBlendMode(layerId: ULong, blendMode: String) {
        paintExecutor.execute {
            runCatching {
                engine.setLayerBlendMode(layerId, blendMode)
                val layers = engine.getLayers()
                _uiState.update { it.copy(layers = layers) }
                NativeSurfaceBridge.nativeRenderFrame()
            }.onFailure { err ->
                showStatus("Karışım modu hatası: ${err.message}")
            }
        }
    }

    fun toggleLayerLocked(layerId: ULong, locked: Boolean) {
        paintExecutor.execute {
            runCatching {
                engine.setLayerLocked(layerId, locked)
                val layers = engine.getLayers()
                _uiState.update { it.copy(layers = layers) }
                showStatus(
                    if (locked) "Orijinal fotoğraf kilitlendi (koruma açık)"
                    else "Fotoğraf kilidi açıldı — artık düzenleyebilirsin",
                    2500L
                )
            }
        }
    }

    fun newBlankCanvas() {
        currentSavedPhotoId = null
        currentSavedDisplayName = null
        paintExecutor.execute {
            runCatching {
                val info = engine.newDocument("StonebridgeCraft Canvas", 2048u, 2048u)
                val layers = engine.getLayers()
                _uiState.update {
                    it.copy(
                        docInfo = info,
                        layers = layers,
                        bwLayerId = null,
                        invertLayerId = null,
                        symmetryMode = "off",
                        rulerEnabled = false,
                        activeTool = ActiveTool.NONE,
                        fitCameraSignal = it.fitCameraSignal + 1
                    )
                }
            }
        }
    }

    fun openSavedPhoto(item: SavedPhotoItem) {
        currentSavedPhotoId = item.id
        currentSavedDisplayName = item.displayName
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isBusy = true) }
            runCatching {
                val f = File(item.filePath)
                val bytes = f.readBytes()
                val info = engine.openDocumentBytes(f.name, bytes)
                val layers = engine.getLayers()
                _uiState.update {
                    it.copy(
                        docInfo = info,
                        layers = layers,
                        isBusy = false,
                        bwLayerId = null,
                        invertLayerId = null,
                        symmetryMode = "off",
                        rulerEnabled = false,
                        activeTool = ActiveTool.NONE,
                        fitCameraSignal = it.fitCameraSignal + 1
                    )
                }
                showStatus("Açıldı: ${item.displayName}", 2000L)
            }.onFailure { err ->
                _uiState.update { it.copy(isBusy = false) }
                showStatus("Açma hatası: ${err.message}", 4500L)
            }
        }
    }

    private fun decodeHardwareSafeRgba(
        resolver: ContentResolver,
        uri: Uri,
        maxLongEdge: Int = 4096
    ): Triple<Int, Int, Pair<ByteArray, Boolean>>? {
        // 1. Read bounds only (0 pixel RAM allocated, ~2 ms)
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, boundsOpts) }
        val origW = boundsOpts.outWidth
        val origH = boundsOpts.outHeight
        if (origW <= 0 || origH <= 0) return null

        // 2. Read EXIF orientation so portrait camera photos stay upright
        val exifOrientation = runCatching {
            resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        // 3. Safe OOM guard: subsample ONLY if photo exceeds 4096px (e.g. 48MP/64MP/108MP camera shots)
        var sample = 1
        var curMax = maxOf(origW, origH)
        while (curMax / sample > maxLongEdge) {
            sample *= 2
        }
        val wasDownscaled = sample > 1

        // 4. Hardware NEON SIMD decode (~90 ms for 12MP!)
        val decOpts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inPremultiplied = false
        }
        var bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decOpts) }
            ?: return null

        // If still slightly above maxLongEdge (e.g. 4200px), scale cleanly
        val decodedMax = maxOf(bmp.width, bmp.height)
        if (decodedMax > maxLongEdge) {
            val scale = maxLongEdge.toFloat() / decodedMax.toFloat()
            val nw = (bmp.width * scale).toInt().coerceAtLeast(1)
            val nh = (bmp.height * scale).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(bmp, nw, nh, true)
            if (scaled != bmp) {
                bmp.recycle()
                bmp = scaled
            }
        }

        // Apply EXIF rotation/flip if needed
        val matrix = Matrix()
        when (exifOrientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
        }
        if (!matrix.isIdentity) {
            val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
            if (rotated != bmp) {
                bmp.recycle()
                bmp = rotated
            }
        }

        val finalW = bmp.width
        val finalH = bmp.height
        val buf = ByteBuffer.allocate(finalW * finalH * 4)
        bmp.copyPixelsToBuffer(buf)
        bmp.recycle()
        return Triple(finalW, finalH, buf.array() to (wasDownscaled || decodedMax > maxLongEdge))
    }

    fun openDocumentFromUri(resolver: ContentResolver, uri: Uri, asLayer: Boolean = false) {
        if (!asLayer) {
            currentSavedPhotoId = null
            currentSavedDisplayName = null
        }
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isBusy = true) }
            runCatching {
                val fileName = resolveDisplayName(resolver, uri) ?: "image.png"
                val lower = fileName.lowercase()
                val isComplexFormat = lower.endsWith(".psd") || lower.endsWith(".psb") ||
                    lower.endsWith(".pcraft") || lower.endsWith(".svg") ||
                    lower.endsWith(".pdf") || lower.endsWith(".exr") ||
                    lower.endsWith(".tif") || lower.endsWith(".tiff")

                var downscaledNote = false
                val info = if (!asLayer && !isComplexFormat) {
                    val fastDecoded = decodeHardwareSafeRgba(resolver, uri, maxLongEdge = 4096)
                    if (fastDecoded != null) {
                        val (w, h, payload) = fastDecoded
                        downscaledNote = payload.second
                        engine.openRgbaBytes(fileName, w.toUInt(), h.toUInt(), payload.first)
                    } else {
                        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                            ?: error("Dosya okunamadı")
                        engine.openDocumentBytes(fileName, bytes)
                    }
                } else {
                    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("Dosya okunamadı")
                    if (asLayer) engine.placeImageBytes(fileName, bytes)
                    else engine.openDocumentBytes(fileName, bytes)
                }

                val layers = engine.getLayers()
                _uiState.update {
                    it.copy(
                        docInfo = info,
                        layers = layers,
                        isBusy = false,
                        bwLayerId = if (asLayer) it.bwLayerId else null,
                        invertLayerId = if (asLayer) it.invertLayerId else null,
                        symmetryMode = if (asLayer) it.symmetryMode else "off",
                        rulerEnabled = if (asLayer) it.rulerEnabled else false,
                        activeTool = if (asLayer) it.activeTool else ActiveTool.NONE,
                        fitCameraSignal = if (asLayer) it.fitCameraSignal else it.fitCameraSignal + 1
                    )
                }
                if (asLayer) {
                    NativeSurfaceBridge.nativeRenderFrame()
                }
                val suffix = if (downscaledNote) " · bellek koruması" else ""
                showStatus("Açıldı: ${info.name} (${info.width}×${info.height}$suffix)", 2400L)
            }.onFailure { err ->
                _uiState.update { it.copy(isBusy = false) }
                showStatus("Açma hatası: ${err.message}", 5000L)
            }
        }
    }

    fun exportDocumentToUri(context: Context, uri: Uri, formatExt: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isBusy = true) }
            showStatus("Kaydediliyor ($formatExt)...", 0L)
            runCatching {
                val bytes = engine.exportDocumentBytes(formatExt)
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                    ?: error("Hedef dosyaya yazılamadı")
                val doc = _uiState.value.docInfo
                val previewBytes = if (formatExt.equals("png", true) || formatExt.equals("jpg", true) || formatExt.equals("webp", true)) {
                    null
                } else {
                    runCatching { engine.exportDocumentBytes("jpg") }.getOrNull()
                }
                val (savedId, updatedGallery) = SavedGalleryStore.recordSavedPhoto(
                    context = context,
                    displayName = currentSavedDisplayName ?: doc?.name ?: "StonebridgeCraft",
                    formatExt = formatExt,
                    exportedBytes = bytes,
                    previewPngBytes = previewBytes,
                    width = doc?.width?.toInt() ?: 0,
                    height = doc?.height?.toInt() ?: 0,
                    existingId = currentSavedPhotoId
                )
                currentSavedPhotoId = savedId
                _uiState.update { it.copy(isBusy = false, savedPhotos = updatedGallery) }
                showStatus("Kaydedildi (${bytes.size / 1024} KB)")
            }.onFailure { err ->
                _uiState.update { it.copy(isBusy = false) }
                showStatus("Kayıt hatası: ${err.message}", 5000L)
            }
        }
    }

    fun clearStatus() {
        statusAutoHideJob?.cancel()
        _uiState.update { it.copy(statusMessage = null) }
    }

    private fun resolveDisplayName(resolver: ContentResolver, uri: Uri): String? {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return cursor.getString(idx)
            }
        }
        return uri.lastPathSegment
    }

    // ==================== v1.5.0: NON-DESTRUCTIVE LAYER PRO ====================

    fun addLayerMask(mode: String) {
        paintExecutor.execute {
            runCatching {
                val info = engine.addLayerMask(mode)
                val layers = engine.getLayers()
                _uiState.update { it.copy(docInfo = info, layers = layers) }
                NativeSurfaceBridge.nativeRenderFrame()
                refreshLayerThumbnails()
                showStatus("Katman maskesi eklendi", 1600L)
            }.onFailure { err -> showStatus("Maske eklenemedi: ${err.message}", 2600L) }
        }
    }

    fun toggleLayerMaskEnabled(layerId: ULong, enabled: Boolean) {
        paintExecutor.execute {
            runCatching {
                val info = engine.toggleLayerMaskEnabled(layerId, enabled)
                val layers = engine.getLayers()
                _uiState.update { it.copy(docInfo = info, layers = layers) }
                NativeSurfaceBridge.nativeRenderFrame()
                showStatus(if (enabled) "Maske açıldı" else "Maske geçici kapatıldı", 1400L)
            }.onFailure { err -> showStatus("Maske değiştirilemedi: ${err.message}", 2400L) }
        }
    }

    fun invertActiveLayerMask() {
        paintExecutor.execute {
            runCatching {
                val info = engine.invertActiveLayerMask()
                val layers = engine.getLayers()
                _uiState.update { it.copy(docInfo = info, layers = layers) }
                NativeSurfaceBridge.nativeRenderFrame()
                refreshLayerThumbnails()
                showStatus("Maske ters çevrildi", 1500L)
            }.onFailure { err -> showStatus("Ters çevrilemedi: ${err.message}", 2400L) }
        }
    }

    fun deleteActiveLayerMask() {
        paintExecutor.execute {
            runCatching {
                val info = engine.deleteActiveLayerMask()
                val layers = engine.getLayers()
                _uiState.update { it.copy(docInfo = info, layers = layers, maskPaintMode = false) }
                NativeSurfaceBridge.nativeRenderFrame()
                refreshLayerThumbnails()
                showStatus("Katman maskesi silindi", 1600L)
            }.onFailure { err -> showStatus("Maske silinemedi: ${err.message}", 2400L) }
        }
    }

    fun applyActiveLayerMask() {
        paintExecutor.execute {
            runCatching {
                val info = engine.applyActiveLayerMask()
                val layers = engine.getLayers()
                _uiState.update { it.copy(docInfo = info, layers = layers, maskPaintMode = false) }
                NativeSurfaceBridge.nativeRenderFrame()
                refreshLayerThumbnails()
                showStatus("Maske katmana uygulandı", 1600L)
            }.onFailure { err -> showStatus("Maske uygulanamadı: ${err.message}", 2400L) }
        }
    }

    fun enterMaskPaintMode(hideWithBlack: Boolean = true) {
        paintExecutor.execute {
            runCatching {
                val cur = engine.getLayers().firstOrNull { it.isActive }
                if (cur != null && !cur.hasMask) {
                    engine.addLayerMask("revealAll")
                }
                engine.setMaskPaintMode(true)
                val info = engine.getDocumentInfo()
                val layers = engine.getLayers()
                val hex = if (hideWithBlack) "#000000" else "#FFFFFF"
                _uiState.update {
                    it.copy(
                        docInfo = info,
                        layers = layers,
                        maskPaintMode = true,
                        activeTool = ActiveTool.BRUSH,
                        colorHex = hex
                    )
                }
                syncBrushToEngine()
                NativeSurfaceBridge.nativeRenderFrame()
                showStatus(if (hideWithBlack) "Maske Modu: Siyah ile gizle" else "Maske Modu: Beyaz ile göster", 2000L)
            }.onFailure { err -> showStatus("Maske modu açılamadı: ${err.message}", 2600L) }
        }
    }

    fun exitMaskPaintMode() {
        paintExecutor.execute {
            runCatching {
                engine.setMaskPaintMode(false)
                _uiState.update { it.copy(maskPaintMode = false) }
            }
        }
    }

    fun toggleClippingMask() {
        paintExecutor.execute {
            runCatching {
                val info = engine.toggleClippingMask()
                val layers = engine.getLayers()
                val nowClipped = layers.firstOrNull { it.isActive }?.clipped == true
                _uiState.update { it.copy(docInfo = info, layers = layers) }
                NativeSurfaceBridge.nativeRenderFrame()
                showStatus(if (nowClipped) "Kırpma maskesi bağlandı (↳)" else "Kırpma maskesi bırakıldı", 1600L)
            }.onFailure { err -> showStatus("Kırpma maskesi: ${err.message}", 2600L) }
        }
    }

    fun groupActiveLayer() {
        paintExecutor.execute {
            runCatching {
                val grpNum = _uiState.value.layers.count { it.isGroup } + 1
                val info = engine.groupActiveLayer("Grup $grpNum")
                val layers = engine.getLayers()
                _uiState.update { it.copy(docInfo = info, layers = layers) }
                NativeSurfaceBridge.nativeRenderFrame()
                refreshLayerThumbnails()
                showStatus("Katman gruba alındı", 1500L)
            }.onFailure { err -> showStatus("Gruplanamadı: ${err.message}", 2400L) }
        }
    }

    fun ungroupActiveLayer() {
        paintExecutor.execute {
            runCatching {
                val info = engine.ungroupActiveLayer()
                val layers = engine.getLayers()
                _uiState.update { it.copy(docInfo = info, layers = layers) }
                NativeSurfaceBridge.nativeRenderFrame()
                refreshLayerThumbnails()
                showStatus("Grup dağıtıldı", 1500L)
            }.onFailure { err -> showStatus("Grup dağıtılamadı: ${err.message}", 2400L) }
        }
    }

    fun updateProxyBlendIf(thisBlack: Int, thisWhite: Int, underBlack: Int, underWhite: Int, feather: Int) {
        enqueueProxyJob(ProxyJob.BlendIf(thisBlack, thisWhite, underBlack, underWhite, feather))
    }

    fun applyBlendIf(thisBlack: Int, thisWhite: Int, underBlack: Int, underWhite: Int, feather: Int) {
        paintExecutor.execute {
            runCatching {
                val info = engine.applyBlendIf(
                    thisBlack.coerceIn(0, 255).toUByte(),
                    thisWhite.coerceIn(0, 255).toUByte(),
                    underBlack.coerceIn(0, 255).toUByte(),
                    underWhite.coerceIn(0, 255).toUByte(),
                    feather.coerceIn(0, 80).toUByte()
                )
                val layers = engine.getLayers()
                _uiState.update { it.copy(docInfo = info, layers = layers) }
                NativeSurfaceBridge.nativeRenderFrame()
                showStatus("Ton Karışımı (Blend If) uygulandı", 1800L)
            }.onFailure { err -> showStatus("Blend If hatası: ${err.message}", 2600L) }
        }
    }

    fun resetBlendIf() {
        paintExecutor.execute {
            runCatching {
                val info = engine.resetBlendIf()
                val layers = engine.getLayers()
                _uiState.update { it.copy(docInfo = info, layers = layers) }
                NativeSurfaceBridge.nativeRenderFrame()
                showStatus("Ton Karışımı sıfırlandı", 1500L)
            }
        }
    }

    // ==================== v1.6.0: LIGHTROOM RAW & COLOR PRO ====================

    fun updateProxyCameraRawJson(paramsJson: String) {
        enqueueProxyJob(ProxyJob.CameraRawJson(paramsJson))
    }

    fun applyCameraRawJson(paramsJson: String) {
        invalidateProxyQueue()
        _uiState.update { it.copy(isBusy = true) }
        paintExecutor.execute {
            runCatching {
                val info = engine.applyCameraRawJson(paramsJson)
                val layers = engine.getLayers()
                _uiState.update { it.copy(docInfo = info, layers = layers, isBusy = false) }
                NativeSurfaceBridge.nativeRenderFrame()
                refreshLayerThumbnails()
                showStatus("Kamera RAW Pro uygulandı", 1800L)
            }.onFailure { err ->
                _uiState.update { it.copy(isBusy = false) }
                showStatus("Kamera RAW hatası: ${err.message}", 2800L)
            }
        }
    }

    fun importXmpPreset(context: Context, uri: Uri, onLoaded: (uniffi.kastamonushop_ffi.CameraRawPresetValues) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("XMP dosyası okunamadı")
                val parsed = engine.parseXmpPresetBytes(bytes)
                withContext(Dispatchers.Main) {
                    onLoaded(parsed)
                }
                showStatus("Lightroom .xmp önayarı yüklendi", 2000L)
            }.onFailure { err ->
                showStatus("XMP yüklenemedi: ${err.message}", 3000L)
            }
        }
    }

    fun updateProxyLensCorrection(
        distortion: Float,
        vignetteAmount: Float,
        redCyan: Float,
        blueYellow: Float,
        vertical: Float,
        horizontal: Float
    ) {
        // Quantize to integer steps matching UI display so sub-pixel slider jitter never floods Rust
        enqueueProxyJob(
            ProxyJob.LensCorrection(
                kotlin.math.round(distortion),
                kotlin.math.round(vignetteAmount),
                kotlin.math.round(redCyan),
                kotlin.math.round(blueYellow),
                kotlin.math.round(vertical),
                kotlin.math.round(horizontal)
            )
        )
    }

    fun applyLensCorrection(
        distortion: Float,
        vignetteAmount: Float,
        redCyan: Float,
        blueYellow: Float,
        vertical: Float,
        horizontal: Float
    ) {
        invalidateProxyQueue()
        _uiState.update { it.copy(isBusy = true) }
        paintExecutor.execute {
            runCatching {
                val info = engine.applyLensCorrection(distortion, vignetteAmount, redCyan, blueYellow, vertical, horizontal)
                val layers = engine.getLayers()
                _uiState.update { it.copy(docInfo = info, layers = layers, isBusy = false) }
                NativeSurfaceBridge.nativeRenderFrame()
                refreshLayerThumbnails()
                showStatus("Lens düzeltmesi uygulandı", 1800L)
            }.onFailure { err ->
                _uiState.update { it.copy(isBusy = false) }
                showStatus("Lens düzeltme hatası: ${err.message}", 2800L)
            }
        }
    }

    fun importCubeLutFile(context: Context, uri: Uri, onLoaded: (String, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val name = resolveDisplayName(context.contentResolver, uri) ?: "custom.cube"
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("LUT dosyası okunamadı")
                withContext(Dispatchers.Main) {
                    onLoaded(name, text)
                }
                showStatus("3D LUT yüklendi: $name", 2000L)
            }.onFailure { err ->
                showStatus("LUT okunamadı: ${err.message}", 3000L)
            }
        }
    }

    fun updateProxyColorLookup(fileName: String, lutText: String, opacityPct: Float) {
        val op = (kotlin.math.round(opacityPct) / 100f).coerceIn(0f, 1f)
        enqueueProxyJob(ProxyJob.ColorLookup(fileName, lutText, op))
    }

    fun applyColorLookup(fileName: String, lutText: String, opacityPct: Float) {
        invalidateProxyQueue()
        _uiState.update { it.copy(isBusy = true) }
        paintExecutor.execute {
            runCatching {
                val info = engine.applyColorLookup(fileName, lutText, (opacityPct / 100f).coerceIn(0f, 1f))
                val layers = engine.getLayers()
                _uiState.update { it.copy(docInfo = info, layers = layers, isBusy = false) }
                NativeSurfaceBridge.nativeRenderFrame()
                refreshLayerThumbnails()
                showStatus("3D LUT uygulandı: $fileName", 1800L)
            }.onFailure { err ->
                _uiState.update { it.copy(isBusy = false) }
                showStatus("3D LUT hatası: ${err.message}", 3000L)
            }
        }
    }

    // ==================== v1.7.0 PRO BRUSH & RETOUCH STUDIO ====================

    fun updateBrushDynamics(
        spacing: Float = _uiState.value.brushSpacing,
        scatter: Float = _uiState.value.brushScatter,
        sizeJitter: Float = _uiState.value.brushSizeJitter,
        angleJitter: Float = _uiState.value.brushAngleJitter,
        opacityJitter: Float = _uiState.value.brushOpacityJitter
    ) {
        _uiState.update {
            it.copy(
                brushSpacing = spacing,
                brushScatter = scatter,
                brushSizeJitter = sizeJitter,
                brushAngleJitter = angleJitter,
                brushOpacityJitter = opacityJitter
            )
        }
        paintExecutor.execute {
            runCatching {
                engine.setBrushDynamics(spacing, scatter, sizeJitter, angleJitter, opacityJitter)
            }
        }
    }

    fun loadBrushPresets() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { engine.getBrushPresets() }.onSuccess { list ->
                _uiState.update { it.copy(brushPresets = list) }
            }
        }
    }

    fun importAbrFromUri(resolver: ContentResolver, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isBusy = true) }
            runCatching {
                val name = uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { null } ?: "FırçaPaketi.abr"
                val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("ABR dosyası okunamadı")
                val list = engine.importAbrBytes(name, bytes)
                _uiState.update { it.copy(brushPresets = list, isBusy = false) }
                showStatus("${list.size} fırça yüklendi ($name)")
            }.onFailure { err ->
                _uiState.update { it.copy(isBusy = false) }
                showStatus("ABR içe aktarma hatası: ${err.message}")
            }
        }
    }

    fun selectBrushPreset(name: String) {
        paintExecutor.execute {
            runCatching { engine.selectBrushPreset(name) }.onSuccess { summary ->
                _uiState.update {
                    it.copy(
                        activeBrushPresetName = summary.name,
                        brushHardness = summary.hardness.coerceIn(0f, 1f),
                        brushSpacing = summary.spacing.coerceIn(0.05f, 2f)
                    )
                }
                showStatus("Fırça seçildi: ${summary.name}")
            }.onFailure { err ->
                showStatus("Fırça seçilemedi: ${err.message}")
            }
        }
    }

    fun resetBrushToRound() {
        paintExecutor.execute {
            runCatching { engine.resetBrushToRound() }.onSuccess {
                _uiState.update {
                    it.copy(
                        activeBrushPresetName = null,
                        brushSpacing = 0.15f,
                        brushScatter = 0f,
                        brushSizeJitter = 0f,
                        brushAngleJitter = 0f,
                        brushOpacityJitter = 0f
                    )
                }
                showStatus("Yuvarlak fırçaya dönüldü")
            }
        }
    }

    fun updateMixerParams(
        wet: Float = _uiState.value.mixerWet,
        load: Float = _uiState.value.mixerLoad,
        mix: Float = _uiState.value.mixerMix,
        flow: Float = _uiState.value.mixerFlow,
        sampleAll: Boolean = _uiState.value.mixerSampleAll
    ) {
        _uiState.update {
            it.copy(
                mixerWet = wet,
                mixerLoad = load,
                mixerMix = mix,
                mixerFlow = flow,
                mixerSampleAll = sampleAll
            )
        }
    }

    fun updateColorReplaceParams(
        mode: String = _uiState.value.colorReplaceMode,
        tolerance: Float = _uiState.value.colorReplaceTolerance
    ) {
        _uiState.update {
            it.copy(
                colorReplaceMode = mode,
                colorReplaceTolerance = tolerance
            )
        }
    }

    fun updatePatchMode(mode: String) {
        _uiState.update { it.copy(patchMode = mode) }
    }

    fun updateRedEyeParams(
        pupilSize: Float = _uiState.value.redEyePupilSize,
        darken: Float = _uiState.value.redEyeDarken
    ) {
        _uiState.update {
            it.copy(
                redEyePupilSize = pupilSize,
                redEyeDarken = darken
            )
        }
    }

    fun applyPatchTool(dx: Int, dy: Int) {
        val s = _uiState.value
        if (s.selectionBounds == null) {
            showStatus("Önce Seçim aracıyla yamalanacak alanı seç, sonra Yama ile kaynak dokuya sürükle", 3400L)
            return
        }
        if (dx == 0 && dy == 0) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isBusy = true) }
            runCatching {
                val info = engine.applyPatchToolPro(dx, dy, s.patchMode, s.patchContentAware, s.patchStructure.toUInt(), s.patchColor.toUInt(), s.patchSampleAll)
                val layers = engine.getLayers()
                _uiState.update { it.copy(docInfo = info, layers = layers, isBusy = false) }
                NativeSurfaceBridge.nativeRenderFrame()
                showStatus("Yama uygulandı")
            }.onFailure { err ->
                _uiState.update { it.copy(isBusy = false) }
                showStatus("Yama hatası: ${err.message}")
            }
        }
    }

    fun applyRedEye(docX: Float, docY: Float) {
        val s = _uiState.value
        paintExecutor.execute {
            runCatching {
                val info = engine.applyRedEye(docX, docY, s.redEyePupilSize, s.redEyeDarken)
                _uiState.update { it.copy(docInfo = info) }
                NativeSurfaceBridge.nativeRenderFrame()
                showStatus("Kırmızı göz giderildi")
            }.onFailure { err ->
                showStatus("Kırmızı göz: ${err.message}")
            }
        }
    }


    // ==================== v1.7.3 MULTI-FX LAYER STYLE STUDIO ====================

    fun updateProxyLayerStyleStack(effectsJson: String) {
        enqueueProxyJob(ProxyJob.LayerStyleStack(effectsJson))
    }

    fun applyLayerStyleStack(effectsJson: String) {
        invalidateProxyQueue()
        paintExecutor.execute {
            _uiState.update { it.copy(isBusy = true) }
            runCatching {
                val info = engine.applyLayerStyleStack(effectsJson)
                val layers = engine.getLayers()
                _uiState.update { it.copy(docInfo = info, layers = layers, isBusy = false) }
                NativeSurfaceBridge.nativeRenderFrame()
                showStatus("Katman stilleri uygulandı")
            }.onFailure { err ->
                _uiState.update { it.copy(isBusy = false) }
                showStatus("Stil hatası: ${err.message}")
            }
        }
    }

    fun copyActiveLayerStyle() {
        paintExecutor.execute {
            runCatching { engine.copyActiveLayerStyle() }.onSuccess { count ->
                _uiState.update { it.copy(hasCopiedStyle = true) }
                showStatus("$count katman efekti kopyalandı")
            }.onFailure { err ->
                showStatus(err.message ?: "Kopyalanacak stil yok")
            }
        }
    }

    fun pasteLayerStyleToActive() {
        paintExecutor.execute {
            _uiState.update { it.copy(isBusy = true) }
            runCatching {
                val info = engine.pasteLayerStyleToActive()
                val layers = engine.getLayers()
                _uiState.update { it.copy(docInfo = info, layers = layers, isBusy = false) }
                NativeSurfaceBridge.nativeRenderFrame()
                showStatus("Katman stili yapıştırıldı")
            }.onFailure { err ->
                _uiState.update { it.copy(isBusy = false) }
                showStatus(err.message ?: "Stil yapıştırılamadı")
            }
        }
    }


    // ==================== v1.8.0 CONTENT-AWARE & OBJECT REMOVAL ====================

    fun updateRemoveToolSettings(closeLoops: Boolean? = null, sampleAllLayers: Boolean? = null, newLayer: Boolean? = null) {
        _uiState.update {
            it.copy(
                removeCloseLoops = closeLoops ?: it.removeCloseLoops,
                removeSampleAllLayers = sampleAllLayers ?: it.removeSampleAllLayers,
                removeNewLayer = newLayer ?: it.removeNewLayer
            )
        }
    }

    fun applyRemoveTool(samples: List<StrokeSample>) {
        if (samples.isEmpty()) return
        invalidateProxyQueue()
        val s = _uiState.value
        _uiState.update { it.copy(isBusy = true) }
        paintExecutor.execute {
            runCatching {
                val info = engine.applyRemoveTool(
                    samples, s.brushSize, s.removeCloseLoops, s.removeSampleAllLayers, s.removeNewLayer
                )
                val layers = engine.getLayers()
                _uiState.update { it.copy(docInfo = info, layers = layers, isBusy = false) }
                NativeSurfaceBridge.nativeRenderFrame()
                refreshLayerThumbnails()
                showStatus("Nesne kaldırıldı", 1600L)
            }.onFailure { err ->
                _uiState.update { it.copy(isBusy = false) }
                showStatus("Kaldırma hatası: ${err.message}", 2600L)
            }
        }
    }

    fun updateProxyContentAwareFill(
        sampling: String,
        colorAdaptation: String,
        rotationAdaptation: String,
        scale: Boolean,
        mirror: Boolean,
        sampleAllLayers: Boolean,
        output: String
    ) {
        enqueueProxyJob(
            ProxyJob.ContentAwareFill(sampling, colorAdaptation, rotationAdaptation, scale, mirror, sampleAllLayers, output)
        )
    }

    fun applyContentAwareFill(
        sampling: String,
        colorAdaptation: String,
        rotationAdaptation: String,
        scale: Boolean,
        mirror: Boolean,
        sampleAllLayers: Boolean,
        output: String
    ) {
        invalidateProxyQueue()
        _uiState.update { it.copy(isBusy = true) }
        paintExecutor.execute {
            runCatching {
                val info = engine.applyContentAwareFill(
                    sampling, colorAdaptation, rotationAdaptation, scale, mirror, sampleAllLayers, output
                )
                val layers = engine.getLayers()
                _uiState.update { it.copy(docInfo = info, layers = layers, isBusy = false) }
                NativeSurfaceBridge.nativeRenderFrame()
                refreshLayerThumbnails()
                showStatus("İçerik duyarlı dolgu uygulandı", 1800L)
            }.onFailure { err ->
                _uiState.update { it.copy(isBusy = false) }
                showStatus("İçerik dolgu: ${err.message}", 2800L)
            }
        }
    }

    fun applyContentAwareMove(
        dx: Float,
        dy: Float,
        mode: String,
        structure: Int,
        color: Int,
        scalePct: Float,
        rotateDeg: Float,
        sampleAllLayers: Boolean
    ) {
        invalidateProxyQueue()
        _uiState.update { it.copy(isBusy = true) }
        paintExecutor.execute {
            runCatching {
                val info = engine.applyContentAwareMove(
                    dx, dy, mode,
                    structure.coerceIn(1, 7).toUByte(),
                    color.coerceIn(0, 10).toUByte(),
                    scalePct, scalePct, rotateDeg, sampleAllLayers
                )
                val layers = engine.getLayers()
                val bounds = runCatching { engine.getSelectionBounds() }.getOrNull()
                _uiState.update { it.copy(docInfo = info, layers = layers, selectionBounds = bounds, isBusy = false) }
                NativeSurfaceBridge.nativeRenderFrame()
                refreshLayerThumbnails()
                showStatus(if (mode == "extend") "İçerik duyarlı çoğaltıldı" else "İçerik duyarlı taşındı", 1800L)
            }.onFailure { err ->
                _uiState.update { it.copy(isBusy = false) }
                showStatus("Akıllı taşıma: ${err.message}", 2800L)
            }
        }
    }

    fun updateProxyContentAwareScale(scaleX: Float, scaleY: Float, amount: Float, protectSkinTones: Boolean) {
        enqueueProxyJob(
            ProxyJob.ContentAwareScale(
                kotlin.math.round(scaleX),
                kotlin.math.round(scaleY),
                kotlin.math.round(amount),
                protectSkinTones
            )
        )
    }

    fun applyContentAwareScale(scaleX: Float, scaleY: Float, amount: Float, protectSkinTones: Boolean) {
        invalidateProxyQueue()
        _uiState.update { it.copy(isBusy = true) }
        paintExecutor.execute {
            runCatching {
                val info = engine.applyContentAwareScale(scaleX, scaleY, amount, protectSkinTones)
                val layers = engine.getLayers()
                _uiState.update { it.copy(docInfo = info, layers = layers, isBusy = false) }
                NativeSurfaceBridge.nativeRenderFrame()
                refreshLayerThumbnails()
                showStatus("İçerik duyarlı ölçekleme uygulandı", 1800L)
            }.onFailure { err ->
                _uiState.update { it.copy(isBusy = false) }
                showStatus("Akıllı ölçekleme: ${err.message}", 2800L)
            }
        }
    }

    // ==================== v1.8.2 ADVANCED SELECTION & QUICK MASK ====================

    fun applyLassoSelection(samples: List<StrokeSample>, magnetic: Boolean = false) {
        if (samples.size < 3) {
            showStatus("Kement için en az 3 nokta çizin", 1800L)
            return
        }
        _uiState.update { it.copy(isBusy = true) }
        paintExecutor.execute {
            runCatching {
                val info = if (magnetic) {
                    engine.selectMagneticLasso(samples, 12f, 12f, "replace", 0f)
                } else {
                    engine.selectLasso(samples, "replace", 0f)
                }
                val bounds = runCatching { engine.getSelectionBounds() }.getOrNull()
                _uiState.update { it.copy(docInfo = info, selectionBounds = bounds, isBusy = false) }
                NativeSurfaceBridge.nativeRenderFrame()
                showStatus(if (magnetic) "Manyetik kement seçildi" else "Kement seçildi", 1500L)
            }.onFailure { err ->
                _uiState.update { it.copy(isBusy = false) }
                showStatus("Kement hatası: ${err.message}", 2400L)
            }
        }
    }

    fun updateProxyImageResize(width: Int, height: Int, resample: String) {
        enqueueProxyJob(ProxyJob.ImageResize(width.coerceIn(16, 8192), height.coerceIn(16, 8192), resample))
    }

    fun updateProxyModifySelection(op: String, radius: Float) {
        enqueueProxyJob(ProxyJob.ModifySelection(op, kotlin.math.round(radius)))
    }

    fun updateProxyContentAwareMove(
        dx: Float,
        dy: Float,
        mode: String,
        structure: Int,
        color: Int,
        scalePct: Float,
        rotateDeg: Float,
        sampleAllLayers: Boolean
    ) {
        enqueueProxyJob(
            ProxyJob.ContentAwareMove(
                kotlin.math.round(dx),
                kotlin.math.round(dy),
                mode,
                structure.coerceIn(1, 7),
                color.coerceIn(0, 10),
                kotlin.math.round(scalePct),
                kotlin.math.round(rotateDeg),
                sampleAllLayers
            )
        )
    }

    fun updateProxySelectSky(threshold: Float, softness: Float, mode: String, sampleAllLayers: Boolean) {
        enqueueProxyJob(ProxyJob.SelectSky(kotlin.math.round(threshold), kotlin.math.round(softness), mode, sampleAllLayers))
    }

    fun applySelectSky(threshold: Float, softness: Float, mode: String, sampleAllLayers: Boolean) {
        invalidateProxyQueue()
        _uiState.update { it.copy(isBusy = true) }
        paintExecutor.execute {
            runCatching {
                engine.endProxyPreview()
                val info = engine.selectSky(threshold, softness, mode, sampleAllLayers)
                val bounds = runCatching { engine.getSelectionBounds() }.getOrNull()
                _uiState.update { it.copy(docInfo = info, selectionBounds = bounds, isBusy = false) }
                NativeSurfaceBridge.nativeRenderFrame()
                showStatus(if (bounds != null) "Gökyüzü seçildi" else "Gökyüzü bulunamadı", 1800L)
            }.onFailure { err ->
                _uiState.update { it.copy(isBusy = false) }
                showStatus("Gökyüzü seç: ${err.message}", 2400L)
            }
        }
    }

    fun updateProxyColorRange(preset: String, colorHex: String, fuzziness: Float, invert: Boolean, mode: String, sampleAllLayers: Boolean) {
        enqueueProxyJob(ProxyJob.ColorRange(preset, colorHex, kotlin.math.round(fuzziness), invert, mode, sampleAllLayers))
    }

    fun applyColorRange(preset: String, colorHex: String, fuzziness: Float, invert: Boolean, mode: String, sampleAllLayers: Boolean) {
        invalidateProxyQueue()
        _uiState.update { it.copy(isBusy = true) }
        paintExecutor.execute {
            runCatching {
                engine.endProxyPreview()
                val info = engine.selectColorRange(preset, colorHex, fuzziness, invert, mode, sampleAllLayers)
                val bounds = runCatching { engine.getSelectionBounds() }.getOrNull()
                _uiState.update { it.copy(docInfo = info, selectionBounds = bounds, isBusy = false) }
                NativeSurfaceBridge.nativeRenderFrame()
                showStatus(if (bounds != null) "Renk aralığı seçildi" else "Eşleşen renk bulunamadı", 1800L)
            }.onFailure { err ->
                _uiState.update { it.copy(isBusy = false) }
                showStatus("Renk aralığı: ${err.message}", 2400L)
            }
        }
    }

    fun updateProxyFocusArea(rangePct: Float, noisePct: Float, mode: String, sampleAllLayers: Boolean) {
        enqueueProxyJob(ProxyJob.FocusArea(kotlin.math.round(rangePct), kotlin.math.round(noisePct), mode, sampleAllLayers))
    }

    fun applyFocusArea(rangePct: Float, noisePct: Float, mode: String, sampleAllLayers: Boolean) {
        invalidateProxyQueue()
        _uiState.update { it.copy(isBusy = true) }
        paintExecutor.execute {
            runCatching {
                engine.endProxyPreview()
                val info = engine.selectFocusArea(rangePct, noisePct, mode, sampleAllLayers)
                val bounds = runCatching { engine.getSelectionBounds() }.getOrNull()
                _uiState.update { it.copy(docInfo = info, selectionBounds = bounds, isBusy = false) }
                NativeSurfaceBridge.nativeRenderFrame()
                showStatus(if (bounds != null) "Odak alanı seçildi" else "Odak alanı bulunamadı", 1800L)
            }.onFailure { err ->
                _uiState.update { it.copy(isBusy = false) }
                showStatus("Odak alanı: ${err.message}", 2400L)
            }
        }
    }

    fun applyModifySelection(op: String, radius: Float) {
        invalidateProxyQueue()
        _uiState.update { it.copy(isBusy = true) }
        paintExecutor.execute {
            runCatching {
                engine.endProxyPreview()
                val info = engine.modifySelection(op, radius)
                val bounds = runCatching { engine.getSelectionBounds() }.getOrNull()
                _uiState.update { it.copy(docInfo = info, selectionBounds = bounds, isBusy = false) }
                NativeSurfaceBridge.nativeRenderFrame()
                val lbl = when (op) {
                    "expand" -> "Seçim genişletildi"
                    "contract" -> "Seçim daraltıldı"
                    "border" -> "Kenarlık seçimi oluşturuldu"
                    "smooth" -> "Seçim pürüzsüzleştirildi"
                    else -> "Seçim yumuşatıldı"
                }
                showStatus(lbl, 1600L)
            }.onFailure { err ->
                _uiState.update { it.copy(isBusy = false) }
                showStatus("Seçim değiştir: ${err.message}", 2400L)
            }
        }
    }

    fun applyGrowOrSimilarSelection(similar: Boolean, tolerance: Float, sampleAllLayers: Boolean = true) {
        _uiState.update { it.copy(isBusy = true) }
        paintExecutor.execute {
            runCatching {
                val info = engine.growOrSimilarSelection(similar, tolerance, sampleAllLayers)
                val bounds = runCatching { engine.getSelectionBounds() }.getOrNull()
                _uiState.update { it.copy(docInfo = info, selectionBounds = bounds, isBusy = false) }
                NativeSurfaceBridge.nativeRenderFrame()
                showStatus(if (similar) "Benzer renkler seçildi" else "Komşu benzer alan büyütüldü", 1600L)
            }.onFailure { err ->
                _uiState.update { it.copy(isBusy = false) }
                showStatus("Seçim hatası: ${err.message}", 2400L)
            }
        }
    }

    fun toggleQuickMaskMode(on: Boolean? = null) {
        val target = on ?: !_uiState.value.quickMaskMode
        paintExecutor.execute {
            runCatching {
                val active = engine.toggleQuickMaskMode(target)
                val bounds = runCatching { engine.getSelectionBounds() }.getOrNull()
                _uiState.update {
                    it.copy(
                        quickMaskMode = active,
                        selectionBounds = bounds,
                        activeTool = if (active) ActiveTool.BRUSH else ActiveTool.NONE
                    )
                }
                NativeSurfaceBridge.nativeRenderFrame()
                showStatus(if (active) "Hızlı Maske AÇIK — Siyah/Beyaz fırçayla maskeyi boya" else "Hızlı Maske seçime dönüştürüldü", 2200L)
            }.onFailure { err ->
                showStatus("Hızlı Maske: ${err.message}", 2400L)
            }
        }
    }

    // ==================== v1.8.3 PRO DRAW & RETOUCH TOOLS ====================

    fun setSymmetryMode(mode: String, rotationDeg: Float = 0f) {
        paintExecutor.execute {
            runCatching {
                val active = engine.setSymmetryMode(mode, rotationDeg)
                _uiState.update { it.copy(symmetryMode = active) }
                NativeSurfaceBridge.nativeRenderFrame()
                val lbl = when (active) {
                    "vertical" -> "Simetri: Dikey"
                    "horizontal" -> "Simetri: Yatay"
                    "dual" -> "Simetri: Çift Eksen"
                    "diagonal" -> "Simetri: Çapraz"
                    else -> "Simetri kapatıldı"
                }
                showStatus(lbl, 1500L)
            }.onFailure { err ->
                showStatus("Simetri hatası: ${err.message}", 2200L)
            }
        }
    }

    fun updateV183ToolSettings(
        magicTol: Float? = null,
        magicContiguous: Boolean? = null,
        magicSampleAll: Boolean? = null,
        bgTol: Float? = null,
        bgLimits: String? = null,
        bgProtectFg: Boolean? = null,
        adjKind: String? = null,
        adjSubtract: Boolean? = null,
        artStyle: String? = null,
        artArea: Float? = null,
        artTol: Float? = null,
        healingSampleAll: Boolean? = null
    ) {
        _uiState.update {
            it.copy(
                magicEraserTolerance = magicTol ?: it.magicEraserTolerance,
                magicEraserContiguous = magicContiguous ?: it.magicEraserContiguous,
                magicEraserSampleAll = magicSampleAll ?: it.magicEraserSampleAll,
                bgEraserTolerance = bgTol ?: it.bgEraserTolerance,
                bgEraserLimits = bgLimits ?: it.bgEraserLimits,
                bgEraserProtectFg = bgProtectFg ?: it.bgEraserProtectFg,
                adjBrushKind = adjKind ?: it.adjBrushKind,
                adjBrushSubtract = adjSubtract ?: it.adjBrushSubtract,
                artHistoryStyle = artStyle ?: it.artHistoryStyle,
                artHistoryArea = artArea ?: it.artHistoryArea,
                artHistoryTolerance = artTol ?: it.artHistoryTolerance,
                healingSampleAll = healingSampleAll ?: it.healingSampleAll
            )
        }
    }

    fun applyPencilStroke(samples: List<StrokeSample>) {
        if (samples.isEmpty()) return
        val s = _uiState.value
        paintExecutor.execute {
            runCatching {
                val info = engine.applyPencilStroke(samples, s.brushSize, s.brushOpacity, s.colorHex)
                _uiState.update { it.copy(docInfo = info) }
                NativeSurfaceBridge.nativeRenderFrame()
                refreshLayerThumbnails()
            }.onFailure { err ->
                showStatus("Kalem hatası: ${err.message}", 2200L)
            }
        }
    }

    fun applyMagicEraser(docX: Float, docY: Float) {
        val s = _uiState.value
        paintExecutor.execute {
            runCatching {
                val info = engine.applyMagicEraser(
                    docX, docY, s.magicEraserTolerance, s.magicEraserContiguous, s.magicEraserSampleAll, s.brushOpacity
                )
                val layers = engine.getLayers()
                _uiState.update { it.copy(docInfo = info, layers = layers) }
                NativeSurfaceBridge.nativeRenderFrame()
                refreshLayerThumbnails()
                showStatus("Sihirli silgi uygulandı", 1400L)
            }.onFailure { err ->
                showStatus("Sihirli silgi: ${err.message}", 2400L)
            }
        }
    }

    fun applyBackgroundEraser(samples: List<StrokeSample>) {
        if (samples.isEmpty()) return
        val s = _uiState.value
        paintExecutor.execute {
            runCatching {
                val info = engine.applyBackgroundEraser(
                    samples, s.brushSize, s.brushHardness, s.bgEraserTolerance, s.bgEraserLimits, s.bgEraserProtectFg
                )
                val layers = engine.getLayers()
                _uiState.update { it.copy(docInfo = info, layers = layers) }
                NativeSurfaceBridge.nativeRenderFrame()
                refreshLayerThumbnails()
            }.onFailure { err ->
                showStatus("Arka plan silgisi: ${err.message}", 2400L)
            }
        }
    }

    fun applyAdjustmentBrush(samples: List<StrokeSample>) {
        if (samples.isEmpty()) return
        val s = _uiState.value
        paintExecutor.execute {
            runCatching {
                val info = engine.applyAdjustmentBrush(
                    samples, s.adjBrushKind, s.adjBrushSubtract, s.brushSize, s.brushHardness, s.brushOpacity
                )
                val layers = engine.getLayers()
                _uiState.update { it.copy(docInfo = info, layers = layers) }
                NativeSurfaceBridge.nativeRenderFrame()
                refreshLayerThumbnails()
                showStatus(if (s.adjBrushSubtract) "Ayar maskesinden silindi" else "Ayar fırçası uygulandı", 1400L)
            }.onFailure { err ->
                showStatus("Ayar fırçası: ${err.message}", 2400L)
            }
        }
    }

    fun applyHistoryBrush(samples: List<StrokeSample>) {
        if (samples.isEmpty()) return
        val s = _uiState.value
        paintExecutor.execute {
            runCatching {
                val info = engine.applyHistoryBrush(samples, s.brushSize, s.brushHardness, s.brushOpacity)
                _uiState.update { it.copy(docInfo = info) }
                NativeSurfaceBridge.nativeRenderFrame()
                refreshLayerThumbnails()
            }.onFailure { err ->
                showStatus("Geçmiş fırçası: ${err.message}", 2400L)
            }
        }
    }

    fun applyArtHistoryBrush(samples: List<StrokeSample>) {
        if (samples.isEmpty()) return
        val s = _uiState.value
        paintExecutor.execute {
            runCatching {
                val info = engine.applyArtHistoryBrush(
                    samples, s.brushSize, s.brushHardness, s.brushOpacity,
                    s.artHistoryStyle, s.artHistoryArea, s.artHistoryTolerance
                )
                _uiState.update { it.copy(docInfo = info) }
                NativeSurfaceBridge.nativeRenderFrame()
                refreshLayerThumbnails()
            }.onFailure { err ->
                showStatus("Sanat geçmişi: ${err.message}", 2400L)
            }
        }
    }

    fun startPickHealingSource() {
        _uiState.update { it.copy(activeTool = ActiveTool.HEALING_BRUSH, pickingCloneSource = true) }
        showStatus("Kaynak doku noktasına dokun", 2000L)
    }

    fun applyHealingBrush(samples: List<StrokeSample>) {
        if (samples.isEmpty()) return
        val s = _uiState.value
        val src = s.cloneSourceDoc
        if (src == null) {
            _uiState.update { it.copy(pickingCloneSource = true) }
            showStatus("Önce fotoğraf üzerinde kaynak doku noktasına dokun", 2200L)
            return
        }
        paintExecutor.execute {
            runCatching {
                val info = engine.applyHealingBrushPro(samples, s.brushSize, s.brushHardness, src.first, src.second, s.healingAligned, s.healingSampleLayer, s.healingMode)
                _uiState.update { it.copy(docInfo = info) }
                NativeSurfaceBridge.nativeRenderFrame()
                refreshLayerThumbnails()
            }.onFailure { err ->
                showStatus("Düzeltme fırçası: ${err.message}", 2400L)
            }
        }
    }

    // ==================== v2.4.0 PARÇA 3: LIQUIFY PRO, CLONE SOURCE 5-SLOT, RETOUCH PRO, CAF/CAS PRO ====================

    fun onLiquifyStartPro(tool: String, size: Float, pressure: Float, density: Float, rate: Float, docX: Float, docY: Float) {
        paintExecutor.execute {
            runCatching {
                engine.liquifyStrokeBeginPro(tool, size, pressure, density, rate, docX, docY)
                val hist = engine.getLiquifyHistoryState()
                _uiState.update { it.copy(liquifyCanUndo = hist.canUndo, liquifyCanRedo = hist.canRedo) }
                NativeSurfaceBridge.nativeRenderFrame()
            }
        }
    }

    fun setLiquifyPinEdges(pin: Boolean) {
        paintExecutor.execute {
            runCatching {
                engine.setLiquifyPinEdges(pin)
                NativeSurfaceBridge.nativeRenderFrame()
            }
        }
    }

    fun liquifyMaskAction(action: String) {
        paintExecutor.execute {
            runCatching {
                val hist = engine.liquifyMaskAction(action)
                _uiState.update { it.copy(liquifyCanUndo = hist.canUndo, liquifyCanRedo = hist.canRedo) }
                NativeSurfaceBridge.nativeRenderFrame()
            }
        }
    }

    fun liquifyReconstructAll(amountPct: Float) {
        paintExecutor.execute {
            runCatching {
                val hist = engine.liquifyReconstructAll(amountPct)
                _uiState.update { it.copy(liquifyCanUndo = hist.canUndo, liquifyCanRedo = hist.canRedo) }
                NativeSurfaceBridge.nativeRenderFrame()
            }
        }
    }

    fun liquifyLassoMask(points: List<StrokeSample>, freeze: Boolean) {
        if (points.size < 3) return
        paintExecutor.execute {
            runCatching {
                val hist = engine.liquifyLassoMask(points, freeze)
                _uiState.update { it.copy(liquifyCanUndo = hist.canUndo, liquifyCanRedo = hist.canRedo) }
                NativeSurfaceBridge.nativeRenderFrame()
            }
        }
    }

    fun updateCloneSourcePro(
        slot: Int = _uiState.value.cloneSlot,
        scale: Float = _uiState.value.cloneScale,
        angle: Float = _uiState.value.cloneAngle,
        flipH: Boolean = _uiState.value.cloneFlipH,
        flipV: Boolean = _uiState.value.cloneFlipV,
        overlay: Boolean = _uiState.value.cloneOverlay,
        aligned: Boolean = _uiState.value.cloneAligned,
        sampleLayer: String = _uiState.value.cloneSampleLayer,
        blendMode: String = _uiState.value.cloneBlendMode
    ) {
        _uiState.update {
            it.copy(
                cloneSlot = slot.coerceIn(0, 4),
                cloneScale = scale.coerceIn(25f, 300f),
                cloneAngle = angle.coerceIn(-180f, 180f),
                cloneFlipH = flipH,
                cloneFlipV = flipV,
                cloneOverlay = overlay,
                cloneAligned = aligned,
                cloneSampleLayer = sampleLayer,
                cloneBlendMode = blendMode
            )
        }
        paintExecutor.execute {
            runCatching {
                engine.configureCloneSource(slot.toUInt(), scale, scale, angle, flipH, flipV, overlay, 50f)
            }
        }
    }

    fun resetCloneSourceTransform() {
        val slot = _uiState.value.cloneSlot
        _uiState.update { it.copy(cloneScale = 100f, cloneAngle = 0f, cloneFlipH = false, cloneFlipV = false) }
        paintExecutor.execute {
            runCatching { engine.resetCloneSourceTransform(slot.toUInt()) }
        }
    }

    fun updateHealingPro(aligned: Boolean = _uiState.value.healingAligned, sampleLayer: String = _uiState.value.healingSampleLayer, mode: String = _uiState.value.healingMode) {
        _uiState.update { it.copy(healingAligned = aligned, healingSampleLayer = sampleLayer, healingMode = mode, healingSampleAll = sampleLayer != "current") }
    }

    fun updateSpotHealPro(type: String = _uiState.value.spotHealType, sampleAll: Boolean = _uiState.value.spotHealSampleAll) {
        _uiState.update { it.copy(spotHealType = type, spotHealSampleAll = sampleAll) }
    }

    fun updatePatchPro(contentAware: Boolean = _uiState.value.patchContentAware, structure: Int = _uiState.value.patchStructure, color: Int = _uiState.value.patchColor, sampleAll: Boolean = _uiState.value.patchSampleAll) {
        _uiState.update { it.copy(patchContentAware = contentAware, patchStructure = structure.coerceIn(1, 7), patchColor = color.coerceIn(0, 10), patchSampleAll = sampleAll) }
    }

    fun updateRetouchPro(
        range: String = _uiState.value.retouchRange,
        protectTones: Boolean = _uiState.value.retouchProtectTones,
        fingerPainting: Boolean = _uiState.value.smudgeFingerPainting,
        protectDetail: Boolean = _uiState.value.sharpenProtectDetail,
        sampleAll: Boolean = _uiState.value.retouchSampleAll
    ) {
        _uiState.update {
            it.copy(
                retouchRange = range,
                retouchProtectTones = protectTones,
                smudgeFingerPainting = fingerPainting,
                sharpenProtectDetail = protectDetail,
                retouchSampleAll = sampleAll
            )
        }
    }

    fun updateProxyContentAwareFillPro(
        sampling: String, colorAdaptation: String, rotationAdaptation: String,
        scale: Boolean, mirror: Boolean, sampleAllLayers: Boolean,
        strokes: List<CafSamplingStrokeInput>
    ) {
        enqueueProxyJob(ProxyJob.ContentAwareFillPro(sampling, colorAdaptation, rotationAdaptation, scale, mirror, sampleAllLayers, strokes))
    }

    fun applyContentAwareFillPro(
        sampling: String, colorAdaptation: String, rotationAdaptation: String,
        scale: Boolean, mirror: Boolean, sampleAllLayers: Boolean, output: String,
        strokes: List<CafSamplingStrokeInput>
    ) {
        _uiState.update { it.copy(isBusy = true) }
        paintExecutor.execute {
            runCatching {
                val info = engine.applyContentAwareFillPro(sampling, colorAdaptation, rotationAdaptation, scale, mirror, sampleAllLayers, output, strokes)
                val layers = engine.getLayers()
                _uiState.update { it.copy(docInfo = info, layers = layers, isBusy = false) }
                NativeSurfaceBridge.nativeRenderFrame()
                showStatus("İçeriğe Uygun Dolgu uygulandı", 2200L)
            }.onFailure { err ->
                _uiState.update { it.copy(isBusy = false) }
                showStatus("Dolgu hatası: ${err.message}", 3200L)
            }
        }
    }

    fun updateProxyContentAwareScalePro(
        scaleX: Float, scaleY: Float, amount: Float, protectSkinTones: Boolean,
        protectStrokes: List<CafSamplingStrokeInput>
    ) {
        enqueueProxyJob(ProxyJob.ContentAwareScalePro(scaleX, scaleY, amount, protectSkinTones, protectStrokes))
    }

    fun applyContentAwareScalePro(
        scaleX: Float, scaleY: Float, amount: Float, protectSkinTones: Boolean,
        protectStrokes: List<CafSamplingStrokeInput>
    ) {
        _uiState.update { it.copy(isBusy = true) }
        paintExecutor.execute {
            runCatching {
                val info = engine.applyContentAwareScalePro(scaleX, scaleY, amount, protectSkinTones, protectStrokes)
                val layers = engine.getLayers()
                _uiState.update { it.copy(docInfo = info, layers = layers, isBusy = false) }
                NativeSurfaceBridge.nativeRenderFrame()
                showStatus("İçeriğe Uygun Ölçek uygulandı", 2200L)
            }.onFailure { err ->
                _uiState.update { it.copy(isBusy = false) }
                showStatus("Ölçekleme hatası: ${err.message}", 3200L)
            }
        }
    }

}
