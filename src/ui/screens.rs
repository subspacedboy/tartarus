use embedded_graphics::{
    draw_target::DrawTarget,
    geometry::Point,
    pixelcolor::Rgb565,
    prelude::*,
    primitives::{Circle, PrimitiveStyleBuilder},
    Drawable,
};

use super::components::{draw_badge, draw_qr_code};
use super::font::{draw_centered_text, draw_text};
use super::theme::*;

pub struct UiData<'a> {
    pub active_screen: i32,
    pub screen_title: &'a str,
    pub is_emergency_screen_available: bool,
    pub emergency_unlocked: bool,
    pub emergency_press_count: i32,
    pub is_locked: bool,
    pub needs_config: bool,
    pub ble_pin: &'a str,
    pub ble_authenticated: bool,
    pub ble_locked_out: bool,
    pub contract_id: &'a str,
    pub wifi_connected: bool,
    pub wifi_ssid: &'a str,
    pub firmware_version: &'a str,
    pub next_firmware_version: &'a str,
    pub firmware_upgrade_available: bool,
    pub button_indicator: i32,
    pub qr_content: Option<&'a str>,
}

pub fn draw_screen_0_qr<D: DrawTarget<Color = Rgb565>>(
    target: &mut D,
    data: &UiData,
) -> Result<(), D::Error> {
    if data.needs_config {
        draw_centered_text(
            target,
            "Configuration Needed",
            120,
            46,
            14,
            COLOR_RED_LIGHT,
            None,
        )?;
        draw_centered_text(
            target,
            "Scan config payload to initialize",
            120,
            70,
            11,
            COLOR_TEXT_MUTED,
            None,
        )?;
    } else {
        // QR Code box on left: expanded to 95x95 (taking almost the entire 97px vertical area)
        if let Some(content) = data.qr_content {
            draw_qr_code(target, content, 8, 21, 95)?;
        }

        // Text details on right
        draw_text(
            target,
            "Tartarus Lock",
            Point::new(110, 36),
            13,
            COLOR_GREEN,
            None,
        )?;
        draw_text(
            target,
            "Scan to pair",
            Point::new(110, 58),
            11,
            COLOR_WHITE,
            None,
        )?;
        draw_text(
            target,
            "or enter contract",
            Point::new(110, 78),
            9,
            COLOR_TEXT_DIM,
            None,
        )?;
    }
    Ok(())
}

pub fn draw_screen_1_lock<D: DrawTarget<Color = Rgb565>>(
    target: &mut D,
    data: &UiData,
) -> Result<(), D::Error> {
    // Status Badge
    let (width, border, bg, text, text_color, font_size) = if data.emergency_unlocked {
        (
            190,
            COLOR_RED,
            COLOR_RED_DARK,
            "EMERGENCY UNLOCKED",
            COLOR_RED_LIGHT,
            13,
        )
    } else if data.is_locked {
        (
            150,
            COLOR_GREEN,
            COLOR_GREEN_DARK,
            "LOCKED",
            COLOR_GREEN,
            18,
        )
    } else {
        (
            150,
            COLOR_ORANGE,
            COLOR_ORANGE_DARK,
            "UNLOCKED",
            COLOR_ORANGE,
            18,
        )
    };

    draw_badge(
        target,
        120,
        30,
        width,
        36,
        border,
        bg,
        text,
        text_color,
        font_size,
    )?;

    // Contract label
    let contract_text = if !data.contract_id.is_empty() {
        format!("Contract: {}", data.contract_id)
    } else {
        "No Active Contract".to_string()
    };
    draw_centered_text(
        target,
        &contract_text,
        120,
        74,
        11,
        COLOR_TEXT_MUTED,
        None,
    )?;

    // Instruction label
    let hint_text = if data.emergency_unlocked {
        "Awaiting explicit LOCK command"
    } else {
        "D1: Toggle Lock"
    };
    let hint_color = if data.emergency_unlocked {
        COLOR_RED_LIGHT
    } else {
        COLOR_TEXT_DIM
    };
    draw_centered_text(target, hint_text, 120, 94, 9, hint_color, None)?;

    Ok(())
}

