import assert from 'node:assert/strict';
import test from 'node:test';
import { profileKey, profileName, readProfile, writeProfile } from '../src/main/web/player-profile.mjs';

test('Profile names use normalized letters and digits and fit the Android limit', () => {
    assert.equal(profileName("  S\u0326tefan-2 "), 'Ștefan-2');
    assert.equal(profileName("Ana O'Neil"), "Ana O'Neil");
    assert.equal(profileName('   '), '');
    assert.equal(profileName('ABCDEFGHIJK'), null);
    assert.equal(profileName('<script>'), null);
    assert.equal(profileName("--'"), null);
});

test('The versioned profile is independent of saved games', () => {
    const data = new Map([['chesslab.match-ledger.v1', 'game']]);
    const storage = { getItem: key => data.get(key) ?? null, setItem: (key, value) => data.set(key, value) };
    assert.deepEqual(readProfile(() => storage), { name: '', language: 'en' });
    writeProfile(storage, { name: 'Ștefan', language: 'ro' });
    assert.deepEqual(readProfile(() => storage), { name: 'Ștefan', language: 'ro' });
    assert.equal(data.get('chesslab.match-ledger.v1'), 'game');
    data.set(profileKey, '{broken');
    assert.deepEqual(readProfile(() => storage), { name: '', language: 'en' });
    data.set(profileKey, '{"name":"<b>","language":"es"}');
    assert.deepEqual(readProfile(() => storage), { name: '', language: 'en' });
    assert.deepEqual(readProfile(() => { throw new Error('unavailable'); }), { name: '', language: 'en' });
    assert.throws(() => writeProfile({ setItem() { throw new Error('quota'); } }, { name: 'Ștefan', language: 'ro' }), /quota/);
});
