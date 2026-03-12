package org.jahia.community.translation.assisted.service.impl.openai;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatModel;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import org.apache.commons.lang.StringUtils;
import org.jahia.community.translation.assisted.graphql.TranslatedField;
import org.jahia.community.translation.assisted.service.AssistedTranslationResponse;
import org.jahia.community.translation.assisted.service.TranslationServicesManager;
import org.jahia.community.translation.assisted.service.TranslationServicesManagerImpl;
import org.jahia.community.translation.assisted.service.TranslatorService;
import org.jahia.community.translation.assisted.service.impl.TranslationData;
import org.jahia.osgi.BundleUtils;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.utils.LanguageCodeConverters;
import org.json.JSONObject;
import org.osgi.service.component.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.text.MessageFormat;
import java.util.List;
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

        translationServicesManager.buildDataToTranslate(localizedNode, data, true);
        // Build JSON to send to OpenAI, using data.getTexts() which is a map of key->text to translate, where key is the JCR path to the property (e.g. /a/title) and value is the text to translate (e.g. "Hello <b>world</b>")
//        {
//            "role": "user",
//                "content": "{\"sourceLanguage\":\"en\",\"targetLanguage\":\"fr\",\"texts\":{\"/a/title\":\"Hello <b>world</b>\",\"/a/desc\":\"A &amp; B\"}}"
//        }
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
                    .map(e -> new TranslatedField(StringUtils.substringAfterLast(e.getKey(),SLASH), (String) e.getValue()))
                    .collect(Collectors.toList());
        } else  {
            return List.of();
        }
    }
}
