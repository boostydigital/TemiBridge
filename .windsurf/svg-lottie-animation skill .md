---
name: svg-lottie-animation
description: >
  Create complex, production-grade SVG animations that rival Lottie files in richness and detail.
  Use this skill whenever the user asks for animated SVGs, Lottie-style animations, motion graphics,
  animated illustrations, animated icons, CSS keyframe animations, SVG SMIL animations, or any request
  involving "complex animations", "smooth animations", "animated scenes", or "moving graphics" in HTML.
  Also trigger for requests like "animate this SVG", "make it move like Lottie", "create an animated
  logo", "liquid/fluid animation", "particle effects", "morphing shapes", or "physics-based animation".
  Always use this skill when animation quality, realism, or complexity is important — even if the user
  only says "make it look nice" or "make it feel alive".
---

# SVG Lottie-Style Animation Skill

Create complex, layered SVG animations delivered as single-file HTML with CSS keyframes and/or
inline SVG SMIL. The goal is Lottie-quality motion: smooth, layered, physically plausible, and
visually rich — all without external dependencies.

---

## Design Thinking Before Coding

Before writing any code, commit to answers for:

1. **Subject matter** — What is the scene? (liquid, fire, particles, characters, icons, logos, etc.)
2. **Mood / Aesthetic** — Cinematic? Playful? Minimal? Dark? Luxury?
3. **Key motion metaphors** — What real-world physics/behavior should this evoke?
   - Liquid → surface tension, viscosity, ripple decay
   - Fire/steam → turbulence, heat shimmer, rising dissipation
   - Particles → gravity, drag, spawn/die lifecycle
   - Morphing → easing curves, anticipation, follow-through
4. **Layering plan** — List animation layers from back to front (atmosphere → body → surface → detail → highlight)

---

## SVG Animation Toolkit

### CSS Keyframe Patterns (preferred for complex scenes)

```css
/* Organic float */
@keyframes float {
  0%, 100% { transform: translateY(0) rotate(0deg); }
  33%       { transform: translateY(-8px) rotate(1.5deg); }
  66%       { transform: translateY(-4px) rotate(-1deg); }
}

/* Liquid wave via path morphing */
@keyframes wave {
  0%   { d: path("M 0 50 Q 25 40 50 50 Q 75 60 100 50 L 100 100 L 0 100 Z"); }
  50%  { d: path("M 0 50 Q 25 60 50 50 Q 75 40 100 50 L 100 100 L 0 100 Z"); }
  100% { d: path("M 0 50 Q 25 40 50 50 Q 75 60 100 50 L 100 100 L 0 100 Z"); }
}

/* Steam / smoke wisp */
@keyframes wisp {
  0%   { transform: translateY(0) scaleX(1); opacity: 0; }
  15%  { opacity: 0.8; }
  60%  { transform: translateY(-60px) scaleX(2.5) rotate(12deg); opacity: 0.4; }
  100% { transform: translateY(-110px) scaleX(0.5) rotate(-8deg); opacity: 0; }
}

/* Radial pulse / ripple */
@keyframes ripple {
  0%   { transform: scale(0.2); opacity: 0.9; }
  100% { transform: scale(1.8); opacity: 0; }
}

/* Crema / surface swirl */
@keyframes swirl {
  from { transform: rotate(0deg); }
  to   { transform: rotate(360deg); }
}

/* Bubble rise */
@keyframes bubble {
  0%   { transform: translateY(0) scale(0.4); opacity: 0; }
  15%  { opacity: 0.7; }
  100% { transform: translateY(-80px) scale(1); opacity: 0; }
}

/* Caustic light shift */
@keyframes caustic {
  0%, 100% { transform: scaleX(1) scaleY(1); opacity: 0.15; }
  50%       { transform: scaleX(1.4) scaleY(0.6); opacity: 0.35; }
}

/* Pour stream pulse */
@keyframes pourStream {
  0%, 100% { opacity: 0.85; transform: scaleX(1); }
  50%       { opacity: 1; transform: scaleX(1.15); }
}

/* Splash drop fall */
@keyframes dropFall {
  0%   { transform: translateY(0) scale(1); opacity: 1; }
  100% { transform: translateY(70px) scale(0.2); opacity: 0; }
}

/* Splash arc (lateral) — uses CSS custom props --dx */
@keyframes arcFly {
  0%   { transform: translate(0, 0) scale(1); opacity: 0.9; }
  100% { transform: translate(var(--dx, 20px), 60px) scale(0.3); opacity: 0; }
}

/* Glow pulse */
@keyframes glow {
  0%, 100% { opacity: 0.25; }
  50%       { opacity: 0.6; }
}

/* Glint flash */
@keyframes glint {
  0%, 80%, 100% { opacity: 0; }
  88%            { opacity: 0.95; }
}
```

