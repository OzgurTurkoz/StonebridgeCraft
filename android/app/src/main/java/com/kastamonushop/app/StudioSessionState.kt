package com.kastamonushop.app

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kastamonushop.app.bridge.NativeSurfaceBridge
import com.kastamonushop.app.data.SavedPhotoItem
import com.kastamonushop.app.ui.canvas.RulerLinePreview
import com.kastamonushop.app.ui.canvas.VulkanCanvasView
import com.kastamonushop.app.viewmodel.ActiveTool
import com.kastamonushop.app.viewmodel.EditorUiState
import com.kastamonushop.app.viewmodel.EditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uniffi.kastamonushop_ffi.PuppetPinPoint
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

@Stable
internal class StudioSessionState {
    var ldKind by mutableStateOf("none")
    var ldAmount by mutableStateOf(50f)
    var ldDetail by mutableStateOf(45f)
    var ldX by mutableStateOf(35f)
    var ldY by mutableStateOf(30f)
    var ldVariant by mutableStateOf("moviePrime")
    var puppetPins by mutableStateOf<List<PuppetPinPoint>>(emptyList())
    var puppetSelectedIdx by mutableIntStateOf(-1)
    var puppetMode by mutableStateOf("normal")
    var puppetDensity by mutableStateOf("normal")
    var puppetExpansion by mutableFloatStateOf(2f)
    var puppetShowMesh by mutableStateOf(false)
    var puppetMeshSegments by mutableStateOf<List<Float>>(emptyList())
    var puppetUndoStack by mutableStateOf<List<List<PuppetPinPoint>>>(emptyList())
    var puppetRedoStack by mutableStateOf<List<List<PuppetPinPoint>>>(emptyList())
    var resizeWidthText by mutableStateOf("1080")
    var resizeHeightText by mutableStateOf("1080")
    var resizeLockAspect by mutableStateOf(true)
    var resizeResample by mutableStateOf("lanczos")
    var loupeScreenPos by mutableStateOf<Offset?>(null)
    var showLayers by mutableStateOf(false)
    var singleFingerPanMode by mutableStateOf(false)
    var activePanel by mutableStateOf(BottomPanel.DOCK)
    var showHsvPicker by mutableStateOf(false)
    var showOpenMenu by mutableStateOf(false)
    var showExportMenu by mutableStateOf(false)
    var showBlendMenu by mutableStateOf(false)
    var pendingExportFormat by mutableStateOf("png")
    var openAsLayer by mutableStateOf(false)
    var canvasViewRef by mutableStateOf<VulkanCanvasView?>(null)
    var rulerPreview by mutableStateOf<RulerLinePreview?>(null)
    var retouchCursor by mutableStateOf<Pair<Float, Float>?>(null)
    val retouchTrailPoints = mutableStateListOf<Offset>()
    var cropNormL by mutableFloatStateOf(0.05f)
    var cropNormT by mutableFloatStateOf(0.05f)
    var cropNormR by mutableFloatStateOf(0.95f)
    var cropNormB by mutableFloatStateOf(0.95f)
    var cropFreeMode by mutableStateOf(false)
    var selectPreviewRect by mutableStateOf<FloatArray?>(null)
    var levelsBlackVal by mutableFloatStateOf(0f)
    var levelsGammaVal by mutableFloatStateOf(1f)
    var levelsWhiteVal by mutableFloatStateOf(255f)
    var cbCyanRedVal by mutableFloatStateOf(0f)
    var cbMagentaGreenVal by mutableFloatStateOf(0f)
    var cbYellowBlueVal by mutableFloatStateOf(0f)
    var curveBlackVal by mutableFloatStateOf(0.0f)
    var curveShadowVal by mutableFloatStateOf(0.25f)
    var curveMidVal by mutableFloatStateOf(0.50f)
    var curveHighVal by mutableFloatStateOf(0.75f)
    var curveWhiteVal by mutableFloatStateOf(1.0f)
    var curveOverlayExpanded by mutableStateOf(false)
    var draggingCurveNode by mutableIntStateOf(-1)
    var pTl by mutableStateOf(Offset(0.12f, 0.12f))
    var pTr by mutableStateOf(Offset(0.88f, 0.12f))
    var pBr by mutableStateOf(Offset(0.88f, 0.88f))
    var pBl by mutableStateOf(Offset(0.12f, 0.88f))
    var draggingCornerIdx by mutableIntStateOf(-1)
    var layerDx by mutableFloatStateOf(0f)
    var layerDy by mutableFloatStateOf(0f)
    var layerScalePct by mutableFloatStateOf(100f)
    var layerScaleXPct by mutableFloatStateOf(100f)
    var layerScaleYPct by mutableFloatStateOf(100f)
    var layerLockAspect by mutableStateOf(false)
    var layerRotDeg by mutableFloatStateOf(0f)
    var showBrushFineTune by mutableStateOf(false)
    var brushSizePreviewTick by mutableIntStateOf(0)
    var showBrushSizeCircle by mutableStateOf(false)
    var fxKind by mutableStateOf("dropShadow")
    var fxColorHex by mutableStateOf("#000000")
    var fxSizeVal by mutableFloatStateOf(16f)
    var fxOpacityVal by mutableFloatStateOf(75f)
    var fxDistanceVal by mutableFloatStateOf(12f)
    var showFxFineTune by mutableStateOf(false)
    var blendIfThisBlack by mutableFloatStateOf(0f)
    var blendIfThisWhite by mutableFloatStateOf(255f)
    var blendIfUnderBlack by mutableFloatStateOf(0f)
    var blendIfUnderWhite by mutableFloatStateOf(255f)
    var blendIfFeather by mutableFloatStateOf(20f)
    var showExitSaveDialog by mutableStateOf(false)
    var textBoxSize by mutableStateOf(androidx.compose.ui.unit.IntSize.Zero)
    var camExposureVal by mutableFloatStateOf(0f)
    var camShadowsVal by mutableFloatStateOf(0f)
    var camHighlightsVal by mutableFloatStateOf(0f)
    var camVibranceVal by mutableFloatStateOf(0f)
    var camTempVal by mutableFloatStateOf(0f)
    var camTintVal by mutableFloatStateOf(0f)
    var camClarityVal by mutableFloatStateOf(0f)
    var camDehazeVal by mutableFloatStateOf(0f)
    var showCamFineTune by mutableStateOf(false)
    // v1.6.0 Lightroom RAW Pro extended states
    var camTabIdx by mutableIntStateOf(0)
    var camContrastVal by mutableFloatStateOf(0f)
    var camWhitesVal by mutableFloatStateOf(0f)
    var camBlacksVal by mutableFloatStateOf(0f)
    var camSatVal by mutableFloatStateOf(0f)
    var camTextureVal by mutableFloatStateOf(0f)
    var camSharpenVal by mutableFloatStateOf(0f)
    var camNoiseLumVal by mutableFloatStateOf(0f)
    var camNoiseColVal by mutableFloatStateOf(0f)
    var camGradeShadowHue by mutableFloatStateOf(220f)
    var camGradeShadowSat by mutableFloatStateOf(0f)
    var camGradeMidHue by mutableFloatStateOf(40f)
    var camGradeMidSat by mutableFloatStateOf(0f)
    var camGradeHighHue by mutableFloatStateOf(45f)
    var camGradeHighSat by mutableFloatStateOf(0f)
    var camCalRedHue by mutableFloatStateOf(0f)
    var camCalRedSat by mutableFloatStateOf(0f)
    var camCalGreenHue by mutableFloatStateOf(0f)
    var camCalGreenSat by mutableFloatStateOf(0f)
    var camCalBlueHue by mutableFloatStateOf(0f)
    var camCalBlueSat by mutableFloatStateOf(0f)
    var camVignetteVal by mutableFloatStateOf(0f)
    var camGrainVal by mutableFloatStateOf(0f)
    // v1.6.0 Lens Correction states
    var lensDistortion by mutableFloatStateOf(0f)
    var lensVignette by mutableFloatStateOf(0f)
    var lensRedCyan by mutableFloatStateOf(0f)
    var lensBlueYellow by mutableFloatStateOf(0f)
    var lensVertical by mutableFloatStateOf(0f)
    var lensHorizontal by mutableFloatStateOf(0f)
    // v1.6.0 .cube 3D LUT states
    var lutFileName by mutableStateOf("")
    var lutText by mutableStateOf("")
    var lutOpacity by mutableFloatStateOf(100f)
    // v1.6.0 Per-channel R/G/B Curves states
    var curveChannelIdx by mutableIntStateOf(0) // 0=RGB, 1=R, 2=G, 3=B
    var curveRedPts by mutableStateOf(listOf(0f, 0.25f, 0.50f, 0.75f, 1f))
    var curveGreenPts by mutableStateOf(listOf(0f, 0.25f, 0.50f, 0.75f, 1f))
    var curveBluePts by mutableStateOf(listOf(0f, 0.25f, 0.50f, 0.75f, 1f))
    // v1.6.0 3-Tone Color Balance states
    var cbToneIdx by mutableIntStateOf(1) // 0=Gölgeler, 1=Orta Tonlar, 2=Aydınlıklar
    var cbShadowCR by mutableFloatStateOf(0f)
    var cbShadowMG by mutableFloatStateOf(0f)
    var cbShadowYB by mutableFloatStateOf(0f)
    var cbHighCR by mutableFloatStateOf(0f)
    var cbHighMG by mutableFloatStateOf(0f)
    var cbHighYB by mutableFloatStateOf(0f)
    var gradFromNormX by mutableFloatStateOf(0.20f)
    var gradFromNormY by mutableFloatStateOf(0.20f)
    var gradToNormX by mutableFloatStateOf(0.80f)
    var gradToNormY by mutableFloatStateOf(0.80f)
    var gradStyle by mutableStateOf("linear")
    var gradStartHex by mutableStateOf("#4F9CFF")
    var gradEndHex by mutableStateOf("#FF4D4F")
    var gradOpacity by mutableFloatStateOf(85f)
    var shapeKind by mutableStateOf("roundedRect")
    var shapeX0Norm by mutableFloatStateOf(0.25f)
    var shapeY0Norm by mutableFloatStateOf(0.25f)
    var shapeX1Norm by mutableFloatStateOf(0.75f)
    var shapeY1Norm by mutableFloatStateOf(0.75f)
    var shapeFillHex by mutableStateOf("#4F9CFF")
    var shapeStrokeHex by mutableStateOf("#FFFFFF")
    var shapeStrokeWidth by mutableFloatStateOf(6f)
    var shapeCornerRadius by mutableFloatStateOf(24f)
    var shapeSides by mutableIntStateOf(6)
    var showShapeFineTune by mutableStateOf(false)
    var liquifyTool by mutableStateOf("forwardWarp")
    var liquifySize by mutableFloatStateOf(120f)
    var liquifyPressure by mutableFloatStateOf(75f)
    var liquifyCursorScreen by mutableStateOf<Offset?>(null)
    var cinemaPreset by mutableStateOf("golden")
    var cinemaIntensity by mutableFloatStateOf(80f)
    var cinemaVignette by mutableFloatStateOf(-25f)
    var cinemaGrain by mutableFloatStateOf(12f)
    var motionBlurAngle by mutableFloatStateOf(0f)
    var motionBlurDistance by mutableFloatStateOf(24f)
    var radialBlurAmount by mutableFloatStateOf(14f)
    var radialBlurMethod by mutableStateOf("zoom")
    var hslBandIdx by mutableIntStateOf(0)
    var hslHue by mutableStateOf(List(8) { 0f })
    var hslSat by mutableStateOf(List(8) { 0f })
    var hslLum by mutableStateOf(List(8) { 0f })
    var bucketTolerance by mutableFloatStateOf(36f)
    var bucketContiguous by mutableStateOf(true)
    var posterMode by mutableStateOf(true)
    var posterLevels by mutableFloatStateOf(6f)
    var threshLevel by mutableFloatStateOf(128f)
    var posterTouched by mutableStateOf(false)
    var galleryKind by mutableStateOf("none")
    var galleryAmount by mutableFloatStateOf(45f)
    var galleryDetail by mutableFloatStateOf(50f)
    var textDraft by mutableStateOf("StonebridgeCraft")
    var textNormX by mutableFloatStateOf(0.50f)
    var textNormY by mutableFloatStateOf(0.50f)
    var textSizePx by mutableFloatStateOf(140f)
    var textColorHex by mutableStateOf("#FFFFFF")
    var textAlign by mutableStateOf("center")
    var textFontFamily by mutableStateOf("Montserrat")
    var textBold by mutableStateOf(false)
    var textItalic by mutableStateOf(false)
    var textTracking by mutableFloatStateOf(0f)
    var textLineHeight by mutableFloatStateOf(1.2f)
    var textVertical by mutableStateOf(false)
    var textEditLayerId by mutableStateOf<ULong?>(null)
    var textUnderline by mutableStateOf(false)
    var textStrike by mutableStateOf(false)
    var textCaps by mutableStateOf("normal")
    var textHScale by mutableFloatStateOf(100f)
    var textVScale by mutableFloatStateOf(100f)
    var textWarpStyle by mutableStateOf("none")
    var textWarpBend by mutableFloatStateOf(50f)
    var textWarpHDist by mutableFloatStateOf(0f)
    var textWarpVDist by mutableFloatStateOf(0f)
    // v2.2.0 Parça 1: Text Pro extras
    var textRotationDeg by mutableFloatStateOf(0f)
    var textOpacity by mutableFloatStateOf(1f)
    var textBlendMode by mutableStateOf("Normal")
    var textStrokeWidth by mutableFloatStateOf(0f)
    var textStrokeColor by mutableStateOf("#111318")
    var textShadowEnabled by mutableStateOf(false)
    var textShadowDist by mutableFloatStateOf(10f)
    var textShadowBlur by mutableFloatStateOf(12f)
    var textShadowAngle by mutableFloatStateOf(120f)
    var textShadowColor by mutableStateOf("#000000")
    var textGlowSize by mutableFloatStateOf(0f)
    var textGlowColor by mutableStateOf("#38BDF8")
    var textGradientEnabled by mutableStateOf(false)
    var textGradientEndColor by mutableStateOf("#F59E0B")
    var textGradientAngle by mutableFloatStateOf(90f)
    var textBgEnabled by mutableStateOf(false)
    var textBgColor by mutableStateOf("#111318")
    var textBgOpacity by mutableFloatStateOf(0.80f)
    var textBgRadius by mutableFloatStateOf(16f)
    var textBgPadding by mutableFloatStateOf(18f)
    var textBaselineShift by mutableFloatStateOf(0f)
    var textBoxWidthNorm by mutableFloatStateOf(0f)
    var textSnapX by mutableStateOf(false)
    var textSnapY by mutableStateOf(false)

