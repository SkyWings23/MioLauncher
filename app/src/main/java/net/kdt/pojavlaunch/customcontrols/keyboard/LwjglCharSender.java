package net.kdt.pojavlaunch.customcontrols.keyboard;

import org.lwjgl.glfw.CallbackBridge;

/**
 * 将输入法输入转发到游戏（MC 聊天/命令/存档名输入）。
 * 字符经 CallbackBridge.sendChar 发送；回车/退格用对应 GLFW 键码。
 */
public class LwjglCharSender implements CharacterSenderStrategy {
    @Override
    public void sendBackspace() {
        sendKeycode(net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_BACKSPACE);
    }

    @Override
    public void sendEnter() {
        sendKeycode(net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_ENTER);
    }

    @Override
    public void sendChar(char c) {
        CallbackBridge.sendChar(c, CallbackBridge.getCurrentMods());
    }

    @Override
    public void sendChar(String s) {
        for (int i = 0; i < s.length(); i++) {
            CallbackBridge.sendChar(s.charAt(i), CallbackBridge.getCurrentMods());
        }
    }

    private void sendKeycode(int keycode) {
        CallbackBridge.sendKeyPress(keycode, CallbackBridge.getCurrentMods(), true);
        CallbackBridge.sendKeyPress(keycode, CallbackBridge.getCurrentMods(), false);
    }
}
