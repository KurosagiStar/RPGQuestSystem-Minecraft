package com.rpgquestengine;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

public class GearRoadmapScreen extends Screen {
    private int activeTab = 0; // 0 = Armaduras, 1 = Armas
    private int scrollOffset = 0;
    private int maxScroll = 0;

    private record GearEntry(String itemId, String name, String source, String tip) {}
    private record TierRow(String badge, String title, int color, List<GearEntry> entries) {}

    private static final List<TierRow> ARMOR_TIERS = List.of(
            new TierRow("D", "INICIO (Tier 1)", 0xFF9CA3AF, List.of(
                    new GearEntry("minecraft:iron_chestplate", "Hierro", "Mineo básico", "Primer set sólido para aldeas y cuevas."),
                    new GearEntry("minecraft:diamond_chestplate", "Diamante", "Cuevas / Aldeanos", "Base para mejorar a Netherita o sets iniciales."),
                    new GearEntry("cisco_mod:adventurer_chestplate", "Adventurer", "Crafteo / Botín Cisco", "Set de aventurero con bonos tempranos de movilidad.")
            )),
            new TierRow("C", "MEDIO (Tier 2)", 0xFF38BDF8, List.of(
                    new GearEntry("minecraft:netherite_chestplate", "Netherita", "Nether + Plantilla", "Resistencia base para el Nether y jefes tempranos."),
                    new GearEntry("cataclysm:bone_reptile_chestplate", "Bone Reptile", "Cataclysm (Koboleton)", "Excelente defensa media contra daño físico."),
                    new GearEntry("cisco_mod:gilded_eagle_chestplate", "Gilded Eagle", "Crafteo Cisco", "Orientado a hechizos / agilidad intermedia.")
            )),
            new TierRow("B", "AVANZADO (Tier 3)", 0xFFA855F7, List.of(
                    new GearEntry("cisco_mod:brightsteel_chestplate", "Brightsteel", "Aleación Brightsteel", "Gran salto de stats + afinidad mágica/crítico."),
                    new GearEntry("cisco_mod:darksteel_armor_chestplate", "Darksteel", "Dark Core + Lingotes", "Armadura pesada clave antes de jefes mayores."),
                    new GearEntry("twilightforest:fiery_chestplate", "Fiery / Twilight", "Jefes Twilight Forest", "Quema atacantes y prepara para Cataclysm.")
            )),
            new TierRow("A", "JEFES (Tier 4)", 0xFFF59E0B, List.of(
                    new GearEntry("cataclysm:ignitium_chestplate", "Ignitium", "Ignis (Cataclysm)", "Alta armadura, inmune a lava y combinable con Élitros."),
                    new GearEntry("cataclysm:cursium_chestplate", "Cursium", "Maledictus (Cataclysm)", "Revive tras golpe mortal y otorga esquiva."),
                    new GearEntry("cisco_mod:descended_hero_chestplate", "Descended Hero", "Progresión Hero Cisco", "Puente directo hacia armaduras Ascendant.")
            )),
            new TierRow("S", "ENDGAME (Tier 5)", 0xFFEF4444, List.of(
                    new GearEntry("cisco_mod:ascended_hero_chestplate", "Ascendant Azure", "Endgame Cisco", "Set mítico de héroe ascendido con stats máximos."),
                    new GearEntry("cisco_mod:fallen_hero_armor_chestplate", "Fallen Hero", "Jefes / Endgame Cisco", "Variante ofensiva oscura de alto daño."),
                    new GearEntry("cisco_mod:fell_king_armor_chestplate", "Fell King", "The Fell King", "Armadura del Rey Caído para contenido máximo."),
                    new GearEntry("cisco_mod:sovereign_ascendant_chestplate", "Sovereign Ascendant", "Crafteo Final Cisco", "Tope absoluto de defensa y poder en Dragonfyre.")
            ))
    );

