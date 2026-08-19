"""Имена Java-классов exteraGram → имена классов exteraless.

Плагины каталога написаны под exteraGram и берут его классы по полному имени:
``find_class("com.exteragram.messenger.pillstack.core.PillRegistry")``,
``jclass(...)`` или ``from com.exteragram.messenger.plugins import PluginsController``.
У нас тот же код лежит в ``app.exteraless.*``, поэтому без подстановки плагин
получает ``None`` и падает на первом же обращении.

Подстановка работает только на имени: если класса с полученным именем у нас нет,
вызывающий получает прежний отказ. Разрешения проверяются уже по нашему имени —
правила в ``plugin_loader._JAVA_CLASS_RULES`` записаны для ``app.exteraless.*``.
"""

import importlib as _importlib
import importlib.util as _importlib_util
import sys as _sys
import types as _types

ROOT = "com.exteragram.messenger"

_EXACT = {
    "com.exteragram.messenger.pillstack.ui.pills.weather.WeatherPill":
        "app.exteraless.pillstack.pills.WeatherPill",
    "com.exteragram.messenger.pillstack.ui.PillStackPreferencesActivity":
        "app.exteraless.pillstack.PillStackSettingsActivity",
    "com.exteragram.messenger.pillstack.ui.PillStackLayout":
        "app.exteraless.pillstack.PillStackView",
    "com.exteragram.messenger.pillstack.ui.pills.weather.WeatherPreferencesActivity":
        "app.exteraless.pillstack.pills.weather.WeatherSettingsActivity",
}

_PREFIXES = (
    ("com.exteragram.messenger.pillstack.core.", "app.exteraless.pillstack."),
    ("com.exteragram.messenger.pillstack.ui.pills.", "app.exteraless.pillstack.pills."),
    ("com.exteragram.messenger.pillstack.ui.", "app.exteraless.pillstack."),
    ("com.exteragram.messenger.preferences.", "app.exteraless.settings."),
    ("com.exteragram.messenger.plugins.", "app.exteraless.plugins."),
    ("com.exteragram.messenger.icons.", "app.exteraless.icons."),
    ("com.exteragram.messenger.camera.", "app.exteraless.camera."),
    ("com.exteragram.messenger.backup.", "app.exteraless.backup."),
    ("com.exteragram.messenger.feed.", "app.exteraless.feed."),
    ("com.exteragram.messenger.drawer.", "app.exteraless.drawer."),
    ("com.exteragram.messenger.components.", "app.exteraless.components."),
    ("com.exteragram.messenger.utils.", "app.exteraless.utils."),
)


def resolve(name):
    """Наше имя класса для имени exteraGram; чужие имена возвращаются как есть."""
    if not isinstance(name, str) or not name.startswith(ROOT + "."):
        return name
    exact = _EXACT.get(name)
    if exact is not None:
        return exact
    outer, sep, nested = name.partition("$")
    exact = _EXACT.get(outer)
    if exact is not None:
        return exact + sep + nested
    for old, new in _PREFIXES:
        if outer.startswith(old):
            return new + outer[len(old):] + sep + nested
    return name


def is_alias(name):
    """Стоит ли пытаться подставлять это имя."""
    return isinstance(name, str) and name.startswith(ROOT + ".")


def _find_class(name):
    try:
        from hook_utils import find_class
        return find_class(name)
    except Exception:
        return None


class _AliasModule(_types.ModuleType):
    """Пакет-заглушка: атрибут сначала пробуется как класс, потом как подпакет."""

    def __getattr__(self, attr):
        if attr.startswith("__"):
            raise AttributeError(attr)
        full = self.__name__ + "." + attr
        found = _find_class(full)
        if found is not None:
            return found
        if attr[:1].isupper():
            raise AttributeError(attr)
        try:
            return _importlib.import_module(full)
        except Exception:
            raise AttributeError(attr)


class _AliasFinder:
    """sys.meta_path-финдер на ``com.exteragram.*``.

    Нужен для формы ``from com.exteragram.messenger.plugins import PluginsController``:
    она идёт мимо find_class и jclass, в машинерию импорта.
    """

    PACKAGE = "com.exteragram"

    def find_spec(self, fullname, path=None, target=None):
        if fullname != self.PACKAGE and not fullname.startswith(self.PACKAGE + "."):
            return None
        if fullname.rpartition(".")[2][:1].isupper():
            return None
        return _importlib_util.spec_from_loader(fullname, _AliasLoader(), is_package=True)


class _AliasLoader:

    def create_module(self, spec):
        module = _AliasModule(spec.name)
        module.__path__ = []
        return module

    def exec_module(self, module):
        return None


def _ensure_root_package():
    """Создаёт пакет ``com``, если его не даёт Chaquopy.

    На устройстве ``com`` существует (com.google, com.android), и подменять его
    нельзя. Заглушка ставится только там, где импорт вообще не проходит.
    """
    try:
        _importlib.import_module("com")
        return
    except Exception:
        pass
    module = _AliasModule("com")
    module.__path__ = []
    _sys.modules["com"] = module


def install_import_hook():
    """Ставит финдер в sys.meta_path. Идемпотентно, ничего не бросает."""
    try:
        if any(isinstance(finder, _AliasFinder) for finder in _sys.meta_path):
            return
        _sys.meta_path.insert(0, _AliasFinder())
        _ensure_root_package()
    except Exception:
        pass
