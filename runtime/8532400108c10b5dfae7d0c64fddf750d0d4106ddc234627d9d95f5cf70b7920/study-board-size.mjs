// Capture before preview chrome hides the source rail or changes its container.
// CSS may shrink this ceiling for the available space, but cannot enlarge it.
export function rememberBoardSize(source, preview) {
    const side = source.getBoundingClientRect().width;
    if (Number.isFinite(side) && side > 0) preview.style.setProperty('--replay-board-side', side + 'px');
}
