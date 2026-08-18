# KStack Devs — R2/HLS Pilot Results

> Repository record for the completed local static-HLS pilot.
>
> Measurement date: 18 August 2026
>
> Pilot asset: `representative-code-lesson/2026-08-17-v3`
>
> Tested duration: 3,600 seconds
> Status: local Chromium integration passed on corrected `2026-08-18-v4`; production go/no-go remains conditional

## Executive result

The self-managed R2/HLS path works end to end inside Devs. The Oracle ARM VPS encoded both renditions without disrupting its hosted services, the package uploaded and cached correctly, and Vidstack played it with seeking, captions, speed control, manual and automatic quality selection, network throttling, responsive layouts, and Arabic navigation. A corrected immutable `2026-08-18-v4` package was published and registered in a real Devs course; automatic downshift to 720p and subsequent upshift to 1080p passed.

The pilot is not yet a complete production go decision. The remaining material checks are:

1. Test Firefox, Safari/iPhone, and Android Chrome on real devices.
2. Confirm the public-downloadability tradeoff and operational ownership with the team.

## Encoding measurements

| Metric | Measured result |
|---|---:|
| Source duration | 60:00 (3,600 seconds) |
| Wall-clock encoding time | 33:40.17 (2,020.17 seconds) |
| Encoding speed | 1.78× realtime |
| Average CPU reported by GNU `time` | 368% (about 3.68 cores) |
| Four-core utilization observed in `htop` | About 95%+ per core during the encode |
| Peak resident memory | 946,632 KiB (about 0.90 GiB) |
| Hosted-service impact | No observed slowdown; existing services and a Dokploy redeploy remained healthy |

At this rate, 100 source hours require approximately 56.1 wall-clock hours if encoded sequentially on the same VPS. Production scheduling should still use `nice`, run off-peak, and avoid concurrent encodes until resource controls are automated.

## Storage measurements

| Item | Bytes | Decimal GB |
|---|---:|---:|
| One-hour source | 171,049,586 | 0.171050 |
| 1080p rendition directory | 174,189,017 | 0.174189 |
| 720p rendition directory | 121,269,083 | 0.121269 |
| Complete HLS output directory (`du -sb`, includes directory entries) | 295,594,100 | 0.295594 |
| Exact R2 object bytes | 295,545,346 | 0.295545 |
| Source plus exact R2 object bytes | 466,594,932 | 0.466595 |

The HLS package is approximately 1.73 times the size of this unusually compressible source. This ratio must not be assumed for camera footage, animation, noisy recordings, or other content types.

The complete package contained 601 media segments per rendition. The final R2 inventory reported 1,213 objects totaling 295,545,346 bytes. A final local-to-R2 size comparison reported all 1,213 files matching with zero differences.

## 100-hour projection

Assuming future content compresses like this pilot:

| Projection | Decimal GB |
|---|---:|
| HLS outputs only | 29.555 |
| Originals plus HLS outputs | 46.659 |

At the current R2 Standard rate of `$0.015/GB-month`:

- Without any free tier, 46.659 GB costs approximately **$0.70/month**.
- If the account's full 10 GB-month Standard free tier remains available, the calculated amount is approximately **$0.55/month** before Cloudflare's billing-unit rounding.
- The initial 1,210 uploads fit comfortably within the monthly one-million Class A free allowance.
- A watched hour uses roughly 600 segment requests plus a small number of playlist, initialization, poster, and caption requests. CDN hits reduce R2-origin Class B operations.
- R2 Standard currently has no Internet egress fee, but operation counts and other Cloudflare services still need monitoring.

For comparison, Mux Basic currently starts at `$0.003` per stored 1080p minute per month. At that list rate, 100 stored hours are approximately `$18/month` before cold-storage discounts or plan credits. Mux includes managed encoding, delivery, playback security options, and QoE analytics that this R2 design must operate separately.

## Playback observations

