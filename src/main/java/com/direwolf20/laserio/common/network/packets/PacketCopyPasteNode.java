package com.direwolf20.laserio.common.network.packets;

import com.direwolf20.laserio.common.blockentities.LaserNodeBE;
import com.direwolf20.laserio.common.blocks.LaserNode;
import com.direwolf20.laserio.common.containers.CardHolderContainer;
import com.direwolf20.laserio.common.containers.LaserNodeContainer;
import com.direwolf20.laserio.common.containers.customhandler.LaserNodeItemHandler;
import com.direwolf20.laserio.common.items.CardCloner;
import com.direwolf20.laserio.common.items.cards.BaseCard;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketCopyPasteNode {
    public final static boolean CLONER_COPY = true;
    public final static boolean CLONER_PASTE = false;

    private final BlockPos pos;
    private final boolean copy;

    public PacketCopyPasteNode(BlockPos pos, boolean copy) {
        this.pos = pos;
        this.copy = copy;
    }

    public static void encode(PacketCopyPasteNode msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeBoolean(msg.copy);
    }

    public static PacketCopyPasteNode decode(FriendlyByteBuf buf) {
        return new PacketCopyPasteNode(buf.readBlockPos(), buf.readBoolean());
    }

    public static void playSound(ServerPlayer player, Holder<SoundEvent> soundEventHolder) {
        // Get player's position
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();

        // Create the packet
        ClientboundSoundPacket packet = new ClientboundSoundPacket(
                soundEventHolder, // The sound event
                SoundSource.MASTER, // The sound category
                x, y, z, // The sound location
                1, // The volume, 1 is normal, higher is louder
                1, // The pitch, 1 is normal, higher is higher pitch
                1 // A random for some reason? (Some sounds have different variants, like the enchanting table success
        );

        // Send the packet to the player
        player.connection.send(packet);
    }
    public static ItemStack[] getExistingCardsInNode(LaserNodeBE laserNode, ItemStack clonerStack){
        // Array to hold all slots for cards(number of node faces times the number of slots per face) with one extra for the overclock slot.
        ItemStack[] existingCards = new ItemStack[Direction.values().length * LaserNodeContainer.CARDSLOTS + 1];

        for (Direction d : Direction.values()) {
            LaserNodeItemHandler nodeItemHandler = (LaserNodeItemHandler)laserNode.getCapability(ForgeCapabilities.ITEM_HANDLER, d).orElse(new ItemStackHandler(CardHolderContainer.SLOTS));
            for (int slot = 0; slot < LaserNodeContainer.CARDSLOTS; slot++) {
                existingCards[d.ordinal()*LaserNodeContainer.CARDSLOTS + slot] = nodeItemHandler.getStackInSlot(slot);

                System.out.print(nodeItemHandler.getStackInSlot(slot) + " ");
            }
            System.out.println();
        }
        System.out.println(ItemStack.isSameItemSameTags(existingCards[0],(existingCards[10])));
        return existingCards;
    }

    public static Integer[] getCardAmounts(){
        return new Integer[BaseCard.CardType.values().length];
    }

    public static class Handler {
        public static void handle(PacketCopyPasteNode msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null)
                    return;
                if (!(player.getMainHandItem().getItem() instanceof CardCloner))
                    return;

                BlockEntity be = player.level().getBlockEntity(msg.pos);
                if (!(be instanceof LaserNodeBE))
                    return;

                LaserNodeBE laserNode = (LaserNodeBE) be;

                ItemStack clonerStack = player.getMainHandItem();
                if(msg.copy){
                    CompoundTag nodeTag = new CompoundTag();
                    laserNode.saveAdditional(nodeTag);
                    nodeTag.putInt("cloned", nodeTag.hashCode());
                    CardCloner.saveNodeData(clonerStack, nodeTag);
                    playSound(player, Holder.direct(SoundEvent.createVariableRangeEvent(new ResourceLocation(SoundEvents.WOOD_BREAK.getLocation().toString()))));
                } else {
                    CompoundTag newTag = clonerStack.getTag();
                    if (newTag == null) {
                        System.out.println("Null");
                        return;
                    }
                    if (!newTag.contains("nodeData")) {
                        System.out.println("not cloned");
                        return;
                    }
                    ItemStack cardHolder = LaserNode.findCardHolders(player);
                    IItemHandler cardHolderHandler = cardHolder.getCapability(ForgeCapabilities.ITEM_HANDLER, null).orElse(new ItemStackHandler(CardHolderContainer.SLOTS));

                    ItemStack[] existingCards = getExistingCardsInNode(laserNode, clonerStack);
                    Integer[] cardCounts = new Integer[BaseCard.CardType.values().length];

                    cardHolderHandler.getSlots();
//                    for(int i = 0; i < cardHolderHandler.getSlots(); i++) {
//                        System.out.print(cardHolderHandler.getStackInSlot(i).getItem() + " ");
//                    }


                    // DROP EXCESS CARDS/FILTERS
                    //laserNode.load(newTag.getCompound("nodeData"));
                    laserNode.updateThisNode();
                    playSound(player, Holder.direct(SoundEvent.createVariableRangeEvent(new ResourceLocation(SoundEvents.CANDLE_PLACE.getLocation().toString()))));
                }

            });
            ctx.get().setPacketHandled(true);
        }
    }
}

