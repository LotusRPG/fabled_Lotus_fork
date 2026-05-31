package studio.magemonkey.fabled.dynamic.trigger;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import studio.magemonkey.fabled.api.CastData;
import studio.magemonkey.fabled.api.Settings;
import studio.magemonkey.fabled.api.event.ProjectileTickEvent;
import studio.magemonkey.fabled.api.particle.target.EntityTarget;
import studio.magemonkey.fabled.dynamic.TempEntity;
import studio.magemonkey.fabled.dynamic.mechanic.MarkProjectileMechanic;
import studio.magemonkey.fabled.hook.DivinityHook;
import studio.magemonkey.fabled.listener.MechanicListener;
import studio.magemonkey.fabled.hook.PluginChecker;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * © 2026 VoidEdge
 * studio.magemonkey.fabled.dynamic.trigger.ProjectileTickTrigger
 */
public class ProjectileTickTrigger implements Trigger<ProjectileTickEvent> {
    private Map<UUID, Integer> timer = new HashMap<>();

    /**
     * {@inheritDoc}
     */
    @Override
    public String getKey() {
        return "PROJECTILE_TICK";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Class<ProjectileTickEvent> getEvent() {
        return ProjectileTickEvent.class;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean shouldTrigger(ProjectileTickEvent event, int level, Settings settings) {
        // ignore-skill-projectiles: skip projectiles fired by Fabled's Projectile mechanic
        // (tagged with P_CALL metadata after launch).
        if (settings.getBool("ignore-skill-projectiles", false)
                && event.getProjectile().hasMetadata(MechanicListener.P_CALL)) {
            return false;
        }

        // Divinity arrow id filter — AND-ed with `projectile:` entity-type filter.
        List<String> divArrowIds = settings.getStringList("divinity-arrow-ids");
        if (divArrowIds != null && !divArrowIds.isEmpty()) {
            if (!PluginChecker.isDivinityActive()) return false;
            if (!DivinityHook.matchesArrowIdFilter(event.getProjectile(), divArrowIds)) return false;
        }

        // requires-mark: only fire for projectiles tagged by MarkProjectileMechanic with this key.
        // Set via `Launch (target: projectile) → Mark Projectile (key: <name>)` in the launching skill.
        String requiresMark = settings.getString("requires-mark", "");
        if (!requiresMark.isEmpty()) {
            if (!event.getProjectile().hasMetadata(MarkProjectileMechanic.META_PREFIX + requiresMark)) return false;
        }

        Entity       proj        = event.getEntity();
        List<String> projectiles = settings.getStringList("projectile");
        boolean correctProjectile = projectiles.isEmpty()
                || projectiles.contains("Any")
                || projectiles.stream().anyMatch(projectile -> proj.getType().name().equalsIgnoreCase(projectile));
        if (!correctProjectile) return false;

        int interval = settings.getInt("interval", 1);
        int delay    = settings.getInt("delay", 0);

        UUID projectileUUID = event.getProjectile().getUniqueId();
        if (!timer.containsKey(projectileUUID)) timer.put(projectileUUID, 0);

        int     timerValue  = timer.get(event.getProjectile().getUniqueId());
        boolean isTimerTick = event.getTick() - timerValue * interval - delay >= 0;
        if (isTimerTick) timer.put(projectileUUID, timerValue + 1);

        return isTimerTick;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setValues(ProjectileTickEvent event, final CastData data) {
        data.put("api-projectile-type", event.getProjectile().getType().name());
        if (PluginChecker.isDivinityActive()) {
            String arrowId = DivinityHook.getArrowId(event.getProjectile());
            if (arrowId != null) {
                data.put("api-divinity-arrow-id", arrowId);
                data.put("api-divinity-arrow-level", DivinityHook.getArrowLevel(event.getProjectile()));
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LivingEntity getCaster(ProjectileTickEvent event) {
        return (LivingEntity) event.getEntity();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LivingEntity getTarget(ProjectileTickEvent event, Settings settings) {
        boolean targetCaster = settings.getBool("target", false);
        return targetCaster
                ? (LivingEntity) event.getEntity()
                : new TempEntity(new EntityTarget(event.getProjectile()));
    }

    /**
     * Removes projectile from the timer Map.
     *
     * @param uuid UUID of the projectile
     */
    public void removeProjectile(UUID uuid) {
        timer.remove(uuid);
    }
}