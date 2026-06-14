# GuideMe Travel — AI Cost Analysis (Discovery vs Full Upfront)

## Why Gemini prepay may show only ₹18k → ₹15k

The admin **Gemini prepay** line is **not total project cost**. It excludes Cloud TTS (~₹9,600/country) and Google Maps (~₹2,900), which were the largest savings from deferring offline audio.

| Line (India scale, 360 trips) | Full mode | Discovery (first migration) | Discovery (batched + Flash-Lite) |
|-------------------------------|-----------|----------------------------|----------------------------------|
| **Gemini prepay** | ~₹18,800 | ~₹15,600 | **~₹9,500** |
| Cloud TTS (upfront) | ~₹9,600 | ₹0 | ₹0 |
| Maps / GCP | ~₹2,900 | ~₹2,900 | ~₹2,900 |
| **Total upfront** | ~₹31,300 | ~₹18,500 | **~₹12,400** |

The first migration only removed the **guides phase** (~₹3,200 Gemini) but **added two Gemini calls per spot** (summary + preview) instead of one (full transcript), so Gemini prepay barely moved. **Batching + Flash-Lite** (current code) fixes that.

---

This document estimates Gemini and GCP costs per country under the **legacy full upfront** model vs the **discovery-first + lazy guide** model.

## Assumptions

| Component | Model / rate | Notes |
|-----------|--------------|-------|
| Expert spot curation | Gemini 2.5 Pro | Admin bulk; ~2× target spots per package |
| Bulk copy (genres, packages, extras, summaries, previews) | Gemini 2.5 Flash | Admin default |
| Full narration (lazy / manual) | Gemini 2.5 Flash | 250–400 words via `buildAttractionGuideScriptPrompt` |
| Cloud TTS | Wavenet ~$16 / 1M characters | ~1,800 chars per 300-word script |
| Cloud Translate | ~$20 / 1M characters | Non-English languages only |
| Google Places | ~$0.08 per package detail | Geocoding + nearby hotels/restaurants |
| USD → INR | 96 | Matches `curationEstimate.ts` |

## Catalog scale (per country)

Based on [`curationEstimate.ts`](../backend/functions/src/curationEstimate.ts) constants:

- **12 genres** × **30 packages** = **360 trip cards**
- **~18 spots/package**, **~35% unique** after dedupe ≈ **2,268 unique attractions** (large countries)
- Smaller countries/regions use proportionally fewer packages when curated manually

| Region | ISO | Est. packages | Est. unique spots |
|--------|-----|---------------|-------------------|
| India | IN | 360 | ~2,268 |
| USA | US | 360 | ~2,268 |
| Thailand | TH | ~300 | ~1,890 |
| Sri Lanka | LK | ~180 | ~1,134 |
| Bali (region) | ID | ~120 | ~756 |

## Gemini calls per package (discovery detail phase)

| Operation | Calls / package | Output |
|-----------|-----------------|--------|
| Expert spots | 1 (Pro) | Ranked attraction list |
| Why chosen | ~18 (Flash) | One sentence each |
| Attraction summary | ~18 (Flash) | 50–80 words each |
| Audio preview | ~18 (Flash) | 80–120 words each |
| Package extras | 1 (Flash) | Overview, tips, essentials, highlights, day summaries |
| Hotels / restaurants | 0 Gemini (Places) | Optional one-line blurbs off in cost-saver |

**Removed from discovery (moved to lazy):**

| Operation | Calls / package | Output |
|-----------|-----------------|--------|
| Full tour transcript | ~~18~~ → **0** | 250–400 words (was 350–500) |
| Bulk TTS pregen | ~~spots × langs~~ → **0** | MP3 in Storage |

## Per-country cost comparison (English only)

### Large countries — India, USA

| Cost line | BEFORE (full upfront) | AFTER (discovery only) | Savings |
|-----------|----------------------|------------------------|---------|
| Gemini catalog | ~$83 | ~$55 | ~34% |
| Gemini full narrations (details) | ~$8 | **$0** | 100% |
| Cloud TTS (1 language) | ~$100 | **$0** upfront | 100% upfront |
| Cloud Translate | $0 (en only) | $0 | — |
| Google Places | ~$30 | ~$30 | — |
| **Total upfront** | **~$221** | **~$85** | **~62%** |
| **Total upfront (INR)** | **~₹21,200** | **~₹8,200** | **~62%** |

