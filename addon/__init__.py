import anki
import aqt
import os
from aqt.qt import QAction


anki_version = tuple(int(segment) for segment in aqt.appVersion.split("."))
if anki_version < (25, 07, 5):
    raise Exception("Minimum supported Anki version is 25.07.5")


def update_cards():
    collection = aqt.mw.col
    if collection is None:
        raise Exception("collection is not available")

    current_path = os.path.dirname(os.path.abspath(__file__))
    with open(os.path.join(current_path, "update_custom_data.sql"), "r") as file:
        sql = file.read()
        collection.db.execute(sql)


if __name__ != "plugin":
    action = QAction(
        "AnkiSrsKai: Update cards with consecutive successful review counts",
        aqt.mw,
        triggered=update_cards,
    )
    aqt.mw.form.menuTools.addAction(action)
