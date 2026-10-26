use esp_idf_svc::nvs::{EspNvs, NvsDefault};
use crate::event_generated::club::subjugated::fb::event::root_as_signed_event;

pub const MAX_EVENTS: usize = 20;
const META_KEY: &str = "ev_meta";

/// An unacknowledged event loaded from persistent NVS storage.
#[derive(Debug, Clone)]
pub struct UnacknowledgedEvent {
    pub contract_serial: u16,
    pub counter: u16,
    pub data: Vec<u8>,
}

/// Extracts (contract_serial_number, counter) from a serialized SignedEvent flatbuffer blob.
pub fn extract_event_meta(payload: &[u8]) -> Option<(u16, u16)> {
    let signed_event = root_as_signed_event(payload).ok()?;
    let event = signed_event.payload()?;
    let metadata = event.metadata()?;
    Some((metadata.contract_serial_number(), metadata.counter()))
}

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

    pub fn get_unacknowledged_events(&self, nvs: &EspNvs<NvsDefault>) -> Vec<UnacknowledgedEvent> {
        let mut events = Vec::new();
        let mut buf = [0u8; 512];

        for i in 0..self.meta.count {
            let slot = (self.meta.tail + i) % (MAX_EVENTS as u8);
            let slot_key = format!("ev_d_{}", slot);

            let event_data = if let Ok(Some(slice)) = nvs.get_blob(&slot_key, &mut buf) {
                Some(slice.to_vec())
            } else if let Ok(Some(actual_len)) = nvs.blob_len(&slot_key) {
                let mut dyn_buf = vec![0u8; actual_len];
                if let Ok(Some(slice)) = nvs.get_blob(&slot_key, &mut dyn_buf) {
                    Some(slice.to_vec())
                } else {
                    None
                }
            } else {
                None
            };

            if let Some(data) = event_data {
                if let Some((contract_serial, counter)) = extract_event_meta(&data) {
                    events.push(UnacknowledgedEvent {
                        contract_serial,
                        counter,
                        data,
                    });
                } else {
                    log::warn!("Could not extract event metadata from slot {}", slot);
                }
            }
        }

        events
    }

    /// Acknowledges events in the ring buffer up to and including the specified `(contract_serial, counter)`.
    ///
    /// Inspects the event at `tail`:
    /// - If `tail` belongs to an older contract (`tail_contract < contract_serial`), it is obsolete and removed.
    /// - If `tail` belongs to the same contract and `tail_counter <= counter`, it is removed.
    /// - If `tail` is newer than `counter` or belongs to a newer contract, inspection stops.
    ///
    /// This watermark-based approach prevents out-of-order corruption and safely ignores
    /// acknowledgements for events that were already overwritten due to ring buffer overflow.
    pub fn acknowledge_up_to(
        &mut self,
        nvs: &EspNvs<NvsDefault>,
        contract_serial: u16,
        counter: u16,
    ) {
        let mut buf = [0u8; 512];
        let mut acked_any = false;

        while self.meta.count > 0 {
            let slot = self.meta.tail;
            let slot_key = format!("ev_d_{}", slot);

            let event_data = if let Ok(Some(slice)) = nvs.get_blob(&slot_key, &mut buf) {
                Some(slice.to_vec())
            } else if let Ok(Some(actual_len)) = nvs.blob_len(&slot_key) {
                let mut dyn_buf = vec![0u8; actual_len];
                if let Ok(Some(slice)) = nvs.get_blob(&slot_key, &mut dyn_buf) {
                    Some(slice.to_vec())
                } else {
                    None
                }
            } else {
                None
            };

            let should_pop = match event_data.as_deref().and_then(extract_event_meta) {
                Some((tail_contract, tail_counter)) => {
                    if tail_contract < contract_serial {
                        // Obsolete event from older contract
                        true
                    } else if tail_contract == contract_serial {
                        // Same contract: acknowledge if counter <= target counter
                        tail_counter <= counter
                    } else {
                        // Newer contract: stop
                        false
                    }
                }
                None => {
                    // Corrupted or missing blob at tail: pop to prevent head-of-line blocking
                    log::warn!("Removing unparseable event blob at tail slot {}", slot);
                    true
                }
            };

            if should_pop {
                let _ = nvs.remove(&slot_key);
                self.meta.tail = (self.meta.tail + 1) % (MAX_EVENTS as u8);
                self.meta.count -= 1;
                acked_any = true;
            } else {
                break;
            }
        }

        if acked_any {
            let _ = nvs.set_blob(META_KEY, &self.meta.to_bytes());
            log::info!(
                "Acknowledged events up to contract {}, counter {}. Remaining unacked count: {}",
                contract_serial,
                counter,
                self.meta.count
            );
        }
    }

    /// Wipes all unacknowledged events from the flash ring buffer and resets head/tail/count.
    ///
    /// Preserves the monotonic counter so that future events continue from the current counter.
    pub fn clear(&mut self, nvs: &EspNvs<NvsDefault>) -> usize {
        let cleared = self.meta.count as usize;
        for i in 0..MAX_EVENTS {
            let slot_key = format!("ev_d_{}", i);
            let _ = nvs.remove(&slot_key);
        }
        self.meta.head = 0;
        self.meta.tail = 0;
        self.meta.count = 0;
        let _ = nvs.set_blob(META_KEY, &self.meta.to_bytes());
        log::info!("Cleared PersistentEventRingBuffer (removed {} events)", cleared);
        cleared
    }

    /// Returns a compact JSON string representing the unacknowledged events currently in the ring buffer.
    pub fn inspect_events_json(&self, nvs: &EspNvs<NvsDefault>) -> String {
        let mut buf = [0u8; 512];
        let mut items = Vec::new();

        for i in 0..self.meta.count {
            let slot = (self.meta.tail + i) % (MAX_EVENTS as u8);
            let slot_key = format!("ev_d_{}", slot);

            let event_data = if let Ok(Some(slice)) = nvs.get_blob(&slot_key, &mut buf) {
                Some(slice.to_vec())
            } else if let Ok(Some(actual_len)) = nvs.blob_len(&slot_key) {
                let mut dyn_buf = vec![0u8; actual_len];
                if let Ok(Some(slice)) = nvs.get_blob(&slot_key, &mut dyn_buf) {
                    Some(slice.to_vec())
                } else {
                    None
                }
            } else {
                None
            };

            if let Some(data) = event_data {
                let (contract, counter, event_type) = if let Ok(signed_event) = root_as_signed_event(&data) {
                    if let Some(event) = signed_event.payload() {
                        let metadata = event.metadata();
                        let contract = metadata.as_ref().map(|m| m.contract_serial_number()).unwrap_or(0);
                        let counter = metadata.as_ref().map(|m| m.counter()).unwrap_or(0);
                        let ev_type = match event.event_type() {
                            crate::event_generated::club::subjugated::fb::event::EventType::AcceptContract => "AcceptContract",
                            crate::event_generated::club::subjugated::fb::event::EventType::LocalLock => "LocalLock",
                            crate::event_generated::club::subjugated::fb::event::EventType::LocalUnlock => "LocalUnlock",
                            crate::event_generated::club::subjugated::fb::event::EventType::Lock => "Lock",
                            crate::event_generated::club::subjugated::fb::event::EventType::Unlock => "Unlock",
                            crate::event_generated::club::subjugated::fb::event::EventType::ReleaseContract => "ReleaseContract",
                            crate::event_generated::club::subjugated::fb::event::EventType::EmergencyUnlock => "EmergencyUnlock",
                            crate::event_generated::club::subjugated::fb::event::EventType::StartedUpdate => "StartedUpdate",
                            crate::event_generated::club::subjugated::fb::event::EventType::PeriodicUpdate => "PeriodicUpdate",
                            _ => "Unknown",
                        };
                        (contract, counter, ev_type)
                    } else {
                        (0, 0, "NoPayload")
                    }
                } else {
                    (0, 0, "Corrupt")
                };

                items.push(format!(
                    r#"{{"slot":{},"contract":{},"counter":{},"type":"{}"}}"#,
                    slot, contract, counter, event_type
                ));
            }
        }

        format!("[{}]", items.join(","))
    }
}

