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

import com.google.common.collect.Multimap;
import org.joml.Matrix3x2fStack;
import eu.the5zig.mod.gui.ingame.ItemStack;
import eu.the5zig.mod.util.RenderContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.TooltipFlag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Wraps a {@link net.minecraft.world.item.ItemStack} for the mod's own {@link ItemStack} abstraction
 * (the two types are unrelated: the core's one lives in {@code eu.the5zig.mod.gui.ingame}).
 * <p>
 * 1.15 rendered through {@code Variables#renderItem}; 1.20.1 items are rendered with
 * {@link GuiGraphics#renderItem} against the frame's graphics, which is why the attribute overlay is
 * drawn here with an explicit {@link PoseStack} instead of the old GL matrix stack.
 */
public class WrappedItemStack implements ItemStack {

	protected static final UUID nameDisplayModifier = UUID.fromString("CB3F55D3-645C-4F38-A497-9C13A33DB5CF");

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
		List<Component> components = item.getTooltipLines(Item.TooltipContext.EMPTY, minecraft.player, TooltipFlag.Default.NORMAL);
		List<String> lines = new ArrayList<>(components.size());
		for (Component component : components) {
			lines.add(component.getString());
		}
		return lines;
	}

	@Override
	public int getHealAmount() {
		// 1.21 moved food properties onto the item's data components.
		FoodProperties food = item.get(net.minecraft.core.component.DataComponents.FOOD);
		return food != null ? food.nutrition() : 0;
	}

	@Override
	public float getSaturationModifier() {
		FoodProperties food = item.get(net.minecraft.core.component.DataComponents.FOOD);
		return food != null ? food.saturation() : 0;
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
		// 1.21 stores attribute modifiers as a data component and no longer exposes
		// ItemStack#getAttributeModifiers(EquipmentSlot); the enchantment damage bonus
		// (EnchantmentHelper#getDamageBonus) was removed together with MobType.
		ItemAttributeModifiers modifiers = item.getOrDefault(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS,
				ItemAttributeModifiers.EMPTY);
		for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
			Attribute attribute = entry.attribute().value();
			if (attribute != Attributes.ATTACK_DAMAGE.value() && attribute != Attributes.ARMOR.value()) {
				continue;
			}
			double value = entry.modifier().amount();
			// 1.21.5+ GUI matrices are 2D (JOML Matrix3x2fStack).
			Matrix3x2fStack pose = graphics.pose();
			pose.pushMatrix();
			try {
				pose.translate(x + 8, y + 10);
				pose.scale(0.7F, 0.7F);
				graphics.drawString(minecraft.font, Component.literal("+" + Math.round(value)).withStyle(ChatFormatting.BLUE), 0, 0, eu.the5zig.mod.util.RenderColor.opaque(0xFFFFFF));
			} finally {
				pose.popMatrix();
			}
		}
	}

	public static String getResourceKey(net.minecraft.world.item.ItemStack item) {
		Item value = item.getItem();
		return BuiltInRegistries.ITEM.getKey(value).toString();
	}
}
