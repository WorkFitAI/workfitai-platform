package org.workfitai.jobservice.util;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

public class HtmlSanitizer {

    /**
     * Sanitize HTML giữ các tag cơ bản cho rich text.
     * Cho phép: <p>, <strong>, <em>, <ul>, <ol>, <li>, <br>, <a>, <img>, <span>, ...
     */
    public static String sanitize(String html) {
        if (html == null) return null;

        return Jsoup.clean(html, Safelist.relaxed()
                .addTags("span") // nếu muốn giữ thêm tag
                .addAttributes(":all", "style", "class") // giữ style, class
                .addProtocols("a", "href", "http", "https", "mailto") // chỉ cho phép url an toàn
        );
    }
}