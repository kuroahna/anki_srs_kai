UPDATE cards
-- Merge our customData changes into the existing JSON object
SET data = json_patch(
    -- It is safe to assume that every new card has an empty JSON object '{}' in the data column
    data,
    json_object(
        -- Anki stores the customData in the cards table under the data column as a JSON string in
        -- the "cd" field
        -- https://github.com/ankitects/anki/blob/25.07.5/rslib/src/storage/card/data.rs#L60
        'cd',
        -- Remove the single quotes introduced by calling quote
        -- https://www.sqlite.org/lang_corefunc.html#quote
        replace(
            -- We need to quote the JSON object returned by SQL because Anki stores a JSON string in
            -- $.cd
            --
            -- In the data column, it is of the format
            --     {"pos":9008,"cd":"{\"c\":1}"}
            -- And calling quote(...) will escape the double quotes for the JSON object returned by
            -- json_set, but will leave leading/trailing single quotes
            --     {"pos":9008,"cd":"'{\"c\":3}'"}
            quote(
                json_set(
                    -- The custom data might not exist if this is a new card
                    IFNULL(json_extract(cards.data, '$.cd'), '{}'),
                    '$.c',
                    (
                        -- Count the number of consecutive successful reviews
                        SELECT COUNT(revlog.id)
                        FROM revlog
                        WHERE revlog.cid = cards.id
                            -- 0 = Learning
                            -- 1 = Review
                            -- 2 = Relearning
                            -- 3 = Filtered (Old Anki versions called this "Cram" or "Early". It's
                            --               assigned when reviewing cards before they're due, or
                            --               when rescheduling is disabled)
                            -- 4 = Manual
                            -- 5 = Rescheduled
                            -- https://github.com/ankitects/anki/blob/25.07.5/rslib/src/revlog/mod.rs#L65-L76
                            --
                            -- We only want to find cards that have been reviewed normally.
                            -- Reviewing cards before they're due or when rescheduling is disabled
                            -- are ignored because we want to keep the same behaviour as the custom
                            -- scheduler
                            AND revlog.type = 1
                            -- review: 1 (again), 2 (hard), 3 (good), 4 (easy)
                            -- learn/relearn: 1 (again), 2 (good), 3 (easy)
                            -- 0 represents manual rescheduling
                            -- https://github.com/ankitects/anki/blob/25.07.5/rslib/src/revlog/mod.rs#L43
                            --
                            -- A successful review is if we have pressed ease=3 (Good) or
                            -- ease=4 (Easy)
                            AND revlog.ease IN (3, 4)
                            -- revlog ids are epoch-milliseconds timestamp of when you did the
                            -- review
                            -- https://github.com/ankitects/anki/blob/25.07.5/rslib/src/scheduler/answering/revlog.rs#L44
                            --
                            -- We want to find the number of successful reviews after the last time
                            -- ease=1 (Again) was pressed
                            AND revlog.id > (
                                -- Find the last revlog id where ease=1 (Again) was pressed
                                --
                                -- 3 cases to consider
                                --
                                -- 1. ease=1 (Again) was pressed at the end of the revlog
                                -- 2. ease=1 (Again) was pressed in the middle of the revlog, and
                               --     the reviews after this was either ease=2 (Hard), ease=3 (Good)
                               --     or ease=4 (Easy)
                                -- 3. ease=1 (Again) was never pressed
                                --
                                -- For case (1), this means that the number of consecutive
                                -- successful reviews should be 0, since there is no revlog after
                                -- this
                                --
                                -- For case (2), this means that the number of consecutive
                                -- successful reviews should be n, where n is the number of
                                -- consecutive successful reviews.
                                --
                                -- For case (3), this means that the number of consecutive
                                -- successful reviews should be n, where n is the number of
                                -- consecutive successful reviews. In this case, MAX(id) is null,
                                -- and hence we want to return 0, so that we select all the revlogs
                                -- for the card
                                SELECT IFNULL(MAX(revlog.id), 0)
                                FROM revlog
                                WHERE revlog.cid = cards.id
                                    -- 0 = Learning
                                    -- 1 = Review
                                    -- 2 = Relearning
                                    -- 3 = Filtered (Old Anki versions called this "Cram" or
                                    --               "Early". It's assigned when reviewing cards
                                    --               before they're due, or when rescheduling is
                                    --               disabled)
                                    -- 4 = Manual
                                    -- 5 = Rescheduled
                                    -- https://github.com/ankitects/anki/blob/25.07.5/rslib/src/revlog/mod.rs#L65-L76
                                    --
                                    -- We only want to find cards that have been reviewed normally.
                                    -- Reviewing cards before they're due or when rescheduling is
                                    -- disabled are ignored because we want to keep the same
                                    -- behaviour as the custom scheduler
                                    AND revlog.type = 1
                                    -- review: 1 (again), 2 (hard), 3 (good), 4 (easy)
                                    -- learn/relearn: 1 (again), 2 (good), 3 (easy)
                                    -- 0 represents manual rescheduling
                                    -- https://github.com/ankitects/anki/blob/25.07.5/rslib/src/revlog/mod.rs#L43
                                    --
                                    -- ease=1 (Again) was pressed
                                    AND revlog.ease = 1
                                ORDER BY revlog.id ASC
                            )
                    )
                )
            ),
            '''',
            ''
        )
    )
)
-- 0 = New
-- 1 = Learn
-- 2 = Review
-- 3 = Relearn
-- https://github.com/ankitects/anki/blob/25.07.5/rslib/src/card/mod.rs#L40
--
-- Only modify review cards because new, learn, and relearn cards have not had a successful review
-- yet. It'll also avoid adding unnecessary customData to the cards which will take up extra space
-- in the database per card
WHERE type = 2;
