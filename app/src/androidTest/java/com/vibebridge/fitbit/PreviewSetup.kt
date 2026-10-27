package com.vibebridge.fitbit

import androidx.test.platform.app.InstrumentationRegistry
import com.vibebridge.fitbit.schedule.AlertSchedule
import com.vibebridge.fitbit.schedule.AlertScheduleStore
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Explicit development setup only, not a validation check. Never bundled with the app. */
class PreviewSetup {
    @Test fun configurePresets() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("presets") == "off")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        TestPreferenceBackup.restore(context)
        AlertScheduleStore.save(context, AlertSchedule(false))
        context.getSharedPreferences("air_bridge_schedule", 0).edit().commit()
        println("Editable Workout/Sleep presets saved; scheduling off")
    }
}
