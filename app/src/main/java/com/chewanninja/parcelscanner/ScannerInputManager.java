package com.chewanninja.parcelscanner;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

/**
 * Keyboard-wedge scanner integration for the Kaicom K7.
 *
 * The built-in scanner "types" the barcode into the focused EditText and then sends ENTER.
 * ENTER can arrive in three different ways depending on the firmware, and all are handled:
 *   1. hardware key event (ACTION_DOWN + KEYCODE_ENTER / KEYCODE_NUMPAD_ENTER)  -> KEY_EVENT
 *   2. IME editor action                                                        -> EDITOR_ACTION
 *   3. a literal CR/LF character inside the text                                -> NEWLINE_CHAR
 * A short guard window stops the same ENTER from being processed twice.
 */
public class ScannerInputManager implements ScannerSource {

    private static final long DOUBLE_ENTER_GUARD_MS = 400;

    private final EditText field;
    private final ScanListener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int timeoutMs = 2000;
    private long lastSubmitUptime = -1000000;
    private boolean internalChange;
    private boolean started;

    private final Runnable staleClear = new Runnable() {
        @Override public void run() {
            String stale = field.getText().toString();
            if (!stale.isEmpty()) {
                clearBuffer();
                listener.onBufferCleared(stale);
            }
        }
    };

    private final TextWatcher watcher = new TextWatcher() {
        @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
        @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
        @Override public void afterTextChanged(Editable e) {
            if (internalChange) return;
            handleTextChanged(e.toString());
        }
    };

    public ScannerInputManager(EditText field, ScanListener listener) {
        this.field = field;
        this.listener = listener;
        configureField();
    }

    private void configureField() {
        field.setSingleLine(true);
        field.setMaxLines(1);
        field.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        field.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        field.setShowSoftInputOnFocus(false);   // no on-screen keyboard (API 21+)
        field.setCursorVisible(false);
        field.setFocusable(true);
        field.setFocusableInTouchMode(true);
    }

    public void setTimeoutMs(int ms) { this.timeoutMs = ms; }

    @Override
    public void start() {
        if (started) return;
        started = true;
        field.addTextChangedListener(watcher);

        field.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                listener.onRawEvent((event.getAction() == KeyEvent.ACTION_DOWN ? "DOWN " : "UP ")
                        + KeyEvent.keyCodeToString(keyCode) + " (" + keyCode + ")"
                        + " repeat=" + event.getRepeatCount());
                if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                        submitBuffer("KEY_EVENT");
                    }
                    return true; // swallow both DOWN and UP so the editor callback does not fire too
                }
                return false;
            }
        });

        field.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                listener.onRawEvent("EDITOR_ACTION id=" + actionId);
                submitBuffer("EDITOR_ACTION");
                return true; // keep focus
            }
        });
    }

    @Override
    public void stop() {
        started = false;
        handler.removeCallbacks(staleClear);
        field.removeTextChangedListener(watcher);
        field.setOnKeyListener(null);
        field.setOnEditorActionListener(null);
    }

    private void handleTextChanged(String s) {
        int nl = indexOfNewline(s);
        if (nl >= 0) {
            // scanner delivered ENTER as a character: split "NV123...\n" (possibly several scans)
            String first = s.substring(0, nl);
            String rest = s.substring(nl + 1);
            setBuffer(rest);
            deliver(first, "NEWLINE_CHAR");
            if (!rest.isEmpty()) handleTextChanged(rest);
            return;
        }
        listener.onBufferChanged(s);
        handler.removeCallbacks(staleClear);
        if (!s.isEmpty() && timeoutMs > 0) handler.postDelayed(staleClear, timeoutMs);
    }

    private static int indexOfNewline(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\n' || c == '\r') return i;
        }
        return -1;
    }

    private void submitBuffer(String source) {
        String raw = field.getText().toString();
        clearBuffer();                 // clear immediately so the next scan starts clean
        deliver(raw, source);
    }

    private void deliver(String raw, String source) {
        handler.removeCallbacks(staleClear);
        String t = raw == null ? "" : raw.trim();
        long now = SystemClock.uptimeMillis();
        if (t.isEmpty()) {
            // second half of a double ENTER (key event + editor action, or scanner sending CR+LF)
            if (now - lastSubmitUptime < DOUBLE_ENTER_GUARD_MS) return;
            listener.onEmptyScan(source);
            return;
        }
        lastSubmitUptime = now;
        listener.onScan(raw, source);
    }

    private void setBuffer(String text) {
        internalChange = true;
        field.setText(text);
        field.setSelection(field.getText().length());
        internalChange = false;
    }

    public void clearBuffer() {
        handler.removeCallbacks(staleClear);
        setBuffer("");
    }

    /** Put the cursor back in the scanner field and keep the soft keyboard hidden. */
    public void restoreFocus() {
        if (!field.hasFocus()) field.requestFocus();
        InputMethodManager imm = (InputMethodManager) field.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(field.getWindowToken(), 0);
    }
}
