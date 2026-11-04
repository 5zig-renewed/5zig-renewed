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
import com.mojang.blaze3d.vertex.PoseStack;
import eu.the5zig.mod.gui.ingame.ItemStack;
import eu.the5zig.mod.util.RenderContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import eu.the5zig.mod.util.GuiContext;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Wraps a {@link net.minecraft.world.item.ItemStack} for the mod's own {@link ItemStack} abstraction
 * (the two types are unrelated: the core's one lives in {@code eu.the5zig.mod.gui.ingame}).
 * <p>
 * 1.15 rendered through {@code Variables#renderItem}; 1.19.2 items are rendered with
 * {@link GuiContext#renderItem} against the frame's graphics, which is why the attribute overlay is
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
		List<Component> components = item.getTooltipLines(minecraft.player, TooltipFlag.Default.NORMAL);
		List<String> lines = new ArrayList<>(components.size());
		for (Component component : components) {
			lines.add(component.getString());
		}
		return lines;
	}

	@Override
	public int getHealAmount() {
		FoodProperties food = item.getItem().getFoodProperties();
		return food != null ? food.getNutrition() : 0;
	}

	@Override
	public float getSaturationModifier() {
		FoodProperties food = item.getItem().getFoodProperties();
		return food != null ? food.getSaturationModifier() : 0;
	}

	@Override
	public void render(int x, int y, boolean withGenericAttributes) {
		GuiContext graphics = RenderContext.get();
		if (graphics == null || item == null) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		// 1.19.2 has no GuiGraphics#renderItem: the item renderer draws straight into the frame.
		minecraft.getItemRenderer().renderAndDecorateItem(item, x, y);
		minecraft.getItemRenderer().renderGuiItemDecorations(minecraft.font, item, x, y);

		if (!withGenericAttributes) {
			return;
		}
		Multimap<Attribute, AttributeModifier> modifiers = item.getAttributeModifiers(EquipmentSlot.MAINHAND);
		for (Map.Entry<Attribute, AttributeModifier> entry : modifiers.entries()) {
			AttributeModifier modifier = entry.getValue();
			double value = modifier.getAmount();
			if (nameDisplayModifier.equals(modifier.getId())) {
				value += EnchantmentHelper.getDamageBonus(item, MobType.UNDEFINED);
			}
			if (entry.getKey() != Attributes.ATTACK_DAMAGE && entry.getKey() != Attributes.ARMOR) {
				continue;
			}
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
