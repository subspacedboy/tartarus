use crate::lock_ctx::LockCtx;
use crate::prelude::prelude::{DynScreen, MySPI};
use crate::screen_ids::ScreenId;
use crate::screen_state::ScreenState;
use embedded_graphics::mono_font::ascii::FONT_10X20;
use embedded_graphics::mono_font::MonoTextStyle;
use embedded_graphics::text::{Alignment, Text};
use embedded_graphics_core::geometry::Point;
use embedded_graphics_core::pixelcolor::Rgb565;
use embedded_graphics_core::prelude::{DrawTarget, RgbColor};
use embedded_graphics_core::Drawable;
use embedded_hal::digital::OutputPin;
use esp_idf_hal::gpio::{GpioError, Output, PinDriver};
use crate::verifier::VerifiedType;

pub struct UnderContractScreen<SPI, DC, RST, PinE> {
    _spi: core::marker::PhantomData<SPI>,
    _dc: core::marker::PhantomData<DC>,
    _rst: core::marker::PhantomData<RST>,
    _pin: core::marker::PhantomData<PinE>,
    needs_redraw: bool,
    text: String
}

impl<SPI, DC, RST, PinE> UnderContractScreen<SPI, DC, RST, PinE> {
    pub fn new() -> Self {
        Self {
            _spi: core::marker::PhantomData,
            _dc: core::marker::PhantomData,
            _rst: core::marker::PhantomData,
            _pin: core::marker::PhantomData,
            needs_redraw: true,
            text: "Locked :-)".to_string()
        }
    }
}

impl<SPI, DC, RST, PinE> ScreenState for UnderContractScreen<SPI, DC, RST, PinE>
where
    SPI: display_interface::WriteOnlyDataCommand,
    DC: OutputPin<Error = PinE>,
    RST: OutputPin<Error = PinE>,
    PinE: std::fmt::Debug ,{
    type SPI = SPI;
    type PinE = PinE;
    type DC = DC;
    type RST = RST;

    fn on_update(&mut self, lock_ctx: &mut LockCtx) -> Option<Box<DynScreen<'static>>> {
        let contract_option = lock_ctx.contract.as_ref();
        let contract = contract_option.expect("contract");

        if let Some(update) = &lock_ctx.this_update {
            if update.d1_pressed && contract.temporary_unlock_allowed {
                if lock_ctx.is_locked() {
                    lock_ctx.unlock();
                    self.text = "Unlocked".to_string();
                    self.needs_redraw = true;
                } else {
                    lock_ctx.lock();
                    self.text = "Locked :-)".to_string();
                    self.needs_redraw = true;
                }
            }

            // if update.d1_pressed && !lock_ctx.is_locked() {
            //     // Relock
            //     if contract.temporary_unlock_allowed {
            //         lock_ctx.lock();
            //         self.text = "Locked :-)".to_string();
            //         self.needs_redraw = true;
            //     }
            // }
        }
        None
    }

    fn process_command(&mut self, lock_ctx: &mut LockCtx, command: VerifiedType) -> Result<Option<Box<DynScreen<'static>>>, String> {
        log::info!("process_command: {:?}", command);
        match command {
            VerifiedType::UnlockCommand(_) => {
                lock_ctx.unlock();
                self.text = "Unlocked".to_string();
                self.needs_redraw = true;
                Ok(None)
            }
            VerifiedType::LockCommand(_) => {
                lock_ctx.lock();
                self.text = "Locked :-)".to_string();
                self.needs_redraw = true;
                Ok(None)
            }
            _ => {
                Err("Command doesn't work on UnderContractScreen".parse().unwrap())
            }
        }
    }


    fn draw_screen(&mut self, lock_ctx : &mut LockCtx) {
        lock_ctx.display.clear(Rgb565::BLACK).expect("Screen should have cleared");

        let style = MonoTextStyle::new(&FONT_10X20, Rgb565::GREEN);
        let draw_position = Point::new(120, 67);
        let text = Text::with_alignment(self.text.as_str(), draw_position, style, Alignment::Center);
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