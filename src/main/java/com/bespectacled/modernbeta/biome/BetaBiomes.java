package com.bespectacled.modernbeta.biome;

import java.util.Arrays;

import com.bespectacled.modernbeta.ModernBeta;
import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.types.templates.List;

import net.minecraft.util.Identifier;
import net.minecraft.util.registry.BuiltinRegistries;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.biome.DefaultBiomeCreator;

public class BetaBiomes {
    public static final ImmutableList<Identifier> BIOMES = ImmutableList.of(
        new Identifier(ModernBeta.ID, "forest"),
        new Identifier(ModernBeta.ID, "shrubland"),
        new Identifier(ModernBeta.ID, "desert"),
        new Identifier(ModernBeta.ID, "savanna"),
        new Identifier(ModernBeta.ID, "plains"),
        new Identifier(ModernBeta.ID, "seasonal_forest"),
        new Identifier(ModernBeta.ID, "rainforest"),
        new Identifier(ModernBeta.ID, "swampland"),
        new Identifier(ModernBeta.ID, "taiga"),
        new Identifier(ModernBeta.ID, "tundra"),
        new Identifier(ModernBeta.ID, "ice_desert"),
        
        new Identifier(ModernBeta.ID, "ocean"),
        new Identifier(ModernBeta.ID, "lukewarm_ocean"),
        new Identifier(ModernBeta.ID, "warm_ocean"),
        new Identifier(ModernBeta.ID, "cold_ocean"),
        new Identifier(ModernBeta.ID, "frozen_ocean")
    );
    
    public static void reserveBiomeIDs() {
        for (Identifier i : BIOMES) {
            Registry.register(BuiltinRegistries.BIOME, i, DefaultBiomeCreator.createNormalOcean(false));
        }
    }
	
}
