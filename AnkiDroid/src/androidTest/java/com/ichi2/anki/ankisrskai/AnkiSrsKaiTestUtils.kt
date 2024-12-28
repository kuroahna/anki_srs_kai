package com.ichi2.anki.ankisrskai

import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.ViewAssertion
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.longClick
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import anki.cards.FsrsMemoryState
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.R
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.libanki.Card
import com.ichi2.libanki.Consts
import com.ichi2.libanki.utils.TimeManager
import java.util.concurrent.TimeUnit
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.fail

class AnkiSrsKaiTestUtils private constructor() {
    companion object {
        fun disableAnimations() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val key = context.getString(R.string.safe_display_key)
            context.sharedPrefs().edit().putBoolean(key, true).commit()
        }

        fun closeGetStartedScreenIfExists() {
            onView(withId(R.id.get_started))
                .withFailureHandler { _, _ -> }
                .perform(click())
        }

        fun closeBackupCollectionDialogIfExists() {
            onView(withText(R.string.button_backup_later))
                .withFailureHandler { _, _ -> }
                .perform(click())
        }

        private fun clickOnDeckWithName(deckName: String) {
            onView(withId(R.id.decks)).perform(
                RecyclerViewActions.actionOnItem<RecyclerView.ViewHolder>(
                    hasDescendant(withText(deckName)),
                    click()
                )
            )
        }

        fun reviewDeckWithName(deckName: String) {
            onView(withId(R.id.decks)).checkWithTimeout(matches(hasDescendant(withText(deckName))))
            clickOnDeckWithName(deckName)
        }

        fun clickBackButton() {
            onView(withContentDescription(androidx.appcompat.R.string.abc_action_bar_up_description))
                .checkWithTimeout(matches(isDisplayed()))
            onView(withContentDescription(androidx.appcompat.R.string.abc_action_bar_up_description))
                .perform(click())
        }

        /**
         * Refreshes the card counts in the deck picker. Useful for tests when
         * modifying the card state directly from the database
         */
        fun refreshDeck() {
            onView(withContentDescription(R.string.drawer_open))
                .checkWithTimeout(matches(isDisplayed()))
            onView(withContentDescription(R.string.drawer_open))
                .perform(click())
            // Opening the settings page and going back to the deck picker
            // will cause the deck picker to refresh its state
            onView(withId(R.id.nav_settings)).checkWithTimeout(matches(isDisplayed()))
            onView(withId(R.id.nav_settings)).perform(click())
            clickBackButton()
            onView(withContentDescription(R.string.drawer_open))
                .checkWithTimeout(matches(isDisplayed()))
            onView(withContentDescription(R.string.drawer_open))
                .perform(click())
            onView(withId(R.id.nav_decks)).checkWithTimeout(matches(isDisplayed()))
            onView(withId(R.id.nav_decks)).perform(click())
        }

        fun rebuildFilteredDeck(deckName: String) {
            onView(withId(R.id.decks)).checkWithTimeout(matches(hasDescendant(withText(deckName))))
            onView(withId(R.id.decks)).perform(
                RecyclerViewActions.actionOnItem<RecyclerView.ViewHolder>(
                    hasDescendant(withText(deckName)),
                    longClick()
                )
            )
            onView(withText("Rebuild"))
                .checkWithTimeout(matches(withText("Rebuild")))
            onView(withText("Rebuild")).perform(click())
        }

        fun clickShowAnswerAndAnswerAgain() {
            onView(withId(R.id.flashcard_layout_flip))
                .checkWithTimeout(matches(isDisplayed()))
            onView(withId(R.id.flashcard_layout_flip))
                .perform(click())
            // We need to wait for the card to fully load to allow enough time for
            // the messages to be passed in and out of the WebView when evaluating
            // the custom JS scheduler code. The ease buttons are hidden until the
            // custom scheduler has finished running
            onView(withId(R.id.flashcard_layout_ease1))
                .checkWithTimeout(matches(isDisplayed()))
            onView(withId(R.id.flashcard_layout_ease1))
                .perform(click())
        }

        fun clickShowAnswerAndAnswerHard() {
            onView(withId(R.id.flashcard_layout_flip))
                .checkWithTimeout(matches(isDisplayed()))
            onView(withId(R.id.flashcard_layout_flip))
                .perform(click())
            // We need to wait for the card to fully load to allow enough time for
            // the messages to be passed in and out of the WebView when evaluating
            // the custom JS scheduler code. The ease buttons are hidden until the
            // custom scheduler has finished running
            onView(withId(R.id.flashcard_layout_ease2))
                .checkWithTimeout(matches(isDisplayed()))
            onView(withId(R.id.flashcard_layout_ease2))
                .perform(click())
        }

