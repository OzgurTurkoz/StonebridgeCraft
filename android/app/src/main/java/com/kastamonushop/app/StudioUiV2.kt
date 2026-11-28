package com.kastamonushop.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * StonebridgeCraft Studio v2.0 OLED Charcoal + Warm Amber Design Tokens.
 */
internal object StudioV2Colors {
    val OledCanvasBg = Color(0xFF07080A)
    val GlassBar = Color(0xE8101218)
    val GlassSheet = Color(0xF50E1016)
    val GlassBorder = Color(0x22FFFFFF)
    val TileBg = Color(0x12FFFFFF)
    val TileBorder = Color(0x16FFFFFF)
    val Amber = Color(0xFFF59E0B)
    val AmberSoft = Color(0x2AF59E0B)
    val AmberBorder = Color(0x66F59E0B)
    val TextPrimary = Color(0xFFF3F4F6)
    val TextMuted = Color(0xFF9CA3AF)
    val Badge60FpsBg = Color(0x2410B981)
    val Badge60FpsText = Color(0xFF34D399)
    val BadgeProxyBg = Color(0x2438BDF8)
    val BadgeProxyText = Color(0xFF38BDF8)
}

/**
 * Fixed 4 + 1 Studio Bottom Dock.
 * NEVER scrolls horizontally; every workspace stays at the exact same thumb coordinate.
 */
