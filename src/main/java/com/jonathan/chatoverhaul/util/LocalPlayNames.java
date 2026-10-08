package com.jonathan.chatoverhaul.util;

/**
 * Mod-specific identity fixes for join/leave names.
 *
 * Applied ONLY where a join/leave line's name is extracted from a mod event
 * (PlayerListJoinLeaveMixin, NullJoinCommandInterceptor,
 * PlayerDisplayMessageMixin) - never to arbitrary message text, so normal
 * player names and unrelated messages containing the word "null" are
 * untouched.
 *
 * - The Broken Script's fake joiner reports its name as the literal string
 *   "null"; it must display as the U+273A symbol.
 * - The ARG Container's chat identity is "The_Creator"; it must display as
 *   "author".
 */
public final class LocalPlayNames {

    private LocalPlayNames() {}

    /**
     * Returns the display name for a join/leave event. Legacy section-sign
     * formatting codes are stripped first (The ARG Container embeds a leading
     * "\u00A7e" yellow code in its literal, which would otherwise survive the
     * white style), then the whole (trimmed) name is matched exactly against
     * the two mod identities; any other name is returned clean and otherwise
     * unchanged.
     */
    public static String normalize(String name) {
        if (name == null) {
            return "";
        }
        String cleaned = name.replaceAll("\u00A7.", "").trim();
        if ("null".equals(cleaned)) {
            return "\u273A";
        }
        if ("The_Creator".equals(cleaned)) {
            return "author";
        }
        return cleaned;
    }
}
