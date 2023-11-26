use std::collections::HashMap;

use serde::Deserialize;
use wasm_bindgen::prelude::wasm_bindgen;
use wasm_bindgen::JsValue;

use crate::anki::javascript::{CONTEXT, CUSTOM_DATA, STATES};
use crate::anki::{FilteredState, NormalState, SchedulingStateKind, SchedulingStates};
use crate::ease_reward::EaseReward;

mod anki;
mod ease_reward;

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DeckOptions {
    ease_reward: EaseReward,
}

#[wasm_bindgen(js_name = calculateNextCardStates)]
pub fn calculate_next_card_states(
    deck_name_to_deck_options: JsValue,
    states: JsValue,
) -> Result<JsValue, JsValue> {
    let deck_name_to_deck_options: HashMap<String, DeckOptions> =
        serde_wasm_bindgen::from_value(deck_name_to_deck_options)?;
    let deck_options = match deck_name_to_deck_options.get(CONTEXT.deck_name().as_str()) {
        None => match deck_name_to_deck_options.get("Global Settings") {
            None => return Ok(JsValue::NULL),
            Some(deck_options) => deck_options,
        },
        Some(deck_options) => deck_options,
    };

    let states: SchedulingStates = serde_wasm_bindgen::from_value(states)?;

    match states.current.kind {
        SchedulingStateKind::Normal(normal) => match normal {
            // We don't want to affect cards that are in Relearning state
            // because we want to keep Anki's "New Interval" setting behaviour
            NormalState::New(_) | NormalState::Learning(_) | NormalState::Relearning(_) => {
                return Ok(JsValue::NULL)
            }
            NormalState::Review(_) => {}
        },
        SchedulingStateKind::Filtered(filtered) => match filtered {
            FilteredState::Preview(_) => return Ok(JsValue::NULL),
            FilteredState::Rescheduling(rescheduling) => match rescheduling.original_state {
                NormalState::New(_) | NormalState::Learning(_) | NormalState::Relearning(_) => {
                    return Ok(JsValue::NULL)
                }
                NormalState::Review(_) => {}
            },
        },
    };

    match states.again.kind {
        SchedulingStateKind::Normal(normal) => match normal {
            NormalState::New(_) => {}
            NormalState::Learning(_) => {}
            NormalState::Review(_) => {}
            NormalState::Relearning(_) => {
                CUSTOM_DATA.again().set_c(None);
            }
        },
        SchedulingStateKind::Filtered(filtered) => match filtered {
            FilteredState::Preview(_) => {}
            FilteredState::Rescheduling(rescheduling) => match rescheduling.original_state {
                NormalState::New(_) => {}
                NormalState::Learning(_) => {}
                NormalState::Review(_) => {}
                NormalState::Relearning(_) => {
                    CUSTOM_DATA.again().set_c(None);
                }
            },
        },
    };

    match states.good.kind {
        SchedulingStateKind::Normal(normal) => match normal {
            NormalState::New(_) => {}
            NormalState::Learning(_) => {}
            NormalState::Review(review) => {
                let number_of_successful_reviews =
                    CUSTOM_DATA.good().c().map_or_else(|| 1, |c| c + 1);
                STATES.good().normal().review().set_ease_factor(
                    deck_options.ease_reward.calculate_new_ease_factor(
                        number_of_successful_reviews,
                        review.ease_factor,
                    ),
                );
                CUSTOM_DATA.good().set_c(Some(number_of_successful_reviews));
            }
            NormalState::Relearning(_) => {}
        },
        SchedulingStateKind::Filtered(filtered) => match filtered {
            FilteredState::Preview(_) => {}
            FilteredState::Rescheduling(rescheduling) => match rescheduling.original_state {
                NormalState::New(_) => {}
                NormalState::Learning(_) => {}
                NormalState::Review(review) => {
                    let number_of_successful_reviews =
                        CUSTOM_DATA.good().c().map_or_else(|| 1, |c| c + 1);
                    STATES
                        .good()
                        .filtered()
                        .rescheduling()
                        .original_state()
                        .review()
                        .set_ease_factor(deck_options.ease_reward.calculate_new_ease_factor(
                            number_of_successful_reviews,
                            review.ease_factor,
                        ));
                    CUSTOM_DATA.good().set_c(Some(number_of_successful_reviews));
                }
                NormalState::Relearning(_) => {}
            },
        },
    };

    match states.easy.kind {
        SchedulingStateKind::Normal(normal) => match normal {
            NormalState::New(_) => {}
            NormalState::Learning(_) => {}
            NormalState::Review(review) => {
                let number_of_successful_reviews =
                    CUSTOM_DATA.easy().c().map_or_else(|| 1, |c| c + 1);
                STATES.easy().normal().review().set_ease_factor(
                    deck_options.ease_reward.calculate_new_ease_factor(
                        number_of_successful_reviews,
                        review.ease_factor,
                    ),
                );
                CUSTOM_DATA.easy().set_c(Some(number_of_successful_reviews));
            }
            NormalState::Relearning(_) => {}
        },
        SchedulingStateKind::Filtered(filtered) => match filtered {
            FilteredState::Preview(_) => {}
            FilteredState::Rescheduling(rescheduling) => match rescheduling.original_state {
                NormalState::New(_) => {}
                NormalState::Learning(_) => {}
                NormalState::Review(review) => {
                    let number_of_successful_reviews =
                        CUSTOM_DATA.easy().c().map_or_else(|| 1, |c| c + 1);
                    STATES
                        .easy()
                        .filtered()
                        .rescheduling()
                        .original_state()
                        .review()
                        .set_ease_factor(deck_options.ease_reward.calculate_new_ease_factor(
                            number_of_successful_reviews,
                            review.ease_factor,
                        ));
                    CUSTOM_DATA.easy().set_c(Some(number_of_successful_reviews));
                }
                NormalState::Relearning(_) => {}
            },
        },
    };

    Ok(JsValue::NULL)
}
