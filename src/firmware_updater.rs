use crate::mqtt_service::SignedMessageTransport;
use anyhow::{anyhow, Context};
use embedded_svc::ota::{Ota, SlotState};
use esp_idf_svc::ota::{EspOta, EspOtaUpdate};
use std::cell::RefCell;
use std::collections::VecDeque;
use std::marker::PhantomData;
use std::rc::Rc;
use std::sync::{Arc, Mutex};
use std::thread;
use std::time::Duration;

pub struct FirmwareUpdater {
    incoming_bytes: Arc<Mutex<VecDeque<Vec<u8>>>>,
    complete: Arc<Mutex<bool>>,
}

impl FirmwareUpdater {
    pub fn new() -> Self {
        let mut updater = Self {
            incoming_bytes: Arc::new(Mutex::new(VecDeque::new())),
            complete: Arc::new(Mutex::new(false)),
        };

        let queue_ref = Arc::clone(&updater.incoming_bytes);
        let complete_ref = Arc::clone(&updater.complete);
        thread::spawn(move || {
            let mut ota = EspOta::new().expect("Ota new");
            // let mut update = ota.initiate_update().expect("Failed to initiate OTA");

            loop {
                if let Ok(complete_flag) = complete_ref.lock() {
                    if *complete_flag {
                        log::info!("OTA update complete");
                        break;
                    }
                }

                let mut bytes_to_write: VecDeque<Vec<u8>> =
                    queue_ref.lock().unwrap().drain(..).collect();
                while !bytes_to_write.is_empty() {
                    let data = bytes_to_write.pop_front().unwrap();
                    log::info!("FIRMWARE Writing {} bytes", data.len());
                    // update.write(data.as_slice()).expect("Failed to write data");
                }
                std::thread::sleep(Duration::from_millis(800));
            }
        });

        updater
    }

    pub fn queue_bytes(&self, bytes: Vec<u8>) {
        if let Ok(mut incoming_bytes) = self.incoming_bytes.lock() {
            incoming_bytes.push_back(bytes);
        }
    }

    fn get_running_version() -> anyhow::Result<heapless::String<24>> {
        let ota = EspOta::new().expect("Ota new");

        if let Ok(slot) = ota.get_running_slot() {
            if let Some(firmware) = slot.firmware {
                Ok(firmware.version)
            } else {
                Err(anyhow!("Failed to get firmware version"))
            }
        } else {
            Err(anyhow!("Failed to get firmware version"))
        }
    }

    pub fn do_something() -> anyhow::Result<()> {
        let mut ota = EspOta::new()?;
        let running_slot = ota.get_running_slot()?;

        if running_slot.state == SlotState::Factory {
            log::info!("Factory slot can't be marked");
            return Ok(());
        }

        if running_slot.state != SlotState::Valid {
            let is_app_valid = true;

            // Do the necessary checks to validate that your app is working as expected.
            // For example, you can contact your API to verify you still have access to it.

            if is_app_valid {
                ota.mark_running_slot_valid()?;
            } else {
                ota.mark_running_slot_invalid_and_reboot();
            }
        }

        Ok(())
    }
}
