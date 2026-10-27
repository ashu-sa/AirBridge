package com.vibebridge.fitbit.calls

object CallingAppSlots {
    const val MAX = 5
    val preferred = listOf("com.whatsapp", "com.instagram.android", "org.telegram.messenger")
    fun resolve(saved: List<String>, selected: Set<String>, installed: Set<String>, recognized: List<String>): List<String> {
        val kept = saved.filter { it in installed }.distinct().take(MAX)
        val initial = if (kept.isNotEmpty()) kept else preferred.filter { it in selected } + selected.sorted()
        return (initial + preferred + recognized).filter { it in installed }.distinct().take(MAX)
    }
    fun replace(slots: List<String>, selected: Set<String>, index: Int, pkg: String): Pair<List<String>, Set<String>> {
        require(index in 0 until MAX && pkg.isNotBlank())
        val next = MutableList(MAX) { slots.getOrNull(it).orEmpty() }
        val old = next[index]
        val existing = next.indexOf(pkg)
        if (existing >= 0) next[existing] = old
        next[index] = pkg
        val result = next.filter { it.isNotBlank() }.distinct()
        val enabled = if (existing >= 0) selected else if (old in selected) selected - old + pkg else selected - old
        return result to enabled.intersect(result.toSet())
    }
}
