use crate::lock_ctx::LockCtx;
use crate::verifier::VerifiedType;
use embedded_hal::digital::OutputPin;

pub trait ScreenState {
    type SPI: display_interface::WriteOnlyDataCommand;
    type PinE: std::fmt::Debug;
    type DC: OutputPin<Error = Self::PinE>;
    type RST: OutputPin<Error = Self::PinE>;

    fn on_update(&mut self, lock_ctx: &mut LockCtx) -> Option<usize>;

    fn process_command(
        &mut self,
        lock_ctx: &mut LockCtx,
        command: VerifiedType,
    ) -> Result<Option<usize>, String>;

    fn draw_screen(&mut self, lock_ctx: &mut LockCtx);

    // Clippy warns that I should probably just be using '&dyn ScreenState' as the type
    // here but then I can't seem to pass it around and use the indirection that makes
    // the most sense. Revisit it somewhere down the line.
    // fn on_entry(&mut self, from_screen: &Box<dyn ScreenState<SPI, DC, RST>>);

    fn needs_redraw(&self) -> bool;
}
