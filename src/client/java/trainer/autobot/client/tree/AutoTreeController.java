package trainer.autobot.client.tree;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlayerAbilitiesPacket;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import trainer.autobot.client.movement.MouseAction;
import trainer.autobot.client.movement.MovementController;
import trainer.autobot.client.movement.MovementDirection;
import trainer.autobot.client.rotation.RotationController;

import java.util.*;

public final class AutoTreeController {

	private static int searchRadius = 16;
	private static final double STAND_DISTANCE = 1.0;
	private static final int MAX_GROUND_REACH_HEIGHT = 4;
	private static final float FLY_HEIGHT_TOLERANCE = 0.3f;
	private static final double MAX_REACH = 4.5;
	private static final double REPOSITION_WALK_DISTANCE = 2.0;
	private static final int MAX_REPOSITION_ATTEMPTS = 3;

	private enum State {
		IDLE, SEARCHING, FACING_TREE, WALKING_TO_TREE, FACING_LOG, BREAKING_LOG,
		FLYING_UP, FLYING_DOWN, FLYING_TO_LOG, REPOSITIONING
	}

	private static State state = State.IDLE;
	private static boolean stateEntered = false;
	private static List<BlockPos> targetLogs = new ArrayList<>();
	private static int currentLogIndex = 0;
	private static int groundY = 0;
	private static boolean wasFlying = false;
	private static double targetFlyY = 0;
	private static boolean repositioningMoving = false;
	private static int repositionAttempts = 0;
	private static boolean flyingToLogMoving = false;
	private static int flyingDownTicks = 0;
	private static double lastFlyingUpY = 0;
	private static int flyingUpStuckTicks = 0;

	private AutoTreeController() {
	}

	public static boolean isEnabled() {
		return state != State.IDLE;
	}

	public static int getSearchRadius() {
		return searchRadius;
	}

	public static void setSearchRadius(int radius) {
		searchRadius = Math.max(1, Math.min(64, radius));
	}

	public static void toggle(Minecraft client) {
		if (state != State.IDLE) {
			stop(client);
			if (client.player != null) {
				client.player.displayClientMessage(Component.literal("Auto-tree disabled"), true);
			}
		} else {
			setState(State.SEARCHING);
			if (client.player != null) {
				client.player.displayClientMessage(Component.literal("Auto-tree enabled"), true);
			}
		}
	}

	public static void tick(Minecraft client) {
		if (state == State.IDLE) {
			return;
		}

		LocalPlayer player = client.player;
		if (player == null || client.level == null || client.gameMode == null) {
			stop(client);
			return;
		}

		// Loop to handle states that transition immediately (e.g. already within STAND_DISTANCE)
		int safetyLimit = 8;
		while (state != State.IDLE && safetyLimit-- > 0) {
			if (!stateEntered) {
				State stateBeforeEnter = state;
				enterState(client, player);
				if (state == stateBeforeEnter) {
					stateEntered = true;
				}
				// Continue: re-evaluate in case enterState triggered an immediate transition
				continue;
			}
			tickState(client, player);
			break;
		}
	}

	public static boolean isBusy() {
		return state != State.IDLE;
	}

	private static void setState(State newState) {
		state = newState;
		stateEntered = false;
	}

	private static void stop(Minecraft client) {
		LocalPlayer player = client.player;
		if (player != null) {
			client.options.keyJump.setDown(false);
			client.options.keyShift.setDown(false);
			if (wasFlying) {
				stopFlight(player);
			}
		}
		MovementController.stopMovement(client);
		targetLogs.clear();
		currentLogIndex = 0;
		wasFlying = false;
		flyingDownTicks = 0;
		flyingUpStuckTicks = 0;
		state = State.IDLE;
		stateEntered = false;
	}

