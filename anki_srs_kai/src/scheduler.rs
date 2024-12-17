use rand::rngs::StdRng;
use rand::{Rng, SeedableRng};
use serde::Deserialize;
use wasm_bindgen::JsValue;

#[derive(Debug, PartialEq)]
pub struct NextState {
    pub hard_interval: Option<u32>,
    pub good_interval: Option<u32>,
    pub easy_interval: Option<u32>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Scheduler {
    enable_fuzz: bool,
    maximum_interval: u32,
    interval_modifier: f64,
    #[serde(with = "serde_wasm_bindgen::preserve")]
    calculate_hard_multiplier: js_sys::Function,
    #[serde(with = "serde_wasm_bindgen::preserve")]
    calculate_good_multiplier: js_sys::Function,
    #[serde(with = "serde_wasm_bindgen::preserve")]
    calculate_easy_multiplier: js_sys::Function,
}

impl Scheduler {
    pub fn next_states(
        &self,
        fuzz_seed: u64,
        scheduled_days: u32,
        elapsed_days: u32,
        ease_factor: f64,
    ) -> NextState {
        let hard_multiplier = self.calculate_hard_multiplier(ease_factor, scheduled_days);
        let good_multiplier = self.calculate_good_multiplier(ease_factor, scheduled_days);
        let easy_multiplier = self.calculate_easy_multiplier(ease_factor, scheduled_days);
        let scheduler = InternalScheduler::new(
            self.enable_fuzz,
            self.maximum_interval,
            self.interval_modifier,
            hard_multiplier,
            good_multiplier,
            easy_multiplier,
            fuzz_seed,
            scheduled_days,
            elapsed_days,
        );
        scheduler.next_states()
    }

    fn calculate_hard_multiplier(&self, current_ease_factor: f64, current_interval: u32) -> f64 {
        self.calculate_hard_multiplier
            .call2(
                &JsValue::NULL,
                &JsValue::from(current_ease_factor),
                &JsValue::from(current_interval),
            )
            .unwrap()
            .as_f64()
            .expect("The calculateHardMultiplier JavaScript function should return a number")
    }

    fn calculate_good_multiplier(&self, current_ease_factor: f64, current_interval: u32) -> f64 {
        self.calculate_good_multiplier
            .call2(
                &JsValue::NULL,
                &JsValue::from(current_ease_factor),
                &JsValue::from(current_interval),
            )
            .unwrap()
            .as_f64()
            .expect("The calculateGoodMultiplier JavaScript function should return a number")
    }

    fn calculate_easy_multiplier(&self, current_ease_factor: f64, current_interval: u32) -> f64 {
        self.calculate_easy_multiplier
            .call2(
                &JsValue::NULL,
                &JsValue::from(current_ease_factor),
                &JsValue::from(current_interval),
            )
            .unwrap()
            .as_f64()
            .expect("The calculateEasyMultiplier JavaScript function should return a number")
    }
}

// The code below has been taken from Anki's code base and minimally modified so
// that if Anki changes its algorithm in the future, it's easier to update ours
// and re-apply the changes to the algorithm
//
// Note, any f32 data types have been converted to f64 to avoid loss of
// precision when we cross the Rust and JavaScript boundary since all numbers
// are f64 in JavaScript
//
// See: https://github.com/ankitects/anki/blob/23.12beta1/rslib/src/scheduler/states/review.rs

struct InternalScheduler {
    fuzz_factor: Option<f64>,
    maximum_interval: u32,
    interval_modifier: f64,
    hard_multiplier: f64,
    good_multiplier: f64,
    easy_multiplier: f64,
    scheduled_days: u32,
    elapsed_days: u32,
}

impl InternalScheduler {
    #[allow(clippy::too_many_arguments)]
    fn new(
        enable_fuzz: bool,
        maximum_interval: u32,
        interval_modifier: f64,
        hard_multiplier: f64,
        good_multiplier: f64,
        easy_multiplier: f64,
        fuzz_seed: u64,
        scheduled_days: u32,
        elapsed_days: u32,
    ) -> Self {
        let fuzz_factor = if enable_fuzz {
            get_fuzz_factor(Some(fuzz_seed))
        } else {
            get_fuzz_factor(None)
        };
        Self {
            fuzz_factor,
            maximum_interval,
            interval_modifier,
            hard_multiplier,
            good_multiplier,
            easy_multiplier,
            scheduled_days,
            elapsed_days,
        }
    }

