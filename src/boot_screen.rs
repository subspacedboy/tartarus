use display_interface_spi::SPIInterface;
use embedded_graphics::pixelcolor::{Rgb565, RgbColor};
use embedded_hal::digital::OutputPin;
use crate::screen_ids::ScreenId;
use crate::screen_state::ScreenState;
use st7789::{Error, ST7789};
use embedded_graphics::geometry::{Point, Size};
use embedded_graphics::primitives::{PrimitiveStyleBuilder, Rectangle};
use embedded_graphics_core::draw_target::DrawTarget;

use embedded_graphics::prelude::Primitive;
use embedded_graphics::Drawable;
use esp_idf_hal::gpio::{Gpio40, Gpio41, Gpio45, Output, PinDriver};
use esp_idf_hal::spi::{SpiDeviceDriver, SpiDriver};

//
//
pub struct BootScreen {}

impl ScreenState for BootScreen {
    fn on_qr(&mut self, input: [u8; 254]) {
        todo!()
    }

    fn draw_screen<SPI, DC, RST, PinE>(&self, display: &mut ST7789<SPI, DC, RST>)
    where
        SPI: display_interface::WriteOnlyDataCommand,
        DC: OutputPin<Error = PinE>,
        RST: OutputPin<Error = PinE>,
        ST7789<SPI, DC, RST>: DrawTarget<Color = Rgb565, Error = Error<PinE>>,
        PinE: std::fmt::Debug
    {
        // Example drawing logic (clear screen)
        // let _test: &mut dyn DrawTarget<Color = Rgb565, Error=()> = display;
        // display.clear(Rgb565::BLUE).map_err(|_| "ST7789 clear failed").unwrap();
        display.clear(Rgb565::BLUE).expect("pos");

        println!("BootScreen drawn!");
    }

    fn get_id(&self) -> ScreenId {
        todo!()
    }
}

//
// impl<SPI, DC, RST> ScreenState for BootScreen<SPI, DC, RST>
// where
//     SPI: Spi + write::Default<u8> + display_interface::WriteOnlyDataCommand,
//     DC: OutputPin,
//     RST: OutputPin,
// {
//     // fn on_d0(&mut self) -> Option<Box<dyn ScreenState<SPI, DC, RST>>> {
//     //     todo!()
//     // }
//     //
//     // fn on_d1(&mut self) -> Option<Box<dyn ScreenState<SPI, DC, RST>>> {
//     //     todo!()
//     // }
//     //
//     // fn on_d2(&mut self, input: char) -> Option<Box<dyn ScreenState<SPI, DC, RST>>> {
//     //     todo!()
//     // }
//
//     fn on_qr(&mut self, input: [u8; 254]) {
//         todo!()
//     }
//
//     fn draw_screen<SPI, DC, RST>(&self, display: &mut ST7789<SPI, DC, RST>)
//     where
//         SPI: embedded_hal::blocking::spi::Write<u8>,
//         DC: embedded_hal::digital::v2::OutputPin,
//         RST: embedded_hal::digital::v2::OutputPin
//     {
//         todo!()
//     }
//
//
//     // fn on_entry(&mut self, from_screen: &Box<dyn ScreenState<SPI, DC, RST>>) {
//     //     todo!()
//     // }
//
//     fn get_id(&self) -> ScreenId {
//         todo!()
//     }
//     // Implement the required methods here
// }