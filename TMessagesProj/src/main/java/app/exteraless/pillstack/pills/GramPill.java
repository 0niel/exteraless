package app.exteraless.pillstack.pills;

import android.annotation.SuppressLint;
import android.content.Context;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

import app.exteraless.pillstack.PillStackConfig;
import app.exteraless.pillstack.PillType;

/** Курс TON. */
@SuppressLint("ViewConstructor")
public class GramPill extends RatePill {

    private static final RateCache CACHE = new RateCache();

    public GramPill(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, resourcesProvider, CACHE, "TON", 3, R.drawable.mini_gram_16,
                new ColoredBackground());
    }

    @Override
    public int getPillId() {
        return PillType.GRAM.id;
    }

    @Override
    public String getTargetSelection() {
        return PillStackConfig.gramTargetCurrency.String();
    }

    @Override
    public void setTargetSelection(String currency) {
        PillStackConfig.gramTargetCurrency.setConfigString(currency);
    }
}
