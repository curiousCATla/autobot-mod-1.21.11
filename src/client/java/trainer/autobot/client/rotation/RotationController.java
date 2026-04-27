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
	private static final float TURN_STEP_DEGREES = 8.0f;
	private static final float PITCH_STEP_DEGREES = 8.0f;

	private static float targetYaw = NORTH_YAW;
	private static boolean facingActive = false;

	private static float targetPitch = 0.0f;
	private static boolean pitchActive = false;

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

	public static void startFacingPitch(Minecraft client, float desiredPitch) {
		if (client.player == null) {
			return;
		}

		targetPitch = Mth.clamp(desiredPitch, -90.0f, 90.0f);
		pitchActive = true;
	}

	public static void tick(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null) {
			facingActive = false;
			pitchActive = false;
			return;
		}

		if (facingActive) {
			float yawDelta = Mth.wrapDegrees(targetYaw - player.getYRot());
			if (Math.abs(yawDelta) <= TURN_STEP_DEGREES) {
				setPlayerYaw(player, targetYaw);
				facingActive = false;
			} else {
				setPlayerYaw(player, player.getYRot() + Math.signum(yawDelta) * TURN_STEP_DEGREES);
			}
		}

		if (pitchActive) {
			float pitchDelta = targetPitch - player.getXRot();
			if (Math.abs(pitchDelta) <= PITCH_STEP_DEGREES) {
				setPlayerPitch(player, targetPitch);
				pitchActive = false;
			} else {
				setPlayerPitch(player, player.getXRot() + Math.signum(pitchDelta) * PITCH_STEP_DEGREES);
			}
		}
	}

	public static boolean isBusy() {
		return facingActive || pitchActive;
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

	private static void setPlayerPitch(LocalPlayer player, float pitch) {
		float clampedPitch = Mth.clamp(pitch, -90.0f, 90.0f);

		player.setXRot(clampedPitch);
		player.xRotO = clampedPitch;
	}
}
