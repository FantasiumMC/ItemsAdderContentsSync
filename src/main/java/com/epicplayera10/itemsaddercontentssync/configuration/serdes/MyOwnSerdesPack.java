package com.epicplayera10.itemsaddercontentssync.configuration.serdes;

import com.epicplayera10.itemsaddercontentssync.configuration.serdes.serializers.GitCredentialsSerializer;
import eu.okaeri.configs.serdes.OkaeriSerdesPack;
import eu.okaeri.configs.serdes.SerdesRegistry;
import org.jetbrains.annotations.NotNull;

public class MyOwnSerdesPack implements OkaeriSerdesPack {
    @Override
    public void register(@NotNull SerdesRegistry registry) {
        registry.register(new GitCredentialsSerializer());
    }
}