    // v2.3.0 Parça 2: Arbitrary-point Curves Pro (2..16 points per channel) + Eyedropper
    var curveRgbNodes by mutableStateOf(listOf(0f to 0f, 0.25f to 0.25f, 0.50f to 0.50f, 0.75f to 0.75f, 1f to 1f))
    var curveRedNodes by mutableStateOf(listOf(0f to 0f, 0.25f to 0.25f, 0.50f to 0.50f, 0.75f to 0.75f, 1f to 1f))
    var curveGreenNodes by mutableStateOf(listOf(0f to 0f, 0.25f to 0.25f, 0.50f to 0.50f, 0.75f to 0.75f, 1f to 1f))
    var curveBlueNodes by mutableStateOf(listOf(0f to 0f, 0.25f to 0.25f, 0.50f to 0.50f, 0.75f to 0.75f, 1f to 1f))
    var curveSelectedNodeIdx by mutableIntStateOf(-1)
    var curveEyedropperMode by mutableIntStateOf(0) // 0=off, 1=black, 2=gray, 3=white

    data class CurveSnapshot(
        val rgb: List<Pair<Float, Float>>,
        val red: List<Pair<Float, Float>>,
        val green: List<Pair<Float, Float>>,
        val blue: List<Pair<Float, Float>>
    )
    var curveUndoStack by mutableStateOf<List<CurveSnapshot>>(emptyList())
    var curveRedoStack by mutableStateOf<List<CurveSnapshot>>(emptyList())

