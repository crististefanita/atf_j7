import { hintArrow } from './hint-arrow.mjs';

export function boardPlacement(fen) {
    if (/^[prnbqkPRNBQK ]{64}$/.test(fen)) return fen;
    const ranks = fen.split(' ', 1)[0].split('/').map(rank => rank.replace(/[1-8]/g, count => ' '.repeat(Number(count))));
    if (ranks.length !== 8 || ranks.some(rank => !/^[prnbqkPRNBQK ]{8}$/.test(rank))) throw new Error('Invalid board placement');
    return ranks.join('');
}

export function boardKeyIndex(key, index) {
    const delta = { ArrowLeft: -1, ArrowRight: 1, ArrowUp: -8, ArrowDown: 8 }[key];
    if (!delta || index < 0) return -1;
    const next = index + delta;
    if (next < 0 || next >= 64 || (Math.abs(delta) === 1 && Math.floor(index / 8) !== Math.floor(next / 8))) return -1;
    return next;
}

export function studyMarkers(marks = []) {
    const [origin, destination] = marks.filter(marker => typeof marker === 'string');
    const danger = marks.find(marker => marker.kind === 'danger')?.square;
    return { origin, destination, danger };
}

export function studyArrow(origin, destination, blackBelow) {
    const square = /^[a-h][1-8]$/;
    if (!square.test(origin) || !square.test(destination) || origin === destination) return null;
    const center = name => {
        const column = 'abcdefgh'.indexOf(name[0]);
        const row = 8 - Number(name[1]);
        return blackBelow ? [7.5 - column, 7.5 - row] : [column + .5, row + .5];
    };
    const [x, y] = center(origin);
    const [endX, endY] = center(destination);
    const dx = endX - x;
    const dy = endY - y;
    // Each square is one SVG unit; both Web renderers use the same geometry.
    const shape = hintArrow(dx, dy);
    const angle = shape.radians * 180 / Math.PI;
    return {
        transform: `translate(${x} ${y}) rotate(${angle})`,
        path: `M ${shape.start} ${-shape.halfShaft} H ${shape.head} V ${-shape.halfHead} L ${shape.tip} 0 L ${shape.head} ${shape.halfHead} V ${shape.halfShaft} H ${shape.start} Z`,
    };
}

export function mountStudyArrow(host) {
    const namespace = 'http://www.w3.org/2000/svg';
    const overlay = host.ownerDocument.createElementNS(namespace, 'svg');
    overlay.setAttribute('class', 'study-arrow');
    overlay.setAttribute('viewBox', '0 0 8 8');
    overlay.setAttribute('aria-hidden', 'true');
    const path = host.ownerDocument.createElementNS(namespace, 'path');
    overlay.append(path);
    return (origin, destination, blackBelow) => {
        const arrow = studyArrow(origin, destination, blackBelow);
        if (!arrow) {
            overlay.remove();
            return;
        }
        path.setAttribute('d', arrow.path);
        path.setAttribute('transform', arrow.transform);
        if (overlay.parentNode !== host) host.append(overlay);
    };
}

const markerLabels = {
    en: ['legal destination', 'hinted piece', 'solution destination', 'danger'],
    ro: ['destinație legală', 'piesa indicată', 'destinația soluției', 'pericol'],
};

export function studySquareDescription(description, locale, { target, origin, destination, danger }) {
    const labels = markerLabels[locale] ?? markerLabels.en;
    const flags = [target, origin, destination, danger];
    const details = labels.filter((_, index) => flags[index]);
    return [description, ...details].join(', ');
}

export function bindBoardSelection(host, select) {
    host.addEventListener('click', event => {
        const cell = event.target.closest('button[data-square]');
        if (!cell || !host.contains(cell)) return;
        if (cell.tabIndex === -1) {
            const previous = host.querySelector('[tabindex="0"]');
            if (previous) previous.tabIndex = -1;
            cell.tabIndex = 0;
        }
        if (cell.getAttribute('aria-disabled') !== 'true') select(cell.dataset.square);
    });
    host.addEventListener('keydown', event => {
        const cell = event.target.closest('button[data-square]');
        if (!cell || !host.contains(cell)) return;
        const next = boardKeyIndex(event.key, Array.from(host.children).indexOf(cell));
        if (next === -1) return;
        event.preventDefault();
        cell.tabIndex = -1;
        const destination = host.children[next];
        destination.tabIndex = 0;
        destination.focus();
    });
}
