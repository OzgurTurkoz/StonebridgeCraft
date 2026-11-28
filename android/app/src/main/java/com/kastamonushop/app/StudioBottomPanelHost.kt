package com.kastamonushop.app

import androidx.compose.animation.togetherWith

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

@Composable
internal fun StudioBottomPanelHost(
    vm: EditorViewModel,
    state: EditorUiState,
    s: StudioSessionState,
    context: Context,
    studioFonts: List<EditorViewModel.StudioFontOption>,
    cinemaThumbs: List<ImageBitmap>,
    galleryThumbs: List<ImageBitmap>,
    lightDistortThumbs: List<ImageBitmap>,
    dockScrollState: ScrollState,
    drawScrollState: ScrollState,
    selectScrollState: ScrollState,
    adjustScrollState: ScrollState,
    cropScrollState: ScrollState,
    effectScrollState: ScrollState,
    onImportFont: () -> Unit,
    onImportXmp: () -> Unit,
    onImportLut: () -> Unit,
    onImportAbr: () -> Unit,
    onPushCurveGpu: (Float, Float, Float, Float, Float) -> Unit,
    onPushGpuMirrorPreview: (MirrorModeOption, Float) -> Unit,
    onFitToSafeArea: (Int, BottomPanel) -> Unit,
    onClearGpuPreview: () -> Unit,
    onClearAdjustGpuPreviewOnly: () -> Unit,
    onPushGpuTransformPreview: (Int, Boolean, Boolean) -> Unit
) {
            val activeColor = remember(state.colorHex) {
                runCatching { Color(AndroidColor.parseColor(state.colorHex)) }
                    .getOrDefault(Color.Red)
            }

            androidx.compose.animation.AnimatedContent(
                targetState = s.activePanel,
                transitionSpec = {
                    (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(140)) +
                        androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(160)) { it / 6 })
                        .togetherWith(
                            androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(90))
                        )
                },
                label = "bottom_panel_transition"
            ) { panel ->
            when (panel) {
                BottomPanel.DOCK -> {
                    StudioFixedDockV2(
                        activeWorkspace = s.activeWorkspaceV2,
                        onSelectWorkspace = { ws ->
                            s.showLayers = false
                            s.activeWorkspaceV2 = if (s.activeWorkspaceV2 == ws) null else ws
                        },
                        onOpenSearch = {
                            s.showLayers = false
                            s.activeWorkspaceV2 = null
                            s.showSearchPaletteV2 = true
                        }
                    )
                }

                BottomPanel.DRAW_MENU -> CompactDrawBottomBar(
                    vm = vm,
                    state = state,
                    popoverOpen = s.showToolPopover,
                    onTogglePopover = { s.showToolPopover = !s.showToolPopover },
                    onOpenColorWheel = {
                        s.showToolPopover = false
                        s.showQuickColorWheel = true
                    },
                    onOpenToolPicker = {
                        s.showToolPopover = false
                        s.activeWorkspaceV2 = StudioWorkspace.DRAW
                    },
                    onClose = {
                        s.showToolPopover = false
                        vm.setTool(ActiveTool.NONE)
                        s.activePanel = BottomPanel.DOCK
                        s.activeWorkspaceV2 = s.lastWorkspaceV2 ?: StudioWorkspace.DRAW
                    }
                )

                BottomPanel.RETOUCH_MENU -> CompactRetouchBottomBar(
                    vm = vm,
                    state = state,
                    popoverOpen = s.showToolPopover,
                    onTogglePopover = { s.showToolPopover = !s.showToolPopover },
                    onOpenToolPicker = {
                        s.showToolPopover = false
                        s.activeWorkspaceV2 = StudioWorkspace.DRAW
                    },
                    onClose = {
                        s.showToolPopover = false
                        vm.setTool(ActiveTool.NONE)
                        s.activePanel = BottomPanel.DOCK
                        s.activeWorkspaceV2 = s.lastWorkspaceV2 ?: StudioWorkspace.DRAW
                    }
                )

                BottomPanel.SELECT_MENU -> SelectMenuPanel(
                    vm = vm,
                    state = state,
                    onOpenToolPicker = {
                        s.showToolPopover = false
                        s.activeWorkspaceV2 = StudioWorkspace.SELECT
                    },
                    onOpenContentAwareFill = {
                        s.cafSampling = "auto"; s.cafColorAdaptation = "default"
                        s.cafRotationAdaptation = "none"; s.cafScale = false
                        s.cafMirror = false; s.cafSampleAllLayers = false; s.cafOutput = "current"
                        vm.beginProxyPreview()
                        vm.updateProxyContentAwareFill("auto", "default", "none", false, false, false, "current")
                        s.activePanel = BottomPanel.CONTENT_AWARE_FILL_EDIT
                    },
                    onOpenContentAwareMove = {
                        s.camMoveDx = 0f; s.camMoveDy = 0f; s.camMoveMode = "move"
                        s.camMoveStructure = 4f; s.camMoveColor = 0f
                        s.camMoveScale = 100f; s.camMoveRotate = 0f; s.camMoveSampleAll = false
                        vm.setTool(ActiveTool.NONE)
                        vm.beginProxyPreview()
                        s.activePanel = BottomPanel.CONTENT_AWARE_MOVE_EDIT
                    },
                    onOpenSelectSky = {
                        vm.setTool(ActiveTool.NONE)
                        vm.beginProxyPreview()
                        vm.updateProxySelectSky(s.skyThreshold, s.skySoftness, s.skyMode, s.skySampleAll)
                        s.activePanel = BottomPanel.SELECT_SKY_EDIT
                    },
                    onOpenColorRange = {
                        vm.setTool(ActiveTool.NONE)
                        vm.beginProxyPreview()
                        vm.updateProxyColorRange(s.crPreset, s.crColorHex, s.crFuzziness, s.crInvert, s.crMode, s.crSampleAll)
                        s.activePanel = BottomPanel.SELECT_COLOR_RANGE_EDIT
                    },
                    onOpenFocusArea = {
                        vm.setTool(ActiveTool.NONE)
                        vm.beginProxyPreview()
                        vm.updateProxyFocusArea(s.focusRange, s.focusNoise, s.focusMode, s.focusSampleAll)
                        s.activePanel = BottomPanel.SELECT_FOCUS_AREA_EDIT
                    },
                    onOpenSelectModify = {
                        vm.setTool(ActiveTool.NONE)
                        vm.beginProxyPreview()
                        vm.updateProxyModifySelection(s.selModifyOp, s.selModifyRadius)
                        s.activePanel = BottomPanel.SELECT_MODIFY_EDIT
                    },
                    onClose = { vm.setTool(ActiveTool.NONE); s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2 }
                )

                BottomPanel.TEXT_EDIT -> {
                    val docW = (state.docInfo?.width?.toInt() ?: 2048).toFloat()
                    val docH = (state.docInfo?.height?.toInt() ?: 2048).toFloat()
                    TextProStudioPanel(
                        s = s,
                        docW = docW,
                        studioFonts = studioFonts,
                        onImportFont = { onImportFont() },
                        onCreateSelection = {
                            vm.createTextSelectionMask(
                                text = s.textDraft,
                                x = docW * s.textNormX,
                                y = docH * s.textNormY,
                                sizePx = s.textSizePx,
                                align = s.textAlign,
                                fontFamily = s.textFontFamily,
                                bold = s.textBold,
                                italic = s.textItalic,
                                caps = s.textCaps,
                                hScale = s.textHScale,
                                vScale = s.textVScale,
                                tracking = s.textTracking,
                                lineHeight = s.textLineHeight,
                                vertical = s.textVertical,
                                warpStyle = s.textWarpStyle,
                                warpBend = s.textWarpBend,
                                warpHDist = s.textWarpHDist,
                                warpVDist = s.textWarpVDist
                            )
                            s.activePanel = BottomPanel.DOCK
                        },
                        onRasterize = {
                            vm.rasterizeActiveTextLayer()
                            s.activePanel = BottomPanel.DOCK
                        },
                        onClose = {
                            s.resetTextProDefaults(docW)
                            vm.endProxyPreview()
                            s.activePanel = BottomPanel.DOCK
                            s.activeWorkspaceV2 = s.lastWorkspaceV2
                        },
                        onApply = {
                            s.textSnapX = false; s.textSnapY = false
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
                            vm.addOrEditTextLayerPro(
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
                            s.activePanel = BottomPanel.DOCK
                            s.activeWorkspaceV2 = s.lastWorkspaceV2
                        }
                    )
                }

                BottomPanel.ADJUST_MENU -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IconButton(onClick = { s.activePanel = BottomPanel.DOCK }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(adjustScrollState),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SlimToolChip(Icons.Default.AutoFixHigh, "Oto", false, onClick = { s.activePanel = BottomPanel.AUTO_ENHANCE_MENU }
                            )
                            SlimToolChip(Icons.Default.Camera, "Lens", false, onClick = {
                                    s.lensDistortion = 0f; s.lensVignette = 0f
                                    s.lensRedCyan = 0f; s.lensBlueYellow = 0f
                                    s.lensVertical = 0f; s.lensHorizontal = 0f
                                    vm.beginProxyPreview()
                                    s.activePanel = BottomPanel.LENS_CORRECTION_EDIT
                                }
                            )
                            SlimToolChip(Icons.Default.ColorLens, "3D LUT", false, onClick = {
                                    vm.beginProxyPreview()
                                    s.activePanel = BottomPanel.LUT_IMPORT_EDIT
                                }
                            )
                            SlimToolChip(Icons.Default.WbSunny, "Kamera", false, onClick = {
                                    s.camExposureVal = 0f; s.camShadowsVal = 0f; s.camHighlightsVal = 0f; s.camVibranceVal = 0f
                                    s.camTempVal = 0f; s.camTintVal = 0f; s.camClarityVal = 0f; s.camDehazeVal = 0f
                                    s.showCamFineTune = false
                                    vm.beginProxyPreview()
                                    s.activePanel = BottomPanel.CAMERA_LIGHT_LIVE
                                }
                            )
                            SlimToolChip(Icons.Default.Brightness6, "Işık", false, onClick = {
                                    s.brightnessVal = 0f
                                    s.contrastVal = 0f
                                    vm.beginProxyPreview()
                                    s.activePanel = BottomPanel.LIGHT_LIVE
                                }
                            )
                            SlimToolChip(Icons.Default.Palette, "Renk", false, onClick = {
                                    s.hueVal = 0f
                                    s.saturationVal = 0f
                                    vm.beginProxyPreview()
                                    s.activePanel = BottomPanel.COLOR_LIVE
                                }
                            )
                            SlimToolChip(Icons.Default.Equalizer, "Düzeyler", false, onClick = {
                                    s.levelsBlackVal = 0f
                                    s.levelsGammaVal = 1f
                                    s.levelsWhiteVal = 255f
                                    vm.beginProxyPreview()
                                    s.activePanel = BottomPanel.LEVELS_LIVE
                                }
                            )
                            SlimToolChip(Icons.Default.ColorLens, "Denge", false, onClick = {
                                    s.cbCyanRedVal = 0f
                                    s.cbMagentaGreenVal = 0f
                                    s.cbYellowBlueVal = 0f
                                    vm.beginProxyPreview()
                                    s.activePanel = BottomPanel.COLOR_BAL_LIVE
                                }
                            )
                            SlimToolChip(Icons.Default.ShowChart, "Eğriler", false, onClick = {
                                    s.curveBlackVal = 0.0f
                                    s.curveShadowVal = 0.25f
                                    s.curveMidVal = 0.50f
                                    s.curveHighVal = 0.75f
                                    s.curveWhiteVal = 1.0f
                                    s.curveOverlayExpanded = false
                                    vm.beginProxyPreview()
                                    s.activePanel = BottomPanel.CURVES_LIVE
                                }
                            )
                            SlimToolChip(Icons.Default.Gradient, "HSL", false, onClick = {
                                    s.hslBandIdx = 0
                                    s.hslHue = List(8) { 0f }
                                    s.hslSat = List(8) { 0f }
                                    s.hslLum = List(8) { 0f }
                                    vm.beginProxyPreview()
                                    s.activePanel = BottomPanel.HSL_MIXER_LIVE
                                }
                            )
                            SlimToolChip(Icons.Default.FilterFrames, "Poster", false, onClick = {
                                    s.posterMode = true
                                    s.posterLevels = 6f
                                    s.threshLevel = 128f
                                    s.posterTouched = false
                                    vm.beginProxyPreview()
                                    s.activePanel = BottomPanel.POSTER_THRESH_LIVE
                                }
                            )
                        }
                    }
                }

                BottomPanel.CAMERA_LIGHT_LIVE -> CameraRawProPanel(
                    s = s,
                    onPreview = { vm.updateProxyCameraRawJson(buildCameraRawProJson(s)) },
                    onImportXmp = onImportXmp,
                    onReset = {
                        resetCameraRawProState(s)
                        vm.resetProxyPreview()
                    },
                    onClose = { onClearAdjustGpuPreviewOnly(); s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2 },
                    onApply = {
                        vm.applyCameraRawJson(buildCameraRawProJson(s))
                        resetCameraRawProState(s)
                        vm.beginProxyPreview()
                    }
                )
                BottomPanel.GRADIENT_EDIT -> {
                    val docW = (state.docInfo?.width?.toInt() ?: 2048).toFloat()
                    val docH = (state.docInfo?.height?.toInt() ?: 2048).toFloat()
                    GradientEditPanel(
                        style = s.gradStyle, onStyle = { s.gradStyle = it },
                        startHex = s.gradStartHex, onStartHex = { s.gradStartHex = it },
                        endHex = s.gradEndHex, onEndHex = { s.gradEndHex = it },
                        opacity = s.gradOpacity, onOpacity = { s.gradOpacity = it },
                        onPreview = {
                            vm.updateProxyGradient(
                                docW * s.gradFromNormX, docH * s.gradFromNormY,
                                docW * s.gradToNormX, docH * s.gradToNormY,
                                s.gradStyle, s.gradStartHex, s.gradEndHex, s.gradOpacity
                            )
                        },
                        onClose = { vm.endProxyPreview(); s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2 },
                        onApply = {
                            vm.applyGradientFill(
                                docW * s.gradFromNormX, docH * s.gradFromNormY,
                                docW * s.gradToNormX, docH * s.gradToNormY,
                                s.gradStyle, s.gradStartHex, s.gradEndHex, s.gradOpacity
                            )
                            s.activePanel = BottomPanel.DOCK
                            s.activeWorkspaceV2 = s.lastWorkspaceV2
                        }
                    )
                }
                BottomPanel.SHAPE_EDIT -> {
                    val docW = (state.docInfo?.width?.toInt() ?: 2048).toFloat()
                    val docH = (state.docInfo?.height?.toInt() ?: 2048).toFloat()
                    ShapeEditPanel(
                        kind = s.shapeKind, onKind = { s.shapeKind = it },
                        fillHex = s.shapeFillHex, onFillHex = { s.shapeFillHex = it },
                        strokeWidth = s.shapeStrokeWidth, onStrokeWidth = { s.shapeStrokeWidth = it },
                        cornerRadius = s.shapeCornerRadius, onCornerRadius = { s.shapeCornerRadius = it },
                        sides = s.shapeSides, onSides = { s.shapeSides = it },
                        showFine = s.showShapeFineTune, onToggleFine = { s.showShapeFineTune = !s.showShapeFineTune },
                        onPreview = {
                            vm.updateProxyShape(
                                s.shapeKind,
                                docW * s.shapeX0Norm, docH * s.shapeY0Norm,
                                docW * s.shapeX1Norm, docH * s.shapeY1Norm,
                                s.shapeFillHex, s.shapeStrokeHex, s.shapeStrokeWidth, s.shapeCornerRadius, s.shapeSides
                            )
                        },
                        onClose = { vm.endProxyPreview(); s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2 },
                        onApply = {
                            vm.createShapeLayer(
                                s.shapeKind,
                                docW * s.shapeX0Norm, docH * s.shapeY0Norm,
                                docW * s.shapeX1Norm, docH * s.shapeY1Norm,
                                s.shapeFillHex, s.shapeStrokeHex, s.shapeStrokeWidth, s.shapeCornerRadius, s.shapeSides
                            )
                            s.activePanel = BottomPanel.DOCK
                            s.activeWorkspaceV2 = s.lastWorkspaceV2
                        }
                    )
                }
                BottomPanel.LIGHT_LIVE -> LightLivePanel(
                    brightness = s.brightnessVal,
                    contrast = s.contrastVal,
                    onBrightness = {
                        s.brightnessVal = it
                        vm.updateProxyAdjustment("brightnessContrast", """{"brightness":${s.brightnessVal.roundToInt()},"contrast":${s.contrastVal.roundToInt()}}""")
                    },
                    onContrast = {
                        s.contrastVal = it
                        vm.updateProxyAdjustment("brightnessContrast", """{"brightness":${s.brightnessVal.roundToInt()},"contrast":${s.contrastVal.roundToInt()}}""")
                    },
                    onClose = { onClearAdjustGpuPreviewOnly(); s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2 },
                    onApply = {
                        if (s.brightnessVal.roundToInt() != 0 || s.contrastVal.roundToInt() != 0) {
                            vm.applyAdjustmentOnce("brightnessContrast", """{"brightness":${s.brightnessVal.roundToInt()},"contrast":${s.contrastVal.roundToInt()}}""", "Işık & Kontrast")
                            s.brightnessVal = 0f; s.contrastVal = 0f
                        }
                    }
                )

                BottomPanel.COLOR_LIVE -> ColorLivePanel(
                    saturation = s.saturationVal,
                    hue = s.hueVal,
                    onSaturation = {
                        s.saturationVal = it
                        vm.updateProxyAdjustment("hueSaturation", """{"hue":${s.hueVal.roundToInt()},"saturation":${s.saturationVal.roundToInt()},"lightness":0}""")
                    },
                    onHue = {
                        s.hueVal = it
                        vm.updateProxyAdjustment("hueSaturation", """{"hue":${s.hueVal.roundToInt()},"saturation":${s.saturationVal.roundToInt()},"lightness":0}""")
                    },
                    onClose = { onClearAdjustGpuPreviewOnly(); s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2 },
                    onApply = {
                        if (s.saturationVal.roundToInt() != 0 || s.hueVal.roundToInt() != 0) {
                            vm.applyAdjustmentOnce("hueSaturation", """{"hue":${s.hueVal.roundToInt()},"saturation":${s.saturationVal.roundToInt()},"lightness":0}""", "Renk & Canlılık")
                            s.saturationVal = 0f; s.hueVal = 0f
                        }
                    }
                )

                BottomPanel.LEVELS_LIVE -> LevelsProPanel(
                    s = s,
                    histogram = state.histogram,
                    onPreview = {
                        vm.updateProxyAdjustment("levels", s.buildLevelsProJson())
                        vm.refreshHistogram()
                    },
                    onAuto = {
                        vm.computeAutoLevels { auto ->
                            when (s.levelsChannelIdx) {
                                1 -> s.levelsRed = s.levelsRed.copy(inBlack = auto.redBlack, inWhite = auto.redWhite)
                                2 -> s.levelsGreen = s.levelsGreen.copy(inBlack = auto.greenBlack, inWhite = auto.greenWhite)
                                3 -> s.levelsBlue = s.levelsBlue.copy(inBlack = auto.blueBlack, inWhite = auto.blueWhite)
                                else -> s.levelsRgb = s.levelsRgb.copy(inBlack = auto.rgbBlack, inWhite = auto.rgbWhite)
                            }
                            vm.updateProxyAdjustment("levels", s.buildLevelsProJson())
                            vm.refreshHistogram(force = true)
                        }
                    },
                    onReset = {
                        s.resetLevelsPro()
                        vm.resetProxyPreview()
                        vm.refreshHistogram(force = true)
                    },
                    onClose = {
                        vm.clearHistogram()
                        onClearAdjustGpuPreviewOnly()
                        s.activePanel = BottomPanel.DOCK
                        s.activeWorkspaceV2 = s.lastWorkspaceV2
                    },
                    onApply = {
                        vm.applyAdjustmentOnce("levels", s.buildLevelsProJson(), "Seviyeler")
                        s.resetLevelsPro()
                        vm.beginProxyPreview()
                        vm.refreshHistogram(force = true)
                    }
                )

                BottomPanel.COLOR_BAL_LIVE -> {
                    fun buildCbJson(): String =
                        "{\"shadows\":[${s.cbShadowCR.roundToInt()},${s.cbShadowMG.roundToInt()},${s.cbShadowYB.roundToInt()}],\"midtones\":[${s.cbCyanRedVal.roundToInt()},${s.cbMagentaGreenVal.roundToInt()},${s.cbYellowBlueVal.roundToInt()}],\"highlights\":[${s.cbHighCR.roundToInt()},${s.cbHighMG.roundToInt()},${s.cbHighYB.roundToInt()}],\"preserveLuminosity\":${s.cbPreserveLuminosity}}"
                    val curCR = when (s.cbToneIdx) { 0 -> s.cbShadowCR; 2 -> s.cbHighCR; else -> s.cbCyanRedVal }
                    val curMG = when (s.cbToneIdx) { 0 -> s.cbShadowMG; 2 -> s.cbHighMG; else -> s.cbMagentaGreenVal }
                    val curYB = when (s.cbToneIdx) { 0 -> s.cbShadowYB; 2 -> s.cbHighYB; else -> s.cbYellowBlueVal }
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        BottomEditorHeader(
                            title = "Renk Dengesi Pro",
                            subtitle = "Gölgeler, Orta Tonlar ve Aydınlıklar",
                            applyLabel = "Uygula",
                            onClose = {
                                onClearAdjustGpuPreviewOnly()
                                vm.endProxyPreview()
                                s.activePanel = BottomPanel.DOCK
                                s.activeWorkspaceV2 = s.lastWorkspaceV2
                            },
                            onApply = {
                                vm.applyAdjustmentOnce("colorBalance", buildCbJson(), "Renk dengesi")
                                s.resetColorBalancePro()
                                vm.beginProxyPreview()
                            }
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("Gölgeler", "Orta Tonlar", "Aydınlıklar").forEachIndexed { idx, lbl ->
                                ProPill(
                                    selected = s.cbToneIdx == idx,
                                    onClick = { s.cbToneIdx = idx },
                                    label = { Text(lbl, fontSize = 11.sp) }
                                )
                            }
                            ProPill(
                                selected = s.cbPreserveLuminosity,
                                onClick = {
                                    s.cbPreserveLuminosity = !s.cbPreserveLuminosity
                                    vm.updateProxyAdjustment("colorBalance", buildCbJson())
                                },
                                label = { Text(if (s.cbPreserveLuminosity) "Parlaklık Koru ✓" else "Parlaklık Koru", fontSize = 10.5.sp) }
                            )
                            ProPill(
                                selected = false,
                                onClick = {
                                    s.resetColorBalancePro()
                                    vm.resetProxyPreview()
                                },
                                label = { Text("Sıfırla", fontSize = 10.5.sp, color = Color(0xFFFF8A8A)) }
                            )
                        }
                        CompactSliderRow("Cyan / Kırmızı", "${curCR.roundToInt()}", curCR, -100f..100f, defaultValue = 0f) {
                            when (s.cbToneIdx) { 0 -> s.cbShadowCR = it; 2 -> s.cbHighCR = it; else -> s.cbCyanRedVal = it }
                            vm.updateProxyAdjustment("colorBalance", buildCbJson())
                        }
                        CompactSliderRow("Magenta / Yeşil", "${curMG.roundToInt()}", curMG, -100f..100f, defaultValue = 0f) {
                            when (s.cbToneIdx) { 0 -> s.cbShadowMG = it; 2 -> s.cbHighMG = it; else -> s.cbMagentaGreenVal = it }
                            vm.updateProxyAdjustment("colorBalance", buildCbJson())
                        }
                        CompactSliderRow("Sarı / Mavi", "${curYB.roundToInt()}", curYB, -100f..100f, defaultValue = 0f) {
                            when (s.cbToneIdx) { 0 -> s.cbShadowYB = it; 2 -> s.cbHighYB = it; else -> s.cbYellowBlueVal = it }
                            vm.updateProxyAdjustment("colorBalance", buildCbJson())
                        }
                    }
                }

                BottomPanel.CURVES_LIVE -> {
                    val activeNodes = s.activeCurveNodes()
                    val canDeleteNode = s.curveSelectedNodeIdx in 1 until (activeNodes.size - 1) && activeNodes.size > 2
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        BottomEditorHeader(
                            title = "Eğriler Pro (Curves)",
                            subtitle = when (s.curveEyedropperMode) {
                                1 -> "Siyah nokta için fotoğrafta en koyu alana dokun"
                                2 -> "Gri denge için fotoğrafta nötr alana dokun"
                                3 -> "Beyaz nokta için fotoğrafta en parlak alana dokun"
                                else -> "Noktaları sürükle veya boşluğa dokunarak yeni nokta ekle"
                            },
                            applyLabel = "Uygula",
                            onClose = {
                                s.curveOverlayExpanded = false
                                s.curveEyedropperMode = 0
                                vm.clearHistogram()
                                onClearAdjustGpuPreviewOnly()
                                vm.endProxyPreview()
                                s.activePanel = BottomPanel.DOCK
                                s.activeWorkspaceV2 = s.lastWorkspaceV2
                            },
                            onApply = {
                                s.curveEyedropperMode = 0
                                vm.applyAdjustmentOnce("curves", s.buildCurvesProJson(), "Eğriler")
                                s.resetAllCurves()
                                s.curveUndoStack = emptyList()
                                s.curveRedoStack = emptyList()
                                s.curveOverlayExpanded = true
                                vm.beginProxyPreview()
                                vm.refreshHistogram(force = true)
                            }
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("RGB", "Kırmızı", "Yeşil", "Mavi").forEachIndexed { idx, lbl ->
                                ProPill(
                                    selected = s.curveChannelIdx == idx,
                                    onClick = {
                                        // Bug #1 fix: NEVER call beginProxyPreview() here — switching channels preserves all channels!
                                        s.curveChannelIdx = idx
                                        s.curveSelectedNodeIdx = -1
                                    },
                                    label = { Text(lbl, fontSize = 11.5.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (canDeleteNode) {
                                ProPill(selected = false, onClick = {
                                    s.curveUndoStack = (s.curveUndoStack + s.snapshotCurve()).takeLast(25)
                                    s.curveRedoStack = emptyList()
                                    val updated = activeNodes.toMutableList().apply { removeAt(s.curveSelectedNodeIdx) }
                                    s.setActiveCurveNodes(updated)
                                    s.curveSelectedNodeIdx = -1
                                    if (s.isCurvesIdentity()) vm.resetProxyPreview() else vm.updateProxyAdjustment("curves", s.buildCurvesProJson())
                                    vm.refreshHistogram()
                                }, label = { Text("Nokta Sil", fontSize = 11.sp, color = Color(0xFFFF8A8A)) })
                            }
                            listOf(1 to "Siyah Damla", 2 to "Gri Damla", 3 to "Beyaz Damla").forEach { (mode, lbl) ->
                                ProPill(
                                    selected = s.curveEyedropperMode == mode,
                                    onClick = { s.curveEyedropperMode = if (s.curveEyedropperMode == mode) 0 else mode },
                                    label = { Text(lbl, fontSize = 10.5.sp) }
                                )
                            }
                            ProPill(selected = false, onClick = {
                                s.curveUndoStack = (s.curveUndoStack + s.snapshotCurve()).takeLast(25)
                                s.curveRedoStack = emptyList()
                                s.curveRgbNodes = listOf(0f to 0f, 0.25f to 0.18f, 0.50f to 0.50f, 0.75f to 0.82f, 1f to 1f)
                                s.curveChannelIdx = 0
                                vm.updateProxyAdjustment("curves", s.buildCurvesProJson())
                                vm.refreshHistogram()
                            }, label = { Text("S-Kontrast", fontSize = 11.sp) })
                            ProPill(selected = false, onClick = {
                                s.curveUndoStack = (s.curveUndoStack + s.snapshotCurve()).takeLast(25)
                                s.curveRedoStack = emptyList()
                                s.curveRgbNodes = listOf(0f to 0.08f, 0.25f to 0.28f, 0.50f to 0.52f, 0.75f to 0.78f, 1f to 0.94f)
                                s.curveChannelIdx = 0
                                vm.updateProxyAdjustment("curves", s.buildCurvesProJson())
                                vm.refreshHistogram()
                            }, label = { Text("Mat Film", fontSize = 11.sp) })
                            ProPill(selected = false, onClick = {
                                s.curveUndoStack = (s.curveUndoStack + s.snapshotCurve()).takeLast(25)
                                s.curveRedoStack = emptyList()
                                s.curveRgbNodes = listOf(0f to 0f, 0.25f to 0.34f, 0.50f to 0.62f, 0.75f to 0.85f, 1f to 1f)
                                s.curveChannelIdx = 0
                                vm.updateProxyAdjustment("curves", s.buildCurvesProJson())
                                vm.refreshHistogram()
                            }, label = { Text("Aydınlık", fontSize = 11.sp) })
                            ProPill(selected = false, onClick = {
                                // Bug #9 fix: Reset active channel to straight diagonal WITHOUT hiding curveOverlayExpanded!
                                s.curveUndoStack = (s.curveUndoStack + s.snapshotCurve()).takeLast(25)
                                s.curveRedoStack = emptyList()
                                s.resetActiveCurveChannel()
                                s.curveOverlayExpanded = true
                                if (s.isCurvesIdentity()) vm.resetProxyPreview() else vm.updateProxyAdjustment("curves", s.buildCurvesProJson())
                                vm.refreshHistogram(force = true)
                            }, label = { Text("Kanal Sıfırla", fontSize = 11.sp, color = Color(0xFFFFB74D)) })
                            ProPill(selected = false, onClick = {
                                // Bug #9 fix: Reset ALL channels to straight diagonal WITHOUT hiding curveOverlayExpanded!
                                s.curveUndoStack = (s.curveUndoStack + s.snapshotCurve()).takeLast(25)
                                s.curveRedoStack = emptyList()
                                s.resetAllCurves()
                                s.curveOverlayExpanded = true
                                vm.resetProxyPreview()
                                vm.refreshHistogram(force = true)
                            }, label = { Text("Tümü Sıfırla", fontSize = 11.sp, color = Color(0xFFFF8A8A)) })
                        }
                    }
                }

                BottomPanel.CROP_MENU -> {
                    val hasPendingTransform = s.pendingQuarterTurns != 0 || s.pendingFlipH || s.pendingFlipV
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IconButton(
                            onClick = {
                                if (hasPendingTransform) onClearGpuPreview()
                                s.activePanel = BottomPanel.DOCK
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(cropScrollState),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SlimToolChip(Icons.Default.Crop, "Oran Kırp", false, onClick = {
                                    if (hasPendingTransform) onClearGpuPreview()
                                    s.canvasViewRef?.fitToDocument(0)
                                    s.cropScale = 0.90f
                                    s.cropFreeMode = false
                                    s.cropScale = 0.90f
                                    val dw0 = state.docInfo?.width?.toInt() ?: 2048
                                    val dh0 = state.docInfo?.height?.toInt() ?: 2048
                                    val r0 = computeCropRect(dw0, dh0, s.selectedCropPreset.ratio, 0.90f)
                                    s.cropNormL = (r0[0].toFloat() / dw0).coerceIn(0f, 1f)
                                    s.cropNormT = (r0[1].toFloat() / dh0).coerceIn(0f, 1f)
                                    s.cropNormR = ((r0[0] + r0[2]).toFloat() / dw0).coerceIn(0f, 1f)
                                    s.cropNormB = ((r0[1] + r0[3]).toFloat() / dh0).coerceIn(0f, 1f)
                                    s.activePanel = BottomPanel.CROP_RATIO_EDIT
                                }
                            )
                            SlimToolChip(Icons.Default.Transform, "Perspektif", false, onClick = {
                                    if (hasPendingTransform) onClearGpuPreview()
                                    s.canvasViewRef?.fitToDocument(0)
                                    s.pTl = Offset(0.12f, 0.12f)
                                    s.pTr = Offset(0.88f, 0.12f)
                                    s.pBr = Offset(0.88f, 0.88f)
                                    s.pBl = Offset(0.12f, 0.88f)
                                    s.activePanel = BottomPanel.PERSPECTIVE_CROP_EDIT
                                }
                            )
                            SlimToolChip(Icons.Default.PhotoSizeSelectLarge, "Boyut", false, onClick = {
                                    if (hasPendingTransform) onClearGpuPreview()
                                    val dw0 = state.docInfo?.width?.toInt() ?: 1080
                                    val dh0 = state.docInfo?.height?.toInt() ?: 1080
                                    s.resizeWidthText = dw0.toString()
                                    s.resizeHeightText = dh0.toString()
                                    s.resizeLockAspect = true
                                    s.resizeResample = "lanczos"
                                    vm.beginProxyPreview()
                                    s.activePanel = BottomPanel.IMAGE_RESIZE_EDIT
                                }
                            )
                            SlimToolChip(Icons.Default.AspectRatio, "Akıllı Ölçek", false, onClick = {
                                    if (hasPendingTransform) onClearGpuPreview()
                                    s.casScaleX = 100f; s.casScaleY = 100f; s.casAmount = 100f; s.casProtectSkinTones = true
                                    vm.beginProxyPreview()
                                    s.activePanel = BottomPanel.CONTENT_AWARE_SCALE_EDIT
                                }
                            )
                            SlimToolChip(Icons.AutoMirrored.Filled.RotateLeft, "90° Sol", false, onClick = {
                                    s.pendingQuarterTurns = (s.pendingQuarterTurns + 3) % 4
                                    onPushGpuTransformPreview(s.pendingQuarterTurns, s.pendingFlipH, s.pendingFlipV)
                                }
                            )
                            SlimToolChip(Icons.AutoMirrored.Filled.RotateRight, "90° Sağ", false, onClick = {
                                    s.pendingQuarterTurns = (s.pendingQuarterTurns + 1) % 4
                                    onPushGpuTransformPreview(s.pendingQuarterTurns, s.pendingFlipH, s.pendingFlipV)
                                }
                            )
                            SlimToolChip(Icons.Default.Flip, "Yatay", s.pendingFlipH, onClick = {
                                    s.pendingFlipH = !s.pendingFlipH
                                    onPushGpuTransformPreview(s.pendingQuarterTurns, s.pendingFlipH, s.pendingFlipV)
                                }
                            )
                            SlimToolChip(Icons.Default.SwapVert, "Dikey", s.pendingFlipV, onClick = {
                                    s.pendingFlipV = !s.pendingFlipV
                                    onPushGpuTransformPreview(s.pendingQuarterTurns, s.pendingFlipH, s.pendingFlipV)
                                }
                            )
                        }
                        if (hasPendingTransform) {
                            Button(
                                onClick = {
                                    val t = s.pendingQuarterTurns
                                    val fh = s.pendingFlipH
                                    val fv = s.pendingFlipV
                                    s.pendingQuarterTurns = 0
                                    s.pendingFlipH = false
                                    s.pendingFlipV = false
                                    vm.applyTransformBatch(t, fh, fv)
                                    s.activePanel = BottomPanel.DOCK
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("Uygula", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                BottomPanel.CROP_RATIO_EDIT -> {
                    val docW = state.docInfo?.width?.toInt() ?: 2048
                    val docH = state.docInfo?.height?.toInt() ?: 2048
                    val cropX = (s.cropNormL * docW).roundToInt().coerceIn(0, (docW - 4).coerceAtLeast(0))
                    val cropY = (s.cropNormT * docH).roundToInt().coerceIn(0, (docH - 4).coerceAtLeast(0))
                    val cropW = ((s.cropNormR - s.cropNormL) * docW).roundToInt().coerceIn(4, (docW - cropX).coerceAtLeast(4))
                    val cropH = ((s.cropNormB - s.cropNormT) * docH).roundToInt().coerceIn(4, (docH - cropY).coerceAtLeast(4))
                    val syncFromPreset = { ratio: Float?, sc: Float ->
                        val r = computeCropRect(docW, docH, ratio, sc)
                        s.cropNormL = (r[0].toFloat() / docW).coerceIn(0f, 1f)
                        s.cropNormT = (r[1].toFloat() / docH).coerceIn(0f, 1f)
                        s.cropNormR = ((r[0] + r[2]).toFloat() / docW).coerceIn(0f, 1f)
                        s.cropNormB = ((r[1] + r[3]).toFloat() / docH).coerceIn(0f, 1f)
                    }
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BottomEditorHeader(
                            title = "Kırp (${cropW}×${cropH})",
                            subtitle = "4 köşeden boyutlandır veya içinden tutup taşı",
                            applyLabel = "Uygula",
                            onClose = { s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2 },
                            onApply = {
                                vm.cropCanvas(cropX, cropY, cropW, cropH)
                                s.activePanel = BottomPanel.DOCK
                                s.activeWorkspaceV2 = s.lastWorkspaceV2
                            }
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(effectScrollState),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ProPill(
                                selected = s.cropFreeMode,
                                onClick = { s.cropFreeMode = true },
                                label = { Text("Serbest", fontSize = 11.sp) }
                            )
                            CROP_PRESETS.forEach { preset ->
                                ProPill(
                                    selected = !s.cropFreeMode && preset == s.selectedCropPreset,
                                    onClick = {
                                        s.cropFreeMode = false
                                        s.selectedCropPreset = preset
                                        syncFromPreset(preset.ratio, s.cropScale)
                                    },
                                    label = { Text(preset.label, fontSize = 11.sp) }
                                )
                            }
                        }
                        CompactSliderRow("Alan", "%${(s.cropScale * 100).roundToInt()}", s.cropScale, 0.40f..1.0f, defaultValue = 1.0f) {
                            s.cropScale = it
                            syncFromPreset(if (s.cropFreeMode) null else s.selectedCropPreset.ratio, it)
                        }
                    }
                }

                BottomPanel.PERSPECTIVE_CROP_EDIT -> {
                    val docW = (state.docInfo?.width?.toInt() ?: 2048).toFloat()
                    val docH = (state.docInfo?.height?.toInt() ?: 2048).toFloat()
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BottomEditorHeader(
                            title = "Perspektif Kırpma",
                            subtitle = "4 mavi köşeyi belge/nesne köşelerine sürükle, Uygula de",
                            applyLabel = "Uygula",
                            onClose = { s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2 },
                            onApply = {
                                vm.perspectiveCrop(
                                    s.pTl.x * docW, s.pTl.y * docH,
                                    s.pTr.x * docW, s.pTr.y * docH,
                                    s.pBr.x * docW, s.pBr.y * docH,
                                    s.pBl.x * docW, s.pBl.y * docH
                                )
                                s.activePanel = BottomPanel.DOCK
                                s.activeWorkspaceV2 = s.lastWorkspaceV2
                            }
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Eğik belge, tablo veya bina cephesini düzleştirir.",
                                color = Color(0xFFB4B9CB),
                                fontSize = 11.sp
                            )
                            TextButton(onClick = {
                                s.pTl = Offset(0.12f, 0.12f)
                                s.pTr = Offset(0.88f, 0.12f)
                                s.pBr = Offset(0.88f, 0.88f)
                                s.pBl = Offset(0.12f, 0.88f)
                            }) {
                                Text("Sıfırla", fontSize = 11.sp)
                            }
                        }
                    }
                }

                BottomPanel.FILTER_MENU -> FilterMenuPanel(
                    onClose = { vm.setTool(ActiveTool.NONE); s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2 },
                    onBlur = { s.blurRadiusVal = 0f; vm.beginProxyPreview(); s.activePanel = BottomPanel.BLUR_EDIT },
                    onSharpen = { s.sharpenAmountVal = 0f; vm.beginProxyPreview(); s.activePanel = BottomPanel.SHARPEN_EDIT },
                    onMotion = {
                        s.motionBlurAngle = 0f; s.motionBlurDistance = 24f
                        vm.beginProxyPreview(); vm.updateProxyMotionBlur(s.motionBlurAngle, s.motionBlurDistance)
                        s.activePanel = BottomPanel.MOTION_BLUR_EDIT
                    },
                    onRadial = {
                        s.radialBlurAmount = 14f; s.radialBlurMethod = "zoom"
                        vm.beginProxyPreview(); vm.updateProxyRadialBlur(s.radialBlurAmount, s.radialBlurMethod)
                        s.activePanel = BottomPanel.RADIAL_BLUR_EDIT
                    },
                    onGallery = {
                        s.galleryKind = "none"; s.galleryAmount = 45f; s.galleryDetail = 50f
                        vm.loadGalleryThumbs(); vm.beginProxyPreview()
                        s.activePanel = BottomPanel.FILTER_GALLERY_EDIT
                    },
                    onLightDistort = {
                        s.ldKind = "none"; s.ldAmount = 50f; s.ldDetail = 45f; s.ldX = 35f; s.ldY = 30f; s.ldVariant = "moviePrime"
                        vm.loadLightDistortThumbs(); vm.beginProxyPreview()
                        s.activePanel = BottomPanel.LIGHT_DISTORT_EDIT
                    }
                )

                BottomPanel.BLUR_EDIT -> BlurEditPanel(
                    radius = s.blurRadiusVal,
                    onRadius = { s.blurRadiusVal = it; vm.updateProxyBlur(it) },
                    onClose = { onClearAdjustGpuPreviewOnly(); s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2 },
                    onApply = {
                        if (s.blurRadiusVal > 0.2f) {
                            vm.applyBlurFast(s.blurRadiusVal)
                            s.blurRadiusVal = 0f
                            vm.beginProxyPreview()
                        }
                    }
                )

                BottomPanel.SHARPEN_EDIT -> SharpenEditPanel(
                    amount = s.sharpenAmountVal,
                    onAmount = { s.sharpenAmountVal = it; vm.updateProxySharpen(it) },
                    onClose = { onClearAdjustGpuPreviewOnly(); s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2 },
                    onApply = {
                        if (s.sharpenAmountVal > 0.2f) {
                            vm.applySharpenFast(s.sharpenAmountVal)
                            s.sharpenAmountVal = 0f
                            vm.beginProxyPreview()
                        }
                    }
                )
                BottomPanel.QUICK_ACTIONS_MENU -> QuickActionsPanel(
                    onClose = { vm.setTool(ActiveTool.NONE); s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2 },
                    onRemoveBg = { vm.smartRemoveBackground(); s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2 },
                    onPortraitBlur = { vm.smartPortraitBlur(18f); s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2 },
                    onObjectRemove = {
                        vm.setTool(ActiveTool.REMOVE_TOOL)
                        s.activePanel = BottomPanel.RETOUCH_MENU
                        vm.showStatus("Silmek istediğin nesnenin üstünü boya veya etrafını çiz", 3000L)
                    }
                )
                BottomPanel.HSL_MIXER_LIVE -> HslMixerPanel(
                    bandIdx = s.hslBandIdx, onBand = { s.hslBandIdx = it },
                    hues = s.hslHue, sats = s.hslSat, lums = s.hslLum,
                    tatActive = s.hslTatActive, onTatToggle = { s.hslTatActive = !s.hslTatActive },
                    tatParamIdx = s.hslTatParamIdx, onTatParam = { s.hslTatParamIdx = it },
                    onResetBand = {
                        val i = s.hslBandIdx.coerceIn(0, 7)
                        s.hslHue = s.hslHue.toMutableList().apply { this[i] = 0f }
                        s.hslSat = s.hslSat.toMutableList().apply { this[i] = 0f }
                        s.hslLum = s.hslLum.toMutableList().apply { this[i] = 0f }
                        vm.updateProxyHslMixer(s.hslHue, s.hslSat, s.hslLum)
                    },
                    onResetAll = {
                        s.resetHslPro()
                        vm.resetProxyPreview()
                    },
                    onUpdate = { h, sat, l ->
                        s.hslHue = h; s.hslSat = sat; s.hslLum = l
                        vm.updateProxyHslMixer(h, sat, l)
                    },
                    onClose = { s.hslTatActive = false; vm.endProxyPreview(); s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2 },
                    onApply = {
                        vm.applyHslMixer(s.hslHue, s.hslSat, s.hslLum)
                        val keepTat = s.hslTatActive
                        s.resetHslPro()
                        s.hslTatActive = keepTat
                        vm.beginProxyPreview()
                    }
                )
                BottomPanel.POSTER_THRESH_LIVE -> PosterThreshPanel(
                    isPoster = s.posterMode,
                    isTouched = s.posterTouched,
                    onMode = { isP ->
                        s.posterMode = isP
                        if (s.posterTouched) {
                            if (isP) vm.updateProxyAdjustment("posterize", """{"levels":${s.posterLevels.roundToInt()}}""")
                            else vm.updateProxyAdjustment("threshold", """{"level":${s.threshLevel.roundToInt()}}""")
                        }
                    },
                    posterLevels = s.posterLevels,
                    onPosterLevels = {
                        val prevInt = s.posterLevels.roundToInt()
                        val nextInt = it.roundToInt()
                        val wasTouched = s.posterTouched
                        s.posterTouched = true
                        s.posterLevels = it
                        if (!wasTouched || nextInt != prevInt) {
                            vm.updateProxyAdjustment("posterize", """{"levels":$nextInt}""")
                        }
                    },
                    threshLevel = s.threshLevel,
                    onThreshLevel = {
                        val prevInt = s.threshLevel.roundToInt()
                        val nextInt = it.roundToInt()
                        val wasTouched = s.posterTouched
                        s.posterTouched = true
                        s.threshLevel = it
                        if (!wasTouched || nextInt != prevInt) {
                            vm.updateProxyAdjustment("threshold", """{"level":$nextInt}""")
                        }
                    },
                    onReset = {
                        s.posterTouched = false
                        s.posterLevels = 8f
                        s.threshLevel = 128f
                        vm.resetProxyPreview()
                    },
                    onClose = { vm.endProxyPreview(); s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2 },
                    onApply = {
                        if (s.posterTouched) {
                            if (s.posterMode) vm.applyAdjustmentOnce("posterize", """{"levels":${s.posterLevels.roundToInt()}}""", "Posterize")
                            else vm.applyAdjustmentOnce("threshold", """{"level":${s.threshLevel.roundToInt()}}""", "Eşik")
                            s.posterTouched = false
                            vm.beginProxyPreview()
                        }
                    }
                )
                BottomPanel.AUTO_ENHANCE_MENU -> AutoEnhancePanel(
                    onClose = { s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2 },
                    onPick = { mode, lbl -> vm.applyAutoEnhance(mode, lbl); s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2 }
                )
                BottomPanel.CINEMA_LOOK_EDIT -> CinemaLookPanel(
                    thumbs = cinemaThumbs,
                    preset = s.cinemaPreset, onPreset = { s.cinemaPreset = it },
                    intensity = s.cinemaIntensity, onIntensity = { s.cinemaIntensity = it },
                    vignette = s.cinemaVignette, onVignette = { s.cinemaVignette = it },
                    grain = s.cinemaGrain, onGrain = { s.cinemaGrain = it },
                    onPreview = { vm.updateProxyCinemaLook(s.cinemaPreset, s.cinemaIntensity, s.cinemaVignette, s.cinemaGrain) },
                    onClose = { vm.endProxyPreview(); s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2 },
                    onApply = {
                        vm.applyCinemaLook(s.cinemaPreset, s.cinemaIntensity, s.cinemaVignette, s.cinemaGrain)
                        vm.beginProxyPreview()
                    }
                )
                BottomPanel.FILTER_GALLERY_EDIT -> FilterGalleryPanel(
                    thumbs = galleryThumbs,
                    kind = s.galleryKind,
                    onKind = { k ->
                        s.galleryKind = k
                        vm.updateProxyGalleryFilter(k, s.galleryAmount, s.galleryDetail)
                    },
                    amount = s.galleryAmount,
                    onAmount = { a ->
                        s.galleryAmount = a
                        if (s.galleryKind != "none") vm.updateProxyGalleryFilter(s.galleryKind, a, s.galleryDetail)
                    },
                    detail = s.galleryDetail,
                    onDetail = { d ->
                        s.galleryDetail = d
                        if (s.galleryKind != "none") vm.updateProxyGalleryFilter(s.galleryKind, s.galleryAmount, d)
                    },
                    onClose = { vm.endProxyPreview(); s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2 },
                    onApply = { lbl ->
                        if (s.galleryKind != "none") {
                            vm.applyGalleryFilter(s.galleryKind, s.galleryAmount, s.galleryDetail, lbl)
                            s.galleryKind = "none"
                            vm.beginProxyPreview()
                        }
                    }
                )
                BottomPanel.LIGHT_DISTORT_EDIT -> LightDistortPanel(
                    thumbs = lightDistortThumbs,
                    kind = s.ldKind,
                    onKind = { k ->
                        s.ldKind = k
                        when (k) {
                            "lensFlare" -> { s.ldAmount = 55f; s.ldX = 35f; s.ldY = 30f; s.ldVariant = "moviePrime" }
                            "lensBlur" -> { s.ldAmount = 40f; s.ldDetail = 40f; s.ldVariant = "hexagon" }
                            "wave" -> { s.ldAmount = 40f; s.ldDetail = 55f; s.ldVariant = "sine" }
                            "spherize" -> { s.ldAmount = 55f; s.ldVariant = "normal" }
                            "twirl" -> { s.ldAmount = 90f; s.ldVariant = "" }
                        }
                        if (k == "none") vm.resetProxyPreview()
                        else vm.updateProxyLightDistort(k, s.ldAmount, s.ldDetail, s.ldX, s.ldY, s.ldVariant)
                    },
                    amount = s.ldAmount,
                    onAmount = { v ->
                        s.ldAmount = v
                        if (s.ldKind != "none") vm.updateProxyLightDistort(s.ldKind, v, s.ldDetail, s.ldX, s.ldY, s.ldVariant)
                    },
                    detail = s.ldDetail,
                    onDetail = { v ->
                        s.ldDetail = v
                        if (s.ldKind != "none") vm.updateProxyLightDistort(s.ldKind, s.ldAmount, v, s.ldX, s.ldY, s.ldVariant)
                    },
                    posX = s.ldX,
                    onPosX = { v ->
                        s.ldX = v
                        if (s.ldKind == "lensFlare") vm.updateProxyLightDistort(s.ldKind, s.ldAmount, s.ldDetail, v, s.ldY, s.ldVariant)
                    },
                    posY = s.ldY,
                    onPosY = { v ->
                        s.ldY = v
                        if (s.ldKind == "lensFlare") vm.updateProxyLightDistort(s.ldKind, s.ldAmount, s.ldDetail, s.ldX, v, s.ldVariant)
                    },
                    variant = s.ldVariant,
                    onVariant = { v ->
                        s.ldVariant = v
                        if (s.ldKind != "none") vm.updateProxyLightDistort(s.ldKind, s.ldAmount, s.ldDetail, s.ldX, s.ldY, v)
                    },
                    onClose = { vm.endProxyPreview(); s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2 },
                    onApply = { lbl ->
                        if (s.ldKind != "none") {
                            vm.applyLightDistort(s.ldKind, s.ldAmount, s.ldDetail, s.ldX, s.ldY, s.ldVariant, lbl)
                            s.ldKind = "none"
                            vm.beginProxyPreview()
                        }
                    }
                )
                BottomPanel.PUPPET_WARP_EDIT -> {
                    fun refreshPuppet(pins: List<PuppetPinPoint> = s.puppetPins, mode: String = s.puppetMode, dens: String = s.puppetDensity, exp: Float = s.puppetExpansion, showM: Boolean = s.puppetShowMesh) {
                        if (pins.isEmpty()) vm.resetProxyPreview() else vm.updateProxyPuppetWarp(pins, mode, dens, exp)
                        if (showM) vm.fetchPuppetMeshSegments(pins, mode, dens, exp) { s.puppetMeshSegments = it }
                    }
                    PuppetWarpPanel(
                        pinCount = s.puppetPins.size,
                        selectedPin = s.puppetPins.getOrNull(s.puppetSelectedIdx),
                        mode = s.puppetMode,
                        density = s.puppetDensity,
                        expansion = s.puppetExpansion,
                        showMesh = s.puppetShowMesh,
                        onMode = { m ->
                            s.puppetMode = m
                            refreshPuppet(mode = m)
                        },
                        onDensity = { d ->
                            s.puppetDensity = d
                            refreshPuppet(dens = d)
                        },
                        onExpansion = { ex ->
                            s.puppetExpansion = ex
                            refreshPuppet(exp = ex)
                        },
                        onToggleMesh = {
                            val nextM = !s.puppetShowMesh
                            s.puppetShowMesh = nextM
                            if (nextM) vm.fetchPuppetMeshSegments(s.puppetPins, s.puppetMode, s.puppetDensity, s.puppetExpansion) { s.puppetMeshSegments = it }
                        },
                        onRotateSelected = { deg ->
                            val idx = s.puppetSelectedIdx
                            if (idx in s.puppetPins.indices) {
                                val old = s.puppetPins[idx]
                                val updated = s.puppetPins.toMutableList()
                                updated[idx] = PuppetPinPoint(old.srcX, old.srcY, old.dstX, old.dstY, deg, old.depth)
                                s.puppetPins = updated
                                refreshPuppet(pins = updated)
                            }
                        },
                        onDepthDelta = { delta ->
                            val idx = s.puppetSelectedIdx
                            if (idx in s.puppetPins.indices) {
                                s.puppetUndoStack = s.puppetUndoStack + listOf(s.puppetPins)
                                s.puppetRedoStack = emptyList()
                                val old = s.puppetPins[idx]
                                val updated = s.puppetPins.toMutableList()
                                updated[idx] = PuppetPinPoint(old.srcX, old.srcY, old.dstX, old.dstY, old.rotateDeg, (old.depth + delta).coerceIn(-10, 10))
                                s.puppetPins = updated
                                refreshPuppet(pins = updated)
                            }
                        },
                        onNudgeSelected = { ndx, ndy ->
                            val idx = s.puppetSelectedIdx
                            if (idx in s.puppetPins.indices) {
                                s.puppetUndoStack = s.puppetUndoStack + listOf(s.puppetPins)
                                s.puppetRedoStack = emptyList()
                                val old = s.puppetPins[idx]
                                val updated = s.puppetPins.toMutableList()
                                updated[idx] = PuppetPinPoint(old.srcX, old.srcY, old.dstX + ndx, old.dstY + ndy, old.rotateDeg, old.depth)
                                s.puppetPins = updated
                                refreshPuppet(pins = updated)
                            }
                        },
                        onDeleteSelected = {
                            if (s.puppetSelectedIdx in s.puppetPins.indices) {
                                s.puppetUndoStack = s.puppetUndoStack + listOf(s.puppetPins)
                                s.puppetRedoStack = emptyList()
                                val next = s.puppetPins.toMutableList().apply { removeAt(s.puppetSelectedIdx) }
                                s.puppetPins = next
                                s.puppetSelectedIdx = next.lastIndex
                                refreshPuppet(pins = next)
                            }
                        },
                        onReset = {
                            if (s.puppetPins.isNotEmpty()) {
                                s.puppetUndoStack = s.puppetUndoStack + listOf(s.puppetPins)
                                s.puppetRedoStack = emptyList()
                            }
                            s.puppetPins = emptyList()
                            s.puppetSelectedIdx = -1
                            vm.resetProxyPreview()
                            if (s.puppetShowMesh) vm.fetchPuppetMeshSegments(emptyList(), s.puppetMode, s.puppetDensity, s.puppetExpansion) { s.puppetMeshSegments = it }
                        },
                        onClose = {
                            s.puppetPins = emptyList()
                            s.puppetSelectedIdx = -1
                            s.puppetShowMesh = false
                            s.puppetMeshSegments = emptyList()
                            vm.endProxyPreview()
                            s.activePanel = BottomPanel.DOCK
                        },
                        onApply = {
                            if (s.puppetPins.isNotEmpty()) {
                                vm.applyPuppetWarp(s.puppetPins, s.puppetMode, s.puppetDensity, s.puppetExpansion)
                                s.puppetPins = emptyList()
                                s.puppetSelectedIdx = -1
                                s.puppetMeshSegments = emptyList()
                                vm.beginProxyPreview()
                            }
                        }
                    )
                }

                BottomPanel.IMAGE_RESIZE_EDIT -> ImageResizePanel(
                    origW = state.docInfo?.width?.toInt() ?: 1080,
                    origH = state.docInfo?.height?.toInt() ?: 1080,
                    widthText = s.resizeWidthText,
                    heightText = s.resizeHeightText,
                    lockAspect = s.resizeLockAspect,
                    resample = s.resizeResample,
                    onWidthText = { txt ->
                        val clean = txt.filter { it.isDigit() }.take(4)
                        s.resizeWidthText = clean
                        if (s.resizeLockAspect) {
                            val ow = (state.docInfo?.width?.toInt() ?: 1080).coerceAtLeast(1)
                            val oh = (state.docInfo?.height?.toInt() ?: 1080).coerceAtLeast(1)
                            val nw = clean.toIntOrNull()
                            if (nw != null && nw > 0) {
                                s.resizeHeightText = ((nw.toFloat() * oh / ow).roundToInt().coerceIn(16, 8192)).toString()
                            }
                        }
                        val cw = s.resizeWidthText.toIntOrNull()
                        val ch = s.resizeHeightText.toIntOrNull()
                        if (cw != null && ch != null && cw >= 16 && ch >= 16) {
                            vm.updateProxyImageResize(cw.coerceIn(16, 8192), ch.coerceIn(16, 8192), s.resizeResample)
                        }
                    },
                    onHeightText = { txt ->
                        val clean = txt.filter { it.isDigit() }.take(4)
                        s.resizeHeightText = clean
                        if (s.resizeLockAspect) {
                            val ow = (state.docInfo?.width?.toInt() ?: 1080).coerceAtLeast(1)
                            val oh = (state.docInfo?.height?.toInt() ?: 1080).coerceAtLeast(1)
                            val nh = clean.toIntOrNull()
                            if (nh != null && nh > 0) {
                                s.resizeWidthText = ((nh.toFloat() * ow / oh).roundToInt().coerceIn(16, 8192)).toString()
                            }
                        }
                        val cw = s.resizeWidthText.toIntOrNull()
                        val ch = s.resizeHeightText.toIntOrNull()
                        if (cw != null && ch != null && cw >= 16 && ch >= 16) {
                            vm.updateProxyImageResize(cw.coerceIn(16, 8192), ch.coerceIn(16, 8192), s.resizeResample)
                        }
                    },
                    onToggleLock = { s.resizeLockAspect = !s.resizeLockAspect },
                    onPreset = { pw, ph ->
                        val cw = pw.coerceIn(16, 8192)
                        val ch = ph.coerceIn(16, 8192)
                        s.resizeWidthText = cw.toString()
                        s.resizeHeightText = ch.toString()
                        vm.updateProxyImageResize(cw, ch, s.resizeResample)
                    },
                    onResample = {
                        s.resizeResample = it
                        val cw = s.resizeWidthText.toIntOrNull() ?: (state.docInfo?.width?.toInt() ?: 1080)
                        val ch = s.resizeHeightText.toIntOrNull() ?: (state.docInfo?.height?.toInt() ?: 1080)
                        vm.updateProxyImageResize(cw, ch, it)
                    },
                    onClose = { vm.endProxyPreview(); s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2 },
                    onApply = {
                        val tw = s.resizeWidthText.toIntOrNull()?.coerceIn(16, 8192) ?: (state.docInfo?.width?.toInt() ?: 1080)
                        val th = s.resizeHeightText.toIntOrNull()?.coerceIn(16, 8192) ?: (state.docInfo?.height?.toInt() ?: 1080)
                        vm.resizeImage(tw, th, s.resizeResample)
                        s.activePanel = BottomPanel.DOCK
                        s.activeWorkspaceV2 = s.lastWorkspaceV2
                    }
                )
                BottomPanel.MOTION_BLUR_EDIT -> MotionBlurPanel(
                    angle = s.motionBlurAngle, onAngle = { s.motionBlurAngle = it },
                    distance = s.motionBlurDistance, onDistance = { s.motionBlurDistance = it },
                    onPreview = { vm.updateProxyMotionBlur(s.motionBlurAngle, s.motionBlurDistance) },
                    onClose = { vm.endProxyPreview(); s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2 },
                    onApply = {
                        if (s.motionBlurDistance >= 1f) {
                            vm.applyMotionBlur(s.motionBlurAngle, s.motionBlurDistance)
                            s.motionBlurDistance = 0f
                            vm.beginProxyPreview()
                        }
                    }
                )
                BottomPanel.RADIAL_BLUR_EDIT -> RadialBlurPanel(
                    amount = s.radialBlurAmount, onAmount = { s.radialBlurAmount = it },
                    method = s.radialBlurMethod, onMethod = { s.radialBlurMethod = it },
                    onPreview = { vm.updateProxyRadialBlur(s.radialBlurAmount, s.radialBlurMethod) },
                    onClose = { vm.endProxyPreview(); s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2 },
                    onApply = {
                        if (s.radialBlurAmount >= 1f) {
                            vm.applyRadialBlur(s.radialBlurAmount, s.radialBlurMethod)
                            s.radialBlurAmount = 0f
                            vm.beginProxyPreview()
                        }
                    }
                )
                BottomPanel.LIQUIFY_EDIT -> LiquifyEditPanel(
                    tool = s.liquifyTool, onTool = { s.liquifyTool = it; s.liquifyLassoPoints = emptyList() },
                    size = s.liquifySize, onSize = { s.liquifySize = it },
                    pressure = s.liquifyPressure, onPressure = { s.liquifyPressure = it },
                    density = s.liquifyDensity, onDensity = { s.liquifyDensity = it },
                    rate = s.liquifyRate, onRate = { s.liquifyRate = it },
                    pinEdges = s.liquifyPinEdges, onPinEdges = { s.liquifyPinEdges = it; vm.setLiquifyPinEdges(it) },
                    reconstructPct = s.liquifyReconstructPct, onReconstructAll = { s.liquifyReconstructPct = it; vm.liquifyReconstructAll(it) },
                    onMaskAction = { vm.liquifyMaskAction(it) },
                    onReset = { s.liquifyReconstructPct = 0f; s.liquifyLassoPoints = emptyList(); vm.resetLiquify() },
                    onClose = { vm.cancelLiquify(); s.liquifyCursorScreen = null; s.liquifyLassoPoints = emptyList(); s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2 },
                    onApply = {
                        s.liquifyCursorScreen = null
                        s.liquifyLassoPoints = emptyList()
                        s.liquifyReconstructPct = 0f
                        vm.commitLiquify()
                        vm.beginLiquify()
                    }
                )
                BottomPanel.EFFECT_MENU -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IconButton(onClick = { s.activePanel = BottomPanel.DOCK }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Row(
                            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SlimToolChip(Icons.Default.FilterBAndW, "Siyah-Beyaz", state.bwLayerId != null, onClick = { vm.toggleBlackAndWhite() }
                            )
                            SlimToolChip(Icons.Default.InvertColors, "Negatif", state.invertLayerId != null, onClick = { vm.toggleInvert() }
                            )
                            SlimToolChip(Icons.Default.Compare, "Ayna", false, onClick = {
                                    s.selectedMirrorMode = MIRROR_MODES.first()
                                    s.mirrorAxisRatio = 0.50f
                                    s.canvasViewRef?.fitToDocument(0)
                                    onPushGpuMirrorPreview(s.selectedMirrorMode, s.mirrorAxisRatio)
                                    s.activePanel = BottomPanel.MIRROR_EDIT
                                }
                            )
                            SlimToolChip(Icons.Default.Waves, "Sıvılaştır", false, onClick = {
                                    s.liquifyTool = "forwardWarp"
                                    val docMax = maxOf(state.docInfo?.width?.toInt() ?: 1024, state.docInfo?.height?.toInt() ?: 1024).toFloat()
                                    s.liquifySize = (docMax * 0.14f).coerceIn(48f, 220f)
                                    s.liquifyPressure = 75f
                                    vm.beginLiquify()
                                    s.activePanel = BottomPanel.LIQUIFY_EDIT
                                }
                            )
                            SlimToolChip(Icons.Default.PushPin, "Kukla", false, onClick = {
                                    s.puppetPins = emptyList()
                                    s.puppetSelectedIdx = -1
                                    s.puppetMode = "normal"
                                    s.puppetUndoStack = emptyList()
                                    s.puppetRedoStack = emptyList()
                                    vm.beginProxyPreview()
                                    s.activePanel = BottomPanel.PUPPET_WARP_EDIT
                                }
                            )
                            SlimToolChip(Icons.Default.MovieFilter, "Sinema", false, onClick = {
                                    s.cinemaPreset = "golden"
                                    s.cinemaIntensity = 80f
                                    s.cinemaVignette = -25f
                                    s.cinemaGrain = 12f
                                    vm.loadCinemaThumbs()
                                    vm.beginProxyPreview()
                                    vm.updateProxyCinemaLook(s.cinemaPreset, s.cinemaIntensity, s.cinemaVignette, s.cinemaGrain)
                                    s.activePanel = BottomPanel.CINEMA_LOOK_EDIT
                                }
                            )
                        }
                    }
                }

                // 60 FPS GPU Mirror Effect — drag right on the photo OR use the slider!
                BottomPanel.MIRROR_EDIT -> {
                    val isEnine = s.selectedMirrorMode.isVerticalAxis
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BottomEditorHeader(
                            title = "Ayna Efekti",
                            subtitle = "Fotoğraf üzerinde parmağınla çizgiyi sürükle",
                            applyLabel = "Uygula",
                            onClose = {
                                onClearGpuPreview()
                                s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2
                            },
                            onApply = {
                                val modeId = s.selectedMirrorMode.id
                                val ratio = s.mirrorAxisRatio
                                vm.applyPhotoMirror(modeId, ratio)
                                onClearGpuPreview()
                            }
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ProPill(
                                selected = isEnine,
                                onClick = {
                                    s.selectedMirrorMode = MIRROR_MODES[0]
                                    onPushGpuMirrorPreview(s.selectedMirrorMode, s.mirrorAxisRatio)
                                },
                                label = { Text("Ayna (Enine)", fontSize = 11.sp) }
                            )
                            ProPill(
                                selected = !isEnine,
                                onClick = {
                                    s.selectedMirrorMode = MIRROR_MODES[2]
                                    onPushGpuMirrorPreview(s.selectedMirrorMode, s.mirrorAxisRatio)
                                },
                                label = { Text("Ayna (Boyuna)", fontSize = 11.sp) }
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            // Flip mirrored side (Sol->Sağ <-> Sağ->Sol, Üst->Alt <-> Alt->Üst)
                            OutlinedButton(
                                onClick = {
                                    s.selectedMirrorMode = when (s.selectedMirrorMode.id) {
                                        "enine_auto" -> MIRROR_MODES[1]
                                        "enine_flip" -> MIRROR_MODES[0]
                                        "boyuna_auto" -> MIRROR_MODES[3]
                                        else -> MIRROR_MODES[2]
                                    }
                                    onPushGpuMirrorPreview(s.selectedMirrorMode, s.mirrorAxisRatio)
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.SwapHoriz, contentDescription = "Yön Çevir", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Ters", fontSize = 11.sp)
                            }
                        }
                        CompactSliderRow(
                            label = "Pozisyon",
                            valueText = "%${(s.mirrorAxisRatio * 100).roundToInt()}",
                            value = s.mirrorAxisRatio,
                            range = 0.05f..0.95f,
                            defaultValue = 0.50f
                        ) {
                            s.mirrorAxisRatio = it
                            onPushGpuMirrorPreview(s.selectedMirrorMode, it)
                        }
                    }
                }

                BottomPanel.LAYER_TRANSFORM -> {
                    val activeName = state.layers.firstOrNull { it.isActive }?.name ?: "Aktif Katman"
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        BottomEditorHeader(
                            title = "Katmanı Taşı: $activeName",
                            subtitle = "İçinden taşı · Kenar/köşeden serbest uzat · 2 parmak döndür",
                            applyLabel = "Uygula",
                            onClose = {
                                vm.clearActiveLayerPreview()
                                s.activePanel = BottomPanel.DOCK
                            },
                            onApply = {
                                vm.transformActiveLayerXY(s.layerDx, s.layerDy, s.layerScaleXPct, s.layerScaleYPct, s.layerRotDeg)
                                s.activePanel = BottomPanel.DOCK
                                s.activeWorkspaceV2 = s.lastWorkspaceV2
                            }
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ProPill(
                                selected = !s.layerLockAspect,
                                onClick = { s.layerLockAspect = false },
                                label = { Text("Serbest Uzat", fontSize = 11.sp) }
                            )
                            ProPill(
                                selected = s.layerLockAspect,
                                onClick = {
                                    s.layerLockAspect = true
                                    val avg = (s.layerScaleXPct + s.layerScaleYPct) * 0.5f
                                    s.layerScaleXPct = avg; s.layerScaleYPct = avg; s.layerScalePct = avg
                                },
                                label = { Text("Oranlı", fontSize = 11.sp) }
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            TextButton(onClick = {
                                s.layerDx = 0f; s.layerDy = 0f; s.layerScaleXPct = 100f; s.layerScaleYPct = 100f; s.layerScalePct = 100f; s.layerRotDeg = 0f
                            }) {
                                Text("Sıfırla", fontSize = 11.sp, color = Color(0xFFB8BDD0))
                            }
                        }
                        if (s.layerLockAspect) {
                            CompactSliderRow("Boyut", "%${s.layerScaleXPct.roundToInt()}", s.layerScaleXPct, 10f..350f, defaultValue = 100f) {
                                s.layerScaleXPct = it; s.layerScaleYPct = it; s.layerScalePct = it
                            }
                        } else {
                            CompactSliderRow("Genişlik", "%${s.layerScaleXPct.roundToInt()}", s.layerScaleXPct, 10f..350f, defaultValue = 100f) {
                                s.layerScaleXPct = it
                            }
                            CompactSliderRow("Yükseklik", "%${s.layerScaleYPct.roundToInt()}", s.layerScaleYPct, 10f..350f, defaultValue = 100f) {
                                s.layerScaleYPct = it
                            }
                        }
                        CompactSliderRow("Döndür", "${s.layerRotDeg.roundToInt()}°", s.layerRotDeg, -180f..180f, defaultValue = 0f) {
                            s.layerRotDeg = it
                        }
                    }
                }

                BottomPanel.LAYER_FX_EDIT -> {
                    val fxSlots = remember { createDefaultMultiFxSlots() }
                    MultiFxLayerStyleStudioPanel(
                        slots = fxSlots,
                        onPreviewStack = { json -> vm.updateProxyLayerStyleStack(json) },
                        onClearAll = {
                            vm.clearLayerEffects()
                            vm.endProxyPreview()
                            s.activePanel = BottomPanel.DOCK
                        },
                        onClose = {
                            vm.endProxyPreview()
                            s.activePanel = BottomPanel.DOCK
                        },
                        onApply = { json ->
                            vm.applyLayerStyleStack(json)
                            vm.beginProxyPreview()
                        }
                    )
                }

                BottomPanel.LAYER_MASK_EDIT -> {
                    val activeL = state.layers.firstOrNull { it.isActive }
                    LayerMaskPanel(
                        layerName = activeL?.name ?: "Katman",
                        hasMask = activeL?.hasMask == true,
                        maskEnabled = activeL?.maskEnabled == true,
                        hasSelection = state.selectionBounds != null,
                        maskPaintMode = state.maskPaintMode,
                        onAddRevealAll = { vm.addLayerMask("revealAll") },
                        onAddHideAll = { vm.addLayerMask("hideAll") },
                        onAddFromSelection = { vm.addLayerMask("revealSelection") },
                        onPaintHide = {
                            vm.enterMaskPaintMode(hideWithBlack = true)
                            s.activePanel = BottomPanel.DOCK
                        },
                        onPaintReveal = {
                            vm.enterMaskPaintMode(hideWithBlack = false)
                            s.activePanel = BottomPanel.DOCK
                        },
                        onToggleEnabled = {
                            if (activeL != null) vm.toggleLayerMaskEnabled(activeL.id, !activeL.maskEnabled)
                        },
                        onInvert = { vm.invertActiveLayerMask() },
                        onApplyMask = { vm.applyActiveLayerMask(); s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2 },
                        onDeleteMask = { vm.deleteActiveLayerMask() },
                        onClose = { vm.setTool(ActiveTool.NONE); s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2 }
                    )
                }
                BottomPanel.BLEND_IF_EDIT -> {
                    val activeName = state.layers.firstOrNull { it.isActive }?.name ?: "Katman"
                    fun pushBi() {
                        vm.updateProxyBlendIf(
                            s.blendIfThisBlack.roundToInt(),
                            s.blendIfThisWhite.roundToInt(),
                            s.blendIfUnderBlack.roundToInt(),
                            s.blendIfUnderWhite.roundToInt(),
                            s.blendIfFeather.roundToInt()
                        )
                    }
                    BlendIfPanel(
                        layerName = activeName,
                        thisBlack = s.blendIfThisBlack, onThisBlack = { s.blendIfThisBlack = it; pushBi() },
                        thisWhite = s.blendIfThisWhite, onThisWhite = { s.blendIfThisWhite = it; pushBi() },
                        underBlack = s.blendIfUnderBlack, onUnderBlack = { s.blendIfUnderBlack = it; pushBi() },
                        underWhite = s.blendIfUnderWhite, onUnderWhite = { s.blendIfUnderWhite = it; pushBi() },
                        feather = s.blendIfFeather, onFeather = { s.blendIfFeather = it; pushBi() },
                        onReset = {
                            s.blendIfThisBlack = 0f; s.blendIfThisWhite = 255f
                            s.blendIfUnderBlack = 0f; s.blendIfUnderWhite = 255f; s.blendIfFeather = 20f
                            vm.resetBlendIf()
                        },
                        onClose = { vm.endProxyPreview(); s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2 },
                        onApply = {
                            vm.applyBlendIf(
                                s.blendIfThisBlack.roundToInt(),
                                s.blendIfThisWhite.roundToInt(),
                                s.blendIfUnderBlack.roundToInt(),
                                s.blendIfUnderWhite.roundToInt(),
                                s.blendIfFeather.roundToInt()
                            )
                            vm.beginProxyPreview()
                        }
                    )
                }
                BottomPanel.LENS_CORRECTION_EDIT -> LensCorrectionPanel(
                    s = s,
                    onPreview = { vm.updateProxyLensCorrection(s.lensDistortion, s.lensVignette, s.lensRedCyan, s.lensBlueYellow, s.lensVertical, s.lensHorizontal) },
                    onReset = {
                        s.lensDistortion = 0f; s.lensVignette = 0f; s.lensRedCyan = 0f
                        s.lensBlueYellow = 0f; s.lensVertical = 0f; s.lensHorizontal = 0f
                        vm.resetProxyPreview()
                    },
                    onClose = { vm.endProxyPreview(); s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2 },
                    onApply = {
                        vm.applyLensCorrection(s.lensDistortion, s.lensVignette, s.lensRedCyan, s.lensBlueYellow, s.lensVertical, s.lensHorizontal)
                        s.activePanel = BottomPanel.DOCK
                        s.activeWorkspaceV2 = s.lastWorkspaceV2
                    }
                )
                BottomPanel.LUT_IMPORT_EDIT -> CubeLutPanel(
                    fileName = s.lutFileName,
                    opacityPct = s.lutOpacity,
                    onPickFile = onImportLut,
                    onOpacity = {
                        s.lutOpacity = it
                        if (s.lutText.isNotBlank()) vm.updateProxyColorLookup(s.lutFileName, s.lutText, it)
                    },
                    onClose = { vm.endProxyPreview(); s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2 },
                    onApply = {
                        if (s.lutText.isNotBlank()) {
                            vm.applyColorLookup(s.lutFileName, s.lutText, s.lutOpacity)
                            vm.beginProxyPreview()
                        }
                    }
                )
                BottomPanel.CONTENT_AWARE_FILL_EDIT -> ContentAwareFillPanel(
                    s = s,
                    onPreview = {
                        vm.updateProxyContentAwareFillPro(
                            s.cafSampling, s.cafColorAdaptation, s.cafRotationAdaptation,
                            s.cafScale, s.cafMirror, s.cafSampleAllLayers, s.cafStrokes
                        )
                    },
                    onReset = {
                        s.cafSampling = "auto"; s.cafColorAdaptation = "default"
                        s.cafRotationAdaptation = "none"; s.cafScale = false
                        s.cafMirror = false; s.cafSampleAllLayers = false; s.cafOutput = "current"
                        s.cafBrushActive = false; s.cafStrokes = emptyList(); s.cafActiveScreenStroke = emptyList()
                        vm.resetProxyPreview()
                    },
                    onClose = {
                        s.cafBrushActive = false; s.cafStrokes = emptyList(); s.cafActiveScreenStroke = emptyList()
                        vm.endProxyPreview(); s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2
                    },
                    onApply = {
                        val strokes = s.cafStrokes
                        s.cafBrushActive = false; s.cafStrokes = emptyList(); s.cafActiveScreenStroke = emptyList()
                        vm.applyContentAwareFillPro(
                            s.cafSampling, s.cafColorAdaptation, s.cafRotationAdaptation,
                            s.cafScale, s.cafMirror, s.cafSampleAllLayers, s.cafOutput, strokes
                        )
                        s.activePanel = BottomPanel.DOCK
                        s.activeWorkspaceV2 = s.lastWorkspaceV2
                    }
                )
                BottomPanel.CONTENT_AWARE_MOVE_EDIT -> ContentAwareMovePanel(
                    s = s,
                    onPreview = {
                        vm.updateProxyContentAwareMove(
                            s.camMoveDx, s.camMoveDy, s.camMoveMode,
                            s.camMoveStructure.roundToInt(), s.camMoveColor.roundToInt(),
                            s.camMoveScale, s.camMoveRotate, s.camMoveSampleAll
                        )
                    },
                    onReset = {
                        s.camMoveDx = 0f; s.camMoveDy = 0f; s.camMoveMode = "move"
                        s.camMoveStructure = 4f; s.camMoveColor = 0f
                        s.camMoveScale = 100f; s.camMoveRotate = 0f; s.camMoveSampleAll = false
                        vm.resetProxyPreview()
                    },
                    onClose = { vm.endProxyPreview(); s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2 },
                    onApply = {
                        vm.applyContentAwareMove(
                            s.camMoveDx, s.camMoveDy, s.camMoveMode,
                            s.camMoveStructure.roundToInt(), s.camMoveColor.roundToInt(),
                            s.camMoveScale, s.camMoveRotate, s.camMoveSampleAll
                        )
                        s.activePanel = BottomPanel.DOCK
                        s.activeWorkspaceV2 = s.lastWorkspaceV2
                    }
                )
                BottomPanel.CONTENT_AWARE_SCALE_EDIT -> ContentAwareScalePanel(
                    s = s,
                    onPreview = {
                        vm.updateProxyContentAwareScalePro(s.casScaleX, s.casScaleY, s.casAmount, s.casProtectSkinTones, s.casProtectStrokes)
                    },
                    onReset = {
                        s.casScaleX = 100f; s.casScaleY = 100f; s.casAmount = 100f; s.casProtectSkinTones = true
                        s.casBrushActive = false; s.casProtectStrokes = emptyList(); s.casActiveScreenStroke = emptyList()
                        vm.resetProxyPreview()
                    },
                    onClose = {
                        s.casBrushActive = false; s.casProtectStrokes = emptyList(); s.casActiveScreenStroke = emptyList()
                        vm.endProxyPreview(); s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2
                    },
                    onApply = {
                        val strokes = s.casProtectStrokes
                        s.casBrushActive = false; s.casProtectStrokes = emptyList(); s.casActiveScreenStroke = emptyList()
                        vm.applyContentAwareScalePro(s.casScaleX, s.casScaleY, s.casAmount, s.casProtectSkinTones, strokes)
                        s.activePanel = BottomPanel.DOCK
                        s.activeWorkspaceV2 = s.lastWorkspaceV2
                    }
                )
                BottomPanel.SELECT_COLOR_RANGE_EDIT -> ColorRangePanel(
                    s = s,
                    onPreview = { vm.updateProxyColorRange(s.crPreset, s.crColorHex, s.crFuzziness, s.crInvert, s.crMode, s.crSampleAll) },
                    onClose = { vm.endProxyPreview(); s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2 },
                    onApply = {
                        vm.applyColorRange(s.crPreset, s.crColorHex, s.crFuzziness, s.crInvert, s.crMode, s.crSampleAll)
                        s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2
                    }
                )
                BottomPanel.SELECT_SKY_EDIT -> SelectSkyPanel(
                    s = s,
                    onPreview = { vm.updateProxySelectSky(s.skyThreshold, s.skySoftness, s.skyMode, s.skySampleAll) },
                    onClose = { vm.endProxyPreview(); s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2 },
                    onApply = {
                        vm.applySelectSky(s.skyThreshold, s.skySoftness, s.skyMode, s.skySampleAll)
                        s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2
                    }
                )
                BottomPanel.SELECT_FOCUS_AREA_EDIT -> FocusAreaPanel(
                    s = s,
                    onPreview = { vm.updateProxyFocusArea(s.focusRange, s.focusNoise, s.focusMode, s.focusSampleAll) },
                    onClose = { vm.endProxyPreview(); s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2 },
                    onApply = {
                        vm.applyFocusArea(s.focusRange, s.focusNoise, s.focusMode, s.focusSampleAll)
                        s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2
                    }
                )
                BottomPanel.SELECT_MODIFY_EDIT -> SelectModifyPanel(
                    s = s,
                    onPreview = {
                        vm.updateProxyModifySelection(s.selModifyOp, s.selModifyRadius)
                    },
                    onApplyModify = {
                        vm.applyModifySelection(s.selModifyOp, s.selModifyRadius)
                        s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2
                    },
                    onGrow = {
                        vm.endProxyPreview()
                        vm.applyGrowOrSimilarSelection(similar = false, tolerance = s.selGrowTolerance)
                        s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2
                    },
                    onSimilar = {
                        vm.endProxyPreview()
                        vm.applyGrowOrSimilarSelection(similar = true, tolerance = s.selGrowTolerance)
                        s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2
                    },
                    onClose = { vm.endProxyPreview(); s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2 }
                )
                BottomPanel.BW_PRO_EDIT -> BlackWhiteProPanel(
                    s = s,
                    onPreview = { vm.updateProxyAdjustment("blackWhite", s.buildBwProJson()) },
                    onReset = {
                        s.resetBwPro()
                        vm.updateProxyAdjustment("blackWhite", s.buildBwProJson())
                    },
                    onClose = { onClearAdjustGpuPreviewOnly(); vm.endProxyPreview(); s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2 },
                    onApply = { vm.applyAdjustmentOnce("blackWhite", s.buildBwProJson(), "Siyah-Beyaz Pro"); s.resetBwPro(); vm.beginProxyPreview() }
                )
                BottomPanel.PHOTO_FILTER_EDIT -> PhotoFilterProPanel(
                    s = s,
                    onPreview = { vm.updateProxyAdjustment("photoFilter", s.buildPhotoFilterJson()) },
                    onReset = {
                        s.resetPhotoFilterPro()
                        vm.updateProxyAdjustment("photoFilter", s.buildPhotoFilterJson())
                    },
                    onClose = { onClearAdjustGpuPreviewOnly(); vm.endProxyPreview(); s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2 },
                    onApply = { vm.applyAdjustmentOnce("photoFilter", s.buildPhotoFilterJson(), "Fotoğraf Filtresi"); s.resetPhotoFilterPro(); vm.beginProxyPreview() }
                )
                BottomPanel.SELECTIVE_COLOR_EDIT -> SelectiveColorProPanel(
                    s = s,
                    onPreview = { vm.updateProxyAdjustment("selectiveColor", s.buildSelectiveColorJson()) },
                    onReset = {
                        s.resetSelectiveColorPro()
                        vm.resetProxyPreview()
                    },
                    onClose = { onClearAdjustGpuPreviewOnly(); vm.endProxyPreview(); s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2 },
                    onApply = { vm.applyAdjustmentOnce("selectiveColor", s.buildSelectiveColorJson(), "Seçici Renk"); s.resetSelectiveColorPro(); vm.beginProxyPreview() }
                )
                BottomPanel.CHANNEL_MIXER_EDIT -> ChannelMixerProPanel(
                    s = s,
                    onPreview = { vm.updateProxyAdjustment("channelMixer", s.buildChannelMixerJson()) },
                    onReset = {
                        s.resetChannelMixerPro()
                        vm.resetProxyPreview()
                    },
                    onClose = { onClearAdjustGpuPreviewOnly(); vm.endProxyPreview(); s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2 },
                    onApply = { vm.applyAdjustmentOnce("channelMixer", s.buildChannelMixerJson(), "Kanal Karıştırıcı"); s.resetChannelMixerPro(); vm.beginProxyPreview() }
                )
                BottomPanel.GRADIENT_MAP_EDIT -> GradientMapProPanel(
                    s = s,
                    onPreview = { vm.updateProxyAdjustment("gradientMap", s.buildGradientMapJson()) },
                    onReset = {
                        s.resetGradientMapPro()
                        vm.updateProxyAdjustment("gradientMap", s.buildGradientMapJson())
                    },
                    onClose = { onClearAdjustGpuPreviewOnly(); vm.endProxyPreview(); s.activePanel = BottomPanel.DOCK; s.activeWorkspaceV2 = s.lastWorkspaceV2 },
                    onApply = { vm.applyAdjustmentOnce("gradientMap", s.buildGradientMapJson(), "Degrade Eşleme"); s.resetGradientMapPro(); vm.beginProxyPreview() }
                )
                else -> Unit
            }

}
}
