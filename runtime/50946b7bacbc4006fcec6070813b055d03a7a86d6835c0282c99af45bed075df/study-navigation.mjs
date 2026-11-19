export const studyHashes = Object.freeze({ quests: '#puzzles', openings: '#openings', analysis: '#analysis' });
export const routeHashes = Object.freeze({ review: '#review', match: '#game', ai: '#ai', saved: '#saved', finished: '#finished', ...studyHashes });

export function pageBackVisible(mode, { matchRoute, setupVisible = false, hasReview = false } = {}) {
    return ['saved', 'finished', 'archive'].includes(mode) || Boolean(matchRoute && mode === matchRoute)
        || (mode === 'ai' && setupVisible) || (mode === 'review' && !hasReview);
}

export function routeForHash(hash) {
    const mode = Object.keys(routeHashes).find(route => routeHashes[route] === hash);
    if (mode) return { mode };
    if (hash.startsWith('#review/') && hash.length > '#review/'.length) return { mode: 'archive-review', archiveId: hash.slice('#review/'.length) };
    if (hash.startsWith('#archive/') && hash.length > '#archive/'.length) return { mode: 'archive', archiveId: hash.slice('#archive/'.length) };
    return { mode: 'home' };
}

export function writeModeHash(browser, hash, addHistory, resetOrigin = false) {
    if (resetOrigin) browser.scrollTo(0, 0);
    if ((hash ?? '') === browser.location.hash) return;
    const url = hash ?? (browser.location.pathname + browser.location.search);
    const update = addHistory ? 'pushState' : 'replaceState';
    browser.history[update](null, '', url);
}

export function watchRouteHistory(browser, currentHash, navigate) {
    browser.addEventListener('hashchange', () => {
        const hash = browser.location.hash;
        if (hash !== (currentHash() ?? '')) void navigate(hash);
    });
}

export function matchAnalysisNavigation({ browser, currentMatch, openStudy, returnToMatch }) {
    let origin;
    let returning = false;

    function discard() {
        origin = undefined;
        returning = false;
    }

    return {
        open(mode, opener) {
            const match = currentMatch();
            if (origin || !match?.reviewCheckpoint) return false;
            origin = { match, mode, opener, hash: browser.location.hash };
            void openStudy({ checkpoint: match.reviewCheckpoint, ply: match.viewPly, blackBelow: match.blackBelow });
            return true;
        },
        back() {
            if (!origin) return false;
            if (!returning) {
                returning = true;
                browser.history.back();
            }
            return true;
        },
        restore(hash) {
            const previous = origin;
            discard();
            if (!previous || previous.hash !== hash || previous.match !== currentMatch()) return false;
            returnToMatch(previous.mode);
            if (previous.opener?.isConnected) previous.opener.focus({ preventScroll: true });
            return true;
        },
        discard,
    };
}
