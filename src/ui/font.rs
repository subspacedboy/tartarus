include!(concat!(env!("OUT_DIR"), "/dejavu_fonts.rs"));

use embedded_graphics::{
    draw_target::DrawTarget,
    geometry::Point,
    pixelcolor::{Rgb565, RgbColor},
    Pixel,
};

use super::theme::{COLOR_BG, DISPLAY_HEIGHT, DISPLAY_WIDTH};

#[inline]
pub fn blend_color(fg: Rgb565, bg: Rgb565, alpha: u8) -> Rgb565 {
    if alpha >= 240 {
        return fg;
    }
    let a = alpha as u16;
    let inv_a = 255 - a;

    let r = ((fg.r() as u16 * a + bg.r() as u16 * inv_a) / 255) as u8;
    let g = ((fg.g() as u16 * a + bg.g() as u16 * inv_a) / 255) as u8;
    let b = ((fg.b() as u16 * a + bg.b() as u16 * inv_a) / 255) as u8;

    Rgb565::new(r, g, b)
}

pub fn measure_text_width(text: &str, font_size: u32) -> u32 {
    let font = get_font_face(font_size);
    let mut width = 0u32;
    for ch in text.chars() {
        let glyph = font.get_glyph(ch);
        width += glyph.advance as u32;
    }
    width
}

pub fn draw_text<D: DrawTarget<Color = Rgb565>>(
    target: &mut D,
    text: &str,
    pos: Point,
    font_size: u32,
    color: Rgb565,
    bg_color: Option<Rgb565>,
) -> Result<(), D::Error> {
    let font = get_font_face(font_size);
    let effective_bg = bg_color.unwrap_or(COLOR_BG);
    let mut pixels = Vec::new();
    let mut cursor_x = pos.x;

    for ch in text.chars() {
        let glyph = font.get_glyph(ch);
        if glyph.width > 0 && glyph.height > 0 {
            let gx = cursor_x + glyph.x_offset as i32;
            let gy = pos.y + glyph.y_offset as i32;
            let w = glyph.width as usize;

            for row in 0..glyph.height {
                for col in 0..glyph.width {
                    let alpha = glyph.bitmap[row as usize * w + col as usize];
                    if alpha < 25 {
                        continue;
                    }
                    let px = gx + col as i32;
                    let py = gy + row as i32;
                    if px >= 0 && px < DISPLAY_WIDTH as i32 && py >= 0 && py < DISPLAY_HEIGHT as i32 {
                        let blended = blend_color(color, effective_bg, alpha);
                        pixels.push(Pixel(Point::new(px, py), blended));
                    }
                }
            }
        }
        cursor_x += glyph.advance as i32;
    }

    target.draw_iter(pixels)?;
    Ok(())
}

pub fn draw_centered_text<D: DrawTarget<Color = Rgb565>>(
    target: &mut D,
    text: &str,
    center_x: i32,
    top_y: i32,
    font_size: u32,
    color: Rgb565,
    bg_color: Option<Rgb565>,
) -> Result<(), D::Error> {
    let width = measure_text_width(text, font_size) as i32;
    let x = center_x - (width / 2);
    draw_text(target, text, Point::new(x, top_y), font_size, color, bg_color)?;
    Ok(())
}
