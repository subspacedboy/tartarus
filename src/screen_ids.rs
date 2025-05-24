#[repr(usize)]
pub enum ScreenId {
    QrCode = 0,
    LockState = 1,
    WifiInfo = 2,
    FirmwareInfo = 3,
}

impl From<usize> for ScreenId {
    fn from(value: usize) -> Self {
        match value {
            0 => ScreenId::QrCode,
            1 => ScreenId::LockState,
            2 => ScreenId::WifiInfo,
            3 => ScreenId::FirmwareInfo,
            _ => unreachable!(),
        }
    }
}

impl From<ScreenId> for usize {
    fn from(value: ScreenId) -> Self {
        match value {
            ScreenId::QrCode => 0,
            ScreenId::LockState => 1,
            ScreenId::WifiInfo => 2,
            ScreenId::FirmwareInfo => 3,
        }
    }
}
