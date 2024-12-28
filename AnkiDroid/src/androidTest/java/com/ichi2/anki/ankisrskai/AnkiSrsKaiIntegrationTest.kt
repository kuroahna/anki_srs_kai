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
import com.ichi2.anki.ankisrskai.AnkiSrsKaiTestUtils.Companion.disableAnimations
import com.ichi2.anki.ankisrskai.AnkiSrsKaiTestUtils.Companion.hasCardType
import com.ichi2.anki.ankisrskai.AnkiSrsKaiTestUtils.Companion.hasCustomData
import com.ichi2.anki.ankisrskai.AnkiSrsKaiTestUtils.Companion.hasDesiredRetention
import com.ichi2.anki.ankisrskai.AnkiSrsKaiTestUtils.Companion.hasDue
import com.ichi2.anki.ankisrskai.AnkiSrsKaiTestUtils.Companion.hasDueWithinRange
import com.ichi2.anki.ankisrskai.AnkiSrsKaiTestUtils.Companion.hasEaseFactor
import com.ichi2.anki.ankisrskai.AnkiSrsKaiTestUtils.Companion.hasInterval
import com.ichi2.anki.ankisrskai.AnkiSrsKaiTestUtils.Companion.hasIntervalWithinRange
import com.ichi2.anki.ankisrskai.AnkiSrsKaiTestUtils.Companion.hasLapses
import com.ichi2.anki.ankisrskai.AnkiSrsKaiTestUtils.Companion.hasMemoryState
import com.ichi2.anki.ankisrskai.AnkiSrsKaiTestUtils.Companion.hasQueueType
import com.ichi2.anki.ankisrskai.AnkiSrsKaiTestUtils.Companion.hasRemainingSteps
import com.ichi2.anki.ankisrskai.AnkiSrsKaiTestUtils.Companion.hasReps
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnkiSrsKaiIntegrationTest : InstrumentedTest() {

    companion object {
        private const val SCHEDULER_FILE_NAME = "anki_srs_kai.js"
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
        disableAnimations()
        runBlocking {
            CollectionManager.deleteCollectionDirectory()
        }
    }

    @Test
    fun easeRewardCounterIsResetWhenPressingAgain() {
        val deckOptions = """
const deckOptions = {
    "Global Settings": {
        easeReward: {
            minimumConsecutiveSuccessfulReviewsRequiredForReward: 1,
            baseEaseReward: 0.05,
            stepEaseReward: 0.05,
            minimumEase: 1.30,
            maximumEase: 2.50,
        },
        scheduler: {
            enableFuzz: false,
            maximumInterval: 36500,
            intervalModifier: 1.00,
            calculateHardMultiplier: (currentEaseFactor, currentInterval) => {
                return 2.0;
            },
            calculateGoodMultiplier: (currentEaseFactor, currentInterval) => {
                return 3.0;
            },
            calculateEasyMultiplier: (currentEaseFactor, currentInterval) => {
                return 4.0;
            },
        },
    },
};
        """
        val scheduler = readAssetFile(SCHEDULER_FILE_NAME)
        col.config.set("cardStateCustomizer", deckOptions + scheduler)
        val note = addNoteUsingBasicModel("foo", "bar")
        val card = note.firstCard(col)
        val deck = col.decks.get(note.notetype.did)!!
        card.moveToReviewQueue()
        card.factor = 2000
        card.ivl = 100
        col.updateCard(card, skipUndoEntry = true)

        closeGetStartedScreenIfExists()
        closeBackupCollectionDialogIfExists()
        reviewDeckWithName(deck.name)

        var cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb)
            .hasDue(0)
            .hasInterval(100)
            .hasEaseFactor(2000)
            .hasReps(0)
            .hasCustomData("")

        clickShowAnswerAndAnswerGood()

        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb)
            .hasDue(300)
            .hasInterval(300)
            .hasEaseFactor(2050)
            .hasReps(1)
            .hasCustomData("""{"c":1}""")

        col.getCard(card.id).moveToReviewQueue()
        refreshDeck()
        reviewDeckWithName(deck.name)
        clickShowAnswerAndAnswerAgain()

        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb)
            .hasCardType(Consts.CARD_TYPE_RELEARNING)
            .hasQueueType(Consts.QUEUE_TYPE_LRN)
            .hasDue(cardFromDb.toBackendCard().due)
            .hasInterval(1)
            .hasEaseFactor(1850)
            .hasReps(2)
            .hasLapses(1)
            .hasRemainingSteps(1)
            .hasCustomData("")

        refreshDeck()
        reviewDeckWithName(deck.name)
        clickShowAnswerAndAnswerGood()

        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb)
            .hasDue(1)
            .hasInterval(1)
            .hasEaseFactor(1850)
            .hasReps(3)
            .hasLapses(1)
            .hasCustomData("")
    }

    @Test
    fun easeRewardIsNotAppliedWhenPressingHard() {
        val deckOptions = """
const deckOptions = {
    "Global Settings": {
        easeReward: {
            minimumConsecutiveSuccessfulReviewsRequiredForReward: 3,
            baseEaseReward: 0.05,
            stepEaseReward: 0.05,
            minimumEase: 1.30,
            maximumEase: 2.50,
        },
        scheduler: {
            enableFuzz: false,
            maximumInterval: 36500,
            intervalModifier: 1.00,
            calculateHardMultiplier: (currentEaseFactor, currentInterval) => {
                return 2.0;
            },
            calculateGoodMultiplier: (currentEaseFactor, currentInterval) => {
                return 3.0;
            },
            calculateEasyMultiplier: (currentEaseFactor, currentInterval) => {
                return 4.0;
            },
        },
    },
};
        """
        val scheduler = readAssetFile(SCHEDULER_FILE_NAME)
        col.config.set("cardStateCustomizer", deckOptions + scheduler)
        val note = addNoteUsingBasicModel("foo", "bar")
        val card = note.firstCard(col)
        val deck = col.decks.get(note.notetype.did)!!
        card.moveToReviewQueue()
        card.factor = 2000
        card.ivl = 100
        col.updateCard(card, skipUndoEntry = true)

        closeGetStartedScreenIfExists()
        closeBackupCollectionDialogIfExists()
        reviewDeckWithName(deck.name)

        var cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb)
            .hasDue(0)
            .hasInterval(100)
            .hasEaseFactor(2000)
            .hasReps(0)
            .hasCustomData("")

        clickShowAnswerAndAnswerHard()

        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb)
            .hasDue(200)
            .hasInterval(200)
            .hasEaseFactor(1850)
            .hasReps(1)
            .hasCustomData("")

        col.getCard(card.id).moveToReviewQueue()
        refreshDeck()
        reviewDeckWithName(deck.name)
        clickShowAnswerAndAnswerHard()

        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb)
            .hasDue(400)
            .hasInterval(400)
            .hasEaseFactor(1700)
            .hasReps(2)
            .hasCustomData("")

        col.getCard(card.id).moveToReviewQueue()
        refreshDeck()
        reviewDeckWithName(deck.name)
        clickShowAnswerAndAnswerHard()

        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb)
            .hasDue(800)
            .hasInterval(800)
            .hasEaseFactor(1550)
            .hasReps(3)
            .hasCustomData("")

        col.getCard(card.id).moveToReviewQueue()
        refreshDeck()
        reviewDeckWithName(deck.name)
        clickShowAnswerAndAnswerHard()

        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb)
            .hasDue(1600)
            .hasInterval(1600)
            .hasEaseFactor(1400)
            .hasReps(4)
            .hasCustomData("")
    }

    @Test
    fun easeRewardIsAppliedWhenPressingGood() {
        val deckOptions = """
const deckOptions = {
    "Global Settings": {
        easeReward: {
            minimumConsecutiveSuccessfulReviewsRequiredForReward: 3,
            baseEaseReward: 0.05,
            stepEaseReward: 0.05,
            minimumEase: 1.30,
            maximumEase: 2.50,
        },
        scheduler: {
            enableFuzz: false,
            maximumInterval: 36500,
            intervalModifier: 1.00,
            calculateHardMultiplier: (currentEaseFactor, currentInterval) => {
                return 2.0;
            },
            calculateGoodMultiplier: (currentEaseFactor, currentInterval) => {
                return 3.0;
            },
            calculateEasyMultiplier: (currentEaseFactor, currentInterval) => {
                return 4.0;
            },
        },
    },
};
        """
        val scheduler = readAssetFile(SCHEDULER_FILE_NAME)
        col.config.set("cardStateCustomizer", deckOptions + scheduler)
        val note = addNoteUsingBasicModel("foo", "bar")
        val card = note.firstCard(col)
        val deck = col.decks.get(note.notetype.did)!!
        card.moveToReviewQueue()
        card.factor = 2000
        card.ivl = 100
        col.updateCard(card, skipUndoEntry = true)

        closeGetStartedScreenIfExists()
        closeBackupCollectionDialogIfExists()
        reviewDeckWithName(deck.name)

        var cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb)
            .hasDue(0)
            .hasInterval(100)
            .hasEaseFactor(2000)
            .hasReps(0)
            .hasCustomData("")

        clickShowAnswerAndAnswerGood()

        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb)
            .hasDue(300)
            .hasInterval(300)
            .hasEaseFactor(2000)
            .hasReps(1)
            .hasCustomData("""{"c":1}""")

        col.getCard(card.id).moveToReviewQueue()
        refreshDeck()
        reviewDeckWithName(deck.name)
        clickShowAnswerAndAnswerGood()

        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb)
            .hasDue(900)
            .hasInterval(900)
            .hasEaseFactor(2000)
            .hasReps(2)
            .hasCustomData("""{"c":2}""")

        col.getCard(card.id).moveToReviewQueue()
        refreshDeck()
        reviewDeckWithName(deck.name)
        clickShowAnswerAndAnswerGood()

        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb)
            .hasDue(2700)
            .hasInterval(2700)
            .hasEaseFactor(2050)
            .hasReps(3)
            .hasCustomData("""{"c":3}""")

        col.getCard(card.id).moveToReviewQueue()
        refreshDeck()
        reviewDeckWithName(deck.name)
        clickShowAnswerAndAnswerGood()

        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb)
            .hasDue(8100)
            .hasInterval(8100)
            .hasEaseFactor(2150)
            .hasReps(4)
            .hasCustomData("""{"c":4}""")
    }

    @Test
    fun easeRewardIsAppliedWhenPressingEasy() {
        val deckOptions = """
const deckOptions = {
    "Global Settings": {
        easeReward: {
            minimumConsecutiveSuccessfulReviewsRequiredForReward: 3,
            baseEaseReward: 0.05,
            stepEaseReward: 0.05,
            minimumEase: 1.30,
            maximumEase: 3.00,
        },
        scheduler: {
            enableFuzz: false,
            maximumInterval: 36500,
            intervalModifier: 1.00,
            calculateHardMultiplier: (currentEaseFactor, currentInterval) => {
                return 2.0;
            },
            calculateGoodMultiplier: (currentEaseFactor, currentInterval) => {
                return 3.0;
            },
            calculateEasyMultiplier: (currentEaseFactor, currentInterval) => {
                return 4.0;
            },
        },
    },
};
        """
        val scheduler = readAssetFile(SCHEDULER_FILE_NAME)
        col.config.set("cardStateCustomizer", deckOptions + scheduler)
        val note = addNoteUsingBasicModel("foo", "bar")
        val card = note.firstCard(col)
        val deck = col.decks.get(note.notetype.did)!!
        card.moveToReviewQueue()
        card.factor = 2000
        card.ivl = 100
        col.updateCard(card, skipUndoEntry = true)

        closeGetStartedScreenIfExists()
        closeBackupCollectionDialogIfExists()
        reviewDeckWithName(deck.name)

        var cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb)
            .hasDue(0)
            .hasInterval(100)
            .hasEaseFactor(2000)
            .hasReps(0)
            .hasCustomData("")

        clickShowAnswerAndAnswerEasy()

        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb)
            .hasDue(400)
            .hasInterval(400)
            .hasEaseFactor(2150)
            .hasReps(1)
            .hasCustomData("""{"c":1}""")

        col.getCard(card.id).moveToReviewQueue()
        refreshDeck()
        reviewDeckWithName(deck.name)
        clickShowAnswerAndAnswerEasy()

        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb)
            .hasDue(1600)
            .hasInterval(1600)
            .hasEaseFactor(2300)
            .hasReps(2)
            .hasCustomData("""{"c":2}""")

        col.getCard(card.id).moveToReviewQueue()
        refreshDeck()
        reviewDeckWithName(deck.name)
        clickShowAnswerAndAnswerEasy()

        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb)
            .hasDue(6400)
            .hasInterval(6400)
            .hasEaseFactor(2500)
            .hasReps(3)
            .hasCustomData("""{"c":3}""")

        col.getCard(card.id).moveToReviewQueue()
        refreshDeck()
        reviewDeckWithName(deck.name)
        clickShowAnswerAndAnswerEasy()

        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb)
            .hasDue(25600)
            .hasInterval(25600)
            .hasEaseFactor(2750)
            .hasReps(4)
            .hasCustomData("""{"c":4}""")
    }

    @Test
    fun multipliersWithCustomFunctionAndEaseReward() {
        val deckOptions = """
const deckOptions = {
    "Global Settings": {
        easeReward: {
            minimumConsecutiveSuccessfulReviewsRequiredForReward: 1,
            baseEaseReward: 0.05,
            stepEaseReward: 0.05,
            minimumEase: 1.30,
            maximumEase: 2.50,
        },
        scheduler: {
            enableFuzz: false,
            maximumInterval: 36500,
            intervalModifier: 1.00,
            calculateHardMultiplier: (currentEaseFactor, currentInterval) => {
                // Function chosen such that f(100) = 2.0
                return (currentEaseFactor / Math.pow(currentInterval, 0.10)) + 0.7381;
            },
            calculateGoodMultiplier: (currentEaseFactor, currentInterval) => {
                // Function chosen such that f(100) = 3.0
                return (currentEaseFactor / Math.pow(currentInterval, 0.10)) + 1.7381;
            },
            calculateEasyMultiplier: (currentEaseFactor, currentInterval) => {
                // Function chosen such that f(100) = 4.0
                return (currentEaseFactor / Math.pow(currentInterval, 0.10)) + 2.7381;
            },
        },
    },
};
        """
        val scheduler = readAssetFile(SCHEDULER_FILE_NAME)
        col.config.set("cardStateCustomizer", deckOptions + scheduler)
        val hardNote = addNoteUsingBasicModel("foo", "hard")
        val hardCard = hardNote.firstCard(col)
        val deck = col.decks.get(hardNote.notetype.did)!!
        hardCard.moveToReviewQueue()
        hardCard.factor = 2000
        hardCard.ivl = 100
        col.updateCard(hardCard, skipUndoEntry = true)

        val goodNote = addNoteUsingBasicModel("foo", "good")
        val goodCard = goodNote.firstCard(col)
        goodCard.moveToReviewQueue()
        goodCard.factor = 2000
        goodCard.ivl = 100
        col.updateCard(goodCard, skipUndoEntry = true)

        val easyNote = addNoteUsingBasicModel("foo", "easy")
        val easyCard = easyNote.firstCard(col)
        easyCard.moveToReviewQueue()
        easyCard.factor = 2000
        easyCard.ivl = 100
        col.updateCard(easyCard, skipUndoEntry = true)

        closeGetStartedScreenIfExists()
        closeBackupCollectionDialogIfExists()
        reviewDeckWithName(deck.name)

        val queuedCards = col.backend.getQueuedCards(100, false).cardsList

        var cardFromDb = col.getCard(queuedCards[0].card.id)
        assertThat(cardFromDb)
            .hasDue(0)
            .hasInterval(100)
            .hasEaseFactor(2000)
            .hasReps(0)
            .hasCustomData("")

        clickShowAnswerAndAnswerHard()
        cardFromDb = col.getCard(queuedCards[0].card.id)
        assertThat(cardFromDb)
            .hasDue(200)
            .hasInterval(200)
            .hasEaseFactor(1850)
            .hasReps(1)
            .hasCustomData("")

        cardFromDb = col.getCard(queuedCards[1].card.id)
        assertThat(cardFromDb)
            .hasDue(0)
            .hasInterval(100)
            .hasEaseFactor(2000)
            .hasReps(0)
            .hasCustomData("")
        clickShowAnswerAndAnswerGood()
        cardFromDb = col.getCard(queuedCards[1].card.id)
        assertThat(cardFromDb)
            .hasDue(300)
            .hasInterval(300)
            .hasEaseFactor(2050)
            .hasReps(1)
            .hasCustomData("""{"c":1}""")

        cardFromDb = col.getCard(queuedCards[2].card.id)
        assertThat(cardFromDb)
            .hasDue(0)
            .hasInterval(100)
            .hasEaseFactor(2000)
            .hasReps(0)
            .hasCustomData("")
        clickShowAnswerAndAnswerEasy()
        cardFromDb = col.getCard(queuedCards[2].card.id)
        assertThat(cardFromDb)
            .hasDue(400)
            .hasInterval(400)
            .hasEaseFactor(2200)
            .hasReps(1)
            .hasCustomData("""{"c":1}""")
    }

    @Test
    fun ensureFsrsAndExistingCustomDataAreNotLost() {
        val deckOptions = """
const deckOptions = {
    "Global Settings": {
        easeReward: {
            minimumConsecutiveSuccessfulReviewsRequiredForReward: 1,
            baseEaseReward: 0.05,
            stepEaseReward: 0.05,
            minimumEase: 1.30,
            maximumEase: 2.50,
        },
        scheduler: {
            enableFuzz: false,
            maximumInterval: 36500,
            intervalModifier: 1.00,
            calculateHardMultiplier: (currentEaseFactor, currentInterval) => {
                return 2.0;
            },
            calculateGoodMultiplier: (currentEaseFactor, currentInterval) => {
                return 3.0;
            },
            calculateEasyMultiplier: (currentEaseFactor, currentInterval) => {
                return 4.0;
            },
        },
    },
};
        """
        val scheduler = readAssetFile(SCHEDULER_FILE_NAME)
        val note = addNoteUsingBasicModel("foo", "bar")
        val card = note.firstCard(col)
        val deck = col.decks.get(note.notetype.did)!!
        val deckConfig = col.backend.getDeckConfig(deck.id)
        col.backend.updateDeckConfigs(
            deck.id,
            listOf(deckConfig),
            emptyList(),
            cardStateCustomizer = deckOptions + scheduler,
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

        // AnkiDroid's Card class currently doesn't contain all the fields, so
        // we have to use the protobuf-generated Card class
        var cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb)
            .hasDue(0)
            .hasInterval(100)
            .hasEaseFactor(2000)
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

        clickShowAnswerAndAnswerGood()

        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb)
            .hasDue(300)
            .hasInterval(300)
            .hasEaseFactor(2050)
            .hasReps(1)
            .hasCustomData("""{"test":100,"c":1}""")
            // Although we set the difficulty and stability to 5.0 and 100.0 respectively, after
            // answering good, new FSRS memory states are calculated using the default weights.
            //
            // Any time the default FSRS weights are updated, these values will change
            //
            // https://github.com/ankitects/anki/blob/24.11/rslib/src/scheduler/answering/mod.rs#L433
            .hasMemoryState(
                FsrsMemoryState
                    .newBuilder()
                    .setDifficulty(4.992F)
                    .setStability(140.777F)
                    .build()
            )
            .hasDesiredRetention(0.90F)
    }

    @Test
    fun turningOffAllSettingsPreservesOriginalAnkiSrsBehaviour() {
        val deckOptions = """
const deckOptions = {
    "Global Settings": {
        easeReward: {
            minimumConsecutiveSuccessfulReviewsRequiredForReward: 0,
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
                return 0.0;
            },
            calculateEasyMultiplier: (currentEaseFactor, currentInterval) => {
                return 0.0;
            },
        },
    },
};
        """
        val scheduler = readAssetFile(SCHEDULER_FILE_NAME)
        col.config.set("cardStateCustomizer", deckOptions + scheduler)
        val hardNote = addNoteUsingBasicModel("foo", "hard")
        val hardCard = hardNote.firstCard(col)
        val deck = col.decks.get(hardNote.notetype.did)!!
        hardCard.moveToReviewQueue()
        hardCard.factor = 2000
        hardCard.ivl = 100
        col.updateCard(hardCard, skipUndoEntry = true)

        val goodNote = addNoteUsingBasicModel("foo", "good")
        val goodCard = goodNote.firstCard(col)
        goodCard.moveToReviewQueue()
        goodCard.factor = 2000
        goodCard.ivl = 100
        col.updateCard(goodCard, skipUndoEntry = true)

        val easyNote = addNoteUsingBasicModel("foo", "easy")
        val easyCard = easyNote.firstCard(col)
        easyCard.moveToReviewQueue()
        easyCard.factor = 2000
        easyCard.ivl = 100
        col.updateCard(easyCard, skipUndoEntry = true)

        closeGetStartedScreenIfExists()
        closeBackupCollectionDialogIfExists()
        reviewDeckWithName(deck.name)

        val queuedCards = col.backend.getQueuedCards(100, false).cardsList

        var schedulingStates = col.backend.getSchedulingStates(queuedCards[0].card.id)
        var cardFromDb = col.getCard(queuedCards[0].card.id)
        assertThat(cardFromDb)
            .hasDue(0)
            .hasInterval(100)
            .hasEaseFactor(2000)
            .hasReps(0)
            .hasCustomData("")
        clickShowAnswerAndAnswerHard()

        cardFromDb = col.getCard(queuedCards[0].card.id)
        assertThat(cardFromDb)
            .hasDue(schedulingStates.hard.normal.review.scheduledDays)
            .hasInterval(schedulingStates.hard.normal.review.scheduledDays)
            .hasEaseFactor(1850)
            .hasReps(1)
            .hasCustomData("")

        schedulingStates = col.backend.getSchedulingStates(queuedCards[1].card.id)
        cardFromDb = col.getCard(queuedCards[1].card.id)
        assertThat(cardFromDb)
            .hasDue(0)
            .hasInterval(100)
            .hasEaseFactor(2000)
            .hasReps(0)
            .hasCustomData("")
        clickShowAnswerAndAnswerGood()
        cardFromDb = col.getCard(queuedCards[1].card.id)
        assertThat(cardFromDb)
            .hasDue(schedulingStates.good.normal.review.scheduledDays)
            .hasInterval(schedulingStates.good.normal.review.scheduledDays)
            .hasEaseFactor(2000)
            .hasReps(1)
            // The number of successful reviews should still be counted so that
            // when the custom scheduler is turned back on, the counter will be
            // accurate
            .hasCustomData("""{"c":1}""")

        schedulingStates = col.backend.getSchedulingStates(queuedCards[2].card.id)
        cardFromDb = col.getCard(queuedCards[2].card.id)
        assertThat(cardFromDb)
            .hasDue(0)
            .hasInterval(100)
            .hasEaseFactor(2000)
            .hasReps(0)
            .hasCustomData("")
        clickShowAnswerAndAnswerEasy()
        cardFromDb = col.getCard(queuedCards[2].card.id)
        assertThat(cardFromDb)
            .hasDue(schedulingStates.easy.normal.review.scheduledDays)
            .hasInterval(schedulingStates.easy.normal.review.scheduledDays)
            .hasEaseFactor(2150)
            .hasReps(1)
            // The number of successful reviews should still be counted so that
            // when the custom scheduler is turned back on, the counter will be
            // accurate
            .hasCustomData("""{"c":1}""")
    }

    @Test
    fun enableFuzz() {
        val deckOptions = """
const deckOptions = {
    "Global Settings": {
        easeReward: {
            minimumConsecutiveSuccessfulReviewsRequiredForReward: 1,
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
                return 2.0;
            },
            calculateGoodMultiplier: (currentEaseFactor, currentInterval) => {
                return 3.0;
            },
            calculateEasyMultiplier: (currentEaseFactor, currentInterval) => {
                return 4.0;
            },
        },
    },
};
        """
        val scheduler = readAssetFile(SCHEDULER_FILE_NAME)
        col.config.set("cardStateCustomizer", deckOptions + scheduler)
        val note = addNoteUsingBasicModel("foo", "bar")
        val card = note.firstCard(col)
        val deck = col.decks.get(note.notetype.did)!!
        card.moveToReviewQueue()
        card.factor = 2000
        card.ivl = 100
        col.updateCard(card, skipUndoEntry = true)

        closeGetStartedScreenIfExists()
        closeBackupCollectionDialogIfExists()
        reviewDeckWithName(deck.name)

        var cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb)
            .hasDue(0)
            .hasInterval(100)
            .hasEaseFactor(2000)
            .hasReps(0)
            .hasCustomData("")

        clickShowAnswerAndAnswerGood()

        cardFromDb = col.getCard(card.id)
        // Unfortunately, we need to assert that the interval is within a range
        // instead of an exact number because enabling fuzz will return a random
        // number. Under the hood, the fuzz seed is based on the card's id which
        // is set to the current system time with no way to create a card with a
        // custom id or set the system time without requiring extra privileges
        assertThat(cardFromDb)
            .hasDueWithinRange(283..318)
            .hasIntervalWithinRange(283..318)
            .hasEaseFactor(2050)
            .hasReps(1)
            .hasCustomData("""{"c":1}""")
    }

    @Test
    fun customDeckOptions() {
        val deckOptions = """
const deckOptions = {
    "Default": {
        easeReward: {
            minimumConsecutiveSuccessfulReviewsRequiredForReward: 1,
            baseEaseReward: 0.05,
            stepEaseReward: 0.05,
            minimumEase: 1.30,
            maximumEase: 2.50,
        },
        scheduler: {
            enableFuzz: false,
            maximumInterval: 36500,
            intervalModifier: 1.00,
            calculateHardMultiplier: (currentEaseFactor, currentInterval) => {
                return 0.0;
            },
            calculateGoodMultiplier: (currentEaseFactor, currentInterval) => {
                return 6.0;
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
            calculateHardMultiplier: (currentEaseFactor, currentInterval) => {
                return 2.0;
            },
            calculateGoodMultiplier: (currentEaseFactor, currentInterval) => {
                return 3.0;
            },
            calculateEasyMultiplier: (currentEaseFactor, currentInterval) => {
                return 4.0;
            },
        },
    },
};
        """
        val scheduler = readAssetFile(SCHEDULER_FILE_NAME)
        col.config.set("cardStateCustomizer", deckOptions + scheduler)
        val note = addNoteUsingBasicModel("foo", "bar")
        val card = note.firstCard(col)
        val deck = col.decks.get(note.notetype.did)!!
        card.moveToReviewQueue()
        card.factor = 2000
        card.ivl = 100
        col.updateCard(card, skipUndoEntry = true)

        closeGetStartedScreenIfExists()
        closeBackupCollectionDialogIfExists()
        reviewDeckWithName(deck.name)

        var cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb)
            .hasDue(0)
            .hasInterval(100)
            .hasEaseFactor(2000)
            .hasReps(0)
            .hasCustomData("")

        clickShowAnswerAndAnswerGood()

        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb)
            .hasDue(600)
            .hasInterval(600)
            .hasEaseFactor(2050)
            .hasReps(1)
            .hasCustomData("""{"c":1}""")
    }

    @Test
    fun filteredDeck() {
        val deckOptions = """
const deckOptions = {
    "Default": {
        easeReward: {
            minimumConsecutiveSuccessfulReviewsRequiredForReward: 1,
            baseEaseReward: 0.05,
            stepEaseReward: 0.05,
            minimumEase: 1.30,
            maximumEase: 2.50,
        },
        scheduler: {
            enableFuzz: false,
            maximumInterval: 36500,
            intervalModifier: 1.00,
            calculateHardMultiplier: (currentEaseFactor, currentInterval) => {
                return 2.0;
            },
            calculateGoodMultiplier: (currentEaseFactor, currentInterval) => {
                return 3.0;
            },
            calculateEasyMultiplier: (currentEaseFactor, currentInterval) => {
                return 4.0;
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
            enableFuzz: false,
            maximumInterval: 36500,
            intervalModifier: 1.00,
            calculateHardMultiplier: (currentEaseFactor, currentInterval) => {
                return 5.0;
            },
            calculateGoodMultiplier: (currentEaseFactor, currentInterval) => {
                return 6.0;
            },
            calculateEasyMultiplier: (currentEaseFactor, currentInterval) => {
                return 7.0;
            },
        },
    },
};
        """
        val scheduler = readAssetFile(SCHEDULER_FILE_NAME)
        col.config.set("cardStateCustomizer", deckOptions + scheduler)
        val hardNote = addNoteUsingBasicModel("foo", "hard")
        val hardCard = hardNote.firstCard(col)
        hardCard.moveToReviewQueue()
        hardCard.factor = 2000
        hardCard.ivl = 100
        col.updateCard(hardCard, skipUndoEntry = true)

        val goodNote = addNoteUsingBasicModel("foo", "good")
        val goodCard = goodNote.firstCard(col)
        goodCard.moveToReviewQueue()
        goodCard.factor = 2000
        goodCard.ivl = 100
        col.updateCard(goodCard, skipUndoEntry = true)

        val easyNote = addNoteUsingBasicModel("foo", "easy")
        val easyCard = easyNote.firstCard(col)
        easyCard.moveToReviewQueue()
        easyCard.factor = 2000
        easyCard.ivl = 100
        col.updateCard(easyCard, skipUndoEntry = true)

        val filteredDeckId =
            col.decks.newDeck().toBuilder().setName("Filtered Deck" + TimeManager.time.intTimeMS())
                .build().id
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

        val queuedCards = col.backend.getQueuedCards(100, false).cardsList

        var cardFromDb = col.getCard(queuedCards[0].card.id)
        assertThat(cardFromDb)
            .hasDue(0)
            .hasInterval(100)
            .hasEaseFactor(2000)
            .hasReps(0)
            .hasCustomData("")
        clickShowAnswerAndAnswerHard()
        cardFromDb = col.getCard(queuedCards[0].card.id)
        assertThat(cardFromDb)
            .hasDue(200)
            .hasInterval(200)
            .hasEaseFactor(1850)
            .hasReps(1)
            .hasCustomData("")

        cardFromDb = col.getCard(queuedCards[1].card.id)
        assertThat(cardFromDb)
            .hasDue(0)
            .hasInterval(100)
            .hasEaseFactor(2000)
            .hasReps(0)
            .hasCustomData("")
        clickShowAnswerAndAnswerGood()
        cardFromDb = col.getCard(queuedCards[1].card.id)
        assertThat(cardFromDb)
            .hasDue(300)
            .hasInterval(300)
            .hasEaseFactor(2050)
            .hasReps(1)
            .hasCustomData("""{"c":1}""")

        cardFromDb = col.getCard(queuedCards[2].card.id)
        assertThat(cardFromDb)
            .hasDue(0)
            .hasInterval(100)
            .hasEaseFactor(2000)
            .hasReps(0)
            .hasCustomData("")
        clickShowAnswerAndAnswerEasy()
        cardFromDb = col.getCard(queuedCards[2].card.id)
        assertThat(cardFromDb)
            .hasDue(400)
            .hasInterval(400)
            .hasEaseFactor(2200)
            .hasReps(1)
            .hasCustomData("""{"c":1}""")
    }

    @Test
    fun filteredDeckEaseRewardIsResetWhenPressingAgain() {
        val deckOptions = """
const deckOptions = {
    "Default": {
        easeReward: {
            minimumConsecutiveSuccessfulReviewsRequiredForReward: 1,
            baseEaseReward: 0.05,
            stepEaseReward: 0.05,
            minimumEase: 1.30,
            maximumEase: 2.50,
        },
        scheduler: {
            enableFuzz: false,
            maximumInterval: 36500,
            intervalModifier: 1.00,
            calculateHardMultiplier: (currentEaseFactor, currentInterval) => {
                return 2.0;
            },
            calculateGoodMultiplier: (currentEaseFactor, currentInterval) => {
                return 3.0;
            },
            calculateEasyMultiplier: (currentEaseFactor, currentInterval) => {
                return 4.0;
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
            enableFuzz: false,
            maximumInterval: 36500,
            intervalModifier: 1.00,
            calculateHardMultiplier: (currentEaseFactor, currentInterval) => {
                return 5.0;
            },
            calculateGoodMultiplier: (currentEaseFactor, currentInterval) => {
                return 6.0;
            },
            calculateEasyMultiplier: (currentEaseFactor, currentInterval) => {
                return 7.0;
            },
        },
    },
};
        """
        val scheduler = readAssetFile(SCHEDULER_FILE_NAME)
        col.config.set("cardStateCustomizer", deckOptions + scheduler)
        val note = addNoteUsingBasicModel("foo", "bar")
        val card = note.firstCard(col)
        card.moveToReviewQueue()
        card.factor = 2000
        card.ivl = 100
        col.updateCard(card, skipUndoEntry = true)

        val filteredDeckId =
            col.decks.newDeck().toBuilder().setName("Filtered Deck" + TimeManager.time.intTimeMS())
                .build().id
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
        assertThat(cardFromDb)
            .hasDue(0)
            .hasInterval(100)
            .hasEaseFactor(2000)
            .hasReps(0)
            .hasCustomData("")

        clickShowAnswerAndAnswerGood()

        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb)
            .hasDue(300)
            .hasInterval(300)
            .hasEaseFactor(2050)
            .hasReps(1)
            .hasCustomData("""{"c":1}""")

        col.getCard(card.id).moveToReviewQueue()
        refreshDeck()
        rebuildFilteredDeck(filteredDeck.name)
        reviewDeckWithName(filteredDeck.name)
        clickShowAnswerAndAnswerAgain()

        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb)
            .hasCardType(Consts.CARD_TYPE_RELEARNING)
            .hasQueueType(Consts.QUEUE_TYPE_LRN)
            .hasDue(cardFromDb.toBackendCard().due)
            .hasInterval(1)
            .hasEaseFactor(1850)
            .hasReps(2)
            .hasLapses(1)
            .hasRemainingSteps(1)
            .hasCustomData("")

        refreshDeck()
        rebuildFilteredDeck(filteredDeck.name)
        reviewDeckWithName(filteredDeck.name)
        clickShowAnswerAndAnswerGood()

        cardFromDb = col.getCard(card.id)
        assertThat(cardFromDb)
            .hasDue(1)
            .hasInterval(1)
            .hasEaseFactor(1850)
            .hasReps(3)
            .hasLapses(1)
            .hasCustomData("")
    }
}
