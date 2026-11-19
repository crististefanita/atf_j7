# Android signing identity

This project record was explicitly requested for J7. It contains an encrypted
PKCS12 key, its public certificate, a relative-path manifest and a password
protected with Windows DPAPI. No plaintext password belongs in the repository.

- Load with `. ./scripts/Initialize-J7Signing.ps1 -ProjectIdentity` in the
  same Windows user context that protected `password.dpapi`.
- `-StartNewIdentity` is required only when provisioning a missing identity
  after a published certificate. Existing or partial records are never overwritten.
- `identity.json` retains the previous published certificate. This identity
  requires a clean installation; Android cannot update an APK signed by the old key.
- DPAPI protection is tied to this Windows account. A clone on another account
  does not provide a usable password. Preserve the account's recovery material
  separately; do not turn the password into a committed text file.
- Keep build logs, APKs and qualification reports outside this directory.
  [Release protocol](../docs/release/README.md#qualification-and-publication).
