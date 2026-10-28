package com.vibebridge.fitbit.ble

import com.vibebridge.fitbit.AirHapticPattern
import io.reactivex.Scheduler
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.TimeUnit

interface HapticSettingsClient {
    fun read(): Single<Int>
    fun write(intensity: Int): Single<Unit>
}

data class AirHapticExecutionResult(val initialIntensity: Int, val finalIntensity: Int, val groupCount: Int)
class HapticsRestoreException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

/** Testable protocol-independent runner. A checkpoint is saved BEFORE changing the band. */
class HapticPatternRunner(
    private val client: HapticSettingsClient,
    private val scheduler: Scheduler = Schedulers.computation(),
    private val checkpoint: (Int) -> Unit = {},
    private val verified: () -> Unit = {}
) {
    /** A link check never writes, checkpoints, recovers, or clears a pending recovery. */
    fun checkConnection(): Single<AirHapticExecutionResult> = client.read().map { intensity ->
        require(intensity in AirDirectSettingsProtocol.HAPTICS_OFF..AirDirectSettingsProtocol.HAPTICS_HIGH) { "Unsupported haptics intensity" }
        AirHapticExecutionResult(intensity, intensity, 0)
    }
    fun execute(pattern: AirHapticPattern): Single<AirHapticExecutionResult> = client.read().flatMap { original ->
        require(original in AirDirectSettingsProtocol.HAPTICS_OFF..AirDirectSettingsProtocol.HAPTICS_HIGH) { "Unsupported starting haptics intensity" }
        checkpoint(original)
        val alternate = if (original == AirDirectSettingsProtocol.HAPTICS_HIGH) AirDirectSettingsProtocol.HAPTICS_LOW else AirDirectSettingsProtocol.HAPTICS_HIGH
        var chain = Single.just(Unit)
        repeat(pattern.groupCount) { group ->
            chain = chain.flatMap {
                val before = if (group == 0) 0L else pattern.betweenGroupDelaysMillis[group - 1]
                Single.timer(before, TimeUnit.MILLISECONDS, scheduler).flatMap {
                    client.write(alternate).flatMap {
                        Single.timer(pattern.restoreDelayMillis, TimeUnit.MILLISECONDS, scheduler)
                            .flatMap { restoreWrite(original) }
                    }.onErrorResumeNext { error ->
                        // A lost write response may still mean the band changed. Try restoration.
                        if (error is HapticsRestoreException) Single.error(error)
                        else restoreWrite(original).flatMap { Single.error<Unit>(error) }
                    }
                }
            }
        }
        chain.flatMap { verify(original) }.map {
            verified()
            AirHapticExecutionResult(original, original, pattern.groupCount)
        }
    }

    /** One bounded call session: restore between pulses, check cancellation during every delay. */
    fun executeRinging(pattern: AirHapticPattern, shouldContinue: () -> Boolean,
                       maxDurationMillis: Long = 60_000L): Single<AirHapticExecutionResult> = Single.defer {
        require(maxDurationMillis > 0) { "Call duration must be bounded" }
        val started = scheduler.now(TimeUnit.MILLISECONDS)
        fun allowed() = shouldContinue() && scheduler.now(TimeUnit.MILLISECONDS) - started < maxDurationMillis
        fun waitOrStop(delay: Long): Single<Long> = Single.timer(delay, TimeUnit.MILLISECONDS, scheduler)
            .ambWith(io.reactivex.Observable.interval(0, 50, TimeUnit.MILLISECONDS, scheduler)
                .filter { !allowed() }.firstOrError())
        client.read().flatMap { original ->
            require(original in AirDirectSettingsProtocol.HAPTICS_OFF..AirDirectSettingsProtocol.HAPTICS_HIGH) { "Unsupported starting haptics intensity" }
            if (!allowed()) return@flatMap Single.just(AirHapticExecutionResult(original, original, 0))
            checkpoint(original)
            val alternate = if (original == AirDirectSettingsProtocol.HAPTICS_HIGH) AirDirectSettingsProtocol.HAPTICS_LOW else AirDirectSettingsProtocol.HAPTICS_HIGH
            fun pulse(): Single<Unit> = client.write(alternate).flatMap {
                waitOrStop(pattern.restoreDelayMillis).flatMap { restoreWrite(original) }
            }.onErrorResumeNext { error ->
                if (error is HapticsRestoreException) Single.error(error)
                else restoreWrite(original).flatMap { Single.error<Unit>(error) }
            }
            fun next(group: Int, count: Int): Single<Int> = Single.defer {
                if (!allowed()) Single.just(count)
                else pulse().flatMap {
                    val nextGroup = (group + 1) % pattern.groupCount
                    val gap = if (nextGroup == 0) {
                        if (pattern == AirHapticPattern.CONTINUOUS) 120L else 1_000L
                    } else pattern.betweenGroupDelaysMillis[group]
                    waitOrStop(gap).flatMap { next(nextGroup, count + 1) }
                }
            }
            next(0, 0).flatMap { count ->
                verify(original).map {
                    verified()
                    AirHapticExecutionResult(original, original, count)
                }
            }
        }
    }

    fun recover(original: Int): Single<Unit> {
        require(original in AirDirectSettingsProtocol.HAPTICS_OFF..AirDirectSettingsProtocol.HAPTICS_HIGH) { "Invalid recovery intensity" }
        return restoreWrite(original).flatMap { verify(original) }.doOnSuccess { verified() }
    }

    private fun restoreWrite(original: Int): Single<Unit> = client.write(original).retry(2)
        .onErrorResumeNext { Single.error(HapticsRestoreException("Band vibration setting could not be restored.", it)) }

    private fun verify(original: Int): Single<Unit> = client.read().flatMap { final ->
        if (final == original) Single.just(Unit)
        else Single.error(HapticsRestoreException("Band vibration setting was not restored."))
    }.onErrorResumeNext {
        Single.error(HapticsRestoreException("Unable to verify the restored band vibration setting.", it))
    }
}
