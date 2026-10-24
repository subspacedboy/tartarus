use embedded_graphics::{
    draw_target::DrawTarget,
    geometry::{Point, Size},
    pixelcolor::Rgb565,
    prelude::*,
    primitives::{Circle, CornerRadii, PrimitiveStyle, PrimitiveStyleBuilder, Rectangle, RoundedRectangle},
    Drawable,
};
use qrcode::QrCode;

use super::font::{draw_centered_text, draw_text};
use super::theme::*;

pub fn draw_button_indicator<D: DrawTarget<Color = Rgb565>>(
    target: &mut D,
    button_indicator: i32,
) -> Result<(), D::Error> {
    let dot_color = match button_indicator {
        1 => COLOR_BLUE,
        2 => COLOR_GREEN,
        3 => COLOR_RED,
        _ => COLOR_GRAY_INACTIVE,
    };
    Circle::new(Point::new(6, 5), 10)
        .into_styled(PrimitiveStyle::with_fill(dot_color))
        .draw(target)?;
    Ok(())
}

pub fn draw_header<D: DrawTarget<Color = Rgb565>>(
    target: &mut D,
    title: &str,
    active_screen: i32,
    button_indicator: i32,
    is_emergency_available: bool,
) -> Result<(), D::Error> {
    // Background bar
    Rectangle::new(Point::new(0, 0), Size::new(DISPLAY_WIDTH, HEADER_HEIGHT))
        .into_styled(PrimitiveStyle::with_fill(COLOR_HEADER_BG))
        .draw(target)?;

    // Button indicator dot (leftmost)
    draw_button_indicator(target, button_indicator)?;

    // Title
    let title_color = if active_screen == 2 {
        COLOR_RED_LIGHT
    } else {
        COLOR_TEXT_LIGHT
    };
    draw_text(
        target,
        title,
        Point::new(24, 4),
        11,
        title_color,
        Some(COLOR_HEADER_BG),
    )?;

    // Pagination indicator dots
    if is_emergency_available {
        let screens = [0, 1, 2, 3, 4, 5];
        for (i, &s) in screens.iter().enumerate() {
            let x = 160 + (i as i32 * 12);
            let color = if s == active_screen {
                if s == 2 {
                    COLOR_RED
                } else {
                    COLOR_GREEN
                }
            } else if s == 2 {
                COLOR_RED_INACTIVE
            } else {
                COLOR_GRAY_DOT
            };
            Circle::new(Point::new(x, 6), 8)
                .into_styled(PrimitiveStyle::with_fill(color))
                .draw(target)?;
        }
    } else {
        let screens = [0, 1, 3, 4, 5];
        for (i, &s) in screens.iter().enumerate() {
            let x = 172 + (i as i32 * 12);
            let color = if s == active_screen {
                COLOR_GREEN
            } else {
                COLOR_GRAY_DOT
            };
            Circle::new(Point::new(x, 6), 8)
                .into_styled(PrimitiveStyle::with_fill(color))
                .draw(target)?;
        }
    }

    Ok(())
}

pub fn draw_footer<D: DrawTarget<Color = Rgb565>>(target: &mut D) -> Result<(), D::Error> {
    // Footer background bar
    Rectangle::new(Point::new(0, 117), Size::new(DISPLAY_WIDTH, FOOTER_HEIGHT))
        .into_styled(PrimitiveStyle::with_fill(COLOR_FOOTER_BG))
        .draw(target)?;

    draw_text(
        target,
        "D2: Next Screen",
        Point::new(8, 120),
        10,
        COLOR_TEXT_DIM,
        Some(COLOR_FOOTER_BG),
    )?;

    draw_text(
        target,
        "Tartarus v2",
        Point::new(160, 120),
        10,
        COLOR_TEXT_DARK,
        Some(COLOR_FOOTER_BG),
    )?;

    Ok(())
}

pub fn draw_badge<D: DrawTarget<Color = Rgb565>>(
    target: &mut D,
    center_x: i32,
    top_y: i32,
    width: u32,
    height: u32,
    border_color: Rgb565,
    bg_color: Rgb565,
    text: &str,
    text_color: Rgb565,
    font_size: u32,
) -> Result<(), D::Error> {
    let top_left = Point::new(center_x - (width as i32 / 2), top_y);
    let size = Size::new(width, height);

    let style = PrimitiveStyleBuilder::new()
        .stroke_color(border_color)
        .stroke_width(1)
        .fill_color(bg_color)
        .build();

    RoundedRectangle::new(
        Rectangle::new(top_left, size),
        CornerRadii::new(Size::new(6, 6)),
    )
    .into_styled(style)
    .draw(target)?;

    // Center text vertically in the badge
    let text_y = top_y + ((height as i32 - font_size as i32) / 2);
    draw_centered_text(
        target,
        text,
        center_x,
        text_y,
        font_size,
        text_color,
        Some(bg_color),
    )?;

    Ok(())
}

/// Draws an expanded, highly readable QR code filling the designated square.
pub fn draw_qr_code<D: DrawTarget<Color = Rgb565>>(
    target: &mut D,
    content: &str,
    box_x: i32,
    box_y: i32,
    box_size: u32,
) -> Result<(), D::Error> {
    // 1. Generate QR code with EcLevel::L (Low error correction) to minimize module count and maximize module pixel size
    let qr = match QrCode::with_error_correction_level(content.as_bytes(), qrcode::EcLevel::L) {
        Ok(q) => q,
        Err(_) => match QrCode::new(content.as_bytes()) {
            Ok(q) => q,
            Err(_) => return Ok(()),
        },
    };

    let qr_modules = qr.width() as u32;

    // Draw white background card with rounded corners
    let card_style = PrimitiveStyle::with_fill(COLOR_WHITE);
    RoundedRectangle::new(
        Rectangle::new(Point::new(box_x, box_y), Size::new(box_size, box_size)),
        CornerRadii::new(Size::new(4, 4)),
    )
    .into_styled(card_style)
    .draw(target)?;

    // Quiet zone padding (2px on each edge to maximize module size)
    let quiet_zone = 2u32;
    let inner_size = box_size.saturating_sub(quiet_zone * 2);
    let start_x = box_x + quiet_zone as i32;
    let start_y = box_y + quiet_zone as i32;

    let pixel_style = PrimitiveStyle::with_fill(COLOR_BG);

    // Map every module to the full inner_size using mapped rectangle spans
    for my in 0..qr_modules {
        let py_start = start_y + ((my * inner_size) / qr_modules) as i32;
        let py_end = start_y + (((my + 1) * inner_size) / qr_modules) as i32;
        let h = (py_end - py_start).max(1) as u32;

        for mx in 0..qr_modules {
            if qr[(mx as usize, my as usize)] == qrcode::Color::Dark {
                let px_start = start_x + ((mx * inner_size) / qr_modules) as i32;
                let px_end = start_x + (((mx + 1) * inner_size) / qr_modules) as i32;
                let w = (px_end - px_start).max(1) as u32;

                Rectangle::new(Point::new(px_start, py_start), Size::new(w, h))
                    .into_styled(pixel_style)
                    .draw(target)?;
            }
        }
    }

    Ok(())
}
