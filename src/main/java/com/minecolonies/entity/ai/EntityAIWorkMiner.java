package com.minecolonies.entity.ai;

import com.minecolonies.colony.buildings.BuildingMiner;
import com.minecolonies.colony.jobs.JobMiner;
import com.minecolonies.entity.EntityCitizen;
import com.minecolonies.util.ChunkCoordUtils;
import com.minecolonies.util.InventoryUtils;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChunkCoordinates;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.ArrayList;
import java.util.List;

/**
 * Miner AI class
 * Created: December 20, 2014
 *
 * @author Colton, Raycoms
 */

public class EntityAIWorkMiner extends EntityAIWork<JobMiner>
{
    private static Logger logger = LogManager.getLogger("Miner");
    private static final int MAXIMUM_LEVEL = 4;

    public enum Stage
    {
        INSUFFICIENT_TOOLS,
        INSUFFICIENT_BLOCKS,
        INVENTORY_FULL,
        SEARCHING_LADDER,
        MINING_VEIN,
        MINING_SHAFT,
        WORKING
    }

    private int delay = 0;                   //Must be saved here

    private ChunkCoordinates ladderLocation; //Can be saved here for now
    private ChunkCoordinates cobbleLocation; //Can be saved here for now

    private boolean foundLadder = false;     //Can be saved here for now

    private int vektorx = 1;
    private int vektorz = 1;

    private int baseSpeed = 1;

    private int currentY=200;                //Can be saved here
    private int clear = 1;                   //Can be saved here for now

    private Block needBlock = Blocks.dirt;
    private Item needItem = Items.coal;

    //TODO Diggable by Tool
    //TODO Calculate DelayTime by Blockhardness and ToolSpeed
    //TODO Gather ores himself

    //Node Position if Node is close to x+5 or z+5 to other Node delete them Both


    public EntityAIWorkMiner(JobMiner job)
    {
        super(job);

    }

    @Override
    public boolean shouldExecute()
    {
        return super.shouldExecute();
    }

    @Override
    public void startExecuting()
    {
        /*if (!Configurations.builderInfiniteResources)
        {
            //requestMaterials();
        }*/

        worker.setStatus(EntityCitizen.Status.WORKING);
        updateTask();
    }

    @Override
    public void updateTask()
    {
        if (delay > 0)
        {
            delay--;
        }
        else
        {
            switch (job.getStage()) {
                case INSUFFICIENT_TOOLS:
                    if (ChunkCoordUtils.isWorkerAtSiteWithMove(worker, worker.getWorkBuilding().getLocation()))//Go Home
                    {
                        logger.info("Need tools");
                        delay = 30;
                        isInHut(needItem);

                        if (hasAllTheTools())
                        {
                            job.setStage(Stage.WORKING);
                        }
                    }
                    break;
                case INSUFFICIENT_BLOCKS:
                    if (ChunkCoordUtils.isWorkerAtSiteWithMove(worker, worker.getWorkBuilding().getLocation()))//Go Home
                    {
                        logger.info("Need" + needBlock.toString() + needItem.toString());
                        delay = 30;
                        isInHut(needBlock);
                        isInHut(needItem);

                        boolean hasBlock = inventoryContains(needBlock) != -1;
                        boolean hasItem = inventoryContains(needItem)!=-1;

                       if (hasBlock && hasItem)
                        {
                            job.setStage(Stage.WORKING);
                        }
                    }
                    break;
                case INVENTORY_FULL:
                    dumpInventory();
                    break;
                case SEARCHING_LADDER:
                    findLadder();
                    break;
                case MINING_VEIN:
                    mineVein();
                    break;
                case MINING_SHAFT:
                    createShaft(vektorx, vektorz);
                    break;
                case WORKING:
                    BuildingMiner b = (BuildingMiner) worker.getWorkBuilding();
                    if (b == null) return;

                    if (!foundLadder)
                    {
                        job.setStage(Stage.SEARCHING_LADDER);
                    }
                    else if (!b.cleared)
                    {
                        job.setStage(Stage.MINING_SHAFT);
                    }
                    break;
            }
        }
        /*
        Rough outline:
            Data structures:
                Nodes are 5x5x4 (3 tall + a ceiling)
                connections are 3x5x3 tunnels
            Connections should be automatically completed when moving from node to node

            max Level depth depends on current hut startingLevel
                example:
                    1: y=44
                    2: y=28
                    3: y=10
                Personally I think our lowest Level should be 4 or 5, whatever one you can't run into bedrock on

            If the miner has a node, then he should create the connection, then mine the node

            else findNewNode

            That's basically it...
            Also note we need to check the tool durability and for torches,
                wood for building the tunnel structure (exact plan to be determined)

            You also may want to create another node status before AVAILABLE for when the connection isn't completed.
                Maybe even two status, one for can_connect_too then connection_in_progress
         */
    }
    private boolean isStackTool(ItemStack stack)
    {
        return stack != null && (stack.getItem().getToolClasses(null /* not used */).contains("pickaxe") || stack.getItem().getToolClasses(null /* not used */).contains("shovel"));
    }

