use crate::lock_ctx::LockCtx;
use crate::lock_state_screen::LockstateScreen;
use crate::prelude::prelude::{DynScreen, MySPI};
use crate::qr_screen::QrCodeScreen;
use crate::screen_ids::ScreenId;
use crate::screen_state::ScreenState;
use crate::verifier::VerifiedType;
use crate::wifi_util::{connect_wifi, parse_wifi_qr};
use embedded_graphics::mono_font::ascii::FONT_10X20;
use embedded_graphics::mono_font::MonoTextStyle;
use embedded_graphics::text::{Alignment, Text};
use embedded_graphics_core::geometry::Point;
use embedded_graphics_core::pixelcolor::Rgb565;
use embedded_graphics_core::prelude::{DrawTarget, RgbColor};
use embedded_graphics_core::Drawable;
use embedded_hal::digital::OutputPin;
use esp_idf_hal::gpio::{Gpio41, Gpio45, GpioError, Output, PinDriver};

pub struct FirmwareScreen {
    needs_redraw: bool,
    error_string: Option<String>,
    text: String,
}

impl FirmwareScreen {
    pub fn new() -> Self {
        Self {
            needs_redraw: true,
            error_string: None,
            text: "Wifi Info".to_string(),
        }
    }
}

impl ScreenState for FirmwareScreen {
    type SPI = MySPI<'static>;
    type PinE = GpioError;
    type DC = PinDriver<'static, Gpio41, Output>;
    type RST = PinDriver<'static, Gpio45, Output>;

    fn on_update(&mut self, lock_ctx: &mut LockCtx) -> Option<usize> {
        None
    }

    fn process_command(
        &mut self,
        lock_ctx: &mut LockCtx,
        command: VerifiedType,
    ) -> Result<Option<usize>, String> {
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
            Text::with_alignment("Firmware Info", banner_position, style, Alignment::Left);
        banner_text
            .draw(&mut lock_ctx.display)
            .expect("Should have drawn");

        let current_version = lock_ctx
            .firmware_manager
            .get_running_version()
            .expect("Get current firmware version");

        let version_position = Point::new(60, 40);
        let version_text = Text::with_alignment(
            current_version.as_str(),
            version_position,
            style,
            Alignment::Left,
        );
        version_text
            .draw(&mut lock_ctx.display)
            .expect("Should have drawn");

        self.needs_redraw = false;
    }

    fn get_id(&self) -> ScreenId {
        ScreenId::WifiInfo
    }

    fn needs_redraw(&self) -> bool {
        self.needs_redraw
    }
}
