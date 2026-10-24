use embedded_graphics::pixelcolor::Rgb565;

pub const DISPLAY_WIDTH: u32 = 240;
pub const DISPLAY_HEIGHT: u32 = 135;
pub const HEADER_HEIGHT: u32 = 20;
pub const FOOTER_HEIGHT: u32 = 18;
pub const CONTENT_HEIGHT: u32 = DISPLAY_HEIGHT - HEADER_HEIGHT - FOOTER_HEIGHT; // 97px

// Theme Colors
pub const COLOR_BG: Rgb565 = Rgb565::new(0, 0, 0);
pub const COLOR_WHITE: Rgb565 = Rgb565::new(31, 63, 31);

// Header / Footer
pub const COLOR_HEADER_BG: Rgb565 = Rgb565::new(2, 5, 3);   // #141419
pub const COLOR_FOOTER_BG: Rgb565 = Rgb565::new(1, 3, 2);   // #0d0d11

// Status & Accents
pub const COLOR_GREEN: Rgb565 = Rgb565::new(0, 57, 14);        // #00e676
pub const COLOR_GREEN_DARK: Rgb565 = Rgb565::new(0, 12, 2);    // #003314
pub const COLOR_ORANGE: Rgb565 = Rgb565::new(31, 36, 0);       // #ff9100
pub const COLOR_ORANGE_DARK: Rgb565 = Rgb565::new(6, 4, 0);    // #331100
pub const COLOR_RED: Rgb565 = Rgb565::new(31, 5, 8);           // #ff1744
pub const COLOR_RED_LIGHT: Rgb565 = Rgb565::new(31, 20, 10);   // #ff5252
pub const COLOR_RED_DARK: Rgb565 = Rgb565::new(6, 0, 0);       // #330000
pub const COLOR_RED_INACTIVE: Rgb565 = Rgb565::new(10, 12, 6); // #553333
pub const COLOR_BLUE: Rgb565 = Rgb565::new(5, 30, 31);         // #2979ff
pub const COLOR_BLUE_DARK: Rgb565 = Rgb565::new(3, 6, 4);      // #1a1a24
pub const COLOR_YELLOW: Rgb565 = Rgb565::new(31, 53, 0);       // #ffd600

// Neutrals & Grays
pub const COLOR_TEXT_LIGHT: Rgb565 = Rgb565::new(28, 56, 28);     // #e0e0e0
pub const COLOR_TEXT_MUTED: Rgb565 = Rgb565::new(22, 47, 24);     // #b0bec5
pub const COLOR_TEXT_DIM: Rgb565 = Rgb565::new(12, 31, 17);       // #607d8b
pub const COLOR_TEXT_DARK: Rgb565 = Rgb565::new(6, 17, 9);        // #37474f
pub const COLOR_GRAY_DOT: Rgb565 = Rgb565::new(8, 17, 10);        // #444455
pub const COLOR_GRAY_INACTIVE: Rgb565 = Rgb565::new(6, 12, 7);    // #33333e
pub const COLOR_GRAY_BOX: Rgb565 = Rgb565::new(6, 12, 8);         // #333344
pub const COLOR_GRAY_BOX_BORDER: Rgb565 = Rgb565::new(10, 21, 12);// #555566
