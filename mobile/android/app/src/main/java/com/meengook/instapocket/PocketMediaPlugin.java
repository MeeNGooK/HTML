package com.meengook.instapocket;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.webkit.CookieManager;
import android.webkit.WebStorage;
import android.webkit.WebSettings;
import androidx.activity.result.ActivityResult;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;
import org.json.JSONArray;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

@CapacitorPlugin(name = "PocketMedia")
public class PocketMediaPlugin extends Plugin {
    private String sharedText = "";
    @Override public void load() { readShared(getActivity().getIntent()); }
    private void readShared(Intent intent) {
        if (intent != null && Intent.ACTION_SEND.equals(intent.getAction()) && "text/plain".equals(intent.getType())) {
            String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            sharedText = text == null ? "" : text;
            intent.removeExtra(Intent.EXTRA_TEXT);
        }
    }
    @Override protected void handleOnNewIntent(Intent intent) {
        readShared(intent);
        notifyListeners("sharedText", new JSObject());
    }
    @Override protected void handleOnResume() { notifyListeners("resume", new JSObject()); }
    @PluginMethod public void consumeSharedText(PluginCall call) {
        JSObject result = new JSObject(); result.put("text", sharedText); sharedText = ""; call.resolve(result);
    }
    @PluginMethod public void readClipboard(PluginCall call) {
        getActivity().runOnUiThread(() -> {
            ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            String text = "";
            if (clipboard.hasPrimaryClip() && clipboard.getPrimaryClip() != null && clipboard.getPrimaryClip().getItemCount() > 0) {
                CharSequence value = clipboard.getPrimaryClip().getItemAt(0).coerceToText(getContext());
                if (value != null) text = value.toString();
            }
            JSObject result = new JSObject(); result.put("text", text); call.resolve(result);
        });
    }
    @PluginMethod public void openPost(PluginCall call) {
        String url = call.getString("url", "");
        if (!UrlPolicy.isPost(url)) { call.reject("유효한 인스타그램 게시물 링크가 아니에요."); return; }
        Intent intent = new Intent(getContext(), PostBrowserActivity.class);
        intent.putExtra("url", url);
        startActivityForResult(call, intent, "postResult");
    }
    @ActivityCallback private void postResult(PluginCall call, ActivityResult activityResult) {
        if (call == null) return;
        JSObject result = new JSObject();
        Intent data = activityResult.getData();
        if (activityResult.getResultCode() != Activity.RESULT_OK || data == null) {
            result.put("cancelled", true); call.resolve(result); return;
        }
        try {
            result.put("items", new JSArray(data.getStringExtra("items")));
            call.resolve(result);
        } catch (Exception e) { call.reject("미디어 정보를 읽지 못했어요."); }
    }
    private DownloadManager manager() { return (DownloadManager) getContext().getSystemService(Context.DOWNLOAD_SERVICE); }
    @PluginMethod public void download(PluginCall call) {
        String url = call.getString("url", "");
        String type = call.getString("type", "");
        String post = call.getString("postUrl", "");
        if (!UrlPolicy.isMedia(url) || !UrlPolicy.isPost(post) || !("video".equals(type) || "image".equals(type))) {
            call.reject("다운로드할 미디어 주소를 확인해 주세요."); return;
        }
        try {
            String extension = "video".equals(type) ? ".mp4" : ".jpg";
            String path = Uri.parse(url).getPath();
            if ("image".equals(type) && path != null) {
                if (path.toLowerCase(Locale.ROOT).endsWith(".webp")) extension = ".webp";
                else if (path.toLowerCase(Locale.ROOT).endsWith(".png")) extension = ".png";
            }
            String name = "Pocket_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + "_" + UUID.randomUUID().toString().substring(0, 6) + extension;
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setTitle(name).setDescription("Insta Pocket에 저장 중");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "InstaPocket/" + name);
            request.addRequestHeader("User-Agent", WebSettings.getDefaultUserAgent(getContext()));
            request.addRequestHeader("Referer", "https://www.instagram.com/");
            // CDN links are signed. Never attach the Instagram login cookie to a file request.
            long id = manager().enqueue(request);
            getContext().getSharedPreferences("downloads", Context.MODE_PRIVATE).edit().putBoolean(Long.toString(id), true).apply();
            JSObject result = new JSObject(); result.put("id", Long.toString(id)); result.put("name", name); call.resolve(result);
        } catch (Exception e) { call.reject("저장을 시작하지 못했어요. 저장 공간과 다운로드 관리자 설정을 확인해 주세요."); }
    }
    private boolean owned(String id) { return getContext().getSharedPreferences("downloads", Context.MODE_PRIVATE).getBoolean(id, false); }
    @PluginMethod public void getDownloads(PluginCall call) {
        JSArray ids = call.getArray("ids", new JSArray());
        JSArray downloads = new JSArray();
        for (int i = 0; i < Math.min(ids.length(), 200); i++) {
            String id = ids.optString(i);
            if (!owned(id)) continue;
            JSObject row = new JSObject(); row.put("id", id);
            try (Cursor cursor = manager().query(new DownloadManager.Query().setFilterById(Long.parseLong(id)))) {
                if (cursor == null || !cursor.moveToFirst()) { row.put("status", "missing"); downloads.put(row); continue; }
                int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                String state = "pending";
                if (status == DownloadManager.STATUS_RUNNING) state = "running";
                else if (status == DownloadManager.STATUS_SUCCESSFUL) state = "complete";
                else if (status == DownloadManager.STATUS_PAUSED) state = "paused";
                else if (status == DownloadManager.STATUS_FAILED) state = "failed";
                row.put("status", state);
                row.put("bytes", cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)));
                row.put("total", cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)));
                if (status == DownloadManager.STATUS_FAILED) {
                    int reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON));
                    row.put("reason", reason == DownloadManager.ERROR_INSUFFICIENT_SPACE ? "저장 공간이 부족해요" : "저장 실패 · 게시물을 다시 열어 주세요 (" + reason + ")");
                }
            } catch (Exception e) { row.put("status", "missing"); }
            downloads.put(row);
        }
        JSObject result = new JSObject(); result.put("downloads", downloads); call.resolve(result);
    }
    @PluginMethod public void openDownload(PluginCall call) {
        String value = call.getString("id", "");
        if (!owned(value)) { call.reject("이 앱에서 저장한 파일이 아니에요."); return; }
        try {
            long id = Long.parseLong(value);
            Uri uri = manager().getUriForDownloadedFile(id);
            if (uri == null) { call.reject("파일이 없거나 아직 저장 중이에요."); return; }
            String mime = manager().getMimeTypeForDownloadedFile(id);
            Intent intent = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime == null ? "*/*" : mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            getActivity().startActivity(intent); call.resolve();
        } catch (Exception e) { call.reject("파일이 삭제되었거나 열 수 있는 앱이 없어요. 파일 앱의 Download/InstaPocket에서 확인해 주세요."); }
    }
    @PluginMethod public void clearSession(PluginCall call) {
        getActivity().runOnUiThread(() -> {
            CookieManager.getInstance().removeAllCookies(value -> {
                CookieManager.getInstance().flush();
                WebStorage.getInstance().deleteAllData();
                call.resolve();
            });
        });
    }
}
