# Scheduler

The scheduler is the same exact implementation as the Anki SM-2 algorithm.
However, only review cards are affected. Reviewing cards early (cramming),
new cards, learning cards, and relearning cards are not affected and will
default to the native Anki scheduler, either SM-2 or FSRS.

The main difference is that scheduler extends the notion of the ease factor in
Anki SM-2 by converting it from a scalar value to a mathematical function based
on two parameters, the current ease factor and the current interval of the card.
This addresses the [long intervals for mature cards issue with Anki
SM-2](issuesWithAnkiSM2.md#long-intervals-for-mature-cards).

The next interval for a card upon review is calculated with the following
formula

\\[
\text{NewInterval} = \text{OldInterval} \times \text{EaseFactor} \times
\text{IntervalModifier}
\\]

## Default configuration

```javascript
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
```

## Enable fuzz

According to the [Anki
docs](https://docs.ankiweb.net/studying.html#fuzz-factor),

> When you select an answer button on a review card, Anki also applies a small
> amount of random “fuzz” to prevent cards that were introduced at the same time
> and given the same ratings from sticking together and always coming up for
> review on the same day.
>
> Learning cards are also given up to 5 minutes of extra delay so that they
> don’t always appear in the same order, but answer buttons won't reflect that.
> It is not possible to turn this feature off.

Set this to `true` to enable fuzzing. Otherwise, set to `false` to disable
fuzzing.

The custom scheduler allows the user to disable fuzzing, whereas Anki SM-2 does
not. It is **recommended** to set this value to `true` to prevent cards from
sticking together and coming up for review on the same day.

## Maximum interval

According to the [Anki
docs](https://docs.ankiweb.net/deck-options.html#maximum-interval),

> The maximum number of days a review card will wait before it's shown again.
> When reviews have reached the limit, Hard, Good and Easy will all give the
> same delay. The shorter you set this, the greater your workload will be. The
> default is 100 years; you can decrease this to a smaller number if you’re
> willing to trade extra study time for higher retention.

**IMPORTANT:** Since the scheduler only affects review cards, the maximum
interval setting in the deck options preset in the Anki UI has no effect for
review cards. Please copy the maximum interval setting for the deck here.

## Interval modifier

According to the [Anki
docs](https://docs.ankiweb.net/deck-options.html#interval-modifier),

> An extra multiplier that is applied to all reviews. At its default of 1.00 it
> does nothing. If you set it to 0.80, intervals will be generated at 80% of
> their normal size (so a 10 day interval would become 8 days). You can You can
> thus use the multiplier to to make your reviews less or more frequent.

**IMPORTANT:** Since the scheduler only affects review cards, the interval
modifier setting in the deck options preset in the Anki UI has no effect for
review cards. Please copy the interval modifier for the deck here.

## Calculate hard multiplier

A function that takes in two parameters, `currentEaseFactor` and
`currentInterval`, and outputs the resulting ease factor for the Hard button.

Set to `return 0.0;` to fallback to Anki's native scheduler, either SM-2 or
FSRS. This effectively disables the custom scheduler when pressing the Hard
button.

## Calculate good multiplier

A function that takes in two parameters, `currentEaseFactor` and
`currentInterval`, and outputs the resulting ease factor for the Good button.

Set to `return 0.0;` to fallback to Anki's native scheduler, either SM-2 or
FSRS. This effectively disables the custom scheduler when pressing the Good
button.

## Calculate easy multiplier

A function that takes in two parameters, `currentEaseFactor` and
`currentInterval`, and outputs the resulting ease factor for the Easy button.

Set to `return 0.0;` to fallback to Anki's native scheduler, either SM-2 or
FSRS. This effectively disables the custom scheduler when pressing the Easy
button.
