use crate::lock_ctx::LockCtx;
use embedded_hal::digital::OutputPin;

pub trait Overlay {
    type SPI: display_interface::WriteOnlyDataCommand;
    type PinE: std::fmt::Debug;
    type DC: OutputPin<Error = Self::PinE>;
    type RST: OutputPin<Error = Self::PinE>;

    fn draw_screen(&mut self, lock_ctx: &mut LockCtx);
}
