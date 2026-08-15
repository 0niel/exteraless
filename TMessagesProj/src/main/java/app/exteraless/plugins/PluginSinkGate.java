package app.exteraless.plugins;

import org.telegram.messenger.FileLog;

import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * Гейт на Java-стоках: то, чего Python-аудит увидеть не может.
 *
 * Python-гейт (extera_utils/audit_gate.py) ловит действия внутри CPython.
 * Но Chaquopy отдаёт плагину живые Java-объекты, и дальше всё происходит в
 * JVM: {@code java.net.URL.openConnection()}, {@code Runtime.exec()},
 * {@code Class.forName(...)} — ни одного события PEP 578. Проверено на
 * устройстве: рефлексией до {@code Runtime.exec("id")} плагин дошёл за шесть
 * событий, из которых пять — импорт {@code java.lang}.
 *
 * Здесь стоят хуки на сами стоки. Проверка идёт только если на этом потоке
 * стоит метка {@link PluginRuntime}, то есть исполняется код плагина; для
 * приложения хук — один ThreadLocal.get() и выход.
 *
 * Набор стоков сознательно узкий: запуск процессов, загрузка нативных
 * библиотек, сеть и резолв классов. Хуки на {@code FileInputStream} и прочую
 * горячую механику java.io не ставятся — цена высокая, а файловую сторону
 * закрывает Python-гейт (плагины работают с файлами через open()).
 *
 * Чего он не держит:
 * <ul>
 *   <li>плагин с разрешением {@code hooks} снимает эти хуки — против него
 *       защиты нет и быть не может;</li>
 *   <li>отложенную работу: Runnable, поставленный плагином в очередь,
 *       исполняется без метки потока.</li>
 * </ul>
 */
public final class PluginSinkGate {

    private static volatile boolean installed;

    /**
     * Защита от рекурсии. Проверка внутри хука сама трогает классы и настройки,
     * а значит снова попадает в {@code loadClass} — без флага это бесконечный
     * спуск на первом же отказе.
     */
    private static final ThreadLocal<Boolean> INSIDE = new ThreadLocal<>();

    /** Классы, недоступные плагину ни при каких разрешениях. */
    private static final String[] DENIED_CLASSES = {
            "java.lang.Runtime",
            "java.lang.ProcessBuilder",
            "java.lang.Process",
            "dalvik.system.DexClassLoader",
            "dalvik.system.PathClassLoader",
            "dalvik.system.InMemoryDexClassLoader",
            "dalvik.system.BaseDexClassLoader",
    };

    /** Классы, требующие разрешения network. */
    private static final String[] NETWORK_CLASSES = {
            "java.net.URL",
            "java.net.Socket",
            "java.net.HttpURLConnection",
            "java.net.URLConnection",
            "java.net.DatagramSocket",
            "java.net.ServerSocket",
            "javax.net.ssl.",
            "okhttp3.",
            "org.apache.http.",
            "java.nio.channels.SocketChannel",
            "java.nio.channels.DatagramChannel",
    };

    private PluginSinkGate() {
    }

    /**
     * Поставить хуки. Идемпотентно, ошибки не фатальны: без Aliuhook просто
     * остаётся Python-гейт.
     */
    public static synchronized void install() {
        if (installed) {
            return;
        }
        if (!app.exteraless.plugins.xposed.XposedHooks.ensureReady()) {
            FileLog.w("PluginSinkGate: Aliuhook unavailable, Java-side gate is off");
            return;
        }
        installed = true;
        int ok = 0;
        ok += hookDeny(Runtime.class, "exec", "run a shell command", "process");
        ok += hookDeny(ProcessBuilder.class, "start", "start a process", "process");
        ok += hookDeny(Runtime.class, "load", "load a native library", "native");
        ok += hookDeny(Runtime.class, "loadLibrary", "load a native library", "native");
        ok += hookDeny(System.class, "load", "load a native library", "native");
        ok += hookDeny(System.class, "loadLibrary", "load a native library", "native");
        ok += hookNetwork(URL.class, "openConnection", "open a network connection");
        ok += hookNetwork(Socket.class, "connect", "connect to the network");
        ok += hookClassResolution();
        FileLog.d("PluginSinkGate: " + ok + " hooks installed");
    }

    // ---------- стоки ----------

