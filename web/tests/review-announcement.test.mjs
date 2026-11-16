import assert from 'node:assert/strict';
import { test } from 'node:test';
import { reviewAnnouncementChannel, reviewGraphText, reviewPositionText, reviewWhyText,
    sliderPlyForKey, updateReviewRange } from '../src/main/web/review-announcement.mjs';

test('Move text speaks the recorded move and position in both languages', () => {
    const ro = reviewPositionText('ro', '2. g2 → g4 · Greșeală gravă', 3, 4);
    const en = reviewPositionText('en', '2. g2 → g4 · Blunder', 3, 4);
    assert.match(ro, /g2 la g4.*Greșeală gravă.*Poziția 3 din 4/);
    assert.match(en, /g2 to g4.*Blunder.*Position 3 of 4/);
    assert.doesNotMatch(ro + en, /[→·]/);
});

test('Graph text adds the White score without losing the move or cursor', () => {
    const ro = reviewPositionText('ro', '2. g2 → g4 · Greșeală gravă', 3, 4);
    const en = reviewPositionText('en', '2. g2 → g4 · Blunder', 3, 4);
    const romanian = reviewGraphText('ro', ro, '51,8');
    const english = reviewGraphText('en', en, '51.8');
    assert.ok(romanian.includes(ro));
    assert.ok(english.includes(en));
    assert.match(romanian, /albului.*51,8/i);
    assert.match(english, /White.*51\.8/i);
    assert.match(reviewGraphText('en', en, '0.0'), /White score 0\.0 percent\./);
    assert.equal(reviewGraphText('ro', ro, null), ro);
});

test('A committed slider change publishes one textual value and keeps unchanged renders silent', () => {
    const writes = [];
    let value = '3';
    let announcement = 'Position 3 of 4. White score 0.0 percent.';
    const slider = {
        max: '4', disabled: false,
        get value() { return value; },
        set value(next) { writes.push(['value', next]); value = next; },
        getAttribute(name) { return name === 'aria-valuetext' ? announcement : null; },
        setAttribute(name, next) { writes.push([name, next]); announcement = next; }
    };
    updateReviewRange(slider, 3, 4, announcement);
    assert.deepEqual(writes, []);
    const next = 'Position 2 of 4. White score 40.5 percent.';
    updateReviewRange(slider, 2, 4, next);
    updateReviewRange(slider, 2, 4, next);
    assert.deepEqual(writes, [['aria-valuetext', next], ['value', '2']]);
    updateReviewRange(slider, 0, 0, 'Starting position.');
    assert.deepEqual([slider.max, slider.value, slider.disabled, announcement], ['0', '0', true, 'Starting position.']);
});

test('Why text keeps the recorded move with or without a detailed reason', () => {
    const ro = reviewPositionText('ro', '2. g2 → g4 · Greșeală gravă', 3, 4);
    const en = reviewPositionText('en', '2. g2 → g4 · Blunder', 3, 4);
    assert.match(reviewWhyText('ro', ro, ''), /De ce\?.*g2 la g4/);
    assert.match(reviewWhyText('en', en, 'The reply mates.'), /Why\?.*g2 to g4.*The reply mates\./);
});

test('Focus, value and live status are mutually exclusive channels', () => {
    assert.equal(reviewAnnouncementChannel('graph', 'seek'), 'value');
    assert.equal(reviewAnnouncementChannel('history', 'selected-history'), 'focus');
    assert.equal(reviewAnnouncementChannel('open-why', 'close-why'), 'focus');
    assert.equal(reviewAnnouncementChannel('close-why', 'why'), 'focus');
    assert.equal(reviewAnnouncementChannel('toolbar', 'next'), 'status');
    assert.equal(reviewAnnouncementChannel('graph', ''), 'status');
    assert.equal(reviewAnnouncementChannel('unknown', ''), null);
});

test('Slider commands clamp at the archived endpoints', () => {
    assert.equal(sliderPlyForKey('ArrowLeft', 3, 185), 2);
    assert.equal(sliderPlyForKey('ArrowRight', 3, 185), 4);
    assert.equal(sliderPlyForKey('ArrowUp', 185, 185), 185);
    assert.equal(sliderPlyForKey('ArrowDown', 0, 185), 0);
    assert.equal(sliderPlyForKey('Home', 93, 185), 0);
    assert.equal(sliderPlyForKey('End', 93, 185), 185);
    assert.equal(sliderPlyForKey('PageUp', 185, 185), 185);
    assert.equal(sliderPlyForKey('PageDown', 0, 185), 0);
    assert.equal(sliderPlyForKey('Tab', 3, 185), null);
});
