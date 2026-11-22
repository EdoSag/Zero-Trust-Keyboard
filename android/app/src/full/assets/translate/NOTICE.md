# Translation engine

`translator.js` and `worker/` are Firefox's translation engine, Bergamot
(`@browsermt/bergamot-translator` 0.4.9, https://github.com/browsermt/bergamot-translator),
under the Mozilla Public License 2.0 (https://mozilla.org/MPL/2.0/). They are
unchanged, and are the same files ZT Browser ships. `driver.js` and `index.html`
are ZT's own.

The language models are Mozilla's Firefox Translations models
(https://github.com/mozilla/translations, MPL-2.0). They are not part of the app:
the user downloads the languages they want from ZT Browser's "translation-models"
release, where a workflow of that repository copies them with a signed list.
