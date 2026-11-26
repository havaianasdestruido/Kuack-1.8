package net.caffeinemc.mods.sodium.client.gui.options;

import java.util.ArrayList;
import java.util.List;

public class SodiumOptionPage {

	public static class OptionGroup {
		private final String title;
		private final List<SodiumOptionEntry> options = new ArrayList<SodiumOptionEntry>();

		public OptionGroup(String title) {
			this.title = title;
		}

		public OptionGroup add(SodiumOptionEntry entry) {
			this.options.add(entry);
			return this;
		}

		public String getTitle() {
			return this.title;
		}

		public List<SodiumOptionEntry> getOptions() {
			return this.options;
		}
	}

	private final String name;
	private final List<OptionGroup> groups = new ArrayList<OptionGroup>();

	public SodiumOptionPage(String name) {
		this.name = name;
	}

	public SodiumOptionPage addGroup(OptionGroup group) {
		this.groups.add(group);
		return this;
	}

	public String getName() {
		return this.name;
	}

	public List<OptionGroup> getGroups() {
		return this.groups;
	}

	public boolean hasPendingChanges() {
		for (int i = 0, n = this.groups.size(); i < n; ++i) {
			List<SodiumOptionEntry> opts = this.groups.get(i).getOptions();
			for (int j = 0, m = opts.size(); j < m; ++j) {
				if (opts.get(j).isModified()) {
					return true;
				}
			}
		}
		return false;
	}
}
