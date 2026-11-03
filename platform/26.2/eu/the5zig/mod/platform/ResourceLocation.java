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
import net.minecraft.resources.Identifier;

import java.util.Locale;

/**
 * The platform implementation of {@link IResourceLocation}.
 * <p>
 * 26.x note: {@code net.minecraft.resources.ResourceLocation} was renamed to {@link Identifier} and
 * made {@code final}, so this can no longer <em>extend</em> the vanilla type (as the 1.20.1 port
 * did). It is a small value wrapper instead: the core keeps passing {@link IResourceLocation}s
 * around, and {@link Variables} converts them with {@link #toIdentifier()}.
 */
public class ResourceLocation implements IResourceLocation {

	private static final String DEFAULT_NAMESPACE = "minecraft";
	private static final String FALLBACK_PATH = "missing";

	private final String namespace;
	private final String path;
	private final Identifier identifier;

	public ResourceLocation(String resourcePath) {
		this(namespaceOf(resourcePath), pathOf(resourcePath));
	}

	public ResourceLocation(String resourceDomain, String resourcePath) {
		this.namespace = sanitizeNamespace(resourceDomain);
		this.path = sanitizePath(resourcePath);
		this.identifier = Identifier.fromNamespaceAndPath(this.namespace, this.path);
	}

	/**
	 * Wraps a Minecraft identifier without copying it if it already is a 5zig one.
	 *
	 * @param resourceLocation the resource location to wrap.
	 * @return the 5zig resource location, or {@code null} if the argument was {@code null}.
	 */
	public static ResourceLocation fromMinecraft(Identifier resourceLocation) {
		if (resourceLocation == null) {
			return null;
		}
		return new ResourceLocation(resourceLocation.getNamespace(), resourceLocation.getPath());
	}

	public Identifier toIdentifier() {
		return identifier;
	}

	@Override
	public String callGetResourcePath() {
		return path;
	}

	@Override
	public String callGetResourceDomain() {
		return namespace;
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
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof ResourceLocation)) {
			return false;
		}
		ResourceLocation that = (ResourceLocation) other;
		return namespace.equals(that.namespace) && path.equals(that.path);
	}

	@Override
	public int hashCode() {
		return identifier.hashCode();
	}

	@Override
	public String toString() {
		return namespace + ":" + path;
	}
}