#[cfg(test)]
#[allow(unused_imports)]
mod tests {
    use super::*;
    use flatbuffers::FlatBufferBuilder;
    use crate::event_generated::club::subjugated::fb::event::{
        CommonMetadata, CommonMetadataArgs, Event, EventArgs, EventType, Lock, LockArgs,
        SignedEvent, SignedEventArgs,
    };

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

    #[test]
    fn test_extract_event_meta() {
        let mut builder = FlatBufferBuilder::with_capacity(256);
        let session = builder.create_string("test_session");
        let event_id = builder.create_string("test_event_id");

        let metadata = CommonMetadata::create(
            &mut builder,
            &CommonMetadataArgs {
                lock_session: Some(session),
                contract_serial_number: 12,
                serial_number: 0,
                counter: 7,
                event_id: Some(event_id),
                correlation_id: None,
            },
        );

        let lock_evt = Lock::create(&mut builder, &LockArgs {});
        let event = Event::create(
            &mut builder,
            &EventArgs {
                metadata: Some(metadata),
                event_type: EventType::Lock,
                event: Some(lock_evt.as_union_value()),
            },
        );

        let sig = builder.create_vector(&[1u8; 64]);
        let signed_event = SignedEvent::create(
            &mut builder,
            &SignedEventArgs {
                signature: Some(sig),
                payload: Some(event),
            },
        );

        builder.finish(signed_event, None);
        let data = builder.finished_data();

        let extracted = extract_event_meta(data);
        assert_eq!(extracted, Some((12, 7)));
    }
}
