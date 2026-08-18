# KStack Devs — One-Video R2/HLS Pilot Guide

> Repository record for the Devs static-HLS delivery pilot.
>
> Last verified: 18 August 2026
>
> Purpose: test the proposed self-managed video pipeline from source file to real browser playback using one representative video of approximately one hour.
> Scope: this is a disposable pilot, not yet the production publishing workflow.

## 1. What this pilot should prove

At the end, we should know whether this architecture is genuinely suitable for Devs:

```text
Original one-hour video
        ↓
FFmpeg on the Oracle ARM server
        ↓
1080p and 720p HLS files
        ↓
Cloudflare R2 delivery bucket
        ↓
Cloudflare custom domain and CDN cache
        ↓
Browser HLS playback
        ↓
Vidstack inside Devs
```

The test is successful only if all of the following are true:

- Text remains readable at 1080p and 720p on realistic devices.
- Playback starts quickly and seeking works.
- The player switches quality when network capacity changes.
- Playback works on Chrome, Firefox, Safari/iPhone, and Android Chrome.
- CORS and HTTP content types are correct.
- Cloudflare caches playlists and segments.
- The Oracle server can encode without harming the projects already running there.
- Measured storage extrapolates to an acceptable cost for the full library.
- The team explicitly accepts that the resulting video URLs are public.
- Repeating or replacing an encode cannot corrupt an already published lesson.

Do not batch the complete backlog until this pilot passes.

---

## 2. Essential vocabulary

### Video, codec, and container

A **video file** normally contains compressed video, compressed audio, and metadata.

A **codec** is the algorithm used to compress and decompress the media. This guide uses:

- **H.264/AVC** for video. It is not the newest codec, but it has excellent browser and device support.
- **AAC** for audio. It is broadly supported alongside H.264.

A **container** packages encoded streams and metadata. MP4 is a container; H.264 is a codec. Saying “an MP4 video” does not tell us which codec is inside it.

### Encoding and transcoding

**Encoding** converts uncompressed or decoded media into a compressed codec.

**Transcoding** decodes an existing compressed video and encodes it again into another resolution, bitrate, codec, or packaging format. In practice, people often use “encoding” for both.

FFmpeg will transcode the source into two H.264 renditions.

### Resolution

**Resolution** is the number of pixels in each frame:

- 1080p is normally `1920 × 1080`.
- 720p is normally `1280 × 720`.

Higher resolution can make code clearer, but it generally requires more data and more processing power.

### Frame and frame rate

A **frame** is one still image in the video.

**Frame rate** is the number of frames displayed each second, written as fps. This pilot normalizes the output to 30 fps. That is normally sufficient for coding lectures and halves the work of a 60 fps source.

### Bit, byte, bitrate, and bandwidth

A **bit** is a single binary digit. A **byte** contains eight bits.

Video **bitrate** is how much encoded data the video consumes per second. It is normally written in kilobits or megabits per second:

```text
1 Mbps = 1 megabit per second
1 MB/s = 1 megabyte per second = approximately 8 Mbps
```

A video averaging 2 Mbps transfers roughly:

```text
2 megabits/second ÷ 8 = 0.25 megabytes/second
0.25 × 3,600 seconds ≈ 900 MB for one hour
```

**Bandwidth** is the carrying capacity of a connection. If a student has 1.5 Mbps of usable bandwidth but the selected video needs 2.5 Mbps, the player consumes its buffered data faster than it can download more, so it eventually stalls.

Bitrate describes the media. Bandwidth describes the connection.

### Buffering and `bufsize`

The word **buffer** has two related but different meanings here:

1. The player downloads video ahead of the current position. That downloaded-ahead window is the playback buffer.
2. FFmpeg's `bufsize` is part of the encoder's bitrate-control model, commonly called the VBV buffer. It controls how much bitrate may vary over a window of time. It is not the browser's playback buffer.

`maxrate` limits short bitrate peaks. `bufsize` determines how aggressively the encoder must remain near that cap. These help prevent a supposedly lightweight rendition from suddenly requiring much more bandwidth during scrolling or animation.

### CRF and preset

**CRF**, or Constant Rate Factor, asks H.264 to target a visual quality rather than a fixed file size.

- Lower CRF = higher quality and a larger file.
- Higher CRF = lower quality and a smaller file.

This pilot starts at CRF 21 for 1080p and 22 for 720p. The values are only starting points; code readability on a real phone decides whether they are acceptable.

The H.264 **preset** trades encoding time for compression efficiency. `slow` takes more CPU time but usually produces a smaller file at comparable visual quality than `fast`. It does not mean the resulting video plays slowly.

### Keyframe and GOP

Most video frames store differences from nearby frames. A **keyframe**, also called an I-frame, can be decoded independently.

A **GOP**, or Group of Pictures, is the group from one keyframe to the next. HLS normally begins a segment at a keyframe. If the 1080p and 720p versions do not place keyframes at the same times, switching quality can glitch or fail.

This pilot:

- Normalizes both outputs to 30 fps.
- Forces a keyframe every six seconds.
- Uses `-g 180` because `30 fps × 6 seconds = 180 frames`.
- Disables extra scene-change keyframes with `-sc_threshold 0`.

### HLS, playlists, renditions, and segments

**HLS** means HTTP Live Streaming. Despite the name, it supports prerecorded video.

HLS divides video into short **segments** and describes them with text **playlists**:

```text
master.m3u8
├── 1080p/playlist.m3u8
│   ├── init_0.mp4
│   ├── seg_00000.m4s
│   └── ...
└── 720p/playlist.m3u8
    ├── init_1.mp4
    ├── seg_00000.m4s
    └── ...
```

A **rendition** is one quality version, such as 1080p or 720p.

The **master playlist** lists all renditions. A **variant playlist** lists the segments for one rendition.

The player estimates available bandwidth and selects a rendition. This is **adaptive bitrate streaming**, abbreviated ABR.

This pilot uses **fragmented MP4**, or fMP4. Each `.m4s` file is a media fragment, while `init_0.mp4` or `init_1.mp4` contains initialization information needed to decode its rendition.

### Manifest

In HLS discussions, **manifest** and **playlist** are often used interchangeably. In this guide, the manifest URL is the URL of `master.m3u8`.

### VTT captions

**WebVTT**, normally stored as `.vtt`, is a text format for captions, subtitles, chapters, or timed metadata:

