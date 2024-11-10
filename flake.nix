{
  description = "Anki SRS Kai";

  inputs = {
    flake-utils.url = "github:numtide/flake-utils";
    nixpkgs.url = "github:nixos/nixpkgs/nixos-unstable";
    crane.url = "github:ipetkov/crane";
    rust-overlay = {
      url = "github:oxalica/rust-overlay";
      inputs.nixpkgs.follows = "nixpkgs";
    };
    advisory-db = {
      url = "github:rustsec/advisory-db";
      flake = false;
    };
  };

  outputs =
    {
      self,
      flake-utils,
      nixpkgs,
      crane,
      rust-overlay,
      advisory-db,
      ...
    }:
    flake-utils.lib.eachDefaultSystem (
      system:
      let
        targetSystem = "wasm32-unknown-unknown";

        pkgs = import nixpkgs {
          inherit system;
          overlays = [ (import rust-overlay) ];
        };

        craneLib = (crane.mkLib pkgs).overrideToolchain (
          p:
          p.rust-bin.stable.latest.default.override {
            targets = [ targetSystem ];
          }
        );
        src = craneLib.cleanCargoSource ./.;

        commonArgs = {
          inherit src;
          strictDeps = true;
        };

        cargoArtifacts = craneLib.buildDepsOnly (
          commonArgs
          // {
            buildPhaseCargoCommand = ''
              cargo check --profile release --frozen --all-targets
              cargo build --profile release --frozen --workspace --exclude wasm
              cargo build --profile release --frozen --target ${targetSystem} --workspace --exclude xtask
            '';
            checkPhaseCargoCommand = ''
              cargo test --profile release --frozen --workspace --exclude wasm --no-run
              cargo test --profile release --frozen --target ${targetSystem} --workspace --exclude xtask --no-run
            '';
          }
        );

        anki_srs_kai = craneLib.buildPackage (
          commonArgs
          // {
            inherit cargoArtifacts;

            cargoExtraArgs = "--frozen --target ${targetSystem} --workspace --exclude xtask";

            nativeBuildInputs = with pkgs; [
              binaryen
              wasm-bindgen-cli
            ];

            CARGO_TARGET_WASM32_UNKNOWN_UNKNOWN_RUNNER = "wasm-bindgen-test-runner";
            nativeCheckInputs = with pkgs; [
              nodePackages_latest.nodejs
            ];

            postInstall = ''
              cargo run --release --package xtask --
            '';
          }
        );
      in
      {
        formatter = pkgs.nixfmt-rfc-style;
        packages.default = anki_srs_kai;
        checks = {
          inherit anki_srs_kai;

          clippy = craneLib.cargoClippy (
            commonArgs
            // {
              inherit cargoArtifacts;
              cargoClippyExtraArgs = "--all-targets -- --deny warnings";
            }
          );

          format = craneLib.cargoFmt {
            inherit src;
          };

          toml_format = craneLib.taploFmt {
            src = pkgs.lib.sources.sourceFilesBySuffices src [ ".toml" ];
          };

          audit = craneLib.cargoAudit {
            inherit src advisory-db;
          };

          deny = craneLib.cargoDeny {
            inherit src;
          };
        };
        devShells.default = craneLib.devShell {
          checks = self.checks.${system};

          packages = with pkgs; [
            rust-analyzer
          ];

          # fixes: the cargo feature `public-dependency` requires a nightly
          # version of Cargo, but this is the `stable` channel
          #
          # This enables unstable features with the stable compiler
          # Remove once this is fixed in stable
          #
          # https://github.com/rust-lang/rust/issues/112391
          # https://github.com/rust-lang/rust-analyzer/issues/15046
          RUSTC_BOOTSTRAP = 1;
        };
      }
    );
}
