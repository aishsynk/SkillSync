# Vercel Setup - Tokens & Configuration

**Your complete guide to deploy backend on Vercel with NO tokens needed for Phase 1.**

---

## 🎯 The Short Answer

**For Phase 1:** You don't need any special tokens! Just:
1. GitHub account (you have it ✅)
2. Vercel account (free, takes 2 min)
3. Click deploy

**That's it.** Vercel is connected to your GitHub repo and auto-deploys.

---

## 📋 Do You Need Tokens?

| What | Needed Now? | When? |
|-----|------------|-------|
| **Vercel API Token** | ❌ No | Later (GitHub Actions integration) |
| **GitHub Token** | ✅ Yes | Already in GitHub (auto) |
| **Database credentials** | ❌ No | Phase 2 |
| **API keys** | ❌ No | Phase 2 |
| **Signing certificates** | ❌ No | Google Play release |

---

## 🚀 EXACT STEPS TO DEPLOY ON VERCEL

### Step 1️⃣: Create Vercel Account (2 minutes)

**URL:** https://vercel.com/signup

1. Click **"Continue with GitHub"**
2. Click **"Authorize vercel"**
3. Authorize Vercel to access your repos
4. Verify email (check inbox)

✅ **Vercel account created**

---

### Step 2️⃣: Import Your Repository (1 minute)

**URL:** https://vercel.com/new

1. You'll see "Import Git Repository"
2. In the search box, type: `SkillSync`
3. Click on `aishsynk/SkillSync` when it appears
4. Click **"Import"**

✅ **Repository imported**

---

### Step 3️⃣: Configure Project (2 minutes)

After importing, you'll see this screen. **Fill in exactly:**

```
Project Name:         skillsync-api
Framework Preset:     Other (Python)  ← IMPORTANT
Root Directory:       ./
Build Command:        pip install -r requirements.txt
Output Directory:     (leave blank)
Install Command:      (leave blank)
Environment Variables: (skip, not needed for Phase 1)
```

**DO NOT change these settings.**

Then click **"Deploy"** button.

✅ **Deployment started (takes 2-3 minutes)**

---

### Step 4️⃣: Wait for Green Checkmark ✅ (3 minutes)

You'll see:
- Building... 
- Installing dependencies...
- Deploying...
- ✅ Deployment Complete!

Once you see the checkmark and a URL like:
```
https://skillsync-api.vercel.app
```

**Your backend is LIVE!** 🎉

---

## ✅ VERIFY IT WORKS

### Test in Browser

Open this URL in your browser:
```
https://skillsync-api.vercel.app/healthz
```

**You should see:**
```json
{
  "status": "ok",
  "service": "SkillSync Backend",
  "version": "1.0.0",
  "environment": "production",
  "timestamp": "2026-08-06T12:34:56.789123"
}
```

If you see this, **your backend is working!** ✅

---

### Test API Endpoints

Open these in your browser to test:

**1. Dashboard KPIs:**
```
https://skillsync-api.vercel.app/api/dashboard/kpis
```
Returns KPI cards (trainers, utilization, actions, etc.)

**2. All Trainers:**
```
https://skillsync-api.vercel.app/api/trainers
```
Returns list of 3 mock trainers

**3. All Actions:**
```
https://skillsync-api.vercel.app/api/actions
```
Returns list of pending actions

**4. Full Dashboard Data:**
```
https://skillsync-api.vercel.app/api/data/unified-manager-intelligence
```
Returns everything the Android app needs

---

## 🔄 From Now On - It's Automatic!

**Every time you push code to GitHub:**

```
Your machine:
  git push origin main
       ↓
GitHub receives code
       ↓
Vercel sees the update
       ↓
Vercel auto-deploys
  (2-3 minutes)
       ↓
Your backend is updated
  NO MANUAL WORK! 🎉
```

---

## 📊 Monitor Your Backend on Vercel

### View Live Backend

**Go to:** https://vercel.com/dashboard

You'll see your projects. Click **"skillsync-api"**

You can now:
- ✅ See deployment status
- ✅ View logs
- ✅ Check performance
- ✅ See all deployments

### View Request Logs

1. Click **"skillsync-api"** project
2. Click **"Deployments"** tab
3. Click latest deployment (should be green ✅)
4. Click **"Runtime Logs"** or **"Logs"**
5. See all API requests in real-time

### View Errors (if any)

Same place. Logs show:
- ✅ Successful requests
- ⚠️ Warnings
- ❌ Errors with stack traces

---

## 🔑 TOKENS EXPLAINED

### Do You Need Them Now?

**No!** For Phase 1, you don't need tokens because:
- ✅ Vercel auto-connects to GitHub (already authorized)
- ✅ No database (using mock data)
- ✅ No external APIs (no keys needed)
- ✅ No secrets (no environment variables)

### When Do You Need Tokens? (Phase 2+)

**API Keys for external services:**
- Google Cloud credentials
- Database password
- Payment API keys
- Email service keys
- Third-party integrations

**Then you'll add them as Environment Variables:**
1. Vercel Dashboard → skillsync-api → Settings → Environment Variables
2. Add: `KEY_NAME = value`
3. Save
4. Vercel re-deploys automatically

### Vercel API Token (Advanced, not needed now)

Only needed if you want to deploy via GitHub Actions or CLI.

