# Feedback · Chess Lab

## Pe scurt

Registrul păstrează lacunele și criteriile deschise. [Ghidul tablei](../board/README.md)
descrie comportamentul, iar [catalogul edițiilor](../../release/README.md) indică dovezile publicate.

[Revizuirea mutărilor între suite](../testing/README.md#schimbarea-testelor-față-de-o-bază-git) descrie baza Git și dovezile de acoperire.

| Prioritate | Zonă | Observație | Criteriu de închidere |
|---|---|---|---|
| P1 | Release · identitatea guvernanței | Identitatea XML este verificată după nume și verdict. | Respinge recepțiile PASS din alt commit sau din altă rulare. |
| P2 | Release · arhiva probei compacte | Proba compactă are o arhivă separată. | Respinge fazele lipsă, rulările străine și PNG-urile substituite. |
| P2 | Game Review · NVDA | Vorbirea RO repeta poziția și scorul. | Confirmă un singur anunț în RO și EN și revenirea focusului. |
| P1 | Italiană · schimbul neîncheiat nativ | Extensia uniformă repară exemplul, dar pierde trei familii; verificarea doar a damei reintroduce sacrificiul fals. | Închide schimbul la orizont comun și cel mult 200k, păstrând martorii și transferul independent înainte de duel. |
| P1 | Indiciu · precizia alternativelor independente | Liderul sicilian corect `N1c3` încă oferă alternativa inferioară `Bg5`. | Corectează alternativele oferite fără regresia liderului, cu dovezi la orizont comun și replici libere. |
| P2 | Audit indicii · transfer mai larg | Planul inițial acoperă trei trasee scurte de deschidere și trei bugete. | Adaugă familii cu istorii separate și măsoară pierderea liderului distinct de rangurile inferioare jucate intenționat. |
| P2 | Probleme · focus TalkBack în opțiuni | Opțiunile problemelor mai cer dovada focusului. | Înregistrează traseul focusului în ambele limbi. |
| P2 | Android · gestul predictiv | Gestul predictiv mai cere un dispozitiv compatibil. | Verifică anularea și reluarea pe API 33+. |
| P1 | Calibrarea profilurilor AI | Intervalele Elo încă se suprapun. | Adaugă jocuri pereche pentru a restrânge incertitudinea. |
| P1 | Forța Hard adaptiv | Căutarea pornește cu 100.000 de noduri și o rezervă până la 200.000. | Compară cele trei moduri pe partide excluse din ajustare și verifică replica adversă. |
| P1 | Învățarea Hard din PGN-uri | Modelul de ordonare antrenat pe pierderi păstrează toate mutările finale în 36 de familii noi. | Adaugă mutări bune păstrate și sacrificii, apoi verifică ordonarea și deciziile complete. |
| P1 | Căutarea Hard · generalizare | Recaptura selectivă pierde pe deschideri independente. | Cere progres independent la bugetul fix. |
| P1 | Hard · conversia după captura de pion | Avantajul după captură poate fi pierdut ulterior. | Verifică păstrarea avantajului pe poziții și replici noi. |
| P1 | Transferul tactică → partidă | Hard ratează tactici fără obiectiv declarat. | Măsoară detectarea și păstrează contractul solverului. |
| P2 | Progres · upgrade Android | Upgrade-ul semnat mai cere o probă de progres. | Păstrează ID-urile, rezolvările și punctele după upgrade și restart. |
| P2 | Calitatea AI · evaluare material | Scorul material diferă de Stockfish. | Redu abaterea la aceleași bugete. |
| P2 | Calibrarea problemelor | Benzile de dificultate importate se suprapun. | Verifică timpul și încercările pe un lot amestecat separat. |
| P2 | Android · separarea tranzițiilor de ecran | Ecranul Android deține mai multe tranziții de sesiune. | Mută tranzițiile într-un owner testabil în JVM. |

## Dovezi și verificări de închidere

<details>
<summary>P1 · Release · identitatea guvernanței</summary>

Preflight leagă recepția pentru schimbarea testelor de Git, iar XML-urile Documentation, Release și Extent sunt verificate după nume și verdict.

Leagă fiecare XML de commitul candidat și de rularea de calificare. Înlocuiește pe rând un XML cu o recepție PASS din alt commit sau din altă rulare și verifică refuzul, păstrând recepția Git curentă. [Preflight](../../release-tools/src/main/java/ro/atf/chess/release/ReleaseGate.java) · [Teste](../../atf-tests/src/test/java/ro/atf/chess/ReleaseGovernanceTest.java)

</details>

<details>
<summary>P2 · Release · arhiva probei compacte</summary>

Publicatorul Android verifică raportul compact bilingv. Poarta reutilizabilă califică fazele Android generale; procedura cere și arhiva compactă pentru schimbările de geometrie.

Adaugă verificarea compactă în poarta de promovare pentru schimbările de geometrie. Respinge faza lipsă, faza din altă rulare și PNG-ul substituit. Păstrează validarea arhivelor din edițiile anterioare. [Calificare](../release/README.md#calificare-și-publicare) · [Publicator compact](../../atf-tests/src/test/java/ro/atf/chess/AndroidSanityPublication.java)

</details>

<details>
<summary>P2 · Game Review · NVDA</summary>

NVDA 2026.3.1 a reprodus în RO dublarea poziției și a scorului. Componenta și Edge confirmă acum o singură valoare accesibilă.

Confirmați vorbirea pe build-ul corectat, în RO și EN, și revenirea la aceeași mutare din **De ce?**. [Feedback Web](web-parity.md)

</details>

<details>
<summary>P1 · Italiană · schimbul neîncheiat din căutarea nativă</summary>

Indiciul S5 rămâne corectat la `…Qg6`; Hard nativ păstrează `…Na5` la H5/100k noduri suplimentare. PV-ul se încheie cu `…Nxc4`, înainte de `Nxc6` care câștigă dama. La H6, comparația exactă separată încă ordonează `…Na5 −154` înainte de `…Qg6 −170 cp`; în frunzele lor, Stockfish H19 găsește recapturi favorabile negrelor `…Qxe5` (+306) și `…Nxe5` (+493 cp). Simplul acces la mai multe rădăcini nu rezolvă această pereche.

Nu repeta extensia uniformă de două semimutări: repară exemplul italian, dar pierde trei familii cu cel puțin 150‰ și trece numai 83/87 componente față de 87/87 pe reper. Verificarea doar a damei atacate reintroduce `…Bxf2+`. Următoarea probă trebuie să trateze schimbul legal neîncheiat fără a micșora declarativ adâncimea sau a copia indiciul în joc. Păstrează `Be2` față de `Nb5`, `Nxc6` față de `Nxc7+`, familiile 019/021/022, sacrificiile corecte și martorii Sicilianei/Caro–Kann. După transfer independent și plafon de cel mult 200k noduri suplimentare, măsoară duelul candidat–live cu culorile inversate.

Motorul live nu s-a schimbat. Elo local aplicabil rămâne 1.507 [1.357–1.656], din 100 de partide; nu există ΔElo nou al candidaților respinși. Benchmarkul nativ reparat păstrează toate pragurile și adaugă audit pentru fiecare poziție. [Dovezi și rezultate](../board/ai-quality.md#studiul-frontierei-native-și-auditul-puzzle-urilor) · [Contracte de audit](../testing/hard-audit.md#auditul-puzzle-urilor-fără-obiectiv-transmis-adversarului)

</details>

<details>
<summary>P1 · Indiciu · precizia alternativelor independente</summary>

Alternativa `…Nd7` din Siciliana principală și liderul `…d6` din Caro–Kann principală sunt corectate. Diferența de transfer rămasă apare în vecinul Sicilian cu `Nc3`, unde vechiul lider `…Nd7` are 44,5‰ față de 479,5‰ pentru `…cxd4` la H19. În vecinul Caro–Kann cu `Nc3`, `…d6` are încă 400‰ față de 482‰ pentru `…d5`. Liderul martorului anterior capturii `Nxc2` rămâne `…Nf6`, iar alternativa sa `…b5` este mai slabă. `cxd5` din Catalană rămâne inferior lui `Nf3` din Stockfish. [Comparații închise](../board/ai-quality.md#comparația-selectivă-a-indiciului-și-auditul-mutărilor)

În S7 sicilian din v1, liderul `N1c3` are −1 cp / 499,5‰ la H19, iar alternativa oferită `Bg5` are −166 cp / 24‰ după replica liberă `…Qa5+`. Față de optimul rădăcinii libere, pierderile sunt 9‰ și 484,5‰; diferența dintre rădăcinile impuse este 475,5‰. Martorul de regresie protejează numai liderul și nu certifică eliminarea alternativei slabe.

Îmbunătățiți ordonarea comună în plafonul total de 200.000 de noduri pentru indiciu. Comparați toate mutările oferite la același orizont complet și verificați replicile adverse libere la H17/H19 pe familii noi. Păstrați sacrificiile corecte de damă și cal, negativele limitelor din cache și identitatea bec/comparație/rejucare. Un PASS de integritate a rejucării nu dovedește forță egală între alternative. Păstrați dovezile adâncimii complete și ale nodurilor efective. [Probe AI](../testing/README.md#partidă-și-istoric)

</details>

<details>
<summary>P2 · Audit indicii · transfer mai larg</summary>

Colectorul versionat păstrează identitatea sursei, istoricul complet, adâncimile efective, candidații oferiți și comparați, rangurile indisponibile și bugetele plafonate. Contractele cu mock-uri acoperă substituția, scorurile reamprentate, reluarea și ieșirea întreruptă. Traseele reale sunt partide diagnostice ale politicilor de indicii, cu mutarea teoretică a adversarului Hard normal înregistrată separat.

Adăugați familii independente de deschidere și final din PGN-urile arhivate. Înghețați istoriile înainte de reglaj, verificați prima pierdere cu replici Stockfish H17/H19 libere și păstrați toate eșecurile. Comparați bugetele numai din checkpoint-uri identice. Păstrați Extent, PGN și manifestul JSONL de încredere legate de sursă și separați pierderea liderului de rangurile 2 și 3 alese intenționat. [Protocolul auditului](../testing/hint-audit.md)

</details>

<details>
<summary>P2 · Probleme · focus TalkBack în opțiuni</summary>

Sanity-ul verifică deschiderea, comenzile și păstrarea progresului în toate cele șase capitole.

Cu TalkBack activ, verificați focusul pe **Întoarce tabla**, glisarea la **Meniu principal** și revenirea la butonul din antet după **Înapoi**. Înregistrați evenimentele de focus în RO și EN pe API 28 și pe un profil cu navigare gestuală. [Proba TalkBack](../testing/README.md#proba-talkback)

</details>

<details>
<summary>P2 · Android · gestul predictiv</summary>

Lint validează protecția API, iar APK-ul 0.13.0 păstrează partida Hard la comanda Înapoi pe API 28.

Verificați gestul predictiv în joc și Deschideri pe API 33+, inclusiv anularea gestului. [Sanity Android](../testing/README.md#sanity-android)

</details>

<details>
<summary>P1 · Calibrarea profilurilor AI</summary>

Basic a estimat 1067 Elo (638–1225) după 144 de partide cu Stockfish 19. Advanced la 10.000 de noduri a estimat 1421 (1245–1553), iar Hard la 44.000 a estimat 1534 (1396–1672) în același protocol.

Adăugați perechi noi cu ambele culori pentru a restrânge incertitudinea în jurul limitei Basic de 1200 și a benzii Advanced. Recalibrați valorile din aplicație cu sursă fixată și dovezi guvernate. [Calibrarea Basic](../board/ai-quality.md#calibrarea-basic-cu-adâncime-selectivă)

</details>

<details>
<summary>P1 · Căutarea Hard · generalizare</summary>

Recaptura selectivă îmbunătățește Tactica, dar pierderea medie pe deschideri independente urcă de la 53,0‰ la 68,0‰. Prototipul cache cu limite pierde o problemă de mat în trei, iar intervalul duelului include zero.

În benchmarkul Hard, includeți Scandinavă după `4.d4` și Siciliană clasică după `6.Bg5`. Cereți progres pe lot independent și în duel cu culori inversate, cu profilul fix sau adaptiv declarat. Păstrați corectitudinea matului în trei și înregistrați nodurile efectiv consumate. [Recaptură](../board/ai-quality.md#recaptura-hard-la-orizont) · [Cache experimental](../board/ai-quality.md#prototip-local-de-limite-alpha-beta--neintegrat)

</details>

<details>
<summary>P1 · Hard · conversia după captura de pion</summary>

Hard găsește acum `Nxe5` în Siciliana cu `…h6` și în poziții vecine, la 44.000 de noduri. În continuarea cu `…Nc6 Nxc6`, împingerea `e5` pierde 211‰ față de `d3` la adâncimea 17. În lotul tematic final, B a încheiat cu 3 victorii, iar A cu 5.

Verificați alegerea `d3` pe poziții noi și după replici adverse distincte, apoi urmăriți păstrarea avantajului în jocuri pereche cu același buget. Păstrați testele de captură și martorii cu pion apărat. [Dovezi](../board/ai-quality.md#verificarea-schimburilor-după-o-captură-de-pion)

</details>

<details>
<summary>P1 · Transferul tactică → partidă</summary>

Cu obiectiv declarat, solverul rezolvă 1.220/1.220 publice și 24/24 independente. La obiectiv necunoscut, Hard ratează încă șase maturi în trei și chei materiale. Oprirea la mat dovedit a redus costul mediu pe Mat în trei cu 33%.

Măsurați ce tactici pot fi detectate din poziție și păstrați 100% în solver. Pentru adversar, cereți progres pe lotul independent cu aceleași bugete și absența regresiilor pe jocuri pereche. [Dovezi și duel](../board/ai-quality.md#rezolvarea-quest-urilor-cu-obiectiv-declarat)

</details>

<details>
<summary>P2 · Progres · upgrade Android</summary>

Restartul procesului pentru Antrenament tactic este demonstrat în 0.12.0, iar extinderea catalogului păstrează cursorul și punctajul în componentă. Lipsește proba de update cu APK-ul semnat și date deja salvate, pe două ediții cu aceeași identitate.

Instalați ediția publicată pe un emulator curat, creați progres, instalați candidatul compatibil peste ea și verificați după restart ID-ul, rezolvările și punctele fără dublare. [Proba de upgrade](../testing/README.md#upgrade-al-progresului)

</details>

<details>
<summary>P2 · Calitatea AI · evaluare material</summary>

După `2…dxc4`, J7 afișează `−0,60`, iar Stockfish 19 la adâncimea 13 indică `+0,21`; după `3.e3`, abaterea ajunge la 111 cp.

Urmăriți recuperarea pionului pe ambele culori și cu ramuri de control. Reduceți abaterea fără să depășiți bugetul barei sau să slăbiți porțile. [Măsurători](../board/ai-quality.md#calibrarea-hard-și-a-barei)

</details>

<details>
<summary>P2 · Calibrarea problemelor</summary>

În Mat în 1, medianele ratingului importat urcă pe cele cinci segmente, cu suprapuneri între segmente.

Măsurați timpul și încercările greșite înaintea indiciului pe poziții amestecate, separat pentru pozițiile pornite din șah. Validați ponderile YAML pe un lot separat și păstrați ID-urile. [Calibrare](../board/quest-curation.md#calibrarea-dificultății)

</details>

<details>
<summary>P2 · Android · separarea tranzițiilor de ecran</summary>

`ChessActivity` deține meniurile, cursorul, salvarea și programarea replicilor. O modificare în reluarea partidei atinge aceeași clasă care orchestrează exercițiile și navigarea.

Extrageți întâi tranzițiile reluare, anulare și rezultat AI întârziat într-un owner testabil JVM. Păstrați componentele pentru restaurare și respingerea replicii vechi; pe Android verificați o singură reluare și ordinea vizibilă. [Partidă și istoric](../testing/README.md#partidă-și-istoric)

</details>

<details>
<summary>P1 · Forța Hard adaptiv</summary>

Căutarea pentru mutarea curentă folosește o alocație configurabilă de 100.000 de noduri, cu o rezervă până la 200.000. Continuarea păstrează stiva activă și cache-ul. Testele de componentă protejează verificarea existentă a schimbului.

Comparația istorică v1 păstrează `Bg5` nativ în S7 sicilian (24‰), față de `N1c3` din indiciu (499,5‰). Indiciul italian este între timp reparat la `…Qg6`; vechea recomandare `…Bxf2+` nu mai descrie sursa live. Compară în continuare API-urile independent, fără a copia automat selecția indiciului în Hard.

Măsurați 100.000 fix, 200.000 fix și modul adaptiv pe aceeași sursă, în partide excluse din ajustare și în poziții cu pierderi verificate de Stockfish. Păstrați identitatea sursei pentru liga ordonării învățate și verificați alternativele legale liniștite, matul și finalurile de remiză. [Contractul adaptiv](../board/ai-quality.md#buget-adaptiv-în-prim-plan)

</details>

<details>
<summary>P1 · Învățarea Hard din PGN-uri</summary>

Modelul de ordonare antrenat pe 58 de pierderi schimbă 34/36 de liste de rădăcini excluse din ajustare și păstrează toate mutările finale. Verificarea cronologică identifică o pierdere mai devreme într-una dintre cele 36 de partide.

Includeți în următorul lot de antrenare mutări bune păstrate, replici liniștite și sacrificii verificate. Cereți îmbunătățirea rangului alternativei și schimbarea deciziilor complete în familii excluse din ajustare înainte de interpretarea Elo pereche. [Cercetarea offline](hard-learning.md)

</details>
