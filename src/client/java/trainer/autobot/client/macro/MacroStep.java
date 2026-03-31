package trainer.autobot.client.macro;

import trainer.autobot.client.movement.MovementDirection;

public record MacroStep(
		MacroActionType actionType,
		MovementDirection direction,
		double distanceBlocks,
		boolean holdLeftMouseButton,
		int heightBlocks,
		int waitTicks,
		float faceYaw
) {
}
