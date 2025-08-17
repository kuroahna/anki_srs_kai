# Configuration

The default deck options are

```javascript
const deckOptions = {
    "deck1": {
        easeReward: {
            minimumConsecutiveSuccessfulReviewsRequiredForReward: 3,
            baseEaseReward: 0.05,
            stepEaseReward: 0.05,
            minimumEase: 1.30,
            maximumEase: 2.50,
        },
        scheduler: {
            enableFuzz: true,
            maximumInterval: 36500,
            intervalModifier: 1.00,
            calculateHardMultiplier: (currentEaseFactor, currentInterval) => {
                return 0.0;
            },
            calculateGoodMultiplier: (currentEaseFactor, currentInterval) => {
                return currentEaseFactor / Math.pow(currentInterval, 0.054297);
            },
            calculateEasyMultiplier: (currentEaseFactor, currentInterval) => {
                return 0.0;
            },
        },
    },
    "Global Settings": {
        easeReward: {
            minimumConsecutiveSuccessfulReviewsRequiredForReward: 3,
            baseEaseReward: 0.05,
            stepEaseReward: 0.05,
            minimumEase: 1.30,
            maximumEase: 2.50,
        },
        scheduler: {
            enableFuzz: true,
            maximumInterval: 36500,
            intervalModifier: 1.00,
            // Approximation of the default FSRS-6 parameters
            // [0.212, 1.2931, 2.3065, 8.2956, 6.4133, 0.8334, 3.0194, 0.001, 1.8722, 0.1666, 0.796, 1.4835, 0.0614, 0.2629, 1.6483, 0.6014, 1.8729, 0.5425, 0.0912, 0.0658, 0.1542]
            calculateHardMultiplier: (currentEaseFactor, currentInterval) => {
                return currentEaseFactor * Math.pow(currentInterval, -0.077098162) + (0.144440985);
            },
            calculateGoodMultiplier: (currentEaseFactor, currentInterval) => {
                return currentEaseFactor * Math.pow(currentInterval, -0.182458510) + (1.779479164);
            },
            calculateEasyMultiplier: (currentEaseFactor, currentInterval) => {
                return currentEaseFactor * Math.pow(currentInterval, -0.183552566) + (3.407921198);
            },
        },
    },
};
```

Each deck can be configured individually by adding a new top level entry in
`deckOptions` with the name of the deck you want to configure.

The `easeReward` and `scheduler` fields, along with their corresponding
settings, must be correctly configured. Any typos or omitted fields will cause
the custom scheduler to fallback to Anki's native scheduler, either SM-2 or
FSRS, whichever has been enabled for the deck options preset.

The `Global Settings` entry serves as a fallback configuration for any decks
that do not match the name of a specified deck. If `Global Settings` is removed
from `deckOptions`, Anki will revert to using its native scheduler, either SM-2
or FSRS, based on the enabled preset.

Any subdecks under a parent deck will not inherit its options from the parent
deck. A separate top level entry must be created in order for the custom
scheduler to take effect. Otherwise, it will be scheduled using the settings
configured in `Global Settings` if present. For example, if there is a parent
deck called `parentDeck` and a subdeck called `subDeck`, then the top level
entry should be named `parentDeck::subDeck`.
