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

import com.mojang.blaze3d.platform.InputConstants;

/**
 * Puts the core's {@link Keyboard} {@code KEY_*} constants into the code space 26.3 actually uses.
 * <p>
 * {@link Keyboard#init(Keyboard.KeyboardHandler)} fills those fields with GLFW key codes
 * ({@code KEY_ESCAPE = 256}, {@code KEY_A = 65}), which was correct while the platform polled
 * {@code GLFW.glfwGetKey(window, key)} and received GLFW key codes in {@code Screen#keyPressed}.
 * 26.3 replaced GLFW with SDL: key state is read from {@code SDL_GetKeyboardState()} indexed by
 * <b>SDL scancode</b>, and {@code net.minecraft.client.input.KeyEvent#key()} carries that same
 * scancode. GLFW is not part of a 26.3 install at all, so its numbering has to go.
 * <p>
 * Rather than translating at every boundary (the core compares {@code key == Keyboard.KEY_TAB} in a
 * dozen screens, polls {@code Keyboard.isKeyDown(Keyboard.KEY_LMENU)} for shift-hover and stores
 * {@code Keyboard.KEY_*} values in text macros), the platform installs the 26.3 code space once,
 * right after {@link Keyboard#init(Keyboard.KeyboardHandler)}. Every {@code Keyboard.KEY_*} read -
 * in the core or in a plugin - then yields the same value the game reports for that key, which is
 * exactly the contract {@code Keyboard.init} defines for a platform.
 */
public final class InputKeyCodes {

	/**
	 * Keyboard scancode of the keypad {@code .} key; 26.3 has no named constant for it
	 * ({@code KEY_NUMPADCOMMA} is the AS/400 comma, not the decimal separator).
	 */
	private static final int KEYPAD_PERIOD = 99;

	/** Keyboard scancode of the keypad {@code -} key; like {@link #KEYPAD_PERIOD}, unnamed in 26.3. */
	private static final int KEYPAD_SUBTRACT = 86;

	private InputKeyCodes() {
	}

