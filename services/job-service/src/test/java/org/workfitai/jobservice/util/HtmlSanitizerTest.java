package org.workfitai.jobservice.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlSanitizerTest {

    @Test
    void sanitize_returnsNull_whenInputIsNull() {
        assertThat(HtmlSanitizer.sanitize(null)).isNull();
    }

    @Test
    void sanitize_keepsAllowedTagsAndStripsScripts() {
        String dirty = "<p>Hello</p><script>alert('xss')</script>";

        String clean = HtmlSanitizer.sanitize(dirty);

        assertThat(clean).contains("<p>Hello</p>").doesNotContain("<script>").doesNotContain("alert");
    }

    @Test
    void sanitize_preservesStyleAndClassAttributes() {
        String dirty = "<span style=\"color:red\" class=\"highlight\">text</span>";

        String clean = HtmlSanitizer.sanitize(dirty);

        assertThat(clean).contains("style=\"color:red\"").contains("class=\"highlight\"");
    }

    @Test
    void sanitize_stripsUnsafeLinkProtocols() {
        String dirty = "<a href=\"javascript:alert(1)\">click</a>";

        String clean = HtmlSanitizer.sanitize(dirty);

        assertThat(clean).doesNotContain("javascript:");
    }

    @Test
    void sanitize_allowsSafeHttpLinks() {
        String dirty = "<a href=\"https://example.com\">link</a>";

        String clean = HtmlSanitizer.sanitize(dirty);

        assertThat(clean).contains("href=\"https://example.com\"");
    }
}