| Area | Result |
|---|---|
| Startup and normal playback | Passed in Vivaldi/Chromium |
| Seeking | Passed |
| Manual 1080p/720p switching | Passed |
| Automatic weak-network behavior | 720p remained usable; 1080p buffered under a sufficiently constrained profile |
| Automatic upward switching | Passed in Devs after publishing corrected `2026-08-18-v4` metadata |
| Playback at 2× | Passed |
| English and Arabic pilot VTT tracks | Passed after correcting the VTT package |
| Responsive and Arabic layouts | Passed locally |
| CDN caching | Repeated request changed from `MISS` to `HIT` |
| Devs SSR refresh | Passed after fixing the language-link hydration mismatch |
| Oracle workload coexistence | Passed during the observed encode |

The pilot VTT files validate caption delivery and player plumbing only. They are not complete production captions.

## Playlist bandwidth measurements

| Rendition | Measured average | RFC peak | Current declared `BANDWIDTH` |
|---|---:|---:|---:|
| 1080p | 386,988 bps | 1,767,801 bps | 5,090,800 bps |
| 720p | 269,390 bps | 911,117 bps | 2,890,800 bps |

Each rendition ends with a 0.066667-second partial segment. Dividing that segment's bytes by its duration in isolation produced misleading raw values of 6.96 Mbps and 3.99 Mbps. RFC 8216 instead defines peak bandwidth using contiguous segment sets whose total duration is between 0.5 and 1.5 times the six-second target duration. The short tail therefore cannot be measured alone; combining it with the preceding segment remains below the full-segment peaks shown above.

The next immutable master should declare approximately these measured values and include the missing frame rate:

```text
1080p: BANDWIDTH=1767801,AVERAGE-BANDWIDTH=386988,FRAME-RATE=30.000
720p:  BANDWIDTH=911117,AVERAGE-BANDWIDTH=269390,FRAME-RATE=30.000
```

The measured peak-to-average ratios still exceed Apple's `SHOULD` recommendation of 200%. This is understandable for mostly static coding content with occasional high-motion changes, and the absolute peaks remained modest in browser tests, but it is a documented authoring exception rather than a fully Apple-conformant ladder.

The corrected master was published under the immutable `2026-08-18-v4` prefix with `AVERAGE-BANDWIDTH`, RFC peak `BANDWIDTH`, `FRAME-RATE=30.000`, independent-segment signaling, and explicit absence of in-band closed captions. Local and remote package verification passed before publication. The package was registered through the Devs admin workflow and passed automatic quality switching in the real Vidstack integration.

## Cleanup decision

Do not delete the accepted `2026-08-17-v3` local or R2 package yet. It is the reproducible evidence behind this report and remains registered in the Devs test database.

Keep until the production decision is complete:

- The verified source or its verified backup
- The accepted HLS package
- `SHA256SUMS.txt`
- FFmpeg command and version
- `encode-time.txt`
- This measurement report

The two rehearsal inputs and four sample output versions were removed after their exact paths were verified, reclaiming approximately 112 MiB. The accepted source, one-hour input, logs, checksums, and `2026-08-17-v3` package were retained. Do not overwrite or mutate the published `2026-08-17-v3` R2 prefix.

## Remaining go/no-go work

- [x] Diagnose the raw peak segment for each rendition and calculate RFC peak bandwidth.
- [x] Publish a new immutable version with corrected `BANDWIDTH` and `AVERAGE-BANDWIDTH` metadata.
- [x] Repeat automatic quality switching against that version.
- [ ] Test Firefox desktop.
- [ ] Test Safari on an iPhone/iPad.
- [ ] Test Android Chrome, fullscreen, and rotation.
- [ ] Test at least one real Saudi mobile or home network.
- [ ] Confirm public URL/downloadability acceptance with the team.
- [ ] Assign ownership for encoding failures, caption review, browser regressions, and CDN/R2 monitoring.
- [ ] Decide between self-managed R2/HLS and Mux using both provider cost and engineering/operational cost.

## Pricing references

- [Cloudflare R2 pricing](https://developers.cloudflare.com/r2/pricing/)
- [Mux Video pricing](https://www.mux.com/pricing)
