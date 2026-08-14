package app.exteraless.plugins.xposed;

import com.chaquo.python.PyObject;

import java.util.List;

import de.robv.android.xposed.XC_MethodHook;

/**
 * before/after-хук: вызывает Python-методы {@code before_hooked_method(param)} и
 * {@code after_hooked_method(param)} (любой из них может отсутствовать — наличие
 * проверено один раз при регистрации). MethodHookParam передаётся в Python как есть,
 * Chaquopy оборачивает его в прокси (param.thisObject, param.args, param.getResult(),
 * param.setResult(...) доступны из Python).
 *
 * Фильтры считаются на Java-стороне ДО входа в Python: "before"-фильтры гейтят
 * before_hooked_method, "after"-фильтры — after_hooked_method.
 */
public class PyMethodHook extends XC_MethodHook {

    private final String pluginId;
    private final PyObject handler;
    private final boolean hasBefore;
    private final boolean hasAfter;
    private final List<HookFilter> beforeFilters;
    private final List<HookFilter> afterFilters;

    public PyMethodHook(String pluginId, PyObject handler, int priority,
                 List<HookFilter> beforeFilters, List<HookFilter> afterFilters) {
        super(priority);
        this.pluginId = pluginId;
        this.handler = handler;
        this.hasBefore = handler.containsKey("before_hooked_method");
        this.hasAfter = handler.containsKey("after_hooked_method");
        this.beforeFilters = beforeFilters;
        this.afterFilters = afterFilters;
    }

    @Override
    protected void beforeHookedMethod(MethodHookParam param) {
        if (hasBefore && HookFilter.evaluateAll(beforeFilters, param, false)) {
            XposedHooks.callPython(pluginId, handler, "before_hooked_method", param);
        }
    }

    @Override
    protected void afterHookedMethod(MethodHookParam param) {
        if (hasAfter && HookFilter.evaluateAll(afterFilters, param, true)) {
            XposedHooks.callPython(pluginId, handler, "after_hooked_method", param);
        }
    }
}
