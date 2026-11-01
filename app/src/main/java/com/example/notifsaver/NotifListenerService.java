package com.example.notifsaver;

import android.app.Notification;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

/**
 * Receives every notification posted to the status bar (apps and system) once the user
 * grants "Notification access" and stores it in the local database.
 */
public class NotifListenerService extends NotificationListenerService {

    private static final String TAG = "NotifSaver";

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        // Also capture whatever is already sitting in the notification shade.
        try {
            StatusBarNotification[] active = getActiveNotifications();
            if (active != null) {
                for (StatusBarNotification sbn : active) {
                    save(sbn);
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "Failed to read active notifications", t);
        }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            save(sbn);
        } catch (Throwable t) {
            Log.e(TAG, "Failed to save notification", t);
        }
    }

    private void save(StatusBarNotification sbn) {
        if (sbn == null) return;
        String pkg = sbn.getPackageName();
        if (pkg == null || pkg.equals(getPackageName())) return;

        Notification n = sbn.getNotification();
        if (n == null || n.extras == null) return;
        Bundle e = n.extras;

        String title = str(e.getCharSequence(Notification.EXTRA_TITLE));
        if (title.isEmpty()) title = str(e.getCharSequence(Notification.EXTRA_TITLE_BIG));
        String text = str(e.getCharSequence(Notification.EXTRA_TEXT));
        String big = str(e.getCharSequence(Notification.EXTRA_BIG_TEXT));
        String sub = str(e.getCharSequence(Notification.EXTRA_SUB_TEXT));

        // Inbox-style notifications keep their content in a list of lines.
        CharSequence[] lines = e.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        if (lines != null && lines.length > 0 && big.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (CharSequence line : lines) {
                if (line == null) continue;
                if (sb.length() > 0) sb.append('\n');
                sb.append(line);
            }
            big = sb.toString();
        }

        // Skip empty group-summary shells; keep everything that carries content.
        if (title.isEmpty() && text.isEmpty() && big.isEmpty() && sub.isEmpty()) return;

        NotificationRecord r = new NotificationRecord();
        r.pkg = pkg;
        r.app = pkg;
        r.title = title;
        r.text = text;
        r.bigText = big;
        r.subText = sub;
        r.category = n.category == null ? "" : n.category;
        r.channel = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && n.getChannelId() != null
                ? n.getChannelId() : "";
        r.postedAt = sbn.getPostTime();
        r.key = sbn.getKey() == null ? "" : sbn.getKey();

        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            r.app = String.valueOf(pm.getApplicationLabel(ai));
            r.isSystem = (ai.flags & (ApplicationInfo.FLAG_SYSTEM
                    | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
        } catch (PackageManager.NameNotFoundException ignored) {
            // keep the package name as the app label
        }

        NotificationStore.get(this).insert(r);
    }

    private static String str(CharSequence cs) {
        return cs == null ? "" : cs.toString();
    }
}
