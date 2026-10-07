package com.halo.decomp;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.util.regex.Pattern;

/**
 * Keeps Android-generated state from going stale across launches and updates.
 *
 * The external files root contains user-owned game data, so it is never
 * cleared wholesale. Only disposable caches and transactional temp files are
 * removed here.
 */
final class StorageMaintenance {
    private static final int DATA_SCHEMA = 4;
    private static final String PREFERENCES = "halo_storage_maintenance";
    private static final String KEY_VERSION = "version_code";
    private static final String KEY_SCHEMA = "data_schema";

    /* Halo's persistent precache files: z:\\cache%03d.map
       (source/cache/cache_files_windows.c). They accelerate later loads, so
       keep them unless DATA_SCHEMA changes. They can be rebuilt from maps/,
       unlike z:/saved which contains real save data. */
    private static final Pattern MAP_CACHE = Pattern.compile("cache\\d{3}\\.map", Pattern.CASE_INSENSITIVE);

    private StorageMaintenance() {
    }

    /** Run before the launcher inspects data or starts native Halo. */
    static void onLaunch(Context context) {
        SharedPreferences preferences =
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        int previousVersion = preferences.getInt(KEY_VERSION, -1);
        int previousSchema = preferences.getInt(KEY_SCHEMA, -1);
        int currentVersion = BuildConfig.VERSION_CODE;
        File external = context.getExternalFilesDir(null);

        /*
         * Android's ordinary cache is never authoritative. Invalidate it on
         * every APK/schema change.
         */
        if (previousVersion != currentVersion || previousSchema != DATA_SCHEMA) {
            clearDirectory(context.getCacheDir());
            android.util.Log.i("halo", "storage: cache schema "
                + previousSchema + " -> " + DATA_SCHEMA + ", app "
                + previousVersion + " -> " + currentVersion);
        }

        /*
         * Halo's z:/cache###.map files are useful persistent precaches and
         * should survive ordinary launches and APK updates. Invalidate them
         * only when this cache schema changes: that means the port changed in
         * a way which may make previously generated cache maps unsafe. Once
         * rebuilt for the current schema they are retained and reused.
         */
        if (previousSchema != DATA_SCHEMA) {
            clearHaloMapCache(external);
            android.util.Log.i("halo", "storage: rebuilt Halo map cache for schema " + DATA_SCHEMA);
        }

        /*
         * Interrupted transactional writes are never valid persistent state.
         */
        cleanTransientExternalState(external);

        preferences.edit()
            .putInt(KEY_VERSION, currentVersion)
            .putInt(KEY_SCHEMA, DATA_SCHEMA)
            .commit();
    }

    private static void clearHaloMapCache(File external) {
        if (external == null)
            return;

        File z = new File(new File(external, "save"), "z");
        File[] children = z.listFiles();
        if (children == null)
            return;

        int removed = 0;
        for (File child : children) {
            if (child.isFile() && MAP_CACHE.matcher(child.getName()).matches()) {
                if (child.delete())
                    removed++;
                else
                    android.util.Log.w("halo", "storage: could not remove stale map cache " + child);
            }
        }
        if (removed != 0)
            android.util.Log.i("halo", "storage: removed " + removed + " stale map cache file(s)");
    }

    private static void cleanTransientExternalState(File root) {
        if (root == null)
            return;

        deleteRecursively(new File(root, "maps.partial"));
        deleteRecursively(new File(root, "join_link.txt.tmp"));
        deleteRecursively(new File(root, "hardware_id.txt.tmp"));

        /*
         * The updater lives in getCacheDir(), but remove any abandoned update
         * directory there through the normal cache invalidation path rather
         * than touching user data here.
         */
    }

    /** Delete a cache directory's contents, but keep the directory itself. */
    private static void clearDirectory(File directory) {
        if (directory == null)
            return;
        File[] children = directory.listFiles();
        if (children == null)
            return;

        for (File child : children)
            deleteRecursively(child);
    }

    private static void deleteRecursively(File path) {
        if (path == null || !path.exists())
            return;
        if (path.isDirectory()) {
            File[] children = path.listFiles();
            if (children != null) {
                for (File child : children)
                    deleteRecursively(child);
            }
        }
        if (!path.delete())
            android.util.Log.w("halo", "storage: could not remove stale transient " + path);
    }
}
