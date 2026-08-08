# Release signing

`skillsync-release.jks` is the app's one and only release signing key
(created 2026-08-08, valid to 2053, alias `skillsync-release`, PKCS12).

**Every release build must be signed with this exact key.** Android refuses
to install an update over an app already on the device unless the new APK's
signature matches the installed one — mismatch means the user has to
uninstall first, losing their session. As long as this file and
`keystore.properties` (one level up, git-ignored) stay put, `assembleRelease`
signs every future build with the same certificate and installs update over
the previous release with no uninstall step.

Neither this `.jks` file nor `keystore.properties` (which holds the
passwords) is committed to git — both are in `.gitignore`. Back them up
somewhere durable outside the repo. If they are ever lost, a new keystore has
to be generated and every user with the app installed will need to uninstall
the old build once before the new signature can install.

## History

The repo previously had a different `release.jks` committed directly to git
(added in commit `93bde7d`, never actually wired into any signing config —
every release before v1.30.0 was signed with the machine's default debug
key). That file is public in git history and was never used for a real
signed release, so it was retired and replaced with this one rather than
reused, since a keystore that has been pushed to a public remote should be
treated as compromised regardless of whether its password ever leaked.

## Setup on a new machine

Copy `skillsync-release.jks` into this folder and create
`SkillEdge_Android/keystore.properties`:

```
storeFile=keystore/skillsync-release.jks
storePassword=<password>
keyAlias=skillsync-release
keyPassword=<password>
```

(PKCS12 keystores use one password for both the store and the key.)