```text
WEBVTT

00:00:00.000 --> 00:00:04.000
Welcome to this lesson.
```

VTT files contain timed text, not audio. FFmpeg does not magically create accurate captions. They must be written, exported, or generated with speech recognition and reviewed by a person.

### Object storage, bucket, object, and key

R2 is **object storage**. It stores files as objects rather than presenting a normal server filesystem.

- A **bucket** is a top-level collection of objects.
- An **object** is one uploaded file plus metadata.
- An object **key** is its path-like name, such as `pilots/java-intro/2026-08-13-v1/master.m3u8`.
- A **prefix** is the beginning of a group of keys, such as `pilots/java-intro/2026-08-13-v1/`.

Folders in object storage are mostly a user-interface illusion created from `/` characters in keys.

### Rclone

**Rclone** is an open-source command-line program for copying and synchronizing files between local disks and cloud/object-storage services. It is similar in spirit to `rsync`, but understands providers such as R2, S3, Google Drive, and many others.

For this pilot, rclone will:

- Authenticate to R2 using a bucket-scoped access key.
- Upload hundreds of HLS objects efficiently.
- Preserve the directory layout.
- Apply HTTP metadata such as `Content-Type` and `Cache-Control`.
- Compare local and remote results.

Rclone is not a video encoder or player.

### MIME type and `Content-Type`

A **MIME type** tells a browser what kind of content an HTTP response contains. It is delivered in the `Content-Type` header.

Important types for this pilot are:

```text
.m3u8  application/vnd.apple.mpegurl
.m4s   video/iso.segment
.mp4   video/mp4
.vtt   text/vtt
.webp  image/webp
```

A wrong type can cause a browser to download a playlist as text or reject playback.

### Origin, same-origin policy, and CORS

In browser security, an **origin** is the combination of scheme, hostname, and port:

```text
http://localhost:3000
https://devs.example.com
https://video.example.com
```

Those are three different origins.

Browsers enforce the **same-origin policy**, which restricts a page from reading resources from another origin.

**CORS**, or Cross-Origin Resource Sharing, is a set of HTTP response headers through which `video.example.com` tells the browser that `devs.example.com` is allowed to load its resources.

CORS is not access control. It does not prevent someone using `curl`, a download manager, or another server from fetching a public video URL.

### CDN, edge, origin, cache hit, and cache miss

A **CDN**, or Content Delivery Network, stores copies near users.

- R2 is the storage **origin**.
- A Cloudflare data center near a viewer is an **edge**.
- A **cache miss** means the edge does not have the object and retrieves it from R2 or an upper cache tier.
- A **cache hit** means the edge already has it and responds directly.
- **TTL**, or Time To Live, says how long a cached object remains fresh.
- **Eviction** means Cloudflare removes a less-popular object to make space. A one-year TTL does not guarantee one year of physical residence at every edge.

### Latency and throughput

**Latency** is delay, such as the time for a request to travel to a server and back.

**Throughput** is the amount of data successfully transferred over time. Good video playback needs enough throughput for the selected rendition and reasonably low latency for playlists and segments.

### Checksum

A **checksum** is a digest calculated from file bytes. If the bytes change, the checksum almost certainly changes. This pilot uses SHA-256 checksums to help detect corruption or incomplete copying.

### Versioned and immutable paths

**Immutable** means “never changed after publication.” Instead of overwriting:

```text
lessons/java-intro/master.m3u8
```

publish a new version:

```text
lessons/java-intro/2026-08-13-v1/master.m3u8
lessons/java-intro/2026-08-20-v2/master.m3u8
```

The database pointer changes only after the new version is complete. This prevents old cached playlists from referring to newly overwritten segments and makes rollback straightforward.

---

## 3. Pilot safety rules

1. Use content KStacks has permission to store and stream.
2. Use a separate test bucket and test hostname.
3. Never put R2 secrets in Git, `.env.example`, chat, screenshots, or shell commands that may enter shell history.
4. Create an R2 token restricted to the pilot bucket.
5. Keep the original video until verification and backup are complete.
6. Never overwrite a published prefix. Create a new version.
7. Do not enable an indefinite bucket lock on a disposable test bucket.
8. Remember that the delivery bucket and manifest URL are public.
9. Do not run a full-speed encode during peak use of the Oracle VPS.
10. Do not point Devs production data at the pilot until the isolated playback test passes.

---

## 4. What you need before starting

### Accounts and infrastructure

- A Cloudflare account with R2 billing enabled.
- A domain managed by Cloudflare DNS.
- Permission to add a test subdomain such as `video-test.example.com`.
- SSH access to the Oracle ARM VPS.
- At least one representative one-hour source video.
- Enough temporary disk capacity for the source and both renditions.

Allow at least three to five times the source file size as free temporary space until the actual output ratio is known.

### Tools

On the encoding machine, we need:

- `ffmpeg` — encodes and packages video.
- `ffprobe` — inspects media metadata.
- `rclone` — uploads to R2.
- `curl` — inspects HTTP responses.
- `sha256sum` — calculates checksums.
- `tmux` — optional, keeps an encode running after SSH disconnects.
- `htop` — optional, displays CPU and memory use.

On Ubuntu, install distribution packages with:

```bash
sudo apt update
sudo apt install ffmpeg rclone curl tmux htop
```

Then confirm:

```bash
ffmpeg -version
ffprobe -version
rclone version
curl --version
```

If Ubuntu's rclone package is too old, use rclone's official installation instructions rather than an unofficial binary.

### Replace these examples

This guide uses placeholders. Choose real values and keep them consistent:

```bash
export PILOT_ROOT="/srv/devs-video-pilot"
export PILOT_INPUT="/srv/devs-video-pilot/input/lesson-source.mp4"
export PILOT_SAMPLE="/srv/devs-video-pilot/input/lesson-sample.mp4"
export PILOT_SLUG="representative-code-lesson"
export PILOT_VERSION="2026-08-13-v1"
export PILOT_OUTPUT="/srv/devs-video-pilot/output/representative-code-lesson/2026-08-13-v1"

export PILOT_BUCKET="kstacks-devs-video-delivery-test"
export PILOT_PREFIX="pilots/representative-code-lesson/2026-08-13-v1"
export PILOT_REMOTE="devs-r2:kstacks-devs-video-delivery-test/pilots/representative-code-lesson/2026-08-13-v1"
export PILOT_VIDEO_HOST="video-test.example.com"
export PILOT_MANIFEST_URL="https://video-test.example.com/pilots/representative-code-lesson/2026-08-13-v1/master.m3u8"
```

