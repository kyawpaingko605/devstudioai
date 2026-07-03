// IPC for build-process isolation (docs/build-process-isolation.md). Daemon -> UI callbacks,
// invoked on a background thread in the UI process. Oneway so the daemon never blocks on the UI.
package devstudioai.android.daemon;

oneway interface IBuildCallback {
    // Heavy engine initialization finished. If [success] is false, the project failed to open
    // (e.g. invalid build scripts) and subsequent commands are invalid.
    void onOpened(boolean success);

    // One-shot state delta: stdout text appended to the active build console.
    void onBuildOutput(String text);

    // One-shot state delta: the active build completed / was canceled.
    void onBuildComplete(boolean success);

    // --- Phase 4: interactive run (the program runs in :build; these pipe its stdout + request permission).
    void onRunOutput(String text);       // stdout line from the running program
    void onRunComplete(int exitCode);    // program exited
    void onRequestPermission(int id, String permission, String rationale); // prompt user for a sandbox permission
}
