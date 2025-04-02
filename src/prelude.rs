use crate::screen_state::ScreenState;

pub mod prelude {
    use display_interface_spi::SPIInterface;
    use esp_idf_hal::gpio::{Gpio40, Gpio41, Gpio45, GpioError, Output, PinDriver};
    use esp_idf_hal::spi::{SpiDeviceDriver, SpiDriver};
    use st7789::ST7789;
    use crate::overlay::Overlay;
    use crate::screen_state::ScreenState;

    pub type MySPI<'a> = SPIInterface<SpiDeviceDriver<'a, SpiDriver<'a>>, PinDriver<'a, Gpio40, Output>>;
    pub type DynScreen<'a> = dyn ScreenState<
        SPI= MySPI<'a>,
        DC= PinDriver<'a, Gpio41, Output>,
        PinE=GpioError,
        RST=PinDriver<'a, Gpio45, Output>>;

    pub type DynOverlay<'a> = dyn Overlay<
        SPI= MySPI<'a>,
        DC= PinDriver<'a, Gpio41, Output>,
        PinE=GpioError,
        RST=PinDriver<'a, Gpio45, Output>>;

    pub type MyDisplay<'a> = ST7789<MySPI<'a>, PinDriver<'a, Gpio41, Output>, PinDriver<'a, Gpio45, Output>>;
}
