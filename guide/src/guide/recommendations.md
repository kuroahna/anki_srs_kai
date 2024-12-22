# Recommendations

Below is general advice to potentially help increase retention rate and reduce
overall workload, but is by no means hard, set in stone rules, and may not be
applicable to all contexts.

## Auto suspend leeches

It is **highly recommended** to auto suspend
[leeches](https://docs.ankiweb.net/leeches.html). 

While Ease Reward or FSRS' mean reversion algorithm can help address Ease Hell,
it only solves half of the problem. Suppose you have a difficult card that you
keep failing. In this case, Ease Reward or the FSRS mean reversion algorithm
will never be applied, and hence, the card will be stuck in Ease Hell.

Assuming the starting ease for a card is 250%. If we set the leech threshold
to 4, then pressing the Again button 4 times at any point in its review history
will automatically suspend the card. This means the lowest ease a card can have
is \\(250\\% - (4 - 1) \\cdot 20\\% = 190\\%\\). Likewise, if the leech
threshold is 8, then the lowest ease a card can have is \\(250\\% - (8 - 1)
\\cdot 20\\% = 110\\%\\), but since the minimum ease factor in Anki is 130%, it
is capped at 130%.

Once a card has leeched, it is **highly recommended** to deal with the leech. As
mentioned in the Anki docs, there are 3 ways to deal with a leech.

1. [Editing](https://docs.ankiweb.net/leeches.html#editing)
2. [Deleting](https://docs.ankiweb.net/leeches.html#deleting)
3. [Waiting](https://docs.ankiweb.net/leeches.html#waiting)

The best option is to edit the card to make it less difficult. This includes
sticking to the [minimum information
principle](https://super-memory.com/articles/20rules.htm) and refactoring the
card, or adding hints to the front of the card such as example sentences or
hints on how to read a word (such as 訓読み, 音読み, 当て字, 湯桶読み, or
重箱読み for Japanese). Also, it is beneficial to spend more time to understand
the material better, looking up a word's etymology, extra example sentences,
paying close attention to how the character is written (such as how the kanji
character is written in Japanese), creating mnemonics, etc.

Once the card has been refactored, it is **highly recommended** to **Reset** the
card in the **Card Browser** with the **Reset repetition and lapse counts**
checkbox enabled and treat it as a new card. If the lapse counts are not reset,
then Anki will automatically suspend the card again in **half** the threshold.
For example, if the threshold is set to 4, and the card is not reset, then it
will be suspended again at the 6th lapse instead of the 8th lapse.

To enable this feature,
1. Click on the **Gear icon** button for your deck.
2. Click on the **Options** button.
3. Scroll down to the **Lapses** section.
4. Set **Leech threshold** to a value between 4 to 8.
5. Set **Leech action** to **Suspend Card**.
6. Click on the **Save** button.

## Set post-lapse interval to 0

While preserving a card's interval might make sense since the material has not
been completely forgotten, SuperMemo has found that it is
[harmful](https://supermemo.guru/wiki/Post-lapse_stability), since it slows down
the identification of leeches. They state

> Post-lapse stability (PLS) is the stability after a review with a failing
> grade. Unlike stability computed after a successful repetition, post-lapse
> stability cannot be derived from the SInc matrix.
>
> In the ideal case, for simple memories, forgetting results in a reset of
> estimated stability back to near-zero. In theory, only difficult items made of
> composite memories may show a substantial decrease in the costs of
> re-learning, however, even that does not show in data.
> 
> It has been shown long ago that the length of the first post-lapse optimum
> interval is best correlated with the number of memory lapses recorded for the
> item. Even then, **post-lapse interval usually oscillates in the range of 1-4
> days for the default forgetting index of 10%**. The correlation between lapses
> and the PLS is not very useful in adding to the efficiency of learning. Some
> competitive spaced repetition software, as well as SuperMemo in its first
> years, experimented with re-learning hypotheses based on ancient wisdoms of
> psychology, e.g. by halving intervals after a memory lapse. **Current data
> shows clearly that this approach is harmful, as it slows down the
> identification of leeches.** Such an approach to handling forgotten items is a
> form of irrational procrastination. 

By aggressively dealing with difficult cards and allowing them to leech, your
retention should stay fairly high (around 90%) since a majority of your cards
are made less difficult. This also allows the FSRS optimizer to provide better
optimized parameters that reduce your overall workload.

Completely resetting a card's interval to 0 after a lapse is not harmful either.
If the card is truly easy, then the card can be passed within a very short
amount of time and does not contribute much to the actual overall workload.
Otherwise, if it is difficult to recall, then it needed the extra repetitions in
order for it to consolidate in your long term memory.

To enable this feature,
1. Click on the **Gear icon** button for your deck.
2. Click on the **Options** button.
3. Scroll down to the **Advanced** section.
4. Set **New Interval** to `0.00`.
5. Click on the **Save** button.

## Avoid spending too much time on a single card

As the [Anki docs](https://docs.ankiweb.net/studying.html#questions) states,
> When a card is shown, only the question is shown at first. After thinking
> about the answer, either click the Show Answer button, or press the spacebar.
> The answer will then be shown. It’s okay if it takes you a little while to
> recall the answer, but as a general rule **if you can’t answer within about 10
> seconds, it’s probably better to move on and show the answer than keep
> struggling to remember.**

It is generally better to spend as little time as possible per card. If you
stick to the minimum information principle, the cards should naturally be simple
and easy to remember. In this case, if the card still takes awhile to remember,
then it indicates that the material is not yet well understood and needs the
extra repetitions, so it is best to quickly fail rather than spend a long time
trying to recall the information.

For example, in language learning contexts such as Japanese, vocabulary cards
can be reviewed within 2 to 3 seconds each. If it takes longer than that, it is
a good indication that the word has not yet been well learned and should be
reviewed more frequently by failing the card and trying again. A benefit of
spending less time per card is that the overall time to review a deck decreases.
Also, unintuitively, failing cards quickly has the extra benefit of _increasing_
your retention since it forces you to know the material well.

## Only use Again and Good buttons

It may be beneficial to limit yourself and only use the Again (Fail) and Good
(Pass) buttons. The main benefit is to reduce the mental fatigue of choosing
whether a card was hard, good, or easy and limiting the number of choices from 4
to 2.

There is no conclusive evidence that this is objectively better, but FSRS has
done some
[research](https://www.reddit.com/r/Anki/comments/1d0fmsz/fsrs_is_more_accurate_if_you_only_use_again_and/)
and found using only the Again and Good buttons was more accurate. However, they
have noted that it is inconclusive and they do not endorse the advice anymore.
