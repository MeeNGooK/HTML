package com.meengook.instapocket;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;

public class PostBrowserActivity extends Activity {
    private WebView web;
    private TextView status;
    private Button done;
    private String post;
    private String collector;
    private boolean collecting;
    private final LinkedHashMap<String, JSONObject> found = new LinkedHashMap<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable scan = new Runnable() {
        @Override public void run() { collect(false); handler.postDelayed(this, 2500); }
    };
    private int dp(int n) { return (int) (getResources().getDisplayMetrics().density * n); }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        post = getIntent().getStringExtra("url");
        if (!UrlPolicy.isPost(post)) { finish(); return; }
        try (InputStream stream = getAssets().open("collect-media.js")) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192]; int count;
            while ((count = stream.read(chunk)) != -1) buffer.write(chunk, 0, count);
            collector = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) { Toast.makeText(this, "미디어 수집기를 불러오지 못했어요.", Toast.LENGTH_LONG).show(); finish(); return; }
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.rgb(248,247,243));
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets system = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(system.left, system.top, system.right, system.bottom); return insets;
        });
        LinearLayout toolbar = new LinearLayout(this); toolbar.setPadding(dp(8), dp(4), dp(8), dp(4));
        Button close = new Button(this); close.setText("닫기"); close.setOnClickListener(v -> finish()); toolbar.addView(close);
        TextView title = new TextView(this); title.setText("  Instagram 게시물"); title.setTextColor(Color.rgb(35,92,73)); title.setTextSize(16); title.setGravity(android.view.Gravity.CENTER_VERTICAL); toolbar.addView(title, new LinearLayout.LayoutParams(0, dp(52), 1));
        Button reload = new Button(this); reload.setText("새로고침"); reload.setOnClickListener(v -> web.loadUrl(post)); toolbar.addView(reload); root.addView(toolbar);
        status = new TextView(this); status.setPadding(dp(18),dp(10),dp(18),dp(10)); status.setTextSize(12); status.setTextColor(Color.rgb(86,104,82)); status.setText("필요하면 로그인하세요. 사진은 넘기고 동영상은 재생해 주세요."); root.addView(status);
        web = new WebView(this);
        WebSettings settings = web.getSettings(); settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false); settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportMultipleWindows(false); settings.setMediaPlaybackRequiresUserGesture(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                boolean blocked = !UrlPolicy.isInstagram(request.getUrl().toString());
                if (blocked && request.isForMainFrame()) Toast.makeText(PostBrowserActivity.this, "인스타그램 페이지 안에서 계속해 주세요.", Toast.LENGTH_SHORT).show();
                return blocked;
            }
            @Override public void onPageFinished(WebView view, String url) { collect(false); }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) status.setText("페이지를 불러오지 못했어요. 연결 확인 후 새로고침해 주세요.");
            }
        });
        root.addView(web, new LinearLayout.LayoutParams(-1,0,1));
        done = new Button(this); done.setText("미디어 확인하기"); done.setTextColor(Color.WHITE); done.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(35,92,73))); done.setOnClickListener(v -> collect(true));
        LinearLayout.LayoutParams bottom = new LinearLayout.LayoutParams(-1, dp(58)); bottom.setMargins(dp(12), dp(6), dp(12), dp(8)); root.addView(done, bottom);
        setContentView(root);
        if (state != null) {
            try { JSONArray previous = new JSONArray(state.getString("items", "[]")); for (int i=0;i<previous.length();i++) add(previous.getJSONObject(i)); } catch(Exception ignored) {}
            if (web.restoreState(state) == null) web.loadUrl(post);
        } else web.loadUrl(post);
    }
    private void add(JSONObject item) {
        String url = item.optString("url");
        if (!UrlPolicy.isMedia(url) || !("image".equals(item.optString("type")) || "video".equals(item.optString("type")))) return;
        if (found.size() < 100) found.put(android.net.Uri.parse(url).getPath(), item);
    }
    private boolean onTargetPost() {
        String current = web.getUrl();
        if (!UrlPolicy.isPost(current)) return false;
        String[] target = android.net.Uri.parse(post).getPath().split("/");
        String[] actual = android.net.Uri.parse(current).getPath().split("/");
        return target.length > 2 && actual.length > 2 && target[2].equals(actual[2]);
    }
    private void collect(boolean finishAfter) {
        if (web == null || isFinishing() || collecting) return;
        if (!onTargetPost()) {
            if (finishAfter && !found.isEmpty()) finishWithItems();
            else if (finishAfter) Toast.makeText(this, "로그인 후 새로고침을 눌러 원래 게시물을 열어 주세요.", Toast.LENGTH_LONG).show();
            return;
        }
        collecting = true;
        web.evaluateJavascript(collector, raw -> {
            collecting = false;
            if (isFinishing()) return;
            try {
                Object value = new JSONTokener(raw).nextValue();
                if (value instanceof String) {
                    JSONObject payload = new JSONObject((String) value);
                    JSONArray items = payload.optJSONArray("items");
                    if (items != null) for (int i=0;i<items.length();i++) add(items.getJSONObject(i));
                    if (found.isEmpty()) status.setText(payload.optBoolean("streaming") ? "스트리밍 영상은 저장 가능한 원본을 아직 찾지 못했어요. 재생 후 다시 확인해 주세요." : "사진을 넘기거나 영상을 재생하세요. 찾은 항목은 자동으로 모아집니다.");
                    else status.setText(found.size() + "개 찾았어요 · 여러 장은 옆으로 넘기면 추가돼요.");
                }
            } catch (Exception ignored) { status.setText("미디어 정보를 읽지 못했어요. 새로고침 후 다시 시도해 주세요."); }
            done.setText(found.isEmpty() ? "미디어 다시 확인하기" : found.size() + "개 미디어 선택하러 가기");
            if (finishAfter) {
                if (found.isEmpty()) Toast.makeText(this, "저장할 미디어가 없어요. 게시물 로드 또는 로그인 후 다시 확인해 주세요.", Toast.LENGTH_LONG).show();
                else finishWithItems();
            }
        });
    }
    private void finishWithItems() {
        JSONArray items = new JSONArray(); for (JSONObject item : found.values()) items.put(item);
        setResult(RESULT_OK, new Intent().putExtra("items", items.toString())); finish();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state); web.saveState(state);
        JSONArray items = new JSONArray(); for (JSONObject item : found.values()) items.put(item); state.putString("items", items.toString());
    }
    @Override protected void onResume() { super.onResume(); if(web != null) { web.onResume(); handler.postDelayed(scan,1500); } }
    @Override protected void onPause() { handler.removeCallbacks(scan); if(web != null) web.onPause(); CookieManager.getInstance().flush(); super.onPause(); }
    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null); if(web != null) { web.stopLoading(); web.destroy(); web=null; } super.onDestroy(); }
}