These variables last only in the current shell. Re-enter them after reconnecting unless you place the non-secret values in a private operations script. Do not store credentials in that script.

Create working directories:

```bash
sudo mkdir -p /srv/devs-video-pilot/input
sudo mkdir -p /srv/devs-video-pilot/output
sudo mkdir -p /srv/devs-video-pilot/logs
sudo chown -R "$(id -un):$(id -gn)" /srv/devs-video-pilot
```

Check capacity and CPU count:

```bash
df -h /srv/devs-video-pilot
nproc
free -h
```

---

## 5. Provision the Cloudflare pilot

### Step 5.1 — Create a Standard R2 delivery bucket

In Cloudflare:

1. Open **Storage & databases → R2**.
2. Select **Create bucket**.
3. Name it `kstacks-devs-video-delivery-test`, or another clearly disposable name.
4. Select **Standard** storage.
5. Do not enable a lifecycle deletion rule yet.
6. Do not place private original recordings in this bucket.

Standard storage currently includes a shared monthly free tier of 10 GB, one million Class A operations, and ten million Class B operations. Verify current prices before production because provider pricing can change.

### Step 5.2 — Attach a custom domain

Inside the bucket settings:

1. Find **Public access → Custom Domains**.
2. Connect `video-test.example.com`.
3. Wait until its TLS certificate is active.
4. Keep the `r2.dev` development URL disabled, or disable it after testing.

The custom domain makes the bucket publicly readable and enables Cloudflare caching, WAF, and related controls. Do not attach the private-master bucket to a custom domain.

### Step 5.3 — Configure CORS

For the isolated browser test, serve the test page at `http://localhost:8088`. Add the real Devs preview origin when integrating the application.

In **Bucket → Settings → CORS Policy**, use:

```json
[
  {
    "AllowedOrigins": [
      "http://localhost:8088",
      "http://localhost:3000",
      "https://replace-with-devs-preview.example.com"
    ],
    "AllowedMethods": ["GET", "HEAD"],
    "AllowedHeaders": ["Range"],
    "ExposeHeaders": [
      "Content-Length",
      "Content-Range",
      "ETag",
      "CF-Cache-Status"
    ],
    "MaxAgeSeconds": 3600
  }
]
```

Important details:

- Origins must match exactly, including `http` versus `https` and the port.
- Do not use `*` if you want to detect accidental origin mistakes.
- The `Range` header permits byte-range requests some media engines use.
- CORS changes can take a short time to propagate.
- Purge cached pilot objects after changing CORS if old cached responses lack the new headers.

### Step 5.4 — Create a Cache Rule

Cloudflare does not cache every extension by default. `.m3u8` and `.m4s` need an explicit rule.

In **Rules → Cache Rules**, create:

```text
Name: Devs video pilot immutable HLS

When:
  Hostname equals video-test.example.com

Then:
  Cache eligibility: Eligible for cache
  Edge TTL: Override origin, 1 year
```

This is safe only because every publication path contains a version and is never overwritten.

If the hostname will eventually contain mutable files, narrow the rule to a versioned path such as `/pilots/` or `/lessons/`.

Enable **Smart Tiered Cache** if available in the zone. It makes nearby edge locations check an upper cache tier before returning to R2, reducing R2 reads.

### Step 5.5 — Create a bucket-scoped R2 access key

In **R2 → Manage API Tokens**:

1. Create an account or user API token.
2. Give it **Object Read & Write** permission.
3. Apply it only to `kstacks-devs-video-delivery-test`.
4. Name it clearly, such as `devs-video-pilot-uploader`.
5. Copy the **Access Key ID**, **Secret Access Key**, and S3 endpoint once.
6. Put them directly into interactive rclone configuration; do not paste them into a shell command or repository file.

The endpoint has this shape:

```text
https://<CLOUDFLARE_ACCOUNT_ID>.r2.cloudflarestorage.com
```

The access key is a machine credential. Anyone possessing both values can perform the actions allowed by the token.

---

## 6. Configure and verify rclone

Run:

```bash
rclone config
```

Create a new remote with values equivalent to:

```text
Name: devs-r2
Storage type: s3
Provider: Cloudflare
Access credentials: enter interactively
Region: auto
Endpoint: https://<ACCOUNT_ID>.r2.cloudflarestorage.com
```

Accept defaults for unfamiliar advanced settings during the pilot.

Rclone normally stores configuration at a per-user path such as `.config/rclone/rclone.conf`. Protect it:

```bash
rclone config file
```

Use the path printed by that command and make sure only your user can read it:

```bash
chmod 600 /replace/with/the/printed/rclone.conf
```

Test authentication without uploading video:

```bash
rclone lsd devs-r2:
rclone lsf "devs-r2:${PILOT_BUCKET}"
```

The first command should show only buckets the token may list or access. The second should show an empty pilot bucket.

If the token can modify unrelated production buckets, delete it and create a properly scoped token.

---

## 7. Transfer and inspect the source video

### Step 7.1 — Copy the source to Oracle

From your own computer, use `rsync` over SSH. Replace the SSH details:

```bash
rsync --archive --partial --progress \
  "/local/path/to/lesson-source.mp4" \
  "oracle-user@oracle-host:/srv/devs-video-pilot/input/lesson-source.mp4"
```

`--partial` retains a partially transferred file if the connection drops. Running the same command again resumes efficiently.

Calculate a checksum on both machines:

```bash
sha256sum "/local/path/to/lesson-source.mp4"
sha256sum "/srv/devs-video-pilot/input/lesson-source.mp4"
```

The two SHA-256 values must match.

### Step 7.2 — Inspect media metadata

On Oracle:

```bash
ffprobe \
  -v error \
  -show_entries \
format=filename,duration,size,bit_rate:stream=index,codec_type,codec_name,width,height,r_frame_rate,avg_frame_rate,pix_fmt,sample_rate,channels \
  -of json \
  "$PILOT_INPUT"
```

Record:

- Duration
- Width and height
- Average frame rate
- Video codec
- Audio codec
- Audio sample rate and channel count
- Source size and bitrate

