use serde::{Deserialize, Deserializer};

fn round_to_places(value: f64, decimal_places: u32) -> f64 {
    let factor = 10_f64.powi(decimal_places as i32);
    (value * factor).round() / factor
}

pub struct EaseReward {
    minimum_consecutive_successful_reviews_required_for_reward: u32,
    base_ease_reward: f64,
    step_ease_reward: f64,
    minimum_ease: f64,
    maximum_ease: f64,
}

impl<'de> Deserialize<'de> for EaseReward {
    fn deserialize<D>(deserializer: D) -> Result<Self, D::Error>
    where
        D: Deserializer<'de>,
    {
        #[derive(Deserialize)]
        #[serde(rename_all = "camelCase")]
        struct Fields {
            minimum_consecutive_successful_reviews_required_for_reward: u32,
            base_ease_reward: f64,
            step_ease_reward: f64,
            minimum_ease: f64,
            maximum_ease: f64,
        }

        let fields = Fields::deserialize(deserializer)?;

        Ok(EaseReward::new(
            fields.minimum_consecutive_successful_reviews_required_for_reward,
            fields.base_ease_reward,
            fields.step_ease_reward,
            fields.minimum_ease,
            fields.maximum_ease,
        ))
    }
}

impl EaseReward {
    pub fn new(
        minimum_consecutive_successful_reviews_required_for_reward: u32,
        base_ease_reward: f64,
        step_ease_reward: f64,
        minimum_ease: f64,
        maximum_ease: f64,
    ) -> Self {
        Self {
            minimum_consecutive_successful_reviews_required_for_reward,
            base_ease_reward: base_ease_reward.max(0.0),
            step_ease_reward: step_ease_reward.max(0.0),
            minimum_ease: minimum_ease.max(1.3),
            maximum_ease: maximum_ease.clamp(0.0, 9.99),
        }
    }

    pub fn calculate_new_ease_factor(
        &self,
        number_of_successful_reviews: u32,
        ease_factor: f64,
    ) -> f64 {
        if self.minimum_consecutive_successful_reviews_required_for_reward == 0
            || number_of_successful_reviews
                < self.minimum_consecutive_successful_reviews_required_for_reward
            || ease_factor < self.minimum_ease
            || ease_factor > self.maximum_ease
        {
            return ease_factor;
        }

        let reward = self.base_ease_reward
            + (number_of_successful_reviews
                - self.minimum_consecutive_successful_reviews_required_for_reward)
                as f64
                * self.step_ease_reward;
        // Anki only stores up to the 3rd decimal place for the ease factor
        round_to_places(
            self.minimum_ease
                .max(ease_factor + reward)
                .min(self.maximum_ease),
            3,
        )
    }
}

#[cfg(test)]
mod tests {
    use crate::ease_reward::EaseReward;
    use wasm_bindgen_test::wasm_bindgen_test;

    #[derive(Default)]
    struct EaseRewardBuilder {
        minimum_consecutive_successful_reviews_required_for_reward: Option<u32>,
        base_ease_reward: Option<f64>,
        step_ease_reward: Option<f64>,
        minimum_ease: Option<f64>,
        maximum_ease: Option<f64>,
    }

    impl EaseRewardBuilder {
        fn minimum_consecutive_successful_reviews_required_for_reward(
            mut self,
            minimum_consecutive_successful_reviews_required_for_reward: u32,
        ) -> Self {
            self.minimum_consecutive_successful_reviews_required_for_reward =
                Some(minimum_consecutive_successful_reviews_required_for_reward);
            self
        }

        fn base_ease_reward(mut self, base_ease_reward: f64) -> Self {
            self.base_ease_reward = Some(base_ease_reward);
            self
        }

        fn step_ease_reward(mut self, step_ease_reward: f64) -> Self {
            self.step_ease_reward = Some(step_ease_reward);
            self
        }

        fn minimum_ease(mut self, minimum_ease: f64) -> Self {
            self.minimum_ease = Some(minimum_ease);
            self
        }

        fn maximum_ease(mut self, maximum_ease: f64) -> Self {
            self.maximum_ease = Some(maximum_ease);
            self
        }

        fn build(&self) -> EaseReward {
            EaseReward::new(
                self.minimum_consecutive_successful_reviews_required_for_reward.expect("minimum_consecutive_successful_reviews_required_for_reward should be set in the test"),
                 self.base_ease_reward.expect("base_ease_reward should be set in the test"),
                 self.step_ease_reward.expect("step_ease_reward should be set in the test"),
                 self.minimum_ease.expect("minimum_ease should be set in the test"),
                 self.maximum_ease.expect("maximum_ease should be set in the test"),
            )
        }
    }

