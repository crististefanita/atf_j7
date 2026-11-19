import { chrome, backTargets, reviewBackTargets } from './chrome-contract.mjs';

const labels = {
    en: { HOME: 'Back to main menu', PUZZLES: 'Back to puzzle chapters', OPENINGS: 'Back to openings', SOURCE: 'Back to the previous screen' },
    ro: { HOME: 'Înapoi la meniul principal', PUZZLES: 'Înapoi la capitole', OPENINGS: 'Înapoi la deschideri', SOURCE: 'Înapoi la ecranul anterior' },
};

export function backLabel(page, locale, reviewing = false) {
    const target = (reviewing ? reviewBackTargets : backTargets)[page];
    if (!target) throw new RangeError('Unknown chess screen: ' + page);
    return labels[locale === 'ro' ? 'ro' : 'en'][target];
}

export function matchBackPage(mode, setupVisible, setupBackMode) {
    return mode === 'ai' && setupVisible && setupBackMode && setupBackMode !== 'home' ? 'ANALYSIS' : 'MATCH';
}

export function installChrome(document) {
    const style = document.documentElement?.style;
    if (!style?.setProperty) return;
    for (const [name, value] of Object.entries({
        '--chess-control-size': chrome.target + 'px', '--chess-screen-title': chrome.title / 16 + 'rem',
        '--chess-header-gap': chrome.gap + 'px', '--chess-header-inset': chrome.inset + 'px',
        '--chess-navigation-max': chrome.navigationMax + 'px',
    })) style.setProperty(name, value);
}

export function compactAutomation(browser, input, label, description) {
    input.setAttribute('aria-label', description);
    label.title = description;
    const media = browser.matchMedia?.('(max-width: ' + chrome.compactWidth + 'px)');
    const render = () => { label.textContent = media?.matches ? 'AI' : description; };
    render();
    media?.addEventListener('change', render);
    return () => media?.removeEventListener('change', render);
}
