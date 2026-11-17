import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { setImmediate } from 'node:timers/promises';
import { createHomeMenu, hasSavedHomeGames, homeLabels, homeLocale, reloadProfileLanguage, saveHomeProfile } from '../src/main/web/home-menu.mjs';
import { profileKey, readProfile, writeProfile } from '../src/main/web/player-profile.mjs';
import { showReviewPiece } from '../src/main/web/review-workspace.mjs';
import { routeForHash } from '../src/main/web/study-navigation.mjs';
import { createMatchBotTimer } from '../src/main/web/match-bot-timer.mjs';

const base = new URL('../../', import.meta.url);
const read = path => readFile(new URL(path, base), 'utf8');

const translations = {
    'home-games-heading': 'menu_play', 'home-study-heading': 'menu_study', 'home-profile-heading': 'profile_title',
    'open-ai': 'new_match', 'open-saved': 'match_saved_games', 'open-finished': 'match_archive',
    'home-puzzles': 'menu_puzzles', 'home-openings': 'openings_title', 'home-analysis': 'manual_analysis',
    'home-settings': 'settings_title', 'profile-name-label': 'profile_name_hint', 'profile-language-label': 'settings_language_label',
    'save-profile': 'profile_save', 'profile-reset': 'profile_reset', defaultName: 'profile_default',
};

const groups = [
    ['home_games', 'home-games', ['new_match', 'resume_match', 'match_archive'], ['open-ai', 'open-saved', 'open-finished']],
    ['home_study', 'home-study', ['open_puzzles', 'home_totals', 'open_openings', 'open_analysis'],
        ['home-puzzles', 'home-study-totals', 'home-openings', 'home-analysis']],
    ['home_profile', 'home-profile', ['edit_player_name', 'open_settings'], ['home-name', 'home-settings']],
];

