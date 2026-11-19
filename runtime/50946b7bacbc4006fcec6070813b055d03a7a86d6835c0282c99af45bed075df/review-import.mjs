/** Keeps standalone archive tools outside the native home and game screens. */
export function createReviewImport(find, resume, load) {
    const tools = find('review-tools');
    const reopen = find('reopen-review');
    reopen.addEventListener('click', () => { if (!reopen.hidden) void resume(); });
    find('archive').addEventListener('change', event => {
        const file = event.target.files[0];
        event.target.value = '';
        if (file && !tools.hidden) void load(file);
    });
    return {
        show(mode, hasArchive, fromMatch) {
            const visible = mode === 'review' && !fromMatch;
            tools.hidden = !visible;
            reopen.hidden = !visible || !hasArchive;
        },
    };
}
