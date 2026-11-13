package com.chewanninja.parcelscanner;

import android.content.Intent;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {
    private AppSettings s;
    private EditText etUrl, etKey, etTimeout, etBcAction, etBcExtra;
    private CheckBox cbBeep, cbVibrate, cbAwake, cbBroadcast;
    private TextView tvPing;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_settings);
        s = new AppSettings(this);
        etUrl = findViewById(R.id.etUrl);
        etKey = findViewById(R.id.etKey);
        etTimeout = findViewById(R.id.etTimeout);
        etBcAction = findViewById(R.id.etBcAction);
        etBcExtra = findViewById(R.id.etBcExtra);
        cbBeep = findViewById(R.id.cbBeep);
        cbVibrate = findViewById(R.id.cbVibrate);
        cbAwake = findViewById(R.id.cbAwake);
        cbBroadcast = findViewById(R.id.cbBroadcast);
        tvPing = findViewById(R.id.tvPing);

        etUrl.setText(s.getScriptUrl());
        etKey.setText(s.getSecretKey());
        etTimeout.setText(String.valueOf(s.getScanTimeoutMs()));
        etBcAction.setText(s.getBroadcastAction());
        etBcExtra.setText(s.getBroadcastExtra());
        cbBeep.setChecked(s.isBeep());
        cbVibrate.setChecked(s.isVibrate());
        cbAwake.setChecked(s.isKeepAwake());
        cbBroadcast.setChecked(s.isBroadcastEnabled());

        findViewById(R.id.btnSave).setOnClickListener(v -> { save(); Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show(); });
        findViewById(R.id.btnBack).setOnClickListener(v -> { save(); finish(); });
        findViewById(R.id.btnScannerTest).setOnClickListener(v -> { save(); startActivity(new Intent(this, ScannerTestActivity.class)); });
        findViewById(R.id.btnRetry).setOnClickListener(v -> {
            save();
            App.sync().kick(false);
            Toast.makeText(this, "Uploading pending scans...", Toast.LENGTH_SHORT).show();
        });
        findViewById(R.id.btnPing).setOnClickListener(v -> {
            save();
            tvPing.setText("Testing...");
            final String url = s.getScriptUrl();
            final String key = s.getSecretKey();
            new Thread(() -> {
                final SheetsClient.Result r = new SheetsClient().ping(url, key);
                runOnUiThread(() -> tvPing.setText(r.status + ": " + r.message));
            }).start();
        });
    }

    private void save() {
        int timeout = 2000;
        try { timeout = Math.max(0, Integer.parseInt(etTimeout.getText().toString().trim())); } catch (NumberFormatException ignored) { }
        s.save(etUrl.getText().toString(), etKey.getText().toString(), timeout, cbBeep.isChecked(),
                cbVibrate.isChecked(), cbAwake.isChecked(), cbBroadcast.isChecked(),
                etBcAction.getText().toString(), etBcExtra.getText().toString());
    }

    @Override
    public void onBackPressed() {
        save();
        super.onBackPressed();
    }
}
