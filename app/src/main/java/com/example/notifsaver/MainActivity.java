package com.example.notifsaver;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private final List<NotificationRecord> items = new ArrayList<>();
    private final SimpleDateFormat fmt = new SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault());

    private TextView status;
    private Button accessButton;
    private EditText search;
    private BaseAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Notification Saver");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(8), dp(12), dp(8));

        status = new TextView(this);
        status.setTextSize(14);
        status.setPadding(0, dp(4), 0, dp(8));
        root.addView(status);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);

        accessButton = new Button(this);
        accessButton.setText("Notification access");
        accessButton.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        buttons.addView(accessButton, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button export = new Button(this);
        export.setText("Export CSV");
        export.setOnClickListener(v -> exportCsv());
        buttons.addView(export, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button clear = new Button(this);
        clear.setText("Clear all");
        clear.setOnClickListener(v -> confirmClear());
        buttons.addView(clear, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        root.addView(buttons);

        search = new EditText(this);
        search.setHint("Search app, title or text");
        search.setSingleLine(true);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { refresh(); }
        });
        root.addView(search);

        ListView list = new ListView(this);
        adapter = new RecordAdapter();
        list.setAdapter(adapter);
        list.setEmptyView(null);
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            final NotificationRecord r = items.get(position);
            new AlertDialog.Builder(this)
                    .setMessage("Delete this saved notification?")
                    .setPositiveButton("Delete", (d, w) -> {
                        NotificationStore.get(this).delete(r.id);
                        refresh();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return true;
        });
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isListenerEnabled()) {
            try {
                NotificationListenerService.requestRebind(
                        new ComponentName(this, NotifListenerService.class));
            } catch (Throwable ignored) {
            }
        }
        refresh();
    }

    private boolean isListenerEnabled() {
        String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return flat != null && flat.contains(getPackageName());
    }

    private void refresh() {
        boolean enabled = isListenerEnabled();
        int total = NotificationStore.get(this).count();
        if (enabled) {
            status.setText("Saving is ON  •  " + total + " notifications stored");
            status.setTextColor(Color.parseColor("#2E7D32"));
            accessButton.setText("Access settings");
        } else {
            status.setText("Saving is OFF. Tap \"Notification access\" and enable Notification Saver.");
            status.setTextColor(Color.parseColor("#C62828"));
            accessButton.setText("Grant access");
        }
        items.clear();
        items.addAll(NotificationStore.get(this).query(search.getText().toString()));
        adapter.notifyDataSetChanged();
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setMessage("Delete ALL saved notifications? This cannot be undone.")
                .setPositiveButton("Delete all", (d, w) -> {
                    NotificationStore.get(this).clear();
                    refresh();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Writes every saved notification to Downloads/notifications_export_<time>.csv */
    private void exportCsv() {
        List<NotificationRecord> all = NotificationStore.get(this).query("");
        if (all.isEmpty()) {
            Toast.makeText(this, "Nothing to export yet", Toast.LENGTH_SHORT).show();
            return;
        }
        StringBuilder sb = new StringBuilder("﻿");
        sb.append("time,app,package,system_app,title,text,expanded_text,sub_text,category,channel\n");
        for (NotificationRecord r : all) {
            sb.append(csv(fmt.format(new Date(r.postedAt)))).append(',')
                    .append(csv(r.app)).append(',')
                    .append(csv(r.pkg)).append(',')
                    .append(r.isSystem ? "yes" : "no").append(',')
                    .append(csv(r.title)).append(',')
                    .append(csv(r.text)).append(',')
                    .append(csv(r.bigText)).append(',')
                    .append(csv(r.subText)).append(',')
                    .append(csv(r.category)).append(',')
                    .append(csv(r.channel)).append('\n');
        }
        String name = "notifications_export_"
                + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".csv";
        try {
            ContentValues v = new ContentValues();
            v.put(MediaStore.Downloads.DISPLAY_NAME, name);
            v.put(MediaStore.Downloads.MIME_TYPE, "text/csv");
            v.put(MediaStore.Downloads.RELATIVE_PATH, "Download");
            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (uri == null) throw new IllegalStateException("insert failed");
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new IllegalStateException("no stream");
                out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            }
            Toast.makeText(this, "Saved to Downloads/" + name, Toast.LENGTH_LONG).show();
        } catch (Exception ex) {
            Toast.makeText(this, "Export failed: " + ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private static String csv(String s) {
        if (s == null) s = "";
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private class RecordAdapter extends BaseAdapter {
        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int i) { return items.get(i); }
        @Override public long getItemId(int i) { return items.get(i).id; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row;
            TextView header;
            TextView title;
            TextView body;
            if (convertView == null) {
                row = new LinearLayout(MainActivity.this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(dp(4), dp(10), dp(4), dp(10));

                header = new TextView(MainActivity.this);
                header.setTextSize(12);
                header.setTextColor(Color.parseColor("#607D8B"));
                header.setId(1);
                row.addView(header);

                title = new TextView(MainActivity.this);
                title.setTextSize(16);
                title.setTypeface(Typeface.DEFAULT_BOLD);
                title.setId(2);
                row.addView(title);

                body = new TextView(MainActivity.this);
                body.setTextSize(14);
                body.setId(3);
                row.addView(body);
            } else {
                row = (LinearLayout) convertView;
                header = row.findViewById(1);
                title = row.findViewById(2);
                body = row.findViewById(3);
            }

            NotificationRecord r = items.get(position);
            header.setText(r.app + (r.isSystem ? "  (system)" : "") + "  •  "
                    + fmt.format(new Date(r.postedAt)));
            title.setText(r.title);
            title.setVisibility(r.title.isEmpty() ? View.GONE : View.VISIBLE);
            String content = !r.bigText.isEmpty() ? r.bigText : r.text;
            body.setText(content);
            body.setVisibility(content.isEmpty() ? View.GONE : View.VISIBLE);
            return row;
        }
    }
}
