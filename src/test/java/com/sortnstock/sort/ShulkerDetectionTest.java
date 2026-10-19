package com.sortnstock.sort;

import java.util.List;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the assumption behind shulker-box detection: that every shulker box item really is a
 * {@link BlockItem} over a {@link ShulkerBoxBlock}.
 *
 * <p>This is the kind of assumption that is easy to get wrong and hard to notice — the ordering
 * rule can be perfectly correct and still do nothing if detection returns false for every item.</p>
 *
 * <p>Why this works where most item tests do not: {@code instanceof} and {@code getBlock()} read
 * plain Java fields, not data components. Only {@code ItemStack} construction and component lookups
 * need the datapack phase that {@code Bootstrap.bootStrap()} does not perform (see
 * {@code MinecraftBootstrapSpikeTest}). Note the item constants must be read <i>inside</i> the test
 * methods: a static field would be initialised while the test class loads, before
 * {@link BeforeAll} has run the bootstrap.</p>
 */
class ShulkerDetectionTest
{
    @BeforeAll
    static void bootstrapMinecraft()
    {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** All seventeen vanilla shulker box items: the plain one plus the sixteen dyed ones. */
    private static List<Item> vanillaShulkerBoxes()
    {
        return List.of(
                Items.SHULKER_BOX,
                Items.WHITE_SHULKER_BOX, Items.ORANGE_SHULKER_BOX, Items.MAGENTA_SHULKER_BOX,
                Items.LIGHT_BLUE_SHULKER_BOX, Items.YELLOW_SHULKER_BOX, Items.LIME_SHULKER_BOX,
                Items.PINK_SHULKER_BOX, Items.GRAY_SHULKER_BOX, Items.LIGHT_GRAY_SHULKER_BOX,
                Items.CYAN_SHULKER_BOX, Items.PURPLE_SHULKER_BOX, Items.BLUE_SHULKER_BOX,
                Items.BROWN_SHULKER_BOX, Items.GREEN_SHULKER_BOX, Items.RED_SHULKER_BOX,
                Items.BLACK_SHULKER_BOX);
    }

    @Test
    void everyVanillaShulkerBoxIsABlockItemOverShulkerBoxBlock()
    {
        for (Item item : vanillaShulkerBoxes())
        {
            assertTrue(item instanceof BlockItem,
                    item + " is not a BlockItem, so the block-based detection would miss it");

            BlockItem blockItem = (BlockItem) item;

            assertTrue(blockItem.getBlock() instanceof ShulkerBoxBlock,
                    item + " is not backed by a ShulkerBoxBlock");
        }
    }

    /** The detection must not swallow ordinary blocks. */
    @Test
    void ordinaryBlocksAreNotMistakenForShulkerBoxes()
    {
        assertTrue(Items.CHEST instanceof BlockItem);

        assertTrue(((BlockItem) Items.CHEST).getBlock() instanceof ShulkerBoxBlock == false,
                "a chest must not be treated as a shulker box");

        assertTrue(Items.STONE instanceof BlockItem);

        assertTrue(((BlockItem) Items.STONE).getBlock() instanceof ShulkerBoxBlock == false,
                "stone must not be treated as a shulker box");
    }
}
