package trainer.autobot.client.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class MovementController {
	private static final double CLIMB_LOOK_AHEAD_BLOCKS = 0.6;
	private static final double CENTERING_TOLERANCE_BLOCKS = 0.1;
	private static final int STUCK_TICK_THRESHOLD = 10;
	private static final double STUCK_MOVEMENT_EPSILON = 0.02;

	private static boolean movementActive = false;
	private static MouseAction activeMouseAction = MouseAction.NONE;
	private static double movementDistanceTravelled = 0.0;
	private static Vec3 movementDirectionVector = Vec3.ZERO;
	private static Vec3 lastMovementPosition = Vec3.ZERO;
	private static double targetMovementDistance = 0.0;
	private static MovementDirection activeMovementDirection = MovementDirection.UP;
	private static boolean centeringAfterMovement = false;
	private static BlockPos movementCenterTargetBlock = BlockPos.ZERO;

	private static boolean climbActive = false;
	private static int climbTargetHeight = 0;
	private static int climbStartBlockY = 0;
	private static MovementDirection climbDirection = MovementDirection.UP;
	private static boolean centeringAfterClimb = false;
	private static BlockPos climbCenterTargetBlock = BlockPos.ZERO;

	private static boolean stuckRecoveryActive = false;
	private static BlockPos stuckRecoveryTargetBlock = BlockPos.ZERO;
	private static Vec3 lastActionCheckPosition = Vec3.ZERO;
	private static int stuckTickCounter = 0;

	private MovementController() {
	}

	public static void tick(Minecraft client) {
		updateClimbing(client);
		updateControlledMovement(client);
	}

	public static void movePlayer(Minecraft client, MovementDirection direction, double distanceBlocks, MouseAction mouseAction) {
		LocalPlayer player = client.player;
		if (player == null || distanceBlocks <= 0.0) {
			return;
		}

		movePlayer(client, player, direction, distanceBlocks, mouseAction);
	}

	public static void climbPlayer(Minecraft client, MovementDirection direction, int heightBlocks) {
		LocalPlayer player = client.player;
		if (player == null || heightBlocks <= 0) {
			return;
		}

		stopMovement(client);
		climbDirection = direction;
		climbTargetHeight = heightBlocks;
		climbStartBlockY = player.getBlockY();
		climbActive = true;
		resetStuckTracking(player);
		applyClimbKeys(client, direction, false);
	}

	public static void stopMovement(Minecraft client) {
		movementActive = false;
		activeMouseAction = MouseAction.NONE;
		movementDistanceTravelled = 0.0;
		movementDirectionVector = Vec3.ZERO;
		lastMovementPosition = Vec3.ZERO;
		targetMovementDistance = 0.0;
		activeMovementDirection = MovementDirection.UP;
		centeringAfterMovement = false;
		movementCenterTargetBlock = BlockPos.ZERO;
		resetStuckTracking(null);
		releaseMovementKeys(client);
	}

	public static void stopClimbing(Minecraft client) {
		climbActive = false;
		climbTargetHeight = 0;
		climbStartBlockY = 0;
		climbDirection = MovementDirection.UP;
		centeringAfterClimb = false;
		climbCenterTargetBlock = BlockPos.ZERO;
		resetStuckTracking(null);
		client.options.keyJump.setDown(false);
		releaseMovementKeys(client);
	}

	public static boolean isBusy() {
		return movementActive || climbActive;
	}

	private static void movePlayer(Minecraft client, LocalPlayer player, MovementDirection direction, double distanceBlocks, MouseAction mouseAction) {
		stopClimbing(client);

		Vec3 movementVector = getMovementVector(player, direction);
		if (movementVector.lengthSqr() < 1.0E-6) {
			return;
		}

		movementDirectionVector = movementVector;
		activeMovementDirection = direction;
		targetMovementDistance = distanceBlocks;
		activeMouseAction = mouseAction;
		movementActive = true;
		movementDistanceTravelled = 0.0;
		lastMovementPosition = player.position();
		resetStuckTracking(player);
		applyMovementKeys(client, direction);
	}

	private static void updateClimbing(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null) {
			stopClimbing(client);
			return;
		}

		if (!climbActive) {
			return;
		}

		if (handleStuckRecovery(client, player, true)) {
			return;
		}

		int climbedBlocks = player.getBlockY() - climbStartBlockY;
		if (climbedBlocks >= climbTargetHeight && player.onGround()) {
			if (!centeringAfterClimb) {
				climbCenterTargetBlock = resolveClimbCenterTargetBlock(player);
				centeringAfterClimb = true;
			}
			updateCenteringAfterClimb(client, player);
			return;
		}

		Vec3 climbVector = getMovementVector(player, climbDirection);
		boolean shouldJump = player.onGround() && shouldJumpOntoBlock(player, climbVector);
		applyClimbKeys(client, climbDirection, shouldJump);
	}

	private static void updateControlledMovement(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null) {
			stopMovement(client);
			return;
		}

		if (!movementActive) {
			return;
		}

		if (handleStuckRecovery(client, player, false)) {
			return;
		}

		Vec3 currentPosition = player.position();
		Vec3 delta = currentPosition.subtract(lastMovementPosition);
		double progress = delta.dot(movementDirectionVector);
		if (progress > 0.0) {
			movementDistanceTravelled += progress;
		}
		lastMovementPosition = currentPosition;

		double remainingDistance = targetMovementDistance - movementDistanceTravelled;
		if (remainingDistance <= 0.0) {
			if (!centeringAfterMovement && player.onGround()) {
				movementCenterTargetBlock = resolveCenterTargetBlock(player, activeMovementDirection);
				centeringAfterMovement = true;
			}

			if (!centeringAfterMovement || updateCenteringTowardsBlock(client, player, movementCenterTargetBlock)) {
				stopMovement(client);
			}
			return;
		}

		applyMovementKeys(client, activeMovementDirection);
		performMouseAction(client, player, activeMouseAction);
	}

	private static void applyMovementKeys(Minecraft client, MovementDirection direction) {
		client.options.keyUp.setDown(direction == MovementDirection.UP);
		client.options.keyDown.setDown(direction == MovementDirection.DOWN);
		client.options.keyLeft.setDown(direction == MovementDirection.LEFT);
		client.options.keyRight.setDown(direction == MovementDirection.RIGHT);
	}

	private static void releaseMovementKeys(Minecraft client) {
		client.options.keyUp.setDown(false);
		client.options.keyDown.setDown(false);
		client.options.keyLeft.setDown(false);
		client.options.keyRight.setDown(false);
	}

	private static void performMouseAction(Minecraft client, LocalPlayer player, MouseAction mouseAction) {
		if (mouseAction == MouseAction.NONE || client.hitResult == null || client.gameMode == null) {
			return;
		}

		if (mouseAction == MouseAction.ATTACK) {
			if (client.hitResult.getType() == HitResult.Type.BLOCK) {
				BlockHitResult blockHit = (BlockHitResult) client.hitResult;
				client.gameMode.continueDestroyBlock(blockHit.getBlockPos(), blockHit.getDirection());
			} else if (client.hitResult.getType() == HitResult.Type.ENTITY) {
				EntityHitResult entityHit = (EntityHitResult) client.hitResult;
				client.gameMode.attack(player, entityHit.getEntity());
			}
		} else if (mouseAction == MouseAction.USE) {
			if (client.hitResult.getType() == HitResult.Type.BLOCK) {
				BlockHitResult blockHit = (BlockHitResult) client.hitResult;
				client.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, blockHit);
			} else if (client.hitResult.getType() == HitResult.Type.ENTITY) {
				EntityHitResult entityHit = (EntityHitResult) client.hitResult;
				client.gameMode.interactAt(player, entityHit.getEntity(), entityHit, InteractionHand.MAIN_HAND);
			} else {
				client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
			}
		}
	}

	private static void applyClimbKeys(Minecraft client, MovementDirection direction, boolean jump) {
		applyMovementKeys(client, direction);
		client.options.keyJump.setDown(jump);
	}

	private static Vec3 getMovementVector(LocalPlayer player, MovementDirection direction) {
		Vec3 forward = player.getLookAngle().multiply(1.0, 0.0, 1.0);
		if (forward.lengthSqr() < 1.0E-6) {
			return Vec3.ZERO;
		}

		forward = forward.normalize();
		Vec3 right = new Vec3(-forward.z, 0.0, forward.x);

		return switch (direction) {
			case UP -> forward;
			case DOWN -> forward.scale(-1.0);
			case LEFT -> right.scale(-1.0);
			case RIGHT -> right;
		};
	}

	private static boolean shouldJumpOntoBlock(LocalPlayer player, Vec3 movementVector) {
		if (movementVector.lengthSqr() < 1.0E-6) {
			return false;
		}

		Vec3 lookAheadPosition = player.position().add(movementVector.scale(CLIMB_LOOK_AHEAD_BLOCKS));
		BlockPos feetBlock = BlockPos.containing(lookAheadPosition.x, player.getY(), lookAheadPosition.z);
		BlockPos headBlock = feetBlock.above();

		boolean obstacleAhead = !player.level().getBlockState(feetBlock).getCollisionShape(player.level(), feetBlock).isEmpty();
		boolean spaceAboveObstacle = player.level().getBlockState(headBlock).getCollisionShape(player.level(), headBlock).isEmpty();
		return obstacleAhead && spaceAboveObstacle;
	}

	private static void updateCenteringAfterClimb(Minecraft client, LocalPlayer player) {
		if (updateCenteringTowardsBlock(client, player, climbCenterTargetBlock)) {
			stopClimbing(client);
		}
	}

	private static boolean updateCenteringTowardsBlock(Minecraft client, LocalPlayer player, BlockPos targetBlock) {
		double targetX = targetBlock.getX() + 0.5;
		double targetZ = targetBlock.getZ() + 0.5;
		double deltaX = targetX - player.getX();
		double deltaZ = targetZ - player.getZ();

		if (Math.abs(deltaX) <= CENTERING_TOLERANCE_BLOCKS && Math.abs(deltaZ) <= CENTERING_TOLERANCE_BLOCKS) {
			return true;
		}

		releaseMovementKeys(client);
		client.options.keyJump.setDown(false);

		Vec3 forward = getMovementVector(player, MovementDirection.UP);
		Vec3 right = getMovementVector(player, MovementDirection.RIGHT);
		if (forward.lengthSqr() < 1.0E-6 || right.lengthSqr() < 1.0E-6) {
			return true;
		}

		double forwardDot = deltaX * forward.x + deltaZ * forward.z;
		double rightDot = deltaX * right.x + deltaZ * right.z;

		if (forwardDot > CENTERING_TOLERANCE_BLOCKS) {
			client.options.keyUp.setDown(true);
		} else if (forwardDot < -CENTERING_TOLERANCE_BLOCKS) {
			client.options.keyDown.setDown(true);
		}

		if (rightDot > CENTERING_TOLERANCE_BLOCKS) {
			client.options.keyRight.setDown(true);
		} else if (rightDot < -CENTERING_TOLERANCE_BLOCKS) {
			client.options.keyLeft.setDown(true);
		}

		return false;
	}

	private static BlockPos resolveClimbCenterTargetBlock(LocalPlayer player) {
		BlockPos supportBlock = getSupportBlock(player);
		BlockPos directionalBlock = getDirectionalNeighborBlock(player, supportBlock, climbDirection);

		return switch (climbDirection) {
			case DOWN, LEFT, RIGHT -> directionalBlock;
			case UP -> {
				double supportDistance = horizontalDistanceToBlockCenter(player, supportBlock);
				double directionalDistance = horizontalDistanceToBlockCenter(player, directionalBlock);
				yield directionalDistance < supportDistance ? directionalBlock : supportBlock;
			}
		};
	}

	private static BlockPos resolveCenterTargetBlock(LocalPlayer player, MovementDirection direction) {
		BlockPos standingBlock = getSupportBlock(player);
		BlockPos aheadBlock = getDirectionalNeighborBlock(player, standingBlock, direction);

		double standingDistance = horizontalDistanceToBlockCenter(player, standingBlock);
		double aheadDistance = horizontalDistanceToBlockCenter(player, aheadBlock);

		return aheadDistance < standingDistance ? aheadBlock : standingBlock;
	}

	private static BlockPos getDirectionalNeighborBlock(LocalPlayer player, BlockPos originBlock, MovementDirection direction) {
		return originBlock.relative(switch (direction) {
			case UP -> player.getDirection();
			case DOWN -> player.getDirection().getOpposite();
			case LEFT -> player.getDirection().getCounterClockWise();
			case RIGHT -> player.getDirection().getClockWise();
		});
	}

	private static BlockPos getSupportBlock(LocalPlayer player) {
		return BlockPos.containing(player.getX(), player.getY() - 0.2, player.getZ());
	}

	private static double horizontalDistanceToBlockCenter(LocalPlayer player, BlockPos blockPos) {
		double deltaX = (blockPos.getX() + 0.5) - player.getX();
		double deltaZ = (blockPos.getZ() + 0.5) - player.getZ();
		return Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
	}

	private static boolean handleStuckRecovery(Minecraft client, LocalPlayer player, boolean climbingAction) {
		if (stuckRecoveryActive) {
			if (updateCenteringTowardsBlock(client, player, stuckRecoveryTargetBlock)) {
				stuckRecoveryActive = false;
				stuckRecoveryTargetBlock = BlockPos.ZERO;
				resetStuckTracking(player);
			}
			return true;
		}

		if (lastActionCheckPosition == Vec3.ZERO) {
			lastActionCheckPosition = player.position();
			return false;
		}

		Vec3 currentPosition = player.position();
		double horizontalMovement = currentPosition.subtract(lastActionCheckPosition).multiply(1.0, 0.0, 1.0).length();
		double verticalMovement = Math.abs(currentPosition.y - lastActionCheckPosition.y);
		int currentBlockY = player.getBlockY();
		int lastBlockY = BlockPos.containing(lastActionCheckPosition).getY();

		boolean madeProgress;
		if (climbingAction) {
			madeProgress = horizontalMovement > STUCK_MOVEMENT_EPSILON || currentBlockY > lastBlockY;
		} else {
			madeProgress = horizontalMovement > STUCK_MOVEMENT_EPSILON || verticalMovement > STUCK_MOVEMENT_EPSILON;
		}

		if (!madeProgress) {
			stuckTickCounter++;
		} else {
			stuckTickCounter = 0;
		}

		lastActionCheckPosition = currentPosition;
		if (stuckTickCounter < STUCK_TICK_THRESHOLD) {
			return false;
		}

		stuckRecoveryActive = true;
		stuckRecoveryTargetBlock = getSupportBlock(player);
		stuckTickCounter = 0;
		return true;
	}

	private static void resetStuckTracking(LocalPlayer player) {
		stuckRecoveryActive = false;
		stuckRecoveryTargetBlock = BlockPos.ZERO;
		lastActionCheckPosition = player == null ? Vec3.ZERO : player.position();
		stuckTickCounter = 0;
	}
}
