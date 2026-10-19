package com.sortnstock;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Records *why* ordinary unit tests in this project cannot build real {@code ItemStack}s, so that
 * nobody re-investigates it.
 *
 * <p>Minecraft 26.1 binds an item's data components during the <b>datapack / registry-data load</b>
 * phase, not during {@link Bootstrap#bootStrap()}. The only caller of
 * {@code Holder.Reference#bindComponents} in the whole game jar is
 * {@code DataComponentInitializers$BakedEntry#apply()}, driven by
 * {@code DataComponentInitializers#build(HolderLookup.Provider)}. Calling {@code Bootstrap.bootStrap()}
 * leaves {@code Item#getDefaultMaxStackSize()} throwing {@code NullPointerException: Components not
 * bound yet}, and calling {@code BuiltInRegistries.bootStrap()} as well only throws
 * {@code Registry is already frozen} — it is already invoked by {@code Bootstrap.bootStrap()}.</p>
 *
 * <p>Reproducing that phase in a unit test means standing up the pack/registry-data system, and the
 * Fabric client gametest route needs a full asset download (which did not complete in this
 * environment). Tests here therefore target <b>pure logic</b> extracted out of the
 * Minecraft-touching code, and this class stays disabled as the written-down reason.</p>
 */
class MinecraftBootstrapSpikeTest
{
    @BeforeAll
    static void bootstrapMinecraft()
    {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    @Disabled("Item components require the datapack load phase; see the class comment.")
    void itemComponentsAreNotBoundByPlainBootstrap()
    {
        // This is the assertion that fails without the datapack phase. Kept as executable
        // documentation: flip the @Disabled off if Minecraft ever changes this.
        assertTrue(BuiltInRegistries.ITEM.wrapAsHolder(Items.STONE).areComponentsBound(),
                "26.1 binds item components during datapack load, not during Bootstrap.bootStrap()");
    }
}