Lazy cost **per downloaded trip** (~18 spots, 1 language):

- Gemini full narration: 18 × ~$0.002 ≈ **$0.04**
- TTS: 18 × ~$0.003 ≈ **$0.05**
- **~$0.09 per first-time download**, then **$0** for all future users (permanent cache)

Break-even: if **&lt;10%** of catalog trips ever get downloaded, discovery-first wins materially.

### Thailand

| | BEFORE | AFTER |
|---|--------|-------|
| Upfront USD | ~$175 | ~$68 |
| Upfront INR | ~₹16,800 | ~₹6,500 |
| Unique spots | ~1,890 | ~1,890 |

### Sri Lanka

| | BEFORE | AFTER |
|---|--------|-------|
| Upfront USD | ~$95 | ~$42 |
| Upfront INR | ~₹9,100 | ~₹4,000 |
| Unique spots | ~1,134 | ~1,134 |

### Bali (Indonesia region catalog)

| | BEFORE | AFTER |
|---|--------|-------|
| Upfront USD | ~$65 | ~$28 |
| Upfront INR | ~₹6,200 | ~₹2,700 |
| Unique spots | ~756 | ~756 |

## Multi-language impact

Legacy **full** mode pre-generated TTS for every spot × every language:

| Languages | BEFORE upfront TTS (India scale) | AFTER upfront TTS |
|-----------|----------------------------------|-------------------|
| 1 (en) | ~$100 | **$0** |
| 3 | ~$300 | **$0** |
| 5 | ~$500 | **$0** |

With discovery-first, translation + TTS run only when a user downloads in that language (or admin manually runs **Generate Full Guide**). For a 5-language catalog that is rarely downloaded, upfront savings exceed **85%**.

## Token model (sanity check — India, one package)

| Prompt | In tokens (est.) | Out tokens (est.) | Model |
|--------|------------------|-------------------|-------|
| Expert spots (×1) | 800 | 2,500 | Pro |
| Summary (×18) | 200 × 18 | 80 × 18 | Flash |
| Audio preview (×18) | 250 × 18 | 120 × 18 | Flash |
| Why chosen (×18) | 150 × 18 | 25 × 18 | Flash |
| Package extras (×1) | 1,200 | 800 | Flash |

**Discovery package total (Flash portion only):** ~450K input + ~180K output tokens ≈ **$0.15/package**  
**360 packages:** ~$54 Gemini Flash + ~$12 Pro spot curation ≈ **$66** (aligns with ~$55–85 range after Maps variance)

**Legacy full transcript (×18):** ~300 out × 18 ≈ 5,400 words ≈ **$0.02/package** Gemini — small vs TTS  
**Legacy TTS (×18 spots):** ~$0.28/package → **$100+ per country** dominates cost

## Operational guidance

1. **Generate Country (discovery)** — run once per country for browse-ready catalog (`generationStatus: PREVIEW_READY`).
2. **Download Trip** — lazy full guide + TTS; sets `GUIDE_READY`; never regenerates on cache hit.
3. **Admin → Generate Full Guide** — manual pre-generation for high-traffic packages (`destination_popularity` view/download &gt; 100).
4. **Popularity** — tracked in `destination_popularity/{packageId}`; no automatic pre-generation (manual admin action).

## Summary

| Country / region | BEFORE upfront | AFTER upfront | Upfront savings |
|------------------|----------------|---------------|-----------------|
| India | ~$221 / ₹21.2K | ~$85 / ₹8.2K | ~62% |
| USA | ~$221 / ₹21.2K | ~$85 / ₹8.2K | ~62% |
| Thailand | ~$175 / ₹16.8K | ~$68 / ₹6.5K | ~61% |
| Sri Lanka | ~$95 / ₹9.1K | ~$42 / ₹4.0K | ~56% |
| Bali | ~$65 / ₹6.2K | ~$28 / ₹2.7K | ~57% |

The dominant savings come from **deferring Cloud TTS and full narrations** until user intent is confirmed, while preserving the full Trip Details browsing experience via summaries and audio previews.
