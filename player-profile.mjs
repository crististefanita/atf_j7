export const profileKey = 'chesslab.player-profile.v1';

const defaults = Object.freeze({ name: '', language: 'en' });

export function profileName(value) {
    const name = value.trim().normalize('NFC');
    if (name === '') return name;
    if ([...name].length > 10 || !/[\p{L}\p{Nd}]/u.test(name) || /[^\p{L}\p{Nd} '\-]/u.test(name)) return null;
    return name;
}

export function readProfile(getStorage) {
    try {
        const saved = JSON.parse(getStorage().getItem(profileKey));
        const name = typeof saved?.name === 'string' ? profileName(saved.name) : null;
        return { name: name ?? defaults.name, language: saved?.language === 'ro' ? 'ro' : defaults.language };
    } catch {
        return defaults;
    }
}

export function writeProfile(storage, profile) {
    storage.setItem(profileKey, JSON.stringify(profile));
}
