package com.epicplayera10.itemsaddercontentssync.configuration;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.NameModifier;
import eu.okaeri.configs.annotation.NameStrategy;
import eu.okaeri.configs.annotation.Names;
import eu.okaeri.validator.annotation.Nullable;

@Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
public class PluginConfiguration extends OkaeriConfig {
    @Comment("")
    @Comment("! NIE DOTYKAĆ !")
    public String lastCommitHash = "";

    @Comment("")
    @Comment("Czy plugin powinien zsynchronizować paczkę (pobrać nową i wgrać) przy starcie serwera")
    public boolean syncOnStartup = false;

    @Comment("")
    @Comment("Link do repo gdzie znajduje się paczka")
    public String packRepoUrl = "https://github.com/example/example-repo";

    @Comment("")
    @Comment("Token do sklonowania repo (Opcjonalne)")
    @Nullable
    public String accessToken = null;
}