    private void isInHut(Block block)
    {
        if(worker.getWorkBuilding().getTileEntity()==null)
        {
            job.setStage(Stage.INSUFFICIENT_BLOCKS);
            needBlock = block;
            return;
        }

        int size = worker.getWorkBuilding().getTileEntity().getSizeInventory();

        for(int i = 0; i < size; i++)
        {
            ItemStack stack = worker.getWorkBuilding().getTileEntity().getStackInSlot(i);
            if(stack != null && stack.getItem() instanceof ItemBlock)
            {
                    Block content = ((ItemBlock) stack.getItem()).field_150939_a;
                    if(content.equals(block))
                    {
                        ItemStack returnStack = InventoryUtils.setStack(worker.getInventory(), stack);

                        if (returnStack == null)
                        {
                            worker.getWorkBuilding().getTileEntity().decrStackSize(i, stack.stackSize);
                        }
                        else
                        {
                            worker.getWorkBuilding().getTileEntity().decrStackSize(i, stack.stackSize - returnStack.stackSize);
                        }
                    }
            }
        }
    }

    private void isInHut(Item item)
    {
        if(worker.getWorkBuilding().getTileEntity()==null)
        {
            job.setStage(Stage.INSUFFICIENT_BLOCKS);
            needItem = item;
            return;
        }

        int size = worker.getWorkBuilding().getTileEntity().getSizeInventory();

        for(int i = 0; i < size; i++)
        {
            ItemStack stack = worker.getWorkBuilding().getTileEntity().getStackInSlot(i);
            if(stack != null)
            {
                Item content = stack.getItem();
                if(content.equals(item))
                {
                    ItemStack returnStack = InventoryUtils.setStack(worker.getInventory(), stack);

                    if (returnStack == null)
                    {
                        worker.getWorkBuilding().getTileEntity().decrStackSize(i, stack.stackSize);
                    }
                    else
                    {
                        worker.getWorkBuilding().getTileEntity().decrStackSize(i, stack.stackSize - returnStack.stackSize);
                    }
                }
            }
        }
    }


    private void dumpInventory()
    {
        if (ChunkCoordUtils.isWorkerAtSiteWithMove(worker, worker.getWorkBuilding().getLocation()))
        {
            for (int i = 0; i < worker.getInventory().getSizeInventory(); i++)
            {
                ItemStack stack = worker.getInventory().getStackInSlot(i);
                if (stack != null && !isStackTool(stack))
                {
                    if (worker.getWorkBuilding().getTileEntity() != null)
                    {
                        ItemStack returnStack = InventoryUtils.setStack(worker.getWorkBuilding().getTileEntity(), stack);
                        if (returnStack == null)
                        {
                            worker.getInventory().decrStackSize(i, stack.stackSize);
                        }
                        else
                        {
                            worker.getInventory().decrStackSize(i, stack.stackSize - returnStack.stackSize);
                        }
                    }
                }

            }
            job.setStage(Stage.WORKING);
        }
    }

    private void mineVein()
    {
        BuildingMiner b = (BuildingMiner)worker.getWorkBuilding();
        if (b == null) return;


        if(job.vein.size() == 0)
        {
            job.vein = null;
            job.veinId = 0;
            job.setStage(Stage.WORKING);
        }
        else
        {
            ChunkCoordinates nextLoc = job.vein.get(0);
            Block block = ChunkCoordUtils.getBlock(world, nextLoc);
            int x = nextLoc.posX;
            int y = nextLoc.posY;
            int z = nextLoc.posZ;
            doMining(block, nextLoc.posX, nextLoc.posY, nextLoc.posZ);
            job.vein.remove(0);

            if (world.isAirBlock(x, y - 1, z) || !canWalkOn(x, y - 1, z))
            {
                world.setBlock(x, y - 1, z, Blocks.dirt);
                int slot = inventoryContains(Blocks.dirt);
                worker.getInventory().decrStackSize(slot, 1);
            }
            avoidCloseLiquid(x,y,z);

        }



    }

    private void avoidCloseLiquid(int x, int y, int z)
    {
        for (int x1 = x - 3; x1 <= x + 3; x1++)
        {
            for (int z1 = z - 3; z1 <= z + 3; z1++)
            {
                for (int y1 = y ; y1 <= y + 2; y1++)
                {
                    Block block = world.getBlock(x1,y1,z1);
                    if (block == Blocks.lava || block == Blocks.flowing_lava || block == Blocks.water || block == Blocks.flowing_water)//Add Mod Liquids
                    {
                        world.setBlock(x, y - 1, z, Blocks.dirt);
                        int slot = inventoryContains(Blocks.dirt);
                        worker.getInventory().decrStackSize(slot, 1);
                    }
                }
            }
        }

    }