    private static final List<TierRow> WEAPON_TIERS = List.of(
            new TierRow("D", "INICIO (Tier 1)", 0xFF9CA3AF, List.of(
                    new GearEntry("minecraft:iron_sword", "Espada de Hierro", "Crafteo inicial", "Arma básica para primeras misiones."),
                    new GearEntry("minecraft:diamond_sword", "Espada de Diamante", "Cuevas / Comercio", "Recomendada encantar con Apotheosis."),
                    new GearEntry("simplyswords:diamond_katana", "Katana Diamante", "Simply Swords", "Ataques rápidos con buen DPS temprano.")
            )),
            new TierRow("C", "MEDIO (Tier 2)", 0xFF38BDF8, List.of(
                    new GearEntry("minecraft:netherite_sword", "Espada Netherita", "Mesa de Herrería", "Base confiable para imbuir gemas."),
                    new GearEntry("simplyswords:netherite_glaive", "Glaive Netherita", "Simply Swords", "Mayor alcance y barrido para oleadas."),
                    new GearEntry("cataclysm:black_steel_sword", "Black Steel", "Cataclysm", "Buen daño físico antes de armas de jefes.")
            )),
            new TierRow("B", "AVANZADO (Tier 3)", 0xFFA855F7, List.of(
                    new GearEntry("simplyswords:runic_rapier", "Runic Rapier", "Botín / Dungeons", "Arma rúnica escalable con runas y afijos."),
                    new GearEntry("cataclysm:coral_spear", "Coral Spear", "Leviathan / Sunken", "Gran alcance y daño elemental."),
                    new GearEntry("irons_spellbooks:amethyst_rapier", "Amethyst Rapier", "Iron's Spellbooks", "Híbrida cuerpo a cuerpo + poder mágico.")
            )),
            new TierRow("A", "JEFES (Tier 4)", 0xFFF59E0B, List.of(
                    new GearEntry("cataclysm:the_incinerator", "The Incinerator", "Ignis (Cataclysm)", "Espada ígnea pesada con habilidad de área."),
                    new GearEntry("cataclysm:soul_render", "Soul Render", "Maledictus (Cataclysm)", "Alabarda espiritual de altísimo DPS."),
                    new GearEntry("cataclysm:gauntlet_of_maelstrom", "Guantelete Maelstrom", "Scylla (Cataclysm)", "Control de masas y daño explosivo.")
            )),
            new TierRow("S", "ENDGAME (Tier 5)", 0xFFEF4444, List.of(
                    new GearEntry("cisco_mod:bjorn_helmet", "Equipo de Campeones", "Jefes Cisco / Tienda", "Combina armas con afijos Godforged + Gemas."),
                    new GearEntry("apotheosis:mythic_material", "Reforja Godforged", "Desafío Wither / Jefes", "Mejora tu arma Tier A/S a rareza Mítica en Apotheosis."),
                    new GearEntry("apotheosis:sigil_of_socketing", "Zócalos y Gemas", "Wither / Misiones", "Añade hasta 3-4 gemas perfectas a tu arma final.")
            ))
    );

    public GearRoadmapScreen() {
        super(Component.literal("Roadmap de Equipamiento"));
    }

    @Override
    protected void init() {
        super.init();
        UiStyle.addZenlessNavBar(this.width, 6, 0, this::addRenderableWidget);

        int boxX = 24;
        int boxY = 32;
        this.addRenderableWidget(new UiStyle.OreButton(boxX + 12, boxY + 28, 100, 18,
                Component.literal("§fArmaduras"), b -> {
            activeTab = 0;
            scrollOffset = 0;
        }, UiStyle.ACCENT_SLATE, 0xFF47474D, () -> activeTab == 0));

        this.addRenderableWidget(new UiStyle.OreButton(boxX + 118, boxY + 28, 100, 18,
                Component.literal("§fArmas"), b -> {
            activeTab = 1;
            scrollOffset = 0;
        }, UiStyle.ACCENT_SLATE, 0xFF47474D, () -> activeTab == 1));

        this.addRenderableWidget(new UiStyle.OreButton(this.width - 106, this.height - 28, 90, 20,
                Component.literal("§fCerrar"), b -> this.onClose(),
                UiStyle.ACCENT_SLATE, 0xFF47474D));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0xEE0A0A0D);

        int boxX = 24;
        int boxY = 32;
        int boxW = this.width - 48;
        int boxH = this.height - 68;

        UiStyle.drawRoundedCard(g, boxX - 1, boxY - 1, boxW + 2, boxH + 2, 4, UiStyle.BG_DARK, UiStyle.BORDER_PANEL, true);
        UiStyle.drawRoundedRect(g, boxX + 2, boxY + 2, boxW - 4, 22, 0, UiStyle.BG_HEADER);
        g.drawString(this.font, "§6§lROADMAP DE PROGRESIÓN §7— Tierlist Armaduras y Armas", boxX + 12, boxY + 9, 0xFFD4AF37);

