package app.exteraless.pillstack.pills;

import android.annotation.SuppressLint;
import android.content.Context;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.IconBackgroundColors;

import app.exteraless.pillstack.PillCurrencies;
import app.exteraless.pillstack.PillStackConfig;
import app.exteraless.pillstack.PillType;

/** Курс доллара к выбранной валюте. USD в списке целей смысла не имеет. */
@SuppressLint("ViewConstructor")
public class UsdPill extends RatePill {

    private static final RateCache CACHE = new RateCache();

    public UsdPill(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, resourcesProvider, CACHE, "USD", 2, R.drawable.pillstack_usd,
                new ColoredBackground(IconBackgroundColors.GREEN_DEEP.top,
                        IconBackgroundColors.GREEN_DEEP.bottom));
    }

    @Override
    public int getPillId() {
        return PillType.USD.id;
    }

    @Override
    public String[] getTargetCurrencies() {
        return PillCurrencies.getTargetCurrencies("USD");
    }

    @Override
    public String getTargetSelection() {
        String selection = PillStackConfig.usdTargetCurrency.String();
        return "USD".equalsIgnoreCase(selection) ? PillCurrencies.AUTO : selection;
    }

    @Override
    public void setTargetSelection(String currency) {
        PillStackConfig.usdTargetCurrency.setConfigString(currency);
    }
}
