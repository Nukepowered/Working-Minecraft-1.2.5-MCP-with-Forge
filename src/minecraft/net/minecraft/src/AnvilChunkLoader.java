package net.minecraft.src;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import net.minecraft.src.forge.ForgeHooks;

public class AnvilChunkLoader implements IThreadedFileIO, IChunkLoader
{
    private List field_48451_a = new ArrayList();
    private Set field_48449_b = new HashSet();
    private Object field_48450_c = new Object();

    /** Save directory for chunks using the Anvil format */
    private final File chunkSaveLocation;

    public AnvilChunkLoader(File par1File)
    {
        this.chunkSaveLocation = par1File;
    }

    /**
     * Loads the specified(XZ) chunk into the specified world.
     */
    public Chunk loadChunk(World par1World, int par2, int par3) throws IOException
    {
        NBTTagCompound var4 = null;
        ChunkCoordIntPair var5 = new ChunkCoordIntPair(par2, par3);
        Object var6 = this.field_48450_c;

        synchronized (this.field_48450_c)
        {
            if (this.field_48449_b.contains(var5))
            {
                for (int var7 = 0; var7 < this.field_48451_a.size(); ++var7)
                {
                    if (((AnvilChunkLoaderPending)this.field_48451_a.get(var7)).field_48427_a.equals(var5))
                    {
                        var4 = ((AnvilChunkLoaderPending)this.field_48451_a.get(var7)).field_48426_b;
                        break;
                    }
                }
            }
        }

        if (var4 == null)
        {
            DataInputStream var10 = RegionFileCache.getChunkInputStream(this.chunkSaveLocation, par2, par3);

            if (var10 == null)
            {
                return null;
            }

            var4 = CompressedStreamTools.read(var10);
        }

        return this.func_48443_a(par1World, par2, par3, var4);
    }

    protected Chunk func_48443_a(World par1World, int par2, int par3, NBTTagCompound par4NBTTagCompound)
    {
        if (!par4NBTTagCompound.hasKey("Level"))
        {
            System.out.println("Chunk file at " + par2 + "," + par3 + " is missing level data, skipping");
            return null;
        }
        else if (!par4NBTTagCompound.getCompoundTag("Level").hasKey("Sections"))
        {
            System.out.println("Chunk file at " + par2 + "," + par3 + " is missing block data, skipping");
            return null;
        }
        else
        {
            Chunk var5 = this.readChunkFromNBT(par1World, par4NBTTagCompound.getCompoundTag("Level"));

            if (!var5.isAtLocation(par2, par3))
            {
                System.out.println("Chunk file at " + par2 + "," + par3 + " is in the wrong location; relocating. (Expected " + par2 + ", " + par3 + ", got " + var5.xPosition + ", " + var5.zPosition + ")");
                par4NBTTagCompound.setInteger("xPos", par2);
                par4NBTTagCompound.setInteger("zPos", par3);
                var5 = this.readChunkFromNBT(par1World, par4NBTTagCompound.getCompoundTag("Level"));
            }

            var5.removeUnknownBlocks();
            ForgeHooks.onChunkLoadData(par1World, var5, par4NBTTagCompound);
            return var5;
        }
    }

    public void saveChunk(World par1World, Chunk par2Chunk) throws IOException
    {
        par1World.checkSessionLock();

        try
        {
            NBTTagCompound var3 = new NBTTagCompound();
            NBTTagCompound var4 = new NBTTagCompound();
            var3.setTag("Level", var4);
            this.writeChunkToNBT(par2Chunk, par1World, var4);
            this.func_48446_a(par2Chunk.getChunkCoordIntPair(), var3);
            ForgeHooks.onChunkSaveData(par1World, par2Chunk, var3);
        }
        catch (Exception var5)
        {
            var5.printStackTrace();
        }
    }

    protected void func_48446_a(ChunkCoordIntPair par1ChunkCoordIntPair, NBTTagCompound par2NBTTagCompound)
    {
        Object var3 = this.field_48450_c;

        synchronized (this.field_48450_c)
        {
            if (this.field_48449_b.contains(par1ChunkCoordIntPair))
            {
                for (int var4 = 0; var4 < this.field_48451_a.size(); ++var4)
                {
                    if (((AnvilChunkLoaderPending)this.field_48451_a.get(var4)).field_48427_a.equals(par1ChunkCoordIntPair))
                    {
                        this.field_48451_a.set(var4, new AnvilChunkLoaderPending(par1ChunkCoordIntPair, par2NBTTagCompound));
                        return;
                    }
                }
            }

            this.field_48451_a.add(new AnvilChunkLoaderPending(par1ChunkCoordIntPair, par2NBTTagCompound));
            this.field_48449_b.add(par1ChunkCoordIntPair);
            ThreadedFileIOBase.threadedIOInstance.queueIO(this);
        }
    }

