# SkillSync Development - Quick Start

**Everything runs through GitHub. No external tools needed.**

## 🚀 First Time Setup

```bash
# Clone the repo (if not already done)
git clone https://github.com/aishsynk/SkillSync.git
cd SkillSync

# Verify Gradle wrapper works
./gradlew --version
# Should show: Gradle 8.1.0
```

## 📱 Development Workflow

### Option A: GitHub-Driven (Recommended)

**Step 1: Make code changes**
```bash
# Edit Kotlin files in: app/src/main/java/com/koenig/skilledge/
# Example: Add new screen, fix bug, improve UI
```

**Step 2: Commit and push to GitHub**
```bash
git add .
git commit -m "Feature: Add new screen / Fix: Handle error case"
git push origin main
```

**Step 3: GitHub Actions builds automatically**
- Go to: https://github.com/aishsynk/SkillSync/actions
- Watch workflow complete (~5-10 minutes)
- Check if build ✅ or ❌

**Step 4: Download and test APK**
- Go to: https://github.com/aishsynk/SkillSync/releases
- Download latest: `SkillSync-v{timestamp}.apk`
- Install: `adb install -r SkillSync-v*.apk`
- Test on device/emulator

### Option B: Local Testing (Optional)

**For quick testing before pushing:**

```bash
# Build debug APK locally
./gradlew assembleDebug

# Install on device/emulator
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Run tests
./gradlew test

# Then commit & push
git add .
git commit -m "Feature: XYZ"
git push origin main
```

## 📂 Where to Edit Code

```
app/src/main/java/com/koenig/skilledge/
├── presentation/          ← UI Screens (edit here)
│   ├── login/
│   ├── dashboard/
│   └── ...
├── viewmodels/            ← ViewModel logic
├── data/                  ← API calls & storage
├── domain/                ← Data models
└── core/                  ← Theme, navigation, DI
```

### Example: Add a new screen

1. **Create UI screen** → `presentation/newscreen/NewScreen.kt`
2. **Create ViewModel** → `viewmodels/NewScreenViewModel.kt`
3. **Add to navigation** → Edit `core/navigation/Navigation.kt`
4. **Commit & push** → GitHub builds APK

## 🔍 Checking Build Status

```
GitHub Actions (Live builds)
↓
https://github.com/aishsynk/SkillSync/actions
↓
Click latest workflow run → see build logs
```

```
GitHub Releases (Download APKs)
↓
https://github.com/aishsynk/SkillSync/releases
↓
Download SkillSync-v{timestamp}.apk
```

## 🐛 Troubleshooting

| Issue | Solution |
|-------|----------|
| "gradlew not found" | Run from repo root: `cd SkillSync` |
| "Cannot find Android SDK" | Set `ANDROID_HOME` environment variable |
| Build fails on GitHub | Check Actions tab logs for error |
| APK won't install | Try: `adb uninstall com.koenig.skilledge` then reinstall |

## 📋 Typical Day

```
9:00 AM - Open code editor, make changes
         git add . && git commit -m "Feature: X" && git push

9:15 AM - GitHub Actions starts building
         Check: https://github.com/aishsynk/SkillSync/actions

9:25 AM - Build complete, download APK
         Go to: https://github.com/aishsynk/SkillSync/releases

9:30 AM - Install and test: adb install -r SkillSync-v*.apk

10:00 AM - Found issue? Make fix, git push again
          GitHub rebuilds automatically

10:30 AM - Test new APK
```

## 🎯 Important: Always Push to GitHub

**Your workflow should be:**

1. ✏️ Edit code locally
2. 🔄 Push to GitHub (`git push origin main`)
3. ⚙️ GitHub Actions builds automatically
4. ⬇️ Download APK from Releases
5. 📱 Install and test
6. 🔁 Repeat

**Do NOT:**
- ❌ Build APK locally and ship it (GitHub builds are authoritative)
- ❌ Forget to push code (it won't be in history)
- ❌ Skip testing the APK before final release

## 📚 Need More Info?

- **Architecture:** Read `ARCHITECTURE.md`
- **Implementation:** Read `IMPLEMENTATION_GUIDE.md`
- **CI/CD Details:** Read `CI-CD_GUIDE.md`
- **Setup Help:** Read `SETUP.md`

## ✨ You're Ready!

```bash
# Next: Make your first change
cd "C:\Users\Aishw\OneDrive - Koenig Solutions Ltd\SkillEdge\skillsync"
# Edit code...
git add .
git commit -m "Your first feature"
git push origin main
# Watch GitHub build it automatically!
```

---

**Questions?** Check the docs or the GitHub repo issues/discussions.

**Happy coding!** 🚀
