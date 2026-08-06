# SkillSync CI/CD & Development Guide

Complete guide for developing, building, and deploying SkillSync Android app entirely through GitHub.

## 🏗️ Project Architecture

```
GitHub Repository: https://github.com/aishsynk/SkillSync
│
├── Source Code (app/)
│   └── Kotlin + Jetpack Compose
│
├── GitHub Actions CI/CD (.github/workflows/)
│   ├── build-and-release.yml    (Primary: Auto-build & release)
│   └── build-and-deploy.yml     (Alternative)
│
├── Gradle Wrapper (gradle/)
│   └── Enables builds without Gradle installed
│
└── Documentation
    ├── README.md
    ├── SETUP.md
    ├── ARCHITECTURE.md
    └── IMPLEMENTATION_GUIDE.md
```

## 🔄 Workflow: From Code to APK

### Step 1: Push Code to GitHub

```bash
cd skillsync
git add .
git commit -m "Feature: Add new screen"
git push origin main
```

**Result:** GitHub Actions automatically triggered

### Step 2: GitHub Actions Builds

GitHub Actions workflow runs automatically:

1. **Setup Environment**
   - Checkout code
   - Set up Java 11
   - Cache Gradle dependencies

2. **Build APK**
   - `./gradlew assembleDebug` → Debug APK
   - `./gradlew assembleRelease` → Release APK

3. **Upload Artifacts**
   - APKs stored for 30 days
   - Available at Actions → Artifacts

4. **Create Release** (main branch only)
   - Auto-version: `SkillSync-v{YYYY.MM.DD.HHMM}.apk`
   - Published to GitHub Releases
   - Downloadable for testing

### Step 3: Test & Deploy

- **Download APK** from: https://github.com/aishsynk/SkillSync/releases
- **Install on device:** `adb install -r SkillSync-v*.apk`
- **Test thoroughly**
- **Push fixes to GitHub** → Automatic rebuild

---

## 📦 Building Locally

For local development testing:

### Debug Build (Fast)

```bash
cd skillsync
./gradlew assembleDebug

# Install on connected device/emulator
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Or launch directly in Android Studio
./gradlew installDebug
```

**Output:** `app/build/outputs/apk/debug/app-debug.apk`

### Release Build (Optimized)

```bash
./gradlew assembleRelease

# APK with ProGuard optimizations
# Output: app/build/outputs/apk/release/app-release.apk
```

### Run Tests

```bash
# Unit tests
./gradlew test

# Instrumented tests (on device/emulator)
./gradlew connectedAndroidTest

# Full test suite
./gradlew testRelease
```

---

## 🎯 GitHub Actions Workflow Details

### Triggers

- **Every push to:** `main`, `develop`
- **Pull requests:** Also builds (doesn't release)

### Jobs

#### 1. `build` (Always runs)
- Builds debug APK
- Builds release APK
- Uploads artifacts (30-day retention)

#### 2. `release` (Main branch only)
- Runs after `build` succeeds
- Creates GitHub Release
- Versions APK automatically
- Publishes to Releases page

### Files

- **Workflow config:** `.github/workflows/build-and-release.yml`
- **Gradle wrapper:** `gradle/wrapper/gradle-wrapper.properties`
- **Wrapper scripts:** `gradlew`, `gradlew.bat`

---

## 📋 Development Workflow

### 1. Create Feature Branch

```bash
git checkout -b feature/new-screen
# OR work directly on main for quick changes
```

### 2. Make Changes

Edit Kotlin code in `app/src/main/java/`

### 3. Test Locally

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
# Test on device/emulator
```

### 4. Commit & Push

```bash
git add .
git commit -m "Add new feature: XYZ"
git push origin feature/new-screen
# OR: git push origin main
```

### 5. GitHub Actions Builds

- Automatic build triggered
- Check status: https://github.com/aishsynk/SkillSync/actions
- Download APK from artifact or release

### 6. Verify on Device

- Download from Releases
- Install: `adb install -r SkillSync-v*.apk`
- Test thoroughly

### 7. Iterate or Merge

- Fix issues → Commit & push → Auto-rebuild
- OR merge PR → Automatic release build

---

## 🚀 Deployment Pipeline

```
Developer pushes code
        ↓
GitHub Actions triggered
        ↓
├─ Build debug APK
├─ Build release APK
└─ Upload artifacts
        ↓
(Main branch only)
        ↓
├─ Create GitHub Release
├─ Auto-version APK
└─ Publish to Releases page
        ↓
Download & test
        ↓
Install on device: adb install SkillSync-v*.apk
```

---

## 📥 Download APKs

### From GitHub Releases

**URL:** https://github.com/aishsynk/SkillSync/releases

**Available:**
- Production: `SkillSync-v{timestamp}.apk`
- All previous versions (for rollback)
- Release notes with commit info

### From GitHub Actions (Build Artifacts)

**URL:** https://github.com/aishsynk/SkillSync/actions

1. Select latest workflow run
2. Scroll down → "Artifacts"
3. Download `apk-build-{number}`

**Contains:** Both debug & release APKs

---

## 🔧 Configuration

### Gradle Wrapper Version

To update Gradle version, edit `gradle/wrapper/gradle-wrapper.properties`:

```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-8.2.0-bin.zip
```

### Java Version

Specified in `.github/workflows/build-and-release.yml`:

```yaml
java-version: '11'
```

### Gradle Settings

Root: `settings.gradle.kts`
App: `app/build.gradle.kts`

---

## 📊 Monitoring Builds

### Check Workflow Status

1. Go to: https://github.com/aishsynk/SkillSync/actions
2. View workflow runs (latest first)
3. Click run to see details
4. View logs for each job

### Common Issues

| Issue | Solution |
|-------|----------|
| Build fails | Check logs at Actions tab |
| APK not found | Release APKs only created on main branch |
| Old dependencies | Delete `gradle/` build cache locally |
| Gradle hangs | Increase timeout in workflow YAML |

---

## 📝 Best Practices

1. **Always test locally** before pushing
   ```bash
   ./gradlew assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

2. **Use meaningful commit messages**
   ```
   git commit -m "Feature: Add login screen"
   git commit -m "Fix: Handle network timeout"
   git commit -m "Refactor: Extract theme logic"
   ```

3. **Push to main only when ready**
   - Automatic release happens on main branch push
   - Use feature branches for experimentation

4. **Download & test APK before release**
   - Don't assume GitHub Actions build is sufficient
   - Manual testing catches UI/UX issues

5. **Keep APK versions in history**
   - GitHub Releases keeps all previous versions
   - Easy rollback if new version has issues

---

## 🔐 Security

- **No secrets in code** → Use environment variables
- **GitHub Secrets available** for API keys, signing keys
- **APKs signed** with release keystore (when configured)
- **All builds tracked** via GitHub commit history

---

## 📞 Support

Refer to:
- `README.md` - Project overview
- `SETUP.md` - Quick start
- `ARCHITECTURE.md` - App architecture
- `IMPLEMENTATION_GUIDE.md` - Development details
- `.github/workflows/` - Workflow configuration

---

**Everything is GitHub-based. No manual builds needed!** 🎉

**Next:** Push code → GitHub builds → Test → Iterate