    /**
     * Returns a boolean stating if the write was unsuccessful.
     */
    public boolean writeNextIO()
    {
        AnvilChunkLoaderPending var1 = null;
        Object var2 = this.field_48450_c;

        synchronized (this.field_48450_c)
        {
            if (this.field_48451_a.size() <= 0)
            {
                return false;
            }

            var1 = (AnvilChunkLoaderPending)this.field_48451_a.remove(0);
            this.field_48449_b.remove(var1.field_48427_a);
        }

        if (var1 != null)
        {
            try
            {
                this.func_48447_a(var1);
            }
            catch (Exception var4)
            {
                var4.printStackTrace();
            }
        }

        return true;
    }

    private void func_48447_a(AnvilChunkLoaderPending par1AnvilChunkLoaderPending) throws IOException
    {
        DataOutputStream var2 = RegionFileCache.getChunkOutputStream(this.chunkSaveLocation, par1AnvilChunkLoaderPending.field_48427_a.chunkXPos, par1AnvilChunkLoaderPending.field_48427_a.chunkZPosition);
        CompressedStreamTools.write(par1AnvilChunkLoaderPending.field_48426_b, var2);
        var2.close();
    }

    /**
     * Save extra data associated with this Chunk not normally saved during autosave, only during chunk unload.
     * Currently unused.
     */
    public void saveExtraChunkData(World par1World, Chunk par2Chunk) throws IOException {}

    /**
     * Called every World.tick()
     */
    public void chunkTick() {}

    /**
     * Save extra data not associated with any Chunk.  Not saved during autosave, only during world unload.  Currently
     * unused.
     */
    public void saveExtraData() {}

    /**
     * Writes the Chunk passed as an argument to the NBTTagCompound also passed, using the World argument to retrieve
     * the Chunk's last update time.
     */
    private void writeChunkToNBT(Chunk par1Chunk, World par2World, NBTTagCompound par3NBTTagCompound)
    {
        par2World.checkSessionLock();
        par3NBTTagCompound.setInteger("xPos", par1Chunk.xPosition);
        par3NBTTagCompound.setInteger("zPos", par1Chunk.zPosition);
        par3NBTTagCompound.setLong("LastUpdate", par2World.getWorldTime());
        par3NBTTagCompound.setIntArray("HeightMap", par1Chunk.heightMap);
        par3NBTTagCompound.setBoolean("TerrainPopulated", par1Chunk.isTerrainPopulated);
        ExtendedBlockStorage[] var4 = par1Chunk.getBlockStorageArray();
        NBTTagList var5 = new NBTTagList("Sections");
        ExtendedBlockStorage[] var6 = var4;
        int var7 = var4.length;
        NBTTagCompound var10;

        for (int var8 = 0; var8 < var7; ++var8)
        {
            ExtendedBlockStorage var9 = var6[var8];

            if (var9 != null && var9.func_48700_f() != 0)
            {
                var10 = new NBTTagCompound();
                var10.setByte("Y", (byte)(var9.getYLocation() >> 4 & 255));
                var10.setByteArray("Blocks", var9.func_48692_g());

                if (var9.getBlockMSBArray() != null)
                {
                    var10.setByteArray("Add", var9.getBlockMSBArray().data);
                }

                var10.setByteArray("Data", var9.func_48697_j().data);
                var10.setByteArray("SkyLight", var9.getSkylightArray().data);
                var10.setByteArray("BlockLight", var9.getBlocklightArray().data);
                var5.appendTag(var10);
            }
        }

        par3NBTTagCompound.setTag("Sections", var5);
        par3NBTTagCompound.setByteArray("Biomes", par1Chunk.getBiomeArray());
        par1Chunk.hasEntities = false;
        NBTTagList var15 = new NBTTagList();
        Iterator var17;

        for (var7 = 0; var7 < par1Chunk.entityLists.length; ++var7)
        {
            var17 = par1Chunk.entityLists[var7].iterator();

            while (var17.hasNext())
            {
                Entity var19 = (Entity)var17.next();
                par1Chunk.hasEntities = true;
                var10 = new NBTTagCompound();

                if (var19.addEntityID(var10))
                {
                    var15.appendTag(var10);
                }
            }
        }

        par3NBTTagCompound.setTag("Entities", var15);
        NBTTagList var16 = new NBTTagList();
        var17 = par1Chunk.chunkTileEntityMap.values().iterator();

        while (var17.hasNext())
        {
            TileEntity var20 = (TileEntity)var17.next();
            var10 = new NBTTagCompound();
            var20.writeToNBT(var10);
            var16.appendTag(var10);
        }

        par3NBTTagCompound.setTag("TileEntities", var16);
        List var18 = par2World.getPendingBlockUpdates(par1Chunk, false);

        if (var18 != null)
        {
            long var21 = par2World.getWorldTime();
            NBTTagList var11 = new NBTTagList();
            Iterator var12 = var18.iterator();

            while (var12.hasNext())
            {
                NextTickListEntry var13 = (NextTickListEntry)var12.next();
                NBTTagCompound var14 = new NBTTagCompound();
                var14.setInteger("i", var13.blockID);
                var14.setInteger("x", var13.xCoord);
                var14.setInteger("y", var13.yCoord);
                var14.setInteger("z", var13.zCoord);
                var14.setInteger("t", (int)(var13.scheduledTime - var21));
                var11.appendTag(var14);
            }

            par3NBTTagCompound.setTag("TileTicks", var11);
        }
    }

