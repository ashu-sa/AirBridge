package com.vibebridge.fitbit.calls

/** Combines SIM state without letting an idle SIM cancel another ringing SIM. */
class CallStateTracker(ids: Set<Int>) {
    private val states = ids.associateWith { -1 }.toMutableMap()
    fun remove(id: Int) { states.remove(id) }
    fun update(id: Int, state: Int): Int? {
        if (id !in states || state !in 0..2) return null
        states[id] = state
        return when {
            states.values.any { it == 1 } -> 1
            states.values.any { it == -1 } -> null // Wait for initial state on all SIMs.
            states.values.any { it == 2 } -> 2
            else -> 0
        }
    }
}
