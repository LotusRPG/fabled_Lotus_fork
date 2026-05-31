/**
 * Fabled
 * studio.magemonkey.fabled.dynamic.condition.ItemCondition
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
package studio.magemonkey.fabled.dynamic.condition;

import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import studio.magemonkey.fabled.dynamic.ItemChecker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A condition for dynamic skills that requires the target to have a specified item in one of the listed slots.
 * Slots list accepts: Main_Hand, Off_Hand, Helmet, Chestplate, Leggings, Boots, Any.
 * When the list is empty or contains "Any", all six slots are checked. The condition passes if any listed slot matches.
 */
public class ItemCondition extends ConditionComponent {

    private static final List<String> ALL_SLOTS =
            Arrays.asList("MAIN_HAND", "OFF_HAND", "HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS");
    private static final List<String> DEFAULT_SLOTS = Arrays.asList("MAIN_HAND");

    @Override
    boolean test(final LivingEntity caster, final int level, final LivingEntity target) {
        EntityEquipment eq = target.getEquipment();
        if (eq == null) return false;

        List<String> configured = settings.getStringList("slots");
        if (configured == null || configured.isEmpty()) configured = DEFAULT_SLOTS;

        List<String> slots = new ArrayList<>();
        for (String raw : configured) {
            String norm = raw.replace(" ", "_").toUpperCase();
            if (norm.equals("ANY")) {
                slots.addAll(ALL_SLOTS);
            } else {
                slots.add(norm);
            }
        }

        for (String slot : slots) {
            ItemStack item = resolveSlot(eq, slot);
            if (item == null) continue;
            if (ItemChecker.check(item, level, settings)) return true;
        }
        return false;
    }

    private ItemStack resolveSlot(EntityEquipment eq, String slot) {
        switch (slot) {
            case "MAIN_HAND":
            case "MAINHAND":
            case "HAND":
                return eq.getItemInMainHand();
            case "OFF_HAND":
            case "OFFHAND":
                return eq.getItemInOffHand();
            case "HELMET":
            case "HEAD":
                return eq.getHelmet();
            case "CHESTPLATE":
            case "CHEST":
                return eq.getChestplate();
            case "LEGGINGS":
            case "LEGS":
                return eq.getLeggings();
            case "BOOTS":
            case "FEET":
                return eq.getBoots();
            default:
                return null;
        }
    }

    @Override
    public String getKey() {
        return "item";
    }
}
