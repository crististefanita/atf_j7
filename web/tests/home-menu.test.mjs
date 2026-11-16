import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { setImmediate } from 'node:timers/promises';
import { createHomeMenu, hasSavedHomeGames, homeLabels, homeLocale, saveHomeProfile } from '../src/main/web/home-menu.mjs';
import { profileKey, readProfile, writeProfile } from '../src/main/web/player-profile.mjs';
import { showReviewPiece } from '../src/main/web/review-workspace.mjs';

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
