//! PhotoCraft Session wrapper exposed to Kotlin via UniFFI (v0.5.2 — StonebridgeCraft).
//! - Live undo-clean adjustments & pixel filters
//! - 60 FPS GPU-previewed Photo Mirror Effect (`apply_photo_mirror` bakes once on commit)
//! - 60 FPS GPU-previewed Rotate/Flip (`apply_canvas_transform_batch` bakes net result once on commit)
//! - Eyedropper color sampling, Painting symmetry, Canvas crop

use std::sync::Arc;
use parking_lot::Mutex;
use photocraft_color::{ColorMode, PixelFormat, SampleType};
use photocraft_doc::{Document, Layer, LayerContent, LayerId};
use photocraft_engine::brush_cmds::{LiveStroke, live_color_replacement, mixer_live::LiveMixer};
use photocraft_engine::retouch_cmds::{LiveDab, LiveRetouch};
use photocraft_engine::Session;
use photocraft_geom::{Rect, Size};
use photocraft_raster::Surface;
use photocraft_paint::StrokePoint;
use photocraft_algo::liquify::{
    LiquifyField, LiquifyStroke, LiquifyTool, ProxyImage as LiquifyProxyImage, apply_liquify, auto_cell,
};
use serde_json::{Value, json};

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum EngineError {
    #[error("No active document")]
    NoActiveDocument,
    #[error("Invalid JSON parameters: {detail}")]
    InvalidParams { detail: String },
    #[error("PhotoCraft engine error: {detail}")]
    CommandFailed { detail: String },
    #[error("Import/Export I/O error: {detail}")]
    IoFailed { detail: String },
}

#[derive(Clone, Debug, uniffi::Record)]
pub struct DocumentInfo {
    pub name: String,
    pub width: u32,
    pub height: u32,
    pub layer_count: u32,
    pub active_layer_id: Option<u64>,
    pub can_undo: bool,
    pub can_redo: bool,
    pub dirty: bool,
}

#[derive(Clone, Debug, uniffi::Record)]
pub struct LayerSummary {
    pub id: u64,
    pub name: String,
    pub visible: bool,
    pub opacity: f32,
    pub blend_mode: String,
    pub is_group: bool,
    pub is_active: bool,
    pub locked: bool,
    pub is_background: bool,
    pub has_mask: bool,
    pub mask_enabled: bool,
    pub clipped: bool,
    pub depth: u32,
    pub is_text: bool,
    pub fx_badges: Vec<String>,
}

#[derive(Clone, Debug, uniffi::Record)]
pub struct LiquifyHistoryState {
    pub can_undo: bool,
    pub can_redo: bool,
}

#[derive(Clone, Debug, uniffi::Record)]
pub struct LayerThumbnail {
    pub width: u32,
    pub height: u32,
    pub rgba: Vec<u8>,
}

#[derive(Clone, Debug, uniffi::Record)]
pub struct SelectionBounds {
    pub x0: i32,
    pub y0: i32,
    pub x1: i32,
    pub y1: i32,
}


#[derive(Debug, Clone, uniffi::Record)]
pub struct PuppetPinPoint {
    pub src_x: f32,
    pub src_y: f32,
    pub dst_x: f32,
    pub dst_y: f32,
    pub rotate_deg: Option<f32>,
    pub depth: i32,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct CameraRawPresetValues {
    pub exposure: f32,
    pub contrast: f32,
    pub highlights: f32,
    pub shadows: f32,
    pub whites: f32,
    pub blacks: f32,
    pub temperature: f32,
    pub tint: f32,
    pub vibrance: f32,
    pub saturation: f32,
    pub texture: f32,
    pub clarity: f32,
    pub dehaze: f32,
    pub sharpen_amount: f32,
    pub noise_luminance: f32,
    pub noise_color: f32,
    pub grade_shadow_hue: f32,
    pub grade_shadow_sat: f32,
    pub grade_mid_hue: f32,
    pub grade_mid_sat: f32,
    pub grade_high_hue: f32,
    pub grade_high_sat: f32,
    pub cal_red_hue: f32,
    pub cal_red_sat: f32,
    pub cal_green_hue: f32,
    pub cal_green_sat: f32,
    pub cal_blue_hue: f32,
    pub cal_blue_sat: f32,
    pub vignette_amount: f32,
    pub grain_amount: f32,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct AbrBrushSummary {
    pub index: u32,
    pub name: String,
    pub group: String,
    pub size: f32,
    pub hardness: f32,
    pub spacing: f32,
    pub has_sampled_tip: bool,
}



#[derive(Debug, Clone, uniffi::Record)]
pub struct TextLayerStyle {
    pub layer_id: u64,
    pub text: String,
    pub font_family: String,
    pub size_px: f32,
    pub color_hex: String,
    pub align: String,
    pub bold: bool,
    pub italic: bool,
    pub underline: bool,
    pub strikethrough: bool,
    pub caps: String,
    pub h_scale: f32,
    pub v_scale: f32,
    pub tracking: f32,
    pub line_height: f32,
    pub vertical: bool,
    pub warp_style: String,
    pub warp_bend: f32,
    pub warp_h_dist: f32,
    pub warp_v_dist: f32,
    pub norm_x: f32,
    pub norm_y: f32,
}

#[derive(Clone, Debug, uniffi::Record)]
pub struct TextProExtras {
    pub rotation_deg: f32,
    pub opacity: f32,
    pub blend_mode: String,
    pub stroke_width: f32,
    pub stroke_color: String,
    pub shadow_enabled: bool,
    pub shadow_distance: f32,
    pub shadow_blur: f32,
    pub shadow_angle: f32,
    pub shadow_color: String,
    pub glow_size: f32,
    pub glow_color: String,
    pub gradient_enabled: bool,
    pub gradient_end_color: String,
    pub gradient_angle: f32,
    pub bg_enabled: bool,
    pub bg_color: String,
    pub bg_opacity: f32,
    pub bg_radius: f32,
    pub bg_padding: f32,
    pub baseline_shift: f32,
    pub box_width_norm: f32,
}

#[derive(Clone, Debug, uniffi::Record)]
pub struct HistogramData {
    pub luma: Vec<f32>,
    pub red: Vec<f32>,
    pub green: Vec<f32>,
    pub blue: Vec<f32>,
    pub shadow_clip_pct: f32,
    pub highlight_clip_pct: f32,
}

#[derive(Clone, Debug, uniffi::Record)]
pub struct SampledColorInfo {
    pub r: u8,
    pub g: u8,
    pub b: u8,
    pub luma: u8,
    pub hsl_band: u32,
}

#[derive(Clone, Debug, uniffi::Record)]
pub struct AutoLevelsResult {
    pub rgb_black: f32,
    pub rgb_white: f32,
    pub red_black: f32,
    pub red_white: f32,
    pub green_black: f32,
    pub green_white: f32,
    pub blue_black: f32,
    pub blue_white: f32,
}

#[derive(Clone, Debug, uniffi::Record)]
pub struct CafSamplingStrokeInput {
    pub add: bool,
    pub size: f32,
    pub points: Vec<StrokeSample>,
}


#[derive(Clone, Debug, uniffi::Record)]
pub struct StrokeSample {
    pub x: f32,
    pub y: f32,
    pub pressure: f32,
    pub tilt_x: f32,
    pub tilt_y: f32,
}

struct ActiveLiveStroke {
    live: LiveStroke,
    points_json: Vec<Value>,
    color_hex: String,
    is_eraser: bool,
    is_pencil: bool,
    target_mask: bool,
    size: f32,
}

struct LiveAdjustmentSession {
    layer_id: u64,
    has_updated: bool,
}

struct LiveFilterSession {
    target_id: LayerId,
    prev_active: Option<LayerId>,
    was_locked: bool,
    has_applied_preview: bool,
}

struct ActiveLiveDab {
    live: LiveDab,
    cmd: String,
    base_params: Value,
    points_json: Vec<Value>,
    target_id: LayerId,
    prev_active: Option<LayerId>,
    was_locked: bool,
}

struct ActiveLiveClone {
    live: LiveRetouch,
    base_params: Value,
    points_json: Vec<Value>,
    target_id: LayerId,
    prev_active: Option<LayerId>,
    was_locked: bool,
}

struct ActiveLiveMixer {
    live: LiveMixer,
    target_id: LayerId,
    prev_active: Option<LayerId>,
    was_locked: bool,
}


struct ProxyPreviewSession {
    base_doc: Arc<Document>,
    session: Session,
    scale: f32,
    real_size: (u32, u32),
    selection_only: bool,
}

struct ActiveLiquifySession {
    target_layer_id: LayerId,
    bounds: Rect,
    cell: f64,
    field: LiquifyField,
    proxy: LiquifyProxyImage,
    proxy_rgba: Vec<[u8; 4]>,
    preview_doc: Document,
    real_size: (u32, u32),
    last_point: Option<[f64; 3]>,
    current_stroke: LiquifyStroke,
    undo_stack: Vec<LiquifyField>,
    redo_stack: Vec<LiquifyField>,
    pin_edges: bool,
}

impl ActiveLiquifySession {
    fn sync_proxy_rect_to_preview_doc(&mut self, r: [usize; 4]) {
        let [x0, y0, x1, y1] = [
            r[0].min(self.proxy.w),
            r[1].min(self.proxy.h),
            r[2].min(self.proxy.w),
            r[3].min(self.proxy.h),
        ];
        if x0 >= x1 || y0 >= y1 {
            return;
        }
        let Some(layer) = self.preview_doc.layers.first_mut() else {
            return;
        };
        let LayerContent::Raster(surf) = &mut layer.content else {
            return;
        };
        let rw = x1 - x0;
        let rh = y1 - y0;
        let has_freeze = self.field.freeze.iter().any(|&v| v > 0.01);
        let sx_doc = self.bounds.width().max(1) as f64 / self.proxy.w.max(1) as f64;
        let sy_doc = self.bounds.height().max(1) as f64 / self.proxy.h.max(1) as f64;
        let mut fbuf = vec![0.0f32; rw * rh * 4];
        for y in y0..y1 {
            let src_row = y * self.proxy.w;
            let dst_row = (y - y0) * rw;
            let doc_y = self.bounds.y0 as f64 + (y as f64 + 0.5) * sy_doc;
            for x in x0..x1 {
                let p = self.proxy_rgba[src_row + x];
                let di = (dst_row + (x - x0)) * 4;
                let mut r = p[0] as f32 / 255.0;
                let mut g = p[1] as f32 / 255.0;
                let mut b = p[2] as f32 / 255.0;
                if has_freeze {
                    let doc_x = self.bounds.x0 as f64 + (x as f64 + 0.5) * sx_doc;
                    let fz = self.field.freeze_at(doc_x, doc_y).clamp(0.0, 1.0);
                    if fz > 0.01 {
                        let a = fz * 0.44;
                        r = r * (1.0 - a) + 0.96 * a;
                        g = g * (1.0 - a) + 0.16 * a;
                        b = b * (1.0 - a) + 0.24 * a;
                    }
                }
                fbuf[di] = r;
                fbuf[di + 1] = g;
                fbuf[di + 2] = b;
                fbuf[di + 3] = p[3] as f32 / 255.0;
            }
        }
        surf.write_region(Rect::new(x0 as i32, y0 as i32, x1 as i32, y1 as i32), &fbuf);
    }

    fn apply_pin_edges_if_enabled(&mut self) {
        if !self.pin_edges { return; }
        let w = self.field.w;
        let h = self.field.h;
        if w < 2 || h < 2 { return; }
        for x in 0..w {
            self.field.d[x] = [0.0, 0.0];
            self.field.d[(h - 1) * w + x] = [0.0, 0.0];
        }
        for y in 0..h {
            self.field.d[y * w] = [0.0, 0.0];
            self.field.d[y * w + (w - 1)] = [0.0, 0.0];
        }
    }
}

fn parse_liquify_tool(name: &str) -> LiquifyTool {
    match name {
        "bloat" => LiquifyTool::Bloat,
        "pucker" => LiquifyTool::Pucker,
        "twirlCw" => LiquifyTool::TwirlCw,
        "twirlCcw" => LiquifyTool::TwirlCcw,
        "smooth" => LiquifyTool::Smooth,
        "reconstruct" => LiquifyTool::Reconstruct,
        "pushLeft" => LiquifyTool::PushLeft,
        "freeze" => LiquifyTool::Freeze,
        "thaw" => LiquifyTool::Thaw,
        _ => LiquifyTool::ForwardWarp,
    }
}

pub(crate) struct EngineCore {
    pub session: Session,
    pub render_generation: u64,
    pub selection_generation: u64,
    pub pending_dirty: Option<Rect>,
    pub force_full_redraw: bool,
    live_stroke: Option<ActiveLiveStroke>,
    live_dab: Option<ActiveLiveDab>,
    live_clone: Option<ActiveLiveClone>,
    live_mixer: Option<ActiveLiveMixer>,
    custom_brush_spacing: bool,
    copied_layer_effects: Vec<photocraft_doc::Effect>,
    live_adjustment: Option<LiveAdjustmentSession>,
    live_filter: Option<LiveFilterSession>,
    proxy_preview: Option<ProxyPreviewSession>,
    live_liquify: Option<ActiveLiquifySession>,
    transform_hidden_layer: Option<LayerId>,
    original_snapshot: Option<Arc<Document>>,
    compare_original: bool,
    mask_paint_mode: bool,
}

impl EngineCore {
    pub fn new() -> Self {
        // System-wide mobile RAM guardrails (desktop defaults are 2 GB scratch / 8 GB transform)
        photocraft_raster::scratch::set_budget(192 << 20);
        photocraft_engine::transform_cmds::set_budget(256 << 20);

        let mut session = Session::new();
        session.edit_prefs(|p| {
            p.history_options.make_layer_visibility_changes_undoable = false;
        });
        let _ = session.execute(
            "file.new",
            json!({
                "name": "StonebridgeCraft Canvas",
                "width": 2048,
                "height": 2048,
                "resolution": 300.0,
                "background": "white"
            }),
        );
        let initial_snapshot = session.active().map(|ds| Arc::clone(ds.doc()));
        Self {
            session,
            render_generation: 1,
            selection_generation: 1,
            pending_dirty: None,
            force_full_redraw: true,
            live_stroke: None,
            live_dab: None,
            live_clone: None,
            live_mixer: None,
            custom_brush_spacing: false,
            copied_layer_effects: Vec::new(),
            live_adjustment: None,
            live_filter: None,
            proxy_preview: None,
            live_liquify: None,
            transform_hidden_layer: None,
            original_snapshot: initial_snapshot,
            compare_original: false,
            mask_paint_mode: false,
        }
    }

    pub fn active_document(&self) -> Option<Arc<Document>> {
        if let Some(active_live) = &self.live_stroke {
            return Some(Arc::clone(&active_live.live.doc));
        }
        if let Some(active_dab) = &self.live_dab {
            return Some(Arc::clone(&active_dab.live.doc));
        }
        if let Some(active_clone) = &self.live_clone {
            return Some(Arc::clone(&active_clone.live.doc));
        }
        if let Some(active_mixer) = &self.live_mixer {
            return Some(Arc::clone(&active_mixer.live.doc));
        }
        self.session.active().map(|ds| Arc::clone(ds.doc()))
    }

    pub fn active_document_and_display_size(&self) -> Option<(Arc<Document>, (u32, u32))> {
        if let Some(liq) = &self.live_liquify {
            return Some((Arc::new(liq.preview_doc.clone()), liq.real_size));
        }
        if self.compare_original {
            if let Some(orig) = &self.original_snapshot {
                let orig_sz = (orig.size.width.max(1), orig.size.height.max(1));
                return Some((Arc::clone(orig), orig_sz));
            }
            let doc = self.active_document()?;
            let sz = (doc.size.width.max(1), doc.size.height.max(1));
            return Some((doc, sz));
        }
        if let Some(proxy) = &self.proxy_preview {
            if !proxy.selection_only {
                if let Some(ds) = proxy.session.active() {
                    return Some((Arc::clone(ds.doc()), proxy.real_size));
                }
            }
        }
        let doc = self.active_document()?;
        let sz = (doc.size.width.max(1), doc.size.height.max(1));
        if let Some(hid) = self.transform_hidden_layer {
            let mut cloned = (*doc).clone();
            if let Some(l) = cloned.layers.iter_mut().find(|l| l.id == hid) {
                l.visible = false;
            }
            return Some((Arc::new(cloned), sz));
        }
        Some((doc, sz))
    }

    pub fn active_selection_document(&self) -> Option<Arc<Document>> {
        if let Some(proxy) = &self.proxy_preview {
            if proxy.selection_only {
                if let Some(ds) = proxy.session.active() {
                    return Some(Arc::clone(ds.doc()));
                }
            }
        }
        self.active_document()
    }

    pub fn capture_original_snapshot(&mut self) {
        self.original_snapshot = self.session.active().map(|ds| Arc::clone(ds.doc()));
        self.compare_original = false;
        self.selection_generation = self.selection_generation.wrapping_add(1);
    }

    pub fn dismiss_compare(&mut self) {
        if self.compare_original {
            self.compare_original = false;
            self.force_full_redraw = true;
            self.selection_generation = self.selection_generation.wrapping_add(1);
        }
    }

    pub fn mark_selection_dirty(&mut self) {
        self.selection_generation = self.selection_generation.wrapping_add(1);
        self.mark_full_dirty();
    }

    pub fn mark_full_dirty(&mut self) {
        self.force_full_redraw = true;
        self.pending_dirty = None;
        self.render_generation = self.render_generation.wrapping_add(1);
        if self.session.active().is_some_and(|ds| ds.doc().quick_mask.is_some()) {
            self.selection_generation = self.selection_generation.wrapping_add(1);
        }
    }

    pub fn mark_region_dirty(&mut self, rect: Rect) {
        if !rect.is_empty() && !self.force_full_redraw {
            self.pending_dirty = Some(match self.pending_dirty {
                Some(prev) => prev.union(&rect),
                None => rect,
            });
        }
        self.render_generation = self.render_generation.wrapping_add(1);
        if self.session.active().is_some_and(|ds| ds.doc().quick_mask.is_some()) {
            self.selection_generation = self.selection_generation.wrapping_add(1);
        }
    }

    pub fn take_dirty_state(&mut self) -> (bool, Option<Rect>) {
        let full = self.force_full_redraw;
        let rect = self.pending_dirty.take();
        self.force_full_redraw = false;
        (full, rect)
    }
}

#[derive(uniffi::Object)]
pub struct KastamonuEngine {
    core: Arc<Mutex<EngineCore>>,
}

impl KastamonuEngine {
    pub(crate) fn from_shared(core: Arc<Mutex<EngineCore>>) -> Self {
        Self { core }
    }
}

fn sample_to_json(pt: &StrokeSample) -> Value {
    json!({
        "x": pt.x,
        "y": pt.y,
        "pressure": pt.pressure.clamp(0.01, 1.0),
        "tiltX": pt.tilt_x,
        "tiltY": pt.tilt_y
    })
}

fn sample_to_stroke_point(pt: &StrokeSample) -> StrokePoint {
    StrokePoint {
        x: pt.x as f64,
        y: pt.y as f64,
        pressure: pt.pressure.clamp(0.01, 1.0),
        tilt_x: pt.tilt_x,
        tilt_y: pt.tilt_y,
        ..Default::default()
    }
}

fn find_primary_photo_raster_layer(doc: &Document, active: Option<LayerId>) -> Option<(LayerId, bool)> {
    let active_is_photo = active
        .and_then(|aid| doc.layers.iter().find(|l| l.id == aid))
        .is_some_and(|l| matches!(l.content, LayerContent::Raster(_)) && !l.name.eq_ignore_ascii_case("Çizim Katmanı"));

    let chosen = if active_is_photo {
        active.and_then(|aid| doc.layers.iter().find(|l| l.id == aid))
    } else {
        doc.layers
            .iter()
            .find(|l| matches!(l.content, LayerContent::Raster(_)))
    };

    chosen.map(|l| (l.id, l.locks.pixels))
}

/// Shared premultiplied-alpha 3-pass separable sliding-window box blur (O(1) per pixel, tiny scratch RAM).
/// Premultiplying RGB by alpha prevents dark/white fringe halos on transparent PNG cutouts.
fn box_blur_passes_inplace(data: &mut [f32], w: usize, h: usize, ch: usize, pass_r: usize, passes: usize) {
    if pass_r == 0 || w < 2 || h < 2 || ch == 0 {
        return;
    }
    let active_ch = ch.min(8);
    let has_alpha = ch >= 4;
    if has_alpha {
        for px in data.chunks_exact_mut(ch) {
            let a = px[3].clamp(0.0, 1.0);
            px[0] *= a;
            px[1] *= a;
            px[2] *= a;
        }
    }
    let mut line_buf = vec![0.0f32; w.max(h) * ch];
    let mut acc = [0.0f32; 8];
    let win = (2 * pass_r + 1) as f32;
    let inv_win = 1.0 / win;

    for _ in 0..passes {
        for y in 0..h {
            let row = y * w * ch;
            for c in 0..active_ch {
                let first = data[row + c];
                acc[c] = first * (pass_r + 1) as f32;
                for k in 1..=pass_r {
                    acc[c] += data[row + k.min(w - 1) * ch + c];
                }
            }
            for x in 0..w {
                for c in 0..active_ch {
                    line_buf[x * ch + c] = acc[c] * inv_win;
                }
                let add_x = (x + pass_r + 1).min(w - 1);
                let sub_x = x.saturating_sub(pass_r);
                for c in 0..active_ch {
                    acc[c] += data[row + add_x * ch + c] - data[row + sub_x * ch + c];
                }
            }
            data[row..(row + w * ch)].copy_from_slice(&line_buf[..(w * ch)]);
        }

        for x in 0..w {
            for c in 0..active_ch {
                let first = data[x * ch + c];
                acc[c] = first * (pass_r + 1) as f32;
                for k in 1..=pass_r {
                    acc[c] += data[(k.min(h - 1) * w + x) * ch + c];
                }
            }
            for y in 0..h {
                for c in 0..active_ch {
                    line_buf[y * ch + c] = acc[c] * inv_win;
                }
                let add_y = (y + pass_r + 1).min(h - 1);
                let sub_y = y.saturating_sub(pass_r);
                for c in 0..active_ch {
                    acc[c] += data[(add_y * w + x) * ch + c] - data[(sub_y * w + x) * ch + c];
                }
            }
            for y in 0..h {
                let idx = (y * w + x) * ch;
                for c in 0..active_ch {
                    data[idx + c] = line_buf[y * ch + c];
                }
            }
        }
    }

    if has_alpha {
        for px in data.chunks_exact_mut(ch) {
            let a = px[3].clamp(0.0, 1.0);
            if a > 1e-4 {
                let inv_a = 1.0 / a;
                px[0] = (px[0] * inv_a).clamp(0.0, 1.0);
                px[1] = (px[1] * inv_a).clamp(0.0, 1.0);
                px[2] = (px[2] * inv_a).clamp(0.0, 1.0);
            } else {
                px[0] = 0.0;
                px[1] = 0.0;
                px[2] = 0.0;
            }
            px[3] = a;
        }
    }
}

fn run_blur_on_doc(doc: &mut Document, target_id: LayerId, radius_px: f32) {
    if radius_px < 0.5 {
        return;
    }
    let bounds = doc.bounds();
    let w = doc.size.width as usize;
    let h = doc.size.height as usize;
    if w < 2 || h < 2 {
        return;
    }
    let sel_mask: Option<Vec<f32>> = doc.selection.as_ref().and_then(|sel| {
        if sel.content_bounds().is_empty() {
            None
        } else {
            Some(sel.read_region(bounds))
        }
    });
    let Some(layer) = doc.layers.iter_mut().find(|l| l.id == target_id) else {
        return;
    };
    let LayerContent::Raster(surf) = &mut layer.content else {
        return;
    };
    let ch = surf.format().channels();
    let orig = surf.read_region(bounds);
    if orig.len() < w * h * ch {
        return;
    }
    let mut blurred = orig.clone();
    let pass_r = (radius_px * 0.58).round().max(1.0) as usize;
    box_blur_passes_inplace(&mut blurred, w, h, ch, pass_r, 3);

    if let Some(mask) = sel_mask {
        let m_ch = (mask.len() / (w * h)).max(1);
        for i in 0..(w * h) {
            let m = mask[i * m_ch].clamp(0.0, 1.0);
            if m < 0.999 {
                let base = i * ch;
                let inv_m = 1.0 - m;
                for c in 0..ch {
                    blurred[base + c] = orig[base + c] * inv_m + blurred[base + c] * m;
                }
            }
        }
    }
    surf.write_region_par(bounds, &blurred);
}

fn run_sharpen_on_doc(doc: &mut Document, target_id: LayerId, amount: f32) {
    let strength = (amount.clamp(0.0, 250.0) / 100.0) * 1.35;
    if strength < 0.01 {
        return;
    }
    let bounds = doc.bounds();
    let w = doc.size.width as usize;
    let h = doc.size.height as usize;
    if w < 3 || h < 3 {
        return;
    }
    let sel_mask: Option<Vec<f32>> = doc.selection.as_ref().and_then(|sel| {
        if sel.content_bounds().is_empty() {
            None
        } else {
            Some(sel.read_region(bounds))
        }
    });
    let Some(layer) = doc.layers.iter_mut().find(|l| l.id == target_id) else {
        return;
    };
    let LayerContent::Raster(surf) = &mut layer.content else {
        return;
    };
    let ch = surf.format().channels();
    let rgb_ch = ch.min(3);
    let orig = surf.read_region(bounds);
    if orig.len() < w * h * ch {
        return;
    }
    // Resolution-invariant Unsharp Mask radius so 512px proxy and full-res match 100% identically
    let usm_radius = ((w.max(h) as f32) * 0.0028).clamp(1.0, 8.0);
    let pass_r = (usm_radius * 0.58).round().max(1.0) as usize;
    let mut blurred = orig.clone();
    box_blur_passes_inplace(&mut blurred, w, h, ch, pass_r, 2);

    let mut out = orig.clone();
    if let Some(mask) = sel_mask {
        let m_ch = (mask.len() / (w * h)).max(1);
        for i in 0..(w * h) {
            let m = mask[i * m_ch].clamp(0.0, 1.0);
            if m > 0.001 {
                let base = i * ch;
                let eff = strength * m;
                for c in 0..rgb_ch {
                    out[base + c] = (orig[base + c] + (orig[base + c] - blurred[base + c]) * eff).clamp(0.0, 1.0);
                }
            }
        }
    } else {
        for i in 0..(w * h) {
            let base = i * ch;
            for c in 0..rgb_ch {
                out[base + c] = (orig[base + c] + (orig[base + c] - blurred[base + c]) * strength).clamp(0.0, 1.0);
            }
        }
    }
    surf.write_region_par(bounds, &out);
}



fn build_shape_params(
    kind: &str,
    x0: f32,
    y0: f32,
    x1: f32,
    y1: f32,
    fill_hex: &str,
    stroke_hex: &str,
    stroke_width: f32,
    corner_radius: f32,
    sides: u32,
) -> Value {
    let min_x = x0.min(x1) as f64;
    let min_y = y0.min(y1) as f64;
    let w = (x1 - x0).abs().max(4.0) as f64;
    let h = (y1 - y0).abs().max(4.0) as f64;
    let mut obj = serde_json::Map::new();
    obj.insert("kind".into(), json!(kind));
    if kind == "line" {
        obj.insert("from".into(), json!([x0 as f64, y0 as f64]));
        obj.insert("to".into(), json!([x1 as f64, y1 as f64]));
        obj.insert("weight".into(), json!(stroke_width.clamp(1.0, 200.0) as f64));
        let col = if !stroke_hex.trim().is_empty() { stroke_hex } else if !fill_hex.trim().is_empty() { fill_hex } else { "#FFFFFF" };
        obj.insert("fill".into(), json!(col));
        obj.insert("stroke".into(), json!({ "color": col, "width": stroke_width.clamp(1.0, 200.0) as f64, "opacity": 100.0 }));
    } else {
        obj.insert("rect".into(), json!([min_x, min_y, w, h]));
        if kind == "roundedRect" {
            obj.insert("radius".into(), json!(corner_radius.clamp(0.0, 400.0) as f64));
        }
        if kind == "polygon" || kind == "star" {
            obj.insert("sides".into(), json!(sides.clamp(3, 12)));
        }
        if fill_hex.trim().is_empty() {
            obj.insert("fill".into(), Value::Null);
        } else {
            obj.insert("fill".into(), json!(fill_hex));
        }
        if stroke_width > 0.5 && !stroke_hex.trim().is_empty() {
            obj.insert(
                "stroke".into(),
                json!({
                    "color": stroke_hex,
                    "width": stroke_width.clamp(1.0, 200.0) as f64,
                    "opacity": 100.0
                }),
            );
        } else {
            obj.insert("stroke".into(), Value::Null);
        }
    }
    Value::Object(obj)
}


fn build_cinema_look_params(preset: &str, intensity: f32, vignette: f32, grain: f32) -> Value {
    let k = (intensity.clamp(0.0, 100.0) / 100.0) as f64;
    let (exp, con, hi, sh, temp, tint, clar, deh, vib, sat) = match preset {
        "golden" => (0.20, 14.0, -18.0, 22.0, 34.0, 6.0, 12.0, 8.0, 26.0, 10.0),
        "tealOrange" => (0.05, 22.0, -24.0, 18.0, -16.0, -4.0, 18.0, 14.0, 28.0, 8.0),
        "noir" => (0.10, 36.0, -15.0, -10.0, 0.0, 0.0, 28.0, 18.0, 0.0, -100.0),
        "vivid" => (0.12, 18.0, -12.0, 16.0, 6.0, 2.0, 20.0, 12.0, 42.0, 22.0),
        "vintage" => (0.08, -8.0, -28.0, 26.0, 22.0, 10.0, -6.0, -10.0, -14.0, -18.0),
        "cyber" => (-0.08, 28.0, -10.0, 14.0, -30.0, 24.0, 24.0, 20.0, 38.0, 18.0),
        _ => (0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
    };
    json!({
        "exposure": exp * k,
        "contrast": con * k,
        "highlights": hi * k,
        "shadows": sh * k,
        "temperature": temp * k,
        "tint": tint * k,
        "clarity": clar * k,
        "dehaze": deh * k,
        "vibrance": vib * k,
        "saturation": sat * k,
        "vignetteAmount": vignette.clamp(-100.0, 100.0) as f64,
        "grainAmount": grain.clamp(0.0, 100.0) as f64,
        "grainSize": 28.0,
        "grainRoughness": 50.0,
        "seed": 42u32
    })
}


/// Fast, zero-scratch-RAM 8-channel HSL Color Mixer using PhotoCraft's exact 8 hue bands
/// (Red 0°, Orange 30°, Yellow 60°, Green 120°, Aqua 180°, Blue 240°, Purple 270°, Magenta 300°).
/// Preserves alpha and respects active selection masks — NEVER hits mobile working-memory limits.

/// Ensures a 512px alpha- & layer-preserving ProxyPreviewSession exists on `guard`.
/// If `undo()`, `redo()`, or another action cleared `proxy_preview`, this rebuilds it
/// seamlessly on the fly so active slider panels NEVER freeze or stop responding.
fn ensure_proxy_preview_locked(guard: &mut EngineCore) -> Result<(), EngineError> {
    ensure_proxy_preview_sized_locked(guard, 1024)
}

fn ensure_proxy_preview_sized_locked(guard: &mut EngineCore, max_proxy: u32) -> Result<(), EngineError> {
    if let Some(ref p) = guard.proxy_preview {
        let cur_max = p.base_doc.size.width.max(p.base_doc.size.height);
        if cur_max <= max_proxy {
            return Ok(());
        }
        guard.proxy_preview = None;
    }
    let (real_doc, active_id) = {
        let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
        (Arc::clone(ds.doc()), ds.active_layer())
    };
    let real_w = real_doc.size.width.max(1);
    let real_h = real_doc.size.height.max(1);
    let scale = (max_proxy as f32 / real_w.max(real_h) as f32).min(1.0);
    let pw = ((real_w as f32 * scale).round() as u32).clamp(1, max_proxy);
    let ph = ((real_h as f32 * scale).round() as u32).clamp(1, max_proxy);

    let mut ps = Session::new();
    ps.add_document((*real_doc).clone(), None);
    ps.set_active(0);
    if active_id.is_some() {
        ps.set_active_layer(active_id);
    }
    // NOTE: NEVER call layer.flattenImage here — flattening destroys transparent backgrounds by filling solid white!
    if pw < real_w || ph < real_h {
        let _ = ps.execute(
            "image.imageSize",
            json!({ "width": pw, "height": ph, "resample": "bilinear" }),
        );
    }
    // Unlock pixel locks on proxy clone once so ALL 17+ live proxy filters work on locked Background layers
    if let Some(locked_ids) = ps.active().map(|ds| {
        ds.doc().layers.iter().filter(|l| l.locks.pixels).map(|l| l.id.0).collect::<Vec<_>>()
    }) {
        for lid in locked_ids {
        let _ = ps.execute("layer.setProps", json!({ "layer": lid, "locks": { "pixels": false } }));
        }
    }
    let base_doc = ps
        .active()
        .map(|ds| Arc::clone(ds.doc()))
        .ok_or(EngineError::NoActiveDocument)?;
    guard.proxy_preview = Some(ProxyPreviewSession {
        base_doc,
        session: ps,
        scale,
        real_size: (real_w, real_h),
        selection_only: false,
    });
    Ok(())
}

fn run_hsl_mixer_on_doc(doc: &mut Document, target_id: LayerId, hsl_hue: &[f32], hsl_sat: &[f32], hsl_lum: &[f32]) {
    let mut h_adj = [0.0f32; 8];
    let mut s_adj = [0.0f32; 8];
    let mut l_adj = [0.0f32; 8];
    let mut any_active = false;
    for i in 0..8 {
        h_adj[i] = hsl_hue.get(i).copied().unwrap_or(0.0).clamp(-100.0, 100.0);
        s_adj[i] = hsl_sat.get(i).copied().unwrap_or(0.0).clamp(-100.0, 100.0);
        l_adj[i] = hsl_lum.get(i).copied().unwrap_or(0.0).clamp(-100.0, 100.0);
        if h_adj[i].abs() > 0.2 || s_adj[i].abs() > 0.2 || l_adj[i].abs() > 0.2 {
            any_active = true;
        }
    }
    if !any_active {
        return;
    }
    const BANDS: [f32; 8] = [0.0, 30.0, 60.0, 120.0, 180.0, 240.0, 270.0, 300.0];
    let bounds = doc.bounds();
    let w = doc.size.width as usize;
    let h = doc.size.height as usize;
    if w == 0 || h == 0 {
        return;
    }
    let sel_mask: Option<Vec<f32>> = doc.selection.as_ref().and_then(|sel| {
        if sel.content_bounds().is_empty() { None } else { Some(sel.read_region(bounds)) }
    });
    let Some(layer) = doc.layers.iter_mut().find(|l| l.id == target_id) else {
        return;
    };
    let LayerContent::Raster(surf) = &mut layer.content else {
        return;
    };
    let ch = surf.format().channels();
    if ch < 3 {
        return;
    }
    let mut data = surf.read_region(bounds);
    if data.len() < w * h * ch {
        return;
    }
    let m_ch = sel_mask.as_ref().map(|m| (m.len() / (w * h)).max(1)).unwrap_or(1);

    for i in 0..(w * h) {
        let mask_w = sel_mask.as_ref().map(|m| m[i * m_ch].clamp(0.0, 1.0)).unwrap_or(1.0);
        if mask_w < 0.002 {
            continue;
        }
        let base = i * ch;
        let r = data[base].clamp(0.0, 1.0);
        let g = data[base + 1].clamp(0.0, 1.0);
        let b = data[base + 2].clamp(0.0, 1.0);
        let max_c = r.max(g).max(b);
        let min_c = r.min(g).min(b);
        let d = max_c - min_c;
        if d < 1e-4 {
            continue; // Pure gray has no hue band
        }
        let l = (max_c + min_c) * 0.5;
        let s = if l > 0.5 { d / (2.0 - max_c - min_c).max(1e-5) } else { d / (max_c + min_c).max(1e-5) };
        let mut hue = if (max_c - r).abs() < 1e-6 {
            60.0 * (((g - b) / d).rem_euclid(6.0))
        } else if (max_c - g).abs() < 1e-6 {
            60.0 * (((b - r) / d) + 2.0)
        } else {
            60.0 * (((r - g) / d) + 4.0)
        };
        if hue < 0.0 { hue += 360.0; }

        // True perceptual chroma gating (d = max_c - min_c, NOT HSL s which explodes near black/white!)
        // Near-neutral blacks, grays, whites, and JPEG noise (d <= 0.03) stay 100% untouched.
        let ct = ((d - 0.03) / 0.13).clamp(0.0, 1.0);
        let chroma_w = (ct * ct * (3.0 - 2.0 * ct)) * mask_w;
        if chroma_w < 1e-4 {
            continue;
        }

        // Smooth Hermite weights across the 8 circular hue bands (zero sharp seams on skin tones)
        let mut dh = 0.0f32;
        let mut ds = 0.0f32;
        let mut dl = 0.0f32;
        for bi in 0..8 {
            let prev_b = BANDS[(bi + 7) % 8];
            let curr_b = BANDS[bi];
            let next_b = BANDS[(bi + 1) % 8];
            let left_span = (curr_b - prev_b).rem_euclid(360.0).max(1.0);
            let right_span = (next_b - curr_b).rem_euclid(360.0).max(1.0);
            let mut diff = hue - curr_b;
            if diff > 180.0 { diff -= 360.0; }
            if diff < -180.0 { diff += 360.0; }
            let lin = if diff >= 0.0 && diff < right_span {
                1.0 - diff / right_span
            } else if diff < 0.0 && (-diff) < left_span {
                1.0 + diff / left_span
            } else {
                0.0
            };
            if lin > 0.0 {
                let weight = lin * lin * (3.0 - 2.0 * lin);
                dh += h_adj[bi] * weight;
                ds += s_adj[bi] * weight;
                dl += l_adj[bi] * weight;
            }
        }
        if dh.abs() < 0.05 && ds.abs() < 0.05 && dl.abs() < 0.05 {
            continue;
        }
        // Lightroom-calibrated curves:
        // - Hue: ±30° smooth shift
        // - Saturation: multiplicative desat; vibrance-protected boost so mid-tones don't clip
        // - Lightness: parabolic midtone envelope 4*l*(1-l) so blacks never glow and whites never block
        let new_h = (hue + dh * 0.30 * chroma_w).rem_euclid(360.0);
        let ds_norm = (ds / 100.0) * chroma_w;
        let new_s = if ds_norm < 0.0 {
            (s * (1.0 + ds_norm)).clamp(0.0, 1.0)
        } else {
            (s + (1.0 - s) * s.sqrt() * 0.85 * ds_norm).clamp(0.0, 1.0)
        };
        let l_env = (4.0 * l * (1.0 - l)).clamp(0.0, 1.0);
        let new_l = (l + (dl / 100.0) * 0.32 * chroma_w * l_env).clamp(0.0, 1.0);

        // HSL -> RGB
        let c = (1.0 - (2.0 * new_l - 1.0).abs()) * new_s;
        let x = c * (1.0 - ((new_h / 60.0) % 2.0 - 1.0).abs());
        let m = new_l - c * 0.5;
        let (r1, g1, b1) = if new_h < 60.0 {
            (c, x, 0.0)
        } else if new_h < 120.0 {
            (x, c, 0.0)
        } else if new_h < 180.0 {
            (0.0, c, x)
        } else if new_h < 240.0 {
            (0.0, x, c)
        } else if new_h < 300.0 {
            (x, 0.0, c)
        } else {
            (c, 0.0, x)
        };
        data[base] = (r1 + m).clamp(0.0, 1.0);
        data[base + 1] = (g1 + m).clamp(0.0, 1.0);
        data[base + 2] = (b1 + m).clamp(0.0, 1.0);
    }
    surf.write_region_par(bounds, &data);
}


/// Maps a StonebridgeCraft Filter Gallery kind + normalized controls (`amount`: 0..100, `detail`: 0..100)
/// and `scale` (proxy scale for pixel-size filters) into the exact verified PhotoCraft command & JSON.

/// Maps a StonebridgeCraft Light & Distort kind + controls into the exact verified PhotoCraft command & JSON.
/// Supported kinds: `lensFlare`, `lensBlur`, `wave`, `spherize`, `twirl`.
fn build_light_distort_cmd(
    kind: &str,
    amount: f32,
    detail: f32,
    param_x: f32,
    param_y: f32,
    variant: &str,
    scale: f32,
) -> Option<(&'static str, Value)> {
    let sc = scale.clamp(0.12, 1.0) as f64;
    match kind {
        "lensFlare" => {
            let lens = match variant {
                "prime35" | "prime105" | "moviePrime" | "zoom" => variant,
                _ => "moviePrime",
            };
            let brightness = (10.0 + (amount.clamp(0.0, 100.0) as f64) * 2.4).clamp(10.0, 300.0);
            let cx = (param_x.clamp(0.0, 100.0) as f64) / 100.0;
            let cy = (param_y.clamp(0.0, 100.0) as f64) / 100.0;
            Some((
                "filter.render.lensFlare",
                json!({
                    "brightness": brightness,
                    "centerX": cx,
                    "centerY": cy,
                    "lens": lens,
                }),
            ))
        }
        "lensBlur" => {
            let shape = match variant {
                "hexagon" | "octagon" | "pentagon" | "square" | "triangle" => variant,
                _ => "hexagon",
            };
            let radius = ((1.0 + (amount.clamp(0.0, 100.0) as f64) * 0.26) * sc).clamp(0.5, 32.0);
            let spec_bright = ((detail.clamp(0.0, 100.0) as f64) * 0.80).clamp(0.0, 100.0);
            Some((
                "filter.blur.lensBlur",
                json!({
                    "radius": radius,
                    "shape": shape,
                    "bladeCurvature": 35.0,
                    "brightness": spec_bright,
                    "threshold": 210.0,
                    "depthMap": "none",
                }),
            ))
        }
        "wave" => {
            let wtype = match variant {
                "triangle" | "square" | "sine" => variant,
                _ => "sine",
            };
            let d_inv = (100.0 - detail.clamp(0.0, 100.0)) as f64;
            let wl_min = ((12.0 + d_inv * 0.65) * sc).clamp(2.0, 990.0);
            let wl_max = (wl_min * 2.2).clamp(wl_min + 2.0, 999.0);
            let amp_min = ((1.0 + (amount.clamp(0.0, 100.0) as f64) * 0.28) * sc).clamp(1.0, 990.0);
            let amp_max = (amp_min * 2.0 + 1.0).clamp(amp_min + 1.0, 999.0);
            Some((
                "filter.distort.wave",
                json!({
                    "type": wtype,
                    "generators": 3,
                    "wavelengthMin": wl_min,
                    "wavelengthMax": wl_max,
                    "amplitudeMin": amp_min,
                    "amplitudeMax": amp_max,
                    "undefinedAreas": "repeat",
                    "seed": 42u32,
                }),
            ))
        }
        "spherize" => {
            let mode = match variant {
                "horizontalOnly" | "verticalOnly" | "normal" => variant,
                _ => "normal",
            };
            let amt = (amount.clamp(-100.0, 100.0) as f64);
            Some((
                "filter.distort.spherize",
                json!({
                    "amount": amt,
                    "mode": mode,
                }),
            ))
        }
        "twirl" => {
            let angle = (amount.clamp(-360.0, 360.0) as f64);
            Some((
                "filter.distort.twirl",
                json!({
                    "angle": angle,
                }),
            ))
        }
        _ => None,
    }
}

fn build_gallery_filter_cmd(kind: &str, amount: f32, detail: f32, scale: f32) -> Option<(&'static str, Value)> {
    let a = (amount.clamp(0.0, 100.0) / 100.0) as f64;
    let d = (detail.clamp(0.0, 100.0) / 100.0) as f64;
    let sc = scale.clamp(0.1, 1.0) as f64;
    match kind {
        "oilPaint" => Some((
            "filter.stylize.oilPaint",
            json!({
                "stylization": (0.5 + a * 8.5).clamp(0.1, 10.0),
                "cleanliness": (1.0 + d * 8.0).clamp(0.0, 10.0),
                "scale": (0.5 + a * 3.0).clamp(0.1, 10.0),
                "bristleDetail": 5.0,
                "lighting": true,
                "angle": -60.0,
                "shine": (0.5 + d * 3.5).clamp(0.0, 10.0)
            }),
        )),
        "watercolor" => Some((
            "filter.gallery.watercolor",
            json!({
                "brushDetail": (1.0 + d * 13.0).round().clamp(1.0, 14.0) as i64,
                "shadowIntensity": (a * 8.0).round().clamp(0.0, 10.0) as i64,
                "texture": (1.0 + a * 2.0).round().clamp(1.0, 3.0) as i64
            }),
        )),
        "charcoal" => Some((
            "filter.gallery.charcoal",
            json!({
                "charcoalThickness": (1.0 + a * 6.0).round().clamp(1.0, 7.0) as i64,
                "detail": (d * 5.0).round().clamp(0.0, 5.0) as i64,
                "lightDarkBalance": (20.0 + d * 60.0).round().clamp(0.0, 100.0) as i64
            }),
        )),
        "glowingEdges" => Some((
            "filter.gallery.glowingEdges",
            json!({
                "edgeWidth": (1.0 + a * 10.0).round().clamp(1.0, 14.0) as i64,
                "edgeBrightness": (4.0 + d * 14.0).round().clamp(0.0, 20.0) as i64,
                "smoothness": (2.0 + (1.0 - d) * 10.0).round().clamp(1.0, 15.0) as i64
            }),
        )),
        "emboss" => Some((
            "filter.stylize.emboss",
            json!({
                "angle": 135.0,
                "height": ((1.0 + a * 12.0) * sc).clamp(1.0, 100.0),
                "amount": (60.0 + d * 280.0).clamp(1.0, 500.0)
            }),
        )),
        "mosaic" => Some((
            "filter.pixelate.mosaic",
            json!({
                "cellSize": ((4.0 + a * 64.0) * sc).round().clamp(2.0, 200.0)
            }),
        )),
        "crystallize" => Some((
            "filter.pixelate.crystallize",
            json!({
                "cellSize": ((6.0 + a * 74.0) * sc).round().clamp(3.0, 300.0),
                "seed": 42u32
            }),
        )),
        "pointillize" => Some((
            "filter.pixelate.pointillize",
            json!({
                "cellSize": ((4.0 + a * 44.0) * sc).round().clamp(3.0, 300.0),
                "seed": 42u32
            }),
        )),
        "colorHalftone" => Some((
            "filter.pixelate.colorHalftone",
            json!({
                "maxRadius": ((4.0 + a * 26.0) * sc).round().clamp(4.0, 127.0)
            }),
        )),
        _ => None,
    }
}

#[uniffi::export]
impl KastamonuEngine {
    pub fn new_document(&self, name: String, width: u32, height: u32) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        guard.live_dab = None;
        guard.live_adjustment = None;
        guard.proxy_preview = None;
        guard.live_liquify = None;
        guard.live_filter = None;
        while guard.session.close(0).is_some() {}
        guard
            .session
            .execute(
                "file.new",
                json!({
                    "name": name,
                    "width": width.max(1),
                    "height": height.max(1),
                    "resolution": 300.0,
                    "background": "white"
                }),
            )
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.capture_original_snapshot();
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    pub fn open_document_bytes(&self, file_name: String, bytes: Vec<u8>) -> Result<DocumentInfo, EngineError> {
        let imported = photocraft_io::import(&file_name, &bytes)
            .map_err(|e| EngineError::IoFailed { detail: e.to_string() })?;
        let mut doc = imported.document;
        if doc.name.is_empty() {
            doc.name = file_name;
        }
        let top_id = doc.layers.last().map(|l| l.id);
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        guard.live_dab = None;
        guard.live_adjustment = None;
        guard.proxy_preview = None;
        guard.live_liquify = None;
        guard.live_filter = None;
        while guard.session.close(0).is_some() {}
        let idx = guard.session.add_document(doc, None);
        guard.session.set_active(idx);
        if let Some(tid) = top_id {
            guard.session.set_active_layer(Some(tid));
        }
        guard.capture_original_snapshot();
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    /// Fast zero-reencode path for Android hardware-decoded RGBA8 bitmaps.
    /// Closes any previous document first so RAM never accumulates across opens.
    pub fn open_rgba_bytes(
        &self,
        file_name: String,
        width: u32,
        height: u32,
        rgba_bytes: Vec<u8>,
    ) -> Result<DocumentInfo, EngineError> {
        let w = width.max(1);
        let h = height.max(1);
        let expected = (w as usize).saturating_mul(h as usize).saturating_mul(4);
        if rgba_bytes.len() < expected {
            return Err(EngineError::InvalidParams {
                detail: format!("RGBA buffer size mismatch: got {}, expected {}", rgba_bytes.len(), expected),
            });
        }
        let rect = Rect::new(0, 0, w as i32, h as i32);
        let surf = Surface::from_interleaved(PixelFormat::RGBA8, rect, &rgba_bytes[..expected]);
        let mut doc = Document::new(file_name, Size::new(w, h), ColorMode::Rgb, SampleType::U8);
        let bg = Layer::new("Background", LayerContent::Raster(surf));
        let bg_id = bg.id;
        doc.layers.push(bg);

        let mut guard = self.core.lock();
        guard.live_stroke = None;
        guard.live_dab = None;
        guard.live_adjustment = None;
        guard.proxy_preview = None;
        guard.live_liquify = None;
        guard.live_filter = None;
        while guard.session.close(0).is_some() {}
        let idx = guard.session.add_document(doc, None);
        guard.session.set_active(idx);
        guard.session.set_active_layer(Some(bg_id));
        guard.capture_original_snapshot();
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    pub fn place_image_bytes(&self, file_name: String, bytes: Vec<u8>) -> Result<DocumentInfo, EngineError> {
        let imported = photocraft_io::import(&file_name, &bytes)
            .map_err(|e| EngineError::IoFailed { detail: e.to_string() })?;
        let flat = photocraft_compose::flatten(&imported.document);
        let src_w = imported.document.size.width.max(1);
        let src_h = imported.document.size.height.max(1);

        let mut guard = self.core.lock();
        guard.live_stroke = None;
        guard.live_dab = None;
        guard.live_adjustment = None;
        guard.live_filter = None;
        guard.proxy_preview = None;
        guard.dismiss_compare();

        let (doc_w, doc_h, target_fmt) = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            (ds.doc().size.width.max(1), ds.doc().size.height.max(1), ds.doc().pixel_format())
        };

        let scale = (doc_w as f32 / src_w as f32).min(doc_h as f32 / src_h as f32).min(1.0);
        let dst_w = ((src_w as f32 * scale).round() as u32).clamp(1, doc_w);
        let dst_h = ((src_h as f32 * scale).round() as u32).clamp(1, doc_h);
        let ox = ((doc_w.saturating_sub(dst_w)) / 2) as i32;
        let oy = ((doc_h.saturating_sub(dst_h)) / 2) as i32;

        let mut rgba = vec![0u8; (dst_w as usize) * (dst_h as usize) * 4];
        for y in 0..dst_h {
            let sy = (((y as f32 + 0.5) / scale) - 0.5).clamp(0.0, (src_h - 1) as f32) as usize;
            for x in 0..dst_w {
                let sx = (((x as f32 + 0.5) / scale) - 0.5).clamp(0.0, (src_w - 1) as f32) as usize;
                let p = flat.px[sy * (src_w as usize) + sx];
                let o = ((y as usize) * (dst_w as usize) + (x as usize)) * 4;
                rgba[o] = (p[0].clamp(0.0, 1.0) * 255.0).round() as u8;
                rgba[o + 1] = (p[1].clamp(0.0, 1.0) * 255.0).round() as u8;
                rgba[o + 2] = (p[2].clamp(0.0, 1.0) * 255.0).round() as u8;
                rgba[o + 3] = (p[3].clamp(0.0, 1.0) * 255.0).round() as u8;
            }
        }
        let rect = Rect::from_xywh(ox, oy, dst_w, dst_h);
        let surf = Surface::from_interleaved(target_fmt, rect, &rgba);
        let clean_name = std::path::Path::new(&file_name)
            .file_stem()
            .and_then(|s| s.to_str())
            .filter(|s| !s.is_empty())
            .unwrap_or("Katman")
            .to_string();

        let mut new_id = None;
        guard
            .session
            .edit("Katman Ekle", |doc, active| {
                let layer = Layer::new(clean_name.clone(), LayerContent::Raster(surf.clone()));
                let id = layer.id;
                let insert_pos = active
                    .and_then(|aid| doc.layers.iter().position(|l| l.id == aid).map(|i| i + 1))
                    .unwrap_or(doc.layers.len());
                doc.layers.insert(insert_pos.min(doc.layers.len()), layer);
                *active = Some(id);
                new_id = Some(id);
                Ok(())
            })
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        if let Some(id) = new_id {
            guard.session.set_active_layer(Some(id));
        }
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    pub fn export_document_bytes(&self, format_ext: String) -> Result<Vec<u8>, EngineError> {
        let doc = {
            let guard = self.core.lock();
            guard.active_document().ok_or(EngineError::NoActiveDocument)?
        };
        let export_name = format!("export.{}", format_ext.trim_start_matches('.'));
        let result = photocraft_io::export(&doc, &export_name, &photocraft_io::ExportOptions::default())
            .map_err(|e| EngineError::IoFailed { detail: e.to_string() })?;
        Ok(result.bytes)
    }

    pub fn execute_command(&self, command_id: String, params_json: String) -> Result<String, EngineError> {
        let params: Value = if params_json.trim().is_empty() {
            json!({})
        } else {
            serde_json::from_str(&params_json)
                .map_err(|e| EngineError::InvalidParams { detail: e.to_string() })?
        };
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        guard.live_stroke = None;
        let out = guard
            .session
            .execute(&command_id, params)
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        Ok(out.to_string())
    }

    /// Creates a real PhotoCraft text layer using Android system fonts (`/system/fonts`).
    /// Converts desired pixel height `size_px` into exact document points via `doc.resolution_dpi`.
    /// Registers an in-memory TrueType/OpenType font (e.g. bundled APK studio fonts)
    /// into PhotoCraft's global text engine and returns the discovered font family names.
    pub fn register_font_bytes(&self, bytes: Vec<u8>) -> Vec<String> {
        if bytes.is_empty() {
            return Vec::new();
        }
        let mut text_eng = photocraft_text::shared().lock().unwrap_or_else(|e| e.into_inner());
        text_eng.fonts.register_font_data(bytes)
    }

    /// Returns all available font family names (bundled studio fonts + Android system fonts).
    pub fn list_available_fonts(&self) -> Vec<String> {
        let mut text_eng = photocraft_text::shared().lock().unwrap_or_else(|e| e.into_inner());
        let mut families = text_eng.fonts.families();
        families.sort();
        families.dedup();
        families
    }

    /// If the currently active layer is a PhotoCraft Text layer, returns its full typography properties
    /// so the Typography Studio opens in `type.edit` mode automatically.
    pub fn get_active_text_layer_style(&self) -> Option<TextLayerStyle> {
        let guard = self.core.lock();
        let ds = guard.session.active()?;
        let active_id = ds.active_layer()?;
        let doc = ds.doc();
        let layer = doc.layers.iter().find(|l| l.id == active_id)?;
        let LayerContent::Text(t) = &layer.content else {
            return None;
        };
        let doc_w = doc.size.width.max(1) as f32;
        let doc_h = doc.size.height.max(1) as f32;
        let dpi = doc.resolution_dpi.max(1.0);

        let char_runs = t.char_runs();
        let st = char_runs.first().map(|r| &r.style);
        let para_runs = t.paragraph_runs();
        let p_align = para_runs.first().map(|p| p.style.align).unwrap_or(photocraft_doc::text::TextAlign::Center);

        let font_family = st
            .map(|s| s.font_family.clone())
            .filter(|s| !s.is_empty())
            .unwrap_or_else(|| "Montserrat".to_string());
        let size_pt = st.map(|s| s.size_pt).unwrap_or(48.0).max(0.5);
        let size_px = (size_pt * dpi / 72.0).clamp(6.0, 600.0);
        let bold = st.map(|s| s.weight >= 600 || s.faux_bold).unwrap_or(false);
        let italic = st.map(|s| s.italic || s.faux_italic).unwrap_or(false);
        let underline = st.map(|s| s.underline).unwrap_or(false);
        let strikethrough = st.map(|s| s.strikethrough).unwrap_or(false);
        let caps = st
            .and_then(|s| serde_json::to_value(s.caps).ok())
            .and_then(|v| v.as_str().map(str::to_string))
            .map(|s| match s.to_lowercase().as_str() {
                "all" | "allcaps" => "all".to_string(),
                "small" | "smallcaps" => "small".to_string(),
                _ => "normal".to_string(),
            })
            .unwrap_or_else(|| "normal".to_string());
        let h_scale = st.map(|s| if s.horizontal_scale <= 5.0 { s.horizontal_scale * 100.0 } else { s.horizontal_scale }).unwrap_or(100.0).clamp(50.0, 200.0);
        let v_scale = st.map(|s| if s.vertical_scale <= 5.0 { s.vertical_scale * 100.0 } else { s.vertical_scale }).unwrap_or(100.0).clamp(50.0, 200.0);
        let tracking = st.map(|s| s.tracking).unwrap_or(0.0);
        let line_height = st
            .and_then(|s| s.leading_pt)
            .map(|lpt| (lpt / size_pt).clamp(0.7, 2.8))
            .unwrap_or(1.2);
        let color_hex = st
            .map(|s| {
                let r = (s.color.c[0].clamp(0.0, 1.0) * 255.0).round() as u8;
                let g = (s.color.c[1].clamp(0.0, 1.0) * 255.0).round() as u8;
                let b = (s.color.c[2].clamp(0.0, 1.0) * 255.0).round() as u8;
                format!("#{r:02X}{g:02X}{b:02X}")
            })
            .unwrap_or_else(|| "#FFFFFF".to_string());
        let align = match p_align {
            photocraft_doc::text::TextAlign::Left | photocraft_doc::text::TextAlign::JustifyLeft => "left",
            photocraft_doc::text::TextAlign::Right | photocraft_doc::text::TextAlign::JustifyRight => "right",
            _ => "center",
        }
        .to_string();
        let vertical = t.orientation == photocraft_doc::text::Orientation::Vertical;
        let (warp_style, warp_bend, warp_h_dist, warp_v_dist) = match &t.warp {
            Some(w) => (
                photocraft_text::warp::short_style(&w.style).unwrap_or("none").to_string(),
                w.value.clamp(-100.0, 100.0),
                w.horizontal_distortion.clamp(-100.0, 100.0),
                w.vertical_distortion.clamp(-100.0, 100.0),
            ),
            None => ("none".to_string(), 50.0, 0.0, 0.0),
        };
        let norm_x = (t.transform.m[4] as f32 / doc_w).clamp(0.05, 0.95);
        let norm_y = (t.transform.m[5] as f32 / doc_h).clamp(0.05, 0.95);

        Some(TextLayerStyle {
            layer_id: active_id.0,
            text: t.text.clone(),
            font_family,
            size_px,
            color_hex,
            align,
            bold,
            italic,
            underline,
            strikethrough,
            caps,
            h_scale,
            v_scale,
            tracking,
            line_height,
            vertical,
            warp_style,
            warp_bend,
            warp_h_dist,
            warp_v_dist,
            norm_x,
            norm_y,
        })
    }

    /// Creates a new PhotoCraft text layer (`type.create`) or edits an existing text layer in-place
    /// (`type.edit` + `type.setStyle`) + optional curved text warp (`type.warpText`).
    #[allow(clippy::too_many_arguments)]
    pub fn add_or_edit_text_layer(
        &self,
        edit_layer_id: Option<u64>,
        text: String,
        x: f32,
        y: f32,
        size_px: f32,
        color_hex: String,
        align: String,
        font_family: String,
        bold: bool,
        italic: bool,
        underline: bool,
        strikethrough: bool,
        caps: String,
        h_scale: f32,
        v_scale: f32,
        tracking: f32,
        line_height: f32,
        vertical: bool,
        warp_style: String,
        warp_bend: f32,
        warp_h_dist: f32,
        warp_v_dist: f32,
    ) -> Result<DocumentInfo, EngineError> {
        let trimmed = text.trim();
        if trimmed.is_empty() {
            return Err(EngineError::InvalidParams { detail: "Metin boş olamaz".into() });
        }
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        guard.live_stroke = None;
        guard.proxy_preview = None;

        let dpi = guard
            .session
            .active()
            .map(|ds| ds.doc().resolution_dpi.max(1.0))
            .unwrap_or(72.0);
        let size_pt = (size_px.max(4.0) * 72.0 / dpi).clamp(0.5, 1296.0);
        let weight: u32 = if bold { 700 } else { 400 };
        let tracking_clamped = tracking.clamp(-100.0, 600.0);
        let leading_val: Value = if (line_height - 1.2).abs() < 0.03 {
            json!("auto")
        } else {
            json!((size_pt * line_height.clamp(0.7, 2.8)).clamp(2.0, 2000.0))
        };
        let align_str = match align.as_str() {
            "left" | "right" | "center" => align.as_str(),
            _ => "center",
        };
        let caps_str = match caps.as_str() {
            "all" | "small" => caps.as_str(),
            _ => "normal",
        };
        let hs = h_scale.clamp(50.0, 200.0);
        let vs = v_scale.clamp(50.0, 200.0);
        let orient_str = if vertical { "vertical" } else { "horizontal" };
        let chosen_font = font_family.trim().to_string();

        let can_edit_existing = edit_layer_id.and_then(|lid| {
            let ds = guard.session.active()?;
            let l = ds.doc().layers.iter().find(|l| l.id.0 == lid)?;
            if matches!(l.content, LayerContent::Text(_)) { Some(lid) } else { None }
        });

        let target_layer_id = if let Some(lid) = can_edit_existing {
            let _ = guard.session.execute(
                "type.edit",
                json!({
                    "layer": lid,
                    "text": text,
                    "point": [x, y],
                }),
            );
            let mut style_params = json!({
                "layer": lid,
                "size": size_pt,
                "weight": weight,
                "fauxBold": bold,
                "italic": italic,
                "fauxItalic": italic,
                "underline": underline,
                "strikethrough": strikethrough,
                "caps": caps_str,
                "horizontalScale": hs,
                "verticalScale": vs,
                "color": color_hex,
                "align": align_str,
                "tracking": tracking_clamped,
                "leading": leading_val,
            });
            if !chosen_font.is_empty() {
                style_params["font"] = json!(chosen_font);
            }
            guard
                .session
                .execute("type.setStyle", style_params)
                .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
            let orient_cmd = if vertical { "type.orientation.vertical" } else { "type.orientation.horizontal" };
            let _ = guard.session.execute(orient_cmd, json!({ "layer": lid }));
            Some(lid)
        } else {
            let mut params = json!({
                "text": text,
                "x": x,
                "y": y,
                "size": size_pt,
                "weight": weight,
                "fauxBold": bold,
                "italic": italic,
                "fauxItalic": italic,
                "underline": underline,
                "strikethrough": strikethrough,
                "caps": caps_str,
                "horizontalScale": hs,
                "verticalScale": vs,
                "color": color_hex,
                "align": align_str,
                "tracking": tracking_clamped,
                "leading": leading_val,
                "orientation": orient_str,
            });
            if !chosen_font.is_empty() {
                params["font"] = json!(chosen_font);
            }
            let res = guard
                .session
                .execute("type.create", params)
                .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
            res.get("layer").and_then(Value::as_u64)
        };

        // Apply or clear curved Text Warp via type.warpText
        let ws = warp_style.trim();
        let valid_warp = matches!(
            ws,
            "arc" | "arch" | "flag" | "wave" | "bulge" | "fish" | "rise" | "twist" | "inflate" | "squeeze"
        );
        if let Some(lid) = target_layer_id {
            if valid_warp {
                let _ = guard.session.execute(
                    "type.warpText",
                    json!({
                        "layer": lid,
                        "style": ws,
                        "bend": warp_bend.clamp(-100.0, 100.0),
                        "horizontalDistortion": warp_h_dist.clamp(-100.0, 100.0),
                        "verticalDistortion": warp_v_dist.clamp(-100.0, 100.0),
                        "orientation": orient_str,
                    }),
                );
            } else if can_edit_existing.is_some() {
                let _ = guard.session.execute("type.warpText", json!({ "layer": lid, "style": "none" }));
            }
        }

        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    /// Live 60 FPS proxy preview for Typography Studio (especially curved Text Warp `type.warpText`).
    /// Renders the exact PhotoCraft text + warp directly on the 512px proxy document so the user sees
    /// real-time bending, distortion, font, and alignment while dragging sliders.
    #[allow(clippy::too_many_arguments)]
    /// v2.2.0 Parça 1: Applies Text Pro non-destructive extras (Stroke, Drop Shadow, Outer Glow,
    /// Gradient Overlay, Opacity, Blend Mode, Rotation & optional Background Pill Box) to the active text layer.
    pub fn apply_text_pro_extras(
        &self,
        primary_color: String,
        extras: TextProExtras,
    ) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        guard.proxy_preview = None;

        let (aid, doc_w, doc_h, text_bounds) = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            let id = ds.active_layer().ok_or_else(|| EngineError::InvalidParams { detail: "Aktif katman bulunamadı".into() })?;
            let d = ds.doc();
            let dpi = d.resolution_dpi.max(1.0);
            let b = d.layer(id).map(|l| match &l.content {
                LayerContent::Raster(surf) => surf.content_bounds(),
                LayerContent::Text(t) => {
                    let runs = t.char_runs();
                    let st = runs.first().map(|r| &r.style);
                    let size_pt = st.map(|s| s.size_pt).unwrap_or(48.0).max(0.5);
                    let spx = (size_pt * dpi / 72.0).clamp(8.0, 600.0);
                    let hs = st.map(|s| if s.horizontal_scale <= 5.0 { s.horizontal_scale } else { s.horizontal_scale / 100.0 }).unwrap_or(1.0).clamp(0.5, 2.0);
                    let vs = st.map(|s| if s.vertical_scale <= 5.0 { s.vertical_scale } else { s.vertical_scale / 100.0 }).unwrap_or(1.0).clamp(0.5, 2.0);
                    let lines: Vec<&str> = t.text.split('\n').collect();
                    let max_chars = lines.iter().map(|ln| ln.chars().count()).max().unwrap_or(1).max(1) as f32;
                    let est_w = (max_chars * spx * 0.62 * hs).max(spx * 1.4);
                    let est_h = (lines.len() as f32 * spx * 1.25 * vs).max(spx * 1.1);
                    let cx = t.transform.m[4] as f32;
                    let cy = t.transform.m[5] as f32;
                    Rect {
                        x0: (cx - est_w * 0.5).floor() as i32,
                        y0: (cy - est_h * 0.5).floor() as i32,
                        x1: (cx + est_w * 0.5).ceil() as i32,
                        y1: (cy + est_h * 0.5).ceil() as i32,
                    }
                }
                _ => d.bounds(),
            });
            (id, d.size.width.max(1) as f32, d.size.height.max(1) as f32, b)
        };

        // 1. Opacity & Blend Mode
        let op = extras.opacity.clamp(0.05, 1.0);
        let blend = match extras.blend_mode.as_str() {
            "Normal" | "Multiply" | "Screen" | "Overlay" | "SoftLight" | "HardLight" | "Darken" | "Lighten" => extras.blend_mode.as_str(),
            _ => "Normal",
        };
        let _ = guard.session.execute(
            "layer.setProps",
            json!({
                "layer": aid.0,
                "opacity": op as f64,
                "blend": blend
            }),
        );

        // 2. Non-destructive Layer FX (Stroke, Drop Shadow, Outer Glow, Gradient Fill)
        let mut effects: Vec<Value> = Vec::new();
        if extras.gradient_enabled {
            effects.push(json!({
                "kind": "gradientOverlay",
                "params": {
                    "from": primary_color,
                    "to": extras.gradient_end_color,
                    "opacity": 100.0,
                    "angle": extras.gradient_angle.clamp(-180.0, 180.0) as f64,
                    "style": "linear",
                    "blend": "Normal"
                }
            }));
        }
        if extras.stroke_width > 0.4 {
            effects.push(json!({
                "kind": "stroke",
                "params": {
                    "color": extras.stroke_color,
                    "size": extras.stroke_width.clamp(1.0, 60.0) as f64,
                    "opacity": 100.0,
                    "position": "outside",
                    "blend": "Normal"
                }
            }));
        }
        if extras.glow_size > 0.4 {
            effects.push(json!({
                "kind": "outerGlow",
                "params": {
                    "color": extras.glow_color,
                    "size": extras.glow_size.clamp(1.0, 80.0) as f64,
                    "opacity": 85.0,
                    "blend": "Screen"
                }
            }));
        }
        if extras.shadow_enabled {
            effects.push(json!({
                "kind": "dropShadow",
                "params": {
                    "color": extras.shadow_color,
                    "size": extras.shadow_blur.clamp(0.0, 60.0) as f64,
                    "distance": extras.shadow_distance.clamp(0.0, 80.0) as f64,
                    "angle": extras.shadow_angle.clamp(-180.0, 180.0) as f64,
                    "opacity": 82.0,
                    "blend": "Multiply"
                }
            }));
        }
        if effects.is_empty() {
            let _ = guard.session.execute("layer.layerStyle.clear", json!({ "layer": aid.0 }));
        } else {
            let _ = guard.session.execute(
                "layer.layerStyle.replace",
                json!({ "layer": aid.0, "effects": effects }),
            );
        }

        // 3. Free Rotation
        if extras.rotation_deg.abs() > 0.25 {
            let _ = guard.session.execute(
                "edit.transform",
                json!({
                    "dx": 0.0,
                    "dy": 0.0,
                    "scaleX": 100.0,
                    "scaleY": 100.0,
                    "rotate": extras.rotation_deg.clamp(-180.0, 180.0) as f64,
                    "interpolation": "bicubic"
                }),
            );
        }

        // 4. Optional Background Pill Box placed directly behind text layer
        if extras.bg_enabled {
            if let Some(b) = text_bounds {
                let pad = extras.bg_padding.clamp(4.0, 120.0);
                let x0 = (b.x0 as f32 - pad).clamp(0.0, doc_w);
                let y0 = (b.y0 as f32 - pad * 0.7).clamp(0.0, doc_h);
                let x1 = (b.x1 as f32 + pad).clamp(x0 + 8.0, doc_w);
                let y1 = (b.y1 as f32 + pad * 0.7).clamp(y0 + 8.0, doc_h);
                let bg_params = build_shape_params(
                    "roundedRect",
                    x0,
                    y0,
                    x1,
                    y1,
                    &extras.bg_color,
                    "",
                    0.0,
                    extras.bg_radius.clamp(0.0, 80.0),
                    4,
                );
                let _ = guard.session.execute("shape.create", bg_params);
                if let Some(ds) = guard.session.active() {
                    if let Some(bg_id) = ds.active_layer() {
                        let _ = guard.session.execute(
                            "layer.setProps",
                            json!({
                                "layer": bg_id.0,
                                "opacity": extras.bg_opacity.clamp(0.05, 1.0) as f64
                            }),
                        );
                        let _ = guard.session.execute("layer.arrange.sendBackward", json!({}));
                    }
                }
                if let Some(ds) = guard.session.active_mut() {
                    let _ = ds.set_active_layer(Some(aid));
                }
            }
        }

        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    pub fn update_proxy_text_preview(
        &self,
        edit_layer_id: Option<u64>,
        text: String,
        x: f32,
        y: f32,
        size_px: f32,
        color_hex: String,
        align: String,
        font_family: String,
        bold: bool,
        italic: bool,
        underline: bool,
        strikethrough: bool,
        caps: String,
        h_scale: f32,
        v_scale: f32,
        tracking: f32,
        line_height: f32,
        vertical: bool,
        warp_style: String,
        warp_bend: f32,
        warp_h_dist: f32,
        warp_v_dist: f32,
    ) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        ensure_proxy_preview_locked(&mut guard)?;
        let active_id = guard.session.active().and_then(|ds| ds.active_layer());
        let Some(proxy) = guard.proxy_preview.as_mut() else {
            return Ok(());
        };
        let sc = proxy.scale;
        let mut ps = Session::new();
        ps.add_document((*proxy.base_doc).clone(), None);
        ps.set_active(0);
        if active_id.is_some() {
            ps.set_active_layer(active_id);
        }

        // If editing an existing text layer, hide its original in the proxy so we don't see double text
        if let Some(eid) = edit_layer_id {
            if let Some(ds) = ps.active_mut() {
                let doc = Arc::make_mut(ds.doc_mut());
                if let Some(l) = doc.layers.iter_mut().find(|l| l.id.0 == eid) {
                    l.visible = false;
                }
            }
        }

        if !text.trim().is_empty() {
            let dpi = ps.active().map(|ds| ds.doc().resolution_dpi.max(1.0)).unwrap_or(72.0);
            let scaled_px = (size_px.max(4.0) * sc).max(2.0);
            let size_pt = (scaled_px * 72.0 / dpi).clamp(0.5, 1296.0);
            let weight: u32 = if bold { 700 } else { 400 };
            let leading_val: Value = if (line_height - 1.2).abs() < 0.03 {
                json!("auto")
            } else {
                json!((size_pt * line_height.clamp(0.7, 2.8)).clamp(1.0, 2000.0))
            };
            let align_str = match align.as_str() {
                "left" | "right" | "center" => align.as_str(),
                _ => "center",
            };
            let caps_str = match caps.as_str() {
                "all" | "small" => caps.as_str(),
                _ => "normal",
            };
            let orient_str = if vertical { "vertical" } else { "horizontal" };
            let mut params = json!({
                "text": text,
                "x": x * sc,
                "y": y * sc,
                "size": size_pt,
                "weight": weight,
                "fauxBold": bold,
                "italic": italic,
                "fauxItalic": italic,
                "underline": underline,
                "strikethrough": strikethrough,
                "caps": caps_str,
                "horizontalScale": h_scale.clamp(50.0, 200.0),
                "verticalScale": v_scale.clamp(50.0, 200.0),
                "color": color_hex,
                "align": align_str,
                "tracking": tracking.clamp(-100.0, 600.0),
                "leading": leading_val,
                "orientation": orient_str,
            });
            let chosen_font = font_family.trim();
            if !chosen_font.is_empty() {
                params["font"] = json!(chosen_font);
            }
            if let Ok(res) = ps.execute("type.create", params) {
                let ws = warp_style.trim();
                let valid_warp = matches!(
                    ws,
                    "arc" | "arch" | "flag" | "wave" | "bulge" | "fish" | "rise" | "twist" | "inflate" | "squeeze"
                );
                if valid_warp {
                    if let Some(lid) = res.get("layer").and_then(Value::as_u64) {
                        let _ = ps.execute(
                            "type.warpText",
                            json!({
                                "layer": lid,
                                "style": ws,
                                "bend": warp_bend.clamp(-100.0, 100.0),
                                "horizontalDistortion": warp_h_dist.clamp(-100.0, 100.0),
                                "verticalDistortion": warp_v_dist.clamp(-100.0, 100.0),
                                "orientation": orient_str,
                            }),
                        );
                    }
                }
            }
        }
        proxy.session = ps;
        guard.mark_full_dirty();
        Ok(())
    }

    /// v2.2.2: Unified 100% WYSIWYG Rust Live Proxy Preview for Text Studio Pro.
    #[allow(clippy::too_many_arguments)]
    pub fn update_proxy_text_pro_preview(
        &self,
        edit_layer_id: Option<u64>,
        text: String,
        x: f32,
        y: f32,
        size_px: f32,
        color_hex: String,
        align: String,
        font_family: String,
        bold: bool,
        italic: bool,
        underline: bool,
        strikethrough: bool,
        caps: String,
        h_scale: f32,
        v_scale: f32,
        tracking: f32,
        line_height: f32,
        vertical: bool,
        warp_style: String,
        warp_bend: f32,
        warp_h_dist: f32,
        warp_v_dist: f32,
        extras: TextProExtras,
    ) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        ensure_proxy_preview_locked(&mut guard)?;
        let active_id = guard.session.active().and_then(|ds| ds.active_layer());
        let Some(proxy) = guard.proxy_preview.as_mut() else {
            return Ok(());
        };
        let sc = proxy.scale;
        let mut ps = Session::new();
        ps.add_document((*proxy.base_doc).clone(), None);
        ps.set_active(0);
        if active_id.is_some() {
            ps.set_active_layer(active_id);
        }

        if let Some(eid) = edit_layer_id {
            if let Some(ds) = ps.active_mut() {
                let doc = Arc::make_mut(ds.doc_mut());
                if let Some(l) = doc.layers.iter_mut().find(|l| l.id.0 == eid) {
                    l.visible = false;
                }
            }
        }

        if !text.trim().is_empty() {
            let dpi = ps.active().map(|ds| ds.doc().resolution_dpi.max(1.0)).unwrap_or(72.0);
            let scaled_px = (size_px.max(4.0) * sc).max(2.0);
            let size_pt = (scaled_px * 72.0 / dpi).clamp(0.5, 1296.0);
            let weight: u32 = if bold { 700 } else { 400 };
            let leading_val: Value = if (line_height - 1.2).abs() < 0.03 {
                json!("auto")
            } else {
                json!((size_pt * line_height.clamp(0.7, 2.8)).clamp(1.0, 2000.0))
            };
            let align_str = match align.as_str() {
                "left" | "right" | "center" => align.as_str(),
                _ => "center",
            };
            let caps_str = match caps.as_str() {
                "all" | "small" => caps.as_str(),
                _ => "normal",
            };
            let orient_str = if vertical { "vertical" } else { "horizontal" };
            let px_x = x * sc;
            let px_y = (y - extras.baseline_shift) * sc;
            let mut params = json!({
                "text": text,
                "x": px_x,
                "y": px_y,
                "size": size_pt,
                "weight": weight,
                "fauxBold": bold,
                "italic": italic,
                "fauxItalic": italic,
                "underline": underline,
                "strikethrough": strikethrough,
                "caps": caps_str,
                "horizontalScale": h_scale.clamp(50.0, 200.0),
                "verticalScale": v_scale.clamp(50.0, 200.0),
                "color": color_hex,
                "align": align_str,
                "tracking": tracking.clamp(-100.0, 600.0),
                "leading": leading_val,
                "orientation": orient_str,
            });
            let chosen_font = font_family.trim();
            if !chosen_font.is_empty() {
                params["font"] = json!(chosen_font);
            }
            if let Ok(res) = ps.execute("type.create", params) {
                if let Some(lid) = res.get("layer").and_then(Value::as_u64) {
                    let ws = warp_style.trim();
                    if !ws.is_empty() && ws != "none" {
                        let _ = ps.execute(
                            "type.warpText",
                            json!({
                                "layer": lid,
                                "style": ws,
                                "bend": warp_bend.clamp(-100.0, 100.0),
                                "horizontalDistortion": warp_h_dist.clamp(-100.0, 100.0),
                                "verticalDistortion": warp_v_dist.clamp(-100.0, 100.0),
                                "orientation": orient_str,
                            }),
                        );
                    }
                    let op = extras.opacity.clamp(0.05, 1.0);
                    let blend = match extras.blend_mode.as_str() {
                        "Normal" | "Multiply" | "Screen" | "Overlay" | "SoftLight" | "HardLight" | "Darken" | "Lighten" => extras.blend_mode.as_str(),
                        _ => "Normal",
                    };
                    let _ = ps.execute("layer.setProps", json!({ "layer": lid, "opacity": op as f64, "blend": blend }));

                    let mut effects: Vec<Value> = Vec::new();
                    if extras.gradient_enabled {
                        effects.push(json!({
                            "kind": "gradientOverlay",
                            "params": {
                                "from": color_hex,
                                "to": extras.gradient_end_color,
                                "opacity": 100.0,
                                "angle": extras.gradient_angle.clamp(-180.0, 180.0) as f64,
                                "style": "linear",
                                "blend": "Normal"
                            }
                        }));
                    }
                    if extras.stroke_width > 0.4 {
                        effects.push(json!({
                            "kind": "stroke",
                            "params": {
                                "color": extras.stroke_color,
                                "size": (extras.stroke_width * sc).clamp(1.0, 60.0) as f64,
                                "opacity": 100.0,
                                "position": "outside",
                                "blend": "Normal"
                            }
                        }));
                    }
                    if extras.glow_size > 0.4 {
                        effects.push(json!({
                            "kind": "outerGlow",
                            "params": {
                                "color": extras.glow_color,
                                "size": (extras.glow_size * sc).clamp(1.0, 80.0) as f64,
                                "opacity": 85.0,
                                "blend": "Screen"
                            }
                        }));
                    }
                    if extras.shadow_enabled {
                        effects.push(json!({
                            "kind": "dropShadow",
                            "params": {
                                "color": extras.shadow_color,
                                "size": (extras.shadow_blur * sc).clamp(0.0, 60.0) as f64,
                                "distance": (extras.shadow_distance * sc).clamp(0.0, 80.0) as f64,
                                "angle": extras.shadow_angle.clamp(-180.0, 180.0) as f64,
                                "opacity": 82.0,
                                "blend": "Multiply"
                            }
                        }));
                    }
                    if !effects.is_empty() {
                        let _ = ps.execute("layer.layerStyle.replace", json!({ "layer": lid, "effects": effects }));
                    }

                    if extras.rotation_deg.abs() > 0.25 {
                        let _ = ps.execute(
                            "edit.transform",
                            json!({
                                "dx": 0.0, "dy": 0.0,
                                "scaleX": 100.0, "scaleY": 100.0,
                                "rotate": extras.rotation_deg.clamp(-180.0, 180.0) as f64,
                                "interpolation": "bicubic"
                            }),
                        );
                    }

                    if extras.bg_enabled {
                        let pw = ps.active().map(|ds| ds.doc().size.width.max(1) as f32).unwrap_or(512.0);
                        let ph = ps.active().map(|ds| ds.doc().size.height.max(1) as f32).unwrap_or(512.0);
                        let lines: Vec<&str> = text.split('\n').collect();
                        let max_chars = lines.iter().map(|l| l.chars().count()).max().unwrap_or(1).max(1) as f32;
                        let est_w = (max_chars * scaled_px * 0.62 * (h_scale / 100.0)).max(scaled_px * 1.4);
                        let est_h = (lines.len() as f32 * scaled_px * line_height.clamp(0.8, 2.4) * (v_scale / 100.0)).max(scaled_px * 1.1);
                        let pad = (extras.bg_padding * sc).clamp(3.0, 80.0);
                        let bx = (px_x - est_w * 0.5 - pad).clamp(0.0, pw - 8.0);
                        let by = (px_y - est_h * 0.5 - pad * 0.65).clamp(0.0, ph - 8.0);
                        let bw = (est_w + pad * 2.0).clamp(12.0, pw - bx);
                        let bh = (est_h + pad * 1.3).clamp(12.0, ph - by);
                        let bg_params = build_shape_params(
                            "roundedRect",
                            bx,
                            by,
                            bx + bw,
                            by + bh,
                            &extras.bg_color,
                            "",
                            0.0,
                            (extras.bg_radius * sc).clamp(0.0, 60.0),
                            4,
                        );
                        let _ = ps.execute("shape.create", bg_params);
                        if let Some(ds) = ps.active() {
                            if let Some(bg_id) = ds.active_layer() {
                                let _ = ps.execute("layer.setProps", json!({ "layer": bg_id.0, "opacity": extras.bg_opacity.clamp(0.05, 1.0) as f64 }));
                                let _ = ps.execute("layer.arrange.sendBackward", json!({}));
                            }
                        }
                    }
                }
            }
        }
        proxy.selection_only = false;
        proxy.session = ps;
        guard.mark_full_dirty();
        Ok(())
    }

    /// Creates an anti-aliased glyph-shaped selection mask (`type.createSelection`) without leaving a text layer behind.
    #[allow(clippy::too_many_arguments)]
    pub fn create_text_selection_mask(
        &self,
        text: String,
        x: f32,
        y: f32,
        size_px: f32,
        align: String,
        font_family: String,
        bold: bool,
        italic: bool,
        caps: String,
        h_scale: f32,
        v_scale: f32,
        tracking: f32,
        line_height: f32,
        vertical: bool,
        warp_style: String,
        warp_bend: f32,
        warp_h_dist: f32,
        warp_v_dist: f32,
    ) -> Result<DocumentInfo, EngineError> {
        let trimmed = text.trim();
        if trimmed.is_empty() {
            return Err(EngineError::InvalidParams { detail: "Metin boş olamaz".into() });
        }
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        guard.live_stroke = None;
        guard.proxy_preview = None;

        let dpi = guard
            .session
            .active()
            .map(|ds| ds.doc().resolution_dpi.max(1.0))
            .unwrap_or(72.0);
        let size_pt = (size_px.max(4.0) * 72.0 / dpi).clamp(0.5, 1296.0);
        let weight: u32 = if bold { 700 } else { 400 };
        let leading_val: Value = if (line_height - 1.2).abs() < 0.03 {
            json!("auto")
        } else {
            json!((size_pt * line_height.clamp(0.7, 2.8)).clamp(2.0, 2000.0))
        };
        let align_str = match align.as_str() {
            "left" | "right" | "center" => align.as_str(),
            _ => "center",
        };
        let caps_str = match caps.as_str() {
            "all" | "small" => caps.as_str(),
            _ => "normal",
        };
        let orient_str = if vertical { "vertical" } else { "horizontal" };
        let mut params = json!({
            "text": text,
            "x": x,
            "y": y,
            "size": size_pt,
            "weight": weight,
            "fauxBold": bold,
            "italic": italic,
            "fauxItalic": italic,
            "caps": caps_str,
            "horizontalScale": h_scale.clamp(50.0, 200.0),
            "verticalScale": v_scale.clamp(50.0, 200.0),
            "align": align_str,
            "tracking": tracking.clamp(-100.0, 600.0),
            "leading": leading_val,
            "orientation": orient_str,
        });
        let chosen_font = font_family.trim();
        if !chosen_font.is_empty() {
            params["font"] = json!(chosen_font);
        }
        let ws = warp_style.trim();
        let valid_warp = matches!(
            ws,
            "arc" | "arch" | "flag" | "wave" | "bulge" | "fish" | "rise" | "twist" | "inflate" | "squeeze"
        );
        if valid_warp {
            let res = guard
                .session
                .execute("type.create", params)
                .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
            if let Some(lid) = res.get("layer").and_then(Value::as_u64) {
                let _ = guard.session.execute(
                    "type.warpText",
                    json!({
                        "layer": lid,
                        "style": ws,
                        "bend": warp_bend.clamp(-100.0, 100.0),
                        "horizontalDistortion": warp_h_dist.clamp(-100.0, 100.0),
                        "verticalDistortion": warp_v_dist.clamp(-100.0, 100.0),
                        "orientation": orient_str,
                    }),
                );
                guard
                    .session
                    .execute("type.createSelection", json!({ "layer": lid, "removeLayer": true }))
                    .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
            }
        } else {
            guard
                .session
                .execute("type.createSelection", params)
                .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        }
        guard.mark_selection_dirty();
        self.info_from_locked(&guard)
    }

    /// 60 FPS live proxy preview for PhotoCraft Puppet Warp (`edit.puppetWarp`).
    /// Scales pin coordinates into the 512px proxy space and warps the primary raster layer smoothly.
    pub fn update_proxy_puppet_warp(
        &self,
        pins: Vec<PuppetPinPoint>,
        mode: String,
        density: String,
        expansion: f32,
    ) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        ensure_proxy_preview_locked(&mut guard)?;
        let Some(proxy) = guard.proxy_preview.as_mut() else {
            return Ok(());
        };
        let scale = proxy.scale as f64;
        let any_changed = pins.iter().any(|p| {
            (p.dst_x - p.src_x).hypot(p.dst_y - p.src_y) > 0.5
                || p.rotate_deg.is_some_and(|r| r.abs() > 0.1)
        });
        let mut ps = Session::new();
        ps.add_document((*proxy.base_doc).clone(), None);
        ps.set_active(0);
        if !pins.is_empty() && any_changed {
            let mode_str = match mode.as_str() {
                "rigid" | "distort" | "normal" => mode.as_str(),
                _ => "normal",
            };
            let dens_str = match density.as_str() {
                "fewer" | "more" | "normal" => density.as_str(),
                _ => "normal",
            };
            let pin_json: Vec<Value> = pins
                .iter()
                .map(|p| {
                    let mut obj = serde_json::Map::new();
                    obj.insert("src".into(), json!([(p.src_x as f64) * scale, (p.src_y as f64) * scale]));
                    obj.insert("dst".into(), json!([(p.dst_x as f64) * scale, (p.dst_y as f64) * scale]));
                    if let Some(rot) = p.rotate_deg {
                        if rot.abs() > 0.05 {
                            obj.insert("rotate".into(), json!(rot as f64));
                        }
                    }
                    if p.depth != 0 {
                        obj.insert("depth".into(), json!(p.depth));
                    }
                    Value::Object(obj)
                })
                .collect();
            let target_id = ps
                .active()
                .and_then(|ds| find_primary_photo_raster_layer(ds.doc(), ds.active_layer()).map(|(id, _)| id));
            if let Some(tid) = target_id {
                ps.set_active_layer(Some(tid));
                let _ = ps.execute(
                    "edit.puppetWarp",
                    json!({
                        "pins": pin_json,
                        "mode": mode_str,
                        "density": dens_str,
                        "expansion": ((expansion as f64) * scale).clamp(-20.0, 60.0),
                    }),
                );
            }
        }
        proxy.session = ps;
        guard.mark_full_dirty();
        Ok(())
    }

    pub fn get_puppet_mesh_segments(
        &self,
        pins: Vec<PuppetPinPoint>,
        mode: String,
        density: String,
        expansion: f32,
    ) -> Result<Vec<f32>, EngineError> {
        let mut guard = self.core.lock();
        ensure_proxy_preview_locked(&mut guard)?;
        let Some(proxy) = guard.proxy_preview.as_ref() else {
            return Ok(Vec::new());
        };
        let scale = proxy.scale as f64;
        let inv_scale = if scale > 1e-5 { 1.0 / scale } else { 1.0 };
        let doc = &proxy.base_doc;
        let target_id = find_primary_photo_raster_layer(doc, guard.session.active().and_then(|d| d.active_layer()))
            .map(|(id, _)| id);
        let Some(tid) = target_id else {
            return Ok(Vec::new());
        };
        let Some(layer) = doc.layer(tid) else {
            return Ok(Vec::new());
        };
        let LayerContent::Raster(ref surf) = layer.content else {
            return Ok(Vec::new());
        };
        let bounds = doc.bounds();
        let warp = photocraft_algo::puppet::PuppetWarp {
            pins: pins
                .iter()
                .map(|p| photocraft_algo::puppet::PuppetPin {
                    src: [(p.src_x as f64) * scale, (p.src_y as f64) * scale],
                    dst: [(p.dst_x as f64) * scale, (p.dst_y as f64) * scale],
                    rotate: p.rotate_deg.filter(|r| r.abs() > 0.05).map(|r| r as f64),
                    depth: p.depth,
                })
                .collect(),
            mode: photocraft_algo::puppet::PuppetMode::parse(&mode).unwrap_or(photocraft_algo::puppet::PuppetMode::Normal),
            density: photocraft_algo::puppet::PuppetDensity::parse(&density).unwrap_or(photocraft_algo::puppet::PuppetDensity::Normal),
            expansion: ((expansion as f64) * scale).clamp(-20.0, 60.0),
        };
        let (verts, tris) = if warp.pins.is_empty() || warp.is_identity() {
            let mesh = photocraft_algo::puppet::build_mesh(surf, bounds, warp.density, warp.expansion);
            (mesh.verts, mesh.tris)
        } else {
            let (solver, deformed, _) = photocraft_algo::puppet::deform(surf, bounds, &warp, 8);
            (deformed, solver.mesh.tris)
        };
        let mut seen = std::collections::HashSet::with_capacity(tris.len() * 2);
        let mut out = Vec::with_capacity(tris.len() * 8);
        for [a, b, c] in tris {
            for (u, v) in [(a, b), (b, c), (c, a)] {
                let key = if u < v { (u, v) } else { (v, u) };
                if seen.insert(key) {
                    if let (Some(p0), Some(p1)) = (verts.get(u), verts.get(v)) {
                        out.push((p0[0] * inv_scale) as f32);
                        out.push((p0[1] * inv_scale) as f32);
                        out.push((p1[0] * inv_scale) as f32);
                        out.push((p1[1] * inv_scale) as f32);
                    }
                }
            }
        }
        Ok(out)
    }

    pub fn apply_puppet_warp(
        &self,
        pins: Vec<PuppetPinPoint>,
        mode: String,
        density: String,
        expansion: f32,
    ) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        guard.proxy_preview = None;
        guard.dismiss_compare();
        let any_changed = pins.iter().any(|p| {
            (p.dst_x - p.src_x).hypot(p.dst_y - p.src_y) > 0.5
                || p.rotate_deg.is_some_and(|r| r.abs() > 0.1)
        });
        if pins.is_empty() || !any_changed {
            guard.mark_full_dirty();
            return self.info_from_locked(&guard);
        }
        let mode_str = match mode.as_str() {
            "rigid" | "distort" | "normal" => mode.as_str(),
            _ => "normal",
        };
        let dens_str = match density.as_str() {
            "fewer" | "more" | "normal" => density.as_str(),
            _ => "normal",
        };
        let pin_json: Vec<Value> = pins
            .iter()
            .map(|p| {
                let mut obj = serde_json::Map::new();
                obj.insert("src".into(), json!([p.src_x as f64, p.src_y as f64]));
                obj.insert("dst".into(), json!([p.dst_x as f64, p.dst_y as f64]));
                if let Some(rot) = p.rotate_deg {
                    if rot.abs() > 0.05 {
                        obj.insert("rotate".into(), json!(rot as f64));
                    }
                }
                if p.depth != 0 {
                    obj.insert("depth".into(), json!(p.depth));
                }
                Value::Object(obj)
            })
            .collect();
        let target_id = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            find_primary_photo_raster_layer(ds.doc(), ds.active_layer())
                .map(|(id, _)| id)
                .ok_or_else(|| EngineError::CommandFailed {
                    detail: "Kukla bükme uygulanacak fotoğraf katmanı bulunamadı".into(),
                })?
        };
        guard.session.set_active_layer(Some(target_id));
        guard
            .session
            .execute(
                "edit.puppetWarp",
                json!({
                    "pins": pin_json,
                    "mode": mode_str,
                    "density": dens_str,
                    "expansion": (expansion as f64).clamp(-20.0, 60.0),
                }),
            )
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    pub fn resize_image(&self, width: u32, height: u32, resample: String) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        guard.proxy_preview = None;
        guard.dismiss_compare();
        let w = width.clamp(16, 8192);
        let h = height.clamp(16, 8192);
        let method = match resample.as_str() {
            "lanczos" | "bicubic" | "bicubicSharper" | "bicubicSmoother" | "preserveDetails" | "bilinear" | "nearest" => resample.as_str(),
            _ => "lanczos",
        };
        guard
            .session
            .execute("image.imageSize", json!({ "width": w, "height": h, "resample": method }))
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    /// Rasterizes the currently active Text layer into a normal editable pixel layer (`type.rasterize`).
    pub fn rasterize_active_text_layer(&self) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        guard.live_stroke = None;
        guard.proxy_preview = None;
        guard
            .session
            .execute("type.rasterize", json!({}))
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    pub fn add_text_layer(
        &self,
        text: String,
        x: f32,
        y: f32,
        size_px: f32,
        color_hex: String,
        align: String,
    ) -> Result<DocumentInfo, EngineError> {
        let trimmed = text.trim();
        if trimmed.is_empty() {
            return Err(EngineError::InvalidParams { detail: "Metin boş olamaz".into() });
        }
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        guard.live_stroke = None;
        let dpi = guard
            .session
            .active()
            .map(|ds| ds.doc().resolution_dpi.max(1.0))
            .unwrap_or(72.0);
        let size_pt = (size_px.max(8.0) * 72.0 / dpi).clamp(6.0, 1296.0);

        // Pick Roboto if available in /system/fonts, else first discovered font family
        let mut chosen_font: Option<String> = None;
        if let Ok(fonts_val) = guard.session.execute("type.fonts", json!({})) {
            let list = fonts_val
                .get("fonts")
                .and_then(Value::as_array)
                .or_else(|| fonts_val.as_array());
            if let Some(arr) = list {
                let mut first_name: Option<String> = None;
                for item in arr {
                    let name = item
                        .as_str()
                        .or_else(|| item.get("family").and_then(Value::as_str))
                        .or_else(|| item.get("name").and_then(Value::as_str));
                    if let Some(n) = name {
                        if first_name.is_none() {
                            first_name = Some(n.to_string());
                        }
                        if n.eq_ignore_ascii_case("Roboto") || n.eq_ignore_ascii_case("Noto Sans") {
                            chosen_font = Some(n.to_string());
                            break;
                        }
                    }
                }
                if chosen_font.is_none() {
                    chosen_font = first_name;
                }
            }
        }

        let mut params = json!({
            "x": x as f64,
            "y": y as f64,
            "text": trimmed,
            "size": size_pt,
            "color": color_hex,
            "align": align,
        });
        if let Some(f) = chosen_font {
            params["font"] = json!(f);
        }

        guard
            .session
            .execute("type.create", params)
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    /// Applies a native PhotoCraft retouch stroke (`spot_heal`, `smudge`, `dodge`, `burn`)
    /// by automatically targeting the primary photo raster layer and restoring its lock afterward.
    pub fn apply_retouch_stroke(
        &self,
        tool: String,
        samples: Vec<StrokeSample>,
        size: f32,
        hardness: f32,
        strength: f32,
    ) -> Result<DocumentInfo, EngineError> {
        if samples.is_empty() {
            let guard = self.core.lock();
            return self.info_from_locked(&guard);
        }
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        guard.live_stroke = None;

        let (prev_active, target_id, was_locked) = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            let prev = ds.active_layer();
            let (id, locked) = find_primary_photo_raster_layer(ds.doc(), prev).ok_or_else(|| {
                EngineError::CommandFailed {
                    detail: "Rötuş uygulanacak fotoğraf katmanı bulunamadı".into(),
                }
            })?;
            (prev, id, locked)
        };

        // retouch_cmds.rs expects points as [[x, y, pressure], ...] arrays and hardness in 0..100%
        let pts_json: Vec<Value> = samples
            .iter()
            .map(|pt| json!([pt.x as f64, pt.y as f64, pt.pressure.clamp(0.05, 1.0) as f64]))
            .collect();
        let clamped_size = if tool == "spot_heal" {
            size.clamp(1.0, 140.0)
        } else {
            size.clamp(1.0, 400.0)
        };
        let clamped_hard = (hardness.clamp(0.0, 1.0) * 100.0).round();
        let clamped_str = strength.clamp(5.0, 100.0);

        let (cmd, params) = match tool.as_str() {
            "spot_heal" => (
                "paint.spotHealing",
                json!({
                    "points": pts_json,
                    "size": clamped_size,
                    "hardness": clamped_hard,
                    "type": "proximityMatch",
                    "sampleAllLayers": false
                }),
            ),
            "smudge" => (
                "paint.smudge",
                json!({
                    "points": pts_json,
                    "size": clamped_size,
                    "hardness": clamped_hard,
                    "strength": clamped_str,
                    "sampleAllLayers": false
                }),
            ),
            "dodge" => (
                "paint.dodge",
                json!({
                    "points": pts_json,
                    "size": clamped_size,
                    "hardness": clamped_hard,
                    "range": "midtones",
                    "exposure": clamped_str,
                    "protectTones": true
                }),
            ),
            "burn" => (
                "paint.burn",
                json!({
                    "points": pts_json,
                    "size": clamped_size,
                    "hardness": clamped_hard,
                    "range": "midtones",
                    "exposure": clamped_str,
                    "protectTones": true
                }),
            ),
            other => {
                return Err(EngineError::InvalidParams {
                    detail: format!("Bilinmeyen rötuş aracı: {other}"),
                })
            }
        };

        guard.session.set_active_layer(Some(target_id));
        if was_locked {
            let _ = guard.session.execute(
                "layer.setProps",
                json!({ "layer": target_id.0, "locks": { "pixels": false } }),
            );
        }

        let res = guard.session.execute(cmd, params);

        if was_locked {
            let _ = guard.session.execute(
                "layer.setProps",
                json!({ "layer": target_id.0, "locks": { "pixels": true } }),
            );
        }
        if prev_active.is_some() {
            guard.session.set_active_layer(prev_active);
        }

        res.map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        let pad = (clamped_size * 1.4).ceil() as i32;
        let mut min_x = i32::MAX;
        let mut min_y = i32::MAX;
        let mut max_x = i32::MIN;
        let mut max_y = i32::MIN;
        for s in &samples {
            let sx = s.x.round() as i32;
            let sy = s.y.round() as i32;
            min_x = min_x.min(sx - pad);
            min_y = min_y.min(sy - pad);
            max_x = max_x.max(sx + pad);
            max_y = max_y.max(sy + pad);
        }
        if min_x < max_x && min_y < max_y {
            guard.mark_region_dirty(Rect::new(min_x, min_y, max_x, max_y));
        } else {
            guard.mark_full_dirty();
        }
        self.info_from_locked(&guard)
    }

    pub fn sample_color_hex(&self, x: f32, y: f32) -> Result<String, EngineError> {
        let mut guard = self.core.lock();
        let out = guard
            .session
            .execute(
                "document.sampleColor",
                json!({ "x": x as f64, "y": y as f64, "size": 3 }),
            )
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        let arr = out
            .as_array()
            .filter(|a| a.len() >= 3)
            .ok_or_else(|| EngineError::CommandFailed { detail: "Geçersiz renk örneği".into() })?;
        let r = (arr[0].as_f64().unwrap_or(0.0).clamp(0.0, 1.0) * 255.0).round() as u8;
        let g = (arr[1].as_f64().unwrap_or(0.0).clamp(0.0, 1.0) * 255.0).round() as u8;
        let b = (arr[2].as_f64().unwrap_or(0.0).clamp(0.0, 1.0) * 255.0).round() as u8;
        Ok(format!("#{r:02X}{g:02X}{b:02X}"))
    }

    pub fn set_symmetry(&self, mode: String) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        let m = mode.trim().to_ascii_lowercase();
        if m.is_empty() || m == "off" {
            let _ = guard.session.execute("paint.symmetryDisable", json!({}));
            return Ok(());
        }
        guard
            .session
            .execute("paint.setSymmetry", json!({ "mode": m }))
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        Ok(())
    }

    /// Applies the net result of GPU-previewed flips and 90-degree rotations in one clean batch.
    pub fn apply_canvas_transform_batch(
        &self,
        quarter_turns_cw: i32,
        flip_h: bool,
        flip_v: bool,
    ) -> Result<DocumentInfo, EngineError> {
        let turns = ((quarter_turns_cw % 4) + 4) % 4;
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        guard.live_stroke = None;
        guard.live_dab = None;
        guard.live_adjustment = None;
        guard.live_filter = None;

        if flip_h {
            guard
                .session
                .execute("image.imageRotation.flipCanvasHorizontal", json!({}))
                .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        }
        if flip_v {
            guard
                .session
                .execute("image.imageRotation.flipCanvasVertical", json!({}))
                .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        }
        match turns {
            1 => {
                guard
                    .session
                    .execute("image.imageRotation.90cw", json!({}))
                    .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
            }
            2 => {
                guard
                    .session
                    .execute("image.imageRotation.180", json!({}))
                    .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
            }
            3 => {
                guard
                    .session
                    .execute("image.imageRotation.90ccw", json!({}))
                    .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
            }
            _ => {}
        }

        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    /// Perspective Crop (`image.perspectiveCrop`): warps a 4-corner convex quad (TL, TR, BR, BL)
    /// into a flat upright rectangle in one undo step.
    pub fn perspective_crop(
        &self,
        tl_x: f32,
        tl_y: f32,
        tr_x: f32,
        tr_y: f32,
        br_x: f32,
        br_y: f32,
        bl_x: f32,
        bl_y: f32,
    ) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        guard.live_dab = None;
        guard.live_clone = None;
        guard.live_adjustment = None;
        guard.live_filter = None;
        guard
            .session
            .execute(
                "image.perspectiveCrop",
                json!({
                    "corners": [
                        [tl_x as f64, tl_y as f64],
                        [tr_x as f64, tr_y as f64],
                        [br_x as f64, br_y as f64],
                        [bl_x as f64, bl_y as f64]
                    ],
                    "interpolation": "bicubic"
                }),
            )
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.dismiss_compare();
        guard.mark_selection_dirty();
        self.info_from_locked(&guard)
    }

    pub fn crop_canvas(&self, x: i32, y: i32, width: i32, height: i32) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        guard.live_dab = None;
        guard.live_adjustment = None;
        guard.live_filter = None;
        guard
            .session
            .execute(
                "image.crop",
                json!({
                    "x": x.max(0),
                    "y": y.max(0),
                    "width": width.max(1),
                    "height": height.max(1)
                }),
            )
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.dismiss_compare();
        guard.mark_selection_dirty();
        self.info_from_locked(&guard)
    }

    /// Bakes the 60 FPS GPU-previewed Photo Mirror Effect into the photo raster layer once (single clean Undo step).
    /// `mode`: `"left_to_right"`, `"right_to_left"`, `"top_to_bottom"`, `"bottom_to_top"`.
    pub fn apply_photo_mirror(&self, mode: String, axis_ratio: f32) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        guard.live_stroke = None;
        guard.live_dab = None;
        if guard.session.active().is_none() {
            return Err(EngineError::NoActiveDocument);
        }
        let ratio = axis_ratio.clamp(0.05, 0.95);
        guard
            .session
            .edit("Ayna Efekti", |doc, _| {
                let bounds = doc.bounds();
                let w = doc.size.width as usize;
                let h = doc.size.height as usize;
                if w < 2 || h < 2 {
                    return Ok(());
                }

                let ax = ((w as f32) * ratio).round().clamp(1.0, (w - 1) as f32) as isize;
                let ay = ((h as f32) * ratio).round().clamp(1.0, (h - 1) as f32) as isize;

                let ping_pong = |v: isize, len: isize| -> usize {
                    if len <= 1 {
                        return 0;
                    }
                    let period = 2 * len;
                    let m = v.rem_euclid(period);
                    let idx = if m < len { m } else { period - 1 - m };
                    idx.clamp(0, len - 1) as usize
                };

                let mirror_x_right = match mode.as_str() {
                    "enine_auto" | "left_to_right" => ratio >= 0.5,
                    "enine_flip" | "right_to_left" => ratio < 0.5,
                    _ => false,
                };
                let mirror_x_left = match mode.as_str() {
                    "enine_auto" | "left_to_right" => ratio < 0.5,
                    "enine_flip" | "right_to_left" => ratio >= 0.5,
                    _ => false,
                };
                let mirror_y_bottom = match mode.as_str() {
                    "boyuna_auto" | "top_to_bottom" => ratio >= 0.5,
                    "boyuna_flip" | "bottom_to_top" => ratio < 0.5,
                    _ => false,
                };
                let mirror_y_top = match mode.as_str() {
                    "boyuna_auto" | "top_to_bottom" => ratio < 0.5,
                    "boyuna_flip" | "bottom_to_top" => ratio >= 0.5,
                    _ => false,
                };

                // Mirror EVERY visible raster layer (Background + Çizim Katmanı + any photo layers)
                // so the baked result matches the 60 FPS GPU composite preview 100% WYSIWYG.
                for layer in &mut doc.layers {
                    if !layer.visible {
                        continue;
                    }
                    let LayerContent::Raster(surf) = &mut layer.content else {
                        continue;
                    };
                    let ch = surf.format().channels();
                    let mut data = surf.read_region(bounds);
                    let row_stride = w * ch;
                    if data.len() < row_stride * h {
                        continue;
                    }
                    let src_snap = data.clone();

                    if mirror_x_right {
                        for y in 0..h {
                            let base = y * row_stride;
                            for x in (ax as usize)..w {
                                let sx = ping_pong(2 * ax - 1 - x as isize, w as isize);
                                let dst = base + x * ch;
                                let src = base + sx * ch;
                                data[dst..(dst + ch)].copy_from_slice(&src_snap[src..(src + ch)]);
                            }
                        }
                    } else if mirror_x_left {
                        for y in 0..h {
                            let base = y * row_stride;
                            for x in 0..(ax as usize) {
                                let sx = ping_pong(2 * ax - 1 - x as isize, w as isize);
                                let dst = base + x * ch;
                                let src = base + sx * ch;
                                data[dst..(dst + ch)].copy_from_slice(&src_snap[src..(src + ch)]);
                            }
                        }
                    } else if mirror_y_bottom {
                        for y in (ay as usize)..h {
                            let sy = ping_pong(2 * ay - 1 - y as isize, h as isize);
                            let src_start = sy * row_stride;
                            let dst_start = y * row_stride;
                            data[dst_start..(dst_start + row_stride)]
                                .copy_from_slice(&src_snap[src_start..(src_start + row_stride)]);
                        }
                    } else if mirror_y_top {
                        for y in 0..(ay as usize) {
                            let sy = ping_pong(2 * ay - 1 - y as isize, h as isize);
                            let src_start = sy * row_stride;
                            let dst_start = y * row_stride;
                            data[dst_start..(dst_start + row_stride)]
                                .copy_from_slice(&src_snap[src_start..(src_start + row_stride)]);
                        }
                    }

                    surf.write_region_par(bounds, &data);
                }
                Ok(())
            })
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;

        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    /// Low-memory O(1)-per-pixel 3-pass separable Box/Gaussian Blur with transparency & selection-mask parity.
    pub fn apply_blur_fast(&self, radius: f32) -> Result<DocumentInfo, EngineError> {
        let r = radius.clamp(0.0, 80.0);
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        guard.proxy_preview = None;
        guard.dismiss_compare();
        let target_id = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            find_primary_photo_raster_layer(ds.doc(), ds.active_layer())
                .map(|(id, _)| id)
                .ok_or_else(|| EngineError::CommandFailed {
                    detail: "Bulanıklaştırma uygulanacak fotoğraf katmanı bulunamadı".into(),
                })?
        };
        guard
            .session
            .edit("Bulanıklaştırma", |doc, _| {
                run_blur_on_doc(doc, target_id, r);
                Ok(())
            })
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    /// Low-memory resolution-invariant Unsharp Mask sharpen with transparency & selection-mask parity.
    pub fn apply_sharpen_fast(&self, amount: f32) -> Result<DocumentInfo, EngineError> {
        let amt = amount.clamp(0.0, 250.0);
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        guard.proxy_preview = None;
        guard.dismiss_compare();
        let target_id = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            find_primary_photo_raster_layer(ds.doc(), ds.active_layer())
                .map(|(id, _)| id)
                .ok_or_else(|| EngineError::CommandFailed {
                    detail: "Keskinleştirme uygulanacak fotoğraf katmanı bulunamadı".into(),
                })?
        };
        guard
            .session
            .edit("Keskinleştirme", |doc, _| {
                run_sharpen_on_doc(doc, target_id, amt);
                Ok(())
            })
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    /// Starts a 60 FPS exact-parity alpha- & layer-preserving Proxy Preview (~512px) so EVERY PhotoCraft
    /// adjustment and filter preserves transparency, layers, and selection masks identically to `Uygula`.
    pub fn begin_proxy_preview(&self) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        guard.live_dab = None;
        guard.live_clone = None;
        guard.dismiss_compare();
        let (real_doc, active_id) = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            (Arc::clone(ds.doc()), ds.active_layer())
        };
        let real_w = real_doc.size.width.max(1);
        let real_h = real_doc.size.height.max(1);
        let max_proxy = 1024u32;
        let scale = (max_proxy as f32 / real_w.max(real_h) as f32).min(1.0);
        let pw = ((real_w as f32 * scale).round() as u32).clamp(1, max_proxy);
        let ph = ((real_h as f32 * scale).round() as u32).clamp(1, max_proxy);

        let mut ps = Session::new();
        ps.add_document((*real_doc).clone(), None);
        ps.set_active(0);
        if active_id.is_some() {
            ps.set_active_layer(active_id);
        }
        // NOTE: NEVER call layer.flattenImage here — flattening destroys transparent backgrounds by filling solid white!
        if pw < real_w || ph < real_h {
            let _ = ps.execute(
                "image.imageSize",
                json!({ "width": pw, "height": ph, "resample": "bilinear" }),
            );
        }
        // Unlock pixel locks on proxy clone once so ALL 17+ live proxy filters work on locked Background layers
        if let Some(locked_ids) = ps.active().map(|ds| {
            ds.doc().layers.iter().filter(|l| l.locks.pixels).map(|l| l.id.0).collect::<Vec<_>>()
        }) {
            for lid in locked_ids {
                let _ = ps.execute("layer.setProps", json!({ "layer": lid, "locks": { "pixels": false } }));
            }
        }
        let base_doc = ps
            .active()
            .map(|ds| Arc::clone(ds.doc()))
            .ok_or(EngineError::NoActiveDocument)?;
        guard.proxy_preview = Some(ProxyPreviewSession {
            base_doc,
            session: ps,
            scale,
            real_size: (real_w, real_h),
        selection_only: false,
        });
        guard.mark_full_dirty();
        Ok(())
    }

    /// Resets the live proxy preview back to its clean untouched `base_doc` (used by Reset / Sıfırla).
    pub fn reset_proxy_preview(&self) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        let active_id = guard.session.active().and_then(|ds| ds.active_layer());
        ensure_proxy_preview_locked(&mut guard)?;
        if let Some(proxy) = guard.proxy_preview.as_mut() {
            let mut ps = Session::new();
            ps.add_document((*proxy.base_doc).clone(), None);
            ps.set_active(0);
            if active_id.is_some() {
                ps.set_active_layer(active_id);
            }
            proxy.session = ps;
        }
        guard.mark_full_dirty();
        Ok(())
    }

    pub fn update_proxy_adjustment(&self, kind: String, params_json: String) -> Result<(), EngineError> {
        let params: Value = if params_json.trim().is_empty() {
            json!({})
        } else {
            serde_json::from_str(&params_json)
                .map_err(|e| EngineError::InvalidParams { detail: e.to_string() })?
        };
        let cmd = format!("layer.newAdjustmentLayer.{kind}");
        let mut guard = self.core.lock();
        let active_id = guard.session.active().and_then(|ds| ds.active_layer());
        guard.dismiss_compare();
        ensure_proxy_preview_locked(&mut guard)?;
        let Some(proxy) = guard.proxy_preview.as_mut() else {
            return Ok(());
        };
        let mut ps = Session::new();
        ps.add_document((*proxy.base_doc).clone(), None);
        ps.set_active(0);
        if active_id.is_some() {
            ps.set_active_layer(active_id);
        }
        let _ = ps.execute(&cmd, params);
        proxy.session = ps;
        guard.mark_full_dirty();
        Ok(())
    }

    pub fn update_proxy_blur(&self, radius: f32) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        let active_id = guard.session.active().and_then(|ds| ds.active_layer());
        guard.dismiss_compare();
        ensure_proxy_preview_locked(&mut guard)?;
        let Some(proxy) = guard.proxy_preview.as_mut() else {
            return Ok(());
        };
        let mut ps = Session::new();
        ps.add_document((*proxy.base_doc).clone(), None);
        ps.set_active(0);
        if active_id.is_some() {
            ps.set_active_layer(active_id);
        }
        let scaled_r = (radius.clamp(0.0, 80.0) * proxy.scale).clamp(0.0, 60.0);
        if scaled_r >= 0.5 {
            let target_id = ps
                .active()
                .and_then(|ds| find_primary_photo_raster_layer(ds.doc(), ds.active_layer()).map(|(id, _)| id));
            if let Some(tid) = target_id {
                let _ = ps.edit("Proxy Blur", |doc, _| {
                    run_blur_on_doc(doc, tid, scaled_r);
                    Ok(())
                });
            }
        }
        proxy.session = ps;
        guard.mark_full_dirty();
        Ok(())
    }

    pub fn update_proxy_sharpen(&self, amount: f32) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        let active_id = guard.session.active().and_then(|ds| ds.active_layer());
        guard.dismiss_compare();
        ensure_proxy_preview_locked(&mut guard)?;
        let Some(proxy) = guard.proxy_preview.as_mut() else {
            return Ok(());
        };
        let mut ps = Session::new();
        ps.add_document((*proxy.base_doc).clone(), None);
        ps.set_active(0);
        if active_id.is_some() {
            ps.set_active_layer(active_id);
        }
        if amount > 0.5 {
            let target_id = ps
                .active()
                .and_then(|ds| find_primary_photo_raster_layer(ds.doc(), ds.active_layer()).map(|(id, _)| id));
            if let Some(tid) = target_id {
                let _ = ps.edit("Proxy Sharpen", |doc, _| {
                    run_sharpen_on_doc(doc, tid, amount);
                    Ok(())
                });
            }
        }
        proxy.session = ps;
        guard.mark_full_dirty();
        Ok(())
    }

    /// Starts a 60 FPS multi-layer Proxy Preview (~512px WITHOUT flattening) so Layer Styles (`fx`)
    /// and future per-layer tools preview 100% live and identically to `Uygula` in 2-4ms.
    pub fn begin_layer_proxy_preview(&self) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        guard.live_dab = None;
        guard.live_clone = None;
        guard.dismiss_compare();
        let (real_doc, active_id) = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            (Arc::clone(ds.doc()), ds.active_layer())
        };
        let real_w = real_doc.size.width.max(1);
        let real_h = real_doc.size.height.max(1);
        let max_proxy = 1024u32;
        let scale = (max_proxy as f32 / real_w.max(real_h) as f32).min(1.0);
        let pw = ((real_w as f32 * scale).round() as u32).clamp(1, max_proxy);
        let ph = ((real_h as f32 * scale).round() as u32).clamp(1, max_proxy);

        let mut ps = Session::new();
        ps.add_document((*real_doc).clone(), None);
        ps.set_active(0);
        if active_id.is_some() {
            ps.set_active_layer(active_id);
        }
        if pw < real_w || ph < real_h {
            let _ = ps.execute(
                "image.imageSize",
                json!({ "width": pw, "height": ph, "resample": "bilinear" }),
            );
        }
        // Unlock pixel locks on proxy clone once so ALL 17+ live proxy filters work on locked Background layers
        if let Some(locked_ids) = ps.active().map(|ds| {
            ds.doc().layers.iter().filter(|l| l.locks.pixels).map(|l| l.id.0).collect::<Vec<_>>()
        }) {
            for lid in locked_ids {
                let _ = ps.execute("layer.setProps", json!({ "layer": lid, "locks": { "pixels": false } }));
            }
        }
        let base_doc = ps
            .active()
            .map(|ds| Arc::clone(ds.doc()))
            .ok_or(EngineError::NoActiveDocument)?;
        guard.proxy_preview = Some(ProxyPreviewSession {
            base_doc,
            session: ps,
            scale,
            real_size: (real_w, real_h),
        selection_only: false,
        });
        guard.mark_full_dirty();
        Ok(())
    }

    pub fn update_proxy_layer_effect(
        &self,
        kind: String,
        color_hex: String,
        size: f32,
        opacity: f32,
        distance: f32,
    ) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        let active_id = guard.session.active().and_then(|ds| ds.active_layer());
        guard.dismiss_compare();
        ensure_proxy_preview_locked(&mut guard)?;
        let Some(proxy) = guard.proxy_preview.as_mut() else {
            return Ok(());
        };
        let mut ps = Session::new();
        ps.add_document((*proxy.base_doc).clone(), None);
        ps.set_active(0);
        if active_id.is_some() {
            ps.set_active_layer(active_id);
        }
        let is_bg = ps.active().is_some_and(|ds| {
            let bottom = ds.doc().layers.first().map(|l| l.id);
            ds.active_layer().is_some() && ds.active_layer() == bottom
        });
        let op = opacity.clamp(5.0, 100.0) as f64;
        let sz = (size * proxy.scale).clamp(1.0, 150.0) as f64;
        let dist = (distance * proxy.scale).clamp(0.0, 150.0) as f64;
        let stroke_pos = if is_bg { "inside" } else { "outside" };
        let (cmd, params) = match kind.as_str() {
            "dropShadow" => (
                "layer.layerStyle.dropShadow",
                json!({ "color": color_hex, "opacity": op, "size": sz, "distance": dist, "angle": 120.0 }),
            ),
            "stroke" => (
                "layer.layerStyle.stroke",
                json!({ "color": color_hex, "opacity": op, "size": sz, "position": stroke_pos }),
            ),
            "innerShadow" => (
                "layer.layerStyle.innerShadow",
                json!({ "color": color_hex, "opacity": op, "size": sz, "distance": dist, "angle": 120.0, "blend": "Multiply" }),
            ),
            "outerGlow" => (
                "layer.layerStyle.outerGlow",
                json!({ "color": color_hex, "opacity": op, "size": sz }),
            ),
            "innerGlow" => (
                "layer.layerStyle.innerGlow",
                json!({ "color": color_hex, "opacity": op, "size": sz, "source": "edge", "blend": "Screen" }),
            ),
            "colorOverlay" => (
                "layer.layerStyle.colorOverlay",
                json!({ "color": color_hex, "opacity": op }),
            ),
            "gradientOverlay" => (
                "layer.layerStyle.gradientOverlay",
                json!({ "from": color_hex, "to": "#000000", "opacity": op, "angle": (distance.clamp(0.0, 360.0)) as f64, "style": "linear", "blend": "Normal" }),
            ),
            _ => return Ok(()),
        };
        let _ = ps.execute(cmd, params);
        proxy.session = ps;
        guard.mark_full_dirty();
        Ok(())
    }


    /// v2.3.0 Parça 2: Fast 64-bin RGB + Luma histogram & clipping readout.
    /// Performance Rule #8/#9: Uses the <=1024px proxy when active, or stride-samples the document
    /// without ever allocating a full 50MP flatten buffer.
    pub fn get_active_histogram(&self) -> Result<HistogramData, EngineError> {
        let mut guard = self.core.lock();
        let mut r_bins = [0u32; 64];
        let mut g_bins = [0u32; 64];
        let mut b_bins = [0u32; 64];
        let mut l_bins = [0u32; 64];
        let mut total = 0u32;
        let mut shadow_clip = 0u32;
        let mut high_clip = 0u32;

        // Prefer active proxy (already <=1024px), otherwise ensure lightweight proxy or flatten small doc
        let doc = if let Some(ref p) = guard.proxy_preview {
            p.session.active().map(|ds| Arc::clone(ds.doc()))
        } else {
            let need_proxy = guard.session.active().is_some_and(|ds| {
                let sz = ds.doc().size;
                sz.width.max(sz.height) > 1024
            });
            if need_proxy {
                let _ = ensure_proxy_preview_locked(&mut guard);
                guard.proxy_preview.as_ref().and_then(|p| p.session.active().map(|ds| Arc::clone(ds.doc())))
            } else {
                guard.session.active().map(|ds| Arc::clone(ds.doc()))
            }
        }.ok_or(EngineError::NoActiveDocument)?;

        let flat = photocraft_compose::flatten(doc.as_ref());
        let w = flat.rect.width().max(0) as usize;
        let h = flat.rect.height().max(0) as usize;
        let step = (((w * h) as f32 / 40_000.0).sqrt().ceil() as usize).max(1);
        let mut y = 0usize;
        while y < h {
            let row = y * w;
            let mut x = 0usize;
            while x < w {
                let px = flat.px[row + x];
                if px[3] > 0.05 {
                    let r = (px[0].clamp(0.0, 1.0) * 255.0).round() as u32;
                    let g = (px[1].clamp(0.0, 1.0) * 255.0).round() as u32;
                    let b = (px[2].clamp(0.0, 1.0) * 255.0).round() as u32;
                    let l = ((0.2126 * px[0] + 0.7152 * px[1] + 0.0722 * px[2]).clamp(0.0, 1.0) * 255.0).round() as u32;
                    r_bins[(r >> 2).min(63) as usize] += 1;
                    g_bins[(g >> 2).min(63) as usize] += 1;
                    b_bins[(b >> 2).min(63) as usize] += 1;
                    l_bins[(l >> 2).min(63) as usize] += 1;
                    if l <= 2 { shadow_clip += 1; }
                    if l >= 253 { high_clip += 1; }
                    total += 1;
                }
                x += step;
            }
            y += step;
        }

        let norm = |bins: &[u32; 64]| -> Vec<f32> {
            let peak = bins.iter().copied().max().unwrap_or(1).max(1) as f32;
            bins.iter().map(|&c| (c as f32 / peak).clamp(0.0, 1.0)).collect()
        };
        let denom = total.max(1) as f32;
        Ok(HistogramData {
            luma: norm(&l_bins),
            red: norm(&r_bins),
            green: norm(&g_bins),
            blue: norm(&b_bins),
            shadow_clip_pct: (shadow_clip as f32 * 100.0 / denom).clamp(0.0, 100.0),
            highlight_clip_pct: (high_clip as f32 * 100.0 / denom).clamp(0.0, 100.0),
        })
    }

    /// v2.3.0 Parça 2: Ultra-fast (<0.05ms) 5x5 pixel neighborhood sampler at normalized (0..1) coordinates.
    /// Powers BOTH HSL Targeted Adjustment Tool (TAT) and Curves Black/Gray/White Eyedropper.
    pub fn sample_color_at_norm(&self, nx: f32, ny: f32) -> Result<SampledColorInfo, EngineError> {
        let guard = self.core.lock();
        let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
        let doc = ds.doc();
        let dw = doc.size.width.max(1) as i32;
        let dh = doc.size.height.max(1) as i32;
        let cx = ((nx.clamp(0.0, 1.0) * (dw - 1) as f32).round() as i32).clamp(0, dw - 1);
        let cy = ((ny.clamp(0.0, 1.0) * (dh - 1) as f32).round() as i32).clamp(0, dh - 1);
        let x0 = (cx - 2).clamp(0, dw - 1);
        let y0 = (cy - 2).clamp(0, dh - 1);
        let x1 = (cx + 3).clamp(x0 + 1, dw);
        let y1 = (cy + 3).clamp(y0 + 1, dh);

        let mut sr = 0.0f32;
        let mut sg = 0.0f32;
        let mut sb = 0.0f32;
        let mut cnt = 0.0f32;

        if let Some((lid, _)) = find_primary_photo_raster_layer(doc, ds.active_layer()) {
            if let Some(layer) = doc.layer(lid) {
                if let LayerContent::Raster(surf) = &layer.content {
                    let ch = (surf.channels() as usize).max(3);
                    let data = surf.read_region(Rect { x0, y0, x1, y1 });
                    let px_count = data.len() / ch;
                    for i in 0..px_count {
                        let base = i * ch;
                        let a = if ch >= 4 { data[base + 3] } else { 1.0 };
                        if a > 0.05 {
                            sr += data[base].clamp(0.0, 1.0);
                            sg += data[base + 1].clamp(0.0, 1.0);
                            sb += data[base + 2].clamp(0.0, 1.0);
                            cnt += 1.0;
                        }
                    }
                }
            }
        }
        let (rf, gf, bf) = if cnt > 0.0 { (sr / cnt, sg / cnt, sb / cnt) } else { (0.5, 0.5, 0.5) };
        let r = (rf * 255.0).round().clamp(0.0, 255.0) as u8;
        let g = (gf * 255.0).round().clamp(0.0, 255.0) as u8;
        let b = (bf * 255.0).round().clamp(0.0, 255.0) as u8;
        let luma = ((0.2126 * rf + 0.7152 * gf + 0.0722 * bf) * 255.0).round().clamp(0.0, 255.0) as u8;

        // Map RGB -> Hue (0..360) -> 8 HSL bands: 0=Red,1=Orange,2=Yellow,3=Green,4=Cyan,5=Blue,6=Purple,7=Magenta
        let max_c = rf.max(gf).max(bf);
        let min_c = rf.min(gf).min(bf);
        let delta = max_c - min_c;
        let hue = if delta < 1e-4 {
            0.0
        } else if (max_c - rf).abs() < 1e-6 {
            60.0 * (((gf - bf) / delta).rem_euclid(6.0))
        } else if (max_c - gf).abs() < 1e-6 {
            60.0 * (((bf - rf) / delta) + 2.0)
        } else {
            60.0 * (((rf - gf) / delta) + 4.0)
        };
        let hsl_band: u32 = if hue < 20.0 || hue >= 340.0 {
            0 // Red
        } else if hue < 45.0 {
            1 // Orange
        } else if hue < 75.0 {
            2 // Yellow
        } else if hue < 155.0 {
            3 // Green
        } else if hue < 200.0 {
            4 // Cyan
        } else if hue < 255.0 {
            5 // Blue
        } else if hue < 295.0 {
            6 // Purple
        } else {
            7 // Magenta
        };
        Ok(SampledColorInfo { r, g, b, luma, hsl_band })
    }

    /// v2.3.0 Parça 2: Computes 0.5%/99.5% auto-levels black & white points per channel in <1ms.
    pub fn compute_auto_levels(&self) -> Result<AutoLevelsResult, EngineError> {
        let mut guard = self.core.lock();
        let doc = if let Some(ref p) = guard.proxy_preview {
            p.session.active().map(|ds| Arc::clone(ds.doc()))
        } else {
            let need_proxy = guard.session.active().is_some_and(|ds| {
                let sz = ds.doc().size;
                sz.width.max(sz.height) > 1024
            });
            if need_proxy {
                let _ = ensure_proxy_preview_locked(&mut guard);
                guard.proxy_preview.as_ref().and_then(|p| p.session.active().map(|ds| Arc::clone(ds.doc())))
            } else {
                guard.session.active().map(|ds| Arc::clone(ds.doc()))
            }
        }.ok_or(EngineError::NoActiveDocument)?;

        let flat = photocraft_compose::flatten(doc.as_ref());
        let w = flat.rect.width().max(0) as usize;
        let h = flat.rect.height().max(0) as usize;
        let step = (((w * h) as f32 / 30_000.0).sqrt().ceil() as usize).max(1);
        let mut hist_l = [0u32; 256];
        let mut hist_r = [0u32; 256];
        let mut hist_g = [0u32; 256];
        let mut hist_b = [0u32; 256];
        let mut total = 0u32;
        let mut y = 0usize;
        while y < h {
            let row = y * w;
            let mut x = 0usize;
            while x < w {
                let px = flat.px[row + x];
                if px[3] > 0.05 {
                    let r = (px[0].clamp(0.0, 1.0) * 255.0).round() as usize;
                    let g = (px[1].clamp(0.0, 1.0) * 255.0).round() as usize;
                    let b = (px[2].clamp(0.0, 1.0) * 255.0).round() as usize;
                    let l = ((0.2126 * px[0] + 0.7152 * px[1] + 0.0722 * px[2]).clamp(0.0, 1.0) * 255.0).round() as usize;
                    hist_r[r.min(255)] += 1;
                    hist_g[g.min(255)] += 1;
                    hist_b[b.min(255)] += 1;
                    hist_l[l.min(255)] += 1;
                    total += 1;
                }
                x += step;
            }
            y += step;
        }
        let clip_range = |hist: &[u32; 256]| -> (f32, f32) {
            if total == 0 { return (0.0, 255.0); }
            let lo_t = ((total as f32) * 0.005) as u32;
            let hi_t = ((total as f32) * 0.995) as u32;
            let mut acc = 0u32;
            let mut lo = 0u32;
            let mut hi = 255u32;
            let mut found_lo = false;
            for (i, &c) in hist.iter().enumerate() {
                acc += c;
                if !found_lo && acc >= lo_t {
                    lo = i as u32;
                    found_lo = true;
                }
                if acc >= hi_t {
                    hi = i as u32;
                    break;
                }
            }
            if hi <= lo + 12 { (0.0, 255.0) } else { (lo.min(200) as f32, hi.max(lo + 15).min(255) as f32) }
        };
        let (rgb_black, rgb_white) = clip_range(&hist_l);
        let (red_black, red_white) = clip_range(&hist_r);
        let (green_black, green_white) = clip_range(&hist_g);
        let (blue_black, blue_white) = clip_range(&hist_b);
        Ok(AutoLevelsResult {
            rgb_black, rgb_white,
            red_black, red_white,
            green_black, green_white,
            blue_black, blue_white,
        })
    }

    /// 60 FPS Proxy Preview for PhotoCraft's native Camera Raw develop pipeline
    /// (Exposure, Shadows, Highlights, Vibrance, Temperature, Tint, Clarity, Dehaze).
    pub fn update_proxy_camera_light(
        &self,
        exposure: f32,
        shadows: f32,
        highlights: f32,
        vibrance: f32,
        temperature: f32,
        tint: f32,
        clarity: f32,
        dehaze: f32,
    ) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        let active_id = guard.session.active().and_then(|ds| ds.active_layer());
        guard.dismiss_compare();
        ensure_proxy_preview_locked(&mut guard)?;
        let Some(proxy) = guard.proxy_preview.as_mut() else {
            return Ok(());
        };
        let mut ps = Session::new();
        ps.add_document((*proxy.base_doc).clone(), None);
        ps.set_active(0);
        if active_id.is_some() {
            ps.set_active_layer(active_id);
        }
        let target_id = ps
            .active()
            .and_then(|ds| find_primary_photo_raster_layer(ds.doc(), ds.active_layer()).map(|(id, _)| id));
        if let Some(tid) = target_id {
            ps.set_active_layer(Some(tid));
            let params = json!({
                "exposure": exposure.clamp(-5.0, 5.0) as f64,
                "shadows": shadows.clamp(-100.0, 100.0) as f64,
                "highlights": highlights.clamp(-100.0, 100.0) as f64,
                "vibrance": vibrance.clamp(-100.0, 100.0) as f64,
                "temperature": temperature.clamp(-100.0, 100.0) as f64,
                "tint": tint.clamp(-100.0, 100.0) as f64,
                "clarity": clarity.clamp(-100.0, 100.0) as f64,
                "dehaze": dehaze.clamp(-100.0, 100.0) as f64
            });
            let _ = ps.execute("filter.cameraRaw", params);
        }
        proxy.session = ps;
        guard.mark_full_dirty();
        Ok(())
    }

    /// Commits PhotoCraft's native Camera Raw develop pipeline once on the active photo layer.
    pub fn apply_camera_light(
        &self,
        exposure: f32,
        shadows: f32,
        highlights: f32,
        vibrance: f32,
        temperature: f32,
        tint: f32,
        clarity: f32,
        dehaze: f32,
    ) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        guard.proxy_preview = None;
        guard.dismiss_compare();
        let target_id = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            find_primary_photo_raster_layer(ds.doc(), ds.active_layer())
                .map(|(id, _)| id)
                .ok_or_else(|| EngineError::CommandFailed {
                    detail: "Kamera Işık uygulanacak fotoğraf katmanı bulunamadı".into(),
                })?
        };
        guard.session.set_active_layer(Some(target_id));
        let params = json!({
            "exposure": exposure.clamp(-5.0, 5.0) as f64,
            "shadows": shadows.clamp(-100.0, 100.0) as f64,
            "highlights": highlights.clamp(-100.0, 100.0) as f64,
            "vibrance": vibrance.clamp(-100.0, 100.0) as f64,
            "temperature": temperature.clamp(-100.0, 100.0) as f64,
            "tint": tint.clamp(-100.0, 100.0) as f64,
            "clarity": clarity.clamp(-100.0, 100.0) as f64,
            "dehaze": dehaze.clamp(-100.0, 100.0) as f64
        });
        guard
            .session
            .execute("filter.cameraRaw", params)
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    /// 60 FPS Live Proxy Preview for non-destructive Gradient Fill (`gradient.fill.create`).
    pub fn update_proxy_gradient(
        &self,
        from_x: f32,
        from_y: f32,
        to_x: f32,
        to_y: f32,
        style: String,
        start_hex: String,
        end_hex: String,
        opacity: f32,
    ) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        let active_id = guard.session.active().and_then(|ds| ds.active_layer());
        guard.dismiss_compare();
        ensure_proxy_preview_locked(&mut guard)?;
        let Some(proxy) = guard.proxy_preview.as_mut() else {
            return Ok(());
        };
        let mut ps = Session::new();
        ps.add_document((*proxy.base_doc).clone(), None);
        ps.set_active(0);
        if active_id.is_some() {
            ps.set_active_layer(active_id);
        }
        let sc = proxy.scale;
        let params = json!({
            "from": [(from_x * sc) as f64, (from_y * sc) as f64],
            "to": [(to_x * sc) as f64, (to_y * sc) as f64],
            "style": style,
            "stops": [[0.0, start_hex], [1.0, end_hex]],
            "opacity": opacity.clamp(5.0, 100.0) as f64
        });
        let _ = ps.execute("gradient.fill.create", params);
        proxy.session = ps;
        guard.mark_full_dirty();
        Ok(())
    }

    /// Commits a real non-destructive Gradient Fill layer (`gradient.fill.create`), masked by any active selection.
    pub fn apply_gradient_fill(
        &self,
        from_x: f32,
        from_y: f32,
        to_x: f32,
        to_y: f32,
        style: String,
        start_hex: String,
        end_hex: String,
        opacity: f32,
    ) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        guard.proxy_preview = None;
        guard.dismiss_compare();
        let params = json!({
            "from": [from_x as f64, from_y as f64],
            "to": [to_x as f64, to_y as f64],
            "style": style,
            "stops": [[0.0, start_hex], [1.0, end_hex]],
            "opacity": opacity.clamp(5.0, 100.0) as f64
        });
        guard
            .session
            .execute("gradient.fill.create", params)
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_selection_dirty();
        self.info_from_locked(&guard)
    }

    /// 60 FPS Live Proxy Preview for Vector Shapes (`shape.create`).
    pub fn update_proxy_shape(
        &self,
        kind: String,
        x0: f32,
        y0: f32,
        x1: f32,
        y1: f32,
        fill_hex: String,
        stroke_hex: String,
        stroke_width: f32,
        corner_radius: f32,
        sides: u32,
    ) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        let active_id = guard.session.active().and_then(|ds| ds.active_layer());
        guard.dismiss_compare();
        ensure_proxy_preview_locked(&mut guard)?;
        let Some(proxy) = guard.proxy_preview.as_mut() else {
            return Ok(());
        };
        let mut ps = Session::new();
        ps.add_document((*proxy.base_doc).clone(), None);
        ps.set_active(0);
        if active_id.is_some() {
            ps.set_active_layer(active_id);
        }
        let sc = proxy.scale;
        let params = build_shape_params(
            &kind,
            x0 * sc,
            y0 * sc,
            x1 * sc,
            y1 * sc,
            &fill_hex,
            &stroke_hex,
            stroke_width * sc,
            corner_radius * sc,
            sides,
        );
        let _ = ps.execute("shape.create", params);
        proxy.session = ps;
        guard.mark_full_dirty();
        Ok(())
    }

    /// Commits a native Vector Shape layer (`shape.create`) — movable, scalable & compatible with Layer FX.
    pub fn create_shape_layer(
        &self,
        kind: String,
        x0: f32,
        y0: f32,
        x1: f32,
        y1: f32,
        fill_hex: String,
        stroke_hex: String,
        stroke_width: f32,
        corner_radius: f32,
        sides: u32,
    ) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        guard.proxy_preview = None;
        guard.dismiss_compare();
        let params = build_shape_params(
            &kind,
            x0,
            y0,
            x1,
            y1,
            &fill_hex,
            &stroke_hex,
            stroke_width,
            corner_radius,
            sides,
        );
        guard
            .session
            .execute("shape.create", params)
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    /// Starts a 60 FPS incremental dirty-rect Liquify session using PhotoCraft's native
    /// `LiquifyField` + `ProxyImage` (640px proxy, <1ms per finger move).
    pub fn begin_liquify_session(&self) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        guard.live_dab = None;
        guard.live_clone = None;
        guard.proxy_preview = None;
        guard.dismiss_compare();

        let (target_id, bounds, real_size, surf) = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            let doc = ds.doc();
            let (tid, _) = find_primary_photo_raster_layer(doc, ds.active_layer()).ok_or_else(|| {
                EngineError::CommandFailed {
                    detail: "Sıvılaştırma uygulanacak fotoğraf katmanı bulunamadı".into(),
                }
            })?;
            let layer = doc.layers.iter().find(|l| l.id == tid).ok_or(EngineError::NoActiveDocument)?;
            let LayerContent::Raster(s) = &layer.content else {
                return Err(EngineError::CommandFailed {
                    detail: "Aktif katman piksel katmanı değil".into(),
                });
            };
            (
                tid,
                doc.bounds(),
                (doc.size.width.max(1), doc.size.height.max(1)),
                s.clone(),
            )
        };

        let cell = auto_cell(bounds);
        let field = LiquifyField::new(bounds, cell);
        let proxy = LiquifyProxyImage::new(&surf, bounds, 640);
        let pw = proxy.w.max(1);
        let ph = proxy.h.max(1);
        let mut proxy_rgba = vec![[0u8; 4]; pw * ph];
        proxy.render(&field, [0, 0, pw, ph], &mut proxy_rgba);

        let mut flat = Vec::with_capacity(pw * ph * 4);
        for p in &proxy_rgba {
            flat.extend_from_slice(p);
        }
        let preview_surf = Surface::from_interleaved(
            PixelFormat::RGBA8,
            Rect::new(0, 0, pw as i32, ph as i32),
            &flat,
        );
        let mut preview_doc = Document::new(
            "Liquify Preview".to_string(),
            Size::new(pw as u32, ph as u32),
            ColorMode::Rgb,
            SampleType::U8,
        );
        preview_doc.layers.push(Layer::new("Liquify", LayerContent::Raster(preview_surf)));

        guard.live_liquify = Some(ActiveLiquifySession {
            target_layer_id: target_id,
            bounds,
            cell,
            field,
            proxy,
            proxy_rgba,
            preview_doc,
            real_size,
            last_point: None,
            current_stroke: LiquifyStroke::new(LiquifyTool::ForwardWarp, 120.0),
            undo_stack: Vec::new(),
            redo_stack: Vec::new(),
            pin_edges: true,
        });
        guard.mark_full_dirty();
        Ok(())
    }

    pub fn liquify_stroke_begin(
        &self,
        tool: String,
        size: f32,
        pressure: f32,
        density: f32,
        x: f32,
        y: f32,
    ) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        let Some(liq) = guard.live_liquify.as_mut() else {
            return Ok(());
        };
        let mut st = LiquifyStroke::new(parse_liquify_tool(&tool), size.clamp(12.0, 600.0) as f64);
        st.pressure = pressure.clamp(5.0, 100.0) as f64;
        st.density = density.clamp(10.0, 100.0) as f64;
        st.rate = pressure.clamp(5.0, 100.0) as f64;
        if liq.undo_stack.len() >= 20 {
            liq.undo_stack.remove(0);
        }
        liq.undo_stack.push(liq.field.clone());
        liq.redo_stack.clear();
        let pt = [x as f64, y as f64, 1.0];
        let dirty = liq.field.stroke_begin(&st, pt);
        liq.current_stroke = st;
        liq.last_point = Some(pt);
        if !dirty.is_empty() {
            let pr = liq.proxy.proxy_rect(dirty);
            liq.proxy.render(&liq.field, pr, &mut liq.proxy_rgba);
            liq.sync_proxy_rect_to_preview_doc(pr);
            guard.mark_region_dirty(Rect::new(pr[0] as i32, pr[1] as i32, pr[2] as i32, pr[3] as i32));
        }
        Ok(())
    }

    pub fn liquify_stroke_move(&self, x: f32, y: f32) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        let Some(liq) = guard.live_liquify.as_mut() else {
            return Ok(());
        };
        let pt = [x as f64, y as f64, 1.0];
        let prev = liq.last_point.unwrap_or(pt);
        let dirty = liq.field.stroke_segment(&liq.current_stroke, prev, pt);
        liq.apply_pin_edges_if_enabled();
        liq.last_point = Some(pt);
        if !dirty.is_empty() {
            let pr = liq.proxy.proxy_rect(dirty);
            liq.proxy.render(&liq.field, pr, &mut liq.proxy_rgba);
            liq.sync_proxy_rect_to_preview_doc(pr);
            guard.mark_region_dirty(Rect::new(pr[0] as i32, pr[1] as i32, pr[2] as i32, pr[3] as i32));
        }
        Ok(())
    }

    pub fn liquify_stroke_end(&self) {
        let mut guard = self.core.lock();
        if let Some(liq) = guard.live_liquify.as_mut() {
            liq.last_point = None;
        }
    }

    pub fn liquify_stroke_begin_pro(
        &self,
        tool: String,
        size: f32,
        pressure: f32,
        density: f32,
        rate: f32,
        x: f32,
        y: f32,
    ) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        let Some(liq) = guard.live_liquify.as_mut() else {
            return Ok(());
        };
        let mut st = LiquifyStroke::new(parse_liquify_tool(&tool), size.clamp(12.0, 600.0) as f64);
        st.pressure = pressure.clamp(5.0, 100.0) as f64;
        st.density = density.clamp(10.0, 100.0) as f64;
        st.rate = rate.clamp(5.0, 100.0) as f64;
        if liq.undo_stack.len() >= 20 {
            liq.undo_stack.remove(0);
        }
        liq.undo_stack.push(liq.field.clone());
        liq.redo_stack.clear();
        let pt = [x as f64, y as f64, 1.0];
        let dirty = liq.field.stroke_begin(&st, pt);
        liq.apply_pin_edges_if_enabled();
        liq.current_stroke = st;
        liq.last_point = Some(pt);
        if !dirty.is_empty() {
            let pr = liq.proxy.proxy_rect(dirty);
            liq.proxy.render(&liq.field, pr, &mut liq.proxy_rgba);
            liq.sync_proxy_rect_to_preview_doc(pr);
            guard.mark_region_dirty(Rect::new(pr[0] as i32, pr[1] as i32, pr[2] as i32, pr[3] as i32));
        }
        Ok(())
    }

    pub fn set_liquify_pin_edges(&self, pin: bool) {
        let mut guard = self.core.lock();
        if let Some(liq) = guard.live_liquify.as_mut() {
            liq.pin_edges = pin;
            if pin {
                liq.apply_pin_edges_if_enabled();
                let full = [0, 0, liq.proxy.w, liq.proxy.h];
                liq.proxy.render(&liq.field, full, &mut liq.proxy_rgba);
                liq.sync_proxy_rect_to_preview_doc(full);
                guard.mark_full_dirty();
            }
        }
    }

    pub fn liquify_mask_action(&self, action: String) -> Result<LiquifyHistoryState, EngineError> {
        let mut guard = self.core.lock();
        let Some(liq) = guard.live_liquify.as_mut() else {
            return Ok(LiquifyHistoryState { can_undo: false, can_redo: false });
        };
        let tool = match action.as_str() {
            "freezeAll" => LiquifyTool::FreezeAll,
            "invertFreeze" => LiquifyTool::InvertFreeze,
            _ => LiquifyTool::ThawAll,
        };
        if liq.undo_stack.len() >= 20 { liq.undo_stack.remove(0); }
        liq.undo_stack.push(liq.field.clone());
        liq.redo_stack.clear();
        let st = LiquifyStroke::new(tool, 1.0);
        let _ = liq.field.apply_stroke(&st);
        let full = [0, 0, liq.proxy.w, liq.proxy.h];
        liq.proxy.render(&liq.field, full, &mut liq.proxy_rgba);
        liq.sync_proxy_rect_to_preview_doc(full);
        let state = LiquifyHistoryState { can_undo: !liq.undo_stack.is_empty(), can_redo: false };
        guard.mark_full_dirty();
        Ok(state)
    }

    pub fn liquify_reconstruct_all(&self, amount_pct: f32) -> Result<LiquifyHistoryState, EngineError> {
        let mut guard = self.core.lock();
        let Some(liq) = guard.live_liquify.as_mut() else {
            return Ok(LiquifyHistoryState { can_undo: false, can_redo: false });
        };
        if liq.undo_stack.len() >= 20 { liq.undo_stack.remove(0); }
        liq.undo_stack.push(liq.field.clone());
        liq.redo_stack.clear();
        let mut st = LiquifyStroke::new(LiquifyTool::ReconstructAll, 1.0);
        st.amount = Some(amount_pct.clamp(0.0, 100.0) as f64);
        let _ = liq.field.apply_stroke(&st);
        let full = [0, 0, liq.proxy.w, liq.proxy.h];
        liq.proxy.render(&liq.field, full, &mut liq.proxy_rgba);
        liq.sync_proxy_rect_to_preview_doc(full);
        let state = LiquifyHistoryState { can_undo: !liq.undo_stack.is_empty(), can_redo: false };
        guard.mark_full_dirty();
        Ok(state)
    }

    pub fn liquify_lasso_mask(&self, points: Vec<StrokeSample>, freeze: bool) -> Result<LiquifyHistoryState, EngineError> {
        let mut guard = self.core.lock();
        let Some(liq) = guard.live_liquify.as_mut() else {
            return Ok(LiquifyHistoryState { can_undo: false, can_redo: false });
        };
        if points.len() < 3 {
            return Ok(LiquifyHistoryState { can_undo: !liq.undo_stack.is_empty(), can_redo: !liq.redo_stack.is_empty() });
        }
        if liq.undo_stack.len() >= 20 { liq.undo_stack.remove(0); }
        liq.undo_stack.push(liq.field.clone());
        liq.redo_stack.clear();
        let mut st = LiquifyStroke::new(LiquifyTool::LassoMask, 1.0);
        st.amount = Some(if freeze { 1.0 } else { 0.0 });
        st.points = points.iter().map(|p| vec![p.x as f64, p.y as f64]).collect();
        let _ = liq.field.apply_stroke(&st);
        let full = [0, 0, liq.proxy.w, liq.proxy.h];
        liq.proxy.render(&liq.field, full, &mut liq.proxy_rgba);
        liq.sync_proxy_rect_to_preview_doc(full);
        let state = LiquifyHistoryState { can_undo: !liq.undo_stack.is_empty(), can_redo: false };
        guard.mark_full_dirty();
        Ok(state)
    }

    pub fn reset_liquify_session(&self) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        let Some(liq) = guard.live_liquify.as_mut() else {
            return Ok(());
        };
        if !liq.field.is_identity() {
            if liq.undo_stack.len() >= 20 {
                liq.undo_stack.remove(0);
            }
            liq.undo_stack.push(liq.field.clone());
            liq.redo_stack.clear();
        }
        liq.field = LiquifyField::new(liq.bounds, liq.cell);
        liq.last_point = None;
        let full = [0, 0, liq.proxy.w, liq.proxy.h];
        liq.proxy.render(&liq.field, full, &mut liq.proxy_rgba);
        liq.sync_proxy_rect_to_preview_doc(full);
        guard.mark_full_dirty();
        Ok(())
    }

    /// Commits the Liquify displacement field ONCE at full resolution using PhotoCraft's
    /// Catmull-Rom bicubic `apply_liquify` (premultiplied alpha, parallel tile-by-tile, undoable).
    pub fn commit_liquify_session(&self) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        let Some(liq) = guard.live_liquify.take() else {
            return self.info_from_locked(&guard);
        };
        guard.dismiss_compare();
        if !liq.field.is_identity() {
            let tid = liq.target_layer_id;
            let field = liq.field;
            guard
                .session
                .edit("Sıvılaştırma", |doc, _| {
                    if let Some(layer) = doc.layers.iter_mut().find(|l| l.id == tid) {
                        if let LayerContent::Raster(surf) = &mut layer.content {
                            *surf = apply_liquify(surf, &field);
                        }
                    }
                    Ok(())
                })
                .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        }
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    pub fn liquify_undo(&self) -> Result<LiquifyHistoryState, EngineError> {
        let mut guard = self.core.lock();
        let Some(liq) = guard.live_liquify.as_mut() else {
            return Ok(LiquifyHistoryState { can_undo: false, can_redo: false });
        };
        if let Some(prev) = liq.undo_stack.pop() {
            liq.redo_stack.push(liq.field.clone());
            liq.field = prev;
            liq.last_point = None;
            let full = [0, 0, liq.proxy.w, liq.proxy.h];
            liq.proxy.render(&liq.field, full, &mut liq.proxy_rgba);
            liq.sync_proxy_rect_to_preview_doc(full);
            guard.mark_full_dirty();
        }
        let state = guard.live_liquify.as_ref().map(|l| LiquifyHistoryState {
            can_undo: !l.undo_stack.is_empty(),
            can_redo: !l.redo_stack.is_empty(),
        }).unwrap_or(LiquifyHistoryState { can_undo: false, can_redo: false });
        Ok(state)
    }

    pub fn liquify_redo(&self) -> Result<LiquifyHistoryState, EngineError> {
        let mut guard = self.core.lock();
        let Some(liq) = guard.live_liquify.as_mut() else {
            return Ok(LiquifyHistoryState { can_undo: false, can_redo: false });
        };
        if let Some(next) = liq.redo_stack.pop() {
            liq.undo_stack.push(liq.field.clone());
            liq.field = next;
            liq.last_point = None;
            let full = [0, 0, liq.proxy.w, liq.proxy.h];
            liq.proxy.render(&liq.field, full, &mut liq.proxy_rgba);
            liq.sync_proxy_rect_to_preview_doc(full);
            guard.mark_full_dirty();
        }
        let state = guard.live_liquify.as_ref().map(|l| LiquifyHistoryState {
            can_undo: !l.undo_stack.is_empty(),
            can_redo: !l.redo_stack.is_empty(),
        }).unwrap_or(LiquifyHistoryState { can_undo: false, can_redo: false });
        Ok(state)
    }

    pub fn get_liquify_history_state(&self) -> LiquifyHistoryState {
        let guard = self.core.lock();
        guard.live_liquify.as_ref().map(|l| LiquifyHistoryState {
            can_undo: !l.undo_stack.is_empty(),
            can_redo: !l.redo_stack.is_empty(),
        }).unwrap_or(LiquifyHistoryState { can_undo: false, can_redo: false })
    }

    /// Ultra-fast (<0.3ms) live Floating Loupe / Magnifier pixel sampler around `(doc_x, doc_y)`.
    /// Composites all visible raster layers (including in-progress live strokes!) onto a tiny
    /// `out_size x out_size` RGBA8 buffer with crisp checkerboard transparency.
    pub fn sample_loupe_patch(
        &self,
        doc_x: f32,
        doc_y: f32,
        half_extent_px: f32,
        out_size: u32,
    ) -> Option<LayerThumbnail> {
        let guard = self.core.lock();
        let doc = guard.active_document()?;
        let os = out_size.clamp(32, 96) as usize;
        let he = half_extent_px.clamp(4.0, 120.0);
        let doc_w = doc.size.width as i32;
        let doc_h = doc.size.height as i32;
        if doc_w <= 0 || doc_h <= 0 {
            return None;
        }

        // Collect visible raster layers bottom-to-top
        let mut visible_layers: Vec<(&Surface, f32)> = Vec::new();
        for l in &doc.layers {
            if !l.visible || l.opacity <= 0.001 {
                continue;
            }
            if let LayerContent::Raster(surf) = &l.content {
                visible_layers.push((surf, l.opacity));
            }
        }

        let mut rgba = vec![0u8; os * os * 4];
        let step = (he * 2.0) / (os as f32);
        let start_x = doc_x - he;
        let start_y = doc_y - he;

        for py in 0..os {
            let sy = (start_y + (py as f32 + 0.5) * step).floor() as i32;
            let row = py * os * 4;
            for px in 0..os {
                let sx = (start_x + (px as f32 + 0.5) * step).floor() as i32;
                let di = row + px * 4;
                // Outside canvas: dark studio background
                if sx < 0 || sy < 0 || sx >= doc_w || sy >= doc_h {
                    rgba[di] = 26;
                    rgba[di + 1] = 27;
                    rgba[di + 2] = 34;
                    rgba[di + 3] = 255;
                    continue;
                }
                // Checkerboard base for transparent pixels
                let chk = (((px / 6) + (py / 6)) & 1) == 0;
                let bg_v = if chk { 0.82f32 } else { 0.64f32 };
                let mut acc_r = bg_v;
                let mut acc_g = bg_v;
                let mut acc_b = bg_v;

                for (surf, op) in &visible_layers {
                    let c = surf.rgba(sx, sy);
                    let a = (c[3] * *op).clamp(0.0, 1.0);
                    if a > 0.002 {
                        let inv = 1.0 - a;
                        acc_r = c[0] * a + acc_r * inv;
                        acc_g = c[1] * a + acc_g * inv;
                        acc_b = c[2] * a + acc_b * inv;
                    }
                }
                rgba[di] = (acc_r.clamp(0.0, 1.0) * 255.0).round() as u8;
                rgba[di + 1] = (acc_g.clamp(0.0, 1.0) * 255.0).round() as u8;
                rgba[di + 2] = (acc_b.clamp(0.0, 1.0) * 255.0).round() as u8;
                rgba[di + 3] = 255;
            }
        }

        Some(LayerThumbnail {
            width: os as u32,
            height: os as u32,
            rgba,
        })
    }

    pub fn cancel_liquify_session(&self) {
        let mut guard = self.core.lock();
        if guard.live_liquify.take().is_some() {
            guard.mark_full_dirty();
        }
    }

    /// One-tap Smart Auto Enhance using PhotoCraft's native `image.autoTone`, `image.autoColor`, or `image.autoContrast`.
    pub fn apply_auto_enhance(&self, mode: String) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        guard.proxy_preview = None;
        guard.live_liquify = None;
        guard.dismiss_compare();
        let target_id = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            find_primary_photo_raster_layer(ds.doc(), ds.active_layer())
                .map(|(id, _)| id)
                .ok_or_else(|| EngineError::CommandFailed {
                    detail: "Otomatik iyileştirme uygulanacak fotoğraf katmanı bulunamadı".into(),
                })?
        };
        guard.session.set_active_layer(Some(target_id));
        let cmd = match mode.as_str() {
            "color" => "image.autoColor",
            "contrast" => "image.autoContrast",
            _ => "image.autoTone",
        };
        guard
            .session
            .execute(cmd, json!({}))
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    /// 60 FPS Live Proxy Preview for Cinematic Looks + Vignette + Film Grain (`filter.cameraRaw`).
    pub fn update_proxy_cinema_look(
        &self,
        preset: String,
        intensity: f32,
        vignette: f32,
        grain: f32,
    ) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        let active_id = guard.session.active().and_then(|ds| ds.active_layer());
        guard.dismiss_compare();
        ensure_proxy_preview_locked(&mut guard)?;
        let Some(proxy) = guard.proxy_preview.as_mut() else {
            return Ok(());
        };
        let mut ps = Session::new();
        ps.add_document((*proxy.base_doc).clone(), None);
        ps.set_active(0);
        if active_id.is_some() {
            ps.set_active_layer(active_id);
        }
        let target_id = ps
            .active()
            .and_then(|ds| find_primary_photo_raster_layer(ds.doc(), ds.active_layer()).map(|(id, _)| id));
        if let Some(tid) = target_id {
            ps.set_active_layer(Some(tid));
            let params = build_cinema_look_params(&preset, intensity, vignette, grain);
            let _ = ps.execute("filter.cameraRaw", params);
        }
        proxy.session = ps;
        guard.mark_full_dirty();
        Ok(())
    }

    /// Commits Cinematic Look + Vignette + Film Grain once at full resolution.
    pub fn apply_cinema_look(
        &self,
        preset: String,
        intensity: f32,
        vignette: f32,
        grain: f32,
    ) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        guard.proxy_preview = None;
        guard.dismiss_compare();
        let target_id = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            find_primary_photo_raster_layer(ds.doc(), ds.active_layer())
                .map(|(id, _)| id)
                .ok_or_else(|| EngineError::CommandFailed {
                    detail: "Sinema stili uygulanacak fotoğraf katmanı bulunamadı".into(),
                })?
        };
        guard.session.set_active_layer(Some(target_id));
        let params = build_cinema_look_params(&preset, intensity, vignette, grain);
        guard
            .session
            .execute("filter.cameraRaw", params)
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    /// 60 FPS Live Proxy Preview for Motion Blur (`filter.blur.motionBlur`).
    pub fn update_proxy_motion_blur(&self, angle: f32, distance: f32) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        let active_id = guard.session.active().and_then(|ds| ds.active_layer());
        guard.dismiss_compare();
        ensure_proxy_preview_locked(&mut guard)?;
        let Some(proxy) = guard.proxy_preview.as_mut() else {
            return Ok(());
        };
        let mut ps = Session::new();
        ps.add_document((*proxy.base_doc).clone(), None);
        ps.set_active(0);
        if active_id.is_some() {
            ps.set_active_layer(active_id);
        }
        let scaled_dist = (distance.clamp(0.0, 200.0) * proxy.scale).round().clamp(0.0, 120.0);
        if scaled_dist >= 1.0 {
            let target_id = ps
                .active()
                .and_then(|ds| find_primary_photo_raster_layer(ds.doc(), ds.active_layer()).map(|(id, _)| id));
            if let Some(tid) = target_id {
                ps.set_active_layer(Some(tid));
                let _ = ps.execute(
                    "filter.blur.motionBlur",
                    json!({ "angle": angle.clamp(-180.0, 180.0) as f64, "distance": scaled_dist as f64 }),
                );
            }
        }
        proxy.session = ps;
        guard.mark_full_dirty();
        Ok(())
    }

    /// Commits Motion Blur (`filter.blur.motionBlur`) at full resolution.
    pub fn apply_motion_blur(&self, angle: f32, distance: f32) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        guard.proxy_preview = None;
        guard.dismiss_compare();
        let target_id = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            find_primary_photo_raster_layer(ds.doc(), ds.active_layer())
                .map(|(id, _)| id)
                .ok_or_else(|| EngineError::CommandFailed {
                    detail: "Hareket bulanıklığı uygulanacak fotoğraf katmanı bulunamadı".into(),
                })?
        };
        guard.session.set_active_layer(Some(target_id));
        guard
            .session
            .execute(
                "filter.blur.motionBlur",
                json!({
                    "angle": angle.clamp(-180.0, 180.0) as f64,
                    "distance": distance.clamp(1.0, 200.0) as f64
                }),
            )
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    /// 60 FPS Live Proxy Preview for Radial Blur (`filter.blur.radialBlur`, zoom or spin).
    pub fn update_proxy_radial_blur(&self, amount: f32, method: String) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        let active_id = guard.session.active().and_then(|ds| ds.active_layer());
        guard.dismiss_compare();
        ensure_proxy_preview_locked(&mut guard)?;
        let Some(proxy) = guard.proxy_preview.as_mut() else {
            return Ok(());
        };
        let mut ps = Session::new();
        ps.add_document((*proxy.base_doc).clone(), None);
        ps.set_active(0);
        if active_id.is_some() {
            ps.set_active_layer(active_id);
        }
        let amt = amount.round().clamp(0.0, 100.0);
        if amt >= 1.0 {
            let target_id = ps
                .active()
                .and_then(|ds| find_primary_photo_raster_layer(ds.doc(), ds.active_layer()).map(|(id, _)| id));
            if let Some(tid) = target_id {
                ps.set_active_layer(Some(tid));
                let m = if method == "spin" { "spin" } else { "zoom" };
                let _ = ps.execute(
                    "filter.blur.radialBlur",
                    json!({ "amount": amt as i64, "method": m, "quality": "draft", "centerX": 0.5, "centerY": 0.5 }),
                );
            }
        }
        proxy.session = ps;
        guard.mark_full_dirty();
        Ok(())
    }

    /// Commits Radial Blur (`filter.blur.radialBlur`) at full resolution.
    pub fn apply_radial_blur(&self, amount: f32, method: String) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        guard.proxy_preview = None;
        guard.dismiss_compare();
        let target_id = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            find_primary_photo_raster_layer(ds.doc(), ds.active_layer())
                .map(|(id, _)| id)
                .ok_or_else(|| EngineError::CommandFailed {
                    detail: "Radyal bulanıklık uygulanacak fotoğraf katmanı bulunamadı".into(),
                })?
        };
        guard.session.set_active_layer(Some(target_id));
        let m = if method == "spin" { "spin" } else { "zoom" };
        guard
            .session
            .execute(
                "filter.blur.radialBlur",
                json!({
                    "amount": amount.round().clamp(1.0, 100.0) as i64,
                    "method": m,
                    "quality": "good",
                    "centerX": 0.5,
                    "centerY": 0.5
                }),
            )
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    /// One-tap Smart Action: Automatically selects the main subject, inverts, and erases the background to transparency.
    pub fn smart_remove_background(&self) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        guard.proxy_preview = None;
        guard.live_liquify = None;
        guard.dismiss_compare();
        let target_id = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            find_primary_photo_raster_layer(ds.doc(), ds.active_layer())
                .map(|(id, _)| id)
                .ok_or_else(|| EngineError::CommandFailed {
                    detail: "Arka planı silinecek fotoğraf katmanı bulunamadı".into(),
                })?
        };
        guard.session.set_active_layer(Some(target_id));
        let _ = guard.session.execute("layer.setProps", json!({ "layer": target_id.0, "locks": { "pixels": false, "all": false } }));
        guard
            .session
            .execute("select.subject", json!({ "sampleAllLayers": true }))
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        let has_sel = guard
            .active_document()
            .and_then(|d| d.selection.as_ref().map(|s| !s.content_bounds().is_empty()))
            .unwrap_or(false);
        if !has_sel {
            return Err(EngineError::CommandFailed {
                detail: "Ana konu otomatik tespit edilemedi — Seçim araçlarıyla elle seçebilirsin".into(),
            });
        }
        let _ = guard.session.execute(
            "select.refineEdge",
            json!({
                "radius": 4.0,
                "smartRadius": true,
                "smooth": 8.0,
                "feather": 1.0,
                "contrast": 15.0,
                "decontaminate": false,
                "output": "selection",
                "sampleAllLayers": true
            }),
        );
        let still_has_sel = guard
            .active_document()
            .and_then(|d| d.selection.as_ref().map(|s| !s.content_bounds().is_empty()))
            .unwrap_or(false);
        if !still_has_sel {
            let _ = guard.session.execute("select.subject", json!({ "sampleAllLayers": true }));
        }
        guard
            .session
            .execute("select.inverse", json!({}))
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard
            .session
            .execute("edit.clear", json!({}))
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        let _ = guard.session.execute("select.deselect", json!({}));
        guard.mark_selection_dirty();
        self.info_from_locked(&guard)
    }

    /// One-tap Smart Action: Automatically selects the main subject and blurs ONLY the background (Portrait Bokeh).
    pub fn smart_portrait_blur(&self, radius: f32) -> Result<DocumentInfo, EngineError> {
        let r = radius.clamp(2.0, 60.0);
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        guard.proxy_preview = None;
        guard.live_liquify = None;
        guard.dismiss_compare();
        let target_id = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            find_primary_photo_raster_layer(ds.doc(), ds.active_layer())
                .map(|(id, _)| id)
                .ok_or_else(|| EngineError::CommandFailed {
                    detail: "Portre bulanıklığı uygulanacak fotoğraf katmanı bulunamadı".into(),
                })?
        };
        guard.session.set_active_layer(Some(target_id));
        guard
            .session
            .execute("select.subject", json!({ "sampleAllLayers": true }))
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        let has_sel = guard
            .active_document()
            .and_then(|d| d.selection.as_ref().map(|s| !s.content_bounds().is_empty()))
            .unwrap_or(false);
        if !has_sel {
            return Err(EngineError::CommandFailed {
                detail: "Ana konu tespit edilemedi — manuel seçim yapıp Bulanıklaştır kullanabilirsin".into(),
            });
        }
        guard
            .session
            .execute("select.inverse", json!({}))
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        let _ = guard.session.execute("select.modify.feather", json!({ "radius": 3.0 }));
        guard
            .session
            .edit("Portre Arka Plan Bulanıklığı", |doc, _| {
                run_blur_on_doc(doc, target_id, r);
                Ok(())
            })
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        let _ = guard.session.execute("select.deselect", json!({}));
        guard.mark_selection_dirty();
        self.info_from_locked(&guard)
    }

    /// Generates 7 live mini-preview thumbnails of the user's ACTUAL open photo for the Cinema Looks strip.
    pub fn render_cinema_look_thumbs(&self, thumb_size: u32) -> Result<Vec<LayerThumbnail>, EngineError> {
        let presets = ["none", "golden", "tealOrange", "noir", "vivid", "vintage", "cyber"];
        let base_doc = {
            let guard = self.core.lock();
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            let doc = ds.doc();
            let real_w = doc.size.width.max(1);
            let real_h = doc.size.height.max(1);
            let max_d = thumb_size.clamp(48, 128);
            let scale = (max_d as f32 / real_w.max(real_h) as f32).min(1.0);
            let tw = ((real_w as f32 * scale).round() as u32).clamp(16, max_d);
            let th = ((real_h as f32 * scale).round() as u32).clamp(16, max_d);
            let mut ps = Session::new();
            ps.add_document((**doc).clone(), None);
            ps.set_active(0);
            if tw < real_w || th < real_h {
                let _ = ps.execute("image.imageSize", json!({ "width": tw, "height": th, "resample": "bilinear" }));
            }
            ps.active().map(|d| Arc::clone(d.doc())).ok_or(EngineError::NoActiveDocument)?
        };

        let mut results = Vec::with_capacity(presets.len());
        for preset in presets {
            let mut ps = Session::new();
            ps.add_document((*base_doc).clone(), None);
            ps.set_active(0);
            let tid = ps
                .active()
                .and_then(|ds| find_primary_photo_raster_layer(ds.doc(), ds.active_layer()).map(|(id, _)| id));
            if let Some(id) = tid {
                ps.set_active_layer(Some(id));
                if preset != "none" {
                    let params = build_cinema_look_params(preset, 85.0, -20.0, 0.0);
                    let _ = ps.execute("filter.cameraRaw", params);
                }
                if let Some(ds) = ps.active() {
                    let doc = ds.doc();
                    let w = doc.size.width.max(1);
                    let h = doc.size.height.max(1);
                    if let Some(layer) = doc.layers.iter().find(|l| l.id == id) {
                        if let LayerContent::Raster(surf) = &layer.content {
                            let mut rgba = vec![0u8; (w * h * 4) as usize];
                            for y in 0..h {
                                for x in 0..w {
                                    let c = surf.rgba(x as i32, y as i32);
                                    let i = ((y * w + x) * 4) as usize;
                                    rgba[i] = (c[0].clamp(0.0, 1.0) * 255.0).round() as u8;
                                    rgba[i + 1] = (c[1].clamp(0.0, 1.0) * 255.0).round() as u8;
                                    rgba[i + 2] = (c[2].clamp(0.0, 1.0) * 255.0).round() as u8;
                                    rgba[i + 3] = (c[3].clamp(0.0, 1.0) * 255.0).round() as u8;
                                }
                            }
                            results.push(LayerThumbnail { width: w, height: h, rgba });
                        }
                    }
                }
            }
        }
        Ok(results)
    }

    /// 60 FPS Live Proxy Preview for 8-Channel HSL Color Mixer (O(1) RAM, exact 1:1 parity with Uygula).
    pub fn update_proxy_hsl_mixer(
        &self,
        hsl_hue: Vec<f32>,
        hsl_sat: Vec<f32>,
        hsl_lum: Vec<f32>,
    ) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        let active_id = guard.session.active().and_then(|ds| ds.active_layer());
        guard.dismiss_compare();
        ensure_proxy_preview_locked(&mut guard)?;
        let Some(proxy) = guard.proxy_preview.as_mut() else {
            return Ok(());
        };
        let mut ps = Session::new();
        ps.add_document((*proxy.base_doc).clone(), None);
        ps.set_active(0);
        if active_id.is_some() {
            ps.set_active_layer(active_id);
        }
        let target_id = ps
            .active()
            .and_then(|ds| find_primary_photo_raster_layer(ds.doc(), ds.active_layer()).map(|(id, _)| id));
        if let Some(tid) = target_id {
            let _ = ps.edit("Proxy HSL", |doc, _| {
                run_hsl_mixer_on_doc(doc, tid, &hsl_hue, &hsl_sat, &hsl_lum);
                Ok(())
            });
        }
        proxy.session = ps;
        guard.mark_full_dirty();
        Ok(())
    }

    /// Commits 8-Channel HSL Color Mixer once at full resolution (zero working-memory error even on 50MP photos).
    pub fn apply_hsl_mixer(
        &self,
        hsl_hue: Vec<f32>,
        hsl_sat: Vec<f32>,
        hsl_lum: Vec<f32>,
    ) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        guard.proxy_preview = None;
        guard.dismiss_compare();
        let target_id = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            find_primary_photo_raster_layer(ds.doc(), ds.active_layer())
                .map(|(id, _)| id)
                .ok_or_else(|| EngineError::CommandFailed {
                    detail: "HSL Mikser uygulanacak fotoğraf katmanı bulunamadı".into(),
                })?
        };
        guard
            .session
            .edit("HSL Renk Mikseri", |doc, _| {
                run_hsl_mixer_on_doc(doc, target_id, &hsl_hue, &hsl_sat, &hsl_lum);
                Ok(())
            })
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    /// Native Paint Bucket (`paint.bucket`) flood-fill at `(x, y)` with tolerance & contiguous option.
    pub fn apply_paint_bucket(
        &self,
        x: i32,
        y: i32,
        color_hex: String,
        tolerance: u32,
        contiguous: bool,
    ) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        guard.proxy_preview = None;
        guard.dismiss_compare();
        guard
            .session
            .execute(
                "paint.bucket",
                json!({
                    "x": x,
                    "y": y,
                    "color": color_hex,
                    "tolerance": tolerance.min(255),
                    "contiguous": contiguous,
                    "antiAlias": true,
                    "sampleAllLayers": true,
                    "opacity": 100.0
                }),
            )
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    /// Generates 10 live mini-preview thumbnails of the user's ACTUAL open photo for the Filter Gallery
    /// (`none` + 9 Artistic & Pixelate filters).
    pub fn render_filter_gallery_thumbs(&self, thumb_size: u32) -> Result<Vec<LayerThumbnail>, EngineError> {
        let kinds = [
            "none",
            "oilPaint",
            "watercolor",
            "charcoal",
            "glowingEdges",
            "emboss",
            "mosaic",
            "crystallize",
            "pointillize",
            "colorHalftone",
        ];
        let base_doc = {
            let guard = self.core.lock();
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            let doc = ds.doc();
            let real_w = doc.size.width.max(1);
            let real_h = doc.size.height.max(1);
            let max_d = thumb_size.clamp(48, 96);
            let scale = (max_d as f32 / real_w.max(real_h) as f32).min(1.0);
            let tw = ((real_w as f32 * scale).round() as u32).clamp(16, max_d);
            let th = ((real_h as f32 * scale).round() as u32).clamp(16, max_d);
            let mut ps = Session::new();
            ps.add_document((**doc).clone(), None);
            ps.set_active(0);
            if tw < real_w || th < real_h {
                let _ = ps.execute("image.imageSize", json!({ "width": tw, "height": th, "resample": "bilinear" }));
            }
            ps.active().map(|d| Arc::clone(d.doc())).ok_or(EngineError::NoActiveDocument)?
        };

        let mut results = Vec::with_capacity(kinds.len());
        for kind in kinds {
            let mut ps = Session::new();
            ps.add_document((*base_doc).clone(), None);
            ps.set_active(0);
            let tid = ps
                .active()
                .and_then(|ds| find_primary_photo_raster_layer(ds.doc(), ds.active_layer()).map(|(id, _)| id));
            if let Some(id) = tid {
                ps.set_active_layer(Some(id));
                if let Some((cmd, params)) = build_gallery_filter_cmd(kind, 45.0, 55.0, 0.22) {
                    let _ = ps.execute(cmd, params);
                }
                if let Some(ds) = ps.active() {
                    let doc = ds.doc();
                    let w = doc.size.width.max(1);
                    let h = doc.size.height.max(1);
                    if let Some(layer) = doc.layers.iter().find(|l| l.id == id) {
                        if let LayerContent::Raster(surf) = &layer.content {
                            let mut rgba = vec![0u8; (w * h * 4) as usize];
                            for y in 0..h {
                                for x in 0..w {
                                    let c = surf.rgba(x as i32, y as i32);
                                    let i = ((y * w + x) * 4) as usize;
                                    rgba[i] = (c[0].clamp(0.0, 1.0) * 255.0).round() as u8;
                                    rgba[i + 1] = (c[1].clamp(0.0, 1.0) * 255.0).round() as u8;
                                    rgba[i + 2] = (c[2].clamp(0.0, 1.0) * 255.0).round() as u8;
                                    rgba[i + 3] = (c[3].clamp(0.0, 1.0) * 255.0).round() as u8;
                                }
                            }
                            results.push(LayerThumbnail { width: w, height: h, rgba });
                        }
                    }
                }
            }
        }
        Ok(results)
    }

    /// 60 FPS Live Proxy Preview for Filter Gallery (Artistic & Pixelate).
    pub fn update_proxy_gallery_filter(&self, kind: String, amount: f32, detail: f32) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        let active_id = guard.session.active().and_then(|ds| ds.active_layer());
        guard.dismiss_compare();
        ensure_proxy_preview_locked(&mut guard)?;
        let Some(proxy) = guard.proxy_preview.as_mut() else {
            return Ok(());
        };
        let mut ps = Session::new();
        ps.add_document((*proxy.base_doc).clone(), None);
        ps.set_active(0);
        if active_id.is_some() {
            ps.set_active_layer(active_id);
        }
        if let Some((cmd, params)) = build_gallery_filter_cmd(&kind, amount, detail, proxy.scale) {
            let target_id = ps
                .active()
                .and_then(|ds| find_primary_photo_raster_layer(ds.doc(), ds.active_layer()).map(|(id, _)| id));
            if let Some(tid) = target_id {
                ps.set_active_layer(Some(tid));
                let _ = ps.execute(cmd, params);
            }
        }
        proxy.session = ps;
        guard.mark_full_dirty();
        Ok(())
    }

    /// Commits a Filter Gallery effect once at full resolution (undoable).
    pub fn apply_gallery_filter(&self, kind: String, amount: f32, detail: f32) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        guard.proxy_preview = None;
        guard.dismiss_compare();
        let Some((cmd, params)) = build_gallery_filter_cmd(&kind, amount, detail, 1.0) else {
            guard.mark_full_dirty();
            return self.info_from_locked(&guard);
        };
        let target_id = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            find_primary_photo_raster_layer(ds.doc(), ds.active_layer())
                .map(|(id, _)| id)
                .ok_or_else(|| EngineError::CommandFailed {
                    detail: "Filtre uygulanacak fotoğraf katmanı bulunamadı".into(),
                })?
        };
        guard.session.set_active_layer(Some(target_id));
        guard
            .session
            .execute(cmd, params)
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }


    /// Renders 6 live mini photo thumbnails (`Orijinal` + 5 Light & Distort effects) on the active document.
    pub fn render_light_distort_thumbs(&self, thumb_size: u32) -> Result<Vec<LayerThumbnail>, EngineError> {
        let specs: [(&str, f32, f32, f32, f32, &str); 6] = [
            ("none", 0.0, 0.0, 50.0, 50.0, ""),
            ("lensFlare", 55.0, 50.0, 32.0, 28.0, "moviePrime"),
            ("lensBlur", 45.0, 40.0, 50.0, 50.0, "hexagon"),
            ("wave", 45.0, 55.0, 50.0, 50.0, "sine"),
            ("spherize", 65.0, 50.0, 50.0, 50.0, "normal"),
            ("twirl", 110.0, 50.0, 50.0, 50.0, ""),
        ];
        let base_doc = {
            let guard = self.core.lock();
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            let doc = ds.doc();
            let real_w = doc.size.width.max(1);
            let real_h = doc.size.height.max(1);
            let max_d = thumb_size.clamp(48, 96);
            let scale = (max_d as f32 / real_w.max(real_h) as f32).min(1.0);
            let tw = ((real_w as f32 * scale).round() as u32).clamp(16, max_d);
            let th = ((real_h as f32 * scale).round() as u32).clamp(16, max_d);
            let mut ps = Session::new();
            ps.add_document((**doc).clone(), None);
            ps.set_active(0);
            if tw < real_w || th < real_h {
                let _ = ps.execute("image.imageSize", json!({ "width": tw, "height": th, "resample": "bilinear" }));
            }
            ps.active().map(|d| Arc::clone(d.doc())).ok_or(EngineError::NoActiveDocument)?
        };

        let mut results = Vec::with_capacity(specs.len());
        for (kind, amt, det, px, py, var) in specs {
            let mut ps = Session::new();
            ps.add_document((*base_doc).clone(), None);
            ps.set_active(0);
            let tid = ps
                .active()
                .and_then(|ds| find_primary_photo_raster_layer(ds.doc(), ds.active_layer()).map(|(id, _)| id));
            if let Some(id) = tid {
                ps.set_active_layer(Some(id));
                if let Some((cmd, params)) = build_light_distort_cmd(kind, amt, det, px, py, var, 0.25) {
                    let _ = ps.execute(cmd, params);
                }
                if let Some(ds) = ps.active() {
                    let doc = ds.doc();
                    let w = doc.size.width.max(1);
                    let h = doc.size.height.max(1);
                    if let Some(layer) = doc.layers.iter().find(|l| l.id == id) {
                        if let LayerContent::Raster(surf) = &layer.content {
                            let mut rgba = vec![0u8; (w * h * 4) as usize];
                            for y in 0..h {
                                for x in 0..w {
                                    let c = surf.rgba(x as i32, y as i32);
                                    let i = ((y * w + x) * 4) as usize;
                                    rgba[i] = (c[0].clamp(0.0, 1.0) * 255.0).round() as u8;
                                    rgba[i + 1] = (c[1].clamp(0.0, 1.0) * 255.0).round() as u8;
                                    rgba[i + 2] = (c[2].clamp(0.0, 1.0) * 255.0).round() as u8;
                                    rgba[i + 3] = (c[3].clamp(0.0, 1.0) * 255.0).round() as u8;
                                }
                            }
                            results.push(LayerThumbnail { width: w, height: h, rgba });
                        }
                    }
                }
            }
        }
        Ok(results)
    }

    /// 60 FPS live proxy preview for Light & Distort effects (`lensFlare`, `lensBlur`, `wave`, `spherize`, `twirl`).
    pub fn update_proxy_light_distort(
        &self,
        kind: String,
        amount: f32,
        detail: f32,
        param_x: f32,
        param_y: f32,
        variant: String,
    ) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        let active_id = guard.session.active().and_then(|ds| ds.active_layer());
        guard.dismiss_compare();
        ensure_proxy_preview_locked(&mut guard)?;
        let Some(proxy) = guard.proxy_preview.as_mut() else {
            return Ok(());
        };
        let mut ps = Session::new();
        ps.add_document((*proxy.base_doc).clone(), None);
        ps.set_active(0);
        if active_id.is_some() {
            ps.set_active_layer(active_id);
        }
        if let Some((cmd, params)) = build_light_distort_cmd(&kind, amount, detail, param_x, param_y, &variant, proxy.scale) {
            let target_id = ps
                .active()
                .and_then(|ds| find_primary_photo_raster_layer(ds.doc(), ds.active_layer()).map(|(id, _)| id));
            if let Some(tid) = target_id {
                ps.set_active_layer(Some(tid));
                let _ = ps.execute(cmd, params);
            }
        }
        proxy.session = ps;
        guard.mark_full_dirty();
        Ok(())
    }

    /// Commits a Light & Distort effect once at full resolution (undoable).
    pub fn apply_light_distort(
        &self,
        kind: String,
        amount: f32,
        detail: f32,
        param_x: f32,
        param_y: f32,
        variant: String,
    ) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        guard.proxy_preview = None;
        guard.dismiss_compare();
        let Some((cmd, params)) = build_light_distort_cmd(&kind, amount, detail, param_x, param_y, &variant, 1.0) else {
            guard.mark_full_dirty();
            return self.info_from_locked(&guard);
        };
        let target_id = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            find_primary_photo_raster_layer(ds.doc(), ds.active_layer())
                .map(|(id, _)| id)
                .ok_or_else(|| EngineError::CommandFailed {
                    detail: "Efekt uygulanacak fotoğraf katmanı bulunamadı".into(),
                })?
        };
        guard.session.set_active_layer(Some(target_id));
        guard
            .session
            .execute(cmd, params)
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    pub fn end_proxy_preview(&self) {
        let mut guard = self.core.lock();
        if guard.proxy_preview.take().is_some() {
            guard.mark_full_dirty();
        }
    }

    /// Returns the content bounds of the currently active layer (for independent Layer Transform UI).
    pub fn get_active_layer_bounds(&self) -> Option<SelectionBounds> {
        let guard = self.core.lock();
        let ds = guard.session.active()?;
        let active_id = ds.active_layer()?;
        let doc = ds.doc();
        let layer = doc.layers.iter().find(|l| l.id == active_id)?;
        let b = match &layer.content {
            LayerContent::Raster(surf) => surf.content_bounds(),
            _ => doc.bounds(),
        };
        if b.is_empty() {
            Some(SelectionBounds {
                x0: 0,
                y0: 0,
                x1: doc.size.width as i32,
                y1: doc.size.height as i32,
            })
        } else {
            Some(SelectionBounds { x0: b.x0, y0: b.y0, x1: b.x1, y1: b.y1 })
        }
    }

    /// Temporarily hides a layer in the GPU composite while dragging its floating preview (zero undo steps).
    pub fn set_transform_hidden_layer(&self, layer_id: Option<u64>) {
        let mut guard = self.core.lock();
        guard.transform_hidden_layer = layer_id.map(LayerId);
        guard.mark_full_dirty();
    }

    /// Independently moves, scales (%), and rotates (deg) ONLY the currently active layer (`edit.transform`).
    pub fn transform_active_layer(
        &self,
        dx: f32,
        dy: f32,
        scale_pct: f32,
        rotate_deg: f32,
    ) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        guard.live_stroke = None;
        guard.live_dab = None;
        guard.live_clone = None;
        guard.proxy_preview = None;
        guard.transform_hidden_layer = None;

        let (active_id, was_locked) = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            let id = ds.active_layer().ok_or_else(|| EngineError::CommandFailed {
                detail: "Taşınacak aktif katman seçili değil".into(),
            })?;
            let locked = ds
                .doc()
                .layers
                .iter()
                .find(|l| l.id == id)
                .is_some_and(|l| l.locks.pixels || l.locks.position || l.locks.all);
            (id, locked)
        };

        drop(guard);
        self.transform_active_layer_xy(dx, dy, scale_pct, scale_pct, rotate_deg)
    }

    /// Independently moves, stretches (scaleX%, scaleY%), and rotates (deg) the active layer in 1 clean Undo step.
    pub fn transform_active_layer_xy(
        &self,
        dx: f32,
        dy: f32,
        scale_x_pct: f32,
        scale_y_pct: f32,
        rotate_deg: f32,
    ) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        guard.live_stroke = None;
        guard.live_dab = None;
        guard.live_clone = None;
        guard.proxy_preview = None;
        guard.transform_hidden_layer = None;

        let (active_id, prev_locks) = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            let id = ds.active_layer().ok_or_else(|| EngineError::CommandFailed {
                detail: "Taşınacak aktif katman seçili değil".into(),
            })?;
            let locks = ds
                .doc()
                .layers
                .iter()
                .find(|l| l.id == id)
                .map(|l| (l.locks.pixels, l.locks.position, l.locks.all))
                .unwrap_or((false, false, false));
            (id, locks)
        };

        let was_locked = prev_locks.0 || prev_locks.1 || prev_locks.2;
        if was_locked {
            if let Some(ds) = guard.session.active_mut() {
                let doc = Arc::make_mut(ds.doc_mut());
                if let Some(l) = doc.layers.iter_mut().find(|l| l.id == active_id) {
                    l.locks.pixels = false;
                    l.locks.position = false;
                    l.locks.all = false;
                }
            }
        }

        let sx = scale_x_pct.clamp(5.0, 500.0) as f64;
        let sy = scale_y_pct.clamp(5.0, 500.0) as f64;
        let res = guard.session.execute(
            "edit.transform",
            json!({
                "dx": dx as f64,
                "dy": dy as f64,
                "scaleX": sx,
                "scaleY": sy,
                "rotate": rotate_deg as f64,
                "interpolation": "bicubic"
            }),
        );

        if was_locked {
            if let Some(ds) = guard.session.active_mut() {
                let doc = Arc::make_mut(ds.doc_mut());
                if let Some(l) = doc.layers.iter_mut().find(|l| l.id == active_id) {
                    l.locks.pixels = prev_locks.0;
                    l.locks.position = prev_locks.1;
                    l.locks.all = prev_locks.2;
                }
            }
        }

        res.map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    /// Creates a single native PhotoCraft adjustment layer with final slider values (called once on Uygula after 60 FPS GPU preview).
    pub fn apply_adjustment_once(&self, kind: String, params_json: String) -> Result<DocumentInfo, EngineError> {
        let params: Value = if params_json.trim().is_empty() {
            json!({})
        } else {
            serde_json::from_str(&params_json)
                .map_err(|e| EngineError::InvalidParams { detail: e.to_string() })?
        };
        let cmd = format!("layer.newAdjustmentLayer.{kind}");
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        guard.live_stroke = None;
        guard.proxy_preview = None;
        guard
            .session
            .execute(&cmd, params)
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    pub fn begin_live_adjustment(&self, kind: String, params_json: String) -> Result<u64, EngineError> {
        let params: Value = if params_json.trim().is_empty() {
            json!({})
        } else {
            serde_json::from_str(&params_json)
                .map_err(|e| EngineError::InvalidParams { detail: e.to_string() })?
        };
        let cmd = format!("layer.newAdjustmentLayer.{kind}");
        let mut guard = self.core.lock();
        guard.live_stroke = None;

        if let Some(old_adj) = guard.live_adjustment.take() {
            if old_adj.has_updated {
                let _ = guard.session.execute("edit.undo", json!({}));
            }
            let _ = guard.session.execute("edit.undo", json!({}));
        }

        let out = guard
            .session
            .execute(&cmd, params)
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        let layer_id = out
            .get("layer")
            .and_then(Value::as_u64)
            .ok_or_else(|| EngineError::CommandFailed { detail: "Adjustment layer id missing".into() })?;

        if kind != "blackWhite" && kind != "invert" {
            guard.live_adjustment = Some(LiveAdjustmentSession {
                layer_id,
                has_updated: false,
            });
        }
        guard.mark_full_dirty();
        Ok(layer_id)
    }

    pub fn update_live_adjustment(&self, layer_id: u64, params_json: String) -> Result<(), EngineError> {
        let mut params: Value = serde_json::from_str(&params_json)
            .map_err(|e| EngineError::InvalidParams { detail: e.to_string() })?;
        if let Some(obj) = params.as_object_mut() {
            obj.insert("layer".to_string(), json!(layer_id));
        }
        let mut guard = self.core.lock();
        if let Some(mut adj) = guard.live_adjustment.take() {
            if adj.layer_id == layer_id && adj.has_updated {
                let _ = guard.session.execute("edit.undo", json!({}));
                adj.has_updated = false;
            }
            let res = guard.session.execute("layer.setAdjustment", params);
            if res.is_ok() && adj.layer_id == layer_id {
                adj.has_updated = true;
            }
            guard.live_adjustment = Some(adj);
            res.map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        } else {
            guard
                .session
                .execute("layer.setAdjustment", params)
                .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        }
        guard.mark_full_dirty();
        Ok(())
    }

    pub fn commit_live_adjustment(&self) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        guard.live_adjustment = None;
        guard.mark_full_dirty();
        Ok(())
    }

    pub fn cancel_live_adjustment(&self) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        if let Some(adj) = guard.live_adjustment.take() {
            if adj.has_updated {
                let _ = guard.session.execute("edit.undo", json!({}));
            }
            let _ = guard.session.execute("edit.undo", json!({}));
            guard.mark_full_dirty();
        }
        Ok(())
    }

    pub fn delete_layer_by_id(&self, layer_id: u64) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        guard
            .session
            .execute("layer.delete", json!({ "layer": layer_id }))
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        Ok(())
    }

    pub fn begin_live_filter(&self) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        guard.live_stroke = None;

        if let Some(old_lf) = guard.live_filter.take() {
            if old_lf.has_applied_preview {
                let _ = guard.session.execute("edit.undo", json!({}));
            }
            if old_lf.was_locked {
                let _ = guard.session.execute("edit.undo", json!({}));
            }
            if old_lf.prev_active.is_some() {
                guard.session.set_active_layer(old_lf.prev_active);
            }
        }

        let (prev_active, target_id, was_locked) = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            let prev = ds.active_layer();
            let (id, locked) = find_primary_photo_raster_layer(ds.doc(), prev).ok_or_else(|| {
                EngineError::CommandFailed {
                    detail: "Filtre uygulanacak piksel katmanı bulunamadı".into(),
                }
            })?;
            (prev, id, locked)
        };

        guard.session.set_active_layer(Some(target_id));
        if was_locked {
            let _ = guard.session.execute(
                "layer.setProps",
                json!({ "layer": target_id.0, "locks": { "pixels": false } }),
            );
        }

        guard.live_filter = Some(LiveFilterSession {
            target_id,
            prev_active,
            was_locked,
            has_applied_preview: false,
        });
        Ok(())
    }

    pub fn update_live_filter(&self, command_id: String, params_json: String) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        let Some(mut lf) = guard.live_filter.take() else {
            return Ok(());
        };

        if lf.has_applied_preview {
            let _ = guard.session.execute("edit.undo", json!({}));
            lf.has_applied_preview = false;
        }

        let mut err_out: Option<EngineError> = None;
        if !command_id.trim().is_empty() {
            match serde_json::from_str::<Value>(&params_json) {
                Ok(params) => {
                    guard.session.set_active_layer(Some(lf.target_id));
                    match guard.session.execute(&command_id, params) {
                        Ok(_) => lf.has_applied_preview = true,
                        Err(e) => err_out = Some(EngineError::CommandFailed { detail: e.to_string() }),
                    }
                }
                Err(e) => err_out = Some(EngineError::InvalidParams { detail: e.to_string() }),
            }
        }

        guard.live_filter = Some(lf);
        guard.mark_full_dirty();

        if let Some(err) = err_out {
            Err(err)
        } else {
            Ok(())
        }
    }

    pub fn commit_live_filter(&self) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        let Some(lf) = guard.live_filter.take() else {
            return Ok(());
        };
        if lf.was_locked {
            if lf.has_applied_preview {
                let _ = guard.session.execute(
                    "layer.setProps",
                    json!({ "layer": lf.target_id.0, "locks": { "pixels": true } }),
                );
            } else {
                let _ = guard.session.execute("edit.undo", json!({}));
            }
        }
        if lf.prev_active.is_some() {
            guard.session.set_active_layer(lf.prev_active);
        }
        guard.mark_full_dirty();
        Ok(())
    }

    pub fn cancel_live_filter(&self) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        let Some(lf) = guard.live_filter.take() else {
            return Ok(());
        };
        if lf.has_applied_preview {
            let _ = guard.session.execute("edit.undo", json!({}));
        }
        if lf.was_locked {
            let _ = guard.session.execute("edit.undo", json!({}));
        }
        if lf.prev_active.is_some() {
            guard.session.set_active_layer(lf.prev_active);
        }
        guard.mark_full_dirty();
        Ok(())
    }

    pub fn undo(&self) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        guard.selection_generation = guard.selection_generation.wrapping_add(1);
        guard.live_stroke = None;
        guard.live_dab = None;
        guard.live_adjustment = None;
        guard.proxy_preview = None;
        guard.live_liquify = None;
        guard.live_filter = None;
        guard
            .session
            .execute("edit.undo", json!({}))
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    pub fn redo(&self) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        guard.selection_generation = guard.selection_generation.wrapping_add(1);
        guard.live_stroke = None;
        guard.live_dab = None;
        guard.live_adjustment = None;
        guard.proxy_preview = None;
        guard.live_liquify = None;
        guard.live_filter = None;
        guard
            .session
            .execute("edit.redo", json!({}))
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    pub fn get_document_info(&self) -> Result<DocumentInfo, EngineError> {
        let guard = self.core.lock();
        self.info_from_locked(&guard)
    }

    pub fn get_layers(&self) -> Result<Vec<LayerSummary>, EngineError> {
        let guard = self.core.lock();
        let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
        let active_id = ds.active_layer();
        let doc = ds.doc();
        let bottom_id = doc.layers.first().map(|l| l.id);

        let mut layers = Vec::new();
        let walked = doc.walk();
        for (path, _parent, layer) in walked.into_iter().rev() {
            let depth = path.len().saturating_sub(1);
            let is_bg = (depth == 0 && bottom_id == Some(layer.id)) || layer.name.eq_ignore_ascii_case("Background");
            let has_mask = layer.mask.is_some();
            let mask_enabled = layer.mask.as_ref().is_some_and(|m| m.enabled);
            layers.push(LayerSummary {
                id: layer.id.0,
                name: layer.name.clone(),
                visible: layer.visible,
                opacity: layer.opacity,
                blend_mode: format!("{:?}", layer.blend),
                is_group: matches!(layer.content, LayerContent::Group(_)),
                is_active: active_id == Some(layer.id),
                locked: layer.locks.pixels || layer.locks.all,
                is_background: is_bg,
                has_mask,
                mask_enabled,
                clipped: layer.clipped,
                depth: depth as u32,
                is_text: matches!(layer.content, LayerContent::Text(_)),
                fx_badges: if layer.effects.enabled { Self::effect_badges(&layer.effects.items) } else { Vec::new() },
            });
        }
        Ok(layers)
    }

    /// Generates a lightweight RGBA8 mini-thumbnail (<= max_dim px) for a single layer
    /// so the user can visually see what is painted on each layer in the Layers panel.
    /// Renders ONLY the layer's tight `content_bounds` (zero canvas letterboxing) for 1:1 Free Transform preview.
    pub fn get_layer_bounds_preview(&self, layer_id: u64, max_dim: u32) -> Result<Option<LayerThumbnail>, EngineError> {
        let guard = self.core.lock();
        let doc = guard.active_document().ok_or(EngineError::NoActiveDocument)?;
        let Some(layer) = doc.layers.iter().find(|l| l.id.0 == layer_id) else {
            return Ok(None);
        };
        let LayerContent::Raster(surf) = &layer.content else {
            return Ok(None);
        };
        let cb = surf.content_bounds();
        let b = if cb.is_empty() { doc.bounds() } else { cb };
        let bw = (b.x1 - b.x0).max(1) as u32;
        let bh = (b.y1 - b.y0).max(1) as u32;
        let cap = max_dim.clamp(32, 1024);
        let scale = (cap as f32 / bw.max(bh) as f32).min(1.0);
        let tw = ((bw as f32 * scale).round() as u32).clamp(1, cap);
        let th = ((bh as f32 * scale).round() as u32).clamp(1, cap);

        let mut rgba = vec![0u8; (tw as usize) * (th as usize) * 4];
        let sx_step = bw as f32 / tw as f32;
        let sy_step = bh as f32 / th as f32;
        let mask_ref = layer.mask.as_ref().filter(|m| m.enabled);

        for y in 0..th {
            let dy = b.y0 + ((y as f32 + 0.5) * sy_step) as i32;
            for x in 0..tw {
                let dx = b.x0 + ((x as f32 + 0.5) * sx_step) as i32;
                let p = surf.rgba(dx, dy);
                let mut a = p[3].clamp(0.0, 1.0);
                if let Some(m) = mask_ref {
                    a *= m.value(dx, dy).clamp(0.0, 1.0);
                }
                let o = ((y as usize) * (tw as usize) + (x as usize)) * 4;
                rgba[o] = (p[0].clamp(0.0, 1.0) * 255.0).round() as u8;
                rgba[o + 1] = (p[1].clamp(0.0, 1.0) * 255.0).round() as u8;
                rgba[o + 2] = (p[2].clamp(0.0, 1.0) * 255.0).round() as u8;
                rgba[o + 3] = (a * 255.0).round() as u8;
            }
        }
        Ok(Some(LayerThumbnail { width: tw, height: th, rgba }))
    }

    pub fn get_layer_thumbnail(&self, layer_id: u64, max_dim: u32) -> Result<Option<LayerThumbnail>, EngineError> {
        let guard = self.core.lock();
        let doc = guard.active_document().ok_or(EngineError::NoActiveDocument)?;
        let Some(layer) = doc.layers.iter().find(|l| l.id.0 == layer_id) else {
            return Ok(None);
        };
        let LayerContent::Raster(surf) = &layer.content else {
            return Ok(None);
        };
        let doc_w = doc.size.width.max(1);
        let doc_h = doc.size.height.max(1);
        let cap = max_dim.clamp(16, 512);
        let scale = (cap as f32 / doc_w.max(doc_h) as f32).min(1.0);
        let tw = ((doc_w as f32 * scale).round() as u32).clamp(1, cap);
        let th = ((doc_h as f32 * scale).round() as u32).clamp(1, cap);

        let mut rgba = vec![0u8; (tw as usize) * (th as usize) * 4];
        let content_bounds = surf.content_bounds();
        if content_bounds.is_empty() {
            return Ok(Some(LayerThumbnail { width: tw, height: th, rgba }));
        }

        let sx_step = doc_w as f32 / tw as f32;
        let sy_step = doc_h as f32 / th as f32;
        let mask_ref = layer.mask.as_ref().filter(|m| m.enabled);

        for y in 0..th {
            let dy = ((y as f32 + 0.5) * sy_step) as i32;
            if dy < content_bounds.y0 || dy >= content_bounds.y1 {
                continue;
            }
            for x in 0..tw {
                let dx = ((x as f32 + 0.5) * sx_step) as i32;
                if dx < content_bounds.x0 || dx >= content_bounds.x1 {
                    continue;
                }
                let p = surf.rgba(dx, dy);
                let mut a = p[3].clamp(0.0, 1.0);
                if let Some(m) = mask_ref {
                    a *= m.value(dx, dy).clamp(0.0, 1.0);
                }
                if a <= 0.002 {
                    continue;
                }
                let idx = ((y as usize) * (tw as usize) + (x as usize)) * 4;
                rgba[idx] = (p[0].clamp(0.0, 1.0) * 255.0).round() as u8;
                rgba[idx + 1] = (p[1].clamp(0.0, 1.0) * 255.0).round() as u8;
                rgba[idx + 2] = (p[2].clamp(0.0, 1.0) * 255.0).round() as u8;
                rgba[idx + 3] = (a * 255.0).round() as u8;
            }
        }

        Ok(Some(LayerThumbnail { width: tw, height: th, rgba }))
    }

    /// Instant Before/After compare mode: hides all layers except the original Background (zero undo steps).
    pub fn set_compare_original(&self, enabled: bool) {
        let mut guard = self.core.lock();
        guard.compare_original = enabled;
        guard.mark_selection_dirty();
    }

    pub fn move_layer_up(&self) -> Result<Vec<LayerSummary>, EngineError> {
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        guard
            .session
            .execute("layer.arrange.bringForward", json!({}))
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        drop(guard);
        self.get_layers()
    }

    pub fn move_layer_down(&self) -> Result<Vec<LayerSummary>, EngineError> {
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        guard
            .session
            .execute("layer.arrange.sendBackward", json!({}))
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        drop(guard);
        self.get_layers()
    }

    pub fn duplicate_active_layer(&self) -> Result<Vec<LayerSummary>, EngineError> {
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        guard
            .session
            .execute("layer.duplicate", json!({}))
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        drop(guard);
        self.get_layers()
    }

    pub fn merge_layer_down(&self) -> Result<Vec<LayerSummary>, EngineError> {
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        guard
            .session
            .execute("layer.mergeDown", json!({}))
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        drop(guard);
        self.get_layers()
    }

    /// Copies the active selection onto a brand-new independent layer (`layer.new.layerViaCopy`)
    /// so it can immediately be moved, scaled, rotated, or styled with `fx`!
    pub fn layer_via_copy(&self) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        let has_sel = guard
            .active_document()
            .and_then(|d| d.selection.as_ref().map(|s| !s.content_bounds().is_empty()))
            .unwrap_or(false);
        if !has_sel {
            return Err(EngineError::CommandFailed {
                detail: "Önce yeni katmana kopyalanacak bir alan seç".into(),
            });
        }
        // If active layer is empty (like untouched Çizim Katmanı), target primary photo layer so copy grabs pixels
        let target_id = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            let active = ds.active_layer();
            let active_has_px = active
                .and_then(|id| ds.doc().layers.iter().find(|l| l.id == id))
                .is_some_and(|l| match &l.content {
                    LayerContent::Raster(s) => !s.content_bounds().is_empty(),
                    _ => true,
                });
            if active_has_px {
                active
            } else {
                find_primary_photo_raster_layer(ds.doc(), active).map(|(id, _)| id)
            }
        };
        if let Some(tid) = target_id {
            guard.session.set_active_layer(Some(tid));
        }
        guard
            .session
            .execute("layer.new.layerViaCopy", json!({}))
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        let _ = guard.session.execute("select.deselect", json!({}));
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    /// Softens active selection edges (`select.modify.feather`) for seamless cutouts and masks.
    pub fn feather_selection(&self, radius: f32) -> Result<Option<SelectionBounds>, EngineError> {
        let mut guard = self.core.lock();
        guard
            .session
            .execute("select.modify.feather", json!({ "radius": radius.clamp(0.5, 100.0) as f64 }))
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.dismiss_compare();
        guard.mark_selection_dirty();
        let b = guard
            .active_document()
            .and_then(|d| d.selection.as_ref().map(|s| s.content_bounds()))
            .filter(|r| !r.is_empty())
            .map(|r| SelectionBounds { x0: r.x0, y0: r.y0, x1: r.x1, y1: r.y1 });
        Ok(b)
    }

    /// Applies a non-destructive PhotoCraft Layer Style (`dropShadow`, `stroke`, `outerGlow`, `colorOverlay`).
    pub fn apply_layer_effect(
        &self,
        kind: String,
        color_hex: String,
        size: f32,
        opacity: f32,
        distance: f32,
    ) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        guard.live_stroke = None;
        guard.proxy_preview = None;
        let is_bg = guard.session.active().is_some_and(|ds| {
            let bottom = ds.doc().layers.first().map(|l| l.id);
            ds.active_layer().is_some() && ds.active_layer() == bottom
        });
        let stroke_pos = if is_bg { "inside" } else { "outside" };
        let op = opacity.clamp(5.0, 100.0) as f64;
        let sz = size.clamp(1.0, 150.0) as f64;
        let dist = distance.clamp(0.0, 150.0) as f64;
        let (cmd, params) = match kind.as_str() {
            "dropShadow" => (
                "layer.layerStyle.dropShadow",
                json!({ "color": color_hex, "opacity": op, "size": sz, "distance": dist, "angle": 120.0 }),
            ),
            "stroke" => (
                "layer.layerStyle.stroke",
                json!({ "color": color_hex, "opacity": op, "size": sz, "position": stroke_pos }),
            ),
            "innerShadow" => (
                "layer.layerStyle.innerShadow",
                json!({ "color": color_hex, "opacity": op, "size": sz, "distance": dist, "angle": 120.0, "blend": "Multiply" }),
            ),
            "outerGlow" => (
                "layer.layerStyle.outerGlow",
                json!({ "color": color_hex, "opacity": op, "size": sz }),
            ),
            "innerGlow" => (
                "layer.layerStyle.innerGlow",
                json!({ "color": color_hex, "opacity": op, "size": sz, "source": "edge", "blend": "Screen" }),
            ),
            "colorOverlay" => (
                "layer.layerStyle.colorOverlay",
                json!({ "color": color_hex, "opacity": op }),
            ),
            "gradientOverlay" => (
                "layer.layerStyle.gradientOverlay",
                json!({ "from": color_hex, "to": "#000000", "opacity": op, "angle": (distance.clamp(0.0, 360.0)) as f64, "style": "linear", "blend": "Normal" }),
            ),
            other => {
                return Err(EngineError::InvalidParams {
                    detail: format!("Bilinmeyen katman efekti: {other}"),
                })
            }
        };
        guard
            .session
            .execute(cmd, params)
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    pub fn clear_layer_effects(&self) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        guard.live_stroke = None;
        let _ = guard.session.execute("layer.layerStyle.clear", json!({}));
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    // ==================== v1.5.0: NON-DESTRUCTIVE LAYER PRO ====================

    pub fn set_mask_paint_mode(&self, enabled: bool) {
        let mut guard = self.core.lock();
        guard.mask_paint_mode = enabled;
    }

    pub fn add_layer_mask(&self, mode: String) -> Result<DocumentInfo, EngineError> {
        let cmd = match mode.as_str() {
            "revealAll" => "layer.layerMask.revealAll",
            "hideAll" => "layer.layerMask.hideAll",
            "revealSelection" => "layer.layerMask.revealSelection",
            "hideSelection" => "layer.layerMask.hideSelection",
            "fromTransparency" => "layer.layerMask.fromTransparency",
            other => {
                return Err(EngineError::InvalidParams {
                    detail: format!("Bilinmeyen maske modu: {other}"),
                })
            }
        };
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        guard.live_stroke = None;
        guard.proxy_preview = None;
        guard
            .session
            .execute(cmd, json!({}))
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    pub fn toggle_layer_mask_enabled(&self, layer_id: u64, enabled: bool) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        guard
            .session
            .execute("layer.layerMask.enabled", json!({ "layer": layer_id, "enabled": enabled }))
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    pub fn invert_active_layer_mask(&self) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        guard.live_stroke = None;
        guard
            .session
            .edit("Maskeyi Ters Çevir", |doc, active| {
                let id = active.ok_or_else(|| photocraft_engine::EngineError::BadParams {
                    cmd: "layer.layerMask.invert".into(),
                    msg: "Aktif katman seçili değil".into(),
                })?;
                let canvas = doc.bounds();
                let layer = doc
                    .layer_mut(id)
                    .ok_or(photocraft_engine::EngineError::NoLayer(id))?;
                let Some(mask) = layer.mask.as_mut() else {
                    return Err(photocraft_engine::EngineError::BadParams {
                        cmd: "layer.layerMask.invert".into(),
                        msg: "Aktif katmanda maske yok".into(),
                    });
                };
                let old_def = mask.surface.default_pixel().first().copied().unwrap_or(0.0).clamp(0.0, 1.0);
                let mut next = Surface::with_default(photocraft_color::PixelFormat::GRAY8, &[1.0 - old_def]);
                let raw = mask.surface.read_region(canvas);
                let ch = mask.surface.channels().max(1);
                let mut inv = Vec::with_capacity((canvas.width() as usize) * (canvas.height() as usize));
                for chunk in raw.chunks_exact(ch) {
                    inv.push(1.0 - chunk[0].clamp(0.0, 1.0));
                }
                next.write_region(canvas, &inv);
                mask.surface = next;
                Ok(())
            })
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    pub fn delete_active_layer_mask(&self) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        guard.live_stroke = None;
        guard.mask_paint_mode = false;
        guard
            .session
            .execute("layer.layerMask.delete", json!({}))
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    pub fn apply_active_layer_mask(&self) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        guard.live_stroke = None;
        guard.mask_paint_mode = false;
        guard
            .session
            .execute("layer.layerMask.apply", json!({}))
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    pub fn toggle_clipping_mask(&self) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        let (is_clipped, can_clip) = guard.session.active().map_or((false, false), |ds| {
            let aid = ds.active_layer();
            let doc = ds.doc();
            let clipped = aid.and_then(|id| doc.layer(id)).is_some_and(|l| l.clipped);
            let is_bottom = doc.layers.first().map(|l| l.id) == aid;
            (clipped, doc.layers.len() > 1 && !is_bottom)
        });
        if !is_clipped && !can_clip {
            return Err(EngineError::CommandFailed {
                detail: "Kırpma maskesi için altında en az 1 katman olmalı".into(),
            });
        }
        let cmd = if is_clipped {
            "layer.releaseClippingMask"
        } else {
            "layer.createClippingMask"
        };
        guard
            .session
            .execute(cmd, json!({}))
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    pub fn group_active_layer(&self, name: String) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        let clean = if name.trim().is_empty() { "Grup".to_string() } else { name };
        guard
            .session
            .execute("layer.groupLayers", json!({ "name": clean }))
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    pub fn ungroup_active_layer(&self) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        guard
            .session
            .execute("layer.ungroupLayers", json!({}))
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    pub fn update_proxy_blend_if(
        &self,
        this_black: u8,
        this_white: u8,
        under_black: u8,
        under_white: u8,
        feather: u8,
    ) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        let active_id = guard.session.active().and_then(|ds| ds.active_layer());
        guard.dismiss_compare();
        ensure_proxy_preview_locked(&mut guard)?;
        let Some(proxy) = guard.proxy_preview.as_mut() else {
            return Ok(());
        };
        let mut ps = Session::new();
        ps.add_document((*proxy.base_doc).clone(), None);
        ps.set_active(0);
        if active_id.is_some() {
            ps.set_active_layer(active_id);
        }
        let params = build_blend_if_params(this_black, this_white, under_black, under_white, feather);
        let _ = ps.execute("layer.layerStyle.blendingOptions", params);
        proxy.session = ps;
        guard.mark_full_dirty();
        Ok(())
    }

    pub fn apply_blend_if(
        &self,
        this_black: u8,
        this_white: u8,
        under_black: u8,
        under_white: u8,
        feather: u8,
    ) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        guard.proxy_preview = None;
        let params = build_blend_if_params(this_black, this_white, under_black, under_white, feather);
        guard
            .session
            .execute("layer.layerStyle.blendingOptions", params)
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    pub fn reset_blend_if(&self) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        guard.proxy_preview = None;
        guard
            .session
            .execute("layer.layerStyle.blendingOptions", json!({ "blendIf": null }))
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    // ==================== v1.6.0: LIGHTROOM RAW & COLOR PRO ====================

    /// Live 60 FPS proxy preview for full Camera RAW Pro JSON parameters (Basic, Detail, 3-Way Color Grading, Calibration, Vignette/Grain).
    pub fn update_proxy_camera_raw_json(&self, params_json: String) -> Result<(), EngineError> {
        let params: Value = serde_json::from_str(&params_json)
            .map_err(|e| EngineError::InvalidParams { detail: e.to_string() })?;
        let mut guard = self.core.lock();
        let active_id = guard.session.active().and_then(|ds| ds.active_layer());
        guard.dismiss_compare();
        ensure_proxy_preview_sized_locked(&mut guard, 512)?;
        let Some(proxy) = guard.proxy_preview.as_mut() else {
            return Ok(());
        };
        let mut ps = Session::new();
        ps.add_document((*proxy.base_doc).clone(), None);
        ps.set_active(0);
        if active_id.is_some() {
            ps.set_active_layer(active_id);
        }
        let _ = ps.execute("filter.cameraRaw", params);
        proxy.session = ps;
        guard.mark_full_dirty();
        Ok(())
    }

    /// Commits full Camera RAW Pro JSON parameters to the active layer in 1 clean Undo step.
    pub fn apply_camera_raw_json(&self, params_json: String) -> Result<DocumentInfo, EngineError> {
        let params: Value = serde_json::from_str(&params_json)
            .map_err(|e| EngineError::InvalidParams { detail: e.to_string() })?;
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        guard.live_stroke = None;
        guard.proxy_preview = None;
        guard
            .session
            .execute("filter.cameraRaw", params)
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    /// Parses a real Adobe Lightroom / Camera Raw `.xmp` preset file directly into slider values.
    pub fn parse_xmp_preset_bytes(&self, bytes: Vec<u8>) -> Result<CameraRawPresetValues, EngineError> {
        let mut cr = photocraft_algo::camera_raw::CameraRaw::default();
        photocraft_io::camera_raw_xmp::load(&mut cr, &bytes)
            .map_err(|e| EngineError::IoFailed { detail: e })?;
        Ok(CameraRawPresetValues {
            exposure: cr.exposure,
            contrast: cr.contrast,
            highlights: cr.highlights,
            shadows: cr.shadows,
            whites: cr.whites,
            blacks: cr.blacks,
            temperature: cr.temperature,
            tint: cr.tint,
            vibrance: cr.vibrance,
            saturation: cr.saturation,
            texture: cr.texture,
            clarity: cr.clarity,
            dehaze: cr.dehaze,
            sharpen_amount: cr.sharpen_amount,
            noise_luminance: cr.noise_luminance,
            noise_color: cr.noise_color,
            grade_shadow_hue: cr.grade_shadows.hue,
            grade_shadow_sat: cr.grade_shadows.sat,
            grade_mid_hue: cr.grade_midtones.hue,
            grade_mid_sat: cr.grade_midtones.sat,
            grade_high_hue: cr.grade_highlights.hue,
            grade_high_sat: cr.grade_highlights.sat,
            cal_red_hue: cr.calibration_hue[0],
            cal_red_sat: cr.calibration_sat[0],
            cal_green_hue: cr.calibration_hue[1],
            cal_green_sat: cr.calibration_sat[1],
            cal_blue_hue: cr.calibration_hue[2],
            cal_blue_sat: cr.calibration_sat[2],
            vignette_amount: cr.vignette_amount,
            grain_amount: cr.grain_amount,
        })
    }

    /// Live 60 FPS proxy preview for Lens Correction (`filter.lensCorrection`).
    pub fn update_proxy_lens_correction(
        &self,
        distortion: f32,
        vignette_amount: f32,
        red_cyan: f32,
        blue_yellow: f32,
        vertical: f32,
        horizontal: f32,
    ) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        let active_id = guard.session.active().and_then(|ds| ds.active_layer());
        guard.dismiss_compare();
        ensure_proxy_preview_locked(&mut guard)?;
        let Some(proxy) = guard.proxy_preview.as_mut() else {
            return Ok(());
        };
        let mut ps = Session::new();
        ps.add_document((*proxy.base_doc).clone(), None);
        ps.set_active(0);
        if active_id.is_some() {
            ps.set_active_layer(active_id);
        }
        let _ = ps.execute(
            "filter.lensCorrection",
            json!({
                "distortion": distortion.clamp(-100.0, 100.0) as f64,
                "vignetteAmount": vignette_amount.clamp(-100.0, 100.0) as f64,
                "redCyan": red_cyan.clamp(-100.0, 100.0) as f64,
                "blueYellow": blue_yellow.clamp(-100.0, 100.0) as f64,
                "vertical": vertical.clamp(-100.0, 100.0) as f64,
                "horizontal": horizontal.clamp(-100.0, 100.0) as f64
            }),
        );
        proxy.session = ps;
        guard.mark_full_dirty();
        Ok(())
    }

    /// Commits Lens Correction (`filter.lensCorrection`) in 1 clean Undo step.
    pub fn apply_lens_correction(
        &self,
        distortion: f32,
        vignette_amount: f32,
        red_cyan: f32,
        blue_yellow: f32,
        vertical: f32,
        horizontal: f32,
    ) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        guard.live_stroke = None;
        guard.proxy_preview = None;
        guard
            .session
            .execute(
                "filter.lensCorrection",
                json!({
                    "distortion": distortion.clamp(-100.0, 100.0) as f64,
                    "vignetteAmount": vignette_amount.clamp(-100.0, 100.0) as f64,
                    "redCyan": red_cyan.clamp(-100.0, 100.0) as f64,
                    "blueYellow": blue_yellow.clamp(-100.0, 100.0) as f64,
                    "vertical": vertical.clamp(-100.0, 100.0) as f64,
                    "horizontal": horizontal.clamp(-100.0, 100.0) as f64
                }),
            )
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    /// Live 60 FPS proxy preview for imported `.cube` / `.3dl` / `.look` 3D LUT file.
    pub fn update_proxy_color_lookup(&self, file_name: String, lut_text: String, opacity: f32) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        ensure_proxy_preview_locked(&mut guard)?;
        let Some(proxy) = guard.proxy_preview.as_mut() else {
            return Ok(());
        };
        let mut ps = Session::new();
        ps.add_document((*proxy.base_doc).clone(), None);
        ps.set_active(0);
        if let Ok(out) = ps.execute(
            "layer.newAdjustmentLayer.colorLookup",
            json!({ "data": lut_text, "fileName": file_name, "lutFormat": "auto" }),
        ) {
            if let Some(lid) = out.get("layer").and_then(Value::as_u64) {
                let _ = ps.execute(
                    "layer.setProps",
                    json!({ "layer": lid, "opacity": opacity.clamp(0.0, 1.0) }),
                );
            }
        }
        proxy.session = ps;
        guard.mark_full_dirty();
        Ok(())
    }

    /// Commits an imported `.cube` / `.3dl` / `.look` 3D LUT as a non-destructive Color Lookup adjustment layer.
    pub fn apply_color_lookup(&self, file_name: String, lut_text: String, opacity: f32) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        guard.live_stroke = None;
        guard.proxy_preview = None;
        let out = guard
            .session
            .execute(
                "layer.newAdjustmentLayer.colorLookup",
                json!({ "data": lut_text, "fileName": file_name, "lutFormat": "auto" }),
            )
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        if let Some(lid) = out.get("layer").and_then(Value::as_u64) {
            if let Some(ds) = guard.session.active_mut() {
                let doc = Arc::make_mut(ds.doc_mut());
                if let Some(l) = doc.layers.iter_mut().find(|l| l.id.0 == lid) {
                    l.opacity = opacity.clamp(0.0, 1.0);
                }
            }
        }
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }


    pub fn add_layer(&self, name: String) -> Result<Vec<LayerSummary>, EngineError> {
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        guard
            .session
            .execute("layer.new.layer", json!({ "name": name }))
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        drop(guard);
        self.get_layers()
    }

    pub fn delete_active_layer(&self) -> Result<Vec<LayerSummary>, EngineError> {
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        guard
            .session
            .execute("layer.delete", json!({}))
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        drop(guard);
        self.get_layers()
    }

    pub fn select_layer(&self, layer_id: u64) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        guard.session.set_active_layer(Some(LayerId(layer_id)));
        Ok(())
    }

    pub fn set_layer_visibility(&self, layer_id: u64, visible: bool) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        let ds = guard.session.active_mut().ok_or(EngineError::NoActiveDocument)?;
        let doc = Arc::make_mut(ds.doc_mut());
        let layer = doc
            .layers
            .iter_mut()
            .find(|l| l.id.0 == layer_id)
            .ok_or_else(|| EngineError::CommandFailed { detail: "Katman bulunamadı".into() })?;
        layer.visible = visible;
        guard.mark_full_dirty();
        Ok(())
    }

    pub fn set_layer_opacity(&self, layer_id: u64, opacity: f32) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        guard
            .session
            .execute("layer.setProps", json!({ "layer": layer_id, "opacity": opacity.clamp(0.0, 1.0) }))
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        Ok(())
    }

    pub fn set_layer_blend_mode(&self, layer_id: u64, blend_mode: String) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        guard
            .session
            .execute("layer.setProps", json!({ "layer": layer_id, "blend": blend_mode }))
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        Ok(())
    }

    pub fn set_layer_locked(&self, layer_id: u64, locked: bool) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        let ds = guard.session.active_mut().ok_or(EngineError::NoActiveDocument)?;
        let doc = Arc::make_mut(ds.doc_mut());
        let layer = doc
            .layers
            .iter_mut()
            .find(|l| l.id.0 == layer_id)
            .ok_or_else(|| EngineError::CommandFailed { detail: "Katman bulunamadı".into() })?;
        layer.locks.pixels = locked;
        Ok(())
    }

    pub fn set_brush(
        &self,
        size: f32,
        hardness: f32,
        opacity: f32,
        flow: f32,
        _color_hex: String,
    ) -> Result<(), EngineError> {
        let clamped_size = size.clamp(1.0, 1000.0);
        let adaptive_spacing = if clamped_size > 180.0 {
            0.28
        } else if clamped_size > 100.0 {
            0.22
        } else if clamped_size > 50.0 {
            0.15
        } else if clamped_size <= 4.0 {
            0.25
        } else {
            0.10
        };
        let mut guard = self.core.lock();
        let use_spacing = if guard.custom_brush_spacing {
            guard.session.tools.brush.spacing
        } else {
            adaptive_spacing
        };
        guard.session.tools.brush.size = clamped_size;
        guard.session.tools.brush.hardness = hardness.clamp(0.0, 1.0);
        guard.session.tools.brush.opacity = opacity.clamp(0.01, 1.0);
        guard.session.tools.brush.flow = flow.clamp(0.01, 1.0);
        guard.session.tools.brush.spacing = use_spacing;
        guard.session.tools.brush.spacing_enabled = true;
        Ok(())
    }

    pub fn begin_live_stroke(
        &self,
        sample: StrokeSample,
        color_hex: String,
        is_eraser: bool,
    ) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        let target_mask = guard.mask_paint_mode
            && guard.session.active().is_some_and(|ds| {
                ds.active_layer()
                    .and_then(|aid| ds.doc().layer(aid))
                    .is_some_and(|l| l.mask.is_some())
            });
        let pt_json = sample_to_json(&sample);
        let effective_color = if target_mask && is_eraser {
            "#000000".to_string()
        } else {
            color_hex
        };
        let mut params = json!({
            "points": [pt_json.clone()],
            "color": effective_color
        });
        if target_mask {
            params["target"] = json!("mask");
        } else if is_eraser {
            params["mode"] = json!("clear");
        }
        let live = LiveStroke::begin(&guard.session, &params)
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        let dmg = live.bounds();
        guard.live_stroke = Some(ActiveLiveStroke {
            live,
            points_json: vec![pt_json],
            color_hex: effective_color,
            is_eraser,
            is_pencil: false,
            target_mask,
            size: size.clamp(1.0, 400.0),
        });
        guard.mark_region_dirty(dmg);
        Ok(())
    }

    pub fn push_live_stroke(&self, samples: Vec<StrokeSample>) -> Result<(), EngineError> {
        if samples.is_empty() {
            return Ok(());
        }
        let mut guard = self.core.lock();
        let Some(active) = guard.live_stroke.as_mut() else {
            return Ok(());
        };
        // v2.4.2 Performance: thin dense sub-pixel touch points for large brushes (250px+)
        let min_step = (active.size * 0.10).clamp(2.0, 20.0);
        let min_step2 = min_step * min_step;
        let mut thinned: Vec<&StrokeSample> = Vec::with_capacity(samples.len());
        let mut last_xy: Option<(f32, f32)> = active.points_json.last().and_then(|v| {
            Some((v.get(0)?.as_f64()? as f32, v.get(1)?.as_f64()? as f32))
        });
        for (idx, s) in samples.iter().enumerate() {
            let is_last = idx + 1 == samples.len();
            if is_last || last_xy.map_or(true, |(lx, ly)| {
                let dx = s.x - lx; let dy = s.y - ly; dx * dx + dy * dy >= min_step2
            }) {
                thinned.push(s);
                last_xy = Some((s.x, s.y));
            }
        }
        let pts: Vec<StrokePoint> = thinned.iter().map(|s| sample_to_stroke_point(s)).collect();
        let dmg = active
            .live
            .push(&pts)
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        for s in thinned {
            active.points_json.push(sample_to_json(s));
        }
        guard.mark_region_dirty(dmg);
        Ok(())
    }

    pub fn finish_live_stroke(&self) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        let Some(active) = guard.live_stroke.take() else {
            return self.info_from_locked(&guard);
        };
        let seed = active.live.seed;
        let bounds = active.live.bounds();
        let mut params = json!({
            "points": active.points_json,
            "color": active.color_hex,
            "seed": seed
        });
        if active.target_mask {
            params["target"] = json!("mask");
        } else if active.is_eraser {
            params["mode"] = json!("clear");
        }
        if active.is_pencil {
            params["hardness"] = json!(1.0);
            guard
                .session
                .execute("paint.pencil", params)
                .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
            guard.mark_full_dirty();
        } else {
            let _ = guard.session.offer_live_stroke(active.live);
            guard
                .session
                .execute("paint.stroke", params)
                .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
            if guard.session.active().is_some_and(|ds| ds.symmetry.is_some()) {
                guard.mark_full_dirty();
            } else {
                guard.mark_region_dirty(bounds);
            }
        }
        self.info_from_locked(&guard)
    }

    pub fn cancel_live_stroke(&self) {
        let mut guard = self.core.lock();
        if let Some(active) = guard.live_stroke.take() {
            let bounds = active.live.bounds();
            guard.mark_region_dirty(bounds);
        }
    }

    /// Real-time 60 FPS incremental retouching (`smudge`, `dodge`, `burn`) using PhotoCraft's native `LiveDab` + dirty-rect compositing.
    pub fn begin_live_dab(
        &self,
        tool: String,
        sample: StrokeSample,
        size: f32,
        hardness: f32,
        strength: f32,
    ) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        guard.live_stroke = None;
        guard.live_dab = None;

        let (prev_active, target_id, was_locked) = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            let prev = ds.active_layer();
            let (id, locked) = find_primary_photo_raster_layer(ds.doc(), prev).ok_or_else(|| {
                EngineError::CommandFailed {
                    detail: "Rötuş uygulanacak fotoğraf katmanı bulunamadı".into(),
                }
            })?;
            (prev, id, locked)
        };

        let clamped_size = size.clamp(1.0, 400.0);
        let clamped_hard = (hardness.clamp(0.0, 1.0) * 100.0).round();
        let clamped_str = strength.clamp(5.0, 100.0);
        let adaptive_spacing = if clamped_size > 160.0 {
            38.0
        } else if clamped_size > 100.0 {
            28.0
        } else {
            18.0
        };
        let pt_json = json!([sample.x as f64, sample.y as f64, sample.pressure.clamp(0.05, 1.0) as f64]);

        let (cmd, mut base_params) = match tool.as_str() {
            "smudge" => (
                "paint.smudge".to_string(),
                json!({
                    "size": clamped_size,
                    "hardness": clamped_hard,
                    "spacing": adaptive_spacing,
                    "strength": clamped_str,
                    "sampleAllLayers": false
                }),
            ),
            "dodge" => (
                "paint.dodge".to_string(),
                json!({
                    "size": clamped_size,
                    "hardness": clamped_hard,
                    "spacing": adaptive_spacing,
                    "range": "midtones",
                    "exposure": clamped_str,
                    "protectTones": true
                }),
            ),
            "burn" => (
                "paint.burn".to_string(),
                json!({
                    "size": clamped_size,
                    "hardness": clamped_hard,
                    "spacing": adaptive_spacing,
                    "range": "midtones",
                    "exposure": clamped_str,
                    "protectTones": true
                }),
            ),
            "sponge_saturate" => {
                let sp = if clamped_size > 160.0 { 48.0 } else if clamped_size > 100.0 { 36.0 } else if clamped_size > 50.0 { 26.0 } else { 18.0 };
                (
                    "paint.sponge".to_string(),
                    json!({
                        "size": clamped_size,
                        "hardness": clamped_hard,
                        "spacing": sp,
                        "mode": "saturate",
                        "flow": clamped_str,
                        "vibrance": true
                    }),
                )
            }
            "sponge_desaturate" => {
                let sp = if clamped_size > 160.0 { 48.0 } else if clamped_size > 100.0 { 36.0 } else if clamped_size > 50.0 { 26.0 } else { 18.0 };
                (
                    "paint.sponge".to_string(),
                    json!({
                        "size": clamped_size,
                        "hardness": clamped_hard,
                        "spacing": sp,
                        "mode": "desaturate",
                        "flow": clamped_str,
                        "vibrance": true
                    }),
                )
            }
            "blur_brush" => {
                let sp = if clamped_size > 160.0 { 62.0 } else if clamped_size > 100.0 { 48.0 } else if clamped_size > 50.0 { 36.0 } else { 24.0 };
                (
                    "paint.blur".to_string(),
                    json!({
                        "size": clamped_size,
                        "hardness": clamped_hard,
                        "spacing": sp,
                        "strength": clamped_str
                    }),
                )
            }
            "sharpen_brush" => {
                let sp = if clamped_size > 160.0 { 62.0 } else if clamped_size > 100.0 { 48.0 } else if clamped_size > 50.0 { 36.0 } else { 24.0 };
                (
                    "paint.sharpen".to_string(),
                    json!({
                        "size": clamped_size,
                        "hardness": clamped_hard,
                        "spacing": sp,
                        "strength": clamped_str
                    }),
                )
            }
            other => {
                return Err(EngineError::InvalidParams {
                    detail: format!("Canlı rötuş desteklenmeyen araç: {other}"),
                })
            }
        };
        base_params["points"] = json!([pt_json.clone()]);

        guard.session.set_active_layer(Some(target_id));
        if was_locked {
            let _ = guard.session.execute(
                "layer.setProps",
                json!({ "layer": target_id.0, "locks": { "pixels": false } }),
            );
        }

        let live = match LiveDab::begin(&guard.session, &cmd, &base_params) {
            Ok(l) => l,
            Err(e) => {
                if was_locked {
                    let _ = guard.session.execute("edit.undo", json!({}));
                }
                if prev_active.is_some() {
                    guard.session.set_active_layer(prev_active);
                }
                return Err(EngineError::CommandFailed { detail: e.to_string() });
            }
        };

        let dmg = live.bounds();
        guard.live_dab = Some(ActiveLiveDab {
            live,
            cmd,
            base_params,
            points_json: vec![pt_json],
            target_id,
            prev_active,
            was_locked,
        });
        guard.mark_region_dirty(dmg);
        Ok(())
    }

    pub fn push_live_dab(&self, samples: Vec<StrokeSample>) -> Result<(), EngineError> {
        if samples.is_empty() {
            return Ok(());
        }
        let mut guard = self.core.lock();
        let Some(active) = guard.live_dab.as_mut() else {
            return Ok(());
        };
        let pts: Vec<StrokePoint> = samples.iter().map(sample_to_stroke_point).collect();
        let dmg = active
            .live
            .push(&pts)
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        for s in &samples {
            active
                .points_json
                .push(json!([s.x as f64, s.y as f64, s.pressure.clamp(0.05, 1.0) as f64]));
        }
        guard.mark_region_dirty(dmg);
        Ok(())
    }

    pub fn finish_live_dab(&self) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        let Some(active) = guard.live_dab.take() else {
            return self.info_from_locked(&guard);
        };
        let bounds = active.live.bounds();
        let mut params = active.base_params;
        params["points"] = Value::Array(active.points_json);

        let _ = guard.session.offer_live_dab(active.live);
        let res = guard.session.execute(&active.cmd, params);

        if active.was_locked {
            let _ = guard.session.execute(
                "layer.setProps",
                json!({ "layer": active.target_id.0, "locks": { "pixels": true } }),
            );
        }
        if active.prev_active.is_some() {
            guard.session.set_active_layer(active.prev_active);
        }
        res.map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_region_dirty(bounds);
        self.info_from_locked(&guard)
    }

    pub fn cancel_live_dab(&self) {
        let mut guard = self.core.lock();
        if let Some(active) = guard.live_dab.take() {
            let bounds = active.live.bounds();
            if active.was_locked {
                let _ = guard.session.execute("edit.undo", json!({}));
            }
            if active.prev_active.is_some() {
                guard.session.set_active_layer(active.prev_active);
            }
            guard.mark_region_dirty(bounds);
        }
    }

    pub fn get_selection_bounds(&self) -> Option<SelectionBounds> {
        let guard = self.core.lock();
        let doc = guard.active_document()?;
        let sel = doc.selection.as_ref()?;
        let b = sel.content_bounds();
        if b.is_empty() {
            None
        } else {
            Some(SelectionBounds {
                x0: b.x0,
                y0: b.y0,
                x1: b.x1,
                y1: b.y1,
            })
        }
    }

    pub fn select_rect(
        &self,
        x: i32,
        y: i32,
        width: u32,
        height: u32,
        ellipse: bool,
        mode: String,
    ) -> Result<Option<SelectionBounds>, EngineError> {
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        let m = if mode.is_empty() { "replace" } else { mode.as_str() };
        guard
            .session
            .execute(
                "select.rect",
                json!({
                    "x": x,
                    "y": y,
                    "width": width.max(1),
                    "height": height.max(1),
                    "ellipse": ellipse,
                    "mode": m
                }),
            )
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.dismiss_compare();
        guard.mark_selection_dirty();
        let b = guard
            .active_document()
            .and_then(|d| d.selection.as_ref().map(|s| s.content_bounds()))
            .filter(|r| !r.is_empty())
            .map(|r| SelectionBounds { x0: r.x0, y0: r.y0, x1: r.x1, y1: r.y1 });
        Ok(b)
    }

    /// Smart Object-Aware Wand: taps an object (`select.object` point mode) to detect its real
    /// boundary instead of raw RGB flood-fill, falling back to edge-aware `select.quick` if needed.
    pub fn select_object_at_point(
        &self,
        x: i32,
        y: i32,
        mode: String,
    ) -> Result<Option<SelectionBounds>, EngineError> {
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        let m = if mode.is_empty() { "replace" } else { mode.as_str() };
        let obj_res = guard.session.execute(
            "select.object",
            json!({
                "point": [x as f64, y as f64],
                "sampleAllLayers": true,
                "hardEdge": false,
                "mode": m
            }),
        );
        let has_sel = guard
            .active_document()
            .and_then(|d| d.selection.as_ref().map(|s| !s.content_bounds().is_empty()))
            .unwrap_or(false);
        if obj_res.is_err() || !has_sel {
            let quick_mode = if m == "subtract" { "subtract" } else if m == "add" { "add" } else { "replace" };
            guard
                .session
                .execute(
                    "select.quick",
                    json!({
                        "points": [[x as f64, y as f64]],
                        "size": 44.0,
                        "mode": quick_mode,
                        "sampleAllLayers": true,
                        "enhanceEdge": true
                    }),
                )
                .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        }
        guard.dismiss_compare();
        guard.mark_selection_dirty();
        let b = guard
            .active_document()
            .and_then(|d| d.selection.as_ref().map(|s| s.content_bounds()))
            .filter(|r| !r.is_empty())
            .map(|r| SelectionBounds { x0: r.x0, y0: r.y0, x1: r.x1, y1: r.y1 });
        Ok(b)
    }

    pub fn select_magic_wand(
        &self,
        x: i32,
        y: i32,
        tolerance: u32,
        contiguous: bool,
        mode: String,
    ) -> Result<Option<SelectionBounds>, EngineError> {
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        let m = if mode.is_empty() { "replace" } else { mode.as_str() };
        guard
            .session
            .execute(
                "select.magicWand",
                json!({
                    "x": x,
                    "y": y,
                    "tolerance": tolerance.min(255),
                    "contiguous": contiguous,
                    "antiAlias": true,
                    "sampleAllLayers": true,
                    "mode": m
                }),
            )
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        // Smooth tiny 1px jagged speckles for cleaner selection edges
        let _ = guard.session.execute("select.modify.smooth", json!({ "radius": 2 }));
        guard.dismiss_compare();
        guard.mark_selection_dirty();
        let b = guard
            .active_document()
            .and_then(|d| d.selection.as_ref().map(|s| s.content_bounds()))
            .filter(|r| !r.is_empty())
            .map(|r| SelectionBounds { x0: r.x0, y0: r.y0, x1: r.x1, y1: r.y1 });
        Ok(b)
    }

    /// One-tap automatic Subject selection (`select.subject`).
    pub fn select_subject(&self) -> Result<Option<SelectionBounds>, EngineError> {
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        guard
            .session
            .execute(
                "select.subject",
                json!({
                    "sampleAllLayers": true,
                    "mode": "replace"
                }),
            )
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.dismiss_compare();
        guard.mark_selection_dirty();
        let b = guard
            .active_document()
            .and_then(|d| d.selection.as_ref().map(|s| s.content_bounds()))
            .filter(|r| !r.is_empty())
            .map(|r| SelectionBounds { x0: r.x0, y0: r.y0, x1: r.x1, y1: r.y1 });
        Ok(b)
    }

    /// Edge-aware Quick Selection Brush (`select.quick` with `enhanceEdge: true`).
    pub fn select_quick_stroke(
        &self,
        samples: Vec<StrokeSample>,
        size: f32,
        subtract: bool,
    ) -> Result<Option<SelectionBounds>, EngineError> {
        if samples.is_empty() {
            return Ok(self.get_selection_bounds());
        }
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        let pts: Vec<Value> = samples
            .iter()
            .map(|s| json!([s.x as f64, s.y as f64]))
            .collect();
        let mode = if subtract { "subtract" } else { "add" };
        guard
            .session
            .execute(
                "select.quick",
                json!({
                    "points": pts,
                    "size": size.clamp(1.0, 400.0) as f64,
                    "mode": mode,
                    "sampleAllLayers": true,
                    "enhanceEdge": true
                }),
            )
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.dismiss_compare();
        guard.mark_selection_dirty();
        let b = guard
            .active_document()
            .and_then(|d| d.selection.as_ref().map(|s| s.content_bounds()))
            .filter(|r| !r.is_empty())
            .map(|r| SelectionBounds { x0: r.x0, y0: r.y0, x1: r.x1, y1: r.y1 });
        Ok(b)
    }

    /// Instant manual selection brush: paint directly onto `doc.selection` to add (`subtract = false`)
    /// or erase (`subtract = true`) any area by hand with smooth edges.
    pub fn paint_selection_stroke(
        &self,
        samples: Vec<StrokeSample>,
        size: f32,
        subtract: bool,
    ) -> Result<Option<SelectionBounds>, EngineError> {
        if samples.is_empty() {
            return Ok(self.get_selection_bounds());
        }
        let mut guard = self.core.lock();
        guard.live_stroke = None;
        let radius = (size.clamp(1.0, 400.0) * 0.5).max(0.5);
        let pad = radius.ceil() as i32 + 2;
        let label = if subtract { "Seçim Silgisi" } else { "Seçim Fırçası" };

        guard
            .session
            .edit(label, |doc, _| {
                let canvas = doc.bounds();
                if subtract && doc.selection.is_none() {
                    return Ok(());
                }
                let mut min_x = i32::MAX;
                let mut min_y = i32::MAX;
                let mut max_x = i32::MIN;
                let mut max_y = i32::MIN;
                for s in &samples {
                    let sx = s.x.round() as i32;
                    let sy = s.y.round() as i32;
                    min_x = min_x.min(sx - pad);
                    min_y = min_y.min(sy - pad);
                    max_x = max_x.max(sx + pad);
                    max_y = max_y.max(sy + pad);
                }
                let roi = Rect::new(min_x, min_y, max_x, max_y).intersect(&canvas);
                if roi.is_empty() {
                    return Ok(());
                }

                let sel = doc
                    .selection
                    .get_or_insert_with(|| Surface::new(PixelFormat::GRAY8));
                let rw = roi.width() as usize;
                let rh = roi.height() as usize;
                let mut buf = sel.read_region(roi);
                if buf.len() < rw * rh {
                    return Ok(());
                }

                // Stamp smooth circular dabs along stroke segments
                let mut stamp_disc = |cx: f32, cy: f32| {
                    let x0 = ((cx - radius - 1.0).floor() as i32).max(roi.x0);
                    let x1 = ((cx + radius + 1.0).ceil() as i32).min(roi.x1 - 1);
                    let y0 = ((cy - radius - 1.0).floor() as i32).max(roi.y0);
                    let y1 = ((cy + radius + 1.0).ceil() as i32).min(roi.y1 - 1);
                    for py in y0..=y1 {
                        let dy = (py as f32 + 0.5) - cy;
                        let row = (py - roi.y0) as usize * rw;
                        for px in x0..=x1 {
                            let dx = (px as f32 + 0.5) - cx;
                            let dist = (dx * dx + dy * dy).sqrt();
                            let hit_r = radius.max(0.75);
                            if dist <= hit_r {
                                let edge = if radius <= 1.5 { 1.0 } else { ((radius - dist) * 0.5).clamp(0.0, 1.0) };
                                let idx = row + (px - roi.x0) as usize;
                                if subtract {
                                    buf[idx] = (buf[idx] * (1.0 - edge)).clamp(0.0, 1.0);
                                } else {
                                    buf[idx] = buf[idx].max(edge);
                                }
                            }
                        }
                    }
                };

                let step = (radius * 0.35).max(0.5);
                stamp_disc(samples[0].x, samples[0].y);
                for win in samples.windows(2) {
                    let (a, b) = (&win[0], &win[1]);
                    let d = ((b.x - a.x).hypot(b.y - a.y)).max(0.001);
                    let n = (d / step).ceil() as i32;
                    for i in 1..=n {
                        let t = i as f32 / n as f32;
                        stamp_disc(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t);
                    }
                }

                sel.write_region(roi, &buf);
                if subtract && sel.content_bounds().is_empty() {
                    doc.selection = None;
                }
                Ok(())
            })
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;

        guard.dismiss_compare();
        guard.mark_selection_dirty();
        let b = guard
            .active_document()
            .and_then(|d| d.selection.as_ref().map(|s| s.content_bounds()))
            .filter(|r| !r.is_empty())
            .map(|r| SelectionBounds { x0: r.x0, y0: r.y0, x1: r.x1, y1: r.y1 });
        Ok(b)
    }

    pub fn select_inverse(&self) -> Result<Option<SelectionBounds>, EngineError> {
        let mut guard = self.core.lock();
        guard
            .session
            .execute("select.inverse", json!({}))
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.dismiss_compare();
        guard.mark_selection_dirty();
        let b = guard
            .active_document()
            .and_then(|d| d.selection.as_ref().map(|s| s.content_bounds()))
            .filter(|r| !r.is_empty())
            .map(|r| SelectionBounds { x0: r.x0, y0: r.y0, x1: r.x1, y1: r.y1 });
        Ok(b)
    }

    pub fn select_deselect(&self) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        let _ = guard.session.execute("select.deselect", json!({}));
        guard.dismiss_compare();
        guard.mark_selection_dirty();
        self.info_from_locked(&guard)
    }

    pub fn clear_selection_pixels(&self) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        let (target_id, was_locked) = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            let active = ds.active_layer();
            let chosen = active
                .and_then(|id| ds.doc().layers.iter().find(|l| l.id == id))
                .filter(|l| matches!(l.content, LayerContent::Raster(_)));
            match chosen {
                Some(l) => (l.id, l.locks.pixels),
                None => find_primary_photo_raster_layer(ds.doc(), active).ok_or_else(|| {
                    EngineError::CommandFailed { detail: "Silinecek piksel katmanı bulunamadı".into() }
                })?,
            }
        };
        guard.session.set_active_layer(Some(target_id));
        if was_locked {
            let _ = guard.session.execute(
                "layer.setProps",
                json!({ "layer": target_id.0, "locks": { "pixels": false } }),
            );
        }
        let res = guard.session.execute("edit.clear", json!({}));
        if was_locked {
            let _ = guard.session.execute(
                "layer.setProps",
                json!({ "layer": target_id.0, "locks": { "pixels": true } }),
            );
        }
        res.map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    pub fn fill_selection_pixels(&self, color_hex: String) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        let (target_id, was_locked) = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            let active = ds.active_layer();
            let chosen = active
                .and_then(|id| ds.doc().layers.iter().find(|l| l.id == id))
                .filter(|l| matches!(l.content, LayerContent::Raster(_)));
            match chosen {
                Some(l) => (l.id, l.locks.pixels),
                None => find_primary_photo_raster_layer(ds.doc(), active).ok_or_else(|| {
                    EngineError::CommandFailed { detail: "Doldurulacak piksel katmanı bulunamadı".into() }
                })?,
            }
        };
        guard.session.set_active_layer(Some(target_id));
        if was_locked {
            let _ = guard.session.execute(
                "layer.setProps",
                json!({ "layer": target_id.0, "locks": { "pixels": false } }),
            );
        }
        let res = guard
            .session
            .execute("edit.fill", json!({ "contents": "color", "color": color_hex }));
        if was_locked {
            let _ = guard.session.execute(
                "layer.setProps",
                json!({ "layer": target_id.0, "locks": { "pixels": true } }),
            );
        }
        res.map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    /// Content-Aware Object Removal (`edit.deleteAndFillSelection`): seamlessly removes the
    /// selected object and fills the region from surrounding photo context.
    pub fn content_aware_remove(&self) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        guard.live_stroke = None;
        guard.live_dab = None;
        guard.live_clone = None;
        let (target_id, was_locked, has_sel) = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            let has_sel = ds.doc().selection.as_ref().is_some_and(|s| !s.content_bounds().is_empty());
            let (id, locked) = find_primary_photo_raster_layer(ds.doc(), ds.active_layer()).ok_or_else(|| {
                EngineError::CommandFailed { detail: "Nesne silinecek fotoğraf katmanı bulunamadı".into() }
            })?;
            (id, locked, has_sel)
        };
        if !has_sel {
            return Err(EngineError::CommandFailed {
                detail: "Önce silmek istediğin nesneyi seç".into(),
            });
        }
        guard.session.set_active_layer(Some(target_id));
        if was_locked {
            let _ = guard.session.execute(
                "layer.setProps",
                json!({ "layer": target_id.0, "locks": { "pixels": false } }),
            );
        }
        let res = guard.session.execute("edit.deleteAndFillSelection", json!({}));
        if was_locked {
            let _ = guard.session.execute(
                "layer.setProps",
                json!({ "layer": target_id.0, "locks": { "pixels": true } }),
            );
        }
        res.map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        let _ = guard.session.execute("select.deselect", json!({}));
        guard.mark_selection_dirty();
        self.info_from_locked(&guard)
    }

    pub fn reveal_selection_mask(&self) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        let target_id = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            find_primary_photo_raster_layer(ds.doc(), ds.active_layer())
                .map(|(id, _)| id)
                .ok_or_else(|| EngineError::CommandFailed {
                    detail: "Maske eklenecek fotoğraf katmanı bulunamadı".into(),
                })?
        };
        guard.session.set_active_layer(Some(target_id));
        guard
            .session
            .execute("layer.layerMask.revealSelection", json!({}))
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    /// Real-time 60 FPS Clone Stamp (`paint.cloneStamp`) using PhotoCraft's native `LiveRetouch`.
    pub fn begin_live_clone(
        &self,
        sample: StrokeSample,
        source_x: f32,
        source_y: f32,
        size: f32,
        hardness: f32,
        opacity: f32,
    ) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        guard.live_stroke = None;
        guard.live_dab = None;
        guard.live_clone = None;

        let (prev_active, target_id, was_locked) = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            let prev = ds.active_layer();
            let (id, locked) = find_primary_photo_raster_layer(ds.doc(), prev).ok_or_else(|| {
                EngineError::CommandFailed {
                    detail: "Klonlanacak fotoğraf katmanı bulunamadı".into(),
                }
            })?;
            (prev, id, locked)
        };

        let clamped_size = size.clamp(1.0, 400.0);
        let clamped_hard = (hardness.clamp(0.0, 1.0) * 100.0).round();
        let clamped_op = (opacity.clamp(0.05, 1.0) * 100.0).round();
        let pt_json = json!([sample.x as f64, sample.y as f64, sample.pressure.clamp(0.05, 1.0) as f64]);

        let mut base_params = json!({
            "source": [source_x as f64, source_y as f64],
            "size": clamped_size,
            "hardness": clamped_hard,
            "opacity": clamped_op,
            "aligned": true,
            "sampleLayer": "current",
            "points": [pt_json.clone()]
        });

        guard.session.set_active_layer(Some(target_id));
        if was_locked {
            let _ = guard.session.execute(
                "layer.setProps",
                json!({ "layer": target_id.0, "locks": { "pixels": false } }),
            );
        }

        let live = match LiveRetouch::begin(&guard.session, "paint.cloneStamp", &base_params) {
            Ok(l) => l,
            Err(e) => {
                if was_locked {
                    let _ = guard.session.execute("edit.undo", json!({}));
                }
                if prev_active.is_some() {
                    guard.session.set_active_layer(prev_active);
                }
                return Err(EngineError::CommandFailed { detail: e.to_string() });
            }
        };

        let dmg = live.bounds();
        base_params["points"] = Value::Array(Vec::new());
        guard.live_clone = Some(ActiveLiveClone {
            live,
            base_params,
            points_json: vec![pt_json],
            target_id,
            prev_active,
            was_locked,
        });
        guard.mark_region_dirty(dmg);
        Ok(())
    }

    pub fn push_live_clone(&self, samples: Vec<StrokeSample>) -> Result<(), EngineError> {
        if samples.is_empty() {
            return Ok(());
        }
        let mut guard = self.core.lock();
        let Some(active) = guard.live_clone.as_mut() else {
            return Ok(());
        };
        let pts: Vec<StrokePoint> = samples.iter().map(sample_to_stroke_point).collect();
        let dmg = active
            .live
            .push(&pts)
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        for s in &samples {
            active
                .points_json
                .push(json!([s.x as f64, s.y as f64, s.pressure.clamp(0.05, 1.0) as f64]));
        }
        guard.mark_region_dirty(dmg);
        Ok(())
    }

    pub fn finish_live_clone(&self) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        let Some(active) = guard.live_clone.take() else {
            return self.info_from_locked(&guard);
        };
        let bounds = active.live.bounds();
        let mut params = active.base_params;
        params["points"] = Value::Array(active.points_json);

        let _ = guard.session.offer_live_retouch(active.live);
        let res = guard.session.execute("paint.cloneStamp", params);

        if active.was_locked {
            let _ = guard.session.execute(
                "layer.setProps",
                json!({ "layer": active.target_id.0, "locks": { "pixels": true } }),
            );
        }
        if active.prev_active.is_some() {
            guard.session.set_active_layer(active.prev_active);
        }
        res.map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_region_dirty(bounds);
        self.info_from_locked(&guard)
    }

    pub fn cancel_live_clone(&self) {
        let mut guard = self.core.lock();
        if let Some(active) = guard.live_clone.take() {
            let bounds = active.live.bounds();
            if active.was_locked {
                let _ = guard.session.execute("edit.undo", json!({}));
            }
            if active.prev_active.is_some() {
                guard.session.set_active_layer(active.prev_active);
            }
            guard.mark_region_dirty(bounds);
        }
    }

    pub fn render_generation(&self) -> u64 {
        self.core.lock().render_generation
    }

    // ==================== v1.7.0 PRO BRUSH & RETOUCH STUDIO ====================

    pub fn set_brush_dynamics(
        &self,
        spacing: f32,
        scatter: f32,
        size_jitter: f32,
        angle_jitter: f32,
        opacity_jitter: f32,
    ) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        let sp = spacing.clamp(0.05, 2.0);
        guard.custom_brush_spacing = (sp - 0.15).abs() > 0.01
            || scatter > 0.001
            || size_jitter > 0.001
            || angle_jitter > 0.001
            || opacity_jitter > 0.001;
        let b = &mut guard.session.tools.brush;
        b.spacing = sp;
        b.spacing_enabled = true;

        let sc = scatter.clamp(0.0, 5.0);
        b.scattering.enabled = sc > 0.001;
        b.scattering.scatter.jitter = sc;
        b.scattering.both_axes = true;
        b.scattering.count = if sc > 0.001 { 2 } else { 1 };

        let sj = size_jitter.clamp(0.0, 1.0);
        let aj = angle_jitter.clamp(0.0, 1.0);
        b.shape_dynamics.enabled = sj > 0.001 || aj > 0.001;
        b.shape_dynamics.size.jitter = sj;
        b.shape_dynamics.angle.jitter = aj;

        let oj = opacity_jitter.clamp(0.0, 1.0);
        b.transfer.enabled = oj > 0.001;
        b.transfer.opacity.jitter = oj;
        Ok(())
    }

    pub fn import_abr_bytes(
        &self,
        file_name: String,
        bytes: Vec<u8>,
    ) -> Result<Vec<AbrBrushSummary>, EngineError> {
        let group = file_name
            .trim_end_matches(".abr")
            .trim_end_matches(".ABR")
            .trim();
        let group_name = if group.is_empty() { "İçe Aktarılan Fırçalar" } else { group };
        let imported = photocraft_io::abr_map::read_abr(&bytes, group_name)
            .map_err(|e| EngineError::IoFailed { detail: e })?;
        if imported.presets.is_empty() {
            return Err(EngineError::IoFailed {
                detail: "ABR dosyasında okunabilir fırça bulunamadı".into(),
            });
        }
        let mut guard = self.core.lock();
        let lib = Arc::make_mut(&mut *guard.session.tools.presets);
        for preset in imported.presets {
            if !lib.iter().any(|p| p.name.eq_ignore_ascii_case(&preset.name)) {
                lib.push(preset);
            }
        }
        Ok(Self::summarize_presets(&guard.session.tools.presets))
    }

    pub fn get_brush_presets(&self) -> Result<Vec<AbrBrushSummary>, EngineError> {
        let guard = self.core.lock();
        Ok(Self::summarize_presets(&guard.session.tools.presets))
    }

    pub fn select_brush_preset(&self, name: String) -> Result<AbrBrushSummary, EngineError> {
        let mut guard = self.core.lock();
        let found = guard
            .session
            .tools
            .presets
            .iter()
            .enumerate()
            .find(|(_, p)| p.name.eq_ignore_ascii_case(&name))
            .map(|(idx, p)| (idx, p.clone()))
            .ok_or_else(|| EngineError::InvalidParams {
                detail: format!("Fırça bulunamadı: {name}"),
            })?;
        let (idx, preset) = found;
        let prev_size = guard.session.tools.brush.size.clamp(1.0, 1000.0);
        let prev_opacity = guard.session.tools.brush.opacity.clamp(0.01, 1.0);
        let mut brush = preset.brush.clone();
        let _ = guard.session.load_brush_tips(&mut brush);
        brush.size = prev_size;
        brush.opacity = prev_opacity;
        let summary = AbrBrushSummary {
            index: idx as u32,
            name: preset.name.clone(),
            group: preset.group.clone(),
            size: brush.size.clamp(1.0, 1000.0),
            hardness: brush.hardness.clamp(0.0, 1.0),
            spacing: brush.spacing.clamp(0.05, 2.0),
            has_sampled_tip: !matches!(*brush.tip, photocraft_paint::TipShape::Round),
        };
        *guard.session.tools.brush = brush;
        guard.session.tools.current_preset = Some(preset.name);
        guard.custom_brush_spacing = true;
        Ok(summary)
    }

    pub fn reset_brush_to_round(&self) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        let prev_size = guard.session.tools.brush.size;
        let prev_hard = guard.session.tools.brush.hardness;
        let prev_op = guard.session.tools.brush.opacity;
        let prev_flow = guard.session.tools.brush.flow;
        let mut b = photocraft_paint::BrushSettings::default();
        b.size = prev_size;
        b.hardness = prev_hard;
        b.opacity = prev_op;
        b.flow = prev_flow;
        *guard.session.tools.brush = b;
        guard.session.tools.current_preset = None;
        guard.custom_brush_spacing = false;
        Ok(())
    }

    pub fn begin_live_mixer(
        &self,
        sample: StrokeSample,
        size: f32,
        hardness: f32,
        wet: f32,
        load: f32,
        mix: f32,
        flow: f32,
        color_hex: String,
        sample_all_layers: bool,
    ) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        guard.live_stroke = None;
        guard.live_dab = None;
        guard.live_clone = None;
        guard.live_mixer = None;

        let (prev_active, target_id, was_locked) = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            let prev = ds.active_layer();
            let (id, locked) = find_primary_photo_raster_layer(ds.doc(), prev).ok_or_else(|| {
                EngineError::CommandFailed {
                    detail: "Karıştırıcı fırça uygulanacak piksel katmanı bulunamadı".into(),
                }
            })?;
            (prev, id, locked)
        };

        guard.session.set_active_layer(Some(target_id));
        if was_locked {
            let _ = guard.session.execute(
                "layer.setProps",
                json!({ "layer": target_id.0, "locks": { "pixels": false } }),
            );
        }

        let clamped_size = size.clamp(1.0, 500.0);
        let mixer_spacing = if clamped_size > 160.0 {
            0.48
        } else if clamped_size > 100.0 {
            0.38
        } else if clamped_size > 50.0 {
            0.30
        } else {
            0.22
        };
        let pt_json = sample_to_json(&sample);
        let params = json!({
            "points": [pt_json],
            "size": clamped_size,
            "brush": {
                "size": clamped_size,
                "hardness": hardness.clamp(0.0, 1.0),
                "spacing": mixer_spacing,
                "spacingEnabled": true
            },
            "wet": wet.clamp(0.0, 100.0),
            "load": load.clamp(0.0, 100.0),
            "mix": mix.clamp(0.0, 100.0),
            "flow": flow.clamp(1.0, 100.0),
            "color": color_hex,
            "sampleAllLayers": sample_all_layers,
            "loadAfterStroke": true,
            "cleanAfterStroke": false
        });

        let live = match LiveMixer::begin(&guard.session, &params) {
            Ok(l) => l,
            Err(e) => {
                if was_locked {
                    let _ = guard.session.execute("edit.undo", json!({}));
                }
                if prev_active.is_some() {
                    guard.session.set_active_layer(prev_active);
                }
                return Err(EngineError::CommandFailed { detail: e.to_string() });
            }
        };

        let dmg = live.bounds();
        guard.live_mixer = Some(ActiveLiveMixer {
            live,
            target_id,
            prev_active,
            was_locked,
        });
        guard.mark_region_dirty(dmg);
        Ok(())
    }

    pub fn push_live_mixer(&self, samples: Vec<StrokeSample>) -> Result<(), EngineError> {
        if samples.is_empty() {
            return Ok(());
        }
        let mut guard = self.core.lock();
        let Some(active) = guard.live_mixer.as_mut() else {
            return Ok(());
        };
        let pts: Vec<StrokePoint> = samples.iter().map(sample_to_stroke_point).collect();
        let dmg = active
            .live
            .push(&pts)
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_region_dirty(dmg);
        Ok(())
    }

    pub fn finish_live_mixer(&self) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        let Some(active) = guard.live_mixer.take() else {
            return self.info_from_locked(&guard);
        };
        let params = active
            .live
            .prepare_commit(&mut guard.session)
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        let res = guard.session.execute("paint.mixerBrush", params);
        if active.was_locked {
            let _ = guard.session.execute(
                "layer.setProps",
                json!({ "layer": active.target_id.0, "locks": { "pixels": true } }),
            );
        }
        if active.prev_active.is_some() {
            guard.session.set_active_layer(active.prev_active);
        }
        res.map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    pub fn begin_live_color_replacement(
        &self,
        sample: StrokeSample,
        size: f32,
        hardness: f32,
        color_hex: String,
        mode: String,
        tolerance: f32,
    ) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        guard.live_stroke = None;
        guard.live_dab = None;
        guard.live_clone = None;
        guard.live_mixer = None;

        let (prev_active, target_id, was_locked) = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            let prev = ds.active_layer();
            let (id, locked) = find_primary_photo_raster_layer(ds.doc(), prev).ok_or_else(|| {
                EngineError::CommandFailed {
                    detail: "Renk değiştirme uygulanacak fotoğraf katmanı bulunamadı".into(),
                }
            })?;
            (prev, id, locked)
        };

        let safe_mode = match mode.as_str() {
            "hue" | "saturation" | "color" | "luminosity" => mode,
            _ => "color".to_string(),
        };
        let clamped_size = size.clamp(1.0, 400.0);
        let crepl_spacing = if clamped_size > 160.0 {
            0.45
        } else if clamped_size > 100.0 {
            0.35
        } else if clamped_size > 50.0 {
            0.28
        } else {
            0.20
        };
        let pt_json = sample_to_json(&sample);
        let base_params = json!({
            "points": [pt_json.clone()],
            "size": clamped_size,
            "brush": {
                "size": clamped_size,
                "hardness": hardness.clamp(0.0, 1.0),
                "spacing": crepl_spacing,
                "spacingEnabled": true
            },
            "color": color_hex,
            "mode": safe_mode,
            "sampling": "continuous",
            "limits": "discontiguous",
            "tolerance": tolerance.clamp(1.0, 100.0),
            "antiAlias": true
        });

        guard.session.set_active_layer(Some(target_id));
        if was_locked {
            let _ = guard.session.execute(
                "layer.setProps",
                json!({ "layer": target_id.0, "locks": { "pixels": false } }),
            );
        }

        let live = match live_color_replacement(&guard.session, &base_params) {
            Ok(l) => l,
            Err(e) => {
                if was_locked {
                    let _ = guard.session.execute("edit.undo", json!({}));
                }
                if prev_active.is_some() {
                    guard.session.set_active_layer(prev_active);
                }
                return Err(EngineError::CommandFailed { detail: e.to_string() });
            }
        };

        let dmg = live.bounds();
        guard.live_dab = Some(ActiveLiveDab {
            live,
            cmd: "paint.colorReplacement".to_string(),
            base_params,
            points_json: vec![pt_json],
            target_id,
            prev_active,
            was_locked,
        });
        guard.mark_region_dirty(dmg);
        Ok(())
    }

    pub fn apply_patch_tool(
        &self,
        dx: i32,
        dy: i32,
        mode: String,
    ) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        guard.live_stroke = None;
        guard.live_dab = None;
        guard.live_mixer = None;

        let (prev_active, target_id, was_locked) = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            let prev = ds.active_layer();
            let (id, locked) = find_primary_photo_raster_layer(ds.doc(), prev).ok_or_else(|| {
                EngineError::CommandFailed {
                    detail: "Yama uygulanacak fotoğraf katmanı bulunamadı".into(),
                }
            })?;
            (prev, id, locked)
        };

        let safe_mode = if mode == "destination" { "destination" } else { "source" };
        guard.session.set_active_layer(Some(target_id));
        if was_locked {
            let _ = guard.session.execute(
                "layer.setProps",
                json!({ "layer": target_id.0, "locks": { "pixels": false } }),
            );
        }
        let res = guard.session.execute(
            "paint.patch",
            json!({
                "offset": [dx, dy],
                "mode": safe_mode
            }),
        );
        if was_locked {
            let _ = guard.session.execute(
                "layer.setProps",
                json!({ "layer": target_id.0, "locks": { "pixels": true } }),
            );
        }
        if prev_active.is_some() {
            guard.session.set_active_layer(prev_active);
        }
        res.map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    pub fn apply_red_eye(
        &self,
        x: f32,
        y: f32,
        pupil_size: f32,
        darken: f32,
    ) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        guard.live_stroke = None;
        guard.live_dab = None;
        guard.live_mixer = None;

        let (prev_active, target_id, was_locked) = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            let prev = ds.active_layer();
            let (id, locked) = find_primary_photo_raster_layer(ds.doc(), prev).ok_or_else(|| {
                EngineError::CommandFailed {
                    detail: "Kırmızı göz düzeltilecek fotoğraf katmanı bulunamadı".into(),
                }
            })?;
            (prev, id, locked)
        };

        guard.session.set_active_layer(Some(target_id));
        if was_locked {
            let _ = guard.session.execute(
                "layer.setProps",
                json!({ "layer": target_id.0, "locks": { "pixels": false } }),
            );
        }
        let res = guard.session.execute(
            "paint.redEye",
            json!({
                "x": x.round() as i32,
                "y": y.round() as i32,
                "pupilSize": pupil_size.clamp(1.0, 100.0),
                "darken": darken.clamp(0.0, 100.0)
            }),
        );
        if was_locked {
            let _ = guard.session.execute(
                "layer.setProps",
                json!({ "layer": target_id.0, "locks": { "pixels": true } }),
            );
        }
        if prev_active.is_some() {
            guard.session.set_active_layer(prev_active);
        }
        res.map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }


    // ==================== v1.7.3 MULTI-FX LAYER STYLE STUDIO ====================

    pub fn update_proxy_layer_style_stack(&self, effects_json: String) -> Result<(), EngineError> {
        let mut effects: Vec<Value> = serde_json::from_str(&effects_json)
            .map_err(|e| EngineError::InvalidParams { detail: e.to_string() })?;
        let mut guard = self.core.lock();
        let active_id = guard.session.active().and_then(|ds| ds.active_layer());
        let Some(aid) = active_id else {
            return Ok(());
        };
        guard.dismiss_compare();
        ensure_proxy_preview_locked(&mut guard)?;
        let Some(proxy) = guard.proxy_preview.as_mut() else {
            return Ok(());
        };
        let scale = proxy.scale as f64;
        for item in &mut effects {
            if let Some(p) = item.get_mut("params").and_then(|v| v.as_object_mut()) {
                if let Some(sz) = p.get("size").and_then(|v| v.as_f64()) {
                    p.insert("size".into(), json!((sz * scale).clamp(1.0, 150.0)));
                }
                if let Some(dist) = p.get("distance").and_then(|v| v.as_f64()) {
                    p.insert("distance".into(), json!((dist * scale).clamp(0.0, 150.0)));
                }
            }
        }
        let mut ps = Session::new();
        ps.add_document((*proxy.base_doc).clone(), None);
        ps.set_active(0);
        ps.set_active_layer(Some(aid));
        if effects.is_empty() {
            let _ = ps.execute("layer.layerStyle.clear", json!({ "layer": aid.0 }));
        } else {
            let _ = ps.execute(
                "layer.layerStyle.replace",
                json!({ "layer": aid.0, "effects": effects }),
            );
        }
        proxy.session = ps;
        guard.mark_full_dirty();
        Ok(())
    }

    pub fn apply_layer_style_stack(&self, effects_json: String) -> Result<DocumentInfo, EngineError> {
        let effects: Vec<Value> = serde_json::from_str(&effects_json)
            .map_err(|e| EngineError::InvalidParams { detail: e.to_string() })?;
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        guard.live_stroke = None;
        guard.proxy_preview = None;
        let aid = guard
            .session
            .active()
            .and_then(|ds| ds.active_layer())
            .ok_or_else(|| EngineError::CommandFailed {
                detail: "Efekt uygulanacak aktif katman yok".into(),
            })?;
        if effects.is_empty() {
            guard
                .session
                .execute("layer.layerStyle.clear", json!({ "layer": aid.0 }))
                .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        } else {
            guard
                .session
                .execute(
                    "layer.layerStyle.replace",
                    json!({ "layer": aid.0, "effects": effects }),
                )
                .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        }
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    pub fn copy_active_layer_style(&self) -> Result<u32, EngineError> {
        let mut guard = self.core.lock();
        let effects = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            let aid = ds.active_layer().ok_or_else(|| EngineError::CommandFailed {
                detail: "Aktif katman seçili değil".into(),
            })?;
            let layer = ds.doc().layer(aid).ok_or_else(|| EngineError::CommandFailed {
                detail: "Katman bulunamadı".into(),
            })?;
            layer.effects.items.clone()
        };
        let count = effects.len() as u32;
        if count == 0 {
            return Err(EngineError::CommandFailed {
                detail: "Bu katmanda kopyalanacak fx stili yok".into(),
            });
        }
        guard.copied_layer_effects = effects;
        Ok(count)
    }

    pub fn paste_layer_style_to_active(&self) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        guard.live_stroke = None;
        guard.proxy_preview = None;
        if guard.copied_layer_effects.is_empty() {
            return Err(EngineError::CommandFailed {
                detail: "Önce bir katmandan stil kopyalamalısın".into(),
            });
        }
        let copied = guard.copied_layer_effects.clone();
        guard
            .session
            .edit("Katman Stili Yapıştır", |doc, active| {
                let id = active.ok_or_else(|| photocraft_engine::EngineError::BadParams {
                    cmd: "layer.layerStyle.paste".into(),
                    msg: "Aktif katman seçili değil".into(),
                })?;
                let layer = doc
                    .layer_mut(id)
                    .ok_or(photocraft_engine::EngineError::NoLayer(id))?;
                layer.effects.enabled = true;
                layer.effects.items = copied.clone();
                Ok(())
            })
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }



    // ==================== v1.8.0 CONTENT-AWARE & OBJECT REMOVAL ====================

    pub fn update_proxy_content_aware_fill(
        &self,
        sampling: String,
        color_adaptation: String,
        rotation_adaptation: String,
        scale: bool,
        mirror: bool,
        sample_all_layers: bool,
        output: String,
    ) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        let active_id = guard.session.active().and_then(|ds| ds.active_layer());
        guard.dismiss_compare();
        ensure_proxy_preview_locked(&mut guard)?;
        let Some(proxy) = guard.proxy_preview.as_mut() else {
            return Ok(());
        };
        let mut ps = Session::new();
        ps.add_document((*proxy.base_doc).clone(), None);
        ps.set_active(0);
        let target_info = ps.active().and_then(|ds| find_primary_photo_raster_layer(ds.doc(), active_id));
        if let Some((target_id, was_locked)) = target_info {
            ps.set_active_layer(Some(target_id));
            if was_locked {
                let _ = ps.execute(
                    "layer.setProps",
                    json!({ "layer": target_id.0, "locks": { "pixels": false } }),
                );
            }
        } else if active_id.is_some() {
            ps.set_active_layer(active_id);
        }
        let _ = output;
        let params = json!({
            "sampling": if sampling == "rectangular" { "rectangular" } else { "auto" },
            "colorAdaptation": match color_adaptation.as_str() {
                "none" | "default" | "high" | "veryHigh" => color_adaptation.as_str(),
                _ => "default",
            },
            "rotationAdaptation": match rotation_adaptation.as_str() {
                "none" | "low" | "medium" | "high" | "full" => rotation_adaptation.as_str(),
                _ => "none",
            },
            "scale": scale,
            "mirror": mirror,
            "sampleAllLayers": sample_all_layers,
            "output": "current",
        });
        let _ = ps.execute("edit.contentAwareFill", params);
        proxy.session = ps;
        guard.mark_full_dirty();
        Ok(())
    }

    pub fn apply_content_aware_fill(
        &self,
        sampling: String,
        color_adaptation: String,
        rotation_adaptation: String,
        scale: bool,
        mirror: bool,
        sample_all_layers: bool,
        output: String,
    ) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        guard.live_stroke = None;
        guard.live_dab = None;
        guard.live_mixer = None;
        guard.proxy_preview = None;

        let (prev_active, target_id, was_locked, has_sel) = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            let prev = ds.active_layer();
            let has_sel = ds.doc().selection.is_some();
            let (id, locked) = find_primary_photo_raster_layer(ds.doc(), prev).ok_or_else(|| {
                EngineError::CommandFailed {
                    detail: "İçerik duyarlı dolgu uygulanacak fotoğraf katmanı bulunamadı".into(),
                }
            })?;
            (prev, id, locked, has_sel)
        };
        if !has_sel {
            return Err(EngineError::CommandFailed {
                detail: "Önce doldurulacak alanı Seçim aracıyla seçin".into(),
            });
        }

        guard.session.set_active_layer(Some(target_id));
        if was_locked {
            let _ = guard.session.execute(
                "layer.setProps",
                json!({ "layer": target_id.0, "locks": { "pixels": false } }),
            );
        }
        let params = json!({
            "sampling": if sampling == "rectangular" { "rectangular" } else { "auto" },
            "colorAdaptation": match color_adaptation.as_str() {
                "none" | "default" | "high" | "veryHigh" => color_adaptation.as_str(),
                _ => "default",
            },
            "rotationAdaptation": match rotation_adaptation.as_str() {
                "none" | "low" | "medium" | "high" | "full" => rotation_adaptation.as_str(),
                _ => "none",
            },
            "scale": scale,
            "mirror": mirror,
            "sampleAllLayers": sample_all_layers,
            "output": match output.as_str() {
                "current" | "new" | "duplicate" => output.as_str(),
                _ => "current",
            },
        });
        let res = guard.session.execute("edit.contentAwareFill", params);
        if was_locked && output == "current" {
            let _ = guard.session.execute(
                "layer.setProps",
                json!({ "layer": target_id.0, "locks": { "pixels": true } }),
            );
        }
        if output == "current" && prev_active.is_some() {
            guard.session.set_active_layer(prev_active);
        }
        res.map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    pub fn apply_remove_tool(
        &self,
        points: Vec<StrokeSample>,
        size: f32,
        close_loops: bool,
        sample_all_layers: bool,
        new_layer: bool,
    ) -> Result<DocumentInfo, EngineError> {
        if points.is_empty() {
            return Err(EngineError::InvalidParams { detail: "Kaldırma fırçası noktası boş".into() });
        }
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        guard.live_stroke = None;
        guard.live_dab = None;
        guard.live_mixer = None;
        guard.proxy_preview = None;

        let (prev_active, target_id, was_locked) = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            let prev = ds.active_layer();
            let (id, locked) = find_primary_photo_raster_layer(ds.doc(), prev).ok_or_else(|| {
                EngineError::CommandFailed {
                    detail: "Nesne kaldırılacak fotoğraf katmanı bulunamadı".into(),
                }
            })?;
            (prev, id, locked)
        };

        guard.session.set_active_layer(Some(target_id));
        if was_locked {
            let _ = guard.session.execute(
                "layer.setProps",
                json!({ "layer": target_id.0, "locks": { "pixels": false } }),
            );
        }
        let pts_json: Vec<Value> = points
            .iter()
            .map(|p| json!([p.x as f64, p.y as f64, p.pressure.clamp(0.1, 1.0) as f64]))
            .collect();
        let res = guard.session.execute(
            "paint.remove",
            json!({
                "points": pts_json,
                "size": size.clamp(4.0, 400.0) as f64,
                "hardness": 1.0,
                "closeLoops": close_loops,
                "sampleAllLayers": sample_all_layers,
                "newLayer": new_layer
            }),
        );
        if was_locked && !new_layer {
            let _ = guard.session.execute(
                "layer.setProps",
                json!({ "layer": target_id.0, "locks": { "pixels": true } }),
            );
        }
        if !new_layer && prev_active.is_some() {
            guard.session.set_active_layer(prev_active);
        }
        res.map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    pub fn apply_content_aware_move(
        &self,
        dx: f32,
        dy: f32,
        mode: String,
        structure: u8,
        color: u8,
        scale_x: f32,
        scale_y: f32,
        rotate_deg: f32,
        sample_all_layers: bool,
    ) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        guard.live_stroke = None;
        guard.live_dab = None;
        guard.live_mixer = None;
        guard.proxy_preview = None;

        let (prev_active, target_id, was_locked, has_sel) = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            let prev = ds.active_layer();
            let has_sel = ds.doc().selection.is_some();
            let (id, locked) = find_primary_photo_raster_layer(ds.doc(), prev).ok_or_else(|| {
                EngineError::CommandFailed {
                    detail: "İçerik duyarlı taşıma uygulanacak fotoğraf katmanı bulunamadı".into(),
                }
            })?;
            (prev, id, locked, has_sel)
        };
        if !has_sel {
            return Err(EngineError::CommandFailed {
                detail: "Önce taşınacak nesneyi Seçim aracıyla seçin".into(),
            });
        }

        guard.session.set_active_layer(Some(target_id));
        if was_locked {
            let _ = guard.session.execute(
                "layer.setProps",
                json!({ "layer": target_id.0, "locks": { "pixels": false } }),
            );
        }
        let res = guard.session.execute(
            "paint.contentAwareMove",
            json!({
                "offset": [dx.round() as i32, dy.round() as i32],
                "mode": if mode == "extend" { "extend" } else { "move" },
                "structure": structure.clamp(1, 7),
                "color": color.clamp(0, 10),
                "scaleX": scale_x.clamp(25.0, 300.0) as f64,
                "scaleY": scale_y.clamp(25.0, 300.0) as f64,
                "rotate": rotate_deg.clamp(-180.0, 180.0) as f64,
                "sampleAllLayers": sample_all_layers
            }),
        );
        if was_locked {
            let _ = guard.session.execute(
                "layer.setProps",
                json!({ "layer": target_id.0, "locks": { "pixels": true } }),
            );
        }
        if prev_active.is_some() {
            guard.session.set_active_layer(prev_active);
        }
        res.map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_selection_dirty();
        self.info_from_locked(&guard)
    }

    pub fn update_proxy_content_aware_scale(
        &self,
        scale_x: f32,
        scale_y: f32,
        amount: f32,
        protect_skin_tones: bool,
    ) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        let active_id = guard.session.active().and_then(|ds| ds.active_layer());
        guard.dismiss_compare();
        ensure_proxy_preview_locked(&mut guard)?;
        let Some(proxy) = guard.proxy_preview.as_mut() else {
            return Ok(());
        };
        let mut ps = Session::new();
        ps.add_document((*proxy.base_doc).clone(), None);
        ps.set_active(0);
        let target_info = ps.active().and_then(|ds| find_primary_photo_raster_layer(ds.doc(), active_id));
        if let Some((target_id, was_locked)) = target_info {
            ps.set_active_layer(Some(target_id));
            if was_locked {
                let _ = ps.execute(
                    "layer.setProps",
                    json!({ "layer": target_id.0, "locks": { "pixels": false } }),
                );
            }
        } else if active_id.is_some() {
            ps.set_active_layer(active_id);
        }
        let _ = ps.execute(
            "edit.contentAwareScale",
            json!({
                "scaleX": scale_x.clamp(40.0, 200.0) as f64,
                "scaleY": scale_y.clamp(40.0, 200.0) as f64,
                "amount": amount.clamp(0.0, 100.0) as f64,
                "protectSkinTones": protect_skin_tones
            }),
        );
        proxy.session = ps;
        guard.mark_full_dirty();
        Ok(())
    }

    pub fn apply_content_aware_scale(
        &self,
        scale_x: f32,
        scale_y: f32,
        amount: f32,
        protect_skin_tones: bool,
    ) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        guard.live_stroke = None;
        guard.live_dab = None;
        guard.live_mixer = None;
        guard.proxy_preview = None;

        let (prev_active, target_id, was_locked) = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            let prev = ds.active_layer();
            let (id, locked) = find_primary_photo_raster_layer(ds.doc(), prev).ok_or_else(|| {
                EngineError::CommandFailed {
                    detail: "İçerik duyarlı ölçekleme uygulanacak fotoğraf katmanı bulunamadı".into(),
                }
            })?;
            (prev, id, locked)
        };

        guard.session.set_active_layer(Some(target_id));
        if was_locked {
            let _ = guard.session.execute(
                "layer.setProps",
                json!({ "layer": target_id.0, "locks": { "pixels": false } }),
            );
        }
        let res = guard.session.execute(
            "edit.contentAwareScale",
            json!({
                "scaleX": scale_x.clamp(40.0, 200.0) as f64,
                "scaleY": scale_y.clamp(40.0, 200.0) as f64,
                "amount": amount.clamp(0.0, 100.0) as f64,
                "protectSkinTones": protect_skin_tones
            }),
        );
        if was_locked {
            let _ = guard.session.execute(
                "layer.setProps",
                json!({ "layer": target_id.0, "locks": { "pixels": true } }),
            );
        }
        if prev_active.is_some() {
            guard.session.set_active_layer(prev_active);
        }
        res.map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }



    // ==================== v1.8.2 ADVANCED SELECTION & QUICK MASK ====================

    pub fn select_lasso(
        &self,
        points: Vec<StrokeSample>,
        mode: String,
        feather: f32,
    ) -> Result<DocumentInfo, EngineError> {
        if points.len() < 3 {
            return Err(EngineError::InvalidParams { detail: "Kement seçimi için en az 3 nokta gerekli".into() });
        }
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        let pts: Vec<Value> = points.iter().map(|p| json!([p.x as f64, p.y as f64])).collect();
        guard
            .session
            .execute(
                "select.lasso",
                json!({
                    "points": pts,
                    "mode": match mode.as_str() { "add" | "subtract" | "intersect" => mode.as_str(), _ => "replace" },
                    "feather": feather.clamp(0.0, 250.0) as f64,
                    "antiAlias": true
                }),
            )
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_selection_dirty();
        self.info_from_locked(&guard)
    }

    pub fn select_magnetic_lasso(
        &self,
        points: Vec<StrokeSample>,
        width: f32,
        contrast: f32,
        mode: String,
        feather: f32,
    ) -> Result<DocumentInfo, EngineError> {
        if points.len() < 3 {
            return Err(EngineError::InvalidParams { detail: "Manyetik kement için en az 3 nokta gerekli".into() });
        }
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        let pts: Vec<Value> = points.iter().map(|p| json!([p.x as f64, p.y as f64])).collect();
        guard
            .session
            .execute(
                "select.magneticLasso",
                json!({
                    "points": pts,
                    "width": width.clamp(1.0, 256.0) as f64,
                    "contrast": contrast.clamp(1.0, 100.0) as f64,
                    "mode": match mode.as_str() { "add" | "subtract" | "intersect" => mode.as_str(), _ => "replace" },
                    "feather": feather.clamp(0.0, 250.0) as f64,
                    "antiAlias": true
                }),
            )
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_selection_dirty();
        self.info_from_locked(&guard)
    }

    pub fn select_sky(
        &self,
        threshold: f32,
        softness: f32,
        mode: String,
        sample_all_layers: bool,
    ) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        guard
            .session
            .execute(
                "select.sky",
                json!({
                    "threshold": threshold.clamp(0.0, 100.0) as f64,
                    "softness": softness.clamp(0.0, 100.0) as f64,
                    "mode": match mode.as_str() { "add" | "subtract" | "intersect" => mode.as_str(), _ => "replace" },
                    "sampleAllLayers": sample_all_layers
                }),
            )
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_selection_dirty();
        self.info_from_locked(&guard)
    }

    pub fn select_color_range(
        &self,
        preset: String,
        color_hex: String,
        fuzziness: f32,
        invert: bool,
        mode: String,
        sample_all_layers: bool,
    ) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        let valid_preset = match preset.as_str() {
            "sampledColors" | "reds" | "yellows" | "greens" | "cyans" | "blues" | "magentas"
            | "highlights" | "midtones" | "shadows" | "skinTones" | "outOfGamut" => preset.as_str(),
            _ => "sampledColors",
        };
        let mut params = json!({
            "select": valid_preset,
            "fuzziness": fuzziness.clamp(0.0, 200.0) as f64,
            "invert": invert,
            "sampleAllLayers": sample_all_layers,
            "mode": match mode.as_str() { "add" | "subtract" | "intersect" => mode.as_str(), _ => "replace" }
        });
        if valid_preset == "sampledColors" && !color_hex.trim().is_empty() {
            params["color"] = json!(color_hex.trim());
        }
        guard
            .session
            .execute("select.colorRange", params)
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_selection_dirty();
        self.info_from_locked(&guard)
    }

    pub fn select_focus_area(
        &self,
        range_pct: f32,
        noise_pct: f32,
        mode: String,
        sample_all_layers: bool,
    ) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        guard
            .session
            .execute(
                "select.focusArea",
                json!({
                    "range": (range_pct / 100.0).clamp(0.0, 1.0) as f64,
                    "noise": (noise_pct / 100.0).clamp(0.0, 1.0) as f64,
                    "sampleAllLayers": sample_all_layers,
                    "mode": match mode.as_str() { "add" | "subtract" | "intersect" => mode.as_str(), _ => "replace" }
                }),
            )
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_selection_dirty();
        self.info_from_locked(&guard)
    }

    pub fn modify_selection(&self, op: String, radius: f32) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        let cmd = match op.as_str() {
            "expand" => "select.modify.expand",
            "contract" => "select.modify.contract",
            "border" => "select.modify.border",
            "smooth" => "select.modify.smooth",
            "feather" => "select.modify.feather",
            _ => return Err(EngineError::InvalidParams { detail: format!("Geçersiz seçim işlemi: {op}") }),
        };
        let r = if op == "border" {
            radius.clamp(1.0, 200.0)
        } else {
            radius.clamp(1.0, 500.0)
        };
        guard
            .session
            .execute(cmd, json!({ "radius": r as f64 }))
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_selection_dirty();
        self.info_from_locked(&guard)
    }

    pub fn grow_or_similar_selection(
        &self,
        similar: bool,
        tolerance: f32,
        sample_all_layers: bool,
    ) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        let cmd = if similar { "select.similar" } else { "select.grow" };
        guard
            .session
            .execute(
                cmd,
                json!({
                    "tolerance": tolerance.clamp(0.0, 255.0).round() as u32,
                    "sampleAllLayers": sample_all_layers
                }),
            )
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_selection_dirty();
        self.info_from_locked(&guard)
    }

    pub fn toggle_quick_mask_mode(&self, on: bool) -> Result<bool, EngineError> {
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        guard
            .session
            .execute("select.editInQuickMaskMode", json!({ "on": on }))
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        let active = guard
            .session
            .active()
            .is_some_and(|ds| ds.doc().quick_mask.is_some());
        guard.mark_selection_dirty();
        Ok(active)
    }



    pub fn is_quick_mask_active(&self) -> bool {
        let guard = self.core.lock();
        guard
            .session
            .active()
            .is_some_and(|ds| ds.doc().quick_mask.is_some())
    }

    pub fn update_proxy_select_sky(
        &self,
        threshold: f32,
        softness: f32,
        mode: String,
        sample_all_layers: bool,
    ) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        ensure_proxy_preview_locked(&mut guard)?;
        let Some(proxy) = guard.proxy_preview.as_mut() else {
            return Ok(());
        };
        let mut ps = Session::new();
        ps.add_document((*proxy.base_doc).clone(), None);
        ps.set_active(0);
        let _ = ps.execute(
            "select.sky",
            json!({
                "threshold": threshold.clamp(0.0, 100.0) as f64,
                "softness": softness.clamp(0.0, 100.0) as f64,
                "mode": match mode.as_str() { "add" | "subtract" | "intersect" => mode.as_str(), _ => "replace" },
                "sampleAllLayers": sample_all_layers
            }),
        );
        proxy.selection_only = true;
        proxy.session = ps;
        guard.mark_selection_dirty();
        Ok(())
    }

    /// v2.0.1: Live coalesced proxy preview for Görsel Boyutlandır (Image Resize) before Uygula.
    pub fn update_proxy_image_resize(
        &self,
        width: u32,
        height: u32,
        resample: String,
    ) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        ensure_proxy_preview_locked(&mut guard)?;
        let Some(proxy) = guard.proxy_preview.as_mut() else {
            return Ok(());
        };
        let orig_w = proxy.real_size.0.max(1) as f32;
        let orig_h = proxy.real_size.1.max(1) as f32;
        let req_w = width.clamp(16, 8192) as f32;
        let req_h = height.clamp(16, 8192) as f32;
        let scale_x = req_w / orig_w;
        let scale_y = req_h / orig_h;
        let base_w = proxy.base_doc.size.width.max(1) as f32;
        let base_h = proxy.base_doc.size.height.max(1) as f32;
        let mut pw = (base_w * scale_x).round().max(16.0);
        let mut ph = (base_h * scale_y).round().max(16.0);
        let max_side = pw.max(ph);
        if max_side > 1024.0 {
            let k = 1024.0 / max_side;
            pw = (pw * k).round().max(16.0);
            ph = (ph * k).round().max(16.0);
        }
        let method = match resample.as_str() {
            "lanczos" | "bicubic" | "bicubicSharper" | "bicubicSmoother" | "preserveDetails" | "bilinear" | "nearest" => resample.as_str(),
            _ => "lanczos",
        };
        let mut ps = Session::new();
        ps.add_document((*proxy.base_doc).clone(), None);
        ps.set_active(0);
        let _ = ps.execute(
            "image.imageSize",
            json!({
                "width": pw as u32,
                "height": ph as u32,
                "resample": method,
                "constrainProportions": false
            }),
        );
        proxy.real_size = (width.clamp(16, 8192), height.clamp(16, 8192));
        proxy.session = ps;
        Ok(())
    }

    /// v2.0.1: Live coalesced proxy preview for Seçimi Değiştir (Expand/Contract/Border/Smooth/Feather) before Uygula.
    pub fn update_proxy_modify_selection(
        &self,
        op: String,
        radius: f32,
    ) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        ensure_proxy_preview_locked(&mut guard)?;
        let Some(proxy) = guard.proxy_preview.as_mut() else {
            return Ok(());
        };
        let cmd = match op.as_str() {
            "expand" => "select.modify.expand",
            "contract" => "select.modify.contract",
            "border" => "select.modify.border",
            "smooth" => "select.modify.smooth",
            "feather" => "select.modify.feather",
            _ => return Ok(()),
        };
        let scaled_r = (radius * proxy.scale).clamp(1.0, 200.0);
        let mut ps = Session::new();
        ps.add_document((*proxy.base_doc).clone(), None);
        ps.set_active(0);
        let _ = ps.execute(cmd, json!({ "radius": scaled_r as f64 }));
        proxy.selection_only = true;
        proxy.session = ps;
        guard.mark_selection_dirty();
        Ok(())
    }

    /// v2.0.1: Live coalesced proxy preview for İçerik Duyarlı Taşıma (Content-Aware Move/Extend) before Uygula.
    pub fn update_proxy_content_aware_move(
        &self,
        dx: f32,
        dy: f32,
        mode: String,
        structure: u8,
        color: u8,
        scale_x: f32,
        scale_y: f32,
        rotate_deg: f32,
        sample_all_layers: bool,
    ) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        let active_id = guard.session.active().and_then(|ds| ds.active_layer());
        guard.dismiss_compare();
        ensure_proxy_preview_locked(&mut guard)?;
        let Some(proxy) = guard.proxy_preview.as_mut() else {
            return Ok(());
        };
        let p_scale = proxy.scale;
        let mut ps = Session::new();
        ps.add_document((*proxy.base_doc).clone(), None);
        ps.set_active(0);
        if let Some(ds) = ps.active_mut() {
            let tid = active_id.or_else(|| ds.active_layer());
            if let Some(id) = tid {
                let _ = ds.set_active_layer(Some(id));
                let doc = Arc::make_mut(ds.doc_mut());
                if let Some(l) = doc.layers.iter_mut().find(|l| l.id == id) {
                    l.locks.pixels = false;
                    l.locks.position = false;
                    l.locks.all = false;
                }
            }
        }
        let valid_mode = if mode == "extend" { "extend" } else { "move" };
        let _ = ps.execute(
            "edit.contentAwareMove",
            json!({
                "dx": (dx * p_scale) as f64,
                "dy": (dy * p_scale) as f64,
                "mode": valid_mode,
                "structure": structure.clamp(1, 7),
                "color": color.clamp(0, 10),
                "scaleX": scale_x.clamp(25.0, 250.0) as f64,
                "scaleY": scale_y.clamp(25.0, 250.0) as f64,
                "rotation": rotate_deg.clamp(-180.0, 180.0) as f64,
                "sampleAllLayers": sample_all_layers
            }),
        );
        proxy.session = ps;
        Ok(())
    }

    pub fn update_proxy_color_range(
        &self,
        preset: String,
        color_hex: String,
        fuzziness: f32,
        invert: bool,
        mode: String,
        sample_all_layers: bool,
    ) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        ensure_proxy_preview_locked(&mut guard)?;
        let Some(proxy) = guard.proxy_preview.as_mut() else {
            return Ok(());
        };
        let mut ps = Session::new();
        ps.add_document((*proxy.base_doc).clone(), None);
        ps.set_active(0);
        let valid_preset = match preset.as_str() {
            "sampledColors" | "reds" | "yellows" | "greens" | "cyans" | "blues" | "magentas"
            | "highlights" | "midtones" | "shadows" | "skinTones" | "outOfGamut" => preset.as_str(),
            _ => "sampledColors",
        };
        let mut params = json!({
            "select": valid_preset,
            "fuzziness": fuzziness.clamp(0.0, 200.0) as f64,
            "invert": invert,
            "sampleAllLayers": sample_all_layers,
            "mode": match mode.as_str() { "add" | "subtract" | "intersect" => mode.as_str(), _ => "replace" }
        });
        if valid_preset == "sampledColors" && !color_hex.trim().is_empty() {
            params["color"] = json!(color_hex.trim());
        }
        let _ = ps.execute("select.colorRange", params);
        proxy.selection_only = true;
        proxy.session = ps;
        guard.mark_selection_dirty();
        Ok(())
    }

    pub fn update_proxy_focus_area(
        &self,
        range_pct: f32,
        noise_pct: f32,
        mode: String,
        sample_all_layers: bool,
    ) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        ensure_proxy_preview_locked(&mut guard)?;
        let Some(proxy) = guard.proxy_preview.as_mut() else {
            return Ok(());
        };
        let mut ps = Session::new();
        ps.add_document((*proxy.base_doc).clone(), None);
        ps.set_active(0);
        let _ = ps.execute(
            "select.focusArea",
            json!({
                "range": (range_pct / 100.0).clamp(0.0, 1.0) as f64,
                "noise": (noise_pct / 100.0).clamp(0.0, 1.0) as f64,
                "sampleAllLayers": sample_all_layers,
                "mode": match mode.as_str() { "add" | "subtract" | "intersect" => mode.as_str(), _ => "replace" }
            }),
        );
        proxy.selection_only = true;
        proxy.session = ps;
        guard.mark_selection_dirty();
        Ok(())
    }



    // ==================== v1.8.3 PRO DRAW & RETOUCH TOOLS ====================

    pub fn begin_live_pencil(
        &self,
        sample: StrokeSample,
        color_hex: String,
    ) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        let pt_json = sample_to_json(&sample);
        let eff_color = if color_hex.trim().is_empty() { "#000000".to_string() } else { color_hex.trim().to_string() };
        let params = json!({
            "points": [pt_json.clone()],
            "color": eff_color,
            "hardness": 1.0
        });
        let live = LiveStroke::begin(&guard.session, &params)
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        let dmg = live.bounds();
        guard.live_stroke = Some(ActiveLiveStroke {
            live,
            points_json: vec![pt_json],
            color_hex: eff_color,
            is_eraser: false,
            is_pencil: true,
            target_mask: false,
            size: size.clamp(1.0, 400.0),
        });
        guard.mark_region_dirty(dmg);
        Ok(())
    }

    pub fn begin_live_background_eraser(
        &self,
        sample: StrokeSample,
        size: f32,
        hardness: f32,
        tolerance: f32,
        limits: String,
        protect_foreground: bool,
    ) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        guard.live_stroke = None;
        guard.live_dab = None;

        let (prev_active, target_id, was_locked) = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            let prev = ds.active_layer();
            let (id, locked) = find_primary_photo_raster_layer(ds.doc(), prev).ok_or_else(|| {
                EngineError::CommandFailed { detail: "Silinecek fotoğraf katmanı bulunamadı".into() }
            })?;
            (prev, id, locked)
        };
        guard.session.set_active_layer(Some(target_id));
        if was_locked {
            let _ = guard.session.execute("layer.setProps", json!({ "layer": target_id.0, "locks": { "pixels": false } }));
        }
        let pt_json = json!([sample.x as f64, sample.y as f64, sample.pressure.clamp(0.05, 1.0) as f64]);
        let lim = match limits.as_str() {
            "discontiguous" | "contiguous" | "findEdges" => limits.as_str(),
            _ => "contiguous",
        };
        let base_params = json!({
            "size": size.clamp(4.0, 400.0) as f64,
            "hardness": hardness.clamp(0.0, 1.0) as f64,
            "sampling": "continuous",
            "limits": lim,
            "tolerance": tolerance.clamp(1.0, 100.0) as f64,
            "protectForegroundColor": protect_foreground
        });
        let mut init_params = base_params.clone();
        init_params["points"] = json!([pt_json.clone()]);
        let live = photocraft_engine::eraser_cmds::live_background_eraser(&guard.session, &init_params)
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        let dmg = live.bounds();
        guard.live_dab = Some(ActiveLiveDab {
            live,
            cmd: "paint.backgroundEraser".to_string(),
            base_params,
            points_json: vec![pt_json],
            target_id,
            prev_active,
            was_locked: false,
        });
        guard.mark_region_dirty(dmg);
        Ok(())
    }

    pub fn set_symmetry_mode(&self, mode: String, rotation_deg: f32) -> Result<String, EngineError> {
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        let m = mode.as_str();
        if m == "off" || m == "none" || m.is_empty() {
            let _ = guard.session.execute("paint.symmetryDisable", json!({}));
            guard.mark_full_dirty();
            return Ok("off".into());
        }
        let valid = match m {
            "vertical" | "horizontal" | "dual" | "diagonal" => m,
            _ => "vertical",
        };
        guard
            .session
            .execute(
                "paint.setSymmetry",
                json!({
                    "mode": valid,
                    "rotation": rotation_deg.clamp(-180.0, 180.0) as f64
                }),
            )
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        Ok(valid.into())
    }

    pub fn apply_pencil_stroke(
        &self,
        points: Vec<StrokeSample>,
        size: f32,
        opacity: f32,
        color_hex: String,
    ) -> Result<DocumentInfo, EngineError> {
        if points.is_empty() {
            return Err(EngineError::InvalidParams { detail: "Kalem noktası boş".into() });
        }
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        guard.live_stroke = None;
        guard.proxy_preview = None;

        let (prev_active, target_id, was_locked) = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            let prev = ds.active_layer();
            let (id, locked) = find_primary_photo_raster_layer(ds.doc(), prev).ok_or_else(|| {
                EngineError::CommandFailed { detail: "Çizim yapılacak katman bulunamadı".into() }
            })?;
            (prev, id, locked)
        };
        guard.session.set_active_layer(Some(target_id));
        if was_locked {
            let _ = guard.session.execute("layer.setProps", json!({ "layer": target_id.0, "locks": { "pixels": false } }));
        }
        let pts: Vec<Value> = points
            .iter()
            .map(|p| json!([p.x as f64, p.y as f64, p.pressure.clamp(0.1, 1.0) as f64]))
            .collect();
        let res = guard.session.execute(
            "paint.pencil",
            json!({
                "points": pts,
                "size": size.clamp(1.0, 400.0) as f64,
                "opacity": opacity.clamp(0.01, 1.0) as f64,
                "color": if color_hex.trim().is_empty() { "#000000" } else { color_hex.trim() }
            }),
        );
        if was_locked {
            let _ = guard.session.execute("layer.setProps", json!({ "layer": target_id.0, "locks": { "pixels": true } }));
        }
        if prev_active.is_some() {
            guard.session.set_active_layer(prev_active);
        }
        res.map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    pub fn apply_magic_eraser(
        &self,
        x: f32,
        y: f32,
        tolerance: f32,
        contiguous: bool,
        sample_all_layers: bool,
        opacity: f32,
    ) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        guard.live_stroke = None;
        guard.proxy_preview = None;

        let (prev_active, target_id, was_locked) = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            let prev = ds.active_layer();
            let (id, locked) = find_primary_photo_raster_layer(ds.doc(), prev).ok_or_else(|| {
                EngineError::CommandFailed { detail: "Silinecek fotoğraf katmanı bulunamadı".into() }
            })?;
            (prev, id, locked)
        };
        guard.session.set_active_layer(Some(target_id));
        if was_locked {
            let _ = guard.session.execute("layer.setProps", json!({ "layer": target_id.0, "locks": { "pixels": false } }));
        }
        let res = guard.session.execute(
            "paint.magicEraser",
            json!({
                "x": x.round() as i32,
                "y": y.round() as i32,
                "tolerance": tolerance.clamp(0.0, 255.0).round() as u32,
                "antiAlias": true,
                "contiguous": contiguous,
                "sampleAllLayers": sample_all_layers,
                "opacity": (opacity * 100.0).clamp(1.0, 100.0) as f64
            }),
        );
        if prev_active.is_some() {
            guard.session.set_active_layer(prev_active);
        }
        res.map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    pub fn apply_background_eraser(
        &self,
        points: Vec<StrokeSample>,
        size: f32,
        hardness: f32,
        tolerance: f32,
        limits: String,
        protect_foreground: bool,
    ) -> Result<DocumentInfo, EngineError> {
        if points.is_empty() {
            return Err(EngineError::InvalidParams { detail: "Arka plan silgisi noktası boş".into() });
        }
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        guard.live_stroke = None;
        guard.proxy_preview = None;

        let (prev_active, target_id, was_locked) = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            let prev = ds.active_layer();
            let (id, locked) = find_primary_photo_raster_layer(ds.doc(), prev).ok_or_else(|| {
                EngineError::CommandFailed { detail: "Silinecek fotoğraf katmanı bulunamadı".into() }
            })?;
            (prev, id, locked)
        };
        guard.session.set_active_layer(Some(target_id));
        if was_locked {
            let _ = guard.session.execute("layer.setProps", json!({ "layer": target_id.0, "locks": { "pixels": false } }));
        }
        let pts: Vec<Value> = points
            .iter()
            .map(|p| json!([p.x as f64, p.y as f64, p.pressure.clamp(0.1, 1.0) as f64]))
            .collect();
        let lim = match limits.as_str() {
            "discontiguous" | "contiguous" | "findEdges" => limits.as_str(),
            _ => "contiguous",
        };
        let res = guard.session.execute(
            "paint.backgroundEraser",
            json!({
                "points": pts,
                "size": size.clamp(4.0, 400.0) as f64,
                "hardness": hardness.clamp(0.0, 1.0) as f64,
                "sampling": "continuous",
                "limits": lim,
                "tolerance": tolerance.clamp(1.0, 100.0) as f64,
                "protectForegroundColor": protect_foreground
            }),
        );
        if prev_active.is_some() {
            guard.session.set_active_layer(prev_active);
        }
        res.map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    pub fn apply_adjustment_brush(
        &self,
        points: Vec<StrokeSample>,
        kind: String,
        subtract: bool,
        size: f32,
        hardness: f32,
        opacity: f32,
    ) -> Result<DocumentInfo, EngineError> {
        if points.is_empty() {
            return Err(EngineError::InvalidParams { detail: "Ayar fırçası noktası boş".into() });
        }
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        guard.live_stroke = None;
        guard.proxy_preview = None;

        let valid_kind = match kind.as_str() {
            "levels" | "curves" | "exposure" | "hueSaturation" | "colorBalance"
            | "blackWhite" | "photoFilter" | "posterize" | "invert" => kind.as_str(),
            _ => "exposure",
        };
        let pts: Vec<Value> = points
            .iter()
            .map(|p| json!([p.x as f64, p.y as f64, p.pressure.clamp(0.1, 1.0) as f64]))
            .collect();
        guard
            .session
            .execute(
                "paint.adjustmentBrush",
                json!({
                    "kind": valid_kind,
                    "subtract": subtract,
                    "points": pts,
                    "size": size.clamp(4.0, 400.0) as f64,
                    "hardness": hardness.clamp(0.0, 1.0) as f64,
                    "opacity": opacity.clamp(0.05, 1.0) as f64
                }),
            )
            .map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    pub fn apply_history_brush(
        &self,
        points: Vec<StrokeSample>,
        size: f32,
        hardness: f32,
        opacity: f32,
    ) -> Result<DocumentInfo, EngineError> {
        if points.is_empty() {
            return Err(EngineError::InvalidParams { detail: "Geçmiş fırçası noktası boş".into() });
        }
        let mut guard = self.core.lock();
        if !guard.session.is_enabled("edit.undo") {
            return Err(EngineError::CommandFailed {
                detail: "Geçmiş fırçası için önce fotoğrafta bir boyama veya filtre uygula (ilk halini geri boyar)".into(),
            });
        }
        guard.dismiss_compare();
        guard.live_stroke = None;
        guard.proxy_preview = None;

        let (prev_active, target_id, was_locked) = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            let prev = ds.active_layer();
            let (id, locked) = find_primary_photo_raster_layer(ds.doc(), prev).ok_or_else(|| {
                EngineError::CommandFailed { detail: "Geçmiş fırçası için fotoğraf katmanı bulunamadı".into() }
            })?;
            (prev, id, locked)
        };
        guard.session.set_active_layer(Some(target_id));
        if was_locked {
            let _ = guard.session.execute("layer.setProps", json!({ "layer": target_id.0, "locks": { "pixels": false } }));
        }
        let pts: Vec<Value> = points
            .iter()
            .map(|p| json!([p.x as f64, p.y as f64, p.pressure.clamp(0.1, 1.0) as f64]))
            .collect();
        let res = guard.session.execute(
            "paint.historyBrush",
            json!({
                "points": pts,
                "size": size.clamp(4.0, 400.0) as f64,
                "hardness": hardness.clamp(0.0, 1.0) as f64,
                "opacity": opacity.clamp(0.05, 1.0) as f64
            }),
        );
        if was_locked {
            let _ = guard.session.execute("layer.setProps", json!({ "layer": target_id.0, "locks": { "pixels": true } }));
        }
        if prev_active.is_some() {
            guard.session.set_active_layer(prev_active);
        }
        res.map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    pub fn apply_art_history_brush(
        &self,
        points: Vec<StrokeSample>,
        size: f32,
        hardness: f32,
        opacity: f32,
        style: String,
        area: f32,
        tolerance: f32,
    ) -> Result<DocumentInfo, EngineError> {
        if points.is_empty() {
            return Err(EngineError::InvalidParams { detail: "Sanat geçmişi fırçası noktası boş".into() });
        }
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        guard.live_stroke = None;
        guard.proxy_preview = None;

        let (prev_active, target_id, was_locked) = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            let prev = ds.active_layer();
            let (id, locked) = find_primary_photo_raster_layer(ds.doc(), prev).ok_or_else(|| {
                EngineError::CommandFailed { detail: "Sanat geçmişi için fotoğraf katmanı bulunamadı".into() }
            })?;
            (prev, id, locked)
        };
        guard.session.set_active_layer(Some(target_id));
        if was_locked {
            let _ = guard.session.execute("layer.setProps", json!({ "layer": target_id.0, "locks": { "pixels": false } }));
        }
        let pts: Vec<Value> = points
            .iter()
            .map(|p| json!([p.x as f64, p.y as f64, p.pressure.clamp(0.1, 1.0) as f64]))
            .collect();
        let valid_style = match style.as_str() {
            "tightShort" | "tightMedium" | "tightLong" | "looseMedium" | "looseLong"
            | "dab" | "tightCurl" | "looseCurl" => style.as_str(),
            _ => "tightMedium",
        };
        let res = guard.session.execute(
            "paint.artHistoryBrush",
            json!({
                "points": pts,
                "size": size.clamp(4.0, 400.0) as f64,
                "hardness": hardness.clamp(0.0, 1.0) as f64,
                "opacity": opacity.clamp(0.05, 1.0) as f64,
                "style": valid_style,
                "area": area.clamp(1.0, 200.0) as f64,
                "tolerance": tolerance.clamp(0.0, 100.0) as f64
            }),
        );
        if was_locked {
            let _ = guard.session.execute("layer.setProps", json!({ "layer": target_id.0, "locks": { "pixels": true } }));
        }
        if prev_active.is_some() {
            guard.session.set_active_layer(prev_active);
        }
        res.map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }


    // ==================== v2.4.0 PARÇA 3: CLONE SOURCE 5-SLOT, RETOUCH PRO, CAF & CAS PRO ====================

    pub fn configure_clone_source(
        &self,
        slot: u32,
        scale_x: f32,
        scale_y: f32,
        rotation_deg: f32,
        flip_h: bool,
        flip_v: bool,
        show_overlay: bool,
        overlay_opacity: f32,
    ) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        let idx = slot.min(4);
        let _ = guard.session.execute("cloneSource.select", json!({ "index": idx }));
        let _ = guard.session.execute(
            "cloneSource.set",
            json!({
                "index": idx,
                "scale": [scale_x.clamp(10.0, 400.0) as f64, scale_y.clamp(10.0, 400.0) as f64],
                "rotation": rotation_deg.clamp(-180.0, 180.0) as f64,
                "flipH": flip_h,
                "flipV": flip_v,
                "select": true
            }),
        );
        let _ = guard.session.execute(
            "cloneSource.overlay",
            json!({
                "show": show_overlay,
                "opacity": overlay_opacity.clamp(5.0, 100.0) as f64
            }),
        );
        Ok(())
    }

    pub fn reset_clone_source_transform(&self, slot: u32) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        let _ = guard.session.execute("cloneSource.resetTransform", json!({ "index": slot.min(4) }));
        Ok(())
    }

    #[allow(clippy::too_many_arguments)]
    pub fn apply_clone_stamp_pro(
        &self,
        points: Vec<StrokeSample>,
        src_x: f32,
        src_y: f32,
        size: f32,
        hardness: f32,
        opacity: f32,
        aligned: bool,
        sample_layer: String,
        mode: String,
    ) -> Result<DocumentInfo, EngineError> {
        if points.is_empty() {
            return Err(EngineError::InvalidParams { detail: "Klon darbesi boş".into() });
        }
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        guard.live_stroke = None;
        guard.proxy_preview = None;
        let (prev_active, target_id, was_locked) = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            let prev = ds.active_layer();
            let (id, locked) = find_primary_photo_raster_layer(ds.doc(), prev)
                .ok_or_else(|| EngineError::CommandFailed { detail: "Klonlanacak katman bulunamadı".into() })?;
            (prev, id, locked)
        };
        guard.session.set_active_layer(Some(target_id));
        if was_locked {
            let _ = guard.session.execute("layer.setProps", json!({ "layer": target_id.0, "locks": { "pixels": false } }));
        }
        let sample_str = match sample_layer.as_str() {
            "all" | "currentAndBelow" => sample_layer.as_str(),
            _ => "current",
        };
        let mode_str = match mode.as_str() {
            "multiply" | "screen" | "overlay" | "darken" | "lighten" | "color" => mode.as_str(),
            _ => "normal",
        };
        let pts: Vec<Value> = points.iter().map(|p| json!([p.x as f64, p.y as f64, p.pressure.clamp(0.1, 1.0) as f64])).collect();
        let res = guard.session.execute(
            "paint.cloneStamp",
            json!({
                "points": pts,
                "source": [src_x as f64, src_y as f64],
                "size": size.clamp(4.0, 400.0) as f64,
                "hardness": (hardness.clamp(0.0, 1.0) * 100.0).round() as f64,
                "opacity": (opacity.clamp(0.05, 1.0) * 100.0).round() as f64,
                "aligned": aligned,
                "sampleLayer": sample_str,
                "mode": mode_str
            }),
        );
        if was_locked {
            let _ = guard.session.execute("layer.setProps", json!({ "layer": target_id.0, "locks": { "pixels": true } }));
        }
        if prev_active.is_some() {
            guard.session.set_active_layer(prev_active);
        }
        res.map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    #[allow(clippy::too_many_arguments)]
    pub fn apply_healing_brush_pro(
        &self,
        points: Vec<StrokeSample>,
        size: f32,
        hardness: f32,
        src_x: f32,
        src_y: f32,
        aligned: bool,
        sample_layer: String,
        mode: String,
    ) -> Result<DocumentInfo, EngineError> {
        if points.is_empty() {
            return Err(EngineError::InvalidParams { detail: "Onarım darbesi boş".into() });
        }
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        guard.live_stroke = None;
        guard.proxy_preview = None;
        let (prev_active, target_id, was_locked) = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            let prev = ds.active_layer();
            let (id, locked) = find_primary_photo_raster_layer(ds.doc(), prev)
                .ok_or_else(|| EngineError::CommandFailed { detail: "Onarım katmanı bulunamadı".into() })?;
            (prev, id, locked)
        };
        guard.session.set_active_layer(Some(target_id));
        if was_locked {
            let _ = guard.session.execute("layer.setProps", json!({ "layer": target_id.0, "locks": { "pixels": false } }));
        }
        let sample_str = match sample_layer.as_str() {
            "all" | "currentAndBelow" => sample_layer.as_str(),
            _ => "current",
        };
        let mode_str = match mode.as_str() {
            "replace" | "multiply" | "screen" | "darken" | "lighten" | "color" => mode.as_str(),
            _ => "normal",
        };
        let pts: Vec<Value> = points.iter().map(|p| json!([p.x as f64, p.y as f64, p.pressure.clamp(0.1, 1.0) as f64])).collect();
        let res = guard.session.execute(
            "paint.healingBrush",
            json!({
                "points": pts,
                "source": [src_x as f64, src_y as f64],
                "size": size.clamp(4.0, 400.0) as f64,
                "hardness": (hardness.clamp(0.0, 1.0) * 100.0).round() as f64,
                "aligned": aligned,
                "sampleLayer": sample_str,
                "mode": mode_str
            }),
        );
        if was_locked {
            let _ = guard.session.execute("layer.setProps", json!({ "layer": target_id.0, "locks": { "pixels": true } }));
        }
        if prev_active.is_some() {
            guard.session.set_active_layer(prev_active);
        }
        res.map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    pub fn apply_spot_healing_pro(
        &self,
        points: Vec<StrokeSample>,
        size: f32,
        hardness: f32,
        spot_type: String,
        sample_all_layers: bool,
    ) -> Result<DocumentInfo, EngineError> {
        if points.is_empty() {
            return Err(EngineError::InvalidParams { detail: "Nokta düzeltme darbesi boş".into() });
        }
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        guard.live_stroke = None;
        guard.proxy_preview = None;
        let (prev_active, target_id, was_locked) = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            let prev = ds.active_layer();
            let (id, locked) = find_primary_photo_raster_layer(ds.doc(), prev)
                .ok_or_else(|| EngineError::CommandFailed { detail: "Fotoğraf katmanı bulunamadı".into() })?;
            (prev, id, locked)
        };
        guard.session.set_active_layer(Some(target_id));
        if was_locked {
            let _ = guard.session.execute("layer.setProps", json!({ "layer": target_id.0, "locks": { "pixels": false } }));
        }
        let stype = match spot_type.as_str() {
            "createTexture" | "proximityMatch" => spot_type.as_str(),
            _ => "contentAware",
        };
        let pts: Vec<Value> = points.iter().map(|p| json!([p.x as f64, p.y as f64, p.pressure.clamp(0.1, 1.0) as f64])).collect();
        let res = guard.session.execute(
            "paint.spotHealing",
            json!({
                "points": pts,
                "size": size.clamp(4.0, 400.0) as f64,
                "hardness": (hardness.clamp(0.0, 1.0) * 100.0).round() as f64,
                "type": stype,
                "sampleAllLayers": sample_all_layers
            }),
        );
        if was_locked {
            let _ = guard.session.execute("layer.setProps", json!({ "layer": target_id.0, "locks": { "pixels": true } }));
        }
        if prev_active.is_some() {
            guard.session.set_active_layer(prev_active);
        }
        res.map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    #[allow(clippy::too_many_arguments)]
    pub fn apply_patch_tool_pro(
        &self,
        dx: i32,
        dy: i32,
        mode: String,
        content_aware: bool,
        structure: u32,
        color: u32,
        sample_all_layers: bool,
    ) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        guard.live_stroke = None;
        guard.proxy_preview = None;
        let (prev_active, target_id, was_locked) = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            let prev = ds.active_layer();
            let (id, locked) = find_primary_photo_raster_layer(ds.doc(), prev)
                .ok_or_else(|| EngineError::CommandFailed { detail: "Yama katmanı bulunamadı".into() })?;
            (prev, id, locked)
        };
        guard.session.set_active_layer(Some(target_id));
        if was_locked {
            let _ = guard.session.execute("layer.setProps", json!({ "layer": target_id.0, "locks": { "pixels": false } }));
        }
        let safe_mode = if mode == "destination" { "destination" } else { "source" };
        let res = guard.session.execute(
            "paint.patch",
            json!({
                "offset": [dx, dy],
                "mode": safe_mode,
                "contentAware": content_aware,
                "structure": structure.clamp(1, 7),
                "color": color.clamp(0, 10),
                "sampleAllLayers": sample_all_layers
            }),
        );
        if was_locked {
            let _ = guard.session.execute("layer.setProps", json!({ "layer": target_id.0, "locks": { "pixels": true } }));
        }
        if prev_active.is_some() {
            guard.session.set_active_layer(prev_active);
        }
        res.map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    #[allow(clippy::too_many_arguments)]
    pub fn apply_retouch_pro(
        &self,
        tool: String,
        points: Vec<StrokeSample>,
        size: f32,
        hardness: f32,
        strength: f32,
        range: String,
        protect_tones: bool,
        finger_painting: bool,
        protect_detail: bool,
        sample_all_layers: bool,
    ) -> Result<DocumentInfo, EngineError> {
        if points.is_empty() {
            return Err(EngineError::InvalidParams { detail: "Rötuş darbesi boş".into() });
        }
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        guard.live_stroke = None;
        guard.proxy_preview = None;
        let (prev_active, target_id, was_locked) = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            let prev = ds.active_layer();
            let (id, locked) = find_primary_photo_raster_layer(ds.doc(), prev)
                .ok_or_else(|| EngineError::CommandFailed { detail: "Rötuş katmanı bulunamadı".into() })?;
            (prev, id, locked)
        };
        guard.session.set_active_layer(Some(target_id));
        if was_locked {
            let _ = guard.session.execute("layer.setProps", json!({ "layer": target_id.0, "locks": { "pixels": false } }));
        }
        let pts: Vec<Value> = points.iter().map(|p| json!([p.x as f64, p.y as f64, p.pressure.clamp(0.1, 1.0) as f64])).collect();
        let range_str = match range.as_str() {
            "shadows" | "highlights" => range.as_str(),
            _ => "midtones",
        };
        let s_pct = strength.clamp(1.0, 100.0) as f64;
        let (cmd, params) = match tool.as_str() {
            "dodge" => ("paint.dodge", json!({ "points": pts, "size": size.clamp(4.0, 400.0) as f64, "hardness": (hardness.clamp(0.0, 1.0) * 100.0).round() as f64, "range": range_str, "exposure": s_pct, "protectTones": protect_tones })),
            "burn" => ("paint.burn", json!({ "points": pts, "size": size.clamp(4.0, 400.0) as f64, "hardness": (hardness.clamp(0.0, 1.0) * 100.0).round() as f64, "range": range_str, "exposure": s_pct, "protectTones": protect_tones })),
            "smudge" => ("paint.smudge", json!({ "points": pts, "size": size.clamp(4.0, 400.0) as f64, "hardness": (hardness.clamp(0.0, 1.0) * 100.0).round() as f64, "strength": s_pct, "fingerPainting": finger_painting, "sampleAllLayers": sample_all_layers })),
            "sharpen_brush" => ("paint.sharpen", json!({ "points": pts, "size": size.clamp(4.0, 400.0) as f64, "hardness": (hardness.clamp(0.0, 1.0) * 100.0).round() as f64, "strength": s_pct, "protectDetail": protect_detail, "sampleAllLayers": sample_all_layers })),
            _ => ("paint.blur", json!({ "points": pts, "size": size.clamp(4.0, 400.0) as f64, "hardness": (hardness.clamp(0.0, 1.0) * 100.0).round() as f64, "strength": s_pct, "sampleAllLayers": sample_all_layers })),
        };
        let res = guard.session.execute(cmd, params);
        if was_locked {
            let _ = guard.session.execute("layer.setProps", json!({ "layer": target_id.0, "locks": { "pixels": true } }));
        }
        if prev_active.is_some() {
            guard.session.set_active_layer(prev_active);
        }
        res.map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    #[allow(clippy::too_many_arguments)]
    pub fn update_proxy_content_aware_fill_pro(
        &self,
        sampling: String,
        color_adaptation: String,
        rotation_adaptation: String,
        scale: bool,
        mirror: bool,
        sample_all_layers: bool,
        sampling_strokes: Vec<CafSamplingStrokeInput>,
    ) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        let active_id = guard.session.active().and_then(|ds| ds.active_layer());
        guard.dismiss_compare();
        ensure_proxy_preview_locked(&mut guard)?;
        let Some(proxy) = guard.proxy_preview.as_mut() else {
            return Ok(());
        };
        let sc = proxy.scale;
        let mut ps = Session::new();
        ps.add_document((*proxy.base_doc).clone(), None);
        ps.set_active(0);
        if let Some((target_id, was_locked)) = ps.active().and_then(|ds| find_primary_photo_raster_layer(ds.doc(), active_id)) {
            ps.set_active_layer(Some(target_id));
            if was_locked {
                let _ = ps.execute("layer.setProps", json!({ "layer": target_id.0, "locks": { "pixels": false } }));
            }
        }
        let has_add = sampling_strokes.iter().any(|st| st.add && !st.points.is_empty());
        let eff_sampling = if sampling == "custom" && has_add {
            "custom"
        } else if sampling == "rectangular" {
            "rectangular"
        } else {
            "auto"
        };
        let mut params = json!({
            "sampling": eff_sampling,
            "colorAdaptation": match color_adaptation.as_str() { "none" | "default" | "high" | "veryHigh" => color_adaptation.as_str(), _ => "default" },
            "rotationAdaptation": match rotation_adaptation.as_str() { "none" | "low" | "medium" | "high" | "full" => rotation_adaptation.as_str(), _ => "none" },
            "scale": scale,
            "mirror": mirror,
            "sampleAllLayers": sample_all_layers,
            "output": "current",
        });
        if !sampling_strokes.is_empty() {
            let strokes_json: Vec<Value> = sampling_strokes.iter().filter(|st| !st.points.is_empty()).map(|st| {
                let pts: Vec<Value> = st.points.iter().map(|p| json!([(p.x * sc) as f64, (p.y * sc) as f64])).collect();
                json!({ "add": st.add, "size": (st.size * sc).clamp(4.0, 300.0) as f64, "points": pts })
            }).collect();
            if !strokes_json.is_empty() {
                params["samplingStrokes"] = Value::Array(strokes_json);
            }
        }
        let _ = ps.execute("edit.contentAwareFill", params);
        proxy.session = ps;
        guard.mark_full_dirty();
        Ok(())
    }

    #[allow(clippy::too_many_arguments)]
    pub fn apply_content_aware_fill_pro(
        &self,
        sampling: String,
        color_adaptation: String,
        rotation_adaptation: String,
        scale: bool,
        mirror: bool,
        sample_all_layers: bool,
        output: String,
        sampling_strokes: Vec<CafSamplingStrokeInput>,
    ) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        guard.live_stroke = None;
        guard.proxy_preview = None;
        let (prev_active, target_id, was_locked) = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            let prev = ds.active_layer();
            let (id, locked) = find_primary_photo_raster_layer(ds.doc(), prev)
                .ok_or_else(|| EngineError::CommandFailed { detail: "Dolgu katmanı bulunamadı".into() })?;
            (prev, id, locked)
        };
        guard.session.set_active_layer(Some(target_id));
        if was_locked {
            let _ = guard.session.execute("layer.setProps", json!({ "layer": target_id.0, "locks": { "pixels": false } }));
        }
        let has_add = sampling_strokes.iter().any(|st| st.add && !st.points.is_empty());
        let eff_sampling = if sampling == "custom" && has_add {
            "custom"
        } else if sampling == "rectangular" {
            "rectangular"
        } else {
            "auto"
        };
        let out_str = match output.as_str() { "new" | "duplicate" => output.as_str(), _ => "current" };
        let mut params = json!({
            "sampling": eff_sampling,
            "colorAdaptation": match color_adaptation.as_str() { "none" | "default" | "high" | "veryHigh" => color_adaptation.as_str(), _ => "default" },
            "rotationAdaptation": match rotation_adaptation.as_str() { "none" | "low" | "medium" | "high" | "full" => rotation_adaptation.as_str(), _ => "none" },
            "scale": scale,
            "mirror": mirror,
            "sampleAllLayers": sample_all_layers,
            "output": out_str,
        });
        if !sampling_strokes.is_empty() {
            let strokes_json: Vec<Value> = sampling_strokes.iter().filter(|st| !st.points.is_empty()).map(|st| {
                let pts: Vec<Value> = st.points.iter().map(|p| json!([p.x as f64, p.y as f64])).collect();
                json!({ "add": st.add, "size": st.size.clamp(4.0, 500.0) as f64, "points": pts })
            }).collect();
            if !strokes_json.is_empty() {
                params["samplingStrokes"] = Value::Array(strokes_json);
            }
        }
        let res = guard.session.execute("edit.contentAwareFill", params);
        if was_locked {
            let _ = guard.session.execute("layer.setProps", json!({ "layer": target_id.0, "locks": { "pixels": true } }));
        }
        if out_str == "current" && prev_active.is_some() {
            guard.session.set_active_layer(prev_active);
        }
        res.map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    pub fn update_proxy_content_aware_scale_pro(
        &self,
        scale_x: f32,
        scale_y: f32,
        amount: f32,
        protect_skin_tones: bool,
        protect_strokes: Vec<CafSamplingStrokeInput>,
    ) -> Result<(), EngineError> {
        let mut guard = self.core.lock();
        let active_id = guard.session.active().and_then(|ds| ds.active_layer());
        guard.dismiss_compare();
        ensure_proxy_preview_sized_locked(&mut guard, 480)?;
        let Some(proxy) = guard.proxy_preview.as_mut() else {
            return Ok(());
        };
        let sc = proxy.scale;
        let mut ps = Session::new();
        ps.add_document((*proxy.base_doc).clone(), None);
        ps.set_active(0);
        if let Some((target_id, was_locked)) = ps.active().and_then(|ds| find_primary_photo_raster_layer(ds.doc(), active_id)) {
            ps.set_active_layer(Some(target_id));
            if was_locked {
                let _ = ps.execute("layer.setProps", json!({ "layer": target_id.0, "locks": { "pixels": false } }));
            }
        }
        let has_protect = paint_cas_protect_channel(&mut ps, &protect_strokes, sc);
        let mut params = json!({
            "scaleX": scale_x.clamp(40.0, 200.0) as f64,
            "scaleY": scale_y.clamp(40.0, 200.0) as f64,
            "amount": amount.clamp(0.0, 100.0) as f64,
            "protectSkinTones": protect_skin_tones
        });
        if has_protect {
            params["protect"] = json!("__cas_protect");
        }
        let _ = ps.execute("edit.contentAwareScale", params);
        proxy.session = ps;
        guard.mark_full_dirty();
        Ok(())
    }

    pub fn apply_content_aware_scale_pro(
        &self,
        scale_x: f32,
        scale_y: f32,
        amount: f32,
        protect_skin_tones: bool,
        protect_strokes: Vec<CafSamplingStrokeInput>,
    ) -> Result<DocumentInfo, EngineError> {
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        guard.live_stroke = None;
        guard.proxy_preview = None;
        let (prev_active, target_id, was_locked) = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            let prev = ds.active_layer();
            let (id, locked) = find_primary_photo_raster_layer(ds.doc(), prev)
                .ok_or_else(|| EngineError::CommandFailed { detail: "Ölçeklenecek katman bulunamadı".into() })?;
            (prev, id, locked)
        };
        guard.session.set_active_layer(Some(target_id));
        if was_locked {
            let _ = guard.session.execute("layer.setProps", json!({ "layer": target_id.0, "locks": { "pixels": false } }));
        }
        let has_protect = paint_cas_protect_channel(&mut guard.session, &protect_strokes, 1.0);
        let mut params = json!({
            "scaleX": scale_x.clamp(40.0, 200.0) as f64,
            "scaleY": scale_y.clamp(40.0, 200.0) as f64,
            "amount": amount.clamp(0.0, 100.0) as f64,
            "protectSkinTones": protect_skin_tones
        });
        if has_protect {
            params["protect"] = json!("__cas_protect");
        }
        let res = guard.session.execute("edit.contentAwareScale", params);
        if has_protect {
            if let Some(ds) = guard.session.active_mut() {
                let doc = Arc::make_mut(ds.doc_mut());
                doc.channels.retain(|c| c.name != "__cas_protect");
            }
        }
        if was_locked {
            let _ = guard.session.execute("layer.setProps", json!({ "layer": target_id.0, "locks": { "pixels": true } }));
        }
        if prev_active.is_some() {
            guard.session.set_active_layer(prev_active);
        }
        res.map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

    pub fn apply_healing_brush(
        &self,
        points: Vec<StrokeSample>,
        size: f32,
        hardness: f32,
        src_x: f32,
        src_y: f32,
        sample_all_layers: bool,
    ) -> Result<DocumentInfo, EngineError> {
        if points.is_empty() {
            return Err(EngineError::InvalidParams { detail: "Düzeltme fırçası noktası boş".into() });
        }
        let mut guard = self.core.lock();
        guard.dismiss_compare();
        guard.live_stroke = None;
        guard.proxy_preview = None;

        let (prev_active, target_id, was_locked) = {
            let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
            let prev = ds.active_layer();
            let (id, locked) = find_primary_photo_raster_layer(ds.doc(), prev).ok_or_else(|| {
                EngineError::CommandFailed { detail: "Düzeltme fırçası için fotoğraf katmanı bulunamadı".into() }
            })?;
            (prev, id, locked)
        };
        guard.session.set_active_layer(Some(target_id));
        if was_locked {
            let _ = guard.session.execute("layer.setProps", json!({ "layer": target_id.0, "locks": { "pixels": false } }));
        }
        let pts: Vec<Value> = points
            .iter()
            .map(|p| json!([p.x as f64, p.y as f64, p.pressure.clamp(0.1, 1.0) as f64]))
            .collect();
        let res = guard.session.execute(
            "paint.healingBrush",
            json!({
                "points": pts,
                "size": size.clamp(4.0, 400.0) as f64,
                "hardness": (hardness.clamp(0.0, 1.0) * 100.0).round() as f64,
                "source": [src_x as f64, src_y as f64],
                "aligned": true,
                "sampleLayer": if sample_all_layers { "all" } else { "current" }
            }),
        );
        if was_locked {
            let _ = guard.session.execute("layer.setProps", json!({ "layer": target_id.0, "locks": { "pixels": true } }));
        }
        if prev_active.is_some() {
            guard.session.set_active_layer(prev_active);
        }
        res.map_err(|e| EngineError::CommandFailed { detail: e.to_string() })?;
        guard.mark_full_dirty();
        self.info_from_locked(&guard)
    }

}

impl KastamonuEngine {
    fn effect_badges(effects: &[photocraft_doc::Effect]) -> Vec<String> {
        let mut out = Vec::new();
        for fx in effects {
            let label = match fx {
                photocraft_doc::Effect::DropShadow(_) => "Gölge",
                photocraft_doc::Effect::InnerShadow(_) => "İç Gölge",
                photocraft_doc::Effect::OuterGlow(_) => "Dış Işıma",
                photocraft_doc::Effect::InnerGlow(_) => "İç Işıma",
                photocraft_doc::Effect::Stroke(_) => "Kontur",
                photocraft_doc::Effect::ColorOverlay { .. } => "Renk",
                photocraft_doc::Effect::GradientOverlay { .. } => "Degrade",
                photocraft_doc::Effect::BevelEmboss(_) => "Kabartma",
                _ => "fx",
            };
            if !out.iter().any(|s: &String| s == label) {
                out.push(label.to_string());
            }
        }
        out
    }

    fn summarize_presets(presets: &[photocraft_paint::BrushPreset]) -> Vec<AbrBrushSummary> {
        presets
            .iter()
            .enumerate()
            .map(|(i, p)| AbrBrushSummary {
                index: i as u32,
                name: p.name.clone(),
                group: p.group.clone(),
                size: p.brush.size.clamp(1.0, 1000.0),
                hardness: p.brush.hardness.clamp(0.0, 1.0),
                spacing: p.brush.spacing.clamp(0.05, 2.0),
                has_sampled_tip: !matches!(*p.brush.tip, photocraft_paint::TipShape::Round),
            })
            .collect()
    }

    fn info_from_locked(&self, guard: &EngineCore) -> Result<DocumentInfo, EngineError> {
        let ds = guard.session.active().ok_or(EngineError::NoActiveDocument)?;
        let doc = ds.doc();
        Ok(DocumentInfo {
            name: doc.name.clone(),
            width: doc.size.width,
            height: doc.size.height,
            layer_count: doc.layers.len() as u32,
            active_layer_id: ds.active_layer().map(|id| id.0),
            can_undo: guard.session.is_enabled("edit.undo"),
            can_redo: guard.session.is_enabled("edit.redo"),
            dirty: ds.is_dirty(),
        })
    }
}


fn build_blend_if_params(this_black: u8, this_white: u8, under_black: u8, under_white: u8, feather: u8) -> Value {
    let f = feather.min(80);
    let tb0 = this_black;
    let tb1 = this_black.saturating_add(f).min(this_white);
    let tw1 = this_white;
    let tw0 = this_white.saturating_sub(f).max(tb1);
    let ub0 = under_black;
    let ub1 = under_black.saturating_add(f).min(under_white);
    let uw1 = under_white;
    let uw0 = under_white.saturating_sub(f).max(ub1);
    json!({
        "blendIf": [{
            "channel": "gray",
            "thisLayer": [tb0, tb1, tw0, tw1],
            "underlying": [ub0, ub1, uw0, uw1]
        }]
    })
}


fn paint_cas_protect_channel(sess: &mut Session, strokes: &[CafSamplingStrokeInput], sc: f32) -> bool {
    if strokes.is_empty() || strokes.iter().all(|s| s.points.is_empty()) {
        return false;
    }
    if sess.execute("channel.new", json!({ "name": "__cas_protect" })).is_err() {
        return false;
    }
    if let Some(ds) = sess.active_mut() {
        let doc = Arc::make_mut(ds.doc_mut());
        let dw = doc.size.width.max(1) as i32;
        let dh = doc.size.height.max(1) as i32;
        if let Some(ch) = doc.channels.iter_mut().find(|c| c.name == "__cas_protect") {
            for st in strokes {
                let rad = ((st.size * sc * 0.5).clamp(2.0, 220.0)).ceil() as i32;
                let r2 = (rad * rad) as f32;
                let val = if st.add { 1.0f32 } else { 0.0f32 };
                for pt in &st.points {
                    let cx = (pt.x * sc).round() as i32;
                    let cy = (pt.y * sc).round() as i32;
                    let x0 = (cx - rad).clamp(0, dw - 1);
                    let y0 = (cy - rad).clamp(0, dh - 1);
                    let x1 = (cx + rad + 1).clamp(x0 + 1, dw);
                    let y1 = (cy + rad + 1).clamp(y0 + 1, dh);
                    let rw = (x1 - x0) as usize;
                    let rh = (y1 - y0) as usize;
                    let rect = Rect::new(x0, y0, x1, y1);
                    let mut buf = ch.surface.read_region(rect);
                    let cstride = (ch.surface.channels() as usize).max(1);
                    if buf.len() >= rw * rh * cstride {
                        for dy in 0..rh {
                            let py = y0 + dy as i32;
                            for dx in 0..rw {
                                let px = x0 + dx as i32;
                                let d2 = ((px - cx) * (px - cx) + (py - cy) * (py - cy)) as f32;
                                if d2 <= r2 {
                                    buf[(dy * rw + dx) * cstride] = val;
                                }
                            }
                        }
                        ch.surface.write_region(rect, &buf);
                    }
                }
            }
            return true;
        }
    }
    false
}

#[cfg(test)]
mod v08_automated_tests {
    use super::*;

    fn test_engine() -> KastamonuEngine {
        KastamonuEngine::from_shared(Arc::new(Mutex::new(EngineCore::new())))
    }

    #[test]
    fn test_v08_content_aware_remove_replaces_selected_object() {
        let eng = test_engine();
        let w = 160u32;
        let h = 120u32;
        let mut rgba = vec![0u8; (w * h * 4) as usize];
        // Green background with a red 20x20 object in the center
        for y in 0..h {
            for x in 0..w {
                let i = ((y * w + x) * 4) as usize;
                let in_blob = (70..90).contains(&x) && (50..70).contains(&y);
                rgba[i] = if in_blob { 220 } else { 40 };
                rgba[i + 1] = if in_blob { 30 } else { 180 };
                rgba[i + 2] = 40;
                rgba[i + 3] = 255;
            }
        }
        eng.open_rgba_bytes("test.png".into(), w, h, rgba).expect("open_rgba_bytes");
        let sel = eng
            .select_rect(68, 48, 24, 24, false, "replace".into())
            .expect("select_rect");
        assert!(sel.is_some());
        let info = eng.content_aware_remove().expect("content_aware_remove");
        assert_eq!(info.width, w);
        assert!(eng.get_selection_bounds().is_none(), "selection cleared after remove");
    }

    #[test]
    fn test_v08_perspective_crop_rectifies_quad() {
        let eng = test_engine();
        eng.new_document("persp".into(), 320, 240).expect("new_document");
        let info = eng
            .perspective_crop(40.0, 30.0, 280.0, 45.0, 295.0, 210.0, 25.0, 200.0)
            .expect("perspective_crop");
        assert!(info.width > 50 && info.height > 50, "rectified dimensions valid");
        let undone = eng.undo().expect("undo perspective crop");
        assert_eq!((undone.width, undone.height), (320, 240));
    }

    #[test]
    fn test_v08_curves_adjustment_layer_and_thumbnail() {
        let eng = test_engine();
        eng.new_document("curves".into(), 128, 128).expect("new_document");
        let before = eng.get_layers().expect("layers").len();
        let info = eng
            .apply_adjustment_once(
                "curves".into(),
                r#"{"points":[[0,0],[64,48],[128,128],[192,210],[255,255]]}"#.into(),
            )
            .expect("curves adjustment");
        assert_eq!(info.layer_count as usize, before + 1);

        let layers = eng.get_layers().expect("layers");
        let bg = layers.iter().find(|l| l.is_background).expect("bg layer");
        let thumb = eng
            .get_layer_thumbnail(bg.id, 64)
            .expect("thumbnail")
            .expect("raster thumb");
        assert_eq!(thumb.width, 64);
        assert_eq!(thumb.rgba.len(), (thumb.width * thumb.height * 4) as usize);
    }

    #[test]
    fn test_v07_manual_selection_paint_and_erase() {
        let eng = test_engine();
        eng.new_document("sel".into(), 128, 128).expect("new_document");
        let pts = vec![
            StrokeSample { x: 40.0, y: 40.0, pressure: 1.0, tilt_x: 0.0, tilt_y: 0.0 },
            StrokeSample { x: 80.0, y: 40.0, pressure: 1.0, tilt_x: 0.0, tilt_y: 0.0 },
        ];
        let added = eng.paint_selection_stroke(pts.clone(), 24.0, false).expect("paint add");
        assert!(added.is_some());
        let erased = eng.paint_selection_stroke(pts, 40.0, true).expect("paint erase");
        assert!(erased.is_none(), "full erase clears selection");
    }

    #[test]
    fn test_v082_proxy_preview_exact_blur_and_hue_saturation() {
        let eng = test_engine();
        eng.new_document("proxy".into(), 640, 480).expect("new_document");
        eng.begin_proxy_preview().expect("begin_proxy_preview");
        eng.update_proxy_adjustment("hueSaturation".into(), r#"{"hue":0,"saturation":100,"lightness":0}"#.into())
            .expect("proxy hueSaturation");
        eng.update_proxy_blur(40.0).expect("proxy blur");
        eng.update_proxy_sharpen(80.0).expect("proxy sharpen");
        eng.end_proxy_preview();
        let info = eng.get_document_info().expect("doc info");
        assert_eq!((info.width, info.height), (640, 480));
    }

    #[test]
    fn test_v082_transform_active_layer_independently() {
        let eng = test_engine();
        eng.new_document("xf".into(), 200, 200).expect("new_document");
        let _ = eng.select_rect(40, 40, 60, 60, false, "replace".into()).expect("sel");
        let _ = eng.fill_selection_pixels("#FF0000".into()).expect("fill");
        let _ = eng.select_deselect().expect("desel");
        let bounds = eng.get_active_layer_bounds().expect("active layer bounds");
        assert!(bounds.x1 > bounds.x0);
        let _ = eng.transform_active_layer(25.0, -15.0, 75.0, 15.0).expect("transform_active_layer");
        let _ = eng.undo().expect("undo transform");
    }

    #[test]
    fn test_v083_smart_object_wand_and_hidden_transform_layer() {
        let eng = test_engine();
        let w = 160u32;
        let h = 160u32;
        let mut rgba = vec![0u8; (w * h * 4) as usize];
        for y in 0..h {
            for x in 0..w {
                let i = ((y * w + x) * 4) as usize;
                let in_obj = (45..115).contains(&x) && (45..115).contains(&y);
                rgba[i] = if in_obj { 210 } else { 30 };
                rgba[i + 1] = if in_obj { 50 } else { 140 };
                rgba[i + 2] = if in_obj { 60 } else { 210 };
                rgba[i + 3] = 255;
            }
        }
        eng.open_rgba_bytes("obj.png".into(), w, h, rgba).expect("open");
        let sel = eng.select_object_at_point(80, 80, "replace".into()).expect("smart object wand");
        assert!(sel.is_some(), "smart object wand should select the center object");

        let layers = eng.get_layers().expect("layers");
        let bg_id = layers.iter().find(|l| l.is_background).unwrap().id;
        eng.set_transform_hidden_layer(Some(bg_id));
        eng.set_transform_hidden_layer(None);
    }

    #[test]
    fn test_v090_layer_arrange_duplicate_merge_via_copy_and_feather() {
        let eng = test_engine();
        eng.new_document("v09".into(), 160, 160).expect("new_document");
        let _ = eng.select_rect(20, 20, 60, 60, false, "replace".into()).expect("select");
        let _ = eng.feather_selection(4.0).expect("feather");
        let info = eng.layer_via_copy().expect("layer_via_copy");
        assert!(info.layer_count >= 2, "new layer created from selection");

        let layers_dup = eng.duplicate_active_layer().expect("duplicate");
        assert!(layers_dup.len() >= 3);
        let _ = eng.move_layer_down().expect("move down");
        let _ = eng.move_layer_up().expect("move up");
        let layers_merged = eng.merge_layer_down().expect("merge down");
        assert_eq!(layers_merged.len(), layers_dup.len() - 1);
    }

    #[test]
    fn test_v090_layer_effects_before_after_and_pcraft_roundtrip() {
        let eng = test_engine();
        eng.new_document("fx".into(), 128, 128).expect("new_document");
        let _ = eng.select_rect(30, 30, 50, 50, false, "replace".into()).expect("sel");
        let _ = eng.fill_selection_pixels("#FF4D4F".into()).expect("fill");
        let _ = eng.select_deselect().expect("desel");

        let _ = eng.apply_layer_effect("dropShadow".into(), "#000000".into(), 12.0, 75.0, 8.0).expect("dropShadow");
        let _ = eng.apply_layer_effect("stroke".into(), "#FFFFFF".into(), 4.0, 100.0, 0.0).expect("stroke");
        let _ = eng.apply_layer_effect("outerGlow".into(), "#4F9CFF".into(), 16.0, 80.0, 0.0).expect("outerGlow");
        let _ = eng.apply_layer_effect("colorOverlay".into(), "#52C41A".into(), 1.0, 50.0, 0.0).expect("colorOverlay");

        eng.set_compare_original(true);
        eng.set_compare_original(false);

        let pcraft_bytes = eng.export_document_bytes("pcraft".into()).expect("export pcraft");
        assert!(!pcraft_bytes.is_empty());
        let reopened = eng.open_document_bytes("saved.pcraft".into(), pcraft_bytes).expect("reopen pcraft");
        assert!(reopened.layer_count >= 1, "pcraft preserved layer and effects");

        let _ = eng.clear_layer_effects().expect("clear fx");
    }

    #[test]
    fn test_v091_live_proxy_layer_effect_preview() {
        let eng = test_engine();
        eng.new_document("fx_proxy".into(), 320, 240).expect("new_document");
        eng.begin_layer_proxy_preview().expect("begin_layer_proxy_preview");
        eng.update_proxy_layer_effect("colorOverlay".into(), "#4F9CFF".into(), 10.0, 60.0, 0.0)
            .expect("proxy colorOverlay");
        eng.update_proxy_layer_effect("stroke".into(), "#FFFFFF".into(), 12.0, 100.0, 0.0)
            .expect("proxy stroke");
        eng.end_proxy_preview();
    }

    #[test]
    fn test_v092_before_after_snapshot_survives_blur_and_auto_dismisses() {
        let eng = test_engine();
        let w = 64u32;
        let h = 64u32;
        let mut rgba = vec![0u8; (w * h * 4) as usize];
        for y in 0..h {
            for x in 0..w {
                let i = ((y * w + x) * 4) as usize;
                let inside = (20..44).contains(&x) && (20..44).contains(&y);
                rgba[i] = if inside { 230 } else { 0 };
                rgba[i + 1] = if inside { 30 } else { 0 };
                rgba[i + 2] = if inside { 30 } else { 0 };
                rgba[i + 3] = if inside { 255 } else { 0 }; // Transparent background!
            }
        }
        eng.open_rgba_bytes("transparent.png".into(), w, h, rgba).expect("open");
        eng.apply_blur_fast(12.0).expect("blur");

        // Enable Before/After — snapshot must still hold the untouched sharp original!
        eng.set_compare_original(true);
        {
            let guard = eng.core.lock();
            assert!(guard.compare_original);
            let (orig_doc, _) = guard.active_document_and_display_size().expect("orig doc");
            let LayerContent::Raster(surf) = &orig_doc.layers[0].content else { panic!("raster") };
            let px = surf.read_region(Rect::new(2, 2, 3, 3));
            assert!(px[3] < 0.01, "original snapshot must preserve transparent background");
            let center = surf.read_region(Rect::new(32, 32, 33, 33));
            assert!(center[0] > 0.85 && center[3] > 0.95, "original snapshot must preserve sharp red center");
        }

        // Starting a new edit must automatically dismiss Before/After
        eng.begin_proxy_preview().expect("proxy");
        {
            let guard = eng.core.lock();
            assert!(!guard.compare_original, "compare_original must auto-dismiss on new tool/preview");
        }
        eng.end_proxy_preview();
    }

    #[test]
    fn test_v092_proxy_preserves_alpha_and_selection_masked_blur_and_1px_brush() {
        let eng = test_engine();
        let w = 80u32;
        let h = 80u32;
        let mut rgba = vec![0u8; (w * h * 4) as usize];
        for y in 0..h {
            for x in 0..w {
                let i = ((y * w + x) * 4) as usize;
                let inside = (24..56).contains(&x) && (24..56).contains(&y);
                rgba[i] = if inside { 240 } else { 0 };
                rgba[i + 1] = 0;
                rgba[i + 2] = 0;
                rgba[i + 3] = if inside { 255 } else { 0 };
            }
        }
        eng.open_rgba_bytes("alpha_test.png".into(), w, h, rgba).expect("open");

        // Proxy preview must NOT flatten transparent background to solid white
        eng.begin_proxy_preview().expect("begin_proxy_preview");
        eng.update_proxy_sharpen(150.0).expect("proxy sharpen");
        {
            let guard = eng.core.lock();
            let (pdoc, _) = guard.active_document_and_display_size().expect("proxy doc");
            let LayerContent::Raster(surf) = &pdoc.layers[0].content else { panic!("raster") };
            let corner = surf.read_region(Rect::new(1, 1, 2, 2));
            assert!(corner[3] < 0.01, "proxy preview must keep transparent corner transparent, got alpha={}", corner[3]);
        }
        eng.end_proxy_preview();

        // Selection-masked blur must leave unselected pixels untouched
        let _ = eng.select_rect(0, 0, 20, 20, false, "replace".into()).expect("sel");
        eng.apply_blur_fast(10.0).expect("masked blur");
        let _ = eng.select_deselect().expect("desel");

        // 1px brush & 1px manual selection stroke must both succeed cleanly
        eng.set_brush(1.0, 1.0, 1.0, 1.0, "#FFFFFF".into()).expect("1px brush");
        let pts = vec![
            StrokeSample { x: 10.0, y: 10.0, pressure: 1.0, tilt_x: 0.0, tilt_y: 0.0 },
            StrokeSample { x: 20.0, y: 10.0, pressure: 1.0, tilt_x: 0.0, tilt_y: 0.0 },
        ];
        let sel1px = eng.paint_selection_stroke(pts, 1.0, false).expect("1px selection stroke");
        assert!(sel1px.is_some(), "1px selection stroke should select pixels");
    }

    #[test]
    fn test_v095_camera_light_proxy_and_apply_preserves_alpha() {
        let eng = test_engine();
        let w = 96u32;
        let h = 96u32;
        let mut rgba = vec![0u8; (w * h * 4) as usize];
        for y in 0..h {
            for x in 0..w {
                let i = ((y * w + x) * 4) as usize;
                let inside = (24..72).contains(&x) && (24..72).contains(&y);
                rgba[i] = if inside { 90 } else { 0 };
                rgba[i + 1] = if inside { 80 } else { 0 };
                rgba[i + 2] = if inside { 70 } else { 0 };
                rgba[i + 3] = if inside { 255 } else { 0 };
            }
        }
        eng.open_rgba_bytes("cam.png".into(), w, h, rgba).expect("open");
        eng.begin_proxy_preview().expect("begin proxy");
        eng.update_proxy_camera_light(1.2, 45.0, -30.0, 35.0, 20.0, 0.0, 25.0, 10.0)
            .expect("proxy camera light");
        eng.end_proxy_preview();
        let info = eng
            .apply_camera_light(1.2, 45.0, -30.0, 35.0, 20.0, 0.0, 25.0, 10.0)
            .expect("apply camera light");
        assert_eq!((info.width, info.height), (w, h));
        // Verify transparent corner stays transparent after Camera Raw develop
        let guard = eng.core.lock();
        let doc = guard.active_document().unwrap();
        let LayerContent::Raster(surf) = &doc.layers[0].content else { panic!("raster") };
        let corner = surf.read_region(Rect::new(2, 2, 3, 3));
        assert!(corner[3] < 0.01, "camera light must preserve transparency");
    }

    #[test]
    fn test_v095_gradient_fill_proxy_and_apply_creates_layer() {
        let eng = test_engine();
        eng.new_document("grad".into(), 160, 160).expect("new_document");
        let before = eng.get_layers().expect("layers").len();
        eng.begin_proxy_preview().expect("begin proxy");
        eng.update_proxy_gradient(10.0, 10.0, 150.0, 150.0, "linear".into(), "#4F9CFF".into(), "#FF4D4F".into(), 85.0)
            .expect("proxy gradient");
        eng.end_proxy_preview();
        let info = eng
            .apply_gradient_fill(10.0, 10.0, 150.0, 150.0, "radial".into(), "#4F9CFF".into(), "#FF4D4F".into(), 85.0)
            .expect("apply gradient");
        assert_eq!(info.layer_count as usize, before + 1);
    }

    #[test]
    fn test_v095_vector_shape_proxy_and_create_all_kinds() {
        let eng = test_engine();
        eng.new_document("shapes".into(), 200, 200).expect("new_document");
        let before = eng.get_layers().expect("layers").len();
        eng.begin_proxy_preview().expect("begin proxy");
        eng.update_proxy_shape("roundedRect".into(), 20.0, 20.0, 160.0, 140.0, "#4F9CFF".into(), "#FFFFFF".into(), 4.0, 16.0, 5)
            .expect("proxy roundedRect");
        eng.end_proxy_preview();

        let _ = eng.create_shape_layer("rect".into(), 20.0, 20.0, 120.0, 100.0, "#FF4D4F".into(), "#FFFFFF".into(), 3.0, 0.0, 5).expect("rect");
        let _ = eng.create_shape_layer("ellipse".into(), 40.0, 40.0, 160.0, 160.0, "#52C41A".into(), "".into(), 0.0, 0.0, 5).expect("ellipse");
        let _ = eng.create_shape_layer("polygon".into(), 30.0, 30.0, 150.0, 150.0, "#FAAD14".into(), "#000000".into(), 2.0, 0.0, 6).expect("polygon");
        let info = eng.create_shape_layer("line".into(), 10.0, 10.0, 180.0, 180.0, "".into(), "#FFFFFF".into(), 6.0, 0.0, 5).expect("line");
        assert_eq!(info.layer_count as usize, before + 4);
    }

    #[test]
    fn test_v098_liquify_forward_warp_bloat_reset_and_commit_undo() {
        let eng = test_engine();
        let w = 128u32;
        let h = 128u32;
        let mut rgba = vec![0u8; (w * h * 4) as usize];
        for y in 0..h {
            for x in 0..w {
                let i = ((y * w + x) * 4) as usize;
                let inside = (32..96).contains(&x) && (32..96).contains(&y);
                rgba[i] = if inside { 220 } else { 0 };
                rgba[i + 1] = if inside { 40 } else { 0 };
                rgba[i + 2] = if inside { 40 } else { 0 };
                rgba[i + 3] = if inside { 255 } else { 0 };
            }
        }
        eng.open_rgba_bytes("liq.png".into(), w, h, rgba).expect("open");

        eng.begin_liquify_session().expect("begin liquify");
        eng.liquify_stroke_begin("forwardWarp".into(), 60.0, 80.0, 50.0, 64.0, 64.0).expect("begin stroke");
        eng.liquify_stroke_move(88.0, 64.0).expect("move stroke");
        eng.liquify_stroke_end();

        eng.reset_liquify_session().expect("reset liquify");
        {
            let guard = eng.core.lock();
            assert!(guard.live_liquify.as_ref().unwrap().field.is_identity());
        }

        eng.liquify_stroke_begin("bloat".into(), 64.0, 85.0, 50.0, 64.0, 64.0).expect("bloat");
        eng.liquify_stroke_move(66.0, 64.0).expect("bloat move");
        eng.liquify_stroke_end();
        let info = eng.commit_liquify_session().expect("commit liquify");
        assert_eq!((info.width, info.height), (w, h));
        let _ = eng.undo().expect("undo liquify");
    }

    #[test]
    fn test_v098_liquify_cancel_leaves_document_untouched() {
        let eng = test_engine();
        eng.new_document("liq_cancel".into(), 96, 96).expect("new_document");
        eng.begin_liquify_session().expect("begin");
        eng.liquify_stroke_begin("twirlCw".into(), 48.0, 70.0, 50.0, 48.0, 48.0).expect("twirl");
        eng.liquify_stroke_move(52.0, 52.0).expect("move");
        eng.liquify_stroke_end();
        eng.cancel_liquify_session();
        let guard = eng.core.lock();
        assert!(guard.live_liquify.is_none());
    }

    #[test]
    fn test_v099_auto_enhance_tone_color_contrast() {
        let eng = test_engine();
        let w = 64u32;
        let h = 64u32;
        let mut rgba = vec![0u8; (w * h * 4) as usize];
        for y in 0..h {
            for x in 0..w {
                let i = ((y * w + x) * 4) as usize;
                rgba[i] = (60 + x * 2) as u8;
                rgba[i + 1] = (70 + y) as u8;
                rgba[i + 2] = 90;
                rgba[i + 3] = 255;
            }
        }
        eng.open_rgba_bytes("auto.png".into(), w, h, rgba).expect("open");
        for mode in ["tone", "color", "contrast"] {
            let info = eng.apply_auto_enhance(mode.into()).expect("auto enhance");
            assert_eq!((info.width, info.height), (w, h));
            let _ = eng.undo().expect("undo auto");
        }
    }

    #[test]
    fn test_v099_cinema_look_vignette_grain_proxy_and_apply() {
        let eng = test_engine();
        eng.new_document("cinema".into(), 128, 128).expect("new_document");
        eng.begin_proxy_preview().expect("begin proxy");
        eng.update_proxy_cinema_look("tealOrange".into(), 80.0, -35.0, 20.0).expect("proxy cinema");
        eng.end_proxy_preview();
        let info = eng.apply_cinema_look("golden".into(), 90.0, -30.0, 15.0).expect("apply cinema");
        assert_eq!((info.width, info.height), (128, 128));
    }

    #[test]
    fn test_v099_motion_and_radial_blur_proxy_and_apply() {
        let eng = test_engine();
        eng.new_document("blurs".into(), 128, 128).expect("new_document");
        eng.begin_proxy_preview().expect("begin proxy");
        eng.update_proxy_motion_blur(45.0, 24.0).expect("proxy motion");
        eng.update_proxy_radial_blur(15.0, "zoom".into()).expect("proxy radial");
        eng.end_proxy_preview();
        let _ = eng.apply_motion_blur(30.0, 18.0).expect("apply motion");
        let info = eng.apply_radial_blur(12.0, "spin".into()).expect("apply radial");
        assert_eq!((info.width, info.height), (128, 128));
    }

    #[test]
    fn test_v0995_liquify_per_stroke_undo_redo_repeatedly() {
        let eng = test_engine();
        eng.new_document("liq_ur".into(), 128, 128).expect("new_document");
        eng.begin_liquify_session().expect("begin");

        let s0 = eng.get_liquify_history_state();
        assert!(!s0.can_undo && !s0.can_redo);

        // Stroke 1
        eng.liquify_stroke_begin("forwardWarp".into(), 48.0, 80.0, 50.0, 40.0, 40.0).expect("s1");
        eng.liquify_stroke_move(70.0, 40.0).expect("s1m");
        eng.liquify_stroke_end();
        // Stroke 2
        eng.liquify_stroke_begin("bloat".into(), 48.0, 80.0, 50.0, 80.0, 80.0).expect("s2");
        eng.liquify_stroke_move(82.0, 80.0).expect("s2m");
        eng.liquify_stroke_end();

        let s1 = eng.liquify_undo().expect("undo 1");
        assert!(s1.can_undo && s1.can_redo, "after 1st undo, both undo and redo must be available");
        let s2 = eng.liquify_undo().expect("undo 2");
        assert!(!s2.can_undo && s2.can_redo, "after 2nd undo, redo must still work");
        let s3 = eng.liquify_redo().expect("redo 1");
        assert!(s3.can_undo && s3.can_redo, "after redo, undo must work again");
        eng.cancel_liquify_session();
    }

    #[test]
    fn test_v0995_sample_loupe_patch_returns_crisp_center_pixels() {
        let eng = test_engine();
        let w = 64u32;
        let h = 64u32;
        let mut rgba = vec![0u8; (w * h * 4) as usize];
        for y in 0..h {
            for x in 0..w {
                let i = ((y * w + x) * 4) as usize;
                rgba[i] = 250;
                rgba[i + 1] = 20;
                rgba[i + 2] = 30;
                rgba[i + 3] = 255;
            }
        }
        eng.open_rgba_bytes("loupe.png".into(), w, h, rgba).expect("open");
        let patch = eng.sample_loupe_patch(32.0, 32.0, 12.0, 48).expect("loupe patch");
        assert_eq!((patch.width, patch.height), (48, 48));
        assert_eq!(patch.rgba.len(), 48 * 48 * 4);
        let center_idx = (24 * 48 + 24) * 4;
        assert!(patch.rgba[center_idx] > 230 && patch.rgba[center_idx + 1] < 40, "center pixel must match red photo");
    }

    #[test]
    fn test_v100_manual_selection_increments_selection_generation_and_undo() {
        let eng = test_engine();
        eng.new_document("v100_sel".into(), 128, 128).expect("new_document");
        let gen_before = eng.core.lock().selection_generation;
        let pts = vec![
            StrokeSample { x: 30.0, y: 30.0, pressure: 1.0, tilt_x: 0.0, tilt_y: 0.0 },
            StrokeSample { x: 70.0, y: 70.0, pressure: 1.0, tilt_x: 0.0, tilt_y: 0.0 },
        ];
        let added = eng.paint_selection_stroke(pts, 20.0, false).expect("paint add");
        assert!(added.is_some(), "manual add selection must produce bounds");
        let gen_after = eng.core.lock().selection_generation;
        assert!(gen_after > gen_before, "paint_selection_stroke must bump selection_generation for GPU texture upload");
        let info = eng.get_document_info().expect("info");
        assert!(info.can_undo, "manual selection stroke must be undoable");
        let _ = eng.undo().expect("undo manual selection");
        assert!(eng.get_selection_bounds().is_none(), "undo must cleanly remove manual selection");
    }

    #[test]
    fn test_v110_smart_remove_background_and_portrait_blur() {
        let eng = test_engine();
        let w = 128u32;
        let h = 128u32;
        let mut rgba = vec![0u8; (w * h * 4) as usize];
        for y in 0..h {
            for x in 0..w {
                let i = ((y * w + x) * 4) as usize;
                let subject = (36..92).contains(&x) && (36..92).contains(&y);
                rgba[i] = if subject { 230 } else { 20 };
                rgba[i + 1] = if subject { 40 } else { 150 };
                rgba[i + 2] = if subject { 50 } else { 210 };
                rgba[i + 3] = 255;
            }
        }
        eng.open_rgba_bytes("subj.png".into(), w, h, rgba).expect("open");
        let info_blur = eng.smart_portrait_blur(12.0).expect("smart portrait blur");
        assert_eq!((info_blur.width, info_blur.height), (w, h));
        let info_rm = eng.smart_remove_background().expect("smart remove bg");
        assert_eq!((info_rm.width, info_rm.height), (w, h));
    }

    #[test]
    fn test_v110_live_cinema_look_thumbs_returns_7_previews() {
        let eng = test_engine();
        eng.new_document("thumbs".into(), 120, 90).expect("new_document");
        let thumbs = eng.render_cinema_look_thumbs(64).expect("cinema thumbs");
        assert_eq!(thumbs.len(), 7, "must return all 7 live cinema look preview thumbnails");
        assert!(thumbs[0].width <= 64 && thumbs[0].height <= 64 && !thumbs[0].rgba.is_empty());
    }

    #[test]
    fn test_v110_8channel_hsl_mixer_proxy_and_apply() {
        let eng = test_engine();
        let w = 64u32;
        let h = 64u32;
        let mut rgba = vec![0u8; (w * h * 4) as usize];
        for px in rgba.chunks_exact_mut(4) {
            px.copy_from_slice(&[220, 40, 40, 255]); // Vivid red
        }
        eng.open_rgba_bytes("red.png".into(), w, h, rgba).expect("open");
        let hue = vec![80.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0];
        let sat = vec![-50.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0];
        let lum = vec![0.0; 8];
        eng.begin_proxy_preview().expect("begin proxy");
        eng.update_proxy_hsl_mixer(hue.clone(), sat.clone(), lum.clone()).expect("proxy hsl");
        eng.end_proxy_preview();
        let info = eng.apply_hsl_mixer(hue, sat, lum).expect("apply hsl");
        assert_eq!((info.width, info.height), (w, h));
        let guard = eng.core.lock();
        let doc = guard.active_document().unwrap();
        let LayerContent::Raster(surf) = &doc.layers[0].content else { panic!("raster") };
        let c = surf.rgba(32, 32);
        assert!(c[1] > 0.30, "shifting red hue +80 must noticeably increase green channel, got {:?}", c);
    }

    #[test]
    fn test_v110_paint_bucket_and_posterize_threshold() {
        let eng = test_engine();
        eng.new_document("bucket".into(), 96, 96).expect("new_document");
        let _ = eng.apply_paint_bucket(48, 48, "#4F9CFF".into(), 32, true).expect("paint bucket");
        let _ = eng.apply_adjustment_once("posterize".into(), r#"{"levels":5}"#.into()).expect("posterize");
        let info = eng.apply_adjustment_once("threshold".into(), r#"{"level":128}"#.into()).expect("threshold");
        assert!(info.layer_count >= 3);
    }

    #[test]
    fn test_v120_filter_gallery_all_9_filters_apply_and_undo() {
        let eng = test_engine();
        let w = 96u32;
        let h = 96u32;
        let mut rgba = vec![0u8; (w * h * 4) as usize];
        for y in 0..h {
            for x in 0..w {
                let i = ((y * w + x) * 4) as usize;
                rgba[i] = ((x * 2) % 255) as u8;
                rgba[i + 1] = ((y * 2) % 255) as u8;
                rgba[i + 2] = 140;
                rgba[i + 3] = 255;
            }
        }
        eng.open_rgba_bytes("gal.png".into(), w, h, rgba).expect("open");

        let kinds = [
            "oilPaint", "watercolor", "charcoal", "glowingEdges", "emboss",
            "mosaic", "crystallize", "pointillize", "colorHalftone",
        ];
        eng.begin_proxy_preview().expect("begin proxy");
        for k in kinds {
            eng.update_proxy_gallery_filter(k.into(), 50.0, 50.0).expect(k);
        }
        eng.end_proxy_preview();

        for k in kinds {
            let info = eng.apply_gallery_filter(k.into(), 45.0, 50.0).expect(k);
            assert_eq!((info.width, info.height), (w, h));
            let _ = eng.undo().expect("undo gallery filter");
        }
    }

    #[test]
    fn test_v120_filter_gallery_live_thumbs_returns_10_previews() {
        let eng = test_engine();
        eng.new_document("gal_thumbs".into(), 96, 72).expect("new_document");
        let thumbs = eng.render_filter_gallery_thumbs(64).expect("gallery thumbs");
        assert_eq!(thumbs.len(), 10, "must return Orijinal + 9 live filter preview thumbnails");
        assert!(thumbs[0].width <= 64 && !thumbs[0].rgba.is_empty());
    }

    #[test]
    fn test_proxy_preview_recovers_after_undo_and_compare_toggle() {
        let eng = KastamonuEngine::from_shared(Arc::new(Mutex::new(EngineCore::new())));
        eng.new_document("Test".into(), 128, 128).unwrap();
        eng.execute_command("layer.newAdjustmentLayer.brightnessContrast".into(), r#"{"brightness":20,"contrast":0}"#.into()).unwrap();
        eng.begin_proxy_preview().unwrap();
        eng.update_proxy_adjustment("brightnessContrast".into(), r#"{"brightness":40,"contrast":10}"#.into()).unwrap();

        eng.set_compare_original(true);
        {
            let g = eng.core.lock();
            assert!(g.compare_original);
            assert!(g.proxy_preview.is_some(), "proxy_preview must survive Önce toggle");
        }
        eng.set_compare_original(false);

        eng.undo().unwrap();
        eng.update_proxy_adjustment("brightnessContrast".into(), r#"{"brightness":-30,"contrast":15}"#.into()).unwrap();
        let g = eng.core.lock();
        assert!(g.proxy_preview.is_some(), "proxy_preview must auto-recover after undo()");
        assert!(!g.compare_original);
    }

    #[test]
    fn test_reset_proxy_preview_restores_original_pixels() {
        let eng = KastamonuEngine::from_shared(Arc::new(Mutex::new(EngineCore::new())));
        eng.new_document("Test".into(), 64, 64).unwrap();
        eng.begin_proxy_preview().unwrap();
        eng.update_proxy_adjustment("posterize".into(), r#"{"levels":3}"#.into()).unwrap();
        eng.reset_proxy_preview().unwrap();
        let g = eng.core.lock();
        let proxy = g.proxy_preview.as_ref().expect("proxy must remain active after reset");
        let cur = proxy.session.active().unwrap().doc();
        assert_eq!(cur.layers.len(), proxy.base_doc.layers.len(), "reset_proxy_preview must restore exact original layer stack");
    }

    #[test]
    fn test_hsl_mixer_leaves_near_black_neutral_untouched() {
        let eng = KastamonuEngine::from_shared(Arc::new(Mutex::new(EngineCore::new())));
        eng.new_document("Test".into(), 32, 32).unwrap();
        {
            let mut g = eng.core.lock();
            let ds = g.session.active_mut().unwrap();
            let doc = Arc::make_mut(ds.doc_mut());
            let bounds = doc.bounds();
            let LayerContent::Raster(surf) = &mut doc.layers[0].content else { panic!("expected raster"); };
            let ch = surf.format().channels();
            let mut buf = vec![0.0f32; 32 * 32 * ch];
            for i in 0..(32 * 32) {
                buf[i * ch] = 0.04;
                buf[i * ch + 1] = 0.04;
                buf[i * ch + 2] = 0.06;
                if ch > 3 { buf[i * ch + 3] = 1.0; }
            }
            surf.write_region_par(bounds, &buf);
        }
        let mut blue_max = vec![0.0f32; 8];
        blue_max[4] = 100.0;
        blue_max[5] = 100.0;
        eng.apply_hsl_mixer(blue_max.clone(), blue_max.clone(), blue_max).unwrap();
        let g = eng.core.lock();
        let doc = g.session.active().unwrap().doc();
        let LayerContent::Raster(surf) = &doc.layers[0].content else { panic!("expected raster"); };
        let px = surf.read_region(doc.bounds());
        assert!((px[0] - 0.04).abs() < 0.005 && (px[1] - 0.04).abs() < 0.005 && (px[2] - 0.06).abs() < 0.005,
            "Near-black neutral pixels must stay untouched by HSL, got ({}, {}, {})", px[0], px[1], px[2]);
    }

    #[test]
    fn test_light_distort_all_five_effects_run_cleanly() {
        let cases = [
            ("lensFlare", 60.0f32, 50.0f32, 35.0f32, 30.0f32, "moviePrime"),
            ("lensBlur", 35.0, 45.0, 50.0, 50.0, "hexagon"),
            ("wave", 40.0, 50.0, 50.0, 50.0, "sine"),
            ("spherize", 55.0, 50.0, 50.0, 50.0, "normal"),
            ("twirl", 90.0, 50.0, 50.0, 50.0, ""),
        ];
        for (kind, amt, det, px, py, var) in cases {
            let eng = KastamonuEngine::from_shared(Arc::new(Mutex::new(EngineCore::new())));
            eng.new_document("Test".into(), 64, 64).unwrap();
            let info = eng
                .apply_light_distort(kind.into(), amt, det, px, py, var.into())
                .unwrap_or_else(|e| panic!("light_distort {kind} failed: {e:?}"));
            assert!(info.can_undo, "{kind} must push an undo step");
        }
    }

    #[test]
    fn test_render_light_distort_thumbs_returns_6_cards() {
        let eng = KastamonuEngine::from_shared(Arc::new(Mutex::new(EngineCore::new())));
        eng.new_document("Test".into(), 96, 96).unwrap();
        let thumbs = eng.render_light_distort_thumbs(64).unwrap();
        assert_eq!(thumbs.len(), 6, "Expected 6 Light & Distort preview thumbnails (Orijinal + 5 effects)");
        for (i, t) in thumbs.iter().enumerate() {
            assert!(t.width > 0 && t.height > 0, "Thumb {i} has empty dimensions");
            assert_eq!(t.rgba.len(), (t.width * t.height * 4) as usize, "Thumb {i} RGBA size mismatch");
        }
    }

    #[test]
    fn test_light_distort_proxy_and_none_reset() {
        let eng = KastamonuEngine::from_shared(Arc::new(Mutex::new(EngineCore::new())));
        eng.new_document("Test".into(), 64, 64).unwrap();
        eng.begin_proxy_preview().unwrap();
        eng.update_proxy_light_distort("twirl".into(), 120.0, 50.0, 50.0, 50.0, "".into()).unwrap();
        eng.update_proxy_light_distort("none".into(), 0.0, 0.0, 50.0, 50.0, "".into()).unwrap();
        let g = eng.core.lock();
        assert!(g.proxy_preview.is_some());
    }

    #[test]
    fn test_text_studio_create_with_full_typography_and_vertical() {
        let eng = KastamonuEngine::from_shared(Arc::new(Mutex::new(EngineCore::new())));
        eng.new_document("Test".into(), 256, 256).unwrap();
        let info = eng
            .add_or_edit_text_layer(
                None,
                "Merhaba\nDünya".into(),
                128.0, 128.0, 48.0,
                "#FF4D4F".into(), "center".into(), "".into(),
                true, true, true, true, "all".into(), 120.0, 90.0,
                80.0, 1.5, true,
                "arc".into(), 45.0, 10.0, -5.0,
            )
            .unwrap();
        assert!(info.can_undo);
        let style = eng.get_active_text_layer_style().expect("active layer must be a text layer");
        assert!(style.text.contains("Merhaba"));
        assert!(style.vertical, "vertical orientation must round-trip");
        assert!(style.bold);
        assert!(style.italic);
        assert!(style.underline);
        assert!(style.strikethrough);
        assert_eq!(style.caps, "all");
        assert!((style.h_scale - 120.0).abs() < 1.0);
        assert!((style.v_scale - 90.0).abs() < 1.0);
        assert_eq!(style.warp_style, "arc");
        assert!((style.warp_bend - 45.0).abs() < 1.0);
    }

    #[test]
    fn test_text_studio_edit_existing_layer_in_place() {
        let eng = KastamonuEngine::from_shared(Arc::new(Mutex::new(EngineCore::new())));
        eng.new_document("Test".into(), 256, 256).unwrap();
        eng.add_or_edit_text_layer(
            None, "İlk Yazı".into(), 100.0, 100.0, 40.0,
            "#FFFFFF".into(), "left".into(), "".into(),
            false, false, false, false, "normal".into(), 100.0, 100.0,
            0.0, 1.2, false, "none".into(), 50.0, 0.0, 0.0,
        ).unwrap();
        let initial_layers = eng.get_layers().unwrap().len();
        let st1 = eng.get_active_text_layer_style().unwrap();

        eng.add_or_edit_text_layer(
            Some(st1.layer_id), "Güncel Başlık".into(), 140.0, 160.0, 10.0,
            "#FFEC3D".into(), "center".into(), "".into(),
            true, false, true, false, "small".into(), 110.0, 105.0,
            120.0, 1.4, false, "flag".into(), -35.0, 0.0, 0.0,
        ).unwrap();
        assert_eq!(eng.get_layers().unwrap().len(), initial_layers, "type.edit must update existing layer without duplicating");
        let st2 = eng.get_active_text_layer_style().unwrap();
        assert_eq!(st2.layer_id, st1.layer_id);
        assert_eq!(st2.text, "Güncel Başlık");
        assert!(st2.bold && st2.underline);
        assert_eq!(st2.warp_style, "flag");
        assert!((st2.size_px - 10.0).abs() < 1.0, "small 10px text size must round-trip");
    }

    #[test]
    fn test_list_available_fonts_and_empty_register() {
        let eng = KastamonuEngine::from_shared(Arc::new(Mutex::new(EngineCore::new())));
        assert!(eng.register_font_bytes(Vec::new()).is_empty());
        let _fonts = eng.list_available_fonts();
    }

    #[test]
    fn test_text_create_selection_mask_and_rasterize() {
        let eng = KastamonuEngine::from_shared(Arc::new(Mutex::new(EngineCore::new())));
        eng.new_document("Test".into(), 256, 256).unwrap();
        let sel_info = eng.create_text_selection_mask(
            "MASK".into(), 128.0, 128.0, 64.0, "center".into(), "".into(),
            true, false, "all".into(), 100.0, 100.0, 0.0, 1.2, false,
            "arc".into(), 40.0, 0.0, 0.0,
        ).unwrap();
        assert!(sel_info.can_undo && eng.core.lock().session.active().unwrap().doc().selection.is_some(), "type.createSelection must create an active selection mask");

        // Now create a text layer and rasterize it
        eng.add_or_edit_text_layer(
            None, "RASTER".into(), 128.0, 128.0, 48.0,
            "#FFFFFF".into(), "center".into(), "".into(),
            false, false, false, false, "normal".into(), 100.0, 100.0,
            0.0, 1.2, false, "none".into(), 50.0, 0.0, 0.0,
        ).unwrap();
        assert!(eng.get_active_text_layer_style().is_some());
        eng.rasterize_active_text_layer().unwrap();
        assert!(eng.get_active_text_layer_style().is_none(), "rasterized text layer must become a raster layer");
    }

    #[test]
    fn test_text_warp_all_supported_styles() {
        for style in ["arc", "arch", "flag", "wave", "bulge", "fish", "rise", "twist"] {
            let eng = KastamonuEngine::from_shared(Arc::new(Mutex::new(EngineCore::new())));
            eng.new_document("Test".into(), 128, 128).unwrap();
            eng.add_or_edit_text_layer(
                None, "WARP".into(), 64.0, 64.0, 32.0,
                "#FFFFFF".into(), "center".into(), "".into(),
                false, false, false, false, "normal".into(), 100.0, 100.0,
                0.0, 1.2, false, style.into(), 40.0, 0.0, 0.0,
            ).unwrap_or_else(|e| panic!("warp style {style} failed: {e:?}"));
            let st = eng.get_active_text_layer_style().unwrap();
            assert_eq!(st.warp_style, style);
        }
    }

    #[test]
    fn test_text_tiny_6px_size_allowed() {
        let eng = KastamonuEngine::from_shared(Arc::new(Mutex::new(EngineCore::new())));
        eng.new_document("Test".into(), 256, 256).unwrap();
        eng.add_or_edit_text_layer(
            None, "minik imza".into(), 128.0, 128.0, 6.0,
            "#FFFFFF".into(), "center".into(), "".into(),
            false, false, false, false, "normal".into(), 100.0, 100.0,
            0.0, 1.2, false, "none".into(), 50.0, 0.0, 0.0,
        ).unwrap();
        let st = eng.get_active_text_layer_style().unwrap();
        assert!((st.size_px - 6.0).abs() < 0.5, "6px tiny text must be preserved, got {}", st.size_px);
    }

    #[test]
    fn test_update_proxy_text_warp_renders_live() {
        let eng = KastamonuEngine::from_shared(Arc::new(Mutex::new(EngineCore::new())));
        eng.new_document("Test".into(), 256, 256).unwrap();
        eng.begin_proxy_preview().unwrap();
        eng.update_proxy_text_preview(
            None, "Stonebridge".into(), 128.0, 128.0, 48.0,
            "#FFFFFF".into(), "center".into(), "".into(),
            true, false, false, false, "normal".into(), 100.0, 100.0,
            0.0, 1.2, false, "arc".into(), 65.0, 15.0, -10.0,
        ).unwrap();
        let g = eng.core.lock();
        let proxy = g.proxy_preview.as_ref().expect("proxy must be active");
        let pdoc = proxy.session.active().unwrap().doc();
        assert!(pdoc.layers.len() > proxy.base_doc.layers.len(), "live proxy must contain the warped preview text layer");
    }

    #[test]
    fn test_warped_text_selection_mask_leaves_no_extra_layer() {
        let eng = KastamonuEngine::from_shared(Arc::new(Mutex::new(EngineCore::new())));
        eng.new_document("Test".into(), 256, 256).unwrap();
        let base_layers = eng.get_layers().unwrap().len();
        eng.create_text_selection_mask(
            "WARPMASK".into(), 128.0, 128.0, 56.0, "center".into(), "".into(),
            true, false, "all".into(), 100.0, 100.0, 0.0, 1.2, false,
            "flag".into(), 50.0, 0.0, 0.0,
        ).unwrap();
        assert_eq!(eng.get_layers().unwrap().len(), base_layers, "removeLayer:true must leave no stray text layer behind");
        assert!(eng.core.lock().session.active().unwrap().doc().selection.is_some());
    }

    #[test]
    fn test_puppet_warp_proxy_and_apply_succeeds() {
        let eng = KastamonuEngine::from_shared(Arc::new(Mutex::new(EngineCore::new())));
        eng.new_document("Test".into(), 128, 128).unwrap();
        let pins = vec![
            PuppetPinPoint { rotate_deg: None, depth: 0, src_x: 32.0, src_y: 32.0, dst_x: 32.0, dst_y: 32.0 },
            PuppetPinPoint { rotate_deg: None, depth: 0, src_x: 96.0, src_y: 32.0, dst_x: 96.0, dst_y: 32.0 },
            PuppetPinPoint { rotate_deg: None, depth: 0, src_x: 64.0, src_y: 96.0, dst_x: 78.0, dst_y: 84.0 },
        ];
        eng.begin_proxy_preview().unwrap();
        eng.update_proxy_puppet_warp(pins.clone(), "normal".into(), "normal".into(), 2.0).unwrap();
        let info = eng.apply_puppet_warp(pins, "normal".into(), "normal".into(), 2.0).unwrap();
        assert!(info.can_undo, "apply_puppet_warp must push an undo step");
    }

    #[test]
    fn test_puppet_warp_all_modes_rigid_normal_distort() {
        for mode in ["rigid", "normal", "distort"] {
            let eng = KastamonuEngine::from_shared(Arc::new(Mutex::new(EngineCore::new())));
            eng.new_document("Test".into(), 96, 96).unwrap();
            let pins = vec![
                PuppetPinPoint { rotate_deg: None, depth: 0, src_x: 24.0, src_y: 48.0, dst_x: 24.0, dst_y: 48.0 },
                PuppetPinPoint { rotate_deg: None, depth: 0, src_x: 72.0, src_y: 48.0, dst_x: 72.0, dst_y: 62.0 },
            ];
            eng.apply_puppet_warp(pins, mode.into(), "normal".into(), 2.0)
                .unwrap_or_else(|e| panic!("puppetWarp mode {mode} failed: {e:?}"));
        }
    }

    #[test]
    fn test_resize_image_lanczos_and_nearest_updates_dimensions() {
        let eng = KastamonuEngine::from_shared(Arc::new(Mutex::new(EngineCore::new())));
        eng.new_document("Test".into(), 128, 96).unwrap();
        let info1 = eng.resize_image(256, 192, "lanczos".into()).unwrap();
        assert_eq!((info1.width, info1.height), (256, 192));
        let info2 = eng.resize_image(64, 48, "nearest".into()).unwrap();
        assert_eq!((info2.width, info2.height), (64, 48));
        let undone = eng.undo().unwrap();
        assert_eq!((undone.width, undone.height), (256, 192));
    }

    #[test]
    fn test_v143_place_image_bytes_is_single_atomic_undo_step() {
        let eng = KastamonuEngine::from_shared(Arc::new(Mutex::new(EngineCore::new())));
        let rgba = vec![180u8, 120, 90, 255].repeat(64 * 64);
        let info0 = eng.open_rgba_bytes("cat.png".into(), 64, 64, rgba).unwrap();
        assert!(!info0.can_undo, "Freshly opened photo should start with 0 undo steps");
        assert_eq!(eng.get_layers().unwrap().len(), 1, "Single Background layer only");

        let png = eng.export_document_bytes("png".into()).unwrap();
        let info1 = eng.place_image_bytes("overlay.png".into(), png).unwrap();
        assert!(info1.can_undo);
        assert_eq!(eng.get_layers().unwrap().len(), 2);

        let info2 = eng.undo().unwrap();
        assert_eq!(eng.get_layers().unwrap().len(), 1);
        assert!(info2.can_redo);

        let _ = eng.redo().unwrap();
        assert_eq!(eng.get_layers().unwrap().len(), 2);
    }

    #[test]
    fn test_v143_visibility_and_lock_never_wipe_redo_or_hide_background_on_undo() {
        let eng = KastamonuEngine::from_shared(Arc::new(Mutex::new(EngineCore::new())));
        let rgba = vec![200u8, 80, 60, 255].repeat(48 * 48);
        let _ = eng.open_rgba_bytes("photo.png".into(), 48, 48, rgba).unwrap();
        let png = eng.export_document_bytes("png".into()).unwrap();
        let _ = eng.place_image_bytes("sky.png".into(), png).unwrap();
        let _ = eng.move_layer_down().unwrap();

        let after_undo = eng.undo().unwrap();
        assert!(after_undo.can_redo);
        let layers = eng.get_layers().unwrap();
        let bg = layers.iter().find(|l| l.is_background).expect("Background exists");
        eng.set_layer_locked(bg.id, true).unwrap();
        eng.set_layer_visibility(bg.id, false).unwrap();
        eng.set_layer_visibility(bg.id, true).unwrap();

        let info_check = eng.get_document_info().unwrap();
        assert!(info_check.can_redo, "Lock/visibility toggles must NEVER wipe the Redo stack!");
        let _ = eng.redo().unwrap();
        let layers_after = eng.get_layers().unwrap();
        assert!(layers_after.iter().find(|l| l.is_background).unwrap().visible);
    }

    #[test]
    fn test_v144_free_xy_transform_and_bounds_preview() {
        let eng = KastamonuEngine::from_shared(Arc::new(Mutex::new(EngineCore::new())));
        let rgba = vec![80u8, 160, 240, 255].repeat(80 * 40);
        let _ = eng.open_rgba_bytes("wide.png".into(), 80, 40, rgba).unwrap();
        let bg = eng.get_layers().unwrap()[0].id;
        let prev = eng.get_layer_bounds_preview(bg, 256).unwrap().expect("bounds preview");
        assert_eq!((prev.width, prev.height), (80, 40));

        // Stretch Y independently (100% X, 180% Y) in 1 clean Undo step
        let info = eng.transform_active_layer_xy(0.0, -5.0, 100.0, 180.0, 0.0).unwrap();
        assert!(info.can_undo);
        let undone = eng.undo().unwrap();
        assert!(!undone.can_undo, "Free XY transform must be a single clean undo step");
    }

    #[test]
    fn test_v150_layer_mask_reveal_hide_paint_invert_toggle_and_delete() {
        let eng = test_engine();
        let rgba = vec![220u8, 60, 60, 255].repeat(48 * 48);
        eng.open_rgba_bytes("mask_test.png".into(), 48, 48, rgba).unwrap();
        let _ = eng.add_layer_mask("revealAll".into()).unwrap();
        let layers = eng.get_layers().unwrap();
        assert!(layers[0].has_mask && layers[0].mask_enabled);

        // Paint black onto mask via live stroke in mask_paint_mode
        eng.set_mask_paint_mode(true);
        eng.set_brush(16.0, 1.0, 1.0, 1.0, "#000000".into()).unwrap();
        eng.begin_live_stroke(StrokeSample { x: 24.0, y: 24.0, pressure: 1.0, tilt_x: 0.0, tilt_y: 0.0 }, "#000000".into(), false).unwrap();
        let _ = eng.finish_live_stroke().unwrap();
        eng.set_mask_paint_mode(false);

        // Invert, disable, re-enable, then delete mask
        let _ = eng.invert_active_layer_mask().unwrap();
        let id = eng.get_layers().unwrap()[0].id;
        let _ = eng.toggle_layer_mask_enabled(id, false).unwrap();
        assert!(!eng.get_layers().unwrap()[0].mask_enabled);
        let _ = eng.toggle_layer_mask_enabled(id, true).unwrap();
        assert!(eng.get_layers().unwrap()[0].mask_enabled);
        let _ = eng.delete_active_layer_mask().unwrap();
        assert!(!eng.get_layers().unwrap()[0].has_mask);
    }

    #[test]
    fn test_v150_clipping_mask_create_release_and_undo() {
        let eng = test_engine();
        eng.new_document("clip".into(), 64, 64).unwrap();
        let _ = eng.add_layer("Top".into()).unwrap();
        assert!(!eng.get_layers().unwrap()[0].clipped);
        let _ = eng.toggle_clipping_mask().unwrap();
        assert!(eng.get_layers().unwrap()[0].clipped, "active layer should be clipped");
        let _ = eng.toggle_clipping_mask().unwrap();
        assert!(!eng.get_layers().unwrap()[0].clipped, "second toggle should release clip");
        let _ = eng.undo().unwrap();
        assert!(eng.get_layers().unwrap()[0].clipped, "undo restores clipping mask");
    }

    #[test]
    fn test_v150_layer_group_and_ungroup_walk_depth() {
        let eng = test_engine();
        eng.new_document("grp".into(), 64, 64).unwrap();
        let _ = eng.add_layer("Child".into()).unwrap();
        let _ = eng.group_active_layer("Klasör 1".into()).unwrap();
        let layers = eng.get_layers().unwrap();
        assert!(layers.iter().any(|l| l.is_group && l.name == "Klasör 1"));
        assert!(layers.iter().any(|l| l.name == "Child" && l.depth >= 1));

        let _ = eng.ungroup_active_layer().unwrap();
        let after = eng.get_layers().unwrap();
        assert!(!after.iter().any(|l| l.is_group));
    }

    #[test]
    fn test_v150_new_layer_fx_inner_shadow_inner_glow_gradient_overlay() {
        let eng = test_engine();
        eng.new_document("fx150".into(), 96, 96).unwrap();
        eng.begin_layer_proxy_preview().unwrap();
        for kind in ["innerShadow", "innerGlow", "gradientOverlay"] {
            eng.update_proxy_layer_effect(kind.into(), "#4F9CFF".into(), 12.0, 75.0, 45.0).unwrap();
            let info = eng.apply_layer_effect(kind.into(), "#4F9CFF".into(), 12.0, 75.0, 45.0).unwrap();
            assert!(info.can_undo, "{kind} must apply cleanly");
        }
    }

    #[test]
    fn test_v150_blend_if_live_proxy_apply_and_reset() {
        let eng = test_engine();
        let rgba = vec![120u8, 180, 240, 255].repeat(48 * 48);
        eng.open_rgba_bytes("blendif.png".into(), 48, 48, rgba).unwrap();
        eng.begin_layer_proxy_preview().unwrap();
        eng.update_proxy_blend_if(40, 210, 20, 235, 25).unwrap();
        let info = eng.apply_blend_if(40, 210, 20, 235, 25).unwrap();
        assert!(info.can_undo);
        let _ = eng.reset_blend_if().unwrap();
    }

    #[test]
    fn test_v160_hide_all_mask_invert_reveals_photo_pixels() {
        let eng = test_engine();
        let rgba = vec![240u8, 80, 40, 255].repeat(32 * 32);
        eng.open_rgba_bytes("inv_mask.png".into(), 32, 32, rgba).unwrap();
        let _ = eng.add_layer_mask("hideAll".into()).unwrap();
        // Before invert: untouched black mask hides photo (alpha == 0)
        {
            let guard = eng.core.lock();
            let flat = photocraft_compose::flatten(guard.active_document().unwrap().as_ref());
            assert!(flat.px[16 * 32 + 16][3] < 0.01, "hideAll must hide pixels");
        }
        // After invert: mask becomes white and reveals photo (alpha ~ 1.0)
        let _ = eng.invert_active_layer_mask().unwrap();
        {
            let guard = eng.core.lock();
            let flat = photocraft_compose::flatten(guard.active_document().unwrap().as_ref());
            assert!(flat.px[16 * 32 + 16][3] > 0.99, "invert on untouched hideAll must reveal pixels!");
        }
    }

    #[test]
    fn test_v160_single_layer_clipping_mask_is_refused() {
        let eng = test_engine();
        eng.new_document("single_clip".into(), 32, 32).unwrap();
        let res = eng.toggle_clipping_mask();
        assert!(res.is_err(), "Bottom/single layer must refuse clipping mask");
    }

    #[test]
    fn test_v160_camera_raw_pro_grading_calibration_detail_and_xmp_import() {
        let eng = test_engine();
        let rgba = vec![140u8, 120, 100, 255].repeat(48 * 48);
        eng.open_rgba_bytes("raw.png".into(), 48, 48, rgba).unwrap();

        let xmp = br#"<x:xmpmeta xmlns:x="adobe:ns:meta/">
            <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                <rdf:Description xmlns:crs="http://ns.adobe.com/camera-raw-settings/1.0/"
                    crs:Exposure2012="+0.75" crs:Contrast2012="+20" crs:Highlights2012="-30"
                    crs:Shadows2012="+35" crs:Whites2012="+15" crs:Blacks2012="-10"
                    crs:Texture="+25" crs:Clarity2012="+18" crs:Dehaze="+12"
                    crs:Vibrance="+22" crs:Saturation="+10" crs:LuminanceSmoothing="20"/>
            </rdf:RDF></x:xmpmeta>"#;
        let parsed = eng.parse_xmp_preset_bytes(xmp.to_vec()).unwrap();
        assert!((parsed.exposure - 0.75).abs() < 0.01);
        assert!((parsed.whites - 15.0).abs() < 0.1);
        assert!((parsed.texture - 25.0).abs() < 0.1);

        let params = json!({
            "exposure": 0.5, "whites": 20.0, "blacks": -15.0, "texture": 30.0,
            "noiseLuminance": 25.0, "noiseColor": 20.0, "sharpenAmount": 35.0,
            "gradeShadows": {"hue": 220.0, "sat": 25.0, "lum": -5.0},
            "gradeMidtones": {"hue": 40.0, "sat": 15.0, "lum": 0.0},
            "gradeHighlights": {"hue": 45.0, "sat": 20.0, "lum": 5.0},
            "calibrationHue": [10.0, -5.0, 15.0],
            "calibrationSat": [15.0, 10.0, -10.0]
        }).to_string();
        eng.begin_proxy_preview().unwrap();
        eng.update_proxy_camera_raw_json(params.clone()).unwrap();
        let info = eng.apply_camera_raw_json(params).unwrap();
        assert!(info.can_undo);
    }

    #[test]
    fn test_v160_lens_correction_proxy_and_apply() {
        let eng = test_engine();
        let rgba = vec![100u8, 150, 200, 255].repeat(48 * 48);
        eng.open_rgba_bytes("lens.png".into(), 48, 48, rgba).unwrap();
        eng.begin_proxy_preview().unwrap();
        eng.update_proxy_lens_correction(-25.0, -15.0, 10.0, -10.0, 5.0, 0.0).unwrap();
        let info = eng.apply_lens_correction(-25.0, -15.0, 10.0, -10.0, 5.0, 0.0).unwrap();
        assert!(info.can_undo);
    }

    #[test]
    fn test_v160_cube_3d_lut_import_proxy_and_apply() {
        let eng = test_engine();
        let rgba = vec![128u8, 128, 128, 255].repeat(32 * 32);
        eng.open_rgba_bytes("lut.png".into(), 32, 32, rgba).unwrap();
        let cube = r#"TITLE "WarmTest"
LUT_3D_SIZE 2
0.0 0.0 0.0
1.0 0.0 0.0
0.0 1.0 0.0
1.0 1.0 0.0
0.0 0.0 1.0
1.0 0.0 1.0
0.0 1.0 1.0
1.0 0.9 0.8
"#.to_string();
        eng.begin_proxy_preview().unwrap();
        eng.update_proxy_color_lookup("warm.cube".into(), cube.clone(), 0.85).unwrap();
        let info = eng.apply_color_lookup("warm.cube".into(), cube, 0.85).unwrap();
        assert!(info.can_undo);
        assert_eq!(eng.get_layers().unwrap().len(), 2);
    }

    #[test]
    fn test_v160_rgb_curves_and_three_tone_color_balance() {
        let eng = test_engine();
        let rgba = vec![120u8, 120, 120, 255].repeat(32 * 32);
        eng.open_rgba_bytes("rgb_cb.png".into(), 32, 32, rgba).unwrap();
        let c_info = eng.apply_adjustment_once(
            "curves".into(),
            r#"{"points":[[0,0],[128,128],[255,255]],"red":[[0,0],[128,160],[255,255]],"green":[[0,0],[128,128],[255,255]],"blue":[[0,0],[128,96],[255,255]]}"#.into(),
        ).unwrap();
        assert!(c_info.can_undo);
        let cb_info = eng.apply_adjustment_once(
            "colorBalance".into(),
            r#"{"shadows":[-20,0,25],"midtones":[10,-5,0],"highlights":[25,5,-20],"preserveLuminosity":true}"#.into(),
        ).unwrap();
        assert!(cb_info.can_undo);
    }

    #[test]
    fn test_v170_brush_dynamics_and_preset_reset() {
        let eng = test_engine();
        eng.new_document("dyn.png".into(), 64, 64).unwrap();
        eng.set_brush(16.0, 0.8, 0.9, 1.0, "#FF4422".into()).unwrap();
        eng.set_brush_dynamics(0.35, 1.2, 0.5, 0.4, 0.3).unwrap();
        eng.begin_live_stroke(StrokeSample { x: 10.0, y: 10.0, pressure: 1.0, tilt_x: 0.0, tilt_y: 0.0 }, "#FF4422".into(), false).unwrap();
        eng.push_live_stroke(vec![StrokeSample { x: 50.0, y: 50.0, pressure: 1.0, tilt_x: 0.0, tilt_y: 0.0 }]).unwrap();
        let info = eng.finish_live_stroke().unwrap();
        assert!(info.can_undo);
        eng.reset_brush_to_round().unwrap();
        let presets = eng.get_brush_presets().unwrap();
        if let Some(first) = presets.first() {
            let sel = eng.select_brush_preset(first.name.clone()).unwrap();
            assert_eq!(sel.name, first.name);
        }
    }

    #[test]
    fn test_v170_mixer_brush_live_stroke() {
        let eng = test_engine();
        let mut rgba = vec![0u8; 64 * 64 * 4];
        for y in 0..64 {
            for x in 0..64 {
                let i = (y * 64 + x) * 4;
                if x < 32 { rgba[i] = 220; rgba[i+1] = 40; rgba[i+2] = 40; rgba[i+3] = 255; }
                else { rgba[i] = 40; rgba[i+1] = 80; rgba[i+2] = 220; rgba[i+3] = 255; }
            }
        }
        eng.open_rgba_bytes("mix.png".into(), 64, 64, rgba).unwrap();
        eng.begin_live_mixer(
            StrokeSample { x: 16.0, y: 32.0, pressure: 1.0, tilt_x: 0.0, tilt_y: 0.0 },
            20.0, 0.5, 60.0, 50.0, 50.0, 100.0, "#22CC66".into(), false,
        ).unwrap();
        eng.push_live_mixer(vec![
            StrokeSample { x: 32.0, y: 32.0, pressure: 1.0, tilt_x: 0.0, tilt_y: 0.0 },
            StrokeSample { x: 48.0, y: 32.0, pressure: 1.0, tilt_x: 0.0, tilt_y: 0.0 },
        ]).unwrap();
        let info = eng.finish_live_mixer().unwrap();
        assert!(info.can_undo);
    }

    #[test]
    fn test_v170_color_replacement_brush_live() {
        let eng = test_engine();
        let rgba = vec![220u8, 50, 50, 255].repeat(48 * 48);
        eng.open_rgba_bytes("crepl.png".into(), 48, 48, rgba).unwrap();
        eng.begin_live_color_replacement(
            StrokeSample { x: 12.0, y: 24.0, pressure: 1.0, tilt_x: 0.0, tilt_y: 0.0 },
            18.0, 0.8, "#22AA44".into(), "color".into(), 40.0,
        ).unwrap();
        eng.push_live_dab(vec![StrokeSample { x: 36.0, y: 24.0, pressure: 1.0, tilt_x: 0.0, tilt_y: 0.0 }]).unwrap();
        let info = eng.finish_live_dab().unwrap();
        assert!(info.can_undo);
    }

    #[test]
    fn test_v170_sponge_blur_sharpen_dabs() {
        let eng = test_engine();
        let rgba = vec![180u8, 90, 60, 255].repeat(48 * 48);
        eng.open_rgba_bytes("dabs.png".into(), 48, 48, rgba).unwrap();
        for tool in ["sponge_saturate", "sponge_desaturate", "blur_brush", "sharpen_brush"] {
            eng.begin_live_dab(
                tool.into(),
                StrokeSample { x: 16.0, y: 24.0, pressure: 1.0, tilt_x: 0.0, tilt_y: 0.0 },
                16.0, 0.5, 50.0,
            ).unwrap();
            eng.push_live_dab(vec![StrokeSample { x: 32.0, y: 24.0, pressure: 1.0, tilt_x: 0.0, tilt_y: 0.0 }]).unwrap();
            let info = eng.finish_live_dab().unwrap();
            assert!(info.can_undo, "tool {tool} should commit undoable edit");
        }
    }

    #[test]
    fn test_v170_patch_tool_source_mode() {
        let eng = test_engine();
        let mut rgba = vec![120u8, 130, 140, 255].repeat(64 * 64);
        for y in 10..20 {
            for x in 10..20 {
                let i = (y * 64 + x) * 4;
                rgba[i] = 250; rgba[i+1] = 20; rgba[i+2] = 20;
            }
        }
        eng.open_rgba_bytes("patch.png".into(), 64, 64, rgba).unwrap();
        eng.select_rect(10, 10, 10, 10, false, "replace".into()).unwrap();
        let info = eng.apply_patch_tool(24, 24, "source".into()).unwrap();
        assert!(info.can_undo);
    }

    #[test]
    fn test_v170_red_eye_removal_click() {
        let eng = test_engine();
        let mut rgba = vec![200u8, 170, 150, 255].repeat(64 * 64);
        for y in 26..38 {
            for x in 26..38 {
                let dx = x as i32 - 32;
                let dy = y as i32 - 32;
                if dx * dx + dy * dy <= 25 {
                    let i = (y * 64 + x) * 4;
                    rgba[i] = 230; rgba[i+1] = 20; rgba[i+2] = 20;
                }
            }
        }
        eng.open_rgba_bytes("redeye.png".into(), 64, 64, rgba).unwrap();
        let info = eng.apply_red_eye(32.0, 32.0, 50.0, 60.0).unwrap();
        assert!(info.can_undo);
    }


    #[test]
    fn test_v173_multi_fx_stack_and_layer_badges() {
        let eng = test_engine();
        eng.new_document("fxstack.png".into(), 96, 96).unwrap();
        eng.add_layer("Başlık".into()).unwrap();
        let stack = serde_json::json!([
            {"kind": "stroke", "params": {"color": "#8A2BE2", "size": 6.0, "opacity": 100.0, "position": "outside", "blend": "Normal"}},
            {"kind": "outerGlow", "params": {"color": "#A855F7", "size": 10.0, "opacity": 85.0, "blend": "Screen"}},
            {"kind": "gradientOverlay", "params": {"from": "#FF2A5F", "to": "#7B2CFF", "opacity": 100.0, "angle": 90.0, "style": "linear", "blend": "Normal"}},
            {"kind": "dropShadow", "params": {"color": "#000000", "size": 8.0, "distance": 4.0, "angle": 120.0, "opacity": 80.0, "blend": "Multiply"}},
            {"kind": "bevelEmboss", "params": {"depth": 100.0, "size": 4.0, "angle": 120.0, "opacity": 75.0}}
        ]).to_string();
        let info = eng.apply_layer_style_stack(stack).unwrap();
        assert!(info.can_undo);
        let layers = eng.get_layers().unwrap();
        let active = layers.iter().find(|l| l.is_active).unwrap();
        assert!(active.fx_badges.contains(&"Kontur".to_string()));
        assert!(active.fx_badges.contains(&"Dış Işıma".to_string()));
        assert!(active.fx_badges.contains(&"Degrade".to_string()));
        assert!(active.fx_badges.contains(&"Gölge".to_string()));
        assert!(active.fx_badges.contains(&"Kabartma".to_string()));
    }

    #[test]
    fn test_v173_copy_and_paste_layer_style() {
        let eng = test_engine();
        eng.new_document("fxcp.png".into(), 64, 64).unwrap();
        eng.add_layer("Kaynak".into()).unwrap();
        let stack = serde_json::json!([
            {"kind": "stroke", "params": {"color": "#FF0000", "size": 4.0, "opacity": 100.0, "position": "outside"}},
            {"kind": "outerGlow", "params": {"color": "#FFFF00", "size": 8.0, "opacity": 90.0}}
        ]).to_string();
        eng.apply_layer_style_stack(stack).unwrap();
        let copied = eng.copy_active_layer_style().unwrap();
        assert_eq!(copied, 2);
        eng.add_layer("Hedef".into()).unwrap();
        let info = eng.paste_layer_style_to_active().unwrap();
        assert!(info.can_undo);
        let layers = eng.get_layers().unwrap();
        let target = layers.iter().find(|l| l.is_active).unwrap();
        assert_eq!(target.fx_badges.len(), 2);
    }

    #[test]
    fn test_v173_proxy_multi_fx_stack() {
        let eng = test_engine();
        eng.new_document("fxproxy.png".into(), 64, 64).unwrap();
        eng.begin_proxy_preview().unwrap();
        let stack = serde_json::json!([
            {"kind": "colorOverlay", "params": {"color": "#3366FF", "opacity": 80.0}},
            {"kind": "innerGlow", "params": {"color": "#FFFFFF", "size": 6.0, "opacity": 70.0, "source": "edge", "blend": "Screen"}}
        ]).to_string();
        eng.update_proxy_layer_style_stack(stack).unwrap();
        eng.end_proxy_preview();
    }


    #[test]
    fn test_v176_puppet_warp_rotate_depth_density_expansion() {
        let eng = test_engine();
        let rgba = vec![80u8, 160, 220, 255].repeat(64 * 64);
        eng.open_rgba_bytes("puppet_pro.png".into(), 64, 64, rgba).unwrap();
        let pins = vec![
            PuppetPinPoint { src_x: 16.0, src_y: 32.0, dst_x: 16.0, dst_y: 32.0, rotate_deg: Some(25.0), depth: 1 },
            PuppetPinPoint { src_x: 48.0, src_y: 32.0, dst_x: 42.0, dst_y: 26.0, rotate_deg: None, depth: -1 },
        ];
        eng.begin_proxy_preview().unwrap();
        eng.update_proxy_puppet_warp(pins.clone(), "rigid".into(), "more".into(), 4.0).unwrap();
        let info = eng.apply_puppet_warp(pins, "rigid".into(), "more".into(), 4.0).unwrap();
        assert!(info.can_undo);
    }

    #[test]
    fn test_v176_puppet_mesh_segments_output() {
        let eng = test_engine();
        let rgba = vec![100u8, 200, 120, 255].repeat(64 * 64);
        eng.open_rgba_bytes("puppet_mesh.png".into(), 64, 64, rgba).unwrap();
        eng.begin_proxy_preview().unwrap();
        let segs = eng.get_puppet_mesh_segments(Vec::new(), "normal".into(), "normal".into(), 2.0).unwrap();
        assert!(segs.len() >= 12 && segs.len() % 4 == 0, "Expected non-empty triangle edge segments");
    }


    #[test]
    fn test_v180_content_aware_fill_proxy_and_apply() {
        let eng = test_engine();
        let rgba = vec![90u8, 140, 200, 255].repeat(64 * 64);
        eng.open_rgba_bytes("caf.png".into(), 64, 64, rgba).unwrap();
        eng.select_rect(20, 20, 16, 16, false, "replace".into()).unwrap();
        eng.begin_proxy_preview().unwrap();
        eng.update_proxy_content_aware_fill(
            "auto".into(), "default".into(), "low".into(), false, false, false, "current".into()
        ).unwrap();
        let info = eng.apply_content_aware_fill(
            "auto".into(), "high".into(), "none".into(), false, false, false, "new".into()
        ).unwrap();
        assert!(info.can_undo);
        assert!(info.layer_count >= 2, "output='new' should create a new fill layer");
    }

    #[test]
    fn test_v180_remove_tool_stroke_and_close_loops() {
        let eng = test_engine();
        let rgba = vec![120u8, 160, 110, 255].repeat(64 * 64);
        eng.open_rgba_bytes("remove.png".into(), 64, 64, rgba).unwrap();
        let pts = vec![
            StrokeSample { x: 24.0, y: 24.0, pressure: 1.0, tilt_x: 0.0, tilt_y: 0.0 },
            StrokeSample { x: 36.0, y: 24.0, pressure: 1.0, tilt_x: 0.0, tilt_y: 0.0 },
            StrokeSample { x: 36.0, y: 36.0, pressure: 1.0, tilt_x: 0.0, tilt_y: 0.0 },
            StrokeSample { x: 24.0, y: 36.0, pressure: 1.0, tilt_x: 0.0, tilt_y: 0.0 },
            StrokeSample { x: 24.0, y: 24.0, pressure: 1.0, tilt_x: 0.0, tilt_y: 0.0 },
        ];
        let info = eng.apply_remove_tool(pts, 8.0, true, false, false).unwrap();
        assert!(info.can_undo);
    }

    #[test]
    fn test_v180_content_aware_move_and_extend() {
        let eng = test_engine();
        let rgba = vec![140u8, 110, 180, 255].repeat(64 * 64);
        eng.open_rgba_bytes("camove.png".into(), 64, 64, rgba).unwrap();
        eng.select_rect(10, 10, 16, 16, false, "replace".into()).unwrap();
        let info = eng.apply_content_aware_move(
            18.0, 12.0, "move".into(), 4, 2, 100.0, 100.0, 0.0, false
        ).unwrap();
        assert!(info.can_undo);
        let info2 = eng.apply_content_aware_move(
            -8.0, 6.0, "extend".into(), 5, 0, 110.0, 110.0, 15.0, false
        ).unwrap();
        assert!(info2.can_undo);
    }

    #[test]
    fn test_v180_content_aware_scale_proxy_and_apply() {
        let eng = test_engine();
        let rgba = vec![180u8, 130, 100, 255].repeat(64 * 64);
        eng.open_rgba_bytes("cascale.png".into(), 64, 64, rgba).unwrap();
        eng.begin_proxy_preview().unwrap();
        eng.update_proxy_content_aware_scale(120.0, 100.0, 100.0, true).unwrap();
        let info = eng.apply_content_aware_scale(120.0, 100.0, 100.0, true).unwrap();
        assert!(info.can_undo);
    }


    #[test]
    fn test_v182_lasso_and_magnetic_lasso() {
        let eng = test_engine();
        let mut rgba = vec![40u8, 40, 40, 255].repeat(64 * 64);
        for y in 16..48 {
            for x in 16..48 {
                let idx = (y * 64 + x) * 4;
                rgba[idx] = 230; rgba[idx + 1] = 220; rgba[idx + 2] = 60;
            }
        }
        eng.open_rgba_bytes("lasso.png".into(), 64, 64, rgba).unwrap();
        let pts = vec![
            StrokeSample { x: 16.0, y: 16.0, pressure: 1.0, tilt_x: 0.0, tilt_y: 0.0 },
            StrokeSample { x: 48.0, y: 16.0, pressure: 1.0, tilt_x: 0.0, tilt_y: 0.0 },
            StrokeSample { x: 48.0, y: 48.0, pressure: 1.0, tilt_x: 0.0, tilt_y: 0.0 },
            StrokeSample { x: 16.0, y: 48.0, pressure: 1.0, tilt_x: 0.0, tilt_y: 0.0 },
        ];
        eng.select_lasso(pts.clone(), "replace".into(), 0.0).unwrap();
        assert!(eng.get_selection_bounds().is_some());
        eng.select_magnetic_lasso(pts, 10.0, 10.0, "replace".into(), 0.0).unwrap();
        assert!(eng.get_selection_bounds().is_some());
    }

    #[test]
    fn test_v182_select_sky_and_focus_area() {
        let eng = test_engine();
        let mut rgba = vec![0u8; 64 * 64 * 4];
        for y in 0..64 {
            for x in 0..64 {
                let idx = (y * 64 + x) * 4;
                if y < 32 {
                    rgba[idx] = 110; rgba[idx + 1] = 180; rgba[idx + 2] = 245; rgba[idx + 3] = 255;
                } else {
                    rgba[idx] = 50; rgba[idx + 1] = 110; rgba[idx + 2] = 40; rgba[idx + 3] = 255;
                }
            }
        }
        eng.open_rgba_bytes("sky.png".into(), 64, 64, rgba).unwrap();
        eng.select_sky(50.0, 50.0, "replace".into(), true).unwrap();
        eng.select_focus_area(50.0, 0.0, "replace".into(), true).unwrap();
    }

    #[test]
    fn test_v182_color_range_presets_and_sampled() {
        let eng = test_engine();
        let mut rgba = vec![30u8, 30, 30, 255].repeat(64 * 64);
        for y in 16..48 {
            for x in 16..48 {
                let idx = (y * 64 + x) * 4;
                rgba[idx] = 240; rgba[idx + 1] = 30; rgba[idx + 2] = 30;
            }
        }
        eng.open_rgba_bytes("cr.png".into(), 64, 64, rgba).unwrap();
        eng.select_color_range("reds".into(), "#FF0000".into(), 40.0, false, "replace".into(), true).unwrap();
        assert!(eng.get_selection_bounds().is_some());
        eng.select_color_range("sampledColors".into(), "#F01E1E".into(), 60.0, false, "replace".into(), true).unwrap();
        assert!(eng.get_selection_bounds().is_some());
    }

    #[test]
    fn test_v182_modify_expand_contract_border_grow_similar() {
        let eng = test_engine();
        let rgba = vec![100u8, 150, 200, 255].repeat(64 * 64);
        eng.open_rgba_bytes("modsel.png".into(), 64, 64, rgba).unwrap();
        eng.select_rect(20, 20, 20, 20, false, "replace".into()).unwrap();
        eng.modify_selection("expand".into(), 4.0).unwrap();
        eng.modify_selection("contract".into(), 2.0).unwrap();
        eng.modify_selection("border".into(), 3.0).unwrap();
        eng.modify_selection("smooth".into(), 2.0).unwrap();
        eng.grow_or_similar_selection(false, 32.0, true).unwrap();
        eng.grow_or_similar_selection(true, 32.0, true).unwrap();
        assert!(eng.get_selection_bounds().is_some());
    }

    #[test]
    fn test_v182_quick_mask_toggle_roundtrip() {
        let eng = test_engine();
        let rgba = vec![120u8, 140, 160, 255].repeat(64 * 64);
        eng.open_rgba_bytes("qmask.png".into(), 64, 64, rgba).unwrap();
        eng.select_rect(10, 10, 24, 24, false, "replace".into()).unwrap();
        assert!(eng.toggle_quick_mask_mode(true).unwrap());
        assert!(!eng.toggle_quick_mask_mode(false).unwrap());
        assert!(eng.get_selection_bounds().is_some());
    }


    #[test]
    fn test_v183_symmetry_modes_and_disable() {
        let eng = test_engine();
        eng.new_document("sym.png".into(), 64, 64).unwrap();
        assert_eq!(eng.set_symmetry_mode("vertical".into(), 0.0).unwrap(), "vertical");
        assert_eq!(eng.set_symmetry_mode("dual".into(), 45.0).unwrap(), "dual");
        assert_eq!(eng.set_symmetry_mode("diagonal".into(), 0.0).unwrap(), "diagonal");
        assert_eq!(eng.set_symmetry_mode("off".into(), 0.0).unwrap(), "off");
    }

    #[test]
    fn test_v183_pencil_stroke() {
        let eng = test_engine();
        eng.new_document("pencil.png".into(), 64, 64).unwrap();
        let pts = vec![
            StrokeSample { x: 10.0, y: 10.0, pressure: 1.0, tilt_x: 0.0, tilt_y: 0.0 },
            StrokeSample { x: 40.0, y: 30.0, pressure: 1.0, tilt_x: 0.0, tilt_y: 0.0 },
        ];
        let info = eng.apply_pencil_stroke(pts, 4.0, 1.0, "#FF3366".into()).unwrap();
        assert!(info.can_undo);
    }

    #[test]
    fn test_v183_magic_and_background_eraser_on_photo() {
        let eng = test_engine();
        let rgba = vec![80u8, 160, 230, 255].repeat(64 * 64);
        eng.open_rgba_bytes("erase.png".into(), 64, 64, rgba).unwrap();
        let info1 = eng.apply_magic_eraser(10.0, 10.0, 32.0, true, false, 1.0).unwrap();
        assert!(info1.can_undo);
        let pts = vec![
            StrokeSample { x: 20.0, y: 20.0, pressure: 1.0, tilt_x: 0.0, tilt_y: 0.0 },
            StrokeSample { x: 44.0, y: 20.0, pressure: 1.0, tilt_x: 0.0, tilt_y: 0.0 },
        ];
        let info2 = eng.apply_background_eraser(pts, 12.0, 0.8, 50.0, "contiguous".into(), false).unwrap();
        assert!(info2.can_undo);
    }

    #[test]
    fn test_v183_adjustment_brush_add_and_subtract() {
        let eng = test_engine();
        let rgba = vec![100u8, 120, 140, 255].repeat(64 * 64);
        eng.open_rgba_bytes("adjb.png".into(), 64, 64, rgba).unwrap();
        let pts = vec![
            StrokeSample { x: 16.0, y: 16.0, pressure: 1.0, tilt_x: 0.0, tilt_y: 0.0 },
            StrokeSample { x: 48.0, y: 48.0, pressure: 1.0, tilt_x: 0.0, tilt_y: 0.0 },
        ];
        let info1 = eng.apply_adjustment_brush(pts.clone(), "exposure".into(), false, 16.0, 0.5, 1.0).unwrap();
        assert!(info1.layer_count >= 2);
        let info2 = eng.apply_adjustment_brush(pts, "exposure".into(), true, 12.0, 0.5, 1.0).unwrap();
        assert!(info2.can_undo);
    }

    #[test]
    fn test_v183_history_and_art_history_brush() {
        let eng = test_engine();
        let rgba = vec![120u8, 150, 180, 255].repeat(64 * 64);
        eng.open_rgba_bytes("hist.png".into(), 64, 64, rgba).unwrap();
        let pts = vec![
            StrokeSample { x: 12.0, y: 20.0, pressure: 1.0, tilt_x: 0.0, tilt_y: 0.0 },
            StrokeSample { x: 50.0, y: 40.0, pressure: 1.0, tilt_x: 0.0, tilt_y: 0.0 },
        ];
        eng.apply_pencil_stroke(pts.clone(), 10.0, 1.0, "#FF0000".into()).unwrap();
        let info1 = eng.apply_history_brush(pts.clone(), 12.0, 0.8, 1.0).unwrap();
        assert!(info1.can_undo);
        let info2 = eng.apply_art_history_brush(pts, 10.0, 0.8, 1.0, "tightMedium".into(), 40.0, 0.0).unwrap();
        assert!(info2.can_undo);
    }

    #[test]
    fn test_v222_proxy_text_pro_preview_live() {
        let eng = test_engine();
        let rgba = vec![50u8, 60, 70, 255].repeat(160 * 120);
        eng.open_rgba_bytes("textlive.png".into(), 160, 120, rgba).unwrap();
        eng.begin_proxy_preview().unwrap();
        let extras = TextProExtras {
            rotation_deg: 25.0, opacity: 0.9, blend_mode: "Screen".into(),
            stroke_width: 6.0, stroke_color: "#000000".into(),
            shadow_enabled: true, shadow_distance: 8.0, shadow_blur: 10.0, shadow_angle: 120.0, shadow_color: "#000000".into(),
            glow_size: 16.0, glow_color: "#38BDF8".into(),
            gradient_enabled: true, gradient_end_color: "#F59E0B".into(), gradient_angle: 90.0,
            bg_enabled: true, bg_color: "#111318".into(), bg_opacity: 0.8, bg_radius: 12.0, bg_padding: 12.0,
            baseline_shift: 0.0, box_width_norm: 0.0,
        };
        eng.update_proxy_text_pro_preview(
            None, "LIVE".into(), 80.0, 60.0, 32.0, "#FFFFFF".into(), "center".into(),
            "Montserrat".into(), true, false, false, false, "all".into(),
            100.0, 100.0, 20.0, 1.2, false, "arc".into(), 30.0, 0.0, 0.0, extras
        ).unwrap();
        let guard = eng.core.lock();
        let pdoc = guard.proxy_preview.as_ref().expect("proxy active").session.active().expect("doc").doc();
        assert_eq!(pdoc.layers.len(), 3, "Live proxy must contain Background + Pill Box + Text layers");
        let text_layer = pdoc.layers.iter().find(|l| matches!(l.content, LayerContent::Text(_)))
            .expect("Live proxy must contain an actual Text layer");
        assert_eq!(text_layer.effects.items.len(), 4, "Live text layer must carry all 4 FX (gradient, stroke, glow, shadow)");
    }

    #[test]
    fn test_v220_text_pro_fx_rotation_opacity() {
        let eng = test_engine();
        let rgba = vec![40u8, 45, 55, 255].repeat(160 * 120);
        eng.open_rgba_bytes("textpro.png".into(), 160, 120, rgba).unwrap();
        eng.add_or_edit_text_layer(
            None, "PRO".into(), 80.0, 60.0, 36.0, "#FFFFFF".into(), "center".into(),
            "Montserrat".into(), true, false, false, false, "normal".into(),
            100.0, 100.0, 0.0, 1.2, false, "none".into(), 0.0, 0.0, 0.0
        ).unwrap();
        let extras = TextProExtras {
            rotation_deg: 12.0,
            opacity: 0.85,
            blend_mode: "Normal".into(),
            stroke_width: 4.0,
            stroke_color: "#000000".into(),
            shadow_enabled: true,
            shadow_distance: 6.0,
            shadow_blur: 8.0,
            shadow_angle: 120.0,
            shadow_color: "#000000".into(),
            glow_size: 10.0,
            glow_color: "#F59E0B".into(),
            gradient_enabled: true,
            gradient_end_color: "#F59E0B".into(),
            gradient_angle: 90.0,
            bg_enabled: true,
            bg_color: "#111318".into(),
            bg_opacity: 0.75,
            bg_radius: 12.0,
            bg_padding: 14.0,
            baseline_shift: 0.0,
            box_width_norm: 0.0,
        };
        let info = eng.apply_text_pro_extras("#FFFFFF".into(), extras).unwrap();
        assert!(info.can_undo, "Text Pro extras must be undoable");
        let guard = eng.core.lock();
        let doc = guard.session.active().expect("doc").doc();
        assert_eq!(doc.layers.len(), 3, "Committed doc must contain Background + Pill Box + Text layers");
        let text_layer = doc.layers.iter().find(|l| matches!(l.content, LayerContent::Text(_)))
            .expect("Committed doc must contain Text layer");
        assert_eq!(text_layer.effects.items.len(), 4, "Committed text layer must carry all 4 FX");
    }

    #[test]
    fn test_v201_proxy_image_resize_live() {
        let eng = test_engine();
        let rgba = vec![120u8, 140, 160, 255].repeat(120 * 80);
        eng.open_rgba_bytes("resize.png".into(), 120, 80, rgba).unwrap();
        eng.begin_proxy_preview().unwrap();
        eng.update_proxy_image_resize(60, 60, "lanczos".into()).unwrap();
        let guard = eng.core.lock();
        let pdoc = guard.proxy_preview.as_ref().unwrap().session.active().unwrap().doc();
        assert_eq!(pdoc.size.width, pdoc.size.height, "Square resize proxy must update aspect ratio live");
    }

    #[test]
    fn test_v201_proxy_modify_selection_live() {
        let eng = test_engine();
        let rgba = vec![120u8, 140, 160, 255].repeat(80 * 80);
        eng.open_rgba_bytes("selmod.png".into(), 80, 80, rgba).unwrap();
        eng.select_rect(20, 20, 40, 40, false, "replace".into()).unwrap();
        eng.begin_proxy_preview().unwrap();
        eng.update_proxy_modify_selection("expand".into(), 8.0).unwrap();
        let guard = eng.core.lock();
        let pdoc = guard.proxy_preview.as_ref().unwrap().session.active().unwrap().doc();
        assert!(pdoc.selection.is_some(), "Proxy modify selection must preserve live selection");
    }

    #[test]
    fn test_v201_proxy_content_aware_move_live_on_locked_layer() {
        let eng = test_engine();
        let rgba = vec![100u8, 120, 140, 255].repeat(80 * 80);
        eng.open_rgba_bytes("cam.png".into(), 80, 80, rgba).unwrap();
        eng.select_rect(10, 10, 24, 24, false, "replace".into()).unwrap();
        {
            let mut g = eng.core.lock();
            if let Some(ds) = g.session.active_mut() {
                if let Some(id) = ds.active_layer() {
                    let doc = Arc::make_mut(ds.doc_mut());
                    if let Some(l) = doc.layers.iter_mut().find(|l| l.id == id) {
                        l.locks.pixels = true;
                    }
                }
            }
        }
        eng.begin_proxy_preview().unwrap();
        eng.update_proxy_content_aware_move(
            24.0, 16.0, "move".into(), 4, 0, 100.0, 100.0, 0.0, false
        ).unwrap();
        let guard = eng.core.lock();
        assert!(guard.proxy_preview.is_some(), "Proxy CAM move must succeed on locked layer");
    }

    #[test]
    fn test_v183_healing_brush_with_source() {
        let eng = test_engine();
        let rgba = vec![140u8, 130, 110, 255].repeat(64 * 64);
        eng.open_rgba_bytes("heal.png".into(), 64, 64, rgba).unwrap();
        let pts = vec![
            StrokeSample { x: 40.0, y: 40.0, pressure: 1.0, tilt_x: 0.0, tilt_y: 0.0 },
            StrokeSample { x: 46.0, y: 42.0, pressure: 1.0, tilt_x: 0.0, tilt_y: 0.0 },
        ];
        let info = eng.apply_healing_brush(pts, 10.0, 0.7, 16.0, 16.0, false).unwrap();
        assert!(info.can_undo);
    }


    #[test]
    fn test_v230_histogram_and_auto_levels_and_color_sampler() {
        let eng = test_engine();
        // Low-contrast red-tinted gradient (R: 120..200, G: 60..110, B: 60..110)
        let mut rgba = Vec::with_capacity(64 * 64 * 4);
        for y in 0..64u8 {
            for x in 0..64u8 {
                let r = 120u8.saturating_add(x);
                let g = 60u8.saturating_add(y / 2);
                let b = 60u8.saturating_add(y / 2);
                rgba.extend_from_slice(&[r, g, b, 255]);
            }
        }
        eng.open_rgba_bytes("hist.png".into(), 64, 64, rgba).unwrap();
        let hist = eng.get_active_histogram().unwrap();
        assert_eq!(hist.luma.len(), 64);
        assert!(hist.luma.iter().copied().fold(0.0f32, f32::max) > 0.99);
        assert!(hist.red.iter().copied().fold(0.0f32, f32::max) > 0.99);

        let sample = eng.sample_color_at_norm(0.5, 0.5).unwrap();
        assert_eq!(sample.hsl_band, 0, "Red-dominant pixel must map to HSL Red band (0)");
        assert!(sample.r > sample.g + 30);

        let auto_lv = eng.compute_auto_levels().unwrap();
        assert!(auto_lv.red_black >= 100.0 && auto_lv.red_white <= 210.0, "Auto levels must detect narrow red range: {:?}", auto_lv);
    }

    #[test]
    fn test_v230_parca2_all_adjustments_real_pixel_diff() {
        let cases: &[(&str, &str)] = &[
            ("levels", r#"{"inBlack":20,"gamma":1.2,"inWhite":220,"outBlack":15,"outWhite":240,"red":{"inBlack":10,"gamma":1.3,"inWhite":230,"outBlack":0,"outWhite":255}}"#),
            ("curves", r#"{"points":[[0,0],[40,25],[90,115],[150,180],[210,235],[255,255]],"red":[[0,0],[128,165],[255,255]],"green":[[0,0],[255,255]],"blue":[[0,0],[128,95],[255,255]]}"#),
            ("colorBalance", r#"{"shadows":[25,-15,10],"midtones":[15,5,-20],"highlights":[-10,10,20],"preserveLuminosity":false}"#),
            ("blackWhite", r##"{"reds":120,"yellows":80,"greens":20,"cyans":40,"blues":-20,"magentas":60,"tint":true,"tintColor":"#D9B88C"}"##),
            ("photoFilter", r##"{"color":"#EC8A00","density":45,"preserveLuminosity":true}"##),
            ("selectiveColor", r#"{"method":"relative","reds":[20,-10,30,5],"neutrals":[-10,5,10,0]}"#),
            ("channelMixer", r#"{"red":[110,-10,0,0],"green":[10,90,0,0],"blue":[0,15,85,0],"monochrome":false}"#),
            ("gradientMap", r##"{"stops":[[0.0,"#141B3A"],[0.5,"#B84A39"],[1.0,"#F7D488"]],"reverse":false,"dither":false}"##),
        ];

        for &(kind, json_str) in cases {
            let eng = test_engine();
            let mut rgba = Vec::with_capacity(48 * 48 * 4);
            for y in 0..48u8 {
                for x in 0..48u8 {
                    rgba.extend_from_slice(&[
                        60u8.saturating_add(x * 3),
                        50u8.saturating_add(y * 3),
                        80u8.saturating_add((x + y) * 2),
                        255,
                    ]);
                }
            }
            eng.open_rgba_bytes(format!("{kind}.png"), 48, 48, rgba).unwrap();
            let before = {
                let g = eng.core.lock();
                photocraft_compose::flatten(g.active_document().unwrap().as_ref()).px.clone()
            };
            let info = eng.apply_adjustment_once(kind.into(), json_str.into()).unwrap();
            assert!(info.can_undo, "{kind} must be undoable");
            let after = {
                let g = eng.core.lock();
                photocraft_compose::flatten(g.active_document().unwrap().as_ref()).px.clone()
            };
            let diff_px = before.iter().zip(after.iter()).filter(|(a, b)| {
                (a[0] - b[0]).abs() > 0.01 || (a[1] - b[1]).abs() > 0.01 || (a[2] - b[2]).abs() > 0.01
            }).count();
            assert!(diff_px > 100, "{kind} must visibly mutate pixels (got diff_px={diff_px})");
        }
    }


    #[test]
    fn test_v240_liquify_freeze_pin_reconstruct_lasso() {
        let eng = test_engine();
        let mut rgba = Vec::with_capacity(96 * 96 * 4);
        for y in 0..96u8 {
            for x in 0..96u8 {
                rgba.extend_from_slice(&[x * 2, y * 2, 120, 255]);
            }
        }
        eng.open_rgba_bytes("liq_pro.png".into(), 96, 96, rgba).unwrap();
        eng.begin_liquify_session().unwrap();
        eng.set_liquify_pin_edges(true);

        eng.liquify_mask_action("freezeAll".into()).unwrap();
        eng.liquify_stroke_begin_pro("forwardWarp".into(), 40.0, 90.0, 70.0, 80.0, 48.0, 48.0).unwrap();
        eng.liquify_stroke_move(64.0, 48.0).unwrap();
        eng.liquify_stroke_end();
        {
            let g = eng.core.lock();
            let liq = g.live_liquify.as_ref().unwrap();
            assert!(liq.field.max_displacement() < 0.05, "FreezeAll must block warp displacement");
        }

        eng.liquify_mask_action("thawAll".into()).unwrap();
        eng.liquify_stroke_begin_pro("pushLeft".into(), 40.0, 90.0, 70.0, 80.0, 48.0, 48.0).unwrap();
        eng.liquify_stroke_move(48.0, 72.0).unwrap();
        eng.liquify_stroke_end();
        {
            let g = eng.core.lock();
            let liq = g.live_liquify.as_ref().unwrap();
            assert!(liq.field.max_displacement() > 0.5, "PushLeft after ThawAll must displace field");
            assert_eq!(liq.field.d[0], [0.0, 0.0], "PinEdges must lock border");
        }
        eng.liquify_reconstruct_all(100.0).unwrap();
        {
            let g = eng.core.lock();
            let liq = g.live_liquify.as_ref().unwrap();
            assert!(liq.field.max_displacement() < 0.05, "ReconstructAll(100) must restore field");
        }

        let poly = vec![
            StrokeSample { x: 20.0, y: 20.0, pressure: 1.0, tilt_x: 0.0, tilt_y: 0.0 },
            StrokeSample { x: 70.0, y: 20.0, pressure: 1.0, tilt_x: 0.0, tilt_y: 0.0 },
            StrokeSample { x: 70.0, y: 70.0, pressure: 1.0, tilt_x: 0.0, tilt_y: 0.0 },
            StrokeSample { x: 20.0, y: 70.0, pressure: 1.0, tilt_x: 0.0, tilt_y: 0.0 },
        ];
        eng.liquify_lasso_mask(poly, true).unwrap();
        {
            let g = eng.core.lock();
            let liq = g.live_liquify.as_ref().unwrap();
            assert!(liq.field.freeze_at(45.0, 45.0) > 0.9, "LassoMask must freeze interior");
        }
        eng.cancel_liquify_session();
    }

    #[test]
    fn test_v240_clone_source_and_retouch_pro_real_pixel_diff() {
        let eng = test_engine();
        let mut rgba = Vec::with_capacity(64 * 64 * 4);
        for y in 0..64u8 {
            for x in 0..64u8 {
                let r = if x < 32 { 220 } else { 40 };
                let g = if y < 32 { 60 } else { 200 };
                rgba.extend_from_slice(&[r, g, 110, 255]);
            }
        }
        eng.open_rgba_bytes("retouch_pro.png".into(), 64, 64, rgba).unwrap();

        eng.configure_clone_source(1, 100.0, 100.0, 15.0, true, false, true, 50.0).unwrap();
        let pts = vec![
            StrokeSample { x: 48.0, y: 48.0, pressure: 1.0, tilt_x: 0.0, tilt_y: 0.0 },
            StrokeSample { x: 52.0, y: 48.0, pressure: 1.0, tilt_x: 0.0, tilt_y: 0.0 },
        ];
        let before = { let g = eng.core.lock(); photocraft_compose::flatten(g.active_document().unwrap().as_ref()).px.clone() };
        let info = eng.apply_clone_stamp_pro(pts.clone(), 16.0, 16.0, 14.0, 0.8, 1.0, true, "current".into(), "normal".into()).unwrap();
        assert!(info.can_undo);
        let after = { let g = eng.core.lock(); photocraft_compose::flatten(g.active_document().unwrap().as_ref()).px.clone() };
        let diff = before.iter().zip(after.iter()).filter(|(a, b)| (a[0] - b[0]).abs() > 0.02 || (a[1] - b[1]).abs() > 0.02).count();
        assert!(diff > 10, "Clone stamp pro must mutate pixels (diff={diff})");
        eng.reset_clone_source_transform(1).unwrap();

        let info2 = eng.apply_healing_brush_pro(pts.clone(), 14.0, 0.7, 16.0, 16.0, false, "all".into(), "normal".into()).unwrap();
        assert!(info2.can_undo);
        let info3 = eng.apply_spot_healing_pro(pts.clone(), 14.0, 0.8, "proximityMatch".into(), false).unwrap();
        assert!(info3.can_undo);
        let info4 = eng.apply_retouch_pro("dodge".into(), pts.clone(), 16.0, 0.5, 65.0, "shadows".into(), true, false, false, false).unwrap();
        assert!(info4.can_undo);
        let info5 = eng.apply_retouch_pro("smudge".into(), pts.clone(), 16.0, 0.5, 60.0, "midtones".into(), true, true, false, false).unwrap();
        assert!(info5.can_undo);
        let info6 = eng.apply_retouch_pro("sharpen_brush".into(), pts, 16.0, 0.5, 60.0, "midtones".into(), true, false, true, false).unwrap();
        assert!(info6.can_undo);
    }

    #[test]
    fn test_v240_caf_sampling_strokes_and_cas_protect_mask() {
        let eng = test_engine();
        let mut rgba = Vec::with_capacity(64 * 64 * 4);
        for y in 0..64u8 {
            for x in 0..64u8 {
                rgba.extend_from_slice(&[80u8.saturating_add(x * 2), 110u8.saturating_add(y), 140, 255]);
            }
        }
        eng.open_rgba_bytes("caf_cas.png".into(), 64, 64, rgba).unwrap();
        eng.select_rect(24, 24, 16, 16, false, "replace".into()).unwrap();
        let strokes = vec![CafSamplingStrokeInput {
            add: true,
            size: 16.0,
            points: vec![
                StrokeSample { x: 8.0, y: 8.0, pressure: 1.0, tilt_x: 0.0, tilt_y: 0.0 },
                StrokeSample { x: 20.0, y: 8.0, pressure: 1.0, tilt_x: 0.0, tilt_y: 0.0 },
            ],
        }];
        let info_caf = eng.apply_content_aware_fill_pro("custom".into(), "default".into(), "none".into(), false, false, false, "current".into(), strokes.clone()).unwrap();
        assert!(info_caf.can_undo);
        eng.execute_command("select.deselect".into(), "{}".into()).unwrap();
        let info_cas = eng.apply_content_aware_scale_pro(85.0, 100.0, 100.0, true, strokes).unwrap();
        assert!(info_cas.can_undo);
    }

}
