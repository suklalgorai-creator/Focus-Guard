package com.focusguard.app.friction

import android.util.Log
import com.focusguard.app.friction.layers.CognitiveTaskLayer
import com.focusguard.app.friction.layers.FailureInjectionLayer
import com.focusguard.app.friction.layers.ForcedDelayLayer
import com.focusguard.app.friction.layers.FrictionLayer
import com.focusguard.app.friction.layers.RandomWaitLayer
import kotlin.random.Random

/**
 * Builds a randomized friction sequence for each blocked app attempt.
 *
 * The first visible step is always a PYQ task so every distraction attempt
 * creates at least one productive study action before any delay or denial.
 */
class FrictionPipeline {

    fun build(params: EscalationEngine.EscalationParams): List<FrictionLayer> {
        val layers = mutableListOf<FrictionLayer>()

        // Always start with a PYQ so every attempt creates study value.
        layers.add(CognitiveTaskLayer())

        when {
            params.escalationLevel <= 0 -> {
                layers.add(if (Random.nextBoolean()) RandomWaitLayer() else ForcedDelayLayer())
            }
            params.escalationLevel == 1 -> {
                layers.add(RandomWaitLayer())
                if (Random.nextFloat() < 0.5f) layers.add(FailureInjectionLayer())
                if (Random.nextFloat() < 0.3f) layers.add(CognitiveTaskLayer())
            }
            params.escalationLevel == 2 -> {
                layers.add(RandomWaitLayer())
                layers.add(FailureInjectionLayer())
                layers.add(CognitiveTaskLayer())
                if (Random.nextFloat() < 0.4f) layers.add(RandomWaitLayer())
            }
            else -> {
                layers.add(RandomWaitLayer())
                layers.add(FailureInjectionLayer())
                layers.add(ForcedDelayLayer())
                layers.add(CognitiveTaskLayer())
                layers.add(RandomWaitLayer())
                layers.add(FailureInjectionLayer())

                val extraLayers = (params.escalationLevel - 3).coerceAtMost(4)
                repeat(extraLayers) {
                    layers.add(
                        when (Random.nextInt(3)) {
                            0 -> CognitiveTaskLayer()
                            1 -> RandomWaitLayer()
                            else -> ForcedDelayLayer()
                        }
                    )
                }
            }
        }

        if (layers.size > 2) {
            val firstLayer = layers.removeAt(0)
            layers.shuffle()
            layers.add(0, firstLayer)
        }

        Log.d(TAG, "Pipeline built: ${layers.size} layers -> ${layers.map { it.name }}")
        return layers
    }

    companion object {
        private const val TAG = "FrictionPipeline"
    }
}
