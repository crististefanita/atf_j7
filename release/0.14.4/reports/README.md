# Dovezile ediției 0.14.4

- [Componente · Extent](components.html), [calificare](qualification.json) și [amprentele recepțiilor brute](raw-receipts.json).
- [Cele șase capitole · 720 × 1280, română](android-puzzle-ux-720.html) și [recepția Android](puzzle-ux-720-execution.json).
- [Cele șase capitole · 1080 × 1920, engleză](android-puzzle-ux-1080.html) și [recepția Android](puzzle-ux-1080-execution.json).
- [Reluare Mat în 2 și Mat în 3 · Extent](android-puzzle-mate-replay-720.html) și [recepția Android](puzzle-mate-replay-720-execution.json).
- [Reluare Mat în 2 și Mat în 3 pe profilul fizic](android-puzzle-mate-replay-1080.html) și [recepția Android](puzzle-mate-replay-1080-execution.json).
- [Pericol și lățimea tablei · Extent](android-puzzle-danger.html) și [recepția Android](puzzle-danger-execution.json).
- [Analiza partidei · Extent](android-game-review.html) și [recepția Android](game-review-execution.json).
- [Guvernanță JVM · Extent](governance.html), [revizuire Git](test-change-review.html), [testele înlocuitoare](test-change-review.html.proof.html) și [recepția revizuirii](test-change-review-execution.json).
- [Continuitatea semnăturii](signing-transition.json) și [contextul RAG](rag.txt).
- [Asset GitHub](github-release.json), [descărcare directă](public-download.json) și [descărcare autentificată](distribution-verification.json).

Pe Android 9, fiecare profil al celor șase capitole a trecut șase verificări la
font 1,3×. Rapoartele leagă aserțiunile și capturile de APK-ul semnat, de commit
și de recepția fiecărei rulări. Profilul compact păstrează cadrul fizic complet.
La fontul implicit, proba Pericol confirmă latura de 1080 px atât pentru tabla
puzzle-ului, cât și pentru tabla explicației.

Proba compactă confirmă trecerea EN → RO, iar profilul fizic confirmă RO → EN.
Fiecare recepție păstrează limba observată după redeschidere, cu profilul,
partida și progresul intacte. Raportul Game Review include meniul partidei,
bifa de joc automat și coordonatele tablei după schimbarea orientării.

[Procesul de calificare și publicare](../../../docs/release/README.md#calificare-și-publicare)
