"""File system helpers for plugins — part of the exteraless plugin SDK.

Directory resolution uses the Android Context (filesDir/cacheDir) and
FileLoader media directories. Media directories fall back to subdirectories
of filesDir when the real media storage is unavailable (e.g. no storage
permission); the fallback is documented per function.

Also hosts FilesController: per-extension interception of the client's
file-open flow (PLUGINS-API.md §file-utils).
"""

import json
import os
import threading
from dataclasses import dataclass
from typing import Callable, List, Optional


def _context():
    from java import jclass
    return jclass("org.telegram.messenger.ApplicationLoader").applicationContext


def get_plugins_dir() -> str:
    """Absolute path of the plugin directory (filesDir/plugins)."""
    from app.exteraless.plugins import PythonBridge
    return str(PythonBridge.getPluginsDir())


def get_files_dir() -> Optional[str]:
    """Absolute path of the app-private files directory."""
    try:
        return str(_context().getFilesDir().getAbsolutePath())
    except Exception:
        try:
            return os.path.dirname(get_plugins_dir())
        except Exception:
            return None


def get_cache_dir() -> Optional[str]:
    """Absolute path of the app-private cache directory."""
    try:
        return str(_context().getCacheDir().getAbsolutePath())
    except Exception:
        return None


def _media_dir(constant_name: str, fallback_name: str) -> Optional[str]:
    """Resolve a FileLoader media directory, falling back to filesDir/<fallback_name>."""
    try:
        from java import jclass
        FileLoader = jclass("org.telegram.messenger.FileLoader")
        directory = FileLoader.getDirectory(getattr(FileLoader, constant_name))
        if directory is not None:
            path = str(directory.getAbsolutePath())
            ensure_dir_exists(path)
            return path
    except Exception:
        pass
    base = get_files_dir()
    if base is None:
        return None
    path = os.path.join(base, fallback_name)
    ensure_dir_exists(path)
    return path


def get_images_dir() -> Optional[str]:
    """Telegram images directory (fallback: filesDir/images)."""
    return _media_dir("MEDIA_DIR_IMAGE", "images")


def get_videos_dir() -> Optional[str]:
    """Telegram videos directory (fallback: filesDir/videos)."""
    return _media_dir("MEDIA_DIR_VIDEO", "videos")


def get_audios_dir() -> Optional[str]:
    """Telegram audios directory (fallback: filesDir/audios)."""
    return _media_dir("MEDIA_DIR_AUDIO", "audios")


def get_documents_dir() -> Optional[str]:
    """Telegram documents directory (fallback: filesDir/documents)."""
    return _media_dir("MEDIA_DIR_DOCUMENT", "documents")


def ensure_dir_exists(path: str) -> str:
    """Create *path* (and parents) if missing; returns *path*."""
    os.makedirs(path, exist_ok=True)
    return path


def list_dir(path: str, recursive: bool = False, include_files: bool = True,
             include_dirs: bool = False,
             extensions: Optional[List[str]] = None) -> List[str]:
    """List directory contents as sorted absolute paths.

    extensions, when given, filters files by suffix (with or without the dot,
    case-insensitive).
    """
    result: List[str] = []
    if not path or not os.path.isdir(path):
        return result

    allowed = None
    if extensions:
        allowed = {
            ext.lower() if ext.startswith(".") else "." + ext.lower()
            for ext in extensions
        }

    def _extension_ok(name: str) -> bool:
        if allowed is None:
            return True
        return os.path.splitext(name)[1].lower() in allowed

    if recursive:
        for root, dirs, files in os.walk(path):
            if include_dirs:
                result.extend(os.path.join(root, d) for d in dirs)
            if include_files:
                result.extend(
                    os.path.join(root, f) for f in files if _extension_ok(f)
                )
    else:
        for name in os.listdir(path):
            full = os.path.join(path, name)
            if os.path.isdir(full):
                if include_dirs:
                    result.append(full)
            elif include_files and _extension_ok(name):
                result.append(full)
    return sorted(result)


