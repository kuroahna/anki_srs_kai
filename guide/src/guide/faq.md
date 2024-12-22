# FAQ

## What platforms and versions of Anki are supported?

Anki SRS Kai is supported on the following platforms

* Anki Desktop (Windows, Mac, Linux): 24.11+
* AnkiDroid (Android): 2.20.0+
* AnkiMobile (iOS): 24.11+

**IMPORTANT:** AnkiWeb is **not** supported. There is no technical limitation
as [WebAssembly is supported](https://caniuse.com/wasm) by every major browser.
However, [support for the custom scheduler must be enabled by Anki
Web](https://faqs.ankiweb.net/the-2021-scheduler.html#add-ons-and-custom-scheduling)
itself for the custom scheduler to work.

> Because this is implemented in JavaScript, it is not limited to the computer
> version. AnkiMobile and AnkiDroid both support it as well, and **AnkiWeb may
> also support it in the future**. This will allow advanced users to make
> adjustments to the standard scheduling behaviour, that apply on all platforms.

## Can I enable FSRS and Anki SRS Kai at the same time?

While the custom scheduler (Anki SRS Kai) will continue to work even if FSRS is
enabled, it is **highly recommended** to turn FSRS off to avoid any potential
unexpected behaviours not covered in our integration test suite.

## What is the large binary blob called `wasmBytes` in the customer scheduler and is it safe?

The large binary blob called `wasmBytes` in the custom scheduler is the code
that runs the Anki SRS Kai scheduler. It is compiled from Rust to
[WebAssembly](https://webassembly.org/) and is
[safe](https://webassembly.org/docs/security/) by design. Each WebAssembly
module is ran in a memory-safe, sandboxed environment, separated from the host
runtime. The source code is [publicly
available](https://github.com/kuroahna/anki_srs_kai).
