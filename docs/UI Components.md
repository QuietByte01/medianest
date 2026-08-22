
### Design-First Chip Suite in [`AppChips.kt`](file:///Users/sachin/Lab/VibeCoded/medianest/app/src/main/java/com/medianest/ui/components/AppChips.kt)

| Component Name | Visual Design Characteristics | Example Usages |
|---|---|---|
| **`GlossyChip`** / **`AppChip`** | **Universal Glassmorphic Chip**: Translucent glass surface, customizable glowing border, leading/trailing content slots, and indicator dot. | General filters, speed chips (`"1.5x"`), options, tags |
| **`PaletteTagChip`** | **Tinted Pill Badge**: Semi-transparent tinted background + palette/accent border + bold uppercase typography + glowing selection dot. | Formats (`MP4`, `AVI`, `FLAC`), Codecs, Statuses, Category tags, Extension badges |
| **`WireframePreviewChip`** | **Geometric Frame Preview**: Live mini proportional wireframe rectangle canvas + proportion/dimension label + glowing border. | Aspect ratios (`16:9`, `9:16`, `1:1`, `4:3`, `21:9`), Canvas scales, Resolutions, Screen fits |
| **`IconActionChip`** | **Compact Tool Action**: Leading vector icon + action text + active glow feedback. | Toolbar buttons, Transform controls (`"Custom"`, `"Rotate"`, `"Flip"`), Toggles |
| **`TypographyTagChip`** | **Typography & Emoji Tag**: Supports live `FontFamily` text rendering, leading emoji badges (`"😍"`, `"🏷️"`), and styled captions. | Font selectors, Emoji pickers, Decorative tags, Quick action buttons (`"+ Text"`) |

---

### Usage Examples

```kotlin
// 1. Tinted Palette Tag Chip (e.g. Formats, Codecs, Statuses)
PaletteTagChip(
    label = "MP4",
    paletteColor = Color(0xFF06A77D), // or formatKey = "mp4" for auto-palette
    isSelected = isSelected,
    onClick = { onSelect("mp4") }
)

// 2. Geometric Wireframe Preview Chip (e.g. Aspect Ratios, Proportions)
WireframePreviewChip(
    label = "16:9",
    ratio = 16f / 9f,
    isSelected = isSelected,
    onClick = { onSelectRatio() },
    showWireframePreview = true
)

// 3. Icon Action Chip (e.g. Tools, Transform actions)
IconActionChip(
    label = "Custom",
    icon = Icons.Default.Crop,
    isSelected = isCustom,
    onClick = { toggleCustom() }
)

// 4. Typography & Emoji Tag Chip (e.g. Fonts, Emojis, Badges)
TypographyTagChip(
    label = "Cursive",
    fontFamily = FontFamily.Cursive,
    isSelected = isSelected,
    onClick = { selectFont() }
)
```

