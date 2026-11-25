package com.rpgquestengine;

import java.util.ArrayList;
import java.util.List;

/**
 * Defines a boss challenge that can be configured in rpg_quests.json under the "challenges" key.
 * Example:
 * {
 *   "id": "wither",
 *   "title": "Jefe Mundial: El Wither",
 *   "description": "Invoca y derrota a un Wither (4 Arena de Almas + 3 Calaveras Wither).",
 *   "iconItem": "minecraft:nether_star",
 *   "objective": "Elimina 1x Wither",
 *   "cooldownHours": 6,
 *   "xpLevels": 15,
 *   "rewardItems": [
 *     {"id": "cisco_mod:champion_loot_bag", "count": 2},
 *     {"id": "apotheosis:mythic_material", "count": 3},
 *     {"id": "apotheosis:epic_material", "count": 7},
 *     {"id": "apotheosis:sigil_of_socketing", "count": 1},
 *     {"id": "apotheosis:gem", "count": 1, "nbt": "{affix_data:{rarity:\"apotheosis:epic\"},gem:\"apotheosis:core/warlord\"}"}
 *   ],
 *   "tooltips": ["2x Champion Loot Bag", "3x Godforged Pearl", "7x Epic Material + Gema Epica", "1x Sigil of Socketing", "+15 Niveles"]
 * }
 */
public class ChallengeDefinition {
    public String id = "";
    public String title = "";
    public String description = "";
    public String iconItem = "minecraft:nether_star";
    public String objective = "";
    public int cooldownHours = 6;
    public int xpLevels = 0;
    public List<QuestDefinition.RewardItem> rewardItems = new ArrayList<>();
    // Human-readable tooltip per reward slot (parallel to rewardItems); auto-generated if empty
    public List<String> tooltips = new ArrayList<>();

    /** Returns the tooltip for slot i, or auto-generates one. */
    public String getTooltip(int i) {
        if (i < tooltips.size() && tooltips.get(i) != null && !tooltips.get(i).isEmpty()) {
            return tooltips.get(i);
        }
        if (i < rewardItems.size()) {
            QuestDefinition.RewardItem r = rewardItems.get(i);
            String name = r.id.contains(":") ? r.id.substring(r.id.indexOf(':') + 1) : r.id;
            return r.count + "x " + name;
        }
        return "";
    }
}
