use crate::internal_contract::EndCriteria;
use crate::lock_ctx::LockCtx;
use crate::prelude::prelude::{DynScreen, MySPI};
use crate::screen_ids::ScreenId;
use crate::screen_state::ScreenState;
use crate::under_contract_screen::UnderContractScreen;
use embedded_graphics::mono_font::ascii::{FONT_8X13, FONT_8X13_BOLD};
use embedded_graphics::mono_font::MonoTextStyle;
use embedded_graphics::text::{Alignment, Text};
use embedded_graphics_core::geometry::Point;
use embedded_graphics_core::pixelcolor::Rgb565;
use embedded_graphics_core::prelude::{Dimensions, RgbColor};
use embedded_graphics_core::Drawable;
use embedded_hal::digital::OutputPin;
use esp_idf_hal::gpio::{GpioError, Output, PinDriver};

pub struct DisplayContractScreen<SPI, DC, RST, PinE> {
    _spi: core::marker::PhantomData<SPI>,
    _dc: core::marker::PhantomData<DC>,
    _rst: core::marker::PhantomData<RST>,
    _pin: core::marker::PhantomData<PinE>,
    needs_redraw: bool
}

impl<SPI, DC, RST, PinE> DisplayContractScreen<SPI, DC, RST, PinE> {
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

impl<SPI, DC, RST, PinE> ScreenState for DisplayContractScreen<SPI, DC, RST, PinE>
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
            if update.d0_pressed {
                // Accepted!
                lock_ctx.lock();
                let locked_screen = Box::new(
                    UnderContractScreen::<
                        MySPI<'static>,
                        PinDriver<'static, _, Output>,
                        PinDriver<'static, _, Output>,
                        GpioError
                    >::new());
                return Some(locked_screen);
            }
        }
        None
    }

    fn draw_screen(&mut self, lock_ctx : &mut LockCtx) {
        let style = MonoTextStyle::new(&FONT_8X13, Rgb565::BLACK);
        let draw_position = Point::new(30, 30);

        if let Some(contract) = &lock_ctx.contract {
            let end_text : &str = match contract.end_criteria {
                EndCriteria::WhenISaySo => { "When I Say So" },
                EndCriteria::Time => {
                    "Time Based"
                }
            };

            let temp_unlock : &str = if contract.temporary_unlock_allowed {
                "Temporary unlock allowed"
            } else {
                "No temporary unlock"
            };

            let terms = format!("Contract Terms:\nEnds: {}\n{}\nPress 1 for Accept", end_text, temp_unlock);
            let text = Text::with_alignment(terms.as_str(), draw_position, style, Alignment::Left);
            text.draw(&mut lock_ctx.display).expect("Should have drawn");

            if contract.unremovable {
                // Figure out where previous box ended.
                let new_y = text.bounding_box().bottom_right().unwrap().y + 13;

                let terms = "Unremovable!";
                let style = MonoTextStyle::new(&FONT_8X13_BOLD, Rgb565::RED);
                let draw_position = Point::new(30, new_y);
                let text = Text::with_alignment(terms, draw_position, style, Alignment::Left);
                text.draw(&mut lock_ctx.display).expect("Should have drawn");
            }
        }

        self.needs_redraw = false;
    }

    fn get_id(&self) -> ScreenId {
        ScreenId::DisplayContract
    }

    fn needs_redraw(&self) -> bool {
        self.needs_redraw
    }
}