"""Java-facing plugin loader: import, lifecycle, hook dispatch — exteraless plugin SDK.

The Java engine (app.exteraless.plugins.PythonPluginsEngine) calls the
module-level functions of this module via Chaquopy. All return values that
cross the bridge are JSON strings, plain strings or booleans.

User-code exceptions from hook callbacks propagate to Java intentionally —
the engine catches them and disables the offending plugin.
"""

import contextlib
import importlib.machinery
import importlib.util
import inspect
import itertools
import json
import os
import re
import sys
import threading
from dataclasses import dataclass, field
from typing import Any, Callable, Dict, Optional

# Make sibling top-level modules (base_plugin, ui, ...) importable regardless
# of how the interpreter was started.
_SRC_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if _SRC_DIR not in sys.path:
    sys.path.insert(0, _SRC_DIR)

import client_utils
from base_plugin import AppEvent, BasePlugin, HookStrategy
from ui import settings as ui_settings

try:
    import pip_controller
except Exception:  # requests/packaging unavailable — pip support disabled
    pip_controller = None

from .metadata_parser import read_metadata  # noqa: F401
from .metadata_parser import read_metadata_json as _read_metadata_json_py


def read_metadata_json(path: str) -> str:
    """Java-facing metadata entry point. Routes structured Elyx archives
    (.elyx/.eaf) to elyx_runtime, plain .py/.plugin to the AST parser."""
    if str(path).endswith((".elyx", ".eaf")):
        try:
            import elyx_runtime
            return elyx_runtime.read_metadata_json(path)
        except ImportError:
            return '{"ok":false,"error":"elyx_runtime unavailable"}'
        except Exception as e:  # malformed archive etc.
            return '{"ok":false,"error":%s}' % __import__("json").dumps(str(e))
    return _read_metadata_json_py(path)

__all__ = [
    "read_metadata_json", "load_plugin", "unload_plugin", "uninstall_plugin",
    "call_app_event",
    "call_send_message_hook", "call_pre_request_hook", "call_post_request_hook",
    "call_update_hook", "call_updates_hook",
    "get_settings_json", "notify_setting_changed", "dispatch_setting_click",
    "is_loaded", "plugins", "PluginRecord", "start_dev_server",
    "plugin_context", "current_plugin_id",
]


@dataclass
class PluginRecord:
    """Runtime state of a loaded plugin."""
    module: Any
    instance: BasePlugin
    path: str
    # Settings callback registry, rebuilt on every get_settings_json() call:
    click_callbacks: Dict[str, Callable] = field(default_factory=dict)  # callback_id -> on_click
    change_callbacks: Dict[str, Callable] = field(default_factory=dict)  # setting key -> on_change


plugins: Dict[str, PluginRecord] = {}

_VALID_STRATEGIES = frozenset({
    HookStrategy.DEFAULT, HookStrategy.CANCEL, HookStrategy.MODIFY, HookStrategy.MODIFY_FINAL,
})


# ---------------------------------------------------------------------------
# Plugin context (which plugin's code is running on this thread)
# ---------------------------------------------------------------------------

_context_state = threading.local()


@contextlib.contextmanager
def plugin_context(plugin_id: Optional[str]):
    """Mark *plugin_id* as the running plugin on this thread.

    FilesController / IntentsManager resolve their registering plugin from
    this; the loader sets it around load/unload and hook dispatch.
    """
    previous = getattr(_context_state, "plugin_id", None)
    _context_state.plugin_id = plugin_id
    try:
        yield plugin_id
    finally:
        _context_state.plugin_id = previous


def current_plugin_id() -> Optional[str]:
    return getattr(_context_state, "plugin_id", None)


# ---------------------------------------------------------------------------
# Loading / unloading
# ---------------------------------------------------------------------------

def _error_json(exc: BaseException) -> str:
    return json.dumps({"ok": False, "error": f"{type(exc).__name__}: {exc}",
                       "has_settings": False}, ensure_ascii=False)


def _overrides(cls, name: str) -> bool:
    """True if *cls* provides its own implementation of a BasePlugin method."""
    return getattr(cls, name, None) is not getattr(BasePlugin, name, None)