test('Web home preserves Android groups, action order and RO and EN labels', async () => {
    const html = await read('web/src/main/web/index.html');
    const layout = await read('android/app/src/main/res/layout/main_menu.xml');
    const nativeHeading = layout.match(/<TextView\b[^>]*style="@style\/MenuHeading"[^>]*>/)?.[0];
    const strings = await read('android/app/src/main/res/values/strings.xml');
    assert.equal(nativeHeading?.match(/android:text="@string\/([^"]+)"/)?.[1], 'app_name');
    const name = strings.match(/<string name="app_name" translatable="false">([^<]+)<\/string>/)?.[1];
    assert.ok(name, 'The Android home heading resolves to the app name');
    assert.equal(html.match(/<title>([^<]+)<\/title>/)?.[1], name);
    assert.equal(html.match(/<h1\b[^>]*\sid="title"[^>]*>([^<]+)<\/h1>/)?.[1], name);

    for (const [nativeId, webId, nativeActions, webActions] of groups) {
        const native = layout.split('android:id="@+id/' + nativeId + '"')[1].split('</LinearLayout>')[0];
        const web = html.split('id="' + webId + '"')[1].split('</div>')[0];
        assert.deepEqual([...native.matchAll(/android:id="@\+id\/([^"]+)"/g)].map(match => match[1]), nativeActions);
        assert.deepEqual([...web.matchAll(/id="([^"]+)"/g)].map(match => match[1]), webActions);
    }
    for (const [locale, folder] of [['ro', 'values-ro'], ['en', 'values']]) {
        const prefix = 'android/app/src/main/res/' + folder;
        const resources = await read(prefix + '/strings.xml') + await read(prefix + '/settings.xml');
        for (const [webId, resource] of Object.entries(translations)) {
            const value = resources.match(new RegExp('<string name="' + resource + '">([^<]+)'));
            assert.equal(homeLabels[locale][webId], value?.[1], webId + ' in ' + locale);
        }
    }
});

test('Only active saved games show the home action', () => {
    assert.equal(hasSavedHomeGames([]), false);
    assert.equal(hasSavedHomeGames([{ result: 'BLACK_WINS' }]), false);
    assert.equal(hasSavedHomeGames([{ result: 'IN_PROGRESS' }]), true);
});

test('AI color choices preserve Android order and RO and EN labels', async () => {
    const [html, adapter, arrays, english, romanian] = await Promise.all([
        read('web/src/main/web/index.html'),
        read('web/src/main/web/game-review.mjs'),
        read('android/app/src/main/res/values/match_arrays.xml'),
        read('android/app/src/main/res/values/strings.xml'),
        read('android/app/src/main/res/values-ro/strings.xml'),
    ]);
    const colorArray = arrays.match(/<string-array name="match_colors">([^]*?)<\/string-array>/)?.[1] ?? '';
    const nativeIds = [...colorArray.matchAll(/<item>@string\/(\w+)<\/item>/g)].map(match => match[1]);
    const colorSelect = html.match(/<select id="ai-side">([^]*?)<\/select>/)?.[1] ?? '';
    const labels = [...colorSelect.matchAll(/<option id="([^"]+)" value="([^"]+)">([^<]+)<\/option>/g)];
    const resourceFor = { WHITE: 'white', BLACK: 'black', RANDOM: 'match_random_color' };
    assert.deepEqual(labels.map(([, , value]) => resourceFor[value]), nativeIds);
    assert.deepEqual(nativeIds, ['white', 'black', 'match_random_color']);
    const nativeLabel = (resources, id) => resources.match(new RegExp('<string name="' + id + '">([^<]+)'))?.[1];
    for (const [index, [, id, , label]] of labels.entries()) {
        assert.equal(label, nativeLabel(english, nativeIds[index]), id);
        const localized = [...adapter.matchAll(new RegExp("'" + id + "': '([^']+)'", 'g'))].map(match => match[1]);
        assert.deepEqual(localized, [label, nativeLabel(romanian, nativeIds[index])], id);
    }
});

test('The match options expose native board actions in RO and EN', async () => {
    const [html, adapter, toolbar, english, romanian] = await Promise.all([
        read('web/src/main/web/index.html'), read('web/src/main/web/game-review.mjs'),
        read('android/app/src/main/res/layout/match_toolbar.xml'),
        read('android/app/src/main/res/values/strings.xml'), read('android/app/src/main/res/values-ro/strings.xml'),
    ]);
    assert.match(toolbar, /android:contentDescription="@string\/match_actions"/);
    const anchor = html.match(/<div id="match-options-anchor"[^>]*>([^]*?)<h1 id="title">/)?.[1];
    assert.match(anchor, /id="match-options-button"[^>]+aria-controls="match-options" aria-expanded="false"/);
    const panel = anchor.match(/<div id="match-options"[^>]* role="group"[^>]* hidden>([^]*?)<\/div>/)?.[1];
    assert.deepEqual([...panel.matchAll(/<button id="([^"]+)"/g)].map(entry => entry[1]),
        ['flip-match', 'open-review', 'menu-saved', 'menu-analysis', 'menu-review', 'menu-draw', 'menu-resign']);
    for (const [id, key] of [['match-options-title', 'match_actions'], ['flip-match', 'flip'], ['open-review', 'main_menu'],
        ['menu-saved', 'match_saved_games'], ['menu-analysis', 'manual_analysis'], ['menu-review', 'match_game_review'],
        ['menu-draw', 'match_offer_draw'], ['menu-resign', 'match_resign'], ['ai-analysis-label', 'match_analysis_option'],
        ['resign-confirm', 'match_confirm_resign'], ['resign-message', 'match_resign_confirm']]) {
        const localized = [...adapter.matchAll(new RegExp("'" + id + "': '([^']+)'", 'g'))].map(match => match[1]);
        const nativeLabels = [english, romanian].map(resources => resources.match(new RegExp('<string name="' + key + '">([^<]+)'))?.[1]);
        assert.deepEqual(localized, nativeLabels, id);
    }
});

test('The automatic AI control has the native RO and EN labels', async () => {
    const [html, adapter, toolbar, en, ro, css] = await Promise.all([
        read('web/src/main/web/index.html'), read('web/src/main/web/game-review.mjs'),
        read('android/app/src/main/res/layout/match_toolbar.xml'),
        read('android/app/src/main/res/values/strings.xml'), read('android/app/src/main/res/values-ro/strings.xml'),
        read('web/src/main/web/game-review.css'),
    ]);
    assert.match(toolbar, /<CheckBox[^]*android:minHeight="48dp"[^]*android:text="@string\/match_automatic_ai"/);
    assert.match(html, /<label id="match-automatic-option"[^>]*hidden>\s*<input id="match-automatic" type="checkbox">/);
    assert.match(css, /\.match-automatic\s*\{[^}]*min-block-size:\s*var\(--play-action-size\);/);
    for (const [key, id] of [["'match-automatic-label'", 'match_automatic_ai'], ['manualAiTurn', 'match_manual_ai_turn']]) {
        const actual = [...adapter.matchAll(new RegExp(key + ": '([^']+)'", 'g'))].map(match => match[1]);
        const native = [en, ro].map(resources => resources.match(new RegExp('<string name="' + id + '">([^<]+)'))?.[1]);
        assert.deepEqual(actual, native, id);
    }
});

test('A new board revision replaces the scheduled AI reply exactly once', () => {
    const pending = new Map();
    const applied = [];
    let next = 0;
    const clock = {
        setTimeout(action, delay) { assert.equal(delay, 650); pending.set(++next, action); return next; },
        clearTimeout(id) { pending.delete(id); },
    };
    const schedule = createMatchBotTimer(clock, 650, turn => applied.push(turn.revision));
    const before = { revision: 11 };
    schedule(before);
    schedule(before);
    assert.equal(pending.size, 1);
    const staleCallback = pending.get(1);
    schedule(undefined);
    assert.equal(pending.size, 0);
    schedule({ revision: 12 });
    assert.deepEqual([...pending.keys()], [2]);
    staleCallback();
    assert.deepEqual(applied, []);
    const replacement = pending.get(2);
    pending.delete(2);
    replacement();
    replacement();
    assert.deepEqual(applied, [12]);
});

test('Disabling automation aborts a running calculation and discards its result', async () => {
    const jobs = new Map();
    const sent = [];
    const computations = [];
    let next = 0;
    const clock = {
        setTimeout(action) { jobs.set(++next, action); return next; },
        clearTimeout(id) { jobs.delete(id); },
    };
    const schedule = createMatchBotTimer(clock, 650, async (turn, signal) => {
        const result = await new Promise(resolve => computations.push({ resolve, signal }));
        if (!signal.aborted) sent.push([turn.revision, result]);
    });
    const current = { revision: 7 };
    schedule(current);
    const oldReply = jobs.get(1)();
    schedule(current);
    assert.equal(computations.length, 1);
    schedule(undefined);
    assert.equal(computations[0].signal.aborted, true);
    schedule({ revision: 9 });
    const nextReply = jobs.get(2)();
    computations[0].resolve('e7e5');
    computations[1].resolve('c7c5');
    await Promise.all([oldReply, nextReply]);
    assert.deepEqual(sent, [[9, 'c7c5']]);
});

test('Profile persistence respects the native validator and write failures', async () => {
    const entries = new Map();
    const storage = { getItem: key => entries.get(key), setItem: (key, value) => entries.set(key, value) };
    const normalize = async name => {
        if (name === '?') throw new TypeError('Native validator rejected the name');
        return name.trim().normalize();
    };
    writeProfile(storage, { name: 'Ion', language: 'ro' });
    assert.deepEqual(await saveHomeProfile(storage, { name: '  Ana  ' }, 'name', false, normalize), { name: 'Ana', language: 'ro' });
    await assert.rejects(saveHomeProfile(storage, { name: '?' }, 'name', false, normalize), /Native validator/);
    assert.deepEqual(readProfile(() => storage), { name: 'Ana', language: 'ro' });
    await assert.rejects(saveHomeProfile({ setItem() { throw Error('Full'); } }, { name: 'Ion' }, 'name', false, normalize), /Full/);
    assert.deepEqual(await saveHomeProfile(storage, { language: 'en' }, 'language', false, normalize), { name: 'Ana', language: 'en' });
    assert.deepEqual(await saveHomeProfile(storage, { name: '', language: 'ro' }, 'both', false, normalize), { name: '', language: 'ro' });
    assert.deepEqual(await saveHomeProfile(storage, { name: '?' }, 'name', true, normalize), { name: '', language: 'ro' });
    assert.deepEqual([...entries.keys()], [profileKey]);
    assert.equal(homeLocale('', storage), 'ro');
    assert.equal(homeLocale('?lang=en', storage), 'en');
    await assert.rejects(saveHomeProfile(storage, { language: 'fr' }, 'language', false, normalize), TypeError);
});

test('Board piece renderer reuses the same Staunton SVG on a move and clears it on capture', () => {
    class Element {
        children = [];
        attributes = new Map();
        dataset = {};
        constructor(kind, ownerDocument) { this.kind = kind; this.ownerDocument = ownerDocument; }
        setAttribute(attribute, value) { this.attributes.set(attribute, value); }
        removeAttribute(attribute) { if (attribute === 'data-shape') delete this.dataset.shape; }
        replaceChildren(...children) { this.children = children; }
        append(child) { this.children.push(child); }
    }
    const document = { createElementNS: (_, kind) => new Element(kind, document) };
    const art = { b: { body: 'M 0 0 L 10 10 Z', details: 'M 8 3 L 2 9' } };
    const piece = new Element('span', document);
    assert.equal(showReviewPiece(piece, 'b', art), true);
    const [svg] = piece.children;
    assert.equal(svg.attributes.get('viewBox'), '0 0 48 48');
    assert.deepEqual(svg.children.map(path => path.attributes.get('d')), [art.b.body, art.b.details]);
    assert.deepEqual(svg.children.map(path => path.attributes.get('stroke-width')), [1.35, 1.12]);
    assert.equal(showReviewPiece(piece, 'b', art), true);
    assert.equal(piece.children[0], svg);
    assert.equal(showReviewPiece(piece, ' ', art), false);
    assert.equal(piece.dataset.shape, undefined);
    assert.equal(piece.children.length, 0);
});

test('Home name, settings and AI profile share the existing dialog and stored profile', async () => {
    const html = await read('web/src/main/web/index.html');
    const controls = new Map();
    let focused;
    for (const [, id] of html.matchAll(/\bid="([^"]+)"/g)) {
        assert.equal(controls.has(id), false, 'duplicate HTML id: ' + id);
        const events = new Map();
        controls.set(id, {
            addEventListener: (type, handler) => events.set(type, handler),
            fire: type => events.get(type)({ preventDefault() {} }),
            focus: () => { focused = id; },
            select() {}, showModal() { this.open = true; }, close() { this.open = false; },
        });
    }
    const entries = new Map();
    const storage = { getItem: key => entries.get(key), setItem: (key, value) => entries.set(key, value) };
    writeProfile(storage, { name: 'Ion', language: 'ro' });
    const changes = [];
    const find = controls.get.bind(controls);
    const home = createHomeMenu(find, 'ro', storage, async name => name.trim(), (profile, fields) => changes.push({ profile, fields }));
    assert.equal(home.name, 'Ion');
    find('home-name').fire('click');
    assert.equal(focused, 'profile-name');
    assert.equal(find('profile-language-field').hidden, true);
    find('profile-name').value = ' Ana ';
    find('profile-form').fire('submit');
    await setImmediate();
    assert.equal(find('home-name').textContent, 'Nume · Ana');
    find('home-settings').fire('click');
    assert.equal(focused, 'profile-language');
    assert.equal(find('profile-name-field').hidden, true);
    find('profile-language').value = 'en';
    find('profile-form').fire('submit');
    await setImmediate();
    find('open-profile').fire('click');
    assert.equal(find('profile-name-field').hidden, false);
    assert.equal(find('profile-language-field').hidden, false);
    assert.equal(find('profile-name').value, 'Ana');
    assert.deepEqual(changes, [
        { profile: { name: 'Ana', language: 'ro' }, fields: 'name' },
        { profile: { name: 'Ana', language: 'en' }, fields: 'language' },
    ]);
    assert.deepEqual([...entries.keys()], [profileKey]);
});

test('Language reload retains Home with a saved review and preserves the AI profile route', async () => {
    const entries = new Map([
        ['chesslab.game-review.v1', 'saved review'], ['chesslab.match-ledger.v1', 'saved games'],
    ]);
    const storage = { getItem: key => entries.get(key), setItem: (key, value) => entries.set(key, value) };
    const browser = (href) => {
        const location = { href, reloads: 0, reload() { this.reloads++; } };
        const history = {
            state: { other: 'kept' },
            replaceState(state, _, url) {
                this.state = structuredClone(state);
                if (url) location.href = url;
            },
        };
        return { location, history };
    };

    const fromHome = browser('https://example.test/chess/?lang=en&example=1');
    const settings = await saveHomeProfile(storage, { language: 'ro' }, 'language', false);
    assert.equal(reloadProfileLanguage(fromHome, 'en', settings.language, 'language'), true);
    assert.equal(fromHome.location.reloads, 1);
    assert.equal(fromHome.location.href, 'https://example.test/chess/?example=1');
    assert.equal(homeLocale(new URL(fromHome.location.href).search, storage), 'ro');
    assert.deepEqual(routeForHash(new URL(fromHome.location.href).hash), { mode: 'home' });
    assert.equal(entries.get('chesslab.game-review.v1'), 'saved review');
    assert.equal(entries.get('chesslab.match-ledger.v1'), 'saved games');
    assert.deepEqual(fromHome.history.state, { other: 'kept' });
    assert.equal(reloadProfileLanguage(fromHome, 'ro', 'ro', 'language'), false);
    assert.equal(fromHome.location.reloads, 1);

    const inAi = browser('https://example.test/chess/?lang=ro&example=1#ai');
    assert.equal(reloadProfileLanguage(inAi, 'ro', 'en', 'both'), true);
    assert.equal(inAi.location.reloads, 1);
    assert.equal(inAi.location.href, 'https://example.test/chess/?example=1#ai');
    assert.deepEqual(routeForHash(new URL(inAi.location.href).hash), { mode: 'ai' });
    assert.deepEqual(inAi.history.state, { other: 'kept' });
});
