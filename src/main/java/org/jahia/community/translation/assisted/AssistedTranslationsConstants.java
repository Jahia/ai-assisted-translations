package org.jahia.community.translation.assisted;

public class AssistedTranslationsConstants {

    public static final String SERVICE_CONFIG_FILE_NAME = "org.jahia.community.translation.assisted";
    public static final String SERVICE_CONFIG_FILE_NAME_DEEPL = SERVICE_CONFIG_FILE_NAME+".deepl";
    public static final String SERVICE_CONFIG_FILE_NAME_OPENAI = SERVICE_CONFIG_FILE_NAME+".openai";
    public static final String SERVICE_CONFIG_FILE_FULLNAME = SERVICE_CONFIG_FILE_NAME + ".cfg";
    public static final String DEEPL_API_KEY = "translation.deepl.api.key";
    public static final String OPENAI_API_KEY = "translation.openai.api.key";
    public static final String TRANSLATION_OPENAI_PROMPT = "translation.openai.prompt";
    public static final String TRANSLATION_OPENAI_MODEL = "translation.openai.model";
    public static final String PROP_PREFIX_TARGET_LANGUAGES = "targetLanguages.";

    public static final String MSG_NOTHING_TO_TRANSLATE = "Nothing to translate in %";
    public static final String SLASH = "/";

    private AssistedTranslationsConstants() {

    }

}
