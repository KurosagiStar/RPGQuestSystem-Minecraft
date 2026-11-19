# Guía de Estilo UI — RPG Quest Engine

Estándar visual para todas las pantallas 2D del mod (`com.rpgquestengine.UiStyle`). Combina superficies oscuras mate estilo **Darkomizer** con botones táctiles 3D estilo **Minecraft Bedrock Ore UI**, renderizados con geometría vectorial OpenGL (`Tesselator`) y anti-aliasing nativo de pantalla.

---

## 1. Reglas Fundamentales

1. **Prohibido `GuiGraphics.fill()` para tarjetas o botones:** `fill()` se ajusta al `guiScale` y produce esquinas escalonadas. Usar siempre `UiStyle.drawSmoothRoundedRect`, `UiStyle.drawRoundedCard`, `UiStyle.drawPillButton` o `UiStyle.OreButton`.
2. **Prohibido `Button.builder(...)` de Vanilla:** Los botones nativos son reemplazados por resourcepacks externos (como Cisco). Usar siempre `UiStyle.OreButton` para widgets de `Screen.addRenderableWidget()`.
3. **Separación por elevación, no por outlines:** Las tarjetas internas y paneles secundarios usan contraste de fondo (`BG_PANEL`, `BG_CARD`, `BG_HEADER`) con `borderColor = 0`. Reservar bordes visibles solo para el marco principal (`BORDER_PANEL`) o estado activo/reclamable (`ACCENT_GREEN`).
4. **Verde esmeralda como acento único principal:** Botones de acción positiva, pestaña activa, selección y barras de progreso usan la paleta Ore UI verde. Evitar bordes dorados/amarillos (`BORDER_GOLD`) en botones o contenedores generales.

---

## 2. Paleta de Colores (`UiStyle`)

| Constante | Valor ARGB | Uso |
|---|---|---|
| `BG_DARK` | `0xF4141519` | Contenedor raíz / fondo principal de ventana |
| `BG_PANEL` | `0xEC1B1D23` | Columnas y paneles secundarios |
| `BG_CARD` | `0xE823262E` | Tarjetas internas, filas de lista y misiones |
| `BG_HEADER` | `0xF5282C35` | Cabeceras de ventana y encabezados de categoría |
| `BORDER_PANEL` | `0xFF282C35` | Borde fino exclusivo del marco exterior |
| `BORDER_SUBTLE` | `0x14FFFFFF` | Línea divisoria mínima (8% blanco) |
| `ACCENT_GREEN_DARK` | `0xFF3C9D30` | Botón primario / pestaña activa (reposo) |
| `ACCENT_GREEN` | `0xFF4BB83D` | Hover de botón primario, barra de progreso y borde activo |
| `ACCENT_RED_DARK` | `0xFF9E2B36` | Acciones destructivas (`Abandonar`, `Sacar`, `Rechazar`) |
| `ACCENT_SLATE` | `0xFF2E323C` | Botones neutros / secundarios (`Cerrar`, `Ajustar HUD`, pestaña inactiva) |
| `ACCENT_BLUE_DARK` | `0xFF2563EB` | Acento informativo opcional |
| `BORDER_GOLD` | `0xFFE5BE53` | Solo detalles puntuales de texto/slots especiales (no en botones) |

---

## 3. Componentes Estándar

### Tarjetas y Paneles (`drawRoundedCard`)
```java
// Contenedor raíz con sombra difuminada y borde fino
UiStyle.drawRoundedCard(g, x, y, w, h, 7, UiStyle.BG_DARK, UiStyle.BORDER_PANEL, true);

// Panel o tarjeta interna sin outline (solo elevación)
UiStyle.drawRoundedCard(g, x, y, w, h, 6, UiStyle.BG_PANEL, 0, false);

// Tarjeta seleccionada o lista para reclamar
UiStyle.drawRoundedCard(g, x, y, w, h, 6, UiStyle.BG_CARD, UiStyle.ACCENT_GREEN, true);
```
- **Radios recomendados:** `7` ventana principal, `6` paneles/tarjetas grandes, `4–5` filas, slots y cabeceras.

### Botones Interactivos (`UiStyle.OreButton`)
```java
// Botón primario (verde Ore UI)
this.addRenderableWidget(new UiStyle.OreButton(
    x, y, w, h, Component.literal("§f§lAceptar"),
    btn -> { /* acción */ },
    UiStyle.ACCENT_GREEN_DARK, UiStyle.ACCENT_GREEN
));

// Botón neutro (Cerrar / Ajustar)
new UiStyle.OreButton(x, y, w, h, Component.literal("§fCerrar"),
    btn -> this.onClose(), UiStyle.ACCENT_SLATE, 0xFF3A3F4B);

// Botón destructivo
new UiStyle.OreButton(x, y, w, h, Component.literal("§fAbandonar"),
    btn -> { /* acción */ }, UiStyle.ACCENT_RED_DARK, 0xFFB83542);

// Pestaña con indicador inferior blanco cuando está activa
new UiStyle.OreButton(x, y, w, h, Component.literal("§fDiario de Misiones"),
    btn -> { activeTab = 0; },
    UiStyle.ACCENT_SLATE, 0xFF3A3F4B, () -> activeTab == 0);
```
- Usar texto blanco (`§f` / `§f§l`) sobre botones verdes y rojos para máximo contraste.

### Botones Custom / Estados (`drawPillButton`)
Para botones dibujados manualmente en `render()` (ej. listas dinámicas o estados `Reclamado ✔` / `En progreso`):
```java
UiStyle.drawPillButton(g, this.font, x, y, w, h, "§f§lReclamar",
    UiStyle.ACCENT_GREEN_DARK, UiStyle.ACCENT_GREEN, 0, hovered);
```
- Estados deshabilitados/completados sin acción usan fondo apagado (`0xFF1E3324` o `UiStyle.ACCENT_SLATE`) y `hovered = false`.

### Barras de Progreso (`drawProgressBar`)
```java
UiStyle.drawProgressBar(g, x, y, w, 6, ratio, 0xFF2B2E38, UiStyle.ACCENT_GREEN);
```

---

## 4. Notas Técnicas OpenGL (`UiStyle`)
- `UiStyle` llama a `g.flush()` antes de emitir triángulos con `Tesselator` para respetar el orden de capas de `GuiGraphics`.
- Usa `RenderSystem.disableCull()` y vértices en orden anti-horario (CCW) para evitar que `GL_CULL_FACE` oculte los triángulos.
- El anti-aliasing se calcula dinámicamente con `1.0 / guiScale` (`getPhysicalPixel()`) para mantener bordes suaves en cualquier resolución o escala de interfaz.

---

## 5. Pantallas Actualizadas
- [UiStyle.java](Code/rpg-quest-engine/src/main/java/com/rpgquestengine/UiStyle.java)
- [NpcDialogScreen.java](Code/rpg-quest-engine/src/main/java/com/rpgquestengine/NpcDialogScreen.java)
- [PartyScreen.java](Code/rpg-quest-engine/src/main/java/com/rpgquestengine/PartyScreen.java)
- [QuestJournalScreen.java](Code/rpg-quest-engine/src/main/java/com/rpgquestengine/QuestJournalScreen.java)
