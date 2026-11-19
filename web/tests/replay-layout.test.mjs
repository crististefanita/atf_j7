import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import test from 'node:test';

const css=name=>readFileSync(new URL('../src/main/web/'+name,import.meta.url),'utf8');

test('Calculated and Mating line allocate fixed chrome before their scrolling explanation',()=>{
    assert.match(css('game-review.css'),/grid-template-rows: var\(--chess-control-size\) auto var\(--chess-control-size\) minmax\(0, 1fr\)/);
    assert.match(css('game-review.css'),/\.match-inline-preview-details\s*\{[^}]*min-block-size: 0[^}]*overflow: auto/);
    assert.match(css('study-quests.css'),/grid-template-rows: var\(--chess-control-size\) minmax\(0, 1fr\) var\(--chess-control-size\)/);
    assert.match(css('study-quests.css'),/\.quest-study-replay-body\s*\{[^}]*min-inline-size: 0;[^}]*grid-template-columns: minmax\(0, 1fr\);[^}]*grid-template-rows: auto minmax\(0, 1fr\);/);
    assert.match(css('study-quests.css'),/\.quest-study-dialog\s*\{[^}]*grid-template-columns: minmax\(0, 1fr\);/);
    assert.match(css('study-analysis.css'),/\.analysis-study-replay \.analysis-study-stage \{ flex: none; min-block-size: 0; \}/);
    assert.match(css('study-analysis.css'),/\.analysis-study-replay-details\s*\{[^}]*flex: 1 1 0;[^}]*overflow: auto/);
});

test('The AI hint and continuation keep stable slots while short-window metadata shares one row',()=>{
    assert.match(css('game-review.css'),/\.match-inline-analysis\s*\{[^}]*min-block-size: var\(--chess-control-size\)/);
    assert.match(css('chess-controls.css'),/\.match-actions \{ min-block-size: var\(--play-action-size\); \}/);
    assert.match(css('game-review.css'),/@media \(max-height: 480px\)\s*\{\s*\.match-caption \{ display: flex/);
    assert.match(css('game-review.css'),/\.match-caption > \.turn\s*\{[^}]*text-overflow: ellipsis/);
});
