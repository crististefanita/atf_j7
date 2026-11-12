# Dovezi de calificare pentru candidatul 0.12.0

Acest dosar păstrează doar rapoartele care fac verificările candidatului ușor de auditat. Capturile sunt încorporate lângă aserțiunile Extent; nu se dublează aici ca fișiere PNG.

- [`components.html`](components.html) — suita JVM completă: 202 teste, 0 eșecuri, 0 erori, 10 benchmarkuri opționale omise. Include guvernanța, verificarea catalogului și benchmarkul Hard pentru toate cele 1.172 de poziții.
- [`documentation-governance.html`](documentation-governance.html) — cele 8 verificări editoriale rulate după eliminarea versiunilor vechi și mutarea referințelor istorice către tagurile Git.
- [`android-signed-sanity.html`](android-signed-sanity.html), [`android-signed-sanity.json`](android-signed-sanity.json) și [`android-signed-sanity-execution.json`](android-signed-sanity-execution.json) — 94 de aserțiuni trecute pe API 28 cu APK semnat și reluare după restart; recepția originală și domeniul aplicației sunt păstrate separat.
- [`signing-transition.json`](signing-transition.json) — compară certificatul nou cu cel al ediției 0.11.2 și declară `CLEAN_INSTALL_REQUIRED`.
- [`rag.txt`](rag.txt) — citările de context pentru modificările candidatului.

Sanity-ul Android a folosit temporar `ro.atf.chess.android.freshcheck`, pentru a păstra datele locale ale instalării 0.11.2. APK-ul distribuibil este `ro.atf.chess.android`, versiunea `0.12.0`, 1.110.443 bytes, SHA-256 `cbb9da0810602d6e97a2f1dd0d4b9f62f8c4a2b3dd48a7a109ae43d0e464d74c`; hash-ul APK-ului din proba Android este `aff9e12b0e907cb8189a001025b8427053414a98896a9bd5809d25ad5422e495`. De aceea, raportul semnat confirmă comportamentul build-ului și al sursei, dar nu substituie instalarea APK-ului exact; nu există încă `qualification.json` sau dovadă de publicare. Poarta finală cere APK-ul exact pe un emulator curat și verificarea descărcării pornind din README-ul principal.
