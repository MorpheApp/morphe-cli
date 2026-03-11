# Morphe/ MorpheCLI (will be changed to Morphe Desktop) - Developer Guide

## Project Overview
Morphe Desktop is a command-line and a GUI application that uses Morphe Patcher to patch Android apps. It has 2 parts
- **CLI**: Opens when the user calls the .jar file from a terminal.
- **GUI**: Opens when the user double-clicks the jar

## Design Thinking

Before coding, understand the context and commit to a BOLD aesthetic direction:
- **Purpose**: What problem does this interface solve? Who uses it?
- **Tone**: Commit to a distinct direction: brutally minimal, maximalist chaos, luxury/refined, lo-fi/zine, dark/moody, soft/pastel, editorial/magazine, brutalist/raw, retro-futuristic, handcrafted/artisanal, organic/natural, art deco/geometric, playful/whimsical, industrial/utilitarian, etc. There are infinite varieties to start from and surpass. Use these as inspiration, but the final design should feel singular, with every detail working in service of one cohesive direction.
- **Constraints**: Technical requirements (framework, performance, accessibility).
- **Differentiation**: What makes this UNFORGETTABLE? What's the one thing someone will remember?

**CRITICAL**: Choose a clear conceptual direction and execute it vigorously. Bold maximalism and refined minimalism both work - the key is intentionality, not intensity.

Then implement working code (HTML/CSS/JS, React, Vue, etc.) that is:
- Production-grade, functional, and responsive
- Visually striking and memorable
- Cohesive with a clear aesthetic point-of-view
- Meticulously refined in every detail

**Morphe CLI Context (MANDATORY — root every decision here)**:
- **Purpose**: This is a desktop GUI wrapper for morphe-cli (Morphe Patcher). Users select an APK (or APKM bundle), choose from community patches, apply them, sign the output, and optionally push to device via ADB. Core flows: drag-and-drop APK, searchable patch list with descriptions/categories, live log console during patching (long-running process), progress visualization, output APK management. Users are power users/modders (ReVanced/Morphe veterans) — they want speed, transparency, and raw power, not hand-holding.
- **Who uses it**: Tech-savvy Android tinkerers who already run the CLI. Desktop advantage: easier file management, bigger screen for logs/patches, keyboard shortcuts, multi-window feel.
- **Differentiation (UNFORGETTABLE)**: Make the patching process feel like a cyber-hacker ritual. Visual disassembly animation, neon code-rain progress, glitch effects on success/failure, terminal-style logs with syntax coloring. Someone should remember "that one desktop patcher that looks like it belongs in a cyberdeck". Beat Vary (existing simple Gio GUI) by being visually addictive yet perfectly functional.

**CRITICAL**: The UI must feel like a professional dev tool that secretly has underground soul. Never default to plain Material 3 mobile patterns.

## Frontend Aesthetics Guidelines

Focus on:
- **Typography**: Typography carries the design's singular voice. Choose fonts with interesting personality. Default fonts signal default thinking: skip Arial, Inter, Roboto, system stacks. Font choices should be inseparable from the aesthetic direction. Display type should be expressive, even risky. Body text should be legible, refined. Pair them like actors in a scene. Work the full typographic range: size, weight, case, spacing to establish hierarchy.
- **Color & Theme**: Commit to a cohesive aesthetic. Palettes should take a clear position: bold and saturated, moody and restrained, or high-contrast and minimal. Lead with a dominant color, punctuate with sharp accents. Avoid timid, non-committal distributions. Use CSS variables for consistency.
- **Motion**: Use animations for effects and micro-interactions. Prioritize CSS-only solutions for HTML. Use Motion library for React when available. Focus on high-impact moments: one well-orchestrated page load with staggered reveals (animation-delay) creates more delight than scattered micro-interactions. Use scroll-triggering and hover states that surprise.
- **Spatial Composition**: Unexpected layouts. Asymmetry. Overlap and z-depth. Diagonal flow. Grid-breaking elements. Dramatic scale jumps. Full-bleed moments. Generous negative space OR controlled density.
- **Backgrounds & Visual Details**: Create atmosphere and depth rather than defaulting to solid colors. Add contextual effects and textures that match the overall aesthetic. Apply creative forms like gradient meshes, noise and grain overlays, geometric patterns, layered transparencies and glassmorphism, dramatic or soft shadows and glows, parallax depth, decorative borders and clip-path shapes, print-inspired textures (halftone, duotone, stipple), knockout typography, and custom cursors.

