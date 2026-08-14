package app.exteraless.plugins;

/**
 * Результат event-хука. На Java-стороне важна только стратегия:
 * модификация объектов (request/params/...) происходит на месте через Chaquopy-мост,
 * поэтому переносить сами объекты в результате не нужно.
 *
 * Строковые значения совпадают с Python-enum HookStrategy нашего SDK.
 */
public class HookResult {

    public enum Strategy {
        DEFAULT, CANCEL, MODIFY, MODIFY_FINAL;

        public static Strategy fromString(String s) {
            if (s == null) {
                return DEFAULT;
            }
            try {
                return valueOf(s);
            } catch (IllegalArgumentException e) {
                return DEFAULT;
            }
        }
    }

    public static final HookResult DEFAULT = new HookResult(Strategy.DEFAULT);

    public final Strategy strategy;

    public HookResult(Strategy strategy) {
        this.strategy = strategy;
    }

    public boolean isCancel() {
        return strategy == Strategy.CANCEL;
    }

    public boolean isFinal() {
        return strategy == Strategy.MODIFY_FINAL;
    }
}
