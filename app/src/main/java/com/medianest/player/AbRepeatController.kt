package com.medianest.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AbRepeatState(
    val pointA: Long? = null,
    val pointB: Long? = null,
    val isActive: Boolean = false
)

/**
 * Modular controller for managing A-B Repeat point setting, fine-tuning, and playback looping.
 */
class AbRepeatController(
    private val onSeek: (Long) -> Unit
) {
    private val _state = MutableStateFlow(AbRepeatState())
    val state: StateFlow<AbRepeatState> = _state.asStateFlow()

    fun setPointA(currentPosMs: Long) {
        val pos = currentPosMs.coerceAtLeast(0L)
        val curB = _state.value.pointB
        val newB = if (curB != null && pos >= curB) null else curB
        val isActive = newB != null && pos < newB
        _state.value = _state.value.copy(
            pointA = pos,
            pointB = newB,
            isActive = isActive
        )
    }

    fun setPointB(currentPosMs: Long) {
        val pos = currentPosMs.coerceAtLeast(0L)
        val curA = _state.value.pointA ?: 0L
        if (pos > curA) {
            _state.value = _state.value.copy(
                pointA = curA,
                pointB = pos,
                isActive = true
            )
            if (currentPosMs >= pos) {
                onSeek(curA)
            }
        }
    }

    fun adjustPointA(deltaMs: Long, durationMs: Long) {
        val curA = _state.value.pointA ?: return
        val maxLimit = (_state.value.pointB ?: (if (durationMs > 0) durationMs else curA + 10000L)) - 100L
        val newA = (curA + deltaMs).coerceIn(0L, maxLimit.coerceAtLeast(0L))
        _state.value = _state.value.copy(pointA = newA)
    }

    fun adjustPointB(deltaMs: Long, durationMs: Long) {
        val curB = _state.value.pointB ?: return
        val minLimit = (_state.value.pointA ?: 0L) + 100L
        val maxLimit = if (durationMs > 0) durationMs else Long.MAX_VALUE
        val newB = (curB + deltaMs).coerceIn(minLimit, maxLimit)
        _state.value = _state.value.copy(pointB = newB)
    }

    fun toggle(currentPosMs: Long? = null) {
        val cur = _state.value
        if (cur.pointA != null && cur.pointB != null) {
            val nextActive = !cur.isActive
            _state.value = cur.copy(isActive = nextActive)
            if (nextActive && currentPosMs != null) {
                if (currentPosMs < cur.pointA || currentPosMs >= cur.pointB) {
                    onSeek(cur.pointA)
                }
            }
        }
    }

    fun clear() {
        _state.value = AbRepeatState()
    }

    fun checkAndLoop(currentPosMs: Long) {
        val cur = _state.value
        if (cur.isActive && cur.pointA != null && cur.pointB != null && cur.pointB > cur.pointA) {
            if (currentPosMs >= cur.pointB) {
                onSeek(cur.pointA)
            }
        }
    }
}
