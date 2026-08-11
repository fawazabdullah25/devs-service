# Telegram video migration

This operator tool downloads videos from a KStacks-authorized Telegram channel using a user MTProto session, calculates SHA-256, uploads each original to private R2, and registers the object with Devs. Devs then asks Mux to ingest it. Nothing is published automatically.

Only run it for channels and videos KStacks has permission to download, transform, retain, and publish.

## Setup

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
```

Create `TELEGRAM_API_ID` and `TELEGRAM_API_HASH` at Telegram's API development page. On first use Telethon prompts for the authorized account's login and stores `.telegram-session` locally; both session forms are ignored by Git and must be treated as secrets.

`DEVS_API_URL` ends in `/devs/api/v1`. In production, `DEVS_API_TOKEN` is a current KStacks access JWT for an allowed administrator. A local service started with explicit insecure admin mode does not require the token.

## Run safely

Inventory without downloading:

```bash
python telegram_to_r2.py --channel https://t.me/<channel> --dry-run
```

Migrate and register:

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

`--limit N` is useful for a small rehearsal. `--archive-only` uploads to R2 without registering or ingesting; it is intentionally not the normal path.

## Resume and reconciliation

The append-only JSONL manifest records Telegram message ID/date/caption, filename, MIME, byte size, SHA-256, R2 key, Devs media ID, Mux state, and any registration error. Normal reruns skip only `registered` messages. A failure exits non-zero; rerunning safely reuses the checksum-derived R2 object and the idempotent Devs import endpoint.

After the run, compare the dry-run match count with `registered` manifest entries and verify that every Devs media item reaches `READY` before attaching it to content. Temporary video files are deleted at the end of each item automatically.
