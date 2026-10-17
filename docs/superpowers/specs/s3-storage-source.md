# S3-Compatible Storage Source

## Summary
New cloud music source: any S3-compatible object storage (AWS S3, Cloudflare R2, Backblaze B2, MinIO, Wasabi). Pure REST over existing OkHttp — no new dependencies. Follows the existing cloud-source pattern (GDrive/Navidrome/Jellyfin).

## Auth & API
- **SigV4** (`data/s3/S3SigV4.kt`): header-signed `ListObjectsV2` + query-string presigned GET (`UNSIGNED-PAYLOAD`, 1h TTL). Verified against official AWS doc vectors (`S3SigV4Test`).
- **API** (`data/s3/S3ApiService.kt`): `listObjects` (delimiter=`/`, continuation token), `listAllObjects`, `ping` (maxKeys=1), `presignGetUrl` (validates object key via `CloudStreamSecurity.validateS3ObjectKey`).
- Endpoint must be `https://` (cleartext blocked by network config). Region optional (defaults `us-east-1`); R2 uses `auto`.
- Credentials in `EncryptedSharedPreferences("s3_prefs")`. Single connection per app.

## Sync model (GDrive-style)
User connects (ping) → picks folder(s) in bucket via browser → folders stored in Room (`s3_folders`), songs in `s3_songs` + unified `songs` table. Re-sync = diff object listing vs DB, add/remove songs; unified library updated via ID diff. Per-folder sync + sync-all (`Mutex`). Failed folders don't abort the batch.

## IDs & identity
- `SourceType.S3 = 7`; URI scheme `s3://{objectKey}`.
- ID offsets: songs `15e12`, albums `16e12`, artists `17e12` (3–14e12 taken by Netease/QQ/Navidrome/Jellyfin).
- Artist/title parsed from `Artist - Title.ext`; album = first path segment under folder prefix, else `S3`; genre `S3`; duration 0 (unknown).

## Streaming
- `S3StreamProxy` extends `CloudStreamProxy<String>`; route `/s3/{key}`, key URL-encoded with `Uri.encode(id, "")` (URLEncoder breaks spaces→`+`).
- Presigned URL cached 55 min (inside 1h TTL). `DualPlayerEngine` resolves `s3://` when online.
- Presigned URLs exempt from private-IP block in `isSafeRemoteStreamUrl` (self-hosted MinIO/NAS); `X-Amz-Signature` in query is the marker.

## UI
- `S3LoginActivity`: endpoint/region/bucket/accessKey/secretKey/prefix form → folder picker (browse common prefixes, "Add this folder" for current level).
- `S3DashboardScreen`: folder list w/ per-folder sync/delete, sync-all, logout, bucket label.
- Wiring: `AccountsScreen` (S3 palette/icon/disconnect), `Screen.S3Dashboard`, `AppNavigation`, manifest.

## DB
Version 42→43, `MIGRATION_42_43` creates `s3_folders` + `s3_songs` (+3 indices). Schema JSON exported at `app/schemas/.../43.json`.

## Out of scope (deliberate)
Multi-account, multipart upload, offline downloads, per-file browse UI, XML `EncodingType=url` handling.
