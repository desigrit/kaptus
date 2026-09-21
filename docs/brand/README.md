# Kaptus Wordbird

<img src="kaptus-icon.png" width="160" height="160" alt="An ivory and orange folded bird inside a teal circle" />

Wordbird is Kaptus's selected brand mark. Its lifted orange wing carries two caption-shaped openings, while the ivory body and folded tail keep it recognizable as a bird at small sizes.

The background is teal (`#176B70`). The circular badge has transparent corners. Android uses a separate, full-bleed teal background and transparent bird foreground, so the launcher can apply its own circular or rounded-square mask without black corners or clipped artwork. A dedicated monochrome silhouette supports Android's themed icons.

## Assets

- `kaptus-icon.svg`: scalable circular badge for documentation and other brand uses.
- `kaptus-icon.png`: 512 by 512 transparent PNG export used in the main README.
- `kaptus-foreground.svg`: transparent, unmasked 108-unit artwork for adaptive-icon review.
- Android resources live under `app/src/main/res/drawable`, `mipmap-anydpi`, and `values/brand_colors.xml`.

## Editing

The editable vector master is [`tools/generate_brand_assets.py`](../../tools/generate_brand_assets.py). It preserves the selected concept's orange folds, ivory body, and upward pose as clean paths and restrained gradients.

Run `python tools/generate_brand_assets.py` from the repository root to regenerate the SVG and Android resources. If the artwork changes, also export the circular SVG to a 512 by 512 PNG with a transparent canvas and replace `kaptus-icon.png`.

The complete bird fits inside Android's central 66 dp safe circle on a 108 dp adaptive layer. Keep this padding when changing the artwork. Kaptus requires Android 13 or newer, so the adaptive resources cover every supported Android version and density.

The initial selected concept and a circular-badge refinement were created with built-in image generation. The production assets are a vector adaptation, which removes the generated image's transparency artifacts. The generation prompts are recorded in `prompts.json`.
