package com.winworld.coursestools.service.ops;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts the small markdown subset used in ops alerts to Telegram's HTML parse mode.
 * Input is HTML-escaped first, because alert bodies can contain customer-typed text (for example
 * Stripe cancellation comments); only `code`, **bold**, [links](https://...) and "- " bullets become
 * markup, and only http(s) links are linked.
 */
public final class TelegramHtml {
    private static final Pattern INLINE = Pattern.compile(
            "`([^`\\n]+)`|\\*\\*([^*\\n]+)\\*\\*|\\[([^\\]\\n]+)]\\((https?://[^)\\s]+)\\)");

    private TelegramHtml() {
    }

    public static String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    public static String fromMarkdown(String markdown) {
        StringBuilder out = new StringBuilder();
        String[] lines = escape(markdown).split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                out.append('\n');
            }
            String line = lines[i];
            if (line.startsWith("# ") || line.startsWith("## ") || line.startsWith("### ")) {
                out.append("<b>").append(inline(line.substring(line.indexOf(' ') + 1))).append("</b>");
            } else if (line.startsWith("- ")) {
                out.append("• ").append(inline(line.substring(2)));
            } else {
                out.append(inline(line));
            }
        }
        return out.toString();
    }

    private static String inline(String escapedLine) {
        Matcher matcher = INLINE.matcher(escapedLine);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String replacement;
            if (matcher.group(1) != null) {
                replacement = "<code>" + matcher.group(1) + "</code>";
            } else if (matcher.group(2) != null) {
                replacement = "<b>" + matcher.group(2) + "</b>";
            } else {
                replacement = "<a href=\"" + matcher.group(4) + "\">" + matcher.group(3) + "</a>";
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
