"""Что плагин может делать — по исходнику, без его запуска.

Зачем: `__permissions__` объявляет меньшинство плагинов. Из 512 плагинов двух
каталогов большинство не объявляет ничего, и диалог установки показывал либо
пусто, либо «получит всё». Ни то ни другое не даёт человеку решить.

Улики называются техническими именами (`requests`, `SendMessagesHelper`,
`sqlite3`): они одинаково читаются при любом языке интерфейса и указывают
прямо на строку в исходнике, которую можно проверить.

Разбор идёт AST-парсером и поиском по тексту, код не исполняется. Это
догадка по исходнику, а не гарантия: обфускация и вычисляемые имена его
обходят. Поэтому итог называется «что плагин может делать», а не «что он
делает», и решение остаётся за пользователем.

Обратная сторона тоже важна: ненайденное не значит невозможное. Настоящую
границу держат гейты во время работы (audit_gate, PluginSinkGate); этот
разбор — только чтобы спросить разрешения осмысленно, а не списком из восьми
галочек.
"""

import ast
import re
import zipfile
from typing import Dict, List

PERM_MESSAGES_READ = "messages.read"
PERM_MESSAGES_SEND = "messages.send"
PERM_NETWORK = "network"
PERM_FILES = "files"
PERM_INTENTS = "intents"
PERM_SETTINGS = "settings"
PERM_HOOKS = "hooks"
PERM_NATIVE = "native"

#: Признак -> (разрешение, человеческое имя улики).
#: Ключ ищется как подстрока исходника; порядок не важен, дубли схлопываются.
_MARKERS = (
    # ---- сеть ----
    ("import requests", PERM_NETWORK, "requests"),
    ("urllib.request", PERM_NETWORK, "urllib"),
    ("http.client", PERM_NETWORK, "http.client"),
    ("import socket", PERM_NETWORK, "socket"),
    ("websocket", PERM_NETWORK, "websocket"),
    ("java.net", PERM_NETWORK, "java.net"),
    ("HttpURLConnection", PERM_NETWORK, "HttpURLConnection"),
    ("OkHttpClient", PERM_NETWORK, "okhttp"),
    ("WebView", PERM_NETWORK, "WebView"),
    ("DownloadManager", PERM_NETWORK, "DownloadManager"),
    ("loadHttpFile", PERM_NETWORK, "loadHttpFile"),
    ("setDataSource", PERM_NETWORK, "setDataSource"),
    # ---- чтение переписки ----
    ("MessagesStorage", PERM_MESSAGES_READ, "MessagesStorage"),
    ("SQLiteDatabase", PERM_MESSAGES_READ, "MessagesStorage"),
    ("queryFinalized", PERM_MESSAGES_READ, "SQLite"),
    ("on_update", PERM_MESSAGES_READ, "on_update"),
    ("add_request_hook", PERM_MESSAGES_READ, "request hooks"),
    ("get_messages", PERM_MESSAGES_READ, "getMessages"),
    ("getMessages", PERM_MESSAGES_READ, "getMessages"),
    # ---- отправка ----
    ("SendMessagesHelper", PERM_MESSAGES_SEND, "SendMessagesHelper"),
    ("send_message(", PERM_MESSAGES_SEND, "send_message"),
    ("send_text(", PERM_MESSAGES_SEND, "send_text"),
    ("on_send_message", PERM_MESSAGES_SEND, "on_send_message"),
    ("TL_messages_send", PERM_MESSAGES_SEND, "TL_messages_send"),
    ("TL_messages_edit", PERM_MESSAGES_SEND, "TL_messages_edit"),
    ("TL_messages_delete", PERM_MESSAGES_SEND, "TL_messages_delete"),
    ("ConnectionsManager", PERM_MESSAGES_SEND, "ConnectionsManager"),
    # ---- файлы ----
    ("java.io.File", PERM_FILES, "java.io.File"),
    ("FileOutputStream", PERM_FILES, "FileOutputStream"),
    ("ContentResolver", PERM_FILES, "ContentResolver"),
    ("MediaStore", PERM_FILES, "MediaStore"),
    ("import shutil", PERM_FILES, "shutil"),
    ("os.remove", PERM_FILES, "os.remove"),
    ("os.listdir", PERM_FILES, "os.listdir"),
    ("sqlite3", PERM_FILES, "sqlite3"),
    # ---- интенты ----
    ("startActivity", PERM_INTENTS, "startActivity"),
    ("android.content.Intent", PERM_INTENTS, "Intent"),
    ("register_intent_handler", PERM_INTENTS, "intent hooks"),
    # ---- настройки приложения ----
    ("NekoConfig", PERM_SETTINGS, "app config"),
    ("NaConfig", PERM_SETTINGS, "app config"),
    ("ExteraConfig", PERM_SETTINGS, "app config"),
    # ---- хуки и код на ходу ----
    ("MethodHook", PERM_HOOKS, "Xposed"),
    ("hook_method", PERM_HOOKS, "Xposed"),
    ("XposedBridge", PERM_HOOKS, "Xposed"),
    ("InMemoryDexClassLoader", PERM_HOOKS, "DexClassLoader"),
    ("DexClassLoader", PERM_HOOKS, "DexClassLoader"),
    ("generate_proxy_class", PERM_HOOKS, "class proxy"),
    ("deoptimize", PERM_HOOKS, "deoptimize"),
    # ---- нативный код ----
    ("import ctypes", PERM_NATIVE, "ctypes"),
    ("ctypes.CDLL", PERM_NATIVE, "ctypes"),
    ("CDLL(", PERM_NATIVE, "ctypes"),
    ("loadLibrary", PERM_NATIVE, "loadLibrary"),
    (".so\"", PERM_NATIVE, "native library"),
    (".so'", PERM_NATIVE, "native library"),
)

