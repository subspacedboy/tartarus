use crate::lock_ctx::LockCtx;
use crate::prelude::MySPI;
use crate::screen_state::ScreenState;
use crate::verifier::VerifiedType;
use embedded_graphics::mono_font::ascii::FONT_10X20;
use embedded_graphics::mono_font::MonoTextStyle;
use embedded_graphics::text::{Alignment, Text};
use embedded_graphics_core::geometry::Point;
use embedded_graphics_core::pixelcolor::Rgb565;
use embedded_graphics_core::prelude::{DrawTarget, RgbColor};
use embedded_graphics_core::Drawable;
use esp_idf_hal::gpio::{AnyOutputPin, GpioError};

pub struct FirmwareScreen {
    needs_redraw: bool,
}

impl FirmwareScreen {
    pub fn new() -> Self {
        Self { needs_redraw: true }
    }
}

impl ScreenState for FirmwareScreen {
    type SPI = MySPI<'static>;
    type PinE = GpioError;
    type RST = AnyOutputPin;
    type DC = AnyOutputPin;

    fn on_update(&mut self, _lock_ctx: &mut LockCtx) -> Option<usize> {
        None
    }

    fn process_command(
        &mut self,
        _lock_ctx: &mut LockCtx,
        _command: VerifiedType,
    ) -> Result<Option<usize>, String> {
        Ok(None)
    }

    fn draw_screen(&mut self, lock_ctx: &mut LockCtx) {
        lock_ctx
            .display
            .clear(Rgb565::BLACK)
            .expect("Screen should have cleared");

        let style = MonoTextStyle::new(&FONT_10X20, Rgb565::GREEN);

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


        if lock_ctx.firmware_manager.is_update_available() {
            let upgrade_available_position = Point::new(60, 60);

            let next_version = lock_ctx.firmware_manager.next_firmware_version();
            let t = format!("Upgrade: {}", next_version);

            let version_text = Text::with_alignment(
                t.as_str(),
                upgrade_available_position,
                style,
                Alignment::Left,
            );

            version_text.draw(&mut lock_ctx.display).expect("Should have drawn");
        }

        self.needs_redraw = false;
    }

    fn needs_redraw(&self) -> bool {
        self.needs_redraw
    }
}