def _import_module(path: str, plugin_id: str):
    module_name = "extera_plugin_" + re.sub(r"[^0-9A-Za-z_]", "_", str(plugin_id))
    sys.modules.pop(module_name, None)
    # The loader must be explicit. spec_from_file_location() picks one by file
    # extension, and ".plugin" — the canonical extension for published plugins —
    # is not a registered source suffix, so it returns None and the import dies
    # with "cannot create a module spec". The file is plain Python source
    # whatever it is called, so name the loader instead of guessing.
    loader = importlib.machinery.SourceFileLoader(module_name, path)
    spec = importlib.util.spec_from_file_location(module_name, path, loader=loader)
    if spec is None or spec.loader is None:
        raise ImportError(f"cannot create a module spec for {path!r}")
    module = importlib.util.module_from_spec(spec)
    sys.modules[module_name] = module  # needed by dataclasses/pickles inside the module
    try:
        spec.loader.exec_module(module)
    except Exception:
        sys.modules.pop(module_name, None)
        raise
    return module


def _find_plugin_class(module, path: str):
    """The plugin class: a BasePlugin subclass defined in the plugin module itself."""
    for obj in vars(module).values():
        if isinstance(obj, type) and issubclass(obj, BasePlugin) \
                and obj is not BasePlugin and obj.__module__ == module.__name__:
            return obj
    raise RuntimeError(f"no BasePlugin subclass defined in {path!r}")


def load_plugin(path: str, plugin_id: str) -> str:
    """Validate metadata, install requirements, import and start the plugin."""
    if str(path).endswith((".elyx", ".eaf")):
        return _load_elyx_plugin(path, plugin_id)

    try:
        meta = read_metadata(path)  # validation; raises PluginMetadataError
    except Exception as e:
        return _error_json(e)

    try:
        if plugin_id in plugins:
            _unload_record(plugin_id, quiet=True)

        if pip_controller is not None and meta.get("requirements"):
            pip_controller.ensure_requirements(plugin_id, meta["requirements"])

        module = _import_module(path, plugin_id)
        plugin_class = _find_plugin_class(module, path)
        instance = plugin_class()
        instance._attach(plugin_id)
        plugins[plugin_id] = PluginRecord(module=module, instance=instance, path=path)

        try:
            if _overrides(plugin_class, "on_plugin_load"):
                with plugin_context(plugin_id):
                    instance.on_plugin_load()
        except Exception:
            _unload_record(plugin_id, quiet=True)
            raise

        has_settings = _overrides(plugin_class, "create_settings")
        return json.dumps({"ok": True, "error": None, "has_settings": has_settings},
                          ensure_ascii=False)
    except Exception as e:
        return _error_json(e)


def _load_elyx_plugin(path: str, plugin_id: str) -> str:
    """Structured .elyx/.eaf plugins delegate to elyx_runtime (owned elsewhere).

    SINGLE DISPATCH POINT: elyx_runtime.load_plugin_record(record, path)
    populates record.module / record.instance (and _attach()es). Lifecycle
    stays on this loader, per the elyx_runtime contract: we run
    on_plugin_load here, and _unload_record() routes namespace teardown
    back through elyx_runtime.unload_plugin_record().
    """
    try:
        import elyx_runtime
    except ImportError:
        return _error_json(RuntimeError("Elyx runtime unavailable"))
    try:
        if plugin_id in plugins:
            _unload_record(plugin_id, quiet=True)
        record = PluginRecord(module=None, instance=None, path=path)
        record.__dict__["_elyx"] = True
        with plugin_context(plugin_id):
            elyx_runtime.load_plugin_record(record, path)
        if record.instance is None:
            raise RuntimeError("elyx_runtime did not populate the plugin record")
        plugins[plugin_id] = record
        try:
            if _overrides(type(record.instance), "on_plugin_load"):
                with plugin_context(plugin_id):
                    record.instance.on_plugin_load()
        except Exception:
            _unload_record(plugin_id, quiet=True)
            raise
        has_settings = _overrides(type(record.instance), "create_settings")
        return json.dumps({"ok": True, "error": None, "has_settings": has_settings},
                          ensure_ascii=False)
    except Exception as e:
        return _error_json(e)


