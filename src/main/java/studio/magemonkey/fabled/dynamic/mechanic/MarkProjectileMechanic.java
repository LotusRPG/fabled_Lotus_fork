package studio.magemonkey.fabled.dynamic.mechanic;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.metadata.FixedMetadataValue;
import studio.magemonkey.fabled.Fabled;
import studio.magemonkey.fabled.api.particle.target.EffectTarget;
import studio.magemonkey.fabled.api.particle.target.EntityTarget;
import studio.magemonkey.fabled.dynamic.TempEntity;

import java.util.List;

/**
 * Tags a projectile entity with a metadata mark so subsequent Projectile Tick / Projectile Hit
 * triggers can filter to only this specific projectile via {@code requires-mark}.
 * <p>
 * Targets must wrap a {@link Projectile} (e.g. via {@code Launch} trigger with
 * {@code target: projectile}). Non-projectile targets are ignored.
 */
public class MarkProjectileMechanic extends MechanicComponent {
    private static final String KEY         = "key";
    public static final  String META_PREFIX = "fabled_pmark_";

    @Override
    public String getKey() {
        return "mark projectile";
    }

    @Override
    public boolean execute(LivingEntity caster, int level, List<LivingEntity> targets, boolean force) {
        String key = settings.getString(KEY, "");
        if (key.isEmpty()) return false;
        String metaKey = META_PREFIX + key;
        boolean any = false;
        for (LivingEntity t : targets) {
            Projectile proj = unwrapProjectile(t);
            if (proj == null) continue;
            proj.setMetadata(metaKey, new FixedMetadataValue(Fabled.inst(), Boolean.TRUE));
            any = true;
        }
        return any;
    }

    private Projectile unwrapProjectile(LivingEntity target) {
        if (!(target instanceof TempEntity)) return null;
        EffectTarget et = ((TempEntity) target).getEffectTarget();
        if (!(et instanceof EntityTarget)) return null;
        Object entity = ((EntityTarget) et).getEntity();
        return entity instanceof Projectile ? (Projectile) entity : null;
    }
}
