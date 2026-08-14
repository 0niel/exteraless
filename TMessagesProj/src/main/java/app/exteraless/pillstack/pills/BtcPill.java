package app.exteraless.pillstack.pills;

import android.annotation.SuppressLint;
import android.content.Context;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

import app.exteraless.pillstack.PillStackConfig;
import app.exteraless.pillstack.PillType;

/** Курс биткоина. */
@SuppressLint("ViewConstructor")
public class BtcPill extends RatePill {

    private static final RateCache CACHE = new RateCache();

    public BtcPill(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, resourcesProvider, CACHE, "BTC", 2, R.drawable.filter_money_solar,
                new ColoredBackground(0xFFF7931A, 0xFFE07000));
    }

    @Override
    public int getPillId() {
        return PillType.BTC.id;
    }

    @Override
    public String getTargetSelection() {
        return PillStackConfig.btcTargetCurrency.String();
    }

    @Override
    public void setTargetSelection(String currency) {
        PillStackConfig.btcTargetCurrency.setConfigString(currency);
    }
}
