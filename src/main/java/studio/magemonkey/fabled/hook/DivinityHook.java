package studio.magemonkey.fabled.hook;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.inventory.ItemStack;
import studio.magemonkey.codex.util.DataUT;
import studio.magemonkey.divinity.stats.EntityStats;
import studio.magemonkey.divinity.stats.items.ItemStats;
import studio.magemonkey.divinity.stats.items.attributes.AmmoAttribute;
import studio.magemonkey.divinity.stats.items.attributes.api.TypedStat;
import studio.magemonkey.fabled.api.enums.Operation;

public class DivinityHook {
    private static final NamespacedKey KEY_MODULE  = NamespacedKey.fromString("prorpgitems:qrpg_item_module");
    private static final NamespacedKey KEY_MODULE2 = NamespacedKey.fromString("quantumrpg:qrpg_item_module");

    public static boolean isDivinity(ItemStack item) {
        String data = DataUT.getStringData(item, KEY_MODULE2);
        if (data == null) data = DataUT.getStringData(item, KEY_MODULE);

        return data != null;
    }

    /**
     * Returns the projectile entity class declared by a Divinity AmmoAttribute on the given item,
     * or null if the item has no ammo lore stat (or item is null/empty).
     * <p>
     * Used by {@link studio.magemonkey.fabled.dynamic.mechanic.ProjectileMechanic} when
     * {@code use-divinity-ammo: true} — overrides the hardcoded projectile type with the
     * caster's bow ammo type.
     * <p>
     * NOTE: Only call when Divinity is confirmed active (PluginChecker.isDivinityActive()).
     */
    public static Class<? extends Projectile> getAmmoProjectileClass(ItemStack item) {
        if (item == null) return null;
        AmmoAttribute ammo = ItemStats.getAmmo(item);
        return ammo != null ? ammo.getProjectileClass() : null;
    }

    /**
     * Returns the consumable inventory Material for the given bow's ammo lore stat.
     * Returns null when item has no ammo, or ammo type has no native vanilla item
     * (WITHER_SKULL, SHULKER_BULLET, LLAMA_SPIT).
     */
    public static Material getAmmoConsumeMaterial(ItemStack item) {
        if (item == null) return null;
        AmmoAttribute ammo = ItemStats.getAmmo(item);
        return ammo != null ? ammo.getConsumeMaterial() : null;
    }

    /**
     * Returns the Divinity ammo type enum name (e.g. "ARROW", "FIREBALL", "SNOWBALL")
     * declared by the bow's lore stat, or null if no ammo lore present.
     * Used by LaunchTrigger to expose `api-ammo-type` CastData key.
     */
    public static String getAmmoTypeName(ItemStack item) {
        if (item == null) return null;
        AmmoAttribute ammo = ItemStats.getAmmo(item);
        return ammo != null ? ammo.getType().name() : null;
    }

    /**
     * Returns the Divinity QArrow id stored on a projectile by Divinity's ArrowManager
     * via the {@code QRPG_ARROW_ID} metadata key, or null if the projectile is not a
     * Divinity custom arrow.
     * <p>
     * Metadata is set by {@code ArrowManager.markArrow} inside the EntityShootBowEvent
     * handler — by the time Fabled's ProjectileLaunchEvent / ProjectileHitEvent /
     * ProjectileTickEvent observers run, the value is already present.
     * <p>
     * Only call when Divinity is confirmed active (PluginChecker.isDivinityActive()).
     */
    public static String getArrowId(Projectile pj) {
        if (pj == null) return null;
        if (!pj.hasMetadata("QRPG_ARROW_ID")) return null;
        java.util.List<org.bukkit.metadata.MetadataValue> meta = pj.getMetadata("QRPG_ARROW_ID");
        if (meta.isEmpty()) return null;
        return meta.get(0).asString();
    }

    /**
     * Returns the Divinity arrow level metadata ({@code QRPG_ARROW_LEVEL}) for a projectile,
     * or 0 if absent. Companion to {@link #getArrowId}.
     */
    public static int getArrowLevel(Projectile pj) {
        if (pj == null) return 0;
        if (!pj.hasMetadata("QRPG_ARROW_LEVEL")) return 0;
        java.util.List<org.bukkit.metadata.MetadataValue> meta = pj.getMetadata("QRPG_ARROW_LEVEL");
        if (meta.isEmpty()) return 0;
        return meta.get(0).asInt();
    }

