import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { hintArrow } from '../src/main/web/hint-arrow.mjs';
import { studyArrow } from '../src/main/web/study-board.mjs';
import { createReviewWorkspace } from '../src/main/web/review-workspace.mjs';

const examples = JSON.parse(await readFile(process.argv[2], 'utf8'));
const close = (actual, expected, label) => assert.ok(Math.abs(actual - expected) < 1e-7, `${label}: ${actual} != ${expected}`);
const center = cell => [cell.x + cell.width / 2, cell.y + cell.height / 2];
const number = value => (Math.abs(value) < 1e-10 ? 0 : value).toFixed(6);

function canvasArrow(example) {
    const points = [];
    const calls = {};
    const context = {
        setTransform(...values) { calls.transform = values; }, clearRect() {},
        translate(x, y) { calls.origin = [x, y]; }, rotate(angle) { calls.angle = angle; },
        beginPath() { points.length = 0; }, moveTo(x, y) { points.push([x, y]); },
        lineTo(x, y) { points.push([x, y]); }, closePath() {}, stroke() {}, fill() {},
    };
    const bounds = { x: 37, y: 61, width: example.boardPixels, height: example.boardPixels };
    function element() {
        const attributes = new Map();
        return {
            dataset: {}, hidden: false, style: { setProperty() {} },
            setAttribute(key, value) { attributes.set(key, value); }, getAttribute(key) { return attributes.get(key); },
            toggleAttribute() {}, addEventListener() {}, getBoundingClientRect() { return bounds; },
            querySelector() { return { getBoundingClientRect: () => ({ height: 64 }) }; },
        };
    }
    const elements = new Map();
    const find = id => { if (!elements.has(id)) elements.set(id, element()); return elements.get(id); };
    find('review-cue').getContext = () => context;
    find('board').querySelector = selector => {
        const square = /data-square="([^"]+)"/.exec(selector)?.[1];
        const cell = square === example.from ? example.source : square === example.to ? example.destination : null;
        return cell && { getBoundingClientRect: () => cell };
    };
    const frames = [];
    const previous = Object.fromEntries(['document', 'window', 'requestAnimationFrame', 'ResizeObserver'].map(key => [key, globalThis[key]]));
    try {
        globalThis.document = { getElementById: find };
        globalThis.window = { devicePixelRatio: example.ratio };
        globalThis.requestAnimationFrame = action => { frames.push(action); return frames.length; };
        globalThis.ResizeObserver = class { observe() {} };
        const workspace = createReviewWorkspace('en', () => {});
        workspace.render({ moves: [], totalPlies: 0, ply: 0, branch: 'HISTORY', step: 0, lineLength: 0,
            cueFrom: example.from, cueTo: example.to });
        for (const frame of frames) frame();
    } finally {
        Object.assign(globalThis, previous);
    }
    assert.equal(points.length, 7, example.id + ' retains the complete arrow polygon');
    assert.equal(context.strokeStyle, '#173b31', example.id + ' retains the outline');
    assert.equal(context.fillStyle, '#57e0b4', example.id + ' retains the hint color');
    close(context.lineWidth, example.source.width * .027, example.id + ' outline width');
    const pixelSide = Math.round(example.boardPixels * example.ratio);
    close(calls.transform[0], pixelSide / example.boardPixels, example.id + ' pixel ratio');
    const tip = points.find(([, y]) => y === 0);
    assert.ok(tip, example.id + ' paints a tip on the line axis');
    const [sourceX, sourceY] = center(example.source);
    close(calls.origin[0], sourceX - bounds.x, example.id + ' measured source x');
    close(calls.origin[1], sourceY - bounds.y, example.id + ' measured source y');
    return [tip[0] * Math.cos(calls.angle), tip[0] * Math.sin(calls.angle)];
}

const result = [];
for (const example of examples) {
    const [x, y] = center(example.source), [endX, endY] = center(example.destination);
    const shape = hintArrow(endX - x, endY - y, example.source.width);
    for (const [key, value] of Object.entries(example.shape)) close(shape[key], value, example.id + ' shared ' + key);
    const [canvasX, canvasY] = canvasArrow(example);
    close(canvasX, shape.tip * Math.cos(shape.radians), example.id + ' Canvas/native x');
    close(canvasY, shape.tip * Math.sin(shape.radians), example.id + ' Canvas/native y');
    const svg = studyArrow(example.from, example.to, example.blackBelow);
    const transform = /^translate\(([-\d.]+) ([-\d.]+)\) rotate\(([-\d.]+)\)$/.exec(svg.transform);
    const tip = / L ([-\d.e+]+) 0 /.exec(svg.path);
    assert.ok(transform && tip, example.id + ' SVG exposes its actual transform and tip');
    const angle = Number(transform[3]) * Math.PI / 180;
    const actualX = Number(transform[1]) + Number(tip[1]) * Math.cos(angle);
    const actualY = Number(transform[2]) + Number(tip[1]) * Math.sin(angle);
    const column = example.to.charCodeAt(0) - 97, row = 8 - Number(example.to[1]);
    const destinationX = example.blackBelow ? 7.5 - column : column + .5;
    const destinationY = example.blackBelow ? 7.5 - row : row + .5;
    const svgGap = Math.hypot(destinationX - actualX, destinationY - actualY);
    close(svgGap, .20, example.id + ' SVG destination-center gap');
    result.push({ id: example.id, canvasX: number(canvasX), canvasY: number(canvasY), svgGap: number(svgGap) });
}
for (const unit of [0, -1, NaN, Infinity]) assert.throws(() => hintArrow(100, 0, unit), RangeError);
for (const [dx, dy] of [[0, 0], [10, 0], [NaN, 0], [0, Infinity]]) assert.throws(() => hintArrow(dx, dy, 50), RangeError);
console.log(JSON.stringify(result));
