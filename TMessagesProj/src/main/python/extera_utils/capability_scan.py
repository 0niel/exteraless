"""Что плагин может делать — по исходнику, без его запуска.

Зачем: `__permissions__` объявляет меньшинство плагинов. Из 512 плагинов двух
каталогов большинство не объявляет ничего, и диалог установки показывал либо
пусто, либо «получит всё». Ни то ни другое не даёт человеку решить.

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
from typing import Dict, List

PERM_MESSAGES_READ = "messages.read"
PERM_MESSAGES_SEND = "messages.send"
PERM_NETWORK = "network"
PERM_FILES = "files"
PERM_INTENTS = "intents"
PERM_SETTINGS = "settings"
PERM_HOOKS = "hooks"

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
    ("loadHttpFile", PERM_NETWORK, "загрузка по ссылке"),
    ("setDataSource", PERM_NETWORK, "проигрывание по ссылке"),
    # ---- чтение переписки ----
    ("MessagesStorage", PERM_MESSAGES_READ, "база сообщений"),
    ("SQLiteDatabase", PERM_MESSAGES_READ, "база сообщений"),
    ("queryFinalized", PERM_MESSAGES_READ, "запрос к базе"),
    ("on_update", PERM_MESSAGES_READ, "перехват апдейтов"),
    ("add_request_hook", PERM_MESSAGES_READ, "перехват запросов"),
    ("get_messages", PERM_MESSAGES_READ, "чтение сообщений"),
    ("getMessages", PERM_MESSAGES_READ, "чтение сообщений"),
    # ---- отправка ----
    ("SendMessagesHelper", PERM_MESSAGES_SEND, "SendMessagesHelper"),
    ("send_message(", PERM_MESSAGES_SEND, "send_message"),
    ("send_text(", PERM_MESSAGES_SEND, "send_text"),
    ("on_send_message", PERM_MESSAGES_SEND, "перехват отправки"),
    ("TL_messages_send", PERM_MESSAGES_SEND, "запрос отправки"),
    ("TL_messages_edit", PERM_MESSAGES_SEND, "запрос правки"),
    ("TL_messages_delete", PERM_MESSAGES_SEND, "запрос удаления"),
    ("ConnectionsManager", PERM_MESSAGES_SEND, "прямые запросы к серверу"),
    # ---- файлы ----
    ("java.io.File", PERM_FILES, "java.io.File"),
    ("FileOutputStream", PERM_FILES, "запись файла"),
    ("ContentResolver", PERM_FILES, "доступ к файлам устройства"),
    ("MediaStore", PERM_FILES, "галерея"),
    ("import shutil", PERM_FILES, "shutil"),
    ("os.remove", PERM_FILES, "удаление файлов"),
    ("os.listdir", PERM_FILES, "просмотр каталогов"),
    ("sqlite3", PERM_FILES, "sqlite3"),
    # ---- интенты ----
    ("startActivity", PERM_INTENTS, "запуск экранов"),
    ("android.content.Intent", PERM_INTENTS, "интенты"),
    ("register_intent_handler", PERM_INTENTS, "перехват интентов"),
    # ---- настройки приложения ----
    ("NekoConfig", PERM_SETTINGS, "настройки приложения"),
    ("NaConfig", PERM_SETTINGS, "настройки приложения"),
    ("ExteraConfig", PERM_SETTINGS, "настройки приложения"),
    # ---- хуки и код на ходу ----
    ("MethodHook", PERM_HOOKS, "Xposed-хуки"),
    ("hook_method", PERM_HOOKS, "Xposed-хуки"),
    ("XposedBridge", PERM_HOOKS, "Xposed-хуки"),
    ("InMemoryDexClassLoader", PERM_HOOKS, "загрузка своего кода"),
    ("DexClassLoader", PERM_HOOKS, "загрузка своего кода"),
    ("generate_proxy_class", PERM_HOOKS, "подмена классов"),
    ("deoptimize", PERM_HOOKS, "деоптимизация методов"),
)

#: Файл больше этого не разбираем: плагины такого размера не встречаются,
#: а на упавшем установщике польза от разбора отрицательная.
_MAX_SOURCE_BYTES = 4 * 1024 * 1024


def scan(path: str) -> Dict[str, List[str]]:
    """{разрешение: [улики]} — что плагин по исходнику может делать."""
    try:
        with open(path, "rb") as handle:
            raw = handle.read(_MAX_SOURCE_BYTES)
        source = raw.decode("utf-8", errors="replace")
    except Exception:
        return {}

    found: Dict[str, List[str]] = {}
    for marker, permission, evidence in _MARKERS:
        if marker in source and evidence not in found.setdefault(permission, []):
            found[permission].append(evidence)

    for permission, evidence in _scan_imports(source).items():
        target = found.setdefault(permission, [])
        for item in evidence:
            if item not in target:
                target.append(item)

    return {perm: names for perm, names in found.items() if names}


#: Что именно импортируют из пакетов мессенджера. Пакет целиком ни о чём не
#: говорит: из org.telegram.messenger берут и MessagesController, и
#: AndroidUtilities.dp() — по первому спрашивать разрешение нужно, по второму
#: нет, иначе диалог будет требовать доступ к переписке у каждого плагина.
_IMPORTED_NAMES = {
    "MessagesController": (PERM_MESSAGES_READ, "список чатов и сообщения"),
    "MessagesStorage": (PERM_MESSAGES_READ, "база сообщений"),
    "MessageObject": (PERM_MESSAGES_READ, "содержимое сообщений"),
    "NotificationCenter": (PERM_MESSAGES_READ, "события мессенджера"),
    "SendMessagesHelper": (PERM_MESSAGES_SEND, "отправка сообщений"),
    "ConnectionsManager": (PERM_MESSAGES_SEND, "прямые запросы к серверу"),
    "ContentResolver": (PERM_FILES, "доступ к файлам устройства"),
    "MediaStore": (PERM_FILES, "галерея"),
    "WebView": (PERM_NETWORK, "WebView"),
    "InMemoryDexClassLoader": (PERM_HOOKS, "загрузка своего кода"),
    "DexClassLoader": (PERM_HOOKS, "загрузка своего кода"),
    "XposedBridge": (PERM_HOOKS, "Xposed-хуки"),
    "XposedHelpers": (PERM_HOOKS, "Xposed-хуки"),
}


def _scan_imports(source: str) -> Dict[str, List[str]]:
    """Импорты: важно не откуда, а что именно."""
    result: Dict[str, List[str]] = {}
    try:
        tree = ast.parse(source)
    except Exception:
        # Битый Python разберём регулярным выражением: диалог установки всё
        # равно должен что-то показать.
        for names in re.findall(r"^\s*from\s+[A-Za-z0-9_.]+\s+import\s+([^\n#]+)", source, re.M):
            for name in names.split(","):
                _note_name(result, name.strip().split(" as ")[0])
        return result

    for node in ast.walk(tree):
        if isinstance(node, ast.ImportFrom):
            for alias in node.names:
                _note_name(result, alias.name)
        elif isinstance(node, ast.Import):
            for alias in node.names:
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
