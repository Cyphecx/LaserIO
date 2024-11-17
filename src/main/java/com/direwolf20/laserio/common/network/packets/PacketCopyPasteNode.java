package com.direwolf20.laserio.common.network.packets;

import com.direwolf20.laserio.common.blockentities.LaserNodeBE;
import com.direwolf20.laserio.common.blocks.LaserNode;
import com.direwolf20.laserio.common.containers.CardHolderContainer;
import com.direwolf20.laserio.common.containers.LaserNodeContainer;
import com.direwolf20.laserio.common.containers.customhandler.CardItemHandler;
import com.direwolf20.laserio.common.items.CardCloner;
import com.direwolf20.laserio.common.items.CardHolder;
import com.direwolf20.laserio.common.items.cards.BaseCard;
import com.direwolf20.laserio.common.items.cards.CardItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.*;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fml.Logging;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.network.NetworkEvent;

import java.util.*;
import java.util.function.Supplier;

import static com.direwolf20.laserio.setup.Registration.Card_Item;


public class PacketCopyPasteNode {
    public enum NodeCloneModes {
        CLEAR,
        COPY,
        PASTE
    }

    private final BlockPos pos;
    private final NodeCloneModes action;

    public PacketCopyPasteNode(BlockPos pos,NodeCloneModes action) {
        this.pos = pos;
        this.action = action;
    }

    public static void encode(PacketCopyPasteNode msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeEnum(msg.action);
    }

    public static PacketCopyPasteNode decode(FriendlyByteBuf buf) {
        return new PacketCopyPasteNode(buf.readBlockPos(), buf.readEnum(NodeCloneModes.class));
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
                0.5f, // The volume, 1 is normal, higher is louder
                1, // The pitch, 1 is normal, higher is higher pitch
                1 // A random for some reason? (Some sounds have different variants, like the enchanting table success
        );

