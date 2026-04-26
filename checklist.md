# Studyflow + Guard Checklist

## Phase 0 - Stability and Performance

- [ ] Fix Stats tab crashes
- [ ] Fix PYQ freezes
- [ ] Move heavy DB/repository work off Main thread
- [ ] Share usage state between Home and Stats
- [ ] Add usage stats cache
- [ ] Add PYQ question cache
- [ ] Use lazy ViewModel creation where possible
- [ ] Use lifecycle-safe state collection
- [ ] Reduce unnecessary recomposition
- [ ] Convert fatal repository/database errors into safe UI states

## Phase 1 - Smarter Blocking Core

- [ ] Add protected apps list
- [ ] Add app-specific uninstall shield
- [ ] Add app-specific force stop shield
- [ ] Add app-specific clear data / clear storage shield
- [ ] Add app-specific app info shield
- [ ] Remove or soften generic launcher uninstall blocking
- [ ] Add reels-only blocking mode
- [ ] Add shorts-only blocking mode
- [ ] Improve Instagram Reels detection
- [ ] Add YouTube Shorts detection
- [ ] Add Facebook Reels / similar surface detection
- [ ] Add Study YouTube mode
- [ ] Support full-app block vs surface-only block vs limit-based access

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
- [ ] Add PYQ-based unlock
- [ ] Add focus-task-based unlock
- [ ] Add recovery flow after distraction
- [ ] Add smart nudges
- [ ] Add streaks
- [ ] Add XP / discipline score
- [ ] Add saved-time dashboard
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
- [ ] Add protected apps
- [ ] Add app-specific uninstall shield
- [ ] Add reels / shorts blocking
- [ ] Add Studyflow timer sync
- [ ] Add shared analytics basics

### V2

- [ ] Add app limits
- [ ] Add Study YouTube mode
- [ ] Add recovery flow
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
- [ ] Protected apps list
- [ ] App-specific uninstall shield
- [ ] Reels / Shorts-only block
- [ ] Study YouTube mode
- [ ] Studyflow timer sync
- [ ] Distraction event sync into Studyflow
- [ ] App limits
- [ ] Recovery flow
- [ ] Streak + saved-time dashboard
