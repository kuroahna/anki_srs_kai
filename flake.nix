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
          p: p.rust-bin.stable.latest.default.override { targets = [ targetSystem ]; }
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
            nativeCheckInputs = with pkgs; [ nodePackages_latest.nodejs ];

            postInstall = ''
              cargo run --release --package xtask --
            '';
          }
        );

        androidPkgs = import nixpkgs {
          inherit system;
          config = {
            android_sdk.accept_license = true;
            allowUnfree = true;
          };
        };

        buildToolsVersion = "34.0.0";
        platformVersion = "34";
        systemImageType = "google_apis";
        abiVersion = "x86_64";
        androidComposition = androidPkgs.androidenv.composeAndroidPackages {
          buildToolsVersions = [ buildToolsVersion ];
          platformToolsVersion = "34.0.5";
          platformVersions = [ platformVersion ];
          emulatorVersion = "35.2.5";
          includeEmulator = true;
          includeSources = false;
          includeSystemImages = true;
          systemImageTypes = [ systemImageType ];
          abiVersions = [ abiVersion ];
          includeNDK = false;
          useGoogleAPIs = false;
          useGoogleTVAddOns = false;
          includeExtras = [ ];
        };
        androidSdk = androidComposition.androidsdk;

        ankidroid = pkgs.stdenv.mkDerivation (finalAttrs: {
          pname = "AnkiDroid";
          version = "v2.19.1";
          strictDeps = true;

          ankiDroidSource = pkgs.fetchFromGitHub {
            owner = "ankidroid";
            repo = "Anki-Android";
            rev = "${finalAttrs.version}";
            hash = "sha256-CHU2e4eRwTtTC6x61WnIwfvBvImO5jnAD8/hVC5LdWg=";
            name = finalAttrs.pname;
          };
          localDirectory = pkgs.lib.fileset.toSource {
            root = ./.;
            fileset = pkgs.lib.fileset.unions [ ./AnkiDroid ./addon/update_custom_data.sql ];
          };
          srcs = [
            finalAttrs.ankiDroidSource
            finalAttrs.localDirectory
          ];
          sourceRoot = finalAttrs.pname;

          override_anki_srs_kai = anki_srs_kai.overrideAttrs (oldAttrs: {
            INCLUDE_SCHEDULER_HEADER = "false";
          });
          nativeBuildInputs =
            (with pkgs; [
              git
              gradle
              temurin-bin
              keepBuildTree
            ])
            ++ [
              androidSdk
              finalAttrs.override_anki_srs_kai
            ];

          mitmCache = pkgs.gradle.fetchDeps {
            pkg = ankidroid;
            data = ./deps.json;
          };

          ANDROID_HOME = "${androidSdk}/libexec/android-sdk";

          gradleFlags = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${finalAttrs.ANDROID_HOME}/build-tools/${buildToolsVersion}/aapt2";

          requiredSystemFeatures = [ "kvm" ];
          deviceName = "device";
          port = "5554";

          postUnpack = ''
            cp -r source/AnkiDroid ${finalAttrs.pname}
            cp source/addon/update_custom_data.sql ${finalAttrs.pname}/AnkiDroid/src/androidTest/assets
            cp ${finalAttrs.override_anki_srs_kai}/dist/anki_srs_kai.js ${finalAttrs.pname}/AnkiDroid/src/androidTest/assets
          '';

          # Reference
          # https://github.com/NixOS/nixpkgs/blob/a551cfdc3e3381211ff060093a9977c6d3935032/pkgs/development/mobile/androidenv/emulate-app.nix
          postConfigure = ''
            export LOGS_DIR=$(mktemp --directory --tmpdir logs-XXXX)
            export ANDROID_USER_HOME=$(mktemp --directory --tmpdir android-user-home-XXXX)
            export ANDROID_AVD_HOME=$ANDROID_USER_HOME/avd
            export ANDROID_SDK_ROOT=$ANDROID_HOME
            export HOME=$ANDROID_USER_HOME
            export ANDROID_SERIAL="emulator-${finalAttrs.port}"
            ${androidSdk}/bin/avdmanager create avd --force --name ${finalAttrs.deviceName} --package "system-images;android-${platformVersion};${systemImageType};${abiVersion}" --path $ANDROID_AVD_HOME/${finalAttrs.deviceName}.avd < <(yes "")
            echo "hw.gpu.enabled=yes" >> $ANDROID_AVD_HOME/${finalAttrs.deviceName}.avd/config.ini
            $ANDROID_SDK_ROOT/emulator/emulator -avd ${finalAttrs.deviceName} -no-boot-anim -port ${finalAttrs.port} -no-window &
            ${androidSdk}/libexec/android-sdk/platform-tools/adb -s emulator-${finalAttrs.port} wait-for-device
            ${androidSdk}/libexec/android-sdk/platform-tools/adb logcat '*:D' >> $LOGS_DIR/adb-log.txt &
            ${androidSdk}/libexec/android-sdk/platform-tools/adb emu screenrecord start --time-limit 1800 $LOGS_DIR/video.webm
          '';

          # Only capture the dependencies required to run our tests when
          # initializing or updating deps.json
          gradleUpdateTask = "AnkiDroid:connectedPlayDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.package=com.ichi2.anki.ankisrskai";
          gradleBuildTask = finalAttrs.gradleUpdateTask;

          installPhase = ''
            cp $LOGS_DIR/* $out
          '';
        });
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

          format = craneLib.cargoFmt { inherit src; };

          toml_format = craneLib.taploFmt { src = pkgs.lib.sources.sourceFilesBySuffices src [ ".toml" ]; };

          audit = craneLib.cargoAudit { inherit src advisory-db; };

          deny = craneLib.cargoDeny { inherit src; };
        };
        devShells.default = craneLib.devShell {
          checks = self.checks.${system};

          packages = with pkgs; [ rust-analyzer ];

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

        devShells.ankidroid = pkgs.mkShell { packages = with androidPkgs; [ android-studio ]; };

        # Initialize or update deps.json
        # nix build .#ankidroid.mitmCache.updateScript
        # BWRAP_FLAGS="--dev-bind /dev/kvm /dev/kvm" ./result
        #
        # Then run tests with
        # nix build .#ankidroid
        packages.ankidroid = ankidroid;

        devShells.addon = pkgs.mkShell { packages = with pkgs; [ black ]; };
      }
    );
}
