package com.bespectacled.modernbeta.gen;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;
import org.apache.logging.log4j.Level;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.util.registry.RegistryLookupCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.JigsawJunction;
import net.minecraft.structure.PoolStructurePiece;
import net.minecraft.structure.StructureManager;
import net.minecraft.structure.StructurePiece;
import net.minecraft.structure.StructureStart;
import net.minecraft.structure.pool.StructurePool;
import net.minecraft.util.Util;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.registry.DynamicRegistryManager;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.Heightmap;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.GenerationSettings;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.chunk.ProtoChunk;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkRandom;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.carver.ConfiguredCarver;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.chunk.StructureConfig;
import net.minecraft.world.gen.feature.ConfiguredStructureFeature;
import net.minecraft.world.gen.feature.ConfiguredStructureFeatures;
import net.minecraft.world.gen.feature.StructureFeature;
import com.bespectacled.modernbeta.ModernBeta;
import com.bespectacled.modernbeta.biome.BetaBiomeSource;
import com.bespectacled.modernbeta.config.ModernBetaConfig;
import com.bespectacled.modernbeta.decorator.BetaDecorator;
import com.bespectacled.modernbeta.noise.*;
import com.bespectacled.modernbeta.util.MutableBiomeArray;

//private final BetaGeneratorSettings settings;

public class SkylandsChunkGenerator extends NoiseChunkGenerator {
	
    static int noiseWeightX;
    static int noiseWeightY;
    static int noiseWeightZ;
    
    private static final float[] NOISE_WEIGHT_TABLE = Util.<float[]>make(new float[13824], arr -> {
        for (noiseWeightX = 0; noiseWeightX < 24; ++noiseWeightX) {
            for (noiseWeightY = 0; noiseWeightY < 24; ++noiseWeightY) {
                for (noiseWeightZ = 0; noiseWeightZ < 24; ++noiseWeightZ) {
                    arr[noiseWeightX * 24 * 24 + noiseWeightY * 24 + noiseWeightZ] = 
                		(float)calculateNoiseWeight(noiseWeightY - 12, noiseWeightZ - 12, noiseWeightX - 12);
                }
            }
        }
        return;
    });
    
