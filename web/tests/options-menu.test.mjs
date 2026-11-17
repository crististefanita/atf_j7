import assert from 'node:assert/strict';
import test from 'node:test';
import { attachOptionsMenu, followOptionsMenu, optionsMenuPlacement } from '../src/main/web/options-menu.mjs';

function eventTarget() {
    const listeners = new Map();
    return {
        addEventListener(type, callback) {
            if (!listeners.has(type)) listeners.set(type, new Set());
            listeners.get(type).add(callback);
        },
        removeEventListener(type, callback) { listeners.get(type)?.delete(callback); },
        emit(type, event = {}) {
            for (const callback of [...listeners.get(type) ?? []]) callback(event);
        },
        count() { return [...listeners.values()].reduce((total, values) => total + values.size, 0); },
    };
}

function fixture({ left = 18, top = 30, viewport } = {}) {
    const document = eventTarget();
    const view = Object.assign(eventTarget(), { innerWidth: 360, innerHeight: 640, visualViewport: viewport });
    document.defaultView = view;
    function element() {
        return {
            ...eventTarget(), children: [], style: {}, attributes: new Map(), ownerDocument: document,
            append(...items) { this.children.push(...items); for (const item of items) item.parentElement = this; },
            contains(item) { return this === item || this.children.some(child => child.contains(item)); },
            setAttribute(name, value) { this.attributes.set(name, value); },
            focus(options) { document.activeElement = this; this.focusOptions = options; },
        };
    }
    const anchor = element();
    const trigger = element();
    const panel = element();
    const first = element();
    const second = element();
    panel.append(first, second);
    anchor.append(trigger, panel);
    panel.hidden = true;
    panel.querySelector = () => first;
    const bounds = { left, top, right: left + 48, bottom: top + 48, width: 48, height: 48 };
    trigger.getBoundingClientRect = () => ({ ...bounds });
    panel.clientHeight = 240;
    panel.scrollHeight = 240;
    panel.getBoundingClientRect = () => ({ left: 0, top: 5000, right: 220, bottom: 5242, width: 220, height: 242 });
    return { document, view, anchor, trigger, panel, first, second, bounds, element };
}

test('Menu geometry stays beside its trigger on compact and desktop at 100 and 130 percent', () => {
    for (const [width, height] of [[320, 568], [360, 640], [720, 480], [1440, 900]]) {
        for (const scale of [1, 1.3]) {
            const anchor = { left: 16, right: 64, top: 12, bottom: 60 };
            const panel = { width: 240 * scale, height: 144 * scale };
            const value = optionsMenuPlacement(anchor, panel, { width, height });
            assert.equal(value.left, width === 320 && scale === 1.3 ? 8 : 16);
            assert.equal(value.top, 64);
            assert.ok(value.left + Math.min(panel.width, value.maxWidth) <= width - 8);
            assert.ok(value.top + Math.min(panel.height, value.maxHeight) <= height - 8);
        }
    }
});

test('Menus clamp sideways, flip above and retain scroll space for every option', () => {
    const viewport = { width: 320, height: 420 };
    const anchor = { left: 260, right: 308, top: 350, bottom: 398 };
    const panel = { width: 300, height: 600 };
    const value = optionsMenuPlacement(anchor, panel, viewport);
    assert.deepEqual(value, { left: 12, top: 8, maxWidth: 304, maxHeight: 338 });
    assert.ok(value.maxHeight < panel.height);
    const overflow = optionsMenuPlacement({ left: 8, right: 56, top: 20, bottom: 68 },
        { width: 500, height: 800 }, viewport);
    assert.deepEqual(overflow, { left: 8, top: 72, maxWidth: 304, maxHeight: 340 });
});

test('Zoomed visual viewport offsets constrain the menu in the actually visible region', () => {
    const value = optionsMenuPlacement({ left: 260, right: 308, top: 150, bottom: 198 },
        { width: 240, height: 140 }, { left: 100, top: 110, width: 220, height: 250 });
    assert.deepEqual(value, { left: 108, top: 202, maxWidth: 204, maxHeight: 150 });
});

