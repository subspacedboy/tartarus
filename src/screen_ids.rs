#[derive(Debug, Clone, Copy, PartialEq, Eq)]
#[repr(usize)]
pub enum ScreenId {
    QrCode = 0,
    LockState = 1,
    EmergencyUnlock = 2,
    BlePin = 3,
    WifiInfo = 4,
    FirmwareInfo = 5,
}

impl ScreenId {
    pub fn next(&self, is_emergency_available: bool) -> ScreenId {
        match self {
            ScreenId::QrCode => ScreenId::LockState,
            ScreenId::LockState => {
                if is_emergency_available {
                    ScreenId::EmergencyUnlock
                } else {
                    ScreenId::BlePin
                }
            }
            ScreenId::EmergencyUnlock => ScreenId::BlePin,
            ScreenId::BlePin => ScreenId::WifiInfo,
            ScreenId::WifiInfo => ScreenId::FirmwareInfo,
            ScreenId::FirmwareInfo => ScreenId::QrCode,
        }
    }

    pub fn title(&self) -> &'static str {
        match self {
            ScreenId::QrCode => "PAIRING / QR",
            ScreenId::LockState => "LOCK STATUS",
            ScreenId::EmergencyUnlock => "EMERGENCY UNLOCK",
            ScreenId::BlePin => "BLE PIN",
            ScreenId::WifiInfo => "NETWORK",
            ScreenId::FirmwareInfo => "SYSTEM",
        }
    }
}

impl From<usize> for ScreenId {
    fn from(value: usize) -> Self {
        match value {
            0 => ScreenId::QrCode,
            1 => ScreenId::LockState,
            2 => ScreenId::EmergencyUnlock,
            3 => ScreenId::BlePin,
            4 => ScreenId::WifiInfo,
            5 => ScreenId::FirmwareInfo,
            _ => ScreenId::QrCode,
        }
    }
}

impl From<ScreenId> for usize {
    fn from(value: ScreenId) -> Self {
        match value {
            ScreenId::QrCode => 0,
            ScreenId::LockState => 1,
            ScreenId::EmergencyUnlock => 2,
            ScreenId::BlePin => 3,
            ScreenId::WifiInfo => 4,
            ScreenId::FirmwareInfo => 5,
        }
    }
}

#[cfg(test)]
#[allow(unused_imports)]
mod tests {
    use super::*;

    #[test]
    fn test_screen_cycle_without_emergency_unlock() {
        let mut screen = ScreenId::QrCode;
        screen = screen.next(false);
        assert_eq!(screen, ScreenId::LockState);
        screen = screen.next(false);
        assert_eq!(screen, ScreenId::BlePin);
        screen = screen.next(false);
        assert_eq!(screen, ScreenId::WifiInfo);
        screen = screen.next(false);
        assert_eq!(screen, ScreenId::FirmwareInfo);
        screen = screen.next(false);
        assert_eq!(screen, ScreenId::QrCode);
    }

    #[test]
    fn test_screen_cycle_with_emergency_unlock() {
        let mut screen = ScreenId::QrCode;
        screen = screen.next(true);
        assert_eq!(screen, ScreenId::LockState);
        screen = screen.next(true);
        assert_eq!(screen, ScreenId::EmergencyUnlock);
        screen = screen.next(true);
        assert_eq!(screen, ScreenId::BlePin);
        screen = screen.next(true);
        assert_eq!(screen, ScreenId::WifiInfo);
        screen = screen.next(true);
        assert_eq!(screen, ScreenId::FirmwareInfo);
        screen = screen.next(true);
        assert_eq!(screen, ScreenId::QrCode);
    }

    #[test]
    fn test_screen_titles() {
        assert_eq!(ScreenId::QrCode.title(), "PAIRING / QR");
        assert_eq!(ScreenId::LockState.title(), "LOCK STATUS");
        assert_eq!(ScreenId::EmergencyUnlock.title(), "EMERGENCY UNLOCK");
        assert_eq!(ScreenId::BlePin.title(), "BLE PIN");
        assert_eq!(ScreenId::WifiInfo.title(), "NETWORK");
        assert_eq!(ScreenId::FirmwareInfo.title(), "SYSTEM");
    }
}
