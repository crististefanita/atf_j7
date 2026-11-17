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
