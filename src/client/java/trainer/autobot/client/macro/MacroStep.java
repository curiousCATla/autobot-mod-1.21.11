package trainer.autobot.client.macro;

import trainer.autobot.client.movement.MouseAction;
import trainer.autobot.client.movement.MovementDirection;

public record MacroStep(
		MacroActionType actionType,
		MovementDirection direction,
		double distanceBlocks,
		MouseAction mouseAction,
		int heightBlocks,
		int waitTicks,
		float faceYaw,
		float targetPitch
) {
}
