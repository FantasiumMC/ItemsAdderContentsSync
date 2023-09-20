package com.epicplayera10.itemsaddercontentssync.configuration;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.NameModifier;
import eu.okaeri.configs.annotation.NameStrategy;
import eu.okaeri.configs.annotation.Names;
import eu.okaeri.validator.annotation.Nullable;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

@Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
public class PluginConfiguration extends OkaeriConfig {
    @Comment("")
    @Comment("! NIE DOTYKAĆ !")
    public String lastCommitHash = "";

    @Comment("")
    @Comment("Czy plugin powinien zsynchronizować paczkę (pobrać nową i wgrać) przy starcie serwera")
    public boolean syncOnStartup = true;

    @Comment("")
    @Comment("Co ile minut ma się odpalać timer który synchronizuje paczkę.")
    @Comment("Ustaw na -1 jeśli nie chcesz aby paczka się automatycznie synchronizowała")
    public int syncRepeatMinutes = 5;

    @Comment("")
    @Comment("Link do repo gdzie znajduje się paczka")
    public String packRepoUrl = "https://github.com/example/example-repo";

    @Comment("")
    @Comment("Branch")
    public String branch = "master";

    @Comment("")
    @Comment("Dane logowania do repo. (Opcjonalne)")
    @Nullable
    public UsernamePasswordCredentialsProvider credentials = new UsernamePasswordCredentialsProvider("someuser", "somepasswd1");
}