    fun defaultCurveNodes(): List<Pair<Float, Float>> =
        listOf(0f to 0f, 0.25f to 0.25f, 0.50f to 0.50f, 0.75f to 0.75f, 1f to 1f)

    fun activeCurveNodes(): List<Pair<Float, Float>> = when (curveChannelIdx) {
        1 -> curveRedNodes
        2 -> curveGreenNodes
        3 -> curveBlueNodes
        else -> curveRgbNodes
    }

    fun setActiveCurveNodes(nodes: List<Pair<Float, Float>>) {
        val sorted = nodes.sortedBy { it.first }
        when (curveChannelIdx) {
            1 -> curveRedNodes = sorted
            2 -> curveGreenNodes = sorted
            3 -> curveBlueNodes = sorted
            else -> curveRgbNodes = sorted
        }
    }

    fun snapshotCurve(): CurveSnapshot = CurveSnapshot(
        rgb = curveRgbNodes.toList(),
        red = curveRedNodes.toList(),
        green = curveGreenNodes.toList(),
        blue = curveBlueNodes.toList()
    )

    fun restoreCurve(snap: CurveSnapshot) {
        curveRgbNodes = snap.rgb.toList()
        curveRedNodes = snap.red.toList()
        curveGreenNodes = snap.green.toList()
        curveBlueNodes = snap.blue.toList()
        curveSelectedNodeIdx = -1
    }

