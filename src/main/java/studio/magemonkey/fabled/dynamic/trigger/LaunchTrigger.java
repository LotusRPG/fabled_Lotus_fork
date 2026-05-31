package studio.magemonkey.fabled.dynamic.trigger;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import studio.magemonkey.fabled.api.CastData;
import studio.magemonkey.fabled.api.Settings;
import studio.magemonkey.fabled.api.particle.target.EntityTarget;
import studio.magemonkey.fabled.dynamic.TempEntity;
import studio.magemonkey.fabled.dynamic.mechanic.ProjectileMechanic;
import studio.magemonkey.fabled.hook.DivinityHook;
import studio.magemonkey.fabled.hook.PluginChecker;

/**
 * © 2026 VoidEdge
 * studio.magemonkey.fabled.dynamic.trigger.BlockBreakTrigger
 */
public class LaunchTrigger implements Trigger<ProjectileLaunchEvent> {

    /**
     * {@inheritDoc}
     */
    @Override
    public String getKey() {
        return "LAUNCH";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Class<ProjectileLaunchEvent> getEvent() {
        return ProjectileLaunchEvent.class;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean shouldTrigger(final ProjectileLaunchEvent event, final int level, final Settings settings) {
        // ignore-skill-projectiles: skip projectiles fired by Fabled's Projectile mechanic.
        // At launch time the P_CALL metadata is not applied yet (set after launchProjectile returns),
        // so detection uses ProjectileMechanic.launchingSkillProjectile, true only during that call.
        if (settings.getBool("ignore-skill-projectiles", false)
                && ProjectileMechanic.launchingSkillProjectile) {
            return false;
        }

        // Divinity arrow id filter (AND-ed with types: filter when both are set).
        // Empty list = filter not applied (back-compat).
        List<String> divArrowIds = settings.getStringList("divinity-arrow-ids");
        if (divArrowIds != null && !divArrowIds.isEmpty()) {
            if (!PluginChecker.isDivinityActive()) return false;
            if (!DivinityHook.matchesArrowIdFilter(event.getEntity(), divArrowIds)) return false;
        }

        List<String> types = settings.getStringList("types");
        if (types.isEmpty()) {
            types = new ArrayList<>(List.of(settings.getString("type", "Any")));
        }
        if (types.contains("Any")) return true;
        return types.stream().anyMatch(t -> matchesType(event, t));
    }

    /**
     * Match a single types[] entry against the launch event.
     * <p>
     * Two filter modes:
     * <ul>
     *   <li>{@code DIVINITY_<AMMO>} — matches when shooter is Player + Divinity active +
     *       main-hand bow has an AmmoAttribute whose Type enum name equals {@code <AMMO>}
     *       (e.g. {@code DIVINITY_FIREBALL}, {@code DIVINITY_SNOWBALL}).</li>
     *   <li>Anything else — matches against the launched entity type name (existing behavior;
     *       e.g. {@code Arrow}, {@code Snowball}, {@code Ender pearl}).</li>
     * </ul>
     */
    private boolean matchesType(final ProjectileLaunchEvent event, final String filter) {
        if (filter == null || filter.isEmpty()) return false;
        String upper = filter.toUpperCase();
        if (upper.startsWith("DIVINITY_")) {
            if (!(event.getEntity().getShooter() instanceof Player)) return false;
            if (!PluginChecker.isDivinityActive()) return false;
            Player shooter = (Player) event.getEntity().getShooter();
            ItemStack hand = shooter.getInventory().getItemInMainHand();
            String ammoName = DivinityHook.getAmmoTypeName(hand);
            if (ammoName == null) return false;
            String expected = upper.substring("DIVINITY_".length());
            return ammoName.equalsIgnoreCase(expected);
        }
        return event.getEntity().getType().name().equalsIgnoreCase(filter.replace(' ', '_'));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setValues(final ProjectileLaunchEvent event, final CastData data) {
        data.put("api-velocity", event.getEntity().getVelocity().length());
        // Expose launched projectile entity type (e.g. "ARROW", "SNOWBALL")
        data.put("api-projectile-type", event.getEntity().getType().name());
        // If shooter is a Player wielding a Divinity-ammo bow, expose the ammo type name
        if (event.getEntity().getShooter() instanceof Player
                && PluginChecker.isDivinityActive()) {
            Player shooter = (Player) event.getEntity().getShooter();
            ItemStack hand = shooter.getInventory().getItemInMainHand();
            String ammoType = DivinityHook.getAmmoTypeName(hand);
            if (ammoType != null) {
                data.put("api-ammo-type", ammoType);
            }
        }
        // Divinity custom arrow id/level (DIV_ARROW_<id>) if present on the projectile
        if (PluginChecker.isDivinityActive()) {
            Projectile pj = event.getEntity();
            String arrowId = DivinityHook.getArrowId(pj);
            if (arrowId != null) {
                data.put("api-divinity-arrow-id", arrowId);
                data.put("api-divinity-arrow-level", DivinityHook.getArrowLevel(pj));
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LivingEntity getCaster(final ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof LivingEntity) {
            return (LivingEntity) event.getEntity().getShooter();
        } else {
            return null;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LivingEntity getTarget(final ProjectileLaunchEvent event, final Settings settings) {
        // target: "projectile" returns a TempEntity wrapping the launched projectile so that
        // the chain can pass it to MarkProjectileMechanic. Default (any other value or unset) =
        // shooter, preserving existing behavior.
        if ("projectile".equalsIgnoreCase(settings.getString("target", ""))) {
            return new TempEntity(new EntityTarget(event.getEntity()));
        }
        return getCaster(event);
    }
}
