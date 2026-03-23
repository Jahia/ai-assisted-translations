package org.jahia.community.translation.assisted.service.impl.openai;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatModel;
import com.openai.models.ResponseFormatJsonObject;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseTextConfig;
import org.apache.commons.lang.StringUtils;
import org.jahia.api.Constants;
import org.jahia.community.translation.assisted.graphql.TranslatedField;
import org.jahia.community.translation.assisted.service.AssistedTranslationResponse;
import org.jahia.community.translation.assisted.service.TranslationServicesManager;
import org.jahia.community.translation.assisted.service.TranslatorService;
import org.jahia.community.translation.assisted.service.glossary.GlossaryService;
import org.jahia.community.translation.assisted.service.glossary.ResolvedGlossary;
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
import java.util.*;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

import static org.jahia.community.translation.assisted.AssistedTranslationsConstants.*;


@Component(service = TranslatorService.class,
        property = {"service.translation.provider=openai", "service.ranking=10.0"},
        configurationPid = SERVICE_CONFIG_FILE_NAME_OPENAI,
        immediate = true,
        configurationPolicy = ConfigurationPolicy.REQUIRE)
public class OpenAITranslatorService implements TranslatorService {
    private static final Logger logger = LoggerFactory.getLogger(OpenAITranslatorService.class);
    private static final int TRANSLATION_BATCH_SIZE = 200;
    private static final int MAX_GLOSSARY_TERMS_IN_PROMPT = 200;
    private static final String JSON_RESPONSE_INSTRUCTION = "Return only a valid JSON object with exactly the same keys as the input and translated string values. Do not add markdown or explanations.";
    private static final String JSON_INPUT_PREFIX = "json payload to translate:\n";
    private static final ResponseTextConfig JSON_OBJECT_TEXT_CONFIG = ResponseTextConfig.builder()
            .format(ResponseFormatJsonObject.builder().build())
            .build();
    private OpenAIClient openAIClient;
    private MessageFormat messageFormat;
    private ChatModel chatModel;
    private boolean available;

    @Reference
    private TranslationServicesManager translationServicesManager;

    @Reference
    private GlossaryService glossaryService;

    private static String ensureJsonKeywordInInput(String input) {
        String safeInput = input == null ? "" : input;
        if (StringUtils.containsIgnoreCase(safeInput, "json")) {
            return safeInput;
        }
        return JSON_INPUT_PREFIX + safeInput;
    }

