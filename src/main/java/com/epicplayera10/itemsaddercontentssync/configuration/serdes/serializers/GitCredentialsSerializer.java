package com.epicplayera10.itemsaddercontentssync.configuration.serdes.serializers;

import eu.okaeri.configs.schema.GenericsDeclaration;
import eu.okaeri.configs.serdes.DeserializationData;
import eu.okaeri.configs.serdes.ObjectSerializer;
import eu.okaeri.configs.serdes.SerializationData;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.jetbrains.annotations.NotNull;

public class GitCredentialsSerializer implements ObjectSerializer<UsernamePasswordCredentialsProvider> {
    @Override
    public boolean supports(@NotNull Class<? super UsernamePasswordCredentialsProvider> type) {
        return UsernamePasswordCredentialsProvider.class.isAssignableFrom(type);
    }

    @Override
    public void serialize(@NotNull UsernamePasswordCredentialsProvider object, @NotNull SerializationData data, @NotNull GenericsDeclaration generics) {
        CredentialItem.Username username = new CredentialItem.Username();
        CredentialItem.Password password = new CredentialItem.Password();
        object.get(null, username, password);

        data.add("username", username.getValue());
        data.add("password", String.valueOf(password.getValue()));
    }

    @Override
    public UsernamePasswordCredentialsProvider deserialize(@NotNull DeserializationData data, @NotNull GenericsDeclaration generics) {
        String username = data.get("username", String.class);
        String password = data.get("password", String.class);

        return new UsernamePasswordCredentialsProvider(username, password);
    }
}
