# Paritate APK–Web · Chess Lab

## Pe scurt

**Joacă în browser** se află sub linkul de descărcare a APK-ului din README-ul principal. Adresa de bază Web deschide meniul principal; o adresă directă deschide ecranul cerut. Progresul rămâne local pe dispozitiv. [README principal](../README.md#jocul)

## Contracte și probe

Folosiți același caz de componentă pentru starea inițială, acțiuni și observații. Verificați că rezultatele coincid înainte de a compara interfețele randate.

- **Responsabil comun:** `chess-core` furnizează motorul și catalogul. Adaptorul Android scrie în preferințe; adaptorul Web trimite aceleași comenzi în worker și salvează ultimul checkpoint confirmat.
- **Probe de componentă:** Respingeți comenzile expirate și testați erorile la scriere, progresul după upgrade și compatibilitatea salvărilor cu intrări controlate.
- **Sanity de interfață:** Păstrați un singur traseu minim Android sau Web când contractul cere randare, accesibilitate sau un eveniment real de ciclu de viață. [Strategia de testare](../docs/testing/README.md)

| Funcție | Contract comun | Dovezi |
|---|---|---|
| Meniu principal | APK și adresa de bază Web deschid **Chess Lab**, cu **Partide**, **Studiu** și **Profil** în aceeași ordine. | Texte și grupuri RO/EN; navigare cu istoric și stocare simulate. [Probe de meniu](README.md#meniul-principal-și-piesele) |
| Piese și culori | Partidele și Game Review au contururile Staunton din `ChessPieceArtwork` și culorile din `BoardView`. | Componenta de randare și o mutare pe tabla Web. [Probe de piese](README.md#meniul-principal-și-piesele) |
| Partidă locală | Motor comun; ambele culori pot muta, iar checkpointul salvat se redeschide. | Rocadă, en passant, pat, promovare, remize; mutare, restart, reluare și o nouă mutare. |
| Partidă cu AI | Alb, Negru sau Aleatoriu; Basic, Advanced sau Hard. Debifați **Lasă AI-ul să mute** pentru a controla ambele culori în aceeași partidă. | Verificări comune pentru comutare, redeschidere, anulare, revizia replicii și eligibilitatea Elo. [Controlul replicilor](README.md#controlul-replicilor-ai) |
| Analiza în partida cu AI | Configurarea activează bara din stânga și becul de lângă istoric de la poziția inițială; checkpointul păstrează alegerea. | JVM și workerul compară aceeași semimutare. Probele reale RO/EN verifică latura utilă maximă, tabla fixă, comenzile vizibile și redarea de la 1/2 cu redeschidere la 2/2. [Contract](README.md#analiza-în-partida-cu-ai) · [Probe](../docs/testing/README.md#analiza-în-partida-cu-ai) |
| Orientarea tablei | **Întoarce tabla** se păstrează în slotul activ. În previzualizarea arhivei, schimbarea este temporară. | Comparați poziția și cursorul înainte și după întoarcere, apoi orientarea la redeschidere. [Probe](../docs/testing/README.md#orientarea-tablei-în-partidă) |
| Dimensiunea tablei Web | Problemele, partidele și Deschiderile au aceeași limită de fereastră. Lecția rezervă loc pentru bara de evaluare. | Măsurați tabla și comenzile vizibile înainte și după o mutare sau un indiciu. [Dimensiuni](README.md#studiu) |
| Istoric și continuare | Istoricul activ poate înlocui slotul; o semimutare arhivată din Game Review pornește o partidă separată. | Continuați din Game Review pe Android și Web. |
| Partide salvate și încheiate | `MatchStore` și `MatchArchive` păstrează câte zece intrări. ID-ul și checkpointul leagă analiza de poziția de reluare; eliminarea șterge datele intrării. | Recuperați ambele liste după o actualizare a pachetului. |
| Game Review | Tabla fixă, graficul și lista folosesc poziția selectată. Probele din browser acoperă valoarea graficului, rândul selectat și focusul după **De ce?**. | Confirmați separat vorbirea NVDA în RO/EN. [Proba Web](README.md#cititorul-de-ecran-în-game-review) |
| Probleme și antrenament | Șase capitole folosesc catalogul, indiciile, punctajul și reluarea comune. `QuestPayoff` furnizează redarea tacticii și motivul pericolului verificat. | Componentele și browserul refac progresul din Tactică, Antrenament tactic și Mutarea intermediară. [Probe de upgrade](README.md#explicațiile-și-progresul-la-actualizare) |
| Meniul partidei cu AI | `MatchControls` definește aceleași acțiuni, rezultate la remiză sau renunțare și aceeași politică de arhivare. | Workerul verifică oferta refuzată și respinge comenzile din istoric sau expirate. Browserul verifică analiza, confirmarea și rezultatul la reîncărcare. [Opțiunile partidei](README.md#opțiunile-partidei-cu-ai) |
| Deschideri | `OpeningLesson` folosește aceeași geometrie pentru tablă și comenzi pe profilurile compact și orizontal la font 1,3×. | Dezvăluiți al doilea nivel de indiciu și verificați că tabla rămâne fixă. [Profiluri](../docs/testing/README.md#deschideri) |
| Tablă de analiză | `AnalysisEntry` leagă sursa, semimutarea și orientarea pentru partide active și arhivate. Înapoi reia sursa; studiul își păstrează propriul marcaj. | Cazuri comune JVM/worker; mock pentru navigare, stocare și restaurarea focusului. [Proba de contract](../docs/testing/README.md#tabla-de-analiză-dintr-o-partidă) |
| Profil, limbă și prezentare | Profilul local păstrează numele din partidă și preferința RO/EN. | Verificați fontul mărit, piesele capturate, avantajul material, tastatura și echivalentele accesibilității native. |

Ghidurile jocului dețin regulile de produs și comportamentul platformelor. [Partide, probleme și analiză](../docs/board/README.md) · [Game Review](../docs/board/game-review.md)

## Publicarea linkului

1. Construiți și calificați pachetul în TMP. Păstrați arborele static exact la un commit sau tag imuabil; folosiți un worktree separat pentru fișierele pregătite.
2. Păstrați octet cu octet directoarele `runtime/<SHA-256>/` deja publicate și adresele vechi din rădăcină folosite de filele deschise. Includeți noul director runtime, `index.html` și `.nojekyll` în același commit.
3. Declanșați [fluxul Pages](../.github/workflows/chess-pages.yml) pe `main` cu `site_commit` și `manifest_sha256` calificate. Pages publică prin Actions; ramura de documentație `main_RO` nu este niciodată sursă de publicare.
    Folosiți SHA-ul exact al site-ului pentru `site_commit`. Calculați `manifest_sha256` din lista brută a arborelui Git. Într-un shell POSIX rulați:

    ```bash
    git ls-tree -r --full-tree "$SITE_COMMIT" | sha256sum
    ```

4. Deschideți **Joacă în browser**. Verificați fișierele versionate declarate și reîncărcați cu memoria cache intactă. Verificați reluarea checkpointului și jocul fără rețea după prima încărcare; o filă deschisă poate păstra runtime-ul anterior până la reîncărcare.
5. Înregistrați revizia sursă, commitul static publicat, amprenta runtime și recepțiile Extent. Verificați octeții serviți independent de memoria cache a browserului.

[Schema pachetului](README.md#identitatea-pachetului-web) ·
[Strategia de testare](../docs/testing/README.md#actualizarea-pachetului-web) ·
[Procesul de release](../docs/release/README.md)