	public static final Codec<SkylandsChunkGenerator> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(generator -> generator.biomeSource),
            Codec.LONG.fieldOf("seed").stable().forGetter(generator -> generator.worldSeed),
            BetaGeneratorSettings.CODEC.fieldOf("settings").forGetter(generator -> generator.settings)
    ).apply(instance, instance.stable(SkylandsChunkGenerator::new)));
	
	private final BetaGeneratorSettings settings;
	
	private BetaNoiseGeneratorOctaves minLimitNoiseOctaves; 
	private BetaNoiseGeneratorOctaves maxLimitNoiseOctaves;
	private BetaNoiseGeneratorOctaves mainNoiseOctaves; 
	private BetaNoiseGeneratorOctaves beachNoiseOctaves; 
	private BetaNoiseGeneratorOctaves stoneNoiseOctaves;
	public BetaNoiseGeneratorOctaves scaleNoiseOctaves; 
	public BetaNoiseGeneratorOctaves depthNoiseOctaves;
	
	//private final NoiseSampler surfaceDepthNoise;
    
    private double heightmap[]; // field_4180_q
    private static double heightmapCache[];
    
    private double sandNoise[];
    private double gravelNoise[];
    private double stoneNoise[];
    
    double mainNoise[]; // field_4185_d
    double minLimitNoise[]; // field_4184_e
    double maxLimitNoise[]; // field_4183_f

    double scaleNoise[]; // field_4182_g
    double depthNoise[]; // field_4181_h

    private Random rand;
    
    BetaBiomeSource biomeSource;
    private double temps[];
    
    public static long seed;
    //private boolean generateOceans;
    
    // Block Y-height cache, taken from Beta+
 	public Map<BlockPos, Integer> groundCacheY = new HashMap<>();
    
	public SkylandsChunkGenerator(BiomeSource biomes, long seed, BetaGeneratorSettings settings) {
		super(biomes, seed, () -> settings.wrapped);
		this.settings = settings;
		this.seed = seed;
		this.rand = new Random(seed);
		this.biomeSource = (BetaBiomeSource)biomes;
		//this.generateOceans = ModernBetaConfig.loadConfig().generate_oceans;
		
		// Noise Generators
	    minLimitNoiseOctaves = new BetaNoiseGeneratorOctaves(rand, 16); 
	    maxLimitNoiseOctaves = new BetaNoiseGeneratorOctaves(rand, 16); 
	    mainNoiseOctaves = new BetaNoiseGeneratorOctaves(rand, 8);  
	    beachNoiseOctaves = new BetaNoiseGeneratorOctaves(rand, 4); 
	    stoneNoiseOctaves = new BetaNoiseGeneratorOctaves(rand, 4); 
	    scaleNoiseOctaves = new BetaNoiseGeneratorOctaves(rand, 10); 
	    depthNoiseOctaves = new BetaNoiseGeneratorOctaves(rand, 16); 

		// Yes this is messy.  What else am I supposed to do?
	    BetaDecorator.COUNT_BETA_NOISE_DECORATOR.setSeed(seed);
	    ModernBeta.GEN = "skylands";
	    ModernBeta.SEED = seed;
	}
    
	
	public static void register() {
		ModernBeta.LOGGER.log(Level.INFO, "Registering Beta chunk generator...");
		Registry.register(Registry.CHUNK_GENERATOR, new Identifier(ModernBeta.ID, "skylands"), CODEC);
		ModernBeta.LOGGER.log(Level.INFO, "Registered Beta chunk generator.");
	}
	
	@Override
	protected Codec<? extends ChunkGenerator> getCodec() {
		return SkylandsChunkGenerator.CODEC;
	}
	
    @Override
    public void populateNoise(WorldAccess worldAccess, StructureAccessor structureAccessor, Chunk chunk) {
        ChunkPos pos = chunk.getPos();
    	
    	rand.setSeed((long)chunk.getPos().x * 341873128712L  + (long)chunk.getPos().z * 132897987541L);

    	biomeSource.fetchTempHumid(chunk.getPos().x * 16, chunk.getPos().z * 16, 16, 16);
    	temps = biomeSource.temps;
    	generateTerrain(chunk, temps, structureAccessor);    
    }
    
 
    @Override
    public void buildSurface(ChunkRegion chunkRegion, Chunk chunk) {
        
        // Do not use the built-in surface builders..
        // This works better for Beta-accurate surface generation anyway.
        buildBetaSurface(chunk);
    }
    
    
    public void generateTerrain(Chunk chunk, double[] temps, StructureAccessor structureAccessor) {
        byte byte2 = 2;
        //byte seaLevel = (byte)this.getSeaLevel();
        byte byte33 = 33;
        
        int int3_0 = byte2 + 1;
        int int3_1 = byte2 + 1;
        
        BlockPos.Mutable mutableBlock = new BlockPos.Mutable();
        Heightmap heightmapOCEAN = chunk.getHeightmap(Heightmap.Type.OCEAN_FLOOR_WG);
        Heightmap heightmapSURFACE = chunk.getHeightmap(Heightmap.Type.WORLD_SURFACE_WG);
        
        // Not working, densities are calculated differently now.
        /*
        ObjectList<StructurePiece> structureList = (ObjectList<StructurePiece>)new ObjectArrayList(10);
    	ObjectList<JigsawJunction> jigsawList = (ObjectList<JigsawJunction>)new ObjectArrayList(32);
    	
    	for (final StructureFeature<?> s : StructureFeature.JIGSAW_STRUCTURES) {
            
            structureAccessor.getStructuresWithChildren(ChunkSectionPos.from(chunk.getPos(), 0), s).forEach(structureStart -> {
            	Iterator<StructurePiece> structurePieceIterator;
                StructurePiece structurePiece;
                
                Iterator<JigsawJunction> jigsawJunctionIterator;
                JigsawJunction jigsawJunction;
                
                ChunkPos arg2 = chunk.getPos();
                
                PoolStructurePiece poolStructurePiece;
                StructurePool.Projection structureProjection;
                
                ObjectList list;
                ObjectList list2;
                
                int integer13;
                int integer14;
                int n2 = arg2.x;
                int n3 = arg2.z;
            	
            	structurePieceIterator = structureStart.getChildren().iterator();
                while (structurePieceIterator.hasNext()) {
                    structurePiece = structurePieceIterator.next();
                    if (!structurePiece.intersectsChunk(arg2, 12)) {
                        continue;
                    }
                    else if (structurePiece instanceof PoolStructurePiece) {
                        poolStructurePiece = (PoolStructurePiece)structurePiece;
                        structureProjection = poolStructurePiece.getPoolElement().getProjection();
                        
                        if (structureProjection == StructurePool.Projection.RIGID) {
                        	structureList.add(poolStructurePiece);
                        }
                        jigsawJunctionIterator = poolStructurePiece.getJunctions().iterator();
                        while (jigsawJunctionIterator.hasNext()) {
                            jigsawJunction = jigsawJunctionIterator.next();
                            integer13 = jigsawJunction.getSourceX();
                            integer14 = jigsawJunction.getSourceZ();
                            if (integer13 > n2 - 12 && integer14 > n3 - 12 && integer13 < n2 + 15 + 12) {
                                if (integer14 >= n3 + 15 + 12) {
                                    continue;
                                }
                                else {
                                	jigsawList.add(jigsawJunction);
                                }
                            }
                        }
                    }
                    else {
                    	structureList.add(structurePiece);
                    }
                }
                return;
            });
        }
    	
    	ObjectListIterator<StructurePiece> structureListIterator = (ObjectListIterator<StructurePiece>)structureList.iterator();
        ObjectListIterator<JigsawJunction> jigsawListIterator = (ObjectListIterator<JigsawJunction>)jigsawList.iterator();
        */
        
        heightmap = generateHeightmap(heightmap, chunk.getPos().x * byte2, 0, chunk.getPos().z * byte2, int3_0, byte33, int3_1);
        
        // Noise is sampled in 4x16x4 sections?
        for(int i = 0; i < byte2; i++) { 
            for(int j = 0; j < byte2; j++) { 
                for(int k = 0; k < 32; k++) { 
                							  
                    double quarter = 0.25D;
                    
                    double var1 = heightmap[((i + 0) * int3_1 + (j + 0)) * byte33 + (k + 0)];
                    double var2 = heightmap[((i + 0) * int3_1 + (j + 1)) * byte33 + (k + 0)];
                    double var3 = heightmap[((i + 1) * int3_1 + (j + 0)) * byte33 + (k + 0)];
                    double var4 = heightmap[((i + 1) * int3_1 + (j + 1)) * byte33 + (k + 0)];
                    
                    double var5 = (heightmap[((i + 0) * int3_1 + (j + 0)) * byte33 + (k + 1)] - var1) * quarter; // Lerped by this amount, (var5 - var1) * 0.25D
                    double var6 = (heightmap[((i + 0) * int3_1 + (j + 1)) * byte33 + (k + 1)] - var2) * quarter;
                    double var7 = (heightmap[((i + 1) * int3_1 + (j + 0)) * byte33 + (k + 1)] - var3) * quarter;
                    double var8 = (heightmap[((i + 1) * int3_1 + (j + 1)) * byte33 + (k + 1)] - var4) * quarter;
                    
                    for(int l = 0; l < 4; l++) {
                        double eighth = 0.125D; 
                        double var10 = var1;
                        double var11 = var2;
                        double var12 = (var3 - var1) * eighth; // Lerp
                        double var13 = (var4 - var2) * eighth;
                        
                        int integer40 = k * 8 + l;

                        for(int m = 0; m < 8; m++) { 
                            int x = (m + i * 8);
							int y = k * 4 + l;
							int z = (j * 8);
                            
                            double var14 = 0.125D;
                            double density = var10; // var15
                            double var16 = (var11 - var10) * var14; // More lerp
                            
                            int integer54 = (chunk.getPos().x << 4) + i * 4 + m;
                            
                            for(int n = 0; n < 8; n++) { 
                            	
                            	int integer63 = (chunk.getPos().z << 4) + j * 4 + n;
                            	
                            	//double temp = temps[(i * 4 + m) * 16 + (j * 4 + n)];
                            	double temp = 0;
                                
                                double noiseWeight;
                            	/*
                            	while (structureListIterator.hasNext()) {
                                    StructurePiece curStructurePiece = (StructurePiece)structureListIterator.next();
                                    BlockBox blockBox = curStructurePiece.getBoundingBox();
                                    
                                    int sX = Math.max(0, Math.max(blockBox.minX - integer54, integer54 - blockBox.maxX));
                                    int sY = y - (blockBox.minY + ((curStructurePiece instanceof PoolStructurePiece) ? 
                                		((PoolStructurePiece)curStructurePiece).getGroundLevelDelta() : 0));
                                    int sZ = Math.max(0, Math.max(blockBox.minZ - integer63, integer63 - blockBox.maxZ));
                                    
                                    //density += getNoiseWeight(sX, sY, sZ) * 0.2;
                                    // Temporary fix
                                    if (sY >= -2 && sY < 0 && sX == 0 && sZ == 0)
                                        density = 1;
                                }
                                structureListIterator.back(structureList.size());
                                
                                while (jigsawListIterator.hasNext()) {
                                    JigsawJunction curJigsawJunction = (JigsawJunction)jigsawListIterator.next();
                                    
                                    int jX = integer54 - curJigsawJunction.getSourceX();
                                    int jY = y - curJigsawJunction.getSourceGroundY();
                                    int jZ = integer63 - curJigsawJunction.getSourceZ();
                                    
                                    //density += getNoiseWeight(jX, jY, jZ) * 0.4;
                                    // Temporary fix       
                                    if (jY >= -2 && jY < 0 && jX == 0 && jZ == 0)
                                        density = 1;
                                }
                                jigsawListIterator.back(jigsawList.size());
                                */

                            	BlockState blockToSet = this.getBlockState(density, y, temp);
                            	
                            	
                                chunk.setBlockState(mutableBlock.set(x, y, z), blockToSet, false);
                                
                                //heightmapOCEAN.trackUpdate(x, y, z, blockToSet);
                                //heightmapSURFACE.trackUpdate(x, y, z, blockToSet);
                               
                                ++z;
                                density += var16;
                            }

                            var10 += var12;
                            var11 += var13;
                        }

                        var1 += var5;
                        var2 += var6;
                        var3 += var7;
                        var4 += var8;
                    }
                }
            }
        }
    }
    
    private double[] generateHeightmap(double heightmap[], int x, int y, int z, int int5_0, int byte33, int int5_1) {
        if(heightmap == null) {
            heightmap = new double[int5_0 * byte33 * int5_1];
        }
        
        // Var names taken from old customized preset names
        double coordinateScale = 684.41200000000003D; // d
        double heightScale = 684.41200000000003D; // d1
        
        double depthNoiseScaleX = 200D;
        double depthNoiseScaleZ = 200D;
        double depthNoiseScaleExponent = 0.5D;
        
        double mainNoiseScaleX = 80D;
        double mainNoiseScaleY = 160D;
        double mainNoiseScaleZ = 80D;
        
        double lowerLimitScale = 512D;
        double upperLimitScale = 512D;
        
        double temps[] = biomeSource.temps;
        double humids[] = biomeSource.humids;
       
        scaleNoise = scaleNoiseOctaves.func_4109_a(scaleNoise, x, z, int5_0, int5_1, 1.121D, 1.121D, 0.5D);
        depthNoise = depthNoiseOctaves.func_4109_a(depthNoise, x, z, int5_0, int5_1, depthNoiseScaleX, depthNoiseScaleZ, depthNoiseScaleExponent);
        
        coordinateScale *= 2D;
        
        mainNoise = mainNoiseOctaves.generateNoiseOctaves(
    		mainNoise, 
    		x, 
    		y, 
    		z,
    		int5_0, 
    		byte33, 
    		int5_1, 
    		coordinateScale / mainNoiseScaleX, 
    		heightScale / mainNoiseScaleY, 
    		coordinateScale / mainNoiseScaleZ
		);
        
        minLimitNoise = minLimitNoiseOctaves.generateNoiseOctaves(
    		minLimitNoise, 
    		x, 
    		y, 
    		z, 
    		int5_0, 
    		byte33, 
    		int5_1, 
    		coordinateScale, 
    		heightScale, 
    		coordinateScale
		);
        
        maxLimitNoise = maxLimitNoiseOctaves.generateNoiseOctaves(
    		maxLimitNoise,
    		x, 
    		y, 
    		z, 
    		int5_0, 
    		byte33, 
    		int5_1, 
    		coordinateScale, 
    		heightScale, 
    		coordinateScale
		);
        
        int i = 0;
        int j = 0;
        int k = 16 / int5_0;
        
        for(int l = 0; l < int5_0; l++) {
            int idx0 = l * k + k / 2;
            
            for(int m = 0; m < int5_1; m++) {
                int idx1 = m * k + k / 2;
                
                double curTemp = temps[idx0 * 16 + idx1];
                double curHumid = humids[idx0 * 16 + idx1] * curTemp;
                
                double humidMod = 1.0D - curHumid;
                humidMod *= humidMod;
                humidMod *= humidMod;
                humidMod = 1.0D - humidMod;
                
                double scaleMod = (scaleNoise[j] + 256D) / 512D;
                scaleMod *= humidMod;
                
                if(scaleMod > 1.0D) {
                    scaleMod = 1.0D;
                }
                
                double depthMod = depthNoise[j] / 8000D;
                
                if(depthMod < 0.0D) {
                    depthMod = -depthMod * 0.29999999999999999D;
                }
                
                depthMod = depthMod * 3D - 2D;
             
                if(depthMod > 1.0D) {
                    depthMod = 1.0D;
                }
                
                depthMod /= 8D;
                depthMod = 0.0D;
                
                if(scaleMod < 0.0D) {
                    scaleMod = 0.0D;
                }
                
                scaleMod += 0.5D;
                depthMod = (depthMod * (double)byte33) / 16D;
                
                double depthMod2 = (double)byte33 / 16D;
                
                j++;
                
                for(int n = 0; n < byte33; n++)
                {
                    double heightVal = 0.0D;
                    double scaleMod2 = (((double)n - depthMod2) * 8D) / scaleMod;
                    
                    if(scaleMod2 < 0.0D) {
                        scaleMod2 *= -1D;   
                    }
                    
                    double minLimitMod = minLimitNoise[i] / lowerLimitScale;
                    double maxLimitMod = maxLimitNoise[i] / upperLimitScale; 
                    double mainNoiseMod = (mainNoise[i] / 10D + 1.0D) / 2D;
                    
                    if(mainNoiseMod < 0.0D) {
                        heightVal = minLimitMod;
                    } else if(mainNoiseMod > 1.0D) {
                        heightVal = maxLimitMod;
                    } else {
                        heightVal = minLimitMod + (maxLimitMod - minLimitMod) * mainNoiseMod;
                    }
                    heightVal -= 8D;
                    int int_32 = 32;
                    
                    if(n > byte33 - int_32) {
                        double d13 = (float)(n - (byte33 - int_32)) / ((float)int_32 - 1.0F);
                        heightVal = heightVal * (1.0D - d13) + -30D * d13;
                    }
                    
                    int_32 = 8;
                    if(n < int_32) {
                        double d14 = (float)(int_32 - n) / ((float)int_32 - 1.0F);
                        heightVal = heightVal * (1.0D - d14) + -30D * d14;
                    }
                    
                    heightmap[i] = heightVal;
                    i++;
                }

            }

        }

        return heightmap;
    }
    
    
    private void buildBetaSurface(Chunk chunk) {
        byte seaLevel = (byte)this.getSeaLevel();
        double thirtysecond = 0.03125D;
        
        int chunkX = chunk.getPos().x;
        int chunkZ = chunk.getPos().z;
        
        biomeSource.fetchTempHumid(chunkX * 16, chunkZ * 16, 16, 16);
        BlockPos.Mutable mutableBlock = new BlockPos.Mutable();
 
        Biome curBiome;
        
        //sandNoise = beachNoiseOctaves.generateNoiseOctaves(sandNoise, chunkX * 16, chunkZ * 16, 0.0D, 16, 16, 1,  thirtysecond, thirtysecond, 1.0D);
        //gravelNoise = beachNoiseOctaves.generateNoiseOctaves(gravelNoise, chunkX * 16, 109.0134D, chunkZ * 16, 16, 1, 16, thirtysecond, 1.0D, thirtysecond);
        stoneNoise = stoneNoiseOctaves.generateNoiseOctaves(stoneNoise, chunkX * 16, chunkZ * 16, 0.0D, 16, 16, 1, thirtysecond * 2D, thirtysecond * 2D, thirtysecond * 2D);
            
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                
                //boolean genSandBeach = sandNoise[i + j * 16] * rand.nextDouble() * 0.20000000000000001D > 0.0D;
                //boolean genGravelBeach = gravelNoise[i + j * 16] + rand.nextDouble() * 0.20000000000000001D > 3D;
            
                int genStone = (int)(stoneNoise[i + j * 16] / 3D + 3D + rand.nextDouble() * 0.25D); 
                int flag = -1;
                
                //curBiome = biomesInChunk[j][i];
                curBiome = biomeSource.biomesInChunk1D[i + j * 16];
                
                Block biomeTopBlock;
                Block biomeFillerBlock;
                
                // Equivalent of surface builder here
                if (curBiome.equals(biomeSource.biomeRegistry.get(new Identifier(ModernBeta.ID, "desert"))) || 
                    curBiome.equals(biomeSource.biomeRegistry.get(new Identifier(ModernBeta.ID, "ice_desert")))) {
                	biomeTopBlock = biomeFillerBlock = Blocks.SAND;
                } else {
                	biomeTopBlock = Blocks.GRASS_BLOCK;
                	biomeFillerBlock = Blocks.DIRT;
                }
                
                Block topBlock = biomeTopBlock;
                Block fillerBlock = biomeFillerBlock;
                
                // Generate from top to bottom of world
                for (int y = 127; y>= 0; y--) {
                	
                    Block someBlock = chunk.getBlockState(mutableBlock.set(j, y, i)).getBlock();
                    
                    if (someBlock.equals(Blocks.AIR)) { // Skip if air block
                        flag = -1;
                        continue;
                    }
                    
                    if (!someBlock.equals(Blocks.STONE)) { // Skip if not stone
                        continue;
                    }
                    
                    if (flag == -1) {
                        if (genStone <= 0) { // Generate stone basin if noise permits
                            topBlock = Blocks.AIR;
                            fillerBlock = Blocks.STONE;
                        }
           
                       // Main surface builder section
                       flag = genStone;
                       if (y >= 0) {
                    	   chunk.setBlockState(mutableBlock.set(j, y, i), topBlock.getDefaultState(), false);
                       } else {
                    	   chunk.setBlockState(mutableBlock.set(j, y, i), fillerBlock.getDefaultState(), false);
                       }
                       
                       continue;
                    }
                    
                    if (flag <= 0) {
                        continue;
                    }
                    
                    flag--;
                    chunk.setBlockState(mutableBlock.set(j, y, i), fillerBlock.getDefaultState(), false);
                    
                    // Generates layer of sandstone starting at lowest block of sand, of height 1 to 4.
                    if(flag == 0 && fillerBlock.equals(Blocks.SAND)) {
                        flag = rand.nextInt(4);
                        fillerBlock = Blocks.SANDSTONE;
                    }
                }                
            }
        }
    }
    
    protected BlockState getBlockState(double density, int y, double temp) {
        BlockState blockStateToSet = Blocks.AIR.getDefaultState();
        
        if (density > 0.0) {
            blockStateToSet = this.settings.wrapped.getDefaultBlock();
        }
        
        return blockStateToSet;
    }
    
    // From NoiseChunkGenerator
    private static double getNoiseWeight(int x, int y, int z) {
        int i = x + 12;
        int j = y + 12;
        int k = z + 12;
        if (i < 0 || i >= 24) {
            return 0.0;
        }
        if (j < 0 || j >= 24) {
            return 0.0;
        }
        if (k < 0 || k >= 24) {
            return 0.0;
        }
        
        double weight = NOISE_WEIGHT_TABLE[k * 24 * 24 + i * 24 + j];
        
        return weight;
    }
    
    // From NoiseChunkGenerator
    private static double calculateNoiseWeight(int x, int y, int z) {
        double var1 = x * x + z * z;
        double var2 = y + 0.5;
        double var3 = var2 * var2;
        double var4 = Math.pow(2.718281828459045, -(var3 / 16.0 + var1 / 16.0));
        double var5 = -var2 * MathHelper.fastInverseSqrt(var3 / 2.0 + var1 / 2.0) / 2.0;
        return var5 * var4;
    }
    
    // Called only when generating structures
    @Override
    public int getHeight(int x, int z, Heightmap.Type type) {
	
    	BlockPos blockPos = new BlockPos(x, 0, z);
		ChunkPos chunkPos = new ChunkPos(blockPos);
    	
    	if (groundCacheY.get(blockPos) == null) {
    		biomeSource.fetchTempHumid(chunkPos.x * 16, chunkPos.z * 16, 16, 16);
    		sampleHeightmap(chunkPos);	
		}
    	
		int groundHeight = groundCacheY.get(blockPos);
		
		// Not ideal 
		if (type == Heightmap.Type.WORLD_SURFACE_WG && groundHeight < this.getSeaLevel()) 
		    groundHeight = this.getSeaLevel();
		
		return groundHeight;
    }
    
    private int[][] sampleHeightmap(ChunkPos chunkPos) {
    	byte byte2 = 2;
        // byte seaLevel = (byte)this.getSeaLevel();
        byte byte33 = 33;
        
        int int3_0 = byte2 + 1;
        int int3_1 = byte2 + 1;
        
    	heightmapCache = generateHeightmap(heightmapCache, chunkPos.x * byte2, 0, chunkPos.z * byte2, int3_0, byte33, int3_1);
    	
    	int[][] chunkY = new int[16][16];

		for(int i = 0; i < byte2; i++) {
            for(int j = 0; j < byte2; j++) { 
                for(int k = 0; k < 16; k++) { 
                    double quarter = 0.25D;
                    
                    double var1 = heightmapCache[((i + 0) * int3_1 + (j + 0)) * byte33 + (k + 0)];
                    double var2 = heightmapCache[((i + 0) * int3_1 + (j + 1)) * byte33 + (k + 0)];
                    double var3 = heightmapCache[((i + 1) * int3_1 + (j + 0)) * byte33 + (k + 0)];
                    double var4 = heightmapCache[((i + 1) * int3_1 + (j + 1)) * byte33 + (k + 0)];
                    
                    double var5 = (heightmapCache[((i + 0) * int3_1 + (j + 0)) * byte33 + (k + 1)] - var1) * quarter;
                    double var6 = (heightmapCache[((i + 0) * int3_1 + (j + 1)) * byte33 + (k + 1)] - var2) * quarter;
                    double var7 = (heightmapCache[((i + 1) * int3_1 + (j + 0)) * byte33 + (k + 1)] - var3) * quarter;
                    double var8 = (heightmapCache[((i + 1) * int3_1 + (j + 1)) * byte33 + (k + 1)] - var4) * quarter;
                    
                    for(int l = 0; l < 4; l++) { 
                        double eighth = 0.125D; 
                        double var10 = var1;
                        double var11 = var2;
                        double var12 = (var3 - var1) * eighth; // Lerp
                        double var13 = (var4 - var2) * eighth;
                        

                        for(int m = 0; m < 8; m++) { 
                            int x = (m + i * 8);
                            int y = k * 4 + l;
                            int z = (j * 8);
                            
                            double var14 = 0.125D;
                            double density = var10; // var15
                            double var16 = (var11 - var10) * var14; // More lerp
                            
                            
                            for(int n = 0; n < 8; n++) { 
                                if (density > 0.0)
                                {
                                    chunkY[x][z] = y;
                                }
                                
                                ++z;
                                density += var16;
                            }

                            var10 += var12;
                            var11 += var13;
                        }

                        var1 += var5;
                        var2 += var6;
                        var3 += var7;
                        var4 += var8;
                    }
                }
            }
        }
		
		for (int pX = 0; pX < chunkY.length; pX++)
		{
			for (int pZ = 0; pZ < chunkY[pX].length; pZ++)
			{
				BlockPos pos = new BlockPos(chunkPos.getStartX() + pX, 0, chunkPos.getStartZ() + pZ);
				groundCacheY.put(pos, chunkY[pX][pZ] + 1); // +1 because it is one above the ground
			}
		}
		
		return chunkY;
    }
    
    @Override
    public BlockPos locateStructure(ServerWorld world, StructureFeature<?> feature, BlockPos center, int radius, boolean skipExistingChunks) {
        if (feature.equals(StructureFeature.OCEAN_RUIN) || feature.equals(StructureFeature.SHIPWRECK) || feature.equals(StructureFeature.BURIED_TREASURE)) {
            return null;
        }
        
        return super.locateStructure(world, feature, center, radius, skipExistingChunks);
    }
    
    @Override
    public int getMaxY() {
        return 128;
    }
    
    @Override
    public int getSeaLevel() {
        return 64;
    }
    
    @Override
    public ChunkGenerator withSeed(long seed) {
    	return new SkylandsChunkGenerator(this.biomeSource.withSeed(seed), seed, this.settings);
    }
    
}
