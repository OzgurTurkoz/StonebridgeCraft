package com.kastamonushop.app

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kastamonushop.app.viewmodel.ActiveTool
import com.kastamonushop.app.viewmodel.EditorUiState
import com.kastamonushop.app.viewmodel.EditorViewModel
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

// ==================== PROCREATE-STYLE LEFT EDGE VERTICAL SIDEBAR ====================

@Composable
internal fun LeftBrushSidebar(
    vm: EditorViewModel,
    state: EditorUiState,
    bucketTolerance: Float,
    onBucketTolerance: (Float) -> Unit,
    onBrushSizePreview: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tool = state.activeTool
    val showSize = tool != ActiveTool.NONE &&
        tool != ActiveTool.EYEDROPPER &&
        tool != ActiveTool.BUCKET &&
        tool != ActiveTool.PATCH &&
        tool != ActiveTool.RED_EYE &&
        tool != ActiveTool.MAGIC_ERASER &&
        tool != ActiveTool.SELECT_RECT &&
        tool != ActiveTool.SELECT_WAND &&
        tool != ActiveTool.SELECT_LASSO &&
        tool != ActiveTool.SELECT_MAGNETIC_LASSO

    val (secondaryLabel, secondaryNorm, secondaryReadout) = when (tool) {
        ActiveTool.BRUSH, ActiveTool.ERASER, ActiveTool.CLONE_STAMP, ActiveTool.PENCIL, ActiveTool.ADJUST_BRUSH, ActiveTool.HISTORY_BRUSH, ActiveTool.ART_HISTORY_BRUSH, ActiveTool.HEALING_BRUSH ->
            Triple("Opaklık", ((state.brushOpacity - 0.05f) / 0.95f).coerceIn(0f, 1f), "%${(state.brushOpacity * 100f).roundToInt()}")
        ActiveTool.MIXER_BRUSH ->
            Triple("Islaklık", (state.mixerWet / 100f).coerceIn(0f, 1f), "%${state.mixerWet.roundToInt()}")
        ActiveTool.COLOR_REPLACE ->
            Triple("Tolerans", ((state.colorReplaceTolerance - 1f) / 99f).coerceIn(0f, 1f), "%${state.colorReplaceTolerance.roundToInt()}")
        ActiveTool.BUCKET ->
            Triple("Tolerans", (bucketTolerance / 200f).coerceIn(0f, 1f), "${bucketTolerance.roundToInt()}")
        ActiveTool.SELECT_WAND ->
            Triple("Tolerans", (state.wandTolerance / 120f).coerceIn(0f, 1f), "${state.wandTolerance.roundToInt()}")
        ActiveTool.RED_EYE ->
            Triple("Karartma", ((state.redEyeDarken - 10f) / 90f).coerceIn(0f, 1f), "%${state.redEyeDarken.roundToInt()}")
        ActiveTool.SMUDGE, ActiveTool.DODGE, ActiveTool.BURN,
        ActiveTool.SPONGE_SAT, ActiveTool.SPONGE_DESAT,
        ActiveTool.BLUR_BRUSH, ActiveTool.SHARPEN_BRUSH ->
            Triple("Güç", ((state.retouchStrength - 10f) / 90f).coerceIn(0f, 1f), "%${state.retouchStrength.roundToInt()}")
        else -> Triple("", 0f, "")
    }
    val showSecondary = secondaryLabel.isNotEmpty()
    if (!showSize && !showSecondary) return

    var activeDragBadge by remember { mutableStateOf<String?>(null) }

    // Logarithmic mapping for brush size: 1..250px feels silky at both small & large sizes
    val minLog = remember { ln(1f) }
    val maxLog = remember { ln(250f) }
    val sizeNorm = ((ln(state.brushSize.coerceIn(1f, 250f)) - minLog) / (maxLog - minLog)).coerceIn(0f, 1f)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color(0xCC17181E),
            tonalElevation = 6.dp,
            modifier = Modifier
                .width(34.dp)
                .border(1.dp, Color(0x44FFFFFF), RoundedCornerShape(18.dp))
        ) {
            Column(
                modifier = Modifier.padding(vertical = 10.dp, horizontal = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (showSize) {
                    Text(
                        text = "${state.brushSize.roundToInt()}",
                        color = Color(0xFFDCE4F5),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    VerticalEdgeSlider(
                        fraction = sizeNorm,
                        accentColor = StudioV2Colors.Amber,
                        modifier = Modifier
                            .width(24.dp)
                            .height(104.dp),
                        onChange = { f ->
                            val px = kotlin.math.exp(minLog + f * (maxLog - minLog)).coerceIn(1f, 250f)
                            vm.updateBrush(size = px)
                            onBrushSizePreview()
                            activeDragBadge = "Boyut: ${px.roundToInt()}px"
                        },
                        onDragEnd = { activeDragBadge = null }
                    )
                }

                if (showSize && showSecondary) {
                    HorizontalDivider(
                        color = Color(0x33FFFFFF),
                        modifier = Modifier.width(18.dp)
                    )
                }

                if (showSecondary) {
                    VerticalEdgeSlider(
                        fraction = secondaryNorm,
                        accentColor = Color(0xFF8B7BFF),
                        modifier = Modifier
                            .width(24.dp)
                            .height(92.dp),
                        onChange = { f ->
                            when (tool) {
                                ActiveTool.BRUSH, ActiveTool.ERASER, ActiveTool.CLONE_STAMP, ActiveTool.PENCIL, ActiveTool.ADJUST_BRUSH, ActiveTool.HISTORY_BRUSH, ActiveTool.ART_HISTORY_BRUSH, ActiveTool.HEALING_BRUSH -> {
                                    val op = (0.05f + f * 0.95f).coerceIn(0.05f, 1f)
                                    vm.updateBrush(opacity = op)
                                    activeDragBadge = "Opaklık: %${(op * 100f).roundToInt()}"
                                }
                                ActiveTool.MIXER_BRUSH -> {
                                    val wet = (f * 100f).coerceIn(0f, 100f)
                                    vm.updateMixerParams(wet = wet)
                                    activeDragBadge = "Islaklık: %${wet.roundToInt()}"
                                }
                                ActiveTool.COLOR_REPLACE -> {
                                    val tol = (1f + f * 99f).coerceIn(1f, 100f)
                                    vm.updateColorReplaceParams(tolerance = tol)
                                    activeDragBadge = "Tolerans: %${tol.roundToInt()}"
                                }
                                ActiveTool.BUCKET -> {
                                    val tol = (f * 200f).coerceIn(0f, 200f)
                                    onBucketTolerance(tol)
                                    activeDragBadge = "Tolerans: ${tol.roundToInt()}"
                                }
                                ActiveTool.SELECT_WAND -> {
                                    val tol = (f * 120f).coerceIn(1f, 120f)
                                    vm.updateWandTolerance(tol)
                                    activeDragBadge = "Tolerans: ${tol.roundToInt()}"
                                }
                                ActiveTool.RED_EYE -> {
                                    val dk = (10f + f * 90f).coerceIn(10f, 100f)
                                    vm.updateRedEyeParams(darken = dk)
                                    activeDragBadge = "Karartma: %${dk.roundToInt()}"
                                }
                                else -> {
                                    val str = (10f + f * 90f).coerceIn(10f, 100f)
                                    vm.updateRetouchStrength(str)
                                    activeDragBadge = "Güç: %${str.roundToInt()}"
                                }
                            }
                        },
                        onDragEnd = { activeDragBadge = null }
                    )
                    Text(
                        text = secondaryReadout,
                        color = Color(0xFFC5CBE0),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        }

        activeDragBadge?.let { badge ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xEE14161D),
                tonalElevation = 6.dp
            ) {
                Text(
                    text = badge,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun VerticalEdgeSlider(
    fraction: Float,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onChange: (Float) -> Unit,
    onDragEnd: () -> Unit
) {
    val latestOnChange by rememberUpdatedState(onChange)
    val latestOnEnd by rememberUpdatedState(onDragEnd)
    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures { pos ->
                    val h = size.height.toFloat().coerceAtLeast(1f)
                    latestOnChange((1f - pos.y / h).coerceIn(0f, 1f))
                    latestOnEnd()
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { pos ->
                        val h = size.height.toFloat().coerceAtLeast(1f)
                        latestOnChange((1f - pos.y / h).coerceIn(0f, 1f))
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val h = size.height.toFloat().coerceAtLeast(1f)
                        latestOnChange((1f - change.position.y / h).coerceIn(0f, 1f))
                    },
                    onDragEnd = { latestOnEnd() },
                    onDragCancel = { latestOnEnd() }
                )
            }
    ) {
        val trackW = 6.dp.toPx()
        val trackX = (size.width - trackW) * 0.5f
        val r = CornerRadius(trackW * 0.5f, trackW * 0.5f)
        // Background track
        drawRoundRect(
            color = Color(0xFF343846),
            topLeft = Offset(trackX, 0f),
            size = Size(trackW, size.height),
            cornerRadius = r
        )
        // Active fill from bottom
        val fillH = (fraction.coerceIn(0f, 1f) * size.height)
        drawRoundRect(
            color = accentColor,
            topLeft = Offset(trackX, size.height - fillH),
            size = Size(trackW, fillH),
            cornerRadius = r
        )
        // Thumb knob
        val thumbY = (size.height - fillH).coerceIn(7.dp.toPx(), size.height - 7.dp.toPx())
        drawCircle(
            color = Color.White,
            radius = 7.dp.toPx(),
            center = Offset(size.width * 0.5f, thumbY)
        )
        drawCircle(
            color = accentColor,
            radius = 4.dp.toPx(),
            center = Offset(size.width * 0.5f, thumbY)
        )
    }
}

// ==================== SLIM 1-ROW CANVAS-FIRST DRAW & RETOUCH BARS ====================

@Composable
internal fun CompactDrawBottomBar(
    vm: EditorViewModel,
    state: EditorUiState,
    popoverOpen: Boolean,
    onTogglePopover: () -> Unit,
    onOpenColorWheel: () -> Unit,
    onOpenToolPicker: () -> Unit,
    onClose: () -> Unit
) {
    val activeColor = remember(state.colorHex) {
        runCatching { Color(AndroidColor.parseColor(state.colorHex)) }.getOrDefault(Color.White)
    }
    var lastPaintTool by remember { mutableStateOf(ActiveTool.BRUSH) }
    val isEraser = state.activeTool == ActiveTool.ERASER || state.activeTool == ActiveTool.MAGIC_ERASER || state.activeTool == ActiveTool.BG_ERASER
    LaunchedEffect(state.activeTool) {
        if (!isEraser && state.activeTool != ActiveTool.NONE) {
            lastPaintTool = state.activeTool
        }
    }
    val (toolIcon, toolLabel) = when (state.activeTool) {
        ActiveTool.PENCIL -> Icons.Default.Edit to "Kalem"
        ActiveTool.ERASER -> Icons.Default.AutoFixNormal to "Silgi"
        ActiveTool.MAGIC_ERASER -> Icons.Default.AutoFixHigh to "Sihirli Silgi"
        ActiveTool.BG_ERASER -> Icons.Default.CleaningServices to "Arka Silgi"
        ActiveTool.ADJUST_BRUSH -> Icons.Default.Tune to "Ayar Fırçası"
        ActiveTool.BUCKET -> Icons.Default.FormatColorFill to "Boya Kovası"
        ActiveTool.EYEDROPPER -> Icons.Default.Colorize to "Damlalık"
        ActiveTool.MIXER_BRUSH -> Icons.Default.WaterDrop to "Karıştır"
        ActiveTool.COLOR_REPLACE -> Icons.Default.InvertColors to "Renk Değiş"
        else -> Icons.Default.Brush to "Fırça"
    }

    // Pro ibisPaint X + Procreate Pocket Fixed 5-Slot Draw Bar (zero horizontal scroll, zero cut-off text)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri", tint = Color.White, modifier = Modifier.size(20.dp))
        }

        // 1-Tap Brush <-> Eraser quick toggle (ibisPaint X ergonomics)
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (isEraser) StudioV2Colors.AmberSoft else Color(0xFF1F222C),
            border = androidx.compose.foundation.BorderStroke(1.dp, if (isEraser) StudioV2Colors.Amber else Color(0xFF333848)),
            modifier = Modifier
                .height(36.dp)
                .clickable {
                    if (isEraser) vm.setTool(lastPaintTool) else vm.setTool(ActiveTool.ERASER)
                }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    if (isEraser) Icons.Default.Brush else Icons.Default.AutoFixNormal,
                    contentDescription = "Fırça/Silgi Değiştir",
                    tint = if (isEraser) StudioV2Colors.Amber else Color(0xFFD5D9E4),
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    if (isEraser) "Fırça" else "Silgi",
                    color = Color.White,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
        }

        // Center Active Tool Capsule — opens clean Çiz & Rötuş grid
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF232734),
            border = androidx.compose.foundation.BorderStroke(1.dp, StudioV2Colors.Amber.copy(alpha = 0.65f)),
            modifier = Modifier
                .weight(1f)
                .height(36.dp)
                .clickable { onOpenToolPicker() }
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(toolIcon, contentDescription = null, tint = StudioV2Colors.Amber, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(toolLabel, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(3.dp))
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = null, tint = Color(0xFF9CA3AF), modifier = Modifier.size(16.dp))
            }
        }

        // Color Well
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(activeColor)
                .border(2.dp, Color.White, CircleShape)
                .clickable { onOpenColorWheel() }
        )

        // Tool Settings Popover Trigger
        Surface(
            shape = CircleShape,
            color = if (popoverOpen) StudioV2Colors.AmberSoft else Color(0xFF1F222C),
            border = androidx.compose.foundation.BorderStroke(1.dp, if (popoverOpen) StudioV2Colors.Amber else Color(0xFF333848)),
            modifier = Modifier
                .size(36.dp)
                .clickable { onTogglePopover() }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Tune, contentDescription = "Ayarlar", tint = if (popoverOpen) StudioV2Colors.Amber else Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
internal fun CompactRetouchBottomBar(
    vm: EditorViewModel,
    state: EditorUiState,
    popoverOpen: Boolean,
    onTogglePopover: () -> Unit,
    onOpenToolPicker: () -> Unit,
    onClose: () -> Unit
) {
    val (toolIcon, toolLabel) = when (state.activeTool) {
        ActiveTool.REMOVE_TOOL -> Icons.Default.AutoFixHigh to "Akıllı Kaldır"
        ActiveTool.SPOT_HEAL -> Icons.Default.Healing to "Nokta & Rötuş"
        ActiveTool.PATCH -> Icons.Default.Texture to "Yama Aracı"
        ActiveTool.RED_EYE -> Icons.Default.RemoveRedEye to "Kırmızı Göz"
        ActiveTool.SMUDGE -> Icons.Default.Gesture to "Dağıt (Smudge)"
        ActiveTool.DODGE -> Icons.Default.WbSunny to "Aydınlat"
        ActiveTool.BURN -> Icons.Default.NightsStay to "Koyulaştır"
        ActiveTool.SPONGE_SAT -> Icons.Default.Tonality to "Sünger (+)"
        ActiveTool.SPONGE_DESAT -> Icons.Default.FilterBAndW to "Sünger (-)"
        ActiveTool.BLUR_BRUSH -> Icons.Default.BlurOn to "Bulanık Fırça"
        ActiveTool.SHARPEN_BRUSH -> Icons.Default.Details to "Keskin Fırça"
        ActiveTool.HEALING_BRUSH -> Icons.Default.HealthAndSafety to "Onarım Fırçası"
        ActiveTool.HISTORY_BRUSH -> Icons.Default.History to "Geçmiş Fırçası"
        ActiveTool.ART_HISTORY_BRUSH -> Icons.Default.ColorLens to "Sanat Geçmişi"
        else -> Icons.Default.ContentCopy to "Klon Damgası"
    }

    // Pro Fixed 4-Slot Retouch Bar (zero horizontal scroll, zero cut-off text)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri", tint = Color.White, modifier = Modifier.size(20.dp))
        }

        // Center Active Retouch Tool Capsule — opens clean Çiz & Rötuş grid
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF232734),
            border = androidx.compose.foundation.BorderStroke(1.dp, StudioV2Colors.Amber.copy(alpha = 0.65f)),
            modifier = Modifier
                .weight(1f)
                .height(36.dp)
                .clickable { onOpenToolPicker() }
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(toolIcon, contentDescription = null, tint = StudioV2Colors.Amber, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(toolLabel, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(3.dp))
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = null, tint = Color(0xFF9CA3AF), modifier = Modifier.size(16.dp))
            }
        }

        when (state.activeTool) {
            ActiveTool.CLONE_STAMP, ActiveTool.HEALING_BRUSH -> {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (state.pickingCloneSource) StudioV2Colors.AmberSoft else Color(0xFF1F222C),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (state.pickingCloneSource) StudioV2Colors.Amber else Color(0xFF333848)),
                    modifier = Modifier
                        .height(36.dp)
                        .clickable {
                            if (state.activeTool == ActiveTool.CLONE_STAMP) vm.requestPickCloneSource() else vm.startPickHealingSource()
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = "Kaynak", tint = StudioV2Colors.Amber, modifier = Modifier.size(14.dp))
                        Text("Kaynak", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
            ActiveTool.PATCH -> {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF1F222C),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333848)),
                    modifier = Modifier
                        .height(36.dp)
                        .clickable { vm.updatePatchMode(if (state.patchMode == "source") "destination" else "source") }
                ) {
                    Box(modifier = Modifier.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                        Text(if (state.patchMode == "source") "Kaynak" else "Hedef", color = StudioV2Colors.Amber, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
            else -> Unit
        }

        // Tool Settings Popover Trigger
        Surface(
            shape = CircleShape,
            color = if (popoverOpen) StudioV2Colors.AmberSoft else Color(0xFF1F222C),
            border = androidx.compose.foundation.BorderStroke(1.dp, if (popoverOpen) StudioV2Colors.Amber else Color(0xFF333848)),
            modifier = Modifier
                .size(36.dp)
                .clickable { onTogglePopover() }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Tune, contentDescription = "Ayarlar", tint = if (popoverOpen) StudioV2Colors.Amber else Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
internal fun SlimToolChip(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Surface(
        shape = RoundedCornerShape(13.dp),
        color = if (selected) StudioV2Colors.AmberSoft else Color(0x16FFFFFF),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (selected) StudioV2Colors.AmberBorder else Color(0x1EFFFFFF)
        ),
        modifier = Modifier
            .height(38.dp)
            .clickable {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                onClick()
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) StudioV2Colors.Amber else StudioV2Colors.TextMuted,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                color = if (selected) StudioV2Colors.Amber else StudioV2Colors.TextPrimary,
                fontSize = 11.5.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

// ==================== FLOATING BRUSH / TOOL SETTINGS POPOVER (AUTO-CLOSES ON DRAW) ====================

@Composable
internal fun ToolSettingsPopoverCard(
    vm: EditorViewModel,
    state: EditorUiState,
    bucketTolerance: Float,
    onBucketTolerance: (Float) -> Unit,
    bucketContiguous: Boolean,
    onBucketContiguous: (Boolean) -> Unit,
    onImportAbr: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var brushTab by remember { mutableIntStateOf(0) } // 0 = Uçlar & .abr, 1 = Dinamikler

    LaunchedEffect(state.activeTool, brushTab) {
        if (state.activeTool == ActiveTool.BRUSH && brushTab == 0 && state.brushPresets.isEmpty()) {
            vm.loadBrushPresets()
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xF21B1D25),
        tonalElevation = 12.dp
    ) {
        Column(
            modifier = Modifier
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(18.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = when (state.activeTool) {
                        ActiveTool.BRUSH -> "Fırça Stüdyosu"
                        ActiveTool.ERASER -> "Silgi Ayarları"
                        ActiveTool.MIXER_BRUSH -> "Karıştırıcı Fırça"
                        ActiveTool.COLOR_REPLACE -> "Renk Değiştirici"
                        ActiveTool.BUCKET -> "Boya Kovası"
                        ActiveTool.PATCH -> "Yama Aracı"
                        ActiveTool.REMOVE_TOOL -> "Kaldırma Aracı"
                        ActiveTool.RED_EYE -> "Kırmızı Göz"
                        ActiveTool.PENCIL -> "Piksel Kalem"
                        ActiveTool.MAGIC_ERASER -> "Sihirli Silgi"
                        ActiveTool.BG_ERASER -> "Arka Plan Silgisi"
                        ActiveTool.ADJUST_BRUSH -> "Ayar Fırçası"
                        ActiveTool.HISTORY_BRUSH -> "Geçmiş Fırçası"
                        ActiveTool.ART_HISTORY_BRUSH -> "Sanat Geçmişi Fırçası"
                        ActiveTool.HEALING_BRUSH -> "Kaynaklı Düzeltme Fırçası"
                        ActiveTool.CLONE_STAMP -> "Klonlama Damgası"
                        else -> "Rötuş Ayarları"
                    },
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Kapat", tint = Color(0xFF9EA4B8), modifier = Modifier.size(16.dp))
                }
            }

            when (state.activeTool) {
                ActiveTool.BRUSH -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ProPill(
                            selected = brushTab == 0,
                            onClick = { brushTab = 0 },
                            label = { Text("Fırça Uçları", fontSize = 11.sp) }
                        )
                        ProPill(
                            selected = brushTab == 1,
                            onClick = { brushTab = 1 },
                            label = { Text("Dinamikler", fontSize = 11.sp) }
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        OutlinedButton(
                            onClick = onImportAbr,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text(".abr Yükle", fontSize = 10.sp)
                        }
                    }

                    if (brushTab == 0) {
                        CompactSliderRow("Sertlik", "%${(state.brushHardness * 100f).roundToInt()}", state.brushHardness, 0f..1f, defaultValue = 0.85f) {
                            vm.updateBrush(hardness = it)
                        }
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 210.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            item {
                                VisualBrushStrokeItem(
                                    name = "Yuvarlak (Varsayılan)",
                                    hardness = state.brushHardness,
                                    isSampled = false,
                                    selected = state.activeBrushPresetName == null,
                                    onClick = {
                                        vm.resetBrushToRound()
                                        onDismiss()
                                    }
                                )
                            }
                            items(state.brushPresets.take(35), key = { "${it.index}_${it.name}" }) { preset ->
                                VisualBrushStrokeItem(
                                    name = preset.name,
                                    hardness = preset.hardness,
                                    isSampled = preset.hasSampledTip,
                                    selected = state.activeBrushPresetName.equals(preset.name, ignoreCase = true),
                                    onClick = {
                                        vm.selectBrushPreset(preset.name)
                                        onDismiss()
                                    }
                                )
                            }
                        }
                    } else {
                        CompactSliderRow("Akış", "%${(state.brushFlow * 100f).roundToInt()}", state.brushFlow, 0.05f..1f, defaultValue = 1f) {
                            vm.updateBrush(flow = it)
                        }
                        CompactSliderRow("Aralık", "%${(state.brushSpacing * 100f).roundToInt()}", state.brushSpacing, 0.05f..2.0f, defaultValue = 0.15f) {
                            vm.updateBrushDynamics(spacing = it)
                        }
                        CompactSliderRow("Dağılma", "%${(state.brushScatter * 100f).roundToInt()}", state.brushScatter, 0f..3.0f, defaultValue = 0f) {
                            vm.updateBrushDynamics(scatter = it)
                        }
                        CompactSliderRow("Boyut Tit.", "%${(state.brushSizeJitter * 100f).roundToInt()}", state.brushSizeJitter, 0f..1f, defaultValue = 0f) {
                            vm.updateBrushDynamics(sizeJitter = it)
                        }
                        CompactSliderRow("Açı Tit.", "%${(state.brushAngleJitter * 100f).roundToInt()}", state.brushAngleJitter, 0f..1f, defaultValue = 0f) {
                            vm.updateBrushDynamics(angleJitter = it)
                        }
                        CompactSliderRow("Opak. Tit.", "%${(state.brushOpacityJitter * 100f).roundToInt()}", state.brushOpacityJitter, 0f..1f, defaultValue = 0f) {
                            vm.updateBrushDynamics(opacityJitter = it)
                        }
                    }
                }
                ActiveTool.MIXER_BRUSH -> {
                    CompactSliderRow("Islaklık", "%${state.mixerWet.roundToInt()}", state.mixerWet, 0f..100f, defaultValue = 55f) {
                        vm.updateMixerParams(wet = it)
                    }
                    CompactSliderRow("Yük", "%${state.mixerLoad.roundToInt()}", state.mixerLoad, 0f..100f, defaultValue = 50f) {
                        vm.updateMixerParams(load = it)
                    }
                    CompactSliderRow("Karışım", "%${state.mixerMix.roundToInt()}", state.mixerMix, 0f..100f, defaultValue = 50f) {
                        vm.updateMixerParams(mix = it)
                    }
                    ProPill(
                        selected = state.mixerSampleAll,
                        onClick = { vm.updateMixerParams(sampleAll = !state.mixerSampleAll) },
                        label = { Text(if (state.mixerSampleAll) "Tüm Katmanlardan Örnekle ✓" else "Sadece Aktif Katman", fontSize = 11.sp) }
                    )
                }
                ActiveTool.COLOR_REPLACE -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("color" to "Renk", "hue" to "Ton", "saturation" to "Doygunluk", "luminosity" to "Parlaklık").forEach { (k, lbl) ->
                            ProPill(
                                selected = state.colorReplaceMode == k,
                                onClick = { vm.updateColorReplaceParams(mode = k) },
                                label = { Text(lbl, fontSize = 10.sp) }
                            )
                        }
                    }
                    CompactSliderRow("Tolerans", "%${state.colorReplaceTolerance.roundToInt()}", state.colorReplaceTolerance, 1f..100f, defaultValue = 32f) {
                        vm.updateColorReplaceParams(tolerance = it)
                    }
                    CompactSliderRow("Sertlik", "%${(state.brushHardness * 100f).roundToInt()}", state.brushHardness, 0f..1f, defaultValue = 0.85f) {
                        vm.updateBrush(hardness = it)
                    }
                }
                ActiveTool.BUCKET -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProPill(
                            selected = bucketContiguous,
                            onClick = { onBucketContiguous(true) },
                            label = { Text("Bitişik Alan", fontSize = 11.sp) }
                        )
                        ProPill(
                            selected = !bucketContiguous,
                            onClick = { onBucketContiguous(false) },
                            label = { Text("Tüm Benzer Renkler", fontSize = 11.sp) }
                        )
                    }
                    CompactSliderRow("Tolerans", "${bucketTolerance.roundToInt()}", bucketTolerance, 0f..200f, defaultValue = 36f) {
                        onBucketTolerance(it)
                    }
                }
                ActiveTool.PATCH -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ProPill(selected = state.patchMode == "source", onClick = { vm.updatePatchMode("source") }, label = { Text("Kaynak", fontSize = 11.sp) })
                        ProPill(selected = state.patchMode == "destination", onClick = { vm.updatePatchMode("destination") }, label = { Text("Hedef", fontSize = 11.sp) })
                        ProPill(selected = state.patchContentAware, onClick = { vm.updatePatchPro(contentAware = !state.patchContentAware) }, label = { Text(if (state.patchContentAware) "İçerik Duyarlı ✓" else "İçerik Duyarlı", fontSize = 11.sp) })
                        ProPill(selected = state.patchSampleAll, onClick = { vm.updatePatchPro(sampleAll = !state.patchSampleAll) }, label = { Text("Tüm Katmanlar", fontSize = 11.sp) })
                    }
                    if (state.patchContentAware) {
                        CompactSliderRow("Yapı", "${state.patchStructure}", state.patchStructure.toFloat(), 1f..7f, defaultValue = 4f) {
                            vm.updatePatchPro(structure = it.roundToInt())
                        }
                        CompactSliderRow("Renk Uyumu", "${state.patchColor}", state.patchColor.toFloat(), 0f..10f, defaultValue = 0f) {
                            vm.updatePatchPro(color = it.roundToInt())
                        }
                    }
                }
                ActiveTool.SPOT_HEAL -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("contentAware" to "İçerik Duyarlı", "proximityMatch" to "Yakınlık", "createTexture" to "Doku Oluştur").forEach { (id, lbl) ->
                            ProPill(selected = state.spotHealType == id, onClick = { vm.updateSpotHealPro(type = id) }, label = { Text(lbl, fontSize = 11.sp) })
                        }
                        ProPill(selected = state.spotHealSampleAll, onClick = { vm.updateSpotHealPro(sampleAll = !state.spotHealSampleAll) }, label = { Text("Tüm Katmanlar", fontSize = 11.sp) })
                    }
                    CompactSliderRow("Sertlik", "%${(state.brushHardness * 100f).roundToInt()}", state.brushHardness, 0f..1f, defaultValue = 0.85f) {
                        vm.updateBrush(hardness = it)
                    }
                }
                ActiveTool.DODGE, ActiveTool.BURN -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("shadows" to "Gölgeler", "midtones" to "Orta Ton", "highlights" to "Parlaklar").forEach { (id, lbl) ->
                            ProPill(selected = state.retouchRange == id, onClick = { vm.updateRetouchPro(range = id) }, label = { Text(lbl, fontSize = 11.sp) })
                        }
                        ProPill(selected = state.retouchProtectTones, onClick = { vm.updateRetouchPro(protectTones = !state.retouchProtectTones) }, label = { Text("Tonları Koru", fontSize = 11.sp) })
                    }
                    CompactSliderRow("Sertlik", "%${(state.brushHardness * 100f).roundToInt()}", state.brushHardness, 0f..1f, defaultValue = 0.5f) {
                        vm.updateBrush(hardness = it)
                    }
                }
                ActiveTool.SMUDGE -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ProPill(selected = state.smudgeFingerPainting, onClick = { vm.updateRetouchPro(fingerPainting = !state.smudgeFingerPainting) }, label = { Text("Parmak Boyama", fontSize = 11.sp) })
                        ProPill(selected = state.retouchSampleAll, onClick = { vm.updateRetouchPro(sampleAll = !state.retouchSampleAll) }, label = { Text("Tüm Katmanlar", fontSize = 11.sp) })
                    }
                }
                ActiveTool.SHARPEN_BRUSH -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ProPill(selected = state.sharpenProtectDetail, onClick = { vm.updateRetouchPro(protectDetail = !state.sharpenProtectDetail) }, label = { Text("Detayı Koru", fontSize = 11.sp) })
                        ProPill(selected = state.retouchSampleAll, onClick = { vm.updateRetouchPro(sampleAll = !state.retouchSampleAll) }, label = { Text("Tüm Katmanlar", fontSize = 11.sp) })
                    }
                }
                ActiveTool.REMOVE_TOOL -> {
                    Text(
                        "Silmek istediğin nesnenin üstünü boya veya etrafını halka içine al.",
                        color = Color(0xFF9AA4BD),
                        fontSize = 11.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ProPill(
                            selected = state.removeCloseLoops,
                            onClick = { vm.updateRemoveToolSettings(closeLoops = !state.removeCloseLoops) },
                            label = { Text("Halkayı Kapat", fontSize = 11.sp) }
                        )
                        ProPill(
                            selected = state.removeSampleAllLayers,
                            onClick = { vm.updateRemoveToolSettings(sampleAllLayers = !state.removeSampleAllLayers) },
                            label = { Text("Tüm Katmanlar", fontSize = 11.sp) }
                        )
                        ProPill(
                            selected = state.removeNewLayer,
                            onClick = { vm.updateRemoveToolSettings(newLayer = !state.removeNewLayer) },
                            label = { Text("Yeni Katman", fontSize = 11.sp) }
                        )
                    }
                }
                ActiveTool.RED_EYE -> {
                    CompactSliderRow("Göz Bebeği", "%${state.redEyePupilSize.roundToInt()}", state.redEyePupilSize, 10f..100f, defaultValue = 50f) {
                        vm.updateRedEyeParams(pupilSize = it)
                    }
                    CompactSliderRow("Karartma", "%${state.redEyeDarken.roundToInt()}", state.redEyeDarken, 10f..100f, defaultValue = 55f) {
                        vm.updateRedEyeParams(darken = it)
                    }
                }
                ActiveTool.MAGIC_ERASER -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ProPill(
                            selected = state.magicEraserContiguous,
                            onClick = { vm.updateV183ToolSettings(magicContiguous = !state.magicEraserContiguous) },
                            label = { Text("Bitişik", fontSize = 11.sp) }
                        )
                        ProPill(
                            selected = state.magicEraserSampleAll,
                            onClick = { vm.updateV183ToolSettings(magicSampleAll = !state.magicEraserSampleAll) },
                            label = { Text("Tüm Katmanlar", fontSize = 11.sp) }
                        )
                    }
                    CompactSliderRow("Tolerans", "${state.magicEraserTolerance.roundToInt()}", state.magicEraserTolerance, 0f..255f, defaultValue = 32f) {
                        vm.updateV183ToolSettings(magicTol = it)
                    }
                }
                ActiveTool.BG_ERASER -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("contiguous" to "Bitişik", "discontiguous" to "Serbest", "findEdges" to "Kenar Bul").forEach { (id, lbl) ->
                            ProPill(
                                selected = state.bgEraserLimits == id,
                                onClick = { vm.updateV183ToolSettings(bgLimits = id) },
                                label = { Text(lbl, fontSize = 11.sp) }
                            )
                        }
                        ProPill(
                            selected = state.bgEraserProtectFg,
                            onClick = { vm.updateV183ToolSettings(bgProtectFg = !state.bgEraserProtectFg) },
                            label = { Text("Ön Rengi Koru", fontSize = 11.sp) }
                        )
                    }
                    CompactSliderRow("Tolerans", "%${state.bgEraserTolerance.roundToInt()}", state.bgEraserTolerance, 1f..100f, defaultValue = 50f) {
                        vm.updateV183ToolSettings(bgTol = it)
                    }
                }
                ActiveTool.ADJUST_BRUSH -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ProPill(
                            selected = !state.adjBrushSubtract,
                            onClick = { vm.updateV183ToolSettings(adjSubtract = false) },
                            label = { Text("Ekle (+)", fontSize = 11.sp) }
                        )
                        ProPill(
                            selected = state.adjBrushSubtract,
                            onClick = { vm.updateV183ToolSettings(adjSubtract = true) },
                            label = { Text("Maskeden Sil (-)", fontSize = 11.sp) }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "exposure" to "Pozlama",
                            "levels" to "Düzeyler",
                            "curves" to "Eğriler",
                            "hueSaturation" to "Renk/Doyg.",
                            "colorBalance" to "Renk Dengesi",
                            "blackWhite" to "Siyah-Beyaz",
                            "photoFilter" to "Foto Filtre",
                            "posterize" to "Poster",
                            "invert" to "Negatif"
                        ).forEach { (id, lbl) ->
                            ProPill(
                                selected = state.adjBrushKind == id,
                                onClick = { vm.updateV183ToolSettings(adjKind = id) },
                                label = { Text(lbl, fontSize = 11.sp) }
                            )
                        }
                    }
                }
                ActiveTool.ART_HISTORY_BRUSH -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "tightShort" to "Sıkı Kısa",
                            "tightMedium" to "Sıkı Orta",
                            "tightLong" to "Sıkı Uzun",
                            "looseMedium" to "Gevşek Orta",
                            "looseLong" to "Gevşek Uzun",
                            "dab" to "Benek",
                            "tightCurl" to "Sıkı Bukle",
                            "looseCurl" to "Gevşek Bukle"
                        ).forEach { (id, lbl) ->
                            ProPill(
                                selected = state.artHistoryStyle == id,
                                onClick = { vm.updateV183ToolSettings(artStyle = id) },
                                label = { Text(lbl, fontSize = 11.sp) }
                            )
                        }
                    }
                    CompactSliderRow("Alan", "${state.artHistoryArea.roundToInt()}px", state.artHistoryArea, 5f..200f, defaultValue = 50f) {
                        vm.updateV183ToolSettings(artArea = it)
                    }
                    CompactSliderRow("Tolerans", "%${state.artHistoryTolerance.roundToInt()}", state.artHistoryTolerance, 0f..100f, defaultValue = 0f) {
                        vm.updateV183ToolSettings(artTol = it)
                    }
                }
                ActiveTool.HEALING_BRUSH -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ProPill(selected = state.pickingCloneSource, onClick = { vm.startPickHealingSource() }, label = { Text("Kaynak Seç", fontSize = 11.sp) })
                        ProPill(selected = state.healingAligned, onClick = { vm.updateHealingPro(aligned = !state.healingAligned) }, label = { Text(if (state.healingAligned) "Hizalı ✓" else "Hizalı", fontSize = 11.sp) })
                        listOf("current" to "Katman", "currentAndBelow" to "Altı Dahil", "all" to "Tümü").forEach { (id, lbl) ->
                            ProPill(selected = state.healingSampleLayer == id, onClick = { vm.updateHealingPro(sampleLayer = id) }, label = { Text(lbl, fontSize = 10.5.sp) })
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("normal" to "Normal", "replace" to "Değiştir", "multiply" to "Çoğalt", "screen" to "Ekran", "darken" to "Koyu", "lighten" to "Açık").forEach { (id, lbl) ->
                            ProPill(selected = state.healingMode == id, onClick = { vm.updateHealingPro(mode = id) }, label = { Text(lbl, fontSize = 10.5.sp) })
                        }
                    }
                }
                ActiveTool.CLONE_STAMP -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { vm.requestPickCloneSource(); onDismiss() },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Kaynak", fontSize = 10.5.sp)
                        }
                        for (slot in 0..4) {
                            ProPill(
                                selected = state.cloneSlot == slot,
                                onClick = { vm.updateCloneSourcePro(slot = slot) },
                                label = { Text("${slot + 1}", fontSize = 11.sp) }
                            )
                        }
                        ProPill(selected = state.cloneAligned, onClick = { vm.updateCloneSourcePro(aligned = !state.cloneAligned) }, label = { Text(if (state.cloneAligned) "Hizalı ✓" else "Hizalı", fontSize = 10.5.sp) })
                        ProPill(selected = state.cloneFlipH, onClick = { vm.updateCloneSourcePro(flipH = !state.cloneFlipH) }, label = { Text("Yatay", fontSize = 10.5.sp) })
                        ProPill(selected = state.cloneFlipV, onClick = { vm.updateCloneSourcePro(flipV = !state.cloneFlipV) }, label = { Text("Dikey", fontSize = 10.5.sp) })
                        ProPill(selected = state.cloneOverlay, onClick = { vm.updateCloneSourcePro(overlay = !state.cloneOverlay) }, label = { Text("Bindirme", fontSize = 10.5.sp) })
                        ProPill(selected = false, onClick = { vm.resetCloneSourceTransform() }, label = { Text("Sıfırla", fontSize = 10.5.sp, color = Color(0xFFFF8A8A)) })
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        listOf("current" to "Katman", "currentAndBelow" to "Altı Dahil", "all" to "Tüm Katmanlar").forEach { (id, lbl) ->
                            ProPill(selected = state.cloneSampleLayer == id, onClick = { vm.updateCloneSourcePro(sampleLayer = id) }, label = { Text(lbl, fontSize = 10.5.sp) })
                        }
                    }
                    CompactSliderRow("Ölçek", "%${state.cloneScale.roundToInt()}", state.cloneScale, 25f..300f, defaultValue = 100f) {
                        vm.updateCloneSourcePro(scale = it)
                    }
                    CompactSliderRow("Döndür", "${state.cloneAngle.roundToInt()}°", state.cloneAngle, -180f..180f, defaultValue = 0f) {
                        vm.updateCloneSourcePro(angle = it)
                    }
                    CompactSliderRow("Sertlik", "%${(state.brushHardness * 100f).roundToInt()}", state.brushHardness, 0f..1f, defaultValue = 0.85f) {
                        vm.updateBrush(hardness = it)
                    }
                }
                else -> {
                    CompactSliderRow("Sertlik", "%${(state.brushHardness * 100f).roundToInt()}", state.brushHardness, 0f..1f, defaultValue = 0.85f) {
                        vm.updateBrush(hardness = it)
                    }
                }
            }
        }
    }
}

