use crate::lock_ctx::LockCtx;
use crate::prelude::MySPI;
use crate::screen_ids::ScreenId;
use crate::screen_state::ScreenState;
use crate::verifier::VerifiedType;
use crate::wifi_util::parse_wifi_qr;
use embedded_graphics::mono_font::ascii::FONT_10X20;
use embedded_graphics::mono_font::MonoTextStyle;
use embedded_graphics::text::{Alignment, Text};
use embedded_graphics_core::geometry::Point;
use embedded_graphics_core::pixelcolor::Rgb565;
use embedded_graphics_core::prelude::{DrawTarget, RgbColor};
use embedded_graphics_core::Drawable;
use esp_idf_hal::gpio::{AnyOutputPin, GpioError};

pub struct WifiInfoScreen {
    needs_redraw: bool,
    error_string: Option<String>,
    text: String,
}

impl WifiInfoScreen {
    pub fn new() -> Self {
        Self {
            needs_redraw: true,
            error_string: None,
            text: "Wifi Info".to_string(),
        }
    }
}

impl ScreenState for WifiInfoScreen {
    type SPI = MySPI<'static>;
    type PinE = GpioError;
    type DC = AnyOutputPin;
    type RST = AnyOutputPin;

    fn on_update(&mut self, lock_ctx: &mut LockCtx) -> Option<usize> {
        if let Some(update) = &lock_ctx.this_update {
            if let Some(incoming_data) = update.qr_data.clone() {
                if let Ok(maybe_string) = String::from_utf8(incoming_data) {
                    if maybe_string.starts_with("WIFI:") {
                        let maybe_creds = parse_wifi_qr(maybe_string);

                        if let Some((ssid, password)) = maybe_creds {
                            if lock_ctx.connect_wifi(&ssid, &password).is_ok() {
                                self.text = ssid;
                            } else {
                                self.text = "Couldn't connect".to_string()
                            }
                            self.needs_redraw = true;
                        }
                    }
                }
            }
        }

        None
    }

    fn process_command(
        &mut self,
        _lock_ctx: &mut LockCtx,
        _command: VerifiedType,
    ) -> Result<Option<ScreenId>, String> {
        Ok(None)
    }

    fn draw_screen(&mut self, lock_ctx: &mut LockCtx) {
        lock_ctx
            .display
            .clear(Rgb565::BLACK)
            .expect("Screen should have cleared");

        let style = MonoTextStyle::new(&FONT_10X20, Rgb565::GREEN);
        let error_style = MonoTextStyle::new(&FONT_10X20, Rgb565::RED);

        let banner_position = Point::new(60, 20);
        let banner_text =
            Text::with_alignment("Wifi Info", banner_position, style, Alignment::Left);
        banner_text
            .draw(&mut lock_ctx.display)
            .expect("Should have drawn");

        let currently_connected_position = Point::new(60, 40);
        let currently_connected_text = if lock_ctx.wifi_connected {
            let ssid = lock_ctx.get_wifi_ssid().unwrap();
            let ssid_text = format!("SSID: {}", ssid);

            let ssid_position = Point::new(60, 60);
            let ssid_text =
                Text::with_alignment(ssid_text.as_str(), ssid_position, style, Alignment::Left);
            ssid_text
                .draw(&mut lock_ctx.display)
                .expect("Should have drawn");

            Text::with_alignment(
                "Connected",
                currently_connected_position,
                style,
                Alignment::Left,
            )
        } else {
            Text::with_alignment(
                "Disconnected",
                currently_connected_position,
                error_style,
                Alignment::Left,
            )
        };
        currently_connected_text
            .draw(&mut lock_ctx.display)
            .expect("Should have drawn");

        if self.error_string.is_some() {
            let error_position = Point::new(60, 80);
            let error_text = Text::with_alignment(
                self.error_string.as_ref().unwrap().as_str(),
                error_position,
                error_style,
                Alignment::Left,
            );
            error_text
                .draw(&mut lock_ctx.display)
                .expect("Should have drawn");
        }

        self.needs_redraw = false;
    }

    // fn get_id(&self) -> ScreenId {
    //     ScreenId::WifiInfo
    // }

    fn needs_redraw(&self) -> bool {
        self.needs_redraw
    }
}
