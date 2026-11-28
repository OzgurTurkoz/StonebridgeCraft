package com.kastamonushop.app

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import com.kastamonushop.app.viewmodel.ActiveTool
import com.kastamonushop.app.viewmodel.EditorUiState
import com.kastamonushop.app.viewmodel.EditorViewModel

/**
 * Studio v2.0 Fixed 4-Workspace Geometry.
 *
 * Scalability Rule 1: The bottom dock NEVER scrolls and NEVER adds a 5th category tab.
 * Every existing and future tool registers into one of these 4 fixed workspaces
 * so spatial muscle memory stays intact even with 150+ features.
 */
internal enum class StudioWorkspace(
    val shortTitle: String,
    val fullTitle: String,
    val subtitle: String,
    val icon: ImageVector
) {
    LIGHT(
        shortTitle = "Işık",
        fullTitle = "Işık & Renk",
        subtitle = "Pozlama, eğriler, HSL, 3D LUT ve sinematik görünümler",
        icon = Icons.Default.WbSunny
    ),
    DRAW(
        shortTitle = "Çiz",
        fullTitle = "Çiz & Rötuş",
        subtitle = "60 FPS fırçalar, onarım, akıllı dolgu ve sıvılaştır",
        icon = Icons.Default.Brush
    ),
    SELECT(
        shortTitle = "Seç",
        fullTitle = "Seç & Maske",
        subtitle = "Kement, gökyüzü, renk aralığı, odak alanı ve hızlı maske",
        icon = Icons.Default.SelectAll
    ),
    STUDIO(
        shortTitle = "Stüdyo",
        fullTitle = "Stüdyo & Efekt",
        subtitle = "Kırp, metin, şekil, katman stilleri, bulanıklık ve filtreler",
        icon = Icons.Default.AutoAwesome
    )
}

internal enum class EnginePipeline(val badge: String) {
    LIVE_60FPS("60 FPS"),
    COALESCED_PROXY("Proxy"),
    INSTANT_OP("Anlık")
}

internal data class StudioLaunchContext(
    val vm: EditorViewModel,
    val state: EditorUiState,
    val s: StudioSessionState,
    val context: Context,
    val onPushGpuMirrorPreview: (MirrorModeOption, Float) -> Unit,
    val onClearGpuPreview: () -> Unit
)

internal data class StudioToolSpec(
    val id: String,
    val title: String,
    val workspace: StudioWorkspace,
    val group: String,
    val icon: ImageVector,
    val keywords: List<String>,
    val pipeline: EnginePipeline,
    val onLaunch: StudioLaunchContext.() -> Unit
)

/**
 * Single source of truth for all StonebridgeCraft tools & panels.
 * Adding any future feature requires only a single StudioToolSpec entry here:
 * it automatically appears in its workspace sheet, sub-group filter, Favorites, and instant Search.
 */
internal object ToolRegistryV2 {

    val defaultPinnedIds: Set<String> = setOf(
        "light_live",
        "camera_light",
        "brush",
        "remove_tool",
        "select_sky",
        "blur_edit",
        "crop_ratio",
        "text_edit"
    )

