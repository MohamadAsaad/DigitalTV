---
version: alpha
colors:
  canvas: "#101419"
  card: "#B31A3548"
  text: "#FFFFFFFF"
  secondary: "#99FFFFFF"
  accent: "#67C9F5"
typography:
  display:
    fontFamily: "Outfit, sans-serif"
  body:
    fontFamily: "Roboto, sans-serif"
  utility:
    fontFamily: "Roboto Mono, monospace"
rounded:
  card: "18dp"
  pill: "999dp"
spacing:
  remote-safe-inset: "56dp"
components:
  settings-row:
    minimumHeight: "56dp"
    focus: "high-contrast-white-rim"
  home-card:
    surface: "translucent-night-blue"
    focusable: false
omitted:
  - section: elevation
    reason: "Android TV surfaces use translucent fills and focus rims instead of stacked shadows."
---

## Overview

Clean Your TV is a living-room launcher operated mainly with a directional remote. Its interface should feel like a quiet, night-time instrument panel: large readable time, restrained weather and prayer information, and app artwork that remains the main way into the TV.

The memorable signature is the contrast between a calm, oversized clock and compact translucent information cards. Do not make the home screen resemble a phone settings app, a web dashboard, or a busy notification feed.

## Colors

The canvas is deep charcoal; card surfaces use the existing translucent blue-gray treatment. White is reserved for primary content, muted white for supporting details, and sky blue for weather, selected controls, and focus-adjacent accents. Focus itself must remain visible in monochrome and never depend on color alone.

## Typography

Use the bundled Outfit face for large clock and weather values. Use Roboto for settings labels and supporting text. Keep date/time numerals clear at a distance; Arabic and Hebrew fallbacks must remain legible without changing row geometry.

## Layout

Keep the home header, cards, and five-slot dock inside generous safe insets. Remote focus should follow a predictable row-major path. Settings use a single vertical list with generous row height and a visible selected state; dialogs remain short and focusable from the remote.

## Elevation & Depth

Avoid blur and expensive shadow stacks. Separate cards from wallpaper with a translucent opaque-enough fill and a fine light rim.

## Shapes

Cards use restrained rounded corners. Pills and focus controls use the established launcher shapes. Avoid decorative borders that compete with tile artwork.

## Components

Home cards are display-only and must not intercept the remote. Settings rows use the existing `SettingsRow` style. Confirmation dialogs use Android's TV-friendly `AlertDialog` behavior and retain a clear cancel action.

## Do's and Don'ts

- Do keep the reading order and focus order aligned.
- Do reserve stable space for loading, unavailable, and stale-data states.
- Do provide a reduced-motion mode for slower TV hardware.
- Don't rely on network data being available at startup.
- Don't use animation, color, or tiny labels as the only way to convey state.
