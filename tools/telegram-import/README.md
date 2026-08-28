# Telegram video migration

This operator tool downloads videos from a KStacks-authorized Telegram channel using a user MTProto session, calculates SHA-256, and archives each original in R2. Nothing is published automatically. Archived source files must be encoded into an immutable static-HLS package before an administrator registers that package with Devs.

Only run it for channels and videos KStacks has permission to download, transform, retain, and publish.

## Setup

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
```

Create `TELEGRAM_API_ID` and `TELEGRAM_API_HASH` at Telegram's API development page. On first use Telethon prompts for the authorized account's login and stores `.telegram-session` locally; both session forms are ignored by Git and must be treated as secrets.

## Run safely

Inventory without downloading:

```bash
python telegram_to_r2.py --channel https://t.me/<channel> --dry-run
```

Download and archive:

```bash
python telegram_to_r2.py --channel https://t.me/<channel>
```

Use a different manifest per channel or run:

```bash
python telegram_to_r2.py \
  --channel https://t.me/<channel> \
  --manifest output/<channel>.jsonl \
  --prefix telegram-source/<channel>
```

`--limit N` is useful for a small rehearsal. The archive prefix should be private and separate from the public static-HLS prefix.

## Resume and reconciliation

The append-only JSONL manifest records Telegram message ID/date/caption, filename, MIME, byte size, SHA-256, and R2 key. Normal reruns skip only messages already archived. A storage failure exits non-zero; rerunning safely reuses the checksum-derived R2 object.

After the run, compare the dry-run match count with archived manifest entries. Encode and validate each selected source into static HLS, upload the versioned package, and register its master playlist through the Devs admin workflow before attaching it to content. Temporary video files are deleted at the end of each item automatically.
