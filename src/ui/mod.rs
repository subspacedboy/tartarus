pub mod components;
pub mod font;
pub mod screens;
pub mod theme;

use std::time::Instant;
use embedded_graphics::{draw_target::DrawTarget, pixelcolor::Rgb565};

use self::components::{draw_button_indicator, draw_footer, draw_header};
use self::screens::*;
use self::theme::*;

pub struct AppWindow {
    pub active_screen: i32,
    pub screen_title: String,
    pub is_emergency_screen_available: bool,
    pub emergency_unlocked: bool,
    pub emergency_press_count: i32,
    pub is_locked: bool,
    pub needs_config: bool,
    pub ble_pin: String,
    pub ble_authenticated: bool,
    pub ble_locked_out: bool,
    pub contract_id: String,
    pub wifi_connected: bool,
    pub wifi_ssid: String,
    pub firmware_version: String,
    pub next_firmware_version: String,
    pub firmware_upgrade_available: bool,
    pub button_indicator: i32,
    pub qr_content: Option<String>,

    // Animation & dirty tracking
    blink_start: Option<Instant>,
    last_blink_state: Option<bool>,
    screen_dirty: bool,
    indicator_dirty: bool,
}

impl Default for AppWindow {
    fn default() -> Self {
        Self::new()
    }
}

impl AppWindow {
    pub fn new() -> Self {
        Self {
            active_screen: 0,
            screen_title: "PAIRING / QR".to_string(),
            is_emergency_screen_available: false,
            emergency_unlocked: false,
            emergency_press_count: 0,
            is_locked: false,
            needs_config: false,
            ble_pin: "000000".to_string(),
            ble_authenticated: false,
            ble_locked_out: false,
            contract_id: String::new(),
            wifi_connected: false,
            wifi_ssid: String::new(),
            firmware_version: String::new(),
            next_firmware_version: String::new(),
            firmware_upgrade_available: false,
            button_indicator: 0,
            qr_content: None,
            blink_start: None,
            last_blink_state: None,
            screen_dirty: true,
            indicator_dirty: false,
        }
    }

    // Setters matching Slint API
    pub fn set_active_screen(&mut self, val: i32) {
        if self.active_screen != val {
            self.active_screen = val;
            self.screen_dirty = true;
        }
    }

    pub fn set_screen_title(&mut self, val: String) {
        if self.screen_title != val {
            self.screen_title = val;
            self.screen_dirty = true;
        }
    }

    pub fn set_is_emergency_screen_available(&mut self, val: bool) {
        if self.is_emergency_screen_available != val {
            self.is_emergency_screen_available = val;
            self.screen_dirty = true;
        }
    }

    pub fn set_emergency_unlocked(&mut self, val: bool) {
        if self.emergency_unlocked != val {
            self.emergency_unlocked = val;
            self.screen_dirty = true;
        }
    }

    pub fn set_emergency_press_count(&mut self, val: i32) {
        if self.emergency_press_count != val {
            self.emergency_press_count = val;
            self.screen_dirty = true;
        }
    }

    pub fn set_is_locked(&mut self, val: bool) {
        if self.is_locked != val {
            self.is_locked = val;
            self.screen_dirty = true;
        }
    }

    pub fn set_needs_config(&mut self, val: bool) {
        if self.needs_config != val {
            self.needs_config = val;
            self.screen_dirty = true;
        }
    }

    pub fn set_ble_pin(&mut self, val: String) {
        if self.ble_pin != val {
            self.ble_pin = val;
            self.screen_dirty = true;
        }
    }

    pub fn set_ble_authenticated(&mut self, val: bool) {
        if self.ble_authenticated != val {
            self.ble_authenticated = val;
            self.screen_dirty = true;
        }
    }

    pub fn set_ble_locked_out(&mut self, val: bool) {
        if self.ble_locked_out != val {
            self.ble_locked_out = val;
            self.screen_dirty = true;
        }
    }

    pub fn set_contract_id(&mut self, val: String) {
        if self.contract_id != val {
            self.contract_id = val;
            self.screen_dirty = true;
        }
    }

    pub fn set_wifi_connected(&mut self, val: bool) {
        if self.wifi_connected != val {
            self.wifi_connected = val;
            self.screen_dirty = true;
        }
    }

