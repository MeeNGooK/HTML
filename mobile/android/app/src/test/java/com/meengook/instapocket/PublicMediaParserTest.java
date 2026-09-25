package com.meengook.instapocket;
import org.json.JSONArray;
import org.junit.Test;
import static org.junit.Assert.*;

public class PublicMediaParserTest {
    private static final String CDN = "https://scontent.cdninstagram.com/";
    @Test public void parsesAnonymousQueryWithoutAccountOrInteraction() {
        PublicMediaParser parser = new PublicMediaParser("ABC");
        parser.acceptJson("{\"data\":{\"xig_polaris_media\":{\"if_not_gated_logged_out\":{\"media_type\":2,\"video_versions\":[{\"url\":\""+CDN+"video.mp4\",\"width\":1080,\"height\":1920}]}}}}");
        assertEquals(1,parser.result().length());
        assertEquals("video",parser.result().optJSONObject(0).optString("type"));
    }
    @Test public void collectsEveryCarouselItemAtBestAvailableResolution() {
        PublicMediaParser parser = new PublicMediaParser("ABC");
        parser.acceptJson("{\"code\":\"ABC\",\"carousel_media_count\":2,\"carousel_media\":[{\"media_type\":1,\"image_versions2\":{\"candidates\":[{\"url\":\""+CDN+"small.jpg\",\"width\":100,\"height\":100},{\"url\":\""+CDN+"large.jpg\",\"width\":1080,\"height\":1080}]}},{\"media_type\":2,\"video_url\":\""+CDN+"video.mp4\"}]}");
        JSONArray items=parser.result(); assertEquals(2,items.length());
        assertEquals(CDN+"large.jpg",items.optJSONObject(0).optString("url"));
        assertEquals("video",items.optJSONObject(1).optString("type"));
    }
    @Test public void readsEmbeddedJsonAndRejectsRecommendationPosts() {
        PublicMediaParser parser=new PublicMediaParser("ABC");
        parser.acceptHtml("<script type='application/json'>{\"data\":[{\"code\":\"OTHER\",\"display_url\":\""+CDN+"wrong.jpg\"},{\"code\":\"ABC\",\"display_url\":\""+CDN+"right.jpg\"}]}</script>");
        assertEquals(1,parser.result().length());
        assertEquals(CDN+"right.jpg",parser.result().optJSONObject(0).optString("url"));
    }
    @Test public void neverSavesVideoPosterInsteadOfVideo() {
        PublicMediaParser parser=new PublicMediaParser("ABC");
        parser.acceptJson("{\"code\":\"ABC\",\"is_video\":true,\"display_url\":\""+CDN+"poster.jpg\"}");
        assertTrue(parser.isIncomplete()); assertEquals(0,parser.result().length());
        PublicMediaParser meta=new PublicMediaParser("ABC");
        meta.acceptHtml("<meta property='og:image' content='"+CDN+"poster.jpg'>");
        assertEquals(0,meta.result().length());
    }
    @Test public void reportsIncompleteCarouselInsteadOfSilentPartialSuccess() {
        PublicMediaParser parser=new PublicMediaParser("ABC");
        parser.acceptJson("{\"code\":\"ABC\",\"carousel_media_count\":3,\"carousel_media\":[{\"display_url\":\""+CDN+"one.jpg\"}]}");
        assertTrue(parser.isIncomplete()); assertEquals(0,parser.result().length());
    }
    @Test public void rejectsPrivatePostsAndUntrustedMediaHosts() {
        PublicMediaParser parser=new PublicMediaParser("ABC");
        parser.acceptJson("{\"code\":\"ABC\",\"owner\":{\"is_private\":true},\"display_url\":\""+CDN+"private.jpg\"}");
        assertTrue(parser.isPrivate()); assertEquals(0,parser.result().length());
        PublicMediaParser evil=new PublicMediaParser("ABC");
        evil.acceptJson("{\"code\":\"ABC\",\"video_url\":\"https://evil.test/a.mp4\"}");
        assertEquals(0,evil.result().length());
    }
    @Test public void parsesLegacySidecarAndEscapedVideoMeta() {
        PublicMediaParser parser=new PublicMediaParser("ABC");
        parser.acceptHtml("<script>window._sharedData = {\"shortcode\":\"ABC\",\"edge_sidecar_to_children\":{\"edges\":[{\"node\":{\"display_url\":\""+CDN+"a.jpg\"}}]}};</script>");
        assertEquals(1,parser.result().length());
        PublicMediaParser meta=new PublicMediaParser("ABC");
        meta.acceptHtml("<meta content='"+CDN+"v.mp4?a=1&amp;b=2' property='og:video'>");
        assertEquals(CDN+"v.mp4?a=1&b=2",meta.result().optJSONObject(0).optString("url"));
    }
    @Test public void safelyHandlesMalformedAndEmptyResponses() {
        PublicMediaParser parser=new PublicMediaParser("ABC");
        parser.acceptJson("{broken"); parser.acceptHtml("<html>Login required</html>");
        assertEquals(0,parser.result().length());
        assertEquals("64",PublicMediaParser.mediaId("BA"));
        assertEquals("A&B",PublicMediaParser.unescape("A&#38;B"));
    }
    @Test public void parsesJsonWrappedInsideEmbedPayloadString() {
        PublicMediaParser parser=new PublicMediaParser("ABC");
        parser.acceptJson("{\"gql_data\":\"{\\\"shortcode_media\\\":{\\\"shortcode\\\":\\\"ABC\\\",\\\"is_video\\\":true,\\\"video_url\\\":\\\""+CDN+"wrapped.mp4\\\"}}\"}");
        assertEquals(1,parser.result().length());
        assertEquals(CDN+"wrapped.mp4",parser.result().optJSONObject(0).optString("url"));
    }
    @Test public void refusesCopyrightBlockedMediaEvenWhenPosterExists() {
        PublicMediaParser parser=new PublicMediaParser("ABC");
        parser.acceptJson("{\"shortcode\":\"ABC\",\"copyright_blocked\":true,\"display_url\":\""+CDN+"poster.jpg\"}");
        assertTrue(parser.isIncomplete());
        assertEquals(0,parser.result().length());
    }
    @Test public void prioritizesRealEmbedContextJsonOverMetadataOnlyContext() {
        PublicMediaParser parser=new PublicMediaParser("DXeh-kYiIge");
        String inner="{\"context\":{\"type\":\"GraphVideo\",\"shortcode\":\"DXeh-kYiIge\",\"copyright_blocked\":false},"
                +"\"gql_data\":{\"shortcode_media\":{\"__typename\":\"GraphVideo\",\"shortcode\":\"DXeh-kYiIge\","
                +"\"is_video\":true,\"display_url\":\""+CDN+"poster.jpg\",\"video_url\":\""+CDN+"ronaldo.mp4\"}}}";
        String bootstrap=new org.json.JSONObject().put("large_unrelated_bootstrap",new org.json.JSONArray())
                .put("contextJSON",inner).toString();
        parser.acceptHtml("<script type='application/json'>"+bootstrap+"</script>");
        assertFalse(parser.isIncomplete());
        assertEquals(1,parser.result().length());
        assertEquals(CDN+"ronaldo.mp4",parser.result().optJSONObject(0).optString("url"));
    }
    @Test public void metadataOnlyMatchingNodeDoesNotPoisonLaterMedia() {
        PublicMediaParser parser=new PublicMediaParser("ABC");
        parser.acceptJson("[{\"shortcode\":\"ABC\",\"type\":\"GraphVideo\"},{\"shortcode\":\"ABC\",\"is_video\":true,\"video_url\":\""+CDN+"ok.mp4\"}]");
        assertFalse(parser.isIncomplete());
        assertEquals(1,parser.result().length());
    }
    @Test public void parsesCurrentServerJsEmbedWrapperWithoutExecutingJavascript() {
        PublicMediaParser parser=new PublicMediaParser("DXeh-kYiIge");
        String inner="{\"gql_data\":{\"shortcode_media\":{\"shortcode\":\"DXeh-kYiIge\",\"is_video\":true,"
                +"\"video_url\":\""+CDN+"verified.mp4?a=close);still-json\"}}}";
        String bootstrap=new org.json.JSONObject().put("contextJSON",inner).toString();
        parser.acceptHtml("<script nonce='test'>requireLazy([],function(){var s={};s.handle("+bootstrap+");cleanup();});</script>");
        assertEquals(1,parser.result().length());
        assertEquals(CDN+"verified.mp4?a=close);still-json",parser.result().optJSONObject(0).optString("url"));
    }
    @Test public void balancedJsonExtractorRejectsTruncatedWrapper() {
        assertEquals("{\"text\":\"}); still inside\",\"nested\":{\"ok\":true}}",
                PublicMediaParser.extractJsonObject("prefix({\"text\":\"}); still inside\",\"nested\":{\"ok\":true}});tail",6));
        assertEquals("",PublicMediaParser.extractJsonObject("s.handle({\"broken\":true",9));
    }
}
