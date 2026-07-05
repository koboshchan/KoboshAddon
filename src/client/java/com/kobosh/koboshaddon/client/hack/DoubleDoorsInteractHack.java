package com.kobosh.koboshaddon.client.hack;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.RightClickListener;
import net.wurstclient.hack.Hack;

@SearchTags({"double doors", "door interact", "auto door", "double door"})
public final class DoubleDoorsInteractHack extends Hack implements RightClickListener
{
	public DoubleDoorsInteractHack()
	{
		super("DoubleDoorsInteract");
		setCategory(Category.BLOCKS);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(RightClickListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(RightClickListener.class, this);
	}

	@Override
	public void onRightClick(RightClickEvent event)
	{
		if(MC.player == null || MC.level == null)
			return;

		if(MC.player.isSpectator())
			return;

		boolean isSecondaryUseActive = MC.player.isSecondaryUseActive();
		boolean bothHandsEmpty = MC.player.getMainHandItem().isEmpty() && MC.player.getOffhandItem().isEmpty();
		if(isSecondaryUseActive && !bothHandsEmpty)
			return;

		if(MC.hitResult != null && MC.hitResult.getType() == HitResult.Type.BLOCK)
		{
			BlockHitResult hitResult = (BlockHitResult)MC.hitResult;
			BlockPos doorPos = hitResult.getBlockPos();
			BlockState blockState = MC.level.getBlockState(doorPos);

			if(blockState.getBlock() instanceof DoorBlock)
			{
				Direction doorFacing = blockState.getValue(DoorBlock.FACING);
				DoorHingeSide doorHinge = blockState.getValue(DoorBlock.HINGE);
				BlockPos otherDoorPos;

				if(doorHinge == DoorHingeSide.LEFT)
					otherDoorPos = doorPos.relative(doorFacing.getClockWise());
				else
					otherDoorPos = doorPos.relative(doorFacing.getCounterClockWise());

				BlockState otherBlockState = MC.level.getBlockState(otherDoorPos);
				if(otherBlockState.getBlock() instanceof DoorBlock)
				{
					if(blockState.getValue(DoorBlock.HALF) == otherBlockState.getValue(DoorBlock.HALF)
						&& blockState.getValue(DoorBlock.HINGE) != otherBlockState.getValue(DoorBlock.HINGE)
						&& blockState.getValue(DoorBlock.OPEN) == otherBlockState.getValue(DoorBlock.OPEN))
					{
						BlockHitResult otherHitResult = new BlockHitResult(
							new Vec3(otherDoorPos.getX() + 0.5, otherDoorPos.getY() + 0.5, otherDoorPos.getZ() + 0.5),
							hitResult.getDirection(),
							otherDoorPos,
							hitResult.isInside()
						);

						InteractionHand hand = InteractionHand.MAIN_HAND;
						if(MC.player.getCooldowns().isOnCooldown(MC.player.getMainHandItem()))
							hand = InteractionHand.OFF_HAND;

						MC.gameMode.useItemOn(MC.player, hand, otherHitResult);
					}
				}
			}
		}
	}
}