	private static void enterState(Minecraft client, LocalPlayer player) {
		switch (state) {
			case FACING_TREE -> {
				float yaw = yawToBlock(player, targetLogs.get(0));
				RotationController.startFacingDirection(client, yaw);
				RotationController.startFacingPitch(client, 15.0f);
			}
			case WALKING_TO_TREE -> {
				if (horizontalDistToBlock(player, targetLogs.get(0)) <= STAND_DISTANCE) {
					setState(State.FACING_LOG);
				} else {
					RotationController.startFacingPitch(client, 32.0f);
					client.options.keyUp.setDown(true);
				}
			}
			case FACING_LOG -> {
				BlockPos targetLog = targetLogs.get(currentLogIndex);
				RotationController.startFacingDirection(client, yawToBlock(player, targetLog));
				RotationController.startFacingPitch(client, pitchToBlock(player, targetLog));
			}
			case FLYING_UP -> {
				// Snap feet to block centre so the vertical path is a clean straight line
				BlockPos support = BlockPos.containing(player.getX(), player.getY() - 0.2, player.getZ());
				player.setPos(support.getX() + 0.5, player.getY(), support.getZ() + 0.5);
				startFlight(player);
				targetFlyY = targetLogs.get(currentLogIndex).getY() - 1.0;
				lastFlyingUpY = player.getY();
				flyingUpStuckTicks = 0;
				if (targetFlyY >= player.getY()) {
					client.options.keyJump.setDown(true);  // log is above — ascend
				} else {
					client.options.keyShift.setDown(true); // log is below — descend
				}
			}
			case FLYING_DOWN -> {
				flyingDownTicks = 0;
				client.options.keyShift.setDown(true);
			}
			case FLYING_TO_LOG -> {
				flyingToLogMoving = false;
				BlockPos targetLog = targetLogs.get(currentLogIndex);
				RotationController.startFacingDirection(client, yawToBlock(player, targetLog));
				RotationController.startFacingPitch(client, 0.0f);
			}
			case REPOSITIONING -> {
				repositioningMoving = false;
				BlockPos targetLog = targetLogs.get(currentLogIndex);
				RotationController.startFacingDirection(client, yawToBlock(player, targetLog));
				RotationController.startFacingPitch(client, 0.0f);
			}
			default -> {
			} // SEARCHING and BREAKING_LOG have no enter setup
		}
	}

	private static void tickState(Minecraft client, LocalPlayer player) {
		switch (state) {
			case SEARCHING -> tickSearching(client, player);
			case FACING_TREE -> {
				if (!RotationController.isBusy()) setState(State.WALKING_TO_TREE);
			}
			case WALKING_TO_TREE -> tickWalkingToTree(client, player);
			case FACING_LOG -> {
				if (!RotationController.isBusy()) setState(State.BREAKING_LOG);
			}
			case BREAKING_LOG -> tickBreaking(client, player);
			case FLYING_UP -> tickFlyingUp(client, player);
			case FLYING_DOWN -> tickFlyingDown(client, player);
			case FLYING_TO_LOG -> tickFlyingToLog(client, player);
			case REPOSITIONING -> tickRepositioning(client, player);
			default -> {
			}
		}
	}

	private static void tickSearching(Minecraft client, LocalPlayer player) {
		BlockPos treeBase = findNearestTreeBase(player, client.level);

		if (treeBase == null) {
			player.displayClientMessage(
					Component.literal("No trees found within " + searchRadius + " blocks"), true);
			stop(client);
			return;
		}

		targetLogs = findConnectedLogs(client.level, treeBase);
		currentLogIndex = 0;
		groundY = player.blockPosition().getY();
		wasFlying = false;
		setState(State.FACING_TREE);
	}

