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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the battery state of the machine.
 * <p>
 * The 1.19.2 platform asked OSHI, and the 1.15 one bound {@code Kernel32} through JNA. Neither path
 * works here: Minecraft 1.16 ships {@code oshi-core 1.1}, whose hardware layer predates the
 * {@code PowerSource} API entirely ({@code HardwareAbstractionLayer#getPowerSources()} arrived in
 * OSHI 3.x), and this platform deliberately ships no extra native stack. The battery HUD therefore
 * reads the operating system directly:
 * <ul>
 *     <li>Linux: {@code /sys/class/power_supply/BAT*} ({@code capacity}, {@code status}),</li>
 *     <li>macOS: {@code pmset -g batt} output,</li>
 *     <li>Windows: not supported — {@link #get()} returns {@code null} and the HUD renders the same
 *     "100 %" placeholder it shows on a machine without a battery.</li>
 * </ul>
 * Returns {@code null} when the machine has no battery (or when the platform cannot be queried).
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
			String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
			if (os.contains("linux")) {
				return readLinux();
			}
			if (os.contains("mac") || os.contains("darwin")) {
				return readMac();
			}
		} catch (Throwable throwable) {
			// Reading the host battery is allowed to fail (containers, permission, no sysfs).
		}
		return null;
	}

	// ------------------------------------------------------------------------------------------
	// Linux
	// ------------------------------------------------------------------------------------------

	private static ISystemPowerStatus readLinux() {
		File supply = new File("/sys/class/power_supply");
		File[] entries = supply.listFiles();
		if (entries == null) {
			return null;
		}
		for (File entry : entries) {
			if (!entry.getName().startsWith("BAT")) {
				continue;
			}
			Integer capacity = readInt(new File(entry, "capacity"));
			if (capacity == null) {
				continue;
			}
			String status = readString(new File(entry, "status"));
			boolean pluggedIn = status != null
					&& (status.equalsIgnoreCase("Charging") || status.equalsIgnoreCase("Full"));
			int remainingSeconds = 0;
			Integer seconds = readInt(new File(entry, "time_to_empty_now"));
			if (seconds != null && seconds > 0) {
				remainingSeconds = seconds;
			}
			return new SimplePowerStatus(pluggedIn, capacity, remainingSeconds);
		}
		return null;
	}

	private static Integer readInt(File file) {
		String value = readString(file);
		if (value == null) {
			return null;
		}
		try {
			return Integer.valueOf(value.trim());
		} catch (NumberFormatException ignored) {
			return null;
		}
	}

	private static String readString(File file) {
		if (!file.isFile()) {
			return null;
		}
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new FileReader(file));
			String line = reader.readLine();
			return line == null ? null : line.trim();
		} catch (Exception ignored) {
			return null;
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (Exception ignored) {
					// Nothing useful to do.
				}
			}
		}
	}

	// ------------------------------------------------------------------------------------------
	// macOS
	// ------------------------------------------------------------------------------------------

	private static final Pattern MAC_PERCENT = Pattern.compile("(\\d{1,3})%");
	private static final Pattern MAC_REMAINING = Pattern.compile("(\\d+):(\\d{2}) remaining");

	private static ISystemPowerStatus readMac() {
		List<String> lines = run("pmset", "-g", "batt");
		if (lines == null) {
			return null;
		}
		boolean pluggedIn = false;
		int percentage = -1;
		int remainingSeconds = 0;
		for (String line : lines) {
			String lower = line.toLowerCase(Locale.ROOT);
			if (lower.contains("ac power") || lower.contains("charging")) {
				pluggedIn = true;
			}
			Matcher percent = MAC_PERCENT.matcher(line);
			if (percentage < 0 && percent.find()) {
				percentage = Integer.parseInt(percent.group(1));
			}
			Matcher remaining = MAC_REMAINING.matcher(line);
			if (remaining.find()) {
				remainingSeconds = Integer.parseInt(remaining.group(1)) * 3600 + Integer.parseInt(remaining.group(2)) * 60;
			}
		}
		if (percentage < 0) {
			return null;
		}
		return new SimplePowerStatus(pluggedIn, percentage, remainingSeconds);
	}

	private static List<String> run(String... command) {
		Process process = null;
		try {
			process = new ProcessBuilder(command).redirectErrorStream(true).start();
			BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
			List<String> lines = new ArrayList<String>();
			String line;
			while ((line = reader.readLine()) != null) {
				lines.add(line);
			}
			return lines;
		} catch (Exception ignored) {
			return null;
		} finally {
			if (process != null) {
				process.destroy();
			}
		}
	}

	// ------------------------------------------------------------------------------------------

	private static final class SimplePowerStatus implements ISystemPowerStatus {

		private final boolean pluggedIn;
		private final int percentage;
		private final int remainingSeconds;

		private SimplePowerStatus(boolean pluggedIn, int percentage, int remainingSeconds) {
			this.pluggedIn = pluggedIn;
			this.percentage = Math.max(0, Math.min(100, percentage));
			this.remainingSeconds = Math.max(0, remainingSeconds);
		}

		@Override
		public boolean isPluggedIn() {
			return pluggedIn;
		}

		@Override
		public int getBatteryPercentage() {
			return percentage;
		}

		@Override
		public int getRemainingLifeTime() {
			return remainingSeconds;
		}
	}
}
