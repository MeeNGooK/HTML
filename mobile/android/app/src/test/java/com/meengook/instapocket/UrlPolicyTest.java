package com.meengook.instapocket;
import org.junit.Test;
import static org.junit.Assert.*;
public class UrlPolicyTest {
    @Test public void acceptsDouyinShareAndVideoLinks() {
        assertTrue(UrlPolicy.isShareLink("https://v.douyin.com/0rxK1KgNtAA/"));
        assertTrue(UrlPolicy.isPost("https://www.douyin.com/video/7341234567890123456"));
        assertTrue(UrlPolicy.isMedia("https://v3-default.douyinvod.com/file.mp4?signature=123"));
    }
    @Test public void recognizesDouyinShareLandingRouteVariants() {
        assertTrue(UrlPolicy.isPost("https://www.douyin.com/share/video/7341234567890123456?previous_page=app_code_link"));
        assertTrue(UrlPolicy.isPost("https://www.douyin.com/share/note/7341234567890123456/"));
        assertEquals("7341234567890123456", UrlPolicy.videoId("https://www.douyin.com/share/video/7341234567890123456?previous_page=app_code_link"));
        assertEquals("7341234567890123456", UrlPolicy.videoId("https://www.douyin.com/?modal_id=7341234567890123456"));
    }
    @Test public void rejectsSpoofedHostsAndNonHttps() {
        assertFalse(UrlPolicy.isPost("https://douyin.com.evil.test/video/123"));
        assertFalse(UrlPolicy.isPost("https://user@douyin.com/video/123"));
        assertFalse(UrlPolicy.isShareLink("https://v.douyin.com.evil.test/a"));
        assertFalse(UrlPolicy.isMedia("http://v3-default.douyinvod.com/file.mp4"));
        assertFalse(UrlPolicy.isMedia("https://douyinvod.com.evil.test/file.mp4"));
        assertFalse(UrlPolicy.isMedia("file:///sdcard/private"));
        assertFalse(UrlPolicy.isPost(null));
    }
    @Test public void rejectsUnrelatedRoutes() {
        assertFalse(UrlPolicy.isPost("https://www.douyin.com/user/123"));
        assertFalse(UrlPolicy.isPost("https://www.douyin.com/video/not-a-number"));
    }
}
