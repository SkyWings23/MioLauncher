package net.kdt.pojavlaunch.customcontrols.keyboard;

public interface CharacterSenderStrategy {
    void sendBackspace();
    void sendEnter();
    void sendChar(char c);
    void sendChar(String s);
}