    private static int hookDeny(Class<?> owner, String methodName, String what, String category) {
        return hookAll(owner, methodName, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                String pluginId = enterCheck();
                if (pluginId == null) {
                    return;
                }
                try {
                    deny(pluginId, owner.getName() + "." + methodName, category,
                            describe(param), what + " is never available to plugins", param);
                } finally {
                    leaveCheck();
                }
            }
        });
    }

    private static int hookNetwork(Class<?> owner, String methodName, String what) {
        return hookAll(owner, methodName, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                String pluginId = enterCheck();
                if (pluginId == null) {
                    return;
                }
                try {
                    String detail = param.thisObject instanceof URL
                            ? String.valueOf(param.thisObject) : describe(param);
                    if (PluginPermissions.check(pluginId, PluginPermissions.NETWORK)) {
                        PluginAuditJournal.record(pluginId, owner.getName() + "." + methodName,
                                "network", detail, true);
                        return;
                    }
                    deny(pluginId, owner.getName() + "." + methodName, "network", detail,
                            "missing the 'network' permission", param);
                } finally {
                    leaveCheck();
                }
            }
        });
    }

    /**
     * Резолв классов. Без него сеть закрывается на один шаг: плагин, которому
     * запрещён {@code java.net.URL}, достаёт тот же класс через
     * {@code Class.forName} или {@code loadClass} у любого объекта, который
     * у него уже есть.
     */
    private static int hookClassResolution() {
        XC_MethodHook hook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args == null || param.args.length == 0
                        || !(param.args[0] instanceof String)) {
                    return;
                }
                String pluginId = enterCheck();
                if (pluginId == null) {
                    return;
                }
                try {
                    checkClass(pluginId, (String) param.args[0], param);
                } finally {
                    leaveCheck();
                }
            }

            private void checkClass(String pluginId, String name, MethodHookParam param) {
                String event = param.method == null ? "loadClass" : param.method.getName();
                for (String denied : DENIED_CLASSES) {
                    if (name.equals(denied)) {
                        deny(pluginId, event, "reflection", name,
                                "class " + name + " is never available to plugins", param);
                        return;
                    }
                }
                for (String prefix : NETWORK_CLASSES) {
                    boolean match = prefix.endsWith(".") ? name.startsWith(prefix) : name.equals(prefix);
                    if (!match) {
                        continue;
                    }
                    if (!PluginPermissions.check(pluginId, PluginPermissions.NETWORK)) {
                        deny(pluginId, event, "network", name,
                                "missing the 'network' permission", param);
                    } else {
                        PluginAuditJournal.record(pluginId, event, "network", name, true);
                    }
                    return;
                }
            }
        };
        int count = hookAll(Class.class, "forName", hook);
        count += hookAll(ClassLoader.class, "loadClass", hook);
        return count;
    }

    // ---------- инфраструктура ----------

    /** id плагина, если проверять надо; null — приложение или мы уже внутри проверки. */
    private static String enterCheck() {
        if (Boolean.TRUE.equals(INSIDE.get())) {
            return null;
        }
        String pluginId = PluginRuntime.current();
        if (pluginId == null) {
            return null;
        }
        INSIDE.set(Boolean.TRUE);
        return pluginId;
    }

    private static void leaveCheck() {
        INSIDE.remove();
    }

    private static int hookAll(Class<?> owner, String methodName, XC_MethodHook hook) {
        List<Member> targets = new ArrayList<>();
        try {
            for (Method method : owner.getDeclaredMethods()) {
                if (method.getName().equals(methodName)) {
                    targets.add(method);
                }
            }
        } catch (Throwable t) {
            FileLog.e("PluginSinkGate: cannot enumerate " + owner.getName() + "." + methodName, t);
            return 0;
        }
        int count = 0;
        for (Member target : targets) {
            try {
                XposedBridge.hookMethod(target, hook);
                count++;
            } catch (Throwable t) {
                FileLog.e("PluginSinkGate: hook failed for " + target, t);
            }
        }
        return count;
    }

    private static void deny(String pluginId, String event, String category, String detail,
                             String reason, XC_MethodHook.MethodHookParam param) {
        PluginAuditJournal.record(pluginId, event, category, detail, false);
        FileLog.w("PluginSinkGate: denied " + event + " to plugin " + pluginId + " — " + reason);
        param.setThrowable(denialFor(event, category, pluginId, reason));
    }

    /**
     * Чем отвечать на отказ.
     *
     * Плагин умеет обрабатывать отказы сети и отсутствие класса — этих ошибок
     * он ждёт. SecurityException из середины openConnection он не ждёт, и
     * команда просто молча ничего не делала: со стороны неотличимо от поломки.
     * Поэтому сетевой отказ выглядит сетевым, отказ в резолве класса —
     * ClassNotFoundException (он и объявлен у forName/loadClass), и только
     * запуск процессов остаётся SecurityException: там нечего изображать.
     */
    private static Throwable denialFor(String event, String category, String pluginId, String reason) {
        String message = "plugin '" + pluginId + "' cannot " + event + ": " + reason;
        if ("reflection".equals(category) || "loadClass".equals(event) || "forName".equals(event)) {
            return new ClassNotFoundException(message);
        }
        if ("network".equals(category)) {
            return new java.net.ConnectException(message);
        }
        return new SecurityException(message);
    }

    private static String describe(XC_MethodHook.MethodHookParam param) {
        if (param.args == null || param.args.length == 0 || param.args[0] == null) {
            return "";
        }
        Object first = param.args[0];
        if (first instanceof Object[]) {
            Object[] array = (Object[]) first;
            return array.length == 0 ? "" : String.valueOf(array[0]);
        }
        return String.valueOf(first);
    }
}
