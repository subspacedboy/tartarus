use base64::Engine;
use base64::engine::general_purpose;
use crate::screen_ids::ScreenId;
use crate::screen_state::ScreenState;
use embedded_graphics::pixelcolor::{Rgb565, RgbColor};
use embedded_graphics::prelude::Primitive;
use embedded_graphics::primitives::PrimitiveStyleBuilder;
use embedded_graphics_core::draw_target::DrawTarget;
use embedded_graphics_core::Drawable;
use embedded_graphics_core::geometry::{Point, Size};
use embedded_graphics_core::primitives::Rectangle;
use embedded_hal::digital::OutputPin;
use p256::pkcs8::{EncodePublicKey, LineEnding};
use qrcode::{Color, QrCode};
use st7789::ST7789;
use crate::lock_ctx::LockCtx;

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
    
    fn on_qr(&mut self, input: [u8; 254]) {
        todo!()
    }

    fn draw_screen(&mut self, lock_ctx : &mut LockCtx) {
        let whole_message = if let Some(key) = lock_ctx.public_key {
            let encoded_pub_key = general_purpose::STANDARD.encode(key.to_public_key_pem(LineEnding::CR).unwrap());
            const COORDINATOR: &str = "http://192.168.1.180:5002";
            format!("{}/announce?public={}", COORDINATOR, encoded_pub_key)
        } else {
            "http://192.168.1.180:5002/announce".parse().unwrap()
        };

        let qr = QrCode::new(whole_message).expect("Valid QR code");

        let qr_width = qr.width() as u32;

        log::info!("Generated code with version: {:?}", qr.version());
        log::info!("Has width: {:?}", qr_width);

        // Scale factor and positioning
        let scale = 2;
        let offset_y = (135 - qr_width * scale) / 2;
        // let offset_y = (240 - qr_width * scale) / 2;
        let offset_x = 70;

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
        todo!()
    }

    fn needs_redraw(&self) -> bool {
        self.needs_redraw
    }
}