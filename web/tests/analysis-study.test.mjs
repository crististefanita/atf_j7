import assert from 'node:assert/strict';
import { test } from 'node:test';
import { analysisHintDescription, analysisScore, analysisStudySession, createAnalysisStudy } from '../src/main/web/study-analysis.mjs';
import { matchAnalysisNavigation, routeForHash, studyHashes, watchRouteHistory, writeModeHash } from '../src/main/web/study-navigation.mjs';

const flush = () => new Promise(resolve => setImmediate(resolve));
const frame = (revision, ply, extra = {}) => ({
    revision, ply, total: ply, result: 'IN_PROGRESS', analysis: true, evaluation: null, saved: 'line:' + ply, ...extra,
});

test('Mounted analysis restores its bookmark and requests one evaluation for each position', async () => {
    const observed = [];
    const renders = [];
    const local = new Map([['chesslab.analysis-study.v1', 'earlier-study']]);
    const storage = () => ({ getItem: key => local.get(key), setItem: (key, value) => local.set(key, value) });
    const session = analysisStudySession({
        storage,
        failed: () => assert.fail('The analysis request should succeed'),
        render: value => renders.push(value),
        call: async (action, input) => {
            observed.push([action, input]);
            if (action === 'resume' || action === 'flip') return frame(2, 1);
            if (action === 'tap') return frame(3, 2);
            return frame(input.index, input.index - 1, { evaluation: { centipawns: 20 } });
        },
    });
    await session.open();
    await flush();
    await session.request('flip');
    await session.request('tap', { value: 'e5' });
    await flush();
    assert.deepEqual(observed, [['resume', { saved: 'earlier-study' }], ['evaluate', { index: 2 }],
        ['flip', {}], ['tap', { value: 'e5' }], ['evaluate', { index: 3 }]]);
    assert.deepEqual(renders.map(state => state.ply), [1, 1, 1, 2, 2]);
    assert.equal(local.get('chesslab.analysis-study.v1'), 'line:2');
});

test('Late evaluation after a seek or route exit cannot replace the chosen board', async () => {
    const renders = [];
    const pending = [];
    const session = analysisStudySession({
        storage: () => undefined,
        failed: () => assert.fail('A reply without an active route should not surface an error'),
        render: value => renders.push(value.ply),
        call: async (action, input) => {
            if (action === 'open') return frame(1, input.index);
            if (action === 'seek') return frame(2, input.index, { analysis: false });
            return new Promise(resolve => pending.push(resolve));
        },
    });
    await session.open({ checkpoint: 'fixture', ply: 1, blackBelow: true });
    await session.request('seek', { index: 0 });
    pending[0](frame(1, 1, { evaluation: { centipawns: 15 } }));
    await flush();
    session.close();
    await session.open({ checkpoint: 'other', ply: 2 });
    session.close();
    pending[1](frame(1, 2, { evaluation: { centipawns: 15 } }));
    await flush();
    assert.deepEqual(renders, [1, 0, 2]);
});

test('Analysis retains a playable session when storage is blocked', async () => {
    const session = analysisStudySession({
        storage: () => { throw new Error('Storage disabled'); },
        failed: () => assert.fail('Unavailable storage must not disable the board'),
        render: () => {},
        call: async (action, input) => {
            assert.deepEqual([action, input], ['resume', { saved: '' }]);
            return frame(1, 0, { analysis: false });
        },
    });
    assert.equal((await session.open()).ply, 0);
});

test('Match analysis restores its own line after review without touching the standalone bookmark', async () => {
    const observed = [];
    const storageReads = [];
    const storageWrites = [];
    const storage = () => ({
        getItem: key => { storageReads.push(key); return 'standalone-before'; },
        setItem: (key, value) => storageWrites.push([key, value]),
    });
    const analysis = analysisStudySession({
        storage,
        failed: () => assert.fail('Both lines should restore'),
        render: () => {},
        call: async (action, input) => {
            observed.push([action, input]);
            const ply = action === 'resume' ? input.saved === 'standalone-before' ? 4 : 2 : input.index;
            return frame(1, ply, { analysis: false });
        },
    });
    await analysis.open({ checkpoint: 'game-checkpoint', ply: 3, blackBelow: true });
    await analysis.request('seek', { index: 2 });
    analysis.close();
    assert.equal((await analysis.restore()).ply, 2, 'return from Game Review preserves the contextual cursor');
    analysis.close();
    assert.deepEqual(observed, [
        ['open', { value: 'game-checkpoint', index: 3, option: 'black' }],
        ['seek', { index: 2 }],
        ['resume', { saved: 'line:2' }],
    ]);
    assert.deepEqual(storageReads, []);
    assert.deepEqual(storageWrites, []);

    assert.equal((await analysis.open()).ply, 4);
    assert.deepEqual(observed.at(-1), ['resume', { saved: 'standalone-before' }]);
    assert.deepEqual(storageReads, ['chesslab.analysis-study.v1']);
    assert.deepEqual(storageWrites, [['chesslab.analysis-study.v1', 'line:4']]);
});

