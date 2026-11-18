/** Boundary within one selected line; branch ownership stays with its adapter. */
export function lineBoundary(ply, total) {
    if (!Number.isInteger(ply) || !Number.isInteger(total) || ply < 0 || total < ply) throw new RangeError('Invalid line cursor');
    return ply === 0 ? 'START' : total === 1 ? 'ONLY' : ply === 1 ? 'FIRST' : ply === total ? 'LAST' : 'MIDDLE';
}

export function renderLinePosition(element, { ply, total, text, description }) {
    element.dataset.boundary = lineBoundary(ply, total);
    element.setAttribute('data-chess-position', '');
    if (element.textContent !== text) element.textContent = text;
    if (description !== undefined) {
        element.setAttribute('aria-label', description);
        element.title = description;
    }
}