def _unload_record(plugin_id: str, quiet: bool):
    record = plugins.pop(plugin_id, None)
    if record is None:
        return
    instance = record.instance
    try:
        if instance is not None and not quiet \
                and _overrides(type(instance), "on_plugin_unload"):
            with plugin_context(plugin_id):
                instance.on_plugin_unload()
    finally:
        try:
            if instance is not None and hasattr(instance, "_cleanup_resources"):
                with plugin_context(plugin_id):
                    instance._cleanup_resources()
        except Exception:
            pass
        if getattr(record, "_elyx", False):
            # Elyx namespace teardown (module eviction etc.) is elyx_runtime's job.
            try:
                import elyx_runtime
                elyx_runtime.unload_plugin_record(record)
            except Exception:
                pass
        else:
            module = getattr(record, "module", None)
            if module is not None:
                sys.modules.pop(module.__name__, None)


def unload_plugin(plugin_id: str) -> None:
    _unload_record(plugin_id, quiet=False)
    return None


def uninstall_plugin(plugin_id: str) -> None:
    """Full removal: unload plus dependency refcount cleanup.

    Called by the dev server's remove_plugin (the Java uninstall path should
    call this too when wired). File/prefs removal stays on the Java side.
    """
    _unload_record(plugin_id, quiet=False)
    if pip_controller is not None:
        try:
            pip_controller.remove_requirements(plugin_id)
        except Exception as e:
            print(f"[exteraless:plugin_loader] remove_requirements({plugin_id!r}) "
                  f"failed: {e}", file=sys.stderr)
    # Elyx: вычистить экстракции и локальные wheels (<plugins_dir>/.elyx_extracted/<id>).
    try:
        import elyx_runtime
        if hasattr(elyx_runtime, "purge_plugin"):
            from app.exteraless.plugins import PythonBridge
            elyx_runtime.purge_plugin(str(PythonBridge.getPluginsDir()), plugin_id)
    except Exception:
        pass
    return None


def is_loaded(plugin_id: str) -> bool:
    return plugin_id in plugins


# ---------------------------------------------------------------------------
# Event dispatch
# ---------------------------------------------------------------------------

def call_app_event(plugin_id: str, event: str) -> None:
    record = plugins.get(plugin_id)
    if record is None:
        return None
    instance = record.instance
    if not _overrides(type(instance), "on_app_event"):
        return None
    app_event = AppEvent.from_java(event)
    if app_event is None:
        instance.log(f"unknown app event {event!r}")
        return None
    instance.on_app_event(app_event)
    return None


def _strategy_of(result) -> str:
    """Extract a valid strategy string from a hook result (None -> DEFAULT)."""
    if result is None:
        return HookStrategy.DEFAULT
    strategy = getattr(result, "strategy", result)
    try:
        strategy = str(strategy)
    except Exception:
        return HookStrategy.DEFAULT
    return strategy if strategy in _VALID_STRATEGIES else HookStrategy.DEFAULT


def call_send_message_hook(plugin_id: str, account: int, params) -> str:
    record = plugins.get(plugin_id)
    if record is None:
        return HookStrategy.DEFAULT
    instance = record.instance
    if not _overrides(type(instance), "on_send_message_hook"):
        return HookStrategy.DEFAULT
    with client_utils.hook_scope(account), plugin_context(plugin_id):
        return _strategy_of(instance.on_send_message_hook(account, params))


def call_pre_request_hook(plugin_id: str, account: int, request_name: str, request) -> str:
    record = plugins.get(plugin_id)
    if record is None:
        return HookStrategy.DEFAULT
    instance = record.instance
    if not _overrides(type(instance), "pre_request_hook"):
        return HookStrategy.DEFAULT
    with client_utils.hook_scope(account), plugin_context(plugin_id):
        return _strategy_of(instance.pre_request_hook(request_name, account, request))


