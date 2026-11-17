/** A disclosure attached to its toolbar button. Tab can leave the panel. */
export function attachOptionsMenu(trigger, panel, { canOpen = () => true, onToggle = () => {} } = {}) {
    const anchor = trigger.parentElement;
    const isOpen = () => !panel.hidden;

    function close(restoreFocus = false) {
        if (!isOpen()) return;
        panel.hidden = true;
        trigger.setAttribute('aria-expanded', 'false');
        if (restoreFocus) trigger.focus();
        onToggle();
    }

    trigger.addEventListener('click', () => {
        if (isOpen()) {
            close(true);
        } else if (canOpen()) {
            panel.hidden = false;
            trigger.setAttribute('aria-expanded', 'true');
            panel.querySelector('button:not([hidden]):not(:disabled)')?.focus();
            onToggle();
        }
    });
    anchor.addEventListener('keydown', event => {
        if (event.key !== 'Escape' || !isOpen()) return;
        event.preventDefault();
        event.stopPropagation();
        close(true);
    });
    anchor.addEventListener('focusout', event => {
        if (event.relatedTarget && !anchor.contains(event.relatedTarget)) close();
    });
    anchor.ownerDocument.addEventListener('pointerdown', event => {
        if (!anchor.contains(event.target)) close();
    });
    return { isOpen, close };
}
