# Introduction

Anki SRS Kai (暗記ＳＲＳ改) is a custom scheduler written in 🦀 Rust 🚀 and
compiled to 📦 WebAssembly for [Anki](https://apps.ankiweb.net/). It aims to fix
the issues with the default Anki SM-2 algorithm while keeping the same overall
behaviour. In particular,

1. 📉 Ease Hell.
2. ⚡ Short intervals for new cards.
3. 🔄 Long intervals for mature cards.