### SVG Gradient & Filter Recipes

```xml
<!-- Depth liquid fill -->
<linearGradient id="liquidFill" x1="0" y1="0" x2="0" y2="1">
  <stop offset="0%"   stop-color="#LIGHTEST"/>
  <stop offset="50%"  stop-color="#MID"/>
  <stop offset="100%" stop-color="#DARKEST"/>
</linearGradient>

<!-- Radial crema / surface sheen -->
<radialGradient id="surfaceSheen" cx="50%" cy="50%" r="55%">
  <stop offset="0%"   stop-color="HIGHLIGHT" stop-opacity="0.9"/>
  <stop offset="70%"  stop-color="MID"/>
  <stop offset="100%" stop-color="EDGE"/>
</radialGradient>

<!-- Radial glow aura (place under object) -->
<radialGradient id="glowAura" cx="50%" cy="50%" r="50%">
  <stop offset="0%"   stop-color="COLOR" stop-opacity="0.5"/>
  <stop offset="100%" stop-color="COLOR" stop-opacity="0"/>
</radialGradient>

<!-- Glass / transparent wall -->
<linearGradient id="glassWall" x1="0" y1="0" x2="1" y2="0">
  <stop offset="0%"   stop-color="rgba(200,240,255,0.18)"/>
  <stop offset="15%"  stop-color="rgba(200,240,255,0.06)"/>
  <stop offset="85%"  stop-color="rgba(200,240,255,0.06)"/>
  <stop offset="100%" stop-color="rgba(200,240,255,0.22)"/>
</linearGradient>

<!-- Blur filter for steam/fog -->
<filter id="softBlur">
  <feGaussianBlur stdDeviation="2.5"/>
</filter>

<!-- Inner shadow -->
<filter id="innerGlow">
  <feGaussianBlur stdDeviation="4" result="blur"/>
  <feComposite in="SourceGraphic" in2="blur" operator="over"/>
</filter>
```

---

## Layer Architecture (always follow this order)

Build SVG scenes in this z-order for maximum realism:

```
1. ATMOSPHERE     — ambient glow orbs, background fog (position: fixed, blur: 60-100px)
2. CAST SHADOW    — soft ellipse under object, glow-pulse animation
3. BASE OBJECT    — cup/glass/container body with gradient fill
4. LIQUID FILL    — clipped to container, layered gradients
5. SURFACE LAYER  — crema, foam, meniscus ellipse (most detailed)
6. SURFACE FX     — ripple rings, swirl paths, caustic patches (animated)
7. BUBBLES        — rising circles inside clip, staggered delays
8. CONTAINER WALL — transparent overlay, edge highlights
9. RIM / LIP      — top ellipse, glint line
10. HANDLE/DETAIL — structural elements
11. SHINE/GLINT   — animated flash strokes (glintFlash keyframe)
12. STEAM/SMOKE   — wisps above container (blur filter, high z)
13. POUR EFFECTS  — stream paths, splash drops, arc particles (if pouring)
```

---

## Stagger System

Always stagger repeated elements (bubbles, wisps, rings) using CSS custom props or inline `animation-delay`:

```html
<!-- Stagger pattern: element index × base_interval -->
<circle style="--dur:3.5s; --delay:0s"/>
<circle style="--dur:4.0s; --delay:0.7s"/>
<circle style="--dur:3.2s; --delay:1.4s"/>
<circle style="--dur:4.5s; --delay:2.1s"/>
```

Vary both `animation-duration` AND `animation-delay` per element — identical timing feels mechanical.

---

## Liquid Realism Checklist

For any liquid scene, ensure ALL of these are present:

- [ ] **Depth gradient** — liquid darker at bottom, lighter/more saturated at surface
- [ ] **Surface meniscus** — ellipse at liquid top with radial gradient
- [ ] **Wave/slosh** — subtle `d: path()` morph or translateY oscillation
- [ ] **Subsurface reflection** — faint semi-transparent ellipse deep in liquid
- [ ] **Caustic patches** — scaleX/scaleY morphing ellipses inside liquid
- [ ] **Bubbles** — at least 4-6 rising circles, varied size & timing
- [ ] **Ripple rings** — 3 concentric ellipses at surface, staggered scale 0→1.8, opacity 0.9→0
- [ ] **Crema/foam layer** — if hot beverage: radialGradient ellipse + counter-rotating swirl paths
- [ ] **Cast glow** — radialGradient ellipse under container, glow-pulse

