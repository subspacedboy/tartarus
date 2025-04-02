use crate::lock_ctx::LockCtx;
use crate::prelude::prelude::{DynScreen, MySPI};
use crate::qr_screen::QrCodeScreen;
use crate::screen_ids::ScreenId;
use crate::screen_state::ScreenState;
use crate::verifier::VerifiedType;
use embedded_graphics::mono_font::ascii::FONT_10X20;
use embedded_graphics::mono_font::MonoTextStyle;
use embedded_graphics::text::{Alignment, Text};
use embedded_graphics_core::geometry::Point;
use embedded_graphics_core::pixelcolor::Rgb565;
use embedded_graphics_core::prelude::{DrawTarget, RgbColor};
use embedded_graphics_core::Drawable;
use embedded_hal::digital::OutputPin;
use esp_idf_hal::gpio::{Gpio41, Gpio45, GpioError, Output, PinDriver};

pub struct LockstateScreen {
    needs_redraw: bool,
    text: String,
}

impl LockstateScreen {
    pub fn new() -> Self {
        Self {
            needs_redraw: true,
            text: "Locked :-)".to_string(),
        }
    }
}

impl ScreenState for LockstateScreen {
    type SPI = MySPI<'static>;
    type PinE = GpioError;
    type DC = PinDriver<'static, Gpio41, Output>;
    type RST = PinDriver<'static, Gpio45, Output>;

    fn on_update(&mut self, lock_ctx: &mut LockCtx) -> Option<usize> {
        let contract_option = lock_ctx.contract.as_ref();

        let update = lock_ctx.this_update.as_ref().unwrap();
        if let Some(contract) = contract_option {
            // We have a contract
            if update.d1_pressed && contract.temporary_unlock_allowed {
                if lock_ctx.is_locked() {
                    lock_ctx.local_unlock();
                    self.needs_redraw = true;
                } else {
                    lock_ctx.local_lock();
                    self.needs_redraw = true;
                }
            }
        } else {
            // We do not have a contract and just cycling the lock.
            if update.d1_pressed {
                if lock_ctx.is_locked() {
                    lock_ctx.local_unlock();
                    self.needs_redraw = true;
                } else {
                    lock_ctx.local_lock();
                    self.needs_redraw = true;
                }
            }
        }

        None
    }

    fn process_command(
        &mut self,
        lock_ctx: &mut LockCtx,
        command: VerifiedType,
    ) -> Result<Option<usize>, String> {
        log::info!("process_command: {:?}", command);
        self.needs_redraw = true;
        match command {
            VerifiedType::Contract(contract) => {
                lock_ctx.accept_contract(&contract);
                lock_ctx.contract = Some(contract);
                Ok(Some(1))
            }
            VerifiedType::UnlockCommand(_) => {
                lock_ctx.unlock();
                Ok(Some(1))
            }
            VerifiedType::LockCommand(_) => {
                lock_ctx.lock();
                Ok(Some(1))
            }
            VerifiedType::ReleaseCommand(_) => {
                lock_ctx.end_contract();
                Ok(Some(0))
            }
            VerifiedType::AbortCommand(_) => {
                lock_ctx.end_contract();
                Ok(Some(0))
            }
        }
    }

    fn draw_screen(&mut self, lock_ctx: &mut LockCtx) {
        lock_ctx
            .display
            .clear(Rgb565::BLACK)
            .expect("Screen should have cleared");

        if lock_ctx.is_locked() {
            self.text = "Locked :-)".to_string();
        } else {
            self.text = "Unlocked".to_string();
        }

        let style = MonoTextStyle::new(&FONT_10X20, Rgb565::GREEN);
        let draw_position = Point::new(120, 67);
        let text =
            Text::with_alignment(self.text.as_str(), draw_position, style, Alignment::Center);
        text.draw(&mut lock_ctx.display).expect("Should have drawn");

        self.needs_redraw = false;
    }

    fn get_id(&self) -> ScreenId {
        ScreenId::UnderContract
    }

    fn needs_redraw(&self) -> bool {
        self.needs_redraw
    }
}
