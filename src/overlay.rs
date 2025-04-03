use crate::lock_ctx::LockCtx;
use esp_idf_hal::gpio::OutputPin;
use mipidsi::interface::Interface;

pub trait Overlay {
    type SPI: Interface;
    type PinE: std::fmt::Debug;
    type DC: OutputPin;
    type RST: OutputPin;

    fn draw_screen(&mut self, lock_ctx: &mut LockCtx);
}
