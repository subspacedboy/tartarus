use esp_idf_svc::nvs::{EspNvs, NvsDefault};

pub const MAX_EVENTS: usize = 20;
const META_KEY: &str = "ev_meta";

/// Persistent ringbuffer tracking up to MAX_EVENTS (20) in NVS.
///
/// ev_meta: 5 bytes [head: u8, tail: u8, count: u8, counter_lo: u8, counter_hi: u8]
/// ev_d_0 .. ev_d_19: individual SignedEvent blobs
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct EventRingMeta {
    pub head: u8,
    pub tail: u8,
    pub count: u8,
    pub counter: u16,
}

impl EventRingMeta {
    pub fn new() -> Self {
        Self {
            head: 0,
            tail: 0,
            count: 0,
            counter: 0,
        }
    }

    pub fn to_bytes(&self) -> [u8; 5] {
        let counter_bytes = self.counter.to_le_bytes();
        [
            self.head,
            self.tail,
            self.count,
            counter_bytes[0],
            counter_bytes[1],
        ]
    }

    pub fn from_bytes(bytes: &[u8]) -> Option<Self> {
        if bytes.len() < 5 {
            return None;
        }
        let counter = u16::from_le_bytes([bytes[3], bytes[4]]);
        Some(Self {
            head: bytes[0],
            tail: bytes[1],
            count: bytes[2],
            counter,
        })
    }
}

pub struct PersistentEventRingBuffer {
    meta: EventRingMeta,
}

impl PersistentEventRingBuffer {
    pub fn load_or_init(nvs: &EspNvs<NvsDefault>) -> Self {
        let mut buf = [0u8; 5];
        let meta = match nvs.get_blob(META_KEY, &mut buf) {
            Ok(Some(slice)) => EventRingMeta::from_bytes(slice).unwrap_or_else(EventRingMeta::new),
            _ => {
                let initial = EventRingMeta::new();
                let _ = nvs.set_blob(META_KEY, &initial.to_bytes());
                initial
            }
        };

        log::info!("Loaded PersistentEventRingBuffer: {:?}", meta);
        Self { meta }
    }

    #[allow(dead_code)]
    pub fn meta(&self) -> &EventRingMeta {
        &self.meta
    }

    pub fn reset_counter(&mut self, nvs: &EspNvs<NvsDefault>) {
        self.meta.counter = 0;
        let _ = nvs.set_blob(META_KEY, &self.meta.to_bytes());
        log::info!("Reset event counter to 0 for new contract");
    }

    pub fn current_counter(&self) -> u16 {
        self.meta.counter
    }

    pub fn append(&mut self, nvs: &EspNvs<NvsDefault>, payload: &[u8]) {
        let slot = self.meta.head;
        let slot_key = format!("ev_d_{}", slot);

        if let Err(e) = nvs.set_blob(&slot_key, payload) {
            log::error!("Failed to write event slot {}: {:?}", slot, e);
            return;
        }

        self.meta.head = (self.meta.head + 1) % (MAX_EVENTS as u8);
        if self.meta.count < MAX_EVENTS as u8 {
            self.meta.count += 1;
        } else {
            // Buffer full: advance tail to overwrite oldest
            self.meta.tail = (self.meta.tail + 1) % (MAX_EVENTS as u8);
        }

        // Increment counter for next event
        self.meta.counter = self.meta.counter.saturating_add(1);

        if let Err(e) = nvs.set_blob(META_KEY, &self.meta.to_bytes()) {
            log::error!("Failed to save event ring metadata: {:?}", e);
        }
    }

    pub fn get_unacknowledged_events(&self, nvs: &EspNvs<NvsDefault>) -> Vec<Vec<u8>> {
        let mut events = Vec::new();
        let mut buf = [0u8; 512];

        for i in 0..self.meta.count {
            let slot = (self.meta.tail + i) % (MAX_EVENTS as u8);
            let slot_key = format!("ev_d_{}", slot);

            if let Ok(Some(slice)) = nvs.get_blob(&slot_key, &mut buf) {
                events.push(slice.to_vec());
            } else if let Ok(len) = nvs.blob_len(&slot_key) {
                if let Some(actual_len) = len {
                    let mut dyn_buf = vec![0u8; actual_len];
                    if let Ok(Some(slice)) = nvs.get_blob(&slot_key, &mut dyn_buf) {
                        events.push(slice.to_vec());
                    }
                }
            }
        }

        events
    }

    #[allow(dead_code)]
    pub fn acknowledge_oldest(&mut self, nvs: &EspNvs<NvsDefault>) {
        if self.meta.count == 0 {
            return;
        }

        let slot = self.meta.tail;
        let slot_key = format!("ev_d_{}", slot);
        let _ = nvs.remove(&slot_key);

        self.meta.tail = (self.meta.tail + 1) % (MAX_EVENTS as u8);
        self.meta.count -= 1;

        let _ = nvs.set_blob(META_KEY, &self.meta.to_bytes());
        log::debug!("Acknowledged event at slot {}, remaining count: {}", slot, self.meta.count);
    }
}

#[cfg(test)]
#[allow(unused_imports)]
mod tests {
    use super::*;

    #[test]
    fn test_event_ring_meta_roundtrip() {
        let meta = EventRingMeta {
            head: 7,
            tail: 2,
            count: 5,
            counter: 42,
        };

        let bytes = meta.to_bytes();
        let restored = EventRingMeta::from_bytes(&bytes).unwrap();
        assert_eq!(meta, restored);
    }

    #[test]
    fn test_event_ring_meta_invalid() {
        assert!(EventRingMeta::from_bytes(&[1, 2, 3]).is_none());
    }
}
