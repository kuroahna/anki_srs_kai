use serde::Deserialize;

// These structs have mainly been constructed by the protobuf messages defined
// in Anki: https://github.com/ankitects/anki/blob/23.10.1/proto/anki/scheduler.proto#L68
//
// However, to modify the custom data, we actually need to modify the customData
// that gets passed in to the custom scheduler separately, rather than on the
// SchedulingState itself
// See: https://github.com/ankitects/anki/blob/23.10.1/ts/reviewer/answering.ts#L63
//
// Unfortunately, wasm-bindgen is not yet supported for prost, so we have to
// manually write the structs ourselves
// See: https://github.com/tokio-rs/prost/pull/167
#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct FsrsMemoryState {
    // Anki encodes this as f32 but since all numbers are f64 in JavaScript, we
    // use f64 instead to avoid loss of precision when we cross the Rust and
    // JavaScript boundary
    pub stability: f64,
    pub difficulty: f64,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct NewState {
    pub position: u32,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LearnState {
    pub remaining_steps: u32,
    pub scheduled_secs: u32,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub memory_state: Option<FsrsMemoryState>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ReviewState {
    pub scheduled_days: u32,
    pub elapsed_days: u32,
    // Anki encodes this as f32 but since all numbers are f64 in JavaScript, we
    // use f64 instead to avoid loss of precision when we cross the Rust and
    // JavaScript boundary
    pub ease_factor: f64,
    pub lapses: u32,
    pub leeched: bool,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub memory_state: Option<FsrsMemoryState>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct RelearnState {
    pub learning: LearnState,
    pub review: ReviewState,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub enum NormalState {
    New(NewState),
    Learning(LearnState),
    Review(ReviewState),
    Relearning(RelearnState),
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct PreviewState {
    pub scheduled_secs: u32,
    pub finished: bool,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ReschedulingFilterState {
    pub original_state: NormalState,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub enum FilteredState {
    Preview(PreviewState),
    Rescheduling(ReschedulingFilterState),
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub enum SchedulingStateKind {
    Normal(NormalState),
    Filtered(FilteredState),
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SchedulingState {
    #[serde(flatten)]
    pub kind: SchedulingStateKind,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub custom_data: Option<String>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SchedulingStates {
    pub current: SchedulingState,
    pub again: SchedulingState,
    pub hard: SchedulingState,
    pub good: SchedulingState,
    pub easy: SchedulingState,
}

pub mod javascript {
    use wasm_bindgen::prelude::wasm_bindgen;

    #[wasm_bindgen]
    extern "C" {
        pub type ReviewState;

        // Anki encodes this as f32 but since all numbers are f64 in JavaScript, we
        // use f64 instead to avoid loss of precision when we cross the Rust and
        // JavaScript boundary
        #[wasm_bindgen(method, setter, js_name = easeFactor)]
        pub fn set_ease_factor(this: &ReviewState, ease_factor: f64);
    }

    #[wasm_bindgen]
    extern "C" {
        pub type NormalState;

        #[wasm_bindgen(method, getter, js_name = review)]
        pub fn review(this: &NormalState) -> ReviewState;
    }

    #[wasm_bindgen]
    extern "C" {
        pub type ReschedulingFilterState;

        #[wasm_bindgen(method, getter, js_name = originalState)]
        pub fn original_state(this: &ReschedulingFilterState) -> NormalState;
    }

    #[wasm_bindgen]
    extern "C" {
        pub type FilteredState;

        #[wasm_bindgen(method, getter, js_name = rescheduling)]
        pub fn rescheduling(this: &FilteredState) -> ReschedulingFilterState;
    }

    #[wasm_bindgen]
    extern "C" {
        pub type SchedulingState;

        #[wasm_bindgen(method, getter, js_name = normal)]
        pub fn normal(this: &SchedulingState) -> NormalState;

        #[wasm_bindgen(method, getter, js_name = filtered)]
        pub fn filtered(this: &SchedulingState) -> FilteredState;
    }

    #[wasm_bindgen]
    extern "C" {
        pub type SchedulingStates;
        #[wasm_bindgen(js_name = states)]
        pub static STATES: SchedulingStates;

        #[wasm_bindgen(method, getter, js_name = hard)]
        pub fn hard(this: &SchedulingStates) -> SchedulingState;

        #[wasm_bindgen(method, getter, js_name = good)]
        pub fn good(this: &SchedulingStates) -> SchedulingState;

        #[wasm_bindgen(method, getter, js_name = easy)]
        pub fn easy(this: &SchedulingStates) -> SchedulingState;
    }

    #[wasm_bindgen]
    extern "C" {
        pub type SchedulingContext;

        #[wasm_bindgen(js_name = ctx)]
        pub static CONTEXT: SchedulingContext;

        #[wasm_bindgen(method, getter, js_name = deckName)]
        pub fn deck_name(this: &SchedulingContext) -> String;
        // The actual fuzz seed that Anki uses is slightly different from the one
        // passed into us in the SchedulingContext
        //
        // They call
        // (card.id.0 as u64).wrapping_add(card.reps as u64)
        // https://github.com/ankitects/anki/blob/23.12beta1/rslib/src/scheduler/answering/mod.rs#L484
        //
        // But we actually get
        // (self.id.0 as u64).rotate_left(8).wrapping_add(self.reps as u64)
        // https://github.com/ankitects/anki/blob/23.12beta1/rslib/src/card/mod.rs#L256
        #[wasm_bindgen(method, getter, js_name = seed)]
        pub fn seed(this: &SchedulingContext) -> u64;
    }

    #[wasm_bindgen]
    extern "C" {
        pub type CustomDataStates;
        #[wasm_bindgen(js_name = customData)]
        pub static CUSTOM_DATA: CustomDataStates;

        #[wasm_bindgen(method, getter, js_name = again)]
        pub fn again(this: &CustomDataStates) -> CustomDataState;

        #[wasm_bindgen(method, getter, js_name = hard)]
        pub fn hard(this: &CustomDataStates) -> CustomDataState;

        #[wasm_bindgen(method, getter, js_name = good)]
        pub fn good(this: &CustomDataStates) -> CustomDataState;

        #[wasm_bindgen(method, getter, js_name = easy)]
        pub fn easy(this: &CustomDataStates) -> CustomDataState;
    }

    #[wasm_bindgen]
    extern "C" {
        pub type CustomDataState;

        // Custom data key names must be under 8 bytes (8 ASCII characters), so
        // we cannot provide more descriptive names. Also, the serialized JSON
        // must be under 100 bytes.
        // See: https://github.com/ankitects/anki/blob/23.10.1/rslib/src/storage/card/data.rs#L119-L133
        #[wasm_bindgen(method, getter, js_name = c)]
        pub fn c(this: &CustomDataState) -> Option<u32>;

        #[wasm_bindgen(method, setter, js_name = c)]
        pub fn set_c(this: &CustomDataState, c: Option<u32>);
    }
}