@Composable
private fun VisualBrushStrokeItem(
    name: String,
    hardness: Float,
    isSampled: Boolean,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) Color(0xFF2B436E) else Color(0xFF232632),
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) Color(0xFF6BB5FF) else Color(0xFF323646),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Live stylized stroke-wave preview (Photoshop/PhotoCraft style)
            Canvas(
                modifier = Modifier
                    .width(96.dp)
                    .height(26.dp)
            ) {
                val path = Path()
                val steps = 28
                for (i in 0..steps) {
                    val t = i.toFloat() / steps
                    val x = 6f + t * (size.width - 12f)
                    val y = size.height * 0.5f + sin(t * 2.0 * PI).toFloat() * (size.height * 0.28f)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                if (isSampled) {
                    // Textured / sampled brush wave
                    drawPath(
                        path = path,
                        color = Color(0x66DDE6FF),
                        style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                    for (i in 0..14) {
                        val t = i / 14f
                        val x = 6f + t * (size.width - 12f)
                        val y = size.height * 0.5f + sin(t * 2.0 * PI).toFloat() * (size.height * 0.28f)
                        drawCircle(Color.White, radius = (2.5f + (i % 3) * 1.2f).dp.toPx(), center = Offset(x, y))
                    }
                } else {
                    val softHalo = (1f - hardness.coerceIn(0f, 1f))
                    if (softHalo > 0.1f) {
                        drawPath(
                            path = path,
                            color = Color.White.copy(alpha = 0.22f * softHalo),
                            style = Stroke(width = (10f + softHalo * 6f).dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                        drawPath(
                            path = path,
                            color = Color.White.copy(alpha = 0.45f),
                            style = Stroke(width = (7f + softHalo * 2f).dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }
                    drawPath(
                        path = path,
                        color = Color.White,
                        style = Stroke(width = (5f + hardness * 3f).dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isSampled) "Örnekli Uç (.abr)" else "Sertlik %${(hardness * 100f).roundToInt()}",
                    color = Color(0xFF9EA5BA),
                    fontSize = 9.sp,
                    maxLines = 1
                )
            }
        }
    }
}
