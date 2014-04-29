package com.elmakers.mine.bukkit.block;

import org.apache.commons.lang.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.util.BlockVector;

import com.elmakers.mine.bukkit.utility.ConfigurationUtils;

/**
 * Stores a cached Block. Stores the coordinates and world, but will look up a block reference on demand.
 * 
 * This also stores the block state using the MaterialAndData structure as a base, and can be
 * used to restore a previously stored state.
 * 
 * In addition, BlockData instances can be linked to each other for layered undo queues that work
 * even when undone out of order.
 * 
 */
public class BlockData extends MaterialAndData implements com.elmakers.mine.bukkit.api.block.BlockData
{
	public static final BlockFace[] FACES = new BlockFace[] { BlockFace.WEST, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.UP, BlockFace.DOWN };
	public static final BlockFace[] SIDES = new BlockFace[] { BlockFace.WEST, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST };
	
	// Transient
	protected Block     block;
	protected com.elmakers.mine.bukkit.api.block.BlockData	nextState;
	protected com.elmakers.mine.bukkit.api.block.BlockData	priorState;

	// Persistent
	protected BlockVector  location;
	protected String       worldName;

	public static long getBlockId(Block block)
	{
		return getBlockId(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
	}
	
	public static long getBlockId(String world, int x, int y, int z)
	{
		// Long is 63 bits
		// 15 sets of F's (4-bits)
		// world gets 4 bits
	    // y gets 8 bits
		// and x and z get 24 bits each
		return ((world.hashCode() & 0xF) << 56)
			| (((long)x & 0xFFFFFF) << 32) 
			| (((long)z & 0xFFFFFF) << 8) 
			| ((long)y & 0xFF);
	}
	
	@Override
	public int hashCode()
	{
		return (int)getId();
	}
	
	@Override
	public boolean equals(Object other)
	{
		if (other instanceof BlockData) {
			return getId() == ((BlockData)other).getId();
		}
		return super.equals(other);
	}
	
	public long getId()
	{
		return getBlockId(worldName, location.getBlockX(), location.getBlockY(), location.getBlockZ());
	}

	public static BlockFace getReverseFace(BlockFace blockFace)
	{
		switch (blockFace)
		{
		case NORTH:
			return BlockFace.SOUTH;
		case WEST:
			return BlockFace.EAST;
		case SOUTH:
			return BlockFace.NORTH;
		case EAST:
			return BlockFace.WEST;
		case UP:
			return BlockFace.DOWN;
		case DOWN:
			return BlockFace.UP;
		default:
			return BlockFace.SELF;
		}
	}

	public BlockData()
	{
	}

	public BlockData(Block block)
	{
		super(block);
		this.block = block;

		location = new BlockVector(block.getX(), block.getY(), block.getZ());
		worldName = block.getWorld().getName();
	}

	public BlockData(com.elmakers.mine.bukkit.api.block.BlockData copy)
	{
		super(copy);
		location = copy.getPosition();
		worldName = copy.getWorldName();
	}
	
	public BlockData(Location location, Material material, byte data)
	{
		super(material, data);
		this.location = new BlockVector(location.getBlockX(), location.getBlockY(), location.getBlockZ());
		this.worldName = location.getWorld().getName();
	}
	
	public BlockData(int x, int y, int z, String world, Material material, byte data)
	{
		super(material, data);
		this.location = new BlockVector(x, y, z);
		this.worldName = world;
	}
	
	public BlockData(ConfigurationSection node) {
		this(
			ConfigurationUtils.getLocation(node, "location"), 
			ConfigurationUtils.getMaterial(node, "material"), 
			(byte)node.getInt("data", 0)
		);
	}
	
	public void save(ConfigurationSection node) {
		node.set("material", ConfigurationUtils.fromMaterial(material));
		node.set("data", data);
		Location location = new Location(Bukkit.getWorld(worldName), this.location.getX(), this.location.getY(), this.location.getZ());
		node.set("location", ConfigurationUtils.fromLocation(location));
	}

	protected boolean checkBlock()
	{
		if (block == null)
		{
			block = getBlock();
		}

		return block != null;
	}

	public void setPosition(BlockVector location)
	{
		this.location = location;
	}

	@Override
	public boolean undo()
	{
		if (!checkBlock())
		{
			return true;
		}

		Chunk chunk = block.getChunk();
		if (!chunk.isLoaded())
		{
			chunk.load();
			return false;
		}

		if (isDifferent(block))
		{
			modify(block);
		}
		
		if (priorState != null) {
			priorState.setNextState(nextState);
		}
		if (nextState != null) {
			nextState.setPriorState(priorState);
			nextState.updateFrom(this);
		}

		return true;
	}
	
	@Override
	public void commit()
	{
		if (nextState != null) {
			nextState.setPriorState(null);
			nextState.updateFrom(getBlock());
		}
		
		if (priorState != null) {
			// Very important for recursion!
			priorState.setNextState(null);
			priorState.commit();
		}
	}
	
	@SuppressWarnings("deprecation")
	public String toString() {
		return location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ() + "," + worldName + "|" + getMaterial().getId() + ":" + getData();
	}
	
	@SuppressWarnings("deprecation")
	public static BlockData fromString(String s) {
		BlockData result = null;
		if (s == null) return null;
		try {
			String[] pieces = StringUtils.split(s, '|');
			String[] locationPieces = StringUtils.split(pieces[0], ',');
			int x = Integer.parseInt(locationPieces[0]);
			int y = Integer.parseInt(locationPieces[1]);
			int z = Integer.parseInt(locationPieces[2]);
			String world = locationPieces[3];
			String[] materialPieces = StringUtils.split(pieces[1], ':');
			int materialId = Integer.parseInt(materialPieces[0]);
			byte dataId = Byte.parseByte(materialPieces[1]);
			return new BlockData(x, y, z, world, Material.getMaterial(materialId), dataId);
		} catch(Exception ex) {
		}
		
		return result;
	}
	
	@Override
	public com.elmakers.mine.bukkit.api.block.BlockData getNextState() {
		return nextState;
	}
	
	@Override
	public void setNextState(com.elmakers.mine.bukkit.api.block.BlockData next) {
		nextState = next;
	}
	
	@Override
	public com.elmakers.mine.bukkit.api.block.BlockData getPriorState() {
		return priorState;
	}
	
	@Override
	public void setPriorState(com.elmakers.mine.bukkit.api.block.BlockData prior) {
		priorState = prior;
	}
	
	@Override
	public void restore() {
		modify(getBlock());
	}

	@Override
	public String getWorldName() {
		return worldName;
	}
	
	@Override
	public BlockVector getPosition()
	{
		return location;
	}

	@Override
	public World getWorld()
	{
		if (worldName == null || worldName.length() == 0) return null;
		return Bukkit.getWorld(worldName);
	}
	
	@Override
	public Block getBlock()
	{
		if (block == null && location != null)
		{
			World world = getWorld();
			if (world != null) {
				block = world.getBlockAt(location.getBlockX(), location.getBlockY(), location.getBlockZ());
			}
		}
		return block;
	}
}