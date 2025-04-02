use display_interface_spi::SPIInterface;
use embedded_graphics_core::draw_target::DrawTarget;
use embedded_graphics_core::pixelcolor::Rgb565;
use embedded_hal::digital::OutputPin;
use esp_idf_hal::gpio::{Gpio40, Gpio41, Gpio45, GpioError, Output, PinDriver};
use esp_idf_hal::spi::{SpiDeviceDriver, SpiDriver};
use st7789::{Error, ST7789};
use crate::lock_ctx::LockCtx;
use crate::screen_ids::ScreenId;


pub trait ScreenState {
    type SPI: display_interface::WriteOnlyDataCommand;
    type PinE: std::fmt::Debug;
    type DC: OutputPin<Error = Self::PinE>;
    type RST: OutputPin<Error = Self::PinE>;

    // fn on_d0(&mut self) -> Option<Box<dyn ScreenState<SPI, DC, RST>>>;
    // fn on_d1(&mut self) -> Option<Box<dyn ScreenState<SPI, DC, RST>>>;
    // fn on_d2(&mut self, input: char) -> Option<Box<dyn ScreenState<SPI, DC, RST>>>;
    fn on_qr(&mut self, input: [u8; 254]);

    fn draw_screen(&mut self, lock_ctx : &LockCtx, display: &mut ST7789<Self::SPI, Self::DC, Self::RST>);

    // Clippy warns that I should probably just be using '&dyn ScreenState' as the type
    // here but then I can't seem to pass it around and use the indirection that makes
    // the most sense. Revisit it somewhere down the line.
    #[allow(clippy::borrowed_box)]
    // fn on_entry(&mut self, from_screen: &Box<dyn ScreenState<SPI, DC, RST>>);
    fn get_id(&self) -> ScreenId;

    fn needs_redraw(&self) -> bool;
}
