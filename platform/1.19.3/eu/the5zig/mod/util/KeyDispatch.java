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

import eu.the5zig.mod.MinecraftFactory;
import eu.the5zig.mod.The5zigMod;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Key press queue for the 1.19.x line.
 * <p>
 * 1.15 drained the LWJGL2 keyboard queue once per tick in {@code IVariables#dispatchKeypresses()}
 * and fired a {@link eu.the5zig.mod.event.KeyPressEvent} for each press. 1.19.x has no pollable
 * queue, so the presses are queued from the {@code KeyboardHandler} mixin and drained from the same
 * per-tick entry point.
 * <p>
 * Draining in the tick rather than firing from the keyboard callback matters: several core
 * listeners open a screen straight from a {@code KeyPressEvent}, and opening a screen in the middle
 * of {@code KeyboardHandler#keyPress} would run against the screen vanilla is still processing the
 * event for. The queue only holds the presses of a single frame (GLFW events are pumped from the
 * client thread right before the tick), so it never grows.
 */
public final class KeyDispatch {

	/**
	 * Presses that can accumulate before more are dropped; only a runaway input loop could reach it.
	 */
	private static final int LIMIT = 64;

	private static final Deque<Integer> QUEUE = new ArrayDeque<>();

	private static final Integer[] EMPTY = new Integer[0];

	private static boolean dispatchLogged;
	private static boolean errorLogged;

	private KeyDispatch() {
	}

	/**
	 * Records a key press. Called from the keyboard hook, on the client thread.
	 */
	public static void enqueue(int keyCode) {
		synchronized (QUEUE) {
			if (QUEUE.size() < LIMIT) {
				QUEUE.add(keyCode);
			}
		}
	}

	/**
	 * Fires a {@code KeyPressEvent} for every press recorded since the previous tick and then runs
	 * the state-based key handling. Called once per tick before the core's tick listeners.
	 */
	public static void dispatch() {
		Integer[] keys;
		synchronized (QUEUE) {
			if (QUEUE.isEmpty()) {
				keys = EMPTY;
			} else {
				keys = QUEUE.toArray(new Integer[0]);
				QUEUE.clear();
			}
		}
		if (!CoreState.isReady()) {
			return;
		}
		for (Integer key : keys) {
			if (key == null) {
				continue;
			}
			try {
				ModMenuKey.onKeyPress(key);
				MinecraftFactory.getClassProxyCallback().fireKeyPressEvent(key);
			} catch (Throwable throwable) {
				if (!errorLogged) {
					errorLogged = true;
					The5zigMod.logger.warn("[5zig Renewed] Could not dispatch a key press", throwable);
				}
			}
		}
		if (keys.length > 0 && !dispatchLogged) {
			dispatchLogged = true;
			The5zigMod.logger.info("[5zig Renewed] Key event dispatch active");
		}
		ModMenuKey.tick();
	}
}
