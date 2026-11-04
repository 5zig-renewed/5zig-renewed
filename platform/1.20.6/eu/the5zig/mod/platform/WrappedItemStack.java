/*
 * Copyright (c) 2019-2020 5zig Reborn
 *
 * This file is part of The 5zig Mod
 * The 5zig Mod is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The 5zig Mod is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with The 5zig Mod.  If not, see <http://www.gnu.org/licenses/>.
 */


package eu.the5zig.mod.platform;

import com.mojang.blaze3d.vertex.PoseStack;
import eu.the5zig.mod.gui.ingame.ItemStack;
import eu.the5zig.mod.util.RenderContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Wraps a {@link net.minecraft.world.item.ItemStack} for the mod's own {@link ItemStack} abstraction
 * (the two types are unrelated: the core's one lives in {@code eu.the5zig.mod.gui.ingame}).
 * <p>
 * 1.15 rendered through {@code Variables#renderItem}; 1.20.1 items are rendered with
 * {@link GuiGraphics#renderItem} against the frame's graphics, which is why the attribute overlay is
 * drawn here with an explicit {@link PoseStack} instead of the old GL matrix stack.
 */
public class WrappedItemStack implements ItemStack {

	private final net.minecraft.world.item.ItemStack item;

	public WrappedItemStack(net.minecraft.world.item.ItemStack item) {
		this.item = item;
	}

	@Override
	public int getAmount() {
		return item.getCount();
	}

	@Override
	public int getMaxDurability() {
		return item.getMaxDamage();
	}

	@Override
	public int getCurrentDurability() {
		return item.getDamageValue();
	}

	@Override
	public String getKey() {
		return getResourceKey(item);
	}

	@Override
	public String getDisplayName() {
		return item.getHoverName().getString();
	}

	@Override
	public List<String> getLore() {
		Minecraft minecraft = Minecraft.getInstance();
		// 1.20.5+: tooltips take an Item.TooltipContext (the level lookup used to come from the
		// item's own registry access); the client level is the correct scope here.
		List<Component> components = item.getTooltipLines(Item.TooltipContext.of(minecraft.level), minecraft.player, TooltipFlag.Default.NORMAL);
		List<String> lines = new ArrayList<>(components.size());
		for (Component component : components) {
			lines.add(component.getString());
		}
		return lines;
	}

	@Override
	public int getHealAmount() {
		// 1.20.5 moved food data from Item to the minecraft:food data component.
		FoodProperties food = item.get(DataComponents.FOOD);
		return food != null ? food.nutrition() : 0;
	}

	@Override
	public float getSaturationModifier() {
		FoodProperties food = item.get(DataComponents.FOOD);
		if (food == null || food.nutrition() <= 0) {
			return 0;
		}
		// 1.20.4's FoodProperties#getSaturationModifier was a multiplier, the 1.20.5 record stores
		// the final saturation value (nutrition * modifier * 2); invert that so the core's
		// "heal * modifier * 2" saturation preview keeps producing the same number.
		return food.saturation() / (2f * food.nutrition());
	}

	@Override
	public void render(int x, int y, boolean withGenericAttributes) {
		GuiGraphics graphics = RenderContext.get();
		if (graphics == null || item == null) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		graphics.renderItem(item, x, y);
		graphics.renderItemDecorations(minecraft.font, item, x, y);

		if (!withGenericAttributes) {
			return;
		}
		// 1.20.5 moved the attribute modifiers into the minecraft:attribute_modifiers data
		// component. ItemStack only exposes them as a slot-aware callback, and both the attribute
		// (Holder) and the modifier (record) changed shape, so collect them first.
		List<Map.Entry<Attribute, AttributeModifier>> modifiers = new ArrayList<>();
		item.forEachModifier(EquipmentSlot.MAINHAND,
				(attribute, modifier) -> modifiers.add(new AbstractMap.SimpleImmutableEntry<>(attribute.value(), modifier)));
		// The component no longer carries the synthetic "item modifier" that used to smuggle the
		// enchantment damage bonus into getAttributeModifiers, so add it back explicitly.
		float enchantmentDamage = EnchantmentHelper.getDamageBonus(item, null);
		for (Map.Entry<Attribute, AttributeModifier> entry : modifiers) {
			AttributeModifier modifier = entry.getValue();
			boolean attackDamage = entry.getKey() == Attributes.ATTACK_DAMAGE.value();
			if (!attackDamage && entry.getKey() != Attributes.ARMOR.value()) {
				continue;
			}
			// Vanilla's tooltip only folds the enchantment bonus into the base attack-damage
			// modifier, not into every attack-damage entry.
			double value = modifier.amount()
					+ (attackDamage && modifier.id().equals(Item.BASE_ATTACK_DAMAGE_UUID) ? enchantmentDamage : 0);
			PoseStack pose = graphics.pose();
			pose.pushPose();
			try {
				pose.translate(x + 8, y + 10, 1);
				pose.scale(0.7F, 0.7F, 0.7F);
				graphics.drawString(minecraft.font, Component.literal("+" + Math.round(value)).withStyle(ChatFormatting.BLUE), 0, 0, 0xFFFFFF);
			} finally {
				pose.popPose();
			}
		}
	}

	public static String getResourceKey(net.minecraft.world.item.ItemStack item) {
		Item value = item.getItem();
		return BuiltInRegistries.ITEM.getKey(value).toString();
	}
}