    private boolean hasAllTheTools()
    {
        boolean hasPickAxeInHand;
        boolean hasSpadeInHand;

        if (worker.getHeldItem() == null)
        {
            hasPickAxeInHand = false;
            hasSpadeInHand = false;
        }
        else
        {
            hasPickAxeInHand = worker.getHeldItem().getItem().getToolClasses(null /* not used */).contains("pickaxe");
            hasSpadeInHand = worker.getHeldItem().getItem().getToolClasses(null /* not used */).contains("shovel");
        }

        int hasSpade = InventoryUtils.getFirstSlotContainingTool(worker.getInventory(), "shovel");
        int hasPickAxe = InventoryUtils.getFirstSlotContainingTool(worker.getInventory(), "pickaxe");

        boolean Spade = hasSpade > 0 || hasSpadeInHand;
        boolean Pickaxe = hasPickAxeInHand || hasPickAxe > 0;

        if(!Spade)
        {
            needItem = Items.iron_shovel;
        }
        else if (!Pickaxe)
        {
            needItem = Items.iron_pickaxe;
        }

        return Spade && Pickaxe;
    }

    void holdShovel()
    {
        worker.setHeldItem(InventoryUtils.getFirstSlotContainingTool(worker.getInventory(), "shovel"));
    }

    void holdPickAxe()
    {
        worker.setHeldItem(InventoryUtils.getFirstSlotContainingTool(worker.getInventory(), "pickaxe"));
    }

    @Override
    public boolean continueExecuting()
    {
        return super.continueExecuting();
    }

    @Override
    public void resetTask()
    {
        super.resetTask();
    }

    private void findLadder()
    {
        BuildingMiner b = (BuildingMiner)worker.getWorkBuilding();
        if (b == null) return;

        int posX = worker.getWorkBuilding().getLocation().posX;
        int posY = worker.getWorkBuilding().getLocation().posY + 2;
        int posZ = worker.getWorkBuilding().getLocation().posZ;
        int posYWorker = posY;

        for (int x = posX - 10; x < posX + 15; x++)
        {
            for (int z = posZ - 10; z < posZ + 15; z++)
            {
                for (int y = posY - 10; y < posY; y++)
                {
                    if (foundLadder)
                        return;

                    if (world.getBlock(x, y, z).equals(Blocks.ladder))//Parameters unused
                    {
                        if (ladderLocation == null)
                        {
                            int lastY = getLastLadder(x, y, z);
                            ladderLocation = new ChunkCoordinates(x, lastY, z);
                            posYWorker = lastY;
                            logger.info("Found ladder at x:" + x + " y: " + lastY + " z: " + z);
                        }

                        if (ChunkCoordUtils.isWorkerAtSiteWithMove(worker, ladderLocation))
                        {
                            cobbleLocation = new ChunkCoordinates(x, posYWorker, z);

                            //Cobble on x+1 x-1 z+1 or z-1 to the ladder
                            if (world.getBlock(ladderLocation.posX - 1, ladderLocation.posY, ladderLocation.posZ).equals(Blocks.cobblestone))//Parameters unused
                            {
                                cobbleLocation = new ChunkCoordinates(x - 1, posYWorker, z);
                                vektorx = 1;
                                vektorz = 0;
                                logger.info("Found cobble - West");
                                //West

                            }
                            else if (world.getBlock(ladderLocation.posX + 1, ladderLocation.posY, ladderLocation.posZ).equals(Blocks.cobblestone))//Parameters unused
                            {
                                cobbleLocation = new ChunkCoordinates(x + 1, posYWorker, z);
                                vektorx = -1;
                                vektorz = 0;
                                logger.info("Found cobble - East");
                                //East

                            }
                            else if (world.getBlock(ladderLocation.posX,ladderLocation.posY, ladderLocation.posZ - 1).equals(Blocks.cobblestone))//Parameters unused
                            {
                                cobbleLocation = new ChunkCoordinates(x, posYWorker, z - 1);
                                vektorz = 1;
                                vektorx = 0;
                                logger.info("Found cobble - South");
                                //South

                            }
                            else if (world.getBlock(ladderLocation.posX, ladderLocation.posY, ladderLocation.posZ + 1).equals(Blocks.cobblestone))//Parameters unused
                            {
                                cobbleLocation = new ChunkCoordinates(x, posYWorker, z + 1);
                                vektorz = -1;
                                vektorx = 0;
                                logger.info("Found cobble - North");
                                //North
                            }
                            //world.setBlockToAir(ladderLocation.posX, ladderLocation.posY - 1, ladderLocation.posZ);
                            b.getLocation = new ChunkCoordinates(ladderLocation.posX, ladderLocation.posY - 1, ladderLocation.posZ);
                            foundLadder = true;
                            job.setStage(Stage.WORKING);
                        }
                    }
                }
            }
        }
    }