    pub fn set_wifi_ssid(&mut self, val: String) {
        if self.wifi_ssid != val {
            self.wifi_ssid = val;
            self.screen_dirty = true;
        }
    }

    pub fn set_firmware_version(&mut self, val: String) {
        if self.firmware_version != val {
            self.firmware_version = val;
            self.screen_dirty = true;
        }
    }

    pub fn set_next_firmware_version(&mut self, val: String) {
        if self.next_firmware_version != val {
            self.next_firmware_version = val;
            self.screen_dirty = true;
        }
    }

    pub fn set_firmware_upgrade_available(&mut self, val: bool) {
        if self.firmware_upgrade_available != val {
            self.firmware_upgrade_available = val;
            self.screen_dirty = true;
        }
    }

    /// Button indicator only dirties the 10px dot in the header, avoiding full screen repaints.
    pub fn set_button_indicator(&mut self, val: i32) {
        if self.button_indicator != val {
            self.button_indicator = val;
            self.indicator_dirty = true;
        }
    }

    pub fn set_qr_content(&mut self, val: Option<String>) {
        if self.qr_content != val {
            self.qr_content = val;
            self.screen_dirty = true;
        }
    }

    pub fn invoke_trigger_unlock_blink(&mut self) {
        self.blink_start = Some(Instant::now());
        self.screen_dirty = true;
    }

    pub fn render_if_dirty<D: DrawTarget<Color = Rgb565>>(
        &mut self,
        target: &mut D,
    ) -> Result<(), D::Error> {
        // Handle blink animation
        if let Some(start) = self.blink_start {
            let elapsed_ms = start.elapsed().as_millis();
            if elapsed_ms < 720 {
                let phase = (elapsed_ms / 120) as u8;
                let visible = (phase % 2) == 0;
                if self.last_blink_state != Some(visible) {
                    self.last_blink_state = Some(visible);
                    if visible {
                        target.clear(COLOR_GREEN)?;
                        return Ok(());
                    } else {
                        // In between flashes, redraw normal screen
                        self.screen_dirty = true;
                    }
                } else if visible {
                    return Ok(());
                }
            } else {
                self.blink_start = None;
                self.last_blink_state = None;
                self.screen_dirty = true;
            }
        }

        if self.screen_dirty {
            // Full screen repaint (done exactly once on state change)
            target.clear(COLOR_BG)?;

            // Draw persistent top header
            draw_header(
                target,
                &self.screen_title,
                self.active_screen,
                self.button_indicator,
                self.is_emergency_screen_available,
            )?;

            // Draw active screen content
            let data = UiData {
                active_screen: self.active_screen,
                screen_title: &self.screen_title,
                is_emergency_screen_available: self.is_emergency_screen_available,
                emergency_unlocked: self.emergency_unlocked,
                emergency_press_count: self.emergency_press_count,
                is_locked: self.is_locked,
                needs_config: self.needs_config,
                ble_pin: &self.ble_pin,
                ble_authenticated: self.ble_authenticated,
                ble_locked_out: self.ble_locked_out,
                contract_id: &self.contract_id,
                wifi_connected: self.wifi_connected,
                wifi_ssid: &self.wifi_ssid,
                firmware_version: &self.firmware_version,
                next_firmware_version: &self.next_firmware_version,
                firmware_upgrade_available: self.firmware_upgrade_available,
                button_indicator: self.button_indicator,
                qr_content: self.qr_content.as_deref(),
            };

            match self.active_screen {
                0 => draw_screen_0_qr(target, &data)?,
                1 => draw_screen_1_lock(target, &data)?,
                2 => draw_screen_2_emergency(target, &data)?,
                3 => draw_screen_3_ble(target, &data)?,
                4 => draw_screen_4_wifi(target, &data)?,
                5 => draw_screen_5_firmware(target, &data)?,
                _ => {}
            }

            // Draw persistent bottom footer
            draw_footer(target)?;

            self.screen_dirty = false;
            self.indicator_dirty = false;
        } else if self.indicator_dirty {
            // ONLY the 10px button indicator dot changed!
            // Redraw only the dot at (6, 5) without clearing or repainting the screen.
            draw_button_indicator(target, self.button_indicator)?;
            self.indicator_dirty = false;
        }

        Ok(())
    }
}