    /**
     * Returns true when {@code ids} contains the projectile's Divinity arrow id, or contains
     * a wildcard {@code "*"} entry that matches any Divinity arrow.
     * <p>
     * Returns false when:
     *   - {@code ids} is null or empty (caller should treat this as "filter not applied"),
     *   - projectile has no Divinity arrow metadata, or
     *   - the id is set but not contained in {@code ids} (and no wildcard).
     * <p>
     * Only call when Divinity is confirmed active.
     */
    public static boolean matchesArrowIdFilter(Projectile pj, java.util.List<String> ids) {
        if (ids == null || ids.isEmpty()) return false;
        String arrowId = getArrowId(pj);
        if (arrowId == null) return false;
        for (String entry : ids) {
            if (entry == null) continue;
            if (entry.equals("*")) return true;
            if (entry.equalsIgnoreCase(arrowId)) return true;
        }
        return false;
    }

    /**
     * Clamps a StatMechanic modifier amount so the resulting stat total does not exceed
     * Divinity's configured cap for the given stat key.
     * <p>
     * Returns the original amount unchanged if the stat is unknown, has no cap (-1),
     * or the operation is unrecognized.
     * <p>
     * NOTE: All Divinity class references are confined to this method body to allow
     * lazy class-loading — this method must only be called when Divinity is confirmed active.
     *
     * @param key       Divinity stat key (e.g. "critical_rate")
     * @param operation Fabled Operation (ADD_NUMBER or MULTIPLY_PERCENTAGE)
     * @param amount    Modifier amount from the mechanic
     * @param player    Target player
     * @return clamped amount that will not push the stat past its Divinity cap
     */
    public static double clampStatAmount(String key, Operation operation, double amount, Player player) {
        TypedStat.Type statType = TypedStat.Type.getByName(key);
        if (statType == null) return amount;

        TypedStat stat = ItemStats.getStat(statType);
        if (stat == null) return amount;

        double cap = stat.getCapability();
        if (cap < 0) return amount; // unlimited cap

        double currentValue = EntityStats.get(player).getItemStat(statType, true);

        switch (operation) {
            case ADD_NUMBER:
                // Clamp: currentValue + amount <= cap  →  amount <= cap - currentValue
                return Math.min(amount, Math.max(0, cap - currentValue));
            case MULTIPLY_PERCENTAGE:
                // New total = currentValue * amount; clamp: amount <= cap / currentValue
                // Ensure we don't reduce the stat (amount >= 1.0)
                if (currentValue <= 0) return amount;
                if (currentValue >= cap) return 1.0; // already at/above cap, no-op multiplier
                return Math.max(1.0, Math.min(amount, cap / currentValue));
            default:
                return amount;
        }
    }

    /**
     * Applies a temporary stat modifier to a non-player entity via Divinity's AdjustStatEffect.
     * Returns the effect as Object to avoid eager class-loading of Divinity types in callers.
     * Only call when Divinity is confirmed active.
     *
     * @param seconds negative value = permanent
     * @return the created AdjustStatEffect, or null if stat key is unknown / operation invalid
     */
    public static Object applyStatToMob(LivingEntity target, LivingEntity caster,
                                        String key, String operation, double amount, double seconds) {
        studio.magemonkey.divinity.stats.items.api.ItemLoreStat<?> stat = ItemStats.getAttribute(key);
        if (stat == null) return null;

        java.util.function.DoubleUnaryOperator operator;
        switch (operation) {
            case "ADD_NUMBER":
                operator = v -> v + amount;
                break;
            case "MULTIPLY_PERCENTAGE":
                operator = v -> v * amount;
                break;
            default:
                return null;
        }

        studio.magemonkey.divinity.manager.effects.main.AdjustStatEffect effect =
                new studio.magemonkey.divinity.manager.effects.main.AdjustStatEffect.Builder(seconds)
                        .withCaster(caster)
                        .withAdjust(stat, operator)
                        .build();
        effect.applyTo(target);
        return effect;
    }

    /**
     * Removes a stat effect previously returned by {@link #applyStatToMob} from the target's EntityStats.
     * Only call when Divinity is confirmed active.
     */
    public static void removeStatFromMob(LivingEntity target, Object effect) {
        if (effect == null) return;
        EntityStats.get(target).removeEffect(
                (studio.magemonkey.divinity.manager.effects.main.AdjustStatEffect) effect);
    }
}