    private void createShaft(int vektorX, int vektorZ)
    {
        BuildingMiner b = (BuildingMiner)worker.getWorkBuilding();
        if (b == null) return;
        //TODO first found ore in wall not replaced by cobblestone
        //TODO If above ore is fallable material, replace by cobble
        if(b.cleared) {job.setStage(Stage.WORKING); return;}


        int x = b.getLocation.posX;
        int y = b.getLocation.posY;
        int z = b.getLocation.posZ;
        currentY = b.getLocation.posY;

        if (InventoryUtils.getOpenSlot(worker.getInventory()) == -1)//inventory has an open slot - this doesn't account for slots with non full stacks
        {                                                   //also we still may have problems if the block drops multiple items
            job.setStage(Stage.INVENTORY_FULL);
            return;
        }
        if(ladderLocation == null)
        {
            job.setStage(Stage.SEARCHING_LADDER);
        }


            //Needs 39+25 Planks + 4 Torches + 14 fence 5

            if (b.startingLevel % 5 == 0 && b.startingLevel != 0)
            {
                if (inventoryContainsMany(b.floorBlock) >= 64 && inventoryContains(Items.coal)!=-1)
                {



                    if (clear < 50)
                    {
                        if (clear == 1)
                        {
                            // Do nothing
                        }
                        else if (clear == 2)
                        {
                            world.setBlock(x, y + 3, z, b.floorBlock);
                            world.setBlock(x, y + 4, z, b.fenceBlock);
                        }
                        else if (clear <= 5)
                        {
                            // Do nothing
                        }
                        else if (clear <= 7)
                        {
                            if (clear == 6)
                            {
                                world.setBlock(x, y + 4, z, b.fenceBlock);
                            }

                            world.setBlock(x, y + 3, z, b.floorBlock);
                        }
                        else if (clear <= 14)
                        {
                            if (clear == 8)
                            {
                                if (vektorX == 0)
                                {
                                    x += 1;
                                    z -= 7;

                                }
                                else if (vektorZ == 0)
                                {
                                    z += 1;
                                    x -= 7;
                                }
                                world.setBlock(x, y + 3, z, b.floorBlock);

                            }
                            else if (clear == 9)
                            {
                                world.setBlock(x, y + 3, z, b.floorBlock);
                                world.setBlock(x, y + 4, z, b.fenceBlock);
                            }
                            else if (clear > 12)
                            {
                                if (clear == 13)
                                {
                                    world.setBlock(x, y + 4, z, b.fenceBlock);
                                }

                                world.setBlock(x, y + 3, z, b.floorBlock);
                            }

                        }
                        else if (clear <= 21)
                        {
                            if (clear == 15)
                            {
                                if (vektorX == 0)
                                {
                                    x += 1;
                                    z -= 7;
                                } else if (vektorZ == 0) {
                                    z += 1;
                                    x -= 7;
                                }

                            }
                            if (clear > 15 && clear < 21)
                            {
                                world.setBlock(x, y + 4, z, b.fenceBlock);

                                if (clear == 16 || clear == 20)
                                {
                                    world.setBlock(x, y + 5, z, Blocks.torch);
                                }
                            }

                            world.setBlock(x, y + 3, z, b.floorBlock);

                        }
                        else if (clear <= 28)
                        {
                            if (clear == 22)
                            {
                                if (vektorX == 0)
                                {
                                    x += 1;
                                    z -= 7;
                                }
                                else if (vektorZ == 0)
                                {
                                    z += 1;
                                    x -= 7;
                                }
                            }
                            world.setBlock(x, y + 3, z, b.floorBlock);

                        }
                        else if (clear <= 35)
                        {
                            if (clear == 29)
                            {
                                if (vektorX == 0)
                                {
                                    x = x - 4;
                                    z -= 7;
                                }
                                else if (vektorZ == 0)
                                {
                                    z = z - 4;
                                    x -= 7;
                                }
                                world.setBlock(x, y + 3, z, b.floorBlock);

                            }
                            else if (clear == 30) {

                                world.setBlock(x, y + 3, z, b.floorBlock);
                                world.setBlock(x, y + 4, z, b.fenceBlock);
                            }
                            else if (clear > 33)
                            {
                                if (clear == 34)
                                {
                                    world.setBlock(x, y + 4, z, b.fenceBlock);
                                }

                                world.setBlock(x, y + 3, z, b.floorBlock);
                            }
                        } else if (clear <= 42)
                        {
                            if (clear == 36)
                            {
                                if (vektorX == 0)
                                {
                                    x -= 1;
                                    z -= 7;
                                }
                                else if (vektorZ == 0)
                                {
                                    z -= 1;
                                    x -= 7;
                                }
                            }
                            if (clear > 36 && clear < 42)
                            {
                                world.setBlock(x, y + 4, z, b.fenceBlock);
                                if (clear == 37 || clear == 41)
                                {
                                    world.setBlock(x, y + 5, z, Blocks.torch);
                                }
                            }

                            world.setBlock(x, y + 3, z, b.floorBlock);
                        }
                        else if (clear <= 49)
                        {
                            if (clear == 43)
                            {
                                if (vektorX == 0)
                                {
                                    x -= 1;
                                    z -= 7;
                                } else if (vektorZ == 0) {
                                    z -= 1;
                                    x -= 7;
                                }
                            }
                            world.setBlock(x, y + 3, z, b.floorBlock);
                        }

                        x = x + vektorX;
                        z = z + vektorZ;

                        b.getLocation.set(x, y, z);
                        clear += 1;
                    }
                    else if (ChunkCoordUtils.isWorkerAtSiteWithMove(worker, ladderLocation))
                    {
                        int neededPlanks = 64;

                        while(neededPlanks > 0)
                        {
                            int slot = inventoryContains(b.floorBlock);
                            int size = worker.getInventory().getStackInSlot(slot).stackSize;
                            worker.getInventory().decrStackSize(slot,size);
                            neededPlanks -= size;
                        }

                        int slot = inventoryContains(Items.coal);
                        worker.getInventory().decrStackSize(slot,1);
                        //Save Node
                        //(x-4, y+1, z) (x, y+1, Z+4) and (x+4, y+1, z)
                        b.levels = new ArrayList<Level>();
                        if (vektorX == 0)
                        {
                            b.levels.add(new Level(ladderLocation.posX,y + 3,ladderLocation.posZ+3));//FIXME nothing is done with this

                        }
                        else if (vektorZ == 0)
                        {
                            b.levels.add(new Level(ladderLocation.posX+3,y + 3,ladderLocation.posZ));//FIXME nothing is done with this

                        }
                        clear = 1;
                        b.startingLevel++;
                        b.getLocation.set(ladderLocation.posX, ladderLocation.posY - 1, ladderLocation.posZ);
                    }
                }
                else
                {
                    job.setStage(Stage.INSUFFICIENT_BLOCKS);

                    if(inventoryContainsMany(b.floorBlock)>= 64)
                    {
                        needItem= Items.coal;
                    }
                    else
                    {
                        needBlock = b.floorBlock;
                    }
                }

            }
        else if(inventoryContains(Blocks.dirt)!=-1 && inventoryContains(Blocks.cobblestone)!=-1)
        {
            if (clear >= 50)
            {
                if (ChunkCoordUtils.isWorkerAtSiteWithMove(worker, ladderLocation))
                {
                    int meta = world.getBlockMetadata(ladderLocation.posX, ladderLocation.posY, ladderLocation.posZ);
                    cobbleLocation.set(cobbleLocation.posX, ladderLocation.posY - 1, cobbleLocation.posZ);
                    ladderLocation.set(ladderLocation.posX, ladderLocation.posY - 1, ladderLocation.posZ);
                    world.setBlock(cobbleLocation.posX, cobbleLocation.posY, cobbleLocation.posZ, Blocks.cobblestone);
                    world.setBlock(ladderLocation.posX, ladderLocation.posY, ladderLocation.posZ, Blocks.ladder, meta, 0x3);

                    int slot = inventoryContains(Blocks.cobblestone);
                    worker.getInventory().decrStackSize(slot,1);

                    if (y <= MAXIMUM_LEVEL)
                    {
                        b.cleared = true;
                        //If level = +/- long ago, build on y or -1
                    }
                    clear = 1;
                    b.startingLevel++;
                    b.getLocation.set(ladderLocation.posX, ladderLocation.posY - 1, ladderLocation.posZ);
                }
            }
            else if (ChunkCoordUtils.isWorkerAtSiteWithMove(worker, new ChunkCoordinates(x, y, z)))
            {

                worker.getLookHelper().setLookPosition(x, y, z, 90f, worker.getVerticalFaceSpeed());

                //if (!world.getBlock(x,y,z).isAir(world,x,y,z))
                //if (inventoryContains(Blocks.dirt) && inventoryContains(Blocks.cobblestone))
                {
                    if (!world.getBlock(x, y, z).isAir(world, x, y, z))
                    {

                        doMining(world.getBlock(x, y, z), x, y, z);


                        logger.info("Mined at " + x + " " + y + " " + z);
                    }
                    if (clear < 50)
                    {
                        //Check if Block after End is empty (Block of Dungeons...)
                        if ((clear - 1) % 7 == 0)
                        {

                            if (world.isAirBlock(x - vektorX, y, z - vektorZ) || !canWalkOn(x - vektorX, y, z - vektorZ))
                            {
                                world.setBlock(x - vektorX, y, z - vektorZ, Blocks.cobblestone);
                                int slot = inventoryContains(Blocks.cobblestone);
                                worker.getInventory().decrStackSize(slot,1);
                            }
                            else if (isValuable(x - vektorX, y, z - vektorZ))
                            {
                                job.vein = new ArrayList<ChunkCoordinates>();
                                job.vein.add(new ChunkCoordinates(x - vektorX, y, z - vektorZ));
                                logger.info("Found ore");

                                findVein(x - vektorX, y, z - vektorZ);
                                logger.info("finished finding ores: " + job.vein.size());

                            }
                        }
                        else if (clear % 7 == 0)
                        {
                            if (world.isAirBlock(x + vektorX, y, z + vektorZ) || !canWalkOn(x + vektorX, y, z + vektorZ))
                            {
                                world.setBlock(x + vektorX, y, z + vektorZ, Blocks.cobblestone);
                                int slot = inventoryContains(Blocks.cobblestone);
                                worker.getInventory().decrStackSize(slot,1);
                            }
                            else if (isValuable(x + vektorX, y, z + vektorZ))
                            {
                                job.vein = new ArrayList<ChunkCoordinates>();
                                job.vein.add(new ChunkCoordinates(x + vektorX, y, z + vektorZ));
                                logger.info("Found ore");
                                findVein(x + vektorX, y, z + vektorZ);
                                logger.info("finished finding ores: " + job.vein.size());

                            }
                        }

                        if (clear < 7)
                        {
                            //Do Nothing
                        }
                        else if (clear < 14)
                        {
                            if (clear == 7)
                            {
                                if (vektorX == 0)
                                {
                                    x += 1;
                                    z -= 7;
                                }
                                else if (vektorZ == 0)
                                {
                                    z += 1;
                                    x -= 7;
                                }
                            }
                        }
                        else if (clear < 21)
                        {
                            if (clear == 14)
                            {
                                if (vektorX == 0)
                                {
                                    x += 1;
                                    z -= 7;
                                } else if (vektorZ == 0)
                                {
                                    z += 1;
                                    x -= 7;
                                }
                            }
                        }
                        else if (clear < 28)
                        {
                            if (clear == 21)
                            {
                                if (vektorX == 0)
                                {
                                    x += 1;
                                    z -= 7;
                                } else if (vektorZ == 0)
                                {
                                    z += 1;
                                    x -= 7;
                                }
                            }
                            if (vektorX == 0)
                            {

                                if (isValuable(x + 1, y, z))
                                {
                                    job.vein = new ArrayList<ChunkCoordinates>();
                                    job.vein.add(new ChunkCoordinates(x + 1, y, z));
                                    logger.info("Found ore");

                                    findVein(x + 1, y, z);
                                    logger.info("finished finding ores: " + job.vein.size());

                                }
                                if (world.isAirBlock(x + 1, y, z) || !canWalkOn(x + 1, y, z))
                                {
                                    world.setBlock(x + 1, y, z, Blocks.cobblestone);
                                    int slot = inventoryContains(Blocks.cobblestone);
                                    worker.getInventory().decrStackSize(slot,1);
                                }
                            } else if (vektorZ == 0)
                            {

                                if (isValuable(x, y, z + 1))
                                {
                                    job.vein = new ArrayList<ChunkCoordinates>();
                                    job.vein.add(new ChunkCoordinates(x, y, z + 1));
                                    logger.info("Found ore");

                                    findVein(x, y, z + 1);
                                    logger.info("finished finding ores: " + job.vein.size());

                                }
                                if (world.isAirBlock(x, y, z + 1) || !canWalkOn(x, y, z + 1))
                                {
                                    world.setBlock(x, y, z + 1, Blocks.cobblestone);
                                    int slot = inventoryContains(Blocks.cobblestone);
                                    worker.getInventory().decrStackSize(slot,1);
                                }
                            }
                        }
                        else if (clear < 35)
                        {
                            if (clear == 28)
                            {
                                if (vektorX == 0)
                                {

                                    if (isValuable(x + 1, y, z))
                                    {
                                        job.vein = new ArrayList<ChunkCoordinates>();
                                        job.vein.add(new ChunkCoordinates(x + 1, y, z));
                                        logger.info("Found ore");

                                        findVein(x + 1, y, z);
                                        logger.info("finished finding ores: " + job.vein.size());

                                    }
                                    if (world.isAirBlock(x + 1, y, z) || !canWalkOn(x + 1, y, z))
                                    {
                                        world.setBlock(x + 1, y, z, Blocks.cobblestone);
                                        int slot = inventoryContains(Blocks.cobblestone);
                                        worker.getInventory().decrStackSize(slot,1);
                                    }
                                }
                                else if (vektorZ == 0)
                                {

                                    if (isValuable(x, y, z + 1))
                                    {
                                        job.vein = new ArrayList<ChunkCoordinates>();
                                        job.vein.add(new ChunkCoordinates(x, y, z + 1));
                                        logger.info("Found ore");

                                        findVein(x, y, z + 1);
                                        logger.info("finished finding ores: " + job.vein.size());

                                    }
                                    if (world.isAirBlock(x, y, z + 1) || !canWalkOn(x, y, z + 1))
                                    {
                                        world.setBlock(x, y, z + 1, Blocks.cobblestone);
                                        int slot = inventoryContains(Blocks.cobblestone);
                                        worker.getInventory().decrStackSize(slot,1);
                                    }
                                }
                                if (vektorX == 0)
                                {
                                    x = x - 4;
                                    z -= 7;
                                }
                                else if (vektorZ == 0)
                                {
                                    z = z - 4;
                                    x -= 7;
                                }
                            }
                        } else if (clear < 42)
                        {
                            if (clear == 35)
                            {
                                if (vektorX == 0)
                                {
                                    x -= 1;
                                    z -= 7;
                                } else if (vektorZ == 0)
                                {
                                    z -= 1;
                                    x -= 7;
                                }
                            }
                        }
                        else if (clear <= 49)
                        {
                            if (clear == 42)
                            {
                                if (vektorX == 0)
                                {
                                    x -= 1;
                                    z -= 7;
                                }
                                else if (vektorZ == 0)
                                {
                                    z -= 1;
                                    x -= 7;
                                }
                            }
                            if (vektorX == 0)
                            {

                                if (isValuable(x - 1, y, z))
                                {
                                    job.vein = new ArrayList<ChunkCoordinates>();
                                    job.vein.add(new ChunkCoordinates(x - 1, y, z));
                                    logger.info("Found ore");

                                    findVein(x - 1, y, z);
                                    logger.info("finished finding ores: " + job.vein.size());

                                }
                                if (world.isAirBlock(x - 1, y, z) || !canWalkOn(x - 1, y, z))
                                {
                                    world.setBlock(x - 1, y, z, Blocks.cobblestone);
                                    int slot = inventoryContains(Blocks.cobblestone);
                                    worker.getInventory().decrStackSize(slot,1);
                                }
                            } else if (vektorZ == 0)
                            {

                                if (isValuable(x, y, z - 1))
                                {
                                    job.vein = new ArrayList<ChunkCoordinates>();
                                    job.vein.add(new ChunkCoordinates(x, y, z - 1));
                                    logger.info("Found ore");

                                    findVein(x, y, z - 1);
                                    logger.info("finished finding ores: " + job.vein.size());

                                }
                                if (world.isAirBlock(x, y, z - 1) || !canWalkOn(x, y, z - 1))
                                {
                                    world.setBlock(x, y, z - 1, Blocks.cobblestone);
                                    int slot = inventoryContains(Blocks.cobblestone);
                                    worker.getInventory().decrStackSize(slot,1);
                                }
                            }
                        }

                        x = x + vektorX;
                        z = z + vektorZ;

                        if (world.isAirBlock(x, y - 1, z) || !canWalkOn(x, y - 1, z))
                        {
                            world.setBlock(x, y - 1, z, Blocks.dirt);
                            int slot = inventoryContains(Blocks.dirt);
                            worker.getInventory().decrStackSize(slot,1);
                        }

                        b.getLocation.set(x, y, z);
                        clear += 1;
                    }
                }
            }
        }
    }