    fun resetActiveCurveChannel() {
        setActiveCurveNodes(defaultCurveNodes())
        curveSelectedNodeIdx = -1
    }

    fun resetAllCurves() {
        curveRgbNodes = defaultCurveNodes()
        curveRedNodes = defaultCurveNodes()
        curveGreenNodes = defaultCurveNodes()
        curveBlueNodes = defaultCurveNodes()
        curveChannelIdx = 0
        curveSelectedNodeIdx = -1
        curveEyedropperMode = 0
        curveBlackVal = 0f; curveShadowVal = 0.25f; curveMidVal = 0.50f; curveHighVal = 0.75f; curveWhiteVal = 1f
        curveRedPts = listOf(0f, 0.25f, 0.50f, 0.75f, 1f)
        curveGreenPts = listOf(0f, 0.25f, 0.50f, 0.75f, 1f)
        curveBluePts = listOf(0f, 0.25f, 0.50f, 0.75f, 1f)
    }

    fun isCurvesIdentity(): Boolean {
        fun isLine(pts: List<Pair<Float, Float>>): Boolean =
            pts.all { kotlin.math.abs(it.first - it.second) < 0.005f }
        return isLine(curveRgbNodes) && isLine(curveRedNodes) && isLine(curveGreenNodes) && isLine(curveBlueNodes)
    }

