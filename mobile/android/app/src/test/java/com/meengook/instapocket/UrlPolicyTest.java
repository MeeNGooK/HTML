package com.meengook.instapocket;
import org.junit.Test;
import static org.junit.Assert.*;
public class UrlPolicyTest {
    @Test public void permitsPostsAndSignedCdnFiles() {
        assertTrue(UrlPolicy.isPost("https://www.instagram.com/reel/ABC_12-/"));
        assertTrue(UrlPolicy.isMedia("https://scontent.cdninstagram.com/file.mp4?signature=123"));
    }
    @Test public void rejectsSpoofedHostsAndNonHttps() {
        assertFalse(UrlPolicy.isPost("https://instagram.com.evil.test/p/ABC/"));
        assertFalse(UrlPolicy.isPost("https://user@instagram.com/p/ABC/"));
        assertFalse(UrlPolicy.isMedia("http://scontent.cdninstagram.com/file.jpg"));
        assertFalse(UrlPolicy.isMedia("https://evilfbcdn.net/file.mp4"));
        assertFalse(UrlPolicy.isMedia("file:///sdcard/private"));
        assertFalse(UrlPolicy.isPost(null));
    }
    @Test public void rejectsNonPostRoutes() {
        assertFalse(UrlPolicy.isPost("https://www.instagram.com/username/"));
        assertFalse(UrlPolicy.isPost("https://www.instagram.com/stories/name/123/"));
    }
}