    private int getDelay(Block block)
    {
        return baseSpeed  * ((int) worker.getHeldItem().getItem().getDigSpeed(worker.getHeldItem(), block, 0));

    }



    private int inventoryContains(Block block)
    {
        for (int slot = 0; slot < worker.getInventory().getSizeInventory(); slot++)
        {
            ItemStack stack = worker.getInventory().getStackInSlot(slot);

            if (stack != null && stack.getItem() instanceof ItemBlock)
            {
                Block content = ((ItemBlock) stack.getItem()).field_150939_a;
                if(content.equals(block))
                {
                    return slot;
                }
            }
        }

        job.setStage(Stage.INSUFFICIENT_BLOCKS);
        needBlock = block;
        return -1;

    }

    private int inventoryContains(Item item)
    {
        for (int slot = 0; slot < worker.getInventory().getSizeInventory(); slot++)
        {
            ItemStack stack = worker.getInventory().getStackInSlot(slot);

            if (stack != null && stack.getItem() instanceof Item)
            {
                Item content = stack.getItem();
                if(content.equals(item))
                {
                    return slot;
                }
            }
        }

        job.setStage(Stage.INSUFFICIENT_BLOCKS);
        needItem = item;
        return -1;

    }

    private int inventoryContainsMany(Block block)
    {
        int count = 0;

        for (int slot = 0; slot < worker.getInventory().getSizeInventory(); slot++)
        {
            ItemStack stack = worker.getInventory().getStackInSlot(slot);

            if (stack != null && stack.getItem() instanceof ItemBlock)
            {
                Block content = ((ItemBlock) stack.getItem()).field_150939_a;
                if(content.equals(block))
                {
                    count += stack.stackSize;
                }
            }
        }
        return count;
    }


