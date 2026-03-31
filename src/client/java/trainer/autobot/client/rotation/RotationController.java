package trainer.autobot.client.rotation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

public final class RotationController {
	public static final float NORTH_YAW = 180.0f;
	public static final float SOUTH_YAW = 0.0f;
	public static final float EAST_YAW = -90.0f;
	public static final float WEST_YAW = 90.0f;
	public static final float ROTATION_DEGREES = 15.0f;
	private static final float TURN_STEP_DEGREES = 4.0f;

	private static float targetYaw = NORTH_YAW;
	private static boolean facingActive = false;

	private RotationController() {
	}

	public static void rotateCamera(Minecraft client, float degrees) {
		LocalPlayer player = client.player;
		if (player == null) {
			return;
		}

		setPlayerYaw(player, player.getYRot() + degrees);
	}

	public static void startFacingDirection(Minecraft client, float desiredYaw) {
		if (client.player == null) {
			return;
		}

		targetYaw = desiredYaw;
		facingActive = true;
	}

	public static void tick(Minecraft client) {
		if (!facingActive) {
			return;
		}

		LocalPlayer player = client.player;
		if (player == null) {
			facingActive = false;
			return;
		}

		float yawDelta = Mth.wrapDegrees(targetYaw - player.getYRot());
		if (Math.abs(yawDelta) <= TURN_STEP_DEGREES) {
			setPlayerYaw(player, targetYaw);
			facingActive = false;
			return;
		}

		float turnStep = Math.signum(yawDelta) * TURN_STEP_DEGREES;
		setPlayerYaw(player, player.getYRot() + turnStep);
	}

	public static boolean isBusy() {
		return facingActive;
	}

	private static void setPlayerYaw(LocalPlayer player, float yaw) {
		float wrappedYaw = Mth.wrapDegrees(yaw);

		player.setYRot(wrappedYaw);
		player.yRotO = wrappedYaw;
		player.setYHeadRot(wrappedYaw);
		player.yHeadRotO = wrappedYaw;
		player.setYBodyRot(wrappedYaw);
		player.yBodyRotO = wrappedYaw;
	}
}
