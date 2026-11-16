# Instalación Lista para Copiar (`Minecraft-Ready`)

Todo el contenido de `Minecraft-Ready/` replica directamente la raíz de la instancia (`.minecraft/` o `Cisco's Fantasy Medieval RPG [Dragonfyre]/`): `mods/`, `config/`, `kubejs/`, `resourcepacks/`, `xaero/` y `saves/`.

## 1. Descargar o actualizar con Git LFS
```powershell
git lfs install
git clone https://github.com/KurosagiStar/RPGQuestSystem-Minecraft.git
```
*(Si ya lo tienes clonado: `git pull origin main` y `git lfs pull`.)*

## 2. Instalar en Cliente (Jugadores)
> **IMPORTANTE:** **NO borres tu carpeta `config/` completa** (perderías las configs del modpack de Cisco).
1. Si ya habías iniciado el juego antes, borra **únicamente** tus carpetas `config/xaero/` y `config/easy_npc/` para evitar conflictos viejos de minimapa o presets.
2. Copia las carpetas **`mods/`**, **`config/`**, **`kubejs/`**, **`resourcepacks/`** y **`xaero/`** desde `Minecraft-Ready/` hacia la raíz de tu instancia (`Cisco's Fantasy Medieval RPG [Dragonfyre]/` o `.minecraft/`), **sobrescribiendo archivos existentes**.
2. *(Opcional, solo si juegas en solitario con el mundo pre-armado)* Descomprime `Minecraft-Ready/saves/Cisco_Deau_World.zip` dentro de `saves/`.
3. **Reinicia Minecraft.** El mod `rpgquestengine-1.0.0.jar` activa automáticamente los iconos azules en Xaero's Minimap sin tocar `options.txt`.

## 3. Instalar en Servidor Dedicado
1. Copia **`mods/`**, **`config/`** y **`kubejs/`** desde `Minecraft-Ready/` a la raíz del servidor.
2. Si usas el mundo ya armado, descomprime `Minecraft-Ready/saves/Cisco_Deau_World.zip` como tu carpeta del mundo (`world/`).

## 4. Comandos Útiles (OP)
- `/rpgquest ui` — Gestor visual para spawnear o mover los 25 NPCs principales.
- `/rpgquest respawn_saved` — Re-spawnea o reubica los NPCs en sus coordenadas guardadas.
- `/rpgquest reload` — Recarga `rpg_quests.json` en caliente.
