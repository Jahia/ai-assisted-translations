package org.jahia.community.translation.assisted.service.impl.openai;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatModel;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import org.apache.commons.lang.StringUtils;
import org.jahia.api.Constants;
import org.jahia.community.translation.assisted.graphql.TranslatedField;
import org.jahia.community.translation.assisted.service.AssistedTranslationResponse;
import org.jahia.community.translation.assisted.service.TranslationServicesManager;
import org.jahia.community.translation.assisted.service.TranslatorService;
import org.jahia.community.translation.assisted.service.impl.TranslationData;
import org.jahia.community.translation.assisted.service.impl.deepl.DeepLAssistedTranslationResponseImpl;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.utils.LanguageCodeConverters;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.jahia.community.translation.assisted.AssistedTranslationsConstants.*;


@Component(service = TranslatorService.class,
        property = {"service.translation.provider=openai","service.ranking=10.0"},
        configurationPid = SERVICE_CONFIG_FILE_NAME_OPENAI,
        immediate = true,
        configurationPolicy = ConfigurationPolicy.REQUIRE)
public class OpenAITranslatorService implements TranslatorService {
    private static final Logger logger = LoggerFactory.getLogger(OpenAITranslatorService.class);
    private OpenAIClient openAIClient;
    private MessageFormat messageFormat;
    private ChatModel chatModel;
    private boolean available;

    @Reference
    private TranslationServicesManager translationServicesManager;

    @Override
    public String getProviderKey() {
        return "openai";
    }

    @Override
    public Boolean isAvailable() {
        return available;
    }

    @Activate
    public void activate(Map<String, String> properties) {
        if (properties == null) {
            logger.warn("Missing configurations: {}", SERVICE_CONFIG_FILE_FULLNAME);
            return;
        }
        logger.info("Activating OpenAI Translator Service");
        final String authKey = properties.getOrDefault(OPENAI_API_KEY, null);
        if (authKey == null) {
            available=false;
            return;
        }
        openAIClient = OpenAIOkHttpClient.builder().apiKey(authKey).build();
        if (properties.containsKey(TRANSLATION_OPENAI_PROMPT)) {
            String pattern = properties.get(TRANSLATION_OPENAI_PROMPT);
            messageFormat = new MessageFormat(pattern.replace("{{sourceLanguage}}","{0}").replace("{{targetLanguage}}","{1}"));
        }
        if (properties.containsKey(TRANSLATION_OPENAI_MODEL)) {
            chatModel = ChatModel.of(properties.get(TRANSLATION_OPENAI_MODEL));
        }
        available=true;
    }

    @Override
    public AssistedTranslationResponse translateNode(JCRNodeWrapper node, String sourceLanguage, String targetLanguage) throws RepositoryException, InterruptedException {
        final JCRSessionWrapper session = JCRSessionFactory.getInstance().getCurrentUserSession(node.getSession().getWorkspace().getName(), LanguageCodeConverters.languageCodeToLocale(sourceLanguage));
        final JCRSessionWrapper sessionTarget = JCRSessionFactory.getInstance().getCurrentUserSession(Constants.EDIT_WORKSPACE, LanguageCodeConverters.languageCodeToLocale(targetLanguage));
        final JCRNodeWrapper localizedNode = session.getNodeByIdentifier(node.getIdentifier());
        final TranslationData data = new TranslationData();

        translationServicesManager.buildDataToTranslate(localizedNode, data, true, true);
        List<TranslatedField> translatedFields = getTranslatedFieldList(sourceLanguage, targetLanguage, data, true);
        // As we translate a tree, we might have found some duplicates in TranslationData, so we need to complete the translatedFields with the duplicates
        Map<String, String> duplicates = data.getDuplicates();
        duplicates.forEach((key, value) -> {
            Optional<TranslatedField> translatedValue = translatedFields.stream().filter(f -> StringUtils.equals(f.getFieldName(), value)).findFirst();
            if (translatedValue.isPresent()) {
                translatedFields.add(new TranslatedField(key, translatedValue.get().getTranslatedValue()));
            } else {
                logger.warn("No translation found for duplicate key {} with reference {}", key, value);
            }
        });
        if (translatedFields.size() != (data.getDuplicates().size() + data.getTexts().size())) {
            logger.warn("Translated fields size {} is different from texts to translate size {} plus duplicates size {}", translatedFields.size(), data.getTexts().size(), data.getDuplicates().size());
        }
        try {
            translatedFields.forEach(field -> {
                try {
                    String path = field.getFieldName();
                    String value = field.getTranslatedValue();
                    final JCRNodeWrapper targetNode = sessionTarget.getNode(StringUtils.substringBeforeLast(path, SLASH));
                    final String propertyName = StringUtils.substringAfterLast(path, SLASH);
                    if (targetNode.hasProperty(propertyName) && StringUtils.equals(targetNode.getPropertyAsString(propertyName), value)) {
                        logger.warn("{} is already translated", path);
                    } else {
                        targetNode.setProperty(propertyName, value);
                    }
                } catch (RepositoryException e) {
                    if (logger.isErrorEnabled()) {
                        logger.error("", e);
                    }
                }
            });
            sessionTarget.save();
            // Load the translated node in target language to render the new title
            String displayableName = sessionTarget.getNodeByIdentifier(node.getIdentifier()).getDisplayableName();
            Locale targetLocale = LanguageCodeConverters.getLocaleFromCode(targetLanguage);
            Locale sourceLocale = LanguageCodeConverters.getLocaleFromCode(sourceLanguage);
            MessageFormat languageInfo = new MessageFormat("{0} ({1})");
            return new DeepLAssistedTranslationResponseImpl(true, MessageFormat.format("Page/resource {0} translated successfully from {1} to {2}",
                    displayableName,
                    StringUtils.capitalize(languageInfo.format(new Object[]{sourceLocale.getDisplayLanguage(targetLocale), sourceLocale.getDisplayLanguage(sourceLocale)})),
                    StringUtils.capitalize(languageInfo.format(new Object[]{targetLocale.getDisplayLanguage(targetLocale), targetLocale.getDisplayLanguage(sourceLocale)}))));
        } catch (RepositoryException e) {
            logger.error("Error when getting session for target language {}", targetLanguage, e);
        }
        return null;
    }

