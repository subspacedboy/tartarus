use crate::config_verifier::ConfigVerifier;
use crate::lock_ctx::LockCtx;
use crate::prelude::MySPI;
use crate::screen_state::ScreenState;
use crate::verifier::{SignedMessageVerifier, VerifiedType};
use embedded_graphics::mono_font::ascii::FONT_10X20;
use embedded_graphics::mono_font::MonoTextStyle;
use embedded_graphics::pixelcolor::{Rgb565, RgbColor};
use embedded_graphics::prelude::Primitive;
use embedded_graphics::primitives::PrimitiveStyleBuilder;
use embedded_graphics::text::Text;
use embedded_graphics_core::geometry::{Point, Size};
use embedded_graphics_core::prelude::DrawTarget;
use embedded_graphics_core::primitives::Rectangle;
use embedded_graphics_core::Drawable;
use esp_idf_hal::gpio::{AnyOutputPin, GpioError};
use qrcode::{Color, QrCode};

pub struct QrCodeScreen {
    needs_redraw: bool,
    configuration_changed: bool,
}

impl QrCodeScreen {
    pub fn new() -> Self {
        Self {
            needs_redraw: true,
            configuration_changed: false,
        }
    }
}

impl ScreenState for QrCodeScreen {
    type SPI = MySPI<'static>;
    type PinE = GpioError;
    type DC = AnyOutputPin;
    type RST = AnyOutputPin;

    fn on_update(&mut self, lock_ctx: &mut LockCtx) -> Option<usize> {
        if let Some(update) = &lock_ctx.this_update {
            if let Some(qr_data) = &update.qr_data {
                //Maybe it's config data.
                if let Ok(config) = ConfigVerifier::read_configuration(qr_data.clone()) {
                    lock_ctx.save_configuration(&config);
                    self.configuration_changed = true;
                    self.needs_redraw = true;
                } else {
                    let verifier = SignedMessageVerifier::new();

                    let keys = lock_ctx.get_keyring();
                    if let Ok(VerifiedType::Contract(contract)) =
                        verifier.verify(qr_data.clone(), &keys, 0, 0)
                    {
                        lock_ctx.accept_contract(&contract);
                        lock_ctx.contract = Some(contract);
                        return Some(1);
                    }
                }
            }
        }
        None
    }

    fn process_command(
        &mut self,
        lock_ctx: &mut LockCtx,
        command: VerifiedType,
    ) -> Result<Option<usize>, String> {
        match command {
            VerifiedType::Contract(contract) => {
                lock_ctx.accept_contract(&contract);
                lock_ctx.contract = Some(contract);
                Ok(Some(1))
            }
            _ => Err("Command doesn't work on BootScreen".parse().unwrap()),
        }
    }

    fn draw_screen(&mut self, lock_ctx: &mut LockCtx) {
        lock_ctx.display.clear(Rgb565::BLACK).unwrap();

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
            Point::new(
                (offset_x - border_width) as i32,
                (offset_y - border_width) as i32,
            ),
            Size::new(
                qr_width * scale + border_width * 2,
                qr_width * scale + border_width * 2,
            ),
        )
        .into_styled(
            PrimitiveStyleBuilder::new()
                .fill_color(Rgb565::WHITE)
                .build(),
        );
        rect.draw(&mut lock_ctx.display).expect("Expected to draw");

        for y in 0..qr_width {
            for x in 0..qr_width {
                let color = if qr[(x as usize, y as usize)] == Color::Dark {
                    Rgb565::BLACK
                } else {
                    Rgb565::WHITE
                };
                let rect = Rectangle::new(
                    Point::new(
                        (offset_x + (x * scale)) as i32,
                        (offset_y + (y * scale)) as i32,
                    ),
                    Size::new(scale, scale),
                )
                .into_styled(PrimitiveStyleBuilder::new().fill_color(color).build());
                rect.draw(&mut lock_ctx.display).expect("Expected to draw");
            }
        }

        // This is theoretically a _very_ infrequent path taken. So not bothering to conditionally
        // render the whole QR code. Just throw it away and say the lock needs a reset.
        if self.configuration_changed {
            lock_ctx.display.clear(Rgb565::BLACK).unwrap();
            let style = MonoTextStyle::new(&FONT_10X20, Rgb565::GREEN);
            let draw_position = Point::new(25, 85);
            let text = Text::new("Config Changed\nReset Lock", draw_position, style);
            text.draw(&mut lock_ctx.display).expect("Should have drawn");
        }

        self.needs_redraw = false;
    }

    // fn get_id(&self) -> ScreenId {
    //     ScreenId::Boot
    // }

    fn needs_redraw(&self) -> bool {
        self.needs_redraw
    }
}
