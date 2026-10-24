use std::fs;
use std::path::Path;
use std::env;

fn main() {
    let out_dir = env::var("OUT_DIR").unwrap();
    let file_path = "private.key";
    let file_exists = Path::new(file_path).exists();

    println!("cargo:rerun-if-changed={}", file_path);

    let file_content = if file_exists {
        let key_bytes = fs::read(file_path).unwrap();
        let hex_key = if key_bytes.len() == 32 {
            key_bytes.iter().map(|b| format!("{:02x}", b)).collect::<String>()
        } else {
            let s = String::from_utf8_lossy(&key_bytes).trim().to_string();
            if s.len() == 64 && s.chars().all(|c| c.is_ascii_hexdigit()) {
                s
            } else {
                key_bytes.iter().map(|b| format!("{:02x}", b)).collect::<String>()
            }
        };
        hex_key
    } else {
        String::new()
    };

    let dest_path = Path::new(&out_dir).join("key_parts.rs");
    fs::write(dest_path, file_content).unwrap();

    generate_fonts(&out_dir);

    sync_esp_idf_sys_files(Path::new(&env::var("CARGO_MANIFEST_DIR").unwrap()), &out_dir);

    embuild::espidf::sysenv::output();
}

fn generate_fonts(out_dir: &str) {
    let font_path = "ui/fonts/DejaVuSans.ttf";
    println!("cargo:rerun-if-changed={}", font_path);

    let font_data = fs::read(font_path).expect("Failed to read DejaVuSans.ttf");
    let font = rusttype::Font::try_from_bytes(&font_data).expect("Failed to parse DejaVuSans.ttf");

    let sizes: [u8; 9] = [9, 10, 11, 12, 13, 14, 15, 18, 22];

    let mut out = String::new();
    out.push_str("// Auto-generated pre-rasterized DejaVuSans font tables\n\n");
    out.push_str("#[derive(Clone, Copy)]\n");
    out.push_str("pub struct Glyph {\n");
    out.push_str("    pub advance: u8,\n");
    out.push_str("    pub width: u8,\n");
    out.push_str("    pub height: u8,\n");
    out.push_str("    pub x_offset: i8,\n");
    out.push_str("    pub y_offset: i8,\n");
    out.push_str("    pub bitmap: &'static [u8],\n");
    out.push_str("}\n\n");
    out.push_str("#[derive(Clone, Copy)]\n");
    out.push_str("pub struct FontFace {\n");
    out.push_str("    pub size: u8,\n");
    out.push_str("    pub glyphs: &'static [Glyph; 95],\n");
    out.push_str("}\n\n");
    out.push_str("impl FontFace {\n");
    out.push_str("    #[inline]\n");
    out.push_str("    pub fn get_glyph(&self, ch: char) -> &'static Glyph {\n");
    out.push_str("        let code = ch as usize;\n");
    out.push_str("        if (32..=126).contains(&code) {\n");
    out.push_str("            &self.glyphs[code - 32]\n");
    out.push_str("        } else {\n");
    out.push_str("            &self.glyphs[b'?' as usize - 32]\n");
    out.push_str("        }\n");
    out.push_str("    }\n");
    out.push_str("}\n\n");

    for &sz in &sizes {
        let scale = rusttype::Scale::uniform(sz as f32);
        let v_metrics = font.v_metrics(scale);

        out.push_str(&format!("// --- Font size {}px ---\n", sz));
        for code in 32u8..=126u8 {
            let ch = code as char;
            let glyph = font
                .glyph(ch)
                .scaled(scale)
                .positioned(rusttype::point(0.0, v_metrics.ascent));

            if let Some(bb) = glyph.pixel_bounding_box() {
                let w = (bb.max.x - bb.min.x) as usize;
                let h = (bb.max.y - bb.min.y) as usize;
                let mut bmp = vec![0u8; w * h];
                glyph.draw(|gx, gy, cov| {
                    if gx < w as u32 && gy < h as u32 {
                        bmp[gy as usize * w + gx as usize] = (cov * 255.0).round() as u8;
                    }
                });
                out.push_str(&format!(
                    "static GLYPH_{}_{}_DATA: [u8; {}] = {:?};\n",
                    sz, code, bmp.len(), bmp
                ));
            }
        }

        out.push_str(&format!("static GLYPHS_{}: [Glyph; 95] = [\n", sz));
        for code in 32u8..=126u8 {
            let ch = code as char;
            let glyph = font
                .glyph(ch)
                .scaled(scale)
                .positioned(rusttype::point(0.0, v_metrics.ascent));
            let adv = glyph.unpositioned().h_metrics().advance_width.round() as u8;

            if let Some(bb) = glyph.pixel_bounding_box() {
                let w = (bb.max.x - bb.min.x) as u8;
                let h = (bb.max.y - bb.min.y) as u8;
                let ox = bb.min.x as i8;
                let oy = bb.min.y as i8;
                out.push_str(&format!(
                    "    Glyph {{ advance: {}, width: {}, height: {}, x_offset: {}, y_offset: {}, bitmap: &GLYPH_{}_{}_DATA }},\n",
                    adv, w, h, ox, oy, sz, code
                ));
            } else {
                out.push_str(&format!(
                    "    Glyph {{ advance: {}, width: 0, height: 0, x_offset: 0, y_offset: 0, bitmap: &[] }},\n",
                    adv
                ));
            }
        }
        out.push_str("];\n\n");
        out.push_str(&format!(
            "pub static FONT_{}: FontFace = FontFace {{ size: {}, glyphs: &GLYPHS_{} }};\n\n",
            sz, sz, sz
        ));
    }

    out.push_str("pub fn get_font_face(size: u32) -> &'static FontFace {\n");
    out.push_str("    match size {\n");
    out.push_str("        9 => &FONT_9,\n");
    out.push_str("        10 => &FONT_10,\n");
    out.push_str("        11 => &FONT_11,\n");
    out.push_str("        12 => &FONT_12,\n");
    out.push_str("        13 => &FONT_13,\n");
    out.push_str("        14 => &FONT_14,\n");
    out.push_str("        15 => &FONT_15,\n");
    out.push_str("        18 => &FONT_18,\n");
    out.push_str("        22 => &FONT_22,\n");
    out.push_str("        _ if size < 10 => &FONT_9,\n");
    out.push_str("        _ if size < 12 => &FONT_11,\n");
    out.push_str("        _ if size < 15 => &FONT_13,\n");
    out.push_str("        _ if size < 20 => &FONT_18,\n");
    out.push_str("        _ => &FONT_22,\n");
    out.push_str("    }\n");
    out.push_str("}\n");

    let dest = Path::new(out_dir).join("dejavu_fonts.rs");
    fs::write(dest, out).expect("Write dejavu_fonts.rs");
}

