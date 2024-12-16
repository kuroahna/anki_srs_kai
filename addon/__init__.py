import anki
import aqt
from aqt.qt import QAction


anki_version = tuple(int(segment) for segment in aqt.appVersion.split("."))
if anki_version < (23, 10, 1):
    raise Exception("Minimum supported Anki version is 23.10.1")


def update_cards():
    collection = aqt.mw.col
    if collection is None:
        raise Exception("collection is not available")

    with open("update_custom_data.sql", "r") as file:
        sql = file.read()
        collection.db.execute(sql)


if __name__ != "plugin":
    action = QAction(
        "AnkiSrsKai: Update cards with consecutive successful review counts",
        aqt.mw,
        triggered=update_cards,
    )
    aqt.mw.form.menuTools.addAction(action)
