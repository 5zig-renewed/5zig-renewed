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
 * Up to 1.21.8 this extended the Minecraft resource location so it could be handed to every vanilla
 * API unchanged. 1.21.9's rename to {@link Identifier} also made the class {@code final}, so the
 * vanilla value is now held by composition and unwrapped explicitly through
 * {@link #toIdentifier()} at the (few) call sites that talk to the game.
 * <p>
 * Both namespace and path are sanitised the way the 1.15 implementation did it - lower case,
 * stripped down to {@code [a-z0-9/._-]} - because {@link Identifier} still rejects anything else
 * with a hard exception.
 */
public class ResourceLocation implements IResourceLocation {

	private static final String DEFAULT_NAMESPACE = "minecraft";
	private static final String FALLBACK_PATH = "missing";

	private final Identifier identifier;

	public ResourceLocation(String resourcePath) {
		this(namespaceOf(resourcePath), pathOf(resourcePath));
	}

	public ResourceLocation(String resourceDomain, String resourcePath) {
		this.identifier = Identifier.fromNamespaceAndPath(sanitizeNamespace(resourceDomain), sanitizePath(resourcePath));
	}

	private ResourceLocation(Identifier identifier) {
		this.identifier = identifier;
	}

	/**
	 * The Minecraft value backing this resource location.
	 */
	public Identifier toIdentifier() {
		return identifier;
	}

	/**
	 * Wraps a Minecraft identifier without copying it if it already is a 5zig one.
	 *
	 * @param identifier the identifier to wrap.
	 * @return the 5zig resource location, or {@code null} if the argument was {@code null}.
	 */
	public static ResourceLocation fromMinecraft(Identifier identifier) {
		if (identifier == null) {
			return null;
		}
		if (identifier instanceof Identifier && identifier.getClass() == Identifier.class) {
			return new ResourceLocation(identifier);
		}
		return new ResourceLocation(identifier.getNamespace(), identifier.getPath());
	}

	/**
	 * @deprecated use {@link #fromMinecraft(Identifier)}. Kept because the 1.15 platform called
	 * this on obfuscated names.
	 */
	@Deprecated
	public static ResourceLocation fromObfuscated(Identifier identifier) {
		return fromMinecraft(identifier);
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
		return identifier.getPath();
	}

	@Override
	public String callGetResourceDomain() {
		return identifier.getNamespace();
	}

	@Override
	public String toString() {
		return identifier.toString();
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (other instanceof ResourceLocation) {
			return identifier.equals(((ResourceLocation) other).identifier);
		}
		return false;
	}

	@Override
	public int hashCode() {
		return identifier.hashCode();
	}
}
