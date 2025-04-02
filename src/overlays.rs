use embedded_graphics::prelude::Angle;
use base64::engine::general_purpose;
use embedded_graphics::mono_font::ascii::FONT_10X20;
use embedded_graphics::mono_font::MonoTextStyle;
use embedded_graphics::prelude::Primitive;
use embedded_graphics::primitives::PrimitiveStyleBuilder;
use embedded_graphics::text::{Alignment, Text};
use embedded_graphics_core::Drawable;
use embedded_graphics_core::geometry::{Point, Size};
use embedded_graphics_core::pixelcolor::Rgb565;
use embedded_graphics_core::prelude::{Dimensions, RgbColor};
use embedded_graphics_core::primitives::Rectangle;
use embedded_graphics::transform::Transform;
use embedded_hal::digital::OutputPin;
use p256::pkcs8::LineEnding;
use qrcode::{Color, QrCode};
use crate::boot_screen::BootScreen;
use crate::lock_ctx::LockCtx;
use crate::overlay::Overlay;
use crate::screen_ids::ScreenId;
use crate::screen_state::ScreenState;

pub struct WifiOverlay<SPI, DC, RST, PinE> {
    _spi: core::marker::PhantomData<SPI>,
    _dc: core::marker::PhantomData<DC>,
    _rst: core::marker::PhantomData<RST>,
    _pin: core::marker::PhantomData<PinE>,
    needs_redraw: bool
}

impl<SPI, DC, RST, PinE> WifiOverlay<SPI, DC, RST, PinE> {
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

impl<SPI, DC, RST, PinE> Overlay for WifiOverlay<SPI, DC, RST, PinE>
where
    SPI: display_interface::WriteOnlyDataCommand,
    DC: OutputPin<Error = PinE>,
    RST: OutputPin<Error = PinE>,
    PinE: std::fmt::Debug ,{
    type SPI = SPI;
    type PinE = PinE;
    type DC = DC;
    type RST = RST;

    fn draw_screen(&mut self, lock_ctx : &mut LockCtx) {
        let style = MonoTextStyle::new(&FONT_10X20, Rgb565::BLACK);
        let draw_position = Point::new(5, 125);
        let text = if lock_ctx.wifi_connected {
           Text::new("Wifi", draw_position, style)
        } else {
            Text::new("NC", draw_position, style)
        };

        text.draw(&mut lock_ctx.display).expect("Should have drawn");

        self.needs_redraw = false;
    }
}

pub struct ButtonPressOverlay<SPI, DC, RST, PinE> {
    _spi: core::marker::PhantomData<SPI>,
    _dc: core::marker::PhantomData<DC>,
    _rst: core::marker::PhantomData<RST>,
    _pin: core::marker::PhantomData<PinE>,
    needs_redraw: bool
}

impl<SPI, DC, RST, PinE> ButtonPressOverlay<SPI, DC, RST, PinE> {
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

impl<SPI, DC, RST, PinE> Overlay for ButtonPressOverlay<SPI, DC, RST, PinE>
where
    SPI: display_interface::WriteOnlyDataCommand,
    DC: OutputPin<Error = PinE>,
    RST: OutputPin<Error = PinE>,
    PinE: std::fmt::Debug ,{
    type SPI = SPI;
    type PinE = PinE;
    type DC = DC;
    type RST = RST;

    fn draw_screen(&mut self, lock_ctx : &mut LockCtx) {
        let draw_position = Point::new(0, 0);
        let size = Size::new(25, 25);

        if let Some(update) = &lock_ctx.this_update {
            if update.d0_pressed {
                let blue_square = Rectangle::new(draw_position, size)
                    .into_styled(PrimitiveStyleBuilder::new().fill_color(Rgb565::BLUE).build());
                blue_square.draw(&mut lock_ctx.display).expect("Failed to draw screen");
            }

            if update.d1_pressed {
                let green_square = Rectangle::new(draw_position, size)
                    .into_styled(PrimitiveStyleBuilder::new().fill_color(Rgb565::GREEN).build());
                green_square.draw(&mut lock_ctx.display).expect("Failed to draw screen");
            }

            if update.d2_pressed {
                let red_square = Rectangle::new(draw_position, size)
                    .into_styled(PrimitiveStyleBuilder::new().fill_color(Rgb565::RED).build());
                red_square.draw(&mut lock_ctx.display).expect("Failed to draw screen");
            }

            if !update.d0_pressed && !update.d1_pressed && !update.d2_pressed {
                let white_square = Rectangle::new(draw_position, size)
                    .into_styled(PrimitiveStyleBuilder::new().fill_color(Rgb565::WHITE).build());
                white_square.draw(&mut lock_ctx.display).expect("Failed to draw screen");
            }
        }

        self.needs_redraw = false;
    }
}