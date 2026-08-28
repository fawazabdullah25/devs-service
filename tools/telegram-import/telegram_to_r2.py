#!/usr/bin/env python3
"""Resumable, authorized migration of Telegram channel videos into Cloudflare R2."""

from __future__ import annotations

import argparse
import asyncio
import hashlib
import json
import mimetypes
import os
import re
import tempfile
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import boto3
from botocore.client import Config
from botocore.exceptions import ClientError
from dotenv import load_dotenv
from telethon import TelegramClient
from telethon.sessions import StringSession


def required(name: str) -> str:
    value = os.getenv(name, "").strip()
    if not value:
        raise SystemExit(f"Missing required environment variable: {name}")
    return value


def arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--channel", default=os.getenv("TELEGRAM_CHANNEL"), help="Channel username, URL, or numeric ID")
    parser.add_argument("--prefix", default="telegram-source", help="R2 object key prefix")
    parser.add_argument("--manifest", type=Path, default=Path("output/telegram-manifest.jsonl"))
    parser.add_argument("--limit", type=int, default=None, help="Optional maximum number of messages to inspect")
    parser.add_argument("--dry-run", action="store_true", help="List matching videos without downloading or uploading")
    return parser.parse_args()


def safe_filename(value: str) -> str:
    basename = Path(value).name
    cleaned = re.sub(r"[^A-Za-z0-9._-]+", "-", basename).strip("-.")
    return (cleaned or "telegram-video.mp4")[:180]


def file_details(message: Any) -> tuple[str, str] | None:
    if not message.file:
        return None
    mime = message.file.mime_type or ""
    if not (message.video or mime.startswith("video/")):
        return None
    extension = mimetypes.guess_extension(mime) or ".mp4"
    name = message.file.name or f"telegram-{message.id}{extension}"
    return safe_filename(name), mime or "video/mp4"


def completed_messages(manifest: Path) -> set[int]:
    if not manifest.exists():
        return set()
    completed: set[int] = set()
    for line in manifest.read_text(encoding="utf-8").splitlines():
        try:
            row = json.loads(line)
            if row.get("status") in {"uploaded", "already-present"}:
                completed.add(int(row["message_id"]))
        except (ValueError, TypeError, json.JSONDecodeError):
            continue
    return completed


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def r2_client() -> Any:
    return boto3.client(
        "s3",
        endpoint_url=required("R2_ENDPOINT"),
        region_name=os.getenv("R2_REGION", "auto"),
        aws_access_key_id=required("R2_ACCESS_KEY_ID"),
        aws_secret_access_key=required("R2_SECRET_ACCESS_KEY"),
        config=Config(signature_version="s3v4", s3={"addressing_style": "path"}),
    )


def object_exists(client: Any, bucket: str, key: str) -> bool:
    try:
        client.head_object(Bucket=bucket, Key=key)
        return True
    except ClientError as error:
        code = str(error.response.get("Error", {}).get("Code", ""))
        if code in {"404", "NoSuchKey", "NotFound"}:
            return False
        raise


def write_manifest(path: Path, row: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("a", encoding="utf-8") as target:
        target.write(json.dumps(row, ensure_ascii=False) + "\n")


async def migrate(args: argparse.Namespace) -> None:
    if not args.channel:
        raise SystemExit("Provide --channel or TELEGRAM_CHANNEL")

    api_id = int(required("TELEGRAM_API_ID"))
    api_hash = required("TELEGRAM_API_HASH")
    session_value = os.getenv("TELEGRAM_SESSION", "").strip()
    session = StringSession(session_value) if session_value else ".telegram-session"
    client = TelegramClient(session, api_id, api_hash)
    storage = None if args.dry_run else r2_client()
    bucket = "" if args.dry_run else required("R2_BUCKET")
    done = completed_messages(args.manifest)
    matched = uploaded = skipped = 0

    async with client:
        entity = await client.get_entity(args.channel)
        async for message in client.iter_messages(entity, reverse=True, limit=args.limit):
            details = file_details(message)
            if not details:
                continue
            matched += 1
            filename, content_type = details
            if message.id in done:
                skipped += 1
                print(f"skip message={message.id} already in manifest")
                continue
            if args.dry_run:
                print(f"video message={message.id} name={filename} mime={content_type}")
                continue

            with tempfile.TemporaryDirectory(prefix="devs-telegram-") as directory:
                destination = Path(directory) / filename
                result = await client.download_media(message, file=str(destination))
                if not result:
                    raise RuntimeError(f"Telegram did not return a file for message {message.id}")
                checksum = await asyncio.to_thread(sha256, destination)
                key = f"{args.prefix.strip('/')}/{checksum[:16]}-{filename}"
                present = await asyncio.to_thread(object_exists, storage, bucket, key)
                storage_status = "already-present" if present else "uploaded"
                if not present:
                    await asyncio.to_thread(
                        storage.upload_file,
                        str(destination),
                        bucket,
                        key,
                        ExtraArgs={
                            "ContentType": content_type,
                            "Metadata": {
                                "sha256": checksum,
                                "telegram-message-id": str(message.id),
                            },
                        },
                    )
                    uploaded += 1
                else:
                    skipped += 1

                write_manifest(
                    args.manifest,
                    {
                        "status": storage_status,
                        "storage_status": storage_status,
                        "message_id": message.id,
                        "message_date": message.date.astimezone(timezone.utc).isoformat() if message.date else None,
                        "filename": filename,
                        "content_type": content_type,
                        "bytes": destination.stat().st_size,
                        "sha256": checksum,
                        "object_key": key,
                        "caption": message.message or "",
                        "migrated_at": datetime.now(timezone.utc).isoformat(),
                    },
                )
                print(f"{storage_status} message={message.id} key={key}")

    print(
        f"complete matched={matched} uploaded={uploaded} skipped={skipped} dry_run={args.dry_run}"
    )


if __name__ == "__main__":
    load_dotenv()
    asyncio.run(migrate(arguments()))