Stop and adjust the encoding command if:

- There is no audio stream.
- The video is below 1280×720.
- The video is below 1920×1080 but the planned ladder still includes 1080p. Do not upscale a smaller source merely to claim a higher rendition; omit 1080p or choose a ladder appropriate to the source.
- The aspect ratio is not the expected landscape format.
- The file is variable-frame-rate and visibly problematic after normalization.
- The source itself has corruption or audio/video synchronization problems.

### Step 7.3 — Decode-check the source

This reads and decodes the entire source without creating output:

```bash
ffmpeg -v error -i "$PILOT_INPUT" -map 0:v:0 -map 0:a:0 -f null -
```

No output is good. Decode errors should be investigated before encoding.

### Step 7.4 — Make a five-minute rehearsal clip

Before spending hours on the full encode, choose five minutes containing small text, scrolling, terminal output, slides, and speech:

```bash
ffmpeg \
  -ss 00:20:00 \
  -i "$PILOT_INPUT" \
  -t 00:05:00 \
  -map 0:v:0 \
  -map 0:a:0 \
  -c copy \
  "$PILOT_SAMPLE"
```

Use the sample as `PILOT_INPUT` for a first rehearsal. Once it is visually correct, restore `PILOT_INPUT` to the full one-hour file and create a new `PILOT_VERSION` for the full run.

---

## 8. Encode the HLS package

### Step 8.1 — Prepare a clean, versioned output directory

Choose a new version for every attempt. Never reuse a directory that has already been uploaded.

```bash
mkdir -p "$PILOT_OUTPUT/1080p"
mkdir -p "$PILOT_OUTPUT/720p"
```

### Step 8.2 — Start a persistent terminal

Use tmux so an SSH disconnect does not terminate FFmpeg:

```bash
tmux new -s devs-video-pilot
```

Detach with `Ctrl+B`, then `D`. Reconnect later with:

```bash
tmux attach -t devs-video-pilot
```

### Step 8.3 — Run FFmpeg

The following tested command assumes a landscape source at least 1920×1080 with one audio stream. It produces two aligned HLS renditions:

```bash
/usr/bin/time -v -o "$PILOT_ROOT/logs/encode-time.txt" \
nice -n 10 ffmpeg \
  -hide_banner \
  -y \
  -i "$PILOT_INPUT" \
  -filter_complex \
    "[0:v]fps=30,split=2[v1080][v720];\
[v1080]scale=1920:1080:force_original_aspect_ratio=decrease:force_divisible_by=2,pad=1920:1080:(ow-iw)/2:(oh-ih)/2[v1080out];\
[v720]scale=1280:720:force_original_aspect_ratio=decrease:force_divisible_by=2,pad=1280:720:(ow-iw)/2:(oh-ih)/2[v720out]" \
  -map "[v1080out]" -map 0:a:0 \
  -map "[v720out]"  -map 0:a:0 \
  -c:v libx264 \
  -preset slow \
  -pix_fmt yuv420p \
  -profile:v:0 high -level:v:0 4.1 \
  -profile:v:1 high -level:v:1 4.0 \
  -crf:v:0 21 -maxrate:v:0 4500k -bufsize:v:0 9000k \
  -crf:v:1 22 -maxrate:v:1 2500k -bufsize:v:1 5000k \
  -g 180 \
  -keyint_min 180 \
  -sc_threshold 0 \
  -force_key_frames:v:0 "expr:gte(t,n_forced*6)" \
  -force_key_frames:v:1 "expr:gte(t,n_forced*6)" \
  -c:a aac \
  -b:a 128k \
  -ac 2 \
  -ar 48000 \
  -f hls \
  -hls_time 6 \
  -hls_playlist_type vod \
  -hls_segment_type fmp4 \
  -hls_flags independent_segments \
  -hls_fmp4_init_filename "init.mp4" \
  -hls_segment_filename "$PILOT_OUTPUT/%v/seg_%05d.m4s" \
  -master_pl_name master.m3u8 \
  -var_stream_map "v:0,a:0,name:1080p v:1,a:1,name:720p" \
  "$PILOT_OUTPUT/%v/playlist.m3u8" \
  2> "$PILOT_ROOT/logs/ffmpeg.log"
```

What important sections do:

- `fps=30` normalizes both outputs to the same frame rate.
- `split=2` sends the decoded source into two scale pipelines.
- `scale` resizes without stretching.
- `pad` makes the result exactly 16:9 if the input differs slightly.
- `-map` pairs each video rendition with an audio stream.
- `libx264` selects H.264.
- `yuv420p` maximizes device compatibility.
- `crf`, `maxrate`, and `bufsize` balance quality, size, and bitrate peaks.
- The GOP and forced-keyframe options align six-second boundaries.
- AAC audio is normalized to stereo, 48 kHz, and 128 Kbps.
- `vod` creates a finite Video on Demand playlist ending with `#EXT-X-ENDLIST`.
- `%v` is replaced with the rendition name from `var_stream_map`.
- `nice -n 10` gives ordinary application work higher CPU scheduling priority than FFmpeg. It is not a hard CPU limit.

If the server has four CPUs and production services must remain responsive, you can reserve one CPU by inserting:

```text
taskset -c 0-2
```

before `nice`. Confirm CPU numbering with `nproc` first. This slows encoding but reduces interference.

During encoding, monitor:

```bash
htop
df -h "$PILOT_ROOT"
tail -f "$PILOT_ROOT/logs/ffmpeg.log"
```

Also watch the existing websites. If their response time worsens, stop FFmpeg with `q`, restrict CPU use, and rerun in a new output version.

### Step 8.4 — Understand the expected output

For a one-hour video with six-second segments, expect approximately 600 segments per rendition, or roughly 1,200 segment objects total, plus playlists and initialization files.

List results:

```bash
find "$PILOT_OUTPUT" -type f -printf '%P\n' | sort | less
du -sh "$PILOT_INPUT" "$PILOT_OUTPUT"
find "$PILOT_OUTPUT/1080p" -name 'seg_*.m4s' | wc -l
find "$PILOT_OUTPUT/720p" -name 'seg_*.m4s' | wc -l
```

The segment counts should normally match.

---

## 9. Validate the package locally

Do not upload until every check in this section passes.

### Step 9.1 — Inspect the playlists

