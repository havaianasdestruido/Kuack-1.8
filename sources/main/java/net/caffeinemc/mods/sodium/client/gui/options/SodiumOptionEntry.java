package net.caffeinemc.mods.sodium.client.gui.options;

import java.util.EnumSet;
import net.minecraft.util.MathHelper;

public abstract class SodiumOptionEntry {

	public enum ControlType {
		CHECKBOX,
		SLIDER,
		CYCLER
	}

	private final String name;
	private final String tooltip;
	private final OptionImpact impact;
	private final EnumSet<OptionFlag> flags;
	private final ControlType controlType;

	protected SodiumOptionEntry(String name, String tooltip, OptionImpact impact, ControlType controlType,
			OptionFlag... flags) {
		this.name = name;
		this.tooltip = tooltip;
		this.impact = impact;
		this.controlType = controlType;
		this.flags = EnumSet.noneOf(OptionFlag.class);
		if (flags != null) {
			for (int i = 0; i < flags.length; ++i) {
				if (flags[i] != null) {
					this.flags.add(flags[i]);
				}
			}
		}
	}

	public String getName() {
		return this.name;
	}

	public String getTooltip() {
		return this.tooltip;
	}

	public OptionImpact getImpact() {
		return this.impact;
	}

	public EnumSet<OptionFlag> getFlags() {
		return this.flags;
	}

	public ControlType getControlType() {
		return this.controlType;
	}

	public abstract boolean isModified();

	public abstract void apply();

	public abstract void undo();

	public abstract String getFormattedValue();

	public void onClick(double relativeX) {
	}

	public void onDrag(double relativeX) {
	}

	public double getSliderNormalized() {
		return 0.0D;
	}

	public boolean getBooleanValue() {
		return false;
	}

	public interface BooleanGetter {
		boolean get();
	}

	public interface BooleanSetter {
		void set(boolean value);
	}

	public interface IntGetter {
		int get();
	}

	public interface IntSetter {
		void set(int value);
	}

	public interface ValueFormatter {
		String format(int value);
	}

	public static class BooleanOption extends SodiumOptionEntry {
		private final BooleanGetter getter;
		private final BooleanSetter setter;
		private boolean pendingValue;

		public BooleanOption(String name, String tooltip, OptionImpact impact, BooleanGetter getter,
				BooleanSetter setter, OptionFlag... flags) {
			super(name, tooltip, impact, ControlType.CHECKBOX, flags);
			this.getter = getter;
			this.setter = setter;
			this.pendingValue = getter.get();
		}

		@Override
		public boolean isModified() {
			return this.pendingValue != this.getter.get();
		}

		@Override
		public void apply() {
			if (isModified()) {
				this.setter.set(this.pendingValue);
			}
		}

		@Override
		public void undo() {
			this.pendingValue = this.getter.get();
		}

		@Override
		public String getFormattedValue() {
			return this.pendingValue ? "ON" : "OFF";
		}

		@Override
		public boolean getBooleanValue() {
			return this.pendingValue;
		}

		@Override
		public void onClick(double relativeX) {
			this.pendingValue = !this.pendingValue;
		}
	}

	public static class SliderOption extends SodiumOptionEntry {
		private final int min;
		private final int max;
		private final int step;
		private final IntGetter getter;
		private final IntSetter setter;
		private final ValueFormatter formatter;
		private int pendingValue;

		public SliderOption(String name, String tooltip, OptionImpact impact, int min, int max, int step,
				IntGetter getter, IntSetter setter, ValueFormatter formatter, OptionFlag... flags) {
			super(name, tooltip, impact, ControlType.SLIDER, flags);
			this.min = min;
			this.max = max;
			this.step = Math.max(1, step);
			this.getter = getter;
			this.setter = setter;
			this.formatter = formatter;
			this.pendingValue = MathHelper.clamp_int(getter.get(), min, max);
		}

		@Override
		public boolean isModified() {
			return this.pendingValue != MathHelper.clamp_int(this.getter.get(), this.min, this.max);
		}

		@Override
		public void apply() {
			if (isModified()) {
				this.setter.set(this.pendingValue);
			}
		}

		@Override
		public void undo() {
			this.pendingValue = MathHelper.clamp_int(this.getter.get(), this.min, this.max);
		}

		@Override
		public String getFormattedValue() {
			return this.formatter.format(this.pendingValue);
		}

		@Override
		public double getSliderNormalized() {
			if (this.max <= this.min) {
				return 0.0D;
			}
			return MathHelper.clamp_double((double) (this.pendingValue - this.min) / (double) (this.max - this.min),
					0.0D, 1.0D);
		}

		private void updateFromNormalized(double norm) {
			norm = MathHelper.clamp_double(norm, 0.0D, 1.0D);
			double raw = this.min + norm * (this.max - this.min);
			int stepped = this.min + (int) Math.round((raw - this.min) / (double) this.step) * this.step;
			this.pendingValue = MathHelper.clamp_int(stepped, this.min, this.max);
		}

		@Override
		public void onClick(double relativeX) {
			updateFromNormalized(relativeX);
		}

		@Override
		public void onDrag(double relativeX) {
			updateFromNormalized(relativeX);
		}
	}

	public static class CycleOption extends SodiumOptionEntry {
		private final String[] labels;
		private final IntGetter getter;
		private final IntSetter setter;
		private int pendingIndex;

		public CycleOption(String name, String tooltip, OptionImpact impact, String[] labels, IntGetter getter,
				IntSetter setter, OptionFlag... flags) {
			super(name, tooltip, impact, ControlType.CYCLER, flags);
			this.labels = labels;
			this.getter = getter;
			this.setter = setter;
			this.pendingIndex = normalizeIndex(getter.get());
		}

		private int normalizeIndex(int idx) {
			if (this.labels.length == 0) {
				return 0;
			}
			int m = idx % this.labels.length;
			return m < 0 ? m + this.labels.length : m;
		}

		@Override
		public boolean isModified() {
			return this.pendingIndex != normalizeIndex(this.getter.get());
		}

		@Override
		public void apply() {
			if (isModified()) {
				this.setter.set(this.pendingIndex);
			}
		}

		@Override
		public void undo() {
			this.pendingIndex = normalizeIndex(this.getter.get());
		}

		@Override
		public String getFormattedValue() {
			return this.labels[this.pendingIndex];
		}

		@Override
		public void onClick(double relativeX) {
			this.pendingIndex = (this.pendingIndex + 1) % this.labels.length;
		}
	}
}
