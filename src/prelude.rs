use esp_idf_hal::gpio::{Output, PinDriver};
use esp_idf_hal::spi::{SpiDeviceDriver, SpiDriver};
use mipidsi::interface::SpiInterface;
use mipidsi::models::ST7789;
use mipidsi::Display;

pub type MyDisplay<'a> = Display<
    SpiInterface<'a, SpiDeviceDriver<'a, SpiDriver<'a>>, PinDriver<'a, Output>>,
    ST7789,
    PinDriver<'a, Output>,
>;
