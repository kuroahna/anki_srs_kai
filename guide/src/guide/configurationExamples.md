# Examples

## Replace Straight Reward add-on and use the native Anki scheduler

```javascript
const deckOptions = {
    "My Deck": {
        easeReward: {
            minimumConsecutiveSuccessfulReviewsRequiredForReward: 3,
            baseEaseReward: 0.05,
            stepEaseReward: 0.05,
            minimumEase: 1.30,
            maximumEase: 2.50,
        },
        scheduler: {
            // These options are effectively ignored since the hard, good, and
            // easy buttons are using the native Anki scheduler
            enableFuzz: true,
            maximumInterval: 36500,
            intervalModifier: 1.00,
            calculateHardMultiplier: (currentEaseFactor, currentInterval) => {
                // Use the native Anki scheduler for the Hard button
                return 0.0;
            },
            calculateGoodMultiplier: (currentEaseFactor, currentInterval) => {
                // Use the native Anki scheduler for the Good button
                return 0.0;
            },
            calculateEasyMultiplier: (currentEaseFactor, currentInterval) => {
                // Use the native Anki scheduler for the Easy button
                return 0.0;
            },
        },
    },
};
```

## Disable fuzz for Anki SM-2

```javascript
const deckOptions = {
    "My Deck": {
        easeReward: {
            // Disable ease reward
            minimumConsecutiveSuccessfulReviewsRequiredForReward: 0,
            baseEaseReward: 0.05,
            stepEaseReward: 0.05,
            minimumEase: 1.30,
            maximumEase: 2.50,
        },
        scheduler: {
            // Disable fuzz
            enableFuzz: false,
            maximumInterval: 36500,
            intervalModifier: 1.00,
            calculateHardMultiplier: (currentEaseFactor, currentInterval) => {
                return currentEaseFactor;
            },
            calculateGoodMultiplier: (currentEaseFactor, currentInterval) => {
                // Schedule using the SM-2 algorithm with fuzz disabled
                //
                // We cannot return 0.0 since the custom scheduler needs to run
                // in order to disable fuzz
                return currentEaseFactor;
            },
            calculateEasyMultiplier: (currentEaseFactor, currentInterval) => {
                return currentEaseFactor;
            },
        },
    },
};
```
