import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { bindBoardSelection, boardKeyIndex, boardPlacement, mountStudyArrow, studyArrow, studyMarkers, studySquareDescription } from '../src/main/web/study-board.mjs';
import { routeForHash, routeHashes, studyHashes, watchRouteHistory, writeModeHash } from '../src/main/web/study-navigation.mjs';
import { rememberBoardSize } from '../src/main/web/study-board-size.mjs';

test('Each study route has an enabled home entry and a distinct panel', async () => {
    const html = await readFile(new URL('../src/main/web/index.html', import.meta.url), 'utf8');
    assert.match(html, /<button id="open-review"[^>]*>Main menu<\/button>/);
    for (const [entry, route] of [['puzzles', 'quests'], ['openings', 'openings'], ['analysis', 'analysis']]) {
        const button = html.match(new RegExp('<button id="home-' + entry + '"[^>]*>'));
        assert.ok(button);
        assert.doesNotMatch(button[0], /\bdisabled\b/);
        assert.match(html, new RegExp('<section id="study-' + route + '" class="study-surface" hidden>'));
    }
});

test('Study links, Back and Forward reopen the route represented by the address', () => {
    const origin = 'http://127.0.0.1/chess/?lang=ro';
    const entries = [new URL(origin)];
    let index = 0;
    let onHashChange;
    const browser = {
        get location() { return entries[index]; },
        history: {
            pushState(_state, _title, url) {
                entries.splice(index + 1, entries.length, new URL(url, browser.location));
                index++;
            },
            replaceState(_state, _title, url) {
                entries[index] = new URL(url, browser.location);
            },
        },
        addEventListener(type, listener) {
            assert.equal(type, 'hashchange');
            onHashChange = listener;
        },
    };
    function traverse(offset) {
        const previousHash = browser.location.hash;
        index += offset;
        if (browser.location.hash !== previousHash) onHashChange();
    }
    const visible = [];
    let current;
    watchRouteHistory(browser, () => routeHashes[current], hash => {
        current = routeForHash(hash).mode;
        visible.push(current);
        writeModeHash(browser, hash || undefined, false);
    });
    writeModeHash(browser, studyHashes.quests, true);
    current = 'quests';
    writeModeHash(browser, studyHashes.quests, true);
    assert.equal(entries.length, 2, 'an unchanged hash does not create a duplicate entry');
    browser.history.pushState(null, '', studyHashes.openings);
    onHashChange();
    assert.equal(current, 'openings');
    traverse(-1);
    assert.equal(current, 'quests');
    traverse(-1);
    assert.equal(browser.location.href, origin);
    traverse(1);
    traverse(1);
    assert.deepEqual(visible, ['openings', 'quests', 'home', 'quests', 'openings']);
    assert.equal(browser.location.search, '?lang=ro');
});

test('New screens reset the page origin while a move on the same screen preserves scroll', () => {
    const resets = [];
    let writes = 0;
    const browser = {
        location: { hash: '#puzzles' },
        scrollTo: (...point) => resets.push(point),
        history: { pushState(_state, _title, hash) { writes++; browser.location.hash = hash; } },
    };
    writeModeHash(browser, '#puzzles', true, false);
    assert.deepEqual(resets, []);
    assert.equal(writes, 0);
    writeModeHash(browser, '#analysis', true, true);
    assert.deepEqual(resets, [[0, 0]]);
    assert.equal(writes, 1);
    browser.location.hash = '#openings';
    writeModeHash(browser, '#openings', false, true);
    assert.deepEqual(resets, [[0, 0], [0, 0]], 'Back/Forward reset even after the browser has changed the address');
    assert.equal(writes, 1, 'restoring a screen must not add a history entry');
    writeModeHash(browser, '#openings', false);
    assert.equal(resets.length, 2);
});

test('Home is the default route and direct game, study and review links remain distinct', () => {
    for (const hash of ['', '#unknown', '#archive/', '#review/']) assert.deepEqual(routeForHash(hash), { mode: 'home' });
    for (const [mode, hash] of Object.entries(routeHashes)) assert.deepEqual(routeForHash(hash), { mode });
    assert.deepEqual(routeForHash('#archive/archived-game'), { mode: 'archive', archiveId: 'archived-game' });
    assert.deepEqual(routeForHash('#review/archived-game'), { mode: 'archive-review', archiveId: 'archived-game' });
});

