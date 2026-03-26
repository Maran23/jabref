package org.jabref.gui.theme;

import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.NonNull;

/// Represents one of three types of a css based Theme for JabRef:
///
/// The Default type of theme is the light theme (which is in fact the absence of any theme), the dark theme is currently
/// the only embedded theme and the custom themes, that can be created by loading a proper css file.
public class Theme {

    public enum Type {
        SYSTEM, LIGHT, DARK, CUSTOM
    }

    public static final String SYSTEM = "";
    private static final String LIGHT = "light";
    private static final String DARK = "dark";

    private static final String BASE_CSS = "Base.css";
    private static final String DARK_CSS = "Dark.css";

    private final Type type;
    private final String name;
    private final Optional<StyleSheet> additionalStylesheet;

    public Theme(@NonNull String name) {
        this.name = name;

        if (name.equalsIgnoreCase(SYSTEM)) {
            this.type = Type.SYSTEM;
            this.additionalStylesheet = Optional.empty();
        } else if (LIGHT.equalsIgnoreCase(name)
                // FIXME: Legacy names
                || BASE_CSS.equalsIgnoreCase(name)) {
            this.type = Type.LIGHT;
            this.additionalStylesheet = Optional.empty();
        } else if (DARK.equalsIgnoreCase(name)
                // FIXME: Legacy names
                || DARK_CSS.equalsIgnoreCase(name)) {
            this.type = Type.DARK;
            this.additionalStylesheet = Optional.empty();
        } else {
            this.additionalStylesheet = StyleSheet.create(name);
            if (this.additionalStylesheet.isPresent()) {
                this.type = Type.CUSTOM;
            } else {
                this.type = Type.SYSTEM;
            }
        }
    }

    public static Theme light() {
        return new Theme(LIGHT);
    }

    public static Theme dark() {
        return new Theme(DARK);
    }

    public static Theme custom(String name) {
        return new Theme(name);
    }

    public static Theme system() {
        return new Theme(SYSTEM);
    }

    /// @return the Theme type. Guaranteed to be non-null.
    public Type getType() {
        return type;
    }

    /// Provides the name of the CSS, either for a built in theme, or for a raw, configured custom CSS location.
    /// This should be a file system path, but the raw string is
    /// returned even if it is not valid in some way. For this reason, the main use case for this getter is to
    /// storing or display the user preference, rather than to read and use the CSS file.
    ///
    /// @return the raw configured CSS location. Guaranteed to be non-null.
    public String getName() {
        return name;
    }

    /// This method allows callers to obtain the theme's additional stylesheet.
    ///
    /// @return called with the stylesheet location if there is an additional stylesheet present and available. The
    /// location will be a local URL. Typically it will be a `'data:'` URL where the CSS is embedded. However for
    /// large themes it can be `'file:'`.
    public Optional<StyleSheet> getAdditionalStylesheet() {
        return additionalStylesheet;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Theme that = (Theme) o;
        return type == that.type && name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, name);
    }

    @Override
    public String toString() {
        return "Theme{" +
                "type=" + type +
                ", name='" + name + '\'' +
                '}';
    }

    public static StyleSheet getBaseStyleSheet() {
        return StyleSheet.create(BASE_CSS).orElseThrow();
    }
}
