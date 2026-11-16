# Paritate APK–Web · Chess Lab

**Joacă în browser** este imediat sub **Descarcă APK** în secțiunea **Jocul** din README și deschide partida cu AI. Verificarea compară rezultatele, comenzile și starea după reluare pentru funcțiile implementate pe ambele platforme. Fiecare dispozitiv își păstrează progresul local. [README principal](../README.md#jocul)

## Contracte și probe

Același scenariu de componentă fixează starea inițială, acțiunile și observațiile. Motorul și catalogul provin din J7. Adaptorul Android trimite acțiunile către contract și persistă în preferințe. Adaptorul Web trimite aceleași intenții în worker și persistă local ultimul checkpoint confirmat. Verificați în componente inclusiv refuzul unei comenzi expirate, erorile de scriere, păstrarea progresului după upgrade și compatibilitatea salvărilor. Pe fiecare platformă, păstrați un sanity minim pentru traseul care depinde de interfață sau de ciclul de viață. [Strategia de testare](../docs/testing/README.md)

| Funcție din APK | Situație Web | Dovadă necesară pentru paritate |
|---|---|---|
| Meniu principal | Grupurile Partide, Studiu și Profil păstrează ordinea și textele din resursele APK. Studiu afișează cele trei comenzi și totalurile problemelor. | Verificați în browser MCP accesul la comenzi și încadrarea la font mărit. [Verificarea meniului](README.md#studiu) |
| Piese și culorile tablei | Partidele și Game Review folosesc contururile Staunton din `ChessPieceArtwork` și aceleași culori ca `BoardView`. | Rulați componenta de randare și sanity-ul cu mutare pe tabla Web. [Verificarea pieselor](README.md#meniul-principal-și-piesele) |
| Partidă locală, reguli și promovare | Motor comun. Partida locală acceptă mutările ambelor culori și redeschide checkpointul. | Fixture de rocadă, en passant, pat, promovare și remiză. Sanity cu mutare, restart, reluare și o mutare nouă. |
| Partidă cu AI | Web acceptă Alb sau Negru și Basic, Advanced și Hard din motorul Java. Păstrează o partidă automată și reia tura AI din checkpoint. | Extindeți la alegerea aleatorie, controlul manual în aceeași partidă și verificarea reluării cu un calcul deja în curs. |
| Istoric și continuare | Tabla poate rescrie slotul activ ori porni o partidă separată din arhivă. Game Review permite continuarea din semimutarea selectată în istoricul arhivat. | Comparați continuarea unei partide active din Game Review pe APK și Web. |
| Partide salvate și încheiate | Două liste folosesc `MatchStore` și `MatchArchive`, cu zece intrări fiecare. Fiecare rezultat păstrează analiza și poziția de reluare prin ID și checkpoint. Evacuarea elimină datele intrării. | Verificați recuperarea salvărilor după o actualizare a pachetului. |
| Game Review și explicații | Tabla fixă, graficul și lista folosesc aceeași poziție selectată. Edge verifică valoarea graficului, rândul selectat și focusul restaurat după **De ce?**. | Confirmați vorbirea NVDA în RO/EN. [Proba Web](README.md#cititorul-de-ecran-în-game-review) |
| Probleme și antrenament | Cele șase capitole folosesc catalogul, etapele de joc, indiciile, punctajul și reluarea din core. | Confirmați traseul vizual al unei apărări, al unei explicații și al reluării. Verificați un upgrade cu progres salvat. [Probele Studiu](README.md#studiu) |
| Deschideri și tablă de analiză | Lecțiile folosesc `OpeningLesson`. Tabla manuală folosește `AnalysisLine` și deschide același Game Review. | Comparați în browser MCP încadrarea comenzilor și a explicațiilor cu APK-ul în ambele orientări. [Probele Studiu](README.md#studiu) |
| Profil, limbă și prezentare | Profilul local păstrează numele afișat în partida cu AI și limba RO/EN. | Verificați orientarea, geometria la font mărit, capturile și avantajul material, tastatura și echivalentul semantic al probelor native de accesibilitate. |

Detaliile produsului, inclusiv regulile de continuare și comportamentele care depind de platformă, rămân în ghidurile jocului. [Partide, probleme și analiză](../docs/board/README.md) · [Game Review](../docs/board/game-review.md)

## Publicarea linkului

Versiunea Web publică oferă partida cu AI, partida locală și meniul Studiu. Publicați pachetul static și `.nojekyll` în rădăcina `gh-pages`. Verificați din **Joacă în browser** pachetul servit și reluarea în browser. Pentru paritatea ediției, legați commitul și identitățile APK/Web de recepțiile Extent și verificați jocul fără rețea după încărcare și recuperarea progresului la actualizare. [Procesul de release](../docs/release/README.md) · [Reper J4](https://crististefanita.github.io/atf_j4/)
