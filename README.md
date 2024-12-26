# :star: Anki SRS Kai

[![](https://img.shields.io/static/v1?label=Sponsor&message=%E2%9D%A4&logo=GitHub&color=%23fe8e86)](https://github.com/sponsors/kuroahna)

Anki SRS Kai (暗記ＳＲＳ改) is a custom scheduler written in :crab: Rust
:rocket: and compiled to :package: WebAssembly for
[Anki](https://apps.ankiweb.net/). It aims to fix the issues with the default
Anki SM-2 algorithm while keeping the same overall behaviour. In particular,

1. :chart_with_downwards_trend: Ease Hell.
2. :zap: Short intervals for new cards.
3. :arrows_counterclockwise: Long intervals for mature cards.

## :books: User guide

Visit the [user guide](https://kuroahna.github.io/anki_srs_kai) for instructions on how
to install and configure Anki SRS Kai.

## :pick: Build

If you'd like to build the custom scheduler yourself

1. Install the [nix package manager](https://zero-to-nix.com/start/install/).
2. Run `nix build`
3. The custom scheduler is available under `result/dist`