    fn next_states(&self) -> NextState {
        match self.passing_review_intervals() {
            None => NextState {
                hard_interval: None,
                good_interval: None,
                easy_interval: None,
            },
            Some((hard_interval, good_interval, easy_interval)) => NextState {
                hard_interval: if self.hard_multiplier == 0.0 {
                    None
                } else {
                    Some(hard_interval)
                },
                good_interval: if self.good_multiplier == 0.0 {
                    None
                } else {
                    Some(good_interval)
                },
                easy_interval: if self.easy_multiplier == 0.0 {
                    None
                } else {
                    Some(easy_interval)
                },
            },
        }
    }

    fn days_late(&self) -> i32 {
        self.elapsed_days as i32 - self.scheduled_days as i32
    }

    fn passing_review_intervals(&self) -> Option<(u32, u32, u32)> {
        if self.days_late() < 0 {
            // Use original Anki algorithm for early reviews for now because it
            // might not make sense to use the same multipliers as non-early
            // reviews
            //
            // self.passing_early_review_intervals()
            None
        } else {
            Some(self.passing_nonearly_review_intervals())
        }
    }

    fn passing_nonearly_review_intervals(&self) -> (u32, u32, u32) {
        let current_interval = self.scheduled_days as f64;
        let days_late = self.days_late().max(0) as f64;

        // hard
        let hard_factor = self.hard_multiplier;
        let hard_minimum = if hard_factor <= 1.0 {
            0
        } else {
            self.scheduled_days + 1
        };
        let hard_interval =
            self.constrain_passing_interval(current_interval * hard_factor, hard_minimum, true);
        // good
        let good_minimum = if hard_factor <= 1.0 {
            self.scheduled_days + 1
        } else {
            hard_interval + 1
        };
        let good_interval = self.constrain_passing_interval(
            // Anki original implementation
            // (current_interval + days_late / 2.0) * self.ease_factor,
            (current_interval + days_late / 2.0) * self.good_multiplier,
            good_minimum,
            true,
        );
        // easy
        let easy_interval = self.constrain_passing_interval(
            // Anki original implementation
            // (current_interval + days_late) * self.ease_factor * self.easy_multiplier,
            (current_interval + days_late) * self.easy_multiplier,
            good_interval + 1,
            true,
        );

        (hard_interval, good_interval, easy_interval)
    }

    /// Transform the provided hard/good/easy interval.
    /// - Apply configured interval multiplier if not FSRS.
    /// - Apply fuzz.
    /// - Ensure it is at least `minimum`, and at least 1.
    /// - Ensure it is at or below the configured maximum interval.
    fn constrain_passing_interval(&self, interval: f64, minimum: u32, fuzz: bool) -> u32 {
        let interval = interval * self.interval_modifier;
        let (minimum, maximum) = self.min_and_max_review_intervals(minimum);
        if fuzz {
            self.with_review_fuzz(interval, minimum, maximum)
        } else {
            (interval.round() as u32).clamp(minimum, maximum)
        }
    }

    /// Return the minimum and maximum review intervals.
    /// - `maximum` is `self.maximum_review_interval`, but at least 1.
    /// - `minimum` is as passed, but at least 1, and at most `maximum`.
    fn min_and_max_review_intervals(&self, minimum: u32) -> (u32, u32) {
        let maximum = self.maximum_interval.max(1);
        let minimum = minimum.clamp(1, maximum);
        (minimum, maximum)
    }