```bash
sed -n '1,120p' "$PILOT_OUTPUT/master.m3u8"
sed -n '1,80p' "$PILOT_OUTPUT/1080p/playlist.m3u8"
sed -n '1,80p' "$PILOT_OUTPUT/720p/playlist.m3u8"
```

The master should contain two `#EXT-X-STREAM-INF` entries with:

- Different `BANDWIDTH` values
- `RESOLUTION=1920x1080`
- `RESOLUTION=1280x720`
- H.264 and AAC codec identifiers

Each variant should contain:

- `#EXT-X-TARGETDURATION:6`
- `#EXT-X-PLAYLIST-TYPE:VOD`
- `#EXT-X-INDEPENDENT-SEGMENTS`
- `#EXT-X-MAP`
- Segment entries
- `#EXT-X-ENDLIST`

### Step 9.2 — Confirm duration and streams

```bash
ffprobe -v error -show_streams -show_format -of json "$PILOT_OUTPUT/master.m3u8"
```

Confirm the duration is close to the source and both video qualities appear.

### Step 9.3 — Decode every output segment

This is slow but valuable for a production candidate:

```bash
ffmpeg -v error -i "$PILOT_OUTPUT/1080p/playlist.m3u8" -f null -
ffmpeg -v error -i "$PILOT_OUTPUT/720p/playlist.m3u8" -f null -
```

No output means no decoder errors were detected.

### Step 9.4 — Watch locally

Use VLC or FFplay:

```bash
ffplay "$PILOT_OUTPUT/master.m3u8"
```

Inspect at least:

- The first minute
- Several scrolling/code sections
- A terminal section
- A slide or diagram
- The midpoint
- The final minute
- Audio/video synchronization
- Seeking to several distant positions

### Step 9.5 — Add a poster

A **poster** is the still image shown before playback:

```bash
ffmpeg \
  -ss 00:05:00 \
  -i "$PILOT_INPUT" \
  -frames:v 1 \
  -vf "scale=1280:-2" \
  "$PILOT_OUTPUT/poster.webp"
```

Choose a timestamp that does not reveal private information or an awkward transition.

### Step 9.6 — Add pilot captions

Even if complete captions are not ready, create a small valid file to test the delivery path. Save this as `captions/en.vtt` inside the output directory:

```text
WEBVTT

00:00:00.000 --> 00:00:04.000
KStack Devs caption delivery test.

00:00:05.000 --> 00:00:09.000
If you can read this, the English caption track works.
```

Create an Arabic test file at `captions/ar.vtt` with reviewed Arabic text. UTF-8 encoding must be preserved.

These pilot captions test plumbing only; do not represent accessibility completion.

### Step 9.7 — Generate checksums

From the output directory:

```bash
cd "$PILOT_OUTPUT"
find . -type f ! -name SHA256SUMS.txt -print0 \
  | sort -z \
  | xargs -0 sha256sum \
  > SHA256SUMS.txt
sha256sum --check SHA256SUMS.txt
```

The final command should report every file as `OK`.

---

## 10. Upload safely to R2

Upload immutable media objects first, variant playlists second, and the master playlist last. A viewer can discover the lesson only after the master exists.

All commands use the same long cache lifetime because the prefix is versioned and immutable.

### Step 10.1 — Upload media segments

```bash
rclone copy "$PILOT_OUTPUT" "$PILOT_REMOTE" \
  --include '*.m4s' \
  --header-upload 'Content-Type: video/iso.segment' \
  --header-upload 'Cache-Control: public, max-age=31536000, immutable' \
  --transfers 8 \
  --checkers 16 \
  --progress
```

### Step 10.2 — Upload initialization MP4 files

```bash
rclone copy "$PILOT_OUTPUT" "$PILOT_REMOTE" \
  --include '*.mp4' \
  --header-upload 'Content-Type: video/mp4' \
  --header-upload 'Cache-Control: public, max-age=31536000, immutable' \
  --transfers 4 \
  --progress
```

### Step 10.3 — Upload poster and captions

```bash
rclone copy "$PILOT_OUTPUT" "$PILOT_REMOTE" \
  --include '*.webp' \
  --header-upload 'Content-Type: image/webp' \
  --header-upload 'Cache-Control: public, max-age=31536000, immutable' \
  --progress

rclone copy "$PILOT_OUTPUT" "$PILOT_REMOTE" \
  --include '*.vtt' \
  --header-upload 'Content-Type: text/vtt; charset=utf-8' \
  --header-upload 'Cache-Control: public, max-age=31536000, immutable' \
  --progress
```

### Step 10.4 — Upload variant playlists

```bash
rclone copy "$PILOT_OUTPUT" "$PILOT_REMOTE" \
  --include '*/playlist.m3u8' \
  --header-upload 'Content-Type: application/vnd.apple.mpegurl' \
  --header-upload 'Cache-Control: public, max-age=31536000, immutable' \
  --progress
```

### Step 10.5 — Upload checksum manifest

```bash
rclone copyto \
  "$PILOT_OUTPUT/SHA256SUMS.txt" \
  "$PILOT_REMOTE/SHA256SUMS.txt" \
  --header-upload 'Content-Type: text/plain; charset=utf-8' \
  --header-upload 'Cache-Control: public, max-age=31536000, immutable' \
  --progress
```

### Step 10.6 — Verify before publishing the master

Compare local and remote file sizes:

```bash
rclone check "$PILOT_OUTPUT" "$PILOT_REMOTE" \
  --size-only \
  --one-way \
  --exclude '/master.m3u8'
rclone size "$PILOT_OUTPUT"
rclone size "$PILOT_REMOTE"
```

Counts and total bytes should match except for `master.m3u8`, which has intentionally not been uploaded yet.

Inspect the remote tree:

```bash
rclone tree "$PILOT_REMOTE"
```

### Step 10.7 — Publish the master playlist last

```bash
rclone copyto \
  "$PILOT_OUTPUT/master.m3u8" \
  "$PILOT_REMOTE/master.m3u8" \
  --header-upload 'Content-Type: application/vnd.apple.mpegurl' \
  --header-upload 'Cache-Control: public, max-age=31536000, immutable' \
  --progress
```

The manifest should now be available at `$PILOT_MANIFEST_URL`.

Run one final comparison now that the publication marker exists:

```bash
rclone check "$PILOT_OUTPUT" "$PILOT_REMOTE" --size-only --one-way
```

