package com.example.notifsaver;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/** Plain SQLite storage for saved notifications (no third-party libraries). */
public class NotificationStore extends SQLiteOpenHelper {

    private static final String DB_NAME = "notifications.db";
    private static final int DB_VERSION = 1;
    private static final long DUPLICATE_WINDOW_MS = 3000;

    private static NotificationStore instance;

    public static synchronized NotificationStore get(Context context) {
        if (instance == null) {
            instance = new NotificationStore(context.getApplicationContext());
        }
        return instance;
    }

    private NotificationStore(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE notifications ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "pkg TEXT, app TEXT, title TEXT, text TEXT, big_text TEXT,"
                + "sub_text TEXT, category TEXT, channel TEXT,"
                + "posted_at INTEGER, saved_at INTEGER,"
                + "is_system INTEGER DEFAULT 0, sbn_key TEXT)");
        db.execSQL("CREATE INDEX idx_posted ON notifications(posted_at)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS notifications");
        onCreate(db);
    }

    /** Saves a notification unless an identical one was saved moments ago. Returns true if stored. */
    public synchronized boolean insert(NotificationRecord r) {
        SQLiteDatabase db = getWritableDatabase();

        Cursor c = db.rawQuery(
                "SELECT COUNT(*) FROM notifications WHERE pkg=? AND title=? AND text=? AND big_text=? AND saved_at>?",
                new String[]{r.pkg, r.title, r.text, r.bigText,
                        String.valueOf(System.currentTimeMillis() - DUPLICATE_WINDOW_MS)});
        boolean duplicate = false;
        try {
            if (c.moveToFirst()) duplicate = c.getInt(0) > 0;
        } finally {
            c.close();
        }
        if (duplicate) return false;

        ContentValues v = new ContentValues();
        v.put("pkg", r.pkg);
        v.put("app", r.app);
        v.put("title", r.title);
        v.put("text", r.text);
        v.put("big_text", r.bigText);
        v.put("sub_text", r.subText);
        v.put("category", r.category);
        v.put("channel", r.channel);
        v.put("posted_at", r.postedAt);
        v.put("saved_at", System.currentTimeMillis());
        v.put("is_system", r.isSystem ? 1 : 0);
        v.put("sbn_key", r.key);
        return db.insert("notifications", null, v) != -1;
    }

    /** Newest first. An empty filter returns everything. */
    public synchronized List<NotificationRecord> query(String filter) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c;
        if (filter == null || filter.trim().isEmpty()) {
            c = db.rawQuery("SELECT * FROM notifications ORDER BY posted_at DESC, id DESC LIMIT 5000", null);
        } else {
            String like = "%" + filter.trim() + "%";
            c = db.rawQuery("SELECT * FROM notifications WHERE app LIKE ? OR title LIKE ? OR text LIKE ? "
                            + "OR big_text LIKE ? OR pkg LIKE ? ORDER BY posted_at DESC, id DESC LIMIT 5000",
                    new String[]{like, like, like, like, like});
        }
        List<NotificationRecord> out = new ArrayList<>();
        try {
            while (c.moveToNext()) {
                NotificationRecord r = new NotificationRecord();
                r.id = c.getLong(c.getColumnIndexOrThrow("id"));
                r.pkg = c.getString(c.getColumnIndexOrThrow("pkg"));
                r.app = c.getString(c.getColumnIndexOrThrow("app"));
                r.title = c.getString(c.getColumnIndexOrThrow("title"));
                r.text = c.getString(c.getColumnIndexOrThrow("text"));
                r.bigText = c.getString(c.getColumnIndexOrThrow("big_text"));
                r.subText = c.getString(c.getColumnIndexOrThrow("sub_text"));
                r.category = c.getString(c.getColumnIndexOrThrow("category"));
                r.channel = c.getString(c.getColumnIndexOrThrow("channel"));
                r.postedAt = c.getLong(c.getColumnIndexOrThrow("posted_at"));
                r.savedAt = c.getLong(c.getColumnIndexOrThrow("saved_at"));
                r.isSystem = c.getInt(c.getColumnIndexOrThrow("is_system")) == 1;
                r.key = c.getString(c.getColumnIndexOrThrow("sbn_key"));
                out.add(r);
            }
        } finally {
            c.close();
        }
        return out;
    }

    public synchronized int count() {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM notifications", null);
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    public synchronized void delete(long id) {
        getWritableDatabase().delete("notifications", "id=?", new String[]{String.valueOf(id)});
    }

    public synchronized void clear() {
        getWritableDatabase().delete("notifications", null, null);
    }
}
