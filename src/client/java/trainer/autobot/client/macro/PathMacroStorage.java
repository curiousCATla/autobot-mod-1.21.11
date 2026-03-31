package trainer.autobot.client.macro;

import net.fabricmc.loader.api.FabricLoader;
import trainer.autobot.TrainerBot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public final class PathMacroStorage {
	public static final String DEFAULT_PATH_MACRO_FILE_NAME = "path-macro.txt";
	private static final String SELECTED_PATH_CONFIG_FILE_NAME = "selected-path-config.dat";

	private PathMacroStorage() {
	}

	public static void ensureMacroFilesExist() {
		Path macroDirectory = getMacroDirectory();
		Path defaultMacroPath = macroDirectory.resolve(DEFAULT_PATH_MACRO_FILE_NAME);

		try {
			Files.createDirectories(macroDirectory);
			if (Files.notExists(defaultMacroPath)) {
				Files.writeString(defaultMacroPath, """
						# Trainer Bot path macro
						# One command per line.
						# MOVE <UP|DOWN|LEFT|RIGHT> <distanceBlocks> <true|false>
						# CLIMB <UP|DOWN|LEFT|RIGHT> <heightBlocks>
						# WAIT <ticks>
						# FACE <NORTH|SOUTH|EAST|WEST>
						FACE NORTH
						MOVE UP 3 false
						WAIT 20
						CLIMB UP 1
						MOVE RIGHT 2 false
						""");
			}
		} catch (IOException exception) {
			TrainerBot.LOGGER.error("Failed to prepare path macro files", exception);
		}

		ensureSelectedConfigIsValid();
	}

	public static List<Path> listAvailableMacroFiles() {
		ensureMacroFilesExist();

		try (Stream<Path> paths = Files.list(getMacroDirectory())) {
			return paths
					.filter(Files::isRegularFile)
					.filter(path -> path.getFileName().toString().endsWith(".txt"))
					.sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase()))
					.toList();
		} catch (IOException exception) {
			TrainerBot.LOGGER.error("Failed to list path macro files", exception);
			return List.of();
		}
	}

	public static String getSelectedMacroFileName() {
		ensureMacroFilesExist();

		Path selectionPath = getSelectionFilePath();
		try {
			if (Files.exists(selectionPath)) {
				String selectedFileName = Files.readString(selectionPath).trim();
				if (isValidMacroFileName(selectedFileName)) {
					Path selectedPath = getMacroDirectory().resolve(selectedFileName);
					if (Files.exists(selectedPath)) {
						return selectedFileName;
					}
				}
			}
		} catch (IOException exception) {
			TrainerBot.LOGGER.error("Failed to read selected path config", exception);
		}

		List<Path> availableFiles = listAvailableMacroFiles();
		if (!availableFiles.isEmpty()) {
			return availableFiles.getFirst().getFileName().toString();
		}

		return DEFAULT_PATH_MACRO_FILE_NAME;
	}

	public static void saveSelectedMacroFileName(String fileName) {
		if (!isValidMacroFileName(fileName)) {
			throw new IllegalArgumentException("Invalid path config file name: " + fileName);
		}

		try {
			Files.writeString(getSelectionFilePath(), fileName);
		} catch (IOException exception) {
			TrainerBot.LOGGER.error("Failed to save selected path config", exception);
		}
	}

	public static Path getSelectedMacroPath() {
		return getMacroDirectory().resolve(getSelectedMacroFileName());
	}

	public static Path getMacroDirectory() {
		return FabricLoader.getInstance().getConfigDir().resolve(TrainerBot.MOD_ID);
	}

	private static void ensureSelectedConfigIsValid() {
		String selectedFileName = getSelectedMacroFileNameWithoutFallback();
		if (selectedFileName != null && Files.exists(getMacroDirectory().resolve(selectedFileName))) {
			return;
		}

		List<Path> availableFiles = listAvailableMacroFilesWithoutEnsuring();
		if (!availableFiles.isEmpty()) {
			saveSelectedMacroFileName(availableFiles.getFirst().getFileName().toString());
		}
	}

	private static String getSelectedMacroFileNameWithoutFallback() {
		Path selectionPath = getSelectionFilePath();
		if (Files.notExists(selectionPath)) {
			return null;
		}

		try {
			String fileName = Files.readString(selectionPath).trim();
			return isValidMacroFileName(fileName) ? fileName : null;
		} catch (IOException exception) {
			TrainerBot.LOGGER.error("Failed to read selected path config", exception);
			return null;
		}
	}

	private static List<Path> listAvailableMacroFilesWithoutEnsuring() {
		try (Stream<Path> paths = Files.list(getMacroDirectory())) {
			return paths
					.filter(Files::isRegularFile)
					.filter(path -> path.getFileName().toString().endsWith(".txt"))
					.sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase()))
					.toList();
		} catch (IOException exception) {
			TrainerBot.LOGGER.error("Failed to list path macro files", exception);
			return List.of();
		}
	}

	private static boolean isValidMacroFileName(String fileName) {
		return fileName != null
				&& !fileName.isBlank()
				&& !fileName.contains("/")
				&& !fileName.contains("\\")
				&& fileName.endsWith(".txt");
	}

	private static Path getSelectionFilePath() {
		return getMacroDirectory().resolve(SELECTED_PATH_CONFIG_FILE_NAME);
	}
}