    fun buildCurvesProJson(): String {
        fun fmt(pts: List<Pair<Float, Float>>): String =
            pts.sortedBy { it.first }.joinToString(",", "[", "]") { (x, y) ->
                "[${(x * 255f).toInt().coerceIn(0, 255)},${(y * 255f).toInt().coerceIn(0, 255)}]"
            }
        return "{\"points\":${fmt(curveRgbNodes)},\"red\":${fmt(curveRedNodes)},\"green\":${fmt(curveGreenNodes)},\"blue\":${fmt(curveBlueNodes)}}"
    }

    // v2.3.0 Parça 2: Levels Pro (RGB/R/G/B + Output Black/White)
    data class LevelsChannelState(
        val inBlack: Float = 0f,
        val gamma: Float = 1f,
        val inWhite: Float = 255f,
        val outBlack: Float = 0f,
        val outWhite: Float = 255f
    )
    var levelsChannelIdx by mutableIntStateOf(0) // 0=RGB, 1=R, 2=G, 3=B
    var levelsShowOutput by mutableStateOf(false)
    var levelsRgb by mutableStateOf(LevelsChannelState())
    var levelsRed by mutableStateOf(LevelsChannelState())
    var levelsGreen by mutableStateOf(LevelsChannelState())
    var levelsBlue by mutableStateOf(LevelsChannelState())

    fun activeLevelsChannel(): LevelsChannelState = when (levelsChannelIdx) {
        1 -> levelsRed; 2 -> levelsGreen; 3 -> levelsBlue; else -> levelsRgb
    }
    fun setActiveLevelsChannel(v: LevelsChannelState) {
        when (levelsChannelIdx) {
            1 -> levelsRed = v; 2 -> levelsGreen = v; 3 -> levelsBlue = v; else -> levelsRgb = v
        }
    }
    fun resetLevelsPro() {
        levelsChannelIdx = 0; levelsShowOutput = false
        levelsRgb = LevelsChannelState(); levelsRed = LevelsChannelState()
        levelsGreen = LevelsChannelState(); levelsBlue = LevelsChannelState()
        levelsBlackVal = 0f; levelsGammaVal = 1f; levelsWhiteVal = 255f
    }
    fun buildLevelsProJson(): String {
        fun fmtCh(c: LevelsChannelState): String {
            val g = String.format(java.util.Locale.US, "%.2f", c.gamma.coerceIn(0.1f, 9.99f))
            return "\"inBlack\":${c.inBlack.toInt().coerceIn(0, 250)},\"gamma\":$g,\"inWhite\":${c.inWhite.toInt().coerceIn(5, 255)},\"outBlack\":${c.outBlack.toInt().coerceIn(0, 255)},\"outWhite\":${c.outWhite.toInt().coerceIn(0, 255)}"
        }
        return "{${fmtCh(levelsRgb)},\"red\":{${fmtCh(levelsRed)}},\"green\":{${fmtCh(levelsGreen)}},\"blue\":{${fmtCh(levelsBlue)}}}"
    }

