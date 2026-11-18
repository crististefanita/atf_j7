import { readFile } from 'node:fs/promises';
import { cssDeclarations, cssPixels } from './support/css-length.mjs';
const rules = cssDeclarations(await readFile(new URL('../src/main/web/board-layout.css', import.meta.url), 'utf8'));
const cases = JSON.parse(await readFile(new URL('fixtures/analysis-board-layout.json', import.meta.url), 'utf8'));
const rows = cases.map(example => {
    const variables = { ...rules, '--play-column': '35rem', '--chess-control-size': '48px', '--play-page-padding': example.padding + 'px' };
    const limit = cssPixels(rules['--play-board-limit'], { variables, rem: example.font, viewportHeight: example.height });
    return { id: example.id, limit: limit.toFixed(3), square: Math.max(0, Math.min(example.width - example.rail, limit)).toFixed(3) };
});
process.stdout.write(JSON.stringify(rows));
