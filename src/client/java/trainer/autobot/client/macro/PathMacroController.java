package trainer.autobot.client.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import trainer.autobot.TrainerBot;
import trainer.autobot.client.movement.MouseAction;
import trainer.autobot.client.movement.MovementController;
import trainer.autobot.client.movement.MovementDirection;
import trainer.autobot.client.rotation.RotationController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class PathMacroController {
	private static final int PATH_MACRO_STEP_DELAY_TICKS = 4;
	private static boolean running = false;
	private static int currentStepIndex = 0;
	private static int delayTicks = 0;
	private static int currentWaitTicks = 0;
	private static List<MacroStep> steps = List.of();

	private PathMacroController() {
	}

	public static void initialize() {
		PathMacroStorage.ensureMacroFilesExist();
	}

	public static void toggle(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null) {
			return;
		}

		if (running) {
			stop(client, "Path macro stopped");
			return;
		}

		List<MacroStep> loadedSteps = loadPathMacroSteps();
		if (loadedSteps.isEmpty()) {
			player.displayClientMessage(Component.literal("Path macro file is empty or invalid"), true);
			return;
		}

		steps = loadedSteps;
		currentStepIndex = 0;
		delayTicks = 0;
		currentWaitTicks = 0;
		running = true;
		player.displayClientMessage(Component.literal("Path macro started: " + getSelectedConfigName()), true);
	}

	public static void tick(Minecraft client) {
		if (!running) {
			return;
		}

		LocalPlayer player = client.player;
		if (player == null) {
			stop(client, null);
			return;
		}

		if (MovementController.isBusy() || RotationController.isBusy()) {
			return;
		}

		if (currentWaitTicks > 0) {
			currentWaitTicks--;
			return;
		}

		if (delayTicks > 0) {
			delayTicks--;
			return;
		}

		if (currentStepIndex >= steps.size()) {
			stop(client, "Path macro finished");
			return;
		}

		MacroStep step = steps.get(currentStepIndex);
		currentStepIndex++;

		switch (step.actionType()) {
			case MOVE -> MovementController.movePlayer(client, step.direction(), step.distanceBlocks(), step.mouseAction());
			case CLIMB -> MovementController.climbPlayer(client, step.direction(), step.heightBlocks());
			case WAIT -> currentWaitTicks = step.waitTicks();
			case FACE -> RotationController.startFacingDirection(client, step.faceYaw());
			case PITCH -> RotationController.startFacingPitch(client, step.targetPitch());
			case LOOP -> currentStepIndex = 0;
		}

		delayTicks = PATH_MACRO_STEP_DELAY_TICKS;
	}

	public static void stop(Minecraft client, String message) {
		MovementController.stopMovement(client);
		MovementController.stopClimbing(client);
		running = false;
		currentStepIndex = 0;
		delayTicks = 0;
		currentWaitTicks = 0;
		steps = List.of();

		if (message != null && client.player != null) {
			client.player.displayClientMessage(Component.literal(message), true);
		}
	}

	public static List<Path> getAvailablePathConfigs() {
		return PathMacroStorage.listAvailableMacroFiles();
	}

	public static String getSelectedConfigName() {
		return PathMacroStorage.getSelectedMacroFileName();
	}

	public static Path getConfigDirectory() {
		return PathMacroStorage.getMacroDirectory();
	}

	public static void selectPathConfig(Minecraft client, String fileName) {
		List<Path> availableFiles = getAvailablePathConfigs();
		boolean exists = availableFiles.stream()
				.map(path -> path.getFileName().toString())
				.anyMatch(fileName::equals);
		if (!exists) {
			if (client.player != null) {
				client.player.displayClientMessage(Component.literal("Path config not found: " + fileName), true);
			}
			return;
		}

		PathMacroStorage.saveSelectedMacroFileName(fileName);
		if (running) {
			stop(client, "Path macro stopped after selecting " + fileName);
			return;
		}

		if (client.player != null) {
			client.player.displayClientMessage(Component.literal("Selected path config: " + fileName), true);
		}
	}

	private static List<MacroStep> loadPathMacroSteps() {
		Path macroPath = PathMacroStorage.getSelectedMacroPath();
		List<MacroStep> loadedSteps = new ArrayList<>();

		try {
			List<String> lines = Files.readAllLines(macroPath);
			for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
				String rawLine = lines.get(lineIndex).trim();
				if (rawLine.isEmpty() || rawLine.startsWith("#")) {
					continue;
				}

				MacroStep step = parseMacroStep(rawLine, lineIndex + 1);
				if (step != null) {
					loadedSteps.add(step);
				}
			}
		} catch (IOException exception) {
			TrainerBot.LOGGER.error("Failed to read path macro file {}", macroPath, exception);
		}

		return loadedSteps;
	}

	private static MacroStep parseMacroStep(String rawLine, int lineNumber) {
		String[] parts = rawLine.split("\\s+");
		if (parts.length == 0) {
			return null;
		}

		try {
			MacroActionType actionType = MacroActionType.valueOf(parts[0].toUpperCase());

			return switch (actionType) {
				case MOVE -> parseMoveStep(parts);
				case CLIMB -> parseClimbStep(parts);
				case WAIT -> parseWaitStep(parts);
				case FACE -> parseFaceStep(parts);
				case PITCH -> parsePitchStep(parts);
				case LOOP -> parseLoopStep(parts);
			};
		} catch (RuntimeException exception) {
			TrainerBot.LOGGER.warn("Skipping invalid path macro line {}: {}", lineNumber, rawLine);
			return null;
		}
	}

	private static MacroStep parseMoveStep(String[] parts) {
		if (parts.length != 4) {
			throw new IllegalArgumentException("MOVE needs 4 values");
		}

		MovementDirection direction = MovementDirection.valueOf(parts[1].toUpperCase());
		double distanceBlocks = Double.parseDouble(parts[2]);
		MouseAction mouseAction = MouseAction.valueOf(parts[3].toUpperCase());
		return new MacroStep(MacroActionType.MOVE, direction, distanceBlocks, mouseAction, 0, 0, RotationController.NORTH_YAW, 0.0f);
	}

	private static MacroStep parseClimbStep(String[] parts) {
		if (parts.length != 3) {
			throw new IllegalArgumentException("CLIMB needs 3 values");
		}

		MovementDirection direction = MovementDirection.valueOf(parts[1].toUpperCase());
		int heightBlocks = Integer.parseInt(parts[2]);
		return new MacroStep(MacroActionType.CLIMB, direction, 0.0, MouseAction.NONE, heightBlocks, 0, RotationController.NORTH_YAW, 0.0f);
	}

	private static MacroStep parseWaitStep(String[] parts) {
		if (parts.length != 2) {
			throw new IllegalArgumentException("WAIT needs 2 values");
		}

		int waitTicks = Integer.parseInt(parts[1]);
		return new MacroStep(MacroActionType.WAIT, MovementDirection.UP, 0.0, MouseAction.NONE, 0, waitTicks, RotationController.NORTH_YAW, 0.0f);
	}

	private static MacroStep parseFaceStep(String[] parts) {
		if (parts.length != 2) {
			throw new IllegalArgumentException("FACE needs 2 values");
		}

		float faceYaw = switch (parts[1].toUpperCase()) {
			case "NORTH" -> RotationController.NORTH_YAW;
			case "SOUTH" -> RotationController.SOUTH_YAW;
			case "EAST" -> RotationController.EAST_YAW;
			case "WEST" -> RotationController.WEST_YAW;
			default -> throw new IllegalArgumentException("Unknown FACE direction");
		};
		return new MacroStep(MacroActionType.FACE, MovementDirection.UP, 0.0, MouseAction.NONE, 0, 0, faceYaw, 0.0f);
	}

	private static MacroStep parsePitchStep(String[] parts) {
		if (parts.length != 2) {
			throw new IllegalArgumentException("PITCH needs 2 values");
		}

		float targetPitch = Float.parseFloat(parts[1]);
		return new MacroStep(MacroActionType.PITCH, MovementDirection.UP, 0.0, MouseAction.NONE, 0, 0, RotationController.NORTH_YAW, targetPitch);
	}

	private static MacroStep parseLoopStep(String[] parts) {
		if (parts.length != 1) {
			throw new IllegalArgumentException("LOOP takes no arguments");
		}

		return new MacroStep(MacroActionType.LOOP, MovementDirection.UP, 0.0, MouseAction.NONE, 0, 0, RotationController.NORTH_YAW, 0.0f);
	}
};