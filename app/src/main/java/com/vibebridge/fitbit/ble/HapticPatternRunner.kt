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