def call_post_request_hook(plugin_id: str, account: int, request_name: str,
                           response, error) -> str:
    record = plugins.get(plugin_id)
    if record is None:
        return HookStrategy.DEFAULT
    instance = record.instance
    if not _overrides(type(instance), "post_request_hook"):
        return HookStrategy.DEFAULT
    with client_utils.hook_scope(account), plugin_context(plugin_id):
        return _strategy_of(instance.post_request_hook(request_name, account, response, error))


def call_update_hook(plugin_id: str, account: int, update_name: str, update) -> str:
    """Dispatch a single TL_update* to on_update_hook (Java routes by name)."""
    record = plugins.get(plugin_id)
    if record is None:
        return HookStrategy.DEFAULT
    instance = record.instance
    if not _overrides(type(instance), "on_update_hook"):
        return HookStrategy.DEFAULT
    with client_utils.hook_scope(account), plugin_context(plugin_id):
        return _strategy_of(instance.on_update_hook(update_name, account, update))


def call_updates_hook(plugin_id: str, account: int, container_name: str, updates) -> str:
    """Dispatch a TL_updates* container to on_updates_hook (Java routes by name)."""
    record = plugins.get(plugin_id)
    if record is None:
        return HookStrategy.DEFAULT
    instance = record.instance
    if not _overrides(type(instance), "on_updates_hook"):
        return HookStrategy.DEFAULT
    with client_utils.hook_scope(account), plugin_context(plugin_id):
        return _strategy_of(instance.on_updates_hook(container_name, account, updates))


# ---------------------------------------------------------------------------
# Settings serialization
# ---------------------------------------------------------------------------

def _put(data: dict, key: str, value):
    """Set an optional schema field; None-valued optionals are omitted."""
    if value is not None:
        data[key] = value


def _register_callbacks(item, record: PluginRecord, counter) -> Optional[str]:
    """Allocate a callback_id when the item declares on_change and/or on_click."""
    on_change = getattr(item, "on_change", None)
    on_click = getattr(item, "on_click", None)
    if on_change is None and on_click is None:
        return None
    callback_id = f"cb_{next(counter)}"
    if on_click is not None:
        record.click_callbacks[callback_id] = on_click
    key = getattr(item, "key", None)
    if on_change is not None and key is not None:
        record.change_callbacks[key] = on_change
    return callback_id


def _serialize_setting_item(item, record: PluginRecord, counter) -> Optional[dict]:
    s = ui_settings
    instance = record.instance

    if isinstance(item, s.Header):
        return {"type": "header", "text": item.text}

    if isinstance(item, s.Divider):
        data = {"type": "divider"}
        _put(data, "text", item.text)
        return data

    if isinstance(item, s.Switch):
        data = {
            "type": "switch",
            "key": item.key,
            "text": item.text,
            "value": instance.get_setting(item.key, item.default),
        }
        _put(data, "subtext", item.subtext)
        _put(data, "icon", item.icon)
        _put(data, "callback_id", _register_callbacks(item, record, counter))
        return data

    if isinstance(item, s.Selector):
        data = {
            "type": "selector",
            "key": item.key,
            "text": item.text,
            "items": [str(entry) for entry in item.items],
            "value": instance.get_setting(item.key, item.default),
        }
        _put(data, "icon", item.icon)
        _put(data, "callback_id", _register_callbacks(item, record, counter))
        return data

    if isinstance(item, s.Input):
        default = item.default if item.default is not None else ""
        data = {
            "type": "input",
            "key": item.key,
            "text": item.text,
            "value": instance.get_setting(item.key, default),
        }
        _put(data, "subtext", item.subtext)
        _put(data, "icon", item.icon)
        _put(data, "callback_id", _register_callbacks(item, record, counter))
        return data

    if isinstance(item, s.EditText):
        data = {
            "type": "edittext",
            "key": item.key,
            "hint": item.hint,
            "value": instance.get_setting(item.key, item.default),
            "multiline": bool(item.multiline),
        }
        _put(data, "max_length", item.max_length)
        _put(data, "callback_id", _register_callbacks(item, record, counter))
        return data

    if isinstance(item, s.Text):
        data = {
            "type": "text",
            "text": item.text,
            "accent": bool(item.accent),
            "red": bool(item.red),
        }
        _put(data, "subtext", item.subtext)
        _put(data, "icon", item.icon)
        _put(data, "callback_id", _register_callbacks(item, record, counter))
        if item.create_sub_fragment is not None:
            try:
                sub_items = item.create_sub_fragment()
            except Exception as e:
                instance.log(f"create_sub_fragment() failed: {type(e).__name__}: {e}")
                sub_items = None
            if sub_items:
                sub_page = []
                for sub_item in sub_items:
                    entry = _serialize_setting_item(sub_item, record, counter)
                    if entry is not None:
                        sub_page.append(entry)
                data["sub_page"] = sub_page
        return data

    if isinstance(item, s.Custom):
        # Custom views/factories cannot cross the JSON settings bridge;
        # skipped in this build (requires the class-proxy subsystem).
        return None

    return None  # unknown item type: skip defensively


