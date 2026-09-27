# One directory per Scenario, with Templates duplicated

Every **Scenario** is a directory `~/Library/Application Support/Clickify/Scenarios/<id>/`
holding `scenario.json` and `templates/`. Two **Scenario**s that use the same button will hold
**two copies** of the same **Template** — deliberately, not by oversight.

A shared image store (named by content hash) would save disk but would force reference counting
on delete: delete **Scenario** A and delete the image too and B breaks; do not delete it and the
store grows forever. That is the kind of quiet bug almost nobody writes a test for. In exchange
for a few hundred duplicated KB we get: delete = delete a directory, duplicate = copy a
directory, export/import = zip a directory.

`UserDefaults` now holds only the selected **Scenario** id and the **Simple mode** configuration.
