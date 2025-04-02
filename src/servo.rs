use esp_idf_hal::ledc::LedcDriver;

pub struct Servo<'a> {
    driver: LedcDriver<'a>,
    min_limit: u32,
    max_limit: u32,
    max_duty: u32
}

const OPEN_ANGLE : u32 = 1;
const CLOSED_ANGLE : u32 = 90;

impl<'a> Servo<'a> {
    pub fn new(driver: LedcDriver<'a>) -> Servo<'a> {
        let max_duty: u32 = driver.get_max_duty();
        log::info!("Max Duty {}", max_duty);
        let min_limit = max_duty * 25 / 1000;
        log::info!("Min Limit {}", min_limit);
        let max_limit = max_duty * 125 / 1000;
        log::info!("Max Limit {}", max_limit);

        Self{
            driver,
            min_limit,
            max_limit,
            max_duty
        }
    }

    pub fn open_position(&mut self) {
        log::info!("Setting duty to {OPEN_ANGLE}");
        self.driver
            .set_duty(map(OPEN_ANGLE, 0, 180, self.min_limit, self.max_limit))
            .unwrap();
    }

    pub fn close_position(&mut self) {
        log::info!("Setting duty to {CLOSED_ANGLE}");
        self.driver
            .set_duty(map(CLOSED_ANGLE, 0, 180, self.min_limit, self.max_limit))
            .unwrap();
    }
}

fn map(x: u32, in_min: u32, in_max: u32, out_min: u32, out_max: u32) -> u32 {
    (x - in_min) * (out_max - out_min) / (in_max - in_min) + out_min
}