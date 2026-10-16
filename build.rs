use std::path::Path;
use std::{env, fs};

fn main() {
    let out_dir = env::var("OUT_DIR").unwrap();
    let file_path = "private.key";
    let file_exists = Path::new(file_path).exists();

    println!("cargo:rerun-if-changed={}", file_path);

    let file_content = if file_exists {
        let key_bytes = fs::read(file_path).unwrap();
        let hex_key = if key_bytes.len() == 32 {
            key_bytes.iter().map(|b| format!("{:02x}", b)).collect::<String>()
        } else {
            let s = String::from_utf8_lossy(&key_bytes).trim().to_string();
            if s.len() == 64 && s.chars().all(|c| c.is_ascii_hexdigit()) {
                s
            } else {
                key_bytes.iter().map(|b| format!("{:02x}", b)).collect::<String>()
            }
        };
        hex_key
    } else {
        String::new()
    };

    let dest_path = Path::new(&out_dir).join("key_parts.rs");
    fs::write(dest_path, file_content).unwrap();

    let config = slint_build::CompilerConfiguration::new()
        .embed_resources(slint_build::EmbedResourcesKind::EmbedForSoftwareRenderer);
    slint_build::compile_with_config("ui/app.slint", config).unwrap();

    sync_esp_idf_sys_files(Path::new(&env::var("CARGO_MANIFEST_DIR").unwrap()), &out_dir);

    embuild::espidf::sysenv::output();
}

fn sync_esp_idf_sys_files(manifest_dir: &Path, out_dir: &str) {
    let partitions_file = manifest_dir.join("partitions.csv");
    let signing_key_file = manifest_dir.join("secure_boot_signing_key.pem");

    println!("cargo:rerun-if-changed={}", partitions_file.display());
    if signing_key_file.exists() {
        println!("cargo:rerun-if-changed={}", signing_key_file.display());
    }

    let partitions_bytes = if partitions_file.exists() {
        fs::read(&partitions_file).ok()
    } else {
        None
    };

    let signing_key_bytes = if signing_key_file.exists() {
        fs::read(&signing_key_file).ok()
    } else {
        None
    };

    // Locate target build directory: OUT_DIR is target/xtensa-esp32s3-espidf/<profile>/build/tartarus-firmware-<hash>/out
    if let Some(build_dir) = Path::new(out_dir).ancestors().nth(2) {
        if let Ok(entries) = fs::read_dir(build_dir) {
            for entry in entries.flatten() {
                let name = entry.file_name();
                let name_str = name.to_string_lossy();
                if name_str.starts_with("esp-idf-sys-") {
                    let out_path = entry.path().join("out");
                    if out_path.is_dir() {
                        if let Some(ref bytes) = partitions_bytes {
                            let dest = out_path.join("partitions.csv");
                            let should_write = match fs::read(&dest) {
                                Ok(ref existing) => existing != bytes,
                                Err(_) => true,
                            };
                            if should_write {
                                let _ = fs::write(&dest, bytes);
                            }
                        }
                        if let Some(ref bytes) = signing_key_bytes {
                            let dest = out_path.join("secure_boot_signing_key.pem");
                            let should_write = match fs::read(&dest) {
                                Ok(ref existing) => existing != bytes,
                                Err(_) => true,
                            };
                            if should_write {
                                let _ = fs::write(&dest, bytes);
                            }
                        }
                    }
                }
            }
        }
    }
}
