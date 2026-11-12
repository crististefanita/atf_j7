# Edițiile Chess Lab

## Ediția publicată

[`index.json`](index.json) este catalogul tehnic. `currentVersion` identifică ultima ediție publicată; `versions` păstrează ediția curentă și o singură ediție anterioară pentru comparație. README-ul principal leagă doar ediția curentă; catalogul local indică și dosarul anterior.

| Rol | Versiune | Dovezi |
|---|---|---|
| Curentă | 0.11.2 | [APK și calificare](0.11.2/README.md) |
| Precedentă | 0.11.1 | [Ediția de comparație](0.11.1/README.md) |

## Candidat

Un `candidate` din catalog este pregătire internă; nu intră în lista descărcabilă și nu schimbă README-ul principal. După semnare, calificare și verificarea asset-ului, promovați candidatul în `currentVersion`, puneți versiunea anterioară imediată pe locul al doilea în `versions` și eliminați `candidate`. Pe GitHub și în dosarele locale rămân doar aceste două ediții; tagurile Git mai vechi se păstrează pentru proveniența sursei.

[Procesul de release](../docs/release/README.md) · [Dovezile ediției publicate](0.11.2/reports/README.md) · [Candidatul pregătit](0.12.0/README.md)
