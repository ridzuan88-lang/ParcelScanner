package com.chewanninja.parcelscanner;

/** All callbacks arrive on the main thread. */
public interface ScanListener {
    /** A complete scan. enterSource: KEY_EVENT, EDITOR_ACTION, NEWLINE_CHAR or BROADCAST. */
    void onScan(String rawText, String enterSource);

    /** ENTER arrived but there was no data. */
    void onEmptyScan(String enterSource);

    /** Live content of the input buffer (diagnostics). */
    void onBufferChanged(String buffer);

    /** Characters arrived but ENTER never did within the timeout; buffer was discarded. */
    void onBufferCleared(String staleText);

    /** Human-readable description of each raw key/broadcast event (diagnostics). */
    void onRawEvent(String description);
}
