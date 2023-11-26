PKG_NAME = $(shell grep "name" Cargo.toml | cut -d " " -f 3 | tr -d '"')
PKG_VERSION = $(shell grep "version" Cargo.toml | cut -d " " -f 3 | tr -d '"')

.PHONY: clean
clean:
	cargo clean
	rm -rf pkg dist

.PHONY: release
release:
	wasm-pack build --release --no-typescript --no-pack --target no-modules
	mkdir -p dist
	wasm-opt -O4 --shrink-level 2 --output "pkg/${PKG_NAME}_bg_optimized.wasm" "pkg/${PKG_NAME}_bg.wasm"
	./release.sh "pkg/${PKG_NAME}_bg_optimized.wasm" "pkg/${PKG_NAME}.js" "dist/${PKG_NAME}.js"
