#[derive(Debug, Clone)]
pub struct InternalChallenge {
    pub nonce: Vec<u8>,
    pub request_id: i64,
}

impl InternalChallenge {
    pub fn new(nonce: Vec<u8>, request_id: i64) -> Self {
        Self { nonce, request_id }
    }
}

#[derive(Debug, Clone)]
pub struct InternalFirmwareResponse {
    pub firmware_name: String,
    pub version_name: String,
    pub size: usize,
}

impl InternalFirmwareResponse {
    pub fn new(name: String, version_name: String, size: usize) -> Self {
        Self {
            firmware_name: name,
            version_name,
            size,
        }
    }
}

#[derive(Debug, Clone)]
pub struct InternalFirmwareChunk {
    data: Vec<u8>,
    size: usize,
    offset: usize,
}

impl InternalFirmwareChunk {
    pub fn new(data: Vec<u8>, size: usize, offset: usize) -> Self {
        Self { data, size, offset }
    }

    pub fn data(&self) -> &[u8] {
        &self.data
    }

    pub fn size(&self) -> usize {
        self.size
    }

    pub fn offset(&self) -> usize {
        self.offset
    }
}

#[derive(Debug, Clone)]
pub enum FirmwareMessageType {
    Challenge(InternalChallenge),
    FirmwareResponse(InternalFirmwareResponse),
    FirmwareChunk(InternalFirmwareChunk),
}
