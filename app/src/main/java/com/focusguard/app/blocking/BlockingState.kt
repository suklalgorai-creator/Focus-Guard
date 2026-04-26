package com.focusguard.app.blocking

enum class BlockingState {
    IDLE,
    MONITORING,
    BLOCKING,
    FRICTION_ACTIVE,
    COOLDOWN
}

enum class DetectionSource {
    ACCESSIBILITY,
    USAGE_STATS
}
