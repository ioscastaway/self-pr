package com.ioscastaway.selfpr.platform

import com.anthropic.client.AnthropicClient
import com.anthropic.core.JsonValue
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.ioscastaway.selfpr.healer.CrashReport
import com.ioscastaway.selfpr.healer.Diagnoser
import com.ioscastaway.selfpr.healer.Diagnosis
import com.ioscastaway.selfpr.healer.HealPrompt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.jvm.optionals.getOrNull

/**
 * One request, structured output. What leaves the device: the trace, the architecture notes, and
 * the app's own source files for the frames in the trace. Nothing about the user.
 */
class ClaudeDiagnoser(private val client: AnthropicClient, private val model: String) : Diagnoser {

    override suspend fun diagnose(crash: CrashReport, files: List<Pair<String, String>>, notes: String): Diagnosis =
        withContext(Dispatchers.IO) {
            val schema = JsonOutputFormat.Schema.builder().apply {
                HealPrompt.schema.forEach { (k, v) -> putAdditionalProperty(k, JsonValue.from(v)) }
            }.build()
            val params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(16000L)
                .system(HealPrompt.SYSTEM)
                // Adaptive thinking is the model default. A patch is worth thinking about.
                .outputConfig(
                    OutputConfig.builder()
                        .effort(OutputConfig.Effort.HIGH)
                        .format(JsonOutputFormat.builder().schema(schema).build())
                        .build(),
                )
                .addUserMessage(HealPrompt.user(crash, files, notes))
                .build()
            val response = client.messages().create(params)
            if (response.stopReason().getOrNull() == StopReason.REFUSAL) {
                throw IllegalStateException("The model declined to look at this crash.")
            }
            if (response.stopReason().getOrNull() == StopReason.MAX_TOKENS) {
                throw IllegalStateException("The diagnosis was cut off at max_tokens; the patched files are too large.")
            }
            Diagnosis.parse(response.content().mapNotNull { it.text().getOrNull()?.text() }.joinToString(""))
        }
}