**To get it later (when needed):**
1. Vercel Dashboard → Settings → Tokens
2. Create token
3. Copy it
4. Add to GitHub secrets

**But you don't need this for now.**

---

## 📱 Connect Android App to Backend

Now that your backend is live at:
```
https://skillsync-api.vercel.app
```

Update your Android app to use it.

### Option A: Update in Android Code

**File:** `app/build.gradle.kts`

Add this line:
```kotlin
buildConfigField("String", "API_BASE_URL", "\"https://skillsync-api.vercel.app\"")
```

Then in your Kotlin code:
```kotlin
val apiService = retrofitClient
    .baseUrl(BuildConfig.API_BASE_URL)
    .build()
    .create(ApiService::class.java)
```

### Option B: In API Service File

**File:** `app/src/main/java/com/koenig/skilledge/data/api/SkillEdgeApiService.kt`

```kotlin
interface SkillEdgeApiService {
    @GET("/api/data/unified-manager-intelligence")
    suspend fun getUnifiedData(): Response<UnifiedData>
    
    @POST("/api/auth/login")
    suspend fun login(@Body loginRequest: LoginRequest): Response<LoginResponse>
}

// Base URL
const val BASE_URL = "https://skillsync-api.vercel.app/"
```

### Option C: At Runtime

```kotlin
// In your App initialization
val backendUrl = "https://skillsync-api.vercel.app"
val retrofit = Retrofit.Builder()
    .baseUrl(backendUrl)
    .build()
```

---

## 🔗 Your Backend URLs (Save These)

### Production Backend
```
https://skillsync-api.vercel.app
```

### All Endpoints

| Endpoint | Full URL |
|----------|----------|
| Health | `https://skillsync-api.vercel.app/healthz` |
| Login | `https://skillsync-api.vercel.app/api/auth/login` |
| Dashboard | `https://skillsync-api.vercel.app/api/dashboard/summary` |
| KPIs | `https://skillsync-api.vercel.app/api/dashboard/kpis` |
| Trainers | `https://skillsync-api.vercel.app/api/trainers` |
| Trainer Detail | `https://skillsync-api.vercel.app/api/trainers/{id}` |
| Actions | `https://skillsync-api.vercel.app/api/actions` |
| All Data | `https://skillsync-api.vercel.app/api/data/unified-manager-intelligence` |

---

## ✨ Complete Workflow

### Day 1: Setup (Done ✅)
- ✅ Backend code on GitHub
- ✅ Vercel account created
- ✅ Backend deployed
- ✅ APIs working

### Day 2: Connect Android
- Edit Android code
- Add backend URL
- Build APK
- Test login
- Test dashboard loading

### Day 3+: Iterate
```
Edit code → git push → Vercel deploys (2-3 min) → Test → Repeat
```

No tokens needed. No manual server work. Just code and push!

---

## 🎊 You're All Set!

### What You Have Now

| Component | Status | URL |
|-----------|--------|-----|
| Android App | ✅ Built via GitHub Actions | https://github.com/aishsynk/SkillSync/releases |
| Backend API | ✅ Running 24/7 on Vercel | https://skillsync-api.vercel.app |
| CI/CD | ✅ Automated | GitHub Actions + Vercel |
| Tokens Needed | ❌ None for Phase 1 | Add in Phase 2 if needed |

### Next Steps

1. ✅ **Check backend is live:**
   ```
   https://skillsync-api.vercel.app/healthz
   ```

2. ⏭️ **Update Android code with backend URL:**
   ```kotlin
   val BASE_URL = "https://skillsync-api.vercel.app/"
   ```

3. ⏭️ **Push Android changes to GitHub:**
   ```bash
   git add .
   git commit -m "Connect Android app to Vercel backend"
   git push origin main
   ```

4. ⏭️ **GitHub builds new APK (5-10 min)**

5. ⏭️ **Download and test:**
   - Go to: https://github.com/aishsynk/SkillSync/releases
   - Download latest APK
   - `adb install -r SkillSync-v*.apk`
   - Test login & dashboard

---

## 🆘 Troubleshooting

### Backend Returns 500 Error
1. Go to Vercel Dashboard
2. Click "skillsync-api"
3. Click latest deployment
4. Check "Logs" for error
5. Fix error in `backend.py`
6. `git push origin main` (Vercel auto-deploys)

### Vercel Deployment Fails
1. Check Vercel "Build Logs"
2. Look for Python errors
3. Verify `requirements.txt` is correct
4. Verify `backend.py` has no syntax errors
5. Fix and push again

### Android Can't Connect to Backend
1. Verify URL is correct: `https://skillsync-api.vercel.app`
2. Test in browser: `https://skillsync-api.vercel.app/healthz`
3. Check network on device (WiFi/cellular)
4. Verify API base URL in Android code
5. Check network errors in logcat

---

## 📚 Full Documentation

For more details, read:
- `VERCEL_SETUP.md` - Step-by-step deployment
- `backend.py` - API implementation
- `DEVELOPMENT_START.md` - Daily workflow

---

**Status:** ✅ Backend live and running 24/7  
**URL:** https://skillsync-api.vercel.app  
**Tokens needed:** 0 (for Phase 1)  
**Cost:** Free tier (no payment card needed)  
**Management:** 100% automatic (no server work!)