This time it should finish without missing-file or size-difference errors.

Never rerun with changed bytes under this same remote prefix. If anything needs changing, increment `PILOT_VERSION` and publish a new prefix.

---

## 11. Validate HTTP, CORS, and caching

### Step 11.1 — Inspect the master response

```bash
curl -sS -D - -o /dev/null \
  -H 'Origin: http://localhost:8088' \
  "$PILOT_MANIFEST_URL"
```

Look for:

```text
HTTP/2 200
content-type: application/vnd.apple.mpegurl
cache-control: public, max-age=31536000, immutable
access-control-allow-origin: http://localhost:8088
cf-cache-status: MISS or HIT
```

Run the same command again. It should normally become `HIT`, although tiered-cache behavior and propagation can affect the first requests.

### Step 11.2 — Inspect a segment

```bash
export PILOT_SEGMENT_URL="https://${PILOT_VIDEO_HOST}/${PILOT_PREFIX}/720p/seg_00000.m4s"

curl -sS -D - -o /dev/null \
  -H 'Origin: http://localhost:8088' \
  "$PILOT_SEGMENT_URL"
```

Confirm:

```text
content-type: video/iso.segment
cache-control: public, max-age=31536000, immutable
access-control-allow-origin: http://localhost:8088
```

Request it again and inspect `CF-Cache-Status` and `Age`. `HIT` means the object came from Cloudflare cache. `MISS` means Cloudflare fetched it from R2. `DYNAMIC` means the Cache Rule is not making it eligible.

### Step 11.3 — Test an unauthorized browser origin

```bash
curl -sS -D - -o /dev/null \
  -H 'Origin: https://not-approved.example' \
  "$PILOT_MANIFEST_URL"
```

It should not return an `Access-Control-Allow-Origin` header for the unapproved origin. The URL remains publicly downloadable; this test verifies browser CORS behavior, not secrecy.

### Step 11.4 — Inspect actual playlist contents remotely

```bash
curl -sS "$PILOT_MANIFEST_URL"
```

Open one returned variant URL and confirm its relative segment URLs resolve successfully.

---

## 12. Test HLS in a browser before touching Devs

This isolates HLS/R2/CDN problems from React and Vidstack problems.

Create a small file named `hls-pilot.html` outside the repositories:

```html
<!doctype html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>Devs HLS pilot</title>
    <style>
      body { margin: 0; padding: 24px; color: #fff; background: #111; font-family: sans-serif; }
      video { width: min(100%, 1100px); background: #000; }
      pre { white-space: pre-wrap; }
    </style>
  </head>
  <body>
    <h1>Devs HLS pilot</h1>
    <video id="video" controls crossorigin="anonymous">
      <track
        kind="subtitles"
        src="REPLACE_WITH_THE_ENGLISH_VTT_URL"
        srclang="en"
        label="English"
      />
      <track
        kind="subtitles"
        src="REPLACE_WITH_THE_ARABIC_VTT_URL"
        srclang="ar"
        label="العربية"
      />
    </video>
    <pre id="status">Loading…</pre>

    <script src="https://cdn.jsdelivr.net/npm/hls.js@1/dist/hls.min.js"></script>
    <script>
      const source = "REPLACE_WITH_THE_PILOT_MANIFEST_URL";
      const video = document.querySelector("#video");
      const status = document.querySelector("#status");

      if (video.canPlayType("application/vnd.apple.mpegurl")) {
        video.src = source;
        status.textContent = "Using the browser's native HLS engine";
      } else if (window.Hls?.isSupported()) {
        const hls = new Hls();
        hls.loadSource(source);
        hls.attachMedia(video);
        hls.on(Hls.Events.MANIFEST_PARSED, (_, data) => {
          status.textContent = `Loaded ${data.levels.length} qualities`;
        });
        hls.on(Hls.Events.LEVEL_SWITCHED, (_, data) => {
          const level = hls.levels[data.level];
          status.textContent = `Current quality: ${level.height}p; declared bitrate: ${Math.round(level.bitrate / 1000)} Kbps`;
        });
        hls.on(Hls.Events.ERROR, (_, data) => {
          console.error(data);
          status.textContent = `HLS error: ${data.type} / ${data.details}`;
        });
      } else {
        status.textContent = "This browser cannot play HLS";
      }
    </script>
  </body>
</html>
```

Replace the manifest and both caption URL placeholders. From that file's directory, serve it:

```bash
python3 -m http.server 8088
```

Open:

```text
http://localhost:8088/hls-pilot.html
```

Why use a server instead of double-clicking the file? A local `file://` page has a special origin and does not reproduce the CORS behavior of an actual website.

The CDN-hosted hls.js script is acceptable for an isolated pilot. In Devs, install and bundle `hls.js` rather than making production playback depend on JSDelivr.

---

## 13. Browser and network test matrix

Record results rather than relying on memory.

### Functional tests

For each browser/device:

- Start from the beginning.
- Seek to 10, 30, and 50 minutes.
- Pause for one minute and resume.
- Switch tabs and return.
- Enter and leave fullscreen.
- Change playback speed.
- Change quality manually if the final player exposes it.
- Enable English and Arabic pilot captions.
- Rotate a phone between portrait and landscape.
- Confirm audio remains synchronized near the end.
- Confirm no errors appear in the browser console.

Minimum devices:

| Platform | Browser | Result |
|---|---|---|
| Desktop | Chrome | |
| Desktop | Firefox | |
| macOS/iPhone | Safari native HLS | |
| Android phone | Chrome | |
| Real Saudi mobile or campus network | Available browser | |

### Network-throttling tests

In Chrome DevTools:

1. Open **Network**.
2. Disable browser cache only when testing cold behavior.
3. Create custom throttling profiles.
4. Start fast, then reduce throughput while playing.
5. Observe the status text and `.m4s` requests.

Suggested profiles:

| Profile | Download | Latency | Expected observation |
|---|---:|---:|---|
| Strong Wi-Fi | 10 Mbps | 30 ms | 1080p should be stable |
| Moderate | 3 Mbps | 80 ms | It may choose or switch to 720p |
| Weak | 1.5 Mbps | 150 ms | Tests whether the current ladder is too heavy |
| Very weak | 700 Kbps | 250 ms | Likely demonstrates need for a lower rendition |