    @Override
    public AssistedTranslationResponse translateProperty(JCRNodeWrapper node, String propertyName, String sourceLanguage, String targetLanguage) throws RepositoryException, InterruptedException {
        return null;
    }

    @Override
    public List<TranslatedField> suggestTranslationForNode(JCRNodeWrapper node, String sourceLanguage, String targetLanguage) throws RepositoryException, InterruptedException {
        // Ensure we get the nod ein the source language to be able to suggest translations
        final JCRSessionWrapper session = JCRSessionFactory.getInstance().getCurrentUserSession(node.getSession().getWorkspace().getName(), LanguageCodeConverters.languageCodeToLocale(sourceLanguage));
        final JCRNodeWrapper localizedNode = session.getNodeByIdentifier(node.getIdentifier());
        final TranslationData data = new TranslationData();

        translationServicesManager.buildDataToTranslate(localizedNode, data, true, false);
        // Build JSON to send to OpenAI, using data.getTexts() which is a map of key->text to translate, where key is the JCR path to the property (e.g. /a/title) and value is the text to translate (e.g. "Hello <b>world</b>")
//        {
//            "role": "user",
//                "content": "{\"sourceLanguage\":\"en\",\"targetLanguage\":\"fr\",\"texts\":{\"/a/title\":\"Hello <b>world</b>\",\"/a/desc\":\"A &amp; B\"}}"
//        }
        return getTranslatedFieldList(sourceLanguage, targetLanguage, data, false);
    }

    private @NotNull List<TranslatedField> getTranslatedFieldList(String sourceLanguage, String targetLanguage, TranslationData data, boolean subtree) {
        JSONObject json = new JSONObject(data.getTexts());
        // Use Chat Completions for traductions
        String sourceLanguageMapped = translationServicesManager.getTargetLanguages().getOrDefault(sourceLanguage, sourceLanguage);
        String targetLanguageMapped = translationServicesManager.getTargetLanguages().getOrDefault(targetLanguage, targetLanguage);
        String systemPrompt = messageFormat.format(new Object[]{sourceLanguageMapped, targetLanguageMapped});
        ChatCompletionCreateParams params = ChatCompletionCreateParams
                .builder()
                .model(chatModel)
                .addSystemMessage(systemPrompt)
                .addUserMessage(json.toString())
                .build();
        // Call OpenAI API
        if (logger.isDebugEnabled()) {
            logger.debug("Calling OpenAI API with prompt: {} and requested translation {}", systemPrompt, json.toString());
        }
        ChatCompletion chatCompletion = openAIClient.chat().completions().create(params);
        List<ChatCompletion.Choice> choices = chatCompletion.choices();
        Optional<String> content = choices.get(0).message().content();
        if(content.isPresent()) {
            JSONObject responseJson = new JSONObject(content.get());
            // The response is a JSON object with the same structure as the request, but with the texts translated, e.g. {"sourceLanguage":"en","targetLanguage":"fr","texts":{"/a/title":"Bonjour <b>le monde</b>","/a/desc":"A &amp; B"}}
            // Build the response with the translated texts by transforming in TranslatedField objects, where the key is the JCR path to the property and the value is the translated text
            return responseJson.toMap().entrySet().stream()
                    .map(e -> new TranslatedField((subtree ? e.getKey() : StringUtils.substringAfterLast(e.getKey(), SLASH)), (String) e.getValue()))
                    .collect(Collectors.toList());
        } else  {
            return List.of();
        }
    }
}