#: Файл больше этого не разбираем: плагины такого размера не встречаются,
#: а на упавшем установщике польза от разбора отрицательная.
_MAX_SOURCE_BYTES = 4 * 1024 * 1024


_MAX_ARCHIVE_BYTES = 16 * 1024 * 1024

_SOURCE_MEMBER_SUFFIXES = (".py", ".pyc")


def _merge(target: Dict[str, List[str]], addition: Dict[str, List[str]]) -> None:
    for permission, evidence in addition.items():
        bucket = target.setdefault(permission, [])
        for item in evidence:
            if item not in bucket:
                bucket.append(item)


def _scan_source(source: str) -> Dict[str, List[str]]:
    found: Dict[str, List[str]] = {}
    for marker, permission, evidence in _MARKERS:
        if marker in source and evidence not in found.setdefault(permission, []):
            found[permission].append(evidence)

    _merge(found, _scan_imports(source))

    return {perm: names for perm, names in found.items() if names}


def _scan_archive(path: str) -> Dict[str, List[str]]:
    found: Dict[str, List[str]] = {}
    budget = _MAX_ARCHIVE_BYTES
    with zipfile.ZipFile(path) as archive:
        for info in archive.infolist():
            if budget <= 0:
                break
            if info.is_dir() or not info.filename.endswith(_SOURCE_MEMBER_SUFFIXES):
                continue
            try:
                with archive.open(info) as handle:
                    raw = handle.read(min(_MAX_SOURCE_BYTES, budget))
            except Exception:
                continue
            budget -= len(raw)
            _merge(found, _scan_source(raw.decode("utf-8", errors="replace")))
    return {perm: names for perm, names in found.items() if names}


def scan(path: str) -> Dict[str, List[str]]:
    """{разрешение: [улики]} — что плагин по исходнику может делать."""
    try:
        if zipfile.is_zipfile(path):
            return _scan_archive(path)
        with open(path, "rb") as handle:
            raw = handle.read(_MAX_SOURCE_BYTES)
        source = raw.decode("utf-8", errors="replace")
    except Exception:
        return {}

    return _scan_source(source)


