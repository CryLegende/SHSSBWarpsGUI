package org.Cry_Legende.sHSSBWarpsGUI.config;

import org.bukkit.Sound;

public class SoundConfig {
    public final Sound sound;
    public final float volume;
    public final float pitch;

    public SoundConfig(Sound sound, float volume, float pitch) {
        this.sound = sound;
        this.volume = volume;
        this.pitch = pitch;
    }
}