test('Contextual analysis returns once to the exact active or archived game and restores focus', () => {
    const origins = [['ai', '#ai'], ['match', '#game'], ['archive', '#archive/finished-12']];
    for (const [mode, hash] of origins) {
        const match = Object.freeze({
            checkpoint: 'encoded-ledger-move-history', reviewCheckpoint: 'bare-chess-line',
            viewPly: 2, ply: 5, blackBelow: true, revision: 6,
        });
        const shown = [];
        const history = [];
        let screen = mode;
        let focused = 0;
        let onHashChange;
        const browser = {
            location: { hash },
            history: {
                pushState(state, _title, hash) {
                    history.push(['push', state, hash]);
                    browser.location.hash = hash;
                },
                back() { history.push(['back']); },
            },
            addEventListener(type, handler) {
                assert.equal(type, 'hashchange');
                onHashChange = handler;
            },
        };
        const navigation = matchAnalysisNavigation({
            browser,
            currentMatch: () => match,
            openStudy(entry) {
                screen = 'analysis';
                shown.push(['open', entry]);
                writeModeHash(browser, studyHashes.analysis, true);
            },
            returnToMatch(route) {
                screen = route;
                shown.push(['resume source', route, match.viewPly, match.revision]);
            },
        });
        watchRouteHistory(browser, () => screen === 'analysis' ? studyHashes.analysis : hash, destination => {
            if (navigation.restore(destination)) return;
            screen = routeForHash(destination).mode;
            shown.push(['direct', destination]);
        });
        const opener = { isConnected: true, focus({ preventScroll }) { assert.equal(preventScroll, true); focused++; } };
        assert.equal(navigation.open(mode, opener), true);
        assert.equal(navigation.open(mode, opener), false, 'double activation must not replace the source');
        assert.equal(browser.location.hash, studyHashes.analysis);
        assert.equal(navigation.back(), true);
        assert.equal(navigation.back(), true);
        browser.location.hash = hash;
        onHashChange();
        onHashChange();
        assert.equal(focused, 1);
        assert.deepEqual(history, [['push', null, '#analysis'], ['back']], 'no source data enters browser history');
        assert.deepEqual(shown, [
            ['open', { checkpoint: 'bare-chess-line', ply: 2, blackBelow: true }],
            ['resume source', mode, 2, 6],
        ]);
        assert.equal(navigation.back(), false);
        browser.location.hash = '#analysis';
        onHashChange();
        assert.deepEqual(shown.at(-1), ['direct', '#analysis'], 'forward navigation has no stale transient source');
    }
});

test('Leaving contextual analysis through another route discards the old game origin', () => {
    const current = { reviewCheckpoint: 'game', viewPly: 0, blackBelow: false };
    const navigation = matchAnalysisNavigation({
        browser: { location: { hash: '#ai' }, history: { back: () => assert.fail('No saved origin remains') } },
        currentMatch: () => current,
        openStudy: () => {},
        returnToMatch: () => assert.fail('Do not replace a newly selected route'),
    });
    assert.equal(navigation.open('ai'), true);
    assert.equal(navigation.restore('#saved'), false);
    assert.equal(navigation.restore('#ai'), false);
    assert.equal(navigation.back(), false);
});

test('Reported analysis score uses the same side, mate and decimal policy as Android', () => {
    assert.equal(analysisScore({ ending: 'HORIZON', centipawns: 42 }, 'ro'), '+0,42 · Alb');
    assert.equal(analysisScore({ ending: 'HORIZON', centipawns: -42 }, 'en'), '−0.42 · Black');
    assert.equal(analysisScore({ ending: 'HORIZON', centipawns: 0 }, 'en'), '0.00 · Balanced');
    assert.equal(analysisScore({ ending: 'CHECKMATE', mate: -2 }, 'ro'), 'Negru · Mat în 2');
    assert.equal(analysisScore({ ending: 'FINISHED' }, 'en'), 'AI analysis · Completed game');
});

