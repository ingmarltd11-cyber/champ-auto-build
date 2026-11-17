package com.champ.autobuild.builder;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.function.Predicate;

public final class InvUtil {
    private InvUtil() {
    }

    public static Identifier idOf(Item item) {
        return Registries.ITEM.getId(item);
    }

    public static String stringId(Item item) {
        return idOf(item).toString();
    }

    public static Item itemOf(String id) {
        Identifier identifier = Identifier.tryParse(id);
        return identifier == null ? null : Registries.ITEM.get(identifier);
    }

    



    public static int count(ClientPlayerEntity player, Item item) {
        if (item == null) return 0;
        int total = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() == item) total += stack.getCount();
        }
        return total;
    }

    
    public static int findSlot(ClientPlayerEntity player, Predicate<ItemStack> predicate) {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (!stack.isEmpty() && predicate.test(stack)) return i;
        }
        return -1;
    }

    





    public static boolean selectItem(MinecraftClient client, Item item) {
        ClientPlayerEntity player = client.player;
        if (player == null || item == null) return false;
        
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() == item) {
                select(client, i);
                return true;
            }
        }
        int slot = findSlot(player, stack -> stack.getItem() == item);
        if (slot < 0) return false;
        
        
        
        
        int selected = player.getInventory().getSelectedSlot();
        if (client.interactionManager != null) {
            client.interactionManager.clickSlot(player.playerScreenHandler.syncId, handlerSlot(slot),
                    selected, net.minecraft.screen.slot.SlotActionType.SWAP, player);
        }
        select(client, selected);
        return true;
    }

    
    private static int handlerSlot(int inventorySlot) {
        return inventorySlot < 9 ? 36 + inventorySlot : inventorySlot;
    }

    private static void select(MinecraftClient client, int slot) {
        ClientPlayerEntity player = client.player;
        if (player == null) return;
        player.getInventory().setSelectedSlot(slot);
        if (client.getNetworkHandler() != null) {
            client.getNetworkHandler().sendPacket(
                    new net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket(slot));
        }
    }

    public static boolean hasFreeSlot(ClientPlayerEntity player) {
        for (int i = 0; i < 36; i++) {
            if (player.getInventory().getStack(i).isEmpty()) return true;
        }
        return false;
    }

    public static boolean isBlockItem(ItemStack stack) {
        return stack.getItem() instanceof BlockItem;
    }

    
    public static Item findBlock(ClientPlayerEntity player, Predicate<Item> predicate) {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem)) continue;
            if (predicate.test(stack.getItem())) return stack.getItem();
        }
        return null;
    }
}
