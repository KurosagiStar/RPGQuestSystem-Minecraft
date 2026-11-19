# Guía de Estilo UI — RPG Quest Engine

Estándar visual para todas las pantallas 2D del mod (`com.rpgquestengine.UiStyle`). Combina paneles oscuros con bisel pixel-art estilo **Darkomizer** (outline negro `#0B0B0D`, carbón `#2B2B2E`, cajas hundidas `#1C1C1F`) y botones táctiles 3D estilo **Minecraft Bedrock Ore UI**.

---

## 1. Reglas Fundamentales

1. **Usar siempre `UiStyle` para paneles y botones:** Usar `UiStyle.drawRoundedCard`, `UiStyle.drawRoundedRect`, `UiStyle.drawPillButton`, `UiStyle.drawProgressBar` y `UiStyle.OreButton`.
2. **Prohibido `Button.builder(...)` de Vanilla:** Los botones nativos son reemplazados por resourcepacks externos (como Cisco). Usar siempre `UiStyle.OreButton` para widgets de `Screen.addRenderableWidget()`.
3. **Paneles elevados vs cajas hundidas:**
   - Ventana raíz: `borderColor = BORDER_PANEL` (outline negro, bisel interior 3D elevado).
   - Paneles/slots internos: `borderColor = 0` (caja hundida estilo inventario Darkomizer: sombra arriba/izq, brillo abajo/der).
   - Tarjeta seleccionada/reclamable: `borderColor = ACCENT_GREEN`.
4. **Verde esmeralda como acento único principal:** Botones de acción positiva, pestaña activa, selección y barras de progreso usan la paleta Ore UI verde.

---

## 2. Paleta de Colores (`UiStyle`)

| Constante | Valor ARGB | Uso |
|---|---|---|
| `BG_DARK` | `0xFF2B2B2E` | Ventana raíz gris carbón mate (estilo Darkomizer) |
| `BG_PANEL` | `0xFF222225` | Columnas y sub-paneles hundidos |
| `BG_CARD` | `0xFF1C1C1F` | Tarjetas internas, slots y filas de lista |
| `BG_HEADER` | `0xFF333337` | Cabeceras de ventana y encabezados de categoría |
| `BORDER_PANEL` | `0xFF0B0B0D` | Outline negro exterior + bisel 3D elevado |
| `BORDER_SUBTLE` | `0xFF3E3E42` | Brillo sutil de bisel gris |
| `ACCENT_GREEN_DARK` | `0xFF3C9D30` | Botón primario / pestaña activa (reposo) |
| `ACCENT_GREEN` | `0xFF4BB83D` | Hover de botón primario, barra de progreso y borde activo |
| `ACCENT_RED_DARK` | `0xFF9E2B36` | Acciones destructivas (`Abandonar`, `Sacar`, `Rechazar`) |
| `ACCENT_SLATE` | `0xFF38383D` | Botones neutros / secundarios (`Cerrar`, `Ajustar HUD`, pestaña inactiva) |
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

## 4. Notas de Implementación (`UiStyle`)
- Las esquinas biseladas de `1px` en `GuiGraphics.fill()` mantienen nitidez pixel-art alineada al `guiScale` de Minecraft, igual que los packs de UI oscura tipo Darkomizer.
- `OreButton` sobrescribe `renderWidget()` para dibujar outline negro exterior, labio inferior 3D y bisel superior sin depender de texturas vanilla ni resourcepacks.

---

## 5. Pantallas Actualizadas
- [UiStyle.java](Code/rpg-quest-engine/src/main/java/com/rpgquestengine/UiStyle.java)
- [NpcDialogScreen.java](Code/rpg-quest-engine/src/main/java/com/rpgquestengine/NpcDialogScreen.java)
- [PartyScreen.java](Code/rpg-quest-engine/src/main/java/com/rpgquestengine/PartyScreen.java)
- [QuestJournalScreen.java](Code/rpg-quest-engine/src/main/java/com/rpgquestengine/QuestJournalScreen.java)