Do not confuse the DevTools setting `3 Mbps` with 3 megabytes per second. It is normally megabits per second.

Pass criteria:

- On a stable connection above the selected rendition's required bitrate, playback should not repeatedly stall.
- When bandwidth falls from strong to moderate, quality should change without visible corruption.
- Seeking on a reasonable connection should resume within a few seconds.
- If 720p cannot remain stable on realistic weak connections, add a carefully tested 540p rendition rather than pretending two qualities are sufficient.

### Cold versus warm cache

- A **cold** request is the first request after purge or eviction and may be a cache miss.
- A **warm** request is repeated while the object remains cached.

Measure both. Never judge the CDN only from a warm local browser cache.

---

## 14. Integrate the pilot into Devs

Do this only after the isolated HLS test passes.

As of 18 August 2026, the pilot integration is implemented in Devs. It was added as a dedicated `STATIC_HLS` path without changing the existing Mux workflow or pretending an HLS URL is a Mux playback ID.

The implementation includes:

1. A provider named `STATIC_HLS` rather than merely `R2`. HLS is the playback format; R2 is replaceable storage.
2. A configured media base URL such as `https://video-test.example.com`.
3. A database field containing the relative versioned manifest path, not a hard-coded provider domain.
4. An admin registration endpoint for an already encoded HLS package.
5. Server-side validation that the path is relative, belongs to the configured media host, and cannot trigger SSRF.
6. A `READY` transition only after the remote master and required metadata have been validated.
7. Vidstack plus locally bundled `hls.js` in the frontend.
8. External VTT caption-track support.
9. Updated admin wording that does not mention Mux for static HLS.
10. Automated tests for provider mapping, validation, public DTOs, and playback rendering.

Recommended persisted information:

```text
provider: STATIC_HLS
playback_path: pilots/representative-code-lesson/2026-08-13-v1/master.m3u8
duration_seconds: measured duration
source_checksum_sha256: checksum of the original or package manifest
encoding_version: 2026-08-13-v1
captions:
  - language: en
    path: .../captions/en.vtt
  - language: ar
    path: .../captions/ar.vtt
```

Keeping the hostname in configuration lets KStacks move from R2 to another CDN without rewriting every database row.

Once that implementation exists:

1. Register the pilot media through the admin API/UI.
2. Attach it to one draft course.
3. Preview the draft.
4. Repeat the browser/device matrix inside the actual Devs page.
5. Publish only after media status and playback pass.
6. Test Arabic and English route navigation around the player.
7. Test SSR by directly loading and refreshing the lesson URL.

At this stage, compare Vidstack behavior—not the minimal hls.js page—with Mux Player.

---

## 15. Measure encoding and cost

### Encoding performance

Read:

```bash
cat "$PILOT_ROOT/logs/encode-time.txt"
```

Record:

```text
Source duration:
Wall-clock encoding time:
Peak memory:
Average/peak CPU observation:
Did any hosted application slow down?
```

Encoding speed is:

```text
source duration ÷ encoding wall time
```

Example: a 60-minute video encoded in 30 minutes is 2× realtime. A 60-minute video encoded in 180 minutes is 0.33× realtime.

### Storage measurement

```bash
du -sb "$PILOT_INPUT"
du -sb "$PILOT_OUTPUT/1080p"
du -sb "$PILOT_OUTPUT/720p"
du -sb "$PILOT_OUTPUT"
rclone size "$PILOT_REMOTE"
```

Record decimal gigabytes for:

- Original master
- 1080p package
- 720p package
- Captions/poster/metadata
- Total retained bytes

Estimate 100 hours:

```text
100-hour HLS estimate = one-hour HLS output × 100
100-hour retained estimate = (one-hour original + HLS output) × 100
```

R2 Standard storage is currently `$0.015 per GB-month`, with the first 10 GB-month shared free across the account.

Approximate monthly storage cost:

```text
max(total retained GB - remaining free-tier GB, 0) × $0.015
```

Do not assume every future lesson compresses identically. Camera footage, animation, scrolling, and noise compress less efficiently than a mostly static editor.

### Request estimate

At six-second segments:

```text
3,600 seconds ÷ 6 = approximately 600 segment requests per watched hour
```

A viewer normally downloads one rendition at a time, not both entire renditions. Quality switches may cause some overlap.

Cloudflare cache hits do not need R2 origin reads. Cache misses do. Compare R2 analytics and `CF-Cache-Status` rather than assuming a 100% hit ratio.

### Decision comparison

Complete this table after testing:

| Metric | R2/HLS pilot | Mux Basic comparison |
|---|---:|---:|
| One-hour retained storage | | Provider-managed |
| Projected 100-hour monthly storage cost | | Approximately current Mux rates |
| Encoding time owned by KStacks | | None |
| Startup time | | |
| Seek time | | |
| Weak-network behavior | | |
| Captions | Manual workflow | Managed/manual options |
| QoE analytics | Must add | Included |
| Signed playback | Must design | Built in |
| Operational effort | | |

---

## 16. What can fail and how to diagnose it

### Browser reports a CORS error

Check:

- Exact page origin in the R2 CORS policy
- `GET` and `HEAD` methods
- `Range` in allowed headers
- `crossorigin="anonymous"` on the player
- Whether old responses were cached before CORS was configured
- The blocked request in DevTools Network

Use `curl` with an `Origin` header. Without it, R2 may correctly omit CORS response headers.

### Master downloads instead of playing

Inspect `Content-Type`. It should be `application/vnd.apple.mpegurl`.

### Player loads the master but segments fail

Check:

- Relative URLs inside the variant playlist
- Segment existence in R2
- `.m4s` content type
- CORS on segment responses
- The `init_*.mp4` object
- Whether the master was published before all children finished uploading

### `CF-Cache-Status` is `DYNAMIC`

The Cache Rule is not matching or did not make the extension eligible. Confirm hostname, path, DNS proxy state, and rule order.

### Second request is still `MISS`

Possible reasons include propagation, different edge locations, cache-control conflicts, a mismatched cache rule, or eviction. Inspect the full headers and Cloudflare Trace before changing the video.

### Quality never switches

Check:

- Master contains both variants.
- Both variants have accurate `BANDWIDTH` and `RESOLUTION`.
- The browser uses hls.js or native adaptive HLS.
- DevTools throttling is applied to the video requests.
- Keyframes and segment boundaries are aligned.