@Composable
internal fun StudioFixedDockV2(
    activeWorkspace: StudioWorkspace?,
    onSelectWorkspace: (StudioWorkspace) -> Unit,
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StudioWorkspace.entries.forEach { ws ->
            val selected = activeWorkspace == ws
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selected) Color(0x1EFFFFFF) else Color.Transparent)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelectWorkspace(ws)
                    }
                    .padding(vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = ws.icon,
                    contentDescription = ws.fullTitle,
                    tint = if (selected) StudioV2Colors.Amber else StudioV2Colors.TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = ws.shortTitle,
                    color = if (selected) StudioV2Colors.Amber else StudioV2Colors.TextMuted,
                    fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Fixed 5th slot: Instant Command Palette Search
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(StudioV2Colors.AmberSoft)
                .border(1.dp, StudioV2Colors.AmberBorder, RoundedCornerShape(14.dp))
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onOpenSearch()
                }
                .padding(vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Araç Ara",
                tint = StudioV2Colors.Amber,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Ara",
                color = StudioV2Colors.Amber,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

/**
 * Frosted Dark-Glass Workspace Tool Drawer.
 * Opens above the fixed dock with sub-group pills and large 3-column touch tiles.
 */
@Composable
internal fun StudioWorkspaceSheetV2(
    workspace: StudioWorkspace,
    pinnedIds: Set<String>,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState(),
    initialGroup: String = "Tümü",
    onTogglePin: (String) -> Unit,
    onSelectTool: (StudioToolSpec) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val groups = remember(workspace) { ToolRegistryV2.groupsFor(workspace) }
    var selectedGroup by remember(workspace, initialGroup) { mutableStateOf(initialGroup) }

    val tools = remember(workspace, selectedGroup, pinnedIds) {
        val base = ToolRegistryV2.toolsFor(workspace)
        when (selectedGroup) {
            "Tümü" -> base
            "Sabitlenenler" -> base.filter { it.id in pinnedIds }
            else -> base.filter { it.group == selectedGroup }
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 390.dp),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = StudioV2Colors.GlassSheet,
        border = androidx.compose.foundation.BorderStroke(1.dp, StudioV2Colors.GlassBorder),
        tonalElevation = 12.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Drag indicator
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .align(Alignment.CenterHorizontally)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color(0x33FFFFFF))
            )

            // Sheet Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = workspace.fullTitle,
                        color = StudioV2Colors.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = workspace.subtitle,
                        color = StudioV2Colors.TextMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0x18FFFFFF))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Kapat",
                        tint = StudioV2Colors.TextPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Sub-group Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                groups.forEach { grp ->
                    val isSel = selectedGroup == grp
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (isSel) StudioV2Colors.Amber else Color(0x16FFFFFF))
                            .clickable { selectedGroup = grp }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = grp,
                            color = if (isSel) Color(0xFF0A0B0E) else StudioV2Colors.TextPrimary,
                            fontSize = 11.5.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (tools.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Bu bölümde sabitlenmiş araç yok. Ara (🔍) içinden yıldızlayabilirsin.",
                        color = StudioV2Colors.TextMuted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            } else {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(tools, key = { it.id }) { tool ->
                        val isPinned = tool.id in pinnedIds
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(78.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(StudioV2Colors.TileBg)
                                .border(1.dp, StudioV2Colors.TileBorder, RoundedCornerShape(16.dp))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSelectTool(tool)
                                }
                        ) {
                            // Subtle Pin indicator top-right
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .clickable { onTogglePin(tool.id) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isPinned) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = "Sabitle",
                                    tint = if (isPinned) StudioV2Colors.Amber else Color(0x44FFFFFF),
                                    modifier = Modifier.size(13.dp)
                                )
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 6.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = tool.icon,
                                    contentDescription = tool.title,
                                    tint = StudioV2Colors.Amber,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = tool.title,
                                    color = StudioV2Colors.TextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    lineHeight = 13.sp,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Instant Command Palette / Fuzzy Tool Search Overlay.
 * Scales seamlessly to 150+ future tools in 2 keystrokes.
 */
@Composable
internal fun StudioCommandPaletteV2(
    pinnedIds: Set<String>,
    onTogglePin: (String) -> Unit,
    onSelectTool: (StudioToolSpec) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val results = remember(query) { ToolRegistryV2.search(query) }
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE807080A))
            .statusBarsPadding()
            .navigationBarsPadding()
            .clickable(enabled = true, onClick = {}) // absorb background taps
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            // Search Bar Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF151821))
                        .border(1.dp, StudioV2Colors.AmberBorder, RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = StudioV2Colors.Amber,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                text = "Araç ara (Gökyüzü, Eğri, Fırça, LUT...)",
                                color = StudioV2Colors.TextMuted,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = StudioV2Colors.TextPrimary,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            cursorBrush = SolidColor(StudioV2Colors.Amber),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Temizle", tint = StudioV2Colors.TextMuted, modifier = Modifier.size(15.dp))
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .height(46.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x1EFFFFFF))
                        .clickable { onDismiss() }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Kapat", color = StudioV2Colors.TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${results.size} / ${ToolRegistryV2.allTools.size} araç hazır",
                    color = StudioV2Colors.TextMuted,
                    fontSize = 11.sp
                )
                Text(
                    text = "Yıldızla → Sabitlenenlere ekle",
                    color = StudioV2Colors.TextMuted,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(results, key = { it.id }) { tool ->
                    val isPinned = tool.id in pinnedIds
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0x14FFFFFF))
                            .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(14.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelectTool(tool)
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(StudioV2Colors.AmberSoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = tool.icon,
                                contentDescription = null,
                                tint = StudioV2Colors.Amber,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tool.title,
                                color = StudioV2Colors.TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${tool.workspace.fullTitle} · ${tool.group}",
                                color = StudioV2Colors.TextMuted,
                                fontSize = 11.sp
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (tool.pipeline == EnginePipeline.LIVE_60FPS) StudioV2Colors.Badge60FpsBg
                                    else StudioV2Colors.BadgeProxyBg
                                )
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = tool.pipeline.badge,
                                color = if (tool.pipeline == EnginePipeline.LIVE_60FPS) StudioV2Colors.Badge60FpsText
                                else StudioV2Colors.BadgeProxyText,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = { onTogglePin(tool.id) },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = if (isPinned) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "Sabitle",
                                tint = if (isPinned) StudioV2Colors.Amber else StudioV2Colors.TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
