#[derive(Debug, PartialEq, Eq, Clone, Copy)]
pub enum ScreenId {
    Boot,
    DisplayContract,
    UnderContract,
    CatchAll
}