	private static void tickBreaking(Minecraft client, LocalPlayer player) {
		BlockPos targetLog = targetLogs.get(currentLogIndex);

		if (!isLogBlock(client.level.getBlockState(targetLog))) {
			// Log was broken — advance to next
			repositionAttempts = 0;
			currentLogIndex++;
			if (currentLogIndex >= targetLogs.size()) {
				setState(wasFlying ? State.FLYING_DOWN : State.SEARCHING);
				return;
			}
			BlockPos nextLog = targetLogs.get(currentLogIndex);
			if (isReachableFromGround(nextLog) || !canFly(player)) {
				setState(State.FACING_LOG);
			} else {
				setState(State.FLYING_UP);
			}
			return;
		}

		// Log still exists — keep breaking
		if (client.hitResult == null) {
			return;
		}

		if (client.hitResult.getType() == HitResult.Type.MISS) {
			// Crosshair hits nothing — check whether log is out of reach
			if (dist3DToBlock(player, targetLog) > MAX_REACH) {
				if (canFly(player)) {
					double tgtFlyY = targetLog.getY() - 1.0;
					boolean atRightHeight = Math.abs(player.getY() - tgtFlyY) < 2.0;
					if (wasFlying && atRightHeight && horizontalDistToBlock(player, targetLog) > STAND_DISTANCE + 1.0) {
						setState(State.FLYING_TO_LOG);
					} else {
						setState(State.FLYING_UP);
					}
				} else {
					repositionAttempts++;
					if (repositionAttempts >= MAX_REPOSITION_ATTEMPTS) {
						// Truly unreachable on foot — skip this log
						repositionAttempts = 0;
						currentLogIndex++;
						setState(currentLogIndex >= targetLogs.size()
								? State.SEARCHING
								: State.FACING_LOG);
					} else {
						setState(State.REPOSITIONING);
					}
				}
			} else {
				// Within reach but camera not on block — re-aim
				setState(State.FACING_LOG);
			}
			return;
		}

		if (client.hitResult.getType() != HitResult.Type.BLOCK) {
			return; // entity in the way, wait it out
		}

		BlockHitResult blockHit = (BlockHitResult) client.hitResult;
		if (blockHit.getBlockPos().equals(targetLog)) {
			client.gameMode.continueDestroyBlock(blockHit.getBlockPos(), blockHit.getDirection());
		} else {
			BlockState hitState = client.level.getBlockState(blockHit.getBlockPos());
			if (isBreakableObstacle(hitState)) {
				// Leaf, log, or vine blocking LOS — break it first
				client.gameMode.continueDestroyBlock(blockHit.getBlockPos(), blockHit.getDirection());
			} else {
				// Crosshair on an unrelated block — re-aim
				setState(State.FACING_LOG);
			}
		}
	}

	private static void tickWalkingToTree(Minecraft client, LocalPlayer player) {
		BlockPos treeBase = targetLogs.get(0);

		// Keep yaw aimed at trunk and hold 32° downward pitch while walking
		RotationController.startFacingDirection(client, yawToBlock(player, treeBase));
		RotationController.startFacingPitch(client, 32.0f);

		// Break any leaf, log, or vine that appears in the crosshair on the way
		if (client.hitResult != null && client.hitResult.getType() == HitResult.Type.BLOCK) {
			BlockHitResult blockHit = (BlockHitResult) client.hitResult;
			BlockState hitState = client.level.getBlockState(blockHit.getBlockPos());
			if (isBreakableObstacle(hitState)) {
				client.gameMode.continueDestroyBlock(blockHit.getBlockPos(), blockHit.getDirection());
			}
		}

		// Stop and hand off to FACING_LOG once within reach distance
		if (horizontalDistToBlock(player, treeBase) <= STAND_DISTANCE) {
			client.options.keyUp.setDown(false);
			setState(State.FACING_LOG);
		}
	}

	private static void tickFlyingUp(Minecraft client, LocalPlayer player) {
		boolean descending = targetFlyY < player.getY();

		// Look in the direction of travel to clear any leaves or logs in the path
		RotationController.startFacingPitch(client, descending ? 90.0f : -90.0f);

		if (client.hitResult != null && client.hitResult.getType() == HitResult.Type.BLOCK) {
			BlockHitResult hit = (BlockHitResult) client.hitResult;
			BlockState hitState = client.level.getBlockState(hit.getBlockPos());
			if (isBreakableObstacle(hitState)) {
				client.gameMode.continueDestroyBlock(hit.getBlockPos(), hit.getDirection());
			}
		}

		boolean atTarget = descending
				? player.getY() <= targetFlyY + FLY_HEIGHT_TOLERANCE
				: player.getY() >= targetFlyY - FLY_HEIGHT_TOLERANCE;

		// When descending, detect if the player is blocked above the target (e.g. the
		// target log is a solid block the player can't pass through). If Y hasn't
		// changed for 20 ticks, treat the current position as close enough.
		if (descending) {
			if (Math.abs(player.getY() - lastFlyingUpY) < 0.05) {
				flyingUpStuckTicks++;
			} else {
				flyingUpStuckTicks = 0;
			}
			lastFlyingUpY = player.getY();
			if (flyingUpStuckTicks >= 20) {
				atTarget = true;
			}
		}

		if (atTarget) {
			client.options.keyJump.setDown(false);
			client.options.keyShift.setDown(false);
			flyingUpStuckTicks = 0;
			wasFlying = true;
			setState(State.FACING_LOG);
		}
	}