    private static Optional<String> extractOutputText(Response response) {
        StringBuilder outputText = new StringBuilder();
        response.output().stream()
                .filter(com.openai.models.responses.ResponseOutputItem::isMessage)
                .map(com.openai.models.responses.ResponseOutputItem::asMessage)
                .flatMap(message -> message.content().stream())
                .filter(com.openai.models.responses.ResponseOutputMessage.Content::isOutputText)
                .map(content -> content.asOutputText().text())
                .forEach(outputText::append);
        if (outputText.length() == 0) {
            return Optional.empty();
        }
        return Optional.of(outputText.toString());
    }

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
            available = false;
            return;
        }
        openAIClient = OpenAIOkHttpClient.builder().apiKey(authKey).build();
        if (properties.containsKey(TRANSLATION_OPENAI_PROMPT)) {
            String pattern = properties.get(TRANSLATION_OPENAI_PROMPT);
            messageFormat = new MessageFormat(pattern.replace("{{sourceLanguage}}", "{0}").replace("{{targetLanguage}}", "{1}"));
        }
        if (properties.containsKey(TRANSLATION_OPENAI_MODEL)) {
            chatModel = ChatModel.of(properties.get(TRANSLATION_OPENAI_MODEL));
        }
        available = true;
    }

    @Override
    public AssistedTranslationResponse translateNode(JCRNodeWrapper node, String sourceLanguage, String targetLanguage) throws RepositoryException, InterruptedException {
        final JCRSessionWrapper session = JCRSessionFactory.getInstance().getCurrentUserSession(node.getSession().getWorkspace().getName(), LanguageCodeConverters.languageCodeToLocale(sourceLanguage));
        final JCRSessionWrapper sessionTarget = JCRSessionFactory.getInstance().getCurrentUserSession(Constants.EDIT_WORKSPACE, LanguageCodeConverters.languageCodeToLocale(targetLanguage));
        final JCRNodeWrapper localizedNode = session.getNodeByIdentifier(node.getIdentifier());
        final TranslationData data = new TranslationData();

        translationServicesManager.buildDataToTranslate(localizedNode, data, true, true);
        ResolvedGlossary resolvedGlossary = glossaryService.resolve(localizedNode, sourceLanguage, targetLanguage);
        List<TranslatedField> translatedFields = getTranslatedFieldList(sourceLanguage, targetLanguage, data, true, resolvedGlossary.getTerms());
        // We need to transform this list to handle multivalued fields have their fieldname ending with ___index___
        // Each of those field values need to be stored in a List at the same original index
        Map<String, TranslatedField> translatedFieldMap = translationServicesManager.getTranslatedFieldMap(translatedFields);
        try {
            translatedFieldMap.values().forEach(field -> translationServicesManager.copyFieldValue(field, sessionTarget));
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
        ResolvedGlossary resolvedGlossary = glossaryService.resolve(localizedNode, sourceLanguage, targetLanguage);
        return getTranslatedFieldList(sourceLanguage, targetLanguage, data, false, resolvedGlossary.getTerms());
    }

    private @NotNull List<TranslatedField> getTranslatedFieldList(String sourceLanguage, String targetLanguage, TranslationData data, boolean subtree, Map<String, String> glossaryTerms) {
        // Use Responses API for translations.
        String sourceLanguageMapped = translationServicesManager.getTargetLanguages().getOrDefault(sourceLanguage, sourceLanguage);
        String targetLanguageMapped = translationServicesManager.getTargetLanguages().getOrDefault(targetLanguage, targetLanguage);
        String baseSystemPrompt = messageFormat.format(new Object[]{sourceLanguageMapped, targetLanguageMapped})
                + "\n" + JSON_RESPONSE_INSTRUCTION;
        List<Map.Entry<String, String>> textEntries = new ArrayList<>(data.getTexts().entrySet());
        if (textEntries.isEmpty()) {
            return List.of();
        }

        // Keep one conversation across batches with previous_response_id without replaying full history.
        String previousResponseId = null;
        Map<String, String> translatedValues = new HashMap<>();

        for (int startIdx = 0; startIdx < textEntries.size(); startIdx += TRANSLATION_BATCH_SIZE) {
            int endIdx = Math.min(startIdx + TRANSLATION_BATCH_SIZE, textEntries.size());
            Map<String, String> batchTexts = new LinkedHashMap<>();
            textEntries.subList(startIdx, endIdx).forEach(entry -> batchTexts.put(entry.getKey(), entry.getValue()));

            batchTexts.forEach((key, sourceText) -> {
                String glossaryMatch = glossaryTerms.get(sourceText);
                if (StringUtils.isNotBlank(glossaryMatch)) {
                    translatedValues.put(key, glossaryMatch);
                }
            });

            Map<String, String> textsToTranslate = new LinkedHashMap<>();
            batchTexts.forEach((key, value) -> {
                if (!translatedValues.containsKey(key)) {
                    textsToTranslate.put(key, value);
                }
            });

            if (textsToTranslate.isEmpty()) {
                continue;
            }

            JSONObject requestJson = new JSONObject(textsToTranslate);
            if (logger.isDebugEnabled()) {
                logger.debug("Calling OpenAI API with requested translation batch [{}, {})", startIdx, endIdx);
            }

            String systemPrompt = baseSystemPrompt + buildGlossaryInstruction(glossaryTerms, textsToTranslate.values());
            Response response = createTranslationResponse(requestJson.toString(), previousResponseId, systemPrompt);
            previousResponseId = response.id();
            Optional<JSONObject> responseJson = extractOutputText(response).flatMap(this::parseJsonObject);

            if (responseJson.isEmpty()) {
                logger.warn("OpenAI response {} for batch [{}, {}) was not valid JSON, retrying once", response.id(), startIdx, endIdx);
                String repairPrompt = "Your previous answer was not valid JSON. " + JSON_RESPONSE_INSTRUCTION + " Input payload: " + requestJson;
                Response retryResponse = createTranslationResponse(repairPrompt, previousResponseId, null);
                previousResponseId = retryResponse.id();
                responseJson = extractOutputText(retryResponse).flatMap(this::parseJsonObject);
            }

            if (responseJson.isEmpty()) {
                logger.error("OpenAI did not return valid JSON for translation batch [{}, {})", startIdx, endIdx);
                return List.of();
            }

            Map<String, Object> responseJsonMap = responseJson.get().toMap();
            textsToTranslate.keySet().forEach(key -> {
                if (responseJsonMap.containsKey(key) && responseJsonMap.get(key) != null) {
                    translatedValues.put(key, responseJsonMap.get(key).toString());
                }
            });
        }

        return translationServicesManager.getTranslatedFieldList(data, subtree, translatedValues);
    }

    private Response createTranslationResponse(String input, String previousResponseId, String instructions) {
        ResponseCreateParams.Builder paramsBuilder = ResponseCreateParams
                .builder()
                .model(chatModel)
                .text(JSON_OBJECT_TEXT_CONFIG)
                .input(ensureJsonKeywordInInput(input));
        if (instructions != null) {
            paramsBuilder.instructions(instructions);
        }
        if (previousResponseId != null) {
            paramsBuilder.previousResponseId(previousResponseId);
        }
        return openAIClient.responses().create(paramsBuilder.build());
    }

    private String buildGlossaryInstruction(Map<String, String> glossaryTerms, Collection<String> batchTexts) {
        if (glossaryTerms == null || glossaryTerms.isEmpty()) {
            return "";
        }

        List<String> nonEmptyBatchTexts = batchTexts.stream()
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toList());
        if (nonEmptyBatchTexts.isEmpty()) {
            return "";
        }

        List<Map.Entry<String, String>> entries = new ArrayList<>(glossaryTerms.entrySet());
        // Prefer longer terms first so specific phrases are not dropped when capped.
        entries.sort((a, b) -> Integer.compare(StringUtils.length(b.getKey()), StringUtils.length(a.getKey())));

        Map<String, String> limitedTerms = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : entries) {
            String sourceTerm = StringUtils.trimToEmpty(entry.getKey());
            if (StringUtils.isBlank(sourceTerm)) {
                continue;
            }
            boolean relevant = isRelevantGlossaryTerm(sourceTerm, nonEmptyBatchTexts);
            if (relevant) {
                limitedTerms.put(sourceTerm, entry.getValue());
            }
            if (limitedTerms.size() >= MAX_GLOSSARY_TERMS_IN_PROMPT) {
                break;
            }
        }

        if (limitedTerms.isEmpty()) {
            return "";
        }

        return "\nApply this json glossary (source->target) when terms appear: " + new JSONObject(limitedTerms);
    }

    private boolean isRelevantGlossaryTerm(String sourceTerm, Collection<String> batchTexts) {
        String[] tokens = StringUtils.trimToEmpty(sourceTerm).split("\\s+");
        if (tokens.length == 0) {
            return false;
        }

        String joinedTokens = Arrays.stream(tokens)
                .filter(StringUtils::isNotBlank)
                .map(Pattern::quote)
                .collect(Collectors.joining("\\\\s+"));
        if (StringUtils.isBlank(joinedTokens)) {
            return false;
        }

        Pattern termPattern = Pattern.compile("(?<![\\\\p{L}\\\\p{N}])" + joinedTokens + "(?![\\\\p{L}\\\\p{N}])", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        return batchTexts.stream().anyMatch(text -> termPattern.matcher(text).find());
    }

    private Optional<JSONObject> parseJsonObject(String content) {
        try {
            return Optional.of(new JSONObject(content));
        } catch (Exception e) {
            if (logger.isDebugEnabled()) {
                logger.debug("Unable to parse OpenAI output as JSON: {}", content, e);
            }
            return Optional.empty();
        }
    }
}
