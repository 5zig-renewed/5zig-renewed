/*
 * Copyright (c) 2019-2020 5zig Reborn
 *
 * This file is part of The 5zig Mod
 * The 5zig Mod is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The 5zig Mod is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with The 5zig Mod.  If not, see <http://www.gnu.org/licenses/>.
 */

package eu.the5zig.mod.util;

import oshi.SystemInfo;
import oshi.hardware.PowerSource;

import java.util.List;

/**
 * Reads the battery state of the machine through OSHI.
 * <p>
 * The 1.15 implementation bound {@code Kernel32.SYSTEM_POWER_STATUS} through JNA, which only worked
 * on Windows. OSHI is shipped with the 1.18.2 client anyway and reports the same information on
 * every platform, so the battery HUD item works on Linux and macOS as well.
 * <p>
 * Returns {@code null} when the machine has no battery (or when the platform cannot be queried);
 * the battery module renders "100%" in that case, exactly like 5zig did on a desktop.
 */
public final class SystemPowerStatus {

	private static ISystemPowerStatus cached;
	private static boolean resolved;

	private SystemPowerStatus() {
	}

	public static synchronized ISystemPowerStatus get() {
		if (!resolved) {
			resolved = true;
			cached = resolve();
		}
		return cached;
	}

	private static ISystemPowerStatus resolve() {
		try {
			List<PowerSource> powerSources = new SystemInfo().getHardware().getPowerSources();
			if (powerSources.isEmpty()) {
				return null;
			}
			return new OshiPowerSource(powerSources.get(0));
		} catch (Throwable throwable) {
			// Native backends are allowed to be unavailable (containers, unsupported OS, ...).
			return null;
		}
	}

	private static final class OshiPowerSource implements ISystemPowerStatus {

		private final PowerSource source;

		private OshiPowerSource(PowerSource source) {
			this.source = source;
		}

		@Override
		public boolean isPluggedIn() {
			return source.isPowerOnLine() || source.isCharging();
		}

		@Override
		public int getBatteryPercentage() {
			double capacity = source.getRemainingCapacityPercent();
			if (capacity <= 1.0D) {
				capacity *= 100.0D;
			}
			int percentage = (int) Math.round(capacity);
			return Math.max(0, Math.min(100, percentage));
		}

		@Override
		public int getRemainingLifeTime() {
			double remaining = source.getTimeRemainingEstimated();
			// OSHI reports -1 (unknown) or -2 (charging) for the time estimate.
			return remaining > 0 ? (int) remaining : 0;
		}
	}
}
