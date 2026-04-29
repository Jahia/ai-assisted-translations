package org.jahia.community.translation.assisted.service.impl.deepl;

import org.apache.commons.lang.StringUtils;
import org.jahia.community.translation.assisted.service.AssistedTranslationResponse;

import java.util.ArrayList;
import java.util.List;

public class DeepLAssistedTranslationResponseImpl implements AssistedTranslationResponse {

    private final List<String> messages = new ArrayList<>();
    private boolean state;

    public DeepLAssistedTranslationResponseImpl(boolean state, String reason) {
        this.state = state;
        addMessage(reason);
    }

    @Override
    public boolean isSuccessful() {
        return state;
    }

    @Override
    public void setSuccessful(boolean state) {
        this.state = state;
    }

    @Override
    public String getMessage() {
        return String.join("\n", messages);
    }

    @Override
    public void addMessage(String text) {
        if (StringUtils.isNotBlank(text)) {
            messages.add(text);
        }
    }

    @Override
    public AssistedTranslationResponse merge(AssistedTranslationResponse other) {
        state = state || other.isSuccessful();
        messages.addAll(((DeepLAssistedTranslationResponseImpl) other).messages);
        return this;
    }
}
