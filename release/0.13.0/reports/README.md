# Dovezile release-ului 0.13.0

Acest director păstrează numai dovezile necesare pentru auditarea binarului publicat. Capturile UI sunt încorporate în rapoartele Extent; nu se dublează ca imagini independente.

- `components-verified.html` și `component-execution.json`: suita JVM auditabilă, inclusiv navigarea și redarea variantei recomandate.
- `game-review.html`, `game-review.json` și `game-review-execution.json`: sanity Android concentrat pe sincronizarea tablei, graficului și listei de mutări, precum și pe ramura PV.
- `build-artifact.json`, `qualification.json` și `rag.txt`: proveniența sursei, APK-ul semnat și traseul de impact/documentație.
- `github-release.json`, `public-download.json`, `authenticated-download.json` și `publication.json`: asset-ul și descărcările verificate după publicare.

Fiecare receipt identifică versiunea, commitul și SHA-256-ul APK-ului. Un rezultat incomplet sau provenit din alt APK nu califică release-ul.