	private static void tickFlyingToLog(Minecraft client, LocalPlayer player) {
		BlockPos targetLog = targetLogs.get(currentLogIndex);

		if (!flyingToLogMoving) {
			// Phase 1: rotate to face the log horizontally, then start flying forward
			if (!RotationController.isBusy()) {
				flyingToLogMoving = true;
				client.options.keyUp.setDown(true);
			}
			return;
		}

		// Phase 2: fly forward; re-aim each tick to stay on course and break anything in the path
		RotationController.startFacingDirection(client, yawToBlock(player, targetLog));
		RotationController.startFacingPitch(client, 0.0f);

		if (client.hitResult != null && client.hitResult.getType() == HitResult.Type.BLOCK) {
			BlockHitResult blockHit = (BlockHitResult) client.hitResult;
			if (!blockHit.getBlockPos().equals(targetLog)) {
				BlockState hitState = client.level.getBlockState(blockHit.getBlockPos());
				if (isBreakableObstacle(hitState)) {
					client.gameMode.continueDestroyBlock(blockHit.getBlockPos(), blockHit.getDirection());
				}
			}
		}

		if (horizontalDistToBlock(player, targetLog) <= STAND_DISTANCE + 1.0) {
			client.options.keyUp.setDown(false);
			setState(State.FACING_LOG);
		}
	}

	private static void tickFlyingDown(Minecraft client, LocalPlayer player) {
		flyingDownTicks++;

		BlockPos below = player.blockPosition().below();
		BlockState belowState = client.level.getBlockState(below);
		boolean solidBelow = !isBreakableObstacle(belowState) && !belowState.isAir();

		boolean reachedGround = player.getY() <= groundY + 0.5;
		boolean stuck = flyingDownTicks >= 20 && solidBelow;

		if (reachedGround || stuck) {
			flyingDownTicks = 0;
			client.options.keyShift.setDown(false);
			stopFlight(player);
			wasFlying = false;
			setState(State.SEARCHING);
		}
	}

	private static void tickRepositioning(Minecraft client, LocalPlayer player) {
		if (!repositioningMoving) {
			if (!RotationController.isBusy()) {
				repositioningMoving = true;
				MovementController.movePlayer(client, MovementDirection.UP, REPOSITION_WALK_DISTANCE, MouseAction.NONE);
			}
		} else {
			if (!MovementController.isBusy()) {
				setState(State.FACING_LOG);
			}
		}
	}

