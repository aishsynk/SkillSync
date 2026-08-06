# SkillEdge Phase 1 Deployment Setup

Complete guide for deploying SkillEdge to production across GitHub, Vercel, and Render.

## 📋 Prerequisites

- GitHub Account: https://github.com/aishsynk/SkillSync
- Vercel Account: https://vercel.com
- Render Account: https://render.com
- Personal Access Token (PAT) for GitHub (for CI/CD)
- Android device or emulator for testing

## 🚀 Deployment Architecture

```
┌─────────────────────────────────────────┐
│         GitHub Repository               │
│    github.com/aishsynk/SkillSync       │
│                                         │
│  • Python Backend (server.py)          │
│  • Frontend (HTML/JS)                  │
│  • Android App (Kotlin/Compose)        │
│  • Tests & CI/CD                       │
└──────────────┬──────────────────────────┘
               │ Push to main
               ├─── GitHub Actions CI/CD
               │    ├─ Run tests
               │    ├─ Build Android APK
               │    ├─ Generate Release
               │    └─ Trigger deployments
               │
               ├─── Vercel (Frontend + Backend)
               │    └─ Python serverless runtime
               │
               └─── Render (Backend Alternative)
                    └─ Container-based Python runtime
```

## 1️⃣ GitHub Setup

Already complete. Repository configured at: **https://github.com/aishsynk/SkillSync**

### Verify Access
```bash
git remote -v
# Should show: origin https://github.com/aishsynk/SkillSync.git
```

### Secrets Configuration

Add these secrets to your GitHub repository settings:

**For Vercel Deployment:**
- `VERCEL_TOKEN` - Get from https://vercel.com/account/tokens
- `VERCEL_ORG_ID` - Found in Vercel project settings
- `VERCEL_PROJECT_ID` - Found in Vercel project settings

**Steps:**
1. Go to https://github.com/aishsynk/SkillSync/settings/secrets/actions
2. Click "New repository secret"
3. Add each secret above

## 2️⃣ Vercel Setup

### Create Vercel Project

1. Go to https://vercel.com/new
2. Select "Import Git Repository"
3. Connect GitHub and select `aishsynk/SkillSync`
4. Choose **Python** as the framework
5. Configure project:
   - **Project Name:** skilledge
   - **Root Directory:** ./ (default)
   - **Build Command:** `pip install -r requirements.txt`
   - **Output Directory:** Leave blank (Python serverless)

### Environment Variables

In Vercel dashboard, set:
- `SKILLEDGE_ENV=production`
- `SKILLEDGE_LOG_LEVEL=INFO`
- `SKILLEDGE_LOG_FORMAT=json`
- `SKILLEDGE_PORT=8080`

### Deploy

1. Click "Deploy"
2. Wait for deployment to complete (~2-3 minutes)
3. Vercel will provide a URL: `https://skilledge.vercel.app`

### Test Vercel Deployment

```bash
curl https://skilledge.vercel.app/healthz
# Should return: {"status": "ok", "version": "1.0.0"}
```

## 3️⃣ Render Setup (Alternative Backend)

### Create Render Service

1. Go to https://dashboard.render.com/new/web
2. Connect GitHub repository
3. Select **Python** as runtime
4. Configure:
   - **Name:** skilledge-backend
   - **Build Command:** `pip install -r requirements.txt`
   - **Start Command:** `gunicorn -w 4 -b 0.0.0.0:8080 server:app`
   - **Environment:** Python 3.11
   - **Plan:** Free tier (adequate for Phase 1 testing)

### Environment Variables

Add to Render:
- `SKILLEDGE_ENV=production`
- `SKILLEDGE_LOG_LEVEL=INFO`
- `SKILLEDGE_LOG_FORMAT=json`

### Deploy

1. Click "Create Web Service"
2. Render will pull from GitHub and deploy automatically
3. Provided URL: `https://skilledge-backend.onrender.com`

### Test Render Deployment

```bash
curl https://skilledge-backend.onrender.com/healthz
# Should return: {"status": "ok", ...}
```

## 4️⃣ Android APK Release

### Automatic (via GitHub Actions)

Every push to `main` branch automatically:
1. Runs backend tests
2. Builds Android APK (debug + release)
3. Creates GitHub Release with versioned APK
4. Naming: `SkillEdge-v{YYYY.MM.DD.HHMM}.apk`

### Manual APK Build

```bash
cd android
./gradlew assembleRelease
# APK location: app/build/outputs/apk/release/app-release.apk
```

