package com.direwolf20.laserio.common.containers.CustomItemHandlers;

import com.direwolf20.laserio.common.blockentities.LaserNodeBE;
import com.direwolf20.laserio.common.items.cards.BaseCard;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nonnull;

public class NodeItemHandler extends ItemStackHandler {
    LaserNodeBE blockEntity;

    public NodeItemHandler(int size, LaserNodeBE blockEntity) {
        super(size);
        this.blockEntity = blockEntity;
    }

    @Override
    protected void onContentsChanged(int slot) {
        // To make sure the TE persists when the chunk is saved later we need to
        // mark it dirty every time the item handler changes
        if (blockEntity == null) return;
        blockEntity.setChanged();
        blockEntity.notifyOtherNodesOfChange();
        blockEntity.markDirtyClient();

    }

    @Override
    public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
        return stack.getItem() instanceof BaseCard;//ForgeHooks.getBurnTime(stack, RecipeType.SMELTING) > 0;
    }

    @Nonnull
    @Override
    public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
        if (!(stack.getItem() instanceof BaseCard))
            return stack;
        return super.insertItem(slot, stack, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        return 1;
    }
}
