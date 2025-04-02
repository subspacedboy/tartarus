use crate::display_contract_screen::DisplayContractScreen;
use crate::lock_ctx::LockCtx;
use crate::prelude::prelude::{DynScreen, MySPI};
use crate::screen_ids::ScreenId;
use crate::screen_state::ScreenState;
use crate::verifier::{SignedMessageVerifier, VerifiedType};
use embedded_graphics::pixelcolor::{Rgb565, RgbColor};
use embedded_graphics::prelude::Primitive;
use embedded_graphics::primitives::PrimitiveStyleBuilder;
use embedded_graphics_core::geometry::{Point, Size};
use embedded_graphics_core::primitives::Rectangle;
use embedded_graphics_core::Drawable;
use embedded_hal::digital::OutputPin;
use esp_idf_hal::gpio::{GpioError, Output, PinDriver};
use qrcode::{Color, QrCode};
use crate::under_contract_screen::UnderContractScreen;

pub struct BootScreen<SPI, DC, RST, PinE> {
    _spi: core::marker::PhantomData<SPI>,
    _dc: core::marker::PhantomData<DC>,
    _rst: core::marker::PhantomData<RST>,
    _pin: core::marker::PhantomData<PinE>,
    needs_redraw: bool
}

impl<SPI, DC, RST, PinE> BootScreen<SPI, DC, RST, PinE> {
    pub fn new() -> Self {
        Self {
            _spi: core::marker::PhantomData,
            _dc: core::marker::PhantomData,
            _rst: core::marker::PhantomData,
            _pin: core::marker::PhantomData,
            needs_redraw: true
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

    fn on_update(&mut self, lock_ctx: &mut LockCtx) -> Option<Box<DynScreen<'static>>> {
        if let Some(update) = &lock_ctx.this_update {
            if let Some(qr_data) = &update.qr_data {
                let verifier = SignedMessageVerifier::new();

                if let Ok(verified_type) = verifier.verify(qr_data.clone(), None) {
                    match verified_type {
                        VerifiedType::Contract(contract) => {
                            lock_ctx.accept_contract(&contract);

                            lock_ctx.contract = Some(contract);
                            let under_contract = Box::new(
                                UnderContractScreen::<
                                    MySPI<'static>,
                                    PinDriver<'static, _, Output>,
                                    PinDriver<'static, _, Output>,
                                    GpioError
                                >::new());
                            return Some(under_contract);
                        }
                        _ => {}
                    }
                }
            }
        }

        None
    }

    fn process_command(&mut self, lock_ctx: &mut LockCtx, command: VerifiedType)  -> Result<Option<Box<DynScreen<'static>>>, String>{
        match command {
            VerifiedType::Contract(contract) => {
                lock_ctx.accept_contract(&contract);

                lock_ctx.contract = Some(contract);
                let under_contract = Box::new(
                    UnderContractScreen::<
                        MySPI<'static>,
                        PinDriver<'static, _, Output>,
                        PinDriver<'static, _, Output>,
                        GpioError
                    >::new());
                Ok(Some(under_contract))
            }
            _ => {
                Err("Command doesn't work on BootScreen".parse().unwrap())
            }
        }
    }


    fn draw_screen(&mut self, lock_ctx : &mut LockCtx) {
        // let whole_message = if let Some(key) = lock_ctx.public_key {
        //     let encoded_pub_key = general_purpose::STANDARD.encode(key.to_public_key_pem(LineEnding::CR).unwrap());
        //     const COORDINATOR: &str = "http://192.168.1.180:5002";
        //     format!("{}/announce?public={}", COORDINATOR, encoded_pub_key)
        // } else {
        //     "http://192.168.1.180:5002/announce".parse().unwrap()
        // };
        let whole_message = lock_ctx.get_lock_url().unwrap();
        let qr = QrCode::new(whole_message).expect("Valid QR code");

        let qr_width = qr.width() as u32;

        log::info!("Generated code with version: {:?}", qr.version());
        log::info!("Has width: {:?}", qr_width);

        // Scale factor and positioning
        let scale = 2;
        // let offset_y = (240 - qr_width * scale) / 2;
        let offset_y = 5_u32;
        // let offset_y = (240 - qr_width * scale) / 2;
        let offset_x = 70_u32;

        let border_width = 5;

        let rect = Rectangle::new(
            Point::new((offset_x - border_width) as i32, (offset_y - border_width) as i32),
            Size::new(qr_width * scale + border_width * 2, qr_width * scale + border_width * 2),
        )
            .into_styled(PrimitiveStyleBuilder::new().fill_color(Rgb565::WHITE).build());
        rect.draw(&mut lock_ctx.display).expect("Expected to draw");

        for y in 0..qr_width {
            for x in 0..qr_width {
                let color = if qr[(x as usize, y as usize)] == Color::Dark {
                    Rgb565::BLACK
                } else {
                    Rgb565::WHITE
                };
                let rect = Rectangle::new(
                    Point::new((offset_x + (x * scale)) as i32, (offset_y + (y * scale)) as i32),
                    Size::new(scale, scale),
                )
                    .into_styled(PrimitiveStyleBuilder::new().fill_color(color).build());
                rect.draw(&mut lock_ctx.display).expect("Expected to draw");
            }
        }

        self.needs_redraw = false;
    }

    fn get_id(&self) -> ScreenId {
        ScreenId::Boot
    }

    fn needs_redraw(&self) -> bool {
        self.needs_redraw
    }
}