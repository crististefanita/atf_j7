export const studyHashes = Object.freeze({ quests: '#puzzles', openings: '#openings', analysis: '#analysis' });

export function studyRouteForHash(hash) {
    return Object.keys(studyHashes).find(route => studyHashes[route] === hash);
}

export function writeModeHash(browser, hash, addHistory) {
    if ((hash ?? '') === browser.location.hash) return;
    const url = hash ?? (browser.location.pathname + browser.location.search);
    const update = addHistory ? 'pushState' : 'replaceState';
    browser.history[update](null, '', url);
}

export function watchStudyHistory(browser, currentStudy, navigate) {
    browser.addEventListener('hashchange', () => {
        const hash = browser.location.hash;
        const destination = studyRouteForHash(hash);
        const current = currentStudy();
        if (destination !== current && (destination || current)) void navigate(hash);
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