    /**
     * Reads the data stored in the passed NBTTagCompound and creates a Chunk with that data in the passed World.
     * Returns the created Chunk.
     */
    private Chunk readChunkFromNBT(World par1World, NBTTagCompound par2NBTTagCompound)
    {
        int var3 = par2NBTTagCompound.getInteger("xPos");
        int var4 = par2NBTTagCompound.getInteger("zPos");
        Chunk var5 = new Chunk(par1World, var3, var4);
        var5.heightMap = par2NBTTagCompound.getIntArray("HeightMap");
        var5.isTerrainPopulated = par2NBTTagCompound.getBoolean("TerrainPopulated");
        NBTTagList var6 = par2NBTTagCompound.getTagList("Sections");
        byte var7 = 16;
        ExtendedBlockStorage[] var8 = new ExtendedBlockStorage[var7];

        for (int var9 = 0; var9 < var6.tagCount(); ++var9)
        {
            NBTTagCompound var10 = (NBTTagCompound)var6.tagAt(var9);
            byte var11 = var10.getByte("Y");
            ExtendedBlockStorage var12 = new ExtendedBlockStorage(var11 << 4);
            var12.setBlockLSBArray(var10.getByteArray("Blocks"));

            if (var10.hasKey("Add"))
            {
                var12.setBlockMSBArray(new NibbleArray(var10.getByteArray("Add"), 4));
            }

            var12.setBlockMetadataArray(new NibbleArray(var10.getByteArray("Data"), 4));
            var12.setSkylightArray(new NibbleArray(var10.getByteArray("SkyLight"), 4));
            var12.setBlocklightArray(new NibbleArray(var10.getByteArray("BlockLight"), 4));
            var12.func_48708_d();
            var8[var11] = var12;
        }

        var5.setStorageArrays(var8);

        if (par2NBTTagCompound.hasKey("Biomes"))
        {
            var5.setBiomeArray(par2NBTTagCompound.getByteArray("Biomes"));
        }

        NBTTagList var14 = par2NBTTagCompound.getTagList("Entities");

        if (var14 != null)
        {
            for (int var15 = 0; var15 < var14.tagCount(); ++var15)
            {
                NBTTagCompound var17 = (NBTTagCompound)var14.tagAt(var15);
                Entity var19 = EntityList.createEntityFromNBT(var17, par1World);
                var5.hasEntities = true;

                if (var19 != null)
                {
                    var5.addEntity(var19);
                }
            }
        }

        NBTTagList var16 = par2NBTTagCompound.getTagList("TileEntities");

        if (var16 != null)
        {
            for (int var18 = 0; var18 < var16.tagCount(); ++var18)
            {
                NBTTagCompound var21 = (NBTTagCompound)var16.tagAt(var18);
                TileEntity var13 = TileEntity.createAndLoadEntity(var21);

                if (var13 != null)
                {
                    var5.addTileEntity(var13);
                }
            }
        }

        if (par2NBTTagCompound.hasKey("TileTicks"))
        {
            NBTTagList var20 = par2NBTTagCompound.getTagList("TileTicks");

            if (var20 != null)
            {
                for (int var22 = 0; var22 < var20.tagCount(); ++var22)
                {
                    NBTTagCompound var23 = (NBTTagCompound)var20.tagAt(var22);
                    par1World.scheduleBlockUpdateFromLoad(var23.getInteger("x"), var23.getInteger("y"), var23.getInteger("z"), var23.getInteger("i"), var23.getInteger("t"));
                }
            }
        }

        return var5;
    }
}
