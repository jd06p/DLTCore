package com.jonathan.chatoverhaul.client;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.regex.Pattern;

/** Visual preferences in the existing DLTCore client spec. */
final class CriticalBarHudConfig {
    private static final Pattern HEX_COLOR = Pattern.compile("#(?:[0-9a-fA-F]{6}|[0-9a-fA-F]{8})");

    enum Anchor {
        HOTBAR_LEFT(1, 2),
        TOP_LEFT(0, 0), TOP_CENTER(1, 0), TOP_RIGHT(2, 0),
        CENTER_LEFT(0, 1), CENTER(1, 1), CENTER_RIGHT(2, 1),
        BOTTOM_LEFT(0, 2), BOTTOM_CENTER(1, 2), BOTTOM_RIGHT(2, 2);

        final int horizontal;
        final int vertical;

        Anchor(int horizontal, int vertical) {
            this.horizontal = horizontal;
            this.vertical = vertical;
        }
    }

    enum Style { CLASSIC, SOLID, GRADIENT, OUTLINED }

    record Appearance(int width, int height, Anchor anchor, int xOffset, int yOffset,
                      int primary, int background, int progress, int charged, int chargedHighlight,
                      int borderColor, int chargedBorder, Style style,
                      boolean border, boolean rounded, boolean showBackground, boolean glow) {}

    private final ForgeConfigSpec.IntValue width;
    private final ForgeConfigSpec.IntValue height;
    private final ForgeConfigSpec.DoubleValue scale;
    private final ForgeConfigSpec.EnumValue<Anchor> anchor;
    private final ForgeConfigSpec.IntValue xOffset;
    private final ForgeConfigSpec.IntValue yOffset;
    private final ForgeConfigSpec.ConfigValue<String> primary;
    private final ForgeConfigSpec.ConfigValue<String> background;
    private final ForgeConfigSpec.ConfigValue<String> progress;
    private final ForgeConfigSpec.ConfigValue<String> charged;
    private final ForgeConfigSpec.ConfigValue<String> chargedHighlight;
    private final ForgeConfigSpec.ConfigValue<String> borderColor;
    private final ForgeConfigSpec.ConfigValue<String> chargedBorder;
    private final ForgeConfigSpec.EnumValue<Style> style;
    private final ForgeConfigSpec.BooleanValue border;
    private final ForgeConfigSpec.BooleanValue rounded;
    private final ForgeConfigSpec.BooleanValue showBackground;
    private final ForgeConfigSpec.BooleanValue glow;

    CriticalBarHudConfig(ForgeConfigSpec.Builder builder) {
        builder.comment("Critical Bar appearance for this client. The server controls gameplay and feature availability.")
                .push("critical_bar");
        width = builder.comment("Maximum bar width in GUI pixels before scaling. The bar shrinks to fit available space.")
                .defineInRange("width", 68, 12, 256);
        height = builder.comment("Bar thickness in GUI pixels before scaling.").defineInRange("height", 5, 3, 32);
        scale = builder.comment("Multiplier applied to both width and height.").defineInRange("scale", 1.0, 0.5, 2.0);
        anchor = builder.comment("HOTBAR_LEFT follows the vanilla hotbar. Other anchors are relative to the screen.")
                .defineEnum("anchor", Anchor.HOTBAR_LEFT);
        xOffset = builder.comment("Horizontal offset in GUI pixels. Positive moves right. HOTBAR_LEFT keeps a gap beside the slots.")
                .defineInRange("x_offset", 0, -4096, 4096);
        yOffset = builder.comment("Vertical offset in GUI pixels. Positive moves down. Offsets are clamped to keep the bar on screen.")
                .defineInRange("y_offset", 0, -4096, 4096);
        primary = color(builder, "primary_color", "#C25ACC", "Main accent for the classic highlight or the top of the gradient.");
        background = color(builder, "background_color", "#38153F", "Color behind the progress fill.");
        progress = color(builder, "progress_color", "#9D36A7", "Fill or outline color while charging.");
        charged = color(builder, "charged_color", "#F45ADA", "Fill or outline color while fully charged.");
        chargedHighlight = color(builder, "charged_highlight_color", "#FFBBF4", "Accent used in place of the primary color while charged.");
        borderColor = color(builder, "border_color", "#241128", "Border color while charging.");
        chargedBorder = color(builder, "charged_border_color", "#AA42A9", "Border color while fully charged.");
        style = builder.comment("CLASSIC has a highlight, SOLID is flat, GRADIENT shades vertically, and OUTLINED draws the fill as a contour.")
                .defineEnum("style", Style.CLASSIC);
        border = builder.comment("Draw a one-pixel border around the bar.")
                .define("border", true);
        rounded = builder.comment("Soften the bar corners with a one-pixel cut.")
                .define("rounded_corners", false);
        showBackground = builder.comment("Draw the background behind the progress fill.")
                .define("show_background", true);
        glow = builder.comment("Draw a subtle one-pixel halo while fully charged.")
                .define("charged_glow", false);
        builder.pop();
    }

    Appearance read() {
        double multiplier = scale.get();
        return new Appearance(Math.max(3, (int) Math.round(width.get() * multiplier)),
                Math.max(3, (int) Math.round(height.get() * multiplier)),
                anchor.get(), xOffset.get(), yOffset.get(),
                argb(primary.get()), argb(background.get()), argb(progress.get()), argb(charged.get()),
                argb(chargedHighlight.get()), argb(borderColor.get()), argb(chargedBorder.get()),
                style.get(), border.get(), rounded.get(), showBackground.get(), glow.get());
    }

    private static ForgeConfigSpec.ConfigValue<String> color(ForgeConfigSpec.Builder builder, String key,
                                                              String defaultValue, String description) {
        return builder.comment(description, "Use #RRGGBB or #AARRGGBB, with alpha first (00 transparent, FF opaque).")
                .define(key, defaultValue, value -> value instanceof String text && HEX_COLOR.matcher(text).matches());
    }

    private static int argb(String hex) {
        int value = Integer.parseUnsignedInt(hex.substring(1), 16);
        return hex.length() == 7 ? value | 0xFF000000 : value;
    }
}