    private void doMining(Block block, int x, int y, int z)
    {
        delay = getDelay(block);
        ChunkCoordinates bk = new ChunkCoordinates(x,y,z);
        List<ItemStack> items = ChunkCoordUtils.getBlockDrops(world, bk, 0);//0 is fortune level, it doesn't matter
        for (ItemStack item : items)
        {
            InventoryUtils.setStack(worker.getInventory(), item);
        }

        if(currentY == 200)
        {
            currentY = y;
        }


        if (block == Blocks.dirt || block == Blocks.gravel || block == Blocks.sand || block == Blocks.clay || block == Blocks.grass)
        {
            holdShovel();
        }
        else
        {
            holdPickAxe();
        }

        if (world.isAirBlock(x, y - 1, z) || !canWalkOn(x, y - 1, z))
        {
            world.setBlock(x, y - 1, z, Blocks.dirt);
            int slot = inventoryContains(Blocks.dirt);
            worker.getInventory().decrStackSize(slot,1);
        }


        world.playSoundEffect(
                (float) x + 0.5F,
                (float) y + 0.5F,
                (float) z + 0.5F,
                block.stepSound.getBreakSound(), (block.stepSound.getVolume() + 2.0F) / 8.0F, block.stepSound.getPitch() * 0.5F);

        //TODO particles
        worker.swingItem();
        //Minecraft.getMinecraft().effectRenderer.addBlockDestroyEffects(log.posX, log.posY, log.posZ, block, ChunkCoordUtils.getBlockMetadata(world, log));

        //Damage Tools
        ItemStack Tool = worker.getInventory().getHeldItem();
        if (Tool == null)
        {
            job.setStage(Stage.INSUFFICIENT_TOOLS);
        }

        else
        {
            Tool.getItem().onBlockDestroyed(Tool, world, block, x, y, z, worker);//Dangerous
            if (Tool.stackSize < 1)//if axe breaks
            {
                worker.setCurrentItemOrArmor(0, null);
                worker.getInventory().setInventorySlotContents(worker.getInventory().getHeldItemSlot(), null);
                //TODO particles
            }
        }
        if (!hasAllTheTools())
        {
            job.setStage(Stage.INSUFFICIENT_TOOLS);
        }

        if(job.vein == null)
        {
            if (isValuable(x, y, z))
            {
                job.vein = new ArrayList<ChunkCoordinates>();
                job.vein.add(new ChunkCoordinates(x, y, z));
                logger.info("Found ore");

                findVein(x, y, z);
                logger.info("finished finding ores: " + job.vein.size());

            }
            else
            {
                world.setBlockToAir(x, y, z);

            }
        }
        else
        {
            world.setBlockToAir(x, y, z);
        }

        if(y < currentY)
        {
            world.setBlock(x,y,z,Blocks.cobblestone);
            int slot = inventoryContains(Blocks.cobblestone);
            worker.getInventory().decrStackSize(slot,1);
        }

    }
    private void findVein(int x, int y, int z)
    {
        job.setStage(Stage.MINING_VEIN);

            for (int x1 = x - 1; x1 <= x + 1; x1++)
            {
                for (int z1 = z - 1; z1 <= z + 1; z1++)
                {
                    for (int y1 = y - 1; y1 <= y + 1; y1++)
                    {
                        if (isValuable(x1, y1, z1))
                        {
                            ChunkCoordinates ore = new ChunkCoordinates(x1, y1, z1);
                            if (!job.vein.contains(ore))
                            {
                                job.vein.add(ore);
                                logger.info("Found close ore");
                            }
                        }
                    }
                }
            }

            if((job.veinId < job.vein.size()))
            {
                ChunkCoordinates v = job.vein.get(job.veinId++);
                logger.info("Check next ore");

                findVein(v.posX, v.posY, v.posZ);
            }


    }

    private int getLastLadder(int x, int y, int z)
    {
        if (world.getBlock(x, y, z).isLadder(world, x, y, z, null))//Parameters unused
        {
            return getLastLadder(x, y - 1, z);
        }
        else
        {
            return y + 1;
        }

    }

    private boolean canWalkOn(int x, int y, int z)
    {
        return world.getBlock(x, y, z).getMaterial().isSolid() && world.getBlock(x,y,z)!=Blocks.web;
    }

    private boolean isValuable(int x, int y, int z)
    {
        Block block = world.getBlock(x,y,z);
        String findOre = block.toString();

        return findOre.contains("ore") || findOre.contains("Ore");


    }

    public int getBaseSpeed()
    {
        return baseSpeed;
    }

    public void setBaseSpeed(int baseSpeed)
    {
        this.baseSpeed = baseSpeed;
    }

}