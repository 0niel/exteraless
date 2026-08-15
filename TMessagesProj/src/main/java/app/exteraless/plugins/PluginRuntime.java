package app.exteraless.plugins;

/**
 * Кто из плагинов исполняется на этом потоке — для проверок на Java-стороне.
 *
 * Зачем: Python-гейт (extera_utils/audit_gate.py) видит только действия внутри
 * CPython. Всё, что плагин делает через Chaquopy напрямую — {@code java.net.URL},
 * {@code Runtime.exec}, рефлексия — происходит в JVM, где событий PEP 578 нет
 * (проверено на устройстве: {@code Class.forName("java.lang.Runtime")} + invoke
 * отработал, породив ноль событий сверх импорта {@code java.lang}).
 *
 * Java-стоку нужно знать, чей код его вызвал. Стек JVM этого не скажет: между
 * плагином и стоком лежит Chaquopy, а не наши кадры. Поэтому метку ставит
 * Python в тех точках, где управление переходит к коду плагина, — диспетчеризация
 * хуков и колбэки из UI (plugin_loader.plugin_context, android_utils.safe_call).
 *
 * Честная граница: отложенная работа (Runnable, который плагин поставил в
 * очередь, поток, который он создал сам) исполняется уже без метки и проверку
 * не проходит. Это осознанный остаток — см. PLUGINS-SECURITY.md.
 */
public final class PluginRuntime {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private PluginRuntime() {
    }

    /** Войти в код плагина. Возвращает предыдущее значение — его надо вернуть в {@link #exit}. */
    public static String enter(String pluginId) {
        String previous = CURRENT.get();
        CURRENT.set(pluginId);
        return previous;
    }

    /** Вернуть метку, снятую {@link #enter}. {@code null} очищает. */
    public static void exit(String previous) {
        if (previous == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(previous);
        }
    }

    /** id плагина, чей код исполняется на этом потоке, или {@code null}. */
    public static String current() {
        return CURRENT.get();
    }
}