    // v2.3.0 Parça 2: Color Balance preserveLuminosity + reset
    var cbPreserveLuminosity by mutableStateOf(true)
    fun resetColorBalancePro() {
        cbToneIdx = 1; cbPreserveLuminosity = true
        cbShadowCR = 0f; cbShadowMG = 0f; cbShadowYB = 0f
        cbCyanRedVal = 0f; cbMagentaGreenVal = 0f; cbYellowBlueVal = 0f
        cbHighCR = 0f; cbHighMG = 0f; cbHighYB = 0f
    }

    // v2.3.0 Parça 2: HSL Targeted Adjustment Tool (TAT)
    var hslTatActive by mutableStateOf(false)
    var hslTatParamIdx by mutableIntStateOf(1) // 0=Ton, 1=Doygunluk, 2=Parlaklık
    fun resetHslPro() {
        hslBandIdx = 0; hslTatActive = false; hslTatParamIdx = 1
        hslHue = List(8) { 0f }; hslSat = List(8) { 0f }; hslLum = List(8) { 0f }
    }

    // v2.3.0 Parça 2: Camera RAW Pro focused slider index
    var camFocusParamIdx by mutableIntStateOf(0)

    // v2.3.0 Parça 2: Black & White Pro (6 channels + Tint)
    var bwWeights by mutableStateOf(listOf(40f, 60f, 40f, 60f, 20f, 80f)) // R,Y,G,C,B,M
    var bwSelectedChannel by mutableIntStateOf(0)
    var bwTintEnabled by mutableStateOf(false)
    var bwTintColorHex by mutableStateOf("#E1D3B3")
    fun resetBwPro() {
        bwWeights = listOf(40f, 60f, 40f, 60f, 20f, 80f)
        bwSelectedChannel = 0; bwTintEnabled = false; bwTintColorHex = "#E1D3B3"
    }
    fun buildBwProJson(): String =
        "{\"reds\":${bwWeights[0].toInt()},\"yellows\":${bwWeights[1].toInt()},\"greens\":${bwWeights[2].toInt()},\"cyans\":${bwWeights[3].toInt()},\"blues\":${bwWeights[4].toInt()},\"magentas\":${bwWeights[5].toInt()},\"tint\":$bwTintEnabled,\"tintColor\":\"$bwTintColorHex\"}"

    // v2.3.0 Parça 2: Photo Filter Pro
    var pfColorHex by mutableStateOf("#EC8A00")
    var pfDensity by mutableFloatStateOf(25f)
    var pfPreserveLum by mutableStateOf(true)
    fun resetPhotoFilterPro() { pfColorHex = "#EC8A00"; pfDensity = 25f; pfPreserveLum = true }
    fun buildPhotoFilterJson(): String =
        "{\"color\":\"$pfColorHex\",\"density\":${pfDensity.toInt().coerceIn(0, 100)},\"preserveLuminosity\":$pfPreserveLum}"

    // v2.3.0 Parça 2: Selective Color Pro (9 ranges x 4 CMYK)
    var selColRangeIdx by mutableIntStateOf(0)
    var selColParamIdx by mutableIntStateOf(0) // 0=C,1=M,2=Y,3=K
    var selColRelative by mutableStateOf(true)
    var selColMatrix by mutableStateOf(List(9) { listOf(0f, 0f, 0f, 0f) })
    fun resetSelectiveColorPro() {
        selColRangeIdx = 0; selColParamIdx = 0; selColRelative = true
        selColMatrix = List(9) { listOf(0f, 0f, 0f, 0f) }
    }
    fun buildSelectiveColorJson(): String {
        val keys = listOf("reds", "yellows", "greens", "cyans", "blues", "magentas", "whites", "neutrals", "blacks")
        val method = if (selColRelative) "relative" else "absolute"
        val entries = keys.indices.joinToString(",") { i ->
            val r = selColMatrix[i]
            "\"${keys[i]}\":[${r[0].toInt()},${r[1].toInt()},${r[2].toInt()},${r[3].toInt()}]"
        }
        return "{\"method\":\"$method\",$entries}"
    }

