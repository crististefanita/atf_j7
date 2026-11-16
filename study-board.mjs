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
    return new Map(marks.map(marker => typeof marker === 'string' ? [marker, 'hint'] : [marker.square, marker.kind]));
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
