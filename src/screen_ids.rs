#[derive(Debug, Clone, Copy, PartialEq, Eq)]
#[repr(usize)]
pub enum ScreenId {
    QrCode = 0,
    LockState = 1,
    BlePin = 2,
    WifiInfo = 3,
    FirmwareInfo = 4,
}

impl From<usize> for ScreenId {
    fn from(value: usize) -> Self {
        match value {
            0 => ScreenId::QrCode,
            1 => ScreenId::LockState,
            2 => ScreenId::BlePin,
            3 => ScreenId::WifiInfo,
            4 => ScreenId::FirmwareInfo,
            _ => unreachable!(),
        }
    }
}

impl From<ScreenId> for usize {
    fn from(value: ScreenId) -> Self {
        match value {
            ScreenId::QrCode => 0,
            ScreenId::LockState => 1,
            ScreenId::BlePin => 2,
            ScreenId::WifiInfo => 3,
            ScreenId::FirmwareInfo => 4,
        }
    }
}