**CRITICAL ADDITIONS FOR COMPOSE DESKTOP**:
- **Framework Reality**: You are writing **Kotlin + Jetpack Compose for Desktop** (not HTML/React). Use `@Composable`, `Modifier`, `MaterialTheme` (or fully custom), `Window`, `MenuBar`, `Tray` if needed. Target Windows/macOS/Linux with native window chrome or custom undecorated title bar. Output complete, copy-pasteable, production-ready code with `main()` + `application { Window(...) }`. Include previews where possible.
- **Theming Mastery** (use official custom design system patterns):
    - Extend `MaterialTheme` or build a full custom system with `CompositionLocal` (e.g., `LocalMorpheColors`, `LocalMorpheTypography`).
    - Default to **dark theme** (dev tool law). Create semantic colors: `neonPrimary`, `terminalGreen`, `patchAccent`, `errorGlitchRed`.
    - Typography: Pair a bold display font (load via `FontFamily` from resources — no Inter/Roboto) with **JetBrains Mono** or VT323-style monospace for logs/console. Make hierarchy dramatic (huge patch titles, tiny hex metadata).
    - Shapes: Sharp corners or subtle hexagonal cuts for "patcher" tech feel. Use `absoluteElevation` for dynamic surface tints.
- **Motion & Delight (Compose-native)**:
    - Staggered reveals on window open with `AnimatedVisibility` + `spring()` + `delayMillis`.
    - Patching progress: Custom `Canvas`-based animation (code rain, hex particles, or APK "decompiling" scanline effect — performant, not heavy).
    - Hover/glitch: `graphicsLayer` + `scale` + subtle `colorMatrix` for neon glows and chromatic aberration on active elements.
    - Never overdo micro-animations — one hero animation during patching > 50 tiny ones.
- **Desktop-Power Interactions**:
    - Drag & drop APK anywhere (use `onDrag` modifiers).
    - Keyboard shortcuts (Cmd/Ctrl+O, Enter to patch).
    - Context menus, tooltips, searchable LazyColumn for patches.
    - Live console output panel with auto-scroll and copy button.
    - Adaptive layout: Use `WindowSizeClass` — collapse to single pane on small windows, multi-pane (sidebar + preview + logs) on large.
- **Spatial Composition & Visual Depth**:
    - Asymmetric multi-pane layout: Left = searchable patches (with preview icons via Canvas or SVG), Center = APK info + drop zone, Right/Bottom = live terminal logs + progress.
    - Full-bleed hero moments during patching (overlay the whole window with animated background).
    - Subtle background: Terminal grid + very light noise texture (via `Canvas` or `BitmapShader`). Glassmorphism or heavy drop-shadows for cards.
- **Production-Grade Requirements**:
    - Full responsiveness + accessibility (`semantics` for screen readers, high contrast).
    - Performance: Keyed `LazyColumn`, state hoisting, minimal recomposition. Patching runs in background coroutine.
    - Error states with dramatic feedback (red glitch flash).
    - Include sample patch data and fake progress for demo.
    - Use only Compose Desktop + Material3 + kotlinx.coroutines (no external libs unless absolutely necessary).

**NEVER**:
- Use mobile-first Material patterns (bottom nav, FABs).
- Default fonts/colors/layouts.
- Make it look like another ReVanced Manager clone.
- Heavy GPU effects that kill performance on patching large APKs.
- Using generic AI-generated aesthetics like overused font families (Inter, Roboto, Arial, Space Grotesk, system fonts), clichéd color schemes (particularly purple gradients on white backgrounds), predictable layouts and component patterns, and cookie-cutter designs that lack context-specific character.

**INSTEAD**: 
- Commit violently to one singular aesthetic (THIS IS ONLY AN EXAMPLE. PICK SOMETHING DEPENDING ON THE CONTEXT, e.g., "Neon Cyberdeck Terminal" — dark void background, electric cyan/magenta accents, monospace logs, glitch hover states, code-rain progress). Or "Brutalist Dev Console" — raw, high-contrast, industrial. Or "Refined IntelliJ+Neon" (Jewel-inspired but with Morphe soul). Every pixel serves the "I am hacking my apps" fantasy.
- Build creatively on the user's intent, and make unexpected choices that feel genuinely designed for the context. Every design should feel distinct. Actively explore the full range: light and dark themes, unexpected font pairings, substantially varied aesthetic directions. Let the specific context drive choices, NOT familiar defaults.

**IMPORTANT**: Match implementation complexity to the aesthetic vision. Maximalist designs need elaborate code with extensive animations and effects. Minimalist or refined designs need restraint, elegance, and precision. All designs need careful attention to spacing, typography, and subtle details. Excellence comes from executing the vision well.

Then implement working, complete Kotlin code that is:
- Fully functional (drag-drop, patch selection, animated progress, live logs)
- Visually unforgettable
- Meticulously refined (perfect spacing, hover states, loading skeletons)
- Ready to compile in a standard Compose Desktop project


Remember: Claude you are capable of extraordinary, award-worthy creative work. Don't hold back, show what's truly possible, and commit relentlessly to a distinctive and unforgettable vision.