    // v2.3.0 Parça 2: Channel Mixer Pro
    var chMixOutIdx by mutableIntStateOf(0) // 0=Red, 1=Green, 2=Blue
    var chMixParamIdx by mutableIntStateOf(0) // 0=R, 1=G, 2=B, 3=Const
    var chMixMono by mutableStateOf(false)
    var chMixRows by mutableStateOf(listOf(
        listOf(100f, 0f, 0f, 0f),
        listOf(0f, 100f, 0f, 0f),
        listOf(0f, 0f, 100f, 0f)
    ))
    fun resetChannelMixerPro() {
        chMixOutIdx = 0; chMixParamIdx = 0; chMixMono = false
        chMixRows = listOf(listOf(100f, 0f, 0f, 0f), listOf(0f, 100f, 0f, 0f), listOf(0f, 0f, 100f, 0f))
    }
    fun buildChannelMixerJson(): String {
        fun fmt(r: List<Float>) = "[${r[0].toInt()},${r[1].toInt()},${r[2].toInt()},${r[3].toInt()}]"
        return if (chMixMono) {
            "{\"monochrome\":true,\"gray\":${fmt(chMixRows[0])},\"red\":${fmt(chMixRows[0])},\"green\":${fmt(chMixRows[1])},\"blue\":${fmt(chMixRows[2])}}"
        } else {
            "{\"monochrome\":false,\"red\":${fmt(chMixRows[0])},\"green\":${fmt(chMixRows[1])},\"blue\":${fmt(chMixRows[2])}}"
        }
    }

    // v2.3.0 Parça 2: Gradient Map Pro
    var gmShadowHex by mutableStateOf("#141B3A")
    var gmMidHex by mutableStateOf("#B84A39")
    var gmHighHex by mutableStateOf("#F7D488")
    var gmUseMid by mutableStateOf(true)
    var gmReverse by mutableStateOf(false)
    var gmPresetIdx by mutableIntStateOf(0)

    // v2.4.0 Parça 3: Liquify Pro (Density, Rate, Pin Edges, ReconstructAll, Lasso Freeze)
    var liquifyDensity by mutableFloatStateOf(65f)
    var liquifyRate by mutableFloatStateOf(75f)
    var liquifyPinEdges by mutableStateOf(true)
    var liquifyLassoPoints by mutableStateOf<List<Offset>>(emptyList())
    var liquifyReconstructPct by mutableFloatStateOf(0f)

    // v2.4.0 Parça 3: CAF Sampling Brush (Green Mask) & CAS Protect Brush (Blue Mask)
    var cafBrushActive by mutableStateOf(false)
    var cafBrushAdd by mutableStateOf(true)
    var cafBrushSize by mutableFloatStateOf(80f)
    var cafStrokes by mutableStateOf<List<uniffi.kastamonushop_ffi.CafSamplingStrokeInput>>(emptyList())
    var cafActiveScreenStroke by mutableStateOf<List<Offset>>(emptyList())

    var casBrushActive by mutableStateOf(false)
    var casBrushAdd by mutableStateOf(true)
    var casBrushSize by mutableFloatStateOf(90f)
    var casProtectStrokes by mutableStateOf<List<uniffi.kastamonushop_ffi.CafSamplingStrokeInput>>(emptyList())
    var casActiveScreenStroke by mutableStateOf<List<Offset>>(emptyList())
    fun resetGradientMapPro() {
        gmShadowHex = "#141B3A"; gmMidHex = "#B84A39"; gmHighHex = "#F7D488"
        gmUseMid = true; gmReverse = false; gmPresetIdx = 0
    }
    fun buildGradientMapJson(): String {
        val stops = if (gmUseMid) {
            "[[0.0,\"$gmShadowHex\"],[0.5,\"$gmMidHex\"],[1.0,\"$gmHighHex\"]]"
        } else {
            "[[0.0,\"$gmShadowHex\"],[1.0,\"$gmHighHex\"]]"
        }
        return "{\"stops\":$stops,\"reverse\":$gmReverse,\"dither\":false}"
    }

