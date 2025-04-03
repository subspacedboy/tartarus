use std::path::Path;
use std::{env, fs};

fn main() {
    let out_dir = env::var("OUT_DIR").unwrap();
    let file_path = "private.key";
    let file_exists = Path::new(file_path).exists();

    let file_content = if file_exists {
        // Make the key available in the output directory if it's there.
        let key_path = Path::new(&out_dir).join("private.key");
        fs::copy(file_path, key_path).unwrap();

        format!("Some(include_bytes!(\"{}\"))", file_path)
    } else {
        "None".to_string()
    };

    let dest_path = Path::new(&out_dir).join("key_parts.rs");
    fs::write(dest_path, file_content).unwrap();

    embuild::espidf::sysenv::output();
}
