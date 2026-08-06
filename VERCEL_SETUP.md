# Vercel Deployment Guide

Complete setup to deploy Python backend on Vercel (24/7 running server).

---

## 🎯 What You're Getting

**Backend deployed on Vercel:**
- ✅ Runs 24/7 automatically
- ✅ No manual server management
- ✅ Auto-scales on demand
- ✅ Live monitoring & logs
- ✅ Free tier available
- ✅ Android app connects via API

**Architecture:**
```
GitHub (Push code)
    ↓
GitHub Actions (builds APK)
    ↓
Vercel (deploys backend)
    ↓
Android App (connects to backend)
```

---

## 📋 What's Included

| File | Purpose |
|------|---------|
| `backend.py` | Python Flask API server |
| `requirements.txt` | Python dependencies |
| `vercel.json` | Vercel deployment config |

### Backend Endpoints

| Endpoint | Purpose | Method |
|----------|---------|--------|
| `/healthz` | Health check | GET |
| `/api/auth/login` | User login | POST |
| `/api/auth/logout` | User logout | POST |
| `/api/dashboard/kpis` | KPI cards | GET |
| `/api/dashboard/summary` | Dashboard data | GET |
| `/api/trainers` | All trainers | GET |
| `/api/trainers/{id}` | Trainer details | GET |
| `/api/actions` | Pending actions | GET |
| `/api/actions/{id}` | Action details | GET |
| `/api/actions/{id}/update` | Update action | POST |
| `/api/data/unified-manager-intelligence` | All dashboard data | GET |

---

## 🚀 Step-by-Step Setup

### Step 1: Commit Code to GitHub

```bash
cd "C:\Users\Aishw\OneDrive - Koenig Solutions Ltd\SkillEdge\skillsync"

# Verify files exist
ls -lh backend.py requirements.txt vercel.json

# Stage files
git add backend.py requirements.txt vercel.json

# Commit
git commit -m "Add Python Flask backend for Vercel deployment

- Flask API with all endpoints
- Requirements.txt with dependencies
- Vercel config for 24/7 hosting

Endpoints:
- /healthz (health check)
- /api/auth/login (authentication)
- /api/dashboard/kpis (KPI cards)
- /api/trainers (trainer roster)
- /api/actions (action queue)
- /api/data/unified-manager-intelligence (all data)

Backend will run 24/7 on Vercel with no manual management.

Co-Authored-By: Claude Haiku 4.5 <noreply@anthropic.com>"

# Push to GitHub
git push origin main
```

✅ **GitHub now has your backend code**

---

### Step 2: Create Vercel Account

1. Go to: **https://vercel.com/signup**
2. Click **"Continue with GitHub"**
3. Authorize Vercel to access your GitHub repos
4. Verify email

✅ **Vercel account created and linked to GitHub**

---

### Step 3: Create New Vercel Project

1. Go to: **https://vercel.com/new**
2. Click **"Import Git Repository"**
3. Search for: **`SkillSync`**
4. Click **"Import"**

✅ **Repository imported**

---

### Step 4: Configure Project

After import, you'll see configuration screen:

**Fill in:**
```
Project Name:         skillsync-api
Framework Preset:     Other (Python)
Root Directory:       ./
Build Command:        pip install -r requirements.txt
Output Directory:     (leave blank)
Install Command:      (leave blank)
```

**Environment Variables:** (None needed for Phase 1)

Click **"Deploy"**

✅ **Deployment started**

---

### Step 5: Wait for Deployment

**Vercel will:**
1. Clone your repo
2. Install Python 3.11
3. Install dependencies (pip install -r requirements.txt)
4. Deploy Flask app
5. Assign domain name

**This takes 2-3 minutes.**

You'll see:
- ✅ Deployment complete
- 🔗 Live URL: `https://skillsync-api.vercel.app`

✅ **Backend is now live!**

---

## ✅ Verify It Works

### Test the Backend

**Open in browser or use curl:**

```bash
# Test health endpoint
curl https://skillsync-api.vercel.app/healthz

# Should return:
# {
#   "status": "ok",
#   "service": "SkillSync Backend",
#   "version": "1.0.0",
#   "environment": "production"
# }
```

### Test Dashboard API

```bash
curl https://skillsync-api.vercel.app/api/dashboard/kpis

# Should return KPI data with trainers, utilization, actions, etc.
```

### Test Login

```bash
curl -X POST https://skillsync-api.vercel.app/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "user@company.com"}'

# Should return session info
```

✅ **Backend is working!**

---

## 📱 Connect Android App to Backend

### Update API Base URL

In your Android code, update the API endpoint:

**File:** `app/build.gradle.kts`
```kotlin
buildConfigField("String", "API_BASE_URL", "\"https://skillsync-api.vercel.app\"")
```

Or in `app/src/main/java/.../data/api/SkillEdgeApiService.kt`:
```kotlin
const val BASE_URL = "https://skillsync-api.vercel.app/api/"
```

