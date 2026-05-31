/**
 * Fabled
 * studio.magemonkey.fabled.dynamic.mechanic.PurgeMechanic
 * <p>
 * The MIT License (MIT)
 * <p>
 * © 2026 VoidEdge
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software") to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package studio.magemonkey.fabled.dynamic.mechanic;

import com.google.common.collect.ImmutableSet;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import studio.magemonkey.codex.util.NamespaceResolver;
import studio.magemonkey.fabled.Fabled;
import studio.magemonkey.fabled.api.player.PlayerData;
import studio.magemonkey.fabled.api.util.FlagManager;
import studio.magemonkey.fabled.api.util.StatusFlag;
import studio.magemonkey.fabled.hook.DivinityHook;
import studio.magemonkey.fabled.hook.PluginChecker;

import java.util.*;

/**
 * Purges a target of positive potion or status effects
 */
public class PurgeMechanic extends MechanicComponent {
    private static final Set<PotionEffectType> POTIONS = ImmutableSet.of(
            PotionEffectType.ABSORPTION,
            PotionEffectType.CONDUIT_POWER,
            NamespaceResolver.getPotion("DAMAGE_RESISTANCE", "RESISTANCE"),
            PotionEffectType.DOLPHINS_GRACE,
            NamespaceResolver.getPotion("FAST_DIGGING", "HASTE"),
            PotionEffectType.FIRE_RESISTANCE,
            PotionEffectType.GLOWING,
            PotionEffectType.HEALTH_BOOST,
            NamespaceResolver.getPotion("STRENGTH", "INCREASE_DAMAGE"),
            PotionEffectType.INVISIBILITY,
            NamespaceResolver.getPotion("JUMP", "JUMP_BOOST"),
            PotionEffectType.LUCK,
            PotionEffectType.NIGHT_VISION,
            PotionEffectType.REGENERATION,
            PotionEffectType.SATURATION,
            PotionEffectType.SLOW_FALLING,
            PotionEffectType.SPEED,
            PotionEffectType.WATER_BREATHING
    );

    private static final String STATUS = "status";
    private static final String POTION = "potion";
    private static final String STAT   = "stat";

    @Override
    public String getKey() {
        return "purge";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean execute(LivingEntity caster, int level, List<LivingEntity> targets, boolean force) {
        boolean     worked    = false;
        Set<String> statusSet = new HashSet<>();
        for (String string : settings.getStringList(STATUS)) {
            if (string.equalsIgnoreCase("All")) {
                Collections.addAll(statusSet, StatusFlag.POSITIVE);
                break;
            }
            statusSet.add(string.toLowerCase());
        }
        Set<PotionEffectType> potionSet = new HashSet<>();
        for (String string : settings.getStringList(POTION)) {
            if (string.equalsIgnoreCase("All")) {
                potionSet.addAll(POTIONS);
                break;
            }
            try {
                potionSet.add(Objects.requireNonNull(PotionEffectType.getByName(string.toLowerCase()
                        .replace(' ', '_'))));
            } catch (IllegalArgumentException | NullPointerException ignored) {
            }
        }

        // Stat keys to purge — purges StatMechanic-applied modifiers (player only).
        // "All" means all keys; explicit list filters. Modifiers with ignore-purge=true are skipped.
        Set<String> statSet  = new HashSet<>();
        boolean     statsAll = false;
        for (String string : settings.getStringList(STAT)) {
            if (string.equalsIgnoreCase("All")) {
                statsAll = true;
                statSet.clear();
                break;
            }
            statSet.add(string.toLowerCase());
        }
        boolean purgeStats = statsAll || !statSet.isEmpty();

        for (LivingEntity target : targets) {
            for (String status : statusSet) {
                if (FlagManager.hasFlag(target, status)) {
                    FlagManager.removeFlag(target, status);
                    worked = true;
                }
            }
            for (PotionEffectType type : potionSet) {
                if (target.hasPotionEffect(type)) {
                    target.removePotionEffect(type);
                    worked = true;
                }
            }
            if (purgeStats && target instanceof Player) {
                PlayerData data = Fabled.getData((Player) target);
                if (data != null) {
                    // Purge removes modifiers tagged purgeable OR no-cleanse (both keep Purge eligibility).
                    int removed = data.removeStatModifiersBySource(
                            StatMechanic.SOURCE_PURGEABLE,
                            statsAll ? null : statSet,
                            false);
                    removed += data.removeStatModifiersBySource(
                            StatMechanic.SOURCE_NO_CLEANSE,
                            statsAll ? null : statSet,
                            false);
                    if (removed > 0) {
                        worked = true;
                        if (PluginChecker.isDivinityActive()) {
                            DivinityHook.refreshBonusAttributes((Player) target);
                        }
                    }
                }
            }
        }
        return worked;
    }
}
