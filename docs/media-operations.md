# Media operations

## Static-HLS inventory

Devs accepts only pre-built static-HLS packages. Every registered package has
an immutable manifest path, an encoding version, and a duration computed from
its rendition playlists. The V12 migration rejects non-static legacy rows
before removing obsolete provider-specific columns; it never silently deletes
media data. If a pre-production database still contains an unsupported row,
export and review it before applying the migration.

## Media lifecycle

Current media is referenced by `content_units.media_id`. Replacing it retains the
old ready asset for `DEVS_MEDIA_VERSION_RETENTION` (30 days by default), with a
`retained_for_unit_id` marker. A retained version can only be restored to that
same lesson. Direct deletion is reserved for media that is not current and not a
retained version; it is soft-deleted for `DEVS_MEDIA_RETENTION` (7 days by
default).

The scheduled purge removes database rows only after the backing object cleanup
has succeeded. Static HLS media is deleted as one validated manifest directory.
Managed caption objects are deleted by exact key as part of the same
media/content purge; the immutable HLS package is never modified by caption
edits.

Production must set `STATIC_HLS_ALLOWED_PATH_PREFIX` to the isolated Devs HLS
key prefix. An empty or mismatched prefix deliberately blocks HLS directory
deletion. The R2 API token must be scoped to the Devs bucket and allow object
read, write, and delete operations; bucket creation and account-wide access are
not required.

## Trash purge and retries

Deleting content or a lesson first moves it to trash. The default retention is
seven days (`purgeAfter`); this is a database soft-delete, not an immediate
object-store deletion. A scheduled worker then processes due rows in bounded
steps:

1. It locks and claims the parent/lesson by setting `purgeState=CLAIMED`.
2. It commits that claim, builds a plan from the exact attachment and cover
   keys plus validated Static HLS manifest prefixes, and deletes those objects
   outside the database transaction.
3. Only after every storage operation succeeds does a short transaction clear
   media foreign keys, remove owned media/covers/attachments, and finally remove
   the lesson or aggregate.

Claims prevent restore, upload, attachment, and media replacement while a purge
is in progress. Parent rows are locked before child rows so an aggregate purge
and a lesson purge cannot deadlock. A lesson purge only removes that lesson's
rows; media referenced by another lesson is retained. Before removal, the
coordinator also verifies current-media references and retained-version owners,
so a shared or unexpectedly referenced asset is never deleted.

The object-store step is deliberately outside the database transaction: holding
a database transaction across network calls would keep locks open and still
could not make R2 and PostgreSQL one atomic transaction. Deletes are idempotent,
so a worker crash after storage cleanup is retried from the durable claim. A
storage error (or a later database failure) leaves the claimed rows intact for
the next run; operators should alert on old `CLAIMED` rows rather than force
delete them. Do not manually clear a claim until the object keys have been
verified.

## Cover uploads

Cover uploads are two-phase: the API creates an `UPLOADING` row and a direct R2
presigned upload, then `/complete` verifies object existence and exact byte size
before activating it. Replaced or deleted covers remain for the configured
retention window so a failed deployment or accidental change can be recovered
operationally before purge.
