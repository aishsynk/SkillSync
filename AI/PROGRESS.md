# AGENTS.md — AI Agent Guidelines & Operating Procedures

## Core Operating Principles

1. **Single Source of Truth**: Always start by reading `AI/PROGRESS.md` and treat it as the single source of truth for the project.
2. **Context & Decisions**:
   - Review `AI/CONTEXT.md` for durable, reusable project knowledge and architecture facts.
   - Review `AI/DECISIONS.md` for key architectural, design, business, or process decisions whenever additional context is required.
   - If any required `AI/*.md` file does not exist, create it based on the current project state and continue working.

---

# Session Handover Summary

- **Date and Time:** 2026-09-13T03:38:00+05:30
- **Model Used:** Antigravity (Gemini)
- **Tool/Agent Used:** Antigravity

## 1. What was completed previously
- Extracted business rules and factual logic to correct UI states (fixing "urgent demand" and "unstaffed delivery" assumptions).
- Compiled a validated `.apk` proving the build was stable.

## 2. What is currently in progress
- **Total Redesign (Light-First Enterprise Workspace):** The user mandated a complete redesign of the app away from the dark dashboard into a clean, modern enterprise product (like Linear or Notion). 
- **Today Screen Redesign:** We rewrote `ManagerCommandCentre.kt` with a completely new layout: "Needs your attention", "Today's operations", "Watchlist", and "Coming up".
- **Navigation Update:** We renamed the labels in the bottom navigation of `MainScreen.kt` to exactly match the requested standard: `Today`, `Plan`, `People`, `Delivery`, `More`.
- **Compilation Check:** The app successfully compiles via `:app:compileDebugKotlin`.

## 3. Files Modified
- `app/src/main/java/com/example/skillsync/feature/home/ManagerCommandCentre.kt` (Total Layout Redesign)
- `app/src/main/java/com/example/skillsync/feature/home/MainScreen.kt` (Bottom Nav label changes)

## 4. Current Status
- The `Today` screen has been radically simplified and rebuilt according to the new visual spec. It successfully compiles.
- **Assembling the APK:** `:app:assembleDebug` is currently running to generate the final APK.

## 5. Known Issues or Blockers
- **Icons:** We updated the text strings for the bottom navigation destinations, but the *icons* mapping those destinations might need an update to logically match (e.g., the icon for "More" vs old "Actions", or "Plan" vs old "Command").
- **Theme:** We hardcoded light colors into `ManagerCommandCentre.kt` for now. The global `Color.kt` and `Theme.kt` must be formally refactored next.
- **Tests:** `ScreenRenderTest.kt` or other UI tests may now fail because the UI hierarchy of `ManagerCommandCentre` has completely changed. They will need to be rewritten to assert against the new structure.

## 6. Next Recommended Actions
- Wait for `:app:assembleDebug` to complete.
- Complete the final `.apk` verification check using `aapt2 dump badging`.
- Move on to rebuilding the global Light-First theme in `Color.kt` and `Theme.kt`.
- Fix up the navigation icons.
- Check and fix any broken tests in `ScreenRenderTest.kt`.
- Begin planning for screens 2-9 (`Plan`, `People`, `Delivery`, etc.) after visual approval of `Today`.