fn sync_esp_idf_sys_files(manifest_dir: &Path, out_dir: &str) {
    let partitions_file = manifest_dir.join("partitions.csv");
    let signing_key_file = manifest_dir.join("secure_boot_signing_key.pem");

    println!("cargo:rerun-if-changed={}", partitions_file.display());
    if signing_key_file.exists() {
        println!("cargo:rerun-if-changed={}", signing_key_file.display());
    }

    let partitions_bytes = if partitions_file.exists() {
        fs::read(&partitions_file).ok()
    } else {
        None
    };

    let signing_key_bytes = if signing_key_file.exists() {
        fs::read(&signing_key_file).ok()
    } else {
        None
    };

    // Locate target build directory: OUT_DIR is target/xtensa-esp32s3-espidf/<profile>/build/tartarus-firmware-<hash>/out
    if let Some(build_dir) = Path::new(out_dir).ancestors().nth(2) {
        if let Ok(entries) = fs::read_dir(build_dir) {
            for entry in entries.flatten() {
                let name = entry.file_name();
                let name_str = name.to_string_lossy();
                if name_str.starts_with("esp-idf-sys-") {
                    let out_path = entry.path().join("out");
                    if out_path.is_dir() {
                        if let Some(ref bytes) = partitions_bytes {
                            let dest = out_path.join("partitions.csv");
                            let should_write = match fs::read(&dest) {
                                Ok(ref existing) => existing != bytes,
                                Err(_) => true,
                            };
                            if should_write {
                                let _ = fs::write(&dest, bytes);
                            }
                        }
                        if let Some(ref bytes) = signing_key_bytes {
                            let dest = out_path.join("secure_boot_signing_key.pem");
                            let should_write = match fs::read(&dest) {
                                Ok(ref existing) => existing != bytes,
                                Err(_) => true,
                            };
                            if should_write {
                                let _ = fs::write(&dest, bytes);
                            }
                        }
                    }
                }
            }
        }
    }
}
