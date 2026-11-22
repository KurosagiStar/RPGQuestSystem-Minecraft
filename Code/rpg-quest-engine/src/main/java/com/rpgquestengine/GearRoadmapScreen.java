package com.rpgquestengine;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

public class GearRoadmapScreen extends Screen {
    private int activeTab = 0; // 0 = Armaduras, 1 = Armas, 2 = Fortalecimiento
    private int scrollOffset = 0;
    private int maxScroll = 0;

    private record GearEntry(String itemId, String nbt, String name, String source, String tip, boolean keepTitle) {
        GearEntry(String itemId, String name, String source, String tip) {
            this(itemId, "", name, source, tip, false);
        }
        GearEntry(String itemId, String name, String source, String tip, boolean keepTitle) {
            this(itemId, "", name, source, tip, keepTitle);
        }
    }
    private record TierRow(String badge, String title, int color, List<GearEntry> entries) {}

    private static final List<TierRow> ARMOR_TIERS = List.of(
            new TierRow("D", "INICIO (Tier 1)", 0xFF9CA3AF, List.of(
                    new GearEntry("minecraft:iron_chestplate", "Hierro", "Mineo inicial",
                            "Set básico para explorar aldeas y primeras cuevas."),
                    new GearEntry("minecraft:diamond_chestplate", "Diamante", "Cuevas / Aldeanos",
                            "Encántala pronto; la armadura plana por sí sola cae rápido en late."),
                    new GearEntry("cisco_mod:adventurer_chestplate", "Adventurer", "Crafteo / Botín Cisco",
                            "Mejor primer set RPG (Early Game) por su movilidad y stats iniciales.")
            )),
            new TierRow("C", "MEDIO (Tier 2)", 0xFF38BDF8, List.of(
                    new GearEntry("cisco_mod:brightsteel_chestplate", "Brightsteel", "Aleación Brightsteel",
                            "Set recomendado Pre-Wither (Mid Game) antes de jefes duros."),
                    new GearEntry("minecraft:netherite_chestplate", "Netherita", "Nether + Plantilla",
                            "Sólida para el Nether; prioriza enchants de Protección y Vida Máx."),
                    new GearEntry("cataclysm:bone_reptile_chestplate", "Bone Reptile", "Cataclysm (Koboleton)",
                            "Buena resistencia física media para mazmorras.")
            )),
            new TierRow("B", "AVANZADO (Tier 3)", 0xFFA855F7, List.of(
                    new GearEntry("cisco_mod:darksteel_armor_chestplate", "Darksteel", "Dark Core + Lingotes",
                            "Transición oficial a Late Game. Imprescindible reforjarla con Apotheosis."),
                    new GearEntry("cisco_mod:gilded_eagle_chestplate", "Gilded Eagle", "Crafteo Cisco",
                            "Orientada a magia/movilidad; combínala con gemas de Vida Máx %."),
                    new GearEntry("twilightforest:fiery_chestplate", "Fiery / Twilight", "Jefes Twilight Forest",
                            "Buena utilidad intermedia mientras preparas equipo de Cataclysm.")
            )),
            new TierRow("A", "JEFES (Tier 4)", 0xFFF59E0B, List.of(
                    new GearEntry("cataclysm:ignitium_chestplate", "Ignitium", "Ignis (Cataclysm)",
                            "Inmune a lava, gran defensa y combinable con Élitros."),
                    new GearEntry("cataclysm:cursium_chestplate", "Cursium", "Maledictus (Cataclysm)",
                            "Revive tras golpe mortal + probabilidad de esquiva."),
                    new GearEntry("cisco_mod:bjorn_chestplate", "Bjorn's Armor", "Jefes / Progresión Cisco",
                            "Excelente set intermedio antes de farmear Fell King o Sylvi."),
                    new GearEntry("cisco_mod:ciscos_armor_chestplate", "Cisco's Armor", "Crafteo / Jefe Cisco",
                            "Set icónico de Cisco; requiere gemas y Vida Máx % porque la armadura plana no basta en late.")
            )),
            new TierRow("S", "META ENDGAME (Tier 5)", 0xFFEF4444, List.of(
                    new GearEntry("cisco_mod:sylvi_chestplate", "Sylvi's Armor (META)", "Endgame Cisco",
                            "Top #1 según comunidad/Wiki: -20% daño recibido, +10% Vida Máx y escala ataque con tu HP."),
                    new GearEntry("cisco_mod:fell_king_armor_chestplate", "Fell King", "The Fell King",
                            "Tanque endgame muy fuerte si se reforja con Protección y gemas perfectas."),
                    new GearEntry("cisco_mod:fallen_hero_armor_chestplate", "Fallen Hero", "Endgame Cisco",
                            "Set ofensivo oscuro de alto daño; muy costoso de farmear frente a Sylvi's."),
                    new GearEntry("cisco_mod:ascended_hero_chestplate", "Ascendant (Ojo)", "Grindeo Endgame Cisco",
                            "Aviso Reddit: exige mucho farmeo; sin gemas/Vida Máx % rinde menos que Sylvi's.")
            ))
    );