    fn with_review_fuzz(&self, interval: f64, minimum: u32, maximum: u32) -> u32 {
        with_review_fuzz(self.fuzz_factor, interval, minimum, maximum)
    }
}

fn with_review_fuzz(fuzz_factor: Option<f64>, interval: f64, minimum: u32, maximum: u32) -> u32 {
    if let Some(fuzz_factor) = fuzz_factor {
        let (lower, upper) = constrained_fuzz_bounds(interval, minimum, maximum);
        (lower as f64 + fuzz_factor * ((1 + upper - lower) as f64)).floor() as u32
    } else {
        (interval.round() as u32).clamp(minimum, maximum)
    }
}

/// Return the bounds of the fuzz range, respecting `minimum` and `maximum`.
/// Ensure the upper bound is larger than the lower bound, if `maximum` allows
/// it and it is larger than 1.
fn constrained_fuzz_bounds(interval: f64, minimum: u32, maximum: u32) -> (u32, u32) {
    let minimum = minimum.min(maximum);
    let (mut lower, mut upper) = fuzz_bounds(interval);

    // minimum <= maximum and lower <= upper are assumed
    // now ensure minimum <= lower <= upper <= maximum
    lower = lower.clamp(minimum, maximum);
    upper = upper.clamp(minimum, maximum);

    if upper == lower && upper > 2 && upper < maximum {
        upper = lower + 1;
    };

    (lower, upper)
}

fn fuzz_bounds(interval: f64) -> (u32, u32) {
    let delta = fuzz_delta(interval);
    (
        (interval - delta).round() as u32,
        (interval + delta).round() as u32,
    )
}

/// Return the amount of fuzz to apply to the interval in both directions.
/// Short intervals do not get fuzzed. All other intervals get fuzzed by 1 day
/// plus the number of its days in each defined fuzz range multiplied with the
/// given factor.
fn fuzz_delta(interval: f64) -> f64 {
    if interval < 2.5 {
        0.0
    } else {
        FUZZ_RANGES.iter().fold(1.0, |delta, range| {
            delta + range.factor * (interval.min(range.end) - range.start).max(0.0)
        })
    }
}

/// Describes a range of days for which a certain amount of fuzz is applied to
/// the new interval.
struct FuzzRange {
    start: f64,
    end: f64,
    factor: f64,
}

static FUZZ_RANGES: [FuzzRange; 3] = [
    FuzzRange {
        start: 2.5,
        end: 7.0,
        factor: 0.15,
    },
    FuzzRange {
        start: 7.0,
        end: 20.0,
        factor: 0.1,
    },
    FuzzRange {
        start: 20.0,
        end: f64::MAX,
        factor: 0.05,
    },
];

/// Return a fuzz factor from the range `0.0..1.0`, using the provided seed.
/// None if seed is None.
fn get_fuzz_factor(seed: Option<u64>) -> Option<f64> {
    seed.map(|s| StdRng::seed_from_u64(s).gen_range(0.0..1.0))
}

#[cfg(test)]
mod tests {
    use crate::scheduler::{InternalScheduler, NextState};
    use wasm_bindgen_test::wasm_bindgen_test;

    #[derive(Default)]
    struct InternalSchedulerBuilder {
        enable_fuzz: Option<bool>,
        maximum_interval: Option<u32>,
        interval_modifier: Option<f64>,
        hard_multiplier: Option<f64>,
        good_multiplier: Option<f64>,
        easy_multiplier: Option<f64>,
        fuzz_seed: Option<u64>,
        scheduled_days: Option<u32>,
        elapsed_days: Option<u32>,
    }

    impl InternalSchedulerBuilder {
        fn enable_fuzz(mut self, enable_fuzz: bool) -> Self {
            self.enable_fuzz = Some(enable_fuzz);
            self
        }

        fn maximum_interval(mut self, maximum_interval: u32) -> Self {
            self.maximum_interval = Some(maximum_interval);
            self
        }

        fn interval_modifier(mut self, interval_modifier: f64) -> Self {
            self.interval_modifier = Some(interval_modifier);
            self
        }

        fn hard_multiplier(mut self, hard_multiplier: f64) -> Self {
            self.hard_multiplier = Some(hard_multiplier);
            self
        }

        fn good_multiplier(mut self, good_multiplier: f64) -> Self {
            self.good_multiplier = Some(good_multiplier);
            self
        }

