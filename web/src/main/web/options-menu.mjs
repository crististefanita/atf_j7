/** Keeps an options panel beside its trigger inside the visible viewport. */
export function optionsMenuPlacement(anchor, panel, viewport, gap = 4, margin = 8) {
    const left = (viewport.left ?? 0) + margin;
    const top = (viewport.top ?? 0) + margin;
    const right = (viewport.left ?? 0) + viewport.width - margin;
    const bottom = (viewport.top ?? 0) + viewport.height - margin;
    const maxWidth = Math.max(0, right - left);
    const width = Math.min(panel.width, maxWidth);
    const below = Math.max(0, bottom - anchor.bottom - gap);
    const above = Math.max(0, anchor.top - top - gap);
    const before = panel.height > below && above > below;
    const maxHeight = before ? above : below;
    const height = Math.min(panel.height, maxHeight);
    const onRight = (anchor.left + anchor.right) / 2 > (left + right) / 2;
    const aligned = onRight ? anchor.right - width : anchor.left;
    return {
        left: Math.max(left, Math.min(aligned, right - width)),
        top: before ? anchor.top - gap - height : anchor.bottom + gap,
        maxWidth,
        maxHeight,
    };
}

/** Repositions the open panel on viewport changes, independently of its DOM parent. */
export function followOptionsMenu(trigger, panel, dismiss = () => {}) {
    const document = panel.ownerDocument ?? trigger.parentElement.ownerDocument;
    const view = document.defaultView;
    if (!view) return () => {};
    const viewport = view.visualViewport;

    function stop() {
        view.removeEventListener('resize', position);
        view.removeEventListener('scroll', position, true);
        viewport?.removeEventListener('resize', position);
        viewport?.removeEventListener('scroll', position);
    }

    function position(event) {
        if (panel.hidden) {
            stop();
            return;
        }
        if (event && (event.target === panel || panel.contains(event.target))) return;
        const visible = {
            left: viewport?.offsetLeft ?? 0,
            top: viewport?.offsetTop ?? 0,
            width: viewport?.width ?? document.documentElement?.clientWidth ?? view.innerWidth,
            height: viewport?.height ?? document.documentElement?.clientHeight ?? view.innerHeight,
        };
        const anchor = trigger.getBoundingClientRect();
        if (anchor.bottom <= visible.top || anchor.top >= visible.top + visible.height
                || anchor.right <= visible.left || anchor.left >= visible.left + visible.width) {
            stop();
            dismiss();
            return;
        }
        panel.style.position = 'fixed';
        panel.style.inset = 'auto';
        panel.style.maxInlineSize = Math.max(0, visible.width - 16) + 'px';
        const bounds = panel.getBoundingClientRect();
        const border = Math.max(0, bounds.height - panel.clientHeight);
        const natural = { width: bounds.width, height: Math.max(bounds.height, panel.scrollHeight + border) };
        const placement = optionsMenuPlacement(anchor, natural, visible);
        panel.style.left = placement.left + 'px';
        panel.style.top = placement.top + 'px';
        panel.style.maxBlockSize = placement.maxHeight + 'px';
    }

    view.addEventListener('resize', position);
    view.addEventListener('scroll', position, true);
    viewport?.addEventListener('resize', position);
    viewport?.addEventListener('scroll', position);
    position();
    return stop;
}

/** A disclosure attached to its toolbar button. Tab can leave the panel. */
export function attachOptionsMenu(trigger, panel, { canOpen = () => true, onToggle = () => {} } = {}) {
    const anchor = trigger.parentElement;
    const isOpen = () => !panel.hidden;
    let stopFollowing = () => {};

    function close(restoreFocus = false) {
        stopFollowing();
        if (!isOpen()) return;
        panel.hidden = true;
        trigger.setAttribute('aria-expanded', 'false');
        if (restoreFocus) trigger.focus({ preventScroll: true });
        onToggle();
    }

    trigger.addEventListener('click', () => {
        if (isOpen()) {
            close(true);
        } else if (canOpen()) {
            panel.hidden = false;
            trigger.setAttribute('aria-expanded', 'true');
            stopFollowing = followOptionsMenu(trigger, panel, close);
            if (!isOpen()) return;
            panel.querySelector('button:not([hidden]):not(:disabled), input:not(:disabled)')?.focus({ preventScroll: true });
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
