package com.focusguard.app.friction

import android.util.Log
import com.focusguard.app.friction.layers.*
import kotlin.random.Random

/**
 * Builds a randomized sequence of friction layers for each attempt.
 *
 * The pipeline is different every time to prevent the user from
 * memorizing and adapting to the pattern.
 *
 * Base structure: Delay → Task → Wait → [FailureInjection] → [Task] → [Wait]
 * Additional layers are inserted based on escalation level.
 * Order is partially shuffled for unpredictability.
 */
class FrictionPipeline {

    /**
     * Build a randomized friction layer sequence based on escalation parameters.
     */
    fun build(params: EscalationEngine.EscalationParams): List<FrictionLayer> {
        val layers = mutableListOf<FrictionLayer>()

        // Always start with a forced delay (anchoring — set the frustration tone)
        layers.add(ForcedDelayLayer())

        // Core layers based on escalation
        when {
            params.escalationLevel <= 0 -> {
                // Level 0: Delay → Task → Wait
                layers.add(CognitiveTaskLayer())
                layers.add(RandomWaitLayer())
            }
            params.escalationLevel == 1 -> {
                // Level 1: Delay → Task → Wait → FailureInjection
                layers.add(CognitiveTaskLayer())
                layers.add(RandomWaitLayer())
                layers.add(FailureInjectionLayer())
            }
            params.escalationLevel == 2 -> {
                // Level 2: Delay → Task → Wait → FailureInjection → Task → Wait
                layers.add(CognitiveTaskLayer())
                layers.add(RandomWaitLayer())
                layers.add(FailureInjectionLayer())
                layers.add(CognitiveTaskLayer())
                layers.add(RandomWaitLayer())
            }
            else -> {
                // Level 3+: Maximum friction
                layers.add(CognitiveTaskLayer())
                layers.add(RandomWaitLayer())
                layers.add(FailureInjectionLayer())
                layers.add(ForcedDelayLayer()) // Second delay!
                layers.add(CognitiveTaskLayer())
                layers.add(RandomWaitLayer())
                layers.add(FailureInjectionLayer()) // Second failure chance!

                // Extra layers for extreme escalation
                val extraLayers = (params.escalationLevel - 3).coerceAtMost(4)
                repeat(extraLayers) {
                    layers.add(
                        if (Random.nextBoolean()) CognitiveTaskLayer()
                        else RandomWaitLayer()
                    )
                }
            }
        }

        // Apply anti-adaptation: shuffle middle layers (keep first delay fixed)
        if (layers.size > 3) {
            val firstLayer = layers.removeAt(0)
            layers.shuffle()
            layers.add(0, firstLayer)
        }

        Log.d(TAG, "Pipeline built: ${layers.size} layers → ${layers.map { it.name }}")
        return layers
    }

    companion object {
        private const val TAG = "FrictionPipeline"
    }
}
