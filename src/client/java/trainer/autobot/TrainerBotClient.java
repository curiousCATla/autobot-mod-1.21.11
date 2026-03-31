package trainer.autobot;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import trainer.autobot.client.fishing.AutoFishController;
import trainer.autobot.client.keybind.ModKeybindings;
import trainer.autobot.client.macro.PathMacroController;
import trainer.autobot.client.movement.MovementController;
import trainer.autobot.client.rotation.RotationController;
import trainer.autobot.client.ui.PathConfigSelectionScreen;

public class TrainerBotClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ModKeybindings.registerAll();
		PathMacroController.initialize();

		ClientTickEvents.END_CLIENT_TICK.register(this::onEndClientTick);
	}

	private void onEndClientTick(Minecraft client) {
		while (ModKeybindings.ROTATE_RIGHT_KEY.consumeClick()) {
			RotationController.rotateCamera(client, RotationController.ROTATION_DEGREES);
		}

		while (ModKeybindings.ROTATE_LEFT_KEY.consumeClick()) {
			RotationController.rotateCamera(client, -RotationController.ROTATION_DEGREES);
		}

		while (ModKeybindings.TOGGLE_AUTO_FISH_KEY.consumeClick()) {
			AutoFishController.toggle(client);
		}

		while (ModKeybindings.FACE_NORTH_KEY.consumeClick()) {
			RotationController.startFacingDirection(client, RotationController.NORTH_YAW);
		}

		while (ModKeybindings.FACE_SOUTH_KEY.consumeClick()) {
			RotationController.startFacingDirection(client, RotationController.SOUTH_YAW);
		}

		while (ModKeybindings.FACE_EAST_KEY.consumeClick()) {
			RotationController.startFacingDirection(client, RotationController.EAST_YAW);
		}

		while (ModKeybindings.FACE_WEST_KEY.consumeClick()) {
			RotationController.startFacingDirection(client, RotationController.WEST_YAW);
		}

		while (ModKeybindings.TOGGLE_PATH_MACRO_KEY.consumeClick()) {
			PathMacroController.toggle(client);
		}

		while (ModKeybindings.OPEN_PATH_CONFIG_SCREEN_KEY.consumeClick()) {
			client.setScreen(new PathConfigSelectionScreen(client.screen));
		}

		RotationController.tick(client);
		MovementController.tick(client);
		PathMacroController.tick(client);
		AutoFishController.tick(client);
	}
}
