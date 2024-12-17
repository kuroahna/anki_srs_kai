use core::panic;
use std::io::Write;
use std::path::{Path, PathBuf};
use std::process::Command;

mod javascript;

fn main() {
    println!("Building");
    let out_dir = std::env::var("out").expect("env variable `out` should be set by `nix build`");
    let pname = std::env::var("pname").expect("env variable `pname` should be set by `nix build`");
    let wasm_artifact = get_wasm_artifact(&out_dir, &pname);
    println!("Found wasm artifact `{}`", wasm_artifact.display());

    println!("Running wasm-bindgen");
    let pkg_dir = create_directory(out_dir.as_str(), "pkg");
    let (wasm_bg_artifact, javascript_artifact) = wasm_bindgen(&pkg_dir, &wasm_artifact);
    println!(
        "Built artifacts `{}` and `{}`",
        wasm_bg_artifact.display(),
        javascript_artifact.display()
    );

    println!("Running wasm-opt");
    let wasm_artifact_optimized = wasm_opt(pkg_dir, wasm_bg_artifact);
    println!("Built artifact `{}`", wasm_artifact_optimized.display());

    println!("Building scheduler script");
    let include_header = std::env::var("INCLUDE_SCHEDULER_HEADER")
        .map(|value| value.eq_ignore_ascii_case("true"))
        .unwrap_or(true);
    let dist_dir = create_directory(out_dir.as_str(), "dist");
    let scheduler_script = create_scheduler_script(
        dist_dir,
        wasm_artifact_optimized,
        javascript_artifact,
        include_header,
    );
    println!("Built artifact `{}`", scheduler_script.display());
}

fn get_wasm_artifact<P: AsRef<Path>>(out_dir: P, package_name: &str) -> PathBuf {
    let mut wasm_artifact = out_dir.as_ref().to_path_buf();
    wasm_artifact.push("lib");
    wasm_artifact.push(package_name);
    wasm_artifact.set_extension("wasm");

    assert!(
        validate_file_exists(&wasm_artifact),
        "`cargo build` should produce the artifact `{}`",
        wasm_artifact.display()
    );

    wasm_artifact
}

fn validate_file_exists<P: AsRef<Path>>(path: P) -> bool {
    path.as_ref().try_exists().unwrap_or_else(|e| {
        panic!(
            "cannot check the existence of the file `{}`: {e}",
            path.as_ref().display()
        )
    })
}

fn create_directory<P: AsRef<Path>>(out_dir: P, path: P) -> PathBuf {
    let mut pkg_dir = out_dir.as_ref().to_path_buf();
    pkg_dir.push(path.as_ref());
    std::fs::create_dir_all(&pkg_dir)
        .unwrap_or_else(|e| panic!("failed to create directory `{}`: {}", pkg_dir.display(), e));
    pkg_dir
}

fn wasm_bindgen<P: AsRef<Path>>(out_dir: P, wasm_artifact: P) -> (PathBuf, PathBuf) {
    let output = Command::new("wasm-bindgen")
        .arg("--out-dir")
        .arg(out_dir.as_ref())
        .arg("--target")
        .arg("no-modules")
        .arg("--no-typescript")
        .arg(wasm_artifact.as_ref())
        .output()
        .expect("failed to execute `wasm-bindgen`");
    std::io::stdout()
        .write_all(&output.stdout)
        .expect("failed to write the output of `wasm-bindgen` to stdout");

    let package_name = wasm_artifact.as_ref().file_stem().unwrap_or_else(|| {
        panic!(
            "`{}` should have a file stem",
            wasm_artifact.as_ref().display()
        )
    });
    let mut wasm_bg_artifact_name = package_name.to_os_string();
    wasm_bg_artifact_name.push("_bg");

    let mut wasm_bg_artifact = out_dir.as_ref().to_path_buf();
    wasm_bg_artifact.push(wasm_bg_artifact_name);
    wasm_bg_artifact.set_extension("wasm");
    assert!(
        validate_file_exists(&wasm_bg_artifact),
        "`wasm-bindgen` should produce the artifact `{}`",
        wasm_bg_artifact.display()
    );

    let mut javascript_artifact = out_dir.as_ref().to_path_buf();
    javascript_artifact.push(package_name);
    javascript_artifact.set_extension("js");
    assert!(
        validate_file_exists(&javascript_artifact),
        "`wasm-bindgen` should produce the artifact `{}`",
        javascript_artifact.display()
    );

    (wasm_bg_artifact, javascript_artifact)
}

fn wasm_opt<P: AsRef<Path>>(out_dir: P, wasm_artifact: P) -> PathBuf {
    let wasm_artifact_name = wasm_artifact.as_ref().file_stem().unwrap_or_else(|| {
        panic!(
            "`{}` should have a file stem",
            wasm_artifact.as_ref().display()
        )
    });

    let mut wasm_artifact_optimized_name = wasm_artifact_name.to_os_string();
    wasm_artifact_optimized_name.push("_optimized");

    let mut wasm_artifact_optimized = out_dir.as_ref().to_path_buf();
    wasm_artifact_optimized.push(wasm_artifact_optimized_name);
    wasm_artifact_optimized.set_extension("wasm");

    let output = Command::new("wasm-opt")
        .arg("-O4")
        .arg("--shrink-level")
        .arg("2")
        .arg("--output")
        .arg(&wasm_artifact_optimized)
        .arg(wasm_artifact.as_ref())
        .output()
        .expect("failed to execute `wasm-opt`");
    std::io::stdout()
        .write_all(&output.stdout)
        .expect("failed to write the output of `wasm-opt` to stdout");
    assert!(
        validate_file_exists(&wasm_artifact_optimized),
        "`wasm-opt` should produce the artifact `{}`",
        wasm_artifact_optimized.display()
    );

    wasm_artifact_optimized
}

fn create_scheduler_script<P: AsRef<Path>>(
    out_dir: P,
    wasm_artifact: P,
    javascript_artifact: P,
    include_header: bool,
) -> PathBuf {
    let javascript_artifact_content = std::fs::read_to_string(javascript_artifact.as_ref())
        .unwrap_or_else(|e| {
            panic!(
                "failed to read `{}`: {}",
                javascript_artifact.as_ref().display(),
                e
            )
        });

    let javascript_artifact_name = javascript_artifact.as_ref().file_stem().unwrap_or_else(|| {
        panic!(
            "`{}` should have a file stem",
            javascript_artifact.as_ref().display()
        )
    });

    let mut scheduler_script = out_dir.as_ref().to_path_buf();
    scheduler_script.push(javascript_artifact_name);
    scheduler_script.set_extension("js");

    let bytes = std::fs::read(wasm_artifact.as_ref()).unwrap_or_else(|e| {
        panic!(
            "failed to read file `{}`: {e}",
            wasm_artifact.as_ref().display()
        )
    });
    let wasm_bytes = bytes
        .iter()
        .map(|byte| byte.to_string())
        .collect::<Vec<String>>()
        .join(",");
    std::fs::write(
        &scheduler_script,
        format!(
            "{}\n\
             {}\n\
             const wasmBytes = new Uint8Array([{}]);\n\
             {}",
            if include_header {
                javascript::HEADER
            } else {
                ""
            },
            javascript_artifact_content,
            wasm_bytes,
            javascript::FOOTER
        ),
    )
    .unwrap_or_else(|e| {
        panic!(
            "failed to write to the file `{}`: {e}",
            scheduler_script.display()
        )
    });

    scheduler_script
}
