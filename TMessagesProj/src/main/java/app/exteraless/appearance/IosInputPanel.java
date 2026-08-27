package app.exteraless.appearance;

import android.view.View;

public final class IosInputPanel {

    public static final int SLOT = 44;
    public static final int GAP = 6;
    public static final int ICON_SLOT = 32;
    public static final int ICON_EDGE = 4;
    public static final int TEXT_PADDING = 14;

    private IosInputPanel() {
    }

    public static boolean enabled() {
        return AppearanceConfig.iosInputPanel();
    }

    public static boolean isCircleVisible(View view) {
        return view != null
                && view.getVisibility() == View.VISIBLE
                && view.isShown()
                && view.getAlpha() >= 0.999f
                && view.getScaleX() >= 0.999f;
    }
}