        fn easy_multiplier(mut self, easy_multiplier: f64) -> Self {
            self.easy_multiplier = Some(easy_multiplier);
            self
        }

        fn fuzz_seed(mut self, fuzz_seed: u64) -> Self {
            self.fuzz_seed = Some(fuzz_seed);
            self
        }

        fn scheduled_days(mut self, scheduled_days: u32) -> Self {
            self.scheduled_days = Some(scheduled_days);
            self
        }

        fn elapsed_days(mut self, elapsed_days: u32) -> Self {
            self.elapsed_days = Some(elapsed_days);
            self
        }

        fn build(&self) -> InternalScheduler {
            InternalScheduler::new(
                self.enable_fuzz
                    .expect("enable_fuzz should be set in the test"),
                self.maximum_interval
                    .expect("maximum_interval should be set in the test"),
                self.interval_modifier
                    .expect("interval_modifier should be set in the test"),
                self.hard_multiplier
                    .expect("hard_multiplier should be set in the test"),
                self.good_multiplier
                    .expect("good_multiplier should be set in the test"),
                self.easy_multiplier
                    .expect("easy_multiplier should be set in the test"),
                self.fuzz_seed.expect("fuzz_seed should be set in the test"),
                self.scheduled_days
                    .expect("scheduled_days should be set in the test"),
                self.elapsed_days
                    .expect("elapsed_days should be set in the test"),
            )
        }
    }

    #[wasm_bindgen_test(unsupported = test)]
    fn default_anki_algorithm_is_used_for_early_reviews() {
        let under_test = InternalSchedulerBuilder::default()
            .enable_fuzz(true)
            .maximum_interval(36500)
            .interval_modifier(1.0)
            .hard_multiplier(2.0)
            .good_multiplier(3.0)
            .easy_multiplier(4.0)
            .fuzz_seed(123)
            .scheduled_days(50)
            .elapsed_days(25)
            .build();

        let result = under_test.next_states();

        assert_eq!(
            result,
            NextState {
                hard_interval: None,
                good_interval: None,
                easy_interval: None
            }
        );
    }

    #[wasm_bindgen_test(unsupported = test)]
    fn disable_fuzz() {
        let under_test = InternalSchedulerBuilder::default()
            .enable_fuzz(false)
            .maximum_interval(36500)
            .interval_modifier(1.0)
            .hard_multiplier(2.0)
            .good_multiplier(3.0)
            .easy_multiplier(4.0)
            .fuzz_seed(123)
            .scheduled_days(50)
            .elapsed_days(50)
            .build();

        let result = under_test.next_states();

        assert_eq!(
            result,
            NextState {
                hard_interval: Some(100),
                good_interval: Some(150),
                easy_interval: Some(200),
            }
        );
    }

    #[wasm_bindgen_test(unsupported = test)]
    fn disable_fuzz_with_overdue_card() {
        let under_test = InternalSchedulerBuilder::default()
            .enable_fuzz(false)
            .maximum_interval(36500)
            .interval_modifier(1.0)
            .hard_multiplier(2.0)
            .good_multiplier(3.0)
            .easy_multiplier(4.0)
            .fuzz_seed(123)
            .scheduled_days(50)
            .elapsed_days(100)
            .build();

        let result = under_test.next_states();

        assert_eq!(
            result,
            NextState {
                hard_interval: Some(100),
                good_interval: Some(225),
                easy_interval: Some(400),
            }
        );
    }

    #[wasm_bindgen_test(unsupported = test)]
    fn enable_fuzz() {
        let under_test = InternalSchedulerBuilder::default()
            .enable_fuzz(true)
            .maximum_interval(36500)
            .interval_modifier(1.0)
            .hard_multiplier(2.0)
            .good_multiplier(3.0)
            .easy_multiplier(4.0)
            .fuzz_seed(123)
            .scheduled_days(50)
            .elapsed_days(50)
            .build();

        let result = under_test.next_states();

        assert_eq!(
            result,
            NextState {
                hard_interval: Some(95),
                good_interval: Some(144),
                easy_interval: Some(192),
            }
        );
    }

