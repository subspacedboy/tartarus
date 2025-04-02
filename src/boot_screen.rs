// use embedded_graphics::pixelcolor::{Rgb565, RgbColor};
// use embedded_graphics::prelude::DrawTarget;
// // use embedded_hal::digital::OutputPin;
// use embedded_hal_compat::eh0_2::blocking::spi::write;
// use embedded_hal_compat::eh0_2::digital::OutputPin;
// // use st7789::ST7789;
// use crate::screen_ids::ScreenId;
// use crate::screen_state::ScreenState;
// use esp_idf_hal::spi::{Spi, SpiConfig, SpiDeviceDriver, SpiDriver};
// //
// //
// pub struct BootScreen {}
//
// impl ScreenState for BootScreen {
//     fn on_qr(&mut self, input: [u8; 254]) {
//         todo!()
//     }
//
//     fn draw_screen<SPI, DC, RST>(&self, display: &mut ST7789<SPI, DC, RST>)
//     where
//         SPI: embedded_hal_compat::eh0_2::blocking::spi::write::Default<u8> + display_interface::WriteOnlyDataCommand,
//         DC: OutputPin,
//         RST: OutputPin,
//     {
//         // Example drawing logic (clear screen)
//         display.clear(Rgb565::BLACK).unwrap();
//         println!("BootScreen drawn!");
//     }
//
//     fn get_id(&self) -> ScreenId {
//         todo!()
//     }
// }
//
// //
// // impl<SPI, DC, RST> ScreenState for BootScreen<SPI, DC, RST>
// // where
// //     SPI: Spi + write::Default<u8> + display_interface::WriteOnlyDataCommand,
// //     DC: OutputPin,
// //     RST: OutputPin,
// // {
// //     // fn on_d0(&mut self) -> Option<Box<dyn ScreenState<SPI, DC, RST>>> {
// //     //     todo!()
// //     // }
// //     //
// //     // fn on_d1(&mut self) -> Option<Box<dyn ScreenState<SPI, DC, RST>>> {
// //     //     todo!()
// //     // }
// //     //
// //     // fn on_d2(&mut self, input: char) -> Option<Box<dyn ScreenState<SPI, DC, RST>>> {
// //     //     todo!()
// //     // }
// //
// //     fn on_qr(&mut self, input: [u8; 254]) {
// //         todo!()
// //     }
// //
// //     fn draw_screen<SPI, DC, RST>(&self, display: &mut ST7789<SPI, DC, RST>)
// //     where
// //         SPI: embedded_hal::blocking::spi::Write<u8>,
// //         DC: embedded_hal::digital::v2::OutputPin,
// //         RST: embedded_hal::digital::v2::OutputPin
// //     {
// //         todo!()
// //     }
// //
// //
// //     // fn on_entry(&mut self, from_screen: &Box<dyn ScreenState<SPI, DC, RST>>) {
// //     //     todo!()
// //     // }
// //
// //     fn get_id(&self) -> ScreenId {
// //         todo!()
// //     }
// //     // Implement the required methods here
// // }