const toolbar = ['previous', 'best-line', 'next', 'next-key', 'why', 'review-actions'];
const explanationControls = ['played', 'recommended', 'previous', 'next'];

export function historyPlyForKey(key, ply, totalPlies) {
    switch (key) {
        case 'ArrowUp': return Math.max(0, ply - 1);
        case 'ArrowDown': return Math.min(totalPlies, ply + 1);
        case 'Home': return 0;
        case 'End': return totalPlies;
        default: return null;
    }
}

export function reviewFocusCandidates(intent, initiator = '', explaining = false) {
    switch (intent) {
        case 'open-why': return ['close-why', 'played', 'recommended', 'board'];
        case 'close-why': return ['why', 'best-line', 'next', 'previous', 'next-key', 'board'];
        case 'history': return ['selected-history', 'seek', 'board'];
        case 'graph': return ['seek', 'selected-history', 'board'];
        case 'toolbar': {
            const controls = explaining ? explanationControls : toolbar;
            const index = controls.indexOf(initiator);
            return index < 0 ? [] : [...controls.slice(index), ...controls.slice(0, index).reverse(), 'board'];
        }
        default: return [];
    }
}

export function revealDistance(viewStart, viewEnd, itemStart, itemEnd) {
    if (itemStart < viewStart || itemEnd - itemStart > viewEnd - viewStart) return itemStart - viewStart;
    return Math.max(0, itemEnd - viewEnd);
}
