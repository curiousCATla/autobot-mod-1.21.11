package trainer.autobot.client.fishing;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.Items;
import trainer.autobot.client.movement.MouseAction;
import trainer.autobot.client.movement.MovementController;
import trainer.autobot.client.movement.MovementDirection;

import java.util.concurrent.ThreadLocalRandom;

public final class AutoFishController {
	private static final int FISH_BEFORE_SIDE_STEP = 6;
	private static final int AUTO_FISH_MIN_DELAY_TICKS = 20;
	private static final int AUTO_FISH_MAX_DELAY_TICKS = 40;
	private static final double BITE_DIP_VELOCITY_THRESHOLD = -0.04;
	private static final double SIDE_STEP_DISTANCE_BLOCKS = 5.0;

	private static boolean enabled = false;
	private static int cooldownTicks = 0;
	private static int fishCaughtCount = 0;
	private static boolean moveRightNext = true;

	private AutoFishController() {
	}

	public static void toggle(Minecraft client) {
		enabled = !enabled;
		cooldownTicks = 0;
		fishCaughtCount = 0;
		moveRightNext = true;
		MovementController.stopMovement(client);

		if (client.player != null) {
			String state = enabled ? "enabled" : "disabled";
			client.player.displayClientMessage(Component.literal("Auto-fish " + state), true);
		}
	}

	public static void tick(Minecraft client) {
		if (!enabled) {
			return;
		}

		LocalPlayer player = client.player;
		if (player == null || client.gameMode == null) {
			return;
		}

		if (cooldownTicks > 0) {
			cooldownTicks--;
		}

		InteractionHand rodHand = getFishingRodHand(player);
		if (rodHand == null) {
			return;
		}

		FishingHook hook = player.fishing;
		if (hook != null && !hook.isRemoved()) {
			if (cooldownTicks > 0) {
				return;
			}

			if (shouldReelIn(hook)) {
				useFishingRod(client, player, rodHand);
				onFishCaught(client);
				cooldownTicks = randomAutoFishDelayTicks();
			}

			return;
		}

		if (cooldownTicks > 0) {
			return;
		}

		useFishingRod(client, player, rodHand);
		cooldownTicks = randomAutoFishDelayTicks();
	}

	private static boolean shouldReelIn(FishingHook hook) {
		if (!hook.isInWater()) {
			return false;
		}

		return hook.getDeltaMovement().y < BITE_DIP_VELOCITY_THRESHOLD;
	}

	private static void useFishingRod(Minecraft client, LocalPlayer player, InteractionHand rodHand) {
		client.gameMode.useItem(player, rodHand);
		player.swing(rodHand);
	}

	private static void onFishCaught(Minecraft client) {
		fishCaughtCount++;
		if (fishCaughtCount % FISH_BEFORE_SIDE_STEP != 0) {
			return;
		}

		MovementDirection direction = moveRightNext ? MovementDirection.RIGHT : MovementDirection.LEFT;
		MovementController.movePlayer(client, direction, SIDE_STEP_DISTANCE_BLOCKS, MouseAction.NONE);
		moveRightNext = !moveRightNext;
	}

	private static int randomAutoFishDelayTicks() {
		return ThreadLocalRandom.current().nextInt(AUTO_FISH_MIN_DELAY_TICKS, AUTO_FISH_MAX_DELAY_TICKS + 1);
	}

	private static InteractionHand getFishingRodHand(LocalPlayer player) {
		if (player.getMainHandItem().is(Items.FISHING_ROD)) {
			return InteractionHand.MAIN_HAND;
		}

		if (player.getOffhandItem().is(Items.FISHING_ROD)) {
			return InteractionHand.OFF_HAND;
		}

		return null;
	}
}
