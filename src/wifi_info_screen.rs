use crate::boot_screen::BootScreen;
use crate::lock_ctx::LockCtx;
use crate::prelude::prelude::{DynScreen, MySPI};
use crate::screen_ids::ScreenId;
use crate::screen_state::ScreenState;
use crate::under_contract_screen::UnderContractScreen;
use crate::verifier::VerifiedType;
use crate::wifi_util::{connect_wifi, parse_wifi_qr};
use embedded_graphics::mono_font::ascii::FONT_10X20;
use embedded_graphics::mono_font::MonoTextStyle;
use embedded_graphics::text::{Alignment, Text};
use embedded_graphics_core::geometry::Point;
use embedded_graphics_core::pixelcolor::Rgb565;
use embedded_graphics_core::prelude::{DrawTarget, RgbColor};
use embedded_graphics_core::Drawable;
use embedded_hal::digital::OutputPin;
use esp_idf_hal::gpio::{GpioError, Output, PinDriver};

pub struct WifiInfoScreen<SPI, DC, RST, PinE> {
    _spi: core::marker::PhantomData<SPI>,
    _dc: core::marker::PhantomData<DC>,
    _rst: core::marker::PhantomData<RST>,
    _pin: core::marker::PhantomData<PinE>,
    needs_redraw: bool,
    text: String,
}

impl<SPI, DC, RST, PinE> WifiInfoScreen<SPI, DC, RST, PinE> {
    pub fn new() -> Self {
        Self {
            _spi: core::marker::PhantomData,
            _dc: core::marker::PhantomData,
            _rst: core::marker::PhantomData,
            _pin: core::marker::PhantomData,
            needs_redraw: true,
            text: "Wifi Info".to_string(),
        }
    }
}

impl<SPI, DC, RST, PinE> ScreenState for WifiInfoScreen<SPI, DC, RST, PinE>
where
    SPI: display_interface::WriteOnlyDataCommand,
    DC: OutputPin<Error = PinE>,
    RST: OutputPin<Error = PinE>,
    PinE: std::fmt::Debug,
{
    type SPI = SPI;
    type PinE = PinE;
    type DC = DC;
    type RST = RST;

    fn on_update(&mut self, lock_ctx: &mut LockCtx) -> Option<Box<DynScreen<'static>>> {
        if let Some(update) = &lock_ctx.this_update {
            if let Some(incoming_data) = update.qr_data.clone() {
                if let Ok(maybe_string) = String::from_utf8(incoming_data) {
                    if maybe_string.starts_with("WIFI:") {
                        let maybe_creds = parse_wifi_qr(maybe_string);

                        if let Some((ssid, password)) = maybe_creds {
                            if let Ok(_) = lock_ctx.connect_wifi(&ssid, &password) {
                                self.text = ssid;
                            } else {
                                self.text = "Couldn't connect".to_string()
                            }
                            self.needs_redraw = true;
                        }
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
    ) -> Result<Option<Box<DynScreen<'static>>>, String> {
        Ok(None)
    }

    fn draw_screen(&mut self, lock_ctx: &mut LockCtx) {
        lock_ctx
            .display
            .clear(Rgb565::BLACK)
            .expect("Screen should have cleared");

        self.text = lock_ctx.get_wifi_ssid().unwrap();

        let style = MonoTextStyle::new(&FONT_10X20, Rgb565::GREEN);
        let draw_position = Point::new(80, 10);
        let text =
            Text::with_alignment(self.text.as_str(), draw_position, style, Alignment::Center);
        text.draw(&mut lock_ctx.display).expect("Should have drawn");

        self.needs_redraw = false;
    }

    fn get_id(&self) -> ScreenId {
        ScreenId::WifiInfo
    }

    fn needs_redraw(&self) -> bool {
        self.needs_redraw
    }
}