    fun resetTextProDefaults(docW: Float) {
        textEditLayerId = null
        textDraft = "StonebridgeCraft"
        textFontFamily = "Montserrat"
        textColorHex = "#FFFFFF"
        textAlign = "center"
        textSizePx = (docW * 0.055f).coerceIn(24f, 180f)
        textNormX = 0.50f; textNormY = 0.50f
        textTracking = 0f; textLineHeight = 1.2f
        textVertical = false; textBold = false; textItalic = false
        textUnderline = false; textStrike = false
        textCaps = "normal"; textHScale = 100f; textVScale = 100f
        textWarpStyle = "none"; textWarpBend = 50f; textWarpHDist = 0f; textWarpVDist = 0f
        textRotationDeg = 0f; textOpacity = 1f; textBlendMode = "Normal"
        textStrokeWidth = 0f; textStrokeColor = "#111318"
        textShadowEnabled = false; textShadowDist = 10f; textShadowBlur = 12f; textShadowAngle = 120f; textShadowColor = "#000000"
        textGlowSize = 0f; textGlowColor = "#38BDF8"
        textGradientEnabled = false; textGradientEndColor = "#F59E0B"; textGradientAngle = 90f
        textBgEnabled = false; textBgColor = "#111318"; textBgOpacity = 0.80f; textBgRadius = 16f; textBgPadding = 18f
        textBaselineShift = 0f; textBoxWidthNorm = 0f
        textSnapX = false; textSnapY = false
    }
    var cameraTick by mutableIntStateOf(0)
    var measuredBottomBarPx by mutableFloatStateOf(0f)
    var showToolPopover by mutableStateOf(false)
    var showQuickColorWheel by mutableStateOf(false)
    var brightnessVal by mutableFloatStateOf(0f)
    var contrastVal by mutableFloatStateOf(0f)
    var hueVal by mutableFloatStateOf(0f)
    var saturationVal by mutableFloatStateOf(0f)
    var blurRadiusVal by mutableFloatStateOf(0f)
    var sharpenAmountVal by mutableFloatStateOf(0f)
    var selectedMirrorMode by mutableStateOf(MIRROR_MODES.first())
    var mirrorAxisRatio by mutableFloatStateOf(0.50f)
    var pendingQuarterTurns by mutableIntStateOf(0)
    var pendingFlipH by mutableStateOf(false)
    var pendingFlipV by mutableStateOf(false)
    var selectedCropPreset by mutableStateOf(CROP_PRESETS.first())
    var cropScale by mutableFloatStateOf(0.90f)
    // v1.8.0 Content-Aware Fill / Move / Scale state
    var cafSampling by mutableStateOf("auto")
    var cafColorAdaptation by mutableStateOf("default")
    var cafRotationAdaptation by mutableStateOf("none")
    var cafScale by mutableStateOf(false)
    var cafMirror by mutableStateOf(false)
    var cafSampleAllLayers by mutableStateOf(false)
    var cafOutput by mutableStateOf("current")
    var camMoveDx by mutableFloatStateOf(0f)
    var camMoveDy by mutableFloatStateOf(0f)
    var camMoveMode by mutableStateOf("move")
    var camMoveStructure by mutableFloatStateOf(4f)
    var camMoveColor by mutableFloatStateOf(0f)
    var camMoveScale by mutableFloatStateOf(100f)
    var camMoveRotate by mutableFloatStateOf(0f)
    var camMoveSampleAll by mutableStateOf(false)
    var casScaleX by mutableFloatStateOf(100f)
    var casScaleY by mutableFloatStateOf(100f)
    var casAmount by mutableFloatStateOf(100f)
    var casProtectSkinTones by mutableStateOf(true)
    // v1.8.2 Advanced Selection & Quick Mask state
    var crPreset by mutableStateOf("sampledColors")
    var crColorHex by mutableStateOf("#4F9CFF")
    var crFuzziness by mutableFloatStateOf(40f)
    var crInvert by mutableStateOf(false)
    var crMode by mutableStateOf("replace")
    var crSampleAll by mutableStateOf(true)
    var skyThreshold by mutableFloatStateOf(50f)
    var skySoftness by mutableFloatStateOf(50f)
    var skyMode by mutableStateOf("replace")
    var skySampleAll by mutableStateOf(true)
    var focusRange by mutableFloatStateOf(50f)
    var focusNoise by mutableFloatStateOf(0f)
    var focusMode by mutableStateOf("replace")
    var focusSampleAll by mutableStateOf(true)
    var selModifyOp by mutableStateOf("expand")
    var selModifyRadius by mutableFloatStateOf(8f)
    var selGrowTolerance by mutableFloatStateOf(32f)
    // v2.0.0 Studio v2.0 state (fixed 4+1 dock, frosted workspace sheet, command palette)
    var activeWorkspaceV2 by mutableStateOf<StudioWorkspace?>(null)
    var lastWorkspaceV2 by mutableStateOf<StudioWorkspace?>(null)
    val workspaceGridStates: Map<StudioWorkspace, androidx.compose.foundation.lazy.grid.LazyGridState> =
        StudioWorkspace.entries.associateWith { androidx.compose.foundation.lazy.grid.LazyGridState() }
    var lastDrawNonEraserTool by mutableStateOf(ActiveTool.BRUSH)
    var showSearchPaletteV2 by mutableStateOf(false)
    var pinnedToolIdsV2 by mutableStateOf<Set<String>>(ToolRegistryV2.defaultPinnedIds)
}
