package com.detailflow.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;

final class UpdateManager {
    interface CheckCallback {
        void onResult(Release release, String message, boolean error);
    }

    static final class Release {
        final String tag;
        final String title;
        final String notes;
        final String apkName;
        final String downloadUrl;
        final String digest;
        final long size;

        Release(String tag, String title, String notes, String apkName, String downloadUrl, String digest, long size) {
            this.tag = tag;
            this.title = title;
            this.notes = notes;
            this.apkName = apkName;
            this.downloadUrl = downloadUrl;
            this.digest = digest;
            this.size = size;
        }
    }

    private static final String PREFS = "detailflow_updates";
    private static final String KEY_REPOSITORY = "github_repository";
    private static final String KEY_LAST_CHECK = "last_check";
    private static final String KEY_DOWNLOAD_ID = "download_id";
    private static final String KEY_DIGEST = "download_digest";
    private static final long CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L;

    private final Activity activity;
    private final SharedPreferences preferences;
    private final DownloadManager downloads;
    private final BroadcastReceiver receiver;
    private boolean receiverRegistered;

    UpdateManager(Activity activity) {
        this.activity = activity;
        preferences = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        downloads = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
        receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                long finishedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L);
                if (finishedId == preferences.getLong(KEY_DOWNLOAD_ID, -1L)) handleCompletedDownload(finishedId, true);
            }
        };
        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= 33) activity.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else activity.registerReceiver(receiver, filter);
        receiverRegistered = true;
    }

    String getRepository() {
        return preferences.getString(KEY_REPOSITORY, activity.getString(R.string.default_update_repository));
    }

    boolean setRepository(String input) {
        String normalized = normalizeRepository(input);
        if (normalized == null) return false;
        preferences.edit().putString(KEY_REPOSITORY, normalized).remove(KEY_LAST_CHECK).apply();
        return true;
    }

    String currentVersion() {
        try { return activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionName; }
        catch (Exception ignored) { return "0.0.0"; }
    }

    void maybeCheckAutomatically() {
        String repository = getRepository();
        long lastCheck = preferences.getLong(KEY_LAST_CHECK, 0L);
        if (repository.isEmpty() || System.currentTimeMillis() - lastCheck < CHECK_INTERVAL_MS) return;
        checkForUpdates((release, message, error) -> {
            if (release != null) activity.runOnUiThread(() -> showUpdateDialog(release));
        });
    }

    void checkForUpdates(CheckCallback callback) {
        String repository = getRepository();
        if (repository.isEmpty()) {
            callback.onResult(null, "Сначала укажите репозиторий GitHub", true);
            return;
        }
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                URL endpoint = new URL("https://api.github.com/repos/" + repository + "/releases/latest");
                connection = (HttpURLConnection) endpoint.openConnection();
                connection.setConnectTimeout(12000);
                connection.setReadTimeout(15000);
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                connection.setRequestProperty("User-Agent", "DetailFlow-Android-Updater");
                int responseCode = connection.getResponseCode();
                if (responseCode == 404) throw new IllegalStateException("Релизы не найдены или репозиторий закрытый");
                if (responseCode == 403) throw new IllegalStateException("GitHub временно ограничил число проверок. Попробуйте позже");
                if (responseCode != 200) throw new IllegalStateException("GitHub вернул ошибку " + responseCode);
                String json = readAll(connection.getInputStream());
                JSONObject root = new JSONObject(json);
                JSONArray assets = root.optJSONArray("assets");
                JSONObject apk = null;
                if (assets != null) for (int i = 0; i < assets.length(); i++) {
                    JSONObject candidate = assets.getJSONObject(i);
                    if (candidate.optString("name").toLowerCase(Locale.ROOT).endsWith(".apk")) { apk = candidate; break; }
                }
                if (apk == null) throw new IllegalStateException("В последнем релизе нет APK-файла");
                Release release = new Release(root.optString("tag_name"), root.optString("name"), root.optString("body"),
                        apk.optString("name"), apk.optString("browser_download_url"), apk.optString("digest"), apk.optLong("size"));
                preferences.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply();
                if (compareVersions(release.tag, currentVersion()) <= 0) {
                    callback.onResult(null, "Установлена актуальная версия " + currentVersion(), false);
                } else {
                    callback.onResult(release, "Доступна версия " + release.tag, false);
                }
            } catch (Exception error) {
                callback.onResult(null, error.getMessage() == null ? "Не удалось проверить обновления" : error.getMessage(), true);
            } finally {
                if (connection != null) connection.disconnect();
            }
        }, "detailflow-update-check").start();
    }

    void download(Release release) {
        try {
            String safeTag = release.tag.replaceAll("[^a-zA-Z0-9._-]", "_");
            String filename = "DetailFlow-" + safeTag + "-" + System.currentTimeMillis() + ".apk";
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(release.downloadUrl));
            request.setTitle("Обновление DetailFlow " + release.tag);
            request.setDescription("Загрузка установочного файла");
            request.setMimeType("application/vnd.android.package-archive");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setAllowedOverRoaming(false);
            request.setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_DOWNLOADS, filename);
            long id = downloads.enqueue(request);
            preferences.edit().putLong(KEY_DOWNLOAD_ID, id).putString(KEY_DIGEST, release.digest).apply();
            Toast.makeText(activity, "Обновление загружается", Toast.LENGTH_SHORT).show();
        } catch (Exception error) {
            Toast.makeText(activity, "Не удалось начать загрузку", Toast.LENGTH_LONG).show();
        }
    }

    void resumePendingInstall() {
        long id = preferences.getLong(KEY_DOWNLOAD_ID, -1L);
        if (id >= 0) handleCompletedDownload(id, false);
    }

    private void handleCompletedDownload(long id, boolean showErrors) {
        DownloadManager.Query query = new DownloadManager.Query().setFilterById(id);
        try (Cursor cursor = downloads.query(query)) {
            if (cursor == null || !cursor.moveToFirst()) return;
            int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            if (status == DownloadManager.STATUS_FAILED) {
                preferences.edit().remove(KEY_DOWNLOAD_ID).remove(KEY_DIGEST).apply();
                if (showErrors) Toast.makeText(activity, "Загрузка обновления не удалась", Toast.LENGTH_LONG).show();
                return;
            }
            if (status != DownloadManager.STATUS_SUCCESSFUL) return;
            String localUri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI));
            if (localUri == null) return;
            File apkFile = new File(Uri.parse(localUri).getPath());
            String validationError = validateApk(apkFile, preferences.getString(KEY_DIGEST, ""));
            if (validationError != null) {
                preferences.edit().remove(KEY_DOWNLOAD_ID).remove(KEY_DIGEST).apply();
                Toast.makeText(activity, validationError, Toast.LENGTH_LONG).show();
                return;
            }
            if (!activity.getPackageManager().canRequestPackageInstalls()) {
                new AlertDialog.Builder(activity).setTitle("Разрешите обновление")
                        .setMessage("Android должен разрешить DetailFlow устанавливать загруженные обновления. Включите разрешение для этого приложения и вернитесь назад.")
                        .setNegativeButton("Позже", null)
                        .setPositiveButton("Открыть настройки", (dialog, which) -> {
                            Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:" + activity.getPackageName()));
                            activity.startActivity(settings);
                        }).show();
                return;
            }
            Uri contentUri = downloads.getUriForDownloadedFile(id);
            if (contentUri == null) return;
            Intent install = new Intent(Intent.ACTION_INSTALL_PACKAGE);
            install.setData(contentUri);
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            preferences.edit().remove(KEY_DOWNLOAD_ID).remove(KEY_DIGEST).apply();
            activity.startActivity(install);
        } catch (Exception error) {
            if (showErrors) Toast.makeText(activity, "Не удалось открыть обновление", Toast.LENGTH_LONG).show();
        }
    }

    private String validateApk(File apk, String expectedDigest) {
        if (!apk.isFile()) return "Загруженный APK не найден";
        try {
            if (expectedDigest != null && expectedDigest.startsWith("sha256:")) {
                String actual = sha256(apk);
                if (!actual.equalsIgnoreCase(expectedDigest.substring(7))) return "Контрольная сумма обновления не совпала";
            }
            PackageManager manager = activity.getPackageManager();
            int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
            PackageInfo archive = manager.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
            PackageInfo installed = manager.getPackageInfo(activity.getPackageName(), flags);
            if (archive == null || !activity.getPackageName().equals(archive.packageName)) return "APK относится к другому приложению";
            long archiveCode = Build.VERSION.SDK_INT >= 28 ? archive.getLongVersionCode() : archive.versionCode;
            long installedCode = Build.VERSION.SDK_INT >= 28 ? installed.getLongVersionCode() : installed.versionCode;
            if (archiveCode <= installedCode) return "Версия APK не новее установленной";
            if (!sameSigner(installed, archive)) return "Подпись обновления не совпадает";
            return null;
        } catch (Exception error) {
            return "Не удалось проверить безопасность APK";
        }
    }

    private boolean sameSigner(PackageInfo first, PackageInfo second) {
        Signature[] a;
        Signature[] b;
        if (Build.VERSION.SDK_INT >= 28) {
            a = first.signingInfo.getApkContentsSigners();
            b = second.signingInfo.getApkContentsSigners();
        } else {
            a = first.signatures;
            b = second.signatures;
        }
        return a != null && b != null && Arrays.equals(a, b);
    }

    private void showUpdateDialog(Release release) {
        String sizeText = release.size > 0 ? String.format(Locale.getDefault(), "%.1f МБ", release.size / 1048576f) : "APK";
        String notes = release.notes == null ? "" : release.notes.trim();
        if (notes.length() > 700) notes = notes.substring(0, 700) + "…";
        String message = "Доступна версия " + release.tag + " • " + sizeText + (notes.isEmpty() ? "" : "\n\n" + notes);
        new AlertDialog.Builder(activity).setTitle("Обновление DetailFlow").setMessage(message)
                .setNegativeButton("Позже", null).setPositiveButton("Скачать", (dialog, which) -> download(release)).show();
    }

    void destroy() {
        if (receiverRegistered) {
            try { activity.unregisterReceiver(receiver); } catch (Exception ignored) { }
            receiverRegistered = false;
        }
    }

    static String normalizeRepository(String value) {
        if (value == null) return null;
        String result = value.trim();
        if (result.startsWith("https://github.com/")) result = result.substring("https://github.com/".length());
        if (result.startsWith("http://github.com/")) result = result.substring("http://github.com/".length());
        if (result.startsWith("github.com/")) result = result.substring("github.com/".length());
        if (result.endsWith(".git")) result = result.substring(0, result.length() - 4);
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        return result.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+") ? result : null;
    }

    static int compareVersions(String left, String right) {
        String[] a = left.replaceFirst("^[vV]", "").split("[-+]")[0].split("\\.");
        String[] b = right.replaceFirst("^[vV]", "").split("[-+]")[0].split("\\.");
        int length = Math.max(a.length, b.length);
        for (int i = 0; i < length; i++) {
            int av = i < a.length ? numberPrefix(a[i]) : 0;
            int bv = i < b.length ? numberPrefix(b[i]) : 0;
            if (av != bv) return Integer.compare(av, bv);
        }
        return 0;
    }

    private static int numberPrefix(String value) {
        String digits = value.replaceFirst("^(\\d+).*$", "$1");
        try { return Integer.parseInt(digits); } catch (Exception ignored) { return 0; }
    }

    private static String readAll(InputStream source) throws Exception {
        try (InputStream input = new BufferedInputStream(source)) {
            byte[] buffer = new byte[8192];
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            int count;
            while ((count = input.read(buffer)) != -1) result.write(buffer, 0, count);
            return result.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        StringBuilder result = new StringBuilder();
        for (byte value : digest.digest()) result.append(String.format(Locale.ROOT, "%02x", value));
        return result.toString();
    }
}
