"""Static plugin metadata reader (AST-based, no module execution) — exteraless plugin SDK.

Pure Python: importable and testable on a host interpreter without Chaquopy.
"""

import ast
import json
import re
import sys
from typing import Any, Dict, Optional


class PluginMetadataError(Exception):
    """Raised when a plugin's metadata block is missing or invalid."""


# Metadata keys recognized at the module top level of a plugin file.
_METADATA_KEYS = (
    "__id__", "__name__", "__description__", "__author__", "__version__",
    "__icon__", "__app_version__", "__sdk_version__", "__min_version__",
    "__beta__", "__requirements__",
)

_ID_PATTERN = re.compile(r"^[A-Za-z][A-Za-z0-9_-]{1,31}$")


def _extract_constants(path: str) -> Dict[str, Any]:
    """Pull top-level literal assignments out of a plugin source without running it."""
    try:
        with open(path, "r", encoding="utf-8") as handle:
            source = handle.read()
    except OSError as e:
        raise PluginMetadataError(f"cannot read plugin file {path!r}: {e}")

    try:
        tree = ast.parse(source, filename=path)
    except SyntaxError as e:
        raise PluginMetadataError(f"plugin file {path!r} has a syntax error: {e}")

    constants: Dict[str, Any] = {}
    for node in tree.body:
        target = None
        value_node = None
        if isinstance(node, ast.Assign) and len(node.targets) == 1 \
                and isinstance(node.targets[0], ast.Name):
            target = node.targets[0].id
            value_node = node.value
        elif isinstance(node, ast.AnnAssign) and isinstance(node.target, ast.Name) \
                and node.value is not None:
            target = node.target.id
            value_node = node.value
        if target is None or target not in _METADATA_KEYS or target in constants:
            continue
        try:
            constants[target] = ast.literal_eval(value_node)
        except (ValueError, SyntaxError, TypeError, MemoryError):
            # Dynamic / non-literal metadata is ignored by design.
            pass
    return constants


def _validate_plugin_id(plugin_id: Any) -> str:
    if plugin_id is None:
        raise PluginMetadataError("plugin metadata is missing the required __id__ constant")
    if not isinstance(plugin_id, str):
        raise PluginMetadataError(f"__id__ must be a string, got {type(plugin_id).__name__}")
    if not _ID_PATTERN.match(plugin_id):
        raise PluginMetadataError(
            f"invalid __id__ {plugin_id!r}: must be 2-32 characters, start with a letter "
            "and contain only Latin letters, digits, '_' or '-'"
        )
    if plugin_id in sys.stdlib_module_names:
        raise PluginMetadataError(
            f"__id__ {plugin_id!r} collides with a Python standard library module name"
        )
    # A plugin id that matches an importable module name is legal — the
    # reference allows it and plugin modules are loaded under their own name,
    # not merged into the global import namespace. Refusing it here rejected
    # real published plugins (e.g. "qrcode") for no gain.
    return plugin_id


def read_metadata(path: str) -> Dict[str, Any]:
    """Read and validate plugin metadata from *path* without executing the module.

    Returns a dict with keys: id, name, description, author, version, icon,
    app_version, sdk_version, beta, requirements.
    Raises PluginMetadataError with a human-readable message on any problem.
    """
    constants = _extract_constants(path)

    plugin_id = _validate_plugin_id(constants.get("__id__"))

    name = constants.get("__name__")
    if name is None:
        raise PluginMetadataError("plugin metadata is missing the required __name__ constant")
    if not isinstance(name, str) or not name.strip():
        raise PluginMetadataError("__name__ must be a non-empty string")

    version = constants.get("__version__", "1.0")
    if not isinstance(version, str):
        version = str(version)

    app_version: Optional[str] = constants.get("__app_version__")
    min_version = constants.get("__min_version__")
    if app_version is None and min_version is not None:
        # Legacy alias: __min_version__ = "12.5.1" means __app_version__ = ">=12.5.1".
        app_version = f">={min_version}"

    requirements = constants.get("__requirements__") or []
    # Published plugins write both forms: a list, and a bare string for a single
    # dependency (``__requirements__ = "cachetools"``). Rejecting the string
    # would refuse the plugin outright, so normalise instead.
    if isinstance(requirements, str):
        requirements = [requirements] if requirements.strip() else []
    if not isinstance(requirements, (list, tuple)) \
            or not all(isinstance(item, str) for item in requirements):
        raise PluginMetadataError("__requirements__ must be a string or a list of PEP 508 strings")

    return {
        "id": plugin_id,
        "name": name,
        "description": constants.get("__description__") or "",
        "author": constants.get("__author__") or "",
        "version": version,
        "icon": constants.get("__icon__"),
        "app_version": app_version,
        "sdk_version": constants.get("__sdk_version__"),
        "beta": bool(constants.get("__beta__", False)),
        "requirements": list(requirements),
    }


def read_metadata_json(path: str) -> str:
    """Contract wrapper called from Java: always returns a JSON string."""
    try:
        meta = read_metadata(path)
        return json.dumps({"ok": True, "meta": meta}, ensure_ascii=False)
    except PluginMetadataError as e:
        return json.dumps({"ok": False, "error": str(e)}, ensure_ascii=False)
    except Exception as e:
        return json.dumps({"ok": False, "error": f"{type(e).__name__}: {e}"},
                          ensure_ascii=False)