### Example API Call (Kotlin)

```kotlin
// In your ViewModel or Repository
suspend fun fetchDashboardData() {
    val response = apiService.get("/data/unified-manager-intelligence")
    // Parse and use data
}
```

✅ **Android app will now connect to your backend**

---

## 🔄 Auto-Deployment (GitHub → Vercel)

**From now on, it's automatic:**

```
1. You push code to GitHub
   git push origin main

2. GitHub Actions builds Android APK

3. Vercel sees code change
   (auto-triggered)

4. Vercel deploys new backend
   (2-3 minutes)

5. Your live backend is updated
   No manual work needed!
```

---

## 📊 Monitor Your Backend

### View Logs

1. Go to: **https://vercel.com/dashboard**
2. Click **"skillsync-api" project**
3. Click **"Deployments" tab**
4. Click latest deployment
5. Click **"Runtime Logs"**

**You'll see:**
- Request logs
- Errors
- Performance metrics

### Check Status

1. Dashboard → skillsync-api
2. "Deployment Status" shows:
   - ✅ Healthy
   - ⚠️ Warnings
   - ❌ Errors

### View Analytics

1. Dashboard → skillsync-api
2. "Analytics" tab shows:
   - Request count
   - Response time
   - Error rate

---

## 🔑 Tokens & Secrets (Phase 2+)

When you need API keys, database passwords, etc. later:

1. Go to: **https://vercel.com/dashboard**
2. Select **"skillsync-api" project**
3. Click **"Settings"**
4. Click **"Environment Variables"**
5. Add:
   ```
   DATABASE_URL = your_database_connection
   API_KEY = your_api_key
   SECRET_KEY = your_secret
   ```
6. Click **"Save"**
7. Deployment auto-triggers

These are encrypted and never exposed.

---

## 📍 Your Live Backend URL

**Production:** `https://skillsync-api.vercel.app`

**Use this in Android app to fetch data:**
- Login: `POST /api/auth/login`
- Dashboard: `GET /api/dashboard/summary`
- Trainers: `GET /api/trainers`
- Actions: `GET /api/actions`
- All data: `GET /api/data/unified-manager-intelligence`

---

## 🎯 Workflow Summary

### Daily Workflow

```
1. Modify backend code
   (backend.py)

2. Commit & push
   git push origin main

3. GitHub Actions builds APK
   (automatic)

4. Vercel deploys backend
   (automatic, 2-3 min)

5. Your live backend is updated
   No manual work!

6. Android app uses new backend
   (auto via API calls)
```

### Everything is Automatic

| Task | Before | Now |
|------|--------|-----|
| Deploy backend | Manual SSH + server | Auto via Vercel |
| Manage server | Daily monitoring | Vercel handles it |
| Scale on demand | Manual load balancing | Vercel auto-scales |
| Monitor health | Log into server | Vercel dashboard |
| Update API | Stop server, deploy | Git push → auto deploy |
| Manage secrets | Config files | Vercel env vars |

**You literally never touch a server again.** 🎉

---

## 🆘 Troubleshooting

### Deployment Fails

1. Go to: https://vercel.com/dashboard
2. Click "skillsync-api"
3. Click latest "Deployment"
4. Scroll to "Build Logs"
5. Check error message

**Common issues:**
- Missing `requirements.txt` → Add it
- Python syntax error in `backend.py` → Fix code
- Wrong file path → Check `vercel.json`

### Backend Returns 500 Error

1. Check Vercel logs (see above)
2. Look for traceback in logs
3. Fix error in `backend.py`
4. `git push origin main` → Vercel redeploys

### Android App Can't Connect

1. Verify URL is correct:
   `https://skillsync-api.vercel.app`
2. Test in browser:
   `https://skillsync-api.vercel.app/healthz`
3. Check network on device (WiFi/cellular)
4. Update API base URL in Android code if wrong

---

## 📚 Next Steps

### Phase 1 (Now)
- ✅ Backend deployed on Vercel
- ✅ Android app built on GitHub
- ⏭️ Connect Android app to backend
- ⏭️ Test login flow
- ⏭️ Test dashboard data loading

### Phase 2 (Next)
- Real database (instead of mock data)
- User authentication (JWT tokens)
- Data persistence
- More API endpoints
- Error handling & logging

### Production (Later)
- Custom domain name
- SSL certificate (included free)
- Database backup
- API rate limiting
- Advanced monitoring

---

## ✨ You're Done!

**Your backend is now running 24/7 on Vercel.**

No server management. No manual deployments. No downtime.

Every code push → Auto-deployed to production in 2-3 minutes.

```bash
# That's it. You're all set!
git status   # Check it
git log -1   # View latest commit
# Backend is live at: https://skillsync-api.vercel.app
```

---

**Status:** ✅ Backend live and running 24/7  
**URL:** https://skillsync-api.vercel.app  
**Management:** Zero (Vercel handles everything)  
**Cost:** Free tier (or $20/month if you need more)