        // Send the packet to the player
        player.connection.send(packet);
    }

    public static Map<Item, Integer> getCardsInNode(ItemStackHandler[] faceInventories){
        Map<Item, Integer> existingCards = new HashMap<Item, Integer>();
        for (ItemStackHandler face : faceInventories) {
            for (int slot = 0; slot < LaserNodeContainer.CARDSLOTS; slot++) {
                ItemStack card = face.getStackInSlot(slot);
                if (card.isEmpty()) continue;
                int currentCardCount = existingCards.getOrDefault(card.getItem(), 0);
                existingCards.put(card.getItem(), currentCardCount + card.getCount());
                BaseCard.getCardContents(card).forEach((k, v) -> existingCards.merge(k, v, Integer::sum));
            }
            // Node Overclock Card Slot
            ItemStack card = face.getStackInSlot(LaserNodeContainer.CARDSLOTS);
            if (card.isEmpty()) continue;
            int currentCardCount = existingCards.getOrDefault(card.getItem(), 0);
            existingCards.put(card.getItem(), currentCardCount + card.getCount());
        }
        return existingCards;
    }

    public static class Handler {
        public static void handle(PacketCopyPasteNode msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null)
                    return;
                if (!(player.getMainHandItem().getItem() instanceof CardCloner))
                    return;


                ItemStack clonerStack = player.getMainHandItem();
                CompoundTag newTag = clonerStack.getOrCreateTag();
                if(msg.action == NodeCloneModes.CLEAR){
                    if (newTag.contains("nodeData")) {
                        clonerStack.removeTagKey("nodeData");
                    }
                } else{
                    BlockEntity be = player.level().getBlockEntity(msg.pos);
                    if (!(be instanceof LaserNodeBE laserNode))
                        return;
                    if(msg.action.equals(NodeCloneModes.COPY)){
                        CompoundTag nodeTag = new CompoundTag();
                        laserNode.saveAdditional(nodeTag);
                        nodeTag.putInt("cloned", nodeTag.hashCode());
                        CardCloner.saveNodeData(clonerStack, nodeTag);
                        playSound(player, Holder.direct(SoundEvent.createVariableRangeEvent(new ResourceLocation(SoundEvents.NOTE_BLOCK_BIT.get().getLocation().toString()))));
                    } else if(msg.action.equals(NodeCloneModes.PASTE)){
                        if (newTag.contains("nodeData")) {
                            ItemStack cardHolder = LaserNode.findCardHolders(player);
                            IItemHandler cardHolderHandler = cardHolder.getCapability(ForgeCapabilities.ITEM_HANDLER, null).orElse(new ItemStackHandler(CardHolderContainer.SLOTS));

                            ItemStackHandler[] currentNodeFaces = Arrays.stream(laserNode.nodeSideCaches).map(cache -> {
                                return cache.itemHandler;
                            }).toArray(ItemStackHandler[]::new);
                            Map<Item, Integer> existingCards = getCardsInNode(currentNodeFaces);
                            Map<Item, Integer> neededCards = getCardsInNode(CardCloner.getNodeData(clonerStack));
                            Map<Item, Integer> holderCards = CardHolder.getHolderCardCounts(cardHolder);

                            Map<Item, Integer> cardHolderDelta = new HashMap<Item, Integer>();
                            existingCards.forEach((k, v) -> cardHolderDelta.merge(k, v, Integer::sum));
                            neededCards.forEach((k, v) -> cardHolderDelta.merge(k, -v, Integer::sum));
                            Map<Item, Integer> netCards = new HashMap<Item, Integer>();
                            holderCards.forEach((k, v) -> netCards.merge(k, v, Integer::sum));
                            cardHolderDelta.forEach((k, v) -> netCards.merge(k, v, Integer::sum));

                            System.out.println("Selected Node: " + existingCards);
                            System.out.println("Copied Node: " + neededCards);
                            System.out.println("Held cards: " + holderCards);
                            System.out.println(cardHolderDelta);
                            System.out.println(netCards);

                            // Make sure there are enough of all items to paste successfully.
                            if (netCards.entrySet().stream().allMatch(e -> e.getValue() >= 0)) {

                                for (var entry : cardHolderDelta.entrySet()){
                                    Item cardType = entry.getKey();
                                    int remainingQuantity = entry.getValue();
                                    ItemStack excessItems = ItemStack.EMPTY;
                                    while (remainingQuantity != 0 && excessItems.isEmpty()) {
                                        if (remainingQuantity > 0){
                                            System.out.println("adding");
                                            int maxStackSize = 64;
                                            int stack_size = Math.min(remainingQuantity, maxStackSize);
                                            ItemStack cardDeposit = new ItemStack(cardType, stack_size);
                                            remainingQuantity -= stack_size;
                                            excessItems = CardHolder.addCardToInventory(cardHolder, cardDeposit);
                                        } else if (remainingQuantity < 0) {
                                            System.out.println("removing");
                                            ItemStack retrievedCards = CardHolder.requestCardFromInventory(cardHolder, new ItemStack(cardType, Math.abs(remainingQuantity)));
                                            remainingQuantity += retrievedCards.getCount();
                                            if (retrievedCards.getCount() != remainingQuantity){
                                                // retrieved amount should never be too small since we check the total card counts,
                                                // but just in case, break out of the loop.
                                                remainingQuantity = 0;
                                            }
                                        }
                                    }
                                    if (!excessItems.isEmpty()) {
                                        // Drop any cards that could not fit into the cardHolder
                                        for(int i = 0; i < excessItems.getCount(); i++){
                                            ItemStack individualCard = new ItemStack(excessItems.getItem(), 1);
                                            ItemEntity itemEntity = new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), individualCard);
                                            player.level().addFreshEntity(itemEntity);
                                        }
                                    }
                                }

                                laserNode.load(newTag.getCompound("nodeData"));
                                laserNode.updateThisNode();
                                playSound(player, Holder.direct(SoundEvent.createVariableRangeEvent(new ResourceLocation(SoundEvents.EXPERIENCE_ORB_PICKUP.getLocation().toString()))));
                            } else {
                                player.displayClientMessage(Component.translatable("message.laserio.cloner.cardcount"), true);
                                playSound(player, Holder.direct(SoundEvent.createVariableRangeEvent(new ResourceLocation(SoundEvents.WAXED_SIGN_INTERACT_FAIL.getLocation().toString()))));
                            }
                        } else {
                            player.displayClientMessage(Component.translatable("message.laserio.cloner.nodata"), true);
                            playSound(player, Holder.direct(SoundEvent.createVariableRangeEvent(new ResourceLocation(SoundEvents.WAXED_SIGN_INTERACT_FAIL.getLocation().toString()))));
                        }
                    }
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }
}

