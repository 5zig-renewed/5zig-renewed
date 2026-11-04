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


package eu.the5zig.mod.platform;

import eu.the5zig.mod.util.IResourceLocation;

import java.util.Locale;

/**
 * The platform implementation of {@link IResourceLocation}.
 * <p>
 * Extends the Minecraft resource location so that it can be handed to every vanilla API that expects
 * one, while still being usable by the core through the 5zig interface. Since 1.16 validates
 * namespace and path strictly (an illegal character throws {@link net.minecraft.ResourceLocationException}),
 * both are sanitized the same way the 1.15 implementation did it: lower case and stripped down to
 * {@code [a-z0-9/._-]}.
 */
public class ResourceLocation extends net.minecraft.resources.ResourceLocation implements IResourceLocation {

	private static final String DEFAULT_NAMESPACE = "minecraft";
	private static final String FALLBACK_PATH = "missing";

	public ResourceLocation(String resourcePath) {
		super(namespaceOf(resourcePath), pathOf(resourcePath));
	}

	public ResourceLocation(String resourceDomain, String resourcePath) {
		super(sanitizeNamespace(resourceDomain), sanitizePath(resourcePath));
	}

	/**
	 * Wraps a Minecraft resource location without copying it if it already is a 5zig one.
	 *
	 * @param resourceLocation the resource location to wrap.
	 * @return the 5zig resource location, or {@code null} if the argument was {@code null}.
	 */
	public static ResourceLocation fromMinecraft(net.minecraft.resources.ResourceLocation resourceLocation) {
		if (resourceLocation == null) {
			return null;
		}
		if (resourceLocation instanceof ResourceLocation) {
			return (ResourceLocation) resourceLocation;
		}
		return new ResourceLocation(resourceLocation.getNamespace(), resourceLocation.getPath());
	}

	/**
	 * @deprecated use {@link #fromMinecraft(net.minecraft.resources.ResourceLocation)}. Kept because the
	 * 1.15 platform called this on obfuscated names.
	 */
	@Deprecated
	public static ResourceLocation fromObfuscated(net.minecraft.resources.ResourceLocation resourceLocation) {
		return fromMinecraft(resourceLocation);
	}

	private static String namespaceOf(String resourcePath) {
		if (resourcePath == null) {
			return DEFAULT_NAMESPACE;
		}
		int separator = resourcePath.indexOf(':');
		if (separator == -1) {
			return DEFAULT_NAMESPACE;
		}
		return sanitizeNamespace(resourcePath.substring(0, separator));
	}

	private static String pathOf(String resourcePath) {
		if (resourcePath == null) {
			return FALLBACK_PATH;
		}
		int separator = resourcePath.indexOf(':');
		if (separator == -1) {
			return sanitizePath(resourcePath);
		}
		return sanitizePath(resourcePath.substring(separator + 1));
	}

	private static String sanitizeNamespace(String namespace) {
		if (namespace == null) {
			return DEFAULT_NAMESPACE;
		}
		String sanitized = namespace.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "");
		return sanitized.isEmpty() ? DEFAULT_NAMESPACE : sanitized;
	}

	private static String sanitizePath(String path) {
		if (path == null) {
			return FALLBACK_PATH;
		}
		String sanitized = path.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "");
		return sanitized.isEmpty() ? FALLBACK_PATH : sanitized;
	}

	@Override
	public String callGetResourcePath() {
		return getPath();
	}

	@Override
	public String callGetResourceDomain() {
		return getNamespace();
	}
}
