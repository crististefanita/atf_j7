import { readProfile, writeProfile } from './player-profile.mjs';

export const homeLabels = {
    en: {
        'home-games-heading': 'Games', 'home-study-heading': 'Study', 'home-profile-heading': 'Profile',
        'open-ai': 'New game vs computer', 'open-saved': 'Saved games', 'open-finished': 'Finished games',
        'home-puzzles': 'Puzzles', 'home-openings': 'Openings', 'home-analysis': 'Analysis board',
        'home-settings': 'Settings', 'profile-name-label': 'Display name', 'profile-language-label': 'Language',
        'save-profile': 'Save', 'profile-reset': 'Reset', 'cancel-profile': 'Cancel', defaultName: 'You',
        name: value => 'Name · ' + value,
        invalid: 'Use 1–10 characters with at least one letter or digit. Spaces, hyphens and apostrophes are allowed.',
        failed: 'Progress could not be saved. Try again.',
    },
    ro: {
        'home-games-heading': 'Partide', 'home-study-heading': 'Studiu', 'home-profile-heading': 'Profil',
        'open-ai': 'Partidă nouă cu calculatorul', 'open-saved': 'Partide salvate', 'open-finished': 'Partide încheiate',
        'home-puzzles': 'Probleme', 'home-openings': 'Deschideri', 'home-analysis': 'Tablă de analiză',
        'home-settings': 'Setări', 'profile-name-label': 'Nume afișat', 'profile-language-label': 'Limbă',
        'save-profile': 'Salvează', 'profile-reset': 'Resetează', 'cancel-profile': 'Anulează', defaultName: 'Tu',
        name: value => 'Nume · ' + value,
        invalid: 'Folosește 1–10 caractere, cu cel puțin o literă sau o cifră. Poți include spații, cratime și apostrofuri.',
        failed: 'Progresul nu a putut fi salvat. Încearcă din nou.',
    },
};

const supported = value => value === 'en' || value === 'ro';

export function homeLocale(search, storage) {
    const requested = new URLSearchParams(search).get('lang');
    return supported(requested) ? requested : readProfile(() => storage).language;
}

export function reloadProfileLanguage(browser, locale, language, fields) {
    if (fields === 'name') return false;
    const url = new URL(browser.location.href);
    if (locale === language && !url.searchParams.has('lang')) return false;
    url.searchParams.delete('lang');
    browser.history.replaceState(browser.history.state, '', url.href);
    browser.location.reload();
    return true;
}

export function hasSavedHomeGames(entries) {
    return entries.some(entry => entry.result === 'IN_PROGRESS');
}

export async function saveHomeProfile(storage, input, fields, reset, normalize) {
    const current = readProfile(() => storage);
    const profile = { ...current };
    if (fields !== 'name') {
        if (!supported(input.language)) throw new TypeError('Unsupported application language');
        profile.language = input.language;
    }
    if (fields !== 'language') {
        profile.name = reset || (fields === 'both' && input.name.trim() === '') ? '' : await normalize(input.name);
    }
    writeProfile(storage, profile);
    return profile;
}

export function createHomeMenu(find, locale, storage, normalize, onSave) {
    const labels = homeLabels[locale];
    for (const [id, value] of Object.entries(labels)) {
        const control = find(id);
        if (control && typeof value === 'string') control.textContent = value;
    }
    const dialog = find('profile-dialog');
    const form = find('profile-form');
    const nameInput = find('profile-name');
    const languageInput = find('profile-language');
    const save = find('save-profile');
    const reset = find('profile-reset');
    const error = find('profile-error');
    let profile = readProfile(() => storage);
    let fields;
    let saving = false;
    const labelName = () => { find('home-name').textContent = labels.name(profile.name || labels.defaultName); };
    labelName();

    function show(selection) {
        fields = selection;
        error.hidden = true;
        find('profile-title').textContent = labels[fields === 'language' ? 'home-settings' : 'home-profile-heading'];
        find('profile-name-field').hidden = fields === 'language';
        find('profile-language-field').hidden = fields === 'name';
        reset.hidden = fields !== 'name';
        nameInput.value = profile.name;
        languageInput.value = locale;
        dialog.showModal();
        if (fields === 'language') languageInput.focus();
        else { nameInput.focus(); nameInput.select(); }
    }

    async function submit(resetName = false) {
        if (saving) return;
        saving = save.disabled = reset.disabled = true;
        error.hidden = true;
        try {
            const input = { name: nameInput.value, language: languageInput.value };
            profile = await saveHomeProfile(storage, input, fields, resetName, async value => {
                try { return await normalize(value); } catch { throw new TypeError(labels.invalid); }
            });
            labelName();
            dialog.close();
            onSave(profile, fields);
        } catch (failure) {
            error.textContent = failure.message === labels.invalid ? labels.invalid : labels.failed;
            error.hidden = false;
        } finally {
            saving = save.disabled = reset.disabled = false;
        }
    }

    find('home-name').addEventListener('click', () => show('name'));
    find('home-settings').addEventListener('click', () => show('language'));
    find('cancel-profile').addEventListener('click', () => { if (!saving) dialog.close(); });
    dialog.addEventListener('cancel', event => { if (saving) event.preventDefault(); });
    reset.addEventListener('click', () => { void submit(true); });
    form.addEventListener('submit', event => { event.preventDefault(); void submit(); });
    return {
        get name() { return profile.name; },
        show(visible, matches) {
            find('home-menu').hidden = !visible;
            find('open-saved').hidden = !hasSavedHomeGames(matches);
        },
    };
}
