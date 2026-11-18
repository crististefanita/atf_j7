import { lineBoundary, renderLinePosition } from './line-navigation.mjs';

const labels = {
    en: { START: 'Start', FIRST: 'First move', LAST: 'Latest move', ONLY: 'First and latest move', MIDDLE: 'Move',
        WHITE: 'White', BLACK: 'Black', of: 'of' },
    ro: { START: 'Start', FIRST: 'Prima mutare', LAST: 'Ultima mutare', ONLY: 'Prima și ultima mutare', MIDDLE: 'Mutarea',
        WHITE: 'Alb', BLACK: 'Negru', of: 'din' },
};

// Existing workers already supply the full history and cursor. New workers also
// provide the shared JVM projection; both wire versions obey the same fixtures.
export function selectedMatchMove(match) {
    const ply = match.viewPly;
    const total = match.ply;
    if (!Number.isInteger(ply) || ply < 0 || !Number.isInteger(total) || ply > total
            || !Array.isArray(match.history) || match.history.length !== total
            || !['WHITE', 'BLACK'].includes(match.sideToMove)) throw new RangeError('Invalid match history cursor');
    const boundary = lineBoundary(ply, total);
    const move = ply === 0 ? null : match.history[ply - 1];
    if (ply > 0 && (typeof move !== 'string' || !/^[a-h][1-8][a-h][1-8][qrbn]?$/.test(move))) {
        throw new RangeError('Invalid selected move');
    }
    const other = side => side === 'WHITE' ? 'BLACK' : 'WHITE';
    const initialSide = ply % 2 ? other(match.sideToMove) : match.sideToMove;
    const number = ply === 0 ? 0 : Math.floor((ply + (initialSide === 'WHITE' ? 1 : 2)) / 2);
    const side = ply === 0 ? null : other(match.sideToMove);
    const compact = move ? number + (side === 'WHITE' ? '. ' : '… ') + move.slice(0, 2) + '–' + move.slice(2, 4)
        + (move.length === 5 ? '=' + move[4].toUpperCase() : '') : '';
    const projected = { ply, total, number, side, move, boundary, compact };
    if (match.navigation) {
        if (Object.entries(projected).some(([key, value]) => match.navigation[key] !== value)) {
            throw new RangeError('Navigation belongs to another history position');
        }
        return match.navigation;
    }
    return projected;
}

export function renderMatchHistory(output, match, locale) {
    const frame = selectedMatchMove(match);
    const words = labels[locale] ?? labels.en;
    const visible = frame.move ? frame.compact : words.START;
    const description = frame.move ? `${words[frame.boundary]} · ${visible} · ${words[frame.side]} · ${frame.ply} ${words.of} ${frame.total}` : words.START;
    renderLinePosition(output, { ...frame, text: visible, description });
}
