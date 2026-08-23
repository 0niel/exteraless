import asyncio
import glob
import os

from pyrogram import Client

APK_GLOB = "TMessagesProj/build/outputs/apk/release/*.apk"
CAPTION_LIMIT = 1024


def caption() -> str:
    lines = (os.environ.get("COMMIT_MESSAGE") or "").strip().splitlines()
    subject = lines[0] if lines else "без описания"
    body = "\n".join(lines[1:]).strip()
    sha = (os.environ.get("COMMIT_SHA") or "")[:9]
    parts = [f"**{subject}**"]
    if body:
        parts.append(body)
    parts.append(f"`{sha}`\n{os.environ.get('RUN_URL', '')}")
    return "\n\n".join(parts)[:CAPTION_LIMIT]


def chat() -> "int | str":
    raw = os.environ["TG_CHAT_ID"].strip()
    try:
        return int(raw)
    except ValueError:
        return raw


async def main() -> None:
    apks = sorted(glob.glob(APK_GLOB), key=os.path.getmtime)
    if not apks:
        raise SystemExit(f"APK не найден: {APK_GLOB}")
    apk = apks[-1]
    print(f"{os.path.basename(apk)} — {os.path.getsize(apk) / 1024 / 1024:.1f} МБ")

    async with Client(
        "ci",
        api_id=int(os.environ["TG_API_ID"]),
        api_hash=os.environ["TG_API_HASH"],
        bot_token=os.environ["TG_BOT_TOKEN"],
        in_memory=True,
        no_updates=True,
    ) as app:
        message = await app.send_document(
            chat(),
            apk,
            caption=caption(),
            file_name=os.path.basename(apk),
            force_document=True,
        )
        print("отправлено, id =", message.id)


asyncio.run(main())