test('Browser Back and Forward switch between AI and Home once per changed route', () => {
    let current = 'home';
    let onHashChange;
    const navigated = [];
    const browser = {
        location: { hash: '' },
        history: { pushState(_state, _title, hash) { browser.location.hash = hash; } },
        addEventListener(_type, listener) { onHashChange = listener; },
    };
    watchRouteHistory(browser, () => routeHashes[current], hash => {
        current = routeForHash(hash).mode;
        navigated.push(current);
    });
    writeModeHash(browser, routeHashes.ai, true);
    current = 'ai';
    onHashChange();
    browser.location.hash = '';
    onHashChange();
    assert.equal(current, 'home');
    browser.location.hash = routeHashes.ai;
    onHashChange();
    onHashChange();
    assert.deepEqual(navigated, ['home', 'ai']);
});

test('Study accepts native square order and placement without moving any piece', () => {
    const placement = boardPlacement('r3k2r/8/8/8/8/8/4P3/4K3 w kq - 0 1');
    assert.equal(placement.length, 64);
    assert.equal(placement[0], 'r');
    assert.equal(placement[4], 'k');
    assert.equal(placement[7], 'r');
    assert.equal(placement[52], 'P');
    assert.equal(placement[60], 'K');
    assert.equal(boardPlacement(placement), placement);
    assert.throws(() => boardPlacement('16/8/8/8/8/8/8/8'), /Invalid board placement/);
});