    private static final List<TierRow> WEAPON_TIERS = List.of(
            new TierRow("D", "INICIO (Tier 1)", 0xFF9CA3AF, List.of(
                    new GearEntry("minecraft:iron_sword", "Espada de Hierro", "Crafteo inicial",
                            "Solo para empezar misiones de aldea."),
                    new GearEntry("minecraft:diamond_sword", "Espada Diamante", "Cuevas / Comercio",
                            "Súbela con encantamientos básicos en mesa/librería."),
                    new GearEntry("simplyswords:diamond_katana", "Katana Diamante", "Simply Swords",
                            "Ataque muy rápido ideal para aplicar efectos temprano.")
            )),
            new TierRow("C", "MEDIO (Tier 2)", 0xFF38BDF8, List.of(
                    new GearEntry("minecraft:netherite_sword", "Espada Netherita", "Mesa de Herrería",
                            "Base confiable para empezar a insertar gemas de Apotheosis."),
                    new GearEntry("simplyswords:netherite_glaive", "Glaive Netherita", "Simply Swords",
                            "Gran alcance y barrido para limpiar oleadas de misiones."),
                    new GearEntry("cataclysm:black_steel_sword", "Black Steel", "Cataclysm",
                            "Buen escalado físico antes de ir por jefes de Cataclysm.")
            )),
            new TierRow("B", "AVANZADO (Tier 3)", 0xFFA855F7, List.of(
                    new GearEntry("simplyswords:runic_rapier", "Runic Rapier", "Dungeons / Botín",
                            "Escala muy bien con runas y afijos críticos de Apotheosis."),
                    new GearEntry("simplyswords:frostfall", "Frostfall", "Simply Swords (Única)",
                            "Congela enemigos y te vuelve inmune brevemente con regeneración."),
                    new GearEntry("simplyswords:dreadtide", "Dreadtide", "Simply Swords (Única)",
                            "Aumenta velocidad y reduce daño recibido con acumulaciones de Voidcloak.")
            )),
            new TierRow("A", "JEFES (Tier 4)", 0xFFF59E0B, List.of(
                    new GearEntry("cataclysm:the_incinerator", "The Incinerator", "Ignis (Cataclysm)",
                            "Espada ígnea pesada con gran habilidad en área."),
                    new GearEntry("cataclysm:soul_render", "Soul Render", "Maledictus (Cataclysm)",
                            "Alabarda de altísimo DPS contra jefes y grupos."),
                    new GearEntry("cisco_mod:hellbrand", "Hellbrand (Cuidado)", "Endgame Cisco",
                            "Daño bruto enorme, pero OJO: jefes con Reflect pueden matarte de 1 golpe.")
            )),
            new TierRow("S", "META ENDGAME (Tier 5)", 0xFFEF4444, List.of(
                    new GearEntry("cisco_mod:frostfang", "Frostfang (META)", "Endgame Cisco",
                            "Top #1 Reddit: hace daño por % de HP (derrite a Fell King) y limita daño recibido a ~2.5 corazones."),
                    new GearEntry("cataclysm:gauntlet_of_maelstrom", "Guantelete Maelstrom", "Scylla (Cataclysm)",
                            "Excelente control de masas y daño explosivo secundario."),
                    new GearEntry("apotheosis:mythic_material", "Reforja Mítica", "Desafío Wither / Jefes",
                            "Reforja tu arma Tier A/S a Mítica y añade zócalos + gemas en la pestaña Fortalecimiento.", true)
            ))
    );

