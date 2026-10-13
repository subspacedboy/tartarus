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

    embuild::espidf::sysenv::output();
}
