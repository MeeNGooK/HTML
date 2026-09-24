package com.meengook.instapocket;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Cookie-isolated, anonymous public-post extraction. No WebView, account or external service. */
public final class PublicPostResolver {
    private static final String BASE = "https://www.instagram.com";
    private static final String USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36";
    private static final String QUERY_NAME = "PolarisLoggedOutDesktopWWWPostRootContentQuery";
    // Instagram's logged-out public query; isolated here because its schema can change.
    private static final String QUERY_DOCUMENT = "27130156389949648";
    private final Map<String,String> anonymousCookies = new LinkedHashMap<>();
    private final long deadline = System.currentTimeMillis() + 45000;
    private String token = "";
    private boolean loginGated;
    private boolean incomplete;

    public static final class Result {
        public final JSONArray items;
        Result(JSONArray items) { this.items = items; }
    }
    public static final class ResolveException extends Exception {
        public final String code;
        ResolveException(String code, String message) { super(message); this.code = code; }
    }
    private static final class Response {
        final String body; final int status;
        Response(String body, int status) { this.body = body; this.status = status; }
    }
    public Result resolve(String post) throws Exception {
        if (!UrlPolicy.isPost(post)) throw new ResolveException("INVALID_URL", "인스타그램 게시물 링크를 확인해 주세요.");
        String shortcode = new URI(post).getPath().split("/")[2];
        Response page = request(post, null, post);
        Result parsed = parse(page.body, shortcode); if (parsed != null) return parsed;
        findToken(page.body);
        if (token.isEmpty() && !loginGated) findToken(request(BASE + "/", null, post).body);
        if (!token.isEmpty() && !loginGated) {
            Map<String,String> data = new LinkedHashMap<>();
            data.put("lsd", token); data.put("fb_api_caller_class", "RelayModern");
            data.put("fb_api_req_friendly_name", QUERY_NAME); data.put("server_timestamps", "true");
            JSONObject variables = new JSONObject(); variables.put("media_id", PublicMediaParser.mediaId(shortcode));
            data.put("variables", variables.toString()); data.put("doc_id", QUERY_DOCUMENT);
            Response json = request(BASE + "/api/graphql", form(data), post);
            parsed = parse(json.body, shortcode); if (parsed != null) return parsed;
        }
        // Embeds are a separate anonymous public representation; no account/session is supplied.
        if (!loginGated) {
            Response embed = request(BASE + "/p/" + shortcode + "/embed/captioned/", null, post);
            parsed = parse(embed.body, shortcode); if (parsed != null) return parsed;
        }
        if (loginGated) throw new ResolveException("PUBLIC_ACCESS_RESTRICTED", "인스타그램이 이 게시물의 비로그인 접근을 제한했어요. 공개 게시물인지 확인하거나 잠시 후 다시 시도해 주세요.");
        if (incomplete) throw new ResolveException("NO_DIRECT_MEDIA", "이 게시물의 저장 가능한 원본을 모두 찾지 못했어요. 썸네일만 대신 저장하지는 않습니다.");
        throw new ResolveException("EXTRACTION_FAILED", "게시물의 다운로드 주소를 찾지 못했어요. 삭제·공개 접근 제한 또는 인스타그램 형식 변경일 수 있어요.");
    }
    private Result parse(String body, String shortcode) throws Exception {
        PublicMediaParser parser = new PublicMediaParser(shortcode);
        if (body.trim().startsWith("<")) parser.acceptHtml(body); else parser.acceptJson(body);
        if (parser.isPrivate()) throw new ResolveException("PRIVATE_POST", "비공개 계정의 게시물은 지원하지 않아요.");
        incomplete |= parser.isIncomplete();
        JSONArray items = parser.result(); return items.length() > 0 ? new Result(items) : null;
    }
    private void findToken(String html) {
        Matcher lsd = Pattern.compile("\\[\"LSD\",\\[\\],\\{\"token\":\"([^\"]+)\"").matcher(html);
        if (lsd.find()) token = lsd.group(1);
    }
    private String form(Map<String,String> data) throws Exception {
        StringBuilder result = new StringBuilder();
        for (Map.Entry<String,String> entry:data.entrySet()) {
            if (result.length()>0) result.append('&');
            result.append(URLEncoder.encode(entry.getKey(), "UTF-8")).append('=').append(URLEncoder.encode(entry.getValue(), "UTF-8"));
        }
        return result.toString();
    }
    private Response request(String url, String payload, String post) throws Exception {
        for (int redirect=0;redirect<4;redirect++) {
            if (!UrlPolicy.isInstagram(url)) throw new ResolveException("REDIRECT_BLOCKED", "인스타그램 이외의 주소로 이동하는 링크는 지원하지 않아요.");
            long remaining = deadline - System.currentTimeMillis();
            if (Thread.currentThread().isInterrupted() || remaining <= 0) throw new ResolveException("TIMEOUT", "응답이 늦어지고 있어요. 연결을 확인하고 다시 시도해 주세요.");
            HttpURLConnection connection = (HttpURLConnection)new URL(url).openConnection();
            try {
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout((int)Math.min(remaining,10000)); connection.setReadTimeout((int)Math.min(remaining,10000));
                connection.setRequestProperty("User-Agent",USER_AGENT);
                connection.setRequestProperty("Accept","*/*");
                connection.setRequestProperty("Accept-Language","ko-KR,ko;q=0.9,en;q=0.8");
                connection.setRequestProperty("Referer",post);
                // Explicitly replace any platform cookie header; only fresh anonymous cookies are eligible.
                StringBuilder cookie = new StringBuilder();
                for(Map.Entry<String,String> entry:anonymousCookies.entrySet()) { if(cookie.length()>0)cookie.append("; "); cookie.append(entry.getKey()).append('=').append(entry.getValue()); }
                connection.setRequestProperty("Cookie",cookie.toString());
                if (payload != null) {
                    connection.setRequestMethod("POST"); connection.setDoOutput(true);
                    connection.setRequestProperty("Content-Type","application/x-www-form-urlencoded; charset=UTF-8");
                    connection.setRequestProperty("Origin",BASE);
                    connection.setRequestProperty("X-IG-App-ID","936619743392459");
                    connection.setRequestProperty("X-FB-Friendly-Name",QUERY_NAME);
                    connection.setRequestProperty("X-FB-LSD",token);
                    connection.setRequestProperty("X-Requested-With","XMLHttpRequest");
                    if(anonymousCookies.containsKey("csrftoken")) connection.setRequestProperty("X-CSRFToken",anonymousCookies.get("csrftoken"));
                    try(OutputStream out=connection.getOutputStream()) { out.write(payload.getBytes(StandardCharsets.UTF_8)); }
                }
                int status=connection.getResponseCode();
                for(Map.Entry<String,List<String>> entry:connection.getHeaderFields().entrySet()) {
                    if (entry.getKey()==null || !entry.getKey().equalsIgnoreCase("Set-Cookie")) continue;
                    for(String value:entry.getValue()) {
                        String pair=value.split(";",2)[0]; int equal=pair.indexOf('='); if(equal<=0)continue;
                        String name=pair.substring(0,equal);
                        if(name.equals("csrftoken") || name.equals("mid") || name.equals("ig_did") || name.equals("datr")) anonymousCookies.put(name,pair.substring(equal+1));
                    }
                }
                if(status==429) throw new ResolveException("RATE_LIMITED", "인스타그램의 요청 제한에 걸렸어요. 잠시 후 다시 시도해 주세요.");
                if(status>=300 && status<400) {
                    String target=connection.getHeaderField("Location");
                    if(target==null)break;
                    url=new URL(new URL(url),target).toString();
                    if(new URI(url).getPath().startsWith("/accounts/") || new URI(url).getPath().startsWith("/challenge/")) { loginGated=true; return new Response("",status); }
                    if(status==303 || status==302 || status==301)payload=null;
                    continue;
                }
                if(status==401 || status==403) { loginGated=true; return new Response("",status); }
                if(status==404 || status==410) throw new ResolveException("NOT_FOUND", "게시물을 찾을 수 없어요. 링크가 정확한지 확인해 주세요.");
                if(status>=500) throw new ResolveException("SERVER_ERROR", "인스타그램 서버가 응답하지 않아요. 잠시 후 다시 시도해 주세요.");
                InputStream input=status>=400 ? connection.getErrorStream() : connection.getInputStream();
                if(input==null)return new Response("",status);
                try(InputStream in=input; ByteArrayOutputStream output=new ByteArrayOutputStream()) {
                    byte[] bytes=new byte[8192]; int count;
                    while((count=in.read(bytes))!=-1) {
                        if(output.size()+count>8*1024*1024)throw new ResolveException("RESPONSE_TOO_LARGE", "게시물 응답이 너무 커서 처리하지 못했어요.");
                        if(System.currentTimeMillis()>deadline)throw new ResolveException("TIMEOUT", "응답 시간이 초과됐어요. 다시 시도해 주세요.");
                        output.write(bytes,0,count);
                    }
                    return new Response(new String(output.toByteArray(),StandardCharsets.UTF_8),status);
                }
            } catch(java.net.SocketTimeoutException e) { throw new ResolveException("TIMEOUT", "연결 시간이 초과됐어요. 네트워크를 확인해 주세요."); }
            catch(java.net.UnknownHostException e) { throw new ResolveException("OFFLINE", "인터넷 연결을 확인해 주세요."); }
            finally { connection.disconnect(); }
        }
        throw new ResolveException("REDIRECT_LIMIT", "게시물 주소를 확인할 수 없어요. 원본 게시물 링크를 사용해 주세요.");
    }
}