	/**
	 * Overwrites the GLFW numbering installed by {@code Keyboard.init} with the 26.3
	 * {@link InputConstants} values. Idempotent.
	 */
	public static void install() {
		Keyboard.KEY_NONE = -1;

		Keyboard.KEY_ESCAPE = InputConstants.KEY_ESCAPE;
		Keyboard.KEY_1 = InputConstants.KEY_1;
		Keyboard.KEY_2 = InputConstants.KEY_2;
		Keyboard.KEY_3 = InputConstants.KEY_3;
		Keyboard.KEY_4 = InputConstants.KEY_4;
		Keyboard.KEY_5 = InputConstants.KEY_5;
		Keyboard.KEY_6 = InputConstants.KEY_6;
		Keyboard.KEY_7 = InputConstants.KEY_7;
		Keyboard.KEY_8 = InputConstants.KEY_8;
		Keyboard.KEY_9 = InputConstants.KEY_9;
		Keyboard.KEY_0 = InputConstants.KEY_0;
		Keyboard.KEY_MINUS = InputConstants.KEY_MINUS;
		Keyboard.KEY_EQUALS = InputConstants.KEY_EQUALS;
		Keyboard.KEY_BACK = InputConstants.KEY_BACKSPACE;
		Keyboard.KEY_TAB = InputConstants.KEY_TAB;
		Keyboard.KEY_Q = InputConstants.KEY_Q;
		Keyboard.KEY_W = InputConstants.KEY_W;
		Keyboard.KEY_E = InputConstants.KEY_E;
		Keyboard.KEY_R = InputConstants.KEY_R;
		Keyboard.KEY_T = InputConstants.KEY_T;
		Keyboard.KEY_Y = InputConstants.KEY_Y;
		Keyboard.KEY_U = InputConstants.KEY_U;
		Keyboard.KEY_I = InputConstants.KEY_I;
		Keyboard.KEY_O = InputConstants.KEY_O;
		Keyboard.KEY_P = InputConstants.KEY_P;
		Keyboard.KEY_LBRACKET = InputConstants.KEY_LBRACKET;
		Keyboard.KEY_RBRACKET = InputConstants.KEY_RBRACKET;
		Keyboard.KEY_RETURN = InputConstants.KEY_RETURN;
		Keyboard.KEY_LCONTROL = InputConstants.KEY_LCONTROL;
		Keyboard.KEY_A = InputConstants.KEY_A;
		Keyboard.KEY_S = InputConstants.KEY_S;
		Keyboard.KEY_D = InputConstants.KEY_D;
		Keyboard.KEY_F = InputConstants.KEY_F;
		Keyboard.KEY_G = InputConstants.KEY_G;
		Keyboard.KEY_H = InputConstants.KEY_H;
		Keyboard.KEY_J = InputConstants.KEY_J;
		Keyboard.KEY_K = InputConstants.KEY_K;
		Keyboard.KEY_L = InputConstants.KEY_L;
		Keyboard.KEY_SEMICOLON = InputConstants.KEY_SEMICOLON;
		Keyboard.KEY_APOSTROPHE = InputConstants.KEY_APOSTROPHE;
		Keyboard.KEY_GRAVE = InputConstants.KEY_GRAVE;
		Keyboard.KEY_LSHIFT = InputConstants.KEY_LSHIFT;
		Keyboard.KEY_BACKSLASH = InputConstants.KEY_BACKSLASH;
		Keyboard.KEY_Z = InputConstants.KEY_Z;
		Keyboard.KEY_X = InputConstants.KEY_X;
		Keyboard.KEY_C = InputConstants.KEY_C;
		Keyboard.KEY_V = InputConstants.KEY_V;
		Keyboard.KEY_B = InputConstants.KEY_B;
		Keyboard.KEY_N = InputConstants.KEY_N;
		Keyboard.KEY_M = InputConstants.KEY_M;
		Keyboard.KEY_COMMA = InputConstants.KEY_COMMA;
		Keyboard.KEY_PERIOD = InputConstants.KEY_PERIOD;
		Keyboard.KEY_SLASH = InputConstants.KEY_SLASH;
		Keyboard.KEY_RSHIFT = InputConstants.KEY_RSHIFT;
		Keyboard.KEY_MULTIPLY = InputConstants.KEY_MULTIPLY;
		Keyboard.KEY_LMENU = InputConstants.KEY_LALT;
		Keyboard.KEY_SPACE = InputConstants.KEY_SPACE;
		Keyboard.KEY_CAPITAL = InputConstants.KEY_CAPSLOCK;
		Keyboard.KEY_F1 = InputConstants.KEY_F1;
		Keyboard.KEY_F2 = InputConstants.KEY_F2;
		Keyboard.KEY_F3 = InputConstants.KEY_F3;
		Keyboard.KEY_F4 = InputConstants.KEY_F4;
		Keyboard.KEY_F5 = InputConstants.KEY_F5;
		Keyboard.KEY_F6 = InputConstants.KEY_F6;
		Keyboard.KEY_F7 = InputConstants.KEY_F7;
		Keyboard.KEY_F8 = InputConstants.KEY_F8;
		Keyboard.KEY_F9 = InputConstants.KEY_F9;
		Keyboard.KEY_F10 = InputConstants.KEY_F10;
		Keyboard.KEY_F11 = InputConstants.KEY_F11;
		Keyboard.KEY_F12 = InputConstants.KEY_F12;
		Keyboard.KEY_F13 = InputConstants.KEY_F13;
		Keyboard.KEY_F14 = InputConstants.KEY_F14;
		Keyboard.KEY_F15 = InputConstants.KEY_F15;
		Keyboard.KEY_F16 = InputConstants.KEY_F16;
		Keyboard.KEY_F17 = InputConstants.KEY_F17;
		Keyboard.KEY_F18 = InputConstants.KEY_F18;
		Keyboard.KEY_F19 = InputConstants.KEY_F19;
		Keyboard.KEY_NUMLOCK = InputConstants.KEY_NUMLOCK;
		Keyboard.KEY_SCROLL = InputConstants.KEY_SCROLLLOCK;
		Keyboard.KEY_NUMPAD0 = InputConstants.KEY_NUMPAD0;
		Keyboard.KEY_NUMPAD1 = InputConstants.KEY_NUMPAD1;
		Keyboard.KEY_NUMPAD2 = InputConstants.KEY_NUMPAD2;
		Keyboard.KEY_NUMPAD3 = InputConstants.KEY_NUMPAD3;
		Keyboard.KEY_NUMPAD4 = InputConstants.KEY_NUMPAD4;
		Keyboard.KEY_NUMPAD5 = InputConstants.KEY_NUMPAD5;
		Keyboard.KEY_NUMPAD6 = InputConstants.KEY_NUMPAD6;
		Keyboard.KEY_NUMPAD7 = InputConstants.KEY_NUMPAD7;
		Keyboard.KEY_NUMPAD8 = InputConstants.KEY_NUMPAD8;
		Keyboard.KEY_NUMPAD9 = InputConstants.KEY_NUMPAD9;
		Keyboard.KEY_NUMPADENTER = InputConstants.KEY_NUMPADENTER;
		Keyboard.KEY_ADD = InputConstants.KEY_ADD;
		Keyboard.KEY_SUBTRACT = KEYPAD_SUBTRACT;
		Keyboard.KEY_DECIMAL = KEYPAD_PERIOD;
		Keyboard.KEY_RCONTROL = InputConstants.KEY_RCONTROL;
		Keyboard.KEY_SYSRQ = InputConstants.KEY_PRINTSCREEN;
		Keyboard.KEY_RMENU = InputConstants.KEY_RALT;
		Keyboard.KEY_PAUSE = InputConstants.KEY_PAUSE;
		Keyboard.KEY_HOME = InputConstants.KEY_HOME;
		Keyboard.KEY_UP = InputConstants.KEY_UP;
		Keyboard.KEY_PRIOR = InputConstants.KEY_PAGEUP;
		Keyboard.KEY_LEFT = InputConstants.KEY_LEFT;
		Keyboard.KEY_RIGHT = InputConstants.KEY_RIGHT;
		Keyboard.KEY_END = InputConstants.KEY_END;
		Keyboard.KEY_DOWN = InputConstants.KEY_DOWN;
		Keyboard.KEY_NEXT = InputConstants.KEY_PAGEDOWN;
		Keyboard.KEY_INSERT = InputConstants.KEY_INSERT;
		Keyboard.KEY_DELETE = InputConstants.KEY_DELETE;
		Keyboard.KEY_LMETA = InputConstants.KEY_LGUI;
		Keyboard.KEY_RMETA = InputConstants.KEY_RGUI;
	}
}
