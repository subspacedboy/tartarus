use crate::screen_ids::ScreenId;
use crate::screen_state::ScreenState;
use embedded_graphics::pixelcolor::{Rgb565, RgbColor};
use embedded_graphics_core::draw_target::DrawTarget;
use embedded_hal::digital::OutputPin;
use st7789::ST7789;

pub struct BootScreen<SPI, DC, RST, PinE> {
    _spi: core::marker::PhantomData<SPI>,
    _dc: core::marker::PhantomData<DC>,
    _rst: core::marker::PhantomData<RST>,
    _pin: core::marker::PhantomData<PinE>,
}

impl<SPI, DC, RST, PinE> BootScreen<SPI, DC, RST, PinE> {
    pub fn new() -> Self {
        Self {
            _spi: core::marker::PhantomData,
            _dc: core::marker::PhantomData,
            _rst: core::marker::PhantomData,
            _pin: core::marker::PhantomData,
        }
    }
}

impl<SPI, DC, RST, PinE> ScreenState for BootScreen<SPI, DC, RST, PinE>
where
    SPI: display_interface::WriteOnlyDataCommand,
    DC: OutputPin<Error = PinE>,
    RST: OutputPin<Error = PinE>,
    PinE: std::fmt::Debug ,{
    type SPI = SPI;
    type PinE = PinE;
    type DC = DC;
    type RST = RST;
    
    fn on_qr(&mut self, input: [u8; 254]) {
        todo!()
    }

    fn draw_screen(&self, display: &mut ST7789<SPI, DC, RST>) {
        display.clear(Rgb565::BLUE).unwrap();
        println!("BootScreen drawn!");
    }

    fn get_id(&self) -> ScreenId {
        todo!()
    }
}