### Download APK

1. Go to https://github.com/aishsynk/SkillSync/releases
2. Download latest `SkillEdge-v*.apk`
3. Transfer to device or emulator

### Install APK

**On Android Emulator:**
```bash
adb install -r SkillEdge-v1.0.0.apk
```

**On Physical Device:**
1. Copy APK to phone (via USB or file transfer)
2. Go to Settings → Security → Unknown Sources (enable)
3. Open Files app, tap APK, install

## 5️⃣ Testing Deployment

### Backend API Test

```bash
# Test Vercel
curl https://skilledge.vercel.app/healthz

# Test Render
curl https://skilledge-backend.onrender.com/healthz

# Both should return 200 with status: "ok"
```

### Frontend Test

1. Open browser to Vercel URL
2. Verify dashboard loads
3. Test login with valid email
4. Check KPI cards render correctly
5. Verify responsive design (resize window)

### Android App Test

1. Install APK on device/emulator
2. Launch SkillEdge app
3. Test login with valid email
4. Verify dashboard loads
5. Check API connectivity:
   - KPI cards should show data
   - Action queue should populate
   - Team summary should render

### Connection Verification

**In Android logcat:**
```bash
adb logcat | grep "SkillEdge\|API\|Error"
```

Expected logs:
- `SkillEdge Application initialized`
- `Intelligence loaded: cached, age=X minutes`
- No `API error` or `connection refused` messages

## 6️⃣ Versioning & Releases

### Version Format

`SkillEdge-v{MAJOR}.{MINOR}.{PATCH}.apk`

Examples:
- `SkillEdge-v1.0.0.apk` - Initial Phase 1 release
- `SkillEdge-v1.0.1.apk` - Phase 1 patch
- `SkillEdge-v1.1.0.apk` - Phase 2 release

### Release Process

1. Code is pushed to `main`
2. GitHub Actions automatically:
   - Runs tests
   - Builds APK
   - Creates release with versioned APK
   - Tags commit with version
3. APK available for download at GitHub Releases

### Rolling Back

If a release has issues:
1. Go to GitHub Releases
2. Download previous stable APK
3. Install on device: `adb install -r SkillEdge-v{old}.apk`

## 7️⃣ Monitoring & Logs

### Vercel Logs

```bash
vercel logs --follow --prod
```

### Render Logs

Via Render dashboard:
1. Go to your service
2. Click "Logs" tab
3. View real-time logs

### GitHub Actions

1. Go to https://github.com/aishsynk/SkillSync/actions
2. Select workflow run
3. View step-by-step output

## 8️⃣ Troubleshooting

### APK Installation Fails

```bash
# Clear old installation
adb uninstall com.koenig.skilledge

# Reinstall
adb install SkillEdge-v1.0.0.apk
```

### API Connection Fails

1. Verify backend is running:
   ```bash
   curl https://skilledge.vercel.app/healthz
   ```
2. Check network on device (WiFi/cellular)
3. Verify API URL in `build.gradle.kts`:
   ```kotlin
   buildConfigField("String", "API_BASE_URL", "\"https://skilledge.vercel.app\"")
   ```

### Deployment Pipeline Stalls

1. Check GitHub Actions logs
2. Verify secrets are set:
   - VERCEL_TOKEN
   - VERCEL_ORG_ID
   - VERCEL_PROJECT_ID
3. Restart workflow from Actions tab

### Cold Start Delays

Free tier services have cold starts (5-10s). Expected behavior.
- Vercel: ~2-3s
- Render: ~5-10s

## 9️⃣ Next Steps

After Phase 1 Testing:

### Phase 2 Development
- [ ] Implement Team roster screen
- [ ] Implement Trainer detail screen
- [ ] Implement Action queue management
- [ ] Add charts & visualizations

### Phase 2 Deployment
- [ ] Run integration tests
- [ ] Update APK version to v1.1.0
- [ ] Create GitHub Release
- [ ] Test on multiple devices

### Production Hardening
- [ ] Upgrade to paid Vercel/Render tier
- [ ] Enable SSL certificate pinning
- [ ] Set up monitoring & alerting
- [ ] Configure backup/rollback procedures

---

## 📚 References

- **GitHub:** https://github.com/aishsynk/SkillSync
- **Vercel:** https://vercel.com/docs
- **Render:** https://render.com/docs
- **Android Deployment:** android/README.md

---

**Last Updated:** 2026-08-06  
**Version:** 1.0.0  
**Status:** Phase 1 Deployment Ready