test('A revealed analysis hint names its moves and opening for screen readers', () => {
    const hint = { move: 'e2e4', number: 1, count: 3, family: { ro: 'Jocul deschis', en: 'Open game' } };
    assert.equal(analysisHintDescription(hint, 'ro'), 'Varianta 1 din 3. e2 la e4. Jocul deschis.');
    assert.equal(analysisHintDescription({ move: 'a7a8n', number: 1, count: 1 }, 'en'), 'Variation 1 of 1. a7 to a8 Knight.');
    assert.equal(analysisHintDescription({ number: 0 }, 'en'), '');
});

async function mountAnalysis(openingState, locale = 'en') {
    class Element {
        constructor(tag) { this.tag = tag; }
        children = [];
        attributes = new Map();
        events = new Map();
        classList = { add() {}, toggle() {} };
        dataset = {};
        style = { setProperty(name, value) { this[name] = value; } };
        width = 328.8;
        getBoundingClientRect() { return { width: this.width }; }
        hidden = false;
        scrollLeft = 0;
        append(...items) {
            this.children.push(...items);
            for (const item of items) if (typeof item === 'object') item.parentElement = this;
        }
        replaceChildren(...items) { this.children = items; }
        setAttribute(name, value) { this.attributes.set(name, value); }
        removeAttribute(name) { this.attributes.delete(name); }
        addEventListener(name, action) { this.events.set(name, action); }
        focus(options) { this.ownerDocument.activeElement = this; this.focusOptions = options; }
        contains(item) { return this === item || this.children.some(child => typeof child === 'object' && child.contains(item)); }
        querySelector() {
            for (const child of this.children) {
                if (typeof child !== 'object') continue;
                if (['input', 'button'].includes(child.tag) && !child.hidden && !child.disabled) return child;
                const match = child.querySelector();
                if (match) return match;
            }
            return null;
        }
    }

    const root = new Element('section');
    const document = {
        events: new Map(),
        addEventListener(name, action) { this.events.set(name, action); },
        createElement(tag) { return Object.assign(new Element(tag), { ownerDocument: document }); },
    };
    root.ownerDocument = document;
    const commands = [];
    const view = createAnalysisStudy({ root, locale, storage: () => undefined,
        home: () => assert.fail('The study stays open'), board: () => ({ render() {} }),
        call: async (action, { index }) => {
            commands.push(action);
            if (action === 'resume') return openingState;
            if (action === 'flip') return { ...openingState, blackBelow: !openingState.blackBelow };
            assert.equal(action, 'seek');
            return { ...openingState, ply: index };
        },
    });
    await view.open();
    return { root, view, commands, setFrame(value) { openingState = value; } };
}

test('Analysis places evaluation to the left and all history controls in APK order', async () => {
    const evaluation = { ending: 'HORIZON', centipawns: 20, whiteShare: .55 };
    const { root } = await mountAnalysis(frame(1, 1, { blackBelow: true, sideToMove: 'BLACK', history: ['e2e4'], promotions: [], evaluation }));
    const stage = root.children.find(item => item.className === 'analysis-study-stage');
    const [top, panel, bottom] = stage.children[0].children;
    const [meter, board] = panel.children;
    assert.equal(meter.attributes.get('role'), 'meter');
    assert.equal(meter.attributes.get('aria-valuenow'), 55);
    assert.equal(meter.children[0].style.insetBlockStart, '0');
    assert.equal(board.className, 'analysis-study-board');
    assert.equal(top.textContent, 'White');
    assert.equal(bottom.textContent, 'Black to move');

    const toolbar = root.children.find(item => item.className?.includes('analysis-study-toolbar'));
    assert.deepEqual(toolbar.children.filter(item => !item.hidden).map(item => item.attributes.get('aria-label')),
        ['Previous move', 'Move history', 'Next move', 'Jump to latest move', 'Show AI variation']);
    assert.equal(toolbar.children[2].tag, 'nav');
    assert.equal(toolbar.children[2].children[1].attributes.get('aria-current'), 'step');

    const { root: replay } = await mountAnalysis(frame(1, 1, { history: ['e2e4'], promotions: [], preview: {
        kind: 'THEORY', sideToMove: 'BLACK', ply: 0, total: 4, branch: 0,
        branches: ['e7e5', 'c7c5'], name: { en: 'Italian Game' }, eco: 'C54', idea: { en: 'Control the center' },
    } }));
    const details = replay.children.find(item => item.className === 'analysis-study-replay-details');
    const replayToolbar = replay.children.find(item => item.className?.includes('analysis-study-toolbar'));
    const variants = replay.children.find(item => item.className === 'analysis-study-variants');
    assert.equal(details.hidden, false);
    assert.deepEqual(variants.children.map(item => item.textContent), ['Line 1', 'Line 2']);
    assert.equal(variants.children[1].attributes.get('aria-label'), 'Line 2 · c7 → c5');
    assert.match(details.children[0].textContent, /Control the center/);
    assert.equal(details.children.includes(replayToolbar), false);
    assert.equal(replayToolbar.children[2].hidden, true);
    assert.match(replayToolbar.children[1].textContent, /Step 0 of 4 · e7 → e5/);
});

