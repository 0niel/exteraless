package app.exteraless.pillstack;

import android.content.Context;

import androidx.annotation.Keep;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import app.exteraless.pillstack.pills.BasePill;
import app.exteraless.pillstack.pills.BtcPill;
import app.exteraless.pillstack.pills.CachePill;
import app.exteraless.pillstack.pills.GramPill;
import app.exteraless.pillstack.pills.ProxyPill;
import app.exteraless.pillstack.pills.UsdPill;
import app.exteraless.pillstack.pills.WeatherPill;

/**
 * Реестр доступных пилюль: имя, иконка, цвета и фабрика вью.
 *
 * Реестр открытый: {@link #register(PillInfo)} / {@link #unregister(int)} можно звать в рантайме,
 * раскладка при этом чинится ({@link PillStackConfig#sanitizePills()}) и полоса пересобирается.
 * Пакетную регистрацию оборачивать в {@link #beginTransaction()} / {@link #endTransaction()},
 * чтобы не дёргать перестроение на каждый вызов.
 */
public class PillRegistry {

    public interface PillCreator {
        BasePill create(Context context, Theme.ResourcesProvider resourcesProvider);
    }

    public static class PillInfo {
        public final int id;
        public final CharSequence name;
        public final int iconRes;
        public final int iconColorTop;
        public final int iconColorBottom;
        public final PillCreator creator;

        public PillInfo(int id, CharSequence name, int iconRes, int iconColorTop, int iconColorBottom, PillCreator creator) {
            this.id = id;
            this.name = name;
            this.iconRes = iconRes;
            this.iconColorTop = iconColorTop;
            this.iconColorBottom = iconColorBottom;
            this.creator = creator;
        }

        public int id() {
            return id;
        }

        public CharSequence name() {
            return name;
        }

        public int iconRes() {
            return iconRes;
        }

        public int iconColorTop() {
            return iconColorTop;
        }

        public int iconColorBottom() {
            return iconColorBottom;
        }

        public PillCreator creator() {
            return creator;
        }
    }

    private static final Map<Integer, PillInfo> registry = new LinkedHashMap<>();
    private static volatile boolean batchRegistration;

    static {
        beginTransaction();
        registerDefaultPills();
        endTransaction();
    }

    private static void registerDefaultPills() {
        register(new PillInfo(PillType.WEATHER.id, LocaleController.getString(R.string.PillStackWeather),
                R.drawable.weather_cloudy, 0xFF37B5FF, 0xFF3E7BFF, WeatherPill::new));
        register(new PillInfo(PillType.GRAM.id, "TON",
                R.drawable.mini_gram_16, 0xFF38B9FF, 0xFF2E86FF, GramPill::new));
        register(new PillInfo(PillType.BTC.id, "BTC",
                R.drawable.filter_money_solar, 0xFFF7931A, 0xFFE07000, BtcPill::new));
        register(new PillInfo(PillType.USD.id, "USD",
                R.drawable.filter_money_solar, 0xFF2FA34D, 0xFF1D7A38, UsdPill::new));
        register(new PillInfo(PillType.CACHE.id, LocaleController.getString(R.string.PillStackCache),
                R.drawable.msg2_data, 0xFF9E7CFF, 0xFF7B5CFF, CachePill::new));
        register(new PillInfo(PillType.PROXY.id, LocaleController.getString(R.string.PillStackProxy),
                R.drawable.proxy_on_solar, 0xFF59C1FF, 0xFF3F8FFF, ProxyPill::new));
    }

    // ---- Пакетная регистрация ----

    /** Отключает перестроение полосы до {@link #endTransaction()}. */
    @Keep
    public static void beginTransaction() {
        batchRegistration = true;
    }

    /** Включает перестроение обратно и один раз чинит + оповещает раскладку. */
    @Keep
    public static void endTransaction() {
        batchRegistration = false;
        if (PillStackConfig.isConfigLoaded()) {
            PillStackConfig.sanitizePills();
            PillStackEvents.notifyLayoutChanged();
        }
    }

    // ---- Реестр ----

    public static void register(PillInfo info) {
        if (info == null) {
            return;
        }
        synchronized (registry) {
            registry.put(info.id, info);
        }
        invalidate();
    }

    /** Убирает пилюлю из реестра; из раскладки её вычистит sanitizePills. */
    @Keep
    public static void unregister(int id) {
        boolean removed;
        synchronized (registry) {
            removed = registry.remove(id) != null;
        }
        if (removed) {
            invalidate();
        }
    }

    /** Переносит пилюлю в активные, если она зарегистрирована и ещё не активна. */
    @Keep
    public static void activatePill(int id) {
        if (!isRegistered(id) || PillStackConfig.getActivePills().contains(id)) {
            return;
        }
        PillStackConfig.setPillActive(id, true);
        PillStackEvents.notifyLayoutChanged();
    }

    private static void invalidate() {
        if (batchRegistration) {
            return;
        }
        PillStackConfig.sanitizePills();
        PillStackEvents.notifyLayoutChanged();
    }

    public static PillInfo getPillInfo(int id) {
        synchronized (registry) {
            return registry.get(id);
        }
    }

    public static Collection<PillInfo> getRegisteredPills() {
        synchronized (registry) {
            return new ArrayList<>(registry.values());
        }
    }

    public static List<Integer> getRegisteredIds() {
        synchronized (registry) {
            return new ArrayList<>(registry.keySet());
        }
    }

    public static boolean isRegistered(int id) {
        synchronized (registry) {
            return registry.containsKey(id);
        }
    }

    public static BasePill createPill(int id, Context context, Theme.ResourcesProvider resourcesProvider) {
        PillInfo info = getPillInfo(id);
        if (info == null) {
            return null;
        }
        try {
            return info.creator.create(context, resourcesProvider);
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }
}
