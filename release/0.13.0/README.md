# Chess Lab 0.13.0

## Îmbunătățiri

- Analiza unei partide încheiate păstrează tabla, lista de mutări și graficul pe același ecran. Selectarea oricăruia sincronizează poziția și clasificarea.
- Navigarea are pași înainte/înapoi și salt la următorul moment-cheie. Linia recomandată poate fi parcursă pas cu pas fără să modifice partida salvată.
- Reluarea dintr-un moment ales continuă din poziția anterioară mutării, iar explorarea și anularea dialogului lasă rezultatul arhivat intact.
- Jocul și deschiderile răspund la Înapoi prin mecanismul Android potrivit versiunii. Motorul păstrează compatibilitatea API 26 și deciziile Hard de referință.
- Antrenamentul tactic include 96 de poziții. Progresul se păstrează după ID-ul exercițiului.

## Instalare

Folosește APK-ul semnat atașat acestui release. Ediția 0.13.0 păstrează identitatea de semnare a ediției 0.12.0, deci instalările 0.12.0 se pot actualiza normal.

## Verificare

Dovezile pentru componente, sanity Android, Extent și descărcarea din README sunt în [`reports/`](reports/README.md). README-ul principal indică această ediție și APK-ul ei după publicare.
