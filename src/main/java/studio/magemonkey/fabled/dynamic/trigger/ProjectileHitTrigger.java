package studio.magemonkey.fabled.dynamic.trigger;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.projectiles.ProjectileSource;
import studio.magemonkey.fabled.api.CastData;
import studio.magemonkey.fabled.api.Settings;
import studio.magemonkey.fabled.api.particle.target.EntityTarget;
import studio.magemonkey.fabled.dynamic.TempEntity;
import studio.magemonkey.fabled.dynamic.mechanic.MarkProjectileMechanic;
import studio.magemonkey.fabled.hook.DivinityHook;
import studio.magemonkey.fabled.listener.MechanicListener;
import studio.magemonkey.fabled.hook.PluginChecker;

import java.util.List;
import java.util.Objects;

/**
 * © 2026 VoidEdge
 * studio.magemonkey.fabled.dynamic.trigger.ProjectileHitTrigger
 */
public class ProjectileHitTrigger implements Trigger<ProjectileHitEvent> {
    /**
     * {@inheritDoc}
     */
    @Override
    public String getKey() {
        return "PROJECTILE_HIT";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Class<ProjectileHitEvent> getEvent() {
        return ProjectileHitEvent.class;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean shouldTrigger(ProjectileHitEvent event, int level, Settings settings) {
        Projectile   proj        = event.getEntity();
        List<String> projectiles = settings.getStringList("projectile");
        String       type        = translateType(settings.getString("type", "both"));

        // ignore-skill-projectiles: skip projectiles fired by Fabled's Projectile mechanic
        // (tagged with P_CALL metadata after launch).
        if (settings.getBool("ignore-skill-projectiles", false)
                && proj.hasMetadata(MechanicListener.P_CALL)) {
            return false;
        }

        // Divinity arrow id filter — AND-ed with projectile entity-type and hit-type filters.
        List<String> divArrowIds = settings.getStringList("divinity-arrow-ids");
        if (divArrowIds != null && !divArrowIds.isEmpty()) {
            if (!PluginChecker.isDivinityActive()) return false;
            if (!DivinityHook.matchesArrowIdFilter(proj, divArrowIds)) return false;
        }

        // requires-mark: only fire for projectiles tagged by MarkProjectileMechanic with this key.
        // Pairs with `Launch (target: projectile) → Mark Projectile` in the launching skill.
        String requiresMark = settings.getString("requires-mark", "");
        if (!requiresMark.isEmpty()) {
            if (!proj.hasMetadata(MarkProjectileMechanic.META_PREFIX + requiresMark)) return false;
        }

        boolean hitEntity = Objects.nonNull(event.getHitEntity());

        boolean correctProjectile = projectiles.isEmpty()
                || projectiles.contains("Any")
                || projectiles.stream().anyMatch(projectile -> proj.getType().name().equalsIgnoreCase(projectile));
        boolean correctType = type.equalsIgnoreCase("both") || type.equalsIgnoreCase("entity") == hitEntity;

        return correctProjectile && correctType;
    }

    private String translateType(String typeInput) {
        return switch (typeInput.toLowerCase()) {
            case "both" -> "both";
            case "entity" -> "entity";
            default -> "both";
        };
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setValues(ProjectileHitEvent event, CastData data) {
        data.put("api-projectile-type", event.getEntity().getType().name());
        data.put("api-hit-entity", Objects.nonNull(event.getHitEntity()));
        data.put("api-projectile-age", event.getEntity().getTicksLived());
        if (PluginChecker.isDivinityActive()) {
            String arrowId = DivinityHook.getArrowId(event.getEntity());
            if (arrowId != null) {
                data.put("api-divinity-arrow-id", arrowId);
                data.put("api-divinity-arrow-level", DivinityHook.getArrowLevel(event.getEntity()));
            }
            // Combined raw bow + ammo + DIV_ARROW damage, pre-mitigation. See ProjectileDamageAPI.
            java.util.Map<String, Double> dmgByType =
                    studio.magemonkey.divinity.api.projectile.ProjectileDamageAPI.getBaseDamageByType(event.getEntity());
            double total = 0D;
            for (java.util.Map.Entry<String, Double> e : dmgByType.entrySet()) {
                data.put("api-divinity-base-damage-" + e.getKey(), e.getValue());
                total += e.getValue();
            }
            data.put("api-divinity-base-damage", total);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LivingEntity getCaster(ProjectileHitEvent event) {
        ProjectileSource shooter = event.getEntity().getShooter();
        return shooter instanceof LivingEntity ? (LivingEntity) shooter : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LivingEntity getTarget(ProjectileHitEvent event, Settings settings) {
        TempEntity projectile   = new TempEntity(new EntityTarget(event.getEntity()));
        boolean    targetCaster = settings.getBool("target", false);
        Entity     hit          = event.getHitEntity();

        if (targetCaster) {
            return (LivingEntity) event.getEntity().getShooter();
        } else if (Objects.nonNull(hit)) {
            return hit instanceof LivingEntity ? (LivingEntity) hit : new TempEntity(hit.getLocation());
        }

        return projectile;
    }
}
