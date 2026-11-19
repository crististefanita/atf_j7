// Capture before preview chrome hides the source rail or changes its container.
// The shared viewport limit may clamp it after resize; navigation cannot recapture or enlarge it.
export function rememberBoardSize(source, preview) {
    const side = source.getBoundingClientRect().width;
    if (Number.isFinite(side) && side > 0) preview.style.setProperty('--replay-board-side', side + 'px');
}
