package trainer.autobot.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import trainer.autobot.client.fishing.AutoFishController;
import trainer.autobot.client.macro.PathMacroController;
import trainer.autobot.client.tree.AutoTreeController;

import java.nio.file.Path;
import java.util.List;

public class PathConfigSelectionScreen extends Screen {
	private static final int FILES_PER_PAGE = 5;

	private final Screen parent;
	private int currentPage = 0;
	private EditBox radiusInput;

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
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		g.fill(0, 0, this.width, this.height, 0xB0101010);
		super.render(g, mouseX, mouseY, partialTick);

		int cx = this.width / 2;

		g.drawCenteredString(this.font, this.title, cx, 12, 0xFFFFFF);
		g.drawCenteredString(this.font, "Selected: " + PathMacroController.getSelectedConfigName(), cx, 26, 0xA0E0A0);
		g.drawCenteredString(this.font, "Folder: " + PathMacroController.getConfigDirectory(), cx, 38, 0xA0A0A0);

		g.drawCenteredString(this.font, "- Automations -", cx, 54, 0xC8C8C8);

		// Tree radius label, right-aligned to sit next to the EditBox
		int radiusLabelX = cx - 14 - this.font.width("Tree Radius:");
		g.drawString(this.font, "Tree Radius:", radiusLabelX, 97, 0xFFFFFF, false);

		g.drawCenteredString(this.font, "- Macro Scripts -", cx, 120, 0xC8C8C8);

		List<Path> files = PathMacroController.getAvailablePathConfigs();
		if (files.isEmpty()) {
			g.drawCenteredString(this.font, "No .txt path config files were found.", cx, this.height / 2, 0xFF8080);
			return;
		}

		int totalPages = Math.max(1, (files.size() + FILES_PER_PAGE - 1) / FILES_PER_PAGE);
		g.drawCenteredString(this.font, "Page " + (currentPage + 1) + " / " + totalPages, cx, this.height - 52, 0xFFFFFF);
		g.drawCenteredString(this.font, "Press P to open this screen in game.", cx, this.height - 38, 0xA0A0A0);
	}

	private void refreshButtons() {
		clearWidgets();

		int cx = this.width / 2;

		// --- Automation toggles ---
		boolean fishOn = AutoFishController.isEnabled();
		addRenderableWidget(Button.builder(
				Component.literal("Auto Fish: " + (fishOn ? "ON" : "OFF")),
				btn -> {
					if (this.minecraft != null) AutoFishController.toggle(this.minecraft);
					refreshButtons();
				})
				.bounds(cx - 130, 68, 120, 20)
				.build());

		boolean treeOn = AutoTreeController.isEnabled();
		addRenderableWidget(Button.builder(
				Component.literal("Auto Tree: " + (treeOn ? "ON" : "OFF")),
				btn -> {
					if (this.minecraft != null) AutoTreeController.toggle(this.minecraft);
					refreshButtons();
				})
				.bounds(cx + 10, 68, 120, 16)
				.build());

		// --- Radius input ---
		radiusInput = new EditBox(this.font, cx - 10, 93, 44, 16, Component.empty());
		radiusInput.setMaxLength(3);
		radiusInput.setFilter(s -> s.isEmpty() || s.matches("\\d{1,3}"));
		radiusInput.setValue(String.valueOf(AutoTreeController.getSearchRadius()));
		radiusInput.setResponder(s -> {
			try {
				AutoTreeController.setSearchRadius(Integer.parseInt(s));
			} catch (NumberFormatException ignored) {
			}
		});
		addRenderableWidget(radiusInput);

		// --- Macro file list ---
		List<Path> files = PathMacroController.getAvailablePathConfigs();
		int totalPages = Math.max(1, (files.size() + FILES_PER_PAGE - 1) / FILES_PER_PAGE);
		currentPage = Math.max(0, Math.min(currentPage, totalPages - 1));

		int buttonWidth = 240;
		int buttonHeight = 20;
		int fileListY = 132;
		int startIndex = currentPage * FILES_PER_PAGE;
		int endIndex = Math.min(files.size(), startIndex + FILES_PER_PAGE);
		String selectedName = PathMacroController.getSelectedConfigName();

		for (int i = startIndex; i < endIndex; i++) {
			String fileName = files.get(i).getFileName().toString();
			boolean selected = fileName.equals(selectedName);
			Component label = selected
					? Component.literal("[Selected] " + fileName)
					: Component.literal(fileName);
			Button btn = Button.builder(label, b -> selectFile(fileName))
					.bounds(cx - buttonWidth / 2, fileListY + (i - startIndex) * 24, buttonWidth, buttonHeight)
					.build();
			btn.active = !selected;
			addRenderableWidget(btn);
		}

		// --- Pagination and footer ---
		addRenderableWidget(Button.builder(Component.literal("Previous"), btn -> {
			currentPage--;
			refreshButtons();
		}).bounds(cx - 130, this.height - 78, 120, 20).build()).active = currentPage > 0;

		addRenderableWidget(Button.builder(Component.literal("Next"), btn -> {
			currentPage++;
			refreshButtons();
		}).bounds(cx + 10, this.height - 78, 120, 20).build()).active = currentPage < totalPages - 1;

		addRenderableWidget(Button.builder(Component.literal("Refresh"), btn -> refreshButtons())
				.bounds(cx - 130, this.height - 24, 120, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Done"), btn -> onClose())
				.bounds(cx + 10, this.height - 24, 120, 20).build());
	}

	private void selectFile(String fileName) {
		if (this.minecraft == null) return;
		PathMacroController.selectPathConfig(this.minecraft, fileName);
		refreshButtons();
	}
}
