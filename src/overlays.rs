use base64::engine::general_purpose;
use embedded_graphics::prelude::Primitive;
use embedded_graphics::primitives::PrimitiveStyleBuilder;
use embedded_graphics_core::Drawable;
use embedded_graphics_core::geometry::{Point, Size};
use embedded_graphics_core::pixelcolor::Rgb565;
use embedded_graphics_core::prelude::RgbColor;
use embedded_graphics_core::primitives::Rectangle;
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
        if lock_ctx.wifi_connected {
            // let style = MonoTextStyle::new(&FONT_6X10, Rgb565::GREEN);
            // Text::new("Wifi!", Point::new(0, 50), style)
            //     .draw(&mut self.display).expect("Didn't draw text");
            let green_wifi_square = Rectangle::new(Point::new(0, 25), Size::new(25, 25))
                .into_styled(PrimitiveStyleBuilder::new().fill_color(Rgb565::GREEN).build());
            green_wifi_square.draw(&mut lock_ctx.display).expect("Failed to draw screen");
        }

        self.needs_redraw = false;
    }
}