def get_settings_json(plugin_id: str) -> str:
    """Serialize the plugin's create_settings() list into the Java JSON schema."""
    record = plugins.get(plugin_id)
    if record is None:
        return "null"
    instance = record.instance
    try:
        items = instance.create_settings()
    except Exception as e:
        instance.log(f"create_settings() failed: {type(e).__name__}: {e}")
        return "null"
    if items is None:
        return "null"

    record.click_callbacks = {}
    record.change_callbacks = {}
    counter = itertools.count(1)

    out = []
    for item in items:
        try:
            entry = _serialize_setting_item(item, record, counter)
        except Exception as e:
            instance.log(f"settings item skipped: {type(e).__name__}: {e}")
            continue
        if entry is not None:
            out.append(entry)
    return json.dumps(out, ensure_ascii=False)


# ---------------------------------------------------------------------------
# Settings callbacks
# ---------------------------------------------------------------------------

def _takes_positional_arg(fn) -> bool:
    try:
        signature = inspect.signature(fn)
    except (TypeError, ValueError):
        return True
    for param in signature.parameters.values():
        if param.kind in (param.POSITIONAL_ONLY, param.POSITIONAL_OR_KEYWORD,
                          param.VAR_POSITIONAL):
            return True
    return False


def _call_with_optional_arg(fn, arg):
    """Call fn(arg), or fn() when the callable declares no parameters."""
    if _takes_positional_arg(fn):
        fn(arg)
    else:
        fn()


def notify_setting_changed(plugin_id: str, key: str, json_value: str) -> None:
    """Persist a changed setting (no UI reload) and invoke its on_change callback."""
    record = plugins.get(plugin_id)
    if record is None:
        return None
    try:
        value = json.loads(json_value)
    except (ValueError, TypeError):
        value = None
    record.instance.set_setting(key, value)
    callback = record.change_callbacks.get(key)
    if callback is not None:
        _call_with_optional_arg(callback, value)
    return None


def dispatch_setting_click(plugin_id: str, callback_id: str) -> None:
    """Invoke the on_click callback registered under *callback_id*."""
    record = plugins.get(plugin_id)
    if record is None:
        return None
    callback = record.click_callbacks.get(callback_id)
    if callback is not None:
        _call_with_optional_arg(callback, None)
    return None


# ---------------------------------------------------------------------------
# Dev server (port 42690; started by the engine in developer mode)
# ---------------------------------------------------------------------------

_dev_server_started = False


def start_dev_server() -> None:
    """Start the TCP/JSON dev server once (guarded, never raises)."""
    global _dev_server_started
    if _dev_server_started:
        return None
    _dev_server_started = True
    try:
        import dev_server
        dev_server.start()
    except Exception as e:
        print(f"[exteraless:plugin_loader] dev server start failed: {e}",
              file=sys.stderr)
    return None


# Re-expose previously installed shared libs (pip_controller) at engine start.
if pip_controller is not None:
    try:
        pip_controller.restore_sys_path()
    except Exception as e:
        print(f"[exteraless:plugin_loader] restore_sys_path failed: {e}",
              file=sys.stderr)
