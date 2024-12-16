package com.ichi2.anki.ankisrskai

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.cards.FsrsMemoryState
import anki.deck_config.DeckConfigsForUpdate
import anki.deck_config.UpdateDeckConfigsMode
import anki.decks.Deck
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.IntroductionActivity
import com.ichi2.anki.ankisrskai.AnkiSrsKaiTestUtils.Companion.assertThat
import com.ichi2.anki.ankisrskai.AnkiSrsKaiTestUtils.Companion.clickShowAnswerAndAnswerAgain
import com.ichi2.anki.ankisrskai.AnkiSrsKaiTestUtils.Companion.clickShowAnswerAndAnswerEasy
import com.ichi2.anki.ankisrskai.AnkiSrsKaiTestUtils.Companion.clickShowAnswerAndAnswerGood
import com.ichi2.anki.ankisrskai.AnkiSrsKaiTestUtils.Companion.clickShowAnswerAndAnswerHard
import com.ichi2.anki.ankisrskai.AnkiSrsKaiTestUtils.Companion.closeBackupCollectionDialogIfExists
import com.ichi2.anki.ankisrskai.AnkiSrsKaiTestUtils.Companion.closeGetStartedScreenIfExists
import com.ichi2.anki.ankisrskai.AnkiSrsKaiTestUtils.Companion.hasCardType
import com.ichi2.anki.ankisrskai.AnkiSrsKaiTestUtils.Companion.hasCustomData
import com.ichi2.anki.ankisrskai.AnkiSrsKaiTestUtils.Companion.hasDesiredRetention
import com.ichi2.anki.ankisrskai.AnkiSrsKaiTestUtils.Companion.hasMemoryState
import com.ichi2.anki.ankisrskai.AnkiSrsKaiTestUtils.Companion.hasQueueType
import com.ichi2.anki.ankisrskai.AnkiSrsKaiTestUtils.Companion.hasReps
import com.ichi2.anki.ankisrskai.AnkiSrsKaiTestUtils.Companion.moveToLearnQueue
import com.ichi2.anki.ankisrskai.AnkiSrsKaiTestUtils.Companion.moveToRelearnQueue
import com.ichi2.anki.ankisrskai.AnkiSrsKaiTestUtils.Companion.readAssetFile
import com.ichi2.anki.ankisrskai.AnkiSrsKaiTestUtils.Companion.rebuildFilteredDeck
import com.ichi2.anki.ankisrskai.AnkiSrsKaiTestUtils.Companion.refreshDeck
import com.ichi2.anki.ankisrskai.AnkiSrsKaiTestUtils.Companion.reviewDeckWithName
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.testutil.GrantStoragePermission.storagePermission
import com.ichi2.anki.testutil.grantPermissions
import com.ichi2.anki.testutil.notificationPermission
import com.ichi2.libanki.Consts
import com.ichi2.libanki.utils.TimeManager
import kotlinx.coroutines.runBlocking
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnkiSrsKaiAddonTest : InstrumentedTest() {

    companion object {
        private const val SQL_FILE_NAME = "update_custom_data.sql"
    }

    // Launch IntroductionActivity instead of DeckPicker activity because in CI
    // builds, it seems to create IntroductionActivity after the DeckPicker,
    // causing the DeckPicker activity to be destroyed. As a consequence, this
    // will throw RootViewWithoutFocusException when Espresso tries to interact
    // with an already destroyed activity. By launching IntroductionActivity, we
    // ensure that IntroductionActivity is launched first and navigate to the
    // DeckPicker -> Reviewer activities
    @get:Rule
    val activityScenarioRule = ActivityScenarioRule(IntroductionActivity::class.java)

    @get:Rule
    val mRuntimePermissionRule = grantPermissions(storagePermission, notificationPermission)

    @Before
    fun setup() {
        runBlocking {
            CollectionManager.deleteCollectionDirectory()
        }
    }

    @Test
    fun existingCustomDataIsUpdatedWithCorrectSuccessCount() {
        val sql = readAssetFile(SQL_FILE_NAME)
        col.config.set("cardStateCustomizer", "customData.good.c = 0;")
        val note = addNoteUsingBasicModel("foo", "bar")
        val card = note.firstCard(col)
        val deck = col.decks.get(note.notetype.did)!!
        card.moveToReviewQueue()

        closeGetStartedScreenIfExists()
        closeBackupCollectionDialogIfExists()
        reviewDeckWithName(deck.name)

        var cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(0).hasCustomData("")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        clickShowAnswerAndAnswerGood()
        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(1).hasCustomData("""{"c":0}""")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{"cd":"{\"c\":0}"}""")
        )

        col.db.execute(sql)

        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(1).hasCustomData("""{"c":1}""")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{"cd":"{\"c\":1}"}""")
        )
    }

    @Test
    fun nonExistentCustomDataIsUpdatedWithCorrectSuccessCount() {
        val sql = readAssetFile(SQL_FILE_NAME)
        val note = addNoteUsingBasicModel("foo", "bar")
        val card = note.firstCard(col)
        val deck = col.decks.get(note.notetype.did)!!
        card.moveToReviewQueue()

        closeGetStartedScreenIfExists()
        closeBackupCollectionDialogIfExists()
        reviewDeckWithName(deck.name)

        var cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(0).hasCustomData("")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        clickShowAnswerAndAnswerGood()
        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(1).hasCustomData("")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )

        col.db.execute(sql)

        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(1).hasCustomData("""{"c":1}""")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{"cd":"{\"c\":1}"}""")
        )
    }

    @Test
    fun ensureFsrsAndExistingCustomDataAreNotLost() {
        val sql = readAssetFile(SQL_FILE_NAME)
        val note = addNoteUsingBasicModel("foo", "bar")
        val card = note.firstCard(col)
        val deck = col.decks.get(note.notetype.did)!!
        val deckConfig = col.backend.getDeckConfig(deck.id)
        col.backend.updateDeckConfigs(
            deck.id,
            listOf(deckConfig),
            emptyList(),
            cardStateCustomizer = "",
            newCardsIgnoreReviewLimit = true,
            fsrs = true,
            mode = UpdateDeckConfigsMode.UPDATE_DECK_CONFIGS_MODE_NORMAL,
            limits = DeckConfigsForUpdate.CurrentDeck.Limits
                .newBuilder()
                .setNew(100)
                .setNewToday(100)
                .setNewTodayActive(true)
                .setReview(100)
                .setReviewToday(100)
                .setReviewTodayActive(true)
                .build(),
            applyAllParentLimits = true,
            fsrsReschedule = false
        )
        card.moveToReviewQueue()
        card.factor = 2000
        card.ivl = 100
        col.updateCard(card, skipUndoEntry = true)
        col.backend.updateCards(
            listOf(
                card.toBackendCard()
                    .toBuilder()
                    .setCustomData("""{"test":100}""")
                    .setMemoryState(
                        FsrsMemoryState
                            .newBuilder()
                            .setDifficulty(5.0F)
                            .setStability(100.0F)
                    )
                    .setDesiredRetention(0.90F)
                    .build()
            ),
            true
        )

        closeGetStartedScreenIfExists()
        closeBackupCollectionDialogIfExists()
        reviewDeckWithName(deck.name)

        var cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb)
            .hasReps(0)
            .hasCustomData("""{"test":100}""")
            .hasMemoryState(
                FsrsMemoryState
                    .newBuilder()
                    .setDifficulty(5.0F)
                    .setStability(100.0F)
                    .build()
            )
            .hasDesiredRetention(0.90F)
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{"s":100.0,"d":5.0,"dr":0.9,"cd":"{\"test\":100}"}""")
        )
        clickShowAnswerAndAnswerGood()
        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb)
            .hasReps(1)
            .hasCustomData("""{"test":100}""")
            .hasMemoryState(
                FsrsMemoryState
                    .newBuilder()
                    .setDifficulty(5.0F)
                    .setStability(100.0F)
                    .build()
            )
            .hasDesiredRetention(0.90F)
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{"s":100.0,"d":5.0,"dr":0.9,"cd":"{\"test\":100}"}""")
        )

        col.db.execute(sql)

        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb)
            .hasReps(1)
            .hasCustomData("""{"test":100,"c":1}""")
            .hasMemoryState(
                FsrsMemoryState
                    .newBuilder()
                    .setDifficulty(5.0F)
                    .setStability(100.0F)
                    .build()
            )
            .hasDesiredRetention(0.90F)
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{"s":100.0,"d":5.0,"dr":0.9,"cd":"{\"test\":100,\"c\":1}"}""")
        )
    }

    @Test
    fun newAndLearnAndRelearnCardsAreIgnored() {
        val sql = readAssetFile(SQL_FILE_NAME)
        val newNote = addNoteUsingBasicModel("foo", "bar")
        val newCard = newNote.firstCard(col)
        val learnNote = addNoteUsingBasicModel("foo", "bar")
        val learnCard = learnNote.firstCard(col)
        learnCard.moveToLearnQueue()
        val relearnNote = addNoteUsingBasicModel("foo", "bar")
        val relearnCard = relearnNote.firstCard(col)
        relearnCard.moveToRelearnQueue()

        closeGetStartedScreenIfExists()
        closeBackupCollectionDialogIfExists()

        var newCardFromDb = col.getCard(newCard.id)
        assertThat(newCardFromDb)
            .hasCardType(Consts.CARD_TYPE_NEW)
            .hasQueueType(Consts.QUEUE_TYPE_NEW)
            .hasReps(0)
            .hasCustomData("")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${newCardFromDb.id};"),
            equalTo("""{}""")
        )
        var learnCardFromDb = col.getCard(learnCard.id)
        assertThat(learnCardFromDb)
            .hasCardType(Consts.CARD_TYPE_LRN)
            .hasQueueType(Consts.QUEUE_TYPE_LRN)
            .hasReps(0)
            .hasCustomData("")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${learnCardFromDb.id};"),
            equalTo("""{}""")
        )
        var relearnCardFromDb = col.getCard(relearnCard.id)
        assertThat(relearnCardFromDb)
            .hasCardType(Consts.CARD_TYPE_RELEARNING)
            .hasQueueType(Consts.QUEUE_TYPE_LRN)
            .hasReps(0)
            .hasCustomData("")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${relearnCardFromDb.id};"),
            equalTo("""{}""")
        )

        col.db.execute(sql)

        newCardFromDb = col.getCard(newCard.id)
        assertThat(newCardFromDb)
            .hasCardType(Consts.CARD_TYPE_NEW)
            .hasQueueType(Consts.QUEUE_TYPE_NEW)
            .hasReps(0)
            .hasCustomData("")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${newCardFromDb.id};"),
            equalTo("{}")
        )
        learnCardFromDb = col.getCard(learnCard.id)
        assertThat(learnCardFromDb)
            .hasCardType(Consts.CARD_TYPE_LRN)
            .hasQueueType(Consts.QUEUE_TYPE_LRN)
            .hasReps(0)
            .hasCustomData("")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${learnCardFromDb.id};"),
            equalTo("{}")
        )
        relearnCardFromDb = col.getCard(relearnCard.id)
        assertThat(relearnCardFromDb)
            .hasCardType(Consts.CARD_TYPE_RELEARNING)
            .hasQueueType(Consts.QUEUE_TYPE_LRN)
            .hasReps(0)
            .hasCustomData("")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${relearnCardFromDb.id};"),
            equalTo("{}")
        )
    }

    @Test
    fun pressingHardPreservesSuccessfulReviewCount() {
        val sql = readAssetFile(SQL_FILE_NAME)
        val note = addNoteUsingBasicModel("foo", "bar")
        val card = note.firstCard(col)
        val deck = col.decks.get(note.notetype.did)!!
        card.moveToReviewQueue()

        closeGetStartedScreenIfExists()
        closeBackupCollectionDialogIfExists()
        reviewDeckWithName(deck.name)

        var cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(0).hasCustomData("")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        clickShowAnswerAndAnswerGood()
        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(1).hasCustomData("")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        col.getCard(card.id).moveToReviewQueue()
        refreshDeck()
        reviewDeckWithName(deck.name)
        clickShowAnswerAndAnswerHard()
        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(2).hasCustomData("")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        col.getCard(card.id).moveToReviewQueue()
        refreshDeck()
        reviewDeckWithName(deck.name)
        clickShowAnswerAndAnswerGood()
        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(3).hasCustomData("")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )

        col.db.execute(sql)

        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(3).hasCustomData("""{"c":2}""")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{"cd":"{\"c\":2}"}""")
        )
    }

    @Test
    fun goodSuccessfulReviewCountIsZeroWhenTheAgainButtonWasLastPressed() {
        val sql = readAssetFile(SQL_FILE_NAME)
        val note = addNoteUsingBasicModel("foo", "bar")
        val card = note.firstCard(col)
        val deck = col.decks.get(note.notetype.did)!!
        card.moveToReviewQueue()

        closeGetStartedScreenIfExists()
        closeBackupCollectionDialogIfExists()
        reviewDeckWithName(deck.name)

        var cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(0).hasCustomData("")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        clickShowAnswerAndAnswerGood()
        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(1).hasCustomData("")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        col.getCard(card.id).moveToReviewQueue()
        refreshDeck()
        reviewDeckWithName(deck.name)
        clickShowAnswerAndAnswerAgain()
        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(2).hasCustomData("")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        refreshDeck()
        // Finish reviewing the card after pressing Again to move it to the
        // Review state, since we only update review cards
        reviewDeckWithName(deck.name)
        clickShowAnswerAndAnswerGood()
        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(3).hasCustomData("")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )

        col.db.execute(sql)

        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(3).hasCustomData("""{"c":0}""")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{"cd":"{\"c\":0}"}""")
        )
    }

    @Test
    fun goodSuccessfulReviewCountIsCorrectWhenAgainIsPressedAndSuccessfulReviewsExistAfter() {
        val sql = readAssetFile(SQL_FILE_NAME)
        val note = addNoteUsingBasicModel("foo", "bar")
        val card = note.firstCard(col)
        val deck = col.decks.get(note.notetype.did)!!
        card.moveToReviewQueue()

        closeGetStartedScreenIfExists()
        closeBackupCollectionDialogIfExists()
        reviewDeckWithName(deck.name)

        var cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(0).hasCustomData("")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        clickShowAnswerAndAnswerGood()
        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(1).hasCustomData("")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        col.getCard(card.id).moveToReviewQueue()
        refreshDeck()
        reviewDeckWithName(deck.name)
        clickShowAnswerAndAnswerAgain()
        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(2).hasCustomData("")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        refreshDeck()
        reviewDeckWithName(deck.name)
        clickShowAnswerAndAnswerGood()
        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(3).hasCustomData("")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        col.getCard(card.id).moveToReviewQueue()
        refreshDeck()
        reviewDeckWithName(deck.name)
        clickShowAnswerAndAnswerGood()
        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(4).hasCustomData("")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        col.getCard(card.id).moveToReviewQueue()
        refreshDeck()
        reviewDeckWithName(deck.name)
        clickShowAnswerAndAnswerGood()
        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(5).hasCustomData("")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )

        col.db.execute(sql)

        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(5).hasCustomData("""{"c":2}""")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{"cd":"{\"c\":2}"}""")
        )
    }

    @Test
    fun goodSuccessfulReviewCountIsCorrectWhenTheAgainButtonWasNeverPressed() {
        val sql = readAssetFile(SQL_FILE_NAME)
        val note = addNoteUsingBasicModel("foo", "bar")
        val card = note.firstCard(col)
        val deck = col.decks.get(note.notetype.did)!!
        card.moveToReviewQueue()

        closeGetStartedScreenIfExists()
        closeBackupCollectionDialogIfExists()
        reviewDeckWithName(deck.name)

        var cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(0).hasCustomData("")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        clickShowAnswerAndAnswerGood()
        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(1).hasCustomData("")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        col.getCard(card.id).moveToReviewQueue()
        refreshDeck()
        reviewDeckWithName(deck.name)
        clickShowAnswerAndAnswerGood()
        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(2).hasCustomData("")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )

        col.db.execute(sql)

        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(2).hasCustomData("""{"c":2}""")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{"cd":"{\"c\":2}"}""")
        )
    }

    @Test
    fun easySuccessfulReviewCountIsZeroWhenTheAgainButtonWasLastPressed() {
        val sql = readAssetFile(SQL_FILE_NAME)
        val note = addNoteUsingBasicModel("foo", "bar")
        val card = note.firstCard(col)
        val deck = col.decks.get(note.notetype.did)!!
        card.moveToReviewQueue()

        closeGetStartedScreenIfExists()
        closeBackupCollectionDialogIfExists()
        reviewDeckWithName(deck.name)

        var cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(0).hasCustomData("")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        clickShowAnswerAndAnswerEasy()
        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(1).hasCustomData("")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        col.getCard(card.id).moveToReviewQueue()
        refreshDeck()
        reviewDeckWithName(deck.name)
        clickShowAnswerAndAnswerAgain()
        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(2).hasCustomData("")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        refreshDeck()
        // Finish reviewing the card after pressing Again to move it to the
        // Review state, since we only update review cards
        reviewDeckWithName(deck.name)
        clickShowAnswerAndAnswerEasy()
        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(3).hasCustomData("")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )

        col.db.execute(sql)

        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(3).hasCustomData("""{"c":0}""")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{"cd":"{\"c\":0}"}""")
        )
    }

    @Test
    fun easySuccessfulReviewCountIsCorrectWhenAgainIsPressedAndSuccessfulReviewsExistAfter() {
        val sql = readAssetFile(SQL_FILE_NAME)
        val note = addNoteUsingBasicModel("foo", "bar")
        val card = note.firstCard(col)
        val deck = col.decks.get(note.notetype.did)!!
        card.moveToReviewQueue()

        closeGetStartedScreenIfExists()
        closeBackupCollectionDialogIfExists()
        reviewDeckWithName(deck.name)

        var cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(0).hasCustomData("")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        clickShowAnswerAndAnswerEasy()
        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(1).hasCustomData("")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        col.getCard(card.id).moveToReviewQueue()
        refreshDeck()
        reviewDeckWithName(deck.name)
        clickShowAnswerAndAnswerAgain()
        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(2).hasCustomData("")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        refreshDeck()
        reviewDeckWithName(deck.name)
        clickShowAnswerAndAnswerEasy()
        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(3).hasCustomData("")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        col.getCard(card.id).moveToReviewQueue()
        refreshDeck()
        reviewDeckWithName(deck.name)
        clickShowAnswerAndAnswerEasy()
        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(4).hasCustomData("")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        col.getCard(card.id).moveToReviewQueue()
        refreshDeck()
        reviewDeckWithName(deck.name)
        clickShowAnswerAndAnswerEasy()
        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(5).hasCustomData("")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )

        col.db.execute(sql)

        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(5).hasCustomData("""{"c":2}""")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{"cd":"{\"c\":2}"}""")
        )
    }

    @Test
    fun easySuccessfulReviewCountIsCorrectWhenTheAgainButtonWasNeverPressed() {
        val sql = readAssetFile(SQL_FILE_NAME)
        val note = addNoteUsingBasicModel("foo", "bar")
        val card = note.firstCard(col)
        val deck = col.decks.get(note.notetype.did)!!
        card.moveToReviewQueue()

        closeGetStartedScreenIfExists()
        closeBackupCollectionDialogIfExists()
        reviewDeckWithName(deck.name)

        var cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(0).hasCustomData("")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        clickShowAnswerAndAnswerEasy()
        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(1).hasCustomData("")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        col.getCard(card.id).moveToReviewQueue()
        refreshDeck()
        reviewDeckWithName(deck.name)
        clickShowAnswerAndAnswerEasy()
        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(2).hasCustomData("")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )

        col.db.execute(sql)

        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(2).hasCustomData("""{"c":2}""")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{"cd":"{\"c\":2}"}""")
        )
    }

    @Test
    fun filteredDeckPressingHardPreservesSuccessfulReviewCount() {
        val sql = readAssetFile(SQL_FILE_NAME)
        val note = addNoteUsingBasicModel("foo", "bar")
        val card = note.firstCard(col)
        card.moveToReviewQueue()

        val filteredDeckId = col
            .decks
            .newDeck()
            .toBuilder()
            .setName("Filtered Deck" + TimeManager.time.intTimeMS())
            .build()
            .id
        val filteredDeck = col.backend.getOrCreateFilteredDeck(filteredDeckId)
            .toBuilder()
            .setConfig(
                Deck.Filtered
                    .newBuilder()
                    .setReschedule(true)
                    .addSearchTerms(
                        Deck.Filtered.SearchTerm
                            .newBuilder()
                            .setSearch("(is:due OR is:learn is:review)")
                            .setLimit(100)
                            .setOrder(Deck.Filtered.SearchTerm.Order.RANDOM)
                            .build()
                    )
            )
            .build()
        col.backend.addOrUpdateFilteredDeck(filteredDeck)

        closeGetStartedScreenIfExists()
        closeBackupCollectionDialogIfExists()
        reviewDeckWithName(filteredDeck.name)

        var cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(0).hasCustomData("")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        clickShowAnswerAndAnswerGood()
        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(1).hasCustomData("")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        col.getCard(card.id).moveToReviewQueue()
        refreshDeck()
        rebuildFilteredDeck(filteredDeck.name)
        reviewDeckWithName(filteredDeck.name)
        clickShowAnswerAndAnswerHard()
        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(2).hasCustomData("")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        col.getCard(card.id).moveToReviewQueue()
        refreshDeck()
        rebuildFilteredDeck(filteredDeck.name)
        reviewDeckWithName(filteredDeck.name)
        clickShowAnswerAndAnswerGood()
        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(3).hasCustomData("")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )

        col.db.execute(sql)

        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(3).hasCustomData("""{"c":2}""")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{"cd":"{\"c\":2}"}""")
        )
    }

    @Test
    fun filteredDeckGoodSuccessfulReviewCountIsZeroWhenTheAgainButtonWasLastPressed() {
        val sql = readAssetFile(SQL_FILE_NAME)
        val note = addNoteUsingBasicModel("foo", "bar")
        val card = note.firstCard(col)
        card.moveToReviewQueue()

        val filteredDeckId = col
            .decks
            .newDeck()
            .toBuilder()
            .setName("Filtered Deck" + TimeManager.time.intTimeMS())
            .build()
            .id
        val filteredDeck = col.backend.getOrCreateFilteredDeck(filteredDeckId)
            .toBuilder()
            .setConfig(
                Deck.Filtered
                    .newBuilder()
                    .setReschedule(true)
                    .addSearchTerms(
                        Deck.Filtered.SearchTerm
                            .newBuilder()
                            .setSearch("(is:due OR is:learn is:review)")
                            .setLimit(100)
                            .setOrder(Deck.Filtered.SearchTerm.Order.RANDOM)
                            .build()
                    )
            )
            .build()
        col.backend.addOrUpdateFilteredDeck(filteredDeck)

        closeGetStartedScreenIfExists()
        closeBackupCollectionDialogIfExists()
        reviewDeckWithName(filteredDeck.name)

        var cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(0).hasCustomData("")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        clickShowAnswerAndAnswerGood()
        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(1).hasCustomData("")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        col.getCard(card.id).moveToReviewQueue()
        refreshDeck()
        rebuildFilteredDeck(filteredDeck.name)
        reviewDeckWithName(filteredDeck.name)
        clickShowAnswerAndAnswerAgain()
        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(2).hasCustomData("")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        refreshDeck()
        // Finish reviewing the card after pressing Again to move it to the
        // Review state, since we only update review cards
        rebuildFilteredDeck(filteredDeck.name)
        reviewDeckWithName(filteredDeck.name)
        clickShowAnswerAndAnswerGood()
        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(3).hasCustomData("")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )

        col.db.execute(sql)

        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(3).hasCustomData("""{"c":0}""")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{"cd":"{\"c\":0}"}""")
        )
    }

    @Test
    fun filteredDeckGoodSuccessfulReviewCountIsCorrectWhenAgainIsPressedAndSuccessfulReviewsExistAfter() {
        val sql = readAssetFile(SQL_FILE_NAME)
        val note = addNoteUsingBasicModel("foo", "bar")
        val card = note.firstCard(col)
        card.moveToReviewQueue()

        val filteredDeckId = col
            .decks
            .newDeck()
            .toBuilder()
            .setName("Filtered Deck" + TimeManager.time.intTimeMS())
            .build()
            .id
        val filteredDeck = col.backend.getOrCreateFilteredDeck(filteredDeckId)
            .toBuilder()
            .setConfig(
                Deck.Filtered
                    .newBuilder()
                    .setReschedule(true)
                    .addSearchTerms(
                        Deck.Filtered.SearchTerm
                            .newBuilder()
                            .setSearch("(is:due OR is:learn is:review)")
                            .setLimit(100)
                            .setOrder(Deck.Filtered.SearchTerm.Order.RANDOM)
                            .build()
                    )
            )
            .build()
        col.backend.addOrUpdateFilteredDeck(filteredDeck)

        closeGetStartedScreenIfExists()
        closeBackupCollectionDialogIfExists()
        reviewDeckWithName(filteredDeck.name)

        var cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(0).hasCustomData("")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        clickShowAnswerAndAnswerGood()
        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(1).hasCustomData("")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        col.getCard(card.id).moveToReviewQueue()
        refreshDeck()
        rebuildFilteredDeck(filteredDeck.name)
        reviewDeckWithName(filteredDeck.name)
        clickShowAnswerAndAnswerAgain()
        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(2).hasCustomData("")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        refreshDeck()
        rebuildFilteredDeck(filteredDeck.name)
        reviewDeckWithName(filteredDeck.name)
        clickShowAnswerAndAnswerGood()
        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(3).hasCustomData("")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        col.getCard(card.id).moveToReviewQueue()
        refreshDeck()
        rebuildFilteredDeck(filteredDeck.name)
        reviewDeckWithName(filteredDeck.name)
        clickShowAnswerAndAnswerGood()
        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(4).hasCustomData("")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        col.getCard(card.id).moveToReviewQueue()
        refreshDeck()
        rebuildFilteredDeck(filteredDeck.name)
        reviewDeckWithName(filteredDeck.name)
        clickShowAnswerAndAnswerGood()
        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(5).hasCustomData("")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )

        col.db.execute(sql)

        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(5).hasCustomData("""{"c":2}""")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{"cd":"{\"c\":2}"}""")
        )
    }

    @Test
    fun filteredDeckGoodSuccessfulReviewCountIsCorrectWhenTheAgainButtonWasNeverPressed() {
        val sql = readAssetFile(SQL_FILE_NAME)
        val note = addNoteUsingBasicModel("foo", "bar")
        val card = note.firstCard(col)
        card.moveToReviewQueue()

        val filteredDeckId = col
            .decks
            .newDeck()
            .toBuilder()
            .setName("Filtered Deck" + TimeManager.time.intTimeMS())
            .build()
            .id
        val filteredDeck = col.backend.getOrCreateFilteredDeck(filteredDeckId)
            .toBuilder()
            .setConfig(
                Deck.Filtered
                    .newBuilder()
                    .setReschedule(true)
                    .addSearchTerms(
                        Deck.Filtered.SearchTerm
                            .newBuilder()
                            .setSearch("(is:due OR is:learn is:review)")
                            .setLimit(100)
                            .setOrder(Deck.Filtered.SearchTerm.Order.RANDOM)
                            .build()
                    )
            )
            .build()
        col.backend.addOrUpdateFilteredDeck(filteredDeck)

        closeGetStartedScreenIfExists()
        closeBackupCollectionDialogIfExists()
        reviewDeckWithName(filteredDeck.name)

        var cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(0).hasCustomData("")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        clickShowAnswerAndAnswerGood()
        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(1).hasCustomData("")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        col.getCard(card.id).moveToReviewQueue()
        refreshDeck()
        rebuildFilteredDeck(filteredDeck.name)
        reviewDeckWithName(filteredDeck.name)
        clickShowAnswerAndAnswerGood()
        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(2).hasCustomData("")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )

        col.db.execute(sql)

        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(2).hasCustomData("""{"c":2}""")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{"cd":"{\"c\":2}"}""")
        )
    }

    @Test
    fun filteredDeckEasySuccessfulReviewCountIsZeroWhenTheAgainButtonWasLastPressed() {
        val sql = readAssetFile(SQL_FILE_NAME)
        val note = addNoteUsingBasicModel("foo", "bar")
        val card = note.firstCard(col)
        card.moveToReviewQueue()

        val filteredDeckId = col
            .decks
            .newDeck()
            .toBuilder()
            .setName("Filtered Deck" + TimeManager.time.intTimeMS())
            .build()
            .id
        val filteredDeck = col.backend.getOrCreateFilteredDeck(filteredDeckId)
            .toBuilder()
            .setConfig(
                Deck.Filtered
                    .newBuilder()
                    .setReschedule(true)
                    .addSearchTerms(
                        Deck.Filtered.SearchTerm
                            .newBuilder()
                            .setSearch("(is:due OR is:learn is:review)")
                            .setLimit(100)
                            .setOrder(Deck.Filtered.SearchTerm.Order.RANDOM)
                            .build()
                    )
            )
            .build()
        col.backend.addOrUpdateFilteredDeck(filteredDeck)

        closeGetStartedScreenIfExists()
        closeBackupCollectionDialogIfExists()
        reviewDeckWithName(filteredDeck.name)

        var cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(0).hasCustomData("")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        clickShowAnswerAndAnswerEasy()
        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(1).hasCustomData("")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        col.getCard(card.id).moveToReviewQueue()
        refreshDeck()
        rebuildFilteredDeck(filteredDeck.name)
        reviewDeckWithName(filteredDeck.name)
        clickShowAnswerAndAnswerAgain()
        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(2).hasCustomData("")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        refreshDeck()
        rebuildFilteredDeck(filteredDeck.name)
        reviewDeckWithName(filteredDeck.name)
        clickShowAnswerAndAnswerEasy()
        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(3).hasCustomData("")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        col.getCard(card.id).moveToReviewQueue()
        refreshDeck()
        rebuildFilteredDeck(filteredDeck.name)
        reviewDeckWithName(filteredDeck.name)
        clickShowAnswerAndAnswerEasy()
        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(4).hasCustomData("")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        col.getCard(card.id).moveToReviewQueue()
        refreshDeck()
        rebuildFilteredDeck(filteredDeck.name)
        reviewDeckWithName(filteredDeck.name)
        clickShowAnswerAndAnswerEasy()
        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(5).hasCustomData("")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )

        col.db.execute(sql)

        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(5).hasCustomData("""{"c":2}""")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{"cd":"{\"c\":2}"}""")
        )
    }

    @Test
    fun filteredDeckEasySuccessfulReviewCountIsCorrectWhenAgainIsPressedAndSuccessfulReviewsExistAfter() {
        val sql = readAssetFile(SQL_FILE_NAME)
        val note = addNoteUsingBasicModel("foo", "bar")
        val card = note.firstCard(col)
        card.moveToReviewQueue()

        val filteredDeckId = col
            .decks
            .newDeck()
            .toBuilder()
            .setName("Filtered Deck" + TimeManager.time.intTimeMS())
            .build()
            .id
        val filteredDeck = col.backend.getOrCreateFilteredDeck(filteredDeckId)
            .toBuilder()
            .setConfig(
                Deck.Filtered
                    .newBuilder()
                    .setReschedule(true)
                    .addSearchTerms(
                        Deck.Filtered.SearchTerm
                            .newBuilder()
                            .setSearch("(is:due OR is:learn is:review)")
                            .setLimit(100)
                            .setOrder(Deck.Filtered.SearchTerm.Order.RANDOM)
                            .build()
                    )
            )
            .build()
        col.backend.addOrUpdateFilteredDeck(filteredDeck)

        closeGetStartedScreenIfExists()
        closeBackupCollectionDialogIfExists()
        reviewDeckWithName(filteredDeck.name)

        var cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(0).hasCustomData("")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        clickShowAnswerAndAnswerEasy()
        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(1).hasCustomData("")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        col.getCard(card.id).moveToReviewQueue()
        refreshDeck()
        rebuildFilteredDeck(filteredDeck.name)
        reviewDeckWithName(filteredDeck.name)
        clickShowAnswerAndAnswerAgain()
        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(2).hasCustomData("")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        refreshDeck()
        rebuildFilteredDeck(filteredDeck.name)
        reviewDeckWithName(filteredDeck.name)
        clickShowAnswerAndAnswerEasy()
        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(3).hasCustomData("")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        col.getCard(card.id).moveToReviewQueue()
        refreshDeck()
        rebuildFilteredDeck(filteredDeck.name)
        reviewDeckWithName(filteredDeck.name)
        clickShowAnswerAndAnswerEasy()
        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(4).hasCustomData("")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        col.getCard(card.id).moveToReviewQueue()
        refreshDeck()
        rebuildFilteredDeck(filteredDeck.name)
        reviewDeckWithName(filteredDeck.name)
        clickShowAnswerAndAnswerEasy()
        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(5).hasCustomData("")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )

        col.db.execute(sql)

        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(5).hasCustomData("""{"c":2}""")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{"cd":"{\"c\":2}"}""")
        )
    }

    @Test
    fun filteredDeckEasySuccessfulReviewCountIsCorrectWhenTheAgainButtonWasNeverPressed() {
        val sql = readAssetFile(SQL_FILE_NAME)
        val note = addNoteUsingBasicModel("foo", "bar")
        val card = note.firstCard(col)
        card.moveToReviewQueue()

        val filteredDeckId = col
            .decks
            .newDeck()
            .toBuilder()
            .setName("Filtered Deck" + TimeManager.time.intTimeMS())
            .build()
            .id
        val filteredDeck = col.backend.getOrCreateFilteredDeck(filteredDeckId)
            .toBuilder()
            .setConfig(
                Deck.Filtered
                    .newBuilder()
                    .setReschedule(true)
                    .addSearchTerms(
                        Deck.Filtered.SearchTerm
                            .newBuilder()
                            .setSearch("(is:due OR is:learn is:review)")
                            .setLimit(100)
                            .setOrder(Deck.Filtered.SearchTerm.Order.RANDOM)
                            .build()
                    )
            )
            .build()
        col.backend.addOrUpdateFilteredDeck(filteredDeck)

        closeGetStartedScreenIfExists()
        closeBackupCollectionDialogIfExists()
        reviewDeckWithName(filteredDeck.name)

        var cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(0).hasCustomData("")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        clickShowAnswerAndAnswerEasy()
        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(1).hasCustomData("")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        col.getCard(card.id).moveToReviewQueue()
        refreshDeck()
        rebuildFilteredDeck(filteredDeck.name)
        reviewDeckWithName(filteredDeck.name)
        clickShowAnswerAndAnswerEasy()
        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(2).hasCustomData("")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )

        col.db.execute(sql)

        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb).hasReps(2).hasCustomData("""{"c":2}""")
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{"cd":"{\"c\":2}"}""")
        )
    }

}