	private static double dist3DToBlock(LocalPlayer player, BlockPos target) {
		double dx = (target.getX() + 0.5) - player.getX();
		double dy = (target.getY() + 0.5) - player.getEyeY();
		double dz = (target.getZ() + 0.5) - player.getZ();
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	private static BlockPos findNearestTreeBase(LocalPlayer player, Level level) {
		BlockPos playerPos = player.blockPosition();
		BlockPos nearest = null;
		double minHorizDist = Double.MAX_VALUE;

		for (int dx = -searchRadius; dx <= searchRadius; dx++) {
			for (int dz = -searchRadius; dz <= searchRadius; dz++) {
				double horizDist = Math.sqrt((double) (dx * dx + dz * dz));
				if (horizDist > searchRadius) continue;

				// Scan upward in this column to find the lowest log
				for (int dy = -2; dy <= searchRadius; dy++) {
					BlockPos pos = playerPos.offset(dx, dy, dz);
					if (isLogBlock(level.getBlockState(pos))) {
						// Confirm it is a base log (no log directly below)
						if (!isLogBlock(level.getBlockState(pos.below()))) {
							if (horizDist < minHorizDist) {
								minHorizDist = horizDist;
								nearest = pos;
							}
						}
						break; // Lowest log in this column found
					}
				}
			}
		}

		return nearest;
	}

	private static List<BlockPos> findConnectedLogs(Level level, BlockPos start) {
		List<BlockPos> allLogs = new ArrayList<>();
		Queue<BlockPos> queue = new LinkedList<>();
		Set<BlockPos> visited = new HashSet<>();

		queue.add(start);
		visited.add(start);

		while (!queue.isEmpty()) {
			BlockPos current = queue.poll();
			allLogs.add(current);

			// Check all 26 neighbors in the 3×3×3 cube (1-block radius, diagonal included)
			for (int ddx = -1; ddx <= 1; ddx++) {
				for (int ddy = -1; ddy <= 1; ddy++) {
					for (int ddz = -1; ddz <= 1; ddz++) {
						if (ddx == 0 && ddy == 0 && ddz == 0) continue;
						BlockPos neighbor = current.offset(ddx, ddy, ddz);
						if (!visited.contains(neighbor) && isLogBlock(level.getBlockState(neighbor))) {
							visited.add(neighbor);
							queue.add(neighbor);
						}
					}
				}
			}
		}

		// Group into (X,Z) columns; sort columns nearest to trunk first, then Y ascending within each
		Map<Long, List<BlockPos>> columns = new LinkedHashMap<>();
		for (BlockPos log : allLogs) {
			long key = ((long) log.getX() << 32) | (log.getZ() & 0xFFFFFFFFL);
			columns.computeIfAbsent(key, k -> new ArrayList<>()).add(log);
		}
		columns.values().forEach(col -> col.sort(Comparator.comparingInt(BlockPos::getY)));

		List<List<BlockPos>> sortedColumns = new ArrayList<>(columns.values());
		// Primary sort: column whose base log is lowest comes first.
		// Tiebreak: closest to trunk horizontally.
		sortedColumns.sort(
				Comparator.comparingInt((List<BlockPos> col) -> col.get(0).getY())
						.thenComparingDouble(col -> {
							BlockPos b = col.get(0);
							double dx = b.getX() - start.getX(), dz = b.getZ() - start.getZ();
							return dx * dx + dz * dz;
						}));

		List<BlockPos> result = new ArrayList<>();
		for (List<BlockPos> col : sortedColumns) {
			result.addAll(col);
		}
		return result;
	}

	private static boolean isLogBlock(BlockState blockState) {
		return blockState.is(BlockTags.LOGS);
	}

	private static boolean isBreakableObstacle(BlockState state) {
		return isLogBlock(state) || state.is(BlockTags.LEAVES) || state.is(BlockTags.CLIMBABLE);
	}

	// Compute Minecraft yaw to face a block center (South=0, West=90, North=180, East=-90)
	private static float yawToBlock(LocalPlayer player, BlockPos target) {
		double dx = (target.getX() + 0.5) - player.getX();
		double dz = (target.getZ() + 0.5) - player.getZ();
		return (float) Math.toDegrees(Math.atan2(-dx, dz));
	}

	// Compute Minecraft pitch to face a block center (0=horizontal, -90=up, +90=down)
	private static float pitchToBlock(LocalPlayer player, BlockPos target) {
		double dx = (target.getX() + 0.5) - player.getX();
		double dy = (target.getY() + 0.5) - player.getEyeY();
		double dz = (target.getZ() + 0.5) - player.getZ();
		double horizDist = Math.sqrt(dx * dx + dz * dz);
		if (horizDist < 1.0E-6) {
			return dy > 0 ? -90.0f : 90.0f;
		}
		return (float) -Math.toDegrees(Math.atan2(dy, horizDist));
	}

	private static double horizontalDistToBlock(LocalPlayer player, BlockPos target) {
		double dx = (target.getX() + 0.5) - player.getX();
		double dz = (target.getZ() + 0.5) - player.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	private static boolean isReachableFromGround(BlockPos log) {
		return log.getY() <= groundY + MAX_GROUND_REACH_HEIGHT;
	}

	private static boolean canFly(LocalPlayer player) {
		return player.getAbilities().mayfly;
	}

	private static void startFlight(LocalPlayer player) {
		player.getAbilities().flying = true;
		if (player.connection != null) {
			player.connection.send(new ServerboundPlayerAbilitiesPacket(player.getAbilities()));
		}
	}

	private static void stopFlight(LocalPlayer player) {
		player.getAbilities().flying = false;
		player.resetFallDistance();
		if (player.connection != null) {
			player.connection.send(new ServerboundPlayerAbilitiesPacket(player.getAbilities()));
		}
	}
}
