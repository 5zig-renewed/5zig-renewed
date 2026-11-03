/*
 * Copyright (c) 2019-2020 5zig Reborn
 * Copyright (c) 2015-2019 5zig
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

package eu.the5zig.mod;

import eu.the5zig.mod.util.ClassProxyCallback;
import eu.the5zig.mod.util.IVariables;

/**
 * Service locator for the platform layer.
 * <p>
 * The 1.15 build discovered its platform with {@code Class.forName("Variables")}. That still works
 * on a plain Fabric class loader, but Forge's transforming loader does not resolve classes from the
 * <em>default package</em> by name, so the lookup is now a fallback: every loader injects its
 * platform instance through {@link #bootstrap(IVariables)} before anything touches
 * {@link The5zigMod} (whose static initialiser reads {@link #getVars()} immediately).
 */
public class MinecraftFactory {

	private static IVariables variables;
	private static ClassProxyCallback classProxyCallback;

	static {
		try {
			variables = (IVariables) Class.forName("eu.the5zig.mod.platform.Variables").getDeclaredConstructor().newInstance();
		} catch (Throwable ignored) {
			// Expected on Forge/NeoForge: the loader injects the platform instead.
		}
	}

	/**
	 * Installs the platform implementation. Must be called before {@link The5zigMod} is touched;
	 * later calls with a different instance are ignored.
	 */
	public static void bootstrap(IVariables implementation) {
		if (variables == null) {
			variables = implementation;
		}
	}

	/**
	 * @return the installed platform, or {@code null} if the loader has not injected one yet.
	 */
	public static IVariables getVarsOrNull() {
		return variables;
	}

	public static IVariables getVars() {
		IVariables instance = variables;
		if (instance == null) {
			throw new IllegalStateException("The 5zig Mod platform layer was not installed; "
					+ "the loader must call MinecraftFactory.bootstrap(...) before the core initialises");
		}
		return instance;
	}

	public static void setClassProxyCallback(ClassProxyCallback classProxyCallback) {
		MinecraftFactory.classProxyCallback = classProxyCallback;
	}

	public static ClassProxyCallback getClassProxyCallback() {
		if(classProxyCallback == null && !The5zigMod.hasBeenInitialized()) {
			The5zigMod.init();
		}
		return classProxyCallback;
	}

}
