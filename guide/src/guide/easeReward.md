# Ease Reward

Ease reward works exactly the same as the [Straight
Reward](https://ankiweb.net/shared/info/957961234) addon with the added benefit
of being directly incorporated in the custom scheduler. This means that ease
rewards are applied immediately upon review without having to sync on PC when
reviewing on mobile. Only review cards are affected. Reviewing cards early
(cramming), new cards, learning cards, and relearning cards are not affected.

The ease factor of a card is increased when the Good or Easy button has been
pressed consecutively. Pressing the Good or Easy button is considered as a
successful review. Pressing the Hard button is not considered as a successful
review, but pressing it will not reset the current streak back to 0. Pressing
the Again button will reset the current streak back to 0.

## Algorithm

Let \\(b\\) represent the base ease reward, \\(s\\) represent the step ease
reward, \\(x\\) represent the current number of consecutive successful reviews
(ie, the current streak), and \\(m\\) represent the minimum consecutive
successful reviews required for reward.

Then if \\(x >= m\\), the ease factor of the card will increase by the following
formula

\\[
b + s \cdot (x - m)
\\]

## Example

1. The card has been rated Good for the 6th time in a row.
2. The card currently has an ease factor of 250%.
3. The minimum consecutive successful reviews required for reward is set to 4.
4. The base ease reward is set to 15%.
5. The step ease reward is set to 5%.
6. The minimum ease is set to 130%.
7. The maximum ease is set to 270%.

Since the current streak is 6 and the minimum streak required is 4, we have
\\(x >= m\\), so the card's ease factor will increase by

\\[
\begin{align}
&b + s \cdot (x - m) \\\\
&= 15\\% + 5\\% \cdot (6 - 4) \\\\
&= 15\\% + 10\\% \\\\
&= 25\\%
\end{align}
\\]

which is \\(250\\% + 25\\% = 275\\%\\). However, since the maximum ease is
\\(270\\%\\), then the ease of card will be set to \\(270\\%\\).


## Default configuration

```javascript
easeReward: {
    minimumConsecutiveSuccessfulReviewsRequiredForReward: 3,
    baseEaseReward: 0.05,
    stepEaseReward: 0.05,
    minimumEase: 1.30,
    maximumEase: 2.50,
},
```

## Minimum consecutive successful reviews required for reward

The number of successful reviews required in a streak before the ease reward is
applied. Set this to 0 to disable ease reward.

## Base ease reward

Specifies the initial ease reward as a percentage. For example, a value of
`0.05` represents an increase in the ease factor by 5%.

## Step ease reward

Specifies the additional ease reward as a percentage for each consecutive
successful review in the streak. For example, a value of `0.05` represents a 5%
increase in ease per streak step.

## Minimum ease

Ease rewards are only applied to cards with an ease factor greater than or equal
to the minimum ease. For example, a value of `1.30` represents an ease factor of
130%. Any card with an ease factor less than 130% will be ignored.

## Maximum ease

Ease rewards are only applied to cards with an ease factor less than or equal
to the maximum ease. For example, a value of `2.50` represents an ease factor of
250%. Any card with an ease factor greater than 250% will be ignored.
