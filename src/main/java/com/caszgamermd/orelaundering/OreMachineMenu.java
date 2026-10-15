package com.caszgamermd.orelaundering;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class OreMachineMenu extends AbstractContainerMenu {
    private final Container container;
    private final ContainerData values;

    public OreMachineMenu(int id, Inventory player) {
        this(id, player, new SimpleContainer(3), new SimpleContainerData(21));
    }

    public OreMachineMenu(int id, Inventory player, Container container, ContainerData data) {
        super(OreMenus.MACHINE, id);
        checkContainerSize(container, 3);
        checkContainerDataCount(data, 21);
        this.container = container;
        this.values = data;
        // Three primary slots: input, output, and a read-only sample of the source ore.
        addSlot(new Slot(container, 0, 44, 48) {
            @Override public boolean mayPlace(ItemStack stack) { return container.canPlaceItem(0, stack); }
            @Override public boolean mayPickup(Player player) {
                return !(container instanceof OreMachineEntity machine) || machine.canChangeCastingMold();
            }
        });
        addSlot(new Slot(container, 1, 116, 48) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
        });
        addSlot(new Slot(container, 2, 80, 48) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
            @Override public boolean mayPickup(Player player) { return false; }
        });
        addStandardInventorySlots(player, 8, 114);
        addDataSlots(values);
        container.startOpen(player.player);
    }

    public int value(int index) { return values.get(index); }
    public int millilitres(int index) { return values.get(index) & 0xffff; }

    @Override public ItemStack quickMoveStack(Player player, int slotIndex) {
        if (slotIndex == 2 || slotIndex < 0 || slotIndex >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(slotIndex);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack source = slot.getItem();
        ItemStack old = source.copy();
        if (slotIndex < 3) {
            if (!moveItemStackTo(source, 3, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            if (!container.canPlaceItem(0, source) || !moveItemStackTo(source, 0, 1, false))
                return ItemStack.EMPTY;
        }
        if (source.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return old;
    }
    @Override public boolean stillValid(Player player) { return container.stillValid(player); }
    @Override public void removed(Player player) { super.removed(player); container.stopOpen(player); }
}
