package org.jabref.gui.theme;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javafx.css.CssParser;

import org.jabref.architecture.AllowedToUseClassGetResource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/// Guards the color token contract between the base stylesheet and the themes.
///
/// jabref-base.css styles JabRef's own controls exclusively through `-color-*` tokens that the
/// active theme declares per color scheme. Two things have to hold for a theme to be swappable:
///
/// 1. The base stylesheet must not declare colors of its own. It is installed last, so anything it
///    declares would silently win over the theme.
/// 2. Every theme must declare every token that is used, otherwise JavaFX falls back to a default
///    and the control silently loses its color. Those warnings are suppressed at runtime by
///    [org.jabref.gui.logging.JavaFxCssLogFilter], so nothing else would report the gap.
@AllowedToUseClassGetResource("JavaFX internally handles the passed URLs properly.")
class ThemeTokenContractTest {

    private static final String BASE_CSS = "internal/jabref-base.css";

    /// A `-color-…` token. The look-behind keeps `-fx-body-color-bottomup` from reading as a use of
    /// `-color-bottomup`.
    private static final Pattern TOKEN = Pattern.compile("(?<![A-Za-z0-9])(-color-[a-z0-9-]*[a-z0-9])");

    private static final Pattern COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);

    /// Hex literals, `rgb()`/`rgba()` literals, and the CSS color keywords JabRef used to hardcode.
    private static final Pattern LITERAL_COLOR = Pattern.compile(
            "#[0-9A-Fa-f]{3,8}\\b"
                    + "|rgba?\\(\\s*[0-9]"
                    + "|:\\s*(?:white|black|red|green|blue|yellow|gray|grey|lightgrey|lightgray|crimson|firebrick|darkviolet|teal)\\s*[;!]");

    /// Tokens the base stylesheet derives from theme tokens rather than expecting a theme to declare
    /// them. They follow whatever the theme sets, so they are not part of the contract.
    private static final Pattern DERIVED_IN_BASE = Pattern.compile("-color-(?:match|ai-message)-.*");

    /// The cross-file contract: every token jabref-base.css reads has to be declared by the theme in
    /// *both* color schemes. Declaring it only in the light block leaves the control unstyled in dark
    /// mode, which is the failure mode this whole token set exists to prevent.
    @ParameterizedTest
    @EnumSource(ThemePreset.class)
    void themeDeclaresEveryTokenTheBaseStylesheetUses(ThemePreset theme) {
        String themeCss = theme.getStyleSheet().getName();

        Set<String> required = new TreeSet<>(tokens(BASE_CSS, Kind.USE));
        required.removeIf(token -> DERIVED_IN_BASE.matcher(token).matches());

        for (String colorScheme : List.of("light", "dark")) {
            Set<String> undeclared = new TreeSet<>(required);
            undeclared.removeAll(tokensIn(colorSchemeBlock(themeCss, colorScheme), Kind.DECLARATION));

            assertEquals(Set.of(), undeclared,
                    "%s does not declare every -color- token for 'prefers-color-scheme: %s'".formatted(themeCss, colorScheme));
        }
    }

    /// A theme may introduce tokens of its own (Primer scopes a good number of them to single
    /// controls), but it must not read one it never declares.
    @ParameterizedTest
    @EnumSource(ThemePreset.class)
    void themeDeclaresEveryTokenItUsesItself(ThemePreset theme) {
        String themeCss = theme.getStyleSheet().getName();

        Set<String> undeclared = new TreeSet<>(tokens(themeCss, Kind.USE));
        undeclared.removeAll(tokens(themeCss, Kind.DECLARATION));

        assertEquals(Set.of(), undeclared, "%s reads -color- tokens it never declares".formatted(themeCss));
    }

    @Test
    void baseStylesheetDeclaresNoThemeColors() {
        Set<String> declared = new TreeSet<>(tokens(BASE_CSS, Kind.DECLARATION));
        declared.removeIf(token -> DERIVED_IN_BASE.matcher(token).matches());

        assertEquals(Set.of(), declared,
                "jabref-base.css is installed last and would override the theme, so it must derive colors, never declare them");
    }

    @Test
    void baseStylesheetContainsNoLiteralColors() {
        List<String> literals = LITERAL_COLOR.matcher(withoutComments(read(BASE_CSS)))
                                             .results()
                                             .map(java.util.regex.MatchResult::group)
                                             .distinct()
                                             .toList();

        assertEquals(List.of(), literals,
                "jabref-base.css must take every color from a -color- token");
    }

    @ParameterizedTest
    @EnumSource(ThemePreset.class)
    void themeStylesheetParses(ThemePreset theme) {
        assertDoesNotThrow(() -> new CssParser().parse(resource(theme.getStyleSheet().getName())));
    }

    @Test
    void baseStylesheetParses() {
        assertDoesNotThrow(() -> new CssParser().parse(resource(BASE_CSS)));
    }

    private enum Kind { DECLARATION, USE }

    /// @return the body of `@media (prefers-color-scheme: <colorScheme>) { … }`, comments stripped
    private static String colorSchemeBlock(String css, String colorScheme) {
        String content = withoutComments(read(css));
        String header = "@media (prefers-color-scheme: %s)".formatted(colorScheme);

        int start = content.indexOf(header);
        assertNotEquals(-1, start, "%s has no '%s' block".formatted(css, header));

        int open = content.indexOf('{', start);
        int depth = 0;
        for (int i = open; i < content.length(); i++) {
            switch (content.charAt(i)) {
                case '{' -> depth++;
                case '}' -> depth--;
                default -> { }
            }
            if (depth == 0) {
                return content.substring(open, i);
            }
        }
        throw new IllegalStateException("unbalanced braces in " + css);
    }

    private static Set<String> tokens(String css, Kind kind) {
        return tokensIn(withoutComments(read(css)), kind);
    }

    private static Set<String> tokensIn(String content, Kind kind) {
        Set<String> result = new TreeSet<>();
        Matcher matcher = TOKEN.matcher(content);
        while (matcher.find()) {
            boolean declaration = content.substring(matcher.end()).stripLeading().startsWith(":");
            if (declaration == (kind == Kind.DECLARATION)) {
                result.add(matcher.group(1));
            }
        }
        return result;
    }

    private static String withoutComments(String content) {
        return COMMENT.matcher(content).replaceAll("");
    }

    private static String read(String css) {
        try {
            return new String(resource(css).openStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static URL resource(String css) {
        URL url = StyleSheet.class.getResource(css);
        assertNotNull(url, "cannot find stylesheet " + css);
        return url;
    }
}