        fun clickShowAnswerAndAnswerGood() {
            onView(withId(R.id.flashcard_layout_flip))
                .checkWithTimeout(matches(isDisplayed()))
            onView(withId(R.id.flashcard_layout_flip))
                .perform(click())
            // We need to wait for the card to fully load to allow enough time for
            // the messages to be passed in and out of the WebView when evaluating
            // the custom JS scheduler code. The ease buttons are hidden until the
            // custom scheduler has finished running
            onView(withId(R.id.flashcard_layout_ease3))
                .checkWithTimeout(matches(isDisplayed()))
            onView(withId(R.id.flashcard_layout_ease3))
                .perform(click())
        }

        fun clickShowAnswerAndAnswerEasy() {
            onView(withId(R.id.flashcard_layout_flip))
                .checkWithTimeout(matches(isDisplayed()))
            onView(withId(R.id.flashcard_layout_flip))
                .perform(click())
            // We need to wait for the card to fully load to allow enough time for
            // the messages to be passed in and out of the WebView when evaluating
            // the custom JS scheduler code. The ease buttons are hidden until the
            // custom scheduler has finished running
            onView(withId(R.id.flashcard_layout_ease4))
                .checkWithTimeout(matches(isDisplayed()))
            onView(withId(R.id.flashcard_layout_ease4))
                .perform(click())
        }

        fun Card.moveToLearnQueue() {
            this.queue = Consts.QUEUE_TYPE_LRN
            this.type = Consts.CARD_TYPE_LRN
            this.due = 0
            val col = CollectionManager.getColUnsafe()
            col.updateCard(this, true)
        }

        fun Card.moveToRelearnQueue() {
            this.queue = Consts.QUEUE_TYPE_LRN
            this.type = Consts.CARD_TYPE_RELEARNING
            this.due = 0
            val col = CollectionManager.getColUnsafe()
            col.updateCard(this, true)
        }

        private fun ViewInteraction.checkWithTimeout(
            viewAssertion: ViewAssertion,
            retryWaitTimeInMilliseconds: Long = 100,
            maxWaitTimeInMilliseconds: Long = TimeUnit.SECONDS.toMillis(60)
        ) {
            val startTime = TimeManager.time.intTimeMS()

            while (TimeManager.time.intTimeMS() - startTime < maxWaitTimeInMilliseconds) {
                try {
                    check(viewAssertion)
                    return
                } catch (e: Throwable) {
                    Thread.sleep(retryWaitTimeInMilliseconds)
                }
            }

            fail("View assertion was not true within $maxWaitTimeInMilliseconds milliseconds")
        }

        fun assertThat(card: Card): Card {
            return card
        }

        fun Card.hasCardType(cardType: Int): Card {
            assertEquals(cardType, this.toBackendCard().ctype, "card type did not match")
            return this
        }

        fun Card.hasQueueType(queueType: Int): Card {
            assertEquals(queueType, this.toBackendCard().queue, "queue type did not match")
            return this
        }

        fun Card.hasDue(due: Int): Card {
            assertEquals(due, this.toBackendCard().due, "due did not match")
            return this
        }

        fun Card.hasDueWithinRange(range: IntRange): Card {
            assertContains(range, this.toBackendCard().due, "due was not within the range")
            return this
        }

        fun Card.hasInterval(interval: Int): Card {
            assertEquals(interval, this.toBackendCard().interval, "interval did not match")
            return this
        }

        fun Card.hasIntervalWithinRange(range: IntRange): Card {
            assertContains(
                range,
                this.toBackendCard().interval,
                "interval was not within the range"
            )
            return this
        }

        fun Card.hasEaseFactor(easeFactor: Int): Card {
            assertEquals(easeFactor, this.toBackendCard().easeFactor, "ease factor did not match")
            return this
        }

        fun Card.hasReps(reps: Int): Card {
            assertEquals(reps, this.toBackendCard().reps, "reps did not match")
            return this
        }

        fun Card.hasLapses(lapses: Int): Card {
            assertEquals(lapses, this.toBackendCard().lapses, "lapses did not match")
            return this
        }

        fun Card.hasRemainingSteps(remainingSteps: Int): Card {
            assertEquals(
                remainingSteps,
                this.toBackendCard().remainingSteps,
                "remaining steps did not match"
            )
            return this
        }

        fun Card.hasCustomData(customData: String): Card {
            assertEquals(customData, this.toBackendCard().customData, "custom data did not match")
            return this
        }

        fun Card.hasMemoryState(memoryState: FsrsMemoryState): Card {
            assertEquals(
                memoryState,
                this.toBackendCard().memoryState,
                "memory state did not match"
            )
            return this
        }

        fun Card.hasDesiredRetention(desiredRetention: Float): Card {
            assertEquals(
                desiredRetention,
                this.toBackendCard().desiredRetention,
                "desired retention did not match"
            )
            return this
        }

        fun readAssetFile(path: String): String {
            return InstrumentationRegistry
                .getInstrumentation()
                .context
                .assets
                .open(path)
                .bufferedReader()
                .use { it.readText() }
        }
    }
}
