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
import androidx.compose.ui.layout.onGloballyPositioned
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

@Composable
fun StonebridgeStudioScreen(
    vm: EditorViewModel = viewModel(),
    onGoHome: () -> Unit = {}
) {
    val state by vm.uiState.collectAsState()
    val layerThumbs by vm.layerThumbnails.collectAsState()
    val loupeBitmap by vm.loupeBitmap.collectAsState()
    val cinemaThumbs by vm.cinemaThumbs.collectAsState()
    val galleryThumbs by vm.galleryThumbs.collectAsState()
    val lightDistortThumbs by vm.lightDistortThumbs.collectAsState()
    val s = remember { StudioSessionState() }
    val activeLayerPrev by vm.activeLayerPreview.collectAsState()
    val context = LocalContext.current
    val density = LocalDensity.current

    val currentHandPanState by rememberUpdatedState(s.singleFingerPanMode)



    fun pushCurveGpu(y0: Float, y1: Float, y2: Float, y3: Float, y4: Float) {
        val by = (y0 * 255f).roundToInt().coerceIn(0, 255)
        val sy = (y1 * 255f).roundToInt().coerceIn(0, 255)
        val my = (y2 * 255f).roundToInt().coerceIn(0, 255)
        val hy = (y3 * 255f).roundToInt().coerceIn(0, 255)
        val wy = (y4 * 255f).roundToInt().coerceIn(0, 255)
        vm.updateProxyAdjustment("curves", """{"points":[[0,$by],[64,$sy],[128,$my],[192,$hy],[255,$wy]]}""")
    }

    // 4 normalized corners (TL, TR, BR, BL) in document space (0..1) for 60 FPS Perspective Crop

    // Independent active layer Move / Scale / Rotate state

    // Temel + İnce Ayar & Live Brush Size Preview state

    // Layer Effects (fx) state (Temel + İnce Ayar)

    LaunchedEffect(s.brushSizePreviewTick) {
        if (s.brushSizePreviewTick > 0) {
            s.showBrushSizeCircle = true
            kotlinx.coroutines.delay(550L)
            s.showBrushSizeCircle = false
        }
    }

    // Live draggable Text layer editor state
    val dockScrollState = rememberScrollState()
    val drawScrollState = rememberScrollState()
    val selectScrollState = rememberScrollState()
    val adjustScrollState = rememberScrollState()
    val cropScrollState = rememberScrollState()
    val effectScrollState = rememberScrollState()
    // v0.9.5 Camera Light (PhotoCraft native filter.cameraRaw) state

    // v0.9.5 Live Gradient Fill (gradient.fill.create) state

    // v0.9.5 Native Vector Shape (shape.create) state
    // v0.9.8 60 FPS Liquify (photocraft_algo::liquify) state
    // v0.9.9 Cinematic Looks + Vignette/Grain & Motion/Radial Blur state
    // v1.1.0 HSL 8-Channel Mixer, Paint Bucket & Posterize/Threshold state
    // v1.2.0 Filter Gallery (Artistic & Pixelate) state — starts cleanly on 'none' (Orijinal)
    val studioFonts by vm.studioFonts.collectAsState()
    val importFontLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            vm.importCustomFont(context, uri) { newFam ->
                s.textFontFamily = newFam
            }
        }
    }
    val importXmpLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            vm.importXmpPreset(context, uri) { p ->
                s.camExposureVal = p.exposure; s.camContrastVal = p.contrast
                s.camHighlightsVal = p.highlights; s.camShadowsVal = p.shadows
                s.camWhitesVal = p.whites; s.camBlacksVal = p.blacks
                s.camTempVal = p.temperature; s.camTintVal = p.tint
                s.camVibranceVal = p.vibrance; s.camSatVal = p.saturation
                s.camTextureVal = p.texture; s.camClarityVal = p.clarity
                s.camDehazeVal = p.dehaze; s.camSharpenVal = p.sharpenAmount
                s.camNoiseLumVal = p.noiseLuminance; s.camNoiseColVal = p.noiseColor
                s.camGradeShadowHue = p.gradeShadowHue; s.camGradeShadowSat = p.gradeShadowSat
                s.camGradeMidHue = p.gradeMidHue; s.camGradeMidSat = p.gradeMidSat
                s.camGradeHighHue = p.gradeHighHue; s.camGradeHighSat = p.gradeHighSat
                s.camCalRedHue = p.calRedHue; s.camCalRedSat = p.calRedSat
                s.camCalGreenHue = p.calGreenHue; s.camCalGreenSat = p.calGreenSat
                s.camCalBlueHue = p.calBlueHue; s.camCalBlueSat = p.calBlueSat
                s.camVignetteVal = p.vignetteAmount; s.camGrainVal = p.grainAmount
                vm.updateProxyCameraRawJson(buildCameraRawProJson(s))
            }
        }
    }
    val importLutLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            vm.importCubeLutFile(context, uri) { name, text ->
                s.lutFileName = name
                s.lutText = text
                vm.updateProxyColorLookup(name, text, s.lutOpacity)
            }
        }
    }
    val importAbrLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            vm.importAbrFromUri(context.contentResolver, uri)
        }
    }


    // 60 FPS GPU Mirror Effect state (zero CPU lag!)

    // 60 FPS GPU Rotate/Flip preview state inside Kırp & Çevir

    // Crop overlay state

    // HSV color picker state synced with state.colorHex
    val initHsv = remember(state.colorHex) { hexToHsv(state.colorHex) }
    var pickerH by remember(state.colorHex) { mutableFloatStateOf(initHsv[0]) }
    var pickerS by remember(state.colorHex) { mutableFloatStateOf(initHsv[1]) }
    var pickerV by remember(state.colorHex) { mutableFloatStateOf(initHsv[2]) }

    fun pushGpuMirrorPreview(mode: MirrorModeOption, ratio: Float) {
        NativeSurfaceBridge.nativeSetPreview(mode.gpuId, ratio, false, false)
        NativeSurfaceBridge.nativeRenderFrame()
    }

    fun bottomInsetPxForPanel(panel: BottomPanel): Float {
        val fallbackDp = when (panel) {
            BottomPanel.DOCK -> 112f
            BottomPanel.CURVES_LIVE -> 150f
            BottomPanel.DRAW_MENU -> 280f
            BottomPanel.CAMERA_LIGHT_LIVE, BottomPanel.LENS_CORRECTION_EDIT -> 300f
            BottomPanel.LIGHT_LIVE, BottomPanel.COLOR_LIVE, BottomPanel.LEVELS_LIVE, BottomPanel.COLOR_BAL_LIVE -> 250f
            BottomPanel.BLUR_EDIT, BottomPanel.SHARPEN_EDIT, BottomPanel.MIRROR_EDIT, BottomPanel.LAYER_TRANSFORM, BottomPanel.LAYER_FX_EDIT -> 250f
            BottomPanel.PERSPECTIVE_CROP_EDIT, BottomPanel.CROP_RATIO_EDIT, BottomPanel.TEXT_EDIT -> 220f
            else -> 190f
        }
        val fallbackPx = with(density) { fallbackDp.dp.toPx() }
        val extraPadPx = with(density) { 26.dp.toPx() }
        return if (s.measuredBottomBarPx > 10f) {
            s.measuredBottomBarPx + extraPadPx
        } else {
            fallbackPx
        }
    }

    fun fitToSafeArea(turns: Int = s.pendingQuarterTurns, panel: BottomPanel = s.activePanel) {
        val topPx = with(density) { 96.dp.toPx() }
        val botPx = bottomInsetPxForPanel(panel)
        s.canvasViewRef?.fitToDocument(turns, topPx, botPx)
        s.cameraTick++
    }

    fun clearGpuPreview() {
        s.pendingQuarterTurns = 0
        s.pendingFlipH = false
        s.pendingFlipV = false
        NativeSurfaceBridge.nativeSetPreview(0, 0.5f, false, false)
        NativeSurfaceBridge.nativeSetAdjustPreview(0f, 0f, 0f, 0f, 0f, 0f)
        NativeSurfaceBridge.nativeSetTonePreview(0f, 1f, 1f, 0f, 0f, 0f)
        s.curveOverlayExpanded = false
        NativeSurfaceBridge.nativeSetCurvePreview(0.0f, 0.25f, 0.50f, 0.75f, 1.0f, false)
        vm.endProxyPreview()
        fitToSafeArea(0, BottomPanel.DOCK)
    }

    fun clearAdjustGpuPreviewOnly() {
        NativeSurfaceBridge.nativeSetAdjustPreview(0f, 0f, 0f, 0f, 0f, 0f)
        NativeSurfaceBridge.nativeSetTonePreview(0f, 1f, 1f, 0f, 0f, 0f)
        s.curveOverlayExpanded = false
        s.curveEyedropperMode = 0
        s.hslTatActive = false
        NativeSurfaceBridge.nativeSetCurvePreview(0.0f, 0.25f, 0.50f, 0.75f, 1.0f, false)
        vm.clearHistogram()
        vm.endProxyPreview()
    }

    fun pushGpuTransformPreview(turns: Int, flipH: Boolean, flipV: Boolean) {
        NativeSurfaceBridge.nativeSetPreview(0, 0.5f, flipH, flipV)
        s.canvasViewRef?.fitToDocument(turns)
    }

    fun dismissLivePanelIfActive() {
        when (s.activePanel) {
            BottomPanel.LIGHT_LIVE, BottomPanel.COLOR_LIVE, BottomPanel.LEVELS_LIVE, BottomPanel.COLOR_BAL_LIVE, BottomPanel.CURVES_LIVE,
            BottomPanel.BW_PRO_EDIT, BottomPanel.PHOTO_FILTER_EDIT, BottomPanel.SELECTIVE_COLOR_EDIT, BottomPanel.CHANNEL_MIXER_EDIT, BottomPanel.GRADIENT_MAP_EDIT,
            BottomPanel.CAMERA_LIGHT_LIVE, BottomPanel.HSL_MIXER_LIVE, BottomPanel.POSTER_THRESH_LIVE,
            BottomPanel.CINEMA_LOOK_EDIT, BottomPanel.MOTION_BLUR_EDIT, BottomPanel.RADIAL_BLUR_EDIT,
            BottomPanel.FILTER_GALLERY_EDIT, BottomPanel.LIGHT_DISTORT_EDIT, BottomPanel.PUPPET_WARP_EDIT, BottomPanel.GRADIENT_EDIT, BottomPanel.SHAPE_EDIT -> {
                clearAdjustGpuPreviewOnly()
                s.activePanel = BottomPanel.DOCK
            }
            BottomPanel.BLUR_EDIT, BottomPanel.SHARPEN_EDIT -> {
                clearAdjustGpuPreviewOnly()
                s.activePanel = BottomPanel.DOCK
            }
            BottomPanel.MIRROR_EDIT -> {
                clearGpuPreview()
                s.activePanel = BottomPanel.DOCK
            }
            BottomPanel.LAYER_TRANSFORM -> {
                vm.clearActiveLayerPreview()
                s.activePanel = BottomPanel.DOCK
            }
            BottomPanel.CROP_MENU -> {
                if (s.pendingQuarterTurns != 0 || s.pendingFlipH || s.pendingFlipV) {
                    clearGpuPreview()
                }
                s.activePanel = BottomPanel.DOCK
            }
            else -> Unit
        }
    }

    BackHandler(enabled = true) {
        if (s.showSearchPaletteV2) {
            s.showSearchPaletteV2 = false
        } else if (s.activeWorkspaceV2 != null) {
            s.activeWorkspaceV2 = null
        } else if (s.showLayers) {
            s.showLayers = false
        } else if (s.activePanel != BottomPanel.DOCK) {
            when (s.activePanel) {
                BottomPanel.LIGHT_LIVE, BottomPanel.COLOR_LIVE, BottomPanel.LEVELS_LIVE, BottomPanel.COLOR_BAL_LIVE, BottomPanel.CURVES_LIVE,
                BottomPanel.BW_PRO_EDIT, BottomPanel.PHOTO_FILTER_EDIT, BottomPanel.SELECTIVE_COLOR_EDIT, BottomPanel.CHANNEL_MIXER_EDIT, BottomPanel.GRADIENT_MAP_EDIT -> {
                    s.curveOverlayExpanded = false; clearAdjustGpuPreviewOnly(); vm.endProxyPreview()
                    s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2
                }
                BottomPanel.BLUR_EDIT, BottomPanel.SHARPEN_EDIT -> {
                    s.curveOverlayExpanded = false; clearAdjustGpuPreviewOnly(); vm.endProxyPreview()
                    s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2
                }
                BottomPanel.MIRROR_EDIT -> {
                    clearGpuPreview()
                    s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2
                }
                BottomPanel.CROP_MENU -> {
                    if (s.pendingQuarterTurns != 0 || s.pendingFlipH || s.pendingFlipV) {
                        clearGpuPreview()
                    }
                    s.activePanel = BottomPanel.DOCK
                }
                BottomPanel.CROP_RATIO_EDIT, BottomPanel.PERSPECTIVE_CROP_EDIT -> { s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2 }
                BottomPanel.IMAGE_RESIZE_EDIT -> { vm.endProxyPreview(); s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2 }
                BottomPanel.PUPPET_WARP_EDIT -> { vm.endProxyPreview(); s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2 }
                BottomPanel.LAYER_TRANSFORM -> { vm.clearActiveLayerPreview(); s.activePanel = BottomPanel.DOCK }
                BottomPanel.LAYER_FX_EDIT, BottomPanel.BLEND_IF_EDIT, BottomPanel.GRADIENT_EDIT, BottomPanel.SHAPE_EDIT -> { vm.endProxyPreview(); s.activePanel = BottomPanel.DOCK }
                BottomPanel.CAMERA_LIGHT_LIVE, BottomPanel.LENS_CORRECTION_EDIT, BottomPanel.LUT_IMPORT_EDIT -> { s.curveOverlayExpanded = false; clearAdjustGpuPreviewOnly(); vm.endProxyPreview(); s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2 }
                BottomPanel.LIQUIFY_EDIT -> { vm.cancelLiquify(); s.liquifyCursorScreen = null; s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2 }
                BottomPanel.AUTO_ENHANCE_MENU -> { s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2 }
                BottomPanel.CINEMA_LOOK_EDIT -> { vm.endProxyPreview(); s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2 }
                BottomPanel.MOTION_BLUR_EDIT, BottomPanel.RADIAL_BLUR_EDIT, BottomPanel.FILTER_GALLERY_EDIT, BottomPanel.LIGHT_DISTORT_EDIT -> { vm.endProxyPreview(); s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2 }
                BottomPanel.QUICK_ACTIONS_MENU -> s.activePanel = BottomPanel.DOCK
                BottomPanel.HSL_MIXER_LIVE, BottomPanel.POSTER_THRESH_LIVE -> { vm.endProxyPreview(); s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2 }
                BottomPanel.CONTENT_AWARE_FILL_EDIT -> { vm.endProxyPreview(); s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2 }
                BottomPanel.CONTENT_AWARE_MOVE_EDIT -> { vm.endProxyPreview(); s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2 }
                BottomPanel.CONTENT_AWARE_SCALE_EDIT -> { vm.endProxyPreview(); s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2 }
                BottomPanel.SELECT_COLOR_RANGE_EDIT, BottomPanel.SELECT_SKY_EDIT, BottomPanel.SELECT_FOCUS_AREA_EDIT -> {
                    vm.endProxyPreview(); s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2
                }
                BottomPanel.SELECT_MODIFY_EDIT -> { vm.endProxyPreview(); s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2 }
                BottomPanel.TEXT_EDIT -> { vm.endProxyPreview(); s.activePanel = BottomPanel.DOCK }
                BottomPanel.SELECT_MENU, BottomPanel.DRAW_MENU, BottomPanel.RETOUCH_MENU, BottomPanel.LAYER_MASK_EDIT -> {
                    vm.setTool(ActiveTool.NONE); s.activePanel = BottomPanel.DOCK
                }
                else -> { vm.setTool(ActiveTool.NONE); s.activePanel = BottomPanel.DOCK }
            }
        } else {
            s.showExitSaveDialog = true
        }
    }


    LaunchedEffect(
        s.activePanel, s.textEditLayerId, s.textDraft, s.textNormX, s.textNormY, s.textSizePx,
        s.textColorHex, s.textAlign, s.textFontFamily, s.textBold, s.textItalic,
        s.textUnderline, s.textStrike, s.textCaps, s.textHScale, s.textVScale,
        s.textTracking, s.textLineHeight, s.textVertical,
        s.textWarpStyle, s.textWarpBend, s.textWarpHDist, s.textWarpVDist,
        s.textRotationDeg, s.textOpacity, s.textBlendMode,
        s.textStrokeWidth, s.textStrokeColor,
        s.textShadowEnabled, s.textShadowDist, s.textShadowBlur, s.textShadowAngle, s.textShadowColor,
        s.textGlowSize, s.textGlowColor,
        s.textGradientEnabled, s.textGradientEndColor, s.textGradientAngle,
        s.textBgEnabled, s.textBgColor, s.textBgOpacity, s.textBgRadius, s.textBgPadding,
        s.textBaselineShift, s.textBoxWidthNorm
    ) {
        if (s.activePanel == BottomPanel.TEXT_EDIT) {
            val docW = (state.docInfo?.width?.toInt() ?: 2048).toFloat()
            val docH = (state.docInfo?.height?.toInt() ?: 2048).toFloat()
            val finalTxt = if (!s.textVertical && s.textBoxWidthNorm >= 0.05f) {
                val maxChars = ((docW * s.textBoxWidthNorm) / (s.textSizePx * 0.58f).coerceAtLeast(10f)).roundToInt().coerceIn(4, 80)
                wrapTextForBox(s.textDraft, maxChars)
            } else s.textDraft
            val extras = uniffi.kastamonushop_ffi.TextProExtras(
                rotationDeg = s.textRotationDeg,
                opacity = s.textOpacity,
                blendMode = s.textBlendMode,
                strokeWidth = s.textStrokeWidth,
                strokeColor = s.textStrokeColor,
                shadowEnabled = s.textShadowEnabled,
                shadowDistance = s.textShadowDist,
                shadowBlur = s.textShadowBlur,
                shadowAngle = s.textShadowAngle,
                shadowColor = s.textShadowColor,
                glowSize = s.textGlowSize,
                glowColor = s.textGlowColor,
                gradientEnabled = s.textGradientEnabled,
                gradientEndColor = s.textGradientEndColor,
                gradientAngle = s.textGradientAngle,
                bgEnabled = s.textBgEnabled,
                bgColor = s.textBgColor,
                bgOpacity = s.textBgOpacity,
                bgRadius = s.textBgRadius,
                bgPadding = s.textBgPadding,
                baselineShift = s.textBaselineShift,
                boxWidthNorm = s.textBoxWidthNorm
            )
            vm.updateProxyTextProPreview(
                editLayerId = s.textEditLayerId,
                text = finalTxt,
                x = docW * s.textNormX,
                y = docH * s.textNormY,
                sizePx = s.textSizePx,
                colorHex = s.textColorHex,
                align = s.textAlign,
                fontFamily = s.textFontFamily,
                bold = s.textBold,
                italic = s.textItalic,
                underline = s.textUnderline,
                strikethrough = s.textStrike,
                caps = s.textCaps,
                hScale = s.textHScale,
                vScale = s.textVScale,
                tracking = s.textTracking,
                lineHeight = s.textLineHeight,
                vertical = s.textVertical,
                warpStyle = s.textWarpStyle,
                warpBend = s.textWarpBend,
                warpHDist = s.textWarpHDist,
                warpVDist = s.textWarpVDist,
                extras = extras
            )
        }
    }

    LaunchedEffect(state.fitCameraSignal, s.canvasViewRef) {
        if (state.fitCameraSignal > 0 && s.canvasViewRef != null) {
            fitToSafeArea(0, s.activePanel)
        }
    }

    LaunchedEffect(s.activePanel) {
        if (s.activePanel != BottomPanel.DOCK && state.compareOriginal) {
            vm.dismissCompareOriginal()
        }
        val isProxyPanel = s.activePanel == BottomPanel.LIGHT_LIVE ||
            s.activePanel == BottomPanel.COLOR_LIVE ||
            s.activePanel == BottomPanel.LEVELS_LIVE ||
            s.activePanel == BottomPanel.COLOR_BAL_LIVE ||
            s.activePanel == BottomPanel.CURVES_LIVE ||
            s.activePanel == BottomPanel.BLUR_EDIT ||
            s.activePanel == BottomPanel.SHARPEN_EDIT ||
            s.activePanel == BottomPanel.LAYER_FX_EDIT ||
            s.activePanel == BottomPanel.CAMERA_LIGHT_LIVE ||
            s.activePanel == BottomPanel.GRADIENT_EDIT ||
            s.activePanel == BottomPanel.SHAPE_EDIT ||
            s.activePanel == BottomPanel.CINEMA_LOOK_EDIT ||
            s.activePanel == BottomPanel.MOTION_BLUR_EDIT ||
            s.activePanel == BottomPanel.RADIAL_BLUR_EDIT ||
            s.activePanel == BottomPanel.HSL_MIXER_LIVE ||
            s.activePanel == BottomPanel.POSTER_THRESH_LIVE ||
            s.activePanel == BottomPanel.IMAGE_RESIZE_EDIT ||
            s.activePanel == BottomPanel.SELECT_MODIFY_EDIT ||
            s.activePanel == BottomPanel.CONTENT_AWARE_MOVE_EDIT ||
            s.activePanel == BottomPanel.FILTER_GALLERY_EDIT ||
            s.activePanel == BottomPanel.LIGHT_DISTORT_EDIT ||
            s.activePanel == BottomPanel.TEXT_EDIT ||
            s.activePanel == BottomPanel.PUPPET_WARP_EDIT || s.activePanel == BottomPanel.BLEND_IF_EDIT || s.activePanel == BottomPanel.LENS_CORRECTION_EDIT || s.activePanel == BottomPanel.LUT_IMPORT_EDIT || s.activePanel == BottomPanel.CONTENT_AWARE_FILL_EDIT || s.activePanel == BottomPanel.CONTENT_AWARE_SCALE_EDIT || s.activePanel == BottomPanel.SELECT_COLOR_RANGE_EDIT || s.activePanel == BottomPanel.SELECT_SKY_EDIT || s.activePanel == BottomPanel.SELECT_FOCUS_AREA_EDIT
        if (!isProxyPanel) {
            vm.endProxyPreview()
        }
        if (s.activePanel != BottomPanel.LIQUIFY_EDIT) {
            vm.cancelLiquify()
            s.liquifyCursorScreen = null
        }
    }

    LaunchedEffect(s.activePanel, state.activeTool, s.measuredBottomBarPx, s.curveOverlayExpanded, s.canvasViewRef) {
        if (s.canvasViewRef != null) {
            when (s.activePanel) {
                BottomPanel.DOCK,
                BottomPanel.DRAW_MENU,
                BottomPanel.RETOUCH_MENU,
                BottomPanel.SELECT_MENU,
                BottomPanel.ADJUST_MENU,
                BottomPanel.CROP_MENU,
                BottomPanel.FILTER_MENU,
                BottomPanel.EFFECT_MENU,
                BottomPanel.LAYER_MASK_EDIT,
                BottomPanel.BLEND_IF_EDIT,
                BottomPanel.LENS_CORRECTION_EDIT,
                BottomPanel.LUT_IMPORT_EDIT,
                BottomPanel.CURVES_LIVE,
                BottomPanel.PERSPECTIVE_CROP_EDIT,
                BottomPanel.CROP_RATIO_EDIT,
                BottomPanel.MIRROR_EDIT,
                BottomPanel.LIGHT_LIVE,
                BottomPanel.COLOR_LIVE,
                BottomPanel.LEVELS_LIVE,
                BottomPanel.COLOR_BAL_LIVE,
                BottomPanel.BLUR_EDIT,
                BottomPanel.SHARPEN_EDIT,
                BottomPanel.LAYER_TRANSFORM,
                BottomPanel.LAYER_FX_EDIT,
                BottomPanel.CAMERA_LIGHT_LIVE,
                BottomPanel.GRADIENT_EDIT,
                BottomPanel.SHAPE_EDIT,
                BottomPanel.LIQUIFY_EDIT,
                BottomPanel.CINEMA_LOOK_EDIT,
                BottomPanel.MOTION_BLUR_EDIT,
                BottomPanel.RADIAL_BLUR_EDIT,
                BottomPanel.HSL_MIXER_LIVE,
                BottomPanel.POSTER_THRESH_LIVE,
                BottomPanel.FILTER_GALLERY_EDIT,
                BottomPanel.DOCK -> fitToSafeArea(s.pendingQuarterTurns, s.activePanel)
                else -> Unit
            }
        }
    }

    val openDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            dismissLivePanelIfActive()
            vm.openDocumentFromUri(context.contentResolver, uri, asLayer = s.openAsLayer)
        }
    }

    val imageChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { res ->
        val uri = if (res.resultCode == Activity.RESULT_OK) res.data?.data else null
        if (uri != null) {
            dismissLivePanelIfActive()
            vm.openDocumentFromUri(context.contentResolver, uri, asLayer = s.openAsLayer)
        }
    }

    val exportDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        if (uri != null) {
            vm.exportDocumentToUri(context, uri, s.pendingExportFormat)
        }
    }

    // Keep latest mirror state accessible to VulkanCanvasView touch callbacks
    val currentPanelState by rememberUpdatedState(s.activePanel)
    val currentMirrorModeState by rememberUpdatedState(s.selectedMirrorMode)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                VulkanCanvasView(
                    context = ctx,
                    getDocSize = {
                        val d = vm.uiState.value.docInfo
                        (d?.width?.toInt() ?: 2048) to (d?.height?.toInt() ?: 2048)
                    },
                    getBrushSize = { vm.uiState.value.brushSize },
                    isDrawingActive = {
                        val st = vm.uiState.value
                        val t = st.activeTool
                        t == ActiveTool.BRUSH || t == ActiveTool.PENCIL || t == ActiveTool.ERASER || t == ActiveTool.BG_ERASER ||
                            t == ActiveTool.MIXER_BRUSH || t == ActiveTool.COLOR_REPLACE ||
                            t == ActiveTool.SMUDGE || t == ActiveTool.DODGE || t == ActiveTool.BURN ||
                            t == ActiveTool.SPONGE_SAT || t == ActiveTool.SPONGE_DESAT ||
                            t == ActiveTool.BLUR_BRUSH || t == ActiveTool.SHARPEN_BRUSH ||
                            (t == ActiveTool.CLONE_STAMP && !st.pickingCloneSource)
                    },
                    isRetouchActive = {
                        val t = vm.uiState.value.activeTool
                        t == ActiveTool.SPOT_HEAL ||
                            t == ActiveTool.REMOVE_TOOL ||
                            t == ActiveTool.PATCH ||
                            t == ActiveTool.SELECT_QUICK ||
                            t == ActiveTool.SELECT_BRUSH_ADD ||
                            t == ActiveTool.SELECT_BRUSH_SUB ||
                            t == ActiveTool.SELECT_LASSO ||
                            t == ActiveTool.SELECT_MAGNETIC_LASSO ||
                            t == ActiveTool.ADJUST_BRUSH ||
                            t == ActiveTool.HISTORY_BRUSH ||
                            t == ActiveTool.ART_HISTORY_BRUSH ||
                            (t == ActiveTool.HEALING_BRUSH && !vm.uiState.value.pickingCloneSource)
                    },
                    isEyedropperActive = { vm.uiState.value.activeTool == ActiveTool.EYEDROPPER },
                    isRulerEnabled = { vm.uiState.value.rulerEnabled },
                    isMirrorDragActive = { currentPanelState == BottomPanel.MIRROR_EDIT },
                    isMirrorVerticalAxis = { currentMirrorModeState.isVerticalAxis },
                    isTextDragActive = { currentPanelState == BottomPanel.TEXT_EDIT },
                    isSelectRectActive = { vm.uiState.value.activeTool == ActiveTool.SELECT_RECT },
                    isSelectWandActive = { vm.uiState.value.activeTool == ActiveTool.SELECT_WAND },
                    isBucketActive = { val t = vm.uiState.value.activeTool; t == ActiveTool.BUCKET || t == ActiveTool.RED_EYE || t == ActiveTool.MAGIC_ERASER },
                    isCloneSourcePickActive = {
                        val st = vm.uiState.value
                        (st.activeTool == ActiveTool.CLONE_STAMP || st.activeTool == ActiveTool.HEALING_BRUSH) && st.pickingCloneSource
                    },
                    isHandPanActive = { currentHandPanState },
                    onStrokeBegin = { sample ->
                        if (s.showToolPopover) s.showToolPopover = false
                        vm.beginLiveStroke(sample)
                    },
                    onStrokeMove = { samples -> vm.pushLiveStroke(samples) },
                    onStrokeEnd = { vm.finishLiveStroke() },
                    onStrokeCancel = { vm.cancelLiveStroke() },
                    onRetouchPreview = { pos ->
                        s.retouchCursor = pos
                        if (pos == null) {
                            if (s.retouchTrailPoints.isNotEmpty()) s.retouchTrailPoints.clear()
                        } else {
                            s.retouchTrailPoints.add(Offset(pos.first, pos.second))
                        }
                    },
                    onRetouchCommit = { samples ->
                        val t = vm.uiState.value.activeTool
                        if (t == ActiveTool.SELECT_LASSO || t == ActiveTool.SELECT_MAGNETIC_LASSO) {
                            vm.applyLassoSelection(samples, magnetic = (t == ActiveTool.SELECT_MAGNETIC_LASSO))
                        } else if (t == ActiveTool.SELECT_QUICK || t == ActiveTool.SELECT_BRUSH_ADD || t == ActiveTool.SELECT_BRUSH_SUB) {
                            vm.applySelectionStroke(samples)
                        } else if (t == ActiveTool.PENCIL) {
                            vm.applyPencilStroke(samples)
                        } else if (t == ActiveTool.BG_ERASER) {
                            vm.applyBackgroundEraser(samples)
                        } else if (t == ActiveTool.ADJUST_BRUSH) {
                            vm.applyAdjustmentBrush(samples)
                        } else if (t == ActiveTool.HISTORY_BRUSH) {
                            vm.applyHistoryBrush(samples)
                        } else if (t == ActiveTool.ART_HISTORY_BRUSH) {
                            vm.applyArtHistoryBrush(samples)
                        } else if (t == ActiveTool.HEALING_BRUSH) {
                            vm.applyHealingBrush(samples)
                        } else if (t == ActiveTool.REMOVE_TOOL) {
                            vm.applyRemoveTool(samples)
                        } else if (t == ActiveTool.PATCH && samples.size >= 2) {
                            val dx = (samples.last().x - samples.first().x).roundToInt()
                            val dy = (samples.last().y - samples.first().y).roundToInt()
                            vm.applyPatchTool(dx, dy)
                        } else {
                            vm.applyRetouchStroke(samples)
                        }
                    },
                    onRulerPreview = { preview -> s.rulerPreview = preview },
                    onRulerStrokeCommit = { samples -> vm.commitRulerStroke(samples) },
                    onEyedropperSample = { dx, dy -> vm.sampleColorAt(dx, dy) },
                    onMirrorAxisDrag = { ratio ->
                        s.mirrorAxisRatio = ratio
                        pushGpuMirrorPreview(currentMirrorModeState, ratio)
                    },
                    onTextPositionDrag = { nx, ny ->
                        s.textNormX = nx
                        s.textNormY = ny
                    },
                    onSelectRectPreview = { arr -> s.selectPreviewRect = arr },
                    onSelectRectCommit = { rx, ry, rw, rh -> vm.selectRect(rx, ry, rw, rh) },
                    onSelectWandTap = { dx, dy -> vm.selectMagicWand(dx, dy) },
                    onBucketTap = { dx, dy ->
                        if (s.showToolPopover) s.showToolPopover = false
                        when (vm.uiState.value.activeTool) {
                            ActiveTool.RED_EYE -> vm.applyRedEye(dx, dy)
                            ActiveTool.MAGIC_ERASER -> vm.applyMagicEraser(dx, dy)
                            else -> vm.applyPaintBucket(dx, dy, s.bucketTolerance.roundToInt(), s.bucketContiguous)
                        }
                    },
                    onTwoFingerUndo = {
                        if (s.activePanel == BottomPanel.PUPPET_WARP_EDIT && s.puppetUndoStack.isNotEmpty()) {
                            val prev = s.puppetUndoStack.last()
                            s.puppetRedoStack = s.puppetRedoStack + listOf(s.puppetPins)
                            s.puppetUndoStack = s.puppetUndoStack.dropLast(1)
                            s.puppetPins = prev
                            s.puppetSelectedIdx = s.puppetSelectedIdx.coerceIn(-1, prev.lastIndex)
                            if (prev.isEmpty()) vm.resetProxyPreview() else vm.updateProxyPuppetWarp(prev, s.puppetMode, s.puppetDensity, s.puppetExpansion)
                            if (s.puppetShowMesh) vm.fetchPuppetMeshSegments(prev, s.puppetMode, s.puppetDensity, s.puppetExpansion) { s.puppetMeshSegments = it }
                        } else {
                            vm.undo()
                        }
                    },
                    onThreeFingerRedo = {
                        if (s.activePanel == BottomPanel.PUPPET_WARP_EDIT && s.puppetRedoStack.isNotEmpty()) {
                            val next = s.puppetRedoStack.last()
                            s.puppetUndoStack = s.puppetUndoStack + listOf(s.puppetPins)
                            s.puppetRedoStack = s.puppetRedoStack.dropLast(1)
                            s.puppetPins = next
                            s.puppetSelectedIdx = s.puppetSelectedIdx.coerceIn(-1, next.lastIndex)
                            if (next.isEmpty()) vm.resetProxyPreview() else vm.updateProxyPuppetWarp(next, s.puppetMode, s.puppetDensity, s.puppetExpansion)
                            if (s.puppetShowMesh) vm.fetchPuppetMeshSegments(next, s.puppetMode, s.puppetDensity, s.puppetExpansion) { s.puppetMeshSegments = it }
                        } else {
                            vm.redo()
                        }
                    },
                    onCloneSourcePick = { dx, dy -> vm.setCloneSource(dx, dy) },
                    onLoupePointer = { sx, sy, dx, dy, active ->
                        val tool = vm.uiState.value.activeTool
                        val needsLoupe = tool == ActiveTool.EYEDROPPER ||
                            tool == ActiveTool.CLONE_STAMP ||
                            (vm.uiState.value.brushSize <= 24f && (
                                tool == ActiveTool.BRUSH || tool == ActiveTool.ERASER ||
                                tool == ActiveTool.SPOT_HEAL || tool == ActiveTool.SMUDGE ||
                                tool == ActiveTool.DODGE || tool == ActiveTool.BURN ||
                                tool == ActiveTool.SELECT_BRUSH_ADD || tool == ActiveTool.SELECT_BRUSH_SUB
                            ))
                        if (active && needsLoupe) {
                            s.loupeScreenPos = Offset(sx, sy)
                            val halfExt = if (tool == ActiveTool.EYEDROPPER) 12f else (vm.uiState.value.brushSize * 1.15f).coerceIn(12f, 44f)
                            vm.updateLoupe(dx, dy, halfExt)
                        } else if (s.loupeScreenPos != null) {
                            s.loupeScreenPos = null
                            vm.hideLoupe()
                        }
                    },
                    onCameraChanged = { s.cameraTick++ }
                ).also { s.canvasViewRef = it }
            }
        )

        // Live Selection Box & Active Selection Indicator
        s.selectPreviewRect?.let { r ->
            Canvas(modifier = Modifier.fillMaxSize()) {
                val left = min(r[0], r[2])
                val top = min(r[1], r[3])
                val w = kotlin.math.abs(r[2] - r[0])
                val h = kotlin.math.abs(r[3] - r[1])
                if (state.selectEllipse) {
                    drawOval(
                        color = Color(0xFF4F9CFF),
                        topLeft = Offset(left, top),
                        size = Size(w, h),
                        style = Stroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)))
                    )
                } else {
                    drawRect(
                        color = Color(0xFF4F9CFF),
                        topLeft = Offset(left, top),
                        size = Size(w, h),
                        style = Stroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)))
                    )
                }
            }
        }
        // Active selection pixel mask & organic contour are rendered at 60 FPS directly in blit.wgsl
        // Clone Stamp Source Crosshair
        if (state.activeTool == ActiveTool.CLONE_STAMP || state.activeTool == ActiveTool.HEALING_BRUSH) {
            state.cloneSourceDoc?.let { (cx, cy) ->
                val view = s.canvasViewRef
                if (view != null) {
                    val _tick = s.cameraTick
                    val (sx, sy) = view.docToScreen(cx, cy)
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(color = Color(0xFF2ED573), radius = 14f, center = Offset(sx, sy), style = Stroke(width = 2.5f))
                        drawLine(Color(0xFF2ED573), Offset(sx - 20f, sy), Offset(sx + 20f, sy), strokeWidth = 2.5f)
                        drawLine(Color(0xFF2ED573), Offset(sx, sy - 20f), Offset(sx, sy + 20f), strokeWidth = 2.5f)
                    }
                }
            }
        }

        // Live Brush Size Preview Circle in center of screen when dragging Boyut slider
        if (s.showBrushSizeCircle) {
            val z = s.canvasViewRef?.getCurrentZoom() ?: 1f
            val rPx = (state.brushSize * z * 0.5f).coerceIn(4f, 260f)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val c = Offset(size.width * 0.5f, size.height * 0.42f)
                drawCircle(color = Color(0x334F9CFF), radius = rPx, center = c)
                drawCircle(color = Color.White, radius = rPx, center = c, style = Stroke(width = 2.5f))
            }
        }

        // Live Ruler Straight-Line Guide Overlay
        s.rulerPreview?.let { line ->
            Canvas(modifier = Modifier.fillMaxSize()) {
                val guideColor = if (line.isSnapped) Color(0xFF2ED573) else Color(0xFF4F9CFF)
                drawLine(
                    color = guideColor,
                    start = Offset(line.startX, line.startY),
                    end = Offset(line.endX, line.endY),
                    strokeWidth = 4f,
                    pathEffect = if (line.isSnapped) null else PathEffect.dashPathEffect(floatArrayOf(16f, 10f))
                )
                drawCircle(color = Color.White, radius = 8f, center = Offset(line.startX, line.startY))
                drawCircle(color = guideColor, radius = 8f, center = Offset(line.endX, line.endY))
            }
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 68.dp),
                shape = RoundedCornerShape(14.dp),
                color = if (line.isSnapped) Color(0xDD1E462F) else Color(0xDD1A2333)
            ) {
                Text(
                    text = if (line.isSnapped) "Cetvel · ${line.angleDeg}° (Hizalandı)" else "Cetvel · ${line.angleDeg}°",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }
        }

        // 60 FPS Dashed Mirror Axis Overlay with Finger Drag Handle
        if (s.activePanel == BottomPanel.MIRROR_EDIT) {
            val docW = (state.docInfo?.width?.toInt() ?: 2048).toFloat()
            val docH = (state.docInfo?.height?.toInt() ?: 2048).toFloat()
            val viewRef = s.canvasViewRef
            val tick = s.cameraTick
            if (viewRef != null && tick >= 0) {
                val (p0, p1) = if (s.selectedMirrorMode.isVerticalAxis) {
                    val ax = docW * s.mirrorAxisRatio
                    viewRef.docToScreen(ax, 0f) to viewRef.docToScreen(ax, docH)
                } else {
                    val ay = docH * s.mirrorAxisRatio
                    viewRef.docToScreen(0f, ay) to viewRef.docToScreen(docW, ay)
                }
                val midX = (p0.first + p1.first) * 0.5f
                val midY = (p0.second + p1.second) * 0.5f
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val dash = PathEffect.dashPathEffect(floatArrayOf(18f, 12f))
                    drawLine(
                        color = Color(0x99000000),
                        start = Offset(p0.first, p0.second),
                        end = Offset(p1.first, p1.second),
                        strokeWidth = 6f,
                        pathEffect = dash
                    )
                    drawLine(
                        color = Color.White,
                        start = Offset(p0.first, p0.second),
                        end = Offset(p1.first, p1.second),
                        strokeWidth = 3f,
                        pathEffect = dash
                    )
                    // Center drag handle ring so user knows they can drag right on the photo
                    drawCircle(color = Color(0xCC19191E), radius = 16f, center = Offset(midX, midY))
                    drawCircle(color = Color.White, radius = 16f, center = Offset(midX, midY), style = Stroke(width = 3f))
                    drawCircle(color = Color(0xFF4F9CFF), radius = 6f, center = Offset(midX, midY))
                }
            }
        }

        // Isolated 60 FPS Live Stroke Ribbon Trail (does NOT trigger StudioScreen recomposition!)
        RetouchTrailOverlay(
            trailPoints = s.retouchTrailPoints,
            activeTool = state.activeTool,
            brushSize = state.brushSize,
            zoomProvider = { s.canvasViewRef?.getCurrentZoom() ?: 1f }
        )
        if (state.symmetryMode != "off") {
            SymmetryAxisOverlay(
                mode = state.symmetryMode,
                canvasViewRef = s.canvasViewRef,
                docW = (state.docInfo?.width?.toInt() ?: 2048).toFloat(),
                docH = (state.docInfo?.height?.toInt() ?: 2048).toFloat(),
                cameraTick = s.cameraTick
            )
        }

        // v1.0.0 Interactive 4-Corner Draggable & Movable Normal Crop Overlay
        if (s.activePanel == BottomPanel.CROP_RATIO_EDIT) {
            val docW = (state.docInfo?.width?.toInt() ?: 2048).toFloat()
            val docH = (state.docInfo?.height?.toInt() ?: 2048).toFloat()
            val viewRef = s.canvasViewRef
            val tick = s.cameraTick
            if (viewRef != null && tick >= 0) {
                val (sx0, sy0) = viewRef.docToScreen(docW * s.cropNormL, docH * s.cropNormT)
                val (sx1, sy1) = viewRef.docToScreen(docW * s.cropNormR, docH * s.cropNormB)
                val left = min(sx0, sx1)
                val top = min(sy0, sy1)
                val boxW = abs(sx1 - sx0)
                val boxH = abs(sy1 - sy0)
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (s.singleFingerPanMode) Modifier else Modifier.pointerInput(docW, docH, s.cropFreeMode, s.selectedCropPreset) {
                            awaitPointerEventScope {
                                while (true) {
                                    val firstDown = awaitFirstDown(requireUnconsumed = false)
                                    val pos0 = firstDown.position
                                    val (curX0, curY0) = viewRef.docToScreen(docW * s.cropNormL, docH * s.cropNormT)
                                    val (curX1, curY1) = viewRef.docToScreen(docW * s.cropNormR, docH * s.cropNormB)
                                    val cL = min(curX0, curX1)
                                    val cT = min(curY0, curY1)
                                    val cR = kotlin.math.max(curX0, curX1)
                                    val cB = kotlin.math.max(curY0, curY1)
                                    val corners = listOf(Offset(cL, cT), Offset(cR, cT), Offset(cR, cB), Offset(cL, cB))
                                    var bestIdx = -1
                                    var bestDist = 72f
                                    corners.forEachIndexed { i, c ->
                                        val d = kotlin.math.hypot((pos0.x - c.x).toDouble(), (pos0.y - c.y).toDouble()).toFloat()
                                        if (d < bestDist) { bestDist = d; bestIdx = i }
                                    }
                                    val (dx0, dy0) = viewRef.screenToDoc(pos0.x, pos0.y)
                                    var lastDocX = dx0
                                    var lastDocY = dy0
                                    var dragTarget = when {
                                        bestIdx in 0..3 -> bestIdx
                                        pos0.x in cL..cR && pos0.y in cT..cB -> 4
                                        else -> -1
                                    }
                                    var pinchActive = false
                                    var prevDist = 0f
                                    var prevCentroid = Offset.Zero

                                    while (true) {
                                        val ev = awaitPointerEvent()
                                        val pressed = ev.changes.filter { it.pressed }
                                        if (pressed.isEmpty()) {
                                            s.loupeScreenPos = null
                                            vm.hideLoupe()
                                            break
                                        }
                                        if (pressed.size >= 2) {
                                            s.loupeScreenPos = null
                                            vm.hideLoupe()
                                            val p0 = pressed[0].position
                                            val p1 = pressed[1].position
                                            val dist = kotlin.math.hypot((p1.x - p0.x).toDouble(), (p1.y - p0.y).toDouble()).toFloat().coerceAtLeast(1f)
                                            val centroid = Offset((p0.x + p1.x) * 0.5f, (p0.y + p1.y) * 0.5f)
                                            if (pinchActive && prevDist > 1f) {
                                                val zf = (dist / prevDist).coerceIn(0.85f, 1.18f)
                                                viewRef.applyExternalPinchPan(zf, centroid.x - prevCentroid.x, centroid.y - prevCentroid.y)
                                            }
                                            prevDist = dist
                                            prevCentroid = centroid
                                            pinchActive = true
                                            pressed.forEach { it.consume() }
                                        } else if (!pinchActive && dragTarget != -1) {
                                            val ch = pressed[0]
                                            ch.consume()
                                            val (dx, dy) = viewRef.screenToDoc(ch.position.x, ch.position.y)
                                            val minFrac = 0.08f
                                            val lockedRatio = if (s.cropFreeMode) null else (s.selectedCropPreset.ratio ?: (docW / docH.coerceAtLeast(1f)))
                                            if (dragTarget == 4) {
                                                val dnx = (dx - lastDocX) / docW
                                                val dny = (dy - lastDocY) / docH
                                                lastDocX = dx
                                                lastDocY = dy
                                                val wFrac = s.cropNormR - s.cropNormL
                                                val hFrac = s.cropNormB - s.cropNormT
                                                val nl = (s.cropNormL + dnx).coerceIn(0f, (1f - wFrac).coerceAtLeast(0f))
                                                val nt = (s.cropNormT + dny).coerceIn(0f, (1f - hFrac).coerceAtLeast(0f))
                                                s.cropNormL = nl; s.cropNormR = nl + wFrac
                                                s.cropNormT = nt; s.cropNormB = nt + hFrac
                                            } else {
                                                val nx = (dx / docW).coerceIn(0f, 1f)
                                                val ny = (dy / docH).coerceIn(0f, 1f)
                                                if (lockedRatio == null) {
                                                    when (dragTarget) {
                                                        0 -> { s.cropNormL = nx.coerceAtMost(s.cropNormR - minFrac); s.cropNormT = ny.coerceAtMost(s.cropNormB - minFrac) }
                                                        1 -> { s.cropNormR = nx.coerceAtLeast(s.cropNormL + minFrac); s.cropNormT = ny.coerceAtMost(s.cropNormB - minFrac) }
                                                        2 -> { s.cropNormR = nx.coerceAtLeast(s.cropNormL + minFrac); s.cropNormB = ny.coerceAtLeast(s.cropNormT + minFrac) }
                                                        3 -> { s.cropNormL = nx.coerceAtMost(s.cropNormR - minFrac); s.cropNormB = ny.coerceAtLeast(s.cropNormT + minFrac) }
                                                    }
                                                } else {
                                                    val anchorX = if (dragTarget == 0 || dragTarget == 3) s.cropNormR * docW else s.cropNormL * docW
                                                    val anchorY = if (dragTarget == 0 || dragTarget == 1) s.cropNormB * docH else s.cropNormT * docH
                                                    val rawW = abs(dx - anchorX).coerceAtLeast(docW * minFrac)
                                                    val rawH = (rawW / lockedRatio).coerceAtLeast(docH * minFrac)
                                                    val maxW = if (dragTarget == 0 || dragTarget == 3) anchorX else (docW - anchorX)
                                                    val maxH = if (dragTarget == 0 || dragTarget == 1) anchorY else (docH - anchorY)
                                                    val scaleFit = min(1f, min(maxW / rawW.coerceAtLeast(1f), maxH / rawH.coerceAtLeast(1f)))
                                                    val fw = (rawW * scaleFit) / docW
                                                    val fh = (rawH * scaleFit) / docH
                                                    when (dragTarget) {
                                                        0 -> { s.cropNormL = (s.cropNormR - fw).coerceIn(0f, 1f); s.cropNormT = (s.cropNormB - fh).coerceIn(0f, 1f) }
                                                        1 -> { s.cropNormR = (s.cropNormL + fw).coerceIn(0f, 1f); s.cropNormT = (s.cropNormB - fh).coerceIn(0f, 1f) }
                                                        2 -> { s.cropNormR = (s.cropNormL + fw).coerceIn(0f, 1f); s.cropNormB = (s.cropNormT + fh).coerceIn(0f, 1f) }
                                                        3 -> { s.cropNormL = (s.cropNormR - fw).coerceIn(0f, 1f); s.cropNormB = (s.cropNormT + fh).coerceIn(0f, 1f) }
                                                    }
                                                }
                                                s.loupeScreenPos = ch.position
                                                val cxDoc = if (dragTarget == 0 || dragTarget == 3) s.cropNormL * docW else s.cropNormR * docW
                                                val cyDoc = if (dragTarget == 0 || dragTarget == 1) s.cropNormT * docH else s.cropNormB * docH
                                                vm.updateLoupe(cxDoc, cyDoc, (min(docW, docH) * 0.035f).coerceIn(16f, 64f))
                                            }
                                        }
                                    }
                                }
                            }
                        })
                ) {
                    val dim = Color(0x88000000)
                    drawRect(dim, topLeft = Offset(0f, 0f), size = Size(size.width, top.coerceAtLeast(0f)))
                    drawRect(dim, topLeft = Offset(0f, top + boxH), size = Size(size.width, (size.height - top - boxH).coerceAtLeast(0f)))
                    drawRect(dim, topLeft = Offset(0f, top), size = Size(left.coerceAtLeast(0f), boxH))
                    drawRect(dim, topLeft = Offset(left + boxW, top), size = Size((size.width - left - boxW).coerceAtLeast(0f), boxH))
                    val gridColor = Color(0x66FFFFFF)
                    for (i in 1..2) {
                        val gx = left + boxW * (i / 3f)
                        val gy = top + boxH * (i / 3f)
                        drawLine(gridColor, Offset(gx, top), Offset(gx, top + boxH), strokeWidth = 1.5f)
                        drawLine(gridColor, Offset(left, gy), Offset(left + boxW, gy), strokeWidth = 1.5f)
                    }
                    drawRect(color = Color.White, topLeft = Offset(left, top), size = Size(boxW, boxH), style = Stroke(width = 3.5f))
                    // 4 crisp interactive corner handles
                    listOf(
                        Offset(left, top),
                        Offset(left + boxW, top),
                        Offset(left + boxW, top + boxH),
                        Offset(left, top + boxH)
                    ).forEach { corner ->
                        drawCircle(Color.White, radius = 15f, center = corner)
                        drawCircle(Color(0xFF4F9CFF), radius = 10f, center = corner)
                    }
                }
            }
        }

        // Clean loading indicator while decoding/baking heavy 12MP images
        if (state.isBusy) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x66111217)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xEE1E2028),
                    tonalElevation = 10.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text("İşleniyor...", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        // Live Retouch brush ring indicator while dragging
        s.retouchCursor?.let { (cx, cy) ->
            val zoom = s.canvasViewRef?.getCurrentZoom() ?: 1f
            val rPx = (state.brushSize * 0.5f * zoom).coerceIn(10f, 180f)
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(color = Color(0x88000000), radius = rPx + 2f, center = Offset(cx, cy), style = Stroke(width = 4f))
                drawCircle(color = Color.White, radius = rPx, center = Offset(cx, cy), style = Stroke(width = 2.5f))
            }
        }

        // 60 FPS Live Draggable Text Preview overlay
        // Interactive 60 FPS Full-Canvas Draggable Tone Curve Overlay (toggled by tapping the mini curve box)
        if (s.activePanel == BottomPanel.CURVES_LIVE && s.curveOverlayExpanded) {
            val view = s.canvasViewRef
            val docW = (state.docInfo?.width?.toInt() ?: 2048).toFloat()
            val docH = (state.docInfo?.height?.toInt() ?: 2048).toFloat()
            if (view != null) {
                val _tick = s.cameraTick
                val (rawL, rawT) = view.docToScreen(0f, 0f)
                val (rawR, rawB) = view.docToScreen(docW, docH)
                val boxL = min(rawL, rawR)
                val boxR = kotlin.math.max(rawL, rawR)
                val boxT = min(rawT, rawB)
                val boxB = kotlin.math.max(rawT, rawB)
                val boxW = (boxR - boxL).coerceAtLeast(120f)
                val boxH = (boxB - boxT).coerceAtLeast(120f)

                fun evalMonotoneCurve(x: Float, pts: List<Pair<Float, Float>>): Float {
                    if (pts.isEmpty()) return x.coerceIn(0f, 1f)
                    if (pts.size == 1) return pts[0].second.coerceIn(0f, 1f)
                    val sorted = pts.sortedBy { it.first }
                    if (x <= sorted.first().first) return sorted.first().second.coerceIn(0f, 1f)
                    if (x >= sorted.last().first) return sorted.last().second.coerceIn(0f, 1f)
                    val n = sorted.size
                    val sec = FloatArray(n - 1)
                    for (i in 0 until n - 1) {
                        val dx = (sorted[i + 1].first - sorted[i].first).coerceAtLeast(1e-4f)
                        sec[i] = (sorted[i + 1].second - sorted[i].second) / dx
                    }
                    val tang = FloatArray(n)
                    tang[0] = sec[0]
                    tang[n - 1] = sec[n - 2]
                    for (i in 1 until n - 1) {
                        tang[i] = if (sec[i - 1] * sec[i] <= 0f) 0f else (sec[i - 1] + sec[i]) * 0.5f
                    }
                    var seg = 0
                    while (seg < n - 2 && x > sorted[seg + 1].first) seg++
                    val x0 = sorted[seg].first
                    val x1 = sorted[seg + 1].first
                    val dx = (x1 - x0).coerceAtLeast(1e-4f)
                    val t = ((x - x0) / dx).coerceIn(0f, 1f)
                    val t2 = t * t
                    val t3 = t2 * t
                    val h00 = 2f * t3 - 3f * t2 + 1f
                    val h10 = t3 - 2f * t2 + t
                    val h01 = -2f * t3 + 3f * t2
                    val h11 = t3 - t2
                    return (h00 * sorted[seg].second + h10 * dx * tang[seg] + h01 * sorted[seg + 1].second + h11 * dx * tang[seg + 1]).coerceIn(0f, 1f)
                }

                fun nodeScreen(pt: Pair<Float, Float>): Offset =
                    Offset(boxL + pt.first * boxW, boxB - pt.second * boxH)

                val chColor = when (s.curveChannelIdx) {
                    1 -> Color(0xFFFF5252)
                    2 -> Color(0xFF4ADE80)
                    3 -> Color(0xFF60A5FA)
                    else -> Color.White
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (s.singleFingerPanMode) Modifier else Modifier.pointerInput(boxL, boxT, boxW, boxH, s.curveChannelIdx, s.curveEyedropperMode) {
                            detectDragGestures(
                                onDragStart = { pos ->
                                    if (s.curveEyedropperMode in 1..3 && pos.x in boxL..boxR && pos.y in boxT..boxB) {
                                        val nx = ((pos.x - boxL) / boxW).coerceIn(0f, 1f)
                                        val ny = ((pos.y - boxT) / boxH).coerceIn(0f, 1f)
                                        val mode = s.curveEyedropperMode
                                        vm.sampleColorAtNorm(nx, ny) { info ->
                                            s.curveUndoStack = (s.curveUndoStack + s.snapshotCurve()).takeLast(25)
                                            s.curveRedoStack = emptyList()
                                            val lum = (info.luma.toInt() / 255f).coerceIn(0.02f, 0.98f)
                                            when (mode) {
                                                1 -> { // Black point
                                                    val pts = s.curveRgbNodes.toMutableList()
                                                    pts[0] = (lum.coerceAtMost(0.45f)) to 0f
                                                    s.curveRgbNodes = pts.sortedBy { it.first }
                                                }
                                                2 -> { // Gray point (neutralize R/G/B toward luma)
                                                    val rIn = (info.r.toInt() / 255f).coerceIn(0.05f, 0.95f)
                                                    val gIn = (info.g.toInt() / 255f).coerceIn(0.05f, 0.95f)
                                                    val bIn = (info.b.toInt() / 255f).coerceIn(0.05f, 0.95f)
                                                    s.curveRedNodes = listOf(0f to 0f, rIn to lum, 1f to 1f)
                                                    s.curveGreenNodes = listOf(0f to 0f, gIn to lum, 1f to 1f)
                                                    s.curveBlueNodes = listOf(0f to 0f, bIn to lum, 1f to 1f)
                                                }
                                                3 -> { // White point
                                                    val pts = s.curveRgbNodes.toMutableList()
                                                    pts[pts.lastIndex] = (lum.coerceAtLeast(0.55f)) to 1f
                                                    s.curveRgbNodes = pts.sortedBy { it.first }
                                                }
                                            }
                                            s.curveEyedropperMode = 0
                                            vm.updateProxyAdjustment("curves", s.buildCurvesProJson())
                                            vm.refreshHistogram(force = true)
                                        }
                                        s.draggingCurveNode = -1
                                        return@detectDragGestures
                                    }

                                    val nodes = s.activeCurveNodes()
                                    var bestIdx = -1
                                    var bestDist = 96f
                                    nodes.forEachIndexed { i, pt ->
                                        val sp = nodeScreen(pt)
                                        val d = kotlin.math.hypot((pos.x - sp.x).toDouble(), (pos.y - sp.y).toDouble()).toFloat()
                                        if (d < bestDist) {
                                            bestDist = d
                                            bestIdx = i
                                        }
                                    }
                                    if (bestIdx == -1 && pos.x in (boxL - 28f)..(boxR + 28f) && pos.y in (boxT - 28f)..(boxB + 28f)) {
                                        // Insert a new arbitrary control point (up to 16)
                                        val nx = ((pos.x - boxL) / boxW).coerceIn(0.03f, 0.97f)
                                        val ny = ((boxB - pos.y) / boxH).coerceIn(0f, 1f)
                                        if (nodes.size < 16) {
                                            s.curveUndoStack = (s.curveUndoStack + s.snapshotCurve()).takeLast(25)
                                            s.curveRedoStack = emptyList()
                                            val updated = (nodes + (nx to ny)).sortedBy { it.first }
                                            s.setActiveCurveNodes(updated)
                                            bestIdx = updated.indexOfFirst { kotlin.math.abs(it.first - nx) < 0.002f && kotlin.math.abs(it.second - ny) < 0.002f }
                                            vm.updateProxyAdjustment("curves", s.buildCurvesProJson())
                                        }
                                    } else if (bestIdx >= 0) {
                                        s.curveUndoStack = (s.curveUndoStack + s.snapshotCurve()).takeLast(25)
                                        s.curveRedoStack = emptyList()
                                    }
                                    s.draggingCurveNode = bestIdx
                                    s.curveSelectedNodeIdx = bestIdx
                                },
                                onDragEnd = {
                                    s.draggingCurveNode = -1
                                    vm.refreshHistogram(force = true)
                                },
                                onDragCancel = { s.draggingCurveNode = -1 },
                                onDrag = { change, _ ->
                                    val idx = s.draggingCurveNode
                                    val nodes = s.activeCurveNodes()
                                    if (idx in nodes.indices) {
                                        change.consume()
                                        val ny = ((boxB - change.position.y) / boxH).coerceIn(0f, 1f)
                                        val nx = if (idx == 0) {
                                            ((change.position.x - boxL) / boxW).coerceIn(0f, (nodes[1].first - 0.03f).coerceAtLeast(0f))
                                        } else if (idx == nodes.lastIndex) {
                                            ((change.position.x - boxL) / boxW).coerceIn((nodes[idx - 1].first + 0.03f).coerceAtMost(1f), 1f)
                                        } else {
                                            ((change.position.x - boxL) / boxW).coerceIn(nodes[idx - 1].first + 0.025f, nodes[idx + 1].first - 0.025f)
                                        }
                                        val updated = nodes.toMutableList().apply { this[idx] = nx to ny }
                                        s.setActiveCurveNodes(updated)
                                        vm.updateProxyAdjustment("curves", s.buildCurvesProJson())
                                        vm.refreshHistogram()
                                    }
                                }
                            )
                        })
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // Live RGB / Luma Histogram directly behind the Curves grid (Madde 2)
                        val hist = state.histogram
                        if (hist != null && hist.luma.size >= 2) {
                            fun drawHistArea(bins: List<Float>, fill: Color) {
                                val hp = Path()
                                val step = boxW / (bins.size - 1).toFloat()
                                hp.moveTo(boxL, boxB)
                                bins.forEachIndexed { i, v ->
                                    hp.lineTo(boxL + i * step, boxB - v.coerceIn(0f, 1f) * boxH * 0.42f)
                                }
                                hp.lineTo(boxR, boxB)
                                hp.close()
                                drawPath(hp, color = fill)
                            }
                            when (s.curveChannelIdx) {
                                1 -> drawHistArea(hist.red, Color(0x38FF5252))
                                2 -> drawHistArea(hist.green, Color(0x384ADE80))
                                3 -> drawHistArea(hist.blue, Color(0x3860A5FA))
                                else -> {
                                    drawHistArea(hist.red, Color(0x1CFF5252))
                                    drawHistArea(hist.green, Color(0x1C4ADE80))
                                    drawHistArea(hist.blue, Color(0x1C60A5FA))
                                    drawHistArea(hist.luma, Color(0x28FFFFFF))
                                }
                            }
                        }

                        drawRect(
                            color = Color(0xCCFFFFFF),
                            topLeft = Offset(boxL, boxT),
                            size = Size(boxW, boxH),
                            style = Stroke(width = 2.2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
                        )
                        for (g in 1..3) {
                            val t = g / 4f
                            drawLine(Color(0x28FFFFFF), Offset(boxL + boxW * t, boxT), Offset(boxL + boxW * t, boxB), 1f)
                            drawLine(Color(0x28FFFFFF), Offset(boxL, boxT + boxH * t), Offset(boxR, boxT + boxH * t), 1f)
                        }
                        drawLine(Color(0x44FFFFFF), Offset(boxL, boxB), Offset(boxR, boxT), 1.5f)

                        val nodes = s.activeCurveNodes()
                        val path = Path()
                        val steps = 72
                        for (i in 0..steps) {
                            val x = i.toFloat() / steps.toFloat()
                            val y = evalMonotoneCurve(x, nodes)
                            val sx = boxL + x * boxW
                            val sy = boxB - y * boxH
                            if (i == 0) path.moveTo(sx, sy) else path.lineTo(sx, sy)
                        }
                        drawPath(path, color = Color(0xBB000000), style = Stroke(width = 6.5f))
                        drawPath(path, color = chColor, style = Stroke(width = 3.2f))

                        nodes.forEachIndexed { i, node ->
                            val pt = nodeScreen(node)
                            val active = s.draggingCurveNode == i || s.curveSelectedNodeIdx == i
                            drawCircle(
                                color = if (active) Color(0xCCF59E0B) else Color(0x77FFFFFF),
                                radius = if (active) 30f else 22f,
                                center = pt
                            )
                            drawCircle(
                                color = if (active) Color(0xFFF59E0B) else Color.White,
                                radius = if (active) 28f else 20f,
                                center = pt,
                                style = Stroke(width = 2.5f)
                            )
                            drawCircle(color = Color.White, radius = 5f, center = pt)
                        }
                    }
                }
            }
        }

        // v2.3.0 Parça 2: HSL Targeted Adjustment Tool (TAT) — touch photo to auto-pick HSL color band & drag left/right
        if (s.activePanel == BottomPanel.HSL_MIXER_LIVE && s.hslTatActive && !s.singleFingerPanMode) {
            val view = s.canvasViewRef
            val docW = (state.docInfo?.width?.toInt() ?: 2048).toFloat()
            val docH = (state.docInfo?.height?.toInt() ?: 2048).toFloat()
            if (view != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(docW, docH, s.hslTatParamIdx) {
                            detectDragGestures(
                                onDragStart = { pos ->
                                    val (dx, dy) = view.screenToDoc(pos.x, pos.y)
                                    val nx = (dx / docW).coerceIn(0f, 1f)
                                    val ny = (dy / docH).coerceIn(0f, 1f)
                                    vm.sampleColorAtNorm(nx, ny) { info ->
                                        s.hslBandIdx = info.hslBand.toInt().coerceIn(0, 7)
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val delta = (dragAmount.x * 0.45f)
                                    val idx = s.hslBandIdx.coerceIn(0, 7)
                                    when (s.hslTatParamIdx) {
                                        0 -> s.hslHue = s.hslHue.toMutableList().apply { this[idx] = (this[idx] + delta).coerceIn(-100f, 100f) }
                                        2 -> s.hslLum = s.hslLum.toMutableList().apply { this[idx] = (this[idx] + delta).coerceIn(-100f, 100f) }
                                        else -> s.hslSat = s.hslSat.toMutableList().apply { this[idx] = (this[idx] + delta).coerceIn(-100f, 100f) }
                                    }
                                    vm.updateProxyHslMixer(s.hslHue, s.hslSat, s.hslLum)
                                }
                            )
                        }
                )
            }
        }

        // Interactive 60 FPS 4-Corner Perspective Crop Overlay
        if (s.activePanel == BottomPanel.PERSPECTIVE_CROP_EDIT) {
            val view = s.canvasViewRef
            val docW = (state.docInfo?.width?.toInt() ?: 2048).toFloat()
            val docH = (state.docInfo?.height?.toInt() ?: 2048).toFloat()
            if (view != null) {
                val _tick = s.cameraTick
                fun toScreen(n: Offset): Offset {
                    val (sx, sy) = view.docToScreen(n.x * docW, n.y * docH)
                    return Offset(sx, sy)
                }
                val sTl = toScreen(s.pTl)
                val sTr = toScreen(s.pTr)
                val sBr = toScreen(s.pBr)
                val sBl = toScreen(s.pBl)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (s.singleFingerPanMode) Modifier else Modifier.pointerInput(docW, docH, _tick) {
                            detectDragGestures(
                                onDragStart = { pos ->
                                    val pts = listOf(toScreen(s.pTl), toScreen(s.pTr), toScreen(s.pBr), toScreen(s.pBl))
                                    var bestIdx = -1
                                    var bestDist = 96f
                                    pts.forEachIndexed { i, pt ->
                                        val d = kotlin.math.hypot((pos.x - pt.x).toDouble(), (pos.y - pt.y).toDouble()).toFloat()
                                        if (d < bestDist) {
                                            bestDist = d
                                            bestIdx = i
                                        }
                                    }
                                    s.draggingCornerIdx = bestIdx
                                },
                                onDragEnd = { s.draggingCornerIdx = -1; s.loupeScreenPos = null; vm.hideLoupe() },
                                onDragCancel = { s.draggingCornerIdx = -1; s.loupeScreenPos = null; vm.hideLoupe() },
                                onDrag = { change, dragAmount ->
                                    if (s.draggingCornerIdx in 0..3) {
                                        change.consume()
                                        val z = view.getCurrentZoom().coerceAtLeast(0.05f)
                                        val dnX = dragAmount.x / (docW * z)
                                        val dnY = dragAmount.y / (docH * z)
                                        val cornerPt = when (s.draggingCornerIdx) {
                                            0 -> { s.pTl = Offset((s.pTl.x + dnX).coerceIn(0f, 1f), (s.pTl.y + dnY).coerceIn(0f, 1f)); s.pTl }
                                            1 -> { s.pTr = Offset((s.pTr.x + dnX).coerceIn(0f, 1f), (s.pTr.y + dnY).coerceIn(0f, 1f)); s.pTr }
                                            2 -> { s.pBr = Offset((s.pBr.x + dnX).coerceIn(0f, 1f), (s.pBr.y + dnY).coerceIn(0f, 1f)); s.pBr }
                                            else -> { s.pBl = Offset((s.pBl.x + dnX).coerceIn(0f, 1f), (s.pBl.y + dnY).coerceIn(0f, 1f)); s.pBl }
                                        }
                                        s.loupeScreenPos = change.position
                                        vm.updateLoupe(cornerPt.x * docW, cornerPt.y * docH, (min(docW, docH) * 0.035f).coerceIn(16f, 64f))
                                    }
                                }
                            )
                        })
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val quad = Path().apply {
                            moveTo(sTl.x, sTl.y)
                            lineTo(sTr.x, sTr.y)
                            lineTo(sBr.x, sBr.y)
                            lineTo(sBl.x, sBl.y)
                            close()
                        }
                        drawPath(quad, color = Color(0xFF4F9CFF), style = Stroke(width = 4f))
                        // 3x3 perspective grid inside the quad
                        for (k in 1..2) {
                            val t = k / 3f
                            val topPt = Offset(sTl.x + (sTr.x - sTl.x) * t, sTl.y + (sTr.y - sTl.y) * t)
                            val botPt = Offset(sBl.x + (sBr.x - sBl.x) * t, sBl.y + (sBr.y - sBl.y) * t)
                            drawLine(Color(0x88FFFFFF), topPt, botPt, strokeWidth = 1.5f)
                            val leftPt = Offset(sTl.x + (sBl.x - sTl.x) * t, sTl.y + (sBl.y - sTl.y) * t)
                            val rightPt = Offset(sTr.x + (sBr.x - sTr.x) * t, sTr.y + (sBr.y - sTr.y) * t)
                            drawLine(Color(0x88FFFFFF), leftPt, rightPt, strokeWidth = 1.5f)
                        }
                        listOf(sTl, sTr, sBr, sBl).forEachIndexed { i, pt ->
                            val active = s.draggingCornerIdx == i
                            drawCircle(
                                color = if (active) Color(0xFF2ED573) else Color(0xFF4F9CFF),
                                radius = if (active) 24f else 18f,
                                center = pt
                            )
                            drawCircle(color = Color.White, radius = 8f, center = pt)
                        }
                    }
                }
            }
        }

        val curActiveLayerId = state.layers.firstOrNull { it.isActive }?.id
        LaunchedEffect(s.activePanel, curActiveLayerId) {
            if (s.activePanel == BottomPanel.LAYER_TRANSFORM && curActiveLayerId != null) {
                s.layerDx = 0f
                s.layerDy = 0f
                s.layerScaleXPct = 100f
                s.layerScaleYPct = 100f
                s.layerScalePct = 100f
                s.layerRotDeg = 0f
                vm.loadActiveLayerPreview()
            }
        }

        // Interactive 60 FPS Active Layer Move / Free XY Stretch / Rotate Overlay (8 handles: 4 corners + 4 edges)
        if (s.activePanel == BottomPanel.LAYER_TRANSFORM) {
            val view = s.canvasViewRef
            val prev = activeLayerPrev
            if (view != null && prev != null) {
                val _tick = s.cameraTick
                val b = prev.first
                val baseW = (b.x1 - b.x0).coerceAtLeast(10).toFloat()
                val baseH = (b.y1 - b.y0).coerceAtLeast(10).toFloat()
                val z = view.getCurrentZoom().coerceAtLeast(0.05f)
                val cxDoc = (b.x0 + b.x1) * 0.5f + s.layerDx
                val cyDoc = (b.y0 + b.y1) * 0.5f + s.layerDy
                val curWDoc = baseW * (s.layerScaleXPct / 100f)
                val curHDoc = baseH * (s.layerScaleYPct / 100f)
                val (sCx, sCy) = view.docToScreen(cxDoc, cyDoc)
                val boxW = curWDoc * z
                val boxH = curHDoc * z
                val handleHitPx = with(density) { 26.dp.toPx() }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (s.singleFingerPanMode) Modifier else Modifier.pointerInput(b, z, s.layerLockAspect) {
                            awaitPointerEventScope {
                                while (true) {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val p0 = down.position
                                    val cW = baseW * (s.layerScaleXPct / 100f) * z
                                    val cH = baseH * (s.layerScaleYPct / 100f) * z
                                    val (cx0, cy0) = view.docToScreen((b.x0 + b.x1) * 0.5f + s.layerDx, (b.y0 + b.y1) * 0.5f + s.layerDy)
                                    val rad = Math.toRadians(s.layerRotDeg.toDouble())
                                    val cosR = kotlin.math.cos(rad).toFloat()
                                    val sinR = kotlin.math.sin(rad).toFloat()
                                    fun toScreenLocal(lx: Float, ly: Float) = Offset(cx0 + lx * cosR - ly * sinR, cy0 + lx * sinR + ly * cosR)
                                    val hw = cW * 0.5f; val hh = cH * 0.5f
                                    // 0=TL, 1=TR, 2=BR, 3=BL, 4=Top, 5=Right, 6=Bottom, 7=Left
                                    val handles = listOf(
                                        toScreenLocal(-hw, -hh), toScreenLocal(hw, -hh),
                                        toScreenLocal(hw, hh), toScreenLocal(-hw, hh),
                                        toScreenLocal(0f, -hh), toScreenLocal(hw, 0f),
                                        toScreenLocal(0f, hh), toScreenLocal(-hw, 0f)
                                    )
                                    var grab = -1
                                    var bestD = handleHitPx
                                    handles.forEachIndexed { idx, pt ->
                                        val d = hypot((p0.x - pt.x).toDouble(), (p0.y - pt.y).toDouble()).toFloat()
                                        if (d < bestD) { bestD = d; grab = idx }
                                    }
                                    var prevSingle = p0
                                    var prevDist = 0f
                                    var prevAngle = 0f
                                    var prevMid = Offset.Zero
                                    var wasMulti = false

                                    while (true) {
                                        val ev = awaitPointerEvent()
                                        val active = ev.changes.filter { it.pressed }
                                        if (active.isEmpty()) break
                                        if (active.size >= 2) {
                                            val a0 = active[0].position
                                            val a1 = active[1].position
                                            val mid = Offset((a0.x + a1.x) * 0.5f, (a0.y + a1.y) * 0.5f)
                                            val dist = hypot((a1.x - a0.x).toDouble(), (a1.y - a0.y).toDouble()).toFloat().coerceAtLeast(1f)
                                            val ang = kotlin.math.atan2((a1.y - a0.y).toDouble(), (a1.x - a0.x).toDouble()).toFloat()
                                            if (wasMulti && prevDist > 1f) {
                                                val zf = (dist / prevDist).coerceIn(0.85f, 1.18f)
                                                s.layerScaleXPct = (s.layerScaleXPct * zf).coerceIn(10f, 450f)
                                                s.layerScaleYPct = (s.layerScaleYPct * zf).coerceIn(10f, 450f)
                                                s.layerScalePct = (s.layerScaleXPct + s.layerScaleYPct) * 0.5f
                                                s.layerDx += (mid.x - prevMid.x) / z
                                                s.layerDy += (mid.y - prevMid.y) / z
                                                var dAng = Math.toDegrees((ang - prevAngle).toDouble()).toFloat()
                                                if (dAng > 180f) dAng -= 360f
                                                if (dAng < -180f) dAng += 360f
                                                val nextRot = s.layerRotDeg + dAng
                                                s.layerRotDeg = ((nextRot + 180f) % 360f + 360f) % 360f - 180f
                                            }
                                            prevDist = dist; prevAngle = ang; prevMid = mid; wasMulti = true
                                            active.forEach { it.consume() }
                                        } else if (!wasMulti) {
                                            val ch = active[0]
                                            val dxs = ch.position.x - prevSingle.x
                                            val dys = ch.position.y - prevSingle.y
                                            prevSingle = ch.position
                                            if (grab < 0) {
                                                s.layerDx += dxs / z
                                                s.layerDy += dys / z
                                            } else {
                                                // Project screen delta into layer's rotated local space
                                                val rRad = Math.toRadians(s.layerRotDeg.toDouble())
                                                val cR = kotlin.math.cos(rRad).toFloat()
                                                val sR = kotlin.math.sin(rRad).toFloat()
                                                val dlx = (dxs * cR + dys * sR) / z
                                                val dly = (-dxs * sR + dys * cR) / z
                                                val sxSign = when (grab) { 0, 3, 7 -> -1f; 1, 2, 5 -> 1f; else -> 0f }
                                                val sySign = when (grab) { 0, 1, 4 -> -1f; 2, 3, 6 -> 1f; else -> 0f }
                                                var dW = dlx * sxSign
                                                var dH = dly * sySign
                                                if (s.layerLockAspect && sxSign != 0f && sySign != 0f) {
                                                    val avgPct = ((dW / baseW) + (dH / baseH)) * 0.5f
                                                    dW = avgPct * baseW
                                                    dH = avgPct * baseH
                                                }
                                                val oldW = baseW * (s.layerScaleXPct / 100f)
                                                val oldH = baseH * (s.layerScaleYPct / 100f)
                                                val newW = (oldW + dW).coerceIn(baseW * 0.10f, baseW * 4.5f)
                                                val newH = (oldH + dH).coerceIn(baseH * 0.10f, baseH * 4.5f)
                                                val realDW = newW - oldW
                                                val realDH = newH - oldH
                                                s.layerScaleXPct = (newW / baseW) * 100f
                                                s.layerScaleYPct = (newH / baseH) * 100f
                                                s.layerScalePct = (s.layerScaleXPct + s.layerScaleYPct) * 0.5f
                                                // Anchor opposite edge/corner by shifting center half the delta in rotated space!
                                                val shiftLocalX = realDW * 0.5f * sxSign
                                                val shiftLocalY = realDH * 0.5f * sySign
                                                s.layerDx += shiftLocalX * cR - shiftLocalY * sR
                                                s.layerDy += shiftLocalX * sR + shiftLocalY * cR
                                            }
                                            ch.consume()
                                        }
                                    }
                                }
                            }
                        })
                ) {
                    val boxWDp = with(density) { boxW.toDp() }
                    val boxHDp = with(density) { boxH.toDp() }
                    Box(
                        modifier = Modifier
                            .offset {
                                androidx.compose.ui.unit.IntOffset(
                                    (sCx - boxW * 0.5f).roundToInt(),
                                    (sCy - boxH * 0.5f).roundToInt()
                                )
                            }
                            .size(boxWDp, boxHDp)
                            .graphicsLayer { rotationZ = s.layerRotDeg }
                            .border(2.dp, Color(0xFF4F9CFF), RoundedCornerShape(4.dp))
                    ) {
                        prev.second?.let { bmp ->
                            Image(
                                bitmap = bmp,
                                contentDescription = "Aktif katman",
                                contentScale = ContentScale.FillBounds,
                                alpha = 1.0f,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    // Draw 4 corner handles + 4 edge stretch handles on top (always crisp regardless of scale!)
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val _t = s.cameraTick
                        if (_t < 0) return@Canvas
                        val rad = Math.toRadians(s.layerRotDeg.toDouble())
                        val cosR = kotlin.math.cos(rad).toFloat()
                        val sinR = kotlin.math.sin(rad).toFloat()
                        val hw = boxW * 0.5f; val hh = boxH * 0.5f
                        fun pt(lx: Float, ly: Float) = Offset(sCx + lx * cosR - ly * sinR, sCy + lx * sinR + ly * cosR)
                        val corners = listOf(pt(-hw, -hh), pt(hw, -hh), pt(hw, hh), pt(-hw, hh))
                        val edges = listOf(pt(0f, -hh), pt(hw, 0f), pt(0f, hh), pt(-hw, 0f))
                        corners.forEach { c ->
                            drawCircle(Color.White, radius = 10f, center = c)
                            drawCircle(Color(0xFF4F9CFF), radius = 6.5f, center = c)
                        }
                        edges.forEach { e ->
                            drawCircle(Color.White, radius = 8f, center = e)
                            drawCircle(Color(0xFF2ED573), radius = 5f, center = e)
                        }
                    }
                }
            }
        }

        // v2.2.2 Pure Rust WYSIWYG Text Drag Overlay (no fake Compose box, no detached blue dots!)
        if (s.activePanel == BottomPanel.TEXT_EDIT) {
            val docW = (state.docInfo?.width?.toInt() ?: 2048).toFloat()
            val docH = (state.docInfo?.height?.toInt() ?: 2048).toFloat()
            val viewRef = s.canvasViewRef
            val tick = s.cameraTick
            if (viewRef != null && tick >= 0) {
                val currentZoom = viewRef.getCurrentZoom().coerceAtLeast(0.05f)
                val hapticText = androidx.compose.ui.platform.LocalHapticFeedback.current
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (s.singleFingerPanMode) Modifier else Modifier.pointerInput(docW, docH, currentZoom) {
                                detectDragGestures(
                                    onDragEnd = { s.textSnapX = false; s.textSnapY = false },
                                    onDragCancel = { s.textSnapX = false; s.textSnapY = false }
                                ) { change, dragAmount ->
                                    change.consume()
                                    val dxDoc = dragAmount.x / currentZoom
                                    val dyDoc = dragAmount.y / currentZoom
                                    var nx = (s.textNormX + dxDoc / docW).coerceIn(0.02f, 0.98f)
                                    var ny = (s.textNormY + dyDoc / docH).coerceIn(0.02f, 0.98f)
                                    val snapXNow = kotlin.math.abs(nx - 0.5f) < 0.016f
                                    val snapYNow = kotlin.math.abs(ny - 0.5f) < 0.016f
                                    if (snapXNow && !s.textSnapX) hapticText.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                    if (snapYNow && !s.textSnapY) hapticText.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                    if (snapXNow) nx = 0.5f
                                    if (snapYNow) ny = 0.5f
                                    s.textSnapX = snapXNow
                                    s.textSnapY = snapYNow
                                    s.textNormX = nx
                                    s.textNormY = ny
                                }
                            }
                        )
                ) {
                    if (s.textSnapX || s.textSnapY) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val (cx, cy) = viewRef.docToScreen(docW * 0.5f, docH * 0.5f)
                            if (s.textSnapX) {
                                drawLine(Color(0xCCF59E0B), Offset(cx, 0f), Offset(cx, size.height), strokeWidth = 2f)
                            }
                            if (s.textSnapY) {
                                drawLine(Color(0xCCF59E0B), Offset(0f, cy), Offset(size.width, cy), strokeWidth = 2f)
                            }
                        }
                    }
                }
            }
        }
        if (s.activePanel == BottomPanel.PUPPET_WARP_EDIT) {
            PuppetWarpCanvasOverlay(
                canvasViewRef = s.canvasViewRef,
                cameraTick = s.cameraTick,
                docW = (state.docInfo?.width?.toInt() ?: 2048).toFloat(),
                docH = (state.docInfo?.height?.toInt() ?: 2048).toFloat(),
                pins = s.puppetPins,
                selectedIdx = s.puppetSelectedIdx,
                showMesh = s.puppetShowMesh,
                meshSegments = s.puppetMeshSegments,
                handPanEnabled = s.singleFingerPanMode,
                onSelectPin = { s.puppetSelectedIdx = it },
                onPushUndo = { snapshot ->
                    s.puppetUndoStack = (s.puppetUndoStack + listOf(snapshot)).takeLast(40)
                    s.puppetRedoStack = emptyList()
                },
                onAddPin = { dx, dy ->
                    s.puppetUndoStack = (s.puppetUndoStack + listOf(s.puppetPins)).takeLast(40)
                    s.puppetRedoStack = emptyList()
                    val next = s.puppetPins + PuppetPinPoint(dx, dy, dx, dy, null, 0)
                    s.puppetPins = next
                    s.puppetSelectedIdx = next.lastIndex
                },
                onDeletePin = { idx ->
                    if (idx in s.puppetPins.indices) {
                        s.puppetUndoStack = (s.puppetUndoStack + listOf(s.puppetPins)).takeLast(40)
                        s.puppetRedoStack = emptyList()
                        val next = s.puppetPins.toMutableList().apply { removeAt(idx) }
                        s.puppetPins = next
                        s.puppetSelectedIdx = if (next.isEmpty()) -1 else (idx.coerceAtMost(next.lastIndex))
                        if (next.isEmpty()) vm.resetProxyPreview() else vm.updateProxyPuppetWarp(next, s.puppetMode, s.puppetDensity, s.puppetExpansion)
                    }
                },
                onMovePin = { idx, nx, ny ->
                    if (idx in s.puppetPins.indices) {
                        val updated = s.puppetPins.toMutableList()
                        val old = updated[idx]
                        updated[idx] = PuppetPinPoint(old.srcX, old.srcY, nx, ny, old.rotateDeg, old.depth)
                        s.puppetPins = updated
                        vm.updateProxyPuppetWarp(updated, s.puppetMode, s.puppetDensity, s.puppetExpansion)
                        if (s.puppetShowMesh) vm.fetchPuppetMeshSegments(updated, s.puppetMode, s.puppetDensity, s.puppetExpansion) { s.puppetMeshSegments = it }
                    }
                },
                onRestorePins = { prev ->
                    s.puppetPins = prev
                    if (prev.isEmpty()) vm.resetProxyPreview() else vm.updateProxyPuppetWarp(prev, s.puppetMode, s.puppetDensity, s.puppetExpansion)
                },
                onPinchPan = { dPanX, dPanY, zoomScale, cx, cy ->
                    s.canvasViewRef?.applyExternalPinchPan(zoomScale, dPanX, dPanY)
                    s.cameraTick++
                }
            )
        }

        // v0.9.5 Interactive On-Canvas Gradient Start/End Drag Overlay (60 FPS Live Proxy)
        if (s.activePanel == BottomPanel.GRADIENT_EDIT) {
            val docW = (state.docInfo?.width?.toInt() ?: 2048).toFloat()
            val docH = (state.docInfo?.height?.toInt() ?: 2048).toFloat()
            val viewRef = s.canvasViewRef
            val tick = s.cameraTick
            if (viewRef != null && tick >= 0) {
                val (ax, ay) = viewRef.docToScreen(docW * s.gradFromNormX, docH * s.gradFromNormY)
                val (bx, by) = viewRef.docToScreen(docW * s.gradToNormX, docH * s.gradToNormY)
                val startCol = remember(s.gradStartHex) { runCatching { Color(AndroidColor.parseColor(s.gradStartHex)) }.getOrDefault(Color(0xFF4F9CFF)) }
                val endCol = remember(s.gradEndHex) { runCatching { Color(AndroidColor.parseColor(s.gradEndHex)) }.getOrDefault(Color(0xFFFF4D4F)) }
                androidx.compose.foundation.Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (s.singleFingerPanMode) Modifier else Modifier.pointerInput(docW, docH, s.gradStyle, s.gradStartHex, s.gradEndHex, s.gradOpacity) {
                            detectDragGestures(
                                onDragStart = { pos ->
                                    val dA = kotlin.math.hypot(pos.x - ax, pos.y - ay)
                                    val dB = kotlin.math.hypot(pos.x - bx, pos.y - by)
                                    val (dx, dy) = viewRef.screenToDoc(pos.x, pos.y)
                                    if (dA > 70f && dB > 70f) {
                                        s.gradFromNormX = (dx / docW).coerceIn(0f, 1f)
                                        s.gradFromNormY = (dy / docH).coerceIn(0f, 1f)
                                        s.gradToNormX = s.gradFromNormX
                                        s.gradToNormY = s.gradFromNormY
                                    }
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val p = change.position
                                    val (dx, dy) = viewRef.screenToDoc(p.x, p.y)
                                    val nx = (dx / docW).coerceIn(0f, 1f)
                                    val ny = (dy / docH).coerceIn(0f, 1f)
                                    val (curAx, curAy) = viewRef.docToScreen(docW * s.gradFromNormX, docH * s.gradFromNormY)
                                    val (curBx, curBy) = viewRef.docToScreen(docW * s.gradToNormX, docH * s.gradToNormY)
                                    if (kotlin.math.hypot(p.x - curAx, p.y - curAy) < kotlin.math.hypot(p.x - curBx, p.y - curBy)) {
                                        s.gradFromNormX = nx
                                        s.gradFromNormY = ny
                                    } else {
                                        s.gradToNormX = nx
                                        s.gradToNormY = ny
                                    }
                                    vm.updateProxyGradient(
                                        docW * s.gradFromNormX, docH * s.gradFromNormY,
                                        docW * s.gradToNormX, docH * s.gradToNormY,
                                        s.gradStyle, s.gradStartHex, s.gradEndHex, s.gradOpacity
                                    )
                                }
                            )
                        })
                ) {
                    drawLine(Color.White.copy(alpha = 0.85f), Offset(ax, ay), Offset(bx, by), strokeWidth = 3f)
                    drawCircle(Color.White, radius = 16f, center = Offset(ax, ay))
                    drawCircle(startCol, radius = 12f, center = Offset(ax, ay))
                    drawCircle(Color.White, radius = 16f, center = Offset(bx, by))
                    drawCircle(endCol, radius = 12f, center = Offset(bx, by))
                }
            }
        }

        // v0.9.5 Interactive On-Canvas Vector Shape Draw/Resize Overlay (60 FPS Live Proxy)
        if (s.activePanel == BottomPanel.SHAPE_EDIT) {
            val docW = (state.docInfo?.width?.toInt() ?: 2048).toFloat()
            val docH = (state.docInfo?.height?.toInt() ?: 2048).toFloat()
            val viewRef = s.canvasViewRef
            val handleHitPx = with(density) { 26.dp.toPx() }
            if (viewRef != null) {
                androidx.compose.foundation.Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (s.singleFingerPanMode) Modifier else Modifier.pointerInput(docW, docH, s.shapeKind, s.shapeFillHex, s.shapeStrokeHex, s.shapeStrokeWidth, s.shapeCornerRadius, s.shapeSides) {
                            var dragMode = 5 // 1=TL, 2=TR, 3=BR, 4=BL, 5=rigid move
                            var lastDocX = 0f
                            var lastDocY = 0f
                            detectDragGestures(
                                onDragStart = { pos ->
                                    val (c0x, c0y) = viewRef.docToScreen(docW * s.shapeX0Norm, docH * s.shapeY0Norm)
                                    val (c1x, c1y) = viewRef.docToScreen(docW * s.shapeX1Norm, docH * s.shapeY1Norm)
                                    val (dx, dy) = viewRef.screenToDoc(pos.x, pos.y)
                                    lastDocX = dx
                                    lastDocY = dy
                                    if (s.shapeKind == "line") {
                                        val d0 = kotlin.math.hypot(pos.x - c0x, pos.y - c0y)
                                        val d1 = kotlin.math.hypot(pos.x - c1x, pos.y - c1y)
                                        dragMode = when {
                                            d0 <= handleHitPx -> 1
                                            d1 <= handleHitPx -> 3
                                            else -> 5
                                        }
                                    } else {
                                        // Normalize so x0<=x1 and y0<=y1 for clean 4-corner handles
                                        val nx0 = min(s.shapeX0Norm, s.shapeX1Norm)
                                        val nx1 = kotlin.math.max(s.shapeX0Norm, s.shapeX1Norm)
                                        val ny0 = min(s.shapeY0Norm, s.shapeY1Norm)
                                        val ny1 = kotlin.math.max(s.shapeY0Norm, s.shapeY1Norm)
                                        s.shapeX0Norm = nx0; s.shapeX1Norm = nx1; s.shapeY0Norm = ny0; s.shapeY1Norm = ny1
                                        val (lx, ty) = viewRef.docToScreen(docW * nx0, docH * ny0)
                                        val (rx, by) = viewRef.docToScreen(docW * nx1, docH * ny1)
                                        val pts = listOf(Offset(lx, ty), Offset(rx, ty), Offset(rx, by), Offset(lx, by))
                                        var best = 5
                                        var bestD = handleHitPx
                                        pts.forEachIndexed { idx, pt ->
                                            val d = kotlin.math.hypot(pos.x - pt.x, pos.y - pt.y)
                                            if (d < bestD) { bestD = d; best = idx + 1 }
                                        }
                                        dragMode = best
                                    }
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val (dx, dy) = viewRef.screenToDoc(change.position.x, change.position.y)
                                    val nx = (dx / docW).coerceIn(0f, 1f)
                                    val ny = (dy / docH).coerceIn(0f, 1f)
                                    when (dragMode) {
                                        1 -> { s.shapeX0Norm = nx; s.shapeY0Norm = ny } // TL
                                        2 -> { s.shapeX1Norm = nx; s.shapeY0Norm = ny } // TR
                                        3 -> { s.shapeX1Norm = nx; s.shapeY1Norm = ny } // BR
                                        4 -> { s.shapeX0Norm = nx; s.shapeY1Norm = ny } // BL
                                        else -> {
                                            val dnx = (dx - lastDocX) / docW
                                            val dny = (dy - lastDocY) / docH
                                            lastDocX = dx
                                            lastDocY = dy
                                            val bx0 = min(s.shapeX0Norm, s.shapeX1Norm)
                                            val bx1 = kotlin.math.max(s.shapeX0Norm, s.shapeX1Norm)
                                            val by0 = min(s.shapeY0Norm, s.shapeY1Norm)
                                            val by1 = kotlin.math.max(s.shapeY0Norm, s.shapeY1Norm)
                                            val cdx = dnx.coerceIn(-0.2f - bx0, 1.2f - bx1)
                                            val cdy = dny.coerceIn(-0.2f - by0, 1.2f - by1)
                                            s.shapeX0Norm += cdx
                                            s.shapeX1Norm += cdx
                                            s.shapeY0Norm += cdy
                                            s.shapeY1Norm += cdy
                                        }
                                    }
                                    vm.updateProxyShape(
                                        s.shapeKind,
                                        docW * s.shapeX0Norm, docH * s.shapeY0Norm,
                                        docW * s.shapeX1Norm, docH * s.shapeY1Norm,
                                        s.shapeFillHex, s.shapeStrokeHex, s.shapeStrokeWidth, s.shapeCornerRadius, s.shapeSides
                                    )
                                }
                            )
                        })
                ) {
                    val _camT = s.cameraTick
                    if (_camT < 0) return@Canvas
                    val (sx0, sy0) = viewRef.docToScreen(docW * s.shapeX0Norm, docH * s.shapeY0Norm)
                    val (sx1, sy1) = viewRef.docToScreen(docW * s.shapeX1Norm, docH * s.shapeY1Norm)
                    val lx = min(sx0, sx1)
                    val rx = kotlin.math.max(sx0, sx1)
                    val ty = min(sy0, sy1)
                    val by = kotlin.math.max(sy0, sy1)
                    val rw = abs(sx1 - sx0).coerceAtLeast(4f)
                    val rh = abs(sy1 - sy0).coerceAtLeast(4f)
                    if (s.shapeKind == "line") {
                        drawLine(Color(0xCC4F9CFF), Offset(sx0, sy0), Offset(sx1, sy1), strokeWidth = 2.5f)
                        listOf(Offset(sx0, sy0), Offset(sx1, sy1)).forEach { pt ->
                            drawCircle(Color.White, radius = 10f, center = pt)
                            drawCircle(Color(0xFF4F9CFF), radius = 6f, center = pt)
                        }
                    } else {
                        drawRect(
                            color = Color(0xCC4F9CFF),
                            topLeft = Offset(lx, ty),
                            size = androidx.compose.ui.geometry.Size(rw, rh),
                            style = Stroke(width = 2f)
                        )
                        listOf(Offset(lx, ty), Offset(rx, ty), Offset(rx, by), Offset(lx, by)).forEach { pt ->
                            drawCircle(Color.White, radius = 10f, center = pt)
                            drawCircle(Color(0xFF4F9CFF), radius = 6f, center = pt)
                        }
                    }
                }
            }
        }

        // v0.9.8 Interactive 60 FPS On-Canvas Liquify Drag Overlay
        if (s.activePanel == BottomPanel.LIQUIFY_EDIT) {
            val viewRef = s.canvasViewRef
            if (viewRef != null) {
                androidx.compose.foundation.Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (s.singleFingerPanMode) Modifier else Modifier.pointerInput(s.liquifyTool, s.liquifySize, s.liquifyPressure) {
                            awaitPointerEventScope {
                                while (true) {
                                    val firstDown = awaitFirstDown(requireUnconsumed = false)
                                var singleStrokeActive = false
                                var pinchActive = false
                                var prevDist = 0f
                                var prevCentroid = Offset.Zero
                                val downPos = firstDown.position

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val pressed = event.changes.filter { it.pressed }
                                    if (pressed.isEmpty()) {
                                        if (singleStrokeActive) {
                                            vm.onLiquifyEnd()
                                        }
                                        s.liquifyCursorScreen = null
                                        break
                                    }
                                    if (pressed.size >= 2) {
                                        if (singleStrokeActive) {
                                            vm.onLiquifyEnd()
                                            singleStrokeActive = false
                                        }
                                        s.liquifyCursorScreen = null
                                        val p0 = pressed[0].position
                                        val p1 = pressed[1].position
                                        val dist = kotlin.math.hypot((p1.x - p0.x).toDouble(), (p1.y - p0.y).toDouble()).toFloat().coerceAtLeast(1f)
                                        val centroid = Offset((p0.x + p1.x) * 0.5f, (p0.y + p1.y) * 0.5f)
                                        if (pinchActive && prevDist > 1f) {
                                            val zf = (dist / prevDist).coerceIn(0.85f, 1.18f)
                                            viewRef.applyExternalPinchPan(zf, centroid.x - prevCentroid.x, centroid.y - prevCentroid.y)
                                        }
                                        prevDist = dist
                                        prevCentroid = centroid
                                        pinchActive = true
                                        pressed.forEach { it.consume() }
                                    } else if (!pinchActive) {
                                        val ch = pressed[0]
                                        val pos = ch.position
                                        ch.consume()
                                        if (!singleStrokeActive) {
                                            val moved = kotlin.math.hypot((pos.x - downPos.x).toDouble(), (pos.y - downPos.y).toDouble()).toFloat()
                                            if (moved > 3f || s.liquifyTool == "bloat" || s.liquifyTool == "pucker" || s.liquifyTool == "twirlCw") {
                                                singleStrokeActive = true
                                                s.liquifyCursorScreen = pos
                                                val (dx0, dy0) = viewRef.screenToDoc(downPos.x, downPos.y)
                                                vm.onLiquifyStartPro(s.liquifyTool, s.liquifySize, s.liquifyPressure, s.liquifyDensity, s.liquifyRate, dx0, dy0)
                                                val (dx, dy) = viewRef.screenToDoc(pos.x, pos.y)
                                                vm.onLiquifyMove(dx, dy)
                                            }
                                        } else {
                                            s.liquifyCursorScreen = pos
                                            val (dx, dy) = viewRef.screenToDoc(pos.x, pos.y)
                                            vm.onLiquifyMove(dx, dy)
                                        }
                                    }
                                }
                                }
                            }
                        }
                        )
                ) {
                    val cur = s.liquifyCursorScreen
                    if (cur != null) {
                        val rPx = (s.liquifySize * 0.5f * viewRef.getCurrentZoom()).coerceIn(10f, 360f)
                        drawCircle(Color.White.copy(alpha = 0.85f), radius = rPx, center = cur, style = Stroke(width = 2.2f))
                        drawCircle(Color(0x884F9CFF), radius = (rPx - 2f).coerceAtLeast(4f), center = cur, style = Stroke(width = 1.2f))
                    }
                }
            }
        }

        // v2.4.0 Parça 3 (Blok 1): Global 1-Tap Floating Selection Pill — clear or invert selection from ANY tool!
        if (state.selectionBounds != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(top = 60.dp, end = 12.dp),
                contentAlignment = Alignment.TopEnd
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xE6141720),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x66F59E0B)),
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { vm.deselect() }
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Seçimi Bırak", tint = Color(0xFFFF8A8A), modifier = Modifier.size(14.dp))
                            Text("Seçimi Bırak", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Box(Modifier.width(1.dp).height(14.dp).background(Color(0x33FFFFFF)))
                        Text(
                            "Ters Çevir",
                            color = Color(0xFFF59E0B),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { vm.selectInverse() }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }

        // v2.4.0 Parça 3 (Blok 2): Liquify Lasso Freeze Touch Overlay
        if (s.activePanel == BottomPanel.LIQUIFY_EDIT && s.liquifyTool == "lassoFreeze" && !s.singleFingerPanMode) {
            val viewRef = s.canvasViewRef
            if (viewRef != null) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(viewRef, s.liquifyTool) {
                            detectDragGestures(
                                onDragStart = { pos -> s.liquifyLassoPoints = listOf(pos) },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val prev = s.liquifyLassoPoints.lastOrNull()
                                    if (prev == null || kotlin.math.hypot((change.position.x - prev.x).toDouble(), (change.position.y - prev.y).toDouble()) > 6.0) {
                                        s.liquifyLassoPoints = s.liquifyLassoPoints + change.position
                                    }
                                },
                                onDragEnd = {
                                    val pts = s.liquifyLassoPoints
                                    s.liquifyLassoPoints = emptyList()
                                    if (pts.size >= 3) {
                                        val docPts = pts.map { p ->
                                            val (dx, dy) = viewRef.screenToDoc(p.x, p.y)
                                            uniffi.kastamonushop_ffi.StrokeSample(dx, dy, 1f, 0f, 0f)
                                        }
                                        vm.liquifyLassoMask(docPts, freeze = true)
                                    }
                                },
                                onDragCancel = { s.liquifyLassoPoints = emptyList() }
                            )
                        }
                ) {
                    if (s.liquifyLassoPoints.size >= 2) {
                        val path = Path().apply {
                            moveTo(s.liquifyLassoPoints.first().x, s.liquifyLassoPoints.first().y)
                            s.liquifyLassoPoints.drop(1).forEach { lineTo(it.x, it.y) }
                        }
                        drawPath(path, color = Color(0xFFFF4D6D), style = Stroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))))
                    }
                }
            }
        }

        // v2.4.0 Parça 3 (Blok 5a): CAF Green/Red Sampling Brush Overlay
        if (s.activePanel == BottomPanel.CONTENT_AWARE_FILL_EDIT && s.cafBrushActive && !s.singleFingerPanMode) {
            val viewRef = s.canvasViewRef
            if (viewRef != null) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(viewRef, s.cafBrushAdd, s.cafBrushSize) {
                            detectDragGestures(
                                onDragStart = { pos -> s.cafActiveScreenStroke = listOf(pos) },
                                onDrag = { change, _ ->
                                    change.consume()
                                    s.cafActiveScreenStroke = s.cafActiveScreenStroke + change.position
                                },
                                onDragEnd = {
                                    val pts = s.cafActiveScreenStroke
                                    s.cafActiveScreenStroke = emptyList()
                                    if (pts.isNotEmpty()) {
                                        val docPts = pts.map { p ->
                                            val (dx, dy) = viewRef.screenToDoc(p.x, p.y)
                                            uniffi.kastamonushop_ffi.StrokeSample(dx, dy, 1f, 0f, 0f)
                                        }
                                        val entry = uniffi.kastamonushop_ffi.CafSamplingStrokeInput(s.cafBrushAdd, s.cafBrushSize, docPts)
                                        s.cafStrokes = (s.cafStrokes + entry).takeLast(32)
                                        if (s.cafBrushAdd && s.cafSampling != "custom") s.cafSampling = "custom"
                                        vm.updateProxyContentAwareFillPro(
                                            s.cafSampling, s.cafColorAdaptation, s.cafRotationAdaptation,
                                            s.cafScale, s.cafMirror, s.cafSampleAllLayers, s.cafStrokes
                                        )
                                    }
                                },
                                onDragCancel = { s.cafActiveScreenStroke = emptyList() }
                            )
                        }
                ) {
                    val col = if (s.cafBrushAdd) Color(0x664ADE80) else Color(0x66FF5252)
                    val rPx = (s.cafBrushSize * 0.5f * viewRef.getCurrentZoom()).coerceIn(8f, 120f)
                    s.cafActiveScreenStroke.forEach { pt -> drawCircle(col, radius = rPx, center = pt) }
                }
            }
        }

        // v2.4.0 Parça 3 (Blok 5b): CAS Blue Protect Mask Brush Overlay
        if (s.activePanel == BottomPanel.CONTENT_AWARE_SCALE_EDIT && s.casBrushActive && !s.singleFingerPanMode) {
            val viewRef = s.canvasViewRef
            if (viewRef != null) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(viewRef, s.casBrushAdd, s.casBrushSize) {
                            detectDragGestures(
                                onDragStart = { pos -> s.casActiveScreenStroke = listOf(pos) },
                                onDrag = { change, _ ->
                                    change.consume()
                                    s.casActiveScreenStroke = s.casActiveScreenStroke + change.position
                                },
                                onDragEnd = {
                                    val pts = s.casActiveScreenStroke
                                    s.casActiveScreenStroke = emptyList()
                                    if (pts.isNotEmpty()) {
                                        val docPts = pts.map { p ->
                                            val (dx, dy) = viewRef.screenToDoc(p.x, p.y)
                                            uniffi.kastamonushop_ffi.StrokeSample(dx, dy, 1f, 0f, 0f)
                                        }
                                        val entry = uniffi.kastamonushop_ffi.CafSamplingStrokeInput(s.casBrushAdd, s.casBrushSize, docPts)
                                        s.casProtectStrokes = (s.casProtectStrokes + entry).takeLast(32)
                                        vm.updateProxyContentAwareScalePro(s.casScaleX, s.casScaleY, s.casAmount, s.casProtectSkinTones, s.casProtectStrokes)
                                    }
                                },
                                onDragCancel = { s.casActiveScreenStroke = emptyList() }
                            )
                        }
                ) {
                    val col = if (s.casBrushAdd) Color(0x6660A5FA) else Color(0x66FF5252)
                    val rPx = (s.casBrushSize * 0.5f * viewRef.getCurrentZoom()).coerceIn(8f, 120f)
                    s.casActiveScreenStroke.forEach { pt -> drawCircle(col, radius = rPx, center = pt) }
                }
            }
        }

        // v2.4.0 Parça 3 (Blok 5c): Content-Aware Move Direct Finger Drag on Canvas
        if (s.activePanel == BottomPanel.CONTENT_AWARE_MOVE_EDIT && !s.singleFingerPanMode) {
            val viewRef = s.canvasViewRef
            if (viewRef != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(viewRef, s.camMoveMode, s.camMoveStructure, s.camMoveColor, s.camMoveScale, s.camMoveRotate, s.camMoveSampleAll) {
                            detectDragGestures(
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val zoom = viewRef.getCurrentZoom().coerceAtLeast(0.1f)
                                    s.camMoveDx = (s.camMoveDx + dragAmount.x / zoom).coerceIn(-1200f, 1200f)
                                    s.camMoveDy = (s.camMoveDy + dragAmount.y / zoom).coerceIn(-1200f, 1200f)
                                    vm.updateProxyContentAwareMove(
                                        s.camMoveDx, s.camMoveDy, s.camMoveMode,
                                        s.camMoveStructure.roundToInt(), s.camMoveColor.roundToInt(),
                                        s.camMoveScale, s.camMoveRotate, s.camMoveSampleAll
                                    )
                                }
                            )
                        }
                )
            }
        }

        // v0.9.9.5 Floating Touch-Point Magnifier / Loupe (Fine Brush, Eyedropper, Clone Stamp, Perspective Crop)
        val lPos = s.loupeScreenPos
        val lBmp = loupeBitmap
        if (lPos != null && lBmp != null) {
            FloatingLoupeOverlay(
                fingerScreenPos = lPos,
                loupeBitmap = lBmp,
                isEyedropper = state.activeTool == ActiveTool.EYEDROPPER,
                eyedropperColorHex = state.colorHex
            )
        }

        // Exit Save Confirmation Dialog ("Bu fotoğrafı kaydetmek istiyor musun?")
        if (s.showExitSaveDialog) {
            AlertDialog(
                onDismissRequest = { s.showExitSaveDialog = false },
                containerColor = Color(0xFF232530),
                titleContentColor = Color.White,
                textContentColor = Color(0xFFD8DCE8),
                title = { Text("Fotoğrafı Kaydet", fontWeight = FontWeight.SemiBold) },
                text = { Text("Bu fotoğrafı kaydetmek istiyor musun? Evet dersen Kaydedilen Fotoğraflar bölümüne eklenir.") },
                confirmButton = {
                    Button(
                        onClick = {
                            s.showExitSaveDialog = false
                            clearGpuPreview()
                            vm.quickSaveToGallery(context) {
                                onGoHome()
                            }
                        }
                    ) {
                        Text("Evet")
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = {
                            s.showExitSaveDialog = false
                            clearGpuPreview()
                            onGoHome()
                        }
                    ) {
                        Text("Hayır", color = Color.White)
                    }
                }
            )
        }

        // Studio v2.0 Minimal Floating OLED Top Bar (with Tek El + Ortala built-in)
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = StudioV2Colors.GlassBar,
            border = androidx.compose.foundation.BorderStroke(1.dp, StudioV2Colors.GlassBorder),
            tonalElevation = 6.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TopBarButton(
                    icon = Icons.Default.Home,
                    label = "Ana",
                    onClick = {
                        dismissLivePanelIfActive()
                        s.showExitSaveDialog = true
                    }
                )

                TopBarButton(
                    icon = Icons.AutoMirrored.Filled.Undo,
                    label = "Geri",
                    enabled = when (s.activePanel) {
                        BottomPanel.LIQUIFY_EDIT -> state.liquifyCanUndo
                        BottomPanel.PUPPET_WARP_EDIT -> s.puppetUndoStack.isNotEmpty()
                        BottomPanel.CURVES_LIVE -> s.curveUndoStack.isNotEmpty()
                        else -> state.docInfo?.canUndo == true
                    },
                    onClick = {
                        when (s.activePanel) {
                            BottomPanel.LIQUIFY_EDIT -> vm.liquifyUndo()
                            BottomPanel.CURVES_LIVE -> {
                                if (s.curveUndoStack.isNotEmpty()) {
                                    val prev = s.curveUndoStack.last()
                                    s.curveRedoStack = s.curveRedoStack + s.snapshotCurve()
                                    s.curveUndoStack = s.curveUndoStack.dropLast(1)
                                    s.restoreCurve(prev)
                                    if (s.isCurvesIdentity()) vm.resetProxyPreview() else vm.updateProxyAdjustment("curves", s.buildCurvesProJson())
                                    vm.refreshHistogram(force = true)
                                }
                            }
                            BottomPanel.PUPPET_WARP_EDIT -> {
                                if (s.puppetUndoStack.isNotEmpty()) {
                                    val prev = s.puppetUndoStack.last()
                                    s.puppetRedoStack = s.puppetRedoStack + listOf(s.puppetPins)
                                    s.puppetUndoStack = s.puppetUndoStack.dropLast(1)
                                    s.puppetPins = prev
                                    s.puppetSelectedIdx = s.puppetSelectedIdx.coerceIn(-1, prev.lastIndex)
                                    if (prev.isEmpty()) vm.resetProxyPreview() else vm.updateProxyPuppetWarp(prev, s.puppetMode, s.puppetDensity, s.puppetExpansion)
                                }
                            }
                            else -> {
                                if (s.activePanel == BottomPanel.MIRROR_EDIT || s.activePanel == BottomPanel.CROP_MENU) {
                                    dismissLivePanelIfActive()
                                }
                                vm.undo()
                            }
                        }
                    }
                )

                TopBarButton(
                    icon = Icons.AutoMirrored.Filled.Redo,
                    label = "İleri",
                    enabled = when (s.activePanel) {
                        BottomPanel.LIQUIFY_EDIT -> state.liquifyCanRedo
                        BottomPanel.PUPPET_WARP_EDIT -> s.puppetRedoStack.isNotEmpty()
                        BottomPanel.CURVES_LIVE -> s.curveRedoStack.isNotEmpty()
                        else -> state.docInfo?.canRedo == true
                    },
                    onClick = {
                        when (s.activePanel) {
                            BottomPanel.LIQUIFY_EDIT -> vm.liquifyRedo()
                            BottomPanel.CURVES_LIVE -> {
                                if (s.curveRedoStack.isNotEmpty()) {
                                    val next = s.curveRedoStack.last()
                                    s.curveUndoStack = s.curveUndoStack + s.snapshotCurve()
                                    s.curveRedoStack = s.curveRedoStack.dropLast(1)
                                    s.restoreCurve(next)
                                    if (s.isCurvesIdentity()) vm.resetProxyPreview() else vm.updateProxyAdjustment("curves", s.buildCurvesProJson())
                                    vm.refreshHistogram(force = true)
                                }
                            }
                            BottomPanel.PUPPET_WARP_EDIT -> {
                                if (s.puppetRedoStack.isNotEmpty()) {
                                    val next = s.puppetRedoStack.last()
                                    s.puppetUndoStack = s.puppetUndoStack + listOf(s.puppetPins)
                                    s.puppetRedoStack = s.puppetRedoStack.dropLast(1)
                                    s.puppetPins = next
                                    s.puppetSelectedIdx = s.puppetSelectedIdx.coerceIn(-1, next.lastIndex)
                                    if (next.isEmpty()) vm.resetProxyPreview() else vm.updateProxyPuppetWarp(next, s.puppetMode, s.puppetDensity, s.puppetExpansion)
                                }
                            }
                            else -> {
                                if (s.activePanel == BottomPanel.MIRROR_EDIT || s.activePanel == BottomPanel.CROP_MENU) {
                                    dismissLivePanelIfActive()
                                }
                                vm.redo()
                            }
                        }
                    }
                )

                TopBarButton(
                    icon = Icons.Default.PanTool,
                    label = "Tek El",
                    selected = s.singleFingerPanMode,
                    onClick = { s.singleFingerPanMode = !s.singleFingerPanMode }
                )

                TopBarButton(
                    icon = Icons.Default.CenterFocusStrong,
                    label = "Ortala",
                    onClick = { fitToSafeArea(s.pendingQuarterTurns, s.activePanel) }
                )

                TopBarButton(
                    icon = Icons.Default.Compare,
                    label = "Önce",
                    selected = state.compareOriginal,
                    onClick = { vm.toggleCompareOriginal() }
                )

                TopBarButton(
                    icon = Icons.Default.Layers,
                    label = "Katman",
                    selected = s.showLayers,
                    onClick = { s.showLayers = !s.showLayers }
                )

                Box {
                    TopBarButton(
                        icon = Icons.Default.Save,
                        label = "Kaydet",
                        onClick = { s.showExportMenu = true }
                    )
                    DropdownMenu(
                        expanded = s.showExportMenu,
                        onDismissRequest = { s.showExportMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Cihaz Galerisine Kaydet (PNG)", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary) },
                            onClick = {
                                s.showExportMenu = false
                                vm.saveToDeviceGallery(context)
                            }
                        )
                        HorizontalDivider()
                        listOf(
                            "png" to "PNG Dosya Olarak Kaydet (.png)",
                            "jpg" to "JPEG Resim (.jpg)",
                            "psd" to "Photoshop Katmanlı (.psd)",
                            "pcraft" to "StonebridgeCraft Proje (.pcraft)",
                            "webp" to "WebP (.webp)"
                        ).forEach { (ext, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    s.showExportMenu = false
                                    s.pendingExportFormat = ext
                                    exportDocLauncher.launch("stonebridgecraft.$ext")
                                }
                            )
                        }
                    }
                }

                Box {
                    TopBarButton(
                        icon = Icons.Default.MoreVert,
                        label = "Diğer",
                        selected = s.singleFingerPanMode,
                        onClick = { s.showOpenMenu = true }
                    )
                    DropdownMenu(
                        expanded = s.showOpenMenu,
                        onDismissRequest = { s.showOpenMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Fotoğraf Aç (Galeri)") },
                            onClick = {
                                s.showOpenMenu = false
                                s.openAsLayer = false
                                val pick = Intent(Intent.ACTION_GET_CONTENT).apply {
                                    type = "image/*"
                                    addCategory(Intent.CATEGORY_OPENABLE)
                                }
                                imageChooserLauncher.launch(Intent.createChooser(pick, "Fotoğraf Seç"))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Fotoğrafı Katman Olarak Ekle") },
                            onClick = {
                                s.showOpenMenu = false
                                s.openAsLayer = true
                                val pick = Intent(Intent.ACTION_GET_CONTENT).apply {
                                    type = "image/*"
                                    addCategory(Intent.CATEGORY_OPENABLE)
                                }
                                imageChooserLauncher.launch(Intent.createChooser(pick, "Katman İçin Fotoğraf Seç"))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Proje Aç (.psd / .pcraft)") },
                            onClick = {
                                s.showOpenMenu = false
                                s.openAsLayer = false
                                openDocLauncher.launch(arrayOf("*/*"))
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(if (s.singleFingerPanMode) "Tek Parmak Kaydır: AÇIK ✓" else "Tek Parmak Kaydır Modu") },
                            onClick = {
                                s.showOpenMenu = false
                                s.singleFingerPanMode = !s.singleFingerPanMode
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Fotoğrafı Ekrana Ortala") },
                            onClick = {
                                s.showOpenMenu = false
                                fitToSafeArea(s.pendingQuarterTurns, s.activePanel)
                            }
                        )
                    }
                }
            }
        }

        LaunchedEffect(s.showLayers) {
            vm.setLayersDrawerOpen(s.showLayers)
        }
        LaunchedEffect(s.showLayers, state.layers, state.docInfo) {
            if (s.showLayers) {
                vm.refreshLayerThumbnails(force = true)
            }
        }

        // Floating Quick Mask Mode control bar (v1.8.2)
        if (state.quickMaskMode) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 74.dp),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xEE2B1620),
                tonalElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Hızlı Maske", color = Color(0xFFFF6B81), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    ProPill(
                        selected = state.colorHex.equals("#000000", ignoreCase = true),
                        onClick = { vm.updateBrush(colorHex = "#000000"); vm.setTool(ActiveTool.BRUSH) },
                        label = { Text("Çıkar", fontSize = 11.sp) }
                    )
                    ProPill(
                        selected = state.colorHex.equals("#FFFFFF", ignoreCase = true),
                        onClick = { vm.updateBrush(colorHex = "#FFFFFF"); vm.setTool(ActiveTool.BRUSH) },
                        label = { Text("Ekle", fontSize = 11.sp) }
                    )
                    TextButton(onClick = { vm.toggleQuickMaskMode(false) }) {
                        Text("Seçime Dön", color = Color(0xFFFFD600), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Floating Mask Paint Mode control bar (Siyah: Gizle / Beyaz: Göster / Bitir)
        if (state.maskPaintMode) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 74.dp),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xEE1E2636),
                tonalElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Maske", color = Color(0xFF4F9CFF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    ProPill(
                        selected = state.colorHex.equals("#000000", ignoreCase = true),
                        onClick = { vm.updateBrush(colorHex = "#000000"); vm.setTool(ActiveTool.BRUSH) },
                        label = { Text("Gizle", fontSize = 11.sp) }
                    )
                    ProPill(
                        selected = state.colorHex.equals("#FFFFFF", ignoreCase = true),
                        onClick = { vm.updateBrush(colorHex = "#FFFFFF"); vm.setTool(ActiveTool.BRUSH) },
                        label = { Text("Göster", fontSize = 11.sp) }
                    )
                    TextButton(onClick = { vm.exitMaskPaintMode() }) {
                        Text("Bitir", color = Color(0xFFFFD600), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Safety pill if every layer is currently hidden
        if (state.layers.isNotEmpty() && state.layers.none { it.visible }) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 74.dp)
                    .clickable {
                        val target = state.layers.lastOrNull { it.isBackground } ?: state.layers.last()
                        vm.toggleLayerVisibility(target.id, true)
                    },
                shape = RoundedCornerShape(18.dp),
                color = Color(0xEE3A2418),
                tonalElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = Color(0xFFFFD600), modifier = Modifier.size(16.dp))
                    Text("Tüm katmanlar gizli — Göster", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Interactive On-Canvas Drag Overlay for Content-Aware Move / Extend (v1.8.0)
        if (s.activePanel == BottomPanel.CONTENT_AWARE_MOVE_EDIT) {
            val view = s.canvasViewRef
            val bounds = state.selectionBounds
            if (view != null && bounds != null) {
                val _tick = s.cameraTick
                val z = view.getCurrentZoom().coerceAtLeast(0.05f)
                val (ox0, oy0) = view.docToScreen(bounds.x0.toFloat(), bounds.y0.toFloat())
                val (ox1, oy1) = view.docToScreen(bounds.x1.toFloat(), bounds.y1.toFloat())
                val cxDoc = (bounds.x0 + bounds.x1) * 0.5f + s.camMoveDx
                val cyDoc = (bounds.y0 + bounds.y1) * 0.5f + s.camMoveDy
                val wDoc = (bounds.x1 - bounds.x0).coerceAtLeast(8) * (s.camMoveScale / 100f)
                val hDoc = (bounds.y1 - bounds.y0).coerceAtLeast(8) * (s.camMoveScale / 100f)
                val (tCx, tCy) = view.docToScreen(cxDoc, cyDoc)
                val hw = wDoc * z * 0.5f
                val hh = hDoc * z * 0.5f
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (s.singleFingerPanMode) Modifier else Modifier.pointerInput(bounds, z) {
                                detectDragGestures(
                                    onDragEnd = {
                                        vm.updateProxyContentAwareMove(
                                            s.camMoveDx, s.camMoveDy, s.camMoveMode,
                                            s.camMoveStructure.roundToInt(), s.camMoveColor.roundToInt(),
                                            s.camMoveScale, s.camMoveRotate, s.camMoveSampleAll
                                        )
                                    }
                                ) { change, dragAmount ->
                                    change.consume()
                                    s.camMoveDx += dragAmount.x / z
                                    s.camMoveDy += dragAmount.y / z
                                }
                            }
                        )
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // Original selection dashed box
                        drawRect(
                            color = Color(0x88FFFFFF),
                            topLeft = Offset(ox0, oy0),
                            size = Size((ox1 - ox0).coerceAtLeast(2f), (oy1 - oy0).coerceAtLeast(2f)),
                            style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
                        )
                        // Destination box (rotated & scaled around destination center)
                        val rad = Math.toRadians(s.camMoveRotate.toDouble())
                        val cosR = kotlin.math.cos(rad).toFloat()
                        val sinR = kotlin.math.sin(rad).toFloat()
                        fun rotPt(lx: Float, ly: Float) = Offset(tCx + lx * cosR - ly * sinR, tCy + lx * sinR + ly * cosR)
                        val p0 = rotPt(-hw, -hh); val p1 = rotPt(hw, -hh)
                        val p2 = rotPt(hw, hh); val p3 = rotPt(-hw, hh)
                        val quad = Path().apply { moveTo(p0.x, p0.y); lineTo(p1.x, p1.y); lineTo(p2.x, p2.y); lineTo(p3.x, p3.y); close() }
                        val accent = if (s.camMoveMode == "extend") Color(0xFF2ED573) else Color(0xFF4F9CFF)
                        drawPath(quad, color = accent.copy(alpha = 0.16f))
                        drawPath(quad, color = accent, style = Stroke(width = 3f))
                        drawLine(accent.copy(alpha = 0.7f), Offset((ox0 + ox1) * 0.5f, (oy0 + oy1) * 0.5f), Offset(tCx, tCy), strokeWidth = 2f)
                        drawCircle(accent, radius = 7f, center = Offset(tCx, tCy))
                    }
                }
            }
        }

        // Right Floating Layers Drawer
        if (s.showLayers) {
            val activeLayer = state.layers.firstOrNull { it.isActive }
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(end = 10.dp, top = 74.dp, bottom = 130.dp)
                    .width(268.dp)
                    .fillMaxHeight(0.62f),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                tonalElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Katmanlar", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White)
                        Row {
                            IconButton(onClick = { vm.addLayer() }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Add, contentDescription = "Yeni Katman", tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = { vm.deleteActiveLayer() }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "Katmanı Sil", tint = Color(0xFFFF6B6B), modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                    // Synergistic Layer Quick-Actions: Taşı, fx, Yukarı, Aşağı, Çoğalt, Birleştir
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                s.showLayers = false
                                vm.setTool(ActiveTool.NONE)
                                s.layerDx = 0f; s.layerDy = 0f; s.layerScalePct = 100f; s.layerScaleXPct = 100f; s.layerScaleYPct = 100f; s.layerRotDeg = 0f
                                vm.loadActiveLayerPreview()
                                s.activePanel = BottomPanel.LAYER_TRANSFORM
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.Default.OpenWith, contentDescription = "Taşı", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = {
                                s.showLayers = false
                                vm.setTool(ActiveTool.NONE)
                                s.showFxFineTune = false
                                vm.beginLayerProxyPreview()
                                s.activePanel = BottomPanel.LAYER_FX_EDIT
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = "Efekt (fx)", tint = Color(0xFFFFD54F), modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { vm.moveLayerUp() }, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = "Yukarı", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { vm.moveLayerDown() }, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = "Aşağı", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { vm.duplicateActiveLayer() }, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Çoğalt", tint = Color.White, modifier = Modifier.size(17.dp))
                        }
                        IconButton(onClick = { vm.mergeLayerDown() }, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.MergeType, contentDescription = "Birleştir", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }

                    // v1.5.0 Non-Destructive Layer Pro row: Maske, Kırpma (↳), Grup/Dağıt, Blend If
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(top = 4.dp, bottom = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isActiveGroup = activeLayer?.isGroup == true
                        val isActiveClipped = activeLayer?.clipped == true
                        val hasActiveMask = activeLayer?.hasMask == true
                        ProPill(
                            selected = hasActiveMask || state.maskPaintMode,
                            onClick = {
                                s.showLayers = false
                                s.activePanel = BottomPanel.LAYER_MASK_EDIT
                            },
                            label = { Text(if (hasActiveMask) "Maske ✓" else "Maske", fontSize = 10.sp) }
                        )
                        ProPill(
                            selected = isActiveClipped,
                            onClick = { vm.toggleClippingMask() },
                            label = { Text(if (isActiveClipped) "↳ Kırp" else "Kırpma", fontSize = 10.sp) }
                        )
                        ProPill(
                            selected = isActiveGroup,
                            onClick = { if (isActiveGroup) vm.ungroupActiveLayer() else vm.groupActiveLayer() },
                            label = { Text(if (isActiveGroup) "Dağıt" else "Grupla", fontSize = 10.sp) }
                        )
                        ProPill(
                            selected = s.activePanel == BottomPanel.BLEND_IF_EDIT,
                            onClick = {
                                s.showLayers = false
                                vm.setTool(ActiveTool.NONE)
                                s.blendIfThisBlack = 0f; s.blendIfThisWhite = 255f
                                s.blendIfUnderBlack = 0f; s.blendIfUnderWhite = 255f; s.blendIfFeather = 20f
                                vm.beginLayerProxyPreview()
                                s.activePanel = BottomPanel.BLEND_IF_EDIT
                            },
                            label = { Text("Blend If", fontSize = 10.sp) }
                        )
                        ProPill(
                            selected = false,
                            onClick = { vm.copyActiveLayerStyle() },
                            label = { Text("Stil Kopyala", fontSize = 10.sp) }
                        )
                        if (state.hasCopiedStyle) {
                            ProPill(
                                selected = true,
                                onClick = { vm.pasteLayerStyleToActive() },
                                label = { Text("Stil Yapıştır", fontSize = 10.sp) }
                            )
                        }
                        if (activeLayer?.isText == true) {
                            ProPill(
                                selected = false,
                                onClick = { vm.rasterizeActiveTextLayer() },
                                label = { Text("Rasterleştir", fontSize = 10.sp) }
                            )
                        }
                        if (state.selectionBounds != null) {
                            ProPill(
                                selected = false,
                                onClick = { vm.layerViaCopy() },
                                label = { Text("Seçimden Katman", fontSize = 10.sp) }
                            )
                        }
                    }
                    if (activeLayer != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        CompactSliderRow(
                            label = "Opaklık",
                            valueText = "${(activeLayer.opacity * 100).toInt()}%",
                            value = activeLayer.opacity,
                            range = 0f..1f,
                            defaultValue = 1f
                        ) {
                            vm.setLayerOpacity(activeLayer.id, it)
                        }
                        Spacer(modifier = Modifier.height(4.dp))

                        Box(modifier = Modifier.fillMaxWidth()) {
                            val currentOpt = BEGINNER_BLEND_MODES.firstOrNull {
                                it.engineName.equals(activeLayer.blendMode, ignoreCase = true)
                            }
                            OutlinedButton(
                                onClick = { s.showBlendMenu = true },
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Karışım: ${currentOpt?.labelTr ?: activeLayer.blendMode}",
                                    fontSize = 11.sp,
                                    color = Color.White
                                )
                            }
                            DropdownMenu(
                                expanded = s.showBlendMenu,
                                onDismissRequest = { s.showBlendMenu = false }
                            ) {
                                BEGINNER_BLEND_MODES.forEach { opt ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(opt.labelTr, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                                Text(opt.hintTr, fontSize = 10.sp, color = Color.Gray)
                                            }
                                        },
                                        onClick = {
                                            s.showBlendMenu = false
                                            vm.setLayerBlendMode(activeLayer.id, opt.engineName)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(state.layers, key = { it.id.toLong() }) { layer ->
                            val bg = if (layer.isActive) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(bg)
                                    .clickable { vm.selectLayer(layer.id) }
                                    .padding(horizontal = 6.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { vm.toggleLayerVisibility(layer.id, !layer.visible) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        if (layer.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = "Görünürlük",
                                        tint = if (layer.visible) Color.White else Color.Gray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f).padding(start = (4 + layer.depth.toInt().coerceAtMost(3) * 12).dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        if (layer.clipped) {
                                            Text("↳", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4F9CFF))
                                        }
                                        if (layer.isGroup) {
                                            Icon(Icons.Default.Folder, contentDescription = null, tint = Color(0xFFFFD54F), modifier = Modifier.size(14.dp))
                                        }
                                        Text(layer.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (layer.fxBadges.isNotEmpty()) {
                                            Text(
                                                text = "fx: " + layer.fxBadges.joinToString(" · "),
                                                fontSize = 9.sp,
                                                color = Color(0xFFFFD54F),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Text(
                                            "${layer.blendMode} · ${(layer.opacity * 100).toInt()}%",
                                            fontSize = 10.sp,
                                            color = Color(0xFFB8BCC8)
                                        )
                                        if (layer.hasMask) {
                                            Text(
                                                text = if (layer.maskEnabled) "Maske" else "Maske (Kapalı)",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (layer.maskEnabled) Color(0xFF2ED573) else Color(0xFFFF8A80)
                                            )
                                        }
                                    }
                                }
                                if (layer.isBackground) {
                                    IconButton(
                                        onClick = { vm.toggleLayerLocked(layer.id, !layer.locked) },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(
                                            if (layer.locked) Icons.Default.Lock else Icons.Default.LockOpen,
                                            contentDescription = if (layer.locked) "Fotoğraf Kilitli" else "Fotoğraf Kilidi Açık",
                                            tint = if (layer.locked) Color(0xFFFFB74D) else Color(0xFF8E93A6),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                // Mini layer preview box (checkerboard + layer content thumbnail)
                                val thumb = layerThumbs[layer.id]
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(7.dp))
                                        .border(1.dp, Color(0xFF454A5C), RoundedCornerShape(7.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val cell = 6.dp.toPx()
                                        val cols = (size.width / cell).toInt() + 1
                                        val rows = (size.height / cell).toInt() + 1
                                        for (ry in 0 until rows) {
                                            for (rx in 0 until cols) {
                                                val light = ((rx + ry) and 1) == 0
                                                drawRect(
                                                    color = if (light) Color(0xFFD6D8E0) else Color(0xFFA8ACB8),
                                                    topLeft = Offset(rx * cell, ry * cell),
                                                    size = Size(cell, cell)
                                                )
                                            }
                                        }
                                    }
                                    if (thumb != null) {
                                        Image(
                                            bitmap = thumb,
                                            contentDescription = "${layer.name} önizleme",
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Tune,
                                            contentDescription = null,
                                            tint = Color(0xFF2B2F3D),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Status pill above bottom dock
        state.statusMessage?.let { msg ->
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = if (s.measuredBottomBarPx > 10f) with(density) { s.measuredBottomBarPx.toDp() + 18.dp } else if (s.activePanel == BottomPanel.DOCK) 114.dp else 210.dp)
                    .clickable { vm.clearStatus() },
                shape = RoundedCornerShape(20.dp),
                color = Color(0xEE111217),
                tonalElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(msg, fontSize = 12.sp, color = Color.White)
                }
            }
        }

        // v1.7.2 PROCREATE-STYLE LEFT EDGE VERTICAL SIZE & OPACITY SIDEBAR
        if (s.activePanel == BottomPanel.DRAW_MENU || s.activePanel == BottomPanel.RETOUCH_MENU || s.activePanel == BottomPanel.SELECT_MENU) {
            LeftBrushSidebar(
                vm = vm,
                state = state,
                bucketTolerance = s.bucketTolerance,
                onBucketTolerance = { s.bucketTolerance = it },
                onBrushSizePreview = { s.brushSizePreviewTick++ },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 6.dp)
            )
        }

        // v1.7.2 FLOATING BRUSH / TOOL SETTINGS POPOVER (AUTO-CLOSES ON CANVAS TOUCH)
        if (s.showToolPopover && (s.activePanel == BottomPanel.DRAW_MENU || s.activePanel == BottomPanel.RETOUCH_MENU)) {
            ToolSettingsPopoverCard(
                vm = vm,
                state = state,
                bucketTolerance = s.bucketTolerance,
                onBucketTolerance = { s.bucketTolerance = it },
                bucketContiguous = s.bucketContiguous,
                onBucketContiguous = { s.bucketContiguous = it },
                onImportAbr = { importAbrLauncher.launch(arrayOf("*/*")) },
                onDismiss = { s.showToolPopover = false },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(
                        bottom = if (s.measuredBottomBarPx > 10f)
                            with(density) { s.measuredBottomBarPx.toDp() + 14.dp }
                        else 80.dp
                    )
            )
        }

        // v1.7.2 DIRECT 1-TAP PRO HSV COLOR WHEEL DIALOG
        if (s.showQuickColorWheel) {
            ProColorWheelDialog(
                initialHex = state.colorHex,
                initialOpacity = state.brushOpacity,
                showOpacity = true,
                onPickEyedropper = { vm.setTool(ActiveTool.EYEDROPPER) },
                onDismiss = { s.showQuickColorWheel = false },
                onConfirm = { pickedHex, pickedOpacity ->
                    vm.updateBrush(colorHex = pickedHex, opacity = pickedOpacity)
                    s.showQuickColorWheel = false
                }
            )
        }

        // Studio v2.0 Frosted-Glass Workspace Sheet (opens cleanly above fixed 4+1 dock)
        val openWs = s.activeWorkspaceV2
        if (openWs != null && (s.activePanel == BottomPanel.DOCK || s.activePanel == BottomPanel.DRAW_MENU || s.activePanel == BottomPanel.RETOUCH_MENU)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x55000000))
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) { s.activeWorkspaceV2 = null }
            )
            StudioWorkspaceSheetV2(
                workspace = openWs,
                pinnedIds = s.pinnedToolIdsV2,
                gridState = s.workspaceGridStates[openWs] ?: androidx.compose.foundation.lazy.grid.rememberLazyGridState(),
                initialGroup = when {
                    openWs == StudioWorkspace.DRAW && s.activePanel == BottomPanel.DRAW_MENU -> "Çizim"
                    openWs == StudioWorkspace.DRAW && s.activePanel == BottomPanel.RETOUCH_MENU -> "Rötuş & Onarım"
                    else -> "Tümü"
                },
                onTogglePin = { id ->
                    s.pinnedToolIdsV2 = if (id in s.pinnedToolIdsV2) s.pinnedToolIdsV2 - id else s.pinnedToolIdsV2 + id
                },
                onSelectTool = { spec ->
                    s.lastWorkspaceV2 = spec.workspace
                    s.activeWorkspaceV2 = null
                    val ctx = StudioLaunchContext(
                        vm = vm,
                        state = state,
                        s = s,
                        context = context,
                        onPushGpuMirrorPreview = { mode, ratio -> pushGpuMirrorPreview(mode, ratio) },
                        onClearGpuPreview = { clearGpuPreview() }
                    )
                    spec.onLaunch(ctx)
                },
                onDismiss = { s.activeWorkspaceV2 = null },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 84.dp, start = 8.dp, end = 8.dp)
            )
        }

        // Studio v2.0 Fixed 4+1 Bottom Dock & Focused Tool Host
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .onGloballyPositioned { coords ->
                    val h = coords.size.height.toFloat()
                    if (kotlin.math.abs(h - s.measuredBottomBarPx) > 4f) {
                        s.measuredBottomBarPx = h
                    }
                },
            shape = RoundedCornerShape(22.dp),
            color = StudioV2Colors.GlassBar,
            border = androidx.compose.foundation.BorderStroke(1.dp, StudioV2Colors.GlassBorder),
            tonalElevation = 10.dp
        ) {
            StudioBottomPanelHost(
                vm = vm,
                state = state,
                s = s,
                context = context,
                studioFonts = studioFonts,
                cinemaThumbs = cinemaThumbs,
                galleryThumbs = galleryThumbs,
                lightDistortThumbs = lightDistortThumbs,
                dockScrollState = dockScrollState,
                drawScrollState = drawScrollState,
                selectScrollState = selectScrollState,
                adjustScrollState = adjustScrollState,
                cropScrollState = cropScrollState,
                effectScrollState = effectScrollState,
                onImportFont = { importFontLauncher.launch(arrayOf("*/*")) },
                onImportXmp = { importXmpLauncher.launch(arrayOf("*/*")) },
                onImportLut = { importLutLauncher.launch(arrayOf("*/*")) },
                onImportAbr = { importAbrLauncher.launch(arrayOf("*/*")) },
                onPushCurveGpu = { y0, y1, y2, y3, y4 -> pushCurveGpu(y0, y1, y2, y3, y4) },
                onPushGpuMirrorPreview = { mode, ratio -> pushGpuMirrorPreview(mode, ratio) },
                onFitToSafeArea = { turns, panel -> fitToSafeArea(turns, panel) },
                onClearGpuPreview = { clearGpuPreview() },
                onClearAdjustGpuPreviewOnly = { clearAdjustGpuPreviewOnly() },
                onPushGpuTransformPreview = { turns, fh, fv -> pushGpuTransformPreview(turns, fh, fv) }
            )
        }

        // Studio v2.0 Instant Command Palette (Fuzzy Search across all registered tools)
        if (s.showSearchPaletteV2) {
            StudioCommandPaletteV2(
                pinnedIds = s.pinnedToolIdsV2,
                onTogglePin = { id ->
                    s.pinnedToolIdsV2 = if (id in s.pinnedToolIdsV2) s.pinnedToolIdsV2 - id else s.pinnedToolIdsV2 + id
                },
                onSelectTool = { spec ->
                    s.lastWorkspaceV2 = spec.workspace
                    s.showSearchPaletteV2 = false
                    s.activeWorkspaceV2 = null
                    val ctx = StudioLaunchContext(
                        vm = vm,
                        state = state,
                        s = s,
                        context = context,
                        onPushGpuMirrorPreview = { mode, ratio -> pushGpuMirrorPreview(mode, ratio) },
                        onClearGpuPreview = { clearGpuPreview() }
                    )
                    spec.onLaunch(ctx)
                },
                onDismiss = { s.showSearchPaletteV2 = false }
            )
        }
    }
}


@Composable
private fun RetouchTrailOverlay(
    trailPoints: List<Offset>,
    activeTool: ActiveTool,
    brushSize: Float,
    zoomProvider: () -> Float
) {
    val trailColor = when (activeTool) {
        ActiveTool.REMOVE_TOOL -> Color(0x99FF2EA6)
        ActiveTool.SPOT_HEAL -> Color(0x88FF5A5F)
        ActiveTool.SELECT_BRUSH_SUB -> Color(0x88FF4D4F)
        ActiveTool.SELECT_QUICK -> Color(0x882ED573)
        ActiveTool.SELECT_LASSO -> Color(0xDD00E5FF)
        ActiveTool.SELECT_MAGNETIC_LASSO -> Color(0xDDFFD600)
        ActiveTool.ADJUST_BRUSH -> Color(0x88FFA502)
        ActiveTool.HEALING_BRUSH -> Color(0x882ED573)
        ActiveTool.HISTORY_BRUSH -> Color(0x8870A1FF)
        ActiveTool.ART_HISTORY_BRUSH -> Color(0x88C56CF0)
        else -> Color(0x884F9CFF)
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val n = trailPoints.size
        if (n == 0) return@Canvas
        val strokeW = if (activeTool == ActiveTool.SELECT_LASSO || activeTool == ActiveTool.SELECT_MAGNETIC_LASSO) {
            6f
        } else {
            (brushSize * zoomProvider()).coerceIn(4f, 360f)
        }
        if (n == 1) {
            drawCircle(trailColor, radius = strokeW * 0.5f, center = trailPoints[0])
        } else {
            val p = Path().apply {
                moveTo(trailPoints[0].x, trailPoints[0].y)
                for (i in 1 until n) {
                    lineTo(trailPoints[i].x, trailPoints[i].y)
                }
            }
            drawPath(
                path = p,
                color = trailColor,
                style = Stroke(
                    width = strokeW,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                )
            )
        }
    }
}


@Composable
private fun SymmetryAxisOverlay(
    mode: String,
    canvasViewRef: VulkanCanvasView?,
    docW: Float,
    docH: Float,
    cameraTick: Int
) {
    if (canvasViewRef == null) return
    Canvas(modifier = Modifier.fillMaxSize()) {
        if (cameraTick < 0) return@Canvas
        val (l, t) = canvasViewRef.docToScreen(0f, 0f)
        val (r, b) = canvasViewRef.docToScreen(docW, docH)
        val (cx, cy) = canvasViewRef.docToScreen(docW * 0.5f, docH * 0.5f)
        val dash = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
        val col = Color(0xBB00E5FF)
        if (mode == "vertical" || mode == "dual") {
            drawLine(col, Offset(cx, t), Offset(cx, b), strokeWidth = 2.2f, pathEffect = dash)
        }
        if (mode == "horizontal" || mode == "dual") {
            drawLine(col, Offset(l, cy), Offset(r, cy), strokeWidth = 2.2f, pathEffect = dash)
        }
        if (mode == "diagonal") {
            drawLine(col, Offset(l, t), Offset(r, b), strokeWidth = 2.2f, pathEffect = dash)
        }
    }
}
