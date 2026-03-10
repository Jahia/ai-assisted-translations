package org.jahia.community.translation.assisted.service.impl.openai;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatModel;
import com.openai.models.chat.completions.*;
import org.apache.commons.lang.StringUtils;
import org.jahia.api.Constants;
import org.jahia.community.translation.assisted.graphql.TranslatedField;
import org.jahia.community.translation.assisted.service.AssistedTranslationResponse;
import org.jahia.community.translation.assisted.service.TranslatorService;
import org.jahia.community.translation.assisted.service.impl.TranslationData;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.nodetypes.ExtendedPropertyDefinition;
import org.jahia.utils.LanguageCodeConverters;
import org.json.JSONObject;
import org.osgi.framework.BundleException;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.jahia.community.translation.assisted.AssistedTranslationsConstants.*;


@Component(service = TranslatorService.class, property = {"service.translation.provider=openai","service.ranking=10"}, configurationPid = SERVICE_CONFIG_FILE_NAME)
public class OpenAITranslatorService implements TranslatorService {
    private static final Logger logger = LoggerFactory.getLogger(OpenAITranslatorService.class);
    private static final String SLASH = "/";
    private OpenAIClient openAIClient;
    private boolean available;

    @Override
    public String getProviderKey() {
        return "openai";
    }

    @Override
    public Boolean isAvailable() {
        return available;
    }

    @Activate
    public void activate(Map<String, ?> properties) throws BundleException {
        if (properties == null) {
            logger.warn("Missing configurations: {}", SERVICE_CONFIG_FILE_FULLNAME);
            return;
        }

        final String authKey = (String) properties.getOrDefault(OPENAI_API_KEY, null);
        if (authKey == null) {
            available=false;
        }
        logger.debug("DeepL {} = {}", OPENAI_API_KEY, authKey);
        openAIClient = OpenAIOkHttpClient.builder().apiKey(authKey).organization("org-g0yPMZedFvXKAonaRbXKQykS").project("proj_MEag26KzVkGWSXdevjhi7Syh").build();
        available=true;
    }

    @Override
    public AssistedTranslationResponse translateNode(JCRNodeWrapper node, String sourceLanguage, String targetLanguage) throws RepositoryException, InterruptedException {
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

        buildDataToTranslate(localizedNode, data, true);
        // Build JSON to send to OpenAI, using data.getTexts() which is a map of key->text to translate, where key is the JCR path to the property (e.g. /a/title) and value is the text to translate (e.g. "Hello <b>world</b>")
//        {
//            "role": "user",
//                "content": "{\"sourceLanguage\":\"en\",\"targetLanguage\":\"fr\",\"texts\":{\"/a/title\":\"Hello <b>world</b>\",\"/a/desc\":\"A &amp; B\"}}"
//        }
        JSONObject json = new JSONObject();
        json.put("sourceLanguage", sourceLanguage);
        json.put("targetLanguage", targetLanguage);
        json.put("texts", data.getTexts());
        // Use Chat Completions for traductions
        ChatCompletionCreateParams params = ChatCompletionCreateParams
                .builder()
                .model(ChatModel.GPT_4_1_MINI)
                .addSystemMessage("You are a translation engine. Return only strict JSON object key->translated text.Preserve HTML tags/entities exactly. The source language is " + sourceLanguage + " and the target language is " + targetLanguage)
                .addUserMessage(json.toString())
                .build();
        // Call OpenAI API
        ChatCompletion chatCompletion = openAIClient.chat().completions().create(params);
        List<ChatCompletion.Choice> choices = chatCompletion.choices();
        Optional<String> content = choices.get(0).message().content();
        if(content.isPresent()) {
            JSONObject responseJson = new JSONObject(content.get());
            // The response is a JSON object with the same structure as the request, but with the texts translated, e.g. {"sourceLanguage":"en","targetLanguage":"fr","texts":{"/a/title":"Bonjour <b>le monde</b>","/a/desc":"A &amp; B"}}
            JSONObject translatedTexts = responseJson.getJSONObject("texts");
            // Build the response with the translated texts by transforming in TranslatedField objects, where the key is the JCR path to the property and the value is the translated text
             return translatedTexts.toMap().entrySet().stream()
                    .map(e -> new TranslatedField(StringUtils.substringAfterLast(e.getKey(),SLASH), (String) e.getValue()))
                    .collect(Collectors.toList());
        } else  {
            return List.of();
        }
    }

    private void buildDataToTranslate(JCRNodeWrapper node, TranslationData data, boolean forceTranslation) throws RepositoryException {
        if (!isTranslatableNode(node)) {
            return;
        }

        final PropertyIterator properties;
        try {
            properties = node.getProperties();
        } catch (RepositoryException e) {
            if (logger.isErrorEnabled()) {
                logger.error("", e);
            }
            return;
        }
        while (properties.hasNext()) {
            final Property property = properties.nextProperty();
            if (isTranslatableProperty(property)) {
                final String key = node.getPath() + SLASH + property.getName();
                final String stringValue = StringUtils.trimToNull(property.getValue().getString());
                if (forceTranslation || stringValue != null) {
                    data.trackText(key, stringValue);
                }
            }

        }
    }

    private boolean isTranslatableNode(JCRNodeWrapper node) {
        try {
            if (node.isNodeType(Constants.JAHIANT_PAGE)) {
                return true;
            }
            if (node.isNodeType(Constants.JAHIANT_CONTENT)) {
                return true;
            }
        } catch (RepositoryException e) {
            if (logger.isErrorEnabled()) {
                logger.error("", e);
            }
        }
        return false;
    }

    private boolean isTranslatableProperty(Property property) {
        final ExtendedPropertyDefinition definition;
        try {
            definition = (ExtendedPropertyDefinition) property.getDefinition();
        } catch (RepositoryException e) {
            if (logger.isErrorEnabled()) {
                logger.error("", e);
            }
            return false;
        }

        return definition.isInternationalized() && !definition.isMultiple() && definition.getRequiredType() == PropertyType.STRING && !definition.isHidden() && !definition.isProtected();
    }
}