---

## Steam / Smoke Realism Checklist

- [ ] **4-6 wisps** minimum, each unique width/height/position/timing
- [ ] **blur filter** applied (feGaussianBlur stdDeviation 2-4)
- [ ] **scaleX expansion** during rise (thin at base → wide at top)
- [ ] **Rotation** alternates direction per wisp
- [ ] **Opacity curve** — fade in fast (0→15%), hold mid, fade out slow (60→100%)
- [ ] **Color** matched to liquid: tea = warm beige, coffee = darker taupe, water = cool white

---

## Pour / Splash System

When animating a pour:

```
STREAM       — 2 paths (main + secondary at slight offset), pourStream keyframe
IMPACT GLOW  — static radialGradient ellipse at landing point
RINGS        — 3 splash-ring ellipses, staggered splashRing keyframe
DROP FALL    — 3-4 circles, dropFall keyframe (vertical)
ARC DROPS    — 3-4 circles, arcFly keyframe (diagonal, use --dx custom prop)
LABEL        — tiny Cinzel text below splash
```

---

## Typography & Aesthetic Defaults

- **Display font**: `Cinzel` (serif, elegant) — for labels and titles
- **Body font**: `Cormorant Garamond` — for descriptions
- **Dark background**: `#0b0d0f` or `#08090a`
- **Text color**: `#f0e8d8` (warm white)
- **Label style**: `font-size: 0.7rem; letter-spacing: 0.4em; text-transform: uppercase; opacity: 0.8`

Import via:
```css
@import url('https://fonts.googleapis.com/css2?family=Cormorant+Garamond:ital,wght@0,300;1,300&family=Cinzel:wght@300;400&display=swap');
```

---

## Output Format

Always deliver as a **single self-contained HTML file**:
- All CSS in `<style>` tag
- All SVG inline in `<body>`
- No external JS dependencies
- Google Fonts import via `@import` in CSS
- `overflow: hidden` on body to prevent scrollbar from animations
- Save to `/mnt/user-data/outputs/[name].html` and use `present_files`

---

## Quality Gates

Before delivering, mentally check:

1. **No mechanical timing** — every repeated element has unique duration + delay
2. **Layering complete** — all 13 layers present where applicable
3. **Liquid checklist** — all 8 items checked if scene contains liquid
4. **No flat colors** — every solid surface uses at minimum a 2-stop gradient
5. **Ambient atmosphere** — fixed glow orbs behind scene add depth
6. **Cast shadows** — every floating object has a glow/shadow beneath it
7. **Glints animate** — at least one `glintFlash` element on reflective surfaces
8. **Steam present** — all hot liquids have steam wisps above them

---

## Example Scenes Inventory

Reference these when user asks for specific scene types:

| Scene | Key Techniques |
|-------|---------------|
| Hot tea cup | Steam wisps × 5, crema-less surface rings, warm saucer |
| Espresso cup | Crema swirl (dual counter-rotate), micro-bubbles, dark ceramic |
| Water glass | Wave path morph, glass transparency, caustics, rising bubbles |
| Pour stream | pourStream + dropFall + arcFly + splashRing |
| Candle flame | SVG path morph (flame flicker), particle rise, wax melt |
| Rain / droplets | arcFly drops from top, ripple rings on surface |
| Lava lamp blob | path morph between blob shapes, slow float |
| Loading spinner | strokeDashoffset animation, gradient stroke |

---

## Quick-Start Template

```html
<!DOCTYPE html>
<html>
<head>
<style>
  @import url('https://fonts.googleapis.com/css2?family=Cinzel:wght@300;400&display=swap');
  *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
  body {
    background: #0b0d0f;
    font-family: 'Cinzel', serif;
    color: #f0e8d8;
    overflow: hidden;
    display: flex;
    align-items: center;
    justify-content: center;
    min-height: 100vh;
  }
  /* Atmosphere */
  .ambient {
    position: fixed; border-radius: 50%; pointer-events: none;
    filter: blur(80px); animation: glowPulse 8s ease-in-out infinite;
  }
  @keyframes glowPulse {
    0%, 100% { opacity: 0.06; transform: scale(1); }
    50%       { opacity: 0.1 transform: scale(1.2); }
  }
  /* ... scene-specific keyframes here ... */
</style>
</head>
<body>
  <div class="ambient" style="background:#COLOR1; width:500px; height:500px; top:-10%; left:-10%;"></div>
  <div class="ambient" style="background:#COLOR2; width:400px; height:400px; bottom:-10%; right:-5%; animation-delay:4s;"></div>

  <!-- SVG scene goes here -->

</body>
</html>
```