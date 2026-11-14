# Instalación en la Instancia del Compañero

## 1. Descargar el repositorio con Git LFS
1. Instala **Git LFS** (https://git-lfs.com/) si no lo tienes.
2. Abre PowerShell y ejecuta:
```powershell
git lfs install
git clone https://github.com/KurosagiStar/RPGQuestSystem-Minecraft.git
```
*(Si ya clonaste el repo antes, entra a la carpeta y ejecuta `git lfs pull` y `git pull origin main`.)*

## 2. Copiar archivos a tu instancia de Minecraft
Ubica la carpeta raíz de tu instancia `Cisco's Fantasy Medieval RPG [Dragonfyre]` (donde están `mods`, `config`, `saves`) y copia el contenido de `Minecraft-Ready/`:

1. **`Minecraft-Ready/mods/rpgquestengine-1.0.0.jar`** → pégalo dentro de `mods/` (reemplaza si existe).
2. **`Minecraft-Ready/config/`** → copia y reemplaza dentro de `config/` (`rpg_quests.json`, `easy_npc/`, `xaero/`).
3. **`Minecraft-Ready/xaero/`** → copia y reemplaza en la raíz de la instancia (waypoints y mapa).
4. **`Minecraft-Ready/saves/Mundo_nuevo.zip`** → descomprímelo dentro de `saves/` para obtener la carpeta `saves/Mundo nuevo/`.

## 3. Al entrar al mundo
Si algún NPC ya spawneado tiene diálogos viejos, ejecuta como OP:
```text
/rpgquest sync_npcs
```
