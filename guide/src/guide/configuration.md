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
            // Approximation of the default FSRS-5 parameters
            // [0.40255, 1.18385, 3.173, 15.69105, 7.1949, 0.5345, 1.4604, 0.0046, 1.54575, 0.1192, 1.01925, 1.9395, 0.11, 0.29605, 2.2698, 0.2315, 2.9898, 0.51655, 0.6621]
            calculateHardMultiplier: (currentEaseFactor, currentInterval) => {
                return currentEaseFactor * Math.pow(currentInterval, -0.013242011) + (-1.048236196);
            },
            calculateGoodMultiplier: (currentEaseFactor, currentInterval) => {
                return currentEaseFactor * Math.pow(currentInterval, -0.154370758) + (1.395807731);
            },
            calculateEasyMultiplier: (currentEaseFactor, currentInterval) => {
                return currentEaseFactor * Math.pow(currentInterval, -0.178728777) + (5.295133129);
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
