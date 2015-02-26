package org.apollo.game.message.handler.impl;

import org.apollo.game.message.handler.MessageHandler;
import org.apollo.game.message.handler.MessageHandlerContext;
import org.apollo.game.message.impl.ItemOptionMessage;
import org.apollo.game.model.Item;
import org.apollo.game.model.def.EquipmentDefinition;
import org.apollo.game.model.entity.EquipmentConstants;
import org.apollo.game.model.entity.Player;
import org.apollo.game.model.entity.Skill;
import org.apollo.game.model.inv.Inventory;
import org.apollo.game.model.inv.SynchronizationInventoryListener;
import org.apollo.util.LanguageUtil;

/**
 * A {@link MessageHandler} that equips items.
 * 
 * @author Major
 * @author Graham
 * @author Ryley Kimmel <ryley.kimmel@live.com>
 */
public final class EquipItemHandler extends MessageHandler<ItemOptionMessage> {

	@Override
	public void handle(MessageHandlerContext ctx, Player player, ItemOptionMessage message) {
		if (message.getOption() != 2 || message.getInterfaceId() != SynchronizationInventoryListener.INVENTORY_ID) {
			return;
		}

		int inventorySlot = message.getSlot();
		Item equipping = player.getInventory().get(inventorySlot);
		int equippingId = equipping.getId();
		EquipmentDefinition definition = EquipmentDefinition.lookup(equippingId);

		if (definition == null) {
			// We don't break the chain here or any item option messages won't work!
			return;
		}

		for (int id = 0; id < 5; id++) {
			int requirement = definition.getLevel(id);

			if (player.getSkillSet().getMaximumLevel(id) < requirement) {
				String name = Skill.getName(id);
				String article = LanguageUtil.getIndefiniteArticle(name);

				player.sendMessage("You need " + article + " " + name + " level of " + requirement + " to equip this item.");
				ctx.breakHandlerChain();
				return;
			}
		}

		Inventory inventory = player.getInventory();
		Inventory equipment = player.getEquipment();

		int equipmentSlot = definition.getSlot();

		Item weapon = equipment.get(EquipmentConstants.WEAPON);
		Item shield = equipment.get(EquipmentConstants.SHIELD);

		if (definition.isTwoHanded()) {
			int slotsRequired = weapon != null && shield != null ? 1 : 0;
			if (inventory.freeSlots() < slotsRequired) {
				ctx.breakHandlerChain();
				return;
			}

			// Reset the weapon and the shield slots.
			equipment.reset(EquipmentConstants.WEAPON);
			equipment.reset(EquipmentConstants.SHIELD);

			// Set the two-handed weapon and clear it from the inventory.
			equipment.set(EquipmentConstants.WEAPON, inventory.reset(inventorySlot));

			// Add previous shield or weapon, if present.
			if (shield != null) {
				inventory.add(shield);
			}
			if (weapon != null) {
				inventory.add(weapon);
			}
			return;
		}

		if (definition.getSlot() == EquipmentConstants.SHIELD && weapon != null && EquipmentDefinition.lookup(weapon.getId()).isTwoHanded()) {
			equipment.set(EquipmentConstants.SHIELD, inventory.reset(inventorySlot));
			inventory.add(equipment.reset(EquipmentConstants.WEAPON));
			return;
		}

		Item current = equipment.get(equipmentSlot);

		if (current != null && current.getId() == equipping.getId() && current.getDefinition().isStackable()) {
			long total = (long) current.getAmount() + equipping.getAmount();

			// If the total has not over flown and we can add to the existing stack, do so.
			if (total <= Integer.MAX_VALUE && !equipment.add(inventory.reset(inventorySlot)).isPresent()) {
				return;
			}

			int remaining = (int) (total - Integer.MAX_VALUE);
			int removed = equipping.getAmount() - remaining;

			if (remaining == equipping.getAmount()) {
				equipment.set(equipmentSlot, equipping);
				inventory.set(inventorySlot, current);
				return;
			}

			inventory.remove(equipping.getId(), removed);
			equipment.add(equipping.getId(), removed);
			return;
		}

		Item previous = equipment.reset(equipmentSlot);
		inventory.remove(equipping);
		equipment.set(equipmentSlot, equipping);

		if (previous != null) {
			inventory.set(inventorySlot, previous);
		}
	}

}