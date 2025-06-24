use data_encoding::BASE64;
use crate::lock_ctx::LockCtx;
use crate::prelude::MySPI;
use crate::screen_ids::ScreenId;
use crate::screen_state::ScreenState;
use crate::verifier::{SignedMessageVerifier, VerificationError, VerifiedType};
use embedded_graphics::mono_font::ascii::FONT_10X20;
use embedded_graphics::mono_font::MonoTextStyle;
use embedded_graphics::text::{Alignment, Text};
use embedded_graphics_core::geometry::Point;
use embedded_graphics_core::pixelcolor::Rgb565;
use embedded_graphics_core::prelude::{DrawTarget, RgbColor};
use embedded_graphics_core::Drawable;
use esp_idf_hal::gpio::{AnyOutputPin, GpioError};

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
    type RST = AnyOutputPin;
    type DC = AnyOutputPin;

    fn on_update(&mut self, lock_ctx: &mut LockCtx) -> Option<usize> {
        let update = lock_ctx.this_update.as_ref().unwrap();

        // Clone what you need from `update` so you can drop the borrow
        let d1_pressed = update.d1_pressed;
        let qr_data = update.qr_data.clone(); // assumes Option<String> or similar

        // Clone what you need from `contract`
        let (temporary_unlock_allowed, command_counter, serial_number) = match lock_ctx.contract.as_ref() {
            Some(c) => (c.temporary_unlock_allowed, c.command_counter, c.serial_number.clone()),
            None => {
                // No contract, just toggle local lock
                if d1_pressed {
                    if lock_ctx.is_locked() {
                        lock_ctx.local_unlock();
                    } else {
                        lock_ctx.local_lock();
                    }
                    self.needs_redraw = true;
                }
                return None;
            }
        };

        if d1_pressed && temporary_unlock_allowed {
            if lock_ctx.is_locked() {
                lock_ctx.local_unlock();
            } else {
                lock_ctx.local_lock();
            }
            self.needs_redraw = true;
        }

        if let Some(qr_data) = qr_data {
            let verifier = SignedMessageVerifier::new();
            let keys = lock_ctx.get_keyring();

            let decoded = BASE64.decode(&*qr_data).expect("Valid base64");
            match verifier.verify(decoded, &keys, command_counter, serial_number) {
                Ok(command) => {
                    return match command {
                        VerifiedType::UnlockCommand(c) => {
                            lock_ctx.unlock();
                            lock_ctx.increment_command_counter(c.counter);
                            Some(1)
                        }
                        VerifiedType::LockCommand(c) => {
                            lock_ctx.lock();
                            lock_ctx.increment_command_counter(c.counter);
                            Some(1)
                        }
                        VerifiedType::ReleaseCommand(_) |
                        VerifiedType::AbortCommand(_) => {
                            lock_ctx.end_contract();
                            Some(0)
                        }
                        VerifiedType::ResetCommand(reset) => {
                            lock_ctx.full_reset(&reset);
                            Some(0)
                        }
                        _ => None,
                    };
                }
                Err(e) => {
                    log::error!("Failed to verify QR code {:?}", e);
                }
            }
            self.needs_redraw = true;
        }

        None
    }


    fn process_command(
        &mut self,
        lock_ctx: &mut LockCtx,
        command: VerifiedType,
    ) -> Result<Option<ScreenId>, String> {
        log::info!("process_command: {:?}", command);
        self.needs_redraw = true;
        match command {
            VerifiedType::Contract(contract) => {
                if lock_ctx.contract.is_some() {
                    return Err("Lock already under contract".to_string());
                }

                lock_ctx.accept_contract(&contract);
                lock_ctx.contract = Some(contract);
                Ok(Some(ScreenId::LockState))
            }
            VerifiedType::UnlockCommand(_) => {
                lock_ctx.unlock();
                Ok(Some(ScreenId::LockState))
            }
            VerifiedType::LockCommand(_) => {
                lock_ctx.lock();
                Ok(Some(ScreenId::LockState))
            }
            VerifiedType::ReleaseCommand(_) => {
                lock_ctx.end_contract();
                Ok(Some(ScreenId::QrCode))
            }
            VerifiedType::AbortCommand(_) => {
                lock_ctx.end_contract();
                Ok(Some(ScreenId::QrCode))
            }
            VerifiedType::ResetCommand(reset) => {
                lock_ctx.full_reset(&reset);
                Ok(Some(ScreenId::QrCode))
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
        if let Some(internal_contract) = lock_ctx.contract.as_ref() {
            let contract_position = Point::new(120, 47);
            let contract_str = format!("Contract: {}", internal_contract.serial_number);
            let text = Text::with_alignment(
                contract_str.as_str(),
                contract_position,
                style,
                Alignment::Center,
            );
            text.draw(&mut lock_ctx.display).expect("Should have drawn");
        }

        let draw_position = Point::new(120, 67);
        let text =
            Text::with_alignment(self.text.as_str(), draw_position, style, Alignment::Center);
        text.draw(&mut lock_ctx.display).expect("Should have drawn");

        self.needs_redraw = false;
    }

    fn needs_redraw(&self) -> bool {
        self.needs_redraw
    }
}
