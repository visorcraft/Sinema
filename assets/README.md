# assets/

Master imagery for Sinema. Every other reproduction of the icon and
wordmark across the repository (the Android `mipmap-*` launcher
icons, the `drawable-*` TV banner) is a derived raster that should
trace back to these files.

| File | Size | Purpose |
| ---- | ---- | ------- |
| `Sinema.png` | 1024×1024 | Master square icon — the blue "S" mark. Use this whenever you need a high-resolution square logo (README hero alternate, stores, slides). |
| `Sinema.ico` | 16/32/48/64/128/256 | Multi-resolution Windows-style icon, for any tooling that prefers `.ico` (favicons, GitHub repo, browser bookmarks). |
| `sinema_logo.png` | 662×153 | The horizontal "Sinema" wordmark used by `README.md` as the hero. Keep this in addition to `Sinema.png` — wordmark and square mark are both part of the brand. |
| `social-1024x512.png` | 1024×512 | GitHub social preview / OpenGraph card. Upload via **Settings → Social preview** on github.com. |
| `splash-screen.png` | 800×500 | Reserved for future on-device splash. Same palette as the social card. |
| `screenshots/` | varied | UI screenshots referenced from `README.md` (home screen, scene details, browse folders, Android TV banner). |

The brand is currently shipped only as rasters (no SVG master). If a
vector version is added later, drop `Sinema.svg` here and re-export
the raster sizes from it.

## Regenerating from the master square PNG

```sh
# Multi-resolution ICO.
for s in 16 32 48 64 128 256; do
  magick assets/Sinema.png -filter Lanczos -resize ${s}x${s} /tmp/sinema-${s}.png
done
magick /tmp/sinema-{16,32,48,64,128,256}.png assets/Sinema.ico
rm /tmp/sinema-*.png
```

## Where else the icon lives in the repository

- `app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png`
  — Android launcher icons (the actual square mark used on the
  Android TV device).
- `app/src/main/res/drawable-xhdpi/banner.png` — the wide Leanback
  banner that shows up in the Android TV launcher row.

Both of those are size-targeted assets bound to the Android resource
system. If the brand mark changes, update `assets/Sinema.png` first,
then re-export each of the per-density `mipmap-*` raster sizes from
it (and re-export the wordmark on the Leanback banner background).
