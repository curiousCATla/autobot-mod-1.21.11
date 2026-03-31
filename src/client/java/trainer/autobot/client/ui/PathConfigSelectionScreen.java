package trainer.autobot.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import trainer.autobot.client.macro.PathMacroController;

import java.nio.file.Path;
import java.util.List;

public class PathConfigSelectionScreen extends Screen {
	private static final int FILES_PER_PAGE = 6;

	private final Screen parent;
	private int currentPage = 0;

	public PathConfigSelectionScreen(Screen parent) {
		super(Component.literal("Path Config Selection"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		refreshButtons();
	}

	@Override
	public void onClose() {
		if (this.minecraft != null) {
			this.minecraft.setScreen(parent);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		guiGraphics.fill(0, 0, this.width, this.height, 0xB0101010);
		super.render(guiGraphics, mouseX, mouseY, partialTick);

		int centerX = this.width / 2;
		guiGraphics.drawCenteredString(this.font, this.title, centerX, 20, 0xFFFFFF);
		guiGraphics.drawCenteredString(this.font, "Selected: " + PathMacroController.getSelectedConfigName(), centerX, 40, 0xA0E0A0);
		guiGraphics.drawCenteredString(this.font, "Folder: " + PathMacroController.getConfigDirectory(), centerX, 54, 0xA0A0A0);

		List<Path> files = PathMacroController.getAvailablePathConfigs();
		if (files.isEmpty()) {
			guiGraphics.drawCenteredString(this.font, "No .txt path config files were found.", centerX, this.height / 2, 0xFF8080);
			return;
		}

		int totalPages = Math.max(1, (files.size() + FILES_PER_PAGE - 1) / FILES_PER_PAGE);
		guiGraphics.drawCenteredString(this.font, "Page " + (currentPage + 1) + " / " + totalPages, centerX, this.height - 52, 0xFFFFFF);
		guiGraphics.drawCenteredString(this.font, "Press P to open this screen in game.", centerX, this.height - 38, 0xA0A0A0);
	}

	private void refreshButtons() {
		clearWidgets();

		List<Path> files = PathMacroController.getAvailablePathConfigs();
		int totalPages = Math.max(1, (files.size() + FILES_PER_PAGE - 1) / FILES_PER_PAGE);
		currentPage = Math.max(0, Math.min(currentPage, totalPages - 1));

		int centerX = this.width / 2;
		int buttonWidth = 240;
		int buttonHeight = 20;
		int startY = 84;
		int startIndex = currentPage * FILES_PER_PAGE;
		int endIndex = Math.min(files.size(), startIndex + FILES_PER_PAGE);
		String selectedFileName = PathMacroController.getSelectedConfigName();

		for (int index = startIndex; index < endIndex; index++) {
			String fileName = files.get(index).getFileName().toString();
			boolean selected = fileName.equals(selectedFileName);
			Component label = selected
					? Component.literal("[Selected] " + fileName)
					: Component.literal(fileName);
			Button fileButton = Button.builder(label, button -> selectFile(fileName))
					.bounds(centerX - (buttonWidth / 2), startY + ((index - startIndex) * 24), buttonWidth, buttonHeight)
					.build();
			fileButton.active = !selected;
			addRenderableWidget(fileButton);
		}

		addRenderableWidget(Button.builder(Component.literal("Previous"), button -> {
			currentPage--;
			refreshButtons();
		}).bounds(centerX - 130, this.height - 78, 120, 20).build()).active = currentPage > 0;

		addRenderableWidget(Button.builder(Component.literal("Next"), button -> {
			currentPage++;
			refreshButtons();
		}).bounds(centerX + 10, this.height - 78, 120, 20).build()).active = currentPage < totalPages - 1;

		addRenderableWidget(Button.builder(Component.literal("Refresh"), button -> refreshButtons())
				.bounds(centerX - 130, this.height - 24, 120, 20)
				.build());
		addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
				.bounds(centerX + 10, this.height - 24, 120, 20)
				.build());
	}

	private void selectFile(String fileName) {
		Minecraft client = this.minecraft;
		if (client == null) {
			return;
		}

		PathMacroController.selectPathConfig(client, fileName);
		refreshButtons();
	}
}