test('Selecting an offscreen analysis move scrolls only the strip in either direction', async () => {
    const { root, view } = await mountAnalysis(frame(1, 0, { analysis: false, total: 3,
        history: ['e2e4', 'e7e5', 'g1f3'], promotions: [] }));
    const toolbar = root.children.find(item => item.className?.includes('analysis-study-toolbar'));
    const strip = toolbar.children.find(item => item.tag === 'nav');
    strip.clientWidth = 100;
    strip.getBoundingClientRect = () => ({ left: 40, right: 140 });
    strip.children.forEach((item, index) => {
        item.getBoundingClientRect = () => ({ left: 40 + index * 60 - strip.scrollLeft, right: 100 + index * 60 - strip.scrollLeft });
    });

    await view.seek(3);
    assert.equal(strip.scrollLeft, 140);
    assert.equal(strip.children[3].attributes.get('aria-current'), 'step');
    assert.equal(strip.children[0].attributes.has('aria-current'), false);
    await view.seek(0);
    assert.equal(strip.scrollLeft, 0);
    assert.equal(strip.children[0].attributes.get('aria-current'), 'step');
    assert.equal(root.scrollLeft, 0);
});

test('Analysis replay captures the source before hiding its meter and keeps the size through stepping', async () => {
    const normal = frame(1, 0, { analysis: false, promotions: [], history: [], sideToMove: 'WHITE' });
    const { root, view, setFrame } = await mountAnalysis(normal);
    const board = root.children.find(item => item.className === 'analysis-study-stage').children[0].children[1].children[1];
    board.width = 315.8;
    const preview = { kind: 'THEORY', ply: 0, total: 2, branch: 0, branches: ['e2e4'], sideToMove: 'WHITE' };
    setFrame({ ...normal, preview });
    await view.seek(0);
    assert.equal(root.style['--replay-board-side'], '315.8px');
    board.width = 560;
    await view.seek(1);
    assert.equal(root.style['--replay-board-side'], '315.8px', 'the preview cannot capture its own enlarged container');
    setFrame(normal);
    await view.seek(0);
    setFrame({ ...normal, preview });
    await view.seek(0);
    assert.equal(root.style['--replay-board-side'], '560px', 'opening again uses the current normal board');
});

test('Analysis options use the shared anchored menu and preserve the board on dismissal in RO and EN', async () => {
    for (const [locale, label] of [['en', 'Analysis board options'], ['ro', 'Opțiunile tablei de analiză']]) {
        const { root, view, commands } = await mountAnalysis(frame(1, 0, {
            analysis: false, promotions: [], history: [], sideToMove: 'WHITE',
        }), locale);
        const header = root.children.find(child => child.className === 'analysis-study-header');
        const menu = header.children.find(child => child.className === 'analysis-study-menu options-anchor');
        const [trigger, panel] = menu.children;
        const board = root.children.find(child => child.className === 'analysis-study-stage');
        assert.equal(trigger.tag, 'button');
        assert.equal(trigger.attributes.get('aria-label'), label);
        assert.equal(trigger.attributes.get('aria-controls'), panel.id);
        assert.equal(panel.hidden, true);
        trigger.events.get('click')();
        assert.equal(panel.hidden, false);
        assert.equal(trigger.attributes.get('aria-expanded'), 'true');
        assert.equal(root.ownerDocument.activeElement, panel.children[0].children[0]);
        let handled = false;
        menu.events.get('keydown')({ key: 'Escape', preventDefault() { handled = true; }, stopPropagation() {} });
        assert.equal(handled, true);
        assert.equal(panel.hidden, true);
        assert.equal(root.ownerDocument.activeElement, trigger);
        assert.deepEqual(trigger.focusOptions, { preventScroll: true });
        assert.deepEqual(commands, ['resume']);
        trigger.events.get('click')();
        panel.children.at(-1).events.get('click')();
        await flush();
        assert.equal(panel.hidden, true);
        assert.equal(root.ownerDocument.activeElement, trigger);
        assert.deepEqual(commands, ['resume', 'flip']);
        assert.equal(root.children.find(child => child.className === 'analysis-study-stage'), board);
        trigger.events.get('click')();
        view.close();
        assert.equal(panel.hidden, true);
    }
});