    val allTools: List<StudioToolSpec> = listOf(
        // =====================================================================
        // WORKSPACE 1: IŞIK & RENK (LIGHT)
        // =====================================================================
        StudioToolSpec(
            id = "light_live",
            title = "Işık & Kontrast",
            workspace = StudioWorkspace.LIGHT,
            group = "Temel Işık",
            icon = Icons.Default.Brightness6,
            keywords = listOf("parlaklık", "kontrast", "pozlama", "ışık", "brightness", "contrast"),
            pipeline = EnginePipeline.COALESCED_PROXY
        ) {
            vm.setTool(ActiveTool.NONE)
            s.brightnessVal = 0f
            s.contrastVal = 0f
            vm.beginProxyPreview()
            s.activePanel = BottomPanel.LIGHT_LIVE
        },
        StudioToolSpec(
            id = "camera_light",
            title = "Kamera Işığı",
            workspace = StudioWorkspace.LIGHT,
            group = "Temel Işık",
            icon = Icons.Default.Camera,
            keywords = listOf("raw", "pozlama", "gölge", "açık ton", "canlılık", "sıcaklık", "dehaze", "clarity"),
            pipeline = EnginePipeline.COALESCED_PROXY
        ) {
            vm.setTool(ActiveTool.NONE)
            s.camExposureVal = 0f; s.camShadowsVal = 0f; s.camHighlightsVal = 0f; s.camVibranceVal = 0f
            s.camTempVal = 0f; s.camTintVal = 0f; s.camClarityVal = 0f; s.camDehazeVal = 0f
            s.showCamFineTune = false
            vm.beginProxyPreview()
            s.activePanel = BottomPanel.CAMERA_LIGHT_LIVE
        },
        StudioToolSpec(
            id = "color_live",
            title = "Renk & Doygunluk",
            workspace = StudioWorkspace.LIGHT,
            group = "Temel Işık",
            icon = Icons.Default.Palette,
            keywords = listOf("renk", "ton", "doygunluk", "hue", "saturation"),
            pipeline = EnginePipeline.COALESCED_PROXY
        ) {
            vm.setTool(ActiveTool.NONE)
            s.hueVal = 0f
            s.saturationVal = 0f
            vm.beginProxyPreview()
            s.activePanel = BottomPanel.COLOR_LIVE
        },
        StudioToolSpec(
            id = "auto_enhance",
            title = "Oto İyileştir",
            workspace = StudioWorkspace.LIGHT,
            group = "Temel Işık",
            icon = Icons.Default.AutoFixHigh,
            keywords = listOf("oto", "otomatik", "iyileştir", "auto", "enhance", "ton"),
            pipeline = EnginePipeline.INSTANT_OP
        ) {
            vm.setTool(ActiveTool.NONE)
            s.activePanel = BottomPanel.AUTO_ENHANCE_MENU
        },
        StudioToolSpec(
            id = "curves_live",
            title = "Eğriler (Curves)",
            workspace = StudioWorkspace.LIGHT,
            group = "Pro Tonlama",
            icon = Icons.Default.ShowChart,
            keywords = listOf("eğri", "curves", "rgb", "ton", "kontrast"),
            pipeline = EnginePipeline.COALESCED_PROXY
        ) {
            vm.setTool(ActiveTool.NONE)
            s.resetAllCurves()
            s.curveUndoStack = emptyList()
            s.curveRedoStack = emptyList()
            s.curveOverlayExpanded = true
            vm.beginProxyPreview()
            vm.refreshHistogram(force = true)
            s.activePanel = BottomPanel.CURVES_LIVE
        },
        StudioToolSpec(
            id = "levels_live",
            title = "Seviyeler (Levels)",
            workspace = StudioWorkspace.LIGHT,
            group = "Pro Tonlama",
            icon = Icons.Default.Equalizer,
            keywords = listOf("seviye", "levels", "gama", "siyah", "beyaz"),
            pipeline = EnginePipeline.COALESCED_PROXY
        ) {
            vm.setTool(ActiveTool.NONE)
            s.resetLevelsPro()
            vm.beginProxyPreview()
            vm.refreshHistogram(force = true)
            s.activePanel = BottomPanel.LEVELS_LIVE
        },
        StudioToolSpec(
            id = "hsl_mixer",
            title = "HSL Mikseri",
            workspace = StudioWorkspace.LIGHT,
            group = "Pro Tonlama",
            icon = Icons.Default.Tune,
            keywords = listOf("hsl", "renk mikseri", "kanal", "kırmızı", "mavi", "yeşil"),
            pipeline = EnginePipeline.COALESCED_PROXY
        ) {
            vm.setTool(ActiveTool.NONE)
            s.resetHslPro()
            vm.beginProxyPreview()
            s.activePanel = BottomPanel.HSL_MIXER_LIVE
        },
        StudioToolSpec(
            id = "color_balance",
            title = "Renk Dengesi",
            workspace = StudioWorkspace.LIGHT,
            group = "Pro Tonlama",
            icon = Icons.Default.Contrast,
            keywords = listOf("renk dengesi", "color balance", "camgöbeği", "macenta", "sarı"),
            pipeline = EnginePipeline.COALESCED_PROXY
        ) {
            vm.setTool(ActiveTool.NONE)
            s.resetColorBalancePro()
            vm.beginProxyPreview()
            s.activePanel = BottomPanel.COLOR_BAL_LIVE
        },
        StudioToolSpec(
            id = "bw_pro",
            title = "Siyah-Beyaz Pro",
            workspace = StudioWorkspace.LIGHT,
            group = "Pro Tonlama",
            icon = Icons.Default.FilterBAndW,
            keywords = listOf("siyah beyaz", "bw", "monokrom", "sepya", "kanal"),
            pipeline = EnginePipeline.COALESCED_PROXY
        ) {
            vm.setTool(ActiveTool.NONE)
            s.resetBwPro()
            vm.beginProxyPreview()
            vm.updateProxyAdjustment("blackWhite", s.buildBwProJson())
            s.activePanel = BottomPanel.BW_PRO_EDIT
        },
        StudioToolSpec(
            id = "photo_filter",
            title = "Fotoğraf Filtresi",
            workspace = StudioWorkspace.LIGHT,
            group = "Pro Tonlama",
            icon = Icons.Default.FilterVintage,
            keywords = listOf("fotoğraf filtresi", "ısıtma", "soğutma", "sepya", "optik"),
            pipeline = EnginePipeline.COALESCED_PROXY
        ) {
            vm.setTool(ActiveTool.NONE)
            s.resetPhotoFilterPro()
            vm.beginProxyPreview()
            vm.updateProxyAdjustment("photoFilter", s.buildPhotoFilterJson())
            s.activePanel = BottomPanel.PHOTO_FILTER_EDIT
        },
        StudioToolSpec(
            id = "selective_color",
            title = "Seçici Renk",
            workspace = StudioWorkspace.LIGHT,
            group = "Pro Tonlama",
            icon = Icons.Default.Palette,
            keywords = listOf("seçici renk", "selective", "cmyk", "cyan", "macenta"),
            pipeline = EnginePipeline.COALESCED_PROXY
        ) {
            vm.setTool(ActiveTool.NONE)
            s.resetSelectiveColorPro()
            vm.beginProxyPreview()
            s.activePanel = BottomPanel.SELECTIVE_COLOR_EDIT
        },
        StudioToolSpec(
            id = "channel_mixer",
            title = "Kanal Karıştırıcı",
            workspace = StudioWorkspace.LIGHT,
            group = "Pro Tonlama",
            icon = Icons.Default.Tune,
            keywords = listOf("kanal karıştırıcı", "channel mixer", "rgb", "monokrom"),
            pipeline = EnginePipeline.COALESCED_PROXY
        ) {
            vm.setTool(ActiveTool.NONE)
            s.resetChannelMixerPro()
            vm.beginProxyPreview()
            s.activePanel = BottomPanel.CHANNEL_MIXER_EDIT
        },
        StudioToolSpec(
            id = "gradient_map",
            title = "Degrade Eşleme",
            workspace = StudioWorkspace.LIGHT,
            group = "Görünüm & LUT",
            icon = Icons.Default.Gradient,
            keywords = listOf("degrade eşleme", "gradient map", "duotone", "sinematik"),
            pipeline = EnginePipeline.COALESCED_PROXY
        ) {
            vm.setTool(ActiveTool.NONE)
            s.resetGradientMapPro()
            vm.beginProxyPreview()
            vm.updateProxyAdjustment("gradientMap", s.buildGradientMapJson())
            s.activePanel = BottomPanel.GRADIENT_MAP_EDIT
        },
        StudioToolSpec(
            id = "poster_thresh",
            title = "Posterize / Eşik",
            workspace = StudioWorkspace.LIGHT,
            group = "Pro Tonlama",
            icon = Icons.Default.FilterBAndW,
            keywords = listOf("posterize", "eşik", "threshold", "grafik"),
            pipeline = EnginePipeline.COALESCED_PROXY
        ) {
            vm.setTool(ActiveTool.NONE)
            s.posterLevels = 6f; s.threshLevel = 128f; s.posterTouched = false
            vm.beginProxyPreview()
            s.activePanel = BottomPanel.POSTER_THRESH_LIVE
        },
        StudioToolSpec(
            id = "cinema_look",
            title = "Sinema Görünümü",
            workspace = StudioWorkspace.LIGHT,
            group = "Görünüm & LUT",
            icon = Icons.Default.Movie,
            keywords = listOf("sinema", "film", "preset", "efekt", "gren", "vinyet"),
            pipeline = EnginePipeline.COALESCED_PROXY
        ) {
            vm.setTool(ActiveTool.NONE)
            s.cinemaGrain = 12f
            vm.loadCinemaThumbs()
            vm.beginProxyPreview()
            vm.updateProxyCinemaLook(s.cinemaPreset, s.cinemaIntensity, s.cinemaVignette, s.cinemaGrain)
            s.activePanel = BottomPanel.CINEMA_LOOK_EDIT
        },
        StudioToolSpec(
            id = "lut_import",
            title = "3D LUT (.cube)",
            workspace = StudioWorkspace.LIGHT,
            group = "Görünüm & LUT",
            icon = Icons.Default.ColorLens,
            keywords = listOf("lut", "cube", "renk profili", "sinematik"),
            pipeline = EnginePipeline.COALESCED_PROXY
        ) {
            vm.setTool(ActiveTool.NONE)
            vm.beginProxyPreview()
            s.activePanel = BottomPanel.LUT_IMPORT_EDIT
        },
        StudioToolSpec(
            id = "quick_actions",
            title = "Hızlı Aksiyonlar",
            workspace = StudioWorkspace.LIGHT,
            group = "Görünüm & LUT",
            icon = Icons.Default.Bolt,
            keywords = listOf("hızlı", "aksiyon", "tek tık", "quick"),
            pipeline = EnginePipeline.INSTANT_OP
        ) {
            vm.setTool(ActiveTool.NONE)
            s.activePanel = BottomPanel.QUICK_ACTIONS_MENU
        },

        // =====================================================================
        // WORKSPACE 2: ÇİZ & RÖTUŞ (DRAW)
        // =====================================================================
        StudioToolSpec(
            id = "brush",
            title = "Fırça Stüdyosu",
            workspace = StudioWorkspace.DRAW,
            group = "Çizim",
            icon = Icons.Default.Brush,
            keywords = listOf("fırça", "brush", "boya", "çizim", "simetri", "karıştırıcı", "abr"),
            pipeline = EnginePipeline.LIVE_60FPS
        ) {
            vm.setTool(ActiveTool.BRUSH)
            s.showToolPopover = false
            s.activePanel = BottomPanel.DRAW_MENU
        },
        StudioToolSpec(
            id = "pencil",
            title = "Keskin Kalem",
            workspace = StudioWorkspace.DRAW,
            group = "Çizim",
            icon = Icons.Default.Edit,
            keywords = listOf("kalem", "pencil", "piksel", "sert uç", "çiz"),
            pipeline = EnginePipeline.LIVE_60FPS
        ) {
            vm.setTool(ActiveTool.PENCIL)
            s.showToolPopover = false
            s.activePanel = BottomPanel.DRAW_MENU
        },
        StudioToolSpec(
            id = "eraser",
            title = "Silgi",
            workspace = StudioWorkspace.DRAW,
            group = "Çizim",
            icon = Icons.Default.AutoFixNormal,
            keywords = listOf("silgi", "eraser", "temizle"),
            pipeline = EnginePipeline.LIVE_60FPS
        ) {
            vm.setTool(ActiveTool.ERASER)
            s.showToolPopover = false
            s.activePanel = BottomPanel.DRAW_MENU
        },
        StudioToolSpec(
            id = "bg_eraser",
            title = "Arka Plan Silgi",
            workspace = StudioWorkspace.DRAW,
            group = "Çizim",
            icon = Icons.Default.ContentCut,
            keywords = listOf("arka plan silgi", "background eraser", "şeffaf"),
            pipeline = EnginePipeline.LIVE_60FPS
        ) {
            vm.setTool(ActiveTool.BG_ERASER)
            s.showToolPopover = false
            s.activePanel = BottomPanel.DRAW_MENU
        },
        StudioToolSpec(
            id = "magic_eraser",
            title = "Sihirli Silgi",
            workspace = StudioWorkspace.DRAW,
            group = "Çizim",
            icon = Icons.Default.AutoAwesome,
            keywords = listOf("sihirli silgi", "magic eraser", "tek tık sil"),
            pipeline = EnginePipeline.INSTANT_OP
        ) {
            vm.setTool(ActiveTool.MAGIC_ERASER)
            s.showToolPopover = false
            s.activePanel = BottomPanel.DRAW_MENU
        },
        StudioToolSpec(
            id = "adjust_brush",
            title = "Ayar Fırçası",
            workspace = StudioWorkspace.DRAW,
            group = "Çizim",
            icon = Icons.Default.Flare,
            keywords = listOf("ayar fırçası", "adjustment brush", "bölgesel pozlama"),
            pipeline = EnginePipeline.LIVE_60FPS
        ) {
            vm.setTool(ActiveTool.ADJUST_BRUSH)
            s.showToolPopover = false
            s.activePanel = BottomPanel.DRAW_MENU
        },
        StudioToolSpec(
            id = "history_brush",
            title = "Geçmiş Fırçası",
            workspace = StudioWorkspace.DRAW,
            group = "Çizim",
            icon = Icons.Default.History,
            keywords = listOf("geçmiş fırçası", "history brush", "sanat geçmişi", "geri boya"),
            pipeline = EnginePipeline.LIVE_60FPS
        ) {
            vm.setTool(ActiveTool.HISTORY_BRUSH)
            s.showToolPopover = false
            s.activePanel = BottomPanel.DRAW_MENU
        },
        StudioToolSpec(
            id = "bucket",
            title = "Boya Kovası",
            workspace = StudioWorkspace.DRAW,
            group = "Çizim",
            icon = Icons.Default.FormatColorFill,
            keywords = listOf("kova", "doldur", "bucket", "fill"),
            pipeline = EnginePipeline.INSTANT_OP
        ) {
            vm.setTool(ActiveTool.BUCKET)
            s.showToolPopover = false
            s.activePanel = BottomPanel.DRAW_MENU
        },
        StudioToolSpec(
            id = "spot_heal",
            title = "Nokta & Rötuş",
            workspace = StudioWorkspace.DRAW,
            group = "Rötuş & Onarım",
            icon = Icons.Default.Healing,
            keywords = listOf("rötuş", "leke", "onarım", "spot heal", "klon", "soldur", "yak", "dağıt"),
            pipeline = EnginePipeline.LIVE_60FPS
        ) {
            vm.setTool(ActiveTool.SPOT_HEAL)
            s.showToolPopover = false
            s.activePanel = BottomPanel.RETOUCH_MENU
        },
        StudioToolSpec(
            id = "healing_brush",
            title = "Onarım Fırçası",
            workspace = StudioWorkspace.DRAW,
            group = "Rötuş & Onarım",
            icon = Icons.Default.MyLocation,
            keywords = listOf("onarım fırçası", "healing brush", "kaynak", "doku"),
            pipeline = EnginePipeline.LIVE_60FPS
        ) {
            vm.setTool(ActiveTool.HEALING_BRUSH)
            s.showToolPopover = false
            s.activePanel = BottomPanel.RETOUCH_MENU
        },
        StudioToolSpec(
            id = "clone_stamp",
            title = "Klon Damgası",
            workspace = StudioWorkspace.DRAW,
            group = "Rötuş & Onarım",
            icon = Icons.Default.ContentCopy,
            keywords = listOf("klon", "clone", "stamp", "kopyala"),
            pipeline = EnginePipeline.LIVE_60FPS
        ) {
            vm.setTool(ActiveTool.CLONE_STAMP)
            s.showToolPopover = false
            s.activePanel = BottomPanel.RETOUCH_MENU
        },
        StudioToolSpec(
            id = "remove_tool",
            title = "Akıllı Kaldır",
            workspace = StudioWorkspace.DRAW,
            group = "Akıllı Dolgu",
            icon = Icons.Default.CleaningServices,
            keywords = listOf("kaldır", "remove", "nesne sil", "temizle"),
            pipeline = EnginePipeline.COALESCED_PROXY
        ) {
            vm.setTool(ActiveTool.REMOVE_TOOL)
            s.showToolPopover = false
            s.activePanel = BottomPanel.RETOUCH_MENU
        },
        StudioToolSpec(
            id = "ca_fill",
            title = "İçeriğe Uygun Dolgu",
            workspace = StudioWorkspace.DRAW,
            group = "Akıllı Dolgu",
            icon = Icons.Default.AutoFixHigh,
            keywords = listOf("içeriğe uygun", "content aware", "dolgu", "fill"),
            pipeline = EnginePipeline.COALESCED_PROXY
        ) {
            s.cafRotationAdaptation = "none"; s.cafScale = false
            s.cafMirror = false; s.cafSampleAllLayers = false; s.cafOutput = "current"
            vm.setTool(ActiveTool.NONE)
            vm.beginProxyPreview()
            vm.updateProxyContentAwareFill("auto", "default", "none", false, false, false, "current")
            s.activePanel = BottomPanel.CONTENT_AWARE_FILL_EDIT
        },
        StudioToolSpec(
            id = "ca_move",
            title = "İçeriğe Uygun Taşı",
            workspace = StudioWorkspace.DRAW,
            group = "Akıllı Dolgu",
            icon = Icons.Default.OpenWith,
            keywords = listOf("taşı", "content aware move", "uzat", "extend"),
            pipeline = EnginePipeline.COALESCED_PROXY
        ) {
            s.camMoveDx = 0f; s.camMoveDy = 0f; s.camMoveMode = "move"
            s.camMoveStructure = 4f; s.camMoveColor = 0f
            s.camMoveScale = 100f; s.camMoveRotate = 0f; s.camMoveSampleAll = false
            vm.setTool(ActiveTool.NONE)
            vm.beginProxyPreview()
            s.activePanel = BottomPanel.CONTENT_AWARE_MOVE_EDIT
        },
        StudioToolSpec(
            id = "liquify",
            title = "Sıvılaştır (Liquify)",
            workspace = StudioWorkspace.DRAW,
            group = "Şekillendir",
            icon = Icons.Default.Waves,
            keywords = listOf("sıvılaştır", "liquify", "bük", "şekillendir", "yüz"),
            pipeline = EnginePipeline.LIVE_60FPS
        ) {
            vm.setTool(ActiveTool.NONE)
            val docMax = maxOf(state.docInfo?.width?.toInt() ?: 1024, state.docInfo?.height?.toInt() ?: 1024).toFloat()
            s.liquifySize = (docMax * 0.14f).coerceIn(48f, 220f)
            s.liquifyPressure = 75f
            vm.beginLiquify()
            s.activePanel = BottomPanel.LIQUIFY_EDIT
        },
        StudioToolSpec(
            id = "puppet_warp",
            title = "Kukla Bük (Puppet)",
            workspace = StudioWorkspace.DRAW,
            group = "Şekillendir",
            icon = Icons.Default.Hub,
            keywords = listOf("kukla", "puppet", "warp", "iğne", "pin"),
            pipeline = EnginePipeline.COALESCED_PROXY
        ) {
            vm.setTool(ActiveTool.NONE)
            s.puppetMode = "normal"
            s.puppetUndoStack = emptyList()
            s.puppetRedoStack = emptyList()
            vm.beginProxyPreview()
            s.activePanel = BottomPanel.PUPPET_WARP_EDIT
        },

        // =====================================================================
        // WORKSPACE 3: SEÇ & MASKE (SELECT)
        // =====================================================================
        StudioToolSpec(
            id = "select_quick",
            title = "Hızlı Seçim",
            workspace = StudioWorkspace.SELECT,
            group = "Manuel Seçim",
            icon = Icons.Default.TouchApp,
            keywords = listOf("hızlı seçim", "quick select", "seç"),
            pipeline = EnginePipeline.LIVE_60FPS
        ) {
            vm.setTool(ActiveTool.SELECT_QUICK)
            s.activePanel = BottomPanel.SELECT_MENU
        },
        StudioToolSpec(
            id = "select_lasso",
            title = "Kement Seçim",
            workspace = StudioWorkspace.SELECT,
            group = "Manuel Seçim",
            icon = Icons.Default.Gesture,
            keywords = listOf("kement", "lasso", "serbest seçim"),
            pipeline = EnginePipeline.LIVE_60FPS
        ) {
            vm.setTool(ActiveTool.SELECT_LASSO)
            s.activePanel = BottomPanel.SELECT_MENU
        },
        StudioToolSpec(
            id = "select_mag_lasso",
            title = "Manyetik Kement",
            workspace = StudioWorkspace.SELECT,
            group = "Manuel Seçim",
            icon = Icons.Default.Attractions,
            keywords = listOf("manyetik", "magnetic lasso", "kenar"),
            pipeline = EnginePipeline.LIVE_60FPS
        ) {
            vm.setTool(ActiveTool.SELECT_MAGNETIC_LASSO)
            s.activePanel = BottomPanel.SELECT_MENU
        },
        StudioToolSpec(
            id = "select_brush",
            title = "Seçim Fırçası",
            workspace = StudioWorkspace.SELECT,
            group = "Manuel Seçim",
            icon = Icons.Default.AddCircleOutline,
            keywords = listOf("fırça seçim", "ekle", "sil", "boyayarak seç"),
            pipeline = EnginePipeline.LIVE_60FPS
        ) {
            vm.setTool(ActiveTool.SELECT_BRUSH_ADD)
            s.activePanel = BottomPanel.SELECT_MENU
        },
        StudioToolSpec(
            id = "select_wand",
            title = "Sihirli Değnek",
            workspace = StudioWorkspace.SELECT,
            group = "Manuel Seçim",
            icon = Icons.Default.AutoAwesome,
            keywords = listOf("sihirli değnek", "wand", "benzer renk"),
            pipeline = EnginePipeline.LIVE_60FPS
        ) {
            vm.setTool(ActiveTool.SELECT_WAND)
            s.activePanel = BottomPanel.SELECT_MENU
        },
        StudioToolSpec(
            id = "select_rect",
            title = "Kutu / Elips",
            workspace = StudioWorkspace.SELECT,
            group = "Manuel Seçim",
            icon = Icons.Default.CropSquare,
            keywords = listOf("kare", "kutu", "dikdörtgen", "elips", "daire"),
            pipeline = EnginePipeline.LIVE_60FPS
        ) {
            vm.setTool(ActiveTool.SELECT_RECT)
            s.activePanel = BottomPanel.SELECT_MENU
        },
        StudioToolSpec(
            id = "select_subject",
            title = "Konuyu Seç",
            workspace = StudioWorkspace.SELECT,
            group = "Akıllı Seçim",
            icon = Icons.Default.PersonSearch,
            keywords = listOf("konuyu seç", "subject", "nesne", "otomatik"),
            pipeline = EnginePipeline.INSTANT_OP
        ) {
            vm.selectSubject()
            s.activePanel = BottomPanel.SELECT_MENU
        },
        StudioToolSpec(
            id = "select_sky",
            title = "Gökyüzü Seç",
            workspace = StudioWorkspace.SELECT,
            group = "Akıllı Seçim",
            icon = Icons.Default.Cloud,
            keywords = listOf("gökyüzü", "sky", "bulut", "otomatik"),
            pipeline = EnginePipeline.COALESCED_PROXY
        ) {
            vm.setTool(ActiveTool.NONE)
            vm.beginProxyPreview()
            vm.updateProxySelectSky(s.skyThreshold, s.skySoftness, s.skyMode, s.skySampleAll)
            s.activePanel = BottomPanel.SELECT_SKY_EDIT
        },
        StudioToolSpec(
            id = "select_color_range",
            title = "Renk Aralığı",
            workspace = StudioWorkspace.SELECT,
            group = "Akıllı Seçim",
            icon = Icons.Default.Colorize,
            keywords = listOf("renk aralığı", "color range", "seçim"),
            pipeline = EnginePipeline.COALESCED_PROXY
        ) {
            vm.setTool(ActiveTool.NONE)
            vm.beginProxyPreview()
            vm.updateProxyColorRange(s.crPreset, s.crColorHex, s.crFuzziness, s.crInvert, s.crMode, s.crSampleAll)
            s.activePanel = BottomPanel.SELECT_COLOR_RANGE_EDIT
        },
        StudioToolSpec(
            id = "select_focus_area",
            title = "Odak Alanı Seç",
            workspace = StudioWorkspace.SELECT,
            group = "Akıllı Seçim",
            icon = Icons.Default.CenterFocusWeak,
            keywords = listOf("odak", "focus area", "net alan", "bokeh"),
            pipeline = EnginePipeline.COALESCED_PROXY
        ) {
            vm.setTool(ActiveTool.NONE)
            vm.beginProxyPreview()
            vm.updateProxyFocusArea(s.focusRange, s.focusNoise, s.focusMode, s.focusSampleAll)
            s.activePanel = BottomPanel.SELECT_FOCUS_AREA_EDIT
        },
        StudioToolSpec(
            id = "select_modify",
            title = "Seçimi İncelt",
            workspace = StudioWorkspace.SELECT,
            group = "Maske & İncelt",
            icon = Icons.Default.Tune,
            keywords = listOf("genişlet", "daralt", "yumuşat", "feather", "border", "smooth", "modify"),
            pipeline = EnginePipeline.INSTANT_OP
        ) {
            vm.setTool(ActiveTool.NONE)
            vm.beginProxyPreview()
            vm.updateProxyModifySelection(s.selModifyOp, s.selModifyRadius)
            s.activePanel = BottomPanel.SELECT_MODIFY_EDIT
        },
        StudioToolSpec(
            id = "quick_mask",
            title = "Hızlı Maske (Q)",
            workspace = StudioWorkspace.SELECT,
            group = "Maske & İncelt",
            icon = Icons.Default.Masks,
            keywords = listOf("hızlı maske", "quick mask", "kırmızı maske"),
            pipeline = EnginePipeline.LIVE_60FPS
        ) {
            vm.toggleQuickMaskMode(!state.quickMaskMode)
            s.activePanel = BottomPanel.SELECT_MENU
        },

        // =====================================================================
        // WORKSPACE 4: STÜDYO & EFEKT (STUDIO)
        // =====================================================================
        StudioToolSpec(
            id = "crop_ratio",
            title = "Kırp & Döndür",
            workspace = StudioWorkspace.STUDIO,
            group = "Geometri",
            icon = Icons.Default.Crop,
            keywords = listOf("kırp", "crop", "döndür", "oran", "çevir"),
            pipeline = EnginePipeline.LIVE_60FPS
        ) {
            vm.setTool(ActiveTool.NONE)
            s.pendingQuarterTurns = 0
            s.pendingFlipH = false
            s.pendingFlipV = false
            s.activePanel = BottomPanel.CROP_MENU
        },
        StudioToolSpec(
            id = "persp_crop",
            title = "Perspektif Kırp",
            workspace = StudioWorkspace.STUDIO,
            group = "Geometri",
            icon = Icons.Default.GridOn,
            keywords = listOf("perspektif", "perspective", "düzelt"),
            pipeline = EnginePipeline.COALESCED_PROXY
        ) {
            vm.setTool(ActiveTool.NONE)
            s.pTl = Offset(0.12f, 0.12f); s.pTr = Offset(0.88f, 0.12f)
            s.pBr = Offset(0.88f, 0.88f); s.pBl = Offset(0.12f, 0.88f)
            s.activePanel = BottomPanel.PERSPECTIVE_CROP_EDIT
        },
        StudioToolSpec(
            id = "image_resize",
            title = "Yeniden Boyutlandır",
            workspace = StudioWorkspace.STUDIO,
            group = "Geometri",
            icon = Icons.Default.PhotoSizeSelectLarge,
            keywords = listOf("boyut", "resize", "çözünürlük", "piksel"),
            pipeline = EnginePipeline.INSTANT_OP
        ) {
            vm.setTool(ActiveTool.NONE)
            val dw0 = state.docInfo?.width?.toInt() ?: 1080
            val dh0 = state.docInfo?.height?.toInt() ?: 1080
            s.resizeWidthText = dw0.toString()
            s.resizeHeightText = dh0.toString()
            s.resizeLockAspect = true
            s.resizeResample = "lanczos"
            vm.beginProxyPreview()
            s.activePanel = BottomPanel.IMAGE_RESIZE_EDIT
        },
        StudioToolSpec(
            id = "ca_scale",
            title = "Akıllı Ölçek",
            workspace = StudioWorkspace.STUDIO,
            group = "Geometri",
            icon = Icons.Default.AspectRatio,
            keywords = listOf("akıllı ölçek", "content aware scale", "seam"),
            pipeline = EnginePipeline.COALESCED_PROXY
        ) {
            vm.setTool(ActiveTool.NONE)
            s.casScaleX = 100f; s.casScaleY = 100f; s.casAmount = 100f; s.casProtectSkinTones = true; s.casBrushActive = false; s.casProtectStrokes = emptyList(); s.casActiveScreenStroke = emptyList()
            vm.beginProxyPreview()
            s.activePanel = BottomPanel.CONTENT_AWARE_SCALE_EDIT
        },
        StudioToolSpec(
            id = "lens_corr",
            title = "Lens Düzeltme",
            workspace = StudioWorkspace.STUDIO,
            group = "Geometri",
            icon = Icons.Default.CameraEnhance,
            keywords = listOf("lens", "bükülme", "vinyet", "kromatik"),
            pipeline = EnginePipeline.COALESCED_PROXY
        ) {
            vm.setTool(ActiveTool.NONE)
            s.lensDistortion = 0f; s.lensVignette = 0f
            s.lensRedCyan = 0f; s.lensBlueYellow = 0f
            s.lensVertical = 0f; s.lensHorizontal = 0f
            vm.beginProxyPreview()
            s.activePanel = BottomPanel.LENS_CORRECTION_EDIT
        },
        StudioToolSpec(
            id = "text_edit",
            title = "Metin Ekle",
            workspace = StudioWorkspace.STUDIO,
            group = "Oluştur",
            icon = Icons.Default.Title,
            keywords = listOf("metin", "yazı", "font", "text", "tipografi"),
            pipeline = EnginePipeline.LIVE_60FPS
        ) {
            vm.setTool(ActiveTool.NONE)
            vm.ensureStudioFontsLoaded(context)
            vm.beginProxyPreview()
            val docW = (state.docInfo?.width?.toInt() ?: 2048).toFloat()
            s.resetTextProDefaults(docW)
            vm.inspectActiveTextLayer { existing ->
                if (existing != null) {
                    s.textEditLayerId = existing.layerId
                    s.textDraft = existing.text
                    s.textFontFamily = existing.fontFamily.ifBlank { "Montserrat" }
                    s.textSizePx = existing.sizePx.coerceIn(6f, 600f)
                    s.textColorHex = existing.colorHex
                    s.textAlign = existing.align
                    s.textBold = existing.bold
                    s.textItalic = existing.italic
                    s.textUnderline = existing.underline
                    s.textStrike = existing.strikethrough
                    s.textCaps = existing.caps
                    s.textHScale = existing.hScale
                    s.textVScale = existing.vScale
                    s.textTracking = existing.tracking
                    s.textLineHeight = existing.lineHeight
                    s.textVertical = existing.vertical
                    s.textWarpStyle = existing.warpStyle
                    s.textWarpBend = existing.warpBend
                    s.textWarpHDist = existing.warpHDist
                    s.textWarpVDist = existing.warpVDist
                    s.textNormX = existing.normX
                    s.textNormY = existing.normY
                }
            }
            s.activePanel = BottomPanel.TEXT_EDIT
        },
        StudioToolSpec(
            id = "shape_edit",
            title = "Vektör Şekil",
            workspace = StudioWorkspace.STUDIO,
            group = "Oluştur",
            icon = Icons.Default.Category,
            keywords = listOf("şekil", "shape", "kare", "daire", "yıldız", "ok"),
            pipeline = EnginePipeline.COALESCED_PROXY
        ) {
            vm.setTool(ActiveTool.NONE)
            val docW = (state.docInfo?.width?.toInt() ?: 2048).toFloat()
            val docH = (state.docInfo?.height?.toInt() ?: 2048).toFloat()
            s.shapeX0Norm = 0.25f; s.shapeY0Norm = 0.25f
            s.shapeX1Norm = 0.75f; s.shapeY1Norm = 0.75f
            s.showShapeFineTune = false
            vm.beginProxyPreview()
            vm.updateProxyShape(
                s.shapeKind,
                docW * s.shapeX0Norm, docH * s.shapeY0Norm,
                docW * s.shapeX1Norm, docH * s.shapeY1Norm,
                s.shapeFillHex, s.shapeStrokeHex, s.shapeStrokeWidth, s.shapeCornerRadius, s.shapeSides
            )
            s.activePanel = BottomPanel.SHAPE_EDIT
        },
        StudioToolSpec(
            id = "gradient_edit",
            title = "Degrade",
            workspace = StudioWorkspace.STUDIO,
            group = "Oluştur",
            icon = Icons.Default.Gradient,
            keywords = listOf("degrade", "gradient", "renk geçişi"),
            pipeline = EnginePipeline.COALESCED_PROXY
        ) {
            vm.setTool(ActiveTool.NONE)
            val docW = (state.docInfo?.width?.toInt() ?: 2048).toFloat()
            val docH = (state.docInfo?.height?.toInt() ?: 2048).toFloat()
            s.gradFromNormX = 0.20f; s.gradFromNormY = 0.20f
            s.gradToNormX = 0.80f; s.gradToNormY = 0.80f
            vm.beginProxyPreview()
            vm.updateProxyGradient(
                docW * s.gradFromNormX, docH * s.gradFromNormY,
                docW * s.gradToNormX, docH * s.gradToNormY,
                s.gradStyle, s.gradStartHex, s.gradEndHex, s.gradOpacity
            )
            s.activePanel = BottomPanel.GRADIENT_EDIT
        },
        StudioToolSpec(
            id = "blur_edit",
            title = "Gauss Bulanıklık",
            workspace = StudioWorkspace.STUDIO,
            group = "Filtre & Efekt",
            icon = Icons.Default.BlurOn,
            keywords = listOf("bulanıklık", "blur", "gauss", "flu"),
            pipeline = EnginePipeline.COALESCED_PROXY
        ) {
            vm.setTool(ActiveTool.NONE)
            s.blurRadiusVal = 0f
            vm.beginProxyPreview()
            s.activePanel = BottomPanel.BLUR_EDIT
        },
        StudioToolSpec(
            id = "sharpen_edit",
            title = "Keskinleştir",
            workspace = StudioWorkspace.STUDIO,
            group = "Filtre & Efekt",
            icon = Icons.Default.Details,
            keywords = listOf("keskinleştir", "sharpen", "netleştir"),
            pipeline = EnginePipeline.COALESCED_PROXY
        ) {
            vm.setTool(ActiveTool.NONE)
            s.sharpenAmountVal = 0f
            vm.beginProxyPreview()
            s.activePanel = BottomPanel.SHARPEN_EDIT
        },
        StudioToolSpec(
            id = "motion_blur",
            title = "Hareket Bulanıklığı",
            workspace = StudioWorkspace.STUDIO,
            group = "Filtre & Efekt",
            icon = Icons.Default.BlurLinear,
            keywords = listOf("hareket", "motion blur", "hız"),
            pipeline = EnginePipeline.COALESCED_PROXY
        ) {
            vm.setTool(ActiveTool.NONE)
            s.motionBlurAngle = 0f; s.motionBlurDistance = 24f
            vm.beginProxyPreview()
            vm.updateProxyMotionBlur(s.motionBlurAngle, s.motionBlurDistance)
            s.activePanel = BottomPanel.MOTION_BLUR_EDIT
        },
        StudioToolSpec(
            id = "radial_blur",
            title = "Radyal Bulanıklık",
            workspace = StudioWorkspace.STUDIO,
            group = "Filtre & Efekt",
            icon = Icons.Default.BlurCircular,
            keywords = listOf("radyal", "radial blur", "zoom", "dönme"),
            pipeline = EnginePipeline.COALESCED_PROXY
        ) {
            vm.setTool(ActiveTool.NONE)
            s.radialBlurAmount = 14f; s.radialBlurMethod = "zoom"
            vm.beginProxyPreview()
            vm.updateProxyRadialBlur(s.radialBlurAmount, s.radialBlurMethod)
            s.activePanel = BottomPanel.RADIAL_BLUR_EDIT
        },
        StudioToolSpec(
            id = "filter_gallery",
            title = "Filtre Galerisi",
            workspace = StudioWorkspace.STUDIO,
            group = "Filtre & Efekt",
            icon = Icons.Default.Apps,
            keywords = listOf("filtre galerisi", "sanatsal", "eskiz", "yağlı boya", "suluboya"),
            pipeline = EnginePipeline.COALESCED_PROXY
        ) {
            vm.setTool(ActiveTool.NONE)
            s.galleryKind = "none"; s.galleryAmount = 45f; s.galleryDetail = 50f
            vm.loadGalleryThumbs()
            vm.beginProxyPreview()
            s.activePanel = BottomPanel.FILTER_GALLERY_EDIT
        },
        StudioToolSpec(
            id = "light_distort",
            title = "Işık & Distorsiyon",
            workspace = StudioWorkspace.STUDIO,
            group = "Filtre & Efekt",
            icon = Icons.Default.LensBlur,
            keywords = listOf("distorsiyon", "ışık", "lens flare", "dalga", "girdap"),
            pipeline = EnginePipeline.COALESCED_PROXY
        ) {
            vm.setTool(ActiveTool.NONE)
            s.ldKind = "none"; s.ldAmount = 50f; s.ldDetail = 45f; s.ldX = 35f; s.ldY = 30f; s.ldVariant = "moviePrime"
            vm.loadLightDistortThumbs()
            vm.beginProxyPreview()
            s.activePanel = BottomPanel.LIGHT_DISTORT_EDIT
        },
        StudioToolSpec(
            id = "mirror_edit",
            title = "Ayna & Efektler",
            workspace = StudioWorkspace.STUDIO,
            group = "Filtre & Efekt",
            icon = Icons.Default.Flip,
            keywords = listOf("ayna", "mirror", "kaleydoskop", "siyah beyaz", "ters çevir"),
            pipeline = EnginePipeline.LIVE_60FPS
        ) {
            vm.setTool(ActiveTool.NONE)
            s.selectedMirrorMode = MIRROR_MODES.first()
            s.mirrorAxisRatio = 0.50f
            s.canvasViewRef?.fitToDocument(0)
            onPushGpuMirrorPreview(s.selectedMirrorMode, s.mirrorAxisRatio)
            s.activePanel = BottomPanel.MIRROR_EDIT
        },
        StudioToolSpec(
            id = "effect_menu",
            title = "Siyah-Beyaz / Ters",
            workspace = StudioWorkspace.STUDIO,
            group = "Filtre & Efekt",
            icon = Icons.Default.AutoAwesome,
            keywords = listOf("efekt", "siyah beyaz", "bw", "invert", "ters"),
            pipeline = EnginePipeline.INSTANT_OP
        ) {
            vm.setTool(ActiveTool.NONE)
            s.activePanel = BottomPanel.EFFECT_MENU
        }
    )

    fun toolsFor(workspace: StudioWorkspace): List<StudioToolSpec> =
        allTools.filter { it.workspace == workspace }

    fun groupsFor(workspace: StudioWorkspace): List<String> =
        listOf("Tümü", "Sabitlenenler") + toolsFor(workspace).map { it.group }.distinct()

    fun search(query: String): List<StudioToolSpec> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return allTools
        return allTools.filter { tool ->
            tool.title.lowercase().contains(q) ||
                tool.group.lowercase().contains(q) ||
                tool.workspace.fullTitle.lowercase().contains(q) ||
                tool.keywords.any { it.lowercase().contains(q) }
        }
    }
}