def write_file(path: str, content) -> bool:
    """Write str (UTF-8) or bytes to *path*. Does NOT create parent directories."""
    try:
        if isinstance(content, (bytes, bytearray)):
            with open(path, "wb") as handle:
                handle.write(bytes(content))
        else:
            with open(path, "w", encoding="utf-8") as handle:
                handle.write(str(content))
        return True
    except Exception:
        return False


def read_file(path: str) -> Optional[str]:
    """Read *path* as UTF-8 text; None on any error."""
    try:
        with open(path, "r", encoding="utf-8") as handle:
            return handle.read()
    except Exception:
        return None


def delete_file(path: str) -> bool:
    """Delete a single file (not a directory); True on success."""
    try:
        os.remove(path)
        return True
    except Exception:
        return False


def write_file_bytes(path: str, content: bytes) -> bool:
    """Write bytes to *path*. Does NOT create parent directories."""
    try:
        with open(path, "wb") as handle:
            handle.write(bytes(content))
        return True
    except Exception:
        return False


def read_file_bytes(path: str) -> Optional[bytes]:
    """Read *path* as bytes; None on any error."""
    try:
        with open(path, "rb") as handle:
            return handle.read()
    except Exception:
        return None


# ---------------------------------------------------------------------------
# FilesController (per-extension file-open interception, PLUGINS-API.md §8)
# ---------------------------------------------------------------------------

def _plugin_services():
    from app.exteraless.plugins import PluginServices
    return PluginServices


def _current_plugin_id() -> Optional[str]:
    try:
        from extera_utils import plugin_loader
        return plugin_loader.current_plugin_id()
    except Exception:
        return None


def _context_to_dict(context) -> dict:
    """Accept a java.util.Map (Chaquopy) or a plain dict; return a dict."""
    if context is None:
        return {}
    if isinstance(context, dict):
        return dict(context)
    try:  # java.util.Map
        return {str(key): context.get(key) for key in context.keySet()}
    except Exception:
        pass
    try:
        return dict(context)
    except Exception:
        return {}


class _FileOpenCallback:
    """PyObject handed to Java; called with the OnClickArgs context Map."""

    def __init__(self, fn: Callable):
        self._fn = fn

    def __call__(self, args):
        return self._fn(_context_to_dict(args))


class _FilesControllerMeta(type):
    @property
    def SUPPORT_ICONS(cls) -> bool:
        return cls.icons_supported()


