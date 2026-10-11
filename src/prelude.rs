use esp_idf_hal::gpio::{Gpio40, Gpio41, Output, PinDriver};
use esp_idf_hal::spi::{SpiDeviceDriver, SpiDriver};
use mipidsi::interface::SpiInterface;
use mipidsi::models::ST7789;
use mipidsi::Display;

pub type MySPI<'a> =
    SpiInterface<'a, SpiDeviceDriver<'a, SpiDriver<'a>>, PinDriver<'a, Gpio40, Output>>;

pub type MyDisplay<'a> = Display<
    SpiInterface<'a, SpiDeviceDriver<'a, SpiDriver<'a>>, PinDriver<'a, Gpio40, Output>>,
    ST7789,
    PinDriver<'a, Gpio41, Output>,
>;
