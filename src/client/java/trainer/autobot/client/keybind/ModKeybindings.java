package trainer.autobot.client.keybind;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class ModKeybindings {
	public static final KeyMapping ROTATE_RIGHT_KEY = new KeyMapping(
			"key.trainer-bot.rotate_right",
			GLFW.GLFW_KEY_R,
			KeyMapping.Category.MISC
	);
	public static final KeyMapping ROTATE_LEFT_KEY = new KeyMapping(
			"key.trainer-bot.rotate_left",
			GLFW.GLFW_KEY_L,
			KeyMapping.Category.MISC
	);
	public static final KeyMapping TOGGLE_AUTO_FISH_KEY = new KeyMapping(
			"key.trainer-bot.toggle_auto_fish",
			GLFW.GLFW_KEY_G,
			KeyMapping.Category.MISC
	);
	public static final KeyMapping FACE_NORTH_KEY = new KeyMapping(
			"key.trainer-bot.face_north",
			GLFW.GLFW_KEY_UP,
			KeyMapping.Category.MISC
	);
	public static final KeyMapping FACE_SOUTH_KEY = new KeyMapping(
			"key.trainer-bot.face_south",
			GLFW.GLFW_KEY_DOWN,
			KeyMapping.Category.MISC
	);
	public static final KeyMapping FACE_EAST_KEY = new KeyMapping(
			"key.trainer-bot.face_east",
			GLFW.GLFW_KEY_RIGHT,
			KeyMapping.Category.MISC
	);
	public static final KeyMapping FACE_WEST_KEY = new KeyMapping(
			"key.trainer-bot.face_west",
			GLFW.GLFW_KEY_LEFT,
			KeyMapping.Category.MISC
	);
	public static final KeyMapping TOGGLE_PATH_MACRO_KEY = new KeyMapping(
			"key.trainer-bot.toggle_path_macro",
			GLFW.GLFW_KEY_H,
			KeyMapping.Category.MISC
	);
	public static final KeyMapping OPEN_PATH_CONFIG_SCREEN_KEY = new KeyMapping(
			"key.trainer-bot.open_path_config_screen",
			GLFW.GLFW_KEY_P,
			KeyMapping.Category.MISC
	);

	private ModKeybindings() {
	}

	public static void registerAll() {
		KeyBindingHelper.registerKeyBinding(ROTATE_RIGHT_KEY);
		KeyBindingHelper.registerKeyBinding(ROTATE_LEFT_KEY);
		KeyBindingHelper.registerKeyBinding(TOGGLE_AUTO_FISH_KEY);
		KeyBindingHelper.registerKeyBinding(FACE_NORTH_KEY);
		KeyBindingHelper.registerKeyBinding(FACE_SOUTH_KEY);
		KeyBindingHelper.registerKeyBinding(FACE_EAST_KEY);
		KeyBindingHelper.registerKeyBinding(FACE_WEST_KEY);
		KeyBindingHelper.registerKeyBinding(TOGGLE_PATH_MACRO_KEY);
		KeyBindingHelper.registerKeyBinding(OPEN_PATH_CONFIG_SCREEN_KEY);
	}
}
