export function reviewPositionText(locale, moveLabel, ply, total) {
    const romanian = locale === 'ro';
    const move = moveLabel.replaceAll(' → ', romanian ? ' la ' : ' to ').replaceAll(' · ', '. ');
    const position = romanian ? `Poziția ${ply} din ${total}.` : `Position ${ply} of ${total}.`;
    return `${move}. ${position}`;
}

export function reviewGraphText(locale, positionText, formattedWhiteScore) {
    if (formattedWhiteScore === null) return positionText;
    const evaluation = locale === 'ro' ? `Scorul albului ${formattedWhiteScore} la sută.`
        : `White score ${formattedWhiteScore} percent.`;
    return `${positionText} ${evaluation}`;
}

export function updateReviewRange(input, ply, total, text) {
    const limit = String(total);
    const value = String(ply);
    if (input.getAttribute('aria-valuetext') !== text) input.setAttribute('aria-valuetext', text);
    if (input.max !== limit) input.max = limit;
    if (input.value !== value) input.value = value;
    if (input.disabled !== (total === 0)) input.disabled = total === 0;
}

export function reviewWhyText(locale, positionText, explanation) {
    const heading = locale === 'ro' ? 'De ce?' : 'Why?';
    return explanation ? `${heading} ${positionText} ${explanation}` : `${heading} ${positionText}`;
}

export function reviewAnnouncementChannel(intent, focusId) {
    switch (intent) {
        case 'graph': return focusId === 'seek' ? 'value' : 'status';
        case 'history': return focusId === 'selected-history' ? 'focus' : 'status';
        case 'open-why': return focusId === 'close-why' ? 'focus' : 'status';
        case 'close-why': return focusId === 'why' ? 'focus' : 'status';
        case 'toolbar': return 'status';
        default: return null;
    }
}

export function sliderPlyForKey(key, ply, total) {
    const page = Math.max(1, Math.round(total / 10));
    switch (key) {
        case 'ArrowLeft':
        case 'ArrowDown': return Math.max(0, ply - 1);
        case 'ArrowRight':
        case 'ArrowUp': return Math.min(total, ply + 1);
        case 'PageDown': return Math.max(0, ply - page);
        case 'PageUp': return Math.min(total, ply + page);
        case 'Home': return 0;
        case 'End': return total;
        default: return null;
    }
}
