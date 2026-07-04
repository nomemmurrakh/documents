# Brand assets

`logo.svg` is the master source of truth for the Documents mark. Everything else
(favicons, PNG exports, social previews, package icons) should be derived from it,
not redrawn.

## Concept

A document page with a folded top-right corner, filled with Kotlin's official brand
gradient, with the Kotlin arrow mark pressed subtly into the page like a watermark.

## Colors

| Name          | Hex       | Role                          |
|---------------|-----------|-------------------------------|
| Kotlin violet | `#7F52FF` | Gradient start (top-left)     |
| Kotlin pink   | `#C711E1` | Gradient midpoint             |
| Kotlin red    | `#E44857` | Gradient end (bottom-right)   |

## Files

- `logo.svg` — master vector mark
- `social-preview.svg` — GitHub social preview (logo + wordmark, 1280×640), composed from `logo.svg`
- `exports/` — rasterized PNGs generated from the SVGs above; regenerate with `rsvg-convert`
  (`brew install librsvg`) rather than hand-editing:
  - `logo-512.png`, `logo-256.png` — general use (README, package metadata)
  - `apple-touch-icon-180.png` — iOS home-screen icon size
  - `favicon-32.png`, `favicon-16.png` — browser favicon sizes
  - `social-preview.png` — upload to GitHub repo Settings → Social preview

## Regenerating exports

```sh
cd brand
rsvg-convert -w 512 -h 512 logo.svg -o exports/logo-512.png
rsvg-convert -w 256 -h 256 logo.svg -o exports/logo-256.png
rsvg-convert -w 180 -h 180 logo.svg -o exports/apple-touch-icon-180.png
rsvg-convert -w 32  -h 32  logo.svg -o exports/favicon-32.png
rsvg-convert -w 16  -h 16  logo.svg -o exports/favicon-16.png
rsvg-convert -w 1280 -h 640 social-preview.svg -o exports/social-preview.png
```

A combined multi-resolution `favicon.ico` isn't generated yet (no ImageMagick installed) —
needed once the docs site project exists.
