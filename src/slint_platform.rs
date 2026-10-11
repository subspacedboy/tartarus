use std::rc::Rc;
use std::time::Instant;
use slint::platform::software_renderer::{
    LineBufferProvider, MinimalSoftwareWindow, RepaintBufferType, Rgb565Pixel,
};
use slint::platform::{Platform, WindowAdapter, PlatformError};
use crate::prelude::MyDisplay;
use embedded_graphics::draw_target::DrawTarget;
use embedded_graphics::geometry::{Point, Size};
use embedded_graphics::pixelcolor::raw::RawU16;
use embedded_graphics::pixelcolor::Rgb565;
use embedded_graphics::primitives::Rectangle;

pub struct EspPlatform {
    window: Rc<MinimalSoftwareWindow>,
    start_time: Instant,
}

impl EspPlatform {
    pub fn new(window: Rc<MinimalSoftwareWindow>) -> Self {
        Self {
            window,
            start_time: Instant::now(),
        }
    }
}

impl Platform for EspPlatform {
    fn create_window_adapter(&self) -> Result<Rc<dyn WindowAdapter>, PlatformError> {
        Ok(self.window.clone())
    }

    fn duration_since_start(&self) -> core::time::Duration {
        self.start_time.elapsed()
    }
}

pub struct DisplayWrapper<'a> {
    pub display: &'a mut MyDisplay<'static>,
    pub line_buffer: [Rgb565Pixel; 240],
}

impl<'a> LineBufferProvider for DisplayWrapper<'a> {
    type TargetPixel = Rgb565Pixel;

    fn process_line(
        &mut self,
        line: usize,
        range: core::ops::Range<usize>,
        render_fn: impl FnOnce(&mut [Self::TargetPixel]),
    ) {
        let buffer = &mut self.line_buffer[range.clone()];
        render_fn(buffer);

        let rect = Rectangle::new(
            Point::new(range.start as i32, line as i32),
            Size::new(range.len() as u32, 1),
        );
        let _ = self.display.fill_contiguous(
            &rect,
            buffer.iter().map(|p| Rgb565::from(RawU16::new(p.0))),
        );
    }
}

pub fn generate_qr_image(content: &str) -> Option<slint::Image> {
    use qrcode::QrCode;
    use slint::{Image, Rgb8Pixel, SharedPixelBuffer};

    let qr = QrCode::new(content.as_bytes()).ok()?;
    let width = qr.width();
    let mut buffer = SharedPixelBuffer::<Rgb8Pixel>::new(width as u32, width as u32);
    let slice = buffer.make_mut_slice();

    for y in 0..width {
        for x in 0..width {
            let is_dark = qr[(x, y)] == qrcode::Color::Dark;
            let val = if is_dark { 0 } else { 255 };
            slice[y * width + x] = Rgb8Pixel { r: val, g: val, b: val };
        }
    }

    Some(Image::from_rgb8(buffer))
}

pub fn init_slint_platform() -> (Rc<MinimalSoftwareWindow>, crate::generated_ui::AppWindow) {
    let window = MinimalSoftwareWindow::new(RepaintBufferType::ReusedBuffer);
    window.set_size(slint::PhysicalSize::new(240, 135));

    let platform = EspPlatform::new(window.clone());
    slint::platform::set_platform(Box::new(platform)).expect("Failed to initialize Slint platform");

    let ui = crate::generated_ui::AppWindow::new().expect("Failed to create AppWindow");
    (window, ui)
}