#: Что именно импортируют из пакетов мессенджера. Пакет целиком ни о чём не
#: говорит: из org.telegram.messenger берут и MessagesController, и
#: AndroidUtilities.dp() — по первому спрашивать разрешение нужно, по второму
#: нет, иначе диалог будет требовать доступ к переписке у каждого плагина.
_IMPORTED_NAMES = {
    "MessagesController": (PERM_MESSAGES_READ, "MessagesController"),
    "MessagesStorage": (PERM_MESSAGES_READ, "MessagesStorage"),
    "MessageObject": (PERM_MESSAGES_READ, "MessageObject"),
    "NotificationCenter": (PERM_MESSAGES_READ, "NotificationCenter"),
    "SendMessagesHelper": (PERM_MESSAGES_SEND, "SendMessagesHelper"),
    "ConnectionsManager": (PERM_MESSAGES_SEND, "ConnectionsManager"),
    "ContentResolver": (PERM_FILES, "ContentResolver"),
    "MediaStore": (PERM_FILES, "MediaStore"),
    "WebView": (PERM_NETWORK, "WebView"),
    "InMemoryDexClassLoader": (PERM_HOOKS, "DexClassLoader"),
    "DexClassLoader": (PERM_HOOKS, "DexClassLoader"),
    "XposedBridge": (PERM_HOOKS, "Xposed"),
    "XposedHelpers": (PERM_HOOKS, "Xposed"),
}


_IMPORTED_MODULES = {
    "ctypes": (PERM_NATIVE, "ctypes"),
    "requests": (PERM_NETWORK, "requests"),
    "httpx": (PERM_NETWORK, "httpx"),
    "aiohttp": (PERM_NETWORK, "aiohttp"),
    "urllib": (PERM_NETWORK, "urllib"),
    "urllib3": (PERM_NETWORK, "urllib"),
    "http": (PERM_NETWORK, "http.client"),
    "socket": (PERM_NETWORK, "socket"),
    "socketserver": (PERM_NETWORK, "socket"),
    "ftplib": (PERM_NETWORK, "ftplib"),
    "smtplib": (PERM_NETWORK, "smtplib"),
    "telnetlib": (PERM_NETWORK, "telnetlib"),
    "websocket": (PERM_NETWORK, "websocket"),
    "websockets": (PERM_NETWORK, "websocket"),
    "subprocess": (PERM_NATIVE, "subprocess"),
}


def _note_module(result: Dict[str, List[str]], name: str) -> None:
    rule = _IMPORTED_MODULES.get(name.partition(".")[0])
    if rule is None:
        return
    permission, evidence = rule
    bucket = result.setdefault(permission, [])
    if evidence not in bucket:
        bucket.append(evidence)


def _scan_imports(source: str) -> Dict[str, List[str]]:
    """Импорты: важно не откуда, а что именно."""
    result: Dict[str, List[str]] = {}
    try:
        tree = ast.parse(source)
    except Exception:
        # Битый Python разберём регулярным выражением: диалог установки всё
        # равно должен что-то показать.
        for module, names in re.findall(
                r"^\s*from\s+([A-Za-z0-9_.]+)\s+import\s+([^\n#]+)", source, re.M):
            _note_module(result, module)
            for name in names.split(","):
                _note_name(result, name.strip().split(" as ")[0])
        for names in re.findall(r"^\s*import\s+([^\n#]+)", source, re.M):
            for name in names.split(","):
                _note_module(result, name.strip().split(" as ")[0])
        return result

    for node in ast.walk(tree):
        if isinstance(node, ast.ImportFrom):
            if node.module:
                _note_module(result, node.module)
            for alias in node.names:
                _note_name(result, alias.name)
        elif isinstance(node, ast.Import):
            for alias in node.names:
                _note_module(result, alias.name)
                _note_name(result, alias.name.rpartition(".")[2])
    return result


def _note_name(result: Dict[str, List[str]], name: str) -> None:
    rule = _IMPORTED_NAMES.get(name)
    if rule is None:
        return
    permission, evidence = rule
    target = result.setdefault(permission, [])
    if evidence not in target:
        target.append(evidence)


def scan_json(path: str) -> str:
    """Для Java-стороны: JSON вида {"network": ["requests", ...], ...}."""
    import json
    try:
        return json.dumps(scan(path), ensure_ascii=False)
    except Exception as e:
        return json.dumps({"error": str(e)}, ensure_ascii=False)