class FilesController(metaclass=_FilesControllerMeta):
    """Interception of file opening by extension (see PLUGINS-API.md).

        secret = FilesController.register(FilesController.FileInfo(
            ext="zip", on_click=on_zip_click,
            whitelist_places=[FilesController.Place.ChatActivity],
            get_icon=make_drawable,      # only if FilesController.SUPPORT_ICONS
        ))
        FilesController.unregister("zip", secret)

    The plain form ``register("zip", on_click=..., whitelist_places=[...])``
    is accepted as well. The callback receives an args dict with the
    OnClickArgs keys (place, file, file_name, message, activity,
    parent_fragment) and replaces the default open flow.
    """

    class ExtensionAlreadyRegistered(Exception):
        pass

    class ExtensionNotRegistered(Exception):
        pass

    class SecretInvalid(Exception):
        pass

    class Place:
        """Where the file is being opened; values are Java enum names."""
        UNKNOWN = "UNKNOWN"
        ChatActivity = "ChatActivity"
        FilteredSearchView = "FilteredSearchView"
        SharedMediaLayout = "SharedMediaLayout"
        SearchDownloadsContainer = "SearchDownloadsContainer"
        ChannelAdminLogActivity = "ChannelAdminLogActivity"

    @dataclass
    class FileInfo:
        ext: str
        on_click: Optional[Callable] = None
        whitelist_places: Optional[List[str]] = None
        blacklist_places: Optional[List[str]] = None
        get_icon: Optional[Callable] = None

    _lock = threading.RLock()
    # (plugin_id, ext) -> {"secret", "on_click", "get_icon"}
    _registrations = {}

    # ---- registration ----

    @staticmethod
    def register(info=None, on_click: Optional[Callable] = None,
                 whitelist_places: Optional[List] = None,
                 blacklist_places: Optional[List] = None,
                 get_icon: Optional[Callable] = None,
                 whitelist: Optional[List] = None, blacklist: Optional[List] = None,
                 icon: bool = False) -> str:
        """Register a handler for *ext*; returns the handler secret (keep it)."""
        if isinstance(info, FilesController.FileInfo):
            ext = info.ext
            on_click = info.on_click if on_click is None else on_click
            whitelist_places = info.whitelist_places \
                if whitelist_places is None else whitelist_places
            blacklist_places = info.blacklist_places \
                if blacklist_places is None else blacklist_places
            get_icon = info.get_icon if get_icon is None else get_icon
        else:
            ext = info
        whitelist_places = whitelist if whitelist is not None else whitelist_places
        blacklist_places = blacklist if blacklist is not None else blacklist_places

        if not ext:
            raise ValueError("register() requires a file extension")
        ext = str(ext).lstrip(".").lower()
        if not callable(on_click):
            raise ValueError("register() requires an on_click callback")
        if whitelist_places and blacklist_places:
            raise ValueError("whitelist_places and blacklist_places are mutually exclusive")

        plugin_id = _current_plugin_id()
        if not plugin_id:
            raise RuntimeError(
                "FilesController.register must be called from a plugin context "
                "(on_plugin_load / a hook callback)")

        key = (plugin_id, ext)
        with FilesController._lock:
            if key in FilesController._registrations:
                raise FilesController.ExtensionAlreadyRegistered(
                    f"extension {ext!r} is already registered by {plugin_id!r}")

        whitelist_json = json.dumps([str(p) for p in whitelist_places]) \
            if whitelist_places else None
        blacklist_json = json.dumps([str(p) for p in blacklist_places]) \
            if blacklist_places else None
        has_icon = bool(icon) or get_icon is not None
        callback = _FileOpenCallback(on_click)

        services = _plugin_services()
        try:
            # Preferred: 6-arg form delivering the callback PyObject to Java.
            secret = services.registerFileHandler(plugin_id, ext, whitelist_json,
                                                  blacklist_json, has_icon, callback)
        except Exception:
            # Current Java signature (5-arg, no callback param): register and
            # keep the callback Python-side for the dispatch path.
            secret = services.registerFileHandler(plugin_id, ext, whitelist_json,
                                                  blacklist_json, has_icon)
        if secret is None:
            raise RuntimeError("registerFileHandler failed on the Java side")
        secret = str(secret)
        with FilesController._lock:
            FilesController._registrations[key] = {
                "secret": secret, "on_click": on_click, "get_icon": get_icon,
            }
        return secret

    @staticmethod
    def unregister(ext: str, secret: str) -> None:
        """Remove the handler for *ext*; the secret from register() is required."""
        ext = str(ext).lstrip(".").lower()
        plugin_id = _current_plugin_id()
        with FilesController._lock:
            key = (plugin_id, ext)
            entry = FilesController._registrations.get(key)
            if entry is None and plugin_id is None:
                # No plugin context: fall back to a unique ext match.
                matches = [k for k in FilesController._registrations if k[1] == ext]
                if len(matches) == 1:
                    key = matches[0]
                    entry = FilesController._registrations.get(key)
            if entry is None:
                raise FilesController.ExtensionNotRegistered(
                    f"extension {ext!r} is not registered")
            if entry["secret"] != str(secret):
                raise FilesController.SecretInvalid(
                    f"invalid secret for extension {ext!r}")
            del FilesController._registrations[key]
        try:
            _plugin_services().unregisterFileHandler(key[0], ext, str(secret))
        except Exception:
            pass

    @staticmethod
    def icons_supported() -> bool:
        """Whether custom file icons are supported by this build."""
        try:
            return bool(_plugin_services().fileIconsSupported())
        except Exception:
            return False


# Called from BasePlugin._cleanup_resources() on plugin unload.
def _unregister_all_for_plugin(plugin_id: str):
    if not plugin_id:
        return
    with FilesController._lock:
        entries = [(key, entry) for key, entry in FilesController._registrations.items()
                   if key[0] == plugin_id]
        for key, _ in entries:
            del FilesController._registrations[key]
    for (pid, ext), entry in entries:
        try:
            _plugin_services().unregisterFileHandler(pid, ext, entry["secret"])
        except Exception:
            pass