    private static final List<TierRow> UPGRADE_TIERS = List.of(
            new TierRow("1", "ZÓCALOS (SLOTS)", 0xFF38BDF8, List.of(
                    new GearEntry("apotheosis:sigil_of_socketing", "Sigil of Socketing", "Desafío Wither / Craft",
                            "Úsalo en Mesa de Herrería junto a tu arma/armadura para añadir +1 zócalo vacío (hasta 3-4)."),
                    new GearEntry("apotheosis:sigil_of_withdrawal", "Sigil of Withdrawal", "Apotheosis",
                            "Extrae todas las gemas de una pieza en Mesa de Herrería sin romper el objeto ni perder las gemas."),
                    new GearEntry("apotheosis:gem_dust", "Gem Dust (Polvo)", "Triturar gemas",
                            "Aplasta gemas repetidas con un yunque que cae encima para obtener Gem Dust para reforjar.")
            )),
            new TierRow("2", "REFORGAMIENTO", 0xFFA855F7, List.of(
                    new GearEntry("apotheosis:simple_reforging_table", "Mesa Reforja Simple", "Early / Mid Game",
                            "Reforja hasta rareza Épica usando Epic Material + Gem Dust. Ideal para Darksteel/Brightsteel."),
                    new GearEntry("apotheosis:reforging_table", "Mesa Reforja Avanzada", "Late / Endgame",
                            "Desbloquea rareza Mítica (Naranja) usando Godforged Pearl. Busca Vida Máx % y Reducción de daño."),
                    new GearEntry("apotheosis:mythic_material", "Godforged Pearl", "Wither / Jefes / Desafíos",
                            "Material clave para reforjar a Mítico; también se obtiene desarmando equipo Mítico en Salvaging Table.")
            )),
            new TierRow("S", "GEMAS META (TOP)", 0xFFEF4444, List.of(
                    new GearEntry("apotheosis:gem", "{affix_data:{rarity:\"apotheosis:ancient\"},gem:\"apotheosis:the_end/endersurge\"}",
                            "Endersurge (Tier S)", "The End / Jefes",
                            "Aumenta niveles de TODOS los encantamientos de esa pieza. Obligatoria en casco y pechera.", true),
                    new GearEntry("apotheosis:gem", "{affix_data:{rarity:\"apotheosis:mythic\"},gem:\"apotheosis:overworld/royalty\"}",
                            "Royal Family (Tier S)", "Overworld / Jefes",
                            "Sube TODOS los stats en porcentaje (Vida Máx, Daño, Armadura y Suerte).", true),
                    new GearEntry("apotheosis:gem", "{affix_data:{rarity:\"apotheosis:mythic\"},gem:\"apotheosis:core/combatant\"}",
                            "Combatant (Tier S)", "Global / Jefes",
                            "Daño cuerpo a cuerpo o Reducción de daño físico según la pieza equipada.", true),
                    new GearEntry("apotheosis:gem", "{affix_data:{rarity:\"apotheosis:mythic\"},gem:\"apotheosis:core/warlord\"}",
                            "Warlord (Tier S)", "Global / Misiones",
                            "Aumenta Daño Crítico y Daño base; la mejor gema ofensiva pura para armas.", true)
            )),
            new TierRow("A", "GEMAS + LIBRERÍA", 0xFFF59E0B, List.of(
                    new GearEntry("apotheosis:gem", "{affix_data:{rarity:\"apotheosis:mythic\"},gem:\"apotheosis:core/tyrannical\"}",
                            "Tyrannical (Tier A)", "Global",
                            "Gran bono de Vida Máx % y Armadura; fundamental para aguantar golpes en Dragonfyre.", true),
                    new GearEntry("apotheosis:gem", "{affix_data:{rarity:\"apotheosis:mythic\"},gem:\"apotheosis:the_nether/blood_lord\"}",
                            "Blood Lord (Tier A)", "The Nether",
                            "Robo de vida (Lifesteal) + daño; muy útil en armas rápidas.", true),
                    new GearEntry("apotheosis:ender_library", "Library of Alexandria", "Apotheosis (The End)",
                            "Guarda miles de encantamientos en libros y extrae Protección/Filo máximo sin perder niveles.")
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
        this.addRenderableWidget(new UiStyle.OreButton(boxX + 12, boxY + 28, 92, 18,
                Component.literal("§fArmaduras"), b -> {
            activeTab = 0;
            scrollOffset = 0;
        }, UiStyle.ACCENT_SLATE, 0xFF47474D, () -> activeTab == 0));

        this.addRenderableWidget(new UiStyle.OreButton(boxX + 110, boxY + 28, 84, 18,
                Component.literal("§fArmas"), b -> {
            activeTab = 1;
            scrollOffset = 0;
        }, UiStyle.ACCENT_SLATE, 0xFF47474D, () -> activeTab == 1));

        this.addRenderableWidget(new UiStyle.OreButton(boxX + 200, boxY + 28, 120, 18,
                Component.literal("§fFortalecimiento"), b -> {
            activeTab = 2;
            scrollOffset = 0;
        }, UiStyle.ACCENT_SLATE, 0xFF47474D, () -> activeTab == 2));

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
        g.drawString(this.font, "§6§lROADMAP DE PROGRESIÓN §7— Meta Dragonfyre (Armaduras, Armas y Fortalecimiento)", boxX + 12, boxY + 9, 0xFFD4AF37);

        String metaTip = activeTab == 2
                ? "§eOrden ideal: §fReforjar Mítico → Añadir Zócalos → Insertar Gemas → Encantar"
                : "§eTip Meta: §7Prioriza §fVida Máx %§7, §fGemas§7 y §fDaño %HP §7sobre armadura plana.";
        if (boxW > 520) {
            g.drawString(this.font, metaTip, boxX + 330, boxY + 33, 0xCCCCCC);
        }

        int listTop = boxY + 52;
        int listBottom = boxY + boxH - 8;
        int listH = listBottom - listTop;

        List<TierRow> rows = (activeTab == 0) ? ARMOR_TIERS : (activeTab == 1 ? WEAPON_TIERS : UPGRADE_TIERS);
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

                int tierW = 96;
                UiStyle.drawRoundedCard(g, boxX + 14, y + 6, tierW, rowH - 12, 2, UiStyle.BG_CARD, row.color(), false);
                String badgePrefix = (activeTab == 2 && (row.badge().equals("1") || row.badge().equals("2"))) ? "PASO " : "TIER ";
                g.drawCenteredString(this.font, "§l" + badgePrefix + row.badge(), boxX + 14 + tierW / 2, y + 14, row.color());
                String shortLbl = row.title().contains("(") ? row.title().substring(0, row.title().indexOf('(')).trim() : row.title();
                g.drawCenteredString(this.font, "§7" + shortLbl, boxX + 14 + tierW / 2, y + 28, 0xCCCCCC);

                int cardX = boxX + 14 + tierW + 8;
                int availW = (boxX + boxW - 14) - cardX;
                int count = row.entries().size();
                int cardW = Math.min(180, Math.max(115, (availW - (count - 1) * 6) / Math.max(1, count)));

                for (GearEntry entry : row.entries()) {
                    if (cardX + cardW > boxX + boxW - 10) break;
                    boolean hov = mouseX >= cardX && mouseX <= cardX + cardW
                            && mouseY >= Math.max(listTop, y + 5) && mouseY <= Math.min(listBottom, y + rowH - 5);

                    UiStyle.drawRoundedCard(g, cardX, y + 5, cardW, rowH - 10, 2,
                            hov ? 0xFF2B2B30 : UiStyle.BG_CARD, hov ? row.color() : 0, false);

                    ItemStack stack = resolveStack(entry, activeTab);
                    g.renderItem(stack, cardX + 6, y + 12);

                    String displayName = resolveDisplayName(stack, entry);
                    String nameTrim = this.font.plainSubstrByWidth(displayName, cardW - 30);
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
            ItemStack hStack = resolveStack(hoveredEntry, activeTab);
            String hName = resolveDisplayName(hStack, hoveredEntry);
            List<Component> tip = List.of(
                    Component.literal("§l" + hName),
                    Component.literal("§7Sección: §f" + hoveredTier.title()),
                    Component.literal("§eOrigen / Uso: §f" + hoveredEntry.source()),
                    Component.literal("§aRecomendación: §7" + hoveredEntry.tip())
            );
            g.renderComponentTooltip(this.font, tip, mouseX, mouseY);
        }
    }

    private static String resolveDisplayName(ItemStack stack, GearEntry entry) {
        if (entry.keepTitle()) {
            return entry.name();
        }
        String raw = stack.getHoverName().getString().replaceAll("§.", "").trim();
        return raw.isEmpty() ? entry.name() : raw;
    }

    private static ItemStack resolveStack(GearEntry entry, int tab) {
        Item it = ForgeRegistries.ITEMS.getValue(new ResourceLocation(entry.itemId()));
        if (it == null || it == Items.AIR) {
            it = (tab == 1) ? Items.NETHERITE_SWORD : (tab == 2 ? Items.EMERALD : Items.NETHERITE_CHESTPLATE);
        }
        ItemStack st = new ItemStack(it);
        if (entry.nbt() != null && !entry.nbt().isEmpty()) {
            try {
                st.setTag(TagParser.parseTag(entry.nbt()));
            } catch (Exception ignored) {}
        }
        return st;
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
