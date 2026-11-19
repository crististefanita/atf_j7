import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { chrome, backTargets } from '../src/main/web/chrome-contract.mjs';
import { backLabel, installChrome, compactAutomation, matchBackPage } from '../src/main/web/screen-chrome.mjs';
import { optionsMenuPlacement } from '../src/main/web/options-menu.mjs';
import { cssDeclarations, cssPixels } from './support/css-length.mjs';

assert.equal(matchBackPage('ai', true, 'home'), 'MATCH');
assert.equal(matchBackPage('ai', true, 'saved'), 'ANALYSIS');
assert.equal(matchBackPage('ai', false, 'saved'), 'MATCH');
assert.equal(matchBackPage('saved', false, 'ai'), 'MATCH');

const cases = JSON.parse(await readFile(new URL('../../atf-tests/src/test/resources/ui-chrome-cases.json', import.meta.url)));
const css = await readFile(new URL('../src/main/web/chess-controls.css', import.meta.url), 'utf8');

test('The shared viewport cases center navigation and keep minimum touch targets', () => {
    const declarations = cssDeclarations(css);
    const rule = css.match(/\[data-chess-navigation]\s*\{([^}]+)}/)?.[1];
    assert(rule, 'Shared navigation rule');
    assert.match(rule, /margin-inline:\s*auto/);
    const width = rule.match(/inline-size:\s*([^;]+);/)?.[1];
    for (const profile of cases.profiles) {
        const actual = cssPixels(width.replace('100%', profile.width + 'px'),
            { variables: declarations, rem: 16 * profile.font, viewportHeight: profile.height });
        assert.equal(actual, profile.railWidth, profile.id);
        assert.equal(cssPixels(declarations['--chess-control-size'],
            { variables: declarations, rem: 16 * profile.font, viewportHeight: profile.height }), chrome.target);
        assert(actual >= 2 * chrome.target, profile.id + ' keeps both arrow targets');
    }
});

test('The same popup fixtures stay beside their trigger and retain scroll space', () => {
    for (const profile of cases.profiles) {
        const [left, top, right, bottom] = profile.anchor;
        const placed = optionsMenuPlacement({left, top, right, bottom}, {width:profile.panel[0],height:profile.panel[1]}, profile);
        assert.deepEqual([placed.left,placed.top,Math.min(profile.panel[0],placed.maxWidth),Math.min(profile.panel[1],placed.maxHeight)], profile.menu, profile.id);
    }
});

test('Contextual Back destinations remain identical without replacing route callbacks', () => {
    assert.deepEqual(backTargets, cases.pages);
    for (const page of Object.keys(cases.pages)) for (const locale of ['en','ro']) assert(backLabel(page,locale).length > 0);
    assert.equal(backLabel('PUZZLE','en'),'Back to puzzle chapters');
    assert.equal(backLabel('PUZZLES','ro'),'Înapoi la meniul principal');
    assert.throws(() => backLabel('UNKNOWN','en'),RangeError);
});

test('Compact automation preserves the full accessible name and checked state', () => {
    const attributes = new Map();
    const input = { checked:false,setAttribute:(k,v)=>attributes.set(k,v) };
    const label = {};
    const listeners = new Set();
    const media = {matches:true,addEventListener:(_,f)=>listeners.add(f),removeEventListener:(_,f)=>listeners.delete(f)};
    const stop = compactAutomation({matchMedia:()=>media},input,label,'Let AI play its moves');
    assert.equal(label.textContent,'AI');
    assert.equal(attributes.get('aria-label'),'Let AI play its moves');
    assert.equal(input.checked,false);
    media.matches=false; for(const render of listeners)render();
    assert.equal(label.textContent,'Let AI play its moves');
    assert.equal(input.checked,false);
    stop(); assert.equal(listeners.size,0);
});

test('The generated policy styles adapters without changing game or line state', () => {
    const properties = new Map();
    const document = {documentElement:{style:{setProperty:(key,value)=>properties.set(key,value)}}};
    const state = Object.freeze({branch:1,ply:2,checkpoint:'2|analysis|e2e4 e7e5'});
    installChrome(document);
    assert.equal(properties.get('--chess-navigation-max'),chrome.navigationMax+'px');
    assert.equal(properties.get('--chess-screen-title'),chrome.title/16+'rem');
    assert.equal(properties.get('--chess-header-gap'),chrome.gap+'px');
    assert.equal(state.branch,1); assert.equal(state.ply,2);
});