        String subHint = "§7Pasa el cursor sobre cada objeto para ver dónde conseguirlo. §8(Rueda del ratón para bajar)";
        g.drawString(this.font, subHint, boxX + 230, boxY + 33, 0xAAAAAA);

        int listTop = boxY + 52;
        int listBottom = boxY + boxH - 8;
        int listH = listBottom - listTop;

        List<TierRow> rows = (activeTab == 0) ? ARMOR_TIERS : WEAPON_TIERS;
        int rowH = 56;
        int gap = 6;
        int totalContentH = rows.size() * (rowH + gap);
        maxScroll = Math.max(0, totalContentH - listH);
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;

        g.enableScissor(boxX + 6, listTop, boxX + boxW - 6, listBottom);

        GearEntry hoveredEntry = null;
        TierRow hoveredTier = null;

        int y = listTop - scrollOffset;
        for (TierRow row : rows) {
            if (y + rowH >= listTop && y <= listBottom) {
                UiStyle.drawRoundedCard(g, boxX + 10, y, boxW - 20, rowH, 2, UiStyle.BG_PANEL, 0, false);

                // Badge Tier a la izquierda
                int tierW = 92;
                UiStyle.drawRoundedCard(g, boxX + 14, y + 6, tierW, rowH - 12, 2, UiStyle.BG_CARD, row.color(), false);
                g.drawCenteredString(this.font, "§lTIER " + row.badge(), boxX + 14 + tierW / 2, y + 14, row.color());
                String shortLbl = row.title().contains("(") ? row.title().substring(0, row.title().indexOf('(')).trim() : row.title();
                g.drawCenteredString(this.font, "§7" + shortLbl, boxX + 14 + tierW / 2, y + 28, 0xCCCCCC);

                // Tarjetas de items
                int cardX = boxX + 14 + tierW + 8;
                int availW = (boxX + boxW - 14) - cardX;
                int count = row.entries().size();
                int cardW = Math.min(165, Math.max(110, (availW - (count - 1) * 6) / Math.max(1, count)));

                for (GearEntry entry : row.entries()) {
                    if (cardX + cardW > boxX + boxW - 10) break;
                    boolean hov = mouseX >= cardX && mouseX <= cardX + cardW
                            && mouseY >= Math.max(listTop, y + 5) && mouseY <= Math.min(listBottom, y + rowH - 5);

                    UiStyle.drawRoundedCard(g, cardX, y + 5, cardW, rowH - 10, 2,
                            hov ? 0xFF2B2B30 : UiStyle.BG_CARD, hov ? row.color() : 0, false);

                    ItemStack stack = resolveStack(entry.itemId());
                    g.renderItem(stack, cardX + 6, y + 12);

                    String nameTrim = this.font.plainSubstrByWidth(entry.name(), cardW - 30);
                    String srcTrim = this.font.plainSubstrByWidth(entry.source(), cardW - 30);
                    g.drawString(this.font, "§f" + nameTrim, cardX + 26, y + 12, 0xFFFFFF);
                    g.drawString(this.font, "§7" + srcTrim, cardX + 26, y + 24, 0xAAAAAA);

                    if (hov) {
                        hoveredEntry = entry;
                        hoveredTier = row;
                    }
                    cardX += cardW + 6;
                }
            }
            y += rowH + gap;
        }

        g.disableScissor();
        super.render(g, mouseX, mouseY, partialTick);

        if (hoveredEntry != null && hoveredTier != null) {
            List<Component> tip = List.of(
                    Component.literal("§l" + hoveredEntry.name()),
                    Component.literal("§7Tier: §f" + hoveredTier.title()),
                    Component.literal("§eOrigen: §f" + hoveredEntry.source()),
                    Component.literal("§aConsejo: §7" + hoveredEntry.tip())
            );
            g.renderComponentTooltip(this.font, tip, mouseX, mouseY);
        }
    }

    private static ItemStack resolveStack(String id) {
        Item it = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
        return (it != null && it != Items.AIR) ? new ItemStack(it) : new ItemStack(Items.NETHERITE_CHESTPLATE);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) (delta * 20)));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
