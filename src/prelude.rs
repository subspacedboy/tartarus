use crate::overlay::Overlay;
use esp_idf_hal::gpio::{AnyOutputPin, Gpio40, Gpio41, GpioError, Output, PinDriver};
use esp_idf_hal::spi::{SpiDeviceDriver, SpiDriver};
use mipidsi::interface::SpiInterface;
use mipidsi::models::ST7789;
use mipidsi::Display;

pub type MySPI<'a> =
    SpiInterface<'a, SpiDeviceDriver<'a, SpiDriver<'a>>, PinDriver<'a, Gpio40, Output>>;

pub type DynOverlay<'a> =
    dyn Overlay<SPI = MySPI<'a>, DC = AnyOutputPin, PinE = GpioError, RST = AnyOutputPin>;

pub type MyDisplay<'a> = Display<
    SpiInterface<'a, SpiDeviceDriver<'a, SpiDriver<'a>>, PinDriver<'a, Gpio40, Output>>,
    ST7789,
    PinDriver<'a, Gpio41, Output>,
>;
