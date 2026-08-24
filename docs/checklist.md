# Studyflow + Guard Checklist

## Phase 0 - Stability and Performance

- [x] Fix Stats tab crashes
- [x] Fix PYQ freezes
- [x] Move heavy DB/repository work off Main thread
- [x] Share usage state between Home and Stats
- [x] Add usage stats cache
- [x] Add PYQ question cache
- [ ] Use lazy ViewModel creation where possible
- [x] Use lifecycle-safe state collection
- [x] Remove high-risk null assertions from overlay, PYQ lock card, and stats UI
- [x] Move installed-app scanning off the Blacklist screen main composition path
- [x] Apply modern productivity UI foundation polish
- [ ] Reduce unnecessary recomposition
- [ ] Convert fatal repository/database errors into safe UI states

## Phase 1 - Smarter Blocking Core

- [x] Add protected apps list
- [ ] Add app-specific uninstall shield
- [ ] Add app-specific force stop shield
- [ ] Add app-specific clear data / clear storage shield
- [ ] Add app-specific app info shield
- [x] Remove or soften generic launcher uninstall blocking
- [x] Add reels-only blocking mode
- [x] Add shorts-only blocking mode
- [x] Improve Instagram Reels detection
- [x] Add YouTube Shorts detection
- [ ] Add Facebook Reels / similar surface detection
- [x] Add Study YouTube mode
- [ ] Support full-app block vs surface-only block vs limit-based access

Current rule: full-app blocking is controlled only by apps toggled on in Distraction Blocks. No app is permanently hardcoded as always blocked.
Instagram rule: default mode is Reels-only blocking, so Instagram can open unless the user explicitly full-blacklists it.
PYQ rule: every blocked distraction attempt starts with a PYQ, including normal guard mode and surface blocks like Reels/Shorts.
PYQ analytics rule: overlay PYQ answers from normal blocks, strict blocks, and surface blocks are saved into PYQ attempt history.
Recovery rule: after a distraction block, Today shows a smart playful recovery card with Focus/PYQ actions.
Design rule: use neutral dark surfaces, green for productive actions, amber for recovery/warnings, red only for block/danger states, and tighter card radii.
Distraction Blocks layout now separates Surface Blocks, Full App Blocks, and the future Daily Limit lane. Limit-based access logic is still pending.
Exit protection rule: only Focus Guard self-removal/admin-deactivation is blocked. Other app uninstalls and normal file deletes stay allowed.

## Phase 2 - Studyflow Integration

- [ ] Add focus timer sync
- [ ] Start Guard automatically when Studyflow session starts
- [ ] Relax Guard automatically when Studyflow session ends
- [ ] Add session mode sync for Study / Revision / Break / Exam Sprint
- [ ] Add planner schedule sync
- [ ] Send distraction attempt events from Guard to Studyflow
- [ ] Add resume-session flow after distraction
- [ ] Finalize unified session state contract
- [ ] Keep Studyflow as source of truth
- [ ] Keep Guard as enforcement layer

## Phase 3 - Regain-Inspired Productivity Features

- [ ] Add app limits
- [ ] Add daily time budget per app
- [ ] Add controlled break mode
- [ ] Add session-based unlock
- [x] Add PYQ-based unlock
- [x] Log overlay PYQ attempts into progress analytics
- [ ] Add focus-task-based unlock
- [x] Add recovery flow after distraction
- [x] Add smart nudges
- [x] Add streaks
- [ ] Add XP / discipline score
- [x] Add saved-time dashboard
- [ ] Add weekly focus report
- [ ] Add social detox dashboard
- [ ] Add motivational buddy / focus coach

## Phase 4 - Advanced Guard Mode

- [ ] Add extreme mode
- [ ] Strengthen anti-bypass bundle
- [ ] Add Device Admin hard mode
- [ ] Explore Device Owner mode for dedicated devices
- [ ] Add protected app removal lock
- [ ] Strengthen settings interception
- [ ] Reduce web-version bypasses
- [ ] Handle split screen / floating window cases
- [ ] Detect dual app / clone app cases
- [ ] Add emergency unlock with audit log

## Phase 5 - Social and Accountability Layer

- [ ] Add focus with friends
- [ ] Add shared live timer
- [ ] Add accountability partner
- [ ] Add leaderboard
- [ ] Add group study room
- [ ] Add shared streak
- [ ] Add mentor / parent PIN mode
- [ ] Add daily challenge mode

## Version Plan

### V1

- [ ] Complete Phase 0
- [x] Add protected apps
- [ ] Add app-specific uninstall shield
- [x] Add reels / shorts blocking
- [ ] Add Studyflow timer sync
- [ ] Add shared analytics basics

### V2

- [ ] Add app limits
- [x] Add Study YouTube mode
- [x] Add recovery flow
- [ ] Improve analytics
- [ ] Add streaks and XP
- [ ] Add session-based unlock

### V3

- [ ] Add advanced anti-bypass
- [ ] Add extreme mode
- [ ] Explore Device Admin / Device Owner path
- [ ] Add accountability and social features

## Top 10 First Build Targets

- [ ] Stability and crash fixes
- [x] Protected apps list
- [ ] App-specific uninstall shield
- [x] Reels / Shorts-only block
- [x] Study YouTube mode
- [ ] Studyflow timer sync
- [ ] Distraction event sync into Studyflow
- [ ] App limits
- [x] Recovery flow
- [x] Streak + saved-time dashboard
