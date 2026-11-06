package dev.dybug.routeros.device.run

import com.intellij.execution.configurations.RunConfigurationOptions

enum class RunMode {
    /** Export-style files are imported, scripts are executed. */
    AUTO,

    /**
     * Run the text as a temporary `/system script ide-run-<random>` in the background (so that the
     * log tail and Stop keep working); the script and its output file are removed afterwards.
     */
    EXECUTE,

    /** Store as `/system script` (prefix `ide-`) and run it, so the script's own policies apply. */
    UPLOAD_AND_RUN,

    /** Upload as a file and `/import` it (applies configuration). */
    IMPORT,
}

class RosRunConfigurationOptions : RunConfigurationOptions() {
    var profile by string("")
    var scriptPath by string("")
    var mode by enum(RunMode.AUTO)
    var validateBeforeRun by property(true)
    var tailLog by property(true)
    var keepUploadedScript by property(false)
}