test('Study draws the native origin and destination cue in both board orientations', () => {
    const { origin, destination, danger } = studyMarkers(['e2', { square: 'e7', kind: 'danger' }, 'e4']);
    assert.deepEqual({ origin, destination, danger }, { origin: 'e2', destination: 'e4', danger: 'e7' });
    const white = studyArrow(origin, destination, false);
    const black = studyArrow(origin, destination, true);
    assert.equal(white.transform, 'translate(4.5 6.5) rotate(-90)');
    assert.equal(black.transform, 'translate(3.5 1.5) rotate(90)');
    assert.equal(white.path, black.path);
    const tip = Number(/ L ([\d.]+) 0 /.exec(white.path)[1]);
    assert.ok(Math.abs((2 - tip) - .12) < 1e-10, 'The tip stops twelve percent of a square before the destination center');
    const knight = studyArrow('b1', 'c3', false);
    assert.match(knight.transform, /^translate\(1\.5 7\.5\) rotate\(-63\./);
    assert.equal(studyArrow('e2', undefined, false), null);
    assert.equal(studyArrow('e2', 'e2', false), null);
    assert.equal(studyArrow('e2', 'e9', false), null);
});

test('Study descriptions distinguish the hint, destination and legal move with native RO and EN labels', async () => {
    for (const locale of ['en', 'ro']) {
        const folder = locale === 'ro' ? 'values-ro' : 'values';
        const resources = new URL(`../../android/app/src/main/res/${folder}/strings.xml`, import.meta.url);
        const xml = await readFile(resources, 'utf8');
        for (const [flag, id] of Object.entries({ target: 'legal_destination', origin: 'hint_origin', destination: 'hint_destination' })) {
            const native = xml.match(new RegExp(`<string name="${id}">([^<]+)</string>`))[1].replace('%1$s', 'e4');
            assert.equal(studySquareDescription('e4', locale, { [flag]: true }), native);
        }
    }
    assert.equal(studySquareDescription('e4', 'en', {}), 'e4');
    assert.equal(studySquareDescription('e4', 'ro', { destination: true, danger: true }), 'e4, destinația soluției, pericol');
});

test('Hint overlay survives board rotation and is removed when the solution is hidden', () => {
    function element() {
        return {
            children: [], attributes: {}, parentNode: null,
            setAttribute(name, value) { this.attributes[name] = value; },
            append(child) { child.parentNode = this; this.children.push(child); },
            remove() {
                if (!this.parentNode) return;
                this.parentNode.children = this.parentNode.children.filter(child => child !== this);
                this.parentNode = null;
            },
        };
    }
    const host = element();
    host.ownerDocument = { createElementNS: element };
    const draw = mountStudyArrow(host);
    draw('e2', 'e4', false);
    const [overlay] = host.children;
    assert.equal(overlay.attributes['aria-hidden'], 'true');
    assert.equal(overlay.children[0].attributes.transform, 'translate(4.5 6.5) rotate(-90)');
    draw('e2', 'e4', false);
    assert.deepEqual(host.children, [overlay]);
    overlay.remove();
    draw('e2', 'e4', true);
    assert.deepEqual(host.children, [overlay]);
    assert.equal(overlay.children[0].attributes.transform, 'translate(3.5 1.5) rotate(90)');
    draw('e2', undefined, true);
    assert.deepEqual(host.children, []);
});

test('Keyboard follows displayed squares without wrapping rows or activating a locked position', () => {
    const listeners = new Map();
    const chosen = [];
    let focused = -1;
    const children = Array.from({ length: 64 }, (_, index) => ({
        dataset: { square: String(index) }, tabIndex: index === 0 ? 0 : -1, locked: false,
        closest() { return this; }, focus() { focused = index; },
        getAttribute(name) { return name === 'aria-disabled' ? String(this.locked) : null; },
    }));
    bindBoardSelection({ children, contains: cell => children.includes(cell),
        querySelector: () => children.find(cell => cell.tabIndex === 0),
        addEventListener: (name, callback) => listeners.set(name, callback) }, square => chosen.push(square));
    assert.equal(boardKeyIndex('ArrowRight', 7), -1);
    assert.equal(boardKeyIndex('ArrowDown', 56), -1);
    let prevented = false;
    listeners.get('keydown')({ key: 'ArrowDown', target: children[0], preventDefault() { prevented = true; } });
    assert.equal(prevented, true);
    assert.equal(focused, 8);
    assert.equal(children[8].tabIndex, 0);
    children[8].locked = true;
    listeners.get('click')({ target: children[8] });
    assert.deepEqual(chosen, []);
    listeners.get('click')({ target: children[9] });
    assert.deepEqual(chosen, ['9']);
    assert.equal(children[8].tabIndex, -1);
    assert.equal(children[9].tabIndex, 0);
});

test('Puzzle and match boards share the Analysis reference and keep controls in page flow', async () => {
    const source = name => readFile(new URL(`../src/main/web/${name}`, import.meta.url), 'utf8');
    const [css, puzzleCss, matchMarkup, puzzleMarkup] = await Promise.all([
        source('game-review.css'), source('study-quests.css'), source('index.html'), source('study-quests.mjs'),
    ]);
    const sharedBoard = css.match(/:is\(\s*#match-board\s*,\s*#quest-study-board\s*\)\s*\{([^}]+)\}/)?.[1];
    assert.ok(sharedBoard, 'both boards use one sizing rule');
    assert.match(sharedBoard, /inline-size:\s*min\(100%,\s*var\(--play-board-limit\)\);/);
    assert.match(sharedBoard, /margin-inline:\s*auto;/);
    assert.match(css, /\.board\s*\{[^}]*aspect-ratio:\s*1\s*;/);
    const model = await source('board-layout.css');
    assert.match(model, /--play-board-limit:[^;]*var\(--play-column\)[^;]*100dvh[^;]*var\(--board-reference-chrome\)/);
    assert.doesNotMatch(css, /--play-chrome|--play-puzzle-text/);
    assert.match(css, /\.match-actions button\s*\{[^}]*flex:[^;]*min-content;[^}]*overflow-wrap:\s*normal;/);
    assert.match(css, /#match-history\s*\{[^}]*flex-wrap:\s*nowrap;/);
    assert.match(css, /#match-history :is\(#match-previous, #match-next\)\s*\{[^}]*min-inline-size:\s*var\(--play-action-size\);/);
    assert.match(puzzleCss, /grid-template-rows:\s*auto auto auto minmax\(0, 1fr\) auto;/);
    assert.match(puzzleCss, /\.quest-study-board-and-details\s*\{[^}]*container-type:\s*inline-size;/);
    assert.match(puzzleCss, /#quest-study-board\s*\{[^}]*inline-size:\s*min\(100%, var\(--play-board-limit\)\)/);
    assert.match(puzzleCss, /#quest-study-game\s*\{[^}]*block-size:\s*calc\(100dvh - var\(--quest-insets\)\);[^}]*min-block-size: 0/);
    assert.match(puzzleCss, /\.quest-study-details,[^}]*overflow:\s*auto;/);

    assert.match(puzzleCss, /#quest-study-turn\s*\{[^}]*block-size:\s*calc\(1lh \+ 2 \* var\(--quest-turn-padding\)\)/,
        'the turn row has the same height with and without text');
    assert.match(css, /#match-inline-preview button:not\(\.square\)\s*\{\s*min-block-size:\s*var\(--play-action-size\)/,
        'preview squares follow the board grid while the controls retain their touch target');
    assert.doesNotMatch(css, /#match-inline-preview button\s*\{[^}]*min-block-size/,
        'a 48px minimum on every square clips the eighth row on compact screens');
    assert.match(css, /\.match-inline-preview-details\s*\{[^}]*overflow:\s*auto/,
        'only the evidence scrolls within the preview');
    assert.match(matchMarkup, /<dialog id="match-inline-preview"[^]*?<div id="match-inline-preview-board"[^]*?class="match-inline-preview-navigation"[^]*?class="match-inline-preview-details"/);

    assert.match(matchMarkup, /<section id="match"[^]*?<div id="match-board"[^>]*><\/div>[^]*?<nav id="match-history"[^]*?<div class="movement match-actions">/);
    assert.match(puzzleMarkup, /<section id="quest-study-game"[^]*?<div id="quest-study-board"[^>]*><\/div>[^]*?<div id="quest-study-description"[^]*?<div class="quest-study-footer">/);
});


test('Compact AI board spends the available width only on the rail, gap and square', async () => {
    const css = await readFile(new URL('../src/main/web/game-review.css', import.meta.url), 'utf8');
    const rule = css.match(/\.match\[data-mode="AI"\] > \.match-board-frame\s*\{([^}]+)\}/)?.[1];
    assert.ok(rule, 'the compact rule applies to the AI match board');
    assert.match(rule, /--match-meter-width:\s*var\(--board-meter-size\);/);
    assert.match(rule, /--match-meter-gap:\s*var\(--board-meter-gap\);/);
    assert.match(rule, /grid-template-columns:\s*var\(--match-meter-width\)\s+minmax\(0,\s*1fr\)/);
    assert.match(rule, /inline-size:\s*min\(100%,\s*calc\(var\(--play-board-limit\)\s*\+\s*var\(--match-meter-width\)\s*\+\s*var\(--match-meter-gap\)\)\)/);
    assert.match(css, /match-board-frame:has\(\.match-inline-meter\[hidden\]\)\s*\{[^}]*--match-meter-width:\s*0px;[^}]*--match-meter-gap:\s*0px;/);
    assert.match(css, /:root\s*\{[^}]*scrollbar-gutter:\s*stable;/);
    assert.doesNotMatch(css, /scrollbar-gutter:\s*auto;/, 'AI retains the same available width as the other screens');
    assert.match(css, /\.match\[data-mode="AI"\]\s+#match-board\s*\{[^}]*grid-column:\s*2;[^}]*inline-size:\s*100%;/);
});

test('Replay size records the source square and ignores hidden or invalid geometry', () => {
    const properties = new Map();
    const preview = { style: { setProperty: (name, value) => properties.set(name, value) } };
    for (const width of [192, 315.8, 328.8, 560]) {
        rememberBoardSize({ getBoundingClientRect: () => ({ width }) }, preview);
        assert.equal(properties.get('--replay-board-side'), width + 'px');
    }
    for (const width of [0, -1, NaN, Infinity]) {
        rememberBoardSize({ getBoundingClientRect: () => ({ width }) }, preview);
        assert.equal(properties.get('--replay-board-side'), '560px', 'hidden sources cannot erase a usable cap');
    }
});

test('Replay layout caps puzzle, match and analysis boards at the originating square', async () => {
    const css = await Promise.all(['study-quests.css', 'game-review.css', 'study-analysis.css']
        .map(name => readFile(new URL('../src/main/web/' + name, import.meta.url), 'utf8')));
    for (const [index, selector] of ['#quest-study-replay-board', '#match-inline-preview-board.board'].entries()) {
        const rule = css[index].slice(css[index].indexOf(selector)).split('}')[0];
        assert.match(rule, /(?:inline-size|width): min\(100%, var\(--replay-board-side, var\(--play-board-limit\)\), var\(--play-board-limit\)\)/);
        assert.doesNotMatch(rule, /100cqb|100cqh/);
    }
    assert.match(css[2], /\.analysis-study-replay \.analysis-study-stack\s*\{\s*max-inline-size: var\(--replay-board-side, 100%\);/);
});
