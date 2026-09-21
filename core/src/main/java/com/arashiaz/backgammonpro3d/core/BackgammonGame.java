package com.arashiaz.backgammonpro3d.core;

import com.badlogic.gdx.Game;

public class BackgammonGame extends Game {
    @Override public void create() { setScreen(new BoardScreen()); }
    @Override public void dispose() { super.dispose(); }
}
