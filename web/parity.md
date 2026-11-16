# Paritate APK–Web · Chess Lab

Ținta este **Joacă online** imediat sub **Descarcă APK** în secțiunea **Jocul** din README. Linkul duce la ediția Web care oferă funcțiile ediției Android asociate. Verificarea privește aceleași rezultate, comenzi, stări după reluare și acces la informații. Fiecare dispozitiv își păstrează progresul local. [README principal](../README.md#jocul)

## Contracte și probe

Același scenariu de componentă fixează starea inițială, acțiunile și observațiile. Motorul și catalogul provin din J7. Adaptorul Android trimite acțiunile către contract și persistă în preferințe. Adaptorul Web trimite aceleași intenții în worker și persistă local ultimul checkpoint confirmat. Verificați în componente inclusiv refuzul unei comenzi expirate, erorile de scriere, păstrarea progresului după upgrade și compatibilitatea salvărilor. Pe fiecare platformă, păstrați un sanity minim pentru traseul care depinde de interfață sau de ciclul de viață. [Strategia de testare](../docs/testing/README.md)

| Funcție din APK | Situație Web | Dovadă necesară pentru paritate |
|---|---|---|
| Partidă locală, reguli și promovare | Motor comun. Partida locală acceptă mutările ambelor culori și redeschide checkpointul. | Fixture de rocadă, en passant, pat, promovare și remiză. Sanity cu mutare, restart, reluare și o mutare nouă. |
| Partidă cu AI | Web acceptă Alb sau Negru și Basic, Advanced și Hard din motorul Java. Păstrează o partidă automată și reia tura AI din checkpoint. | Extindeți la alegerea aleatorie, controlul manual în aceeași partidă și verificarea reluării cu un calcul deja în curs. |
| Istoric și continuare | Poziția istorică poate rescrie slotul activ sau porni o partidă separată din arhivă, păstrând sursa. | Extindeți continuarea și în Game Review, la semimutarea selectată în analiză. |
| Partide salvate și încheiate | Două liste folosesc `MatchStore` și `MatchArchive`, cu zece intrări fiecare. Fiecare rezultat păstrează analiza și poziția de reluare prin ID și checkpoint. Evacuarea elimină datele intrării. | Calificați intrarea directă în analiza arhivată și continuarea din semimutarea selectată în Game Review. |
| Game Review și explicații | Se poate calcula și redeschide o analiză în worker. Redarea Web include istoric și ambele ramuri. | Completați graficul selectabil, Elo pentru partidele eligibile, precizia, momentele-cheie și continuarea de la poziție. Comparați mutarea selectată și motivul pe APK și Web. |
| Probleme și antrenament | Workerul încarcă și compară cataloagele din YAML. | Adaptați fluxul fiecărui capitol cu aceeași progresie. Verificați indiciile, replicile, reluarea explicațiilor, pericolul, seria, punctajul și upgrade-ul fără credit acordat de două ori. |
| Deschideri și tablă de analiză | Workerul încarcă lecțiile și indexul ECO. | Refolosiți `OpeningLesson`, `OpeningPractice` și `OpeningTheory`. Parcurgeți și rejucați lecția. Selectați până la trei trasee valabile, verificați transpoziția și trecerea la motor. Redați indiciile și analiza completă din tabla manuală. |
| Profil, limbă și prezentare | Jocul și analiza au text RO/EN. | Refolosiți validarea numelui și preferința de limbă. Verificați orientarea, geometria la font mărit, capturile și avantajul material, tastatura și echivalentul semantic al probelor native de accesibilitate. |

Detaliile produsului, inclusiv regulile de continuare și comportamentele care depind de platformă, rămân în ghidurile jocului. [Partide, probleme și analiză](../docs/board/README.md) · [Game Review](../docs/board/game-review.md)

## Publicarea linkului

Previzualizarea expune partida cu AI, partida locală și analiza. Mențineți acest statut până la calificarea funcțiilor din matrice. Publicați după modelul J4, cu pachetul static și `.nojekyll` în rădăcina `gh-pages`. Verificați pachetul servit din subdirectorul proiectului și reluarea în browser înainte de a da linkul în README. Pentru ediția 1:1, legați commitul și identitățile APK/Web de recepțiile Extent, verificați și jocul fără rețea după încărcare și recuperarea progresului la actualizare. [Procesul de release](../docs/release/README.md) · [Reper J4](https://crististefanita.github.io/atf_j4/)