pub fn draw_screen_2_emergency<D: DrawTarget<Color = Rgb565>>(
    target: &mut D,
    data: &UiData,
) -> Result<(), D::Error> {
    // Emergency Badge
    draw_badge(
        target,
        120,
        26,
        170,
        32,
        COLOR_RED,
        COLOR_RED_DARK,
        "EMERGENCY UNLOCK",
        COLOR_RED_LIGHT,
        13,
    )?;

    draw_centered_text(
        target,
        "Press D1 5 times to unlock",
        120,
        64,
        11,
        COLOR_WHITE,
        None,
    )?;

    // Visual Progress Dots (5 dots)
    let dot_start_x = 74;
    let dot_y = 80;
    for i in 1..=5 {
        let x = dot_start_x + (i - 1) * 20;
        let (fill, stroke) = if data.emergency_press_count >= i {
            if i == 5 {
                (COLOR_GREEN, COLOR_GREEN)
            } else {
                (COLOR_RED, COLOR_RED_LIGHT)
            }
        } else {
            (COLOR_GRAY_BOX, COLOR_GRAY_BOX_BORDER)
        };

        let style = PrimitiveStyleBuilder::new()
            .fill_color(fill)
            .stroke_color(stroke)
            .stroke_width(1)
            .build();

        Circle::new(Point::new(x, dot_y), 12)
            .into_styled(style)
            .draw(target)?;
    }

    let progress_text = format!("Progress: {} / 5", data.emergency_press_count);
    draw_centered_text(
        target,
        &progress_text,
        120,
        98,
        9,
        COLOR_TEXT_MUTED,
        None,
    )?;

    Ok(())
}

pub fn draw_screen_3_ble<D: DrawTarget<Color = Rgb565>>(
    target: &mut D,
    data: &UiData,
) -> Result<(), D::Error> {
    // PIN Badge
    let (border, bg, text, text_color, font_size) = if data.ble_locked_out {
        (
            COLOR_RED,
            COLOR_RED_DARK,
            "LOCKED OUT",
            COLOR_RED_LIGHT,
            15,
        )
    } else if data.ble_authenticated {
        (
            COLOR_GREEN,
            COLOR_GREEN_DARK,
            data.ble_pin,
            COLOR_GREEN,
            22,
        )
    } else {
        (
            COLOR_BLUE,
            COLOR_BLUE_DARK,
            data.ble_pin,
            COLOR_WHITE,
            22,
        )
    };

    draw_badge(
        target, 120, 28, 150, 38, border, bg, text, text_color, font_size,
    )?;

    let status_text = if data.ble_locked_out {
        "Press D1 to reset PIN"
    } else if data.ble_authenticated {
        "Authenticated via Bluetooth"
    } else {
        "Bluetooth Pairing PIN"
    };
    let status_color = if data.ble_locked_out {
        COLOR_RED_LIGHT
    } else if data.ble_authenticated {
        COLOR_GREEN
    } else {
        COLOR_TEXT_MUTED
    };

    draw_centered_text(target, status_text, 120, 74, 11, status_color, None)?;
    draw_centered_text(target, "D1: Regenerate PIN", 120, 94, 9, COLOR_TEXT_DIM, None)?;

    Ok(())
}

pub fn draw_screen_4_wifi<D: DrawTarget<Color = Rgb565>>(
    target: &mut D,
    data: &UiData,
) -> Result<(), D::Error> {
    // Row 1: Status
    draw_text(target, "Status:", Point::new(20, 38), 12, COLOR_TEXT_DIM, None)?;
    let (conn_text, conn_color) = if data.wifi_connected {
        ("Connected", COLOR_GREEN)
    } else {
        ("Disconnected", COLOR_RED_LIGHT)
    };
    draw_text(target, conn_text, Point::new(80, 38), 12, conn_color, None)?;

    // Row 2: SSID
    draw_text(target, "SSID:", Point::new(20, 62), 12, COLOR_TEXT_DIM, None)?;
    let ssid_text = if !data.wifi_ssid.is_empty() {
        data.wifi_ssid
    } else {
        "(none)"
    };
    draw_text(target, ssid_text, Point::new(80, 62), 12, COLOR_WHITE, None)?;

    // Row 3: Helper
    draw_text(
        target,
        "Scan 'WIFI:S:...' QR to configure",
        Point::new(20, 88),
        9,
        COLOR_GRAY_BOX_BORDER,
        None,
    )?;

    Ok(())
}

pub fn draw_screen_5_firmware<D: DrawTarget<Color = Rgb565>>(
    target: &mut D,
    data: &UiData,
) -> Result<(), D::Error> {
    // Row 1: Version
    draw_text(target, "Version:", Point::new(20, 38), 12, COLOR_TEXT_DIM, None)?;
    draw_text(
        target,
        data.firmware_version,
        Point::new(85, 38),
        12,
        COLOR_WHITE,
        None,
    )?;

    // Row 2: Upgrade status
    if data.firmware_upgrade_available {
        draw_text(target, "Upgrade:", Point::new(20, 65), 12, COLOR_YELLOW, None)?;
        let upgrade_str = format!("{} (Press D1)", data.next_firmware_version);
        draw_text(target, &upgrade_str, Point::new(85, 65), 12, COLOR_YELLOW, None)?;
    } else {
        draw_text(
            target,
            "Firmware is up to date",
            Point::new(20, 65),
            10,
            COLOR_GREEN,
            None,
        )?;
    }

    Ok(())
}