    #[wasm_bindgen_test(unsupported = test)]
    fn no_ease_reward_given_if_minimum_consecutive_successful_reviews_required_is_0() {
        let under_test = EaseRewardBuilder::default()
            .minimum_consecutive_successful_reviews_required_for_reward(0)
            .base_ease_reward(0.15)
            .step_ease_reward(0.05)
            .minimum_ease(1.30)
            .maximum_ease(3.00)
            .build();

        let result = under_test.calculate_new_ease_factor(6, 2.0);

        assert_eq!(result, 2.0);
    }

    #[wasm_bindgen_test(unsupported = test)]
    fn no_ease_reward_given_if_number_of_successes_is_below_minimum_required() {
        let under_test = EaseRewardBuilder::default()
            .minimum_consecutive_successful_reviews_required_for_reward(2)
            .base_ease_reward(0.15)
            .step_ease_reward(0.05)
            .minimum_ease(1.30)
            .maximum_ease(3.00)
            .build();

        let result = under_test.calculate_new_ease_factor(1, 2.0);

        assert_eq!(result, 2.0);
    }

    #[wasm_bindgen_test(unsupported = test)]
    fn ease_reward_added() {
        let under_test = EaseRewardBuilder::default()
            .minimum_consecutive_successful_reviews_required_for_reward(4)
            .base_ease_reward(0.15)
            .step_ease_reward(0.05)
            .minimum_ease(1.30)
            .maximum_ease(3.00)
            .build();

        let result = under_test.calculate_new_ease_factor(6, 2.0);

        assert_eq!(result, 2.25);
    }

    #[wasm_bindgen_test(unsupported = test)]
    fn ease_factor_is_rounded() {
        let under_test = EaseRewardBuilder::default()
            .minimum_consecutive_successful_reviews_required_for_reward(1)
            .base_ease_reward(0.999999)
            .step_ease_reward(0.00)
            .minimum_ease(1.30)
            .maximum_ease(3.00)
            .build();

        let result = under_test.calculate_new_ease_factor(1, 2.0);

        assert_eq!(result, 3.00);
    }

    #[wasm_bindgen_test(unsupported = test)]
    fn ease_reward_greater_than_maximum_is_clamped_to_the_maximum() {
        let under_test = EaseRewardBuilder::default()
            .minimum_consecutive_successful_reviews_required_for_reward(4)
            .base_ease_reward(0.15)
            .step_ease_reward(0.05)
            .minimum_ease(1.30)
            .maximum_ease(2.70)
            .build();

        let result = under_test.calculate_new_ease_factor(6, 2.5);

        assert_eq!(result, 2.70);
    }

    #[wasm_bindgen_test(unsupported = test)]
    fn ease_reward_greater_than_absolute_maximum_is_clamped_to_the_absolute_maximum() {
        let under_test = EaseRewardBuilder::default()
            .minimum_consecutive_successful_reviews_required_for_reward(4)
            .base_ease_reward(0.15)
            .step_ease_reward(0.05)
            .minimum_ease(1.30)
            .maximum_ease(20.0)
            .build();

        let result = under_test.calculate_new_ease_factor(1000, 2.5);

        assert_eq!(result, 9.99);
    }

    #[wasm_bindgen_test(unsupported = test)]
    fn no_ease_bonus_rewarded_if_ease_factor_is_already_above_maximum() {
        let under_test = EaseRewardBuilder::default()
            .minimum_consecutive_successful_reviews_required_for_reward(4)
            .base_ease_reward(0.15)
            .step_ease_reward(0.05)
            .minimum_ease(1.30)
            .maximum_ease(2.00)
            .build();

        let result = under_test.calculate_new_ease_factor(6, 2.5);

        assert_eq!(result, 2.5);
    }

    #[wasm_bindgen_test(unsupported = test)]
    fn no_ease_bonus_rewarded_if_ease_factor_is_below_minimum() {
        let under_test = EaseRewardBuilder::default()
            .minimum_consecutive_successful_reviews_required_for_reward(4)
            .base_ease_reward(0.15)
            .step_ease_reward(0.05)
            .minimum_ease(1.30)
            .maximum_ease(2.00)
            .build();

        let result = under_test.calculate_new_ease_factor(6, 1.2);

        assert_eq!(result, 1.2);
    }
}