### Playback glitches during a quality switch

Inspect segment durations and keyframe alignment. Confirm both encodes use the same normalized frame rate, forced-keyframe timing, GOP, and audio timing.

### 720p text is unreadable

First improve the recording standard:

- Larger editor font
- Higher contrast
- Less UI clutter
- Avoid tiny terminal panes

Then consider CRF/bitrate changes. More bitrate cannot recreate details destroyed by scaling.

### 720p repeatedly buffers on realistic networks

Add a tested 540p fallback. The goal is a usable emergency rendition, not a nominal resolution count.

### Audio drifts by the end

Inspect source timestamps and variable frame rate. Compare source and output near the final minute. We may need explicit audio timestamp correction for that source.

### Oracle-hosted sites slow down

- Stop or pause the encode.
- Restrict CPU affinity with `taskset`.
- Keep `nice` enabled.
- Encode off-peak.
- Consider a separate encoder if this becomes routine.

### Rclone reports permission denied

Confirm:

- Remote provider is Cloudflare.
- Endpoint uses the correct account ID.
- Token includes Object Read & Write.
- Token is scoped to the correct bucket.
- Bucket name matches exactly.

Do not solve a permissions problem by issuing an unrestricted account-wide token unless genuinely required.

---

## 17. Cleanup and rollback

### If the pilot fails before Devs integration

Keep local logs and measurements, then remove only the exact pilot prefix:

```bash
rclone delete "$PILOT_REMOTE" --rmdirs
```

Before running it, print and verify the resolved remote path:

```bash
printf '%s\n' "$PILOT_REMOTE"
```

Never run deletion against the remote root or bucket root.

Revoke the pilot R2 token when it is no longer needed.

### If a new encoding version fails after an older version is published

Do not modify the older prefix. Keep the database pointing to the older manifest and remove only the failed new version after investigation.

### Local cleanup

Keep:

- Original master or its verified backup
- FFmpeg command/version
- Encoding log and timing report
- Pilot measurements
- Checksums
- Final accepted package until production migration is complete

Temporary rejected renditions can be removed after their failure is documented.

---

## 18. Final go/no-go checklist

### Technical

- [ ] Source checksum verified after transfer
- [ ] Source decode check passes
- [ ] Five-minute rehearsal passes before full encode
- [ ] Full 1080p and 720p outputs decode without errors
- [ ] Segment counts and durations align
- [ ] Master lists accurate qualities and bandwidth
- [ ] Correct MIME type for every extension
- [ ] CORS works only for intended browser origins
- [ ] Cache Rule produces `HIT` on repeated requests
- [ ] Smart Tiered Cache enabled if desired
- [ ] Chrome, Firefox, Safari/iPhone, and Android tested
- [ ] Real Saudi network tested
- [ ] Seeking, captions, speed, fullscreen, and rotation tested
- [ ] Weak-network result determines whether 540p is required
- [ ] Oracle production workloads remain healthy during encoding

### Cost and operations

- [ ] One-hour output bytes recorded
- [ ] Raw-master backup cost included
- [ ] 100-hour projection remains within budget
- [ ] Encoding time is operationally acceptable
- [ ] Publishing process is versioned and repeatable
- [ ] R2 credentials are bucket-scoped and protected
- [ ] Team understands public URLs can be downloaded
- [ ] Ownership exists for future encoding failures and browser regressions

### Devs integration

- [x] `STATIC_HLS` provider implemented
- [x] Relative manifest path stored
- [x] Vidstack and local hls.js bundle integrated
- [x] VTT tracks integrated
- [x] Admin wording updated
- [x] Media readiness validation implemented
- [x] Automated backend and frontend tests pass
- [ ] Real draft lesson passes the full device matrix

If the pilot passes technically but fails operationally, Mux or Bunny Stream may still be the better system. Engineering time, monitoring, security, and recovery are real costs even when the provider invoice is small.

---

## 19. Recommended sequence in one page

```text
1. Create isolated R2 test bucket and custom domain
2. Configure exact CORS, Cache Rule, and Smart Tiered Cache
3. Create bucket-scoped uploader credentials
4. Configure rclone securely
5. Transfer and checksum the one-hour source
6. Inspect and decode-check the source
7. Encode a representative five-minute excerpt
8. Inspect readability and tune if needed
9. Encode the complete one-hour video into a new version
10. Decode-check and inspect every rendition
11. Add poster, pilot VTT files, and checksums
12. Upload segments and initialization files
13. Upload captions/poster and variant playlists
14. Compare local and remote content
15. Publish master.m3u8 last
16. Verify HTTP types, CORS, and cache behavior with curl
17. Test isolated browser playback and network throttling
18. Measure storage, encoding time, and service impact
19. Implement STATIC_HLS properly in Devs
20. Repeat the complete matrix inside a real draft lesson
21. Compare measured results with Mux and make the decision
```

---

## 20. Primary references

- [Cloudflare R2 pricing](https://developers.cloudflare.com/r2/pricing/)
- [Cloudflare R2 S3 credentials](https://developers.cloudflare.com/r2/get-started/s3/)
- [Cloudflare R2 CORS](https://developers.cloudflare.com/r2/buckets/cors/)
- [Cloudflare R2 custom domains and public buckets](https://developers.cloudflare.com/r2/buckets/public-buckets/)
- [Cloudflare cache for R2](https://developers.cloudflare.com/cache/interaction-cloudflare-products/r2/)
- [Cloudflare Cache Rules](https://developers.cloudflare.com/cache/how-to/cache-rules/create-dashboard/)
- [Cloudflare cache response statuses](https://developers.cloudflare.com/cache/concepts/cache-responses/)
- [Cloudflare Tiered Cache](https://developers.cloudflare.com/cache/how-to/tiered-cache/)
- [Cloudflare R2 durability](https://developers.cloudflare.com/r2/reference/durability/)
- [Rclone installation](https://rclone.org/install/)
- [Rclone S3/Cloudflare provider](https://rclone.org/s3/)
- [FFmpeg HLS muxer documentation](https://ffmpeg.org/ffmpeg-formats.html)
- [FFmpeg keyframe documentation](https://ffmpeg.org/ffmpeg.html)
- [Vidstack HLS provider](https://vidstack.io/docs/player/api/providers/hls/)
- [Vidstack player overview](https://vidstack.io/docs/player/)
