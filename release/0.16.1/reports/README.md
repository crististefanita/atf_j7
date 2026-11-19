# Dovezi pentru ediția 0.16.1

## Pe scurt

Calificarea strictă leagă 534 de cazuri de componentă și 30 de aserțiuni Game Review de aceleași surse, APK semnat și ID de execuție. Cele două rapoarte Puzzles adaugă câte șase verificări de capitol. Rotația adaugă 32 de verificări pentru fiecare limbă pe același APK.

## Dovezi

- [Componente — 534 de cazuri trecute, zero omise](components.html)
- [Game Review Android semnat — 30 de aserțiuni native](android-game-review.html)
- [Puzzles compact — șase capitole, RO, font 1,3](puzzle-ux.html)
- [Puzzles la dimensiunea fizică — șase capitole, EN, font 1,3](puzzle-ux-1080.html)
- [Indiciu deschis, Calculated line și Mating line — rotație RO/EN, 64 de aserțiuni](android-replay-rotation.html)
- [Meniul partidelor salvate — observații native focalizate](match-slots.html)
- [Meniul partidelor terminate — observații native focalizate](match-archive.html)
- [Guvernanța proiectului, documentației și raportării înaintea semnării](governance.html)
- [Revizuire față de reperul Git și protecția sursă pentru Android API 28](test-change-review.html)
- [Acoperirea înlocuitoare executată](test-change-review.html.proof.html)
- [Calificarea strictă a ediției și identitatea surselor](qualification.json)
- [Fazele Game Review și restartul de proces din aceeași execuție](game-review-execution.json)
- [Recepții de rotație și amprentele capturilor incluse](replay-rotation-execution.json)
- [Amprentele reperului Git și ale dovezii înlocuitoare](test-change-review-execution.json)
- [Proveniența recepțiilor brute externe](raw-receipts.json)
- [Tranziția semnării și instalarea curată](signing-transition.json)
- [Metadatele APK-ului publicat](github-release.json)
- [Workflow Web fixat și 30 de fișiere servite](web-publication.json)
- [Motorul calificat reutilizat de pachetul Web care schimbă doar interfața](web-engine-provenance.json)
- [Context RAG consultativ pentru diferențele candidatului](rag.txt)

## Domeniu și lucrări rămase

- Observațiile native ale meniurilor păstrează recepțiile reale fără un ID comun de calificare. Completează poarta strictă; nu înlocuiesc Game Review. Rotația păstrează recepțiile ambelor limbi și toate cele șaisprezece amprente ale capturilor. Capturile sunt incluse în Extent, fără copii PNG separate.
- Browser MCP a verificat antetul, întoarcerea contextuală, opțiunile ancorate în dreapta și navigarea centrată pe ferestre compacte și desktop. Pachetul Web păstrează motorul calificat anterior; eșecul compilării digestului JCA rămâne deschis.
- Helperul compact vechi de accesibilitate a eșuat la o comandă de analiză vizibilă. Recepția eșuată rămâne în studiul extern, iar corecția rămâne în feedback. Game Review la font 1,3 și rotația independentă RO/EN cu redări deschise au trecut.
- Buildurile, XML-ul brut, capturile și probele intermediare rămân în C:\work\sah. Acest folder păstrează dovezile necesare înțelegerii ediției.

[Calificare și publicare](../../../docs/release/README.md#calificare-și-publicare) · [Elemente comune ale ecranului](../../../docs/testing/README.md#antet-și-navigare-comune) · [Lucrări rămase pentru paritate Web](../../../docs/feedback/web-parity.md)