    #[wasm_bindgen_test(unsupported = test)]
    fn intervals_do_not_exceed_maximum_interval() {
        let under_test = InternalSchedulerBuilder::default()
            .enable_fuzz(false)
            .maximum_interval(125)
            .interval_modifier(1.0)
            .hard_multiplier(2.0)
            .good_multiplier(3.0)
            .easy_multiplier(4.0)
            .fuzz_seed(123)
            .scheduled_days(50)
            .elapsed_days(50)
            .build();

        let result = under_test.next_states();

        assert_eq!(
            result,
            NextState {
                hard_interval: Some(100),
                good_interval: Some(125),
                easy_interval: Some(125),
            }
        );
    }

    #[wasm_bindgen_test(unsupported = test)]
    fn interval_modifier_affects_all_intervals() {
        let under_test = InternalSchedulerBuilder::default()
            .enable_fuzz(false)
            .maximum_interval(36500)
            .interval_modifier(2.0)
            .hard_multiplier(2.0)
            .good_multiplier(3.0)
            .easy_multiplier(4.0)
            .fuzz_seed(123)
            .scheduled_days(50)
            .elapsed_days(50)
            .build();

        let result = under_test.next_states();

        assert_eq!(
            result,
            NextState {
                hard_interval: Some(200),
                good_interval: Some(300),
                easy_interval: Some(400),
            }
        );
    }

    #[wasm_bindgen_test(unsupported = test)]
    fn use_original_anki_algorithm_if_hard_multiplier_is_0() {
        let under_test = InternalSchedulerBuilder::default()
            .enable_fuzz(false)
            .maximum_interval(36500)
            .interval_modifier(1.0)
            .hard_multiplier(0.0)
            .good_multiplier(3.0)
            .easy_multiplier(4.0)
            .fuzz_seed(123)
            .scheduled_days(50)
            .elapsed_days(50)
            .build();

        let result = under_test.next_states();

        assert_eq!(
            result,
            NextState {
                hard_interval: None,
                good_interval: Some(150),
                easy_interval: Some(200),
            }
        );
    }

    #[wasm_bindgen_test(unsupported = test)]
    fn use_original_anki_algorithm_if_good_multiplier_is_0() {
        let under_test = InternalSchedulerBuilder::default()
            .enable_fuzz(false)
            .maximum_interval(36500)
            .interval_modifier(1.0)
            .hard_multiplier(2.0)
            .good_multiplier(0.0)
            .easy_multiplier(4.0)
            .fuzz_seed(123)
            .scheduled_days(50)
            .elapsed_days(50)
            .build();

        let result = under_test.next_states();

        assert_eq!(
            result,
            NextState {
                hard_interval: Some(100),
                good_interval: None,
                easy_interval: Some(200),
            }
        );
    }

    #[wasm_bindgen_test(unsupported = test)]
    fn use_original_anki_algorithm_if_easy_multiplier_is_0() {
        let under_test = InternalSchedulerBuilder::default()
            .enable_fuzz(false)
            .maximum_interval(36500)
            .interval_modifier(1.0)
            .hard_multiplier(2.0)
            .good_multiplier(3.0)
            .easy_multiplier(0.0)
            .fuzz_seed(123)
            .scheduled_days(50)
            .elapsed_days(50)
            .build();

        let result = under_test.next_states();

        assert_eq!(
            result,
            NextState {
                hard_interval: Some(100),
                good_interval: Some(150),
                easy_interval: None,
            }
        );
    }

    #[wasm_bindgen_test(unsupported = test)]
    fn use_original_anki_algorithm_if_all_multipliers_are_0() {
        let under_test = InternalSchedulerBuilder::default()
            .enable_fuzz(false)
            .maximum_interval(36500)
            .interval_modifier(1.0)
            .hard_multiplier(0.0)
            .good_multiplier(0.0)
            .easy_multiplier(0.0)
            .fuzz_seed(123)
            .scheduled_days(50)
            .elapsed_days(50)
            .build();

        let result = under_test.next_states();

        assert_eq!(
            result,
            NextState {
                hard_interval: None,
                good_interval: None,
                easy_interval: None,
            }
        );
    }
}
