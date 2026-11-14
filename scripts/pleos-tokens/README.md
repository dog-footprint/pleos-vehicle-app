# Pleos design tokens — how `ui/pleos/PleosTokens.kt` was derived

The official files from Pleos Playground (`Pleos_Connect_design_assets_215.fig`, `Pleos_Connect_display_guide_215.fig`)
are Figma binaries. They can be read offline:

```bash
cd scripts/pleos-tokens && npm init -y >/dev/null && npm i openfig-core
# edit the two paths at the top of each script, then:
node dump-variables.mjs   # variable sets (theme colours per Light/Dark, spacing, typo levels, form_factor sizes)
node dump-structure.mjs   # pages, text styles, components (SYMBOL nodes) with sizes, fonts
```

What we took (Hyundai theme, connect-s): `color/basic/*`, `color/static/*` and `color/alpha/*` ladders,
`informative/positive|negative|active`, `divider/secondary`, button sizes 100/80/64 with radius 32/24/16 and
padding 40/32/24, icon 48, list rows 112/100, status bar 96 / GNB 160, type scale Headline 60/56/48 · Title 40/36/32 ·
Body 30/28/26 · Label 24/20 and `Driving/Speed` 112/134. Fonts in the files are Asta Sans / 42dot Sans /
HyundaiSansUI; the app inherits the vehicle's system font instead of bundling them.
