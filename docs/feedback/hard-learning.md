# Învățarea Hard din partide arhivate

## Pe scurt

Studiul `2026-11-17-j7-hard-learned-order` păstrează un lot istoric de
antrenare înghețat, pierderi extrase independent și o ligă de partide pereche.
Dovezile de lucru rămân în dosarul extern al studiului. Înregistrarea partidelor
cu sursa identificată precizează motorul, resursele, modelul, pozițiile de start
și ambele distribuiri ale culorilor.

## Experimentul de ordonare

Modelul liniar cu nouă trăsături a fost ajustat pe 58 de alternative confirmate
din 30 de familii de deschidere. Validarea cu o familie exclusă pe rând preferă
alternativa etichetată în 28/58 de cazuri. Pe 36 de familii independente,
ordonarea schimbă 34 de liste de rădăcini, trei lideri inițiali și zece consumuri
de noduri. Alternativa etichetată precede mutarea arhivată în 12/36 de cazuri,
față de 11/36 cu ordonarea nativă. Ambele brațe aleg alternativa etichetată în
șase decizii complete. Toate mutările finale coincid.

Reverificarea cronologică a analizat 140 de decizii anterioare și a corectat o
etichetă. În `loss-005`, `Be2 …dxc4` pierde la semimutarea 15, iar `exd5`
recuperează 319,5/402,5 miimi WDL la adâncimile Stockfish 17/19. Lotul inițial
rămâne înghețat, iar poziția corectată este testată separat. Ambele ordonări
aleg `cxd5` în acea poziție.

## Dovezile următoare

Coeficientul pozitiv pentru o piesă care poate fi capturată imediat după mutare
poate amesteca sacrificiile corecte cu piesele rămase în priză. Includeți alături
de exemplele cu pierderi mutări bune păstrate, sacrificii cu replica verificată
independent și alternative apropiate. Împărțiți datele după familia de deschidere
înainte de ajustare și păstrați ambele orizonturi. Urmăriți rangul alternativei,
decizia completă și continuarea adversă rezultată.

În primele 12 poziții divergente din primele 100 de starturi ale ligii,
Stockfish confirmă cinci câștiguri și două pierderi la adâncimile 17 și 19.
În `learned-0007`, `Rb1` câștigă 528 de miimi WDL la adâncimea 19.
În `learned-0015`, `Qxb5 …axb5` pierde 541,5. Ambele compară rezultatul
candidatului la patru semimutări cu cel nativ la cinci. Folosiți aceste exemple
cu efecte opuse pentru a valida replica efectivă după o schimbare selectivă.

Politica adaptivă pentru mutarea curentă este o implementare separată pe sursa
`main` actuală. Rezerva ei de noduri și orice ordonare învățată viitoare cer
propriile comparații cu sursele identificate.

## Liga pereche încheiată

Modelul de ordonare a obținut 818 victorii, 273 de remize, 793 de înfrângeri
și 116 partide neterminate în 1.000 de perechi de deschideri din 996 de familii.
Diferența estimată a fost +4,34 Elo, cu interval de 95% între −2,77 și +11,31.
Atribuirea unei înfrângeri sau unei victorii fiecărei partide neterminate schimbă
estimarea centrală la −15,82 sau +24,53. Rejucarea legală a verificat 238.591
de recepții de decizie. Secvențele complete de mutări coincid în 657 de perechi.
Modelul rămâne o preferință experimentală de ordonare, cu efect incert asupra forței de joc.

Aceste partide au folosit H5 și 44.000 de noduri suplimentare pe sursa de
cercetare înghețată. Profilul main adaptiv, cu buget mai mare, cere propria
estimare a forței de joc.

[Protocolul de evaluare AI](../board/ai-quality.md#control-la-fiecare-schimbare) ·
[Contractul adaptiv pentru mutarea curentă](../board/ai-quality.md#buget-adaptiv-în-prim-plan)