test('A menu outside the trigger parent follows the trigger instead of the document bottom', () => {
    const { panel, trigger, view, bounds, element } = fixture();
    const distant = element();
    distant.append(panel);
    panel.hidden = false;
    const stop = followOptionsMenu(trigger, panel);
    assert.deepEqual([panel.style.position, panel.style.inset, panel.style.left, panel.style.top],
        ['fixed', 'auto', '18px', '82px']);
    bounds.top = 120;
    bounds.bottom = 168;
    view.emit('scroll');
    assert.equal(panel.style.top, '172px');
    stop();
    assert.equal(view.count(), 0);
});

test('Options focus and Escape preserve the page position and return to their own trigger', () => {
    const { trigger, panel, anchor, document, first, view } = fixture();
    const toggle = [];
    const options = attachOptionsMenu(trigger, panel, { onToggle: () => toggle.push(options.isOpen()) });
    trigger.emit('click');
    assert.equal(options.isOpen(), true);
    assert.equal(trigger.attributes.get('aria-expanded'), 'true');
    assert.equal(document.activeElement, first);
    assert.deepEqual(first.focusOptions, { preventScroll: true });
    let prevented = false;
    let stopped = false;
    anchor.emit('keydown', { key: 'Escape', preventDefault() { prevented = true; }, stopPropagation() { stopped = true; } });
    assert.deepEqual([prevented, stopped, options.isOpen()], [true, true, false]);
    assert.equal(trigger.attributes.get('aria-expanded'), 'false');
    assert.equal(document.activeElement, trigger);
    assert.deepEqual(trigger.focusOptions, { preventScroll: true });
    assert.equal(view.count(), 0);
    assert.deepEqual(toggle, [true, false]);
});

test('Outside interaction and blocked actions close or refuse the options without a focus trap', () => {
    const { trigger, panel, anchor, document, second, element, view } = fixture();
    let allowed = false;
    const options = attachOptionsMenu(trigger, panel, { canOpen: () => allowed });
    trigger.emit('click');
    assert.equal(options.isOpen(), false);
    assert.equal(view.count(), 0);
    allowed = true;
    trigger.emit('click');
    anchor.emit('focusout', { relatedTarget: second });
    assert.equal(options.isOpen(), true);
    const outside = element();
    anchor.emit('focusout', { relatedTarget: outside });
    assert.equal(options.isOpen(), false);
    trigger.emit('click');
    document.emit('pointerdown', { target: outside });
    assert.equal(panel.hidden, true);
    assert.equal(view.count(), 0);
});

test('Viewport changes update menu limits and leaving the visible trigger releases listeners', () => {
    const viewport = Object.assign(eventTarget(), { offsetLeft: 0, offsetTop: 0, width: 360, height: 640 });
    const { trigger, panel, view, bounds } = fixture({ viewport });
    const options = attachOptionsMenu(trigger, panel);
    trigger.emit('click');
    assert.equal(viewport.count(), 2);
    viewport.height = 240;
    viewport.emit('resize');
    assert.equal(panel.style.maxBlockSize, '150px');
    panel.scrollHeight = 800;
    viewport.width = 240;
    viewport.emit('scroll');
    assert.equal(panel.style.maxInlineSize, '224px');
    const previous = panel.style.top;
    view.emit('scroll', { target: panel });
    assert.equal(panel.style.top, previous);
    bounds.top = -80;
    bounds.bottom = -32;
    view.emit('scroll');
    assert.equal(options.isOpen(), false);
    assert.equal(view.count(), 0);
    assert.equal(viewport.count(), 0);
    trigger.emit('click');
    assert.equal(options.isOpen(), false);
    assert.equal(view.count(), 0);
    assert.equal(viewport.count(), 0);
});
