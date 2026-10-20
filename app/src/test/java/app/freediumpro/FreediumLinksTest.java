package app.freediumpro;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FreediumLinksTest {

    private static final String MIRROR = FreediumLinks.DEFAULT_MIRROR;

    @Test
    public void extractsUrlFromMediumShareText() {
        String shared = "Check out \"Welcome to Medium\" by Ev Williams on Medium: "
                + "https://medium.com/@ev/welcome-to-medium-9e53ca408c48?source=social.tw.";
        assertEquals("https://medium.com/@ev/welcome-to-medium-9e53ca408c48?source=social.tw",
                FreediumLinks.extractUrl(shared));
    }

    @Test
    public void extractsBareUrlAndStripsTrailingPunctuation() {
        assertEquals("https://medium.com/p/9e53ca408c48", FreediumLinks.extractUrl("https://medium.com/p/9e53ca408c48"));
        assertEquals("https://link.medium.com/AbC123", FreediumLinks.extractUrl("(https://link.medium.com/AbC123)."));
    }

    @Test
    public void returnsNullWithoutUrl() {
        assertNull(FreediumLinks.extractUrl("no link here"));
        assertNull(FreediumLinks.extractUrl(null));
    }

    @Test
    public void buildsFreediumUrlAndDropsTracking() {
        assertEquals(MIRROR + "/https://medium.com/@ev/welcome-to-medium-9e53ca408c48",
                FreediumLinks.toFreedium(MIRROR,
                        "https://medium.com/@ev/welcome-to-medium-9e53ca408c48?source=social.share&utm_medium=x#top"));
    }

    @Test
    public void dropsRefAndBranchTrackingButKeepsFriendLink() {
        assertEquals(MIRROR + "/https://medium.com/p/abc?sk=123",
                FreediumLinks.toFreedium(MIRROR,
                        "https://medium.com/p/abc?source=x&ref=twitter&sk=123&_branch_match_id=99"));
    }

    @Test
    public void handlesSubdomainAndCustomDomainArticles() {
        // towardsdatascience.medium.com subdomain
        assertEquals(MIRROR + "/https://towardsdatascience.medium.com/some-post-abc123",
                FreediumLinks.toFreedium(MIRROR, "https://towardsdatascience.medium.com/some-post-abc123"));
        // Medium-hosted custom domain blog is passed through to Freedium as-is
        assertEquals(MIRROR + "/https://blog.example.com/a-story-deadbeef",
                FreediumLinks.toFreedium(MIRROR, "https://blog.example.com/a-story-deadbeef"));
    }

    @Test
    public void leavesMirrorLinksUnchanged() {
        String already = MIRROR + "/https://medium.com/p/abc";
        assertEquals(already, FreediumLinks.toFreedium(MIRROR, already));
    }

    @Test
    public void normalizesMirror() {
        assertEquals("https://freedium-mirror.cfd", FreediumLinks.normalizeMirror(" freedium-mirror.cfd/ "));
        assertEquals("http://example.org", FreediumLinks.normalizeMirror("http://example.org//"));
        assertNull(FreediumLinks.normalizeMirror("not a domain"));
        assertNull(FreediumLinks.normalizeMirror(""));
    }

    @Test
    public void recognizesMediumHosts() {
        assertTrue(FreediumLinks.isMediumHost("medium.com"));
        assertTrue(FreediumLinks.isMediumHost("towardsdatascience.medium.com"));
        assertTrue(FreediumLinks.isMediumHost("link.medium.com"));
        assertFalse(FreediumLinks.isMediumHost("notmedium.com"));
        assertFalse(FreediumLinks.isMediumHost(null));
    }

    @Test
    public void detectsArticleUrlsVsFeedAndProfile() {
        assertTrue(FreediumLinks.looksLikeArticle("https://medium.com/@ev/welcome-to-medium-9e53ca408c48"));
        assertTrue(FreediumLinks.looksLikeArticle("https://medium.com/p/9e53ca408c48"));
        assertTrue(FreediumLinks.looksLikeArticle("https://towardsdatascience.medium.com/a-post-deadbeef12"));
        assertFalse(FreediumLinks.looksLikeArticle("https://medium.com/"));
        assertFalse(FreediumLinks.looksLikeArticle("https://medium.com/@ev"));
        assertFalse(FreediumLinks.looksLikeArticle("https://medium.com/me/stories/public"));
        assertFalse(FreediumLinks.looksLikeArticle("https://example.com/some-post-abcdef12"));
    }

    @Test
    public void detectsShortLinksThatNeedResolution() {
        assertTrue(FreediumLinks.needsRedirectResolution("https://link.medium.com/AbC123"));
        assertTrue(FreediumLinks.needsRedirectResolution("https://medium.app.link/xyz"));
        assertFalse(FreediumLinks.needsRedirectResolution("https://medium.com/p/abc"));
        assertFalse(FreediumLinks.needsRedirectResolution("https://towardsdatascience.medium.com/post"));
    